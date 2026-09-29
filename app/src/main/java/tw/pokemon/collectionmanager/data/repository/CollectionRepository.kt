package tw.pokemon.collectionmanager.data.repository

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import tw.pokemon.collectionmanager.data.local.AccountDao
import tw.pokemon.collectionmanager.data.local.AccountEntity
import tw.pokemon.collectionmanager.data.local.AccountGroupDao
import tw.pokemon.collectionmanager.data.local.AccountGroupEntity
import tw.pokemon.collectionmanager.data.local.AccountSummaryRow
import tw.pokemon.collectionmanager.data.local.CollectionDatabase
import tw.pokemon.collectionmanager.data.local.CollectionVariantEntity
import tw.pokemon.collectionmanager.data.local.MasterDataDao
import tw.pokemon.collectionmanager.data.local.OwnershipBucketDao
import tw.pokemon.collectionmanager.data.local.OwnershipBucketEntity
import tw.pokemon.collectionmanager.data.local.VariantCardRow
import tw.pokemon.collectionmanager.data.local.VariantDao
import tw.pokemon.collectionmanager.data.local.VariantInfoRow
import tw.pokemon.collectionmanager.data.local.SourceAccountRow
import tw.pokemon.collectionmanager.domain.BucketDraft
import tw.pokemon.collectionmanager.domain.DeleteAccountSummary
import tw.pokemon.collectionmanager.domain.OwnershipRules
import tw.pokemon.collectionmanager.domain.VariantDraft
import tw.pokemon.collectionmanager.domain.VariantKeyFactory
import java.util.UUID

class CollectionRepository(private val database: CollectionDatabase) {
    private val groups: AccountGroupDao = database.accountGroupDao()
    private val accounts: AccountDao = database.accountDao()
    private val master: MasterDataDao = database.masterDataDao()
    private val variants: VariantDao = database.variantDao()
    private val buckets: OwnershipBucketDao = database.ownershipBucketDao()

    val accountSummaries: Flow<List<AccountSummaryRow>> = accounts.observeSummaries().recoverToEmpty("帳號摘要")
    val accountGroups = groups.observeAll().recoverToEmpty("帳號群組")
    val species = master.observeSpecies().recoverToEmpty("寶可夢主資料")
    val costumes = master.observeCostumes().recoverToEmpty("裝扮主資料")
    val backgrounds = master.observeBackgrounds().recoverToEmpty("背景主資料")

    fun forms(speciesId: String) = master.observeForms(speciesId).recoverToEmpty("型態主資料")
    fun account(id: String) = accounts.observeById(id).recoverToNull("帳號")
    fun variantsForAccount(accountId: String): Flow<List<VariantCardRow>> = variants.observeForAccount(accountId).recoverToEmpty("帳號收藏")
    fun variantsForAllAccounts(): Flow<List<VariantCardRow>> = variants.observeForAllAccounts().recoverToEmpty("全部收藏")
    fun variantsForAccounts(accountIds: List<String>): Flow<List<VariantCardRow>> = variants.observeForAccounts(accountIds).recoverToEmpty("選取帳號收藏")
    fun variantInfo(variantId: String): Flow<VariantInfoRow?> = variants.observeInfo(variantId).recoverToNull("收藏組合")
    fun variantSources(variantId: String): Flow<List<SourceAccountRow>> = variants.observeSources(variantId).recoverToEmpty("收藏來源帳號")
    fun bucketsForVariant(accountId: String, variantId: String) = buckets.observeForVariant(accountId, variantId).recoverToEmpty("收藏數量")

    suspend fun createGroup(name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty()) groups.upsert(AccountGroupEntity(UUID.randomUUID().toString(), clean))
    }

    suspend fun deleteGroup(id: String) = groups.deleteById(id)

    suspend fun createAccount(name: String, nickname: String?, groupId: String?) {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "帳號名稱不可為空白" }
        val now = System.currentTimeMillis()
        accounts.upsert(
            AccountEntity(
                id = UUID.randomUUID().toString(),
                name = clean,
                nickname = nickname?.trim()?.ifBlank { null },
                groupId = groupId,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun updateAccount(account: AccountEntity) {
        require(account.name.trim().isNotEmpty()) { "帳號名稱不可為空白" }
        accounts.upsert(account.copy(name = account.name.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateAccountFields(id: String, name: String, nickname: String?, groupId: String?) {
        require(name.trim().isNotEmpty()) { "帳號名稱不可為空白" }
        accounts.updateFields(id, name.trim(), nickname?.trim()?.ifBlank { null }, groupId, System.currentTimeMillis())
    }

    suspend fun setArchived(accountId: String, archived: Boolean) {
        accounts.getById(accountId)?.let { updateAccount(it.copy(isArchived = archived)) }
    }

    suspend fun deleteAccount(accountId: String) = accounts.deleteById(accountId)

    suspend fun accountDeleteSummary(accountId: String): DeleteAccountSummary {
        val summary = accountSummariesSnapshot().firstOrNull { it.id == accountId }
        return DeleteAccountSummary(summary?.variantCount ?: 0, summary?.pokemonCount ?: 0)
    }

    private suspend fun accountSummariesSnapshot(): List<AccountSummaryRow> = database.withTransaction {
        accounts.observeSummaries().first()
    }

    suspend fun addOwnership(accountId: String, variantDraft: VariantDraft, bucketDraft: BucketDraft, quantity: Int) {
        require(quantity > 0) { "數量必須大於 0" }
        database.withTransaction {
            validateAccountAndVariantDraft(accountId, variantDraft)
            val now = System.currentTimeMillis()
            val variantKey = VariantKeyFactory.build(variantDraft)
            val variant = variants.findByKey(variantKey) ?: CollectionVariantEntity(
                id = UUID.randomUUID().toString(),
                speciesId = variantDraft.speciesId,
                formId = variantDraft.formId,
                costumeId = variantDraft.costumeId,
                backgroundId = variantDraft.backgroundId,
                isShiny = variantDraft.isShiny,
                gender = variantDraft.gender,
                shadowState = variantDraft.shadowState,
                dynamaxState = variantDraft.dynamaxState,
                variantKey = variantKey,
                createdAt = now,
                updatedAt = now,
            )
            variants.upsert(variant.copy(updatedAt = now))
            val existing = buckets.find(accountId, variant.id, bucketDraft.sizeType, bucketDraft.hasSpecialMove, bucketDraft.tradeState)
            val nextQuantity = OwnershipRules.mergeQuantity(existing?.quantity ?: 0, quantity)
            buckets.upsert(
                existing?.copy(quantity = nextQuantity, updatedAt = now)
                    ?: OwnershipBucketEntity(
                        id = UUID.randomUUID().toString(),
                        accountId = accountId,
                        variantId = variant.id,
                        sizeType = bucketDraft.sizeType,
                        hasSpecialMove = bucketDraft.hasSpecialMove,
                        tradeState = bucketDraft.tradeState,
                        quantity = nextQuantity,
                        createdAt = now,
                        updatedAt = now,
                    ),
            )
        }
    }

    suspend fun updateVariant(variantId: String, draft: VariantDraft) {
        database.withTransaction {
            val current = variants.getById(variantId) ?: return@withTransaction
            validateVariantDraft(draft)
            val newKey = VariantKeyFactory.build(draft)
            val collision = variants.findByKey(newKey)
            if (collision != null && collision.id != current.id) {
                mergeVariantBuckets(current.id, collision.id)
                variants.deleteById(current.id)
            } else {
                variants.upsert(
                    current.copy(
                        speciesId = draft.speciesId,
                        formId = draft.formId,
                        costumeId = draft.costumeId,
                        backgroundId = draft.backgroundId,
                        isShiny = draft.isShiny,
                        gender = draft.gender,
                        shadowState = draft.shadowState,
                        dynamaxState = draft.dynamaxState,
                        variantKey = newKey,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    /**
     * 編輯單一帳號中的收藏組合時，不能直接改寫共用的 CollectionVariant。
     * 若同一版本同時被其他帳號使用，先把目前帳號的數量搬到新版本，
     * 讓其他帳號仍保留原本的版本。
     */
    suspend fun updateVariantForAccount(accountId: String, variantId: String, draft: VariantDraft) {
        database.withTransaction {
            require(accounts.getById(accountId) != null) { "找不到要編輯的帳號" }
            val current = variants.getById(variantId) ?: return@withTransaction
            validateVariantDraft(draft)
            val sourceBuckets = buckets.getForVariant(accountId, variantId).filter { it.quantity > 0 }
            require(sourceBuckets.isNotEmpty()) { "此帳號沒有這個收藏組合的數量" }

            val newKey = VariantKeyFactory.build(draft)
            val collision = variants.findByKey(newKey)?.takeUnless { it.id == current.id }
            val currentBuckets = buckets.getAll().filter { it.variantId == current.id && it.quantity > 0 }

            if (collision == null && currentBuckets.all { it.accountId == accountId }) {
                variants.upsert(current.withDraft(draft, newKey, System.currentTimeMillis()))
                return@withTransaction
            }

            val destination = collision ?: CollectionVariantEntity(
                id = UUID.randomUUID().toString(),
                speciesId = draft.speciesId,
                formId = draft.formId,
                costumeId = draft.costumeId,
                backgroundId = draft.backgroundId,
                isShiny = draft.isShiny,
                gender = draft.gender,
                shadowState = draft.shadowState,
                dynamaxState = draft.dynamaxState,
                variantKey = newKey,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            variants.upsert(destination)
            sourceBuckets.forEach { sourceBucket ->
                val targetBucket = buckets.find(
                    accountId = accountId,
                    variantId = destination.id,
                    sizeType = sourceBucket.sizeType,
                    hasSpecialMove = sourceBucket.hasSpecialMove,
                    tradeState = sourceBucket.tradeState,
                )
                if (targetBucket == null) {
                    buckets.upsert(
                        sourceBucket.copy(
                            id = UUID.randomUUID().toString(),
                            variantId = destination.id,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                } else {
                    buckets.upsert(
                        targetBucket.copy(
                            quantity = OwnershipRules.mergeQuantity(targetBucket.quantity, sourceBucket.quantity),
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
                buckets.deleteById(sourceBucket.id)
            }
            if (buckets.getAll().none { it.variantId == current.id && it.quantity > 0 }) {
                variants.deleteById(current.id)
            }
        }
    }

    private suspend fun mergeVariantBuckets(fromVariantId: String, toVariantId: String) {
        val now = System.currentTimeMillis()
        val source = buckets.getAll().filter { it.variantId == fromVariantId }
        source.forEach { sourceBucket ->
            val target = buckets.find(sourceBucket.accountId, toVariantId, sourceBucket.sizeType, sourceBucket.hasSpecialMove, sourceBucket.tradeState)
            buckets.upsert(
                target?.copy(quantity = target.quantity + sourceBucket.quantity, updatedAt = now)
                    ?: sourceBucket.copy(id = UUID.randomUUID().toString(), variantId = toVariantId, createdAt = now, updatedAt = now),
            )
        }
        buckets.deleteForVariant(fromVariantId)
    }

    suspend fun updateBucket(bucket: OwnershipBucketEntity, newQuantity: Int) {
        database.withTransaction {
            if (newQuantity <= 0) buckets.deleteById(bucket.id)
            else buckets.upsert(bucket.copy(quantity = newQuantity, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteVariant(variantId: String) = variants.deleteById(variantId)

    suspend fun allAccounts(): List<AccountEntity> = accounts.getAll()
    suspend fun allGroups(): List<AccountGroupEntity> = groups.getAll()
    suspend fun allVariants(): List<CollectionVariantEntity> = variants.getAll()
    suspend fun allBuckets(): List<OwnershipBucketEntity> = buckets.getAll()
    suspend fun allSpecies() = master.getAllSpecies()
    suspend fun allForms() = master.getAllForms()
    suspend fun allCostumes() = master.getAllCostumes()
    suspend fun allBackgrounds() = master.getAllBackgrounds()

    suspend fun restore(
        groupsToRestore: List<AccountGroupEntity>,
        accountsToRestore: List<AccountEntity>,
        variantsToRestore: List<CollectionVariantEntity>,
        bucketsToRestore: List<OwnershipBucketEntity>,
    ) {
        database.withTransaction {
            groups.upsertAll(groupsToRestore)
            accounts.upsertAll(accountsToRestore)
            variants.upsertAll(variantsToRestore)
            buckets.upsertAll(bucketsToRestore.filter { it.quantity > 0 })
        }
    }

    private suspend fun validateAccountAndVariantDraft(accountId: String, draft: VariantDraft) {
        require(accounts.getById(accountId) != null) { "找不到要加入收藏的帳號" }
        validateVariantDraft(draft)
    }

    private suspend fun validateVariantDraft(draft: VariantDraft) {
        val species = master.getSpecies(draft.speciesId)
        require(species != null) { "找不到 Pokémon 主資料，請重新選擇" }
        val form = master.getForm(draft.formId)
        require(form != null && form.speciesId == species.id) { "型態與 Pokémon 不相符，請重新選擇" }
        require(master.getCostume(draft.costumeId) != null) { "找不到裝扮主資料，請重新選擇" }
        require(master.getBackground(draft.backgroundId) != null) { "找不到背景主資料，請重新選擇" }
    }

    private fun CollectionVariantEntity.withDraft(
        draft: VariantDraft,
        variantKey: String,
        updatedAt: Long,
    ): CollectionVariantEntity = copy(
        speciesId = draft.speciesId,
        formId = draft.formId,
        costumeId = draft.costumeId,
        backgroundId = draft.backgroundId,
        isShiny = draft.isShiny,
        gender = draft.gender,
        shadowState = draft.shadowState,
        dynamaxState = draft.dynamaxState,
        variantKey = variantKey,
        updatedAt = updatedAt,
    )
}

private fun <T> Flow<List<T>>.recoverToEmpty(label: String): Flow<List<T>> = catch { error ->
    Log.e("收藏資料", "$label 查詢失敗，暫時顯示空清單", error)
    emit(emptyList())
}

private fun <T> Flow<T?>.recoverToNull(label: String): Flow<T?> = catch { error ->
    Log.e("收藏資料", "$label 查詢失敗，暫時顯示空資料", error)
    emit(null)
}

