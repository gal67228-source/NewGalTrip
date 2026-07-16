package com.gal.familytrips

import kotlinx.serialization.Serializable

@Serializable
data class CloudActivity(
    val dayId: String,
    val position: Int,
    val activity: ActivityItem
)

data class ActivityPatch(
    val old: ActivityItem,
    val new: ActivityItem,
    val oldDayId: String,
    val newDayId: String,
    val oldPosition: Int,
    val newPosition: Int
)

object ActivityDiff {
    // Cloud revision metadata is intentionally excluded.
    fun changedFields(
        old: ActivityItem,
        new: ActivityItem
    ): Map<String, Any> {
        val changes = linkedMapOf<String, Any>()

        fun changed(
            key: String,
            before: Any?,
            after: Any?
        ) {
            if (before != after) {
                changes[key] = after ?: ""
            }
        }

        changed("time", old.time, new.time)
        changed("name", old.name, new.name)
        changed("location", old.location, new.location)
        changed("transport", old.transport, new.transport)
        changed("directions", old.directions, new.directions)
        changed("duration", old.duration, new.duration)
        changed("cost", old.cost, new.cost)
        changed("notes", old.notes, new.notes)
        changed("mapsUrl", old.mapsUrl, new.mapsUrl)
        changed("completed", old.completed, new.completed)
        changed("fixedTime", old.fixedTime, new.fixedTime)
        changed("latitude", old.latitude, new.latitude)
        changed("longitude", old.longitude, new.longitude)
        changed("transitionMode", old.transitionMode, new.transitionMode)
        changed("transitionMinutes", old.transitionMinutes, new.transitionMinutes)
        changed(
            "transitionAutomatic",
            old.transitionAutomatic,
            new.transitionAutomatic
        )
        changed(
            "transitionDetails",
            old.transitionDetails,
            new.transitionDetails
        )
        changed(
            "routeDistanceMeters",
            old.routeDistanceMeters,
            new.routeDistanceMeters
        )
        changed("routeSource", old.routeSource, new.routeSource)
        changed("routeStatus", old.routeStatus, new.routeStatus)
        changed("routeCacheKey", old.routeCacheKey, new.routeCacheKey)
        changed(
            "routeUpdatedAt",
            old.routeUpdatedAt,
            new.routeUpdatedAt
        )
        changed("liveStatus", old.liveStatus, new.liveStatus)
        changed(
            "actualStartTime",
            old.actualStartTime,
            new.actualStartTime
        )
        changed(
            "actualEndTime",
            old.actualEndTime,
            new.actualEndTime
        )
        changed("skipped", old.skipped, new.skipped)

        return changes
    }
}
