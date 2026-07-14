package com.gal.familytrips

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface TripRepository {
    val state: Flow<AppState>
    suspend fun load(): AppState
    suspend fun save(state: AppState)
    suspend fun updateTrip(trip: Trip)
    suspend fun deleteTrip(tripId: String)
    suspend fun setCurrentTrip(tripId: String)
}

class LocalTripRepository(
    initialState: AppState,
    private val onPersist: suspend (AppState) -> Unit
) : TripRepository {
    private val mutableState = MutableStateFlow(initialState)
    override val state: Flow<AppState> = mutableState.asStateFlow()
    override suspend fun load(): AppState = mutableState.value

    override suspend fun save(state: AppState) {
        mutableState.value = state
        onPersist(state)
    }

    override suspend fun updateTrip(trip: Trip) {
        val current = mutableState.value
        save(
            current.copy(
                trips = current.trips.map {
                    if (it.id == trip.id) trip else it
                }
            )
        )
    }

    override suspend fun deleteTrip(tripId: String) {
        val current = mutableState.value
        val remaining = current.trips.filterNot { it.id == tripId }
        if (remaining.isEmpty()) return

        save(
            current.copy(
                trips = remaining,
                currentTripId = if (
                    current.currentTripId == tripId
                ) remaining.first().id else current.currentTripId
            )
        )
    }

    override suspend fun setCurrentTrip(tripId: String) {
        val current = mutableState.value
        if (current.trips.none { it.id == tripId }) return
        save(current.copy(currentTripId = tripId))
    }
}

interface CloudTripRepository : TripRepository {
    suspend fun signInWithGoogle(): CloudUserProfile
    suspend fun signOut()
    suspend fun enableCloudForTrip(tripId: String)
    suspend fun inviteMember(
        tripId: String,
        email: String,
        role: String
    )
}

class DisabledCloudTripRepository(
    private val local: TripRepository
) : CloudTripRepository, TripRepository by local {
    override suspend fun signInWithGoogle(): CloudUserProfile =
        error("Firebase עדיין לא הוגדר")

    override suspend fun signOut() = Unit

    override suspend fun enableCloudForTrip(tripId: String) =
        error("Firebase עדיין לא הוגדר")

    override suspend fun inviteMember(
        tripId: String,
        email: String,
        role: String
    ) = error("Firebase עדיין לא הוגדר")
}
