package tw.pokemon.collectionmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import tw.pokemon.collectionmanager.data.local.BackgroundEntity
import tw.pokemon.collectionmanager.data.local.CollectionVariantEntity
import tw.pokemon.collectionmanager.data.local.CostumeEntity
import tw.pokemon.collectionmanager.data.local.DynamaxState
import tw.pokemon.collectionmanager.data.local.Gender
import tw.pokemon.collectionmanager.data.local.OwnershipBucketEntity
import tw.pokemon.collectionmanager.data.local.PokemonFormEntity
import tw.pokemon.collectionmanager.data.local.PokemonSpeciesEntity
import tw.pokemon.collectionmanager.data.local.ShadowState
import tw.pokemon.collectionmanager.data.local.SizeType
import tw.pokemon.collectionmanager.data.local.TradeState
import tw.pokemon.collectionmanager.data.local.VariantInfoRow
import tw.pokemon.collectionmanager.domain.BucketDraft
import tw.pokemon.collectionmanager.domain.VariantDraft
import java.time.LocalDate

private const val NO_COSTUME_ID = "COSTUME_NONE"
private const val NO_BACKGROUND_ID = "BACKGROUND_NONE"

private enum class BackgroundBrowseMode { BY_YEAR, OVERVIEW }

internal fun backgroundEventListKey(eventKey: String, eventName: String?): String =
    "$eventKey\u0000${eventName.orEmpty()}"

internal fun backgroundCategoryListKey(categoryKey: String, categoryName: String): String =
    "$categoryKey\u0000$categoryName"

internal enum class BackgroundPickerBackTarget {
    EVENT_LIST,
    YEAR_LIST,
    DISMISS,
}

internal fun backgroundPickerBackTarget(
    selectedYear: Int?,
    selectedEventKey: String?,
): BackgroundPickerBackTarget = when {
    selectedEventKey != null -> BackgroundPickerBackTarget.EVENT_LIST
    selectedYear != null -> BackgroundPickerBackTarget.YEAR_LIST
    else -> BackgroundPickerBackTarget.DISMISS
}

private enum class AddStep {
    PICK_POKEMON,
    CONFIGURE_VARIANT,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    viewModel: CollectionViewModel,
    accountId: String?,
    initialInfo: VariantInfoRow? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {},
) {
    val allSpecies by viewModel.species.collectAsStateWithLifecycle()
    val costumes by viewModel.costumes.collectAsStateWithLifecycle()
    val backgrounds by viewModel.backgrounds.collectAsStateWithLifecycle()
    var step by remember(initialInfo?.variantId) {
        mutableStateOf(if (initialInfo == null) AddStep.PICK_POKEMON else AddStep.CONFIGURE_VARIANT)
    }
    var searchText by remember { mutableStateOf("") }
    var generation by remember { mutableStateOf<Int?>(null) }
    var speciesId by remember(initialInfo?.variantId) { mutableStateOf(initialInfo?.speciesId.orEmpty()) }
    var formId by remember(initialInfo?.variantId) { mutableStateOf(initialInfo?.formId.orEmpty()) }
    var costumeId by remember(initialInfo?.variantId) { mutableStateOf(initialInfo?.costumeId ?: NO_COSTUME_ID) }
    var backgroundId by remember(initialInfo?.variantId) { mutableStateOf(initialInfo?.backgroundId ?: NO_BACKGROUND_ID) }
    var shiny by remember(initialInfo?.variantId) { mutableStateOf(initialInfo?.isShiny ?: false) }
    var gender by remember(initialInfo?.variantId) { mutableStateOf(initialInfo?.gender ?: Gender.UNKNOWN) }
    var shadow by remember(initialInfo?.variantId) { mutableStateOf(initialInfo?.shadowState ?: ShadowState.NORMAL) }
    var dynamax by remember(initialInfo?.variantId) { mutableStateOf(initialInfo?.dynamaxState ?: DynamaxState.NONE) }
    var size by remember { mutableStateOf(SizeType.NORMAL) }
    var specialMove by remember { mutableStateOf(false) }
    var tradeState by remember { mutableStateOf(TradeState.UNTRADED) }
    var quantityText by remember { mutableStateOf("1") }
    var showCostumePicker by remember { mutableStateOf(false) }
    var showBackgroundPicker by remember { mutableStateOf(false) }

    val pickerFlow = remember(searchText, generation) { viewModel.searchSpecies(searchText, generation) }
    val pickerSpecies by pickerFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedSpecies = allSpecies.firstOrNull { it.id == speciesId }
    val formsFlow = remember(speciesId) { if (speciesId.isBlank()) flowOf(emptyList()) else viewModel.forms(speciesId) }
    val forms by formsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val costumeCompatibilityFlow = remember(speciesId, formId) {
        if (speciesId.isBlank() || formId.isBlank()) flowOf(emptyList()) else viewModel.costumeCompatibility(speciesId, formId)
    }
    val costumeCompatibility by costumeCompatibilityFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val backgroundCompatibilityFlow = remember(speciesId, formId) {
        if (speciesId.isBlank() || formId.isBlank()) flowOf(emptyList()) else viewModel.backgroundCompatibility(speciesId, formId)
    }
    val backgroundCompatibility by backgroundCompatibilityFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val costumeOptions = remember(costumes, costumeCompatibility) {
        val linked = costumeCompatibility.map { it.costumeId }.toSet()
        costumes.filter { it.id == NO_COSTUME_ID || it.id in linked }
    }

    LaunchedEffect(forms, initialInfo?.variantId) {
        if (forms.isNotEmpty() && forms.none { it.id == formId }) {
            formId = forms.firstOrNull { it.isDefault }?.id ?: forms.first().id
        }
    }
    LaunchedEffect(selectedSpecies) {
        if (selectedSpecies != null && step == AddStep.CONFIGURE_VARIANT && formId.isBlank()) {
            formId = forms.firstOrNull { it.isDefault }?.id.orEmpty()
        }
    }
    LaunchedEffect(costumeOptions, costumeId) {
        if (costumeOptions.isNotEmpty() && costumeOptions.none { it.id == costumeId }) {
            costumeId = NO_COSTUME_ID
        }
    }
    LaunchedEffect(formId, forms) {
        when (forms.firstOrNull { it.id == formId }?.formKey) {
            "GIGANTAMAX" -> dynamax = DynamaxState.GIGANTAMAX
            "DYNAMAX" -> dynamax = DynamaxState.DYNAMAX
            else -> if (dynamax == DynamaxState.GIGANTAMAX) dynamax = DynamaxState.NONE
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            BackHandler(enabled = !showCostumePicker && !showBackgroundPicker) {
                if (step == AddStep.CONFIGURE_VARIANT && initialInfo == null) {
                    step = AddStep.PICK_POKEMON
                } else {
                    onDismiss()
                }
            }

            if (accountId == null && initialInfo == null) {
                EmptyState("尚未選擇帳號", "請先建立並選擇一個帳號，才能新增收藏。")
                Spacer(Modifier.height(24.dp))
            } else if (step == AddStep.PICK_POKEMON) {
                PokemonPicker(
                    imageRepository = viewModel.imageRepository,
                    species = pickerSpecies,
                    searchText = searchText,
                    generation = generation,
                    onSearchTextChanged = { searchText = it },
                    onGenerationChanged = { generation = it },
                    onSelected = { selected ->
                        speciesId = selected.id
                        formId = ""
                        costumeId = NO_COSTUME_ID
                        backgroundId = NO_BACKGROUND_ID
                        step = AddStep.CONFIGURE_VARIANT
                    },
                )
            } else {
                VariantConfiguration(
                    viewModel = viewModel,
                    selectedSpecies = selectedSpecies,
                    forms = forms,
                    selectedFormId = formId,
                    onFormSelected = { formId = it },
                    costumeOptions = costumeOptions,
                    selectedCostumeId = costumeId,
                    onCostumePicker = { showCostumePicker = true },
                    backgrounds = backgrounds,
                    backgroundCompatibilityCount = backgroundCompatibility.size,
                    selectedBackgroundId = backgroundId,
                    onBackgroundPicker = { showBackgroundPicker = true },
                    shiny = shiny,
                    onShinyChanged = { shiny = it },
                    gender = gender,
                    onGenderChanged = { gender = it },
                    shadow = shadow,
                    onShadowChanged = { shadow = it },
                    dynamax = dynamax,
                    onDynamaxChanged = { dynamax = it },
                    size = size,
                    onSizeChanged = { size = it },
                    specialMove = specialMove,
                    onSpecialMoveChanged = { specialMove = it },
                    tradeState = tradeState,
                    onTradeStateChanged = { tradeState = it },
                    quantityText = quantityText,
                    onQuantityChanged = { quantityText = it.filter(Char::isDigit).take(5) },
                    isEditing = initialInfo != null,
                    canSave = accountId != null && speciesId.isNotBlank() && formId.isNotBlank() && (quantityText.toIntOrNull() ?: 0) > 0,
                    onBack = if (initialInfo == null) ({ step = AddStep.PICK_POKEMON }) else null,
                    onCancel = onDismiss,
                    onSave = {
                        val quantity = quantityText.toIntOrNull() ?: 0
                        if (accountId != null && quantity > 0) {
                            if (initialInfo == null) {
                                viewModel.addCollection(
                                    accountId,
                                    VariantDraft(speciesId, formId, costumeId, backgroundId, shiny, gender, shadow, dynamax),
                                    BucketDraft(size, specialMove, tradeState),
                                    quantity,
                                )
                                onDismiss()
                            } else {
                                viewModel.updateVariant(initialInfo.variantId, accountId, VariantDraft(speciesId, formId, costumeId, backgroundId, shiny, gender, shadow, dynamax))
                                onSaved()
                                onDismiss()
                            }
                        }
                    },
                    onSaveAndContinue = if (initialInfo == null) {
                        {
                            val quantity = quantityText.toIntOrNull() ?: 0
                            if (accountId != null && quantity > 0) {
                                viewModel.addCollection(
                                    accountId,
                                    VariantDraft(speciesId, formId, costumeId, backgroundId, shiny, gender, shadow, dynamax),
                                    BucketDraft(size, specialMove, tradeState),
                                    quantity,
                                    continueAdding = true,
                                )
                                searchText = ""
                                generation = null
                                speciesId = ""
                                formId = ""
                                costumeId = NO_COSTUME_ID
                                backgroundId = NO_BACKGROUND_ID
                                quantityText = "1"
                                step = AddStep.PICK_POKEMON
                            }
                        }
                    } else null,
                )
            }
        }
    }

    if (showCostumePicker && selectedSpecies != null) {
        CostumePickerSheet(
            imageRepository = viewModel.imageRepository,
            species = selectedSpecies,
            formId = formId,
            costumes = costumeOptions,
            selectedId = costumeId,
            shiny = shiny,
            onSelected = { costumeId = it; showCostumePicker = false },
            onDismiss = { showCostumePicker = false },
        )
    }

    if (showBackgroundPicker) {
        BackgroundPickerSheet(
            imageRepository = viewModel.imageRepository,
            backgrounds = backgrounds,
            verifiedIds = backgroundCompatibility.filter { it.isVerified }.map { it.backgroundId }.toSet(),
            selectedIds = setOf(backgroundId),
            multiple = false,
            onSelectionChanged = { ids -> backgroundId = ids.firstOrNull() ?: NO_BACKGROUND_ID },
            onDismiss = { showBackgroundPicker = false },
        )
    }
}

@Composable
private fun PokemonPicker(
    imageRepository: tw.pokemon.collectionmanager.data.repository.PokemonImageRepository,
    species: List<PokemonSpeciesEntity>,
    searchText: String,
    generation: Int?,
    onSearchTextChanged: (String) -> Unit,
    onGenerationChanged: (Int?) -> Unit,
    onSelected: (PokemonSpeciesEntity) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("選擇 Pokémon", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChanged,
            label = { Text("搜尋 Pokémon 名稱或圖鑑編號") },
            placeholder = { Text("例如：繁中名稱、英文名稱、384") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyRow(overscrollEffect = null, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(selected = generation == null, onClick = { onGenerationChanged(null) }, label = { Text("全部世代") })
            }
            items((1..9).toList()) { item ->
                FilterChip(selected = generation == item, onClick = { onGenerationChanged(item) }, label = { Text("第${item}世代") })
            }
        }
        if (species.isEmpty()) {
            EmptyState("沒有找到 Pokémon", "請改用繁體中文名稱、英文名稱或圖鑑編號搜尋。")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(118.dp),
                overscrollEffect = null,
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                gridItems(species, key = { it.id }) { item ->
                    PokemonPickerCard(imageRepository, item, onClick = { onSelected(item) })
                }
            }
        }
    }
}

@Composable
private fun PokemonPickerCard(
    imageRepository: tw.pokemon.collectionmanager.data.repository.PokemonImageRepository,
    item: PokemonSpeciesEntity,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
            PokemonArtwork(
                imageRepository = imageRepository,
                speciesId = item.id,
                shiny = false,
                label = item.nameZhTw,
            )
            Text("#${item.dexNumber}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(item.nameZhTw, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.nameEn, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun VariantConfiguration(
    viewModel: CollectionViewModel,
    selectedSpecies: PokemonSpeciesEntity?,
    forms: List<PokemonFormEntity>,
    selectedFormId: String,
    onFormSelected: (String) -> Unit,
    costumeOptions: List<CostumeEntity>,
    selectedCostumeId: String,
    onCostumePicker: () -> Unit,
    backgrounds: List<BackgroundEntity>,
    backgroundCompatibilityCount: Int,
    selectedBackgroundId: String,
    onBackgroundPicker: () -> Unit,
    shiny: Boolean,
    onShinyChanged: (Boolean) -> Unit,
    gender: Gender,
    onGenderChanged: (Gender) -> Unit,
    shadow: ShadowState,
    onShadowChanged: (ShadowState) -> Unit,
    dynamax: DynamaxState,
    onDynamaxChanged: (DynamaxState) -> Unit,
    size: SizeType,
    onSizeChanged: (SizeType) -> Unit,
    specialMove: Boolean,
    onSpecialMoveChanged: (Boolean) -> Unit,
    tradeState: TradeState,
    onTradeStateChanged: (TradeState) -> Unit,
    quantityText: String,
    onQuantityChanged: (String) -> Unit,
    isEditing: Boolean,
    canSave: Boolean,
    onBack: (() -> Unit)?,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onSaveAndContinue: (() -> Unit)?,
) {
    val selectedForm = forms.firstOrNull { it.id == selectedFormId }
    val selectedCostume = costumeOptions.firstOrNull { it.id == selectedCostumeId }
    val selectedBackground = backgrounds.firstOrNull { it.id == selectedBackgroundId }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize()
            .verticalScroll(rememberScrollState(), overscrollEffect = null)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            onBack?.let { TextButton(onClick = it) { Text("‹ 返回") } }
            Text(if (isEditing) "編輯收藏版本" else "設定收藏版本", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        if (selectedSpecies == null) {
            EmptyState("尚未選擇 Pokémon", "請返回上一頁選擇 Pokémon。")
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Box(
                        modifier = Modifier.size(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selectedBackgroundId != NO_BACKGROUND_ID && selectedBackground != null) {
                            BackgroundLayer(
                                imageRepository = viewModel.imageRepository,
                                backgroundId = selectedBackground.id,
                                label = selectedBackground.displayName,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        PokemonArtwork(
                            imageRepository = viewModel.imageRepository,
                            speciesId = selectedSpecies.id,
                            formId = selectedFormId,
                            costumeId = selectedCostumeId,
                            shiny = shiny,
                            label = selectedSpecies.nameZhTw,
                        )
                    }
                    Text("#${selectedSpecies.dexNumber} · ${selectedSpecies.nameZhTw}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(selectedSpecies.nameEn, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        if (shiny) Text("✨ 異色", style = MaterialTheme.typography.labelMedium)
                        if (selectedBackgroundId != NO_BACKGROUND_ID) Text("背景：${selectedBackground?.displayName ?: "已選擇"}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Text("型態", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (forms.size <= 1) {
                Text(selectedForm?.displayName ?: "一般型態", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyRow(overscrollEffect = null, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(forms, key = { it.id }) { form ->
                        Card(
                            modifier = Modifier.width(130.dp).clickable { onFormSelected(form.id) },
                            colors = CardDefaults.cardColors(containerColor = if (form.id == selectedFormId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                PokemonArtwork(viewModel.imageRepository, selectedSpecies.id, form.id, NO_COSTUME_ID, shiny, form.displayName)
                                Text(form.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            Text("裝扮", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (costumeOptions.none { it.id != NO_COSTUME_ID }) {
                Text("這隻 Pokémon 目前沒有已收錄的裝扮。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    "目前選擇：${selectedCostume?.displayName ?: "無裝扮"}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                OutlinedButton(onClick = onCostumePicker, modifier = Modifier.fillMaxWidth()) {
                    Text(if (selectedCostumeId == NO_COSTUME_ID) "瀏覽 ${costumeOptions.count { it.id != NO_COSTUME_ID }} 種裝扮" else "更換裝扮")
                }
                Text("裝扮依推出年份由新到舊排列；無裝扮固定放在最前面。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("背景", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onBackgroundPicker, modifier = Modifier.fillMaxWidth()) {
                Text(selectedBackground?.displayName ?: "選擇背景")
            }
            if (backgroundCompatibilityCount == 0) {
                Text("尚無可靠的適用關係資料，選擇器會列出目前已知背景。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("版本狀態", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("是否異色", style = MaterialTheme.typography.labelLarge)
            HorizontalChoices {
                ChoiceChip("一般", !shiny) { onShinyChanged(false) }
                ChoiceChip("✨ 異色", shiny) { onShinyChanged(true) }
            }
            Text("性別", style = MaterialTheme.typography.labelLarge)
            HorizontalChoices {
                Gender.entries.forEach { value -> ChoiceChip(value.label, gender == value) { onGenderChanged(value) } }
            }
            Text("暗影／淨化狀態", style = MaterialTheme.typography.labelLarge)
            HorizontalChoices {
                ShadowState.entries.forEach { value -> ChoiceChip(value.label, shadow == value) { onShadowChanged(value) } }
            }
            Text("極巨化狀態", style = MaterialTheme.typography.labelLarge)
            when (selectedForm?.formKey) {
                "GIGANTAMAX" -> Text("超極巨化（已由所選型態自動設定）", color = MaterialTheme.colorScheme.primary)
                "DYNAMAX" -> Text("極巨化（已由所選型態自動設定）", color = MaterialTheme.colorScheme.primary)
                else -> HorizontalChoices {
                    DynamaxState.entries.forEach { value -> ChoiceChip(value.label, dynamax == value) { onDynamaxChanged(value) } }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("佔有數量", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("尺寸、特招與交換狀態只會合併數量，不會建立新的收藏版本。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalChoices { SizeType.entries.forEach { value -> ChoiceChip(value.label, size == value) { onSizeChanged(value) } } }
            HorizontalChoices {
                ChoiceChip("無特招", !specialMove) { onSpecialMoveChanged(false) }
                ChoiceChip("⚡ 有特招", specialMove) { onSpecialMoveChanged(true) }
            }
            HorizontalChoices { TradeState.entries.forEach { value -> ChoiceChip(value.label, tradeState == value) { onTradeStateChanged(value) } } }
            OutlinedTextField(
                value = quantityText,
                onValueChange = onQuantityChanged,
                label = { Text("數量") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消", maxLines = 1) }
                    Button(onClick = onSave, enabled = canSave, modifier = Modifier.weight(1f)) { Text("儲存", maxLines = 1) }
                }
                onSaveAndContinue?.let { action ->
                    Button(onClick = action, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
                        Text("儲存並繼續", maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CostumePickerSheet(
    imageRepository: tw.pokemon.collectionmanager.data.repository.PokemonImageRepository,
    species: PokemonSpeciesEntity,
    formId: String,
    costumes: List<CostumeEntity>,
    selectedId: String,
    shiny: Boolean,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visible = costumes.filter { costume ->
        query.isBlank() || listOfNotNull(costume.displayName, costume.costumeKey, costume.eventName)
            .any { value -> value.contains(query, ignoreCase = true) }
    }.sortedWith(
        compareBy<CostumeEntity> { if (it.id == NO_COSTUME_ID) 0 else 1 }
            .thenByDescending { it.releaseYear ?: 0 }
            .thenBy { it.sortOrder }
            .thenBy { it.displayName },
    )
    val noCostume = visible.firstOrNull { it.id == NO_COSTUME_ID }
    val yearGroups = visible.filter { it.id != NO_COSTUME_ID }
        .groupBy { it.releaseYear }
        .toList()
        .sortedWith(compareByDescending<Pair<Int?, List<CostumeEntity>>> { it.first ?: 0 })
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        BackHandler(onBack = onDismiss)

        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.82f).padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("選擇裝扮", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${species.nameZhTw}目前有 ${costumes.count { it.id != NO_COSTUME_ID }} 種相容裝扮", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜尋裝扮、活動或年份") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (visible.isEmpty()) {
                EmptyState("沒有找到裝扮", "請改用裝扮名稱、活動名稱或年份搜尋。")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(128.dp),
                    overscrollEffect = null,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    noCostume?.let { costume ->
                        item(key = costume.id, span = { GridItemSpan(maxLineSpan) }) {
                            CostumeChoiceCard(imageRepository, species, formId, costume, shiny, selectedId, onSelected)
                        }
                    }
                    yearGroups.forEach { (year, yearItems) ->
                        item(key = "costume-year-${year ?: 0}", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                year?.let { "${it} 年推出" } ?: "推出年份待確認",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        gridItems(yearItems, key = { it.id }) { costume ->
                            CostumeChoiceCard(imageRepository, species, formId, costume, shiny, selectedId, onSelected)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CostumeChoiceCard(
    imageRepository: tw.pokemon.collectionmanager.data.repository.PokemonImageRepository,
    species: PokemonSpeciesEntity,
    formId: String,
    costume: CostumeEntity,
    shiny: Boolean,
    selectedId: String,
    onSelected: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSelected(costume.id) },
        colors = CardDefaults.cardColors(
            containerColor = if (costume.id == selectedId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
            PokemonArtwork(imageRepository, species.id, formId, costume.id, shiny, costume.displayName)
            Text(costume.displayName, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            costume.eventName?.takeIf { costume.id != NO_COSTUME_ID }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackgroundPickerSheet(
    imageRepository: tw.pokemon.collectionmanager.data.repository.PokemonImageRepository,
    backgrounds: List<BackgroundEntity>,
    verifiedIds: Set<String>,
    selectedIds: Set<String>,
    multiple: Boolean,
    onSelectionChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(BackgroundBrowseMode.BY_YEAR) }
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var selectedEventKey by remember { mutableStateOf<String?>(null) }
    var selectedCategoryKey by remember { mutableStateOf<String?>(null) }
    val noBackground = backgrounds.firstOrNull { it.id == NO_BACKGROUND_ID }
    val realBackgrounds = backgrounds.filter { it.id != NO_BACKGROUND_ID }
    val visible = realBackgrounds.filter {
        query.isBlank() || listOfNotNull(it.displayName, it.backgroundKey, it.categoryName, it.eventName, it.locationName, it.year?.toString()).any { value -> value.contains(query, ignoreCase = true) }
    }.filter { selectedCategoryKey == null || it.categoryKey == selectedCategoryKey }
    val yearGroups = realBackgrounds.groupBy { it.year ?: 0 }.toList().sortedByDescending { it.first }
    val categoryGroups = realBackgrounds.groupBy { it.categoryKey to it.categoryName }.toList().sortedBy { it.first.second }
    fun handleBack() {
        when (backgroundPickerBackTarget(selectedYear, selectedEventKey)) {
            BackgroundPickerBackTarget.EVENT_LIST -> selectedEventKey = null
            BackgroundPickerBackTarget.YEAR_LIST -> selectedYear = null
            BackgroundPickerBackTarget.DISMISS -> onDismiss()
        }
    }
    fun toggleSelection(id: String) {
        if (!multiple) {
            onSelectionChanged(setOf(id))
            onDismiss()
        } else {
            onSelectionChanged(if (id in selectedIds) selectedIds - id else selectedIds + id)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        // 背景選擇器使用獨立的對話框視窗，返回處理器必須放在視窗內，
        // Android 返回手勢才能回到目前的選擇層級，而不是被對話框吃掉。
        BackHandler(onBack = ::handleBack)

        Column(Modifier.fillMaxWidth().fillMaxHeight(0.82f).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (multiple) "篩選多個背景" else "選擇背景", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (multiple) TextButton(onClick = onDismiss) { Text("完成（${selectedIds.size}）") }
            }
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("搜尋背景、活動、城市或年份") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            noBackground?.let { item ->
                OutlinedButton(onClick = { toggleSelection(item.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (item.id in selectedIds) "✓ 已選：無背景" else "選擇無背景")
                }
            }
            if (multiple && selectedIds.isNotEmpty()) {
                OutlinedButton(onClick = { onSelectionChanged(emptySet()) }, modifier = Modifier.fillMaxWidth()) { Text("清除全部背景條件") }
            }
            HorizontalChoices {
                ChoiceChip("依年度選擇", mode == BackgroundBrowseMode.BY_YEAR) {
                    mode = BackgroundBrowseMode.BY_YEAR; selectedCategoryKey = null
                }
                ChoiceChip("總覽與篩選", mode == BackgroundBrowseMode.OVERVIEW) {
                    mode = BackgroundBrowseMode.OVERVIEW; selectedYear = null; selectedEventKey = null
                }
            }

            if (query.isNotBlank()) {
                Text("搜尋結果：${visible.size} 張背卡", style = MaterialTheme.typography.labelLarge)
                BackgroundChoiceList(imageRepository, visible, verifiedIds, selectedIds, ::toggleSelection, Modifier.weight(1f))
            } else if (mode == BackgroundBrowseMode.BY_YEAR && selectedYear == null) {
                Text("先選年份", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(116.dp),
                    overscrollEffect = null,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    gridItems(yearGroups, key = { it.first }) { (year, items) ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selectedYear = year; selectedEventKey = null },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (year == 0) "年份待確認" else "${year} 年", fontWeight = FontWeight.Bold)
                                Text("${items.size} 張背卡", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            } else if (mode == BackgroundBrowseMode.BY_YEAR && selectedEventKey == null) {
                val year = selectedYear ?: 0
                val events = realBackgrounds.filter { (it.year ?: 0) == year }
                    .groupBy { it.eventKey to (it.eventName ?: it.categoryName) }
                    .toList().sortedBy { it.first.second }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selectedYear = null }) { Text("‹ 年份") }
                    Text(if (year == 0) "年份待確認" else "${year} 年的活動", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                LazyColumn(overscrollEffect = null, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp), modifier = Modifier.weight(1f)) {
                    items(events, key = { backgroundEventListKey(it.first.first, it.first.second) }) { (event, cards) ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedEventKey = backgroundEventListKey(event.first, event.second)
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(event.second, fontWeight = FontWeight.Bold)
                                Text("${cards.first().categoryName} · ${cards.size} 張背卡", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else if (mode == BackgroundBrowseMode.BY_YEAR) {
                val eventCards = realBackgrounds.filter {
                    (it.year ?: 0) == (selectedYear ?: 0) &&
                        backgroundEventListKey(it.eventKey, it.eventName ?: it.categoryName) == selectedEventKey
                }
                    .sortedWith(compareBy<BackgroundEntity> { it.sortOrder }.thenBy { it.displayName })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selectedEventKey = null }) { Text("‹ 活動") }
                    Text(eventCards.firstOrNull()?.eventName ?: "活動背卡", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                BackgroundChoiceList(imageRepository, eventCards, verifiedIds, selectedIds, ::toggleSelection, Modifier.weight(1f))
            } else {
                LazyRow(overscrollEffect = null, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(selected = selectedCategoryKey == null, onClick = { selectedCategoryKey = null }, label = { Text("全部活動") }) }
                    items(categoryGroups, key = { backgroundCategoryListKey(it.first.first, it.first.second) }) { (category, _) ->
                        FilterChip(selected = selectedCategoryKey == category.first, onClick = { selectedCategoryKey = category.first }, label = { Text(category.second) })
                    }
                }
                Text("總覽：${visible.size} 張背卡", style = MaterialTheme.typography.labelLarge)
                BackgroundChoiceList(imageRepository, visible.sortedWith(compareByDescending<BackgroundEntity> { it.year ?: 0 }.thenBy { it.eventName }.thenBy { it.sortOrder }), verifiedIds, selectedIds, ::toggleSelection, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BackgroundChoiceList(
    imageRepository: tw.pokemon.collectionmanager.data.repository.PokemonImageRepository,
    backgrounds: List<BackgroundEntity>,
    verifiedIds: Set<String>,
    selectedIds: Set<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(overscrollEffect = null, modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        items(backgrounds, key = { it.id }) { background ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelected(background.id) },
                colors = CardDefaults.cardColors(containerColor = if (background.id in selectedIds) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                    BackgroundArtwork(imageRepository, background.id, background.displayName, Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(background.displayName, fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(background.categoryName, background.locationName, background.year?.let { "${it} 年" }).distinct().joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!background.previewImageKey.isNullOrBlank()) {
                            Text(
                                "完整靜態預覽${background.previewSource?.let { " · 來源：$it" }.orEmpty()}；遊戲內動畫可能略有差異",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (!background.vfxKeys.isNullOrBlank() || !background.vfxKey.isNullOrBlank()) {
                            Text(
                                "目前只有靜態底圖；尚缺遊戲內特效圖層",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (!background.effectNote.isNullOrBlank()) {
                            Text(
                                background.effectNote,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(backgroundAvailabilityLabel(background), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (background.id in verifiedIds) Text("已驗證適用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

internal fun backgroundAvailabilityLabel(background: BackgroundEntity, today: LocalDate = LocalDate.now()): String {
    val starts = background.availableFrom?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val ends = background.availableUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (starts != null && today.isBefore(starts)) return "尚未推出 · 拆包資訊"
    if (starts != null && !today.isBefore(starts) && (ends == null || !today.isAfter(ends))) return "活動進行中"
    if (ends != null && today.isAfter(ends)) return "活動已結束"
    val year = background.year
    return when {
        year != null && year < today.year -> "已推出"
        year != null && year > today.year -> "尚未推出 · 拆包資訊"
        year == today.year && background.dataSource == "ASSET_ONLY" -> "已收錄圖片 · 活動日期待確認"
        year == today.year -> "已收錄主資料"
        background.dataSource == "ASSET_ONLY" -> "已收錄圖片 · 活動日期待確認"
        else -> "活動日期待確認"
    }
}

@Composable
fun VariantDetailScreen(
    viewModel: CollectionViewModel,
    variantId: String,
    accountId: String?,
    onBack: () -> Unit,
    onOpenAccount: (String) -> Unit,
) {
    val info by viewModel.variantInfo(variantId).collectAsStateWithLifecycle(initialValue = null)
    val sources by viewModel.variantSources(variantId).collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedIds by viewModel.overviewAccountIds.collectAsStateWithLifecycle()
    val selectedSourceRows = if (selectedIds.isEmpty()) sources else sources.filter { it.accountId in selectedIds }
    val aggregateFlow = remember(selectedIds) { viewModel.selectedVariants(selectedIds) }
    val aggregateVariants by aggregateFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val aggregate = aggregateVariants.firstOrNull { it.variantId == variantId }
    val bucketFlow = remember(accountId, variantId) { accountId?.let { viewModel.buckets(it, variantId) } ?: flowOf(emptyList()) }
    val buckets by bucketFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showEditor by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    if (info == null) {
        EmptyState("找不到這個收藏版本", "它可能已被刪除或備份尚未完成。", "返回", onBack)
        return
    }
    val currentInfo = info!!
    val title = buildVariantTitle(currentInfo)
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState(), overscrollEffect = null)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { showEditor = true }) { Text("編輯") }
            TextButton(onClick = { showDelete = true }) { Text("刪除") }
        }
        Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
            if (currentInfo.backgroundId != NO_BACKGROUND_ID) {
                BackgroundLayer(
                    viewModel.imageRepository,
                    currentInfo.backgroundId,
                    currentInfo.backgroundName,
                    Modifier.size(210.dp),
                )
            }
            PokemonArtwork(viewModel.imageRepository, currentInfo.speciesId, currentInfo.formId, currentInfo.costumeId, currentInfo.isShiny, currentInfo.speciesName)
        }
        SectionTitle("收藏版本資訊")
        InfoLine("Pokémon", "${currentInfo.speciesName}（#${currentInfo.dexNumber}）")
        InfoLine("型態", currentInfo.formName)
        InfoLine("異色", if (currentInfo.isShiny) "是" else "否")
        InfoLine("背景", currentInfo.backgroundName)
        InfoLine("背景分類", currentInfo.backgroundCategoryName)
        InfoLine("裝扮", currentInfo.costumeName)
        InfoLine("性別", currentInfo.gender.label)
        InfoLine("暗影／淨化", currentInfo.shadowState.label)
        InfoLine("極巨狀態", currentInfo.dynamaxState.label)
        HorizontalDivider()
        if (accountId != null) {
            val total = buckets.sumOf { it.quantity }
            SectionTitle("${currentInfo.speciesName} · ${sources.firstOrNull { it.accountId == accountId }?.accountName ?: "此帳號"}")
            Text("總數：$total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            buckets.forEach { bucket -> BucketLine(bucket, viewModel) }
            if (buckets.isEmpty()) Text("此帳號目前沒有這個收藏版本的佔有數量。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            SectionTitle("來源帳號")
            Text("總數：${aggregate?.totalQuantity ?: selectedSourceRows.sumOf { it.quantity }}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            aggregate?.let {
                Text("特大：${it.xxlQuantity} · 特小：${it.xxsQuantity} · 特招：${it.specialMoveQuantity}", style = MaterialTheme.typography.bodyMedium)
                Text("未交換：${it.untradedQuantity} · 已交換：${it.tradedQuantity}", style = MaterialTheme.typography.bodyMedium)
            }
            selectedSourceRows.forEach { source ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(source.accountName, modifier = Modifier.weight(1f))
                    Text("×${source.quantity}", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onOpenAccount(source.accountId) }) { Text("查看") }
                }
            }
            if (selectedSourceRows.isEmpty()) Text("目前選取的帳號沒有這個收藏版本。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(20.dp))
    }

    if (showEditor) QuickAddSheet(viewModel, accountId, currentInfo, onDismiss = { showEditor = false })
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("刪除這個收藏版本？") },
            text = { Text("會刪除所有帳號下此收藏版本的佔有數量，且無法復原。") },
            confirmButton = { Button(onClick = { viewModel.deleteVariant(variantId); showDelete = false; onBack() }) { Text("確定刪除") } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun BucketLine(bucket: OwnershipBucketEntity, viewModel: CollectionViewModel) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${bucket.sizeType.label} · ${if (bucket.hasSpecialMove) "有特招" else "無特招"}", fontWeight = FontWeight.Medium)
            Text(bucket.tradeState.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { viewModel.updateBucket(bucket, bucket.quantity - 1) }) { Text("−") }
        Text("×${bucket.quantity}", fontWeight = FontWeight.Bold)
        TextButton(onClick = { viewModel.updateBucket(bucket, bucket.quantity + 1) }) { Text("＋") }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.35f))
        Text(value, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.65f))
    }
}

private fun buildVariantTitle(info: VariantInfoRow): String = buildString {
    if (info.isShiny) append("異色")
    if (info.backgroundId != NO_BACKGROUND_ID) append(info.backgroundName)
    if (info.costumeId != NO_COSTUME_ID) append(info.costumeName)
    append(info.speciesName)
}

