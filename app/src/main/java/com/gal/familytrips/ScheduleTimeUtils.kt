package com.gal.familytrips

internal fun parseActivityDurationMinutes(value: String): Int {
    val normalized = value
        .trim()
        .lowercase()
        .replace("כשעה", "שעה")
        .replace("כ-", "")
        .replace("כ", "")

    // A duration such as "2:30 שעות" used to be interpreted as 30 hours,
    // because the old hours regex started matching after the colon.
    Regex("""(\d{1,2}):(\d{2})(?:\s*(?:שעה|שעות|hours?|hrs?|h))?""")
        .find(normalized)
        ?.let { match ->
            val hours = match.groupValues[1].toIntOrNull() ?: 0
            val minutes = match.groupValues[2].toIntOrNull() ?: 0
            if (minutes in 0..59 && (hours > 0 || minutes > 0)) {
                return hours * 60 + minutes
            }
        }

    if ("שעה וחצי" in normalized) return 90
    if ("חצי שעה" in normalized) return 30
    if ("שעתיים" in normalized) {
        val minutes = Regex("""(\d+)\s*דקות?""")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
        return 120 + minutes
    }
    if ("שלוש שעות" in normalized) return 180

    Regex("""(\d+(?:\.\d+)?)\s*(?:שעה|שעות|hours?|hrs?|h)""")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.toDoubleOrNull()
        ?.let { hours ->
            val extraMinutes = Regex("""(\d+)\s*(?:דקות?|minutes?|mins?|m)""")
                .find(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: 0
            return (hours * 60).toInt() + extraMinutes
        }

    Regex("""(\d+)\s*(?:דקות?|minutes?|mins?|m)""")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.let { return it.coerceAtLeast(5) }

    if ("שעה" in normalized) return 60
    if ("45" in normalized) return 45
    if ("30" in normalized) return 30
    return 60
}

internal fun normalizeActivityStartTimes(
    activities: List<ActivityItem>,
    isFixed: (ActivityItem) -> Boolean
): List<ActivityItem> {
    var previousEnd: Int? = null

    return activities.map { activity ->
        val enteredStart = parseActivityClockMinutes(activity.time)
        val fixed = isFixed(activity)
        val start = when {
            fixed -> enteredStart
            previousEnd != null -> previousEnd!! + activity.transitionMinutes.coerceAtLeast(0)
            else -> enteredStart
        }

        if (start != null) {
            previousEnd = start + parseActivityDurationMinutes(activity.duration)
        }

        if (!fixed && start != null && start != enteredStart) {
            activity.copy(time = formatActivityClock(start))
        } else {
            activity
        }
    }
}

internal fun parseActivityClockMinutes(value: String): Int? {
    val match = Regex("""(\d{1,2}):(\d{2})""").find(value) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
}

internal fun formatActivityClock(totalMinutes: Int): String {
    val safe = totalMinutes.coerceIn(0, 23 * 60 + 59)
    return "%02d:%02d".format(safe / 60, safe % 60)
}
