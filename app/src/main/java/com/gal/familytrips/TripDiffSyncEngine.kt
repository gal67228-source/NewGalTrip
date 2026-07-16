package com.gal.familytrips

class TripDiffSyncEngine(
    private val repository: V9CloudRepository
) {
    suspend fun sync(
        old: Trip,
        new: Trip,
        profile: CloudUserProfile
    ): Trip {
        if (
            !new.cloudEnabled ||
            old.id != new.id
        ) {
            return new
        }

        if (
            old.cloudSchemaVersion < 9 ||
            new.cloudSchemaVersion < 9
        ) {
            return repository.migrateTrip(
                new,
                profile
            )
        }

        repository.updateTripMetadata(
            old,
            new,
            profile.userId
        )

        syncActivities(
            old,
            new,
            profile.userId
        )

        syncCollection(
            tripId = new.id,
            collection = "flights",
            oldItems = old.flights,
            newItems = new.flights,
            idOf = { it.id },
            serializer = Flight.serializer(),
            userId = profile.userId
        )

        syncCollection(
            new.id,
            "hotels",
            old.hotels,
            new.hotels,
            { it.id },
            Hotel.serializer(),
            profile.userId
        )

        syncCollection(
            new.id,
            "restaurants",
            old.restaurants,
            new.restaurants,
            { it.id },
            Restaurant.serializer(),
            profile.userId
        )

        syncCollection(
            new.id,
            "expenses",
            old.expenses,
            new.expenses,
            { it.id },
            Expense.serializer(),
            profile.userId
        )

        syncCollection(
            new.id,
            "packing",
            old.packingItems,
            new.packingItems,
            { it.id },
            PackingItem.serializer(),
            profile.userId
        )

        syncCollection(
            new.id,
            "documents",
            old.documents,
            new.documents,
            { it.id },
            TripDocument.serializer(),
            profile.userId
        )

        syncCollection(
            new.id,
            "destinationStays",
            old.destinationStays,
            new.destinationStays,
            { it.id },
            DestinationStay.serializer(),
            profile.userId
        )

        syncDays(
            old,
            new,
            profile.userId
        )

        val now = System.currentTimeMillis()
        return new.copy(
            cloudSchemaVersion = 9,
            lastSyncedAt = now,
            updatedAt = now,
            updatedBy = profile.userId
        )
    }

    private suspend fun syncActivities(
        old: Trip,
        new: Trip,
        userId: String
    ) {
        val oldLocations =
            activityLocations(old)
        val newLocations =
            activityLocations(new)

        val deletedIds =
            oldLocations.keys -
                newLocations.keys

        deletedIds.forEach { id ->
            repository.deleteEntity(
                new.id,
                "activities",
                id
            )
        }

        val patches = mutableListOf<ActivityPatch>()

        newLocations.forEach {
            (id, newLocation) ->
            val oldLocation =
                oldLocations[id]

            if (oldLocation == null) {
                repository.upsertEntity(
                    tripId = new.id,
                    collection = "activities",
                    entityId = id,
                    entity = CloudActivity(
                        dayId =
                            newLocation.dayId,
                        position =
                            newLocation.position,
                        activity =
                            newLocation.activity
                    ),
                    serializer =
                        CloudActivity.serializer(),
                    userId = userId,
                    metadata = mapOf(
                        "dayId" to
                            newLocation.dayId,
                        "position" to
                            newLocation.position
                    )
                )
            } else if (
                oldLocation != newLocation
            ) {
                patches += ActivityPatch(
                    old =
                        oldLocation.activity,
                    new =
                        newLocation.activity,
                    oldDayId =
                        oldLocation.dayId,
                    newDayId =
                        newLocation.dayId,
                    oldPosition =
                        oldLocation.position,
                    newPosition =
                        newLocation.position
                )
            }
        }

        repository.updateActivityBatch(
            new.id,
            patches,
            userId
        )
    }

    private suspend fun syncDays(
        old: Trip,
        new: Trip,
        userId: String
    ) {
        val oldDays = old.days.associateBy {
            it.id
        }
        val newDays = new.days.associateBy {
            it.id
        }

        (oldDays.keys - newDays.keys)
            .forEach { id ->
                repository.deleteEntity(
                    new.id,
                    "days",
                    id
                )
            }

        newDays.forEach { (id, day) ->
            val cloudDay =
                day.copy(
                    activities = emptyList()
                )
            val oldCloudDay =
                oldDays[id]?.copy(
                    activities = emptyList()
                )

            if (oldCloudDay != cloudDay) {
                repository.upsertEntity(
                    new.id,
                    "days",
                    id,
                    cloudDay,
                    TripDay.serializer(),
                    userId,
                    mapOf(
                        "date" to day.date,
                        "destination" to
                            day.destination
                    )
                )
            }
        }
    }

    private suspend fun <T> syncCollection(
        tripId: String,
        collection: String,
        oldItems: List<T>,
        newItems: List<T>,
        idOf: (T) -> String,
        serializer:
            kotlinx.serialization.KSerializer<T>,
        userId: String
    ) {
        val oldById =
            oldItems.associateBy(idOf)
        val newById =
            newItems.associateBy(idOf)

        (oldById.keys - newById.keys)
            .forEach { id ->
                repository.deleteEntity(
                    tripId,
                    collection,
                    id
                )
            }

        newById.forEach { (id, item) ->
            if (oldById[id] != item) {
                repository.upsertEntity(
                    tripId,
                    collection,
                    id,
                    item,
                    serializer,
                    userId
                )
            }
        }
    }

    private fun activityLocations(
        trip: Trip
    ): Map<String, ActivityLocation> {
        return buildMap {
            trip.days.forEach { day ->
                day.activities.forEachIndexed {
                    index,
                    activity ->
                    put(
                        activity.id,
                        ActivityLocation(
                            day.id,
                            index,
                            activity
                        )
                    )
                }
            }
        }
    }

    private data class ActivityLocation(
        val dayId: String,
        val position: Int,
        val activity: ActivityItem
    )
}
