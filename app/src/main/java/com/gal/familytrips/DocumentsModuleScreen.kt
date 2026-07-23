package com.gal.familytrips

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DocumentsModuleScreen(
    trip: Trip,
    onTripChange: (Trip) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: DocumentsViewModel = viewModel(
        factory = DocumentsViewModel.Factory(context)
    )
    val state by viewModel.state.collectAsState()

    val fileLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let(viewModel::acceptFile)
        }

    val cameraLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            if (success) {
                viewModel.acceptCamera()
            }
        }

    val categories = listOf(
        "הכול",
        "טיסות",
        "מלונות",
        "מסעדות",
        "אטרקציות",
        "תחבורה",
        "ביטוח",
        "מסמכים אישיים",
        "כללי"
    )

    val visibleDocuments = trip.documents
        .filter { document ->
            state.category == "הכול" ||
                document.type == state.category
        }
        .filter { document ->
            state.search.isBlank() ||
                document.name.contains(
                    state.search,
                    ignoreCase = true
                ) ||
                document.bookingReference.contains(
                    state.search,
                    ignoreCase = true
                ) ||
                document.notes.contains(
                    state.search,
                    ignoreCase = true
                )
        }
        .sortedByDescending { it.addedAt }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = 14.dp,
            bottom = 28.dp
        ),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "מסמכים והזמנות",
                style =
                    MaterialTheme.typography
                        .headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "כל האישורים החשובים זמינים גם אופליין",
                color = Color(0xFF64748B)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                fileLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "image/*"
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.UploadFile,
                                contentDescription = null
                            )
                            Spacer(Modifier.padding(3.dp))
                            Text("בחירת קובץ")
                        }

                        OutlinedButton(
                            onClick = {
                                val target =
                                    viewModel.prepareCamera()
                                cameraLauncher.launch(
                                    target.first
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = null
                            )
                            Spacer(Modifier.padding(3.dp))
                            Text("צילום")
                        }
                    }

                    OutlinedTextField(
                        value = state.search,
                        onValueChange =
                            viewModel::setSearch,
                        label = {
                            Text("חיפוש במסמכים")
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            LazyRow(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected =
                            state.category == category,
                        onClick = {
                            viewModel.setCategory(category)
                        },
                        label = { Text(category) }
                    )
                }
            }
        }

        if (visibleDocuments.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF8FAFC)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment =
                            Alignment.CenterHorizontally,
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFF64748B)
                        )
                        Text(
                            "עדיין אין מסמכים",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "העלה PDF, תמונה או צלם מסמך",
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }

        items(
            visibleDocuments,
            key = { it.id }
        ) { document ->
            DocumentCard(
                document = document,
                onOpen = {
                    viewModel.open(document)
                },
                onDelete = {
                    onTripChange(
                        trip.copy(
                            documents = trip.documents
                                .filterNot {
                                    it.id == document.id
                                }
                        )
                    )
                }
            )
        }
    }

    if (state.showMetadata) {
        DocumentMetadataDialog(
            initialName = state.pendingName,
            trip = trip,
            onDismiss = viewModel::dismissMetadata,
            onSave = { input ->
                val document =
                    viewModel.createDocument(input)
                        ?: return@DocumentMetadataDialog
                onTripChange(
                    trip.copy(
                        documents =
                            trip.documents + document
                    )
                )
            }
        )
    }
}

@Composable
private fun DocumentCard(
    document: TripDocument,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = Color(0xFF2563EB)
                )
                Spacer(Modifier.padding(5.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        document.name,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            append(document.type)
                            if (document.offlineAvailable) {
                                append(" · זמין אופליין")
                            }
                        },
                        style =
                            MaterialTheme.typography
                                .bodySmall,
                        color = Color(0xFF64748B)
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "מחיקה",
                        tint = Color(0xFFDC2626)
                    )
                }
            }

            val details = buildString {
                if (document.bookingReference.isNotBlank()) {
                    append(
                        "הזמנה: ${document.bookingReference}"
                    )
                }
                if (document.documentDate.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(document.documentDate)
                    if (document.documentTime.isNotBlank()) {
                        append(" ${document.documentTime}")
                    }
                }
                if (document.linkedEntityType.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append("מקושר לפריט בטיול")
                }
            }

            if (details.isNotBlank()) {
                Text(
                    details,
                    style =
                        MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )
            }

            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("פתיחת המסמך")
            }
        }
    }
}

@Composable
private fun DocumentMetadataDialog(
    initialName: String,
    trip: Trip,
    onDismiss: () -> Unit,
    onSave: (DocumentMetadataInput) -> Unit
) {
    var name by remember {
        mutableStateOf(initialName)
    }
    var category by remember {
        mutableStateOf("כללי")
    }
    var bookingReference by remember {
        mutableStateOf("")
    }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var linkedType by remember {
        mutableStateOf("")
    }
    var linkedId by remember {
        mutableStateOf("")
    }

    val categories = listOf(
        "טיסות",
        "מלונות",
        "מסעדות",
        "אטרקציות",
        "תחבורה",
        "ביטוח",
        "מסמכים אישיים",
        "כללי"
    )

    val links = buildList {
        add(Triple("", "", "ללא קישור"))
        trip.flights.forEach {
            add(
                Triple(
                    "flight",
                    it.id,
                    "טיסה ${it.departureAirport} → ${it.arrivalAirport}"
                )
            )
        }
        trip.hotels.forEach {
            add(Triple("hotel", it.id, it.name))
        }
        trip.days.forEach { day ->
            day.activities.forEach {
                add(
                    Triple(
                        "activity",
                        it.id,
                        it.name
                    )
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("פרטי המסמך") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("שם המסמך") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Text(
                        "קטגוריה",
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(
                        horizontalArrangement =
                            Arrangement.spacedBy(7.dp)
                    ) {
                        items(categories) { value ->
                            FilterChip(
                                selected = category == value,
                                onClick = {
                                    category = value
                                },
                                label = { Text(value) }
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = bookingReference,
                        onValueChange = {
                            bookingReference = it
                        },
                        label = { Text("מספר הזמנה") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = date,
                            onValueChange = { date = it },
                            label = { Text("תאריך") },
                            placeholder = {
                                Text("YYYY-MM-DD")
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = time,
                            onValueChange = { time = it },
                            label = { Text("שעה") },
                            placeholder = {
                                Text("HH:mm")
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Text(
                        "קישור לפריט בטיול",
                        fontWeight = FontWeight.Bold
                    )
                    links.forEach { link ->
                        FilterChip(
                            selected =
                                linkedType == link.first &&
                                    linkedId == link.second,
                            onClick = {
                                linkedType = link.first
                                linkedId = link.second
                            },
                            label = { Text(link.third) },
                            modifier =
                                Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("הערות") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        DocumentMetadataInput(
                            name = name.trim(),
                            category = category,
                            bookingReference =
                                bookingReference.trim(),
                            date = date.trim(),
                            time = time.trim(),
                            linkedEntityType =
                                linkedType,
                            linkedEntityId = linkedId,
                            notes = notes.trim()
                        )
                    )
                }
            ) {
                Text("שמירה")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}
