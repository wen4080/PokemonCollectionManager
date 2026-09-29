package tw.pokemon.collectionmanager.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.pokemon.collectionmanager.data.local.DynamaxState
import tw.pokemon.collectionmanager.data.local.Gender
import tw.pokemon.collectionmanager.data.local.ShadowState
import tw.pokemon.collectionmanager.data.local.VariantCardRow

class CollectionFilterTest {
    private fun row(
        backgroundId: String = "BACKGROUND_OSAKA",
        shiny: Boolean = true,
        gender: Gender = Gender.MALE,
        shadow: ShadowState = ShadowState.NORMAL,
        dynamax: DynamaxState = DynamaxState.NONE,
        xxl: Long = 1,
        xxs: Long = 0,
        untraded: Long = 1,
        traded: Long = 0,
    ) = VariantCardRow(
        variantId = "VARIANT",
        speciesId = "SPECIES_RAYQUAZA",
        formId = "FORM_NORMAL",
        costumeId = "COSTUME_NONE",
        speciesName = "烈空坐",
        dexNumber = 384,
        formName = "一般型態",
        costumeName = "無裝扮",
        backgroundName = "大阪背卡",
        backgroundCategoryName = "GO Fest 地區背卡",
        backgroundId = backgroundId,
        isShiny = shiny,
        gender = gender,
        shadowState = shadow,
        dynamaxState = dynamax,
        totalQuantity = 1,
        xxlQuantity = xxl,
        xxsQuantity = xxs,
        specialMoveQuantity = 1,
        untradedQuantity = untraded,
        tradedQuantity = traded,
    )

    @Test
    fun filtersUseAndBetweenDifferentCategories() {
        assertTrue(row().matches("", setOf(VariantFilterMode.SHINY, VariantFilterMode.XXL, VariantFilterMode.SPECIAL_MOVE)))
        assertFalse(row(shiny = false).matches("", setOf(VariantFilterMode.SHINY, VariantFilterMode.XXL)))
    }

    @Test
    fun sizeAndTradeSelectionsUseOrInsideTheirCategory() {
        assertTrue(row(xxl = 1, xxs = 0).matches("", setOf(VariantFilterMode.XXL, VariantFilterMode.XXS)))
        assertTrue(row(xxl = 0, xxs = 1).matches("", setOf(VariantFilterMode.XXL, VariantFilterMode.XXS)))
        assertTrue(row(untraded = 0, traded = 1).matches("", setOf(VariantFilterMode.AVAILABLE, VariantFilterMode.TRADED)))
    }

    @Test
    fun selectedBackgroundsUseOrAndCombineWithOtherFilters() {
        assertTrue(row().matches("", setOf(VariantFilterMode.SHINY), setOf("BACKGROUND_OSAKA", "BACKGROUND_TAIPEI")))
        assertFalse(row(shiny = false).matches("", setOf(VariantFilterMode.SHINY), setOf("BACKGROUND_OSAKA", "BACKGROUND_TAIPEI")))
        assertFalse(row().matches("", emptySet(), setOf("BACKGROUND_TAIPEI", "BACKGROUND_NEW_YORK")))
    }

    @Test
    fun supportsAnyAndMinimumMatchModes() {
        val filters = setOf(VariantFilterMode.SHINY, VariantFilterMode.XXL, VariantFilterMode.SHADOW)
        assertTrue(row(shiny = true, xxl = 0).matches("", filters, matchMode = FilterMatchMode.ANY))
        assertTrue(row(shiny = true, xxl = 1).matches("", filters, matchMode = FilterMatchMode.AT_LEAST, minimumMatches = 2))
        assertFalse(row(shiny = true, xxl = 0).matches("", filters, matchMode = FilterMatchMode.AT_LEAST, minimumMatches = 2))
    }
}
