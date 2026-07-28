package com.gal.familytrips

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BookingImportDialog(
    trip: Trip,
    onTripChange: (Trip) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var raw by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<BookingImportCandidate>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember {
        BookingDocumentScanner(context)
    }
    var scanBusy by remember {
        mutableStateOf(false)
    }
    var cameraUri by remember {
        mutableStateOf<Uri?>(null)
    }

    fun processScannedDocument(
        uri: Uri,
        mimeType: String
    ) {
        scanBusy = true
        message = "סורק את המסמך…"

        scope.launch {
            runCatching {
                scanner.scan(
                    uri,
                    mimeType
                )
            }.onSuccess { result ->
                raw = result.text
                candidates =
                    BookingImportEngine
                        .parse(result.text)

                message = when {
                    result.text.isBlank() ->
                        "לא זוהה טקסט במסמך."
                    candidates.isEmpty() ->
                        "הטקסט נסרק, אך לא זוהתה הזמנה. אפשר לערוך את הטקסט וללחוץ על זיהוי."
                    result.truncated ->
                        "נסרקו ${result.processedPages} העמודים הראשונים ונמצאו ${candidates.size} פריטים."
                    else ->
                        "הסריקה הסתיימה ונמצאו ${candidates.size} פריטים."
                }
            }.onFailure {
                message =
                    it.localizedMessage
                        ?: "סריקת המסמך נכשלה"
            }
            scanBusy = false
        }
    }

    val documentLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts
                .OpenDocument()
        ) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver
                        .takePersistableUriPermission(
                            uri,
                            android.content.Intent
                                .FLAG_GRANT_READ_URI_PERMISSION
                        )
                }

                processScannedDocument(
                    uri,
                    context.contentResolver
                        .getType(uri)
                        .orEmpty()
                )
            }
        }

    val cameraLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts
                .TakePicture()
        ) { success ->
            val uri = cameraUri
            if (success && uri != null) {
                processScannedDocument(
                    uri,
                    "image/jpeg"
                )
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("קליטת אישור הזמנה") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "הדביקו טקסט, בחרו PDF או תמונה, או צלמו אישור. FamilyGo תסרוק ותזהה טיסה, מלון, אטרקציה או העברה.",
                        color = TextSecondary
                    )
                }
                item {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            enabled = !scanBusy,
                            onClick = {
                                documentLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "image/*"
                                    )
                                )
                            },
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Text("PDF / תמונה")
                        }

                        OutlinedButton(
                            enabled = !scanBusy,
                            onClick = {
                                val directory = File(
                                    context.cacheDir,
                                    "booking-scans"
                                ).apply {
                                    mkdirs()
                                }
                                val file = File(
                                    directory,
                                    "scan_${System.currentTimeMillis()}.jpg"
                                )
                                val uri =
                                    FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                cameraUri = uri
                                cameraLauncher.launch(uri)
                            },
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Text("צילום")
                        }
                    }
                }

                if (scanBusy) {
                    item {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(
                                Modifier.width(10.dp)
                            )
                            Text(
                                "מזהה טקסט…",
                                color = TextSecondary
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = raw,
                        onValueChange = { raw = it },
                        label = { Text("תוכן אישור ההזמנה") },
                        minLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { raw = clipboard.getText()?.text.orEmpty() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentPaste, null)
                            Spacer(Modifier.width(6.dp))
                            Text("הדבקה")
                        }
                        Button(
                            enabled = raw.isNotBlank() && !scanBusy,
                            onClick = {
                                candidates = BookingImportEngine.parse(raw)
                                message = if (candidates.isEmpty()) "לא זוהתה הזמנה." else null
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("זיהוי") }
                    }
                }
                items(candidates, key = { "${it.type}:${it.title}:${it.startDate}" }) { candidate ->
                    Card(colors = CardDefaults.cardColors(containerColor = CardWhite)) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(candidate.title, fontWeight = FontWeight.Bold, color = Navy)
                            Text(
                                when (candidate.type) {
                                    "flight" -> "טיסה"
                                    "hotel" -> "מלון"
                                    "transfer" -> "העברה"
                                    else -> "אטרקציה"
                                },
                                color = TextSecondary
                            )
                            if (candidate.bookingReference.isNotBlank()) {
                                Text("מספר הזמנה: ${candidate.bookingReference}", color = TextSecondary)
                            }
                            if (candidate.startDate.isNotBlank()) {
                                Text("${candidate.startDate} ${candidate.startTime}", color = TextSecondary)
                            }
                            Text("רמת זיהוי: ${candidate.confidence}%", color = TextSecondary)
                            Button(
                                onClick = {
                                    onTripChange(BookingImportEngine.apply(trip, candidate))
                                    message = "הפריט נוסף לטיול"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("הוספה לטיול") }
                        }
                    }
                }
                message?.let { item { Text(it, color = TextSecondary) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגירה") } }
    )
}

@Composable
fun OfflinePackDialog(
    trip: Trip,
    onTripChange: (Trip) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember { OfflinePackManager(context) }
    var status by remember(trip.id) { mutableStateOf(manager.readStatus(trip.id)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("חבילת אופליין מלאה") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "החבילה שומרת במכשיר את נתוני הטיול, הטיסות, המלונות והמסמכים הזמינים.",
                    color = TextSecondary
                )
                status?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = if (it.ready) SoftMint else SoftSun)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(if (it.ready) "הטיול מוכן לאופליין" else "החבילה הוכנה חלקית", fontWeight = FontWeight.Bold, color = Navy)
                            Text("מסמכים זמינים: ${it.documentCount}", color = TextSecondary)
                            if (it.missingDocumentCount > 0) {
                                Text("מסמכים חסרים: ${it.missingDocumentCount}", color = Coral)
                            }
                        }
                    }
                }
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        runCatching { manager.prepare(trip) }
                            .onSuccess { result ->
                                onTripChange(result.first)
                                status = result.second
                                message = if (result.second.ready) "חבילת האופליין מוכנה" else "החבילה נשמרה חלקית"
                            }
                            .onFailure { message = it.localizedMessage ?: "הכנת החבילה נכשלה" }
                        busy = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) CircularProgressIndicator() else {
                        Icon(Icons.Default.CloudDownload, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (status == null) "הכנת הטיול לאופליין" else "רענון חבילת האופליין")
                    }
                }
                if (status != null) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            manager.clear(trip.id)
                            status = null
                            onTripChange(trip.copy(offlineMode = false))
                            message = "חבילת האופליין נמחקה"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("מחיקת חבילת אופליין")
                    }
                }
                message?.let { Text(it, color = TextSecondary) }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("סגירה") } }
    )
}
