package com.gal.familytrips

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DiffSyncCoordinator(
    private val scope: CoroutineScope,
    private val syncEngine:
        TripDiffSyncEngine,
    private val debounceMillis: Long = 650L,
    private val onSynced:
        suspend (Trip) -> Unit,
    private val onError:
        suspend (Throwable) -> Unit
) {
    private var pendingJob: Job? = null
    private var baseTrip: Trip? = null
    private var latestTrip: Trip? = null
    private var profile:
        CloudUserProfile? = null

    fun enqueue(
        old: Trip,
        new: Trip,
        currentProfile:
            CloudUserProfile
    ) {
        if (baseTrip == null) {
            baseTrip = old
        }
        latestTrip = new
        profile = currentProfile

        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(debounceMillis)
            flush()
        }
    }

    suspend fun flush() {
        val base = baseTrip ?: return
        val latest = latestTrip ?: return
        val user = profile ?: return

        baseTrip = null
        latestTrip = null
        profile = null

        runCatching {
            syncEngine.sync(
                base,
                latest,
                user
            )
        }.onSuccess {
            onSynced(it)
        }.onFailure {
            error ->
            if (
                error is SyncConflictException
            ) {
                ConflictCenter.publish(
                    error.conflict
                )
            }
            onError(error)
        }
    }

    fun cancel() {
        pendingJob?.cancel()
        pendingJob = null
        baseTrip = null
        latestTrip = null
        profile = null
    }
}
