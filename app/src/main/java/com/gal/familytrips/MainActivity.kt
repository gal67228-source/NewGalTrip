
package com.gal.familytrips

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.time.LocalDate
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

    LaunchedEffect(state.currentTripId) {
        selectedDayId = null
        tab = 0
        showAddTrip = false
    }

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
        key(trip.id, tab) {
            when (tab) {
                0 -> TripsScreen(
                    state,
                    onStateChange,
                    onShareTrip,
                    onImportTrip,
                    Modifier.padding(padding)
                )

                1 -> if (selectedDayId == null) {
                    DaysScreen(
                        trip,
                        onStateChange = { updated ->
                            onStateChange(state.replaceTrip(updated))
                        },
                        onSelectDay = { selectedDayId = it },
                        modifier = Modifier.padding(padding)
                    )
                } else {
                    DayDetailScreen(
                        trip = trip,
                        dayId = selectedDayId!!,
                        onBack = { selectedDayId = null },
                        onTripChange = {
                            onStateChange(state.replaceTrip(it))
                        },
                        onOpenUrl = onOpenUrl,
                        modifier = Modifier.padding(padding)
                    )
                }

                2 -> HotelsScreen(
                    trip,
                    { onStateChange(state.replaceTrip(it)) },
                    onOpenUrl,
                    Modifier.padding(padding)
                )

                3 -> RestaurantsScreen(
                    trip,
                    { onStateChange(state.replaceTrip(it)) },
                    onOpenUrl,
                    Modifier.padding(padding)
                )

                4 -> ExpensesScreen(
                    trip,
                    { onStateChange(state.replaceTrip(it)) },
                    Modifier.padding(padding)
                )

                5 -> DocumentsScreen(
                    trip,
                    { onStateChange(state.replaceTrip(it)) },
                    Modifier.padding(padding)
                )

                6 -> GeneralInfoScreen(
                    trip = trip,
                    onTripChange = {
                        onStateChange(state.replaceTrip(it))
                    },
                    modifier = Modifier.padding(padding)
                )

                7 -> PackingScreen(
                    trip = trip,
                    onTripChange = {
                        onStateChange(state.replaceTrip(it))
                    },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }

    if (showAddTrip) {
        NewTripDialog(
            onDismiss = { showAddTrip = false },
            onConfirm = { name, destinations, startDate, endDate ->
                val newTrip = Trip(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    destination = destinations.joinToString(" • "),
                    destinationStops = destinations,
                    startDate = startDate,
                    endDate = endDate,
                    days = emptyList(),
                    hotels = emptyList(),
                    restaurants = emptyList(),
                    expenses = emptyList(),
                    documents = emptyList(),
                    packingItems = emptyList(),
                    packingCategories = listOf(
                        "מסמכים",
                        "כסף",
                        "אלקטרוניקה",
                        "בגדים",
                        "רחצה",
                        "בריאות",
                        "ילדים",
                        "טיול יומי",
                        "כללי"
                    ),
                    offlineMode = false
                )

                onStateChange(
                    state.copy(
                        trips = state.trips + newTrip,
                        currentTripId = newTrip.id
                    )
                )
                showAddTrip = false
            }
        )
    }
}

private data class DestinationOption(
    val city: String,
    val country: String
) {
    val displayName: String
        get() = "$city, $country"
}

private val majorDestinations = listOf(
    DestinationOption("בודפשט", "הונגריה"),
    DestinationOption("וינה", "אוסטריה"),
    DestinationOption("פראג", "צ'כיה"),
    DestinationOption("ברלין", "גרמניה"),
    DestinationOption("מינכן", "גרמניה"),
    DestinationOption("פריז", "צרפת"),
    DestinationOption("לונדון", "בריטניה"),
    DestinationOption("אמסטרדם", "הולנד"),
    DestinationOption("בריסל", "בלגיה"),
    DestinationOption("רומא", "איטליה"),
    DestinationOption("מילאנו", "איטליה"),
    DestinationOption("ונציה", "איטליה"),
    DestinationOption("ברצלונה", "ספרד"),
    DestinationOption("מדריד", "ספרד"),
    DestinationOption("ליסבון", "פורטוגל"),
    DestinationOption("אתונה", "יוון"),
    DestinationOption("לרנקה", "קפריסין"),
    DestinationOption("בוקרשט", "רומניה"),
    DestinationOption("ורשה", "פולין"),
    DestinationOption("קרקוב", "פולין"),
    DestinationOption("ציריך", "שווייץ"),
    DestinationOption("ניו יורק", "ארצות הברית"),
    DestinationOption("לוס אנג'לס", "ארצות הברית"),
    DestinationOption("מיאמי", "ארצות הברית"),
    DestinationOption("אורלנדו", "ארצות הברית"),
    DestinationOption("לאס וגאס", "ארצות הברית"),
    DestinationOption("טורונטו", "קנדה"),
    DestinationOption("דובאי", "איחוד האמירויות"),
    DestinationOption("בנגקוק", "תאילנד"),
    DestinationOption("טוקיו", "יפן")
)

@Composable
private fun NewTripDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        destinations: List<String>,
        startDate: String,
        endDate: String
    ) -> Unit
) {
    var tripName by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var multiDestination by remember { mutableStateOf(false) }
    var destinationMenuOpen by remember { mutableStateOf(false) }
    var customDestination by remember { mutableStateOf("") }

    val selectedDestinations = remember {
        mutableStateListOf<String>()
    }

    val validDates = startDate.isNotBlank() &&
        endDate.isNotBlank() &&
        runCatching {
            !LocalDate.parse(endDate).isBefore(LocalDate.parse(startDate))
        }.getOrDefault(false)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("טיול חדש") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = tripName,
                        onValueChange = { tripName = it },
                        label = { Text("שם הטיול") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "טיול רב־יעדי",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "אפשר לבחור כמה ערים או מדינות",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        Switch(
                            checked = multiDestination,
                            onCheckedChange = { checked ->
                                multiDestination = checked
                                if (!checked && selectedDestinations.size > 1) {
                                    val first = selectedDestinations.first()
                                    selectedDestinations.clear()
                                    selectedDestinations.add(first)
                                }
                            }
                        )
                    }
                }

                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { destinationMenuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                if (selectedDestinations.isEmpty()) {
                                    "בחירת עיר או מדינה"
                                } else if (multiDestination) {
                                    "הוספת יעד נוסף"
                                } else {
                                    "החלפת יעד"
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Text("⌄")
                        }

                        DropdownMenu(
                            expanded = destinationMenuOpen,
                            onDismissRequest = {
                                destinationMenuOpen = false
                            }
                        ) {
                            majorDestinations.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(option.displayName)
                                    },
                                    onClick = {
                                        if (multiDestination) {
                                            if (
                                                option.displayName !in
                                                selectedDestinations
                                            ) {
                                                selectedDestinations.add(
                                                    option.displayName
                                                )
                                            }
                                        } else {
                                            selectedDestinations.clear()
                                            selectedDestinations.add(
                                                option.displayName
                                            )
                                        }
                                        destinationMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customDestination,
                            onValueChange = { customDestination = it },
                            label = {
                                Text("עיר/מדינה אחרת")
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        FilledTonalButton(
                            enabled = customDestination.isNotBlank(),
                            onClick = {
                                val value = customDestination.trim()
                                if (multiDestination) {
                                    if (value !in selectedDestinations) {
                                        selectedDestinations.add(value)
                                    }
                                } else {
                                    selectedDestinations.clear()
                                    selectedDestinations.add(value)
                                }
                                customDestination = ""
                            }
                        ) {
                            Text("הוספה")
                        }
                    }
                }

                if (selectedDestinations.isNotEmpty()) {
                    item {
                        Text(
                            "יעדים שנבחרו",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(
                        selectedDestinations,
                        key = { it }
                    ) { destination ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SoftBlue,
                            border = BorderStroke(
                                1.dp,
                                Color(0xFFD6E6F8)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 10.dp,
                                        vertical = 7.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "📍 $destination",
                                    modifier = Modifier.weight(1f),
                                    color = Navy
                                )

                                IconButton(
                                    onClick = {
                                        selectedDestinations.remove(
                                            destination
                                        )
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    SmallDeleteIcon(
                                        Modifier.size(25.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    TripDatePickerField(
                        label = "תאריך התחלה",
                        value = startDate,
                        onValueChange = { selected ->
                            startDate = selected
                            if (
                                endDate.isNotBlank() &&
                                runCatching {
                                    LocalDate.parse(endDate).isBefore(
                                        LocalDate.parse(selected)
                                    )
                                }.getOrDefault(false)
                            ) {
                                endDate = selected
                            }
                        }
                    )
                }

                item {
                    TripDatePickerField(
                        label = "תאריך סיום",
                        value = endDate,
                        minimumDate = startDate,
                        onValueChange = {
                            endDate = it
                        }
                    )
                }

                if (
                    startDate.isNotBlank() &&
                    endDate.isNotBlank() &&
                    !validDates
                ) {
                    item {
                        Text(
                            "תאריך הסיום חייב להיות אחרי תאריך ההתחלה",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = tripName.isNotBlank() &&
                    selectedDestinations.isNotEmpty() &&
                    validDates,
                onClick = {
                    onConfirm(
                        tripName.trim(),
                        selectedDestinations.toList(),
                        startDate,
                        endDate
                    )
                }
            ) {
                Text("יצירת טיול")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}

@Composable
private fun TripDatePickerField(
    label: String,
    value: String,
    minimumDate: String = "",
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    val initialDate = runCatching {
        LocalDate.parse(value)
    }.getOrElse {
        LocalDate.now()
    }

    OutlinedButton(
        onClick = {
            val dialog = DatePickerDialog(
                context,
                { _, year, month, day ->
                    val selected = LocalDate.of(
                        year,
                        month + 1,
                        day
                    )
                    onValueChange(selected.toString())
                },
                initialDate.year,
                initialDate.monthValue - 1,
                initialDate.dayOfMonth
            )

            if (minimumDate.isNotBlank()) {
                runCatching {
                    val minimum = LocalDate.parse(minimumDate)
                    dialog.datePicker.minDate = minimum
                        .atStartOfDay(
                            java.time.ZoneId.systemDefault()
                        )
                        .toInstant()
                        .toEpochMilli()
                }
            }

            dialog.show()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text("📅")
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                if (value.isBlank()) {
                    "בחירת תאריך"
                } else {
                    value
                },
                fontWeight = FontWeight.Bold,
                color = Navy
            )
        }
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
    var quickAddActivity by remember { mutableStateOf(false) }
    var editingActivity by remember { mutableStateOf<ActivityItem?>(null) }
    var movingActivity by remember { mutableStateOf<ActivityItem?>(null) }

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
            IconButton(onClick = { quickAddActivity = true }) {
                Icon(Icons.Default.AddCircle, "הוספת פעילות מהירה", tint = Sky)
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
                                onClick = {
                                    val duplicated = activity.copy(
                                        id = UUID.randomUUID().toString(),
                                        name = "${activity.name} – עותק",
                                        completed = false
                                    )
                                    val activityIndex = day.activities.indexOfFirst {
                                        it.id == activity.id
                                    }
                                    val updatedActivities = day.activities.toMutableList()
                                    val insertIndex = if (activityIndex >= 0) {
                                        activityIndex + 1
                                    } else {
                                        updatedActivities.size
                                    }
                                    updatedActivities.add(insertIndex, duplicated)

                                    val updatedDay = day.copy(
                                        activities = updatedActivities
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
                                CompactActionCircle(
                                    symbol = "⧉",
                                    description = "שכפול פעילות",
                                    background = SoftLavender,
                                    content = Lavender
                                )
                            }

                            IconButton(
                                onClick = { movingActivity = activity },
                                modifier = Modifier.size(38.dp)
                            ) {
                                CompactActionCircle(
                                    symbol = "↪",
                                    description = "העברה ליום אחר",
                                    background = SoftAqua,
                                    content = Aqua
                                )
                            }

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

    if (quickAddActivity) {
        QuickActivityDialog(
            day = day,
            onDismiss = { quickAddActivity = false },
            onOpenFullEditor = {
                quickAddActivity = false
                addActivity = true
            },
            onConfirm = { activity ->
                val updatedDay = day.copy(
                    activities = day.activities + activity
                )
                onTripChange(
                    trip.copy(
                        days = trip.days.map {
                            if (it.id == day.id) updatedDay else it
                        }
                    )
                )
                quickAddActivity = false
            }
        )
    }

    movingActivity?.let { activity ->
        MoveActivityDialog(
            activity = activity,
            currentDayId = day.id,
            days = trip.days.sortedBy { it.date },
            onDismiss = { movingActivity = null },
            onConfirm = { targetDayId ->
                val updatedDays = trip.days.map { tripDay ->
                    when (tripDay.id) {
                        day.id -> tripDay.copy(
                            activities = tripDay.activities.filterNot {
                                it.id == activity.id
                            }
                        )
                        targetDayId -> tripDay.copy(
                            activities = tripDay.activities + activity.copy(
                                completed = false
                            )
                        )
                        else -> tripDay
                    }
                }
                onTripChange(trip.copy(days = updatedDays))
                movingActivity = null
            }
        )
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

private data class ActivityPreset(
    val key: String,
    val title: String,
    val emoji: String,
    val defaultName: String,
    val duration: String,
    val transport: String = "",
    val notes: String = ""
)

private val activityPresets = listOf(
    ActivityPreset(
        key = "attraction",
        title = "אטרקציה",
        emoji = "🎫",
        defaultName = "אטרקציה",
        duration = "כשעתיים",
        notes = "מומלץ לבדוק שעות פתיחה וכרטיסים"
    ),
    ActivityPreset(
        key = "meal",
        title = "ארוחה",
        emoji = "🍽️",
        defaultName = "ארוחה",
        duration = "שעה"
    ),
    ActivityPreset(
        key = "hotel",
        title = "מלון",
        emoji = "🏨",
        defaultName = "הגעה / התארגנות במלון",
        duration = "45 דקות"
    ),
    ActivityPreset(
        key = "flight",
        title = "טיסה",
        emoji = "✈️",
        defaultName = "טיסה",
        duration = "לפי הכרטיס",
        transport = "טיסה",
        notes = "להוסיף מסמכי טיסה לכל נוסע"
    ),
    ActivityPreset(
        key = "transfer",
        title = "הסעה",
        emoji = "🚕",
        defaultName = "הסעה",
        duration = "לפי המסלול",
        transport = "הסעה / מונית"
    ),
    ActivityPreset(
        key = "train",
        title = "רכבת",
        emoji = "🚆",
        defaultName = "נסיעה ברכבת",
        duration = "לפי הכרטיס",
        transport = "רכבת"
    ),
    ActivityPreset(
        key = "shopping",
        title = "קניות",
        emoji = "🛍️",
        defaultName = "קניות",
        duration = "שעתיים"
    ),
    ActivityPreset(
        key = "rest",
        title = "מנוחה",
        emoji = "😴",
        defaultName = "מנוחה",
        duration = "שעה וחצי"
    ),
    ActivityPreset(
        key = "pool",
        title = "בריכה",
        emoji = "🏊",
        defaultName = "בריכה / פארק מים",
        duration = "שעתיים"
    ),
    ActivityPreset(
        key = "walk",
        title = "טיול רגלי",
        emoji = "🚶",
        defaultName = "טיול רגלי",
        duration = "שעה",
        transport = "הליכה"
    )
)

@Composable
private fun CompactActionCircle(
    symbol: String,
    description: String,
    background: Color,
    content: Color
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            color = content,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics {
                contentDescription = description
            }
        )
    }
}

@Composable
private fun QuickActivityDialog(
    day: TripDay,
    onDismiss: () -> Unit,
    onOpenFullEditor: () -> Unit,
    onConfirm: (ActivityItem) -> Unit
) {
    var selectedPreset by remember {
        mutableStateOf(activityPresets.first())
    }
    var time by remember { mutableStateOf(nextSuggestedTime(day)) }
    var name by remember { mutableStateOf(selectedPreset.defaultName) }
    var location by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf(selectedPreset.duration) }

    fun selectPreset(preset: ActivityPreset) {
        selectedPreset = preset
        name = preset.defaultName
        duration = preset.duration
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("הוספת פעילות מהירה")
                Text(
                    "בחר סוג והזן רק את הפרטים החשובים",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "סוג פעילות",
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            activityPresets,
                            key = { it.key }
                        ) { preset ->
                            FilterChip(
                                selected = selectedPreset.key == preset.key,
                                onClick = { selectPreset(preset) },
                                label = {
                                    Text("${preset.emoji} ${preset.title}")
                                }
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = { Text("שעה") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("שם הפעילות") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("מיקום – אפשר להשאיר ריק") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = duration,
                        onValueChange = { duration = it },
                        label = { Text("משך") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SoftBlue
                    ) {
                        Column(
                            modifier = Modifier.padding(11.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                "ימולא אוטומטית",
                                fontWeight = FontWeight.Bold,
                                color = Sky
                            )
                            if (selectedPreset.transport.isNotBlank()) {
                                Text(
                                    "אמצעי הגעה: ${selectedPreset.transport}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (selectedPreset.notes.isNotBlank()) {
                                Text(
                                    selectedPreset.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                "קישורי Maps ו-Waze ייווצרו מהמיקום",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                item {
                    TextButton(
                        onClick = onOpenFullEditor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("פתיחת טופס מלא")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val query = location.ifBlank { name }
                    onConfirm(
                        ActivityItem(
                            id = UUID.randomUUID().toString(),
                            time = time.trim(),
                            name = name.trim(),
                            location = location.trim(),
                            transport = selectedPreset.transport,
                            directions = "",
                            duration = duration.trim(),
                            cost = "",
                            notes = selectedPreset.notes,
                            mapsUrl =
                                "https://www.google.com/maps/search/?api=1&query=" +
                                    Uri.encode(query),
                            completed = false
                        )
                    )
                }
            ) {
                Text("הוספה")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}

private fun nextSuggestedTime(day: TripDay): String {
    val lastTime = day.activities
        .mapNotNull { activity ->
            Regex("""(\d{1,2}):(\d{2})""")
                .find(activity.time)
                ?.let { match ->
                    val hour = match.groupValues[1].toIntOrNull()
                    val minute = match.groupValues[2].toIntOrNull()
                    if (hour != null && minute != null) {
                        hour * 60 + minute
                    } else {
                        null
                    }
                }
        }
        .maxOrNull()
        ?: return "09:00"

    val suggested = (lastTime + 60).coerceAtMost(23 * 60 + 59)
    return "%02d:%02d".format(
        suggested / 60,
        suggested % 60
    )
}

@Composable
private fun MoveActivityDialog(
    activity: ActivityItem,
    currentDayId: String,
    days: List<TripDay>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedDayId by remember {
        mutableStateOf(
            days.firstOrNull { it.id != currentDayId }?.id.orEmpty()
        )
    }

    val targetDays = days.filter { it.id != currentDayId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("העברת פעילות")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    activity.name,
                    fontWeight = FontWeight.Bold
                )

                if (targetDays.isEmpty()) {
                    Text(
                        "אין יום נוסף בטיול. יש להוסיף יום לפני העברת הפעילות.",
                        color = TextSecondary
                    )
                } else {
                    targetDays.forEach { targetDay ->
                        Surface(
                            onClick = {
                                selectedDayId = targetDay.id
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selectedDayId == targetDay.id) {
                                SoftBlue
                            } else {
                                CardWhite
                            },
                            border = BorderStroke(
                                if (selectedDayId == targetDay.id) 2.dp else 1.dp,
                                if (selectedDayId == targetDay.id) {
                                    Sky
                                } else {
                                    Color(0xFFE3E9F0)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedDayId == targetDay.id,
                                    onClick = {
                                        selectedDayId = targetDay.id
                                    }
                                )
                                Column {
                                    Text(
                                        targetDay.title,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        targetDay.date,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedDayId.isNotBlank(),
                onClick = {
                    onConfirm(selectedDayId)
                }
            ) {
                Text("העברה")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
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
    var selectedCategory by remember { mutableStateOf("הכול") }
    var editingTemplate by remember { mutableStateOf<BudgetTemplate?>(null) }
    var editingCustomExpense by remember { mutableStateOf<Expense?>(null) }
    var addCustomExpense by remember { mutableStateOf(false) }
    var addCategory by remember { mutableStateOf(false) }
    var pendingCategory by remember { mutableStateOf<String?>(null) }

    val templates = suggestedBudgetTemplates(trip)
    val templateIds = templates.map { it.id }.toSet()
    val customExpenses = trip.expenses.filterNot { it.id in templateIds }

    val defaultCategories = listOf(
        "טיסות",
        "מלונות",
        "תחבורה",
        "אטרקציות",
        "אוכל",
        "קניות",
        "כללי"
    )

    val categories = (
        defaultCategories +
            templates.map { it.category } +
            trip.expenses.map { it.category } +
            listOfNotNull(pendingCategory)
        )
        .filter { it.isNotBlank() }
        .distinct()

    val categorySummaries = categories.map { category ->
        val categoryTemplates = templates.filter { it.category == category }
        val categoryExpenses = trip.expenses.filter { it.category == category }
        val enteredCount = categoryTemplates.count { template ->
            trip.expenses.any { it.id == template.id && it.amount > 0 }
        }

        BudgetCategorySummary(
            category = category,
            enteredCount = enteredCount,
            totalCount = categoryTemplates.size,
            totals = categoryExpenses
                .filter { it.amount > 0 }
                .groupBy { it.currency }
                .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }
        )
    }

    val visibleTemplates = if (selectedCategory == "הכול") {
        templates
    } else {
        templates.filter { it.category == selectedCategory }
    }

    val visibleCustomExpenses = if (selectedCategory == "הכול") {
        customExpenses
    } else {
        customExpenses.filter { it.category == selectedCategory }
    }

    val allEnteredExpenses = trip.expenses.filter { it.amount > 0 }
    val globalTotals = allEnteredExpenses
        .groupBy { it.currency }
        .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }

    val completedTemplates = templates.count { template ->
        trip.expenses.any { it.id == template.id && it.amount > 0 }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            GradientHeader(
                title = "תקציב",
                subtitle = "ניהול הוצאות לפי קטגוריות",
                emoji = "💰",
                start = Sun,
                end = Color(0xFFE79A18)
            )
        }

        item {
            BudgetOverviewCard(
                completedTemplates = completedTemplates,
                totalTemplates = templates.size,
                totals = globalTotals
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AccentButton(
                    text = "הוצאה",
                    emoji = "＋",
                    onClick = { addCustomExpense = true },
                    color = Color(0xFFE7A62D),
                    modifier = Modifier.weight(1f)
                )

                SoftActionButton(
                    text = "קטגוריה",
                    emoji = "＋",
                    onClick = { addCategory = true },
                    container = SoftSun,
                    contentColor = Color(0xFF8F6500),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text(
                "קטגוריות",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(end = 4.dp)
            ) {
                item {
                    BudgetCategoryCard(
                        summary = BudgetCategorySummary(
                            category = "הכול",
                            enteredCount = completedTemplates,
                            totalCount = templates.size,
                            totals = globalTotals
                        ),
                        selected = selectedCategory == "הכול",
                        onClick = { selectedCategory = "הכול" }
                    )
                }

                items(categorySummaries, key = { it.category }) { summary ->
                    BudgetCategoryCard(
                        summary = summary,
                        selected = selectedCategory == summary.category,
                        onClick = { selectedCategory = summary.category }
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (selectedCategory == "הכול") {
                            "כל סעיפי התקציב"
                        } else {
                            selectedCategory
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${visibleTemplates.size} סעיפים אוטומטיים",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                if (selectedCategory != "הכול") {
                    TextButton(onClick = { selectedCategory = "הכול" }) {
                        Text("הצג הכול")
                    }
                }
            }
        }

        items(visibleTemplates, key = { it.id }) { template ->
            val savedExpense = trip.expenses.firstOrNull { it.id == template.id }

            ProfessionalBudgetItemCard(
                template = template,
                expense = savedExpense,
                onEnterAmount = { editingTemplate = template },
                onClear = {
                    onTripChange(
                        trip.copy(
                            expenses = trip.expenses.filterNot {
                                it.id == template.id
                            }
                        )
                    )
                }
            )
        }

        if (visibleCustomExpenses.isNotEmpty()) {
            item {
                Text(
                    "הוצאות נוספות",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(visibleCustomExpenses, key = { it.id }) { expense ->
                CustomExpenseCard(
                    expense = expense,
                    onEdit = { editingCustomExpense = expense },
                    onDelete = {
                        onTripChange(
                            trip.copy(
                                expenses = trip.expenses.filterNot {
                                    it.id == expense.id
                                }
                            )
                        )
                    }
                )
            }
        }

        if (visibleTemplates.isEmpty() && visibleCustomExpenses.isEmpty()) {
            item {
                SectionCard(containerColor = CardWhite) {
                    Text(
                        "אין עדיין סעיפים בקטגוריה הזו",
                        color = TextSecondary
                    )
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
                        expenses = trip.expenses.filterNot {
                            it.id == template.id
                        } + updated
                    )
                )
                editingTemplate = null
            }
        )
    }

    if (addCustomExpense) {
        CustomExpenseDialog(
            categories = categories,
            defaultCategory = pendingCategory,
            defaultCurrency = destinationCurrency(trip.destination),
            defaultDate = trip.startDate,
            existing = null,
            onDismiss = {
                addCustomExpense = false
                pendingCategory = null
            },
            onConfirm = { expense ->
                onTripChange(
                    trip.copy(expenses = trip.expenses + expense)
                )
                addCustomExpense = false
                pendingCategory = null
                selectedCategory = expense.category
            }
        )
    }

    editingCustomExpense?.let { expense ->
        CustomExpenseDialog(
            categories = categories,
            defaultCategory = expense.category,
            defaultCurrency = expense.currency,
            defaultDate = expense.date,
            existing = expense,
            onDismiss = { editingCustomExpense = null },
            onConfirm = { updated ->
                onTripChange(
                    trip.copy(
                        expenses = trip.expenses.map {
                            if (it.id == updated.id) updated else it
                        }
                    )
                )
                editingCustomExpense = null
            }
        )
    }

    if (addCategory) {
        AddBudgetCategoryDialog(
            existing = categories,
            onDismiss = { addCategory = false },
            onConfirm = { category ->
                pendingCategory = category
                selectedCategory = category
                addCategory = false
                addCustomExpense = true
            }
        )
    }
}

data class BudgetCategorySummary(
    val category: String,
    val enteredCount: Int,
    val totalCount: Int,
    val totals: Map<String, Double>
)

@Composable
private fun BudgetOverviewCard(
    completedTemplates: Int,
    totalTemplates: Int,
    totals: Map<String, Double>
) {
    val progress = if (totalTemplates == 0) {
        0f
    } else {
        completedTemplates.toFloat() / totalTemplates.toFloat()
    }

    SectionCard(containerColor = SoftSun) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "תמונת מצב",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
                Text(
                    "$completedTemplates מתוך $totalTemplates סעיפים הוזנו",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(CardWhite),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${(progress * 100).toInt()}%",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF9A6600)
                )
            }
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFE7A62D),
            trackColor = CardWhite
        )

        if (totals.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardWhite
            ) {
                Text(
                    "עדיין לא הוזנו סכומים",
                    modifier = Modifier.padding(12.dp),
                    color = TextSecondary
                )
            }
        } else {
            totals.forEach { (currency, amount) ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardWhite
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(currency, color = TextSecondary)
                        Text(
                            formatBudgetAmount(amount),
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetCategoryCard(
    summary: BudgetCategorySummary,
    selected: Boolean,
    onClick: () -> Unit
) {
    val categoryColor = budgetCategoryColor(summary.category)

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(156.dp)
            .height(132.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                categoryColor.copy(alpha = .16f)
            } else {
                CardWhite
            }
        ),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) categoryColor else Color(0xFFE3E9F0)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 5.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(13.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(categoryColor.copy(alpha = .15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(budgetCategoryEmoji(summary.category))
                }

                if (selected) {
                    Text(
                        "✓",
                        color = categoryColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column {
                Text(
                    summary.category,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )

                Text(
                    budgetTotalsText(summary.totals),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 2
                )

                if (summary.totalCount > 0) {
                    Text(
                        "${summary.enteredCount}/${summary.totalCount} סעיפים",
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfessionalBudgetItemCard(
    template: BudgetTemplate,
    expense: Expense?,
    onEnterAmount: () -> Unit,
    onClear: () -> Unit
) {
    val hasAmount = expense != null && expense.amount > 0
    val categoryColor = budgetCategoryColor(template.category)

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
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(categoryColor.copy(alpha = .14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(budgetCategoryEmoji(template.category))
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        template.title,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    Text(
                        "${template.category} · ${template.date}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                if (hasAmount) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${formatBudgetAmount(expense!!.amount)} ${expense.currency}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D56)
                        )
                        Text(
                            "הוזן",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D56)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = SoftSun
                    ) {
                        Text(
                            "טרם הוזן",
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 5.dp
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8F6500)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFE8EDF3))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (hasAmount) {
                    TextButton(onClick = onEnterAmount) {
                        Text("עריכת סכום")
                    }

                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(36.dp)
                    ) {
                        SmallDeleteIcon(Modifier.size(28.dp))
                    }
                } else {
                    FilledTonalButton(
                        onClick = onEnterAmount,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = categoryColor.copy(alpha = .14f),
                            contentColor = categoryColor
                        )
                    ) {
                        Text("הזן סכום")
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomExpenseCard(
    expense: Expense,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryColor = budgetCategoryColor(expense.category)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, Color(0xFFE3E9F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = .14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(budgetCategoryEmoji(expense.category))
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(expense.title, fontWeight = FontWeight.Bold)
                Text(
                    "${expense.category} · ${expense.date}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${formatBudgetAmount(expense.amount)} ${expense.currency}",
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )

                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(34.dp)
                    ) {
                        SmallEditIcon(Modifier.size(27.dp))
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(34.dp)
                    ) {
                        SmallDeleteIcon(Modifier.size(27.dp))
                    }
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
        mutableStateOf(
            existing?.amount
                ?.takeIf { it > 0 }
                ?.toString()
                .orEmpty()
        )
    }

    var currency by remember(template.id) {
        mutableStateOf(existing?.currency ?: template.currency)
    }

    var currencyMenuOpen by remember { mutableStateOf(false) }

    val currencies = listOf(
        template.currency,
        existing?.currency.orEmpty(),
        "EUR",
        "USD",
        "ILS",
        "HUF",
        "GBP"
    )
        .filter { it.isNotBlank() }
        .distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = template.title,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = template.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { value ->
                        amountText = value.filter { char ->
                            char.isDigit() || char == '.'
                        }
                    },
                    label = { Text("סכום") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { currencyMenuOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "מטבע: $currency",
                            modifier = Modifier.weight(1f)
                        )
                        Text("⌄")
                    }

                    DropdownMenu(
                        expanded = currencyMenuOpen,
                        onDismissRequest = {
                            currencyMenuOpen = false
                        }
                    ) {
                        currencies.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    currency = option
                                    currencyMenuOpen = false
                                }
                            )
                        }
                    }
                }

                Text(
                    text = "תאריך: ${template.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0,
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    onConfirm(amount, currency)
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

@Composable
private fun CustomExpenseDialog(
    categories: List<String>,
    defaultCategory: String?,
    defaultCurrency: String,
    defaultDate: String,
    existing: Expense?,
    onDismiss: () -> Unit,
    onConfirm: (Expense) -> Unit
) {
    val cleanCategories = categories
        .filter { it.isNotBlank() }
        .distinct()

    var title by remember(existing?.id) {
        mutableStateOf(existing?.title.orEmpty())
    }
    var amountText by remember(existing?.id) {
        mutableStateOf(
            existing?.amount
                ?.takeIf { it > 0 }
                ?.toString()
                .orEmpty()
        )
    }
    var category by remember(existing?.id, defaultCategory) {
        mutableStateOf(
            existing?.category
                ?: defaultCategory
                ?: cleanCategories.firstOrNull()
                ?: "כללי"
        )
    }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var currency by remember(existing?.id) {
        mutableStateOf(existing?.currency ?: defaultCurrency)
    }
    var currencyMenuOpen by remember { mutableStateOf(false) }
    var date by remember(existing?.id) {
        mutableStateOf(existing?.date ?: defaultDate)
    }

    val currencies = listOf(
        defaultCurrency,
        existing?.currency.orEmpty(),
        "EUR",
        "USD",
        "ILS",
        "HUF",
        "GBP"
    )
        .filter { it.isNotBlank() }
        .distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null) {
                    "הוצאה חדשה"
                } else {
                    "עריכת הוצאה"
                }
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("תיאור ההוצאה") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = it.filter { char ->
                                char.isDigit() || char == '.'
                            }
                        },
                        label = { Text("סכום") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { categoryMenuOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "קטגוריה: $category",
                                modifier = Modifier.weight(1f)
                            )
                            Text("⌄")
                        }

                        DropdownMenu(
                            expanded = categoryMenuOpen,
                            onDismissRequest = {
                                categoryMenuOpen = false
                            }
                        ) {
                            cleanCategories.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        category = option
                                        categoryMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { currencyMenuOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "מטבע: $currency",
                                modifier = Modifier.weight(1f)
                            )
                            Text("⌄")
                        }

                        DropdownMenu(
                            expanded = currencyMenuOpen,
                            onDismissRequest = {
                                currencyMenuOpen = false
                            }
                        ) {
                            currencies.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        currency = option
                                        currencyMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("תאריך") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() &&
                    (amountText.toDoubleOrNull() ?: 0.0) > 0,
                onClick = {
                    onConfirm(
                        Expense(
                            id = existing?.id
                                ?: UUID.randomUUID().toString(),
                            title = title.trim(),
                            amount = amountText.toDoubleOrNull() ?: 0.0,
                            currency = currency,
                            category = category,
                            date = date.ifBlank { defaultDate }
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

@Composable
private fun AddBudgetCategoryDialog(
    existing: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val duplicate = existing.any {
        it.equals(name.trim(), ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("קטגוריית תקציב חדשה") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("שם הקטגוריה") },
                isError = duplicate,
                supportingText = {
                    if (duplicate) {
                        Text("הקטגוריה כבר קיימת")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !duplicate,
                onClick = { onConfirm(name.trim()) }
            ) {
                Text("הוספה")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}

private fun budgetCategoryEmoji(category: String): String = when (category) {
    "טיסות" -> "✈️"
    "מלונות" -> "🏨"
    "תחבורה" -> "🚌"
    "אטרקציות" -> "🎫"
    "אוכל" -> "🍽️"
    "קניות" -> "🛍️"
    "כללי" -> "💳"
    "הכול" -> "📊"
    else -> "💰"
}

private fun budgetCategoryColor(category: String): Color = when (category) {
    "טיסות" -> Color(0xFF4F8FD8)
    "מלונות" -> Color(0xFF20AFC4)
    "תחבורה" -> Color(0xFF7C69D9)
    "אטרקציות" -> Color(0xFFFF7A66)
    "אוכל" -> Color(0xFFE7A62D)
    "קניות" -> Color(0xFFE46B9A)
    "כללי" -> Color(0xFF64748B)
    "הכול" -> Navy
    else -> Color(0xFF5C7AEA)
}

private fun budgetTotalsText(totals: Map<String, Double>): String =
    if (totals.isEmpty()) {
        "טרם הוזן"
    } else {
        totals.entries.joinToString(" · ") { entry ->
            "${formatBudgetAmount(entry.value)} ${entry.key}"
        }
    }

private fun formatBudgetAmount(amount: Double): String =
    if (amount % 1.0 == 0.0) {
        amount.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", amount)
    }

@Composable
private fun DocumentsScreen(
    trip: Trip,
    onTripChange: (Trip) -> Unit,
    modifier: Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingRequirement by remember { mutableStateOf<DocumentRequirement?>(null) }
    var pendingPassengerName by remember { mutableStateOf("") }
    var askPassengerName by remember { mutableStateOf(false) }

    val requirements = suggestedDocumentRequirements(trip)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val requirement = pendingRequirement

        if (uri != null && requirement != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            val baseName = uri.lastPathSegment ?: requirement.title
            val finalName = if (
                requirement.type == "טיסות" &&
                pendingPassengerName.isNotBlank()
            ) {
                "$pendingPassengerName - $baseName"
            } else {
                baseName
            }

            val document = TripDocument(
                id = UUID.randomUUID().toString(),
                name = finalName,
                uri = uri.toString(),
                type = requirement.type,
                notes = requirement.key,
                passengerName = pendingPassengerName.trim()
            )

            onTripChange(
                trip.copy(
                    documents = trip.documents + document
                )
            )
        }

        pendingRequirement = null
        pendingPassengerName = ""
    }

    fun startDocumentUpload(requirement: DocumentRequirement) {
        pendingRequirement = requirement

        if (requirement.type == "טיסות") {
            askPassengerName = true
        } else {
            pendingPassengerName = ""
            launcher.launch(arrayOf("*/*"))
        }
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
                        document.name.contains(
                            requirement.title,
                            ignoreCase = true
                        )
                }
            }

            SectionCard(containerColor = SoftMint) {
                Text(
                    "$completed מתוך ${requirements.size} סוגי מסמכים נוספו",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF276B4A)
                )
                LinearProgressIndicator(
                    progress = {
                        if (requirements.isEmpty()) {
                            0f
                        } else {
                            completed.toFloat() / requirements.size.toFloat()
                        }
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
                    it.name.contains(
                        requirement.title,
                        ignoreCase = true
                    )
            }

            val isAdded = matching.isNotEmpty()
            val isFlight = requirement.type == "טיסות"

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAdded) SoftMint else CardWhite
                ),
                border = BorderStroke(
                    1.dp,
                    if (isAdded) {
                        Color(0xFFBEE6CF)
                    } else {
                        Color(0xFFE3E9F0)
                    }
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
                            Text(
                                requirement.title,
                                fontWeight = FontWeight.Bold
                            )
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

                            if (isFlight) {
                                Text(
                                    "אפשר להוסיף מסמך נפרד לכל נוסע",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Sky
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                startDocumentUpload(requirement)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isAdded) {
                                    CardWhite
                                } else {
                                    SoftMint
                                },
                                contentColor = Color(0xFF2E7D56)
                            )
                        ) {
                            Text(
                                when {
                                    isFlight && isAdded -> "נוסע נוסף"
                                    isAdded -> "הוסף עוד"
                                    else -> "הוספה"
                                }
                            )
                        }
                    }

                    if (matching.isNotEmpty()) {
                        matching.forEach { document ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CardWhite,
                                border = BorderStroke(
                                    1.dp,
                                    Color(0xFFE5EAF0)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isFlight) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(SoftBlue),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("✈️")
                                        }
                                        Spacer(Modifier.width(8.dp))
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        if (
                                            isFlight &&
                                            document.passengerName.isNotBlank()
                                        ) {
                                            Text(
                                                document.passengerName,
                                                fontWeight = FontWeight.Bold,
                                                color = Navy
                                            )
                                        }

                                        Text(
                                            document.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            maxLines = 1
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse(document.uri)
                                                    ).addFlags(
                                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                    )
                                                )
                                            }
                                        }
                                    ) {
                                        Text("פתיחה")
                                    }

                                    IconButton(
                                        onClick = {
                                            onTripChange(
                                                trip.copy(
                                                    documents = trip.documents
                                                        .filterNot {
                                                            it.id == document.id
                                                        }
                                                )
                                            )
                                        }
                                    ) {
                                        SmallDeleteIcon(
                                            Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            val generalRequirement = DocumentRequirement(
                key = "general-document",
                title = "מסמך כללי",
                type = "כללי",
                description = "קובץ נוסף שאינו משויך להזמנה"
            )

            AccentButton(
                text = "הוספת מסמך כללי",
                emoji = "＋",
                onClick = {
                    startDocumentUpload(generalRequirement)
                },
                color = Mint,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (askPassengerName) {
        PassengerDocumentDialog(
            onDismiss = {
                askPassengerName = false
                pendingRequirement = null
                pendingPassengerName = ""
            },
            onConfirm = { passengerName ->
                pendingPassengerName = passengerName
                askPassengerName = false
                launcher.launch(arrayOf("*/*"))
            }
        )
    }
}

@Composable
private fun PassengerDocumentDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var passengerName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("מסמך טיסה לנוסע")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "הזן את שם הנוסע לפני בחירת הקובץ.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = passengerName,
                    onValueChange = { passengerName = it },
                    label = { Text("שם הנוסע") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = passengerName.isNotBlank(),
                onClick = {
                    onConfirm(passengerName.trim())
                }
            ) {
                Text("בחירת מסמך")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
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
