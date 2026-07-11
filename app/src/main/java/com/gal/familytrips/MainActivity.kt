
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
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
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
            NavigationBar(
                containerColor = CardWhite,
                tonalElevation = 10.dp,
                modifier = Modifier.height(72.dp)
            ) {
                listOf(
                    Triple(Icons.Default.Home, "טיולים", 0),
                    Triple(Icons.Default.Today, "ימים", 1),
                    Triple(Icons.Default.Hotel, "מלונות", 2),
                    Triple(Icons.Default.Restaurant, "מסעדות", 3),
                    Triple(Icons.Default.AttachMoney, "תקציב", 4),
                    Triple(Icons.Default.Description, "מסמכים", 5),
                    Triple(Icons.Default.Info, "מידע", 6),
                    Triple(Icons.Default.Luggage, "ציוד", 7)
                ).forEach { (icon,label,index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier.size(29.dp)
                            )
                        },
                        label = null,
                        alwaysShowLabel = false
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
            6 -> GeneralInfoScreen(
                trip = trip,
                onTripChange = { onStateChange(state.replaceTrip(it)) },
                modifier = Modifier.padding(padding)
            )
            7 -> PackingScreen(
                trip = trip,
                onTripChange = { onStateChange(state.replaceTrip(it)) },
                modifier = Modifier.padding(padding)
            )
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

        DynamicClockBar(trip)
        Spacer(Modifier.height(10.dp))

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
                        .height(252.dp),
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
                                .height(62.dp)
                        )

                        WeatherCard(
                            trip = trip,
                            day = day,
                            modifier = Modifier.fillMaxWidth()
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
                Icon(Icons.Default.ArrowBack, "חזרה")
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
        DynamicClockBar(trip)
        Spacer(Modifier.height(8.dp))
        WeatherCard(trip = trip, day = day, modifier = Modifier.fillMaxWidth())
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
                            InfoLine("📍", activity.location)
                        }
                        if (activity.transport.isNotBlank()) {
                            InfoLine("🚌", activity.transport)
                        }
                        if (activity.directions.isNotBlank()) {
                            InfoLine("➡️", activity.directions)
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

            item {
                val dayRestaurants = trip.restaurants.filter { it.dayId == day.id }
                DayRestaurantsCard(
                    day = day,
                    restaurants = dayRestaurants,
                    onOpenUrl = onOpenUrl
                )
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
private fun DayRestaurantsCard(
    day: TripDay,
    restaurants: List<Restaurant>,
    onOpenUrl: (String) -> Unit
) {
    SectionCard(containerColor = SoftCoral) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🍽️", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("מסעדות באזור היום", fontWeight = FontWeight.Bold)
                Text(day.title, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }

        if (restaurants.isEmpty()) {
            Text("לא נשמרו עדיין מסעדות ליום הזה", color = TextSecondary)
        } else {
            restaurants.forEach { restaurant ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFDCD6))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(restaurant.name, fontWeight = FontWeight.Bold)
                            Text(
                                listOf(restaurant.area, restaurant.type, restaurant.price)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        IconButton(
                            onClick = {
                                onOpenUrl(
                                    restaurant.mapsUrl.ifBlank {
                                        "https://www.google.com/maps/search/?api=1&query=" +
                                            Uri.encode(restaurant.name + " " + restaurant.area)
                                    }
                                )
                            }
                        ) {
                            GoogleMapsBrandIcon(Modifier.size(30.dp))
                        }
                    }
                }
            }
        }

        SoftActionButton(
            text = "חיפוש מסעדות נוספות באזור",
            emoji = "🔎",
            onClick = {
                val area = day.activities.firstOrNull { it.location.isNotBlank() }?.location
                    ?: day.title
                onOpenUrl(
                    "https://www.google.com/maps/search/?api=1&query=" +
                        Uri.encode("family restaurants near $area")
                )
            },
            container = CardWhite,
            contentColor = Coral,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun InfoLine(marker: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(marker, style = MaterialTheme.typography.bodySmall)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
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
private fun HotelsScreen(
    trip: Trip,
    onTripChange: (Trip) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier
) {
    var add by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            GradientHeader(
                title = "מלונות",
                subtitle = "מקומות הלינה ותאריכי השהייה",
                emoji = "🏨",
                start = Aqua,
                end = Navy
            )
            DynamicClockBar(trip)
            Spacer(Modifier.height(10.dp))
            AccentButton(
                text = "הוספת מלון",
                emoji = "＋",
                onClick = { add = true },
                color = Aqua,
                modifier = Modifier.fillMaxWidth()
            )
        }

        items(trip.hotels, key = { it.id }) { hotel ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDEEF1)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DayThumbnail("hotel", Modifier.size(58.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                hotel.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(hotel.address, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetaChip("כניסה ${hotel.checkIn}", SoftAqua, Color(0xFF087C8A))
                        MetaChip("יציאה ${hotel.checkOut}", SoftBlue, Sky)
                    }

                    if (hotel.notes.isNotBlank()) {
                        Text(hotel.notes, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }

                    HorizontalDivider(color = Color(0xFFE8EDF3))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                onOpenUrl(
                                    hotel.mapsUrl.ifBlank {
                                        "https://www.google.com/maps/search/?api=1&query=" +
                                            Uri.encode(hotel.address.ifBlank { hotel.name })
                                    }
                                )
                            }
                        ) {
                            GoogleMapsBrandIcon(Modifier.size(32.dp))
                        }

                        IconButton(
                            onClick = {
                                onOpenUrl(
                                    "https://waze.com/ul?q=" +
                                        Uri.encode(hotel.address.ifBlank { hotel.name }) +
                                        "&navigate=yes"
                                )
                            }
                        ) {
                            WazeBrandIcon(Modifier.size(32.dp))
                        }

                        Spacer(Modifier.weight(1f))

                        IconButton(
                            onClick = {
                                onTripChange(
                                    trip.copy(hotels = trip.hotels.filterNot { it.id == hotel.id })
                                )
                            }
                        ) {
                            SmallDeleteIcon(Modifier.size(30.dp))
                        }
                    }
                }
            }
        }
    }

    if (add) {
        SimpleTextDialog(
            title = "מלון חדש",
            fields = listOf("שם", "צ'ק-אין", "צ'ק-אאוט", "כתובת"),
            onDismiss = { add = false },
            onConfirm = { values ->
                onTripChange(
                    trip.copy(
                        hotels = trip.hotels + Hotel(
                            id = UUID.randomUUID().toString(),
                            name = values[0],
                            checkIn = values[1],
                            checkOut = values[2],
                            address = values[3],
                            mapsUrl = "https://www.google.com/maps/search/?api=1&query=" +
                                Uri.encode(values[3].ifBlank { values[0] })
                        )
                    )
                )
                add = false
            }
        )
    }
}

@Composable
private fun RestaurantsScreen(
    trip: Trip,
    onTripChange: (Trip) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier
) {
    var add by remember { mutableStateOf(false) }
    val grouped = trip.days.sortedBy { it.date }.map { day ->
        day to trip.restaurants.filter { it.dayId == day.id }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            GradientHeader(
                title = "מסעדות",
                subtitle = "המלצות לפי האזור של כל יום",
                emoji = "🍽️",
                start = Coral,
                end = Color(0xFFB84A3A)
            )
            AccentButton(
                text = "הוספת מסעדה",
                emoji = "＋",
                onClick = { add = true },
                color = Coral,
                modifier = Modifier.fillMaxWidth()
            )
        }

        grouped.forEach { (day, restaurants) ->
            item(key = "header-${day.id}") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DayThumbnail(day.imageKey, Modifier.size(44.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(day.title, fontWeight = FontWeight.Bold)
                        Text(day.date, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }

            items(restaurants, key = { it.id }) { restaurant ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFDDD7)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(SoftCoral),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🍴")
                            }

                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(restaurant.name, fontWeight = FontWeight.Bold)
                                Text(
                                    listOf(restaurant.area, restaurant.type)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            if (restaurant.price.isNotBlank()) {
                                MetaChip(restaurant.price, SoftSun, Color(0xFF8F6500))
                            }
                        }

                        if (restaurant.notes.isNotBlank()) {
                            Text(restaurant.notes, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }

                        HorizontalDivider(color = Color(0xFFE8EDF3))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    onOpenUrl(
                                        restaurant.mapsUrl.ifBlank {
                                            "https://www.google.com/maps/search/?api=1&query=" +
                                                Uri.encode(restaurant.name + " " + restaurant.area)
                                        }
                                    )
                                }
                            ) {
                                GoogleMapsBrandIcon(Modifier.size(32.dp))
                            }

                            Spacer(Modifier.weight(1f))

                            IconButton(
                                onClick = {
                                    onTripChange(
                                        trip.copy(
                                            restaurants = trip.restaurants.filterNot { it.id == restaurant.id }
                                        )
                                    )
                                }
                            ) {
                                SmallDeleteIcon(Modifier.size(30.dp))
                            }
                        }
                    }
                }
            }

            if (restaurants.isEmpty()) {
                item(key = "empty-${day.id}") {
                    Text(
                        "אין עדיין מסעדות שמורות ליום הזה",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }

    if (add) {
        SimpleTextDialog(
            title = "מסעדה חדשה",
            fields = listOf("שם", "אזור", "סוג", "מחיר", "הערה"),
            onDismiss = { add = false },
            onConfirm = { values ->
                val defaultDayId = trip.days.firstOrNull()?.id
                onTripChange(
                    trip.copy(
                        restaurants = trip.restaurants + Restaurant(
                            id = UUID.randomUUID().toString(),
                            dayId = defaultDayId,
                            name = values[0],
                            area = values[1],
                            type = values[2],
                            price = values[3],
                            notes = values[4],
                            mapsUrl = "https://www.google.com/maps/search/?api=1&query=" +
                                Uri.encode(values[0] + " " + values[1])
                        )
                    )
                )
                add = false
            }
        )
    }
}

@Composable
private fun ExpensesScreen(
    trip: Trip,
    onTripChange: (Trip) -> Unit,
    modifier: Modifier
) {
    var addCustom by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<BudgetTemplate?>(null) }

    val templates = suggestedBudgetTemplates(trip)
    val templateIds = templates.map { it.id }.toSet()
    val customExpenses = trip.expenses.filterNot { it.id in templateIds }
    val totals = trip.expenses
        .filter { it.amount > 0 }
        .groupBy { it.currency }
        .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            GradientHeader(
                title = "תקציב",
                subtitle = "סעיפים נוצרים אוטומטית מהמסלול",
                emoji = "💰",
                start = Sun,
                end = Color(0xFFE79A18)
            )
        }

        item {
            SectionCard(containerColor = SoftSun) {
                Text("סיכום הוצאות", fontWeight = FontWeight.Bold)
                if (totals.isEmpty()) {
                    Text("עדיין לא הוזנו סכומים", color = TextSecondary)
                } else {
                    totals.forEach { (currency, amount) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(currency, color = TextSecondary)
                            Text(
                                String.format(java.util.Locale.US, "%.2f", amount),
                                fontWeight = FontWeight.Bold,
                                color = Navy
                            )
                        }
                    }
                }
                AccentButton(
                    text = "הוצאה נוספת",
                    emoji = "＋",
                    onClick = { addCustom = true },
                    color = Color(0xFFE7A62D),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Text(
                "הוצאות מוכנות להזנה",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(templates, key = { it.id }) { template ->
            val saved = trip.expenses.firstOrNull { it.id == template.id }
            BudgetTemplateCard(
                template = template,
                expense = saved,
                onEnterAmount = { editingTemplate = template },
                onClear = {
                    onTripChange(
                        trip.copy(
                            expenses = trip.expenses.filterNot { it.id == template.id }
                        )
                    )
                }
            )
        }

        if (customExpenses.isNotEmpty()) {
            item {
                Text(
                    "הוצאות נוספות",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        items(customExpenses, key = { it.id }) { expense ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, Color(0xFFE3E9F0))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(expense.title, fontWeight = FontWeight.Bold)
                        Text(
                            "${expense.category} · ${expense.date}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Text(
                        "${expense.amount} ${expense.currency}",
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    IconButton(
                        onClick = {
                            onTripChange(
                                trip.copy(
                                    expenses = trip.expenses.filterNot { it.id == expense.id }
                                )
                            )
                        }
                    ) {
                        SmallDeleteIcon(Modifier.size(28.dp))
                    }
                }
            }
        }
    }

    editingTemplate?.let { template ->
        val existing = trip.expenses.firstOrNull { it.id == template.id }
        BudgetAmountDialog(
            template = template,
            existing = existing,
            onDismiss = { editingTemplate = null },
            onConfirm = { amount, currency ->
                val updated = Expense(
                    id = template.id,
                    title = template.title,
                    amount = amount,
                    currency = currency,
                    category = template.category,
                    date = template.date
                )
                onTripChange(
                    trip.copy(
                        expenses = trip.expenses.filterNot { it.id == template.id } + updated
                    )
                )
                editingTemplate = null
            }
        )
    }

    if (addCustom) {
        SimpleTextDialog(
            title = "הוצאה נוספת",
            fields = listOf("תיאור", "סכום", "מטבע", "קטגוריה", "תאריך"),
            onDismiss = { addCustom = false },
            onConfirm = { values ->
                onTripChange(
                    trip.copy(
                        expenses = trip.expenses + Expense(
                            id = UUID.randomUUID().toString(),
                            title = values[0],
                            amount = values[1].toDoubleOrNull() ?: 0.0,
                            currency = values[2].ifBlank { destinationCurrency(trip.destination) },
                            category = values[3].ifBlank { "כללי" },
                            date = values[4].ifBlank { trip.startDate }
                        )
                    )
                )
                addCustom = false
            }
        )
    }
}

@Composable
private fun BudgetTemplateCard(
    template: BudgetTemplate,
    expense: Expense?,
    onEnterAmount: () -> Unit,
    onClear: () -> Unit
) {
    val hasAmount = expense != null && expense.amount > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasAmount) SoftMint else CardWhite
        ),
        border = BorderStroke(
            1.dp,
            if (hasAmount) Color(0xFFBFE5D0) else Color(0xFFE3E9F0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (hasAmount) CardWhite else SoftSun),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (template.category) {
                        "מלונות" -> "🏨"
                        "טיסות" -> "✈️"
                        "תחבורה" -> "🚌"
                        "אוכל" -> "🍽️"
                        "אטרקציות" -> "🎫"
                        "קניות" -> "🛍️"
                        else -> "💳"
                    }
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(template.title, fontWeight = FontWeight.Bold)
                Text(
                    "${template.category} · ${template.date}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            if (hasAmount) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${expense!!.amount} ${expense.currency}",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D56)
                    )
                    Row {
                        TextButton(onClick = onEnterAmount) { Text("עריכה") }
                        IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                            SmallDeleteIcon(Modifier.size(25.dp))
                        }
                    }
                }
            } else {
                FilledTonalButton(
                    onClick = onEnterAmount,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SoftSun,
                        contentColor = Color(0xFF8F6500)
                    )
                ) {
                    Text("הזן סכום")
                }
            }
        }
    }
}

@Composable
private fun BudgetAmountDialog(
    template: BudgetTemplate,
    existing: Expense?,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var amountText by remember(template.id) {
        mutableStateOf(existing?.amount?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var currency by remember(template.id) {
        mutableStateOf(existing?.currency ?: template.currency)
    }
    var menuOpen by remember { mutableStateOf(false) }
    val currencies = listOf(template.currency, "EUR", "USD", "ILS").distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(template.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it.filter { char -> char.isDigit() || char == '.' }
                    },
                    label = { Text("סכום") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("מטבע: $currency", modifier = Modifier.weight(1f))
                        Text("⌄")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        currencies.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    currency = option
                                    menuOpen = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = (amountText.toDoubleOrNull() ?: 0.0) >= 0,
                onClick = {
                    onConfirm(amountText.toDoubleOrNull() ?: 0.0, currency)
                }
            ) { Text("שמירה") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ביטול") }
        }
    )
}

@Composable
private fun DocumentsScreen(
    trip: Trip,
    onTripChange: (Trip) -> Unit,
    modifier: Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingRequirement by remember { mutableStateOf<DocumentRequirement?>(null) }
    val requirements = suggestedDocumentRequirements(trip)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val requirement = pendingRequirement
        if (uri != null && requirement != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val document = TripDocument(
                id = UUID.randomUUID().toString(),
                name = uri.lastPathSegment ?: requirement.title,
                uri = uri.toString(),
                type = requirement.type,
                notes = requirement.key
            )
            onTripChange(trip.copy(documents = trip.documents + document))
        }
        pendingRequirement = null
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            GradientHeader(
                title = "מסמכים",
                subtitle = "נוצר אוטומטית מהמלונות והפעילויות",
                emoji = "🎫",
                start = Mint,
                end = Color(0xFF378A63)
            )

            val completed = requirements.count { requirement ->
                trip.documents.any { document ->
                    document.notes == requirement.key ||
                        document.name.contains(requirement.title, ignoreCase = true)
                }
            }

            SectionCard(containerColor = SoftMint) {
                Text(
                    "$completed מתוך ${requirements.size} מסמכים נוספו",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF276B4A)
                )
                LinearProgressIndicator(
                    progress = {
                        if (requirements.isEmpty()) 0f
                        else completed.toFloat() / requirements.size.toFloat()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = Mint,
                    trackColor = CardWhite
                )
            }
        }

        items(requirements, key = { it.key }) { requirement ->
            val matching = trip.documents.filter {
                it.notes == requirement.key ||
                    it.name.contains(requirement.title, ignoreCase = true)
            }
            val isAdded = matching.isNotEmpty()

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAdded) SoftMint else CardWhite
                ),
                border = BorderStroke(
                    1.dp,
                    if (isAdded) Color(0xFFBEE6CF) else Color(0xFFE3E9F0)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isAdded) "✅" else "📄")
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(requirement.title, fontWeight = FontWeight.Bold)
                            Text(
                                requirement.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                requirement.type,
                                style = MaterialTheme.typography.labelSmall,
                                color = Mint
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                pendingRequirement = requirement
                                launcher.launch(arrayOf("*/*"))
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isAdded) CardWhite else SoftMint,
                                contentColor = Color(0xFF2E7D56)
                            )
                        ) {
                            Text(if (isAdded) "הוסף עוד" else "הוספה")
                        }
                    }

                    matching.forEach { document ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CardWhite
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    document.name,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                TextButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(document.uri))
                                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            )
                                        }
                                    }
                                ) { Text("פתיחה") }

                                IconButton(
                                    onClick = {
                                        onTripChange(
                                            trip.copy(
                                                documents = trip.documents.filterNot {
                                                    it.id == document.id
                                                }
                                            )
                                        )
                                    }
                                ) {
                                    SmallDeleteIcon(Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            val generalRequirement = DocumentRequirement(
                key = "general-${UUID.randomUUID()}",
                title = "מסמך כללי",
                type = "כללי",
                description = "קובץ נוסף שאינו משויך להזמנה"
            )
            AccentButton(
                text = "הוספת מסמך כללי",
                emoji = "＋",
                onClick = {
                    pendingRequirement = generalRequirement
                    launcher.launch(arrayOf("*/*"))
                },
                color = Mint,
                modifier = Modifier.fillMaxWidth()
            )
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
