package tw.pokemon.collectionmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.pokemon.collectionmanager.data.local.Gender
import tw.pokemon.collectionmanager.data.local.VariantCardRow
import tw.pokemon.collectionmanager.data.repository.SavedCollectionFilters

enum class OverviewSummaryMetric(
    val code: String,
    val label: String,
    val description: String,
) {
    COLLECTION_SET("COLLECTION_SET", "收藏組合", "型態、裝扮、背卡、異色等條件完全相同的獨立組合數"),
    SPECIES_COUNT("SPECIES_COUNT", "寶可夢種類", "符合條件的不同寶可夢種類數"),
    POKEMON_COUNT("POKEMON_COUNT", "寶可夢隻數", "符合條件的實際隻數總和"),
    SHINY("SHINY", "異色寶可夢", "異色收藏的實際隻數"),
    BACKGROUND("BACKGROUND", "有背卡", "有背景背卡的實際隻數"),
    COSTUME("COSTUME", "有裝扮", "有裝扮的實際隻數"),
    FORM("FORM", "特殊型態", "非一般型態的實際隻數"),
    XXL("XXL", "特大", "特大尺寸的實際隻數"),
    XXS("XXS", "特小", "特小尺寸的實際隻數"),
    SPECIAL_MOVE("SPECIAL_MOVE", "有特招", "有特招的實際隻數"),
    UNTRADED("UNTRADED", "未交換", "尚未交換的實際隻數"),
    TRADED("TRADED", "已交換", "已經交換過的實際隻數"),
    MALE("MALE", "雄性", "雄性的實際隻數"),
    FEMALE("FEMALE", "雌性", "雌性的實際隻數"),
    SHADOW("SHADOW", "暗影", "暗影狀態的實際隻數"),
    PURIFIED("PURIFIED", "淨化", "淨化狀態的實際隻數"),
    DYNAMAX("DYNAMAX", "極巨化", "極巨化狀態的實際隻數"),
    GIGANTAMAX("GIGANTAMAX", "超極巨化", "超極巨化狀態的實際隻數"),
}

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
    countSource: List<VariantCardRow>,
) {
    CollectionFilterPanel(
        filters = state.filters,
        backgroundIds = state.backgroundIds,
        matchMode = state.matchMode,
        minimumMatches = state.minimumMatches,
        rememberFilters = state.rememberFilters,
        backgrounds = backgrounds,
        imageRepository = viewModel.imageRepository,
        countSource = countSource,
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
    val countSource = variants.filter { it.matchesQuery(query) }
    val filtered = countSource.filter { it.matches("", filterState.filters, filterState.backgroundIds, filterState.matchMode, filterState.minimumMatches) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ 返回") }
                PageTitle(
                    title = account?.name ?: "帳號收藏",
                    subtitle = "${variants.size} 種收藏組合 · ${variants.sumOf { it.totalQuantity }} 隻",
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
            CollectionFilterControls(filterState, viewModel, backgrounds, countSource)
            Spacer(Modifier.padding(8.dp))
            if (filtered.isEmpty()) {
                EmptyState("沒有符合的收藏", "這只表示目前沒有登記符合條件的收藏組合，不代表帳號沒有這隻 Pokémon。", "新增收藏") { showAdd = true }
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

private fun List<VariantCardRow>.summaryValue(metric: OverviewSummaryMetric): Long = when (metric) {
    OverviewSummaryMetric.COLLECTION_SET -> size.toLong()
    OverviewSummaryMetric.SPECIES_COUNT -> distinctBy { it.speciesId }.size.toLong()
    OverviewSummaryMetric.POKEMON_COUNT -> sumOf { it.totalQuantity }
    OverviewSummaryMetric.SHINY -> filter { it.isShiny }.sumOf { it.totalQuantity }
    OverviewSummaryMetric.BACKGROUND -> filter { it.backgroundId != "BACKGROUND_NONE" }.sumOf { it.totalQuantity }
    OverviewSummaryMetric.COSTUME -> filter { it.costumeName != "無裝扮" }.sumOf { it.totalQuantity }
    OverviewSummaryMetric.FORM -> filter { it.formName != "一般型態" }.sumOf { it.totalQuantity }
    OverviewSummaryMetric.XXL -> sumOf { it.xxlQuantity }
    OverviewSummaryMetric.XXS -> sumOf { it.xxsQuantity }
    OverviewSummaryMetric.SPECIAL_MOVE -> sumOf { it.specialMoveQuantity }
    OverviewSummaryMetric.UNTRADED -> sumOf { it.untradedQuantity }
    OverviewSummaryMetric.TRADED -> sumOf { it.tradedQuantity }
    OverviewSummaryMetric.MALE -> filter { it.gender == Gender.MALE }.sumOf { it.totalQuantity }
    OverviewSummaryMetric.FEMALE -> filter { it.gender == Gender.FEMALE }.sumOf { it.totalQuantity }
    OverviewSummaryMetric.SHADOW -> filter { it.shadowState.code == "SHADOW" }.sumOf { it.totalQuantity }
    OverviewSummaryMetric.PURIFIED -> filter { it.shadowState.code == "PURIFIED" }.sumOf { it.totalQuantity }
    OverviewSummaryMetric.DYNAMAX -> filter { it.dynamaxState.code == "DYNAMAX" }.sumOf { it.totalQuantity }
    OverviewSummaryMetric.GIGANTAMAX -> filter { it.dynamaxState.code == "GIGANTAMAX" }.sumOf { it.totalQuantity }
}

@Composable
private fun OverviewSummaryPanel(
    variants: List<VariantCardRow>,
    selectedMetrics: List<OverviewSummaryMetric>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onCustomize: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggleExpanded) {
                    Text(if (expanded) "總覽摘要 ▲" else "總覽摘要 ▼")
                }
                Text(
                    text = if (selectedMetrics.isEmpty()) "未選取" else "已選 ${selectedMetrics.size} 項",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCustomize) { Text("自訂顯示") }
            }
            if (expanded) {
                Text(
                    "收藏組合：型態、裝扮、背卡、異色、性別與特殊狀態完全相同的一組收藏；尺寸、特招、交換狀態只會統計在組合內。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selectedMetrics.isEmpty()) {
                    Text("目前沒有顯示摘要，請按「自訂顯示」選擇想看的項目。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState(), overscrollEffect = null),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        selectedMetrics.chunked(2).forEach { rowMetrics ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowMetrics.forEach { metric ->
                                    StatCard(
                                        label = metric.label,
                                        value = variants.summaryValue(metric).toString(),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (rowMetrics.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewSummaryPickerDialog(
    selectedCodes: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    var workingCodes by remember(selectedCodes) { mutableStateOf(selectedCodes) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 28.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("自訂總覽摘要", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "可選擇任意數量，摘要只統計目前選取帳號與篩選結果。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState(), overscrollEffect = null),
                ) {
                    OverviewSummaryMetric.entries.forEach { metric ->
                        val checked = metric.code in workingCodes
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    workingCodes = if (checked) workingCodes - metric.code else workingCodes + metric.code
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    workingCodes = if (isChecked) workingCodes + metric.code else workingCodes - metric.code
                                },
                            )
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(metric.label, fontWeight = FontWeight.Medium)
                                Text(
                                    metric.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { workingCodes = emptySet() }) { Text("全部清除") }
                    TextButton(onClick = { workingCodes = OverviewSummaryMetric.entries.map { it.code }.toSet() }) { Text("全部選取") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(onClick = { onSave(workingCodes); onDismiss() }) { Text("儲存") }
                }
            }
        }
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
    val summaryCodes by viewModel.overviewSummaryStatCodes.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var summaryExpanded by remember { mutableStateOf(true) }
    var showSummaryPicker by remember { mutableStateOf(false) }
    val filterState = rememberCollectionFilterUiState(viewModel)
    val countSource = variants.filter { it.matchesQuery(query) }
    val visible = countSource.filter { it.matches("", filterState.filters, filterState.backgroundIds, filterState.matchMode, filterState.minimumMatches) }
    val selectedSummaryMetrics = OverviewSummaryMetric.entries.filter { it.code in summaryCodes }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        PageTitle("總覽", subtitle = "跨帳號聚合收藏組合；每張卡代表一種完整組合，×數量是實際隻數。")
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
        CollectionFilterControls(filterState, viewModel, backgrounds, countSource)
        Spacer(Modifier.padding(8.dp))
        OverviewSummaryPanel(
            variants = visible,
            selectedMetrics = selectedSummaryMetrics,
            expanded = summaryExpanded,
            onToggleExpanded = { summaryExpanded = !summaryExpanded },
            onCustomize = { showSummaryPicker = true },
        )
        Spacer(Modifier.padding(8.dp))
        if (visible.isEmpty()) {
            EmptyState("沒有符合的收藏組合", "請調整帳號或篩選條件。未登記項目不會被自動當成未擁有。")
        } else {
            VariantGrid(visible, imageRepository = viewModel.imageRepository, onClick = { onOpenVariant(it.variantId) }, modifier = Modifier.weight(1f))
        }
    }

    if (showSummaryPicker) {
        OverviewSummaryPickerDialog(
            selectedCodes = summaryCodes,
            onDismiss = { showSummaryPicker = false },
            onSave = viewModel::saveOverviewSummaryStatCodes,
        )
    }
}

