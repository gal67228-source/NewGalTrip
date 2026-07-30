package com.gal.familytrips

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Obtains a short-lived Drive token after explicit Google consent. */
class GoogleDriveManager(
    activity: ComponentActivity
) {
    private val client = Identity.getAuthorizationClient(activity)
    private var pending: Continuation<String>? = null
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val continuation = pending ?: return@registerForActivityResult
        pending = null
        if (result.resultCode != Activity.RESULT_OK) {
            continuation.resumeWithException(
                IllegalStateException("לא ניתן אישור לשמירה ב-Google Drive")
            )
            return@registerForActivityResult
        }
        runCatching {
            val data = result.data
                ?: error("Google Drive לא החזיר תוצאת הרשאה")
            client.getAuthorizationResultFromIntent(data)
                .accessToken
                ?: error("Google Drive לא החזיר הרשאת גישה")
        }.onSuccess(continuation::resume)
            .onFailure(continuation::resumeWithException)
    }

    suspend fun accessToken(): String {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(Scope(DRIVE_APPDATA_SCOPE))
            )
            .build()
        val result = client.authorize(request).await()
        result.accessToken?.let { return it }
        val resolution = result.pendingIntent
            ?: error("לא ניתן לפתוח את הרשאת Google Drive")
        return suspendCancellableCoroutine { continuation ->
            check(pending == null) { "בקשת הרשאה כבר מתבצעת" }
            pending = continuation
            continuation.invokeOnCancellation { pending = null }
            launcher.launch(
                IntentSenderRequest.Builder(resolution).build()
            )
        }
    }

    companion object {
        private const val DRIVE_APPDATA_SCOPE =
            "https://www.googleapis.com/auth/drive.appdata"
    }
}
