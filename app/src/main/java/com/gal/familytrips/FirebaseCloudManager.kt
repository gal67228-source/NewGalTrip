package com.gal.familytrips

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

class FirebaseCloudManager(
    private val activity: Activity
) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
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
        val googleIdOption =
            GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(
                    activity.getString(
                        R.string.default_web_client_id
                    )
                )
                .setAutoSelectEnabled(false)
                .build()

        val request =
            GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

        val result = credentialManager.getCredential(
            context = activity,
            request = request
        )

        val credential = result.credential
        if (
            credential !is CustomCredential ||
            credential.type !=
                GoogleIdTokenCredential
                    .TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            error("לא התקבל חשבון Google תקין")
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
            ?: error("ההתחברות ל-Google נכשלה")
    }

    fun signOut() {
        auth.signOut()
    }

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
}
