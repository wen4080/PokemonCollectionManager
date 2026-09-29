package tw.pokemon.collectionmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import tw.pokemon.collectionmanager.data.local.DynamaxState
import tw.pokemon.collectionmanager.data.local.Gender
import tw.pokemon.collectionmanager.data.local.ShadowState

class CollectionRulesTest {
    private fun draft(
        formId: String = "FORM_NORMAL",
        costumeId: String = "COSTUME_NONE",
        backgroundId: String = "BACKGROUND_NONE",
        shiny: Boolean = false,
        gender: Gender = Gender.UNKNOWN,
        shadow: ShadowState = ShadowState.NORMAL,
        dynamax: DynamaxState = DynamaxState.NONE,
    ) = VariantDraft(
        speciesId = "SPECIES_RAYQUAZA",
        formId = formId,
        costumeId = costumeId,
        backgroundId = backgroundId,
        isShiny = shiny,
        gender = gender,
        shadowState = shadow,
        dynamaxState = dynamax,
    )

    @Test
    fun everyVariantIdentityDimensionProducesDifferentKey() {
        val base = VariantKeyFactory.build(draft())
        assertNotEquals(base, VariantKeyFactory.build(draft(shiny = true)))
        assertNotEquals(base, VariantKeyFactory.build(draft(backgroundId = "BACKGROUND_OSAKA_2025")))
        assertNotEquals(base, VariantKeyFactory.build(draft(backgroundId = "BACKGROUND_TAIPEI_2025")))
        assertNotEquals(base, VariantKeyFactory.build(draft(formId = "FORM_DUSK")))
        assertNotEquals(base, VariantKeyFactory.build(draft(costumeId = "COSTUME_PARTY_HAT")))
        assertNotEquals(base, VariantKeyFactory.build(draft(gender = Gender.MALE)))
        assertNotEquals(base, VariantKeyFactory.build(draft(gender = Gender.FEMALE)))
        assertNotEquals(base, VariantKeyFactory.build(draft(shadow = ShadowState.SHADOW)))
        assertNotEquals(base, VariantKeyFactory.build(draft(dynamax = DynamaxState.DYNAMAX)))
        assertNotEquals(base, VariantKeyFactory.build(draft(dynamax = DynamaxState.GIGANTAMAX)))
    }

    @Test
    fun exactVariantIdentityProducesSameCanonicalKey() {
        assertEquals(VariantKeyFactory.build(draft()), VariantKeyFactory.build(draft()))
    }

    @Test
    fun bucketQuantityMergesInsteadOfCreatingAnotherIdentity() {
        assertEquals(5, OwnershipRules.mergeQuantity(2, 3))
        assertEquals(6, OwnershipRules.aggregateQuantities(listOf(2, 1, 3)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroAddedQuantityIsRejected() {
        OwnershipRules.mergeQuantity(2, 0)
    }

    @Test
    fun overviewOnlySumsSelectedAccounts() {
        assertEquals(3, OwnershipRules.aggregateQuantities(listOf(2, 1)))
        assertEquals(6, OwnershipRules.aggregateQuantities(listOf(2, 1, 3)))
    }
}
