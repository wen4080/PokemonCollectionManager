package tw.pokemon.collectionmanager.data.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tw.pokemon.collectionmanager.data.local.AccountEntity
import tw.pokemon.collectionmanager.data.local.AccountGroupEntity
import tw.pokemon.collectionmanager.data.local.CollectionVariantEntity
import tw.pokemon.collectionmanager.data.local.CollectionTagEntity
import tw.pokemon.collectionmanager.data.local.CollectionVariantTagEntity
import tw.pokemon.collectionmanager.data.local.DynamaxState
import tw.pokemon.collectionmanager.data.local.Gender
import tw.pokemon.collectionmanager.data.local.OwnershipBucketEntity
import tw.pokemon.collectionmanager.data.local.ShadowState
import tw.pokemon.collectionmanager.data.local.SizeType
import tw.pokemon.collectionmanager.data.local.TradeState
import tw.pokemon.collectionmanager.data.repository.CollectionRepository

class BackupManager(private val context: Context) {
    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("無法開啟檔案")
    }

    suspend fun exportToUri(uri: Uri, repository: CollectionRepository) = withContext(Dispatchers.IO) {
        val json = exportJson(repository)
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
            ?: error("無法開啟備份目的地")
    }

    suspend fun importFromUri(uri: Uri, repository: CollectionRepository) = withContext(Dispatchers.IO) {
        val json = readText(uri)
        importJson(json, repository)
    }

    suspend fun exportJson(repository: CollectionRepository): String {
        val root = JSONObject()
            .put("backupVersion", 2)
            .put("databaseVersion", 2)
            .put("createdAt", System.currentTimeMillis())
            .put("app", "PokemonCollectionManager")

        root.put("accountGroups", JSONArray().apply {
            repository.allGroups().forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("sortOrder", it.sortOrder)) }
        })
        root.put("accounts", JSONArray().apply {
            repository.allAccounts().forEach {
                put(JSONObject().put("id", it.id).put("name", it.name).putNullable("nickname", it.nickname)
                    .putNullable("groupId", it.groupId).put("sortOrder", it.sortOrder).put("isArchived", it.isArchived)
                    .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt))
            }
        })
        root.put("collectionVariants", JSONArray().apply {
            repository.allVariants().forEach {
                put(JSONObject().put("id", it.id).put("speciesId", it.speciesId).put("formId", it.formId)
                    .put("costumeId", it.costumeId).put("backgroundId", it.backgroundId).put("isShiny", it.isShiny)
                    .put("gender", it.gender.code).put("shadowState", it.shadowState.code).put("dynamaxState", it.dynamaxState.code)
                    .put("variantKey", it.variantKey).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt))
            }
        })
        root.put("ownershipBuckets", JSONArray().apply {
            repository.allBuckets().forEach {
                put(JSONObject().put("id", it.id).put("accountId", it.accountId).put("variantId", it.variantId)
                    .put("sizeType", it.sizeType.code).put("hasSpecialMove", it.hasSpecialMove).put("tradeState", it.tradeState.code)
                    .put("quantity", it.quantity).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt))
            }
        })
        root.put("collectionTags", JSONArray().apply {
            repository.allTags().forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("name", it.name)
                        .putNullable("description", it.description)
                        .put("sortOrder", it.sortOrder)
                        .put("createdAt", it.createdAt)
                        .put("updatedAt", it.updatedAt),
                )
            }
        })
        root.put("collectionVariantTags", JSONArray().apply {
            repository.allTagAssignments().forEach {
                put(JSONObject().put("tagId", it.tagId).put("variantId", it.variantId).put("createdAt", it.createdAt))
            }
        })
        return root.toString(2)
    }

    suspend fun importJson(json: String, repository: CollectionRepository) {
        val root = JSONObject(json)
        val backupVersion = root.optInt("backupVersion", 1)
        require(backupVersion in 1..2) { "不支援的備份版本" }
        val groups = root.requiredArray("accountGroups").toGroups()
        val accounts = root.requiredArray("accounts").toAccounts()
        val variants = root.requiredArray("collectionVariants").toVariants()
        val buckets = root.requiredArray("ownershipBuckets").toBuckets()
        val tags = root.optJSONArray("collectionTags")?.toTags().orEmpty()
        val tagAssignments = root.optJSONArray("collectionVariantTags")?.toTagAssignments().orEmpty()
        require(accounts.all { account -> groups.any { it.id == account.groupId } || account.groupId == null }) { "備份包含不存在的帳號群組" }
        require(buckets.all { bucket -> accounts.any { it.id == bucket.accountId } && variants.any { it.id == bucket.variantId } }) {
            "備份包含找不到來源的收藏數量"
        }
        repository.restore(groups, accounts, variants, buckets, tags, tagAssignments)
    }

    private fun JSONObject.putNullable(key: String, value: String?): JSONObject = put(key, value ?: JSONObject.NULL)

    private fun JSONObject.requiredArray(key: String): JSONArray = optJSONArray(key) ?: error("備份缺少必要資料：$key")

    private fun JSONArray.toGroups(): List<AccountGroupEntity> = buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(AccountGroupEntity(item.getString("id"), item.getString("name"), item.optInt("sortOrder", 0)))
        }
    }

    private fun JSONArray.toAccounts(): List<AccountEntity> = buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                AccountEntity(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    nickname = item.stringOrNull("nickname"),
                    groupId = item.stringOrNull("groupId"),
                    sortOrder = item.optInt("sortOrder", 0),
                    isArchived = item.optBoolean("isArchived", false),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                ),
            )
        }
    }

    private fun JSONArray.toVariants(): List<CollectionVariantEntity> = buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                CollectionVariantEntity(
                    id = item.getString("id"),
                    speciesId = item.getString("speciesId"),
                    formId = item.getString("formId"),
                    costumeId = item.getString("costumeId"),
                    backgroundId = item.getString("backgroundId"),
                    isShiny = item.optBoolean("isShiny", false),
                    gender = Gender.fromCode(item.optString("gender")),
                    shadowState = ShadowState.fromCode(item.optString("shadowState")),
                    dynamaxState = DynamaxState.fromCode(item.optString("dynamaxState")),
                    variantKey = item.getString("variantKey"),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                ),
            )
        }
    }

    private fun JSONArray.toBuckets(): List<OwnershipBucketEntity> = buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            val quantity = item.optInt("quantity", 0)
            if (quantity > 0) {
                add(
                    OwnershipBucketEntity(
                        id = item.getString("id"),
                        accountId = item.getString("accountId"),
                        variantId = item.getString("variantId"),
                        sizeType = SizeType.fromCode(item.optString("sizeType")),
                        hasSpecialMove = item.optBoolean("hasSpecialMove", false),
                        tradeState = TradeState.fromCode(item.optString("tradeState")),
                        quantity = quantity,
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                    ),
                )
            }
        }
    }

    private fun JSONArray.toTags(): List<CollectionTagEntity> = buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                CollectionTagEntity(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    description = item.stringOrNull("description"),
                    sortOrder = item.optInt("sortOrder", 0),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                ),
            )
        }
    }

    private fun JSONArray.toTagAssignments(): List<CollectionVariantTagEntity> = buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                CollectionVariantTagEntity(
                    tagId = item.getString("tagId"),
                    variantId = item.getString("variantId"),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                ),
            )
        }
    }

    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }
}

