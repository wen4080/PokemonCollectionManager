package tw.pokemon.collectionmanager

import android.app.Application
import android.util.Log
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import tw.pokemon.collectionmanager.data.backup.BackupManager
import tw.pokemon.collectionmanager.data.local.CollectionDatabase
import tw.pokemon.collectionmanager.data.repository.CollectionRepository
import tw.pokemon.collectionmanager.data.repository.MasterDataRepository
import tw.pokemon.collectionmanager.data.repository.LocalFirstPokemonImageRepository
import tw.pokemon.collectionmanager.data.repository.UserPreferencesRepository

class CollectionApplication : Application() {
    private fun hasColumn(database: SupportSQLiteDatabase, table: String, column: String): Boolean {
        var found = false
        database.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (nameIndex >= 0 && cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) {
                    found = true
                    break
                }
            }
        }
        return found
    }

    private fun ensureBackgroundPreviewStatusColumn(database: SupportSQLiteDatabase) {
        if (!hasColumn(database, "backgrounds", "previewStatus")) {
            database.execSQL("ALTER TABLE backgrounds ADD COLUMN previewStatus TEXT")
        }
    }

    private val migration1To2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE pokemon_species ADD COLUMN shinyImageKey TEXT")
            database.execSQL("ALTER TABLE pokemon_species ADD COLUMN generation INTEGER")
            database.execSQL("ALTER TABLE pokemon_species ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
            database.execSQL("ALTER TABLE pokemon_forms ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
            database.execSQL("ALTER TABLE costumes ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
            database.execSQL("ALTER TABLE backgrounds ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS pokemon_costume_compatibilities (
                    id TEXT NOT NULL PRIMARY KEY,
                    compatibilityKey TEXT NOT NULL,
                    speciesId TEXT NOT NULL,
                    formId TEXT,
                    costumeId TEXT NOT NULL,
                    imageKey TEXT,
                    isVerified INTEGER NOT NULL,
                    isActive INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pokemon_costume_compatibilities_compatibilityKey ON pokemon_costume_compatibilities(compatibilityKey)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_pokemon_costume_compatibilities_speciesId_formId ON pokemon_costume_compatibilities(speciesId, formId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_pokemon_costume_compatibilities_costumeId ON pokemon_costume_compatibilities(costumeId)")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS pokemon_background_compatibilities (
                    id TEXT NOT NULL PRIMARY KEY,
                    compatibilityKey TEXT NOT NULL,
                    backgroundId TEXT NOT NULL,
                    speciesId TEXT,
                    formId TEXT,
                    isVerified INTEGER NOT NULL,
                    isActive INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pokemon_background_compatibilities_compatibilityKey ON pokemon_background_compatibilities(compatibilityKey)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_pokemon_background_compatibilities_backgroundId ON pokemon_background_compatibilities(backgroundId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_pokemon_background_compatibilities_speciesId_formId ON pokemon_background_compatibilities(speciesId, formId)")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS master_data_meta (
                    id TEXT NOT NULL PRIMARY KEY,
                    masterVersion TEXT NOT NULL,
                    generatedAt TEXT,
                    speciesCount INTEGER NOT NULL,
                    formCount INTEGER NOT NULL,
                    costumeCount INTEGER NOT NULL,
                    backgroundCount INTEGER NOT NULL,
                    imageCount INTEGER NOT NULL,
                    missingImageCount INTEGER NOT NULL,
                    importedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    private val migration2To3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE backgrounds ADD COLUMN categoryKey TEXT NOT NULL DEFAULT 'OTHER_SPECIAL'")
            database.execSQL("ALTER TABLE backgrounds ADD COLUMN categoryName TEXT NOT NULL DEFAULT '其他特殊背卡'")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_backgrounds_categoryKey ON backgrounds(categoryKey)")
        }
    }

    private val migration3To4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE pokemon_forms ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE costumes ADD COLUMN releaseYear INTEGER")
            database.execSQL("ALTER TABLE costumes ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE backgrounds ADD COLUMN eventKey TEXT NOT NULL DEFAULT 'OTHER'")
            database.execSQL("ALTER TABLE backgrounds ADD COLUMN availableFrom TEXT")
            database.execSQL("ALTER TABLE backgrounds ADD COLUMN availableUntil TEXT")
            database.execSQL("ALTER TABLE backgrounds ADD COLUMN dataSource TEXT NOT NULL DEFAULT 'GAME_MASTER'")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_backgrounds_eventKey ON backgrounds(eventKey)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_backgrounds_year ON backgrounds(year)")
        }
    }

    private val migration4To5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE backgrounds ADD COLUMN vfxKey TEXT")
        }
    }

    private val migration5To6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE backgrounds ADD COLUMN vfxKeys TEXT")
            database.execSQL("ALTER TABLE backgrounds ADD COLUMN effectNote TEXT")
            database.execSQL("ALTER TABLE backgrounds ADD COLUMN previewImageKey TEXT")
            database.execSQL("ALTER TABLE backgrounds ADD COLUMN previewSource TEXT")
        }
    }

    private val migration6To7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS collection_tags (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT,
                    sortOrder INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_collection_tags_name ON collection_tags(name)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_collection_tags_sortOrder ON collection_tags(sortOrder)")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS collection_variant_tags (
                    tagId TEXT NOT NULL,
                    variantId TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    PRIMARY KEY(tagId, variantId),
                    FOREIGN KEY(tagId) REFERENCES collection_tags(id) ON DELETE CASCADE,
                    FOREIGN KEY(variantId) REFERENCES collection_variants(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_collection_variant_tags_tagId ON collection_variant_tags(tagId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_collection_variant_tags_variantId ON collection_variant_tags(variantId)")
        }
    }

    private val migration7To8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            ensureBackgroundPreviewStatusColumn(database)
        }
    }

    /**
     * 修復曾安裝過中間版本的資料庫：若資料庫版本號已經是 8，
     * 但欄位尚未實際建立，啟動時仍可安全補上，不刪除收藏資料。
     */
    private val migration8To9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            ensureBackgroundPreviewStatusColumn(database)
        }
    }

    val database: CollectionDatabase by lazy {
        Room.databaseBuilder(this, CollectionDatabase::class.java, "pokemon_collection.db")
            .enableMultiInstanceInvalidation()
            .addMigrations(migration1To2, migration2To3, migration3To4, migration4To5, migration5To6, migration6To7, migration7To8, migration8To9)
            .build()
    }

    val collectionRepository: CollectionRepository by lazy { CollectionRepository(database) }
    val masterDataRepository: MasterDataRepository by lazy { MasterDataRepository(this, database) }
    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(this) }
    val backupManager: BackupManager by lazy { BackupManager(this) }
    val imageRepository by lazy { LocalFirstPokemonImageRepository(this, database) }

    private val applicationScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.IO +
            CoroutineExceptionHandler { _, error ->
                Log.e("收藏應用程式", "背景初始化失敗，保留現有資料並讓應用程式繼續啟動", error)
            },
    )

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            runCatching { masterDataRepository.seedIfNeeded() }
                .onFailure { Log.e("收藏應用程式", "內建主資料初始化失敗，保留現有資料", it) }
            runCatching {
                val enabled = preferencesRepository.automaticMasterDataUpdateEnabled.first()
                val url = preferencesRepository.masterDataUpdateUrl.first().trim()
                val lastCheck = preferencesRepository.lastMasterDataUpdateCheck.first()
                val due = System.currentTimeMillis() - lastCheck >= 24L * 60L * 60L * 1000L
                if (enabled && url.isNotBlank() && due) {
                    runCatching { masterDataRepository.checkAndImportUpdate(url) }
                        .onFailure { Log.e("收藏應用程式", "自動更新主資料失敗", it) }
                    preferencesRepository.markMasterDataUpdateChecked()
                }
            }.onFailure { Log.e("收藏應用程式", "讀取自動更新設定失敗", it) }
        }
    }
}

