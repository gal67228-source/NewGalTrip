package com.gal.familytrips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingImportEngineTest {
    @Test
    fun flightConfirmationExtractsNormalizedDetails() {
        val candidate = BookingImportEngine.parse(
            """
            Flight LY 123
            Booking number: ABCD-1234
            7/8/2026 9:05 TLV
            2026-08-07 12:15 BUD
            """.trimIndent()
        ).single()

        assertEquals("flight", candidate.type)
        assertEquals("ABCD-1234", candidate.bookingReference)
        assertEquals("2026-08-07", candidate.startDate)
        assertEquals("09:05", candidate.startTime)
        assertEquals("TLV", candidate.origin)
        assertEquals("BUD", candidate.destination)
        assertTrue(candidate.confidence >= 85)
    }

    @Test
    fun impossibleCalendarDatesAreNotImported() {
        val candidate = BookingImportEngine.parse(
            "Hotel Example\nCheck-in 31/02/2026\nCheck-out 32/02/2026"
        ).single()

        assertEquals("", candidate.startDate)
        assertEquals("", candidate.endDate)
        assertFalse(candidate.notes.isBlank())
    }

    @Test
    fun applyingDatedAttractionTargetsMatchingDay() {
        val trip = tripWithDays("2026-08-07", "2026-08-08")
        val candidate = BookingImportCandidate(
            type = "attraction",
            title = "Museum ticket",
            startDate = "2026-08-08",
            startTime = "10:30",
            bookingReference = "MUSEUM-1"
        )

        val updated = BookingImportEngine.apply(trip, candidate)

        assertTrue(updated.days.first().activities.isEmpty())
        assertEquals("Museum ticket", updated.days[1].activities.single().name)
        assertTrue(updated.days[1].activities.single().fixedTime)
    }

    private fun tripWithDays(vararg dates: String) = Trip(
        id = "trip",
        name = "Trip",
        destination = "Budapest",
        startDate = dates.first(),
        endDate = dates.last(),
        days = dates.mapIndexed { index, date ->
            TripDay(id = "day-$index", date = date, title = "Day ${index + 1}")
        }
    )
}
