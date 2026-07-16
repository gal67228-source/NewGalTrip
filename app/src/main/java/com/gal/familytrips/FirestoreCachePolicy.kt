package com.gal.familytrips

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

object FirestoreCachePolicy {
    fun configure(
        firestore: FirebaseFirestore
    ) {
        runCatching {
            firestore.firestoreSettings =
                FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(
                        FirebaseFirestoreSettings
                            .CACHE_SIZE_UNLIMITED
                    )
                    .build()
        }
    }
}
