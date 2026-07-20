package com.gal.familytrips

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

class DocumentRepository(
    private val context: Context
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
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            )
        }
    }
}
