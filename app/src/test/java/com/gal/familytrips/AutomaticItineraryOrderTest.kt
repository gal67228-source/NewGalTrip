package com.gal.familytrips

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomaticItineraryOrderTest {
    @Test
    fun newMealsAreInsertedByTime() {
        val morningTour = activity("tour", "10:00")
        val eveningTour = activity("show", "20:00")
        val breakfast = activity("auto-meal-hotel-date-breakfast", "08:00")
        val dinner = activity("auto-meal-hotel-date-dinner", "19:00")

        val ordered = preserveActivityOrder(
            previous = listOf(morningTour, eveningTour),
            generated = listOf(morningTour, eveningTour, breakfast, dinner)
        )

        assertEquals(
            listOf(breakfast.id, morningTour.id, dinner.id, eveningTour.id),
            ordered.map { it.id }
        )
    }

    @Test
    fun existingAutomaticActivitiesAreReinsertedAtTheirCorrectTime() {
        val dinner = activity("auto-meal-hotel-date-dinner", "19:00")
        val tour = activity("tour", "10:00")
        val breakfast = activity("auto-meal-hotel-date-breakfast", "08:00")
        val userOrder = listOf(dinner, tour, breakfast)
        val regenerated = listOf(
            breakfast.copy(notes = "updated hotel"),
            dinner.copy(notes = "updated hotel"),
            tour
        )

        val ordered = preserveActivityOrder(userOrder, regenerated)

        assertEquals(
            listOf(breakfast.id, tour.id, dinner.id),
            ordered.map { it.id }
        )
        assertEquals("updated hotel", ordered.first().notes)
    }

    @Test
    fun existingCheckInDoesNotSkipTheFirstLaterActivity() {
        val lunch = activity("lunch", "13:00")
        val checkIn = activity("auto-hotel-stay-hotel-check-in", "15:00")
        val museum = activity("museum", "16:00")
        val dinner = activity("dinner", "19:00")

        val ordered = preserveActivityOrder(
            previous = listOf(lunch, museum, checkIn, dinner),
            generated = listOf(lunch, museum, checkIn, dinner)
        )

        assertEquals(
            listOf(lunch.id, checkIn.id, museum.id, dinner.id),
            ordered.map { it.id }
        )
    }

    @Test
    fun userActivityOrderSurvivesAutomaticItineraryRebuild() {
        val first = activity("first", "18:00")
        val second = activity("second", "10:00")

        val ordered = preserveActivityOrder(
            previous = listOf(first, second),
            generated = listOf(second, first)
        )

        assertEquals(listOf(first.id, second.id), ordered.map { it.id })
    }

    private fun activity(id: String, time: String) = ActivityItem(
        id = id,
        name = id,
        time = time
    )
}
