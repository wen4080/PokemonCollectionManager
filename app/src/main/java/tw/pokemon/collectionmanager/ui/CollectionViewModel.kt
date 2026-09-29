package tw.pokemon.collectionmanager.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.pokemon.collectionmanager.data.backup.BackupManager
import tw.pokemon.collectionmanager.data.local.AccountEntity
import tw.pokemon.collectionmanager.data.local.AccountGroupEntity
import tw.pokemon.collectionmanager.data.local.AccountSummaryRow
import tw.pokemon.collectionmanager.data.local.BackgroundEntity
import tw.pokemon.collectionmanager.data.local.CollectionVariantEntity
import tw.pokemon.collectionmanager.data.local.CostumeEntity
import tw.pokemon.collectionmanager.data.local.OwnershipBucketEntity
import tw.pokemon.collectionmanager.data.local.PokemonFormEntity
import tw.pokemon.collectionmanager.data.local.PokemonSpeciesEntity
import tw.pokemon.collectionmanager.data.local.ThemeMode
import tw.pokemon.collectionmanager.data.local.VariantCardRow
import tw.pokemon.collectionmanager.data.local.VariantInfoRow
import tw.pokemon.collectionmanager.data.local.SourceAccountRow
import tw.pokemon.collectionmanager.data.repository.CollectionRepository
import tw.pokemon.collectionmanager.data.repository.MasterDataRepository
import tw.pokemon.collectionmanager.data.repository.MasterDataUpdateResult
import tw.pokemon.collectionmanager.data.repository.PokemonImageRepository
import tw.pokemon.collectionmanager.data.repository.SavedCollectionFilters
import tw.pokemon.collectionmanager.data.repository.UserPreferencesRepository
import tw.pokemon.collectionmanager.domain.BucketDraft
import tw.pokemon.collectionmanager.domain.VariantDraft

class CollectionViewModel(
    private val repository: CollectionRepository,
    private val masterDataRepository: MasterDataRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val backupManager: BackupManager,
    val imageRepository: PokemonImageRepository,
) : ViewModel() {
    val accounts: StateFlow<List<AccountSummaryRow>> = repository.accountSummaries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val groups = repository.accountGroups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<AccountGroupEntity>())
    val species = repository.species.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<PokemonSpeciesEntity>())
    val costumes = repository.costumes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<CostumeEntity>())
    val backgrounds = repository.backgrounds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<BackgroundEntity>())
    val themeMode = preferencesRepository.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
    val selectedAccountId = preferencesRepository.selectedAccountId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val masterDataMeta = masterDataRepository.meta.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val masterDataUpdateUrl = preferencesRepository.masterDataUpdateUrl.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val automaticMasterDataUpdateEnabled = preferencesRepository.automaticMasterDataUpdateEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val bundledBackgroundImageCount = backgrounds
        .map(imageRepository::countBundledBackgroundImages)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val savedCollectionFilters = preferencesRepository.savedCollectionFilters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedCollectionFilters())

    private val _overviewAccountIds = MutableStateFlow<List<String>>(emptyList())
    val overviewAccountIds: StateFlow<List<String>> = _overviewAccountIds
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun forms(speciesId: String) = masterDataRepository.forms(speciesId)
    fun searchSpecies(query: String, generation: Int?) = masterDataRepository.searchSpecies(query, generation)
    fun costumeCompatibility(speciesId: String, formId: String) = masterDataRepository.costumeCompatibility(speciesId, formId)
    fun backgroundCompatibility(speciesId: String, formId: String) = masterDataRepository.backgroundCompatibility(speciesId, formId)
    fun account(accountId: String) = repository.account(accountId)
    fun accountVariants(accountId: String) = repository.variantsForAccount(accountId)
    fun allVariants() = repository.variantsForAllAccounts()
    fun selectedVariants(accountIds: List<String>) = if (accountIds.isEmpty()) repository.variantsForAllAccounts() else repository.variantsForAccounts(accountIds)
    fun variantInfo(variantId: String) = repository.variantInfo(variantId)
    fun variantSources(variantId: String) = repository.variantSources(variantId)
    fun buckets(accountId: String, variantId: String) = repository.bucketsForVariant(accountId, variantId)

    fun setSelectedAccount(accountId: String?) = viewModelScope.launch { preferencesRepository.setSelectedAccount(accountId) }

    fun setOverviewAccounts(ids: List<String>) {
        _overviewAccountIds.value = ids
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { preferencesRepository.setThemeMode(mode) }

    fun addGroup(name: String) = launchAction { repository.createGroup(name); "已建立帳號群組" }
    fun deleteGroup(id: String) = launchAction { repository.deleteGroup(id); "已刪除帳號群組" }

    fun addAccount(name: String, nickname: String?, groupId: String?) = launchAction {
        repository.createAccount(name, nickname, groupId)
        "已建立帳號"
    }

    fun updateAccount(account: AccountEntity) = launchAction { repository.updateAccount(account); "已更新帳號" }
    fun updateAccount(id: String, name: String, nickname: String?, groupId: String?) = launchAction {
        repository.updateAccountFields(id, name, nickname, groupId)
        "已更新帳號"
    }
    fun archiveAccount(accountId: String, archived: Boolean) = launchAction {
        repository.setArchived(accountId, archived)
        if (archived) "帳號已封存" else "帳號已恢復"
    }

    fun deleteAccount(accountId: String, onDeleted: () -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.deleteAccount(accountId) }
            .onSuccess { onDeleted(); _messages.emit("帳號已刪除") }
            .onFailure { _messages.emit("刪除失敗：${it.message ?: "未知錯誤"}") }
    }

    suspend fun deleteAccountSummary(accountId: String) = repository.accountDeleteSummary(accountId)

    fun addCollection(accountId: String, variant: VariantDraft, bucket: BucketDraft, quantity: Int, continueAdding: Boolean = false) = launchAction {
        repository.addOwnership(accountId, variant, bucket, quantity)
        if (continueAdding) "已加入，準備下一筆" else "收藏已加入"
    }

    fun updateVariant(variantId: String, draft: VariantDraft) = launchAction {
        repository.updateVariant(variantId, draft)
        "收藏版本已更新"
    }

    fun updateBucket(bucket: OwnershipBucketEntity, quantity: Int) = launchAction {
        repository.updateBucket(bucket, quantity)
        "數量已更新"
    }

    fun deleteVariant(variantId: String) = launchAction { repository.deleteVariant(variantId); "收藏版本已刪除" }

    fun exportBackup(uri: Uri) = launchAction {
        backupManager.exportToUri(uri, repository)
        "備份已匯出"
    }

    fun importBackup(uri: Uri) = launchAction {
        backupManager.importFromUri(uri, repository)
        "備份已還原"
    }

    fun importMasterData(uri: Uri) = launchAction {
        masterDataRepository.importManifest(
            backupManager.readText(uri),
        )
        "主資料已匯入"
    }

    fun setMasterDataUpdateUrl(url: String) = viewModelScope.launch { preferencesRepository.setMasterDataUpdateUrl(url) }

    fun setAutomaticMasterDataUpdateEnabled(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setAutomaticMasterDataUpdateEnabled(enabled)
    }

    fun saveCollectionFilters(filters: SavedCollectionFilters) = viewModelScope.launch {
        preferencesRepository.saveCollectionFilters(filters)
    }

    fun checkMasterDataUpdate(requestedUrl: String = masterDataUpdateUrl.value) = launchAction {
        val url = requestedUrl.trim()
        require(url.isNotBlank()) { "請先設定主資料更新網址" }
        preferencesRepository.setMasterDataUpdateUrl(url)
        val result = masterDataRepository.checkAndImportUpdate(url)
        preferencesRepository.markMasterDataUpdateChecked()
        when (result) {
            is MasterDataUpdateResult.Updated -> "主資料已更新：${result.newVersion}"
            is MasterDataUpdateResult.AlreadyCurrent -> "目前已是最新主資料：${result.version}"
        }
    }

    private fun launchAction(action: suspend () -> String) = viewModelScope.launch {
        runCatching { action() }
            .onSuccess { _messages.emit(it) }
            .onFailure { _messages.emit("操作失敗：${it.message?.takeIf(String::isNotBlank) ?: "未提供詳細錯誤，請稍後再試"}") }
    }
}

class CollectionViewModelFactory(
    private val repository: CollectionRepository,
    private val masterDataRepository: MasterDataRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val backupManager: BackupManager,
    private val imageRepository: PokemonImageRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = CollectionViewModel(
        repository,
        masterDataRepository,
        preferencesRepository,
        backupManager,
        imageRepository,
    ) as T
}

