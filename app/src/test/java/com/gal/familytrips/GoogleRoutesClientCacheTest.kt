package com.gal.familytrips

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleRoutesClientCacheTest {
    @Test
    fun nonRouteEditsKeepSavedRouteValid() {
        val origin = activity("a", "Museum", 47.1, 19.1)
        val destination = cachedDestination(origin, activity("b", "Market", 47.2, 19.2))
        val edited = destination.copy(
            time = "14:30",
            notes = "new note",
            cost = "20",
            duration = "90 minutes",
            fixedTime = !destination.fixedTime
        )

        assertFalse(GoogleRoutesClient.needsRefresh(day(origin, edited)))
    }

    @Test
    fun locationAndModeChangesInvalidateSavedRoute() {
        val origin = activity("a", "Museum", 47.1, 19.1)
        val destination = cachedDestination(origin, activity("b", "Market", 47.2, 19.2))

        assertTrue(
            GoogleRoutesClient.needsRefresh(
                day(origin, destination.copy(latitude = 48.0))
            )
        )
        assertTrue(
            GoogleRoutesClient.needsRefresh(
                day(origin, destination.copy(transitionMode = "drive"))
            )
        )
    }

    @Test
    fun disabledTransitionNeverRequestsRouteCalculation() {
        val origin = activity("a", "Museum", 47.1, 19.1)
        val destination = activity("b", "Market", 47.2, 19.2).copy(
            transitionMode = "none",
            transitionAutomatic = true
        )

        assertFalse(GoogleRoutesClient.needsRefresh(day(origin, destination)))
    }

    @Test
    fun reorderingInvalidatesOnlyEdgesWhoseEndpointsChanged() {
        val first = activity("a", "A", 47.1, 19.1)
        val second = cachedDestination(first, activity("b", "B", 47.2, 19.2))
        val third = cachedDestination(second, activity("c", "C", 47.3, 19.3))
        val fourth = cachedDestination(third, activity("d", "D", 47.4, 19.4))
        val fifth = cachedDestination(fourth, activity("e", "E", 47.5, 19.5))

        val oldSecondKey = second.routeCacheKey
        val oldThirdKey = third.routeCacheKey
        val reorderedSecondKey = GoogleRoutesClient.routeCacheKey(
            first,
            third,
            resolvedTransitionMode(first, third)
        )
        val reorderedThirdKey = GoogleRoutesClient.routeCacheKey(
            third,
            second,
            resolvedTransitionMode(third, second)
        )

        assertNotEquals(oldThirdKey, reorderedSecondKey)
        assertNotEquals(oldSecondKey, reorderedThirdKey)
        val reordered = day(first, second, fourth, third, fifth)
        assertEquals(
            listOf(2, 3, 4),
            GoogleRoutesClient.segmentsNeedingRefresh(reordered)
        )
    }

    @Test
    fun manualSegmentDoesNotRequestRouteUntilAutomaticIsEnabled() {
        val origin = activity("a", "A", 47.1, 19.1)
        val manual = activity("b", "B", 47.2, 19.2).copy(
            transitionAutomatic = false
        )

        assertFalse(GoogleRoutesClient.needsRefresh(day(origin, manual)))
        assertTrue(
            GoogleRoutesClient.needsRefresh(
                day(origin, manual.copy(transitionAutomatic = true))
            )
        )
    }

    @Test
    fun olderCloudSnapshotDoesNotRemoveCalculatedTransition() {
        val local = activity("b", "B", 47.2, 19.2).copy(
            transitionMinutes = 18,
            transitionDetails = "walk north",
            routeDistanceMeters = 1400,
            routeSource = "google",
            routeStatus = "cached",
            routeCacheKey = "route-key",
            routeUpdatedAt = 200L
        )
        val staleRemote = local.copy(
            transitionMinutes = 0,
            transitionDetails = "",
            routeDistanceMeters = 0,
            routeSource = "estimate",
            routeStatus = "",
            routeCacheKey = "",
            routeUpdatedAt = 100L
        )

        assertEquals(
            local,
            GoogleRoutesClient.keepNewerLocalRoute(local, staleRemote)
        )
    }

    private fun cachedDestination(
        previous: ActivityItem,
        current: ActivityItem
    ): ActivityItem {
        val mode = resolvedTransitionMode(previous, current)
        return current.copy(
            transitionMode = mode,
            routeSource = "google",
            routeCacheKey = GoogleRoutesClient.routeCacheKey(previous, current, mode),
            transitionMinutes = 12
        )
    }

    private fun activity(
        id: String,
        name: String,
        latitude: Double,
        longitude: Double
    ) = ActivityItem(
        id = id,
        name = name,
        location = name,
        latitude = latitude,
        longitude = longitude
    )

    private fun day(vararg activities: ActivityItem) = TripDay(
        id = "day",
        date = "2026-07-29",
        title = "Day",
        activities = activities.toList()
    )
}
