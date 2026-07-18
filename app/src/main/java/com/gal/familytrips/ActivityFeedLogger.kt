package com.gal.familytrips

import java.util.UUID

object ActivityFeedLogger {
    suspend fun log(
        manager: FirebaseCloudManager,
        tripId: String,
        type: String,
        entityType: String,
        entityId: String,
        title: String,
        description: String,
        profile: CloudUserProfile
    ) {
        manager.addActivityEvent(
            TripActivityEvent(
                id = UUID.randomUUID().toString(),
                tripId = tripId,
                type = type,
                entityType = entityType,
                entityId = entityId,
                title = title,
                description = description,
                userId = profile.userId,
                userName = profile.displayName,
                createdAt =
                    System.currentTimeMillis()
            )
        )
    }
}
