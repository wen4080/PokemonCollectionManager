package tw.pokemon.collectionmanager.ui

import tw.pokemon.collectionmanager.data.local.CollectionCombinationRow
import tw.pokemon.collectionmanager.data.local.DynamaxState
import tw.pokemon.collectionmanager.data.local.Gender
import tw.pokemon.collectionmanager.data.local.ShadowState
import tw.pokemon.collectionmanager.data.local.SizeType
import tw.pokemon.collectionmanager.data.local.TradeState
import tw.pokemon.collectionmanager.data.local.VariantCardRow
import tw.pokemon.collectionmanager.data.local.VariantInfoRow

/**
 * 畫面上視為同一張卡的核心版本。
 * 性別、尺寸、特招與交換狀態都不是核心版本的一部分，會在摘要中呈現。
 */
data class CollectionDisplayKey(
    val speciesId: String,
    val formId: String,
    val costumeId: String,
    val backgroundId: String,
    val isShiny: Boolean,
    val shadowState: ShadowState,
    val dynamaxState: DynamaxState,
)

data class CollectionDisplayGroup(
    val key: CollectionDisplayKey,
    val representative: VariantCardRow,
    val variants: List<VariantCardRow>,
    val totalQuantity: Long,
) {
    val variantIds: Set<String> = variants.map { it.variantId }.toSet()
}

fun VariantCardRow.displayKey(): CollectionDisplayKey = CollectionDisplayKey(
    speciesId = speciesId,
    formId = formId,
    costumeId = costumeId,
    backgroundId = backgroundId,
    isShiny = isShiny,
    shadowState = shadowState,
    dynamaxState = dynamaxState,
)

/** 將只差性別的資料列合併；尺寸、特招與交換狀態本來就已在資料列內統計。 */
fun List<VariantCardRow>.groupForDisplay(): List<CollectionDisplayGroup> =
    groupBy { it.displayKey() }
        .values
        .map { rows ->
            CollectionDisplayGroup(
                key = rows.first().displayKey(),
                representative = rows.first(),
                variants = rows,
                totalQuantity = rows.sumOf { it.totalQuantity },
            )
        }
        .sortedWith(
            compareBy<CollectionDisplayGroup> { it.representative.dexNumber }
                .thenBy { it.representative.speciesName }
                .thenBy { it.representative.formName }
                .thenBy { it.representative.costumeName }
                .thenBy { it.representative.backgroundName }
                .thenBy { it.representative.isShiny },
        )

data class CollectionCombinationSummary(
    val gender: Gender,
    val sizeType: SizeType,
    val hasSpecialMove: Boolean,
    val tradeState: TradeState,
    val quantity: Long,
)

fun List<CollectionCombinationRow>.summarizeCombinations(): List<CollectionCombinationSummary> =
    groupBy { combination ->
        CombinationSummaryKey(
            gender = combination.gender,
            sizeType = combination.sizeType,
            hasSpecialMove = combination.hasSpecialMove,
            tradeState = combination.tradeState,
        )
    }
        .map { (key, rows) ->
            CollectionCombinationSummary(
                gender = key.gender,
                sizeType = key.sizeType,
                hasSpecialMove = key.hasSpecialMove,
                tradeState = key.tradeState,
                quantity = rows.sumOf { it.quantity },
            )
        }
        .sortedWith(
            compareBy<CollectionCombinationSummary> { genderOrder(it.gender) }
                .thenBy { sizeOrder(it.sizeType) }
                .thenByDescending { it.hasSpecialMove }
                .thenBy { it.tradeState.code },
        )

private data class CombinationSummaryKey(
    val gender: Gender,
    val sizeType: SizeType,
    val hasSpecialMove: Boolean,
    val tradeState: TradeState,
)

private fun genderOrder(gender: Gender): Int = when (gender) {
    Gender.MALE -> 0
    Gender.FEMALE -> 1
    Gender.GENDERLESS -> 2
    Gender.UNKNOWN -> 3
}

private fun sizeOrder(sizeType: SizeType): Int = when (sizeType) {
    SizeType.NORMAL -> 0
    SizeType.XXS -> 1
    SizeType.XXL -> 2
}

/**
 * 顯示精確組合名稱：正常尺寸與無特招不顯示，避免產生「無特招」的冗餘文字；
 * XXS、XXL 保留遊戲內的標準標籤。
 */
fun CollectionCombinationSummary.displayLabel(): String = buildList {
    add(gender.label)
    if (sizeType != SizeType.NORMAL) add(sizeType.code)
    if (hasSpecialMove) add("特招")
    add(tradeState.label)
}.joinToString(" · ")

fun CollectionCombinationSummary.displayText(): String = "${displayLabel()} ×$quantity"

fun CollectionCombinationRow.displayLabel(): String = CollectionCombinationSummary(
    gender = gender,
    sizeType = sizeType,
    hasSpecialMove = hasSpecialMove,
    tradeState = tradeState,
    quantity = quantity,
).displayLabel()

fun CollectionCombinationRow.displayText(): String = "${displayLabel()} ×$quantity"

fun CollectionCombinationRow.matchesDisplayKey(info: VariantInfoRow): Boolean =
    speciesId == info.speciesId &&
        formId == info.formId &&
        costumeId == info.costumeId &&
        backgroundId == info.backgroundId &&
        isShiny == info.isShiny &&
        shadowState == info.shadowState &&
        dynamaxState == info.dynamaxState

fun List<CollectionCombinationRow>.forDisplayKey(info: VariantInfoRow): List<CollectionCombinationRow> =
    filter { it.matchesDisplayKey(info) }

