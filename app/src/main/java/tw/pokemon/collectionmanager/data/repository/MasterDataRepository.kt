package tw.pokemon.collectionmanager.data.repository

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tw.pokemon.collectionmanager.data.local.BackgroundEntity
import tw.pokemon.collectionmanager.data.local.BackgroundType
import tw.pokemon.collectionmanager.data.local.CollectionDatabase
import tw.pokemon.collectionmanager.data.local.CostumeEntity
import tw.pokemon.collectionmanager.data.local.MasterDataMetaEntity
import tw.pokemon.collectionmanager.data.local.PokemonBackgroundCompatibilityEntity
import tw.pokemon.collectionmanager.data.local.PokemonCostumeCompatibilityEntity
import tw.pokemon.collectionmanager.data.local.PokemonFormEntity
import tw.pokemon.collectionmanager.data.local.PokemonSpeciesEntity
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.net.HttpURLConnection
import java.net.URL

sealed interface MasterDataUpdateResult {
    data class Updated(val previousVersion: String?, val newVersion: String) : MasterDataUpdateResult
    data class AlreadyCurrent(val version: String) : MasterDataUpdateResult
}

data class MasterDataUpdateProgress(
    val stage: String,
    val fraction: Float?,
    val detail: String,
)

/**
 * 主資料只由 JSON 匯入，畫面不直接建立 Pokémon、型態、裝扮或背景選項。
 * 使用 isActive 隱藏舊版本資料，但不刪除可能被既有收藏引用的列。
 */
class MasterDataRepository(
    private val context: Context,
    private val database: CollectionDatabase,
) {
    private val dao = database.masterDataDao()

    val species: Flow<List<PokemonSpeciesEntity>> = dao.observeSpecies()
    val costumes = dao.observeCostumes()
    val backgrounds = dao.observeBackgrounds()
    val meta: Flow<MasterDataMetaEntity?> = dao.observeMeta()

    fun forms(speciesId: String): Flow<List<PokemonFormEntity>> = dao.observeForms(speciesId)
    fun searchSpecies(query: String, generation: Int?): Flow<List<PokemonSpeciesEntity>> = dao.observeSpeciesPicker(query.trim(), generation)
    fun costumeCompatibility(speciesId: String, formId: String) = dao.observeCostumeCompatibility(speciesId, formId)
    fun backgroundCompatibility(speciesId: String, formId: String) = dao.observeBackgroundCompatibility(speciesId, formId)

    suspend fun seedIfNeeded() {
        val manifest = runCatching {
            context.assets.open("master/master_manifest.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return
        val version = runCatching {
            val root = JSONObject(manifest)
            root.optString("masterVersion", root.optString("version", ""))
        }.getOrDefault("")
        val expectedCounts = runCatching {
            val root = JSONObject(manifest)
            listOf(
                root.optJSONArray("species")?.length() ?: 0,
                root.optJSONArray("forms")?.length() ?: 0,
                root.optJSONArray("costumes")?.length() ?: 0,
                root.optJSONArray("backgrounds")?.length() ?: 0,
            )
        }.getOrDefault(listOf(0, 0, 0, 0))
        val current = dao.getMeta()
        if (current?.masterVersion == version && current.speciesCount == expectedCounts[0] && current.formCount == expectedCounts[1] && current.costumeCount == expectedCounts[2] && current.backgroundCount == expectedCounts[3] && dao.speciesCount() > 0) return
        importManifest(manifest)
    }

    suspend fun importManifest(
        json: String,
        onProgress: (MasterDataUpdateProgress) -> Unit = {},
    ) {
        val root = JSONObject(json)
        val masterVersion = root.optString("masterVersion", root.optString("version", "未指定"))
        val species = root.optJSONArray("species").toSpecies()
        val forms = root.optJSONArray("forms").toForms()
        val costumes = root.optJSONArray("costumes").toCostumes()
        val backgrounds = root.optJSONArray("backgrounds").toBackgrounds()
        val costumeCompatibility = root.optJSONArray("costumeCompatibility").toCostumeCompatibility()
        val backgroundCompatibility = root.optJSONArray("backgroundCompatibility").toBackgroundCompatibility()
        require(species.isNotEmpty()) { "主資料至少要包含一種 Pokémon" }

        onProgress(
            MasterDataUpdateProgress(
                stage = "解析主資料",
                fraction = 0.72f,
                detail = "已讀取 ${species.size} 種 Pokémon、${forms.size} 種型態、${costumes.size} 項裝扮與 ${backgrounds.size} 張背景",
            ),
        )

        val imageCount = root.optInt("imageCount", countImageKeys(species, forms, costumes, backgrounds))
        val missingImageCount = root.optInt("missingImageCount", 0)
        val generatedAt = root.optString("generatedAt").ifBlank { null }
        onProgress(MasterDataUpdateProgress("更新本機資料庫", 0.84f, "正在保留收藏資料並更新主資料"))
        database.withTransaction {
            dao.deactivateSpecies()
            dao.deactivateForms()
            dao.deactivateCostumes()
            dao.deactivateBackgrounds()
            dao.deactivateCostumeCompatibility()
            dao.deactivateBackgroundCompatibility()
            dao.upsertSpecies(species)
            dao.upsertForms(forms)
            dao.upsertCostumes(costumes)
            dao.upsertBackgrounds(backgrounds)
            dao.upsertCostumeCompatibility(costumeCompatibility)
            dao.upsertBackgroundCompatibility(backgroundCompatibility)
            dao.upsertMeta(
                MasterDataMetaEntity(
                    masterVersion = masterVersion,
                    generatedAt = generatedAt,
                    speciesCount = species.size,
                    formCount = forms.size,
                    costumeCount = costumes.size,
                    backgroundCount = backgrounds.size,
                    imageCount = imageCount,
                    missingImageCount = missingImageCount,
                    importedAt = System.currentTimeMillis(),
                ),
            )
        }
        onProgress(MasterDataUpdateProgress("更新完成", 1f, "主資料已安全更新"))
    }

    suspend fun checkAndImportUpdate(
        url: String,
        onProgress: (MasterDataUpdateProgress) -> Unit = {},
    ): MasterDataUpdateResult = withContext(Dispatchers.IO) {
        onProgress(MasterDataUpdateProgress("準備更新", 0f, "正在連線到主資料來源"))
        require(url.startsWith("https://")) { "主資料更新網址必須使用安全連線" }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/json")
        val json = try {
            require(connection.responseCode in 200..299) { "下載失敗（${connection.responseCode}）" }
            val length = connection.contentLengthLong
            require(length <= 20L * 1024L * 1024L || length < 0) { "主資料檔案超過 20 MB" }
            val downloaded = ByteArrayOutputStream(if (length > 0) length.toInt() else 16 * 1024)
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            connection.inputStream.use { input ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    downloaded.write(buffer, 0, count)
                    total += count
                    require(total <= 20L * 1024L * 1024L) { "主資料檔案超過 20 MB" }
                    val fraction = if (length > 0) (total.toFloat() / length.toFloat()).coerceIn(0f, 1f) else null
                    val detail = if (length > 0) {
                        "已下載 ${formatBytes(total)} / ${formatBytes(length)}"
                    } else {
                        "已下載 ${formatBytes(total)}"
                    }
                    onProgress(MasterDataUpdateProgress("下載主資料", fraction, detail))
                }
            }
            String(downloaded.toByteArray(), Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
        onProgress(MasterDataUpdateProgress("驗證主資料", 0.68f, "正在檢查版本與資料格式"))
        val root = JSONObject(json)
        val remoteVersion = root.optString("masterVersion", root.optString("version", "")).trim()
        require(remoteVersion.isNotBlank()) { "主資料缺少版本資訊" }
        require((root.optJSONArray("species")?.length() ?: 0) > 0) { "主資料缺少 Pokémon 清單" }
        val currentVersion = dao.getMeta()?.masterVersion
        if (currentVersion == remoteVersion) {
            MasterDataUpdateResult.AlreadyCurrent(remoteVersion)
        } else {
            importManifest(json, onProgress)
            MasterDataUpdateResult.Updated(currentVersion, remoteVersion)
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.1f MB".format(java.util.Locale.ROOT, bytes / (1024f * 1024f))
        bytes >= 1024L -> "%.0f KB".format(java.util.Locale.ROOT, bytes / 1024f)
        else -> "$bytes B"
    }

    private fun JSONArray?.toSpecies(): List<PokemonSpeciesEntity> = buildList {
        if (this@toSpecies == null) return@buildList
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                PokemonSpeciesEntity(
                    id = item.getString("id"),
                    dexNumber = item.getInt("dexNumber"),
                    speciesKey = item.getString("speciesKey"),
                    nameZhTw = item.optString("nameZhTw").ifBlank { item.getString("nameEn") },
                    nameEn = item.getString("nameEn"),
                    defaultImageKey = item.optString("defaultImageKey").ifBlank { null },
                    shinyImageKey = item.optString("shinyImageKey").ifBlank { null },
                    generation = item.optInt("generation").takeIf { it > 0 },
                    isActive = true,
                ),
            )
        }
    }

    private fun JSONArray?.toForms(): List<PokemonFormEntity> = buildList {
        if (this@toForms == null) return@buildList
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                PokemonFormEntity(
                    id = item.getString("id"),
                    speciesId = item.getString("speciesId"),
                    formKey = item.getString("formKey"),
                    displayName = item.getString("displayName"),
                    imageKey = item.optString("imageKey").ifBlank { null },
                    isDefault = item.optBoolean("isDefault", false),
                    sortOrder = item.optInt("sortOrder", if (item.optBoolean("isDefault", false)) 0 else 100),
                    isActive = true,
                ),
            )
        }
    }

    private fun JSONArray?.toCostumes(): List<CostumeEntity> = buildList {
        if (this@toCostumes == null) return@buildList
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                CostumeEntity(
                    id = item.getString("id"),
                    costumeKey = item.getString("costumeKey"),
                    displayName = item.getString("displayName"),
                    eventName = item.optString("eventName").ifBlank { null },
                    imageKey = item.optString("imageKey").ifBlank { null },
                    releaseYear = item.optInt("releaseYear").takeIf { it > 0 },
                    sortOrder = item.optInt("sortOrder", 0),
                    isActive = true,
                ),
            )
        }
    }

    private fun JSONArray?.toBackgrounds(): List<BackgroundEntity> = buildList {
        if (this@toBackgrounds == null) return@buildList
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                BackgroundEntity(
                    id = item.getString("id"),
                    backgroundKey = item.getString("backgroundKey"),
                    displayName = item.getString("displayName"),
                    backgroundType = BackgroundType.fromCode(item.optString("backgroundType")),
                    categoryKey = item.optString("categoryKey", "OTHER_SPECIAL"),
                    categoryName = item.optString("categoryName", "其他特殊背卡"),
                    eventKey = item.optString("eventKey", item.optString("categoryKey", "OTHER_SPECIAL")),
                    eventName = item.optString("eventName").ifBlank { null },
                    locationName = item.optString("locationName").ifBlank { null },
                    year = item.optInt("year").takeIf { it > 0 },
                    availableFrom = item.optString("availableFrom").ifBlank { null },
                    availableUntil = item.optString("availableUntil").ifBlank { null },
                    dataSource = item.optString("dataSource", "GAME_MASTER"),
                    imageKey = item.optString("imageKey").ifBlank { null },
                    vfxKey = item.optString("vfxKey").ifBlank { null },
                    vfxKeys = item.optString("vfxKeys").ifBlank { null },
                    effectNote = item.optString("effectNote").ifBlank { null },
                    previewImageKey = item.optString("previewImageKey").ifBlank { null },
                    previewSource = item.optString("previewSource").ifBlank { null },
                    sortOrder = item.optInt("sortOrder", 0),
                    isActive = true,
                ),
            )
        }
    }

    private fun JSONArray?.toCostumeCompatibility(): List<PokemonCostumeCompatibilityEntity> = buildList {
        if (this@toCostumeCompatibility == null) return@buildList
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            val speciesId = item.getString("speciesId")
            val formId = item.optString("formId").ifBlank { null }
            val costumeId = item.getString("costumeId")
            add(
                PokemonCostumeCompatibilityEntity(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    compatibilityKey = item.optString("compatibilityKey").ifBlank { "$speciesId|${formId ?: "ANY"}|$costumeId" },
                    speciesId = speciesId,
                    formId = formId,
                    costumeId = costumeId,
                    imageKey = item.optString("imageKey").ifBlank { null },
                    isVerified = item.optBoolean("isVerified", true),
                    isActive = true,
                ),
            )
        }
    }

    private fun JSONArray?.toBackgroundCompatibility(): List<PokemonBackgroundCompatibilityEntity> = buildList {
        if (this@toBackgroundCompatibility == null) return@buildList
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            val backgroundId = item.getString("backgroundId")
            val speciesId = item.optString("speciesId").ifBlank { null }
            val formId = item.optString("formId").ifBlank { null }
            add(
                PokemonBackgroundCompatibilityEntity(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    compatibilityKey = item.optString("compatibilityKey").ifBlank { "$backgroundId|${speciesId ?: "ALL"}|${formId ?: "ANY"}" },
                    backgroundId = backgroundId,
                    speciesId = speciesId,
                    formId = formId,
                    isVerified = item.optBoolean("isVerified", false),
                    isActive = true,
                ),
            )
        }
    }

    private fun countImageKeys(
        species: List<PokemonSpeciesEntity>,
        forms: List<PokemonFormEntity>,
        costumes: List<CostumeEntity>,
        backgrounds: List<BackgroundEntity>,
    ): Int = (species.mapNotNull { it.defaultImageKey } + forms.mapNotNull { it.imageKey } + costumes.mapNotNull { it.imageKey } + backgrounds.flatMap { listOfNotNull(it.imageKey, it.previewImageKey) }).distinct().size
}

