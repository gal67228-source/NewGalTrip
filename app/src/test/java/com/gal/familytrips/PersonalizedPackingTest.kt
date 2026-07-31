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
            days = listOf(
                TripDay(
                    id = "day", date = "2026-08-02", title = "יום בבריכה",
                    activities = listOf(ActivityItem(id = "pool", name = "בריכת המלון"))
                )
            ),
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

    @Test
    fun usesTripSeasonAndAttractionsAndKeepsPackingProgress() {
        val trip = Trip(
            id = "winter", name = "חורף", destination = "בודפשט",
            startDate = "2026-12-20", endDate = "2026-12-23",
            days = listOf(
                TripDay(
                    id = "day", date = "2026-12-21", title = "פארק מים",
                    activities = listOf(ActivityItem(id = "aqua", name = "Aquaworld pool"))
                )
            ),
            travelers = listOf(TripTraveler("child", "נועה", 4, "נקבה"))
        )

        val generated = PersonalizedPacking.apply(trip)
        assertTrue(generated.packingItems.any { it.name == "כובע לבריכה / לים" })
        assertTrue(generated.packingItems.any { it.name == "מטרייה מתקפלת" })
        assertTrue(generated.packingItems.any { it.name == "תרופות ילדים" })

        val packedId = generated.packingItems.first { it.name == "מעיל חם" }.id
        val packed = generated.copy(
            packingItems = generated.packingItems.map {
                if (it.id == packedId) it.copy(packed = true) else it
            }
        )
        val regenerated = PersonalizedPacking.apply(packed)
        assertTrue(regenerated.packingItems.first { it.id == packedId }.packed)
    }
}
