package com.gal.familytrips

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncConflict(
    val tripId: String,
    val entityType: String,
    val entityId: String,
    val localRevision: Long,
    val remoteRevision: Long,
    val remoteUpdatedBy: String,
    val remoteUpdatedAt: Long,
    val message: String
)

class SyncConflictException(
    val conflict: SyncConflict
) : IllegalStateException(
    conflict.message
)

object ConflictCenter {
    private val mutableConflict =
        MutableStateFlow<SyncConflict?>(null)

    val conflict = mutableConflict.asStateFlow()

    fun publish(value: SyncConflict) {
        mutableConflict.value = value
    }

    fun clear() {
        mutableConflict.value = null
    }
}
