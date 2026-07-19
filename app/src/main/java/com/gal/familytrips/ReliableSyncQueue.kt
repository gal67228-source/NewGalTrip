package com.gal.familytrips

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

class ReliableSyncQueue(
    context: Context
) {
    private val preferences =
        context.getSharedPreferences(
            "familygo_sync_queue",
            Context.MODE_PRIVATE
        )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Synchronized
    fun enqueue(
        old: Trip,
        new: Trip,
        profile: CloudUserProfile,
        error: Throwable
    ) {
        val existing = load().toMutableList()
        val sameTripIndex = existing.indexOfFirst {
            it.tripId == new.id
        }

        val operation = PendingSyncOperation(
            id = if (sameTripIndex >= 0) {
                existing[sameTripIndex].id
            } else {
                UUID.randomUUID().toString()
            },
            tripId = new.id,
            oldTripJson = json.encodeToString(
                Trip.serializer(),
                old
            ),
            newTripJson = json.encodeToString(
                Trip.serializer(),
                new
            ),
            profileJson = json.encodeToString(
                CloudUserProfile.serializer(),
                profile
            ),
            createdAt = if (sameTripIndex >= 0) {
                existing[sameTripIndex].createdAt
            } else {
                System.currentTimeMillis()
            },
            attempts = if (sameTripIndex >= 0) {
                existing[sameTripIndex].attempts + 1
            } else {
                1
            },
            lastError = error.localizedMessage.orEmpty()
        )

        if (sameTripIndex >= 0) {
            existing[sameTripIndex] = operation
        } else {
            existing += operation
        }

        save(existing)
    }

    @Synchronized
    fun load(): List<PendingSyncOperation> {
        val raw = preferences.getString(
            KEY_OPERATIONS,
            null
        ) ?: return emptyList()

        return runCatching {
            json.decodeFromString(
                ListSerializer(
                    PendingSyncOperation.serializer()
                ),
                raw
            )
        }.getOrDefault(emptyList())
    }

    fun pendingCount(): Int = load().size

    suspend fun retryAll(
        syncEngine: TripDiffSyncEngine
    ): Int {
        val remaining =
            mutableListOf<PendingSyncOperation>()
        var completed = 0

        load().forEach { operation ->
            val result = runCatching {
                val old = json.decodeFromString(
                    Trip.serializer(),
                    operation.oldTripJson
                )
                val new = json.decodeFromString(
                    Trip.serializer(),
                    operation.newTripJson
                )
                val profile = json.decodeFromString(
                    CloudUserProfile.serializer(),
                    operation.profileJson
                )
                syncEngine.sync(old, new, profile)
            }

            if (result.isSuccess) {
                completed += 1
            } else {
                remaining += operation.copy(
                    attempts = operation.attempts + 1,
                    lastError = result.exceptionOrNull()
                        ?.localizedMessage
                        .orEmpty()
                )
            }
        }

        save(remaining)
        return completed
    }

    @Synchronized
    fun clear() {
        preferences.edit()
            .remove(KEY_OPERATIONS)
            .apply()
    }

    private fun save(
        operations: List<PendingSyncOperation>
    ) {
        preferences.edit()
            .putString(
                KEY_OPERATIONS,
                json.encodeToString(
                    ListSerializer(
                        PendingSyncOperation.serializer()
                    ),
                    operations
                )
            )
            .apply()
    }

    companion object {
        private const val KEY_OPERATIONS =
            "operations"
    }
}
