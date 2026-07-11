
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
private fun DaysScreen(trip: Trip, onStateChange: (Trip) -> Unit, onSelectDay: (String) -> Unit, modifier: Modifier) {
    var addDay by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            GradientHeader(
                title = "ימי הטיול",
                subtitle = "מסלול מסודר לכל יום",
                emoji = "📅",
                start = Sky,
                end = Navy
            )
            AccentButton("הוספת יום", "＋", { addDay = true }, color = Sky, modifier = Modifier.fillMaxWidth())
        }
        items(trip.days.sortedBy { it.date }) { day ->
            Card(
                onClick = { onSelectDay(day.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = SoftBlue),
                elevation = CardDefaults.cardElevation(5.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(day.date, fontWeight = FontWeight.Bold)
                    Text(day.title)
                    Text("${day.activities.size} פעילויות", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (addDay) {
        SimpleTextDialog("יום חדש", listOf("תאריך YYYY-MM-DD","כותרת"),
            { addDay = false }) { v ->
            onStateChange(trip.copy(days = trip.days + TripDay(UUID.randomUUID().toString(),v[0],v[1])))
            addDay = false
        }
    }
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
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
            Text(day.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = { addActivity = true }) { Icon(Icons.Default.Add, null) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftActionButton("מפה יומית", "🗺️", onClick = {
                val points = day.activities.mapNotNull { it.location.ifBlank { null } }
                if (points.isNotEmpty()) {
                    val origin = points.first()
                    val destination = points.last()
                    val waypoints = points.drop(1).dropLast(1).take(8).joinToString("|")
                    var url = "https://www.google.com/maps/dir/?api=1&origin=${Uri.encode(origin)}&destination=${Uri.encode(destination)}&travelmode=transit"
                    if (waypoints.isNotBlank()) url += "&waypoints=${Uri.encode(waypoints)}"
                    onOpenUrl(url)
                }
            }, container = SoftBlue, contentColor = Sky)
            SoftActionButton("מסלול חי", "📍", onClick = {
                day.activities.firstOrNull { !it.completed }?.let {
                    onOpenUrl("https://www.google.com/maps/search/?api=1&query=${Uri.encode(it.location.ifBlank { it.name })}")
                }
            }, container = SoftAqua, contentColor = Aqua)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(day.activities.sortedBy { it.time }) { activity ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(activity.time, fontWeight = FontWeight.Bold)
                                Text(activity.name, style = MaterialTheme.typography.titleMedium)
                                if (activity.location.isNotBlank()) Text(activity.location, style = MaterialTheme.typography.bodySmall)
                            }
                            Checkbox(activity.completed, onCheckedChange = { checked ->
                                val updatedDay = day.copy(activities = day.activities.map {
                                    if (it.id == activity.id) it.copy(completed = checked) else it
                                })
                                onTripChange(trip.copy(days = trip.days.map { if (it.id == day.id) updatedDay else it }))
                            })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                onOpenUrl(activity.mapsUrl.ifBlank {
                                    "https://www.google.com/maps/search/?api=1&query=${Uri.encode(activity.location.ifBlank { activity.name })}"
                                })
                            }) { Text("Maps") }
                            TextButton(onClick = {
                                onOpenUrl("https://waze.com/ul?q=${Uri.encode(activity.location.ifBlank { activity.name })}&navigate=yes")
                            }) { Text("Waze") }
                            TextButton(onClick = {
                                val filtered = trip.restaurants.filter {
                                    it.activityId == activity.id || (it.activityId == null && it.dayId == day.id)
                                }
                                val query = if (filtered.isNotEmpty()) filtered.first().name else "restaurants near ${activity.location}"
                                onOpenUrl("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")
                            }) { Text("מסעדות") }
                            IconButton(onClick = {
                                val updatedDay = day.copy(activities = day.activities.filterNot { it.id == activity.id })
                                onTripChange(trip.copy(days = trip.days.map { if (it.id == day.id) updatedDay else it }))
                            }) { Icon(Icons.Default.Delete, null) }
                        }
                    }
                }
            }
        }
    }

    if (addActivity) {
        SimpleTextDialog("פעילות חדשה", listOf("שעה","שם","מיקום","הערות"),
            { addActivity = false }) { v ->
            val activity = ActivityItem(
                UUID.randomUUID().toString(), v[0], v[1], v[2], v[3],
                "https://www.google.com/maps/search/?api=1&query=${Uri.encode(v[2].ifBlank { v[1] })}"
            )
            val updatedDay = day.copy(activities = day.activities + activity)
            onTripChange(trip.copy(days = trip.days.map { if (it.id == day.id) updatedDay else it }))
            addActivity = false
        }
    }
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
