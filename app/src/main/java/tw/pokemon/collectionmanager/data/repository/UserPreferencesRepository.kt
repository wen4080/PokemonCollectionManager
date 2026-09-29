package tw.pokemon.collectionmanager.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import tw.pokemon.collectionmanager.data.local.ThemeMode

const val DEFAULT_MASTER_DATA_UPDATE_URL =
    "https://wen4080.github.io/PokemonCollectionManager/master-data/master_manifest.json"

const val DEFAULT_OVERVIEW_SUMMARY_CODES =
    "COLLECTION_SET|SPECIES_COUNT|POKEMON_COUNT|SHINY|BACKGROUND|COSTUME|XXL|SPECIAL_MOVE"

data class SavedCollectionFilters(
    val remember: Boolean = false,
    val filterCodes: Set<String> = emptySet(),
    val backgroundIds: Set<String> = emptySet(),
    val matchMode: String = "ALL",
    val minimumMatches: Int = 2,
)

private val Context.collectionPreferencesDataStore by preferencesDataStore(name = "collection_preferences")

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val selectedAccount = stringPreferencesKey("selected_account_id")
        val masterDataUpdateUrl = stringPreferencesKey("master_data_update_url")
        val automaticMasterDataUpdate = booleanPreferencesKey("automatic_master_data_update")
        val lastMasterDataUpdateCheck = longPreferencesKey("last_master_data_update_check")
        val rememberCollectionFilters = booleanPreferencesKey("remember_collection_filters")
        val collectionFilterCodes = stringPreferencesKey("collection_filter_codes")
        val collectionBackgroundIds = stringPreferencesKey("collection_background_ids")
        val collectionFilterMatchMode = stringPreferencesKey("collection_filter_match_mode")
        val collectionFilterMinimum = intPreferencesKey("collection_filter_minimum")
        val overviewSummaryStatCodes = stringPreferencesKey("overview_summary_stat_codes")
    }

    val themeMode: Flow<ThemeMode> = context.collectionPreferencesDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { ThemeMode.fromCode(it[Keys.theme]) }

    val selectedAccountId: Flow<String?> = context.collectionPreferencesDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it[Keys.selectedAccount] }

    val masterDataUpdateUrl: Flow<String> = context.collectionPreferencesDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it[Keys.masterDataUpdateUrl].orEmpty().ifBlank { DEFAULT_MASTER_DATA_UPDATE_URL } }

    val automaticMasterDataUpdateEnabled: Flow<Boolean> = context.collectionPreferencesDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it[Keys.automaticMasterDataUpdate] ?: true }

    val lastMasterDataUpdateCheck: Flow<Long> = context.collectionPreferencesDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it[Keys.lastMasterDataUpdateCheck] ?: 0L }

    val savedCollectionFilters: Flow<SavedCollectionFilters> = context.collectionPreferencesDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { preferences ->
            SavedCollectionFilters(
                remember = preferences[Keys.rememberCollectionFilters] ?: false,
                filterCodes = preferences[Keys.collectionFilterCodes].toCodeSet(),
                backgroundIds = preferences[Keys.collectionBackgroundIds].toCodeSet(),
                matchMode = preferences[Keys.collectionFilterMatchMode] ?: "ALL",
                minimumMatches = preferences[Keys.collectionFilterMinimum] ?: 2,
            )
        }

    val overviewSummaryStatCodes: Flow<Set<String>> = context.collectionPreferencesDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { preferences ->
            preferences[Keys.overviewSummaryStatCodes]
                ?.toCodeSet()
                ?: DEFAULT_OVERVIEW_SUMMARY_CODES.toCodeSet()
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.collectionPreferencesDataStore.edit { it[Keys.theme] = mode.code }
    }

    suspend fun setSelectedAccount(id: String?) {
        context.collectionPreferencesDataStore.edit {
            if (id == null) it.remove(Keys.selectedAccount) else it[Keys.selectedAccount] = id
        }
    }

    suspend fun setMasterDataUpdateUrl(url: String) {
        context.collectionPreferencesDataStore.edit { it[Keys.masterDataUpdateUrl] = url.trim() }
    }

    suspend fun setAutomaticMasterDataUpdateEnabled(enabled: Boolean) {
        context.collectionPreferencesDataStore.edit { it[Keys.automaticMasterDataUpdate] = enabled }
    }

    suspend fun markMasterDataUpdateChecked(time: Long = System.currentTimeMillis()) {
        context.collectionPreferencesDataStore.edit { it[Keys.lastMasterDataUpdateCheck] = time }
    }

    suspend fun saveCollectionFilters(filters: SavedCollectionFilters) {
        context.collectionPreferencesDataStore.edit { preferences ->
            preferences[Keys.rememberCollectionFilters] = filters.remember
            preferences[Keys.collectionFilterCodes] = filters.filterCodes.sorted().joinToString("|")
            preferences[Keys.collectionBackgroundIds] = filters.backgroundIds.sorted().joinToString("|")
            preferences[Keys.collectionFilterMatchMode] = filters.matchMode
            preferences[Keys.collectionFilterMinimum] = filters.minimumMatches.coerceAtLeast(1)
        }
    }

    suspend fun saveOverviewSummaryStatCodes(codes: Set<String>) {
        context.collectionPreferencesDataStore.edit { preferences ->
            preferences[Keys.overviewSummaryStatCodes] = codes.sorted().joinToString("|")
        }
    }
}

private fun String?.toCodeSet(): Set<String> = this
    ?.split('|')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?.toSet()
    .orEmpty()

