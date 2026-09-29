package tw.pokemon.collectionmanager.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.pokemon.collectionmanager.data.local.BackgroundEntity
import tw.pokemon.collectionmanager.data.local.BackgroundType

class BackgroundAvailabilityTest {
    private fun background(
        year: Int? = null,
        availableFrom: String? = null,
        availableUntil: String? = null,
        dataSource: String = "GAME_MASTER",
    ) = BackgroundEntity(
        id = "BACKGROUND_TEST",
        backgroundKey = "TEST",
        displayName = "測試背卡",
        backgroundType = BackgroundType.SPECIAL,
        year = year,
        availableFrom = availableFrom,
        availableUntil = availableUntil,
        dataSource = dataSource,
    )

    @Test
    fun onlyFutureBackgroundUsesDataminedLabel() {
        val today = LocalDate.of(2026, 9, 28)
        assertEquals("已推出", backgroundAvailabilityLabel(background(year = 2025), today))
        assertFalse(backgroundAvailabilityLabel(background(year = 2026, dataSource = "ASSET_ONLY"), today).contains("拆包"))
        assertTrue(backgroundAvailabilityLabel(background(year = 2027), today).contains("拆包資訊"))
    }

    @Test
    fun datedEventsChangeStatusWithoutMasterDataUpdate() {
        val background = background(year = 2026, availableFrom = "2026-09-20", availableUntil = "2026-09-30")
        assertEquals("尚未推出 · 拆包資訊", backgroundAvailabilityLabel(background, LocalDate.of(2026, 9, 19)))
        assertEquals("活動進行中", backgroundAvailabilityLabel(background, LocalDate.of(2026, 9, 28)))
        assertEquals("活動已結束", backgroundAvailabilityLabel(background, LocalDate.of(2026, 10, 1)))
    }
}
