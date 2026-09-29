package tw.pokemon.collectionmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.util.Log
import kotlinx.coroutines.CancellationException
import tw.pokemon.collectionmanager.data.local.Gender
import tw.pokemon.collectionmanager.data.local.BackgroundEntity
import tw.pokemon.collectionmanager.data.local.VariantCardRow
import tw.pokemon.collectionmanager.data.repository.PokemonImageRepository

enum class VariantFilterMode(val label: String) {
    ALL("全部"),
    SHINY("異色"),
    BACKGROUND("背景"),
    COSTUME("裝扮"),
    FORM("型態"),
    XXL("特大"),
    XXS("特小"),
    SPECIAL_MOVE("特招"),
    AVAILABLE("可交換"),
    TRADED("已交換"),
    MALE("雄性"),
    FEMALE("雌性"),
    SHADOW("暗影"),
    PURIFIED("淨化"),
    DYNAMAX("極巨化"),
    GIGANTAMAX("超極巨化"),
}

enum class FilterMatchMode(val code: String, val label: String) {
    ALL("ALL", "全部條件符合"),
    ANY("ANY", "任一條件符合"),
    AT_LEAST("AT_LEAST", "至少符合 N 個");

    companion object {
        fun fromCode(code: String?): FilterMatchMode = entries.firstOrNull { it.code == code } ?: ALL
    }
}

fun VariantCardRow.matches(
    query: String,
    filters: Set<VariantFilterMode> = emptySet(),
    backgroundIds: Set<String> = emptySet(),
    matchMode: FilterMatchMode = FilterMatchMode.ALL,
    minimumMatches: Int = 2,
): Boolean {
    val normalized = query.trim()
    val textMatches = normalized.isBlank() || listOf(speciesName, formName, costumeName, backgroundName, backgroundCategoryName, dexNumber.toString())
        .any { it.contains(normalized, ignoreCase = true) }
    val activeFilters = filters - VariantFilterMode.ALL
    val exclusiveGroups = listOf(
        setOf(VariantFilterMode.XXL, VariantFilterMode.XXS),
        setOf(VariantFilterMode.AVAILABLE, VariantFilterMode.TRADED),
        setOf(VariantFilterMode.MALE, VariantFilterMode.FEMALE),
        setOf(VariantFilterMode.SHADOW, VariantFilterMode.PURIFIED),
        setOf(VariantFilterMode.DYNAMAX, VariantFilterMode.GIGANTAMAX),
    )
    val groupedFilters = exclusiveGroups.flatMap { group -> activeFilters.intersect(group) }.toSet()
    val criterionResults = (activeFilters - groupedFilters).map(::matchesFilter).toMutableList()
    exclusiveGroups.forEach { group ->
        val selected = activeFilters.intersect(group)
        if (selected.isNotEmpty()) criterionResults += selected.any(::matchesFilter)
    }
    if (backgroundIds.isNotEmpty()) criterionResults += backgroundId in backgroundIds
    val filterMatches = when {
        criterionResults.isEmpty() -> true
        matchMode == FilterMatchMode.ALL -> criterionResults.all { it }
        matchMode == FilterMatchMode.ANY -> criterionResults.any { it }
        else -> criterionResults.count { it } >= minimumMatches.coerceIn(1, criterionResults.size)
    }
    return textMatches && filterMatches
}

fun filterCriterionCount(filters: Set<VariantFilterMode>, backgroundIds: Set<String>): Int {
    val activeFilters = filters - VariantFilterMode.ALL
    val grouped = listOf(
        setOf(VariantFilterMode.XXL, VariantFilterMode.XXS),
        setOf(VariantFilterMode.AVAILABLE, VariantFilterMode.TRADED),
        setOf(VariantFilterMode.MALE, VariantFilterMode.FEMALE),
        setOf(VariantFilterMode.SHADOW, VariantFilterMode.PURIFIED),
        setOf(VariantFilterMode.DYNAMAX, VariantFilterMode.GIGANTAMAX),
    )
    val groupedSelections = grouped.count { activeFilters.intersect(it).isNotEmpty() }
    val groupedModes = grouped.flatten().toSet()
    return (activeFilters - groupedModes).size + groupedSelections + if (backgroundIds.isEmpty()) 0 else 1
}

private fun VariantCardRow.matchesFilter(filter: VariantFilterMode): Boolean = when (filter) {
    VariantFilterMode.ALL -> true
    VariantFilterMode.SHINY -> isShiny
    VariantFilterMode.BACKGROUND -> backgroundId != "BACKGROUND_NONE"
    VariantFilterMode.COSTUME -> costumeName != "無裝扮"
    VariantFilterMode.FORM -> formName != "一般型態"
    VariantFilterMode.XXL -> xxlQuantity > 0
    VariantFilterMode.XXS -> xxsQuantity > 0
    VariantFilterMode.SPECIAL_MOVE -> specialMoveQuantity > 0
    VariantFilterMode.AVAILABLE -> untradedQuantity > 0
    VariantFilterMode.TRADED -> tradedQuantity > 0
    VariantFilterMode.MALE -> gender == Gender.MALE
    VariantFilterMode.FEMALE -> gender == Gender.FEMALE
    VariantFilterMode.SHADOW -> shadowState.code == "SHADOW"
    VariantFilterMode.PURIFIED -> shadowState.code == "PURIFIED"
    VariantFilterMode.DYNAMAX -> dynamaxState.code == "DYNAMAX"
    VariantFilterMode.GIGANTAMAX -> dynamaxState.code == "GIGANTAMAX"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterBar(selected: Set<VariantFilterMode>, onSelected: (Set<VariantFilterMode>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("收藏條件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
        FilterChip(
            selected = selected.isEmpty(),
            onClick = { onSelected(emptySet()) },
            label = { Text("全部") },
        )
        VariantFilterMode.entries.filterNot { it == VariantFilterMode.ALL }.forEach { filter ->
            FilterChip(
                selected = filter in selected,
                onClick = { onSelected(if (filter in selected) selected - filter else selected + filter) },
                label = { Text(filter.label) },
            )
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionFilterPanel(
    filters: Set<VariantFilterMode>,
    backgroundIds: Set<String>,
    matchMode: FilterMatchMode,
    minimumMatches: Int,
    rememberFilters: Boolean,
    backgrounds: List<BackgroundEntity>,
    imageRepository: PokemonImageRepository,
    onFiltersChanged: (Set<VariantFilterMode>) -> Unit,
    onBackgroundsChanged: (Set<String>) -> Unit,
    onMatchModeChanged: (FilterMatchMode) -> Unit,
    onMinimumChanged: (Int) -> Unit,
    onRememberChanged: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val criterionCount = filterCriterionCount(filters, backgroundIds)
    val effectiveMinimum = minimumMatches.coerceIn(1, criterionCount.coerceAtLeast(1))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f)) {
                    Text(if (expanded) "收合篩選條件 ▲" else "展開篩選條件 ▼")
                }
                Text(if (criterionCount == 0) "未套用" else "$criterionCount 個條件", style = MaterialTheme.typography.labelMedium)
            }
            AnimatedVisibility(expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState(), overscrollEffect = null)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilterBar(filters, onFiltersChanged)
                    BackgroundFilterRow(backgroundIds, backgrounds, imageRepository, onBackgroundsChanged)
                    Text("條件配對方式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterMatchMode.entries.forEach { mode ->
                            FilterChip(selected = matchMode == mode, onClick = { onMatchModeChanged(mode) }, label = { Text(mode.label) })
                        }
                    }
                    if (matchMode == FilterMatchMode.AT_LEAST) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("至少符合")
                            TextButton(onClick = { onMinimumChanged((effectiveMinimum - 1).coerceAtLeast(1)) }) { Text("－") }
                            Text(effectiveMinimum.toString(), fontWeight = FontWeight.Bold)
                            TextButton(onClick = { onMinimumChanged((effectiveMinimum + 1).coerceAtMost(criterionCount.coerceAtLeast(1))) }) { Text("＋") }
                            Text("個條件")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("記住篩選條件")
                            Text("下次開啟收藏或總覽時沿用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = rememberFilters, onCheckedChange = onRememberChanged)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BackgroundFilterRow(
    selectedIds: Set<String>,
    backgrounds: List<BackgroundEntity>,
    imageRepository: PokemonImageRepository,
    onSelected: (Set<String>) -> Unit,
) {
    if (backgrounds.isEmpty()) return
    var showPicker by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("背景條件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = selectedIds.isEmpty(), onClick = { onSelected(emptySet()) }, label = { Text("背景：全部") })
        androidx.compose.material3.AssistChip(
            onClick = { showPicker = true },
            label = { Text(if (selectedIds.isEmpty()) "選擇多個背景" else "已選 ${selectedIds.size} 張背景") },
        )
        if (selectedIds.isNotEmpty()) {
            androidx.compose.material3.AssistChip(onClick = { onSelected(emptySet()) }, label = { Text("清除背景") })
        }
        }
    }
    if (showPicker) {
        BackgroundPickerSheet(
            imageRepository = imageRepository,
            backgrounds = backgrounds,
            verifiedIds = emptySet(),
            selectedIds = selectedIds,
            multiple = true,
            onSelectionChanged = onSelected,
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
fun VariantGrid(
    variants: List<VariantCardRow>,
    imageRepository: PokemonImageRepository,
    onClick: (VariantCardRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(150.dp),
        modifier = modifier,
        overscrollEffect = null,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        gridItems(variants, key = { it.variantId }) { variant ->
            VariantCardItem(variant = variant, imageRepository = imageRepository, onClick = { onClick(variant) })
        }
    }
}

@Composable
fun VariantCardItem(variant: VariantCardRow, imageRepository: PokemonImageRepository, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (variant.backgroundId != "BACKGROUND_NONE") {
                    BackgroundLayer(
                        imageRepository = imageRepository,
                        backgroundId = variant.backgroundId,
                        label = variant.backgroundName,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                PokemonArtwork(
                    imageRepository = imageRepository,
                    speciesId = variant.speciesId,
                    formId = variant.formId,
                    costumeId = variant.costumeId,
                    shiny = variant.isShiny,
                    label = variant.speciesName,
                )
                if (variant.totalQuantity > 1) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.inverseSurface,
                    ) {
                        Text(
                            text = "×${variant.totalQuantity}",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = variant.speciesName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "#${variant.dexNumber} · ${variant.formName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BadgeRow(variant)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun BadgeRow(variant: VariantCardRow, compact: Boolean = true) {
    val badges = buildList {
        if (variant.isShiny) add("✨ 異色")
        if (variant.gender == Gender.MALE) add("♂")
        if (variant.gender == Gender.FEMALE) add("♀")
        if (variant.backgroundId != "BACKGROUND_NONE") add("🌆 ${variant.backgroundCategoryName}")
        if (variant.costumeName != "無裝扮") add("🎩 ${variant.costumeName}")
        if (variant.shadowState.code != "NORMAL") add("🌑 ${variant.shadowState.label}")
        if (variant.dynamaxState.code != "NONE") add("⚡ ${variant.dynamaxState.label}")
        if (variant.xxlQuantity > 0) add("特大 ×${variant.xxlQuantity}")
        if (variant.xxsQuantity > 0) add("特小 ×${variant.xxsQuantity}")
        if (variant.specialMoveQuantity > 0) add("特招 ×${variant.specialMoveQuantity}")
    }
    if (badges.isNotEmpty()) {
        // 卡片內不再放水平可滾動列。非預設版本一定會產生徽章，
        // 讓徽章換行可避免巢狀滾動造成快速滑動時的邊界回彈或版面例外。
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            badges.take(if (compact) 3 else badges.size).forEach { badge ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
fun PokemonArtwork(
    imageRepository: PokemonImageRepository,
    speciesId: String,
    formId: String? = null,
    costumeId: String? = null,
    shiny: Boolean,
    label: String,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, speciesId, formId, costumeId, shiny) {
        try {
            val reference = imageRepository.getImage(speciesId, formId, costumeId, shiny)
            value = imageRepository.loadBitmap(reference, maxDimension = 384)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Log.w("收藏畫面", "寶可夢圖片顯示失敗：$speciesId", error)
            value = null
        }
    }
    val loadedBitmap = bitmap
    if (loadedBitmap != null) {
        Image(
            bitmap = loadedBitmap.asImageBitmap(),
            contentDescription = label,
            modifier = modifier.size(132.dp),
        )
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
            Text(text = if (shiny) "✨" else "◉", style = MaterialTheme.typography.displayMedium)
            Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(text = "圖片尚未提供", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BackgroundArtwork(
    imageRepository: PokemonImageRepository,
    backgroundId: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, backgroundId) {
        try {
            val reference = imageRepository.getBackgroundImage(backgroundId)
            value = imageRepository.loadBitmap(reference, maxDimension = 192)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Log.w("收藏畫面", "背景縮圖顯示失敗：$backgroundId", error)
            value = null
        }
    }
    val loadedBitmap = bitmap
    if (loadedBitmap != null) {
        Image(
            bitmap = loadedBitmap.asImageBitmap(),
            contentDescription = label,
            modifier = modifier.size(72.dp),
        )
    } else {
        Box(modifier = modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun BackgroundLayer(
    imageRepository: PokemonImageRepository,
    backgroundId: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, backgroundId) {
        try {
            val reference = imageRepository.getBackgroundImage(backgroundId)
            value = imageRepository.loadBitmap(reference, maxDimension = 512)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Log.w("收藏畫面", "背景圖層顯示失敗：$backgroundId", error)
            value = null
        }
    }
    bitmap?.let { loadedBitmap ->
        Image(
            bitmap = loadedBitmap.asImageBitmap(),
            contentDescription = label,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
fun PageTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionTitle(title: String, action: (@Composable () -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
fun HorizontalChoices(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

