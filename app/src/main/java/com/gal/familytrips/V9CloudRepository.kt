package com.gal.familytrips

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class V9CloudRepository(
    private val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun migrateTrip(
        trip: Trip,
        profile: CloudUserProfile
    ): Trip {
        val now = System.currentTimeMillis()
        val prepared =
            CloudFoundation.prepareTripForCloud(
                trip,
                profile
            ).copy(
                cloudEnabled = true,
                cloudSchemaVersion = 9,
                v9MigratedAt = now,
                updatedAt = now,
                updatedBy = profile.userId,
                lastSyncedAt = now,
                cloudRevision =
                    trip.cloudRevision + 1
            )

        val tripRef = firestore.collection("trips")
            .document(prepared.id)

        val memberIds = prepared.members
            .map { it.userId }
            .plus(profile.userId)
            .distinct()

        val editorIds = prepared.members
            .filter {
                it.role == "owner" ||
                    it.role == "editor"
            }
            .map { it.userId }
            .plus(profile.userId)
            .distinct()

        tripRef.set(
            mapOf(
                "name" to prepared.name,
                "destination" to prepared.destination,
                "destinationStops" to prepared.destinationStops,
                "startDate" to prepared.startDate,
                "endDate" to prepared.endDate,
                "packingCategories" to prepared.packingCategories,
                "offlineMode" to prepared.offlineMode,
                "ownerUserId" to prepared.ownerUserId,
                "memberIds" to memberIds,
                "editorIds" to editorIds,
                "cloudSchemaVersion" to 9,
                "cloudRevision" to prepared.cloudRevision,
                "v9MigratedAt" to now,
                "updatedAt" to now,
                "updatedBy" to profile.userId
            ),
            SetOptions.merge()
        ).await()

        writeEntities(
            tripRef,
            "members",
            prepared.members,
            { it.userId },
            TripMember.serializer()
        ) {
            mapOf(
                "role" to it.role,
                "email" to it.email
            )
        }

        writeEntities(
            tripRef,
            "days",
            prepared.days.map {
                it.copy(activities = emptyList())
            },
            { it.id },
            TripDay.serializer()
        ) {
            mapOf(
                "date" to it.date,
                "destination" to it.destination
            )
        }

        val activities = prepared.days.flatMap {
            day ->
            day.activities.mapIndexed {
                index,
                activity ->
                CloudActivity(
                    dayId = day.id,
                    position = index,
                    activity = activity
                )
            }
        }

        writeEntities(
            tripRef,
            "activities",
            activities,
            { it.activity.id },
            CloudActivity.serializer()
        ) {
            mapOf(
                "dayId" to it.dayId,
                "position" to it.position
            )
        }

        writeEntities(
            tripRef,
            "flights",
            prepared.flights,
            { it.id },
            Flight.serializer()
        )
        writeEntities(
            tripRef,
            "hotels",
            prepared.hotels,
            { it.id },
            Hotel.serializer()
        )
        writeEntities(
            tripRef,
            "restaurants",
            prepared.restaurants,
            { it.id },
            Restaurant.serializer()
        )
        writeEntities(
            tripRef,
            "expenses",
            prepared.expenses,
            { it.id },
            Expense.serializer()
        )
        writeEntities(
            tripRef,
            "packing",
            prepared.packingItems,
            { it.id },
            PackingItem.serializer()
        )
        writeEntities(
            tripRef,
            "documents",
            prepared.documents,
            { it.id },
            TripDocument.serializer()
        )
        writeEntities(
            tripRef,
            "destinationStays",
            prepared.destinationStays,
            { it.id },
            DestinationStay.serializer()
        )

        return prepared
    }

    suspend fun updateActivity(
        tripId: String,
        dayId: String,
        position: Int,
        old: ActivityItem,
        new: ActivityItem,
        userId: String
    ) {
        val values = ActivityDiff
            .changedFields(old, new)
            .toMutableMap()

        if (values.isEmpty()) return

        values["dayId"] = dayId
        values["position"] = position
        values["updatedAt"] =
            System.currentTimeMillis()
        values["updatedBy"] = userId
        values["revision"] =
            com.google.firebase.firestore
                .FieldValue.increment(1)

        firestore.collection("trips")
            .document(tripId)
            .collection("activities")
            .document(new.id)
            .set(values, SetOptions.merge())
            .await()
    }

    suspend fun updateActivityBatch(
        tripId: String,
        changes: List<ActivityPatch>,
        userId: String
    ) {
        if (changes.isEmpty()) return

        val batch = firestore.batch()
        val tripRef = firestore.collection("trips")
            .document(tripId)
        val now = System.currentTimeMillis()

        changes.forEach { patch ->
            val values = ActivityDiff
                .changedFields(
                    patch.old,
                    patch.new
                )
                .toMutableMap()

            if (
                patch.oldPosition !=
                    patch.newPosition
            ) {
                values["position"] =
                    patch.newPosition
            }

            if (
                patch.oldDayId !=
                    patch.newDayId
            ) {
                values["dayId"] =
                    patch.newDayId
            }

            if (values.isNotEmpty()) {
                values["updatedAt"] = now
                values["updatedBy"] = userId
                values["revision"] =
                    com.google.firebase.firestore
                        .FieldValue.increment(1)

                batch.set(
                    tripRef.collection("activities")
                        .document(patch.new.id),
                    values,
                    SetOptions.merge()
                )
            }
        }

        batch.commit().await()
    }

    suspend fun deleteEntity(
        tripId: String,
        collection: String,
        entityId: String
    ) {
        require(
            collection in ALLOWED_COLLECTIONS
        )

        firestore.collection("trips")
            .document(tripId)
            .collection(collection)
            .document(entityId)
            .delete()
            .await()
    }

    private suspend fun <T> writeEntities(
        tripRef:
            com.google.firebase.firestore
                .DocumentReference,
        collection: String,
        entities: List<T>,
        idOf: (T) -> String,
        serializer: KSerializer<T>,
        metadata: (T) -> Map<String, Any> = {
            emptyMap()
        }
    ) {
        entities.chunked(400).forEach {
            chunk ->
            val batch = firestore.batch()
            val now = System.currentTimeMillis()

            chunk.forEach { entity ->
                val values = mutableMapOf<String, Any>(
                    "payload" to
                        json.encodeToString(
                            serializer,
                            entity
                        ),
                    "updatedAt" to now,
                    "revision" to 1L
                )
                values.putAll(metadata(entity))

                batch.set(
                    tripRef.collection(collection)
                        .document(idOf(entity)),
                    values,
                    SetOptions.merge()
                )
            }

            batch.commit().await()
        }
    }

    suspend fun updateTripMetadata(
        old: Trip,
        new: Trip,
        userId: String
    ) {
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

        changed("name", old.name, new.name)
        changed(
            "destination",
            old.destination,
            new.destination
        )
        changed(
            "destinationStops",
            old.destinationStops,
            new.destinationStops
        )
        changed(
            "startDate",
            old.startDate,
            new.startDate
        )
        changed(
            "endDate",
            old.endDate,
            new.endDate
        )
        changed(
            "packingCategories",
            old.packingCategories,
            new.packingCategories
        )
        changed(
            "offlineMode",
            old.offlineMode,
            new.offlineMode
        )

        if (changes.isEmpty()) return

        changes["updatedAt"] =
            System.currentTimeMillis()
        changes["updatedBy"] = userId
        changes["cloudRevision"] =
            com.google.firebase.firestore
                .FieldValue.increment(1)

        firestore.collection("trips")
            .document(new.id)
            .set(changes, SetOptions.merge())
            .await()
    }

    suspend fun <T> upsertEntity(
        tripId: String,
        collection: String,
        entityId: String,
        entity: T,
        serializer: KSerializer<T>,
        userId: String,
        metadata: Map<String, Any> =
            emptyMap()
    ) {
        require(
            collection in ALLOWED_COLLECTIONS
        )

        val values = mutableMapOf<String, Any>(
            "payload" to json.encodeToString(
                serializer,
                entity
            ),
            "updatedAt" to
                System.currentTimeMillis(),
            "updatedBy" to userId,
            "revision" to
                com.google.firebase.firestore
                    .FieldValue.increment(1)
        )
        values.putAll(metadata)

        firestore.collection("trips")
            .document(tripId)
            .collection(collection)
            .document(entityId)
            .set(values, SetOptions.merge())
            .await()
    }


    fun listenActivitiesForDay(
        tripId: String,
        dayId: String,
        onChange: (List<CloudActivity>) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration {
        return firestore.collection("trips")
            .document(tripId)
            .collection("activities")
            .whereEqualTo("dayId", dayId)
            .addSnapshotListener {
                snapshot,
                error ->

                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                val items = snapshot?.documents
                    ?.mapNotNull { document ->
                        document.getString("payload")
                            ?.let { raw ->
                                runCatching {
                                    json.decodeFromString(
                                        CloudActivity.serializer(),
                                        raw
                                    )
                                }.getOrNull()
                            }
                    }
                    ?.sortedBy { it.position }
                    .orEmpty()

                onChange(items)
            }
    }

    fun <T> listenCollection(
        tripId: String,
        collection: String,
        serializer: KSerializer<T>,
        onChange: (List<T>) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration {
        require(
            collection in ALLOWED_COLLECTIONS
        )

        return firestore.collection("trips")
            .document(tripId)
            .collection(collection)
            .addSnapshotListener {
                snapshot,
                error ->

                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                val items = snapshot?.documents
                    ?.mapNotNull { document ->
                        document.getString("payload")
                            ?.let { raw ->
                                runCatching {
                                    json.decodeFromString(
                                        serializer,
                                        raw
                                    )
                                }.getOrNull()
                            }
                    }
                    .orEmpty()

                onChange(items)
            }
    }


    companion object {
        private val ALLOWED_COLLECTIONS =
            setOf(
                "days",
                "activities",
                "flights",
                "hotels",
                "restaurants",
                "expenses",
                "packing",
                "documents",
                "destinationStays"
            )
    }
}
