package tw.pokemon.collectionmanager.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tw.pokemon.collectionmanager.data.local.CollectionDatabase
import tw.pokemon.collectionmanager.data.local.BackgroundEntity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 圖片解析集中在資料層。Compose 只取得 ImageReference，不組網址，也不需要知道圖片供應來源。
 */
interface PokemonImageRepository {
    suspend fun getImage(
        speciesId: String,
        formId: String? = null,
        costumeId: String? = null,
        shiny: Boolean = false,
    ): ImageReference

    suspend fun getBackgroundImage(backgroundId: String): ImageReference
    suspend fun countBundledBackgroundImages(backgrounds: List<BackgroundEntity>): Int
    suspend fun loadBitmap(reference: ImageReference, maxDimension: Int = 512): Bitmap?
}

sealed interface ImageReference {
    val cacheKey: String

    data class LocalFile(val file: File, override val cacheKey: String) : ImageReference
    data class BundledAsset(val assetPath: String, override val cacheKey: String) : ImageReference
    data class RemoteStatic(val url: String, override val cacheKey: String) : ImageReference
    data class Placeholder(val label: String, override val cacheKey: String) : ImageReference
}

class LocalFirstPokemonImageRepository(
    private val context: Context,
    private val database: CollectionDatabase,
) : PokemonImageRepository {
    private val master = database.masterDataDao()
    private val memoryCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8L)
            .coerceIn(4L * 1024L * 1024L, 32L * 1024L * 1024L)
            .toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val bundledBackgroundImageIndex: Map<String, String> by lazy {
        runCatching {
            context.assets.open(BACKGROUND_IMAGE_INDEX).bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                json.keys().asSequence().associateWith(json::getString)
            }
        }.getOrDefault(emptyMap())
    }

    override suspend fun getImage(
        speciesId: String,
        formId: String?,
        costumeId: String?,
        shiny: Boolean,
    ): ImageReference = withContext(Dispatchers.IO) {
        val species = master.getSpecies(speciesId)
            ?: return@withContext ImageReference.Placeholder("找不到 Pokémon 圖片", speciesId)
        val form = formId?.let { master.getForm(it) }
        val costume = costumeId?.takeUnless { it == "COSTUME_NONE" }?.let { master.getCostume(it) }
        val compatibilityImageKey = if (costumeId != null && costumeId != "COSTUME_NONE" && formId != null) {
            master.findCostumeImageKey(speciesId, formId, costumeId)
        } else {
            null
        }
        val imageKey = compatibilityImageKey ?: costume?.imageKey ?: form?.imageKey ?: (if (shiny) species.shinyImageKey else null) ?: species.defaultImageKey
        resolve(toShinyAssetKey(imageKey, shiny, compatibilityImageKey == null && costume == null && form == null), species.nameZhTw, shiny, species.dexNumber)
    }

    override suspend fun getBackgroundImage(backgroundId: String): ImageReference = withContext(Dispatchers.IO) {
        val background = master.getBackground(backgroundId)
        val databaseKey = background?.previewImageKey ?: background?.imageKey

        // A downloaded manifest may intentionally override a bundled image with a newer HTTPS asset.
        if (databaseKey?.startsWith("https://") == true) {
            return@withContext resolve(databaseKey, background?.displayName ?: backgroundId, false, null)
        }

        // Resolve by stable background ID first. This is independent of Room seeding and prevents an
        // old imageKey left by a previous app version from hiding a valid bundled background image.
        bundledBackgroundImageIndex[backgroundId]?.let { bundledKey ->
            val bundledReference = resolve(bundledKey, background?.displayName ?: backgroundId, false, null)
            if (bundledReference !is ImageReference.Placeholder) return@withContext bundledReference
        }

        if (background == null) {
            ImageReference.Placeholder("找不到背景圖片", backgroundId)
        } else {
            resolve(databaseKey, background.displayName, false, null)
        }
    }

    override suspend fun countBundledBackgroundImages(backgrounds: List<BackgroundEntity>): Int = withContext(Dispatchers.IO) {
        val knownIds = backgrounds.asSequence().map(BackgroundEntity::id).toHashSet()
        bundledBackgroundImageIndex.count { (backgroundId, imageKey) ->
            backgroundId in knownIds &&
            runCatching { context.assets.open(bundledAssetPath(imageKey)).use { } }.isSuccess
        }
    }

    override suspend fun loadBitmap(reference: ImageReference, maxDimension: Int): Bitmap? = withContext(Dispatchers.IO) {
        val dimension = maxDimension.coerceIn(96, 1024)
        val cacheKey = "${reference.cacheKey}:$dimension"
        memoryCache.get(cacheKey)?.let { return@withContext it }
        val bitmap = when (reference) {
            is ImageReference.LocalFile -> decodeFile(reference.file, dimension)
            is ImageReference.BundledAsset -> decodeBundledAsset(reference.assetPath, dimension)
            is ImageReference.RemoteStatic -> download(reference, dimension)
            is ImageReference.Placeholder -> null
        }
        bitmap?.let { memoryCache.put(cacheKey, it) }
        bitmap
    }

    private fun resolve(imageKey: String?, label: String, shiny: Boolean, dexNumber: Int?): ImageReference {
        if (imageKey.isNullOrBlank()) {
            val remote = dexNumber?.let { officialArtworkUrl(it, shiny) }
            return remote?.let { ImageReference.RemoteStatic(it, "remote:$it") }
                ?: ImageReference.Placeholder(label, "placeholder:$label")
        }
        val safeKey = imageKey.replace("..", "_").replace('/', '_').replace('\\', '_')
        val cached = File(context.cacheDir, "pokemon_assets/$safeKey")
        if (cached.isFile) return ImageReference.LocalFile(cached, "file:${cached.absolutePath}")
        val bundled = bundledAssetPath(imageKey)
        val bundledExists = runCatching { context.assets.open(bundled).use { } }.isSuccess
        if (bundledExists) return ImageReference.BundledAsset(bundled, "asset:$bundled")
        val remote = when {
            imageKey.startsWith("http://") || imageKey.startsWith("https://") -> imageKey
            imageKey.startsWith("pogo/") -> POGO_RAW_BASE + Uri.encode(imageKey.removePrefix("pogo/"), "/._-")
            dexNumber != null -> officialArtworkUrl(dexNumber, shiny)
            else -> null
        }
        return remote?.let { ImageReference.RemoteStatic(it, "remote:$it") }
            ?: ImageReference.Placeholder(label, "placeholder:$imageKey")
    }

    private fun officialArtworkUrl(dexNumber: Int, shiny: Boolean): String {
        val suffix = if (shiny) "-shiny" else ""
        return "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/${dexNumber}$suffix.png"
    }

    private fun bundledAssetPath(imageKey: String): String = when {
        imageKey.startsWith("pogo/") -> "images/${imageKey.removePrefix("pogo/")}"
        imageKey.substringAfterLast('/', imageKey).contains('.') -> "images/$imageKey"
        else -> "images/$imageKey.png"
    }

    private fun toShinyAssetKey(imageKey: String?, shiny: Boolean, alreadyShinySpecific: Boolean): String? {
        if (!shiny || alreadyShinySpecific || imageKey.isNullOrBlank()) return imageKey
        return when {
            imageKey.endsWith(".icon.png") -> imageKey.replace(".icon.png", ".s.icon.png")
            imageKey.contains("/pokemon_icon_pm", ignoreCase = true) && imageKey.endsWith(".png") -> imageKey.removeSuffix(".png") + "_shiny.png"
            else -> imageKey
        }
    }

    private fun decodeFile(file: File, maxDimension: Int): Bitmap? {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun decodeBundledAsset(assetPath: String, maxDimension: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        while (maxOf(width / sample, height / sample) > maxDimension) {
            sample *= 2
        }
        return sample
    }

    private fun download(reference: ImageReference.RemoteStatic, maxDimension: Int): Bitmap? {
        val safeKey = reference.cacheKey.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destination = File(context.cacheDir, "pokemon_assets/$safeKey.png")
        if (destination.isFile) {
            decodeFile(destination, maxDimension)?.let { return it }
            destination.delete()
        }
        repeat(2) { attempt ->
            val bitmap = runCatching {
                val connection = (URL(reference.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12_000
                    readTimeout = 20_000
                    requestMethod = "GET"
                    doInput = true
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "image/*")
                    setRequestProperty("User-Agent", "PokemonCollectionManager-Android")
                }
                try {
                    connection.connect()
                    if (connection.responseCode !in 200..299) return@runCatching null
                    if (!connection.contentType.orEmpty().startsWith("image/")) return@runCatching null
                    val parent = destination.parentFile ?: return@runCatching null
                    parent.mkdirs()
                    val temporary = File(parent, "${destination.name}.part")
                    temporary.delete()
                    connection.inputStream.use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
                    val decoded = decodeFile(temporary, maxDimension)
                    if (decoded == null) {
                        temporary.delete()
                        null
                    } else {
                        if (!temporary.renameTo(destination)) {
                            temporary.copyTo(destination, overwrite = true)
                            temporary.delete()
                        }
                        decoded
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
            if (bitmap != null) return bitmap
            if (attempt == 0) destination.delete()
        }
        return null
    }
}

private const val POGO_RAW_BASE = "https://raw.githubusercontent.com/PokeMiners/pogo_assets/master/"
private const val BACKGROUND_IMAGE_INDEX = "master/background_image_index.json"

