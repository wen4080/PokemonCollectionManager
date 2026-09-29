package tw.pokemon.collectionmanager.domain

import tw.pokemon.collectionmanager.data.local.DynamaxState
import tw.pokemon.collectionmanager.data.local.Gender
import tw.pokemon.collectionmanager.data.local.ShadowState
import tw.pokemon.collectionmanager.data.local.SizeType
import tw.pokemon.collectionmanager.data.local.TradeState

data class VariantDraft(
    val speciesId: String,
    val formId: String,
    val costumeId: String,
    val backgroundId: String,
    val isShiny: Boolean,
    val gender: Gender,
    val shadowState: ShadowState,
    val dynamaxState: DynamaxState,
)

data class BucketDraft(
    val sizeType: SizeType,
    val hasSpecialMove: Boolean,
    val tradeState: TradeState,
)

data class DeleteAccountSummary(
    val variantCount: Int,
    val pokemonCount: Long,
)

object VariantKeyFactory {
    /** Stable, explicit canonical identity. NONE values are deliberately part of the key. */
    fun build(draft: VariantDraft): String = listOf(
        draft.speciesId,
        draft.formId,
        draft.costumeId,
        draft.backgroundId,
        if (draft.isShiny) "SHINY" else "NORMAL",
        draft.gender.code,
        draft.shadowState.code,
        draft.dynamaxState.code,
    ).joinToString("|")
}
