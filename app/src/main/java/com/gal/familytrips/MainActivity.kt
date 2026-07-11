
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
                    Triple(Icons.Default.Flight, "טיסות", 1),
                    Triple(Icons.Default.Hotel, "מלונות", 2),
                    Triple(Icons.Default.Today, "ימים", 3),
                    Triple(Icons.Default.Restaurant, "מסעדות", 4),
                    Triple(Icons.Default.AttachMoney, "תקציב", 5),
                    Triple(Icons.Default.Description, "מסמכים", 6),
                    Triple(Icons.Default.Info, "מידע", 7),
                    Triple(Icons.Default.Luggage, "ציוד", 8)
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

                1 -> FlightsScreen(
                    trip = trip,
                    onTripChange = {
                        onStateChange(state.replaceTrip(it))
                    },
                    modifier = Modifier.padding(padding)
                )

                2 -> HotelsScreen(
                    trip,
                    { onStateChange(state.replaceTrip(it)) },
                    onOpenUrl,
                    Modifier.padding(padding)
                )

                3 -> if (selectedDayId == null) {
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

                4 -> RestaurantsScreen(
                    trip,
                    { onStateChange(state.replaceTrip(it)) },
                    onOpenUrl,
                    Modifier.padding(padding)
                )

                5 -> ExpensesScreen(
                    trip,
                    { onStateChange(state.replaceTrip(it)) },
                    Modifier.padding(padding)
                )

                6 -> DocumentsScreen(
                    trip,
                    { onStateChange(state.replaceTrip(it)) },
                    Modifier.padding(padding)
                )

                7 -> GeneralInfoScreen(
                    trip = trip,
                    onTripChange = {
                        onStateChange(state.replaceTrip(it))
                    },
                    modifier = Modifier.padding(padding)
                )

                8 -> PackingScreen(
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
            existingTrip = null,
            onDismiss = { showAddTrip = false },
            onConfirm = { name, stays ->
                val normalizedStays = stays.sortedBy { it.startDate }
                val generatedDays = buildDaysFromDestinationStays(
                    stays = normalizedStays,
                    existingDays = emptyList()
                )
                val destinations = normalizedStays
                    .map { it.destination }
                    .distinct()

                val newTrip = Trip(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    destination = destinations.joinToString(" • "),
                    destinationStops = destinations,
                    startDate = normalizedStays.first().startDate,
                    endDate = normalizedStays.last().endDate,
                    days = generatedDays,
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
                    offlineMode = false,
                    destinationStays = normalizedStays
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
    DestinationOption("אבו דאבי", "איחוד האמירויות"),
    DestinationOption("אדיס אבבה", "אתיופיה"),
    DestinationOption("אורלנדו", "ארצות הברית"),
    DestinationOption("איביזה", "ספרד"),
    DestinationOption("איסטנבול", "טורקיה"),
    DestinationOption("אמסטרדם", "הולנד"),
    DestinationOption("אתונה", "יוון"),
    DestinationOption("אטלנטה", "ארצות הברית"),
    DestinationOption("באקו", "אזרבייג'ן"),
    DestinationOption("בזל", "שווייץ"),
    DestinationOption("באטומי", "גאורגיה"),
    DestinationOption("בנגקוק", "תאילנד"),
    DestinationOption("ברטיסלבה", "סלובקיה"),
    DestinationOption("בריסל", "בלגיה"),
    DestinationOption("ברלין", "גרמניה"),
    DestinationOption("בודפשט", "הונגריה"),
    DestinationOption("בוסטון", "ארצות הברית"),
    DestinationOption("בוקרשט", "רומניה"),
    DestinationOption("בלגרד", "סרביה"),
    DestinationOption("בייג'ינג", "סין"),
    DestinationOption("ברצלונה", "ספרד"),
    DestinationOption("ג'נבה", "שווייץ"),
    DestinationOption("דובאי", "איחוד האמירויות"),
    DestinationOption("דוברובניק", "קרואטיה"),
    DestinationOption("דלהי", "הודו"),
    DestinationOption("דיסלדורף", "גרמניה"),
    DestinationOption("הרקליון", "יוון"),
    DestinationOption("וינה", "אוסטריה"),
    DestinationOption("וילנה", "ליטא"),
    DestinationOption("ונציה", "איטליה"),
    DestinationOption("ורונה", "איטליה"),
    DestinationOption("ורשה", "פולין"),
    DestinationOption("זאגרב", "קרואטיה"),
    DestinationOption("זנזיבר", "טנזניה"),
    DestinationOption("טביליסי", "גאורגיה"),
    DestinationOption("טוקיו", "יפן"),
    DestinationOption("טורונטו", "קנדה"),
    DestinationOption("טיווט", "מונטנגרו"),
    DestinationOption("טשקנט", "אוזבקיסטן"),
    DestinationOption("יוהנסבורג", "דרום אפריקה"),
    DestinationOption("לונדון", "בריטניה"),
    DestinationOption("לוס אנג'לס", "ארצות הברית"),
    DestinationOption("לובליאנה", "סלובניה"),
    DestinationOption("ליסבון", "פורטוגל"),
    DestinationOption("לרנקה", "קפריסין"),
    DestinationOption("ליון", "צרפת"),
    DestinationOption("מדריד", "ספרד"),
    DestinationOption("מיאמי", "ארצות הברית"),
    DestinationOption("מילאנו", "איטליה"),
    DestinationOption("מינכן", "גרמניה"),
    DestinationOption("מינסק", "בלארוס"),
    DestinationOption("מיקונוס", "יוון"),
    DestinationOption("מלאגה", "ספרד"),
    DestinationOption("מרסיי", "צרפת"),
    DestinationOption("מוסקבה", "רוסיה"),
    DestinationOption("מונטריאול", "קנדה"),
    DestinationOption("ניו יורק", "ארצות הברית"),
    DestinationOption("ניס", "צרפת"),
    DestinationOption("נאפולי", "איטליה"),
    DestinationOption("סופיה", "בולגריה"),
    DestinationOption("סוצ'י", "רוסיה"),
    DestinationOption("סיאול", "דרום קוריאה"),
    DestinationOption("סלוניקי", "יוון"),
    DestinationOption("סן פרנסיסקו", "ארצות הברית"),
    DestinationOption("פאפוס", "קפריסין"),
    DestinationOption("פאריס", "צרפת"),
    DestinationOption("פראג", "צ'כיה"),
    DestinationOption("פורטו", "פורטוגל"),
    DestinationOption("פוקט", "תאילנד"),
    DestinationOption("פלמה דה מיורקה", "ספרד"),
    DestinationOption("פרנקפורט", "גרמניה"),
    DestinationOption("ציריך", "שווייץ"),
    DestinationOption("קאהיר", "מצרים"),
    DestinationOption("קופנהגן", "דנמרק"),
    DestinationOption("קלן", "גרמניה"),
    DestinationOption("קישינב", "מולדובה"),
    DestinationOption("קרקוב", "פולין"),
    DestinationOption("רודוס", "יוון"),
    DestinationOption("ריגה", "לטביה"),
    DestinationOption("רומא", "איטליה"),
    DestinationOption("שנג'ן", "סין"),
    DestinationOption("שטוקהולם", "שוודיה"),
    DestinationOption("שיקגו", "ארצות הברית"),
    DestinationOption("שרם א-שייח'", "מצרים")
).sortedWith(
    compareBy<DestinationOption> { it.country }.thenBy { it.city }
)

@Composable
private fun NewTripDialog(
    existingTrip: Trip?,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        stays: List<DestinationStay>
    ) -> Unit
) {
    var tripName by remember(existingTrip?.id) {
        mutableStateOf(existingTrip?.name.orEmpty())
    }
    var destinationMenuOpen by remember { mutableStateOf(false) }
    var destinationSearch by remember { mutableStateOf("") }
    var customDestination by remember { mutableStateOf("") }

    val initialStays = remember(existingTrip?.id) {
        when {
            existingTrip == null -> emptyList()
            existingTrip.destinationStays.isNotEmpty() ->
                existingTrip.destinationStays
            else -> listOf(
                DestinationStay(
                    id = UUID.randomUUID().toString(),
                    destination = existingTrip.destinationStops
                        .firstOrNull()
                        ?: existingTrip.destination,
                    startDate = existingTrip.startDate,
                    endDate = existingTrip.endDate
                )
            )
        }
    }

    val stays = remember(existingTrip?.id) {
        mutableStateListOf<DestinationStay>().apply {
            addAll(initialStays)
        }
    }

    val filteredDestinations = remember(destinationSearch) {
        val query = destinationSearch.trim().lowercase()
        if (query.isBlank()) {
            majorDestinations
        } else {
            majorDestinations.filter {
                it.city.lowercase().contains(query) ||
                    it.country.lowercase().contains(query) ||
                    it.displayName.lowercase().contains(query)
            }
        }
    }

    val scheduleError = validateDestinationStays(stays)
    val valid = tripName.isNotBlank() &&
        stays.isNotEmpty() &&
        scheduleError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existingTrip == null) {
                    "טיול חדש"
                } else {
                    "עריכת פרטי הטיול"
                }
            )
        },
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
                    Text(
                        "יעדים ותאריכים",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "יום הסיום של יעד הוא גם יום ההגעה ליעד הבא",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                item {
                    OutlinedTextField(
                        value = destinationSearch,
                        onValueChange = { destinationSearch = it },
                        label = { Text("חיפוש עיר או מדינה") },
                        leadingIcon = { Text("🔎") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { destinationMenuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "הוספת יעד מהרשימה",
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
                            filteredDestinations
                                .take(80)
                                .forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(option.city)
                                                Text(
                                                    option.country,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextSecondary
                                                )
                                            }
                                        },
                                        onClick = {
                                            val suggestedStart = suggestedNextStartDate(stays)
                                            stays.add(
                                                DestinationStay(
                                                    id = UUID.randomUUID().toString(),
                                                    destination = option.displayName,
                                                    startDate = suggestedStart,
                                                    endDate = suggestedStart
                                                )
                                            )
                                            destinationMenuOpen = false
                                            destinationSearch = ""
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
                                Text("יעד אחר שאינו ברשימה")
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        FilledTonalButton(
                            enabled = customDestination.isNotBlank(),
                            onClick = {
                                val suggestedStart = suggestedNextStartDate(stays)
                                stays.add(
                                    DestinationStay(
                                        id = UUID.randomUUID().toString(),
                                        destination = customDestination.trim(),
                                        startDate = suggestedStart,
                                        endDate = suggestedStart
                                    )
                                )
                                customDestination = ""
                            }
                        ) {
                            Text("הוספה")
                        }
                    }
                }

                if (stays.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SoftSun
                        ) {
                            Text(
                                "יש להוסיף לפחות יעד אחד.",
                                modifier = Modifier.padding(11.dp),
                                color = Color(0xFF7D5B00)
                            )
                        }
                    }
                }

                items(
                    stays,
                    key = { it.id }
                ) { stay ->
                    DestinationStayEditorCard(
                        stay = stay,
                        onChange = { updated ->
                            val index = stays.indexOfFirst {
                                it.id == updated.id
                            }
                            if (index >= 0) {
                                stays[index] = updated
                            }
                        },
                        onDelete = {
                            stays.removeAll { it.id == stay.id }
                        }
                    )
                }

                scheduleError?.let { error ->
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFFFE5E1)
                        ) {
                            Text(
                                error,
                                modifier = Modifier.padding(11.dp),
                                color = Coral,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (stays.isNotEmpty() && scheduleError == null) {
                    item {
                        val dayCount = uniqueTripDayCount(stays)

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SoftMint
                        ) {
                            Column(
                                modifier = Modifier.padding(11.dp)
                            ) {
                                Text(
                                    "ייווצרו $dayCount ימים אוטומטית",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D56)
                                )
                                Text(
                                    "לכל יום יהיה יעד קבוע לפי טווח התאריכים.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(
                        tripName.trim(),
                        stays.sortedBy { it.startDate }
                    )
                }
            ) {
                Text(
                    if (existingTrip == null) {
                        "יצירת טיול"
                    } else {
                        "שמירת שינויים"
                    }
                )
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
private fun DestinationStayEditorCard(
    stay: DestinationStay,
    onChange: (DestinationStay) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, Color(0xFFE3E9F0))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📍")
                Spacer(Modifier.width(8.dp))
                Text(
                    stay.destination,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    SmallDeleteIcon(Modifier.size(27.dp))
                }
            }

            TripDatePickerField(
                label = "מהתאריך",
                value = stay.startDate,
                onValueChange = { selected ->
                    val end = if (
                        stay.endDate.isBlank() ||
                        runCatching {
                            LocalDate.parse(stay.endDate)
                                .isBefore(LocalDate.parse(selected))
                        }.getOrDefault(false)
                    ) {
                        selected
                    } else {
                        stay.endDate
                    }
                    onChange(
                        stay.copy(
                            startDate = selected,
                            endDate = end
                        )
                    )
                }
            )

            TripDatePickerField(
                label = "עד התאריך",
                value = stay.endDate,
                minimumDate = stay.startDate,
                onValueChange = {
                    onChange(stay.copy(endDate = it))
                }
            )
        }
    }
}

private fun suggestedNextStartDate(
    stays: List<DestinationStay>
): String {
    val latestEnd = stays
        .mapNotNull {
            runCatching {
                LocalDate.parse(it.endDate)
            }.getOrNull()
        }
        .maxOrNull()

    return (latestEnd ?: LocalDate.now()).toString()
}

private fun validateDestinationStays(
    stays: List<DestinationStay>
): String? {
    if (stays.isEmpty()) {
        return "יש להוסיף לפחות יעד אחד."
    }

    val parsed = stays.map { stay ->
        val start = runCatching {
            LocalDate.parse(stay.startDate)
        }.getOrNull()
            ?: return "יש לבחור תאריך התחלה לכל יעד."

        val end = runCatching {
            LocalDate.parse(stay.endDate)
        }.getOrNull()
            ?: return "יש לבחור תאריך סיום לכל יעד."

        if (end.isBefore(start)) {
            return "תאריך הסיום של ${stay.destination} מוקדם מתאריך ההתחלה."
        }

        Triple(stay, start, end)
    }.sortedBy { it.second }

    parsed.zipWithNext().forEach { (first, second) ->
        if (second.second != first.third) {
            return buildString {
                append("יום הסיום של ")
                append(first.first.destination)
                append(" חייב להיות גם יום ההגעה ל-")
                append(second.first.destination)
                append(". יש לבחור לשני היעדים את אותו תאריך מעבר.")
            }
        }
    }

    return null
}

private fun uniqueTripDayCount(
    stays: List<DestinationStay>
): Int {
    return stays
        .flatMap { stay ->
            val start = runCatching {
                LocalDate.parse(stay.startDate)
            }.getOrNull() ?: return@flatMap emptyList()

            val end = runCatching {
                LocalDate.parse(stay.endDate)
            }.getOrNull() ?: return@flatMap emptyList()

            buildList {
                var date = start
                while (!date.isAfter(end)) {
                    add(date.toString())
                    date = date.plusDays(1)
                }
            }
        }
        .distinct()
        .size
}

private fun inclusiveDayCount(
    startDate: String,
    endDate: String
): Int {
    val start = runCatching {
        LocalDate.parse(startDate)
    }.getOrNull() ?: return 0
    val end = runCatching {
        LocalDate.parse(endDate)
    }.getOrNull() ?: return 0

    return (end.toEpochDay() - start.toEpochDay() + 1)
        .toInt()
        .coerceAtLeast(0)
}

private fun buildDaysFromDestinationStays(
    stays: List<DestinationStay>,
    existingDays: List<TripDay>
): List<TripDay> {
    val existingByDate = existingDays.associateBy { it.date }
    val destinationsByDate = linkedMapOf<String, MutableList<String>>()

    stays.sortedBy { it.startDate }.forEach { stay ->
        val start = LocalDate.parse(stay.startDate)
        val end = LocalDate.parse(stay.endDate)
        var date = start

        while (!date.isAfter(end)) {
            val dateText = date.toString()
            val destinations = destinationsByDate
                .getOrPut(dateText) { mutableListOf() }

            if (stay.destination !in destinations) {
                destinations.add(stay.destination)
            }

            date = date.plusDays(1)
        }
    }

    return destinationsByDate.map { (dateText, destinations) ->
        val existing = existingByDate[dateText]
        val displayDestination = destinations.joinToString(" → ")
        val cityNames = destinations.map {
            it.substringBefore(",").trim()
        }

        val defaultTitle = if (destinations.size > 1) {
            "מעבר מ-${cityNames.first()} ל-${cityNames.last()}"
        } else {
            "יום ב-${cityNames.first()}"
        }

        if (existing != null) {
            existing.copy(
                date = dateText,
                destination = displayDestination,
                title = when {
                    existing.title.isBlank() -> defaultTitle
                    existing.destination != displayDestination &&
                        existing.title.startsWith("יום ב") -> defaultTitle
                    else -> existing.title
                }
            )
        } else {
            TripDay(
                id = UUID.randomUUID().toString(),
                date = dateText,
                title = defaultTitle,
                imageKey = if (destinations.size > 1) {
                    "flight"
                } else {
                    destinationImageKey(destinations.first())
                },
                activities = emptyList(),
                destination = displayDestination
            )
        }
    }.sortedBy { it.date }
}

private fun destinationImageKey(destination: String): String {
    val value = destination.lowercase()
    return when {
        "אי " in value ||
            "פוקט" in value ||
            "רודוס" in value ||
            "מיקונוס" in value ||
            "איביזה" in value ||
            "זנזיבר" in value -> "island"
        "דובאי" in value ||
            "ניו יורק" in value ||
            "לונדון" in value ||
            "פריז" in value -> "city"
        else -> "city"
    }
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
                    onValueChange(
                        LocalDate.of(
                            year,
                            month + 1,
                            day
                        ).toString()
                    )
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
    var editingTrip by remember { mutableStateOf<Trip?>(null) }

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
                        text = "עריכה",
                        emoji = "✏️",
                        onClick = { editingTrip = trip },
                        container = SoftSun,
                        contentColor = Color(0xFF8F6500),
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

    editingTrip?.let { tripToEdit ->
        NewTripDialog(
            existingTrip = tripToEdit,
            onDismiss = { editingTrip = null },
            onConfirm = { name, stays ->
                val normalizedStays = stays.sortedBy { it.startDate }
                val generatedDays = buildDaysFromDestinationStays(
                    stays = normalizedStays,
                    existingDays = tripToEdit.days
                )
                val validDayIds = generatedDays.map { it.id }.toSet()
                val destinations = normalizedStays
                    .map { it.destination }
                    .distinct()

                val updatedTrip = tripToEdit.copy(
                    name = name,
                    destination = destinations.joinToString(" • "),
                    destinationStops = destinations,
                    destinationStays = normalizedStays,
                    startDate = normalizedStays.first().startDate,
                    endDate = normalizedStays.last().endDate,
                    days = generatedDays,
                    restaurants = tripToEdit.restaurants.filter {
                        it.dayId == null || it.dayId in validDayIds
                    }
                )

                onStateChange(state.replaceTrip(updatedTrip))
                editingTrip = null
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

        SectionCard(containerColor = SoftBlue) {
            Text(
                "הימים נוצרים אוטומטית לפי יעדי הטיול והתאריכים.",
                fontWeight = FontWeight.Bold,
                color = Navy
            )
            Text(
                "כדי לשנות תאריכים או להוסיף יעד, ערוך את פרטי הטיול במסך הטיולים.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

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
                            if (day.destination.isNotBlank()) {
                                Text(
                                    text = "📍 ${day.destination}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Aqua,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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

                        }
                    }
                }
            }
        }
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
    var title by remember(day.id) { mutableStateOf(day.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("עריכת יום") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "📍 ${day.destination.ifBlank { "יעד לא משויך" }}",
                    color = Aqua,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    day.date,
                    color = TextSecondary
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
                onClick = { onConfirm(day.copy(title = title)) }
            ) {
                Text("שמירה")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ביטול") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
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
    var showLiveMap by remember { mutableStateOf(false) }

    val orderedActivities = remember(day.id) {
        mutableStateListOf<ActivityItem>().apply {
            addAll(day.activities)
        }
    }
    var draggingActivityId by remember(day.id) {
        mutableStateOf<String?>(null)
    }
    var dragOffsetY by remember(day.id) {
        mutableStateOf(0f)
    }

    LaunchedEffect(day.activities, draggingActivityId) {
        if (draggingActivityId == null) {
            val incomingIds = day.activities.map { it.id }
            val localIds = orderedActivities.map { it.id }
            if (incomingIds != localIds || day.activities != orderedActivities.toList()) {
                orderedActivities.clear()
                orderedActivities.addAll(day.activities)
            }
        }
    }

    fun saveReorderedActivities() {
        val recalculated = recalculateActivityTimes(
            orderedActivities.toList()
        )
        orderedActivities.clear()
        orderedActivities.addAll(recalculated)

        val updatedDay = day.copy(
            activities = recalculated
        )
        onTripChange(
            trip.copy(
                days = trip.days.map {
                    if (it.id == day.id) updatedDay else it
                }
            )
        )
    }

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
                if (day.destination.isNotBlank()) {
                    Text(
                        "📍 ${day.destination}",
                        color = Aqua,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
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
                text = "מפת LIVE",
                emoji = "🛰️",
                onClick = { showLiveMap = true },
                container = SoftMint,
                contentColor = Color(0xFF2E7D56),
                modifier = Modifier.weight(1f)
            )

            SoftActionButton(
                text = "מפה יומית",
                emoji = "🗺️",
                onClick = {
                    val points = orderedActivities
                        .mapNotNull {
                            it.location.ifBlank { it.name }
                                .takeIf(String::isNotBlank)
                        }

                    if (points.isNotEmpty()) {
                        val origin = points.first()
                        val destination = points.last()
                        val waypoints = points
                            .drop(1)
                            .dropLast(1)
                            .take(8)
                            .joinToString("|")

                        var url = "https://www.google.com/maps/dir/?api=1" +
                            "&origin=${Uri.encode(origin)}" +
                            "&destination=${Uri.encode(destination)}" +
                            "&travelmode=transit"

                        if (waypoints.isNotBlank()) {
                            url += "&waypoints=${Uri.encode(waypoints)}"
                        }
                        onOpenUrl(url)
                    }
                },
                container = SoftBlue,
                contentColor = Sky,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(
                items = orderedActivities,
                key = { it.id }
            ) { activity ->
                val isDragging = draggingActivityId == activity.id
                val animatedElevationOffset by animateFloatAsState(
                    targetValue = if (isDragging) 1f else 0f,
                    label = "dragElevation"
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItemPlacement()
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffsetY else 0f
                            scaleX = 1f + animatedElevationOffset * 0.015f
                            scaleY = 1f + animatedElevationOffset * 0.015f
                            shadowElevation = animatedElevationOffset * 18f
                            alpha = if (isDragging) 0.96f else 1f
                        },
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
                            ActivityDragHandle(
                                activityId = activity.id,
                                isDragging = isDragging,
                                onDragStart = {
                                    draggingActivityId = activity.id
                                    dragOffsetY = 0f
                                },
                                onDrag = { deltaY ->
                                    if (draggingActivityId != activity.id) {
                                        return@ActivityDragHandle
                                    }

                                    dragOffsetY += deltaY

                                    val currentIndex = orderedActivities
                                        .indexOfFirst { it.id == activity.id }

                                    if (currentIndex < 0) {
                                        return@ActivityDragHandle
                                    }

                                    val itemStepPx = 92f

                                    while (
                                        dragOffsetY > itemStepPx &&
                                        currentIndex < orderedActivities.lastIndex
                                    ) {
                                        val liveIndex = orderedActivities
                                            .indexOfFirst { it.id == activity.id }
                                        if (liveIndex >= orderedActivities.lastIndex) break

                                        val moved = orderedActivities.removeAt(liveIndex)
                                        orderedActivities.add(liveIndex + 1, moved)
                                        dragOffsetY -= itemStepPx
                                    }

                                    while (
                                        dragOffsetY < -itemStepPx &&
                                        orderedActivities.indexOfFirst {
                                            it.id == activity.id
                                        } > 0
                                    ) {
                                        val liveIndex = orderedActivities
                                            .indexOfFirst { it.id == activity.id }
                                        if (liveIndex <= 0) break

                                        val moved = orderedActivities.removeAt(liveIndex)
                                        orderedActivities.add(liveIndex - 1, moved)
                                        dragOffsetY += itemStepPx
                                    }
                                },
                                onDragEnd = {
                                    draggingActivityId = null
                                    dragOffsetY = 0f
                                    saveReorderedActivities()
                                }
                            )

                            Spacer(Modifier.width(8.dp))

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

    if (showLiveMap) {
        LiveMapDialog(
            day = day.copy(
                activities = orderedActivities.toList()
            ),
            onDismiss = { showLiveMap = false },
            onOpenUrl = onOpenUrl,
            onCompleteActivity = { activityId ->
                val updatedDay = day.copy(
                    activities = day.activities.map {
                        if (it.id == activityId) {
                            it.copy(completed = true)
                        } else {
                            it
                        }
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

    if (quickAddActivity) {
        QuickActivityDialog(
            trip = trip,
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

@Composable
private fun ActivityDragHandle(
    activityId: String,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (isDragging) Sky else SoftBlue,
        label = "dragHandleBackground"
    )
    val foreground by animateColorAsState(
        targetValue = if (isDragging) Color.White else Sky,
        label = "dragHandleForeground"
    )

    Box(
        modifier = Modifier
            .width(36.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .pointerInput(activityId) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        onDragStart()
                    },
                    onDragEnd = {
                        onDragEnd()
                    },
                    onDragCancel = {
                        onDragEnd()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            repeat(3) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(foreground)
                    )
                    Box(
                        Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(foreground)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveMapDialog(
    day: TripDay,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCompleteActivity: (String) -> Unit
) {
    val remaining = day.activities.filterNot { it.completed }
    val nextActivity = remaining.firstOrNull()

    val remainingPoints = remaining.mapNotNull { activity ->
        activity.location
            .ifBlank { activity.name }
            .takeIf { it.isNotBlank() }
    }

    val liveRouteUrl = remember(remainingPoints) {
        when {
            remainingPoints.isEmpty() -> ""
            remainingPoints.size == 1 ->
                "https://www.google.com/maps/dir/?api=1" +
                    "&destination=${Uri.encode(remainingPoints.first())}"

            else -> {
                val destination = remainingPoints.last()
                val waypoints = remainingPoints
                    .dropLast(1)
                    .take(8)
                    .joinToString("|")

                buildString {
                    append("https://www.google.com/maps/dir/?api=1")
                    append("&destination=")
                    append(Uri.encode(destination))
                    append("&travelmode=transit")

                    if (waypoints.isNotBlank()) {
                        append("&waypoints=")
                        append(Uri.encode(waypoints))
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("מפת LIVE")
                Text(
                    "${day.title} · ${day.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    SectionCard(
                        containerColor = if (nextActivity == null) {
                            SoftMint
                        } else {
                            SoftBlue
                        }
                    ) {
                        if (nextActivity == null) {
                            Text(
                                "כל הפעילויות ביום הושלמו 🎉",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D56)
                            )
                        } else {
                            Text(
                                "הפעילות הבאה",
                                style = MaterialTheme.typography.labelSmall,
                                color = Sky
                            )
                            Text(
                                "${nextActivity.time} · ${nextActivity.name}",
                                fontWeight = FontWeight.Bold,
                                color = Navy
                            )
                            if (nextActivity.location.isNotBlank()) {
                                Text(
                                    nextActivity.location,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                if (nextActivity != null) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val query = nextActivity.location
                                        .ifBlank { nextActivity.name }
                                    onOpenUrl(
                                        "https://www.google.com/maps/dir/?api=1" +
                                            "&destination=${Uri.encode(query)}"
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Google Maps")
                            }

                            FilledTonalButton(
                                onClick = {
                                    val query = nextActivity.location
                                        .ifBlank { nextActivity.name }
                                    onOpenUrl(
                                        "https://waze.com/ul?q=" +
                                            Uri.encode(query) +
                                            "&navigate=yes"
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Waze")
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                onCompleteActivity(nextActivity.id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("סיימתי את הפעילות")
                        }
                    }
                }

                if (liveRouteUrl.isNotBlank()) {
                    item {
                        AccentButton(
                            text = "פתיחת המסלול החי",
                            emoji = "🛰️",
                            onClick = {
                                onOpenUrl(liveRouteUrl)
                            },
                            color = Color(0xFF2E9B70),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (remaining.isNotEmpty()) {
                    item {
                        Text(
                            "המשך היום",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(
                        remaining,
                        key = { it.id }
                    ) { activity ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CardWhite,
                            border = BorderStroke(
                                1.dp,
                                Color(0xFFE3E9F0)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = SoftBlue
                                ) {
                                    Text(
                                        activity.time,
                                        modifier = Modifier.padding(
                                            horizontal = 8.dp,
                                            vertical = 5.dp
                                        ),
                                        color = Sky,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(Modifier.width(9.dp))

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        activity.name,
                                        fontWeight = FontWeight.Bold,
                                        color = Navy
                                    )
                                    if (activity.location.isNotBlank()) {
                                        Text(
                                            activity.location,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("סגירה")
            }
        }
    )
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
    trip: Trip,
    day: TripDay,
    onDismiss: () -> Unit,
    onOpenFullEditor: () -> Unit,
    onConfirm: (ActivityItem) -> Unit
) {
    val context = LocalContext.current

    var selectedPreset by remember {
        mutableStateOf(activityPresets.first())
    }
    var time by remember { mutableStateOf(nextSuggestedTime(day)) }
    var name by remember { mutableStateOf(selectedPreset.defaultName) }
    var location by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf(selectedPreset.duration) }
    var selectedSuggestionId by remember { mutableStateOf<String?>(null) }
    var placeSuggestions by remember {
        mutableStateOf<List<SmartPlaceSuggestion>>(emptyList())
    }
    var searching by remember { mutableStateOf(false) }

    val previousActivity = remember(day.activities) {
        day.activities
            .sortedBy { activityTimeMinutes(it.time) ?: Int.MAX_VALUE }
            .lastOrNull()
    }

    val previousLocation = previousActivity
        ?.location
        ?.takeIf { it.isNotBlank() }
        ?: previousActivity
            ?.name
            ?.takeIf { it.isNotBlank() }

    val dayDestination = remember(day.destination, trip.destinationStops, trip.destination) {
        day.destination
            .split("→")
            .lastOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: trip.destinationStops.firstOrNull()
            ?: trip.destination
    }

    val savedHotelSuggestions = remember(
        trip.id,
        trip.hotels,
        selectedPreset.key,
        name
    ) {
        if (selectedPreset.key != "hotel") {
            emptyList()
        } else {
            val query = name.trim().lowercase()
            trip.hotels
                .filter { hotel ->
                    query.length < 2 ||
                        hotel.name.lowercase().contains(query) ||
                        hotel.address.lowercase().contains(query)
                }
                .map { hotel ->
                    SmartPlaceSuggestion(
                        id = "hotel-${hotel.id}",
                        title = hotel.name,
                        address = hotel.address,
                        source = "מלון שמור",
                        mapsUrl = hotel.mapsUrl
                    )
                }
                .distinctBy {
                    "${it.title.lowercase()}|${it.address.lowercase()}"
                }
                .take(8)
        }
    }

    val visibleSuggestions = remember(
        savedHotelSuggestions,
        placeSuggestions,
        selectedPreset.key
    ) {
        if (selectedPreset.key == "hotel") {
            savedHotelSuggestions
        } else {
            placeSuggestions
        }
    }

    LaunchedEffect(
        name,
        selectedPreset.key,
        dayDestination,
        trip.offlineMode
    ) {
        placeSuggestions = emptyList()

        val normalized = name.trim()
        val isDefaultName = normalized == selectedPreset.defaultName

        val searchableType = selectedPreset.key in listOf(
            "attraction",
            "meal",
            "shopping",
            "pool",
            "walk"
        )

        if (
            selectedPreset.key == "hotel" ||
            !searchableType ||
            trip.offlineMode ||
            normalized.length < 3 ||
            isDefaultName
        ) {
            searching = false
            return@LaunchedEffect
        }

        delay(700)
        searching = true

        val typeContext = when (selectedPreset.key) {
            "meal" -> "restaurant"
            "attraction" -> "attraction"
            "shopping" -> "shopping mall"
            "pool" -> "water park pool"
            "walk" -> "landmark park"
            else -> ""
        }

        placeSuggestions = AndroidPlaceSearch.search(
            context = context,
            query = listOf(normalized, typeContext)
                .filter { it.isNotBlank() }
                .joinToString(" "),
            destinationContext = dayDestination
        )
        searching = false
    }

    fun selectPreset(preset: ActivityPreset) {
        selectedPreset = preset
        name = preset.defaultName
        location = ""
        duration = preset.duration
        selectedSuggestionId = null
        placeSuggestions = emptyList()
    }

    fun selectSuggestion(suggestion: SmartPlaceSuggestion) {
        selectedSuggestionId = suggestion.id
        name = suggestion.title
        location = suggestion.address.ifBlank { suggestion.title }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("הוספת פעילות חכמה")
                Text(
                    "החיפוש מתבצע לפי היעד של היום: $dayDestination",
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

                if (
                    selectedPreset.key == "hotel" &&
                    trip.hotels.isEmpty()
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SoftSun
                        ) {
                            Text(
                                "עדיין לא נשמרו מלונות בטיול. אפשר להזין מלון ידנית או להוסיף אותו קודם במסך המלונות.",
                                modifier = Modifier.padding(11.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF7D5B00)
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
                        onValueChange = {
                            name = it
                            selectedSuggestionId = null
                        },
                        label = {
                            Text(
                                when (selectedPreset.key) {
                                    "hotel" -> "בחירת מלון שמור"
                                    "meal" -> "חיפוש מסעדה ב-$dayDestination"
                                    "attraction" -> "חיפוש אטרקציה ב-$dayDestination"
                                    else -> "חיפוש מקום ב-$dayDestination"
                                }
                            )
                        },
                        leadingIcon = {
                            Text(selectedPreset.emoji)
                        },
                        trailingIcon = {
                            when {
                                searching -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                                selectedSuggestionId != null -> {
                                    Text("✓", color = Mint)
                                }
                            }
                        },
                        supportingText = {
                            Text(
                                when {
                                    trip.offlineMode &&
                                        selectedPreset.key != "hotel" ->
                                        "במצב אופליין יש להזין שם וכתובת ידנית"
                                    selectedPreset.key == "hotel" ->
                                        "הבחירה מתבצעת מהמלונות ששמרת בטיול"
                                    else ->
                                        "החיפוש מותאם אוטומטית לעיר ולמדינה של היום"
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (visibleSuggestions.isNotEmpty()) {
                    item {
                        Text(
                            when (selectedPreset.key) {
                                "hotel" -> "מלונות במסלול"
                                "meal" -> "מסעדות ביעד"
                                "attraction" -> "אטרקציות ביעד"
                                else -> "תוצאות חיפוש ביעד"
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    items(
                        visibleSuggestions,
                        key = { it.id }
                    ) { suggestion ->
                        Surface(
                            onClick = {
                                selectSuggestion(suggestion)
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (
                                selectedSuggestionId == suggestion.id
                            ) {
                                SoftMint
                            } else {
                                CardWhite
                            },
                            border = BorderStroke(
                                if (
                                    selectedSuggestionId == suggestion.id
                                ) {
                                    2.dp
                                } else {
                                    1.dp
                                },
                                if (
                                    selectedSuggestionId == suggestion.id
                                ) {
                                    Mint
                                } else {
                                    Color(0xFFE3E9F0)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    when (selectedPreset.key) {
                                        "hotel" -> "🏨"
                                        "meal" -> "🍽️"
                                        "attraction" -> "🎫"
                                        "shopping" -> "🛍️"
                                        "pool" -> "🏊"
                                        "walk" -> "🚶"
                                        else -> "📍"
                                    }
                                )

                                Spacer(Modifier.width(8.dp))

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        suggestion.title,
                                        fontWeight = FontWeight.Bold,
                                        color = Navy
                                    )
                                    if (suggestion.address.isNotBlank()) {
                                        Text(
                                            suggestion.address,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary,
                                            maxLines = 2
                                        )
                                    }
                                    Text(
                                        if (selectedPreset.key == "hotel") {
                                            "מלון שמור"
                                        } else {
                                            dayDestination
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Sky
                                    )
                                }

                                if (
                                    selectedSuggestionId == suggestion.id
                                ) {
                                    Text("✓", color = Mint)
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = location,
                        onValueChange = {
                            location = it
                            selectedSuggestionId = null
                        },
                        label = {
                            Text("כתובת או מיקום")
                        },
                        supportingText = {
                            Text(
                                "מתמלא אוטומטית לאחר בחירת תוצאה"
                            )
                        },
                        singleLine = false,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (previousLocation != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SoftAqua
                        ) {
                            Column(
                                modifier = Modifier.padding(11.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    "ניווט מהמיקום הקודם",
                                    fontWeight = FontWeight.Bold,
                                    color = Aqua
                                )
                                Text(
                                    previousLocation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Text(
                                    "לאחר בחירת מקום ייווצר מסלול אוטומטי אל היעד החדש.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
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
                                "השלמה אוטומטית לפי היעד",
                                fontWeight = FontWeight.Bold,
                                color = Sky
                            )
                            Text(
                                "העיר והמדינה נלקחות מהיום שבו מוסיפים את הפעילות.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
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
                    val destinationQuery = location.ifBlank {
                        "$name, $dayDestination"
                    }

                    val selectedSuggestion = visibleSuggestions
                        .firstOrNull {
                            it.id == selectedSuggestionId
                        }

                    val mapsUrl = when {
                        previousLocation != null -> {
                            "https://www.google.com/maps/dir/?api=1" +
                                "&origin=" +
                                Uri.encode(previousLocation) +
                                "&destination=" +
                                Uri.encode(destinationQuery)
                        }
                        !selectedSuggestion?.mapsUrl.isNullOrBlank() -> {
                            selectedSuggestion!!.mapsUrl
                        }
                        else -> {
                            "https://www.google.com/maps/search/?api=1&query=" +
                                Uri.encode(destinationQuery)
                        }
                    }

                    val directionsText = previousLocation?.let {
                        "ניווט מ-$it אל $destinationQuery"
                    }.orEmpty()

                    onConfirm(
                        ActivityItem(
                            id = UUID.randomUUID().toString(),
                            time = time.trim(),
                            name = name.trim(),
                            location = location.trim(),
                            transport = selectedPreset.transport,
                            directions = directionsText,
                            duration = duration.trim(),
                            cost = "",
                            notes = selectedPreset.notes,
                            mapsUrl = mapsUrl,
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

private fun activityTimeMinutes(value: String): Int? {
    val match = Regex("""(\d{1,2}):(\d{2})""").find(value)
        ?: return null

    val hour = match.groupValues[1].toIntOrNull()
        ?: return null
    val minute = match.groupValues[2].toIntOrNull()
        ?: return null

    return hour * 60 + minute
}

private fun nextSuggestedTime(day: TripDay): String {
    val recalculated = recalculateActivityTimes(day.activities)
    val last = recalculated.lastOrNull() ?: return "09:00"
    val start = activityTimeMinutes(last.time) ?: return "09:00"
    val end = (start + activityDurationMinutes(last.duration))
        .coerceAtMost(23 * 60 + 59)
    return minutesToClock(end)
}

private fun recalculateActivityTimes(
    activities: List<ActivityItem>
): List<ActivityItem> {
    if (activities.isEmpty()) return emptyList()

    val firstStart = activityTimeMinutes(activities.first().time)
        ?: 9 * 60

    var currentStart = firstStart

    return activities.mapIndexed { index, activity ->
        val updated = activity.copy(
            time = minutesToClock(currentStart)
        )

        val duration = activityDurationMinutes(activity.duration)
        currentStart = (currentStart + duration)
            .coerceAtMost(23 * 60 + 59)

        updated
    }
}

private fun activityDurationMinutes(value: String): Int {
    val normalized = value
        .trim()
        .lowercase()
        .replace("כשעה", "שעה")
        .replace("כ-", "")
        .replace("כ", "")

    Regex("""(\d+)\s*שעות?""")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.let { hours ->
            val extraMinutes = Regex("""(\d+)\s*דקות?""")
                .find(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: 0
            return hours * 60 + extraMinutes
        }

    Regex("""(\d+)\s*דקות?""")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.let { return it.coerceAtLeast(5) }

    Regex("""(\d+(?:\.\d+)?)\s*hours?""")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.toDoubleOrNull()
        ?.let { return (it * 60).toInt().coerceAtLeast(5) }

    if ("שעה וחצי" in normalized) return 90
    if ("חצי שעה" in normalized) return 30
    if ("שעה" in normalized) return 60
    if ("שעתיים" in normalized) return 120
    if ("שלוש שעות" in normalized) return 180
    if ("45" in normalized) return 45
    if ("30" in normalized) return 30

    return 60
}

private fun minutesToClock(totalMinutes: Int): String {
    val safe = totalMinutes.coerceIn(0, 23 * 60 + 59)
    return "%02d:%02d".format(
        safe / 60,
        safe % 60
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
    var addHotel by remember { mutableStateOf(false) }
    var editingHotel by remember { mutableStateOf<Hotel?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            GradientHeader(
                title = "מלונות",
                subtitle = "בסיס האירוח יוצר אוטומטית ארוחות במסלול",
                emoji = "🏨",
                start = Aqua,
                end = Navy
            )

            SectionCard(containerColor = SoftAqua) {
                Text(
                    "שעות הארוחות הקבועות",
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
                Text(
                    "בוקר 08:00 · צהריים 13:00 · ערב 19:00",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            AccentButton(
                text = "הוספת מלון",
                emoji = "＋",
                onClick = { addHotel = true },
                color = Aqua,
                modifier = Modifier.fillMaxWidth()
            )
        }

        items(trip.hotels, key = { it.id }) { hotel ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, Color(0xFFDDEEF1)),
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
                            Text(
                                hotel.address,
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        IconButton(
                            onClick = { editingHotel = hotel },
                            modifier = Modifier.size(36.dp)
                        ) {
                            SmallEditIcon(Modifier.size(28.dp))
                        }

                        IconButton(
                            onClick = {
                                val updated = trip.copy(
                                    hotels = trip.hotels.filterNot {
                                        it.id == hotel.id
                                    }
                                )
                                onTripChange(
                                    rebuildAutomaticItinerary(updated)
                                )
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            SmallDeleteIcon(Modifier.size(28.dp))
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetaChip(
                            "כניסה ${hotel.checkIn}",
                            SoftAqua,
                            Color(0xFF087C8A)
                        )
                        MetaChip(
                            "יציאה ${hotel.checkOut}",
                            SoftBlue,
                            Sky
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = boardBasisColor(hotel.boardBasis)
                    ) {
                        Text(
                            "🍽️ ${hotel.boardBasis}",
                            modifier = Modifier.padding(
                                horizontal = 11.dp,
                                vertical = 7.dp
                            ),
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )
                    }

                    if (hotel.includeTransfer) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SoftBlue
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = 11.dp,
                                    vertical = 8.dp
                                )
                            ) {
                                Text(
                                    "🚕 הסעה למלון",
                                    fontWeight = FontWeight.Bold,
                                    color = Navy
                                )
                                Text(
                                    "${hotel.transferTime} · ${
                                        hotel.transferFrom.ifBlank {
                                            "נקודת איסוף"
                                        }
                                    } → ${hotel.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    if (hotel.notes.isNotBlank()) {
                        Text(
                            hotel.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    HorizontalDivider(color = Color(0xFFE8EDF3))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                onOpenUrl(
                                    hotel.mapsUrl.ifBlank {
                                        "https://www.google.com/maps/search/?api=1&query=" +
                                            Uri.encode(
                                                hotel.address.ifBlank {
                                                    hotel.name
                                                }
                                            )
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
                                        Uri.encode(
                                            hotel.address.ifBlank {
                                                hotel.name
                                            }
                                        ) +
                                        "&navigate=yes"
                                )
                            }
                        ) {
                            WazeBrandIcon(Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }

    if (addHotel) {
        HotelSkeletonEditorDialog(
            trip = trip,
            hotel = null,
            onDismiss = { addHotel = false },
            onConfirm = { hotel ->
                val updated = trip.copy(
                    hotels = trip.hotels + hotel
                )
                onTripChange(rebuildAutomaticItinerary(updated))
                addHotel = false
            }
        )
    }

    editingHotel?.let { hotel ->
        HotelSkeletonEditorDialog(
            trip = trip,
            hotel = hotel,
            onDismiss = { editingHotel = null },
            onConfirm = { updatedHotel ->
                val updated = trip.copy(
                    hotels = trip.hotels.map {
                        if (it.id == updatedHotel.id) {
                            updatedHotel
                        } else {
                            it
                        }
                    }
                )
                onTripChange(rebuildAutomaticItinerary(updated))
                editingHotel = null
            }
        )
    }
}

@Composable
private fun HotelSkeletonEditorDialog(
    trip: Trip,
    hotel: Hotel?,
    onDismiss: () -> Unit,
    onConfirm: (Hotel) -> Unit
) {
    val context = LocalContext.current

    var name by remember(hotel?.id) {
        mutableStateOf(hotel?.name.orEmpty())
    }
    var checkIn by remember(hotel?.id) {
        mutableStateOf(hotel?.checkIn ?: trip.startDate)
    }
    var checkOut by remember(hotel?.id) {
        mutableStateOf(hotel?.checkOut ?: trip.endDate)
    }
    var address by remember(hotel?.id) {
        mutableStateOf(hotel?.address.orEmpty())
    }
    var selectedSuggestionId by remember(hotel?.id) {
        mutableStateOf<String?>(null)
    }
    var hotelSuggestions by remember {
        mutableStateOf<List<SmartPlaceSuggestion>>(emptyList())
    }
    var searchingHotel by remember { mutableStateOf(false) }

    var boardBasis by remember(hotel?.id) {
        mutableStateOf(hotel?.boardBasis ?: "לינה בלבד")
    }
    var boardMenuOpen by remember { mutableStateOf(false) }
    var notes by remember(hotel?.id) {
        mutableStateOf(hotel?.notes.orEmpty())
    }

    var includeTransfer by remember(hotel?.id) {
        mutableStateOf(hotel?.includeTransfer ?: false)
    }
    var transferFrom by remember(hotel?.id) {
        mutableStateOf(hotel?.transferFrom.orEmpty())
    }
    var transferTime by remember(hotel?.id) {
        mutableStateOf(hotel?.transferTime ?: "15:00")
    }
    var transferMinutesText by remember(hotel?.id) {
        mutableStateOf((hotel?.transferMinutes ?: 45).toString())
    }

    val bases = listOf(
        "לינה בלבד",
        "ארוחת בוקר",
        "חצי פנסיון",
        "פנסיון מלא"
    )

    LaunchedEffect(
        name,
        trip.destination,
        trip.destinationStops,
        trip.offlineMode
    ) {
        hotelSuggestions = emptyList()

        val query = name.trim()
        if (
            query.length < 3 ||
            trip.offlineMode ||
            query == hotel?.name
        ) {
            searchingHotel = false
            return@LaunchedEffect
        }

        delay(700)
        searchingHotel = true
        hotelSuggestions = AndroidPlaceSearch.search(
            context = context,
            query = "$query hotel",
            destinationContext = trip.destinationStops
                .firstOrNull()
                ?: trip.destination
        )
        searchingHotel = false
    }

    val valid = name.isNotBlank() &&
        address.isNotBlank() &&
        runCatching {
            !LocalDate.parse(checkOut)
                .isBefore(LocalDate.parse(checkIn))
        }.getOrDefault(false) &&
        (
            !includeTransfer ||
                (
                    isValidHotelTransferTime(transferTime) &&
                        (transferMinutesText.toIntOrNull() ?: 0) >= 0
                )
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (hotel == null) "מלון חדש" else "עריכת מלון"
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            selectedSuggestionId = null
                        },
                        label = { Text("חיפוש שם המלון") },
                        leadingIcon = { Text("🏨") },
                        trailingIcon = {
                            when {
                                searchingHotel -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                                selectedSuggestionId != null -> {
                                    Text("✓", color = Mint)
                                }
                            }
                        },
                        supportingText = {
                            Text(
                                if (trip.offlineMode) {
                                    "במצב אופליין יש להזין שם וכתובת ידנית"
                                } else {
                                    "הקלד לפחות 3 תווים לקבלת הצעות"
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (hotelSuggestions.isNotEmpty()) {
                    item {
                        Text(
                            "תוצאות חיפוש",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(
                        hotelSuggestions,
                        key = { it.id }
                    ) { suggestion ->
                        Surface(
                            onClick = {
                                selectedSuggestionId = suggestion.id
                                name = suggestion.title
                                address = suggestion.address
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (
                                selectedSuggestionId == suggestion.id
                            ) {
                                SoftMint
                            } else {
                                CardWhite
                            },
                            border = BorderStroke(
                                if (
                                    selectedSuggestionId == suggestion.id
                                ) 2.dp else 1.dp,
                                if (
                                    selectedSuggestionId == suggestion.id
                                ) Mint else Color(0xFFE3E9F0)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📍")
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        suggestion.title,
                                        fontWeight = FontWeight.Bold,
                                        color = Navy
                                    )
                                    Text(
                                        suggestion.address,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        maxLines = 2
                                    )
                                }
                                if (
                                    selectedSuggestionId == suggestion.id
                                ) {
                                    Text("✓", color = Mint)
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = address,
                        onValueChange = {
                            address = it
                            selectedSuggestionId = null
                        },
                        label = { Text("כתובת המלון") },
                        supportingText = {
                            Text("מתמלא אוטומטית לאחר בחירת תוצאה")
                        },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    TripDatePickerField(
                        label = "צ׳ק־אין",
                        value = checkIn,
                        onValueChange = {
                            checkIn = it
                            if (
                                runCatching {
                                    LocalDate.parse(checkOut)
                                        .isBefore(LocalDate.parse(it))
                                }.getOrDefault(false)
                            ) {
                                checkOut = it
                            }
                        }
                    )
                }

                item {
                    TripDatePickerField(
                        label = "צ׳ק־אאוט",
                        value = checkOut,
                        minimumDate = checkIn,
                        onValueChange = { checkOut = it }
                    )
                }

                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { boardMenuOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "בסיס אירוח: $boardBasis",
                                modifier = Modifier.weight(1f)
                            )
                            Text("⌄")
                        }

                        DropdownMenu(
                            expanded = boardMenuOpen,
                            onDismissRequest = {
                                boardMenuOpen = false
                            }
                        ) {
                            bases.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        boardBasis = option
                                        boardMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    SectionCard(
                        containerColor = if (includeTransfer) {
                            SoftBlue
                        } else {
                            CardWhite
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "הוספת הסעה למלון",
                                    fontWeight = FontWeight.Bold,
                                    color = Navy
                                )
                                Text(
                                    "ההסעה תתווסף ליום הצ׳ק־אין",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }

                            Switch(
                                checked = includeTransfer,
                                onCheckedChange = {
                                    includeTransfer = it
                                }
                            )
                        }

                        if (includeTransfer) {
                            OutlinedTextField(
                                value = transferFrom,
                                onValueChange = { transferFrom = it },
                                label = {
                                    Text("נקודת איסוף")
                                },
                                supportingText = {
                                    Text(
                                        "לדוגמה: שדה התעופה או תחנת הרכבת"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = transferTime,
                                onValueChange = { transferTime = it },
                                label = {
                                    Text("שעת ההסעה HH:mm")
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = transferMinutesText,
                                onValueChange = {
                                    transferMinutesText =
                                        it.filter(Char::isDigit)
                                },
                                label = {
                                    Text("זמן נסיעה בדקות")
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("הערות") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    SectionCard(containerColor = SoftAqua) {
                        Text(
                            mealPlanDescription(boardBasis),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        if (includeTransfer) {
                            Text(
                                "הסעה תתווסף ב־$transferTime מ-${
                                    transferFrom.ifBlank { "נקודת האיסוף" }
                                } אל המלון.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Sky
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val mapsUrl = hotel?.mapsUrl
                        ?.takeIf { it.isNotBlank() }
                        ?: "https://www.google.com/maps/search/?api=1&query=" +
                            Uri.encode(address.ifBlank { name })

                    onConfirm(
                        Hotel(
                            id = hotel?.id
                                ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            checkIn = checkIn,
                            checkOut = checkOut,
                            address = address.trim(),
                            mapsUrl = mapsUrl,
                            notes = notes.trim(),
                            boardBasis = boardBasis,
                            includeTransfer = includeTransfer,
                            transferFrom = transferFrom.trim(),
                            transferTime = transferTime.trim(),
                            transferMinutes = transferMinutesText
                                .toIntOrNull()
                                ?.coerceAtLeast(0)
                                ?: 45
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

private fun isValidHotelTransferTime(value: String): Boolean =
    Regex("""(?:[01]\d|2[0-3]):[0-5]\d""")
        .matches(value.trim())

private fun mealPlanDescription(boardBasis: String): String =
    when (boardBasis) {
        "ארוחת בוקר" ->
            "ארוחת בוקר תתווסף ב־08:00 מהבוקר שלאחר הצ׳ק־אין ועד יום הצ׳ק־אאוט."
        "חצי פנסיון" ->
            "ארוחת בוקר ב־08:00 וארוחת ערב ב־19:00. ערב מתחיל ביום הצ׳ק־אין."
        "פנסיון מלא" ->
            "ארוחת בוקר ב־08:00, צהריים ב־13:00 וערב ב־19:00 בהתאם לימי השהייה."
        else ->
            "לא יתווספו ארוחות אוטומטיות למסלול."
    }

private fun boardBasisColor(boardBasis: String): Color =
    when (boardBasis) {
        "ארוחת בוקר" -> SoftSun
        "חצי פנסיון" -> SoftAqua
        "פנסיון מלא" -> SoftMint
        else -> SoftBlue
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
