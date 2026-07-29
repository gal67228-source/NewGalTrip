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
    fun userOrderSurvivesAutomaticItineraryRebuild() {
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

        assertEquals(userOrder.map { it.id }, ordered.map { it.id })
        assertEquals("updated hotel", ordered.first().notes)
    }

    private fun activity(id: String, time: String) = ActivityItem(
        id = id,
        name = id,
        time = time
    )
}
