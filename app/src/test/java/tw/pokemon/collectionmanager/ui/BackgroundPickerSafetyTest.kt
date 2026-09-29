package tw.pokemon.collectionmanager.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BackgroundPickerSafetyTest {
    @Test
    fun sameEventCodeWithDifferentNamesGetsDifferentLazyListKeys() {
        assertNotEquals(
            backgroundEventListKey("OTHER_LOCATION_UNKNOWN", "東京活動"),
            backgroundEventListKey("OTHER_LOCATION_UNKNOWN", "大阪活動"),
        )
    }

    @Test
    fun sameCategoryPairKeepsStableLazyListKey() {
        assertEquals(
            backgroundCategoryListKey("GO_FEST_REGION", "GO Fest 地區背卡"),
            backgroundCategoryListKey("GO_FEST_REGION", "GO Fest 地區背卡"),
        )
    }

    @Test
    fun systemBackFromEventReturnsToEventList() {
        assertEquals(
            BackgroundPickerBackTarget.EVENT_LIST,
            backgroundPickerBackTarget(selectedYear = 2026, selectedEventKey = "CITY_SAFARI_2026"),
        )
    }

    @Test
    fun systemBackFromYearReturnsToYearList() {
        assertEquals(
            BackgroundPickerBackTarget.YEAR_LIST,
            backgroundPickerBackTarget(selectedYear = 2026, selectedEventKey = null),
        )
    }

    @Test
    fun systemBackAtRootDismissesPicker() {
        assertEquals(
            BackgroundPickerBackTarget.DISMISS,
            backgroundPickerBackTarget(selectedYear = null, selectedEventKey = null),
        )
    }
}
