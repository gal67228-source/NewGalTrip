package com.gal.familytrips

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class DocumentsUiState(
    val search: String = "",
    val category: String = "הכול",
    val pendingUri: Uri? = null,
    val pendingName: String = "",
    val pendingMime: String = "",
    val pendingSource: String = "file",
    val cameraPath: String = "",
    val showMetadata: Boolean = false,
    val pendingRequirementKey: String = "",
    val pendingCategory: String = "",
    val pendingLinkedEntityType: String = "",
    val pendingLinkedEntityId: String = "",
    val pendingBookingId: String = "",
    val pendingSuggestedName: String = "",
    val cloudOperationInProgress: Boolean = false,
    val cloudError: String = ""
)

data class DocumentMetadataInput(
    val name: String,
    val category: String,
    val bookingReference: String,
    val date: String,
    val time: String,
    val linkedEntityType: String,
    val linkedEntityId: String,
    val notes: String,
    val passengerName: String = ""
)

class DocumentsViewModel(
    private val repository: DocumentRepository
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(DocumentsUiState())
    val state: StateFlow<DocumentsUiState> =
        mutableState.asStateFlow()

    fun setSearch(value: String) {
        mutableState.value =
            mutableState.value.copy(search = value)
    }

    fun setCategory(value: String) {
        mutableState.value =
            mutableState.value.copy(category = value)
    }

    fun clearRequirementPreset() {
        mutableState.value = mutableState.value.copy(
            pendingRequirementKey = "",
            pendingSuggestedName = "",
            pendingCategory = "",
            pendingLinkedEntityType = "",
            pendingLinkedEntityId = "",
            pendingBookingId = ""
        )
    }

    fun prepareRequirement(
        requirementKey: String,
        suggestedName: String,
        category: String,
        linkedEntityType: String,
        linkedEntityId: String,
        bookingId: String
    ) {
        mutableState.value = mutableState.value.copy(
            pendingRequirementKey = requirementKey,
            pendingSuggestedName = suggestedName,
            pendingCategory = category,
            pendingLinkedEntityType =
                linkedEntityType,
            pendingLinkedEntityId = linkedEntityId,
            pendingBookingId = bookingId
        )
    }

    fun acceptFile(uri: Uri) {
        repository.persistReadPermission(uri)
        mutableState.value = mutableState.value.copy(
            pendingUri = uri,
            pendingName = mutableState.value
                .pendingSuggestedName
                .ifBlank {
                    uri.lastPathSegment
                        ?.substringAfterLast("/")
                        ?: "מסמך"
                },
            pendingMime = repository.mimeType(uri),
            pendingSource = "file",
            showMetadata = true
        )
    }

    fun prepareCamera(): Pair<Uri, String> {
        val target = repository.createCameraTarget()
        mutableState.value = mutableState.value.copy(
            pendingUri = target.first,
            cameraPath = target.second,
            pendingMime = "image/jpeg",
            pendingSource = "camera"
        )
        return target
    }

    fun acceptCamera() {
        mutableState.value = mutableState.value.copy(
            pendingName =
                "צילום_${System.currentTimeMillis()}.jpg",
            showMetadata = true
        )
    }

    fun dismissMetadata() {
        mutableState.value = DocumentsUiState(
            search = mutableState.value.search,
            category = mutableState.value.category
        )
    }

    suspend fun createDocument(
        input: DocumentMetadataInput,
        saveToDrive: Boolean
    ): TripDocument? {
        val current = mutableState.value
        val uri = current.pendingUri ?: return null

        val localPath = if (
            current.pendingSource == "camera"
        ) {
            repository.moveCameraCopy(
                current.cameraPath
            )
        } else {
            repository.copyForOffline(
                uri,
                input.name
            )
        }

        val documentId = UUID.randomUUID().toString()
        var result = TripDocument(
            id = documentId,
            name = input.name,
            uri = uri.toString(),
            type = input.category,
            notes = input.notes,
            offlineAvailable = localPath.isNotBlank(),
            addedAt = System.currentTimeMillis(),
            bookingReference =
                input.bookingReference,
            documentDate = input.date,
            documentTime = input.time,
            linkedEntityType =
                input.linkedEntityType,
            linkedEntityId = input.linkedEntityId,
            mimeType = current.pendingMime,
            sourceType = current.pendingSource,
            localCopyPath = localPath,
            requirementKey =
                current.pendingRequirementKey,
            bookingId = current.pendingBookingId,
            passengerName = input.passengerName
        )

        if (saveToDrive) {
            mutableState.value = mutableState.value.copy(
                cloudOperationInProgress = true,
                cloudError = ""
            )
            val driveFileId = runCatching {
                repository.uploadToDrive(
                    documentId = documentId,
                    localPath = localPath,
                    name = input.name,
                    mimeType = current.pendingMime
                )
            }.getOrElse { error ->
                mutableState.value = mutableState.value.copy(
                    cloudOperationInProgress = false,
                    cloudError = error.localizedMessage
                        ?: "שמירת המסמך בענן נכשלה"
                )
                return null
            }
            result = result.copy(
                googleDriveFileId = driveFileId
            )
        }

        dismissMetadata()
        return result
    }

    suspend fun open(document: TripDocument) {
        mutableState.value = mutableState.value.copy(
            cloudOperationInProgress = true,
            cloudError = ""
        )
        runCatching {
            repository.ensureLocalCopy(document)
        }.onSuccess {
            repository.open(it)
            mutableState.value = mutableState.value.copy(
                cloudOperationInProgress = false
            )
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(
                cloudOperationInProgress = false,
                cloudError = error.localizedMessage
                    ?: "הורדת המסמך מהענן נכשלה"
            )
        }
    }

    suspend fun delete(document: TripDocument) {
        runCatching { repository.deleteDriveCopy(document) }
    }

    class Factory(
        context: Context,
        driveManager: GoogleDriveManager
    ) : ViewModelProvider.Factory {
        private val repository =
            DocumentRepository(
                context.applicationContext,
                driveManager
            )

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>
        ): T = DocumentsViewModel(repository) as T
    }
}
