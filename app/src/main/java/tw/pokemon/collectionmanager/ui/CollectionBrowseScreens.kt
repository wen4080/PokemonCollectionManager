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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
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
import tw.pokemon.collectionmanager.data.local.CollectionTagEntity
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
    FORM("FORM", "非一般型態", "排除極巨化與暗影狀態後，非一般型態的實際隻數"),
    XXL("XXL", "特大", "特大尺寸的實際隻數"),
    XXS("XXS", "特小", "特小尺寸的實際隻數"),
    SPECIAL_MOVE("SPECIAL_MOVE", "有特招", "有特招的實際隻數"),
    UNTRADED("UNTRADED", "未交換", "尚未交換的實際隻數"),
    TRADED("TRADED", "已交換", "已經交換過的實際隻數"),
    MALE("MALE", "公", "公的實際隻數"),
    FEMALE("FEMALE", "母", "母的實際隻數"),
    SHADOW("SHADOW", "暗影", "暗影狀態的實際隻數"),
    PURIFIED("PURIFIED", "淨化", "淨化狀態的實際隻數"),
    DYNAMAX("DYNAMAX", "極巨化", "極巨化狀態的實際隻數"),
    GIGANTAMAX("GIGANTAMAX", "超極巨化", "超極巨化狀態的實際隻數"),
}

private const val CUSTOM_SUMMARY_TAG_PREFIX = "TAG:"

private data class OverviewSummaryItem(
    val code: String,
    val label: String,
    val description: String,
    val metric: OverviewSummaryMetric? = null,
    val tagId: String? = null,
)

private fun OverviewSummaryMetric.asSummaryItem() = OverviewSummaryItem(
    code = code,
    label = label,
    description = description,
    metric = this,
)

private fun CollectionTagEntity.asSummaryItem() = OverviewSummaryItem(
    code = "$CUSTOM_SUMMARY_TAG_PREFIX$id",
    label = name,
    description = description ?: "自訂標籤收藏的實際隻數",
    tagId = id,
)

private class CollectionFilterUiState {
    var filters by mutableStateOf(emptySet<VariantFilterMode>())
    var backgroundIds by mutableStateOf(emptySet<String>())
    var tagIds by mutableStateOf(emptySet<String>())
    var matchMode by mutableStateOf(FilterMatchMode.ALL)
    var minimumMatches by mutableStateOf(2)
    var rememberFilters by mutableStateOf(false)

    fun save(viewModel: CollectionViewModel) {
        viewModel.saveCollectionFilters(
            SavedCollectionFilters(
                remember = rememberFilters,
                filterCodes = filters.map { it.name }.toSet(),
                backgroundIds = backgroundIds,
                tagIds = tagIds,
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
            state.tagIds = saved.tagIds
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
    customTags: List<CollectionTagEntity>,
    countSource: List<VariantCardRow>,
    tagIdsByVariant: Map<String, Set<String>>,
    allowInnerScroll: Boolean = true,
) {
    val availableTagIds = customTags.map { it.id }.toSet()
    val selectedTagIds = state.tagIds.intersect(availableTagIds)
    CollectionFilterPanel(
        filters = state.filters,
        backgroundIds = state.backgroundIds,
        customTags = customTags,
        selectedTagIds = selectedTagIds,
        tagIdsByVariant = tagIdsByVariant,
        matchMode = state.matchMode,
        minimumMatches = state.minimumMatches,
        rememberFilters = state.rememberFilters,
        backgrounds = backgrounds,
        imageRepository = viewModel.imageRepository,
        countSource = countSource,
        allowInnerScroll = allowInnerScroll,
        onFiltersChanged = { state.filters = it; state.save(viewModel) },
        onBackgroundsChanged = { state.backgroundIds = it; state.save(viewModel) },
        onTagsChanged = { state.tagIds = it; state.save(viewModel) },
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
    val customTags by viewModel.customTags.collectAsStateWithLifecycle()
    val tagAssignments by viewModel.tagAssignments.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val filterState = rememberCollectionFilterUiState(viewModel)
    var showAdd by remember { mutableStateOf(false) }
    val countSource = variants.filter { it.matchesQuery(query) }
    val tagIdsByVariant = tagAssignments.groupBy { it.variantId }.mapValues { (_, assignments) -> assignments.map { it.tagId }.toSet() }
    val filtered = countSource.filter {
        it.matches(
            query = "",
            filters = filterState.filters,
            backgroundIds = filterState.backgroundIds,
            matchMode = filterState.matchMode,
            minimumMatches = filterState.minimumMatches,
            customTagIds = filterState.tagIds,
            assignedTagIds = tagIdsByVariant[it.variantId].orEmpty(),
        )
    }
    val displayGroups = filtered.groupForDisplay()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ 返回") }
                PageTitle(
                    title = account?.name ?: "帳號收藏",
                    subtitle = "${displayGroups.size} 張收藏卡 · ${variants.sumOf { it.totalQuantity }} 隻",
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
            CollectionFilterControls(filterState, viewModel, backgrounds, customTags, countSource, tagIdsByVariant)
            Spacer(Modifier.padding(8.dp))
            if (filtered.isEmpty()) {
                EmptyState("沒有符合的收藏", "這只表示目前沒有登記符合條件的收藏組合，不代表帳號沒有這隻 Pokémon。", "新增收藏") { showAdd = true }
            } else {
                VariantGrid(
                    groups = displayGroups,
                    imageRepository = viewModel.imageRepository,
                    onClick = { onOpenVariant(it.representative.variantId) },
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
    OverviewSummaryMetric.COLLECTION_SET -> groupForDisplay().size.toLong()
    OverviewSummaryMetric.SPECIES_COUNT -> distinctBy { it.speciesId }.size.toLong()
    OverviewSummaryMetric.POKEMON_COUNT -> sumOf { it.totalQuantity }
    OverviewSummaryMetric.SHINY -> filter { it.isShiny }.sumOf { it.totalQuantity }
    OverviewSummaryMetric.BACKGROUND -> filter { it.backgroundId != "BACKGROUND_NONE" }.sumOf { it.totalQuantity }
    OverviewSummaryMetric.COSTUME -> filter { it.costumeName != "無裝扮" }.sumOf { it.totalQuantity }
    OverviewSummaryMetric.FORM -> filter { it.isNonDefaultCollectionForm() }.sumOf { it.totalQuantity }
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

private fun List<VariantCardRow>.summaryValue(
    item: OverviewSummaryItem,
    taggedVariantIdsByTag: Map<String, Set<String>>,
): Long = item.metric?.let { summaryValue(it) }
    ?: item.tagId?.let { tagId ->
        val taggedIds = taggedVariantIdsByTag[tagId].orEmpty()
        filter { it.variantId in taggedIds }.sumOf { it.totalQuantity }
    }
    ?: 0L

@Composable
private fun OverviewSummaryPanel(
    variants: List<VariantCardRow>,
    selectedMetrics: List<OverviewSummaryItem>,
    taggedVariantIdsByTag: Map<String, Set<String>>,
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
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        selectedMetrics.chunked(2).forEach { rowMetrics ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowMetrics.forEach { metric ->
                                    StatCard(
                                        label = metric.label,
                                        value = variants.summaryValue(metric, taggedVariantIdsByTag).toString(),
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
private fun CustomSummaryTagEditorDialog(
    existing: CollectionTagEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String?) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新增自訂摘要標籤" else "編輯自訂摘要標籤") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("標籤名稱") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("說明註解（可留白）") },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), description.trim().ifBlank { null }) },
                enabled = name.trim().isNotEmpty(),
            ) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun OverviewSummaryPickerDialog(
    selectedCodes: Set<String>,
    customTags: List<CollectionTagEntity>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
    onCreateTag: (String, String?, (CollectionTagEntity) -> Unit) -> Unit,
    onUpdateTag: (CollectionTagEntity) -> Unit,
    onDeleteTag: (CollectionTagEntity) -> Unit,
) {
    var workingCodes by remember(selectedCodes) { mutableStateOf(selectedCodes) }
    var editingTag by remember { mutableStateOf<CollectionTagEntity?>(null) }
    var showTagEditor by remember { mutableStateOf(false) }
    var pendingDeleteTag by remember { mutableStateOf<CollectionTagEntity?>(null) }

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
                    Text("自訂摘要標籤", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (customTags.isEmpty()) {
                        Text("尚未建立自訂標籤。建立後可在收藏組合詳細頁套用。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        customTags.forEach { tag ->
                            val code = "$CUSTOM_SUMMARY_TAG_PREFIX${tag.id}"
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = code in workingCodes,
                                    onCheckedChange = { checked ->
                                        workingCodes = if (checked) workingCodes + code else workingCodes - code
                                    },
                                )
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(tag.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        tag.description ?: "未提供說明",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { editingTag = tag; showTagEditor = true }) { Text("編輯") }
                                TextButton(onClick = { pendingDeleteTag = tag }) { Text("刪除") }
                            }
                        }
                    }
                    OutlinedButton(onClick = { editingTag = null; showTagEditor = true }) {
                        Text("新增自訂標籤")
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

    if (showTagEditor) {
        CustomSummaryTagEditorDialog(
            existing = editingTag,
            onDismiss = { showTagEditor = false },
            onSave = { name, description ->
                val existing = editingTag
                if (existing == null) {
                    onCreateTag(name, description) { created ->
                        workingCodes = workingCodes + "$CUSTOM_SUMMARY_TAG_PREFIX${created.id}"
                        showTagEditor = false
                    }
                } else {
                    onUpdateTag(existing.copy(name = name, description = description))
                    showTagEditor = false
                }
            },
        )
    }
    pendingDeleteTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTag = null },
            title = { Text("刪除自訂標籤？") },
            text = { Text("刪除「${tag.name}」後，套用在收藏組合上的標籤也會一併移除。") },
            confirmButton = {
                Button(onClick = {
                    onDeleteTag(tag)
                    workingCodes = workingCodes - "$CUSTOM_SUMMARY_TAG_PREFIX${tag.id}"
                    pendingDeleteTag = null
                }) { Text("刪除") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteTag = null }) { Text("取消") } },
        )
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
    val customTags by viewModel.customTags.collectAsStateWithLifecycle()
    val tagAssignments by viewModel.tagAssignments.collectAsStateWithLifecycle()
    val summaryCodes by viewModel.overviewSummaryStatCodes.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var summaryExpanded by remember { mutableStateOf(true) }
    var showSummaryPicker by remember { mutableStateOf(false) }
    val filterState = rememberCollectionFilterUiState(viewModel)
    val countSource = variants.filter { it.matchesQuery(query) }
    val tagIdsByVariant = tagAssignments.groupBy { it.variantId }.mapValues { (_, assignments) -> assignments.map { it.tagId }.toSet() }
    val visible = countSource.filter {
        it.matches(
            query = "",
            filters = filterState.filters,
            backgroundIds = filterState.backgroundIds,
            matchMode = filterState.matchMode,
            minimumMatches = filterState.minimumMatches,
            customTagIds = filterState.tagIds,
            assignedTagIds = tagIdsByVariant[it.variantId].orEmpty(),
        )
    }
    val displayGroups = visible.groupForDisplay()
    val selectedSummaryMetrics = buildList {
        OverviewSummaryMetric.entries.filter { it.code in summaryCodes }.forEach { add(it.asSummaryItem()) }
        customTags.filter { "$CUSTOM_SUMMARY_TAG_PREFIX${it.id}" in summaryCodes }.forEach { add(it.asSummaryItem()) }
    }
    val taggedVariantIdsByTag = tagAssignments.groupBy { it.tagId }.mapValues { (_, assignments) -> assignments.map { it.variantId }.toSet() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        overscrollEffect = null,
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            PageTitle("總覽", subtitle = "跨帳號合併相同性別以外的核心版本；×數量是實際隻數。")
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("帳號篩選", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LazyRow(
                    overscrollEffect = null,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
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
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜尋 Pokémon、型態或背景") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            CollectionFilterControls(
                state = filterState,
                viewModel = viewModel,
                backgrounds = backgrounds,
                customTags = customTags,
                countSource = countSource,
                tagIdsByVariant = tagIdsByVariant,
                allowInnerScroll = false,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OverviewSummaryPanel(
                variants = visible,
                selectedMetrics = selectedSummaryMetrics,
                taggedVariantIdsByTag = taggedVariantIdsByTag,
                expanded = summaryExpanded,
                onToggleExpanded = { summaryExpanded = !summaryExpanded },
                onCustomize = { showSummaryPicker = true },
            )
        }
        if (visible.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState("沒有符合的收藏組合", "請調整帳號或篩選條件。未登記項目不會被自動當成未擁有。")
            }
        } else {
            // variantId 是資料庫中的穩定字串 key，避免複合資料物件造成
            // Android Lazy Grid 在首次載入／狀態恢復時無法保存 key。
            gridItems(displayGroups, key = { it.representative.variantId }) { group ->
                VariantCardItem(group = group, imageRepository = viewModel.imageRepository, onClick = { onOpenVariant(group.representative.variantId) })
            }
        }
    }

    if (showSummaryPicker) {
        OverviewSummaryPickerDialog(
            selectedCodes = summaryCodes,
            customTags = customTags,
            onDismiss = { showSummaryPicker = false },
            onSave = viewModel::saveOverviewSummaryStatCodes,
            onCreateTag = viewModel::createCustomTag,
            onUpdateTag = viewModel::updateCustomTag,
            onDeleteTag = { viewModel.deleteCustomTag(it.id) },
        )
    }
}

