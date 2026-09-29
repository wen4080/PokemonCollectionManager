package tw.pokemon.collectionmanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AccountGroupEntity::class,
        AccountEntity::class,
        PokemonSpeciesEntity::class,
        PokemonFormEntity::class,
        CostumeEntity::class,
        BackgroundEntity::class,
        PokemonCostumeCompatibilityEntity::class,
        PokemonBackgroundCompatibilityEntity::class,
        MasterDataMetaEntity::class,
        CollectionVariantEntity::class,
        OwnershipBucketEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class CollectionDatabase : RoomDatabase() {
    abstract fun accountGroupDao(): AccountGroupDao
    abstract fun accountDao(): AccountDao
    abstract fun masterDataDao(): MasterDataDao
    abstract fun variantDao(): VariantDao
    abstract fun ownershipBucketDao(): OwnershipBucketDao
}
