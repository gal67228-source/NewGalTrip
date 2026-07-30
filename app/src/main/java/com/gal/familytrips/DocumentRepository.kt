package com.gal.familytrips

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class DocumentRepository(
    private val context: Context,
    private val driveManager: GoogleDriveManager
) {
    fun createCameraTarget(): Pair<Uri, String> {
        val directory = File(
            context.cacheDir,
            "camera"
        ).apply { mkdirs() }

        val file = File(
            directory,
            "familygo_${System.currentTimeMillis()}.jpg"
        )

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        ) to file.absolutePath
    }

    fun persistReadPermission(uri: Uri) {
        runCatching {
            context.contentResolver
                .takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
        }
    }

    fun mimeType(uri: Uri): String =
        context.contentResolver.getType(uri).orEmpty()

    fun copyForOffline(
        sourceUri: Uri,
        suggestedName: String
    ): String = runCatching {
        val directory = File(
            context.filesDir,
            "documents"
        ).apply { mkdirs() }

        val safeName = suggestedName
            .replace(
                Regex("[^a-zA-Z0-9._-]"),
                "_"
            )
            .ifBlank { "document" }

        val target = File(
            directory,
            "${System.currentTimeMillis()}_$safeName"
        )

        context.contentResolver
            .openInputStream(sourceUri)
            ?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to read document")

        target.absolutePath
    }.getOrDefault("")

    fun moveCameraCopy(
        cameraPath: String
    ): String = runCatching {
        val source = File(cameraPath)
        val directory = File(
            context.filesDir,
            "documents"
        ).apply { mkdirs() }
        val target = File(directory, source.name)
        source.copyTo(target, overwrite = true)
        target.absolutePath
    }.getOrDefault("")

    suspend fun uploadToDrive(
        documentId: String,
        localPath: String,
        name: String,
        mimeType: String
    ): String {
        require(localPath.isNotBlank())
        val token = driveManager.accessToken()
        return withContext(Dispatchers.IO) {
            val boundary =
                "familygo-${System.currentTimeMillis()}"
            val file = File(localPath)
            val metadata = JSONObject()
                .put("name", name)
                .put(
                    "parents",
                    org.json.JSONArray()
                        .put("appDataFolder")
                )
                .put(
                    "appProperties",
                    JSONObject().put(
                        "documentId",
                        documentId
                    )
                )
                .toString()
            val connection = openConnection(
                DRIVE_UPLOAD_URL,
                token,
                "POST"
            ).apply {
                setRequestProperty(
                    "Content-Type",
                    "multipart/related; boundary=$boundary"
                )
                doOutput = true
            }
            connection.outputStream.buffered().use { output ->
                output.write(
                    "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n"
                        .toByteArray()
                )
                val contentType = mimeType.ifBlank {
                    "application/octet-stream"
                }
                output.write(
                    "--$boundary\r\nContent-Type: $contentType\r\n\r\n"
                        .toByteArray()
                )
                file.inputStream().use {
                    it.copyTo(output)
                }
                output.write(
                    "\r\n--$boundary--\r\n".toByteArray()
                )
            }
            JSONObject(
                connection.requireSuccess()
            )
                .getString("id")
        }
    }

    suspend fun deleteDriveCopy(document: TripDocument) {
        if (document.googleDriveFileId.isNotBlank()) {
            val token = driveManager.accessToken()
            withContext(Dispatchers.IO) {
                val connection = openConnection(
                    driveFileUrl(document.googleDriveFileId),
                    token,
                    "DELETE"
                )
                connection.requireSuccess()
            }
        }
    }

    suspend fun ensureLocalCopy(document: TripDocument): TripDocument {
        if (
            document.localCopyPath.isNotBlank() &&
            File(document.localCopyPath).exists()
        ) return document
        if (document.googleDriveFileId.isBlank()) return document

        val token = driveManager.accessToken()
        return withContext(Dispatchers.IO) {
            val directory = File(context.filesDir, "documents")
                .apply { mkdirs() }
            val safeName = document.name
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .ifBlank { "document" }
            val target = File(directory, "${document.id}_$safeName")
            val connection = openConnection(
                "${driveFileUrl(document.googleDriveFileId)}?alt=media",
                token,
                "GET"
            )
            connection.requireSuccessStream().use { input ->
                target.outputStream().use(input::copyTo)
            }
            document.copy(
                localCopyPath = target.absolutePath,
                offlineAvailable = true
            )
        }
    }

    private fun openConnection(
        url: String,
        token: String,
        method: String
    ): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection)
        .apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 20_000
            readTimeout = 60_000
        }

    private fun HttpURLConnection.requireSuccess(): String {
        if (responseCode in 200..299) {
            return inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        val details = errorStream?.bufferedReader()?.use { it.readText() }
        error("Google Drive error $responseCode: ${details.orEmpty()}")
    }

    private fun HttpURLConnection.requireSuccessStream(): java.io.InputStream {
        if (responseCode in 200..299) return inputStream
        val details = errorStream?.bufferedReader()?.use { it.readText() }
        error("Google Drive error $responseCode: ${details.orEmpty()}")
    }

    private fun driveFileUrl(fileId: String): String =
        "https://www.googleapis.com/drive/v3/files/$fileId"

    companion object {
        private const val DRIVE_UPLOAD_URL =
            "https://www.googleapis.com/upload/drive/v3/files" +
                "?uploadType=multipart&fields=id"
    }

    fun open(document: TripDocument) {
        runCatching {
            val uri = if (
                document.localCopyPath.isNotBlank() &&
                File(document.localCopyPath).exists()
            ) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(document.localCopyPath)
                )
            } else {
                Uri.parse(document.uri)
            }

            val mime = document.mimeType.ifBlank {
                context.contentResolver
                    .getType(uri)
                    ?: "*/*"
            }

            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }
            )
        }
    }
}
