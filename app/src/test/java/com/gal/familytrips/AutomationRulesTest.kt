package com.gal.familytrips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRulesTest {
    @Test
    fun destinationCurrenciesCoverKnownDestinationsAndFallback() {
        assertEquals("HUF", destinationCurrency("Budapest, Hungary"))
        assertEquals("JPY", destinationCurrency("טוקיו, יפן"))
        assertEquals("EUR", destinationCurrency("Unknown destination"))
    }

    @Test
    fun budgetSuggestionsIncludeHotelsFlightsAndPaidAttractionsOnly() {
        val trip = baseTrip().copy(
            hotels = listOf(
                Hotel("hotel", "Central Hotel", "2026-08-07", "2026-08-09")
            ),
            days = listOf(
                TripDay(
                    id = "day",
                    date = "2026-08-08",
                    title = "Day",
                    activities = listOf(
                        ActivityItem("flight", name = "Flight LY 123", cost = "€200"),
                        ActivityItem("museum", name = "Museum ticket", cost = "20 EUR"),
                        ActivityItem("food", name = "Restaurant ticket", cost = "20 EUR")
                    )
                )
            )
        )

        val suggestions = suggestedBudgetTemplates(trip)

        assertEquals(3, suggestions.size)
        assertEquals(setOf("מלונות", "טיסות", "אטרקציות"), suggestions.map { it.category }.toSet())
        assertFalse(suggestions.any { it.id == "auto-attraction-food" })
    }

    @Test
    fun documentRequirementsAreDeduplicatedAndClassified() {
        val train = ActivityItem("train", name = "Train voucher")
        val trip = baseTrip().copy(
            days = listOf(
                TripDay("one", "2026-08-07", "One", activities = listOf(train)),
                TripDay("two", "2026-08-08", "Two", activities = listOf(train))
            )
        )

        val requirements = suggestedDocumentRequirements(trip)

        assertTrue(requirements.any { it.key == "base-passports" && it.supportsPassengers })
        assertEquals(1, requirements.count { it.key == "activity-train" })
        assertEquals("תחבורה", requirements.single { it.key == "activity-train" }.type)
    }

    private fun baseTrip() = Trip(
        id = "trip",
        name = "Trip",
        destination = "Budapest",
        startDate = "2026-08-07",
        endDate = "2026-08-09"
    )
}
