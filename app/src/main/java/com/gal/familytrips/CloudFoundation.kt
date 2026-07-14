package com.gal.familytrips

import java.util.UUID

object CloudFoundation {
    fun ensureLocalIdentity(state: AppState): AppState {
        if (state.currentUser != null) return state

        return state.copy(
            currentUser = CloudUserProfile(
                userId = "local-${UUID.randomUUID()}",
                displayName = "משתמש מקומי",
                provider = "local"
            )
        )
    }

    fun prepareTripForCloud(
        trip: Trip,
        user: CloudUserProfile
    ): Trip {
        val owner = TripMember(
            userId = user.userId,
            displayName = user.displayName,
            email = user.email,
            role = "owner",
            joinedAt = System.currentTimeMillis(),
            lastSeenAt = System.currentTimeMillis()
        )

        return trip.copy(
            ownerUserId = trip.ownerUserId.ifBlank { user.userId },
            updatedAt = System.currentTimeMillis(),
            updatedBy = user.userId,
            members = if (
                trip.members.any { it.userId == user.userId }
            ) trip.members else trip.members + owner
        )
    }

    fun roleLabel(role: String): String = when (role) {
        "owner" -> "בעלים"
        "editor" -> "עורך"
        else -> "צופה"
    }
}
