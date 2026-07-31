package com.gal.familytrips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizedPackingTest {
    @Test
    fun createsSeparateTravelerRowsAndReplacesOldSuggestions() {
        val trip = Trip(
            id = "trip", name = "טיול", destination = "יעד",
            startDate = "2026-08-01", endDate = "2026-08-06",
            travelers = listOf(
                TripTraveler("manor", "מנור", 8, "נקבה"),
                TripTraveler("oz", "עוז", 35, "זכר")
            )
        )

        val once = PersonalizedPacking.apply(trip)
        val twice = PersonalizedPacking.apply(once)

        assertEquals(once.packingItems.size, twice.packingItems.size)
        assertTrue(once.packingItems.any {
            it.travelerName == "מנור" && it.name == "סטים של בגדים" && it.quantity == 6
        })
        assertTrue(once.packingItems.any {
            it.travelerName == "עוז" && it.name == "בגדי ים" && it.quantity == 2
        })
        assertTrue(once.packingItems.any {
            it.travelerName == "מנור" && it.name == "דרכון"
        })
    }
}
