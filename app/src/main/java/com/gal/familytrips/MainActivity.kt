
package com.gal.familytrips

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var store: TripStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TripStore(this)

        setContent {
            GalTripsTheme {
                var state by remember { mutableStateOf<AppState?>(null) }

                LaunchedEffect(Unit) {
                    state = store.load()
                }

                state?.let { loaded ->
                    GalTripsApp(
                        state = loaded,
                        onStateChange = {
                            state = it
                            lifecycleScope.launch { store.save(it) }
                        },
                        onOpenUrl = ::openUrl,
                        onShareTrip = { shareText(store.exportTrip(it)) },
                        onImportTrip = { raw ->
                            runCatching { store.importTrip(raw) }.onSuccess { trip ->
                                val imported = trip.copy(id = UUID.randomUUID().toString(), name = trip.name + " (מיובא)")
                                val next = loaded.copy(
                                    trips = loaded.trips + imported,
                                    currentTripId = imported.id
                                )
                                state = next
                                lifecycleScope.launch { store.save(next) }
                            }
                        }
                    )
                } ?: Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun shareText(text: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "שיתוף טיול"))
    }
}

@Composable
fun GalTripsApp(
    state: AppState,
    onStateChange: (AppState) -> Unit,
    onOpenUrl: (String) -> Unit,
    onShareTrip: (Trip) -> Unit,
    onImportTrip: (String) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var selectedDayId by remember { mutableStateOf<String?>(null) }
    var showAddTrip by remember { mutableStateOf(false) }
    val trip = state.trips.firstOrNull { it.id == state.currentTripId } ?: state.trips.first()

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = CardWhite, tonalElevation = 10.dp) {
                listOf(
                    Triple(Icons.Default.Home, "טיולים", 0),
                    Triple(Icons.Default.Today, "ימים", 1),
                    Triple(Icons.Default.Hotel, "מלונות", 2),
                    Triple(Icons.Default.Restaurant, "מסעדות", 3),
                    Triple(Icons.Default.AttachMoney, "תקציב", 4),
                    Triple(Icons.Default.Description, "מסמכים", 5)
                ).forEach { (icon,label,index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, null) },
                        label = { Text(label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) FloatingActionButton(onClick = { showAddTrip = true }) {
                Icon(Icons.Default.Add, null)
            }
        }
    ) { padding ->
        when (tab) {
            0 -> TripsScreen(state, onStateChange, onShareTrip, onImportTrip, Modifier.padding(padding))
            1 -> if (selectedDayId == null)
                DaysScreen(trip, onStateChange = { updated -> onStateChange(state.replaceTrip(updated)) },
                    onSelectDay = { selectedDayId = it }, modifier = Modifier.padding(padding))
            else
                DayDetailScreen(
                    trip = trip,
                    dayId = selectedDayId!!,
                    onBack = { selectedDayId = null },
                    onTripChange = { onStateChange(state.replaceTrip(it)) },
                    onOpenUrl = onOpenUrl,
                    modifier = Modifier.padding(padding)
                )
            2 -> HotelsScreen(trip, { onStateChange(state.replaceTrip(it)) }, onOpenUrl, Modifier.padding(padding))
            3 -> RestaurantsScreen(trip, { onStateChange(state.replaceTrip(it)) }, onOpenUrl, Modifier.padding(padding))
            4 -> ExpensesScreen(trip, { onStateChange(state.replaceTrip(it)) }, Modifier.padding(padding))
            5 -> DocumentsScreen(trip, { onStateChange(state.replaceTrip(it)) }, Modifier.padding(padding))
        }
    }

    if (showAddTrip) {
        SimpleTextDialog(
            title = "טיול חדש",
            fields = listOf("שם הטיול","יעד","תאריך התחלה","תאריך סיום"),
            onDismiss = { showAddTrip = false },
            onConfirm = { values ->
                val newTrip = Trip(
                    UUID.randomUUID().toString(),
                    values[0], values[1], values[2], values[3]
                )
                onStateChange(state.copy(trips = state.trips + newTrip, currentTripId = newTrip.id))
                showAddTrip = false
            }
        )
    }
}

private fun AppState.replaceTrip(updated: Trip): AppState =
    copy(trips = trips.map { if (it.id == updated.id) updated else it })

@Composable
private fun TripsScreen(
    state: AppState,
    onStateChange: (AppState) -> Unit,
    onShareTrip: (Trip) -> Unit,
    onImportTrip: (String) -> Unit,
    modifier: Modifier
) {
    var importText by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientHeader(
                title = "הטיולים שלי",
                subtitle = "כל החופשות במקום אחד",
                emoji = "🌍",
                start = Lavender,
                end = Navy
            )
        }

        items(state.trips, key = { it.id }) { trip ->
            SectionCard(containerColor = SoftLavender) {
                Text(
                    text = trip.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = trip.destination,
                    color = TextSecondary
                )
                Text(
                    text = "${trip.startDate}–${trip.endDate}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AccentButton(
                        text = if (state.currentTripId == trip.id) "נבחר" else "בחירה",
                        emoji = if (state.currentTripId == trip.id) "✓" else "✈️",
                        onClick = { onStateChange(state.copy(currentTripId = trip.id)) },
                        color = if (state.currentTripId == trip.id) Mint else Sky,
                        modifier = Modifier.weight(1f)
                    )

                    SoftActionButton(
                        text = "שיתוף",
                        emoji = "📤",
                        onClick = { onShareTrip(trip) },
                        container = SoftBlue,
                        contentColor = Sky,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (state.trips.size > 1) {
                    TextButton(
                        onClick = {
                            val remaining = state.trips.filterNot { it.id == trip.id }
                            onStateChange(
                                state.copy(
                                    trips = remaining,
                                    currentTripId = remaining.first().id
                                )
                            )
                        }
                    ) {
                        Text("🗑️ מחיקת הטיול", color = Coral)
                    }
                }
            }
        }

        item {
            SoftActionButton(
                text = "ייבוא טיול מטקסט JSON",
                emoji = "📥",
                onClick = { importText = "" },
                container = SoftAqua,
                contentColor = Aqua,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Spacer(Modifier.height(20.dp))
        }
    }

    if (importText != null) {
        TextAreaDialog(
            title = "ייבוא טיול",
            initial = importText!!,
            onDismiss = { importText = null },
            onConfirm = {
                onImportTrip(it)
                importText = null
            }
        )
    }
}

@Composable
private fun DaysScreen(
    trip: Trip,
    onStateChange: (Trip) -> Unit,
    onSelectDay: (String) -> Unit,
    modifier: Modifier
) {
    var addDay by remember { mutableStateOf(false) }
    var editingDay by remember { mutableStateOf<TripDay?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        GradientHeader(
            title = "ימי הטיול",
            subtitle = "כל יום עם המסלול המלא",
            emoji = "📅",
            start = Sky,
            end = Navy
        )

        AccentButton(
            text = "הוספת יום",
            emoji = "＋",
            onClick = { addDay = true },
            color = Sky,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(trip.days.sortedBy { it.date }, key = { it.id }) { day ->
                Card(
                    onClick = { onSelectDay(day.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(184.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = BorderStroke(1.dp, Color(0xFFE4EAF1)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        DayThumbnail(
                            imageKey = day.imageKey,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = day.date.substringAfterLast("-") + "." +
                                    day.date.split("-")[1],
                                color = Sky,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = day.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${day.activities.size} פעילויות",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = { editingDay = day },
                                modifier = Modifier.size(32.dp)
                            ) {
                                SmallEditIcon(Modifier.size(28.dp))
                            }
                            IconButton(
                                onClick = {
                                    onStateChange(
                                        trip.copy(
                                            days = trip.days.filterNot { it.id == day.id },
                                            restaurants = trip.restaurants.filterNot { it.dayId == day.id }
                                        )
                                    )
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                SmallDeleteIcon(Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (addDay) {
        SimpleTextDialog(
            title = "יום חדש",
            fields = listOf("תאריך YYYY-MM-DD", "כותרת"),
            onDismiss = { addDay = false },
            onConfirm = { values ->
                onStateChange(
                    trip.copy(
                        days = trip.days + TripDay(
                            id = UUID.randomUUID().toString(),
                            date = values[0],
                            title = values[1],
                            imageKey = "city"
                        )
                    )
                )
                addDay = false
            }
        )
    }

    editingDay?.let { day ->
        EditDayDialog(
            day = day,
            onDismiss = { editingDay = null },
            onConfirm = { updated ->
                onStateChange(
                    trip.copy(days = trip.days.map { if (it.id == updated.id) updated else it })
                )
                editingDay = null
            }
        )
    }
}

@Composable
private fun EditDayDialog(
    day: TripDay,
    onDismiss: () -> Unit,
    onConfirm: (TripDay) -> Unit
) {
    var date by remember(day.id) { mutableStateOf(day.date) }
    var title by remember(day.id) { mutableStateOf(day.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("עריכת יום") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("תאריך") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("כותרת היום") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(day.copy(date = date, title = title)) }
            ) {
                Text("שמירה")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ביטול") }
        }
    )
}

@Composable
private fun DayDetailScreen(
    trip: Trip,
    dayId: String,
    onBack: () -> Unit,
    onTripChange: (Trip) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier
) {
    val day = trip.days.first { it.id == dayId }
    var addActivity by remember { mutableStateOf(false) }
    var editingActivity by remember { mutableStateOf<ActivityItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה")
            }
            DayThumbnail(day.imageKey, Modifier.size(54.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(day.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(day.date, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { addActivity = true }) {
                Icon(Icons.Default.AddCircle, "הוספת פעילות", tint = Sky)
            }
        }

        Spacer(Modifier.height(9.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SoftActionButton(
                text = "מפה יומית",
                emoji = "🗺️",
                onClick = {
                    val points = day.activities.mapNotNull { it.location.ifBlank { null } }
                    if (points.isNotEmpty()) {
                        val origin = points.first()
                        val destination = points.last()
                        val waypoints = points.drop(1).dropLast(1).take(8).joinToString("|")
                        var url = "https://www.google.com/maps/dir/?api=1" +
                            "&origin=${Uri.encode(origin)}" +
                            "&destination=${Uri.encode(destination)}" +
                            "&travelmode=transit"
                        if (waypoints.isNotBlank()) url += "&waypoints=${Uri.encode(waypoints)}"
                        onOpenUrl(url)
                    }
                },
                container = SoftBlue,
                contentColor = Sky,
                modifier = Modifier.weight(1f)
            )
            SoftActionButton(
                text = "הפעילות הבאה",
                emoji = "📍",
                onClick = {
                    day.activities.firstOrNull { !it.completed }?.let {
                        onOpenUrl(it.mapsUrl)
                    }
                },
                container = SoftAqua,
                contentColor = Aqua,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(day.activities.sortedBy { it.time }, key = { it.id }) { activity ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (activity.completed) SoftMint else CardWhite
                    ),
                    border = BorderStroke(1.dp, Color(0xFFE3E9F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(activity.time, color = Sky, fontWeight = FontWeight.Bold)
                                Text(
                                    activity.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Checkbox(
                                checked = activity.completed,
                                onCheckedChange = { checked ->
                                    val updatedDay = day.copy(
                                        activities = day.activities.map {
                                            if (it.id == activity.id) it.copy(completed = checked) else it
                                        }
                                    )
                                    onTripChange(
                                        trip.copy(
                                            days = trip.days.map {
                                                if (it.id == day.id) updatedDay else it
                                            }
                                        )
                                    )
                                }
                            )
                        }

                        if (activity.location.isNotBlank()) {
                            InfoLine(Icons.Default.LocationOn, activity.location)
                        }
                        if (activity.transport.isNotBlank()) {
                            InfoLine(Icons.Default.DirectionsTransit, activity.transport)
                        }
                        if (activity.directions.isNotBlank()) {
                            InfoLine(Icons.Default.Route, activity.directions)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (activity.duration.isNotBlank()) {
                                MetaChip("⏱ ${activity.duration}", SoftBlue, Sky)
                            }
                            if (activity.cost.isNotBlank()) {
                                MetaChip("💳 ${activity.cost}", SoftSun, Color(0xFF9A6600))
                            }
                        }

                        if (activity.notes.isNotBlank()) {
                            Text(
                                text = activity.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        HorizontalDivider(color = Color(0xFFE8EDF3))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    onOpenUrl(
                                        activity.mapsUrl.ifBlank {
                                            "https://www.google.com/maps/search/?api=1&query=" +
                                                Uri.encode(activity.location.ifBlank { activity.name })
                                        }
                                    )
                                },
                                modifier = Modifier.size(42.dp)
                            ) {
                                GoogleMapsBrandIcon(Modifier.size(34.dp))
                            }

                            IconButton(
                                onClick = {
                                    onOpenUrl(
                                        "https://waze.com/ul?q=" +
                                            Uri.encode(activity.location.ifBlank { activity.name }) +
                                            "&navigate=yes"
                                    )
                                },
                                modifier = Modifier.size(42.dp)
                            ) {
                                WazeBrandIcon(Modifier.size(34.dp))
                            }

                            IconButton(
                                onClick = {
                                    val query = "restaurants near " +
                                        activity.location.ifBlank { activity.name }
                                    onOpenUrl(
                                        "https://www.google.com/maps/search/?api=1&query=" +
                                            Uri.encode(query)
                                    )
                                },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(SoftCoral),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Restaurant,
                                        "מסעדות",
                                        tint = Coral,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            IconButton(
                                onClick = { editingActivity = activity },
                                modifier = Modifier.size(38.dp)
                            ) {
                                SmallEditIcon(Modifier.size(30.dp))
                            }

                            IconButton(
                                onClick = {
                                    val updatedDay = day.copy(
                                        activities = day.activities.filterNot { it.id == activity.id }
                                    )
                                    onTripChange(
                                        trip.copy(
                                            days = trip.days.map {
                                                if (it.id == day.id) updatedDay else it
                                            }
                                        )
                                    )
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                SmallDeleteIcon(Modifier.size(30.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (addActivity) {
        ActivityEditorDialog(
            title = "פעילות חדשה",
            activity = null,
            onDismiss = { addActivity = false },
            onConfirm = { activity ->
                val updatedDay = day.copy(activities = day.activities + activity)
                onTripChange(
                    trip.copy(
                        days = trip.days.map { if (it.id == day.id) updatedDay else it }
                    )
                )
                addActivity = false
            }
        )
    }

    editingActivity?.let { activity ->
        ActivityEditorDialog(
            title = "עריכת פעילות",
            activity = activity,
            onDismiss = { editingActivity = null },
            onConfirm = { updated ->
                val updatedDay = day.copy(
                    activities = day.activities.map {
                        if (it.id == updated.id) updated else it
                    }
                )
                onTripChange(
                    trip.copy(
                        days = trip.days.map { if (it.id == day.id) updatedDay else it }
                    )
                )
                editingActivity = null
            }
        )
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(17.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun MetaChip(text: String, background: Color, content: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = background
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun ActivityEditorDialog(
    title: String,
    activity: ActivityItem?,
    onDismiss: () -> Unit,
    onConfirm: (ActivityItem) -> Unit
) {
    var time by remember(activity?.id) { mutableStateOf(activity?.time.orEmpty()) }
    var name by remember(activity?.id) { mutableStateOf(activity?.name.orEmpty()) }
    var location by remember(activity?.id) { mutableStateOf(activity?.location.orEmpty()) }
    var transport by remember(activity?.id) { mutableStateOf(activity?.transport.orEmpty()) }
    var directions by remember(activity?.id) { mutableStateOf(activity?.directions.orEmpty()) }
    var duration by remember(activity?.id) { mutableStateOf(activity?.duration.orEmpty()) }
    var cost by remember(activity?.id) { mutableStateOf(activity?.cost.orEmpty()) }
    var notes by remember(activity?.id) { mutableStateOf(activity?.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(time, { time = it }, label = { Text("שעה") }) }
                item { OutlinedTextField(name, { name = it }, label = { Text("שם הפעילות") }) }
                item { OutlinedTextField(location, { location = it }, label = { Text("מיקום") }) }
                item { OutlinedTextField(transport, { transport = it }, label = { Text("אמצעי הגעה") }) }
                item { OutlinedTextField(directions, { directions = it }, label = { Text("קו / הוראות") }) }
                item { OutlinedTextField(duration, { duration = it }, label = { Text("משך") }) }
                item { OutlinedTextField(cost, { cost = it }, label = { Text("עלות") }) }
                item { OutlinedTextField(notes, { notes = it }, label = { Text("הערות") }) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        ActivityItem(
                            id = activity?.id ?: UUID.randomUUID().toString(),
                            time = time,
                            name = name,
                            location = location,
                            transport = transport,
                            directions = directions,
                            duration = duration,
                            cost = cost,
                            notes = notes,
                            mapsUrl = "https://www.google.com/maps/search/?api=1&query=" +
                                Uri.encode(location.ifBlank { name }),
                            completed = activity?.completed ?: false
                        )
                    )
                }
            ) { Text("שמירה") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

@Composable
private fun HotelsScreen(trip: Trip, onTripChange: (Trip) -> Unit, onOpenUrl: (String) -> Unit, modifier: Modifier) {
    var add by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            GradientHeader(
                title = "מלונות",
                subtitle = "מקומות הלינה ותאריכי השהייה",
                emoji = "🏨",
                start = Aqua,
                end = Navy
            )
            AccentButton("הוספת מלון", "＋", { add = true }, color = Aqua, modifier = Modifier.fillMaxWidth())
        }
        items(trip.hotels) { h ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(h.name, fontWeight = FontWeight.Bold)
                    Text("${h.checkIn}–${h.checkOut}")
                    Text(h.address)
                    Row {
                        TextButton(onClick = { onOpenUrl(h.mapsUrl.ifBlank { "https://www.google.com/maps/search/?api=1&query=${Uri.encode(h.address)}" }) }) { Text("Maps") }
                        IconButton(onClick = { onTripChange(trip.copy(hotels = trip.hotels.filterNot { it.id == h.id })) }) { Icon(Icons.Default.Delete, null) }
                    }
                }
            }
        }
    }
    if (add) {
        SimpleTextDialog("מלון חדש", listOf("שם","צ'ק-אין","צ'ק-אאוט","כתובת"),
            { add = false }) { v ->
            onTripChange(trip.copy(hotels = trip.hotels + Hotel(UUID.randomUUID().toString(),v[0],v[1],v[2],v[3],
                "https://www.google.com/maps/search/?api=1&query=${Uri.encode(v[3].ifBlank { v[0] })}")))
            add = false
        }
    }
}

@Composable
private fun RestaurantsScreen(trip: Trip, onTripChange: (Trip) -> Unit, onOpenUrl: (String) -> Unit, modifier: Modifier) {
    var add by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            GradientHeader(
                title = "מסעדות",
                subtitle = "מקומות מומלצים ליד המסלול",
                emoji = "🍽️",
                start = Coral,
                end = Color(0xFFB84A3A)
            )
            AccentButton("הוספת מסעדה", "＋", { add = true }, color = Coral, modifier = Modifier.fillMaxWidth())
        }
        items(trip.restaurants) { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(r.name, fontWeight = FontWeight.Bold)
                    Text("${r.area} · ${r.type} · ${r.price}")
                    if (r.notes.isNotBlank()) Text(r.notes)
                    Row {
                        TextButton(onClick = { onOpenUrl(r.mapsUrl.ifBlank { "https://www.google.com/maps/search/?api=1&query=${Uri.encode(r.name)}" }) }) { Text("Maps") }
                        IconButton(onClick = { onTripChange(trip.copy(restaurants = trip.restaurants.filterNot { it.id == r.id })) }) { Icon(Icons.Default.Delete, null) }
                    }
                }
            }
        }
    }
    if (add) {
        SimpleTextDialog("מסעדה חדשה", listOf("שם","אזור","סוג","מחיר","הערה"),
            { add = false }) { v ->
            onTripChange(trip.copy(restaurants = trip.restaurants + Restaurant(UUID.randomUUID().toString(),name=v[0],area=v[1],type=v[2],price=v[3],notes=v[4],
                mapsUrl="https://www.google.com/maps/search/?api=1&query=${Uri.encode(v[0] + " " + v[1])}")))
            add = false
        }
    }
}

@Composable
private fun ExpensesScreen(trip: Trip, onTripChange: (Trip) -> Unit, modifier: Modifier) {
    var add by remember { mutableStateOf(false) }
    val sums = trip.expenses.groupBy { it.currency }.mapValues { e -> e.value.sumOf { it.amount } }
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            GradientHeader(
                title = "תקציב",
                subtitle = "מעקב אחר הוצאות הטיול",
                emoji = "💰",
                start = Sun,
                end = Color(0xFFE79A18)
            )
            AccentButton("הוספת הוצאה", "＋", { add = true }, color = Color(0xFFE7A62D), modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("HUF","EUR","ILS").forEach { c ->
                    AssistChip(onClick = {}, label = { Text("$c ${sums[c] ?: 0.0}") })
                }
            }
        }
        items(trip.expenses) { e ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text(e.title, fontWeight = FontWeight.Bold); Text("${e.category} · ${e.date}") }
                    Row { Text("${e.amount} ${e.currency}"); IconButton(onClick = {
                        onTripChange(trip.copy(expenses = trip.expenses.filterNot { it.id == e.id }))
                    }) { Icon(Icons.Default.Delete, null) } }
                }
            }
        }
    }
    if (add) {
        SimpleTextDialog("הוצאה חדשה", listOf("תיאור","סכום","מטבע HUF/EUR/ILS","קטגוריה","תאריך"),
            { add = false }) { v ->
            onTripChange(trip.copy(expenses = trip.expenses + Expense(UUID.randomUUID().toString(),v[0],v[1].toDoubleOrNull() ?: 0.0,v[2],v[3],v[4])))
            add = false
        }
    }
}

@Composable
private fun DocumentsScreen(trip: Trip, onTripChange: (Trip) -> Unit, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val doc = TripDocument(UUID.randomUUID().toString(), it.lastPathSegment ?: "מסמך", it.toString())
            onTripChange(trip.copy(documents = trip.documents + doc))
        }
    }

    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            GradientHeader(
                title = "מסמכים",
                subtitle = "כרטיסים, הזמנות וקבצים חשובים",
                emoji = "🎫",
                start = Mint,
                end = Color(0xFF378A63)
            )
            AccentButton("הוספת מסמך", "＋", { launcher.launch(arrayOf("*/*")) }, color = Mint, modifier = Modifier.fillMaxWidth())
        }
        items(trip.documents) { d ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text(d.name, fontWeight = FontWeight.Bold); Text(d.type) }
                    Row {
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(d.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            }
                        }) { Text("פתיחה") }
                        IconButton(onClick = { onTripChange(trip.copy(documents = trip.documents.filterNot { it.id == d.id })) }) { Icon(Icons.Default.Delete, null) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleTextDialog(
    title: String,
    fields: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val values = remember { fields.map { mutableStateOf("") } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEachIndexed { i, label ->
                    OutlinedTextField(values[i].value, { values[i].value = it }, label = { Text(label) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(values.map { it.value }) }) { Text("שמירה") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

@Composable
private fun TextAreaDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().height(250.dp)) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("ייבוא") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}
