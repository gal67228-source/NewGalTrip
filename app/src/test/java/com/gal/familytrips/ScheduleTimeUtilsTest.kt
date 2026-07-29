package com.gal.familytrips

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleTimeUtilsTest {
    @Test
    fun colonDurationIsParsedAsHoursAndMinutes() {
        assertEquals(150, parseActivityDurationMinutes("2:30 שעות"))
        assertEquals(140, parseActivityDurationMinutes("2:20"))
    }

    @Test
    fun flexibleActivitiesFollowThePreviousActivityEnd() {
        val activities = listOf(
            activity("flight", "10:00", "2:30 שעות", fixed = true),
            activity("lunch", "11:00", "45 דקות"),
            activity("museum", "18:00", "שעה")
        )

        val normalized = normalizeActivityStartTimes(activities) { it.fixedTime }

        assertEquals(listOf("10:00", "12:30", "13:15"), normalized.map { it.time })
    }

    @Test
    fun fixedActivitiesKeepTheirTimeAndResetTheFollowingSchedule() {
        val activities = listOf(
            activity("walk", "09:00", "שעה"),
            activity("flight", "15:00", "2:20", fixed = true),
            activity("hotel", "16:00", "30 דקות")
        )

        val normalized = normalizeActivityStartTimes(activities) { it.fixedTime }

        assertEquals(listOf("09:00", "15:00", "17:20"), normalized.map { it.time })
    }

    private fun activity(
        id: String,
        time: String,
        duration: String,
        fixed: Boolean = false
    ) = ActivityItem(
        id = id,
        name = id,
        time = time,
        duration = duration,
        fixedTime = fixed
    )
}
