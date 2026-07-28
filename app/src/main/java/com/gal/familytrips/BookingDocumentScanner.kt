package com.gal.familytrips

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class BookingScanResult(
    val text: String,
    val processedPages: Int,
    val truncated: Boolean
)

class BookingDocumentScanner(
    private val context: Context
) {
    suspend fun scan(
        uri: Uri,
        mimeType: String
    ): BookingScanResult {
        return if (
            mimeType.equals(
                "application/pdf",
                ignoreCase = true
            )
        ) {
            scanPdf(uri)
        } else {
            scanImage(uri)
        }
    }

    private suspend fun scanImage(
        uri: Uri
    ): BookingScanResult {
        val bitmap = withContext(
            Dispatchers.IO
        ) {
            loadBitmap(uri)
        }

        return BookingScanResult(
            text = recognize(bitmap),
            processedPages = 1,
            truncated = false
        )
    }

    private suspend fun scanPdf(
        uri: Uri
    ): BookingScanResult = withContext(
        Dispatchers.IO
    ) {
        val descriptor = context.contentResolver
            .openFileDescriptor(uri, "r")
            ?: error("לא ניתן לפתוח את קובץ ה-PDF")

        descriptor.use {
            PdfRenderer(it).use { renderer ->
                val pageLimit = minOf(
                    renderer.pageCount,
                    MAX_PDF_PAGES
                )
                val recognizedPages =
                    mutableListOf<String>()

                repeat(pageLimit) { index ->
                    renderer.openPage(index).use {
                        page ->
                        val scale = (
                            TARGET_PAGE_WIDTH
                                .toFloat() /
                                page.width
                                    .coerceAtLeast(1)
                        ).coerceAtLeast(1f)

                        val bitmap = Bitmap.createBitmap(
                            (page.width * scale)
                                .toInt()
                                .coerceAtLeast(1),
                            (page.height * scale)
                                .toInt()
                                .coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )

                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page
                                .RENDER_MODE_FOR_DISPLAY
                        )

                        recognizedPages +=
                            recognize(bitmap)
                        bitmap.recycle()
                    }
                }

                BookingScanResult(
                    text = recognizedPages
                        .filter {
                            it.isNotBlank()
                        }
                        .joinToString("\n\n"),
                    processedPages = pageLimit,
                    truncated =
                        renderer.pageCount >
                            pageLimit
                )
            }
        }
    }

    private suspend fun recognize(
        bitmap: Bitmap
    ): String {
        val recognizer = TextRecognition
            .getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
            )

        return try {
            recognizer.process(
                InputImage.fromBitmap(
                    bitmap,
                    0
                )
            ).await().text.trim()
        } finally {
            recognizer.close()
        }
    }

    @Suppress("DEPRECATION")
    private fun loadBitmap(
        uri: Uri
    ): Bitmap {
        return if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.P
        ) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(
                    context.contentResolver,
                    uri
                )
            ) { decoder, _, _ ->
                decoder.allocator =
                    ImageDecoder
                        .ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            MediaStore.Images.Media
                .getBitmap(
                    context.contentResolver,
                    uri
                )
        }
    }

    companion object {
        private const val MAX_PDF_PAGES = 8
        private const val TARGET_PAGE_WIDTH = 1800
    }
}
