package tw.pokemon.collectionmanager.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesRepositoryTest {
    @Test
    fun blankUrlUsesSharedDefault() {
        assertEquals(DEFAULT_MASTER_DATA_UPDATE_URL, normalizeMasterDataUpdateUrl("  "))
    }

    @Test
    fun knownLegacySharedUrlUsesCurrentPagesManifest() {
        assertEquals(
            DEFAULT_MASTER_DATA_UPDATE_URL,
            normalizeMasterDataUpdateUrl(
                "https://raw.githubusercontent.com/wen4080/PokemonCollectionManager/main/tools/master_data/generated/master_manifest.json",
            ),
        )
    }

    @Test
    fun currentSharedUrlIsUnchanged() {
        assertEquals(
            DEFAULT_MASTER_DATA_UPDATE_URL,
            normalizeMasterDataUpdateUrl(DEFAULT_MASTER_DATA_UPDATE_URL),
        )
    }

    @Test
    fun userProvidedCustomUrlIsPreserved() {
        val custom = "https://example.com/pokemon/master_manifest.json"
        assertEquals(custom, normalizeMasterDataUpdateUrl(custom))
    }
}

