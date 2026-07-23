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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WarningAmber
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

private data class AutomaticDocumentRequirement(
    val key: String,
    val title: String,
    val description: String,
    val category: String,
    val linkedEntityType: String = "",
    val linkedEntityId: String = "",
    val bookingId: String = "",
    val suggestedName: String = title
)

private fun ActivityItem.requiresAttractionDocument(): Boolean {
    val searchable = "$name $notes".lowercase()

    val attractionKeywords = listOf(
        "כרטיס",
        "הזמנה",
        "אישור",
        "voucher",
        "ticket",
        "booking",
        "reservation",
        "אטרקציה",
        "מוזיאון",
        "סיור",
        "מופע",
        "הופעה",
        "תערוכה",
        "פארק שעשועים",
        "כניסה מוזמנת",
        "tour",
        "museum",
        "show",
        "attraction"
    )

    return attractionKeywords.any {
        searchable.contains(it)
    }
}

private fun ActivityItem.requiresTransportDocument(): Boolean {
    val searchable =
        "$transport $name $notes".lowercase()

    val excluded = listOf(
        "הליכה",
        "walk",
        "רכב",
        "נהיגה",
        "car",
        "מטרו",
        "metro",
        "אוטובוס מקומי",
        "local bus",
        "תחבורה ציבורית"
    )

    if (excluded.any { searchable.contains(it) }) {
        return false
    }

    val bookedTransportKeywords = listOf(
        "העברה",
        "הסעה",
        "שאטל",
        "מונית מוזמנת",
        "נהג פרטי",
        "רכבת",
        "מעבורת",
        "אוטובוס מוזמן",
        "transfer",
        "shuttle",
        "private driver",
        "booked taxi",
        "train",
        "ferry",
        "coach"
    )

    val bookingKeywords = listOf(
        "כרטיס",
        "הזמנה",
        "אישור",
        "voucher",
        "ticket",
        "booking",
        "reservation"
    )

    return bookedTransportKeywords.any {
        searchable.contains(it)
    } || (
        transport.isNotBlank() &&
            bookingKeywords.any {
                searchable.contains(it)
            }
    )
}

private fun automaticDocumentRequirements(
    trip: Trip
): List<AutomaticDocumentRequirement> {
    val requirements = mutableListOf(
        AutomaticDocumentRequirement(
            key = "general-passports",
            title = "דרכונים",
            description =
                "צרפו צילום דרכון לכל נוסע",
            category = "מסמכים אישיים",
            suggestedName = "דרכון"
        ),
        AutomaticDocumentRequirement(
            key = "general-travel-insurance",
            title = "ביטוח נסיעות",
            description =
                "פוליסה ופרטי מוקד החירום",
            category = "ביטוח",
            suggestedName = "ביטוח נסיעות"
        ),
        AutomaticDocumentRequirement(
            key = "general-important-documents",
            title = "מסמכים כלליים חשובים",
            description =
                "אשרות, אישורים או מסמכי חירום",
            category = "כללי",
            suggestedName = "מסמך כללי"
        )
    )

    trip.flights.forEach { flight ->
        requirements += AutomaticDocumentRequirement(
            key = "flight-${flight.id}",
            title = buildString {
                append("כרטיסים ואישור טיסה")
                if (flight.flightNumber.isNotBlank()) {
                    append(" · ${flight.flightNumber}")
                }
            },
            description =
                "${flight.departureAirport} → ${flight.arrivalAirport} · ${flight.departureDate}",
            category = "טיסות",
            linkedEntityType = "flight",
            linkedEntityId = flight.id,
            bookingId = flight.id,
            suggestedName = if (
                flight.flightNumber.isNotBlank()
            ) {
                "טיסה ${flight.flightNumber}"
            } else {
                "אישור טיסה ${flight.departureAirport}-${flight.arrivalAirport}"
            }
        )
    }

    trip.hotels.forEach { hotel ->
        requirements += AutomaticDocumentRequirement(
            key = "hotel-${hotel.id}",
            title = "אישור מלון · ${hotel.name}",
            description =
                "${hotel.checkIn} – ${hotel.checkOut}",
            category = "מלונות",
            linkedEntityType = "hotel",
            linkedEntityId = hotel.id,
            bookingId = hotel.id,
            suggestedName = "אישור מלון ${hotel.name}"
        )
    }

    trip.days.forEach { day ->
        day.activities.forEach { activity ->
            if (
                activity.name.isNotBlank() &&
                activity.requiresAttractionDocument()
            ) {
                requirements += AutomaticDocumentRequirement(
                    key = "attraction-${activity.id}",
                    title =
                        "כרטיס / אישור · ${activity.name}",
                    description = buildString {
                        append(day.date)
                        if (activity.time.isNotBlank()) {
                            append(" · ${activity.time}")
                        }
                        if (
                            activity.location.isNotBlank()
                        ) {
                            append(
                                " · ${activity.location}"
                            )
                        }
                    },
                    category = "אטרקציות",
                    linkedEntityType = "activity",
                    linkedEntityId = activity.id,
                    bookingId = activity.id,
                    suggestedName =
                        "כרטיס ${activity.name}"
                )
            }

            if (
                activity.requiresTransportDocument()
            ) {
                val transportTitle =
                    activity.transport
                        .ifBlank {
                            activity.name
                        }

                requirements += AutomaticDocumentRequirement(
                    key = "transfer-${activity.id}",
                    title =
                        "כרטיס / אישור העברה · $transportTitle",
                    description = buildString {
                        if (
                            activity.name.isNotBlank() &&
                            activity.name !=
                                transportTitle
                        ) {
                            append(
                                "לקראת ${activity.name}"
                            )
                        }
                        if (day.date.isNotBlank()) {
                            if (isNotEmpty()) {
                                append(" · ")
                            }
                            append(day.date)
                        }
                        if (
                            activity.time.isNotBlank()
                        ) {
                            append(
                                " · ${activity.time}"
                            )
                        }
                    },
                    category = "תחבורה",
                    linkedEntityType = "activity",
                    linkedEntityId = activity.id,
                    bookingId = activity.id,
                    suggestedName =
                        "אישור העברה $transportTitle"
                )
            }
        }
    }

    return requirements.distinctBy { it.key }
}

private fun requirementEmoji(
    category: String
): String = when (category) {
    "טיסות" -> "✈️"
    "מלונות" -> "🏨"
    "אטרקציות" -> "🎟️"
    "תחבורה" -> "🚆"
    "ביטוח" -> "🛡️"
    "מסמכים אישיים" -> "🛂"
    else -> "📄"
}

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

    val automaticRequirements =
        automaticDocumentRequirements(trip)

    val visibleRequirements =
        automaticRequirements.filter { requirement ->
            (
                state.category == "הכול" ||
                    requirement.category ==
                        state.category
            ) && (
                state.search.isBlank() ||
                    requirement.title.contains(
                        state.search,
                        ignoreCase = true
                    ) ||
                    requirement.description.contains(
                        state.search,
                        ignoreCase = true
                    )
            )
        }

    fun documentsFor(
        requirement: AutomaticDocumentRequirement
    ): List<TripDocument> =
        trip.documents.filter {
            it.requirementKey == requirement.key
        }

    val visibleDocuments = trip.documents
        .filter { it.requirementKey.isBlank() }
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
                                viewModel.clearRequirementPreset()
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

        if (visibleRequirements.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "מסמכים נדרשים",
                            style =
                                MaterialTheme.typography
                                    .titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "הרשימה מתעדכנת אוטומטית לפי הטיול",
                            style =
                                MaterialTheme.typography
                                    .bodySmall,
                            color = Color(0xFF64748B)
                        )
                    }

                    val ready = visibleRequirements.count {
                        documentsFor(it).isNotEmpty()
                    }
                    Text(
                        "$ready/${visibleRequirements.size}",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    )
                }
            }

            items(
                visibleRequirements,
                key = { "requirement-${it.key}" }
            ) { requirement ->
                val attached = documentsFor(requirement)
                val completed = attached.isNotEmpty()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (completed) {
                            Color(0xFFF0FDF4)
                        } else {
                            Color.White
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Text(
                                requirementEmoji(
                                    requirement.category
                                ),
                                style =
                                    MaterialTheme.typography
                                        .titleLarge
                            )
                            Spacer(Modifier.padding(5.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    requirement.title,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    requirement.description,
                                    style =
                                        MaterialTheme.typography
                                            .bodySmall,
                                    color = Color(0xFF64748B)
                                )
                            }

                            Icon(
                                if (completed) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Default.WarningAmber
                                },
                                contentDescription = null,
                                tint = if (completed) {
                                    Color(0xFF16A34A)
                                } else {
                                    Color(0xFFF59E0B)
                                }
                            )
                        }

                        attached.forEach { document ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB)
                                    )
                                    Spacer(Modifier.padding(4.dp))
                                    Text(
                                        document.name,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow =
                                            TextOverflow.Ellipsis
                                    )
                                    TextButton(
                                        onClick = {
                                            viewModel.open(document)
                                        }
                                    ) {
                                        Text("פתיחה")
                                    }
                                    IconButton(
                                        onClick = {
                                            onTripChange(
                                                trip.copy(
                                                    documents =
                                                        trip.documents
                                                            .filterNot {
                                                                it.id ==
                                                                    document.id
                                                            }
                                                )
                                            )
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription =
                                                "מחיקה",
                                            tint = Color(0xFFDC2626)
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.prepareRequirement(
                                    requirementKey =
                                        requirement.key,
                                    suggestedName =
                                        requirement.suggestedName,
                                    category =
                                        requirement.category,
                                    linkedEntityType =
                                        requirement.linkedEntityType,
                                    linkedEntityId =
                                        requirement.linkedEntityId,
                                    bookingId =
                                        requirement.bookingId
                                )
                                fileLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "image/*"
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.AddCircle,
                                contentDescription = null
                            )
                            Spacer(Modifier.padding(3.dp))
                            Text(
                                if (completed) {
                                    "הוספת קובץ נוסף"
                                } else {
                                    "צירוף קובץ"
                                }
                            )
                        }
                    }
                }
            }
        }

        if (
            visibleDocuments.isEmpty() &&
            visibleRequirements.isEmpty()
        ) {
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
            initialCategory = state.pendingCategory,
            initialLinkedEntityType =
                state.pendingLinkedEntityType,
            initialLinkedEntityId =
                state.pendingLinkedEntityId,
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
    initialCategory: String,
    initialLinkedEntityType: String,
    initialLinkedEntityId: String,
    trip: Trip,
    onDismiss: () -> Unit,
    onSave: (DocumentMetadataInput) -> Unit
) {
    var name by remember {
        mutableStateOf(initialName)
    }
    var category by remember(initialCategory) {
        mutableStateOf(
            initialCategory.ifBlank { "כללי" }
        )
    }
    var bookingReference by remember {
        mutableStateOf("")
    }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var linkedType by remember(
        initialLinkedEntityType
    ) {
        mutableStateOf(initialLinkedEntityType)
    }
    var linkedId by remember(
        initialLinkedEntityId
    ) {
        mutableStateOf(initialLinkedEntityId)
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
