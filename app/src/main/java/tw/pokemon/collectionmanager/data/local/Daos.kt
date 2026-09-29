package tw.pokemon.collectionmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountGroupDao {
    @Query("SELECT * FROM account_groups ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<AccountGroupEntity>>

    @Query("SELECT * FROM account_groups ORDER BY sortOrder ASC, name ASC")
    suspend fun getAll(): List<AccountGroupEntity>

    @Upsert
    suspend fun upsert(group: AccountGroupEntity)

    @Upsert
    suspend fun upsertAll(groups: List<AccountGroupEntity>)

    @Query("DELETE FROM account_groups WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface AccountDao {
    @Query(
        """
        SELECT a.id AS id,
               a.name AS name,
               a.nickname AS nickname,
               a.groupId AS groupId,
               g.name AS groupName,
               a.isArchived AS isArchived,
               CAST(COUNT(DISTINCT ob.variantId) AS INTEGER) AS variantCount,
               COALESCE(SUM(ob.quantity), 0) AS pokemonCount
        FROM accounts a
        LEFT JOIN account_groups g ON g.id = a.groupId
        LEFT JOIN ownership_buckets ob ON ob.accountId = a.id AND ob.quantity > 0
        GROUP BY a.id
        ORDER BY a.isArchived ASC, a.sortOrder ASC, a.createdAt ASC
        """,
    )
    fun observeSummaries(): Flow<List<AccountSummaryRow>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<AccountEntity?>

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Query("UPDATE accounts SET name = :name, nickname = :nickname, groupId = :groupId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFields(id: String, name: String, nickname: String?, groupId: String?, updatedAt: Long)

    @Upsert
    suspend fun upsertAll(accounts: List<AccountEntity>)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface MasterDataDao {
    @Query("SELECT * FROM pokemon_species WHERE isActive = 1 ORDER BY dexNumber ASC, nameZhTw ASC")
    fun observeSpecies(): Flow<List<PokemonSpeciesEntity>>

    @Query("SELECT * FROM pokemon_species WHERE isActive = 1 ORDER BY dexNumber ASC, nameZhTw ASC")
    suspend fun getAllSpecies(): List<PokemonSpeciesEntity>

    @Query("SELECT * FROM pokemon_species WHERE isActive = 1 AND (:query = '' OR nameZhTw LIKE '%' || :query || '%' OR nameEn LIKE '%' || :query || '%' OR CAST(dexNumber AS TEXT) LIKE '%' || :query || '%') AND (:generation IS NULL OR generation = :generation) ORDER BY dexNumber ASC, nameZhTw ASC")
    fun observeSpeciesPicker(query: String, generation: Int?): Flow<List<PokemonSpeciesEntity>>

    @Query("SELECT * FROM pokemon_species WHERE id = :speciesId LIMIT 1")
    suspend fun getSpecies(speciesId: String): PokemonSpeciesEntity?

    @Query("SELECT * FROM pokemon_forms WHERE speciesId = :speciesId AND isActive = 1 ORDER BY isDefault DESC, sortOrder ASC, displayName ASC")
    fun observeForms(speciesId: String): Flow<List<PokemonFormEntity>>

    @Query("SELECT * FROM pokemon_forms WHERE isActive = 1 ORDER BY speciesId ASC, isDefault DESC, sortOrder ASC, displayName ASC")
    suspend fun getAllForms(): List<PokemonFormEntity>

    @Query("SELECT * FROM pokemon_forms WHERE id = :formId LIMIT 1")
    suspend fun getForm(formId: String): PokemonFormEntity?

    @Query("SELECT * FROM costumes WHERE isActive = 1 ORDER BY CASE WHEN costumeKey = 'NONE' THEN 0 ELSE 1 END, releaseYear DESC, sortOrder ASC, displayName ASC")
    fun observeCostumes(): Flow<List<CostumeEntity>>

    @Query("SELECT * FROM costumes WHERE isActive = 1 ORDER BY CASE WHEN costumeKey = 'NONE' THEN 0 ELSE 1 END, releaseYear DESC, sortOrder ASC, displayName ASC")
    suspend fun getAllCostumes(): List<CostumeEntity>

    @Query("SELECT * FROM costumes WHERE id = :costumeId LIMIT 1")
    suspend fun getCostume(costumeId: String): CostumeEntity?

    @Query("SELECT * FROM backgrounds WHERE isActive = 1 ORDER BY sortOrder ASC, displayName ASC")
    fun observeBackgrounds(): Flow<List<BackgroundEntity>>

    @Query("SELECT * FROM backgrounds WHERE isActive = 1 ORDER BY sortOrder ASC, displayName ASC")
    suspend fun getAllBackgrounds(): List<BackgroundEntity>

    @Query("SELECT * FROM backgrounds WHERE id = :backgroundId LIMIT 1")
    suspend fun getBackground(backgroundId: String): BackgroundEntity?

    @Query("SELECT * FROM pokemon_costume_compatibilities WHERE speciesId = :speciesId AND isActive = 1 AND (formId IS NULL OR formId = :formId) ORDER BY isVerified DESC, costumeId ASC")
    fun observeCostumeCompatibility(speciesId: String, formId: String): Flow<List<PokemonCostumeCompatibilityEntity>>

    @Query("SELECT imageKey FROM pokemon_costume_compatibilities WHERE speciesId = :speciesId AND costumeId = :costumeId AND isActive = 1 AND (formId IS NULL OR formId = :formId) ORDER BY formId IS NULL ASC LIMIT 1")
    suspend fun findCostumeImageKey(speciesId: String, formId: String, costumeId: String): String?

    @Query("SELECT * FROM pokemon_background_compatibilities WHERE isActive = 1 AND (speciesId IS NULL OR speciesId = :speciesId) AND (formId IS NULL OR formId = :formId) ORDER BY isVerified DESC, backgroundId ASC")
    fun observeBackgroundCompatibility(speciesId: String, formId: String): Flow<List<PokemonBackgroundCompatibilityEntity>>

    @Query("SELECT * FROM master_data_meta WHERE id = 'current' LIMIT 1")
    fun observeMeta(): Flow<MasterDataMetaEntity?>

    @Query("SELECT * FROM master_data_meta WHERE id = 'current' LIMIT 1")
    suspend fun getMeta(): MasterDataMetaEntity?

    @Query("SELECT COUNT(*) FROM pokemon_species WHERE isActive = 1")
    suspend fun speciesCount(): Int

    @Query("UPDATE pokemon_species SET isActive = 0")
    suspend fun deactivateSpecies()

    @Query("UPDATE pokemon_forms SET isActive = 0")
    suspend fun deactivateForms()

    @Query("UPDATE costumes SET isActive = 0")
    suspend fun deactivateCostumes()

    @Query("UPDATE backgrounds SET isActive = 0")
    suspend fun deactivateBackgrounds()

    @Query("UPDATE pokemon_costume_compatibilities SET isActive = 0")
    suspend fun deactivateCostumeCompatibility()

    @Query("UPDATE pokemon_background_compatibilities SET isActive = 0")
    suspend fun deactivateBackgroundCompatibility()

    @Upsert
    suspend fun upsertSpecies(items: List<PokemonSpeciesEntity>)

    @Upsert
    suspend fun upsertForms(items: List<PokemonFormEntity>)

    @Upsert
    suspend fun upsertCostumes(items: List<CostumeEntity>)

    @Upsert
    suspend fun upsertBackgrounds(items: List<BackgroundEntity>)

    @Upsert
    suspend fun upsertCostumeCompatibility(items: List<PokemonCostumeCompatibilityEntity>)

    @Upsert
    suspend fun upsertBackgroundCompatibility(items: List<PokemonBackgroundCompatibilityEntity>)

    @Upsert
    suspend fun upsertMeta(meta: MasterDataMetaEntity)
}

@Dao
interface VariantDao {
    @Query(
        """
        SELECT cv.id AS variantId,
               cv.speciesId AS speciesId,
               cv.formId AS formId,
               cv.costumeId AS costumeId,
               COALESCE(s.nameZhTw, cv.speciesId) AS speciesName,
               COALESCE(s.dexNumber, 0) AS dexNumber,
               COALESCE(f.displayName, cv.formId) AS formName,
               COALESCE(c.displayName, cv.costumeId) AS costumeName,
               COALESCE(b.displayName, cv.backgroundId) AS backgroundName,
               COALESCE(b.categoryName, cv.backgroundId) AS backgroundCategoryName,
               cv.backgroundId AS backgroundId,
               cv.isShiny AS isShiny,
               cv.gender AS gender,
               cv.shadowState AS shadowState,
               cv.dynamaxState AS dynamaxState,
               COALESCE(SUM(ob.quantity), 0) AS totalQuantity,
               COALESCE(SUM(CASE WHEN ob.sizeType = 'XXL' THEN ob.quantity ELSE 0 END), 0) AS xxlQuantity,
               COALESCE(SUM(CASE WHEN ob.sizeType = 'XXS' THEN ob.quantity ELSE 0 END), 0) AS xxsQuantity,
               COALESCE(SUM(CASE WHEN ob.hasSpecialMove = 1 THEN ob.quantity ELSE 0 END), 0) AS specialMoveQuantity,
               COALESCE(SUM(CASE WHEN ob.tradeState = 'UNTRADED' THEN ob.quantity ELSE 0 END), 0) AS untradedQuantity,
               COALESCE(SUM(CASE WHEN ob.tradeState = 'TRADED' THEN ob.quantity ELSE 0 END), 0) AS tradedQuantity
        FROM collection_variants cv
        LEFT JOIN pokemon_species s ON s.id = cv.speciesId
        LEFT JOIN pokemon_forms f ON f.id = cv.formId
        LEFT JOIN costumes c ON c.id = cv.costumeId
        LEFT JOIN backgrounds b ON b.id = cv.backgroundId
        JOIN ownership_buckets ob ON ob.variantId = cv.id
        WHERE ob.accountId = :accountId AND ob.quantity > 0
        GROUP BY cv.id
        ORDER BY cv.updatedAt DESC, s.dexNumber ASC
        """,
    )
    fun observeForAccount(accountId: String): Flow<List<VariantCardRow>>

    @Query(
        """
        SELECT cv.id AS variantId,
               cv.speciesId AS speciesId,
               cv.formId AS formId,
               cv.costumeId AS costumeId,
               COALESCE(s.nameZhTw, cv.speciesId) AS speciesName,
               COALESCE(s.dexNumber, 0) AS dexNumber,
               COALESCE(f.displayName, cv.formId) AS formName,
               COALESCE(c.displayName, cv.costumeId) AS costumeName,
               COALESCE(b.displayName, cv.backgroundId) AS backgroundName,
               COALESCE(b.categoryName, cv.backgroundId) AS backgroundCategoryName,
               cv.backgroundId AS backgroundId,
               cv.isShiny AS isShiny,
               cv.gender AS gender,
               cv.shadowState AS shadowState,
               cv.dynamaxState AS dynamaxState,
               COALESCE(SUM(ob.quantity), 0) AS totalQuantity,
               COALESCE(SUM(CASE WHEN ob.sizeType = 'XXL' THEN ob.quantity ELSE 0 END), 0) AS xxlQuantity,
               COALESCE(SUM(CASE WHEN ob.sizeType = 'XXS' THEN ob.quantity ELSE 0 END), 0) AS xxsQuantity,
               COALESCE(SUM(CASE WHEN ob.hasSpecialMove = 1 THEN ob.quantity ELSE 0 END), 0) AS specialMoveQuantity,
               COALESCE(SUM(CASE WHEN ob.tradeState = 'UNTRADED' THEN ob.quantity ELSE 0 END), 0) AS untradedQuantity,
               COALESCE(SUM(CASE WHEN ob.tradeState = 'TRADED' THEN ob.quantity ELSE 0 END), 0) AS tradedQuantity
        FROM collection_variants cv
        LEFT JOIN pokemon_species s ON s.id = cv.speciesId
        LEFT JOIN pokemon_forms f ON f.id = cv.formId
        LEFT JOIN costumes c ON c.id = cv.costumeId
        LEFT JOIN backgrounds b ON b.id = cv.backgroundId
        JOIN ownership_buckets ob ON ob.variantId = cv.id
        JOIN accounts a ON a.id = ob.accountId
        WHERE a.isArchived = 0 AND ob.quantity > 0
        GROUP BY cv.id
        ORDER BY cv.updatedAt DESC, s.dexNumber ASC
        """,
    )
    fun observeForAllAccounts(): Flow<List<VariantCardRow>>

    @Query(
        """
        SELECT cv.id AS variantId,
               cv.speciesId AS speciesId,
               cv.formId AS formId,
               cv.costumeId AS costumeId,
               COALESCE(s.nameZhTw, cv.speciesId) AS speciesName,
               COALESCE(s.dexNumber, 0) AS dexNumber,
               COALESCE(f.displayName, cv.formId) AS formName,
               COALESCE(c.displayName, cv.costumeId) AS costumeName,
               COALESCE(b.displayName, cv.backgroundId) AS backgroundName,
               COALESCE(b.categoryName, cv.backgroundId) AS backgroundCategoryName,
               cv.backgroundId AS backgroundId,
               cv.isShiny AS isShiny,
               cv.gender AS gender,
               cv.shadowState AS shadowState,
               cv.dynamaxState AS dynamaxState,
               COALESCE(SUM(ob.quantity), 0) AS totalQuantity,
               COALESCE(SUM(CASE WHEN ob.sizeType = 'XXL' THEN ob.quantity ELSE 0 END), 0) AS xxlQuantity,
               COALESCE(SUM(CASE WHEN ob.sizeType = 'XXS' THEN ob.quantity ELSE 0 END), 0) AS xxsQuantity,
               COALESCE(SUM(CASE WHEN ob.hasSpecialMove = 1 THEN ob.quantity ELSE 0 END), 0) AS specialMoveQuantity,
               COALESCE(SUM(CASE WHEN ob.tradeState = 'UNTRADED' THEN ob.quantity ELSE 0 END), 0) AS untradedQuantity,
               COALESCE(SUM(CASE WHEN ob.tradeState = 'TRADED' THEN ob.quantity ELSE 0 END), 0) AS tradedQuantity
        FROM collection_variants cv
        LEFT JOIN pokemon_species s ON s.id = cv.speciesId
        LEFT JOIN pokemon_forms f ON f.id = cv.formId
        LEFT JOIN costumes c ON c.id = cv.costumeId
        LEFT JOIN backgrounds b ON b.id = cv.backgroundId
        JOIN ownership_buckets ob ON ob.variantId = cv.id
        WHERE ob.accountId IN (:accountIds) AND ob.quantity > 0
        GROUP BY cv.id
        ORDER BY cv.updatedAt DESC, s.dexNumber ASC
        """,
    )
    fun observeForAccounts(accountIds: List<String>): Flow<List<VariantCardRow>>

    @Query(
        """
        SELECT cv.id AS variantId,
               cv.speciesId AS speciesId,
               COALESCE(s.nameZhTw, cv.speciesId) AS speciesName,
               COALESCE(s.dexNumber, 0) AS dexNumber,
               cv.formId AS formId,
               COALESCE(f.displayName, cv.formId) AS formName,
               cv.costumeId AS costumeId,
               COALESCE(c.displayName, cv.costumeId) AS costumeName,
               COALESCE(b.displayName, cv.backgroundId) AS backgroundName,
               COALESCE(b.categoryName, cv.backgroundId) AS backgroundCategoryName,
               cv.backgroundId AS backgroundId,
               cv.isShiny AS isShiny,
               cv.gender AS gender,
               cv.shadowState AS shadowState,
               cv.dynamaxState AS dynamaxState,
               cv.variantKey AS variantKey
        FROM collection_variants cv
        LEFT JOIN pokemon_species s ON s.id = cv.speciesId
        LEFT JOIN pokemon_forms f ON f.id = cv.formId
        LEFT JOIN costumes c ON c.id = cv.costumeId
        LEFT JOIN backgrounds b ON b.id = cv.backgroundId
        WHERE cv.id = :variantId
        LIMIT 1
        """,
    )
    fun observeInfo(variantId: String): Flow<VariantInfoRow?>

    @Query(
        """
        SELECT a.id AS accountId, a.name AS accountName, SUM(ob.quantity) AS quantity
        FROM accounts a
        JOIN ownership_buckets ob ON ob.accountId = a.id
        WHERE ob.variantId = :variantId AND ob.quantity > 0
          AND (:includeArchived = 1 OR a.isArchived = 0)
        GROUP BY a.id
        ORDER BY a.sortOrder ASC, a.createdAt ASC
        """,
    )
    fun observeSources(variantId: String, includeArchived: Boolean = false): Flow<List<SourceAccountRow>>

    @Query("SELECT * FROM collection_variants ORDER BY createdAt ASC")
    suspend fun getAll(): List<CollectionVariantEntity>

    @Query("SELECT * FROM collection_variants WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CollectionVariantEntity?

    @Query("SELECT * FROM collection_variants WHERE variantKey = :variantKey LIMIT 1")
    suspend fun findByKey(variantKey: String): CollectionVariantEntity?

    @Upsert
    suspend fun upsert(variant: CollectionVariantEntity)

    @Upsert
    suspend fun upsertAll(variants: List<CollectionVariantEntity>)

    @Query("DELETE FROM collection_variants WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface OwnershipBucketDao {
    @Query(
        """
        SELECT * FROM ownership_buckets
        WHERE accountId = :accountId AND variantId = :variantId
        ORDER BY sizeType ASC, hasSpecialMove ASC, tradeState ASC
        """,
    )
    fun observeForVariant(accountId: String, variantId: String): Flow<List<OwnershipBucketEntity>>

    @Query("SELECT * FROM ownership_buckets WHERE accountId = :accountId AND variantId = :variantId")
    suspend fun getForVariant(accountId: String, variantId: String): List<OwnershipBucketEntity>

    @Query(
        """
        SELECT * FROM ownership_buckets
        WHERE accountId = :accountId AND variantId = :variantId
          AND sizeType = :sizeType AND hasSpecialMove = :hasSpecialMove AND tradeState = :tradeState
        LIMIT 1
        """,
    )
    suspend fun find(accountId: String, variantId: String, sizeType: SizeType, hasSpecialMove: Boolean, tradeState: TradeState): OwnershipBucketEntity?

    @Query("SELECT * FROM ownership_buckets ORDER BY createdAt ASC")
    suspend fun getAll(): List<OwnershipBucketEntity>

    @Upsert
    suspend fun upsert(bucket: OwnershipBucketEntity)

    @Upsert
    suspend fun upsertAll(buckets: List<OwnershipBucketEntity>)

    @Query("DELETE FROM ownership_buckets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM ownership_buckets WHERE variantId = :variantId")
    suspend fun deleteForVariant(variantId: String)
}

