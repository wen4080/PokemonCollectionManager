package tw.pokemon.collectionmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.pokemon.collectionmanager.data.repository.SavedCollectionFilters

private class CollectionFilterUiState {
    var filters by mutableStateOf(emptySet<VariantFilterMode>())
    var backgroundIds by mutableStateOf(emptySet<String>())
    var matchMode by mutableStateOf(FilterMatchMode.ALL)
    var minimumMatches by mutableStateOf(2)
    var rememberFilters by mutableStateOf(false)

    fun save(viewModel: CollectionViewModel) {
        viewModel.saveCollectionFilters(
            SavedCollectionFilters(
                remember = rememberFilters,
                filterCodes = filters.map { it.name }.toSet(),
                backgroundIds = backgroundIds,
                matchMode = matchMode.code,
                minimumMatches = minimumMatches,
            ),
        )
    }
}

@Composable
private fun rememberCollectionFilterUiState(viewModel: CollectionViewModel): CollectionFilterUiState {
    val saved by viewModel.savedCollectionFilters.collectAsStateWithLifecycle()
    val state = remember { CollectionFilterUiState() }
    LaunchedEffect(saved) {
        if (saved.remember) {
            state.rememberFilters = true
            state.filters = saved.filterCodes.mapNotNull { code -> VariantFilterMode.entries.firstOrNull { it.name == code } }.toSet()
            state.backgroundIds = saved.backgroundIds
            state.matchMode = FilterMatchMode.fromCode(saved.matchMode)
            state.minimumMatches = saved.minimumMatches.coerceAtLeast(1)
        }
    }
    return state
}

@Composable
private fun CollectionFilterControls(
    state: CollectionFilterUiState,
    viewModel: CollectionViewModel,
    backgrounds: List<tw.pokemon.collectionmanager.data.local.BackgroundEntity>,
) {
    CollectionFilterPanel(
        filters = state.filters,
        backgroundIds = state.backgroundIds,
        matchMode = state.matchMode,
        minimumMatches = state.minimumMatches,
        rememberFilters = state.rememberFilters,
        backgrounds = backgrounds,
        imageRepository = viewModel.imageRepository,
        onFiltersChanged = { state.filters = it; state.save(viewModel) },
        onBackgroundsChanged = { state.backgroundIds = it; state.save(viewModel) },
        onMatchModeChanged = { state.matchMode = it; state.save(viewModel) },
        onMinimumChanged = { state.minimumMatches = it; state.save(viewModel) },
        onRememberChanged = { state.rememberFilters = it; state.save(viewModel) },
    )
}

@Composable
fun AccountCollectionScreen(
    viewModel: CollectionViewModel,
    accountId: String,
    onBack: () -> Unit,
    onOpenVariant: (String) -> Unit,
) {
    val account by viewModel.account(accountId).collectAsStateWithLifecycle(initialValue = null)
    val variantsFlow = remember(accountId) { viewModel.accountVariants(accountId) }
    val variants by variantsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val backgrounds by viewModel.backgrounds.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val filterState = rememberCollectionFilterUiState(viewModel)
    var showAdd by remember { mutableStateOf(false) }
    val filtered = variants.filter { it.matches(query, filterState.filters, filterState.backgroundIds, filterState.matchMode, filterState.minimumMatches) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ 返回") }
                PageTitle(
                    title = account?.name ?: "帳號收藏",
                    subtitle = "${variants.size} 種收藏版本 · ${variants.sumOf { it.totalQuantity }} 隻",
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Spacer(Modifier.padding(4.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜尋這個帳號的收藏") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.padding(4.dp))
            CollectionFilterControls(filterState, viewModel, backgrounds)
            Spacer(Modifier.padding(8.dp))
            if (filtered.isEmpty()) {
                EmptyState("沒有符合的收藏", "這只表示目前沒有登記符合條件的收藏版本，不代表帳號沒有這隻 Pokémon。", "新增收藏") { showAdd = true }
            } else {
                VariantGrid(
                    variants = filtered,
                    imageRepository = viewModel.imageRepository,
                    onClick = { onOpenVariant(it.variantId) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        androidx.compose.material3.FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Text("＋", style = MaterialTheme.typography.headlineSmall) }
    }

    if (showAdd) {
        QuickAddSheet(viewModel, accountId, onDismiss = { showAdd = false })
    }
}

@Composable
fun OverviewScreen(viewModel: CollectionViewModel, onOpenVariant: (String) -> Unit) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val activeAccounts = accounts.filterNot { it.isArchived }
    val selectedIds by viewModel.overviewAccountIds.collectAsStateWithLifecycle()
    val variantsFlow = remember(selectedIds) { viewModel.selectedVariants(selectedIds) }
    val variants by variantsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val backgrounds by viewModel.backgrounds.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val filterState = rememberCollectionFilterUiState(viewModel)
    val visible = variants.filter { it.matches(query, filterState.filters, filterState.backgroundIds, filterState.matchMode, filterState.minimumMatches) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        PageTitle("總覽", subtitle = "跨帳號聚合完整收藏版本；×數量是選取帳號集合的總和。")
        Spacer(Modifier.padding(6.dp))
        Text("帳號篩選", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.padding(2.dp))
        LazyRow(overscrollEffect = null, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            item {
                FilterChip(
                    selected = selectedIds.isEmpty(),
                    onClick = { viewModel.setOverviewAccounts(emptyList()) },
                    label = { Text("全部帳號") },
                )
            }
            items(activeAccounts, key = { it.id }) { account ->
                FilterChip(
                    selected = selectedIds.contains(account.id),
                    onClick = {
                        val next = if (selectedIds.isEmpty()) {
                            listOf(account.id)
                        } else if (account.id in selectedIds) {
                            selectedIds.filterNot { it == account.id }
                        } else {
                            selectedIds + account.id
                        }
                        viewModel.setOverviewAccounts(next)
                    },
                    label = { Text(account.name) },
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜尋 Pokémon、型態或背景") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.padding(4.dp))
        CollectionFilterControls(filterState, viewModel, backgrounds)
        Spacer(Modifier.padding(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("收藏版本", visible.size.toString(), Modifier.weight(1f))
            StatCard("Pokémon", visible.sumOf { it.totalQuantity }.toString(), Modifier.weight(1f))
            StatCard("特大", visible.sumOf { it.xxlQuantity }.toString(), Modifier.weight(1f))
            StatCard("特招", visible.sumOf { it.specialMoveQuantity }.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.padding(8.dp))
        if (visible.isEmpty()) {
            EmptyState("沒有符合的收藏版本", "請調整帳號或篩選條件。未登記項目不會被自動當成未擁有。")
        } else {
            VariantGrid(visible, imageRepository = viewModel.imageRepository, onClick = { onOpenVariant(it.variantId) }, modifier = Modifier.weight(1f))
        }
    }
}

