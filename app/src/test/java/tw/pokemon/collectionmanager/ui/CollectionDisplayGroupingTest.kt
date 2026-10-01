package tw.pokemon.collectionmanager.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.pokemon.collectionmanager.data.local.CollectionCombinationRow
import tw.pokemon.collectionmanager.data.local.DynamaxState
import tw.pokemon.collectionmanager.data.local.Gender
import tw.pokemon.collectionmanager.data.local.ShadowState
import tw.pokemon.collectionmanager.data.local.SizeType
import tw.pokemon.collectionmanager.data.local.TradeState
import tw.pokemon.collectionmanager.data.local.VariantCardRow

class CollectionDisplayGroupingTest {
    private fun cardRow(
        variantId: String,
        gender: Gender,
        backgroundId: String = "BACKGROUND_NONE",
        quantity: Long = 1,
        xxl: Long = 0,
        xxs: Long = 0,
        specialMove: Long = 0,
    ) = VariantCardRow(
        variantId = variantId,
        speciesId = "SPECIES_998",
        formId = "FORM_NORMAL",
        costumeId = "COSTUME_NONE",
        speciesName = "戟脊龍",
        dexNumber = 998,
        formName = "一般型態",
        costumeName = "無裝扮",
        backgroundName = if (backgroundId == "BACKGROUND_NONE") "無背景" else "活動背卡",
        backgroundCategoryName = "其他特殊背卡",
        backgroundId = backgroundId,
        isShiny = true,
        gender = gender,
        shadowState = ShadowState.NORMAL,
        dynamaxState = DynamaxState.NONE,
        totalQuantity = quantity,
        xxlQuantity = xxl,
        xxsQuantity = xxs,
        specialMoveQuantity = specialMove,
        untradedQuantity = quantity,
        tradedQuantity = 0,
    )

    private fun combination(
        gender: Gender,
        size: SizeType,
        special: Boolean,
        trade: TradeState = TradeState.UNTRADED,
        quantity: Long = 1,
    ) = CollectionCombinationRow(
        accountId = "ACCOUNT_A",
        accountName = "A帳號",
        accountIsArchived = false,
        variantId = "VARIANT_${gender.code}",
        speciesId = "SPECIES_998",
        formId = "FORM_NORMAL",
        costumeId = "COSTUME_NONE",
        speciesName = "戟脊龍",
        dexNumber = 998,
        formName = "一般型態",
        backgroundName = "無背景",
        backgroundCategoryName = "其他特殊背卡",
        backgroundId = "BACKGROUND_NONE",
        isShiny = true,
        gender = gender,
        shadowState = ShadowState.NORMAL,
        dynamaxState = DynamaxState.NONE,
        sizeType = size,
        hasSpecialMove = special,
        tradeState = trade,
        quantity = quantity,
    )

    @Test
    fun mergesGenderRowsIntoOneDisplayCard() {
        val groups = listOf(
            cardRow("VARIANT_MALE", Gender.MALE, quantity = 2),
            cardRow("VARIANT_FEMALE", Gender.FEMALE, quantity = 3),
        ).groupForDisplay()

        assertEquals(1, groups.size)
        assertEquals(5, groups.single().totalQuantity)
        assertEquals(setOf("VARIANT_MALE", "VARIANT_FEMALE"), groups.single().variantIds)
    }

    @Test
    fun keepsDifferentBackgroundsAsDifferentCards() {
        val groups = listOf(
            cardRow("VARIANT_NONE", Gender.MALE),
            cardRow("VARIANT_EVENT", Gender.FEMALE, backgroundId = "BACKGROUND_EVENT"),
        ).groupForDisplay()

        assertEquals(2, groups.size)
    }

    @Test
    fun summarizesOnlyPresentCombinationsAndOmitsNoSpecialMove() {
        val summaries = listOf(
            combination(Gender.FEMALE, SizeType.NORMAL, special = false, quantity = 2),
            combination(Gender.FEMALE, SizeType.XXL, special = true, quantity = 1),
            combination(Gender.MALE, SizeType.XXS, special = true, trade = TradeState.TRADED, quantity = 4),
        ).summarizeCombinations()

        assertEquals(3, summaries.size)
        assertTrue(summaries.any { it.displayText() == "母 · 未交換 ×2" })
        assertTrue(summaries.any { it.displayText() == "母 · XXL · 特招 · 未交換 ×1" })
        assertTrue(summaries.any { it.displayText() == "公 · XXS · 特招 · 已交換 ×4" })
        assertTrue(summaries.none { "無特招" in it.displayText() })
    }
}

