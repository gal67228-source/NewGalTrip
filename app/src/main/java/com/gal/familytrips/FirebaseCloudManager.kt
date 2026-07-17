package com.gal.familytrips

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

class FirebaseCloudManager(
    private val activity: Activity
) {
    private val v9Repository =
        V9CloudRepository()
    private val diffSyncEngine =
        TripDiffSyncEngine(v9Repository)

    private val auth =
        FirebaseAuth.getInstance()
    private val firestore =
        FirebaseFirestore.getInstance().also {
            FirestoreCachePolicy.configure(it)
        }
    private val credentialManager =
        CredentialManager.create(activity)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun currentProfile(): CloudUserProfile? {
        val user = auth.currentUser ?: return null
        return CloudUserProfile(
            userId = user.uid,
            displayName = user.displayName
                ?: user.email
                ?: "משתמש",
            email = user.email.orEmpty(),
            photoUrl = user.photoUrl?.toString().orEmpty(),
            provider = "google"
        )
    }

    suspend fun signInWithGoogle():
        CloudUserProfile {
        val serverClientId =
            activity.getString(
                R.string.default_web_client_id
            )

        val credential = try {
            val googleIdOption =
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(
                        false
                    )
                    .setServerClientId(
                        serverClientId
                    )
                    .setAutoSelectEnabled(false)
                    .build()

            val request =
                GetCredentialRequest.Builder()
                    .addCredentialOption(
                        googleIdOption
                    )
                    .build()

            credentialManager.getCredential(
                context = activity,
                request = request
            ).credential
        } catch (
            error: NoCredentialException
        ) {
            val explicitGoogleOption =
                GetSignInWithGoogleOption
                    .Builder(
                        serverClientId
                    )
                    .build()

            val explicitRequest =
                GetCredentialRequest.Builder()
                    .addCredentialOption(
                        explicitGoogleOption
                    )
                    .build()

            credentialManager.getCredential(
                context = activity,
                request = explicitRequest
            ).credential
        }

        if (
            credential !is CustomCredential ||
            credential.type !=
                GoogleIdTokenCredential
                    .TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            error(
                "לא התקבל חשבון Google תקין"
            )
        }

        val googleCredential =
            GoogleIdTokenCredential.createFrom(
                credential.data
            )

        val firebaseCredential =
            GoogleAuthProvider.getCredential(
                googleCredential.idToken,
                null
            )

        auth.signInWithCredential(
            firebaseCredential
        ).await()

        return currentProfile()
            ?: error(
                "ההתחברות ל-Google נכשלה"
            )
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun migrateTripToV9(
        trip: Trip,
        profile: CloudUserProfile
    ): Trip = v9Repository.migrateTrip(
        trip,
        profile
    )

    suspend fun syncTripDiff(
        old: Trip,
        new: Trip,
        profile: CloudUserProfile
    ): Trip = diffSyncEngine.sync(
        old,
        new,
        profile
    )

    suspend fun uploadTrip(
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
                cloudRevision =
                    trip.cloudRevision + 1,
                lastSyncedAt = now,
                updatedAt = now,
                updatedBy = profile.userId
            )

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

        firestore.collection("trips")
            .document(prepared.id)
            .set(
                mapOf(
                    "tripJson" to json.encodeToString(
                        Trip.serializer(),
                        prepared
                    ),
                    "ownerUserId" to
                        prepared.ownerUserId,
                    "memberIds" to memberIds,
                    "editorIds" to editorIds,
                    "updatedAt" to now,
                    "updatedBy" to profile.userId,
                    "cloudRevision" to
                        prepared.cloudRevision
                )
            )
            .await()

        return prepared
    }

    fun listenToTrip(
        tripId: String,
        onTrip: (Trip) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        return firestore.collection("trips")
            .document(tripId)
            .addSnapshotListener {
                snapshot,
                error ->

                if (error != null) {
                    onError(
                        error.localizedMessage
                            ?: "שגיאת סנכרון"
                    )
                    return@addSnapshotListener
                }

                val raw = snapshot
                    ?.getString("tripJson")
                    ?: return@addSnapshotListener

                runCatching {
                    json.decodeFromString(
                        Trip.serializer(),
                        raw
                    )
                }.onSuccess(onTrip)
                    .onFailure {
                        onError(
                            it.localizedMessage
                                ?: "הטיול בענן אינו תקין"
                        )
                    }
            }
    }

    suspend fun createInvite(
        trip: Trip,
        profile: CloudUserProfile,
        role: String = "editor"
    ): TripInvite {
        if (trip.ownerUserId != profile.userId) {
            error("רק בעל הטיול יכול להזמין חברים")
        }

        val code = (1..6)
            .map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }
            .joinToString("")
        val now = System.currentTimeMillis()
        val invite = TripInvite(
            code = code,
            tripId = trip.id,
            tripName = trip.name,
            role = role,
            createdBy = profile.userId,
            createdAt = now,
            expiresAt = now + 7L * 24 * 60 * 60 * 1000
        )

        firestore.collection("invites")
            .document(code)
            .set(
                mapOf(
                    "code" to code,
                    "tripId" to trip.id,
                    "tripName" to trip.name,
                    "role" to role,
                    "createdBy" to profile.userId,
                    "createdAt" to now,
                    "expiresAt" to invite.expiresAt
                )
            ).await()

        firestore.collection("trips")
            .document(trip.id)
            .update("inviteCodes", FieldValue.arrayUnion(code))
            .await()

        return invite
    }

    suspend fun joinTrip(
        code: String,
        profile: CloudUserProfile
    ): Trip {
        val cleanCode = code.trim().uppercase()
        val inviteSnapshot = firestore.collection("invites")
            .document(cleanCode)
            .get().await()

        if (!inviteSnapshot.exists()) {
            error("קוד ההזמנה אינו קיים")
        }

        val expiresAt = inviteSnapshot.getLong("expiresAt") ?: 0L
        if (expiresAt < System.currentTimeMillis()) {
            error("תוקף קוד ההזמנה פג")
        }

        val tripId = inviteSnapshot.getString("tripId")
            ?: error("קוד ההזמנה אינו תקין")
        val role = inviteSnapshot.getString("role") ?: "editor"
        val tripRef = firestore.collection("trips").document(tripId)
        val snapshot = tripRef.get().await()
        val raw = snapshot.getString("tripJson")
            ?: error("הטיול המשותף לא נמצא")
        val existing = json.decodeFromString(Trip.serializer(), raw)

        val member = TripMember(
            userId = profile.userId,
            displayName = profile.displayName,
            email = profile.email,
            role = role,
            joinedAt = System.currentTimeMillis(),
            lastSeenAt = System.currentTimeMillis()
        )
        val updated = existing.copy(
            cloudEnabled = true,
            members = existing.members
                .filterNot { it.userId == profile.userId } + member,
            cloudRevision = existing.cloudRevision + 1,
            updatedAt = System.currentTimeMillis(),
            updatedBy = profile.userId,
            lastSyncedAt = System.currentTimeMillis()
        )
        val memberIds = updated.members.map { it.userId }
            .plus(updated.ownerUserId).filter { it.isNotBlank() }.distinct()
        val editorIds = updated.members.filter {
            it.role == "owner" || it.role == "editor"
        }.map { it.userId }.plus(updated.ownerUserId)
            .filter { it.isNotBlank() }.distinct()

        tripRef.update(
            mapOf(
                "tripJson" to json.encodeToString(Trip.serializer(), updated),
                "memberIds" to memberIds,
                "editorIds" to editorIds,
                "updatedAt" to updated.updatedAt,
                "updatedBy" to profile.userId,
                "cloudRevision" to updated.cloudRevision,
                "lastJoinCode" to cleanCode
            )
        ).await()

        return updated
    }

    fun listenActivitiesForDayV9(
        tripId: String,
        dayId: String,
        onChange: (List<CloudActivity>) -> Unit,
        onError: (Throwable) -> Unit
    ): com.google.firebase.firestore
        .ListenerRegistration =
        v9Repository.listenActivitiesForDay(
            tripId,
            dayId,
            onChange,
            onError
        )

    fun <T> listenCollectionV9(
        tripId: String,
        collection: String,
        serializer:
            kotlinx.serialization.KSerializer<T>,
        onChange: (List<T>) -> Unit,
        onError: (Throwable) -> Unit
    ): com.google.firebase.firestore
        .ListenerRegistration =
        v9Repository.listenCollection(
            tripId,
            collection,
            serializer,
            onChange,
            onError
        )


    suspend fun fetchUserTrips(
        profile: CloudUserProfile
    ): List<Trip> {
        val snapshot = firestore.collection("trips")
            .whereArrayContains("memberIds", profile.userId)
            .get()
            .await()

        return snapshot.documents.mapNotNull { document ->
            document.getString("tripJson")?.let { raw ->
                runCatching {
                    json.decodeFromString(
                        Trip.serializer(),
                        raw
                    )
                }.getOrNull()
            } ?: runCatching {
                Trip(
                    id = document.id,
                    name = document.getString("name").orEmpty(),
                    destination = document
                        .getString("destination").orEmpty(),
                    destinationStops =
                        (document.get("destinationStops") as? List<*>)
                            ?.filterIsInstance<String>().orEmpty(),
                    startDate = document
                        .getString("startDate").orEmpty(),
                    endDate = document
                        .getString("endDate").orEmpty(),
                    ownerUserId = document
                        .getString("ownerUserId").orEmpty(),
                    cloudEnabled = true,
                    cloudSchemaVersion =
                        document.getLong("cloudSchemaVersion")
                            ?.toInt() ?: 9,
                    cloudRevision =
                        document.getLong("cloudRevision") ?: 0L,
                    updatedAt =
                        document.getLong("updatedAt") ?: 0L,
                    updatedBy =
                        document.getString("updatedBy").orEmpty()
                )
            }.getOrNull()
        }.sortedByDescending { it.updatedAt }
    }


    suspend fun createTripInvite(
        trip: Trip,
        profile: CloudUserProfile
    ): TripInvite {
        val ownerId = trip.ownerUserId.ifBlank {
            profile.userId
        }
        require(ownerId == profile.userId) {
            "רק בעל הטיול יכול ליצור הזמנה"
        }

        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = buildString {
            repeat(6) { append(chars.random()) }
        }
        val now = System.currentTimeMillis()
        val invite = TripInvite(
            code = code,
            tripId = trip.id,
            tripName = trip.name,
            destination = trip.destination,
            createdBy = profile.userId,
            createdByName = profile.displayName,
            createdAt = now,
            expiresAt = now + 604800000L
        )

        firestore.collection("tripInvites")
            .document(code)
            .set(invite)
            .await()

        return invite
    }

    suspend fun getTripInvite(
        code: String
    ): TripInvite {
        val normalized = code.trim().uppercase()
        val snapshot = firestore.collection("tripInvites")
            .document(normalized)
            .get()
            .await()

        if (!snapshot.exists()) {
            error("קוד ההזמנה אינו קיים")
        }

        val invite = snapshot.toObject(
            TripInvite::class.java
        ) ?: error("ההזמנה אינה תקינה")

        if (invite.expiresAt < System.currentTimeMillis()) {
            error("קוד ההזמנה פג תוקף")
        }
        if (invite.status != "pending") {
            error("ההזמנה אינה פעילה")
        }

        return invite
    }

    suspend fun acceptTripInvite(
        invite: TripInvite,
        profile: CloudUserProfile
    ): Trip {
        val tripRef = firestore.collection("trips")
            .document(invite.tripId)
        val inviteRef = firestore.collection("tripInvites")
            .document(invite.code)

        firestore.runTransaction { transaction ->
            val tripSnapshot = transaction.get(tripRef)
            if (!tripSnapshot.exists()) {
                error("הטיול אינו קיים")
            }

            val memberIds =
                (tripSnapshot.get("memberIds") as? List<*>)
                    ?.filterIsInstance<String>()
                    ?.toMutableSet()
                    ?: mutableSetOf()

            val editorIds =
                (tripSnapshot.get("editorIds") as? List<*>)
                    ?.filterIsInstance<String>()
                    ?.toMutableSet()
                    ?: mutableSetOf()

            memberIds += profile.userId
            if (invite.role == "editor") {
                editorIds += profile.userId
            }

            transaction.update(
                tripRef,
                mapOf(
                    "memberIds" to memberIds.toList(),
                    "editorIds" to editorIds.toList(),
                    "updatedAt" to System.currentTimeMillis(),
                    "updatedBy" to profile.userId
                )
            )
            transaction.update(
                inviteRef,
                mapOf(
                    "status" to "accepted",
                    "acceptedBy" to profile.userId,
                    "acceptedAt" to System.currentTimeMillis()
                )
            )
        }.await()

        return fetchUserTrips(profile)
            .firstOrNull { it.id == invite.tripId }
            ?: error("הטיול צורף אך לא נטען")
    }

    suspend fun declineTripInvite(
        invite: TripInvite,
        profile: CloudUserProfile
    ) {
        firestore.collection("tripInvites")
            .document(invite.code)
            .update(
                mapOf(
                    "status" to "declined",
                    "declinedBy" to profile.userId,
                    "declinedAt" to System.currentTimeMillis()
                )
            )
            .await()
    }

    suspend fun getTripMembers(
        tripId: String
    ): List<ManagedTripMember> {
        val tripSnapshot = firestore.collection("trips")
            .document(tripId)
            .get()
            .await()

        if (!tripSnapshot.exists()) {
            error("הטיול אינו קיים")
        }

        val ownerId =
            tripSnapshot.getString("ownerUserId")
                .orEmpty()

        val memberIds =
            (tripSnapshot.get("memberIds") as? List<*>)
                ?.filterIsInstance<String>()
                .orEmpty()

        val editorIds =
            (tripSnapshot.get("editorIds") as? List<*>)
                ?.filterIsInstance<String>()
                .toSet()

        val memberDocs = firestore.collection("trips")
            .document(tripId)
            .collection("members")
            .get()
            .await()
            .documents
            .associateBy { it.id }

        return memberIds.distinct().map { userId ->
            val memberDoc = memberDocs[userId]
            ManagedTripMember(
                userId = userId,
                displayName = memberDoc
                    ?.getString("displayName")
                    .orEmpty(),
                email = memberDoc
                    ?.getString("email")
                    .orEmpty(),
                role = when {
                    userId == ownerId -> "owner"
                    userId in editorIds -> "editor"
                    else -> "viewer"
                },
                joinedAt = memberDoc
                    ?.getLong("joinedAt")
                    ?: 0L,
                invitedBy = memberDoc
                    ?.getString("invitedBy")
                    .orEmpty()
            )
        }.sortedWith(
            compareBy<ManagedTripMember> {
                when (it.role) {
                    "owner" -> 0
                    "editor" -> 1
                    else -> 2
                }
            }.thenBy { it.displayName.ifBlank { it.email } }
        )
    }

    suspend fun getPendingTripInvites(
        tripId: String
    ): List<PendingTripInvite> {
        val snapshot = firestore.collection("tripInvites")
            .whereEqualTo("tripId", tripId)
            .whereEqualTo("status", "pending")
            .get()
            .await()

        return snapshot.documents.mapNotNull { document ->
            document.toObject(
                PendingTripInvite::class.java
            )
        }.filter {
            it.expiresAt >
                System.currentTimeMillis()
        }.sortedByDescending { it.createdAt }
    }

    suspend fun updateTripMemberRole(
        tripId: String,
        memberUserId: String,
        newRole: String,
        currentUser: CloudUserProfile
    ) {
        require(newRole in setOf("editor", "viewer")) {
            "הרשאה לא תקינה"
        }

        val tripRef = firestore.collection("trips")
            .document(tripId)
        val memberRef = tripRef.collection("members")
            .document(memberUserId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(tripRef)
            val ownerId = snapshot
                .getString("ownerUserId")
                .orEmpty()

            if (ownerId != currentUser.userId) {
                error("רק בעל הטיול יכול לשנות הרשאות")
            }
            if (memberUserId == ownerId) {
                error("לא ניתן לשנות את הרשאת בעל הטיול")
            }

            val editorIds =
                (snapshot.get("editorIds") as? List<*>)
                    ?.filterIsInstance<String>()
                    ?.toMutableSet()
                    ?: mutableSetOf()

            if (newRole == "editor") {
                editorIds += memberUserId
            } else {
                editorIds -= memberUserId
            }

            transaction.update(
                tripRef,
                mapOf(
                    "editorIds" to editorIds.toList(),
                    "updatedAt" to System.currentTimeMillis(),
                    "updatedBy" to currentUser.userId
                )
            )
            transaction.set(
                memberRef,
                mapOf(
                    "role" to newRole,
                    "updatedAt" to System.currentTimeMillis(),
                    "updatedBy" to currentUser.userId
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
        }.await()
    }

    suspend fun removeTripMember(
        tripId: String,
        memberUserId: String,
        currentUser: CloudUserProfile
    ) {
        val tripRef = firestore.collection("trips")
            .document(tripId)
        val memberRef = tripRef.collection("members")
            .document(memberUserId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(tripRef)
            val ownerId = snapshot
                .getString("ownerUserId")
                .orEmpty()

            if (ownerId != currentUser.userId) {
                error("רק בעל הטיול יכול להסיר חברים")
            }
            if (memberUserId == ownerId) {
                error("לא ניתן להסיר את בעל הטיול")
            }

            val memberIds =
                (snapshot.get("memberIds") as? List<*>)
                    ?.filterIsInstance<String>()
                    ?.toMutableSet()
                    ?: mutableSetOf()

            val editorIds =
                (snapshot.get("editorIds") as? List<*>)
                    ?.filterIsInstance<String>()
                    ?.toMutableSet()
                    ?: mutableSetOf()

            memberIds -= memberUserId
            editorIds -= memberUserId

            transaction.update(
                tripRef,
                mapOf(
                    "memberIds" to memberIds.toList(),
                    "editorIds" to editorIds.toList(),
                    "updatedAt" to System.currentTimeMillis(),
                    "updatedBy" to currentUser.userId
                )
            )
            transaction.delete(memberRef)
        }.await()
    }

    suspend fun cancelTripInvite(
        inviteCode: String,
        currentUser: CloudUserProfile
    ) {
        val inviteRef = firestore.collection("tripInvites")
            .document(inviteCode)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(inviteRef)
            if (!snapshot.exists()) {
                error("ההזמנה אינה קיימת")
            }

            val createdBy = snapshot
                .getString("createdBy")
                .orEmpty()

            if (createdBy != currentUser.userId) {
                error("רק יוצר ההזמנה יכול לבטל אותה")
            }

            transaction.update(
                inviteRef,
                mapOf(
                    "status" to "cancelled",
                    "cancelledAt" to
                        System.currentTimeMillis(),
                    "cancelledBy" to
                        currentUser.userId
                )
            )
        }.await()
    }


}
