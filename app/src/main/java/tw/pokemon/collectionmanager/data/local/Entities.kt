package tw.pokemon.collectionmanager.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "account_groups",
    indices = [Index(value = ["name"], unique = true)],
)
data class AccountGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = AccountGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("groupId"), Index(value = ["name"])],
)
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nickname: String? = null,
    val groupId: String? = null,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "pokemon_species",
    indices = [Index(value = ["speciesKey"], unique = true), Index(value = ["dexNumber"]), Index(value = ["nameZhTw"]), Index(value = ["nameEn"])],
)
data class PokemonSpeciesEntity(
    @PrimaryKey val id: String,
    val dexNumber: Int,
    val speciesKey: String,
    val nameZhTw: String,
    val nameEn: String,
    val defaultImageKey: String? = null,
    val shinyImageKey: String? = null,
    val generation: Int? = null,
    val isActive: Boolean = true,
)

@Entity(
    tableName = "pokemon_forms",
    foreignKeys = [
        ForeignKey(
            entity = PokemonSpeciesEntity::class,
            parentColumns = ["id"],
            childColumns = ["speciesId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index("speciesId"), Index(value = ["speciesId", "formKey"], unique = true)],
)
data class PokemonFormEntity(
    @PrimaryKey val id: String,
    val speciesId: String,
    val formKey: String,
    val displayName: String,
    val imageKey: String? = null,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
)

@Entity(
    tableName = "costumes",
    indices = [Index(value = ["costumeKey"], unique = true)],
)
data class CostumeEntity(
    @PrimaryKey val id: String,
    val costumeKey: String,
    val displayName: String,
    val eventName: String? = null,
    val imageKey: String? = null,
    val releaseYear: Int? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
)

@Entity(
    tableName = "backgrounds",
    indices = [Index(value = ["backgroundKey"], unique = true), Index("backgroundType"), Index("categoryKey"), Index("eventKey"), Index("year")],
)
data class BackgroundEntity(
    @PrimaryKey val id: String,
    val backgroundKey: String,
    val displayName: String,
    val backgroundType: BackgroundType,
    val categoryKey: String = "OTHER_SPECIAL",
    val categoryName: String = "其他特殊背卡",
    val eventKey: String = "OTHER",
    val eventName: String? = null,
    val locationName: String? = null,
    val year: Int? = null,
    val availableFrom: String? = null,
    val availableUntil: String? = null,
    val dataSource: String = "GAME_MASTER",
    val imageKey: String? = null,
    val vfxKey: String? = null,
    val vfxKeys: String? = null,
    val effectNote: String? = null,
    val previewImageKey: String? = null,
    val previewSource: String? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
)

@Entity(
    tableName = "pokemon_costume_compatibilities",
    indices = [
        Index(value = ["compatibilityKey"], unique = true),
        Index(value = ["speciesId", "formId"]),
        Index(value = ["costumeId"]),
    ],
)
data class PokemonCostumeCompatibilityEntity(
    @PrimaryKey val id: String,
    val compatibilityKey: String,
    val speciesId: String,
    val formId: String? = null,
    val costumeId: String,
    val imageKey: String? = null,
    val isVerified: Boolean = true,
    val isActive: Boolean = true,
)

@Entity(
    tableName = "pokemon_background_compatibilities",
    indices = [
        Index(value = ["compatibilityKey"], unique = true),
        Index(value = ["backgroundId"]),
        Index(value = ["speciesId", "formId"]),
    ],
)
data class PokemonBackgroundCompatibilityEntity(
    @PrimaryKey val id: String,
    val compatibilityKey: String,
    val backgroundId: String,
    val speciesId: String? = null,
    val formId: String? = null,
    val isVerified: Boolean = false,
    val isActive: Boolean = true,
)

@Entity(tableName = "master_data_meta")
data class MasterDataMetaEntity(
    @PrimaryKey val id: String = "current",
    val masterVersion: String,
    val generatedAt: String?,
    val speciesCount: Int,
    val formCount: Int,
    val costumeCount: Int,
    val backgroundCount: Int,
    val imageCount: Int,
    val missingImageCount: Int,
    val importedAt: Long,
)

@Entity(
    tableName = "collection_variants",
    foreignKeys = [
        ForeignKey(entity = PokemonSpeciesEntity::class, parentColumns = ["id"], childColumns = ["speciesId"], onDelete = ForeignKey.NO_ACTION),
        ForeignKey(entity = PokemonFormEntity::class, parentColumns = ["id"], childColumns = ["formId"], onDelete = ForeignKey.NO_ACTION),
        ForeignKey(entity = CostumeEntity::class, parentColumns = ["id"], childColumns = ["costumeId"], onDelete = ForeignKey.NO_ACTION),
        ForeignKey(entity = BackgroundEntity::class, parentColumns = ["id"], childColumns = ["backgroundId"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [
        Index(value = ["variantKey"], unique = true),
        Index("speciesId"),
        Index("formId"),
        Index("costumeId"),
        Index("backgroundId"),
    ],
)
data class CollectionVariantEntity(
    @PrimaryKey val id: String,
    val speciesId: String,
    val formId: String,
    val costumeId: String,
    val backgroundId: String,
    val isShiny: Boolean,
    val gender: Gender,
    val shadowState: ShadowState,
    val dynamaxState: DynamaxState,
    val variantKey: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "ownership_buckets",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CollectionVariantEntity::class, parentColumns = ["id"], childColumns = ["variantId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("accountId"),
        Index("variantId"),
        Index(value = ["accountId", "variantId", "sizeType", "hasSpecialMove", "tradeState"], unique = true),
    ],
)
data class OwnershipBucketEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val variantId: String,
    val sizeType: SizeType,
    val hasSpecialMove: Boolean,
    val tradeState: TradeState,
    val quantity: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class AccountSummaryRow(
    val id: String,
    val name: String,
    val nickname: String?,
    val groupId: String?,
    val groupName: String?,
    val isArchived: Boolean,
    val variantCount: Int,
    val pokemonCount: Long,
)

data class VariantCardRow(
    val variantId: String,
    val speciesId: String,
    val formId: String,
    val costumeId: String,
    val speciesName: String,
    val dexNumber: Int,
    val formName: String,
    val costumeName: String,
    val backgroundName: String,
    val backgroundCategoryName: String,
    val backgroundId: String,
    val isShiny: Boolean,
    val gender: Gender,
    val shadowState: ShadowState,
    val dynamaxState: DynamaxState,
    val totalQuantity: Long,
    val xxlQuantity: Long,
    val xxsQuantity: Long,
    val specialMoveQuantity: Long,
    val untradedQuantity: Long,
    val tradedQuantity: Long,
)

data class VariantInfoRow(
    val variantId: String,
    val speciesId: String,
    val speciesName: String,
    val dexNumber: Int,
    val formId: String,
    val formName: String,
    val costumeId: String,
    val costumeName: String,
    val backgroundId: String,
    val backgroundName: String,
    val backgroundCategoryName: String,
    val isShiny: Boolean,
    val gender: Gender,
    val shadowState: ShadowState,
    val dynamaxState: DynamaxState,
    val variantKey: String,
)

data class SourceAccountRow(
    val accountId: String,
    val accountName: String,
    val quantity: Long,
)
