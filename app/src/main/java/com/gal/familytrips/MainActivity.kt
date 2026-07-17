
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var store: TripStore
    private lateinit var cloudManager: FirebaseCloudManager
    private var diffSyncCoordinator:
        DiffSyncCoordinator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TripStore(this)
        cloudManager = FirebaseCloudManager(this)
        diffSyncCoordinator =
            DiffSyncCoordinator(
                scope = lifecycleScope,
                syncEngine =
                    TripDiffSyncEngine(
                        V9CloudRepository()
                    ),
                onSynced = {
                    synced ->
                    val current =
                        store.load()
                    store.save(
                        current.replaceTrip(
                            synced
                        )
                    )
                },
                onError = {
                    // Local data remains safe.
                }
            )

        setContent {
            GalTripsTheme {
                var state by remember {
                    mutableStateOf<AppState?>(null)
                }
                var authLoading by remember {
                    mutableStateOf(true)
                }
                var authError by remember {
                    mutableStateOf<String?>(null)
                }
                val composeScope = rememberCoroutineScope()

                suspend fun loadAccount(
                    profile: CloudUserProfile
                ) {
                    val local = store.load()
                    val cloudTrips = runCatching {
                        cloudManager.fetchUserTrips(profile)
                    }.getOrElse {
                        authError = it.localizedMessage
                            ?: "טעינת נתוני הענן נכשלה"
                        emptyList()
                    }
                    val trips = if (cloudTrips.isNotEmpty()) {
                        cloudTrips
                    } else {
                        local.trips
                    }
                    val selected = if (
                        trips.any { it.id == local.currentTripId }
                    ) local.currentTripId else trips.first().id

                    val next = local.copy(
                        trips = trips,
                        currentTripId = selected,
                        currentUser = profile,
                        localMode = false
                    )
                    state = next
                    store.save(next)
                }

                LaunchedEffect(Unit) {
                    cloudManager.currentProfile()?.let {
                        loadAccount(it)
                    }
                    authLoading = false
                }

                when {
                    authLoading -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }

                    state?.currentUser == null &&
                        state?.localMode != true -> LoginScreen(
                        error = authError,
                        loading = authLoading,
                        onGoogleSignIn = {
                            authLoading = true
                            authError = null
                            composeScope.launch {
                                runCatching {
                                    cloudManager.signInWithGoogle()
                                }.onSuccess {
                                    loadAccount(it)
                                }.onFailure {
                                    authError = it.localizedMessage
                                        ?: "ההתחברות נכשלה"
                                }
                                authLoading = false
                            }
                        },
                        onContinueLocally = {
                            composeScope.launch {
                                val local = store.load().copy(
                                    currentUser = null,
                                    localMode = true
                                )
                                store.save(local)
                                state = local
                            }
                        }
                    )

                    else -> {
                        val loaded = state!!
                        GalTripsApp(
                        state = loaded,
                        onStateChange = { next ->
                            val previous = state
                            state = next

                            lifecycleScope.launch {
                                store.save(next)
                            }

                            val profile =
                                next.currentUser
                            val oldTrip = previous
                                ?.trips
                                ?.firstOrNull {
                                    it.id ==
                                        next.currentTripId
                                }
                            val newTrip = next.trips
                                .firstOrNull {
                                    it.id ==
                                        next.currentTripId
                                }

                            if (
                                profile != null &&
                                oldTrip != null &&
                                newTrip != null &&
                                newTrip.cloudEnabled &&
                                oldTrip != newTrip
                            ) {
                                diffSyncCoordinator
                                    ?.enqueue(
                                        oldTrip,
                                        newTrip,
                                        profile
                                    )
                            }
                        },
                        onOpenUrl = ::openUrl,
                        onShareTrip = {
                            shareTripPackage(it)
                        },
                        cloudManager = cloudManager,
                        onSignOut = {
                            cloudManager.signOut()
                            composeScope.launch {
                                val local = store.load()
                                val next = local.copy(
                                    currentUser = null,
                                    localMode = false
                                )
                                store.save(next)
                                state = next
                            }
                        },
                        onReloadCloud = {
                            state?.currentUser?.let { profile ->
                                composeScope.launch {
                                    loadAccount(profile)
                                }
                            }
                        },
                        onRemoteStateChange = {
                            remote ->
                            state = remote
                            lifecycleScope.launch {
                                store.save(remote)
                            }
                        },
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
                    }
                }
            }
        }
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun shareTripPackage(trip: Trip) {
        runCatching {
            val uri = TripPackageManager.createPackage(
                this,
                trip
            )

            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(
                            Intent.EXTRA_STREAM,
                            uri
                        )
                        putExtra(
                            Intent.EXTRA_SUBJECT,
                            trip.name
                        )
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    },
                    "שיתוף הטיול"
                )
            )
        }
    }
}

@Composable
private fun LoginScreen(
    error: String?,
    loading: Boolean,
    onGoogleSignIn: () -> Unit,
    onContinueLocally: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFEAF4FF), Color(0xFFF8FAFC), Color.White))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.8f))
            Surface(modifier = Modifier.size(96.dp), shape = RoundedCornerShape(30.dp), color = Navy, shadowElevation = 12.dp) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Public, null, tint = Color.White, modifier = Modifier.size(50.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("FamilyGo", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Navy)
            Spacer(Modifier.height(8.dp))
            Text("Plan together. Travel better.", textAlign = TextAlign.Center, color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(38.dp))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
                Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Button(onClick = onGoogleSignIn, enabled = !loading, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Navy), border = BorderStroke(1.dp, Color(0xFFD8DEE8))) {
                        if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else { Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold); Spacer(Modifier.width(12.dp)); Text("המשך עם Google", fontWeight = FontWeight.Bold) }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(modifier = Modifier.weight(1f)); Text("או", modifier = Modifier.padding(horizontal = 12.dp), color = TextSecondary); HorizontalDivider(modifier = Modifier.weight(1f))
                    }
                    OutlinedButton(onClick = onContinueLocally, enabled = !loading, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.PhoneAndroid, null); Spacer(Modifier.width(10.dp)); Text("המשך מקומית", fontWeight = FontWeight.Bold)
                    }
                    error?.let { Text(it, color = Coral, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("ניתן להתחבר לחשבון Google גם בהמשך דרך ההגדרות", textAlign = TextAlign.Center, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SmartDashboardScreen(
    state: AppState,
    onStateChange: (AppState) -> Unit,
    onCreateTrip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val nextTrip = state.trips
        .sortedBy { it.startDate }
        .firstOrNull {
            runCatching {
                !LocalDate.parse(it.startDate)
                    .isBefore(today)
            }.getOrDefault(false)
        } ?: state.trips.firstOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "שלום ${state.currentUser?.displayName.orEmpty()}",
                style =
                    MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Navy
            )
            Text(
                "הטיולים שלך מסונכרנים עם החשבון",
                color = TextSecondary
            )
        }

        nextTrip?.let { trip ->
            item {
                SectionCard(containerColor = SoftBlue) {
                    Text(
                        "הטיול הקרוב",
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    Text(
                        trip.name,
                        style =
                            MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${trip.destination} · ${trip.startDate}",
                        color = TextSecondary
                    )
                    Button(
                        onClick = {
                            onStateChange(
                                state.copy(
                                    currentTripId = trip.id
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("המשך לטיול")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Text(
                    "הטיולים שלי",
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
                TextButton(onClick = onCreateTrip) {
                    Icon(Icons.Default.Add, null)
                    Text("טיול חדש")
                }
            }
        }

        items(state.trips, key = { it.id }) { trip ->
            SectionCard(containerColor = CardWhite) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            trip.name,
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )
                        Text(
                            trip.destination,
                            color = TextSecondary
                        )
                        Text(
                            "${trip.startDate} – ${trip.endDate}",
                            style =
                                MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = {
                            onStateChange(
                                state.copy(
                                    currentTripId = trip.id
                                )
                            )
                        }
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "פתיחה"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                color = Navy
            )
            Text(
                subtitle,
                style =
                    MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween
    ) {
        Text(title, color = Navy)
        Text(value, color = TextSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalTripsApp(
    state: AppState,
    onStateChange: (AppState) -> Unit,
    onOpenUrl: (String) -> Unit,
    onShareTrip: (Trip) -> Unit,
    cloudManager: FirebaseCloudManager,
    onSignOut: () -> Unit,
    onReloadCloud: () -> Unit,
    onRemoteStateChange: (AppState) -> Unit,
    onImportTrip: (String) -> Unit
) {
    val syncConflict by ConflictCenter.conflict
        .collectAsState()
    val appContext = LocalContext.current

    var tab by remember { mutableIntStateOf(0) }
    var selectedDayId by remember { mutableStateOf<String?>(null) }
    var showAddTrip by remember {
        mutableStateOf(false)
    }
    var showMainMenu by remember {
        mutableStateOf(false)
    }
    var showAccountDialog by remember {
        mutableStateOf(false)
    }
    var showSettingsDialog by remember {
        mutableStateOf(false)
    }
    var showSharingDialog by remember {
        mutableStateOf(false)
    }
    var showJoinDialog by remember {
        mutableStateOf(false)
    }
    var sharingBusy by remember {
        mutableStateOf(false)
    }
    var sharingMessage by remember {
        mutableStateOf<String?>(null)
    }
    var activeInvite by remember {
        mutableStateOf<TripInvite?>(null)
    }
    val sharingScope = rememberCoroutineScope()
    val trip = state.trips.firstOrNull {
        it.id == state.currentTripId
    } ?: state.trips.first()

    val todayListenerDayId = remember(
        trip.id,
        trip.days
    ) {
        val today = LocalDate.now().toString()
        trip.days.firstOrNull {
            it.date == today
        }?.id ?: trip.days
            .sortedBy { it.date }
            .firstOrNull()?.id
    }

    val activeListenerDayId = when {
        tab == 4 && selectedDayId != null ->
            selectedDayId
        tab == 1 -> todayListenerDayId
        else -> null
    }

    DisposableEffect(
        trip.id,
        trip.cloudSchemaVersion,
        tab,
        activeListenerDayId
    ) {
        if (
            !trip.cloudEnabled ||
            trip.cloudSchemaVersion < 9
        ) {
            onDispose {}
        } else {
            val registration = when (tab) {
                1, 4 -> {
                    val dayId =
                        activeListenerDayId
                    if (dayId == null) {
                        null
                    } else {
                        cloudManager
                            .listenActivitiesForDayV9(
                                trip.id,
                                dayId,
                                onChange = {
                                    cloudItems ->
                                    val updatedTrip =
                                        trip.copy(
                                            days =
                                                trip.days.map {
                                                    day ->
                                                    if (
                                                        day.id ==
                                                            dayId
                                                    ) {
                                                        day.copy(
                                                            activities =
                                                                cloudItems
                                                                    .map {
                                                                        it.activity
                                                                    }
                                                        )
                                                    } else {
                                                        day
                                                    }
                                                }
                                        )
                                    if (
                                        updatedTrip != trip
                                    ) {
                                        onRemoteStateChange(
                                            state.replaceTrip(
                                                updatedTrip
                                            )
                                        )
                                    }
                                },
                                onError = {
                                    // Local cache stays visible.
                                }
                            )
                    }
                }

                2 -> cloudManager.listenCollectionV9(
                    trip.id,
                    "flights",
                    Flight.serializer(),
                    onChange = { items ->
                        val sorted = items.sortedWith(
                            compareBy<Flight> {
                                it.departureDate
                            }.thenBy {
                                it.departureTime
                            }
                        )
                        if (sorted != trip.flights) {
                            onRemoteStateChange(
                                state.replaceTrip(
                                    trip.copy(
                                        flights = sorted
                                    )
                                )
                            )
                        }
                    },
                    onError = {}
                )

                3 -> cloudManager.listenCollectionV9(
                    trip.id,
                    "hotels",
                    Hotel.serializer(),
                    onChange = { items ->
                        val sorted = items.sortedBy {
                            it.checkIn
                        }
                        if (sorted != trip.hotels) {
                            onRemoteStateChange(
                                state.replaceTrip(
                                    trip.copy(
                                        hotels = sorted
                                    )
                                )
                            )
                        }
                    },
                    onError = {}
                )

                5 -> cloudManager.listenCollectionV9(
                    trip.id,
                    "restaurants",
                    Restaurant.serializer(),
                    onChange = { items ->
                        if (
                            items !=
                                trip.restaurants
                        ) {
                            onRemoteStateChange(
                                state.replaceTrip(
                                    trip.copy(
                                        restaurants =
                                            items
                                    )
                                )
                            )
                        }
                    },
                    onError = {}
                )

                6 -> cloudManager.listenCollectionV9(
                    trip.id,
                    "expenses",
                    Expense.serializer(),
                    onChange = { items ->
                        if (items != trip.expenses) {
                            onRemoteStateChange(
                                state.replaceTrip(
                                    trip.copy(
                                        expenses = items
                                    )
                                )
                            )
                        }
                    },
                    onError = {}
                )

                7 -> cloudManager.listenCollectionV9(
                    trip.id,
                    "documents",
                    TripDocument.serializer(),
                    onChange = { items ->
                        if (
                            items !=
                                trip.documents
                        ) {
                            onRemoteStateChange(
                                state.replaceTrip(
                                    trip.copy(
                                        documents = items
                                    )
                                )
                            )
                        }
                    },
                    onError = {}
                )

                9 -> cloudManager.listenCollectionV9(
                    trip.id,
                    "packing",
                    PackingItem.serializer(),
                    onChange = { items ->
                        if (
                            items !=
                                trip.packingItems
                        ) {
                            onRemoteStateChange(
                                state.replaceTrip(
                                    trip.copy(
                                        packingItems =
                                            items
                                    )
                                )
                            )
                        }
                    },
                    onError = {}
                )

                else -> null
            }

            onDispose {
                registration?.remove()
            }
        }
    }

    LaunchedEffect(state.currentTripId) {
        selectedDayId = null
        tab = 0
        showAddTrip = false
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (tab == 0) "הטיולים שלי"
                        else trip.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showMainMenu = true }
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "תפריט"
                            )
                        }
                        DropdownMenu(
                            expanded = showMainMenu,
                            onDismissRequest = {
                                showMainMenu = false
                            }
                        ) {
                            DropdownMenuItem(
                                text = { Text("חשבון Google") },
                                leadingIcon = {
                                    Text(
                                        "G",
                                        color = Color(0xFF4285F4),
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                onClick = {
                                    showMainMenu = false
                                    showAccountDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("שיתוף הטיול") },
                                leadingIcon = {
                                    Icon(Icons.Default.GroupAdd, null)
                                },
                                onClick = {
                                    showMainMenu = false
                                    showSharingDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("הצטרפות באמצעות קוד") },
                                leadingIcon = {
                                    Icon(Icons.Default.VpnKey, null)
                                },
                                onClick = {
                                    showMainMenu = false
                                    showJoinDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("הגדרות") },
                                leadingIcon = {
                                    Icon(Icons.Default.Settings, null)
                                },
                                onClick = {
                                    showMainMenu = false
                                    showSettingsDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text("התנתקות", color = Coral)
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Logout,
                                        null,
                                        tint = Coral
                                    )
                                },
                                onClick = {
                                    showMainMenu = false
                                    onSignOut()
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CardWhite,
                tonalElevation = 10.dp,
                modifier = Modifier.height(72.dp)
            ) {
                listOf(
                    Triple(Icons.Default.Home, "טיולים", 0),
                    Triple(Icons.Default.WbSunny, "היום", 1),
                    Triple(Icons.Default.Flight, "טיסות", 2),
                    Triple(Icons.Default.Hotel, "מלונות", 3),
                    Triple(Icons.Default.Today, "ימים", 4),
                    Triple(Icons.Default.Restaurant, "מסעדות", 5),
                    Triple(Icons.Default.AttachMoney, "תקציב", 6),
                    Triple(Icons.Default.Description, "מסמכים", 7),
                    Triple(Icons.Default.Info, "מידע", 8),
                    Triple(Icons.Default.Luggage, "ציוד", 9)
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
                    cloudManager,
                    onImportTrip,
                    Modifier.padding(padding)
                )

                1 -> TodayScreen(
                    trip = trip,
                    onTripChange = {
                        onStateChange(state.replaceTrip(it))
                    },
                    onOpenUrl = onOpenUrl,
                    modifier = Modifier.padding(padding)
                )

                2 -> FlightsScreen(
                    trip = trip,
                    onTripChange = {
                        onStateChange(state.replaceTrip(it))
                    },
                    modifier = Modifier.padding(padding)
                )

                3 -> HotelsScreen(
                    trip,
                    { onStateChange(state.replaceTrip(it)) },
                    onOpenUrl,
                    Modifier.padding(padding)
                )

                4 -> if (selectedDayId == null) {
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

                5 -> RestaurantsScreen(
                    trip,
                    { onStateChange(state.replaceTrip(it)) },
                    onOpenUrl,
                    Modifier.padding(padding)
                )

                6 -> ExpensesScreen(
                    trip,
                    { onStateChange(state.replaceTrip(it)) },
                    Modifier.padding(padding)
                )

                7 -> DocumentsScreen(
                    trip,
                    { onStateChange(state.replaceTrip(it)) },
                    Modifier.padding(padding)
                )

                8 -> GeneralInfoScreen(
                    trip = trip,
                    onTripChange = {
                        onStateChange(state.replaceTrip(it))
                    },
                    modifier = Modifier.padding(padding)
                )

                9 -> PackingScreen(
                    trip = trip,
                    onTripChange = {
                        onStateChange(state.replaceTrip(it))
                    },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }

    syncConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = {
                ConflictCenter.clear()
            },
            title = {
                Text("שינוי במכשיר אחר")
            },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Text(conflict.message)
                    Text(
                        "השינוי המקומי נשמר במכשיר ולא נדרס.",
                        color = TextSecondary
                    )
                    if (
                        conflict.remoteUpdatedBy
                            .isNotBlank()
                    ) {
                        Text(
                            "עודכן על ידי: ${conflict.remoteUpdatedBy}",
                            style =
                                MaterialTheme.typography
                                    .bodySmall,
                            color = TextSecondary
                        )
                    }
                    Text(
                        "גרסה מקומית: ${conflict.localRevision} · גרסה בענן: ${conflict.remoteRevision}",
                        style =
                            MaterialTheme.typography
                                .labelSmall,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ConflictCenter.clear()
                    }
                ) {
                    Text("טען את העדכון מהענן")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        ConflictCenter.clear()
                    }
                ) {
                    Text("השאר את השינוי המקומי")
                }
            }
        )
    }

    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text(if (state.currentUser != null) "חשבון Google" else "מצב מקומי") },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.currentUser?.displayName ?: "משתמש מקומי", fontWeight = FontWeight.Bold, color = Navy)
                    Text(state.currentUser?.email ?: "הנתונים נשמרים במכשיר", color = TextSecondary)
                }
            },
            confirmButton = { Button(onClick = { showAccountDialog = false }) { Text("סגירה") } }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("הגדרות") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text("חשבון", fontWeight = FontWeight.Bold, color = Navy) }
                    item {
                        SectionCard(containerColor = SoftBlue) {
                            Text(if (state.currentUser != null) "מחובר עם Google" else "מצב מקומי", fontWeight = FontWeight.Bold, color = Navy)
                            Text(state.currentUser?.email ?: "הנתונים נשמרים במכשיר בלבד", color = TextSecondary)
                        }
                    }
                    item { Text("סנכרון ושיתוף", fontWeight = FontWeight.Bold, color = Navy) }
                    item { SettingSwitchRow("סנכרון אוטומטי", "שמירת שינויים בענן באופן אוטומטי", state.automaticSync && state.currentUser != null) { onStateChange(state.copy(automaticSync = it)) } }
                    item { SettingSwitchRow("התראות", "עדכונים בטיול משותף", state.notificationsEnabled) { onStateChange(state.copy(notificationsEnabled = it)) } }
                    item {
                        SectionCard(containerColor = CardWhite) {
                            Text("שיתוף משפחתי", fontWeight = FontWeight.Bold, color = Navy)
                            Text(if (state.currentUser != null) "ניהול בני משפחה והרשאות" else "נדרש חשבון Google כדי לשתף טיולים", color = TextSecondary)
                        }
                    }
                    item { Text("העדפות", fontWeight = FontWeight.Bold, color = Navy) }
                    item {
                        Text("מטבע מועדף", color = Navy)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("₪", "$", "€").forEach { c -> FilterChip(selected = state.preferredCurrency == c, onClick = { onStateChange(state.copy(preferredCurrency = c)) }, label = { Text(c) }) } }
                    }
                    item {
                        Text("יחידות מרחק", color = Navy)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("ק״מ", "מייל").forEach { u -> FilterChip(selected = state.distanceUnit == u, onClick = { onStateChange(state.copy(distanceUnit = u)) }, label = { Text(u) }) } }
                    }
                    item { Text("גרסה ${BuildConfig.VERSION_NAME}", color = TextSecondary, style = MaterialTheme.typography.labelSmall) }
                }
            },
            confirmButton = { Button(onClick = { showSettingsDialog = false }) { Text("שמירה וסגירה") } }
        )
    }

    if (showSharingDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!sharingBusy) {
                    showSharingDialog = false
                    activeInvite = null
                    sharingMessage = null
                }
            },
            title = { Text("שיתוף הטיול") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        trip.name,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    Text(trip.destination, color = TextSecondary)

                    when {
                        state.currentUser == null -> {
                            Text(
                                "יש להתחבר עם Google כדי לשתף טיול.",
                                color = TextSecondary
                            )
                        }
                        trip.ownerUserId.isNotBlank() &&
                            trip.ownerUserId != state.currentUser.userId -> {
                            Text(
                                "רק בעל הטיול יכול ליצור הזמנה.",
                                color = TextSecondary
                            )
                        }
                        else -> {
                            Button(
                                enabled = !sharingBusy,
                                onClick = {
                                    val profile =
                                        state.currentUser ?: return@Button
                                    sharingBusy = true
                                    sharingMessage = null
                                    sharingScope.launch {
                                        runCatching {
                                            cloudManager.createTripInvite(
                                                trip,
                                                profile
                                            )
                                        }.onSuccess {
                                            activeInvite = it
                                        }.onFailure {
                                            sharingMessage =
                                                it.localizedMessage
                                                    ?: "יצירת ההזמנה נכשלה"
                                        }
                                        sharingBusy = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Link, null)
                                Spacer(Modifier.width(8.dp))
                                Text("יצירת הזמנה")
                            }
                        }
                    }

                    activeInvite?.let { invite ->
                        SectionCard(containerColor = SoftBlue) {
                            Text("קוד ההזמנה", color = TextSecondary)
                            Text(
                                invite.code,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Navy
                            )
                            Text(
                                "תקף לשבעה ימים",
                                color = TextSecondary
                            )
                            Button(
                                onClick = {
                                    val link =
                                        "familygo://invite?code=${invite.code}"
                                    val message =
                                        "הוזמנת לטיול ${invite.tripName} ב-FamilyGo.\nקוד: ${invite.code}\n$link"
                                    val intent =
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                message
                                            )
                                        }
                                    appContext.startActivity(
                                        Intent.createChooser(
                                            intent,
                                            "שיתוף הזמנה"
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Share, null)
                                Spacer(Modifier.width(8.dp))
                                Text("שיתוף")
                            }
                        }
                    }

                    sharingMessage?.let {
                        Text(it, color = Coral)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSharingDialog = false
                        activeInvite = null
                        sharingMessage = null
                    }
                ) { Text("סגירה") }
            }
        )
    }

    if (showJoinDialog) {
        var inviteCode by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = {
                if (!sharingBusy) {
                    showJoinDialog = false
                    activeInvite = null
                    sharingMessage = null
                }
            },
            title = { Text("הצטרפות לטיול") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = {
                            inviteCode = it.uppercase().take(6)
                        },
                        label = { Text("קוד הזמנה") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        enabled = !sharingBusy &&
                            inviteCode.length == 6,
                        onClick = {
                            sharingBusy = true
                            sharingMessage = null
                            sharingScope.launch {
                                runCatching {
                                    cloudManager.getTripInvite(inviteCode)
                                }.onSuccess {
                                    activeInvite = it
                                }.onFailure {
                                    sharingMessage =
                                        it.localizedMessage
                                            ?: "הקוד אינו תקין"
                                }
                                sharingBusy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("בדיקת הקוד") }

                    activeInvite?.let { invite ->
                        SectionCard(containerColor = SoftBlue) {
                            Text(
                                invite.tripName,
                                fontWeight = FontWeight.Bold,
                                color = Navy
                            )
                            Text(invite.destination, color = TextSecondary)
                            Text(
                                "הזמנה מאת ${invite.createdByName}",
                                color = TextSecondary
                            )
                            Button(
                                enabled = !sharingBusy,
                                onClick = {
                                    val profile =
                                        state.currentUser ?: return@Button
                                    sharingBusy = true
                                    sharingScope.launch {
                                        runCatching {
                                            cloudManager.acceptTripInvite(
                                                invite,
                                                profile
                                            )
                                        }.onSuccess { joined ->
                                            onStateChange(
                                                state.copy(
                                                    trips = state.trips
                                                        .filterNot {
                                                            it.id == joined.id
                                                        } + joined,
                                                    currentTripId = joined.id
                                                )
                                            )
                                            showJoinDialog = false
                                            activeInvite = null
                                        }.onFailure {
                                            sharingMessage =
                                                it.localizedMessage
                                                    ?: "ההצטרפות נכשלה"
                                        }
                                        sharingBusy = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("אישור והצטרפות") }

                            OutlinedButton(
                                enabled = !sharingBusy,
                                onClick = {
                                    val profile =
                                        state.currentUser
                                            ?: return@OutlinedButton
                                    sharingScope.launch {
                                        runCatching {
                                            cloudManager.declineTripInvite(
                                                invite,
                                                profile
                                            )
                                        }
                                        showJoinDialog = false
                                        activeInvite = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("דחייה") }
                        }
                    }

                    sharingMessage?.let {
                        Text(it, color = Coral)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showJoinDialog = false
                        activeInvite = null
                        sharingMessage = null
                    }
                ) { Text("סגירה") }
            }
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripsScreen(
    state: AppState,
    onStateChange: (AppState) -> Unit,
    onShareTrip: (Trip) -> Unit,
    cloudManager: FirebaseCloudManager,
    onImportTrip: (String) -> Unit,
    modifier: Modifier
) {
    var importText by remember {
        mutableStateOf<String?>(null)
    }
    var editingTrip by remember {
        mutableStateOf<Trip?>(null)
    }
    var showTripManager by remember {
        mutableStateOf(false)
    }
    var tripToDelete by remember {
        mutableStateOf<Trip?>(null)
    }
    var showAddTripOptions by remember {
        mutableStateOf(false)
    }
    var importError by remember {
        mutableStateOf<String?>(null)
    }
    var cloudMessage by remember {
        mutableStateOf<String?>(null)
    }
    var cloudBusy by remember {
        mutableStateOf(false)
    }
    var showFamilyDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var inviteCode by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    val cloudScope = rememberCoroutineScope()
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                TripPackageManager.importPackage(
                    context,
                    uri
                )
            }.onSuccess { imported ->
                val copy = imported.copy(
                    id = UUID.randomUUID().toString(),
                    name = imported.name + " (מיובא)"
                )
                onStateChange(
                    state.copy(
                        trips = state.trips + copy,
                        currentTripId = copy.id
                    )
                )
            }.onFailure {
                importError =
                    it.message ?: "הייבוא נכשל"
            }
        }
    }

    val currentTrip = state.trips.firstOrNull {
        it.id == state.currentTripId
    } ?: state.trips.first()

    DisposableEffect(
        currentTrip.id,
        currentTrip.cloudEnabled,
        state.currentUser?.userId
    ) {
        if (
            !currentTrip.cloudEnabled ||
            state.currentUser == null
        ) {
            onDispose {}
        } else {
            val registration =
                cloudManager.listenToTrip(
                    tripId = currentTrip.id,
                    onTrip = { remoteTrip ->
                        if (
                            remoteTrip.updatedBy !=
                                state.currentUser.userId &&
                            remoteTrip.cloudRevision >=
                                currentTrip.cloudRevision
                        ) {
                            onStateChange(
                                state.replaceTrip(
                                    remoteTrip
                                )
                            )
                        }
                    },
                    onError = {
                        cloudMessage = it
                    }
                )

            onDispose {
                registration.remove()
            }
        }
    }

    val today = LocalDate.now()
    val startDate = runCatching {
        LocalDate.parse(currentTrip.startDate)
    }.getOrNull()
    val endDate = runCatching {
        LocalDate.parse(currentTrip.endDate)
    }.getOrNull()

    val tripStateText = when {
        startDate == null || endDate == null ->
            "תאריכי הטיול אינם מלאים"

        today.isBefore(startDate) -> {
            val days = java.time.temporal.ChronoUnit.DAYS
                .between(today, startDate)
            "עוד $days ימים לטיול"
        }

        today.isAfter(endDate) ->
            "הטיול הסתיים ✓"

        else -> {
            val dayNumber = java.time.temporal.ChronoUnit.DAYS
                .between(startDate, today) + 1
            val totalDays = java.time.temporal.ChronoUnit.DAYS
                .between(startDate, endDate) + 1
            "יום $dayNumber מתוך $totalDays"
        }
    }

    val tripProgress = when {
        startDate == null || endDate == null -> 0f
        today.isBefore(startDate) -> 0f
        today.isAfter(endDate) -> 1f
        else -> {
            val total = java.time.temporal.ChronoUnit.DAYS
                .between(startDate, endDate)
                .coerceAtLeast(1)
            val passed = java.time.temporal.ChronoUnit.DAYS
                .between(startDate, today)
            (passed.toFloat() / total.toFloat())
                .coerceIn(0f, 1f)
        }
    }

    val activeDay = currentTrip.days
        .sortedBy { it.date }
        .firstOrNull { it.date == today.toString() }
        ?: currentTrip.days
            .sortedBy { it.date }
            .firstOrNull {
                runCatching {
                    LocalDate.parse(it.date).isAfter(today)
                }.getOrDefault(false)
            }
        ?: currentTrip.days.sortedBy { it.date }.lastOrNull()

    val weather by produceState<DayWeather?>(
        initialValue = null,
        currentTrip.id,
        activeDay?.id,
        currentTrip.offlineMode
    ) {
        value = if (
            currentTrip.offlineMode ||
            activeDay == null
        ) {
            null
        } else {
            runCatching {
                WeatherService.load(
                    currentTrip,
                    activeDay
                )
            }.getOrNull()
        }
    }

    val nextFlight = currentTrip.flights
        .sortedWith(
            compareBy<Flight> {
                it.departureDate
            }.thenBy {
                it.departureTime
            }
        )
        .firstOrNull {
            it.departureDate >= today.toString()
        }

    val activeHotel = currentTrip.hotels.firstOrNull {
        runCatching {
            val checkIn = LocalDate.parse(it.checkIn)
            val checkOut = LocalDate.parse(it.checkOut)
            !today.isBefore(checkIn) &&
                !today.isAfter(checkOut)
        }.getOrDefault(false)
    } ?: currentTrip.hotels
        .sortedBy { it.checkIn }
        .firstOrNull {
            it.checkIn >= today.toString()
        }

    val expenseTotal = currentTrip.expenses.sumOf {
        it.amount
    }

    val packedCount = currentTrip.packingItems.count {
        it.packed
    }
    val packingTotal = currentTrip.packingItems.size

    val totalActivities = currentTrip.days.sumOf {
        it.activities.size
    }
    val activitiesWithoutLocation = currentTrip.days
        .flatMap { it.activities }
        .count {
            it.location.isBlank() &&
                !it.name.contains("ארוחת") &&
                !it.name.contains("מנוחה")
        }

    val missingDocuments =
        calculateMissingDocumentCount(currentTrip)
    val uncoveredHotelNights =
        calculateUncoveredHotelNights(currentTrip)
    val emptyDays = currentTrip.days.count {
        it.activities.isEmpty()
    }

    val healthIssues = buildList {
        if (missingDocuments > 0) {
            add("חסרים $missingDocuments מסמכים או שוברים")
        }
        if (uncoveredHotelNights > 0) {
            add("$uncoveredHotelNights לילות ללא מלון")
        }
        if (activitiesWithoutLocation > 0) {
            add("$activitiesWithoutLocation פעילויות ללא מיקום")
        }
        if (emptyDays > 0) {
            add("$emptyDays ימים ללא פעילויות")
        }
        if (currentTrip.flights.isEmpty()) {
            add("לא הוזנו טיסות")
        }
    }

    val healthScore = (
        100 - healthIssues.size * 12
    ).coerceIn(0, 100)

    val nextAction = calculateHomeNextAction(
        trip = currentTrip,
        activeDay = activeDay,
        today = today,
        missingDocuments = missingDocuments,
        uncoveredHotelNights = uncoveredHotelNights
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = 14.dp,
                vertical = 10.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            bottom = 28.dp
        )
    ) {
        item {
            GradientHeader(
                title = currentTrip.name,
                subtitle = currentTrip.destination,
                emoji = "🌍",
                start = Lavender,
                end = Navy
            )
        }

        item {
            OutlinedButton(
                onClick = {
                    showTripManager = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        currentTrip.name,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    Text(
                        "בחירת טיול",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Text("⌄")
            }
        }

        item {
            SectionCard(
                containerColor = SoftLavender
            ) {
                Text(
                    tripStateText,
                    style =
                        MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
                Text(
                    "${currentTrip.startDate}–${currentTrip.endDate}",
                    color = TextSecondary
                )

                LinearProgressIndicator(
                    progress = { tripProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Lavender,
                    trackColor = Color(0xFFE8E0F5)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {
                    Text(
                        "${currentTrip.days.size} ימים",
                        style =
                            MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        "${(tripProgress * 100).toInt()}%",
                        fontWeight = FontWeight.Bold,
                        color = Lavender
                    )
                }
            }
        }

        item {
            Text(
                "מצב הטיול",
                style =
                    MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Navy
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(9.dp)
            ) {
                HomeStatusCard(
                    emoji = "✈️",
                    title = "טיסה הבאה",
                    value = nextFlight?.let {
                        "${it.departureDate} ${it.departureTime}"
                    } ?: "לא הוזנה",
                    modifier = Modifier.weight(1f)
                )
                HomeStatusCard(
                    emoji = "🏨",
                    title = "מלון",
                    value = activeHotel?.name
                        ?: "לא הוזן",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(9.dp)
            ) {
                HomeStatusCard(
                    emoji = "💰",
                    title = "הוצאות",
                    value = formatHomeMoney(
                        expenseTotal,
                        currentTrip.expenses
                            .firstOrNull()
                            ?.currency
                            ?: "₪"
                    ),
                    modifier = Modifier.weight(1f)
                )
                HomeStatusCard(
                    emoji = "📄",
                    title = "מסמכים",
                    value = if (missingDocuments == 0) {
                        "${currentTrip.documents.size} מוכנים"
                    } else {
                        "$missingDocuments חסרים"
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            SectionCard(containerColor = SoftBlue) {
                Text(
                    "הפעולה הבאה",
                    style =
                        MaterialTheme.typography.labelSmall,
                    color = Sky
                )
                Text(
                    nextAction.first,
                    style =
                        MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
                Text(
                    nextAction.second,
                    color = TextSecondary
                )
            }
        }

        weather?.let { currentWeather ->
            item {
                SectionCard(
                    containerColor = SoftAqua
                ) {
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Text(
                            currentWeather.emoji,
                            style = MaterialTheme.typography
                                .headlineMedium
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "מזג האוויר ב־${currentWeather.locationName}",
                                fontWeight = FontWeight.Bold,
                                color = Navy
                            )
                            Text(
                                "${currentWeather.min}°–${currentWeather.max}° · ${currentWeather.description}",
                                color = TextSecondary
                            )
                        }
                        currentWeather.rainChance?.let {
                            Text(
                                "$it% גשם",
                                color = Aqua,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }


        item {
            SectionCard(
                containerColor = when {
                    healthScore >= 85 -> SoftMint
                    healthScore >= 60 -> SoftSun
                    else -> Color(0xFFFFE5E1)
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "מצב מוכנות הטיול",
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )
                        Text(
                            when {
                                healthScore >= 85 ->
                                    "הטיול מוכן ליציאה"
                                healthScore >= 60 ->
                                    "נשארו כמה דברים לטיפול"
                                else ->
                                    "נדרש טיפול לפני הטיול"
                            },
                            color = TextSecondary,
                            style =
                                MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        "$healthScore%",
                        style =
                            MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            healthScore >= 85 ->
                                Color(0xFF2E7D56)
                            healthScore >= 60 ->
                                Color(0xFF8F6500)
                            else -> Coral
                        }
                    )
                }

                if (healthIssues.isEmpty()) {
                    Text(
                        "✓ לא נמצאו בעיות פתוחות",
                        color = Color(0xFF2E7D56),
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    healthIssues.take(4).forEach {
                        Text(
                            "⚠ $it",
                            color = TextSecondary,
                            style =
                                MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            Text(
                "הטיול במספרים",
                style =
                    MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Navy
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                HomeStatisticCard(
                    emoji = "📍",
                    value = currentTrip.destinationStops
                        .distinct()
                        .size
                        .coerceAtLeast(1)
                        .toString(),
                    label = "יעדים",
                    modifier = Modifier.weight(1f)
                )
                HomeStatisticCard(
                    emoji = "✈️",
                    value = currentTrip.flights.size
                        .toString(),
                    label = "טיסות",
                    modifier = Modifier.weight(1f)
                )
                HomeStatisticCard(
                    emoji = "🏨",
                    value = currentTrip.hotels.size
                        .toString(),
                    label = "מלונות",
                    modifier = Modifier.weight(1f)
                )
                HomeStatisticCard(
                    emoji = "🎟️",
                    value = totalActivities.toString(),
                    label = "פעילויות",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (packingTotal > 0) {
            item {
                SectionCard(containerColor = CardWhite) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Text(
                            "🧳 אריזה",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )
                        Text(
                            "$packedCount מתוך $packingTotal",
                            color = TextSecondary
                        )
                    }
                    LinearProgressIndicator(
                        progress = {
                            packedCount.toFloat() /
                                packingTotal.toFloat()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = Aqua,
                        trackColor = SoftAqua
                    )
                }
            }
        }

        if (state.trips.size > 1) {
            item {
                Text(
                    "טיולים נוספים",
                    style =
                        MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
            }

            items(
                state.trips.filterNot {
                    it.id == currentTrip.id
                },
                key = { it.id }
            ) { trip ->
                Surface(
                    onClick = {
                        onStateChange(
                            state.copy(
                                currentTripId = trip.id
                            )
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = CardWhite,
                    border = BorderStroke(
                        1.dp,
                        Color(0xFFE3E9F0)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Text(
                            "🌍",
                            style = MaterialTheme.typography
                                .titleLarge
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                trip.name,
                                fontWeight = FontWeight.Bold,
                                color = Navy
                            )
                            Text(
                                trip.destination,
                                color = TextSecondary,
                                style =
                                    MaterialTheme.typography.bodySmall
                            )
                        }
                        Text("›", color = Sky)
                    }
                }
            }
        }

    }

    if (importText != null) {
        TextAreaDialog(
            title = "ייבוא טיול",
            initial = importText!!,
            onDismiss = {
                importText = null
            },
            onConfirm = {
                onImportTrip(it)
                importText = null
            }
        )
    }


    if (showFamilyDialog) {
        AlertDialog(
            onDismissRequest = { showFamilyDialog = false },
            title = { Text("בני משפחה") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (currentTrip.members.isEmpty()) {
                        Text("עדיין אין חברים נוספים בטיול")
                    } else {
                        currentTrip.members.forEach { member ->
                            Text("${member.displayName} · ${CloudFoundation.roleLabel(member.role)}")
                        }
                    }
                    if (inviteCode.isNotBlank()) {
                        SectionCard(containerColor = SoftMint) {
                            Text("קוד ההזמנה", fontWeight = FontWeight.Bold)
                            Text(
                                inviteCode,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Navy
                            )
                            Text("הקוד תקף לשבעה ימים")
                        }
                    }
                    if (
                        currentTrip.cloudEnabled &&
                        currentTrip.ownerUserId == state.currentUser?.userId
                    ) {
                        Button(
                            enabled = !cloudBusy,
                            onClick = {
                                val profile = state.currentUser ?: return@Button
                                cloudBusy = true
                                cloudScope.launch {
                                    runCatching {
                                        cloudManager.createInvite(
                                            currentTrip,
                                            profile,
                                            "editor"
                                        )
                                    }.onSuccess {
                                        inviteCode = it.code
                                    }.onFailure {
                                        cloudMessage = it.localizedMessage
                                    }
                                    cloudBusy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("יצירת קוד לעורך") }
                    } else if (!currentTrip.cloudEnabled) {
                        Text("יש להפעיל סנכרון לטיול לפני הזמנת חברים")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFamilyDialog = false }) {
                    Text("סגירה")
                }
            }
        )
    }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("הצטרפות לטיול") },
            text = {
                OutlinedTextField(
                    value = joinCode,
                    onValueChange = { joinCode = it.uppercase().take(6) },
                    label = { Text("קוד הזמנה") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = joinCode.length == 6 && !cloudBusy,
                    onClick = {
                        val profile = state.currentUser ?: return@TextButton
                        cloudBusy = true
                        cloudScope.launch {
                            runCatching {
                                cloudManager.joinTrip(joinCode, profile)
                            }.onSuccess { joined ->
                                val existing = state.trips.any { it.id == joined.id }
                                onStateChange(
                                    if (existing) {
                                        state.replaceTrip(joined).copy(currentTripId = joined.id)
                                    } else {
                                        state.copy(
                                            trips = state.trips + joined,
                                            currentTripId = joined.id
                                        )
                                    }
                                )
                                showJoinDialog = false
                                joinCode = ""
                                cloudMessage = "הצטרפת לטיול ${joined.name}"
                            }.onFailure {
                                cloudMessage = it.localizedMessage ?: "ההצטרפות נכשלה"
                            }
                            cloudBusy = false
                        }
                    }
                ) { Text("הצטרפות") }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) { Text("ביטול") }
            }
        )
    }

    if (showTripManager) {
        ModalBottomSheet(
            onDismissRequest = {
                showTripManager = false
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "הטיולים שלי",
                    style =
                        MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )

                state.trips.forEach { tripItem ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (
                            tripItem.id ==
                                state.currentTripId
                        ) {
                            SoftBlue
                        } else {
                            CardWhite
                        },
                        border = BorderStroke(
                            1.dp,
                            Color(0xFFE3E9F0)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(7.dp)
                        ) {
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier =
                                        Modifier.weight(1f)
                                ) {
                                    Text(
                                        tripItem.name,
                                        fontWeight =
                                            FontWeight.Bold,
                                        color = Navy
                                    )
                                    Text(
                                        "${tripItem.destination} · ${tripItem.startDate}–${tripItem.endDate}",
                                        style =
                                            MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }

                                if (
                                    tripItem.id ==
                                        state.currentTripId
                                ) {
                                    Text(
                                        "✓ נבחר",
                                        color = Sky,
                                        fontWeight =
                                            FontWeight.Bold
                                    )
                                }
                            }

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.spacedBy(6.dp)
                            ) {
                                TextButton(
                                    onClick = {
                                        onStateChange(
                                            state.copy(
                                                currentTripId =
                                                    tripItem.id
                                            )
                                        )
                                        showTripManager = false
                                    }
                                ) {
                                    Text("מעבר")
                                }

                                TextButton(
                                    onClick = {
                                        editingTrip = tripItem
                                        showTripManager = false
                                    }
                                ) {
                                    Text("עריכה")
                                }

                                TextButton(
                                    onClick = {
                                        val duplicate =
                                            tripItem.copy(
                                                id = UUID
                                                    .randomUUID()
                                                    .toString(),
                                                name =
                                                    tripItem.name +
                                                        " – עותק"
                                            )
                                        onStateChange(
                                            state.copy(
                                                trips =
                                                    state.trips +
                                                        duplicate,
                                                currentTripId =
                                                    duplicate.id
                                            )
                                        )
                                        showTripManager = false
                                    }
                                ) {
                                    Text("שכפול")
                                }

                                TextButton(
                                    onClick = {
                                        onShareTrip(tripItem)
                                    }
                                ) {
                                    Text("שיתוף")
                                }
                            }

                            TextButton(
                                enabled = state.trips.size > 1,
                                onClick = {
                                    tripToDelete = tripItem
                                    showTripManager = false
                                }
                            ) {
                                Text(
                                    if (state.trips.size > 1) {
                                        "מחיקת הטיול"
                                    } else {
                                        "לא ניתן למחוק את הטיול האחרון"
                                    },
                                    color = if (
                                        state.trips.size > 1
                                    ) {
                                        Coral
                                    } else {
                                        TextSecondary
                                    }
                                )
                            }
                        }
                    }
                }

                FilledTonalButton(
                    onClick = {
                        showTripManager = false
                        showAddTripOptions = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "+",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "הוספת טיול",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (showAddTripOptions) {
        AlertDialog(
            onDismissRequest = {
                showAddTripOptions = false
            },
            title = {
                Text("הוספת טיול")
            },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            showAddTripOptions = false
                            editingTrip = Trip(
                                id = UUID.randomUUID().toString(),
                                name = "",
                                destination = "",
                                startDate = LocalDate.now()
                                    .toString(),
                                endDate = LocalDate.now()
                                    .plusDays(1)
                                    .toString()
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("יצירת טיול חדש")
                    }

                    OutlinedButton(
                        onClick = {
                            showAddTripOptions = false
                            importLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/octet-stream"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("ייבוא מקובץ .gtrip")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddTripOptions = false
                    }
                ) {
                    Text("ביטול")
                }
            }
        )
    }

    tripToDelete?.let { deleteTrip ->
        AlertDialog(
            onDismissRequest = {
                tripToDelete = null
            },
            title = {
                Text("מחיקת טיול")
            },
            text = {
                Text(
                    "למחוק את \"${deleteTrip.name}\"? " +
                        "המסלול, ההוצאות, המסמכים " +
                        "והציוד יימחקו מהמכשיר."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val remaining =
                            state.trips.filterNot {
                                it.id == deleteTrip.id
                            }

                        if (remaining.isNotEmpty()) {
                            onStateChange(
                                state.copy(
                                    trips = remaining,
                                    currentTripId =
                                        if (
                                            state.currentTripId ==
                                                deleteTrip.id
                                        ) {
                                            remaining.first().id
                                        } else {
                                            state.currentTripId
                                        }
                                )
                            )
                        }
                        tripToDelete = null
                    }
                ) {
                    Text(
                        "מחיקה",
                        color = Coral
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        tripToDelete = null
                    }
                ) {
                    Text("ביטול")
                }
            }
        )
    }

    importError?.let { message ->
        AlertDialog(
            onDismissRequest = {
                importError = null
            },
            title = {
                Text("הייבוא נכשל")
            },
            text = {
                Text(message)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        importError = null
                    }
                ) {
                    Text("אישור")
                }
            }
        )
    }

    editingTrip?.let { tripToEdit ->
        NewTripDialog(
            existingTrip = tripToEdit,
            onDismiss = {
                editingTrip = null
            },
            onConfirm = { name, stays ->
                val normalizedStays =
                    stays.sortedBy { it.startDate }
                val generatedDays =
                    buildDaysFromDestinationStays(
                        stays = normalizedStays,
                        existingDays = tripToEdit.days
                    )
                val validDayIds =
                    generatedDays.map { it.id }.toSet()
                val destinations = normalizedStays
                    .map { it.destination }
                    .distinct()

                val updatedTrip = tripToEdit.copy(
                    name = name,
                    destination =
                        destinations.joinToString(" • "),
                    destinationStops = destinations,
                    destinationStays = normalizedStays,
                    startDate =
                        normalizedStays.first().startDate,
                    endDate =
                        normalizedStays.last().endDate,
                    days = generatedDays,
                    restaurants =
                        tripToEdit.restaurants.filter {
                            it.dayId == null ||
                                it.dayId in validDayIds
                        }
                )

                onStateChange(
                    state.replaceTrip(updatedTrip)
                )
                editingTrip = null
            }
        )
    }
}

@Composable
private fun HomeStatusCard(
    emoji: String,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(18.dp),
        color = CardWhite,
        border = BorderStroke(
            1.dp,
            Color(0xFFE3E9F0)
        ),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement =
                Arrangement.SpaceBetween
        ) {
            Text(
                emoji,
                style =
                    MaterialTheme.typography.titleLarge
            )
            Text(
                title,
                style =
                    MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                value,
                fontWeight = FontWeight.Bold,
                color = Navy,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeStatisticCard(
    emoji: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = CardWhite,
        border = BorderStroke(
            1.dp,
            Color(0xFFE3E9F0)
        )
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 6.dp,
                vertical = 10.dp
            ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Text(emoji)
            Text(
                value,
                fontWeight = FontWeight.Bold,
                color = Navy
            )
            Text(
                label,
                style =
                    MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

private fun calculateMissingDocumentCount(
    trip: Trip
): Int {
    var missing = 0

    trip.flights.forEach { flight ->
        val matched = trip.documents.any {
            it.type.contains("טיסה") ||
                it.name.contains(
                    flight.flightNumber,
                    ignoreCase = true
                )
        }
        if (!matched) {
            missing += 1
        }
    }

    trip.hotels.forEach { hotel ->
        val matched = trip.documents.any {
            it.type.contains("מלון") ||
                it.name.contains(
                    hotel.name,
                    ignoreCase = true
                )
        }
        if (!matched) {
            missing += 1
        }
    }

    return missing
}

private fun calculateUncoveredHotelNights(
    trip: Trip
): Int {
    val start = runCatching {
        LocalDate.parse(trip.startDate)
    }.getOrNull() ?: return 0
    val end = runCatching {
        LocalDate.parse(trip.endDate)
    }.getOrNull() ?: return 0

    var date = start
    var missing = 0

    while (date.isBefore(end)) {
        val covered = trip.hotels.any { hotel ->
            runCatching {
                val checkIn =
                    LocalDate.parse(hotel.checkIn)
                val checkOut =
                    LocalDate.parse(hotel.checkOut)
                !date.isBefore(checkIn) &&
                    date.isBefore(checkOut)
            }.getOrDefault(false)
        }

        if (!covered) {
            missing += 1
        }
        date = date.plusDays(1)
    }

    return missing
}

private fun calculateHomeNextAction(
    trip: Trip,
    activeDay: TripDay?,
    today: LocalDate,
    missingDocuments: Int,
    uncoveredHotelNights: Int
): Pair<String, String> {
    val liveActivity = activeDay?.activities
        ?.firstOrNull {
            it.liveStatus in setOf(
                "traveling",
                "arrived",
                "active"
            )
        }

    if (liveActivity != null) {
        return when (liveActivity.liveStatus) {
            "traveling" ->
                "📍 אשר הגעה" to liveActivity.name
            "arrived" ->
                "✅ סיים פעילות" to liveActivity.name
            else ->
                "✅ סיים פעילות" to liveActivity.name
        }
    }

    val nextActivity = activeDay?.activities
        ?.firstOrNull {
            !it.completed && !it.skipped
        }

    if (
        activeDay?.date == today.toString() &&
        nextActivity != null
    ) {
        return "🚶 צא לדרך" to
            "${nextActivity.time} · ${nextActivity.name}"
    }

    if (missingDocuments > 0) {
        return "📄 השלמת מסמכים" to
            "$missingDocuments מסמכים או שוברים חסרים"
    }

    if (uncoveredHotelNights > 0) {
        return "🏨 השלמת מלונות" to
            "$uncoveredHotelNights לילות ללא מלון"
    }

    val nextFlight = trip.flights
        .sortedBy { it.departureDate }
        .firstOrNull {
            it.departureDate >= today.toString()
        }

    if (nextFlight != null) {
        return "✈️ הטיסה הבאה" to
            "${nextFlight.departureDate} · ${nextFlight.departureTime}"
    }

    return "✓ אין פעולות דחופות" to
        "הטיול נראה מוכן"
}

private fun formatHomeMoney(
    amount: Double,
    currency: String
): String {
    return "$currency${"%,.0f".format(amount)}"
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
    var insertionIndex by remember(day.id) {
        mutableStateOf<Int?>(null)
    }

    val orderedActivities = remember(day.id) {
        mutableStateListOf<ActivityItem>().apply {
            addAll(day.activities)
        }
    }
    var draggingActivityId by remember(day.id) {
        mutableStateOf<String?>(null)
    }
    var dragStartIndex by remember(day.id) {
        mutableStateOf(-1)
    }
    var dragTargetIndex by remember(day.id) {
        mutableStateOf(-1)
    }
    var dragOffsetY by remember(day.id) {
        mutableStateOf(0f)
    }
    var draggedItemSizePx by remember(day.id) {
        mutableStateOf(0)
    }
    val timelineListState = rememberLazyListState()
    val timelineScope = rememberCoroutineScope()
    var routesRefreshing by remember(day.id) { mutableStateOf(false) }
    var routesMessage by remember(day.id) { mutableStateOf<String?>(null) }

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

    val routeInputSignature = remember(day.activities) {
        day.activities.joinToString("|") {
            listOf(
                it.id,
                it.location,
                it.latitude?.toString().orEmpty(),
                it.longitude?.toString().orEmpty(),
                it.transitionMode,
                it.transitionAutomatic.toString(),
                it.routeCacheKey
            ).joinToString(":")
        }
    }

    LaunchedEffect(day.id, routeInputSignature, trip.offlineMode) {
        if (
            !trip.offlineMode &&
            GoogleRoutesClient.isConfigured() &&
            day.activities.size >= 2 &&
            draggingActivityId == null
        ) {
            routesRefreshing = true
            val routedDay = GoogleRoutesClient.refreshDay(day)
            val normalized = validateAndNormalizeDayTimeline(routedDay)
            routesRefreshing = false

            if (normalized.activities != day.activities) {
                routesMessage = "זמני המעבר עודכנו לפי Google Maps"
                onTripChange(
                    trip.copy(
                        days = trip.days.map {
                            if (it.id == day.id) normalized else it
                        }
                    )
                )
            }
        }
    }

    fun commitDraggedActivity() {
        val fromIndex = dragStartIndex
        val toIndex = dragTargetIndex

        if (
            fromIndex !in orderedActivities.indices ||
            toIndex !in orderedActivities.indices
        ) {
            return
        }

        val reordered = orderedActivities.toMutableList()
        if (fromIndex != toIndex) {
            val moved = reordered.removeAt(fromIndex)
            reordered.add(toIndex, moved)
        }

        val updatedDay = validateAndNormalizeDayTimeline(
            day.copy(
                activities = reordered
            )
        )

        orderedActivities.clear()
        orderedActivities.addAll(updatedDay.activities)

        onTripChange(
            trip.copy(
                days = trip.days.map {
                    if (it.id == day.id) updatedDay else it
                }
            )
        )
    }

    fun resetDragState() {
        draggingActivityId = null
        dragStartIndex = -1
        dragTargetIndex = -1
        dragOffsetY = 0f
        draggedItemSizePx = 0
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
        }

        Spacer(Modifier.height(9.dp))
        WeatherCard(trip = trip, day = day, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(9.dp))

        val timelineConflict = remember(orderedActivities.toList()) {
            findTimelineConflict(orderedActivities.toList())
        }

        timelineConflict?.let { conflict ->
            SectionCard(containerColor = Color(0xFFFFE5E1)) {
                Text(
                    "⚠ התנגשות בשעות",
                    fontWeight = FontWeight.Bold,
                    color = Coral
                )
                Text(
                    conflict,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        SectionCard(
            containerColor = if (GoogleRoutesClient.isConfigured()) {
                SoftAqua
            } else {
                SoftSun
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (GoogleRoutesClient.isConfigured()) {
                            "Google Routes פעיל"
                        } else {
                            "Google Routes לא הוגדר"
                        },
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    Text(
                        routesMessage ?: if (GoogleRoutesClient.isConfigured()) {
                            "המסלולים נשמרים קבוע ומחושבים מחדש רק לאחר שינוי או רענון ידני."
                        } else {
                            "יש להגדיר ROUTES_WORKER_URL ו-ROUTES_APP_TOKEN בבנייה."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                if (routesRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else if (GoogleRoutesClient.isConfigured()) {
                    IconButton(
                        onClick = {
                            timelineScope.launch {
                                routesRefreshing = true
                                val cleared = day.copy(
                                    activities = day.activities.mapIndexed { index, item ->
                                        if (index == 0) item else item.copy(
                                            routeCacheKey = "",
                                            routeUpdatedAt = 0L
                                        )
                                    }
                                )
                                val routed = GoogleRoutesClient.refreshDay(cleared)
                                val normalized = validateAndNormalizeDayTimeline(routed)
                                routesRefreshing = false
                                routesMessage = "המסלולים חושבו מחדש ונשמרו"
                                onTripChange(
                                    trip.copy(
                                        days = trip.days.map {
                                            if (it.id == day.id) normalized else it
                                        }
                                    )
                                )
                            }
                        }
                    ) {
                        Text("↻", fontWeight = FontWeight.Bold, color = Aqua)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

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
            state = timelineListState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            itemsIndexed(
                items = orderedActivities,
                key = { _, item -> item.id }
            ) { index, activity ->
                if (index > 0) {
                    TransitionTimeCard(
                        previous = orderedActivities[index - 1],
                        current = activity,
                        onEdit = {
                            editingActivity = activity
                        },
                        onOpenUrl = onOpenUrl
                    )
                }

                TimelineInsertButton(
                    label = if (index == 0) {
                        "הוסף בתחילת היום"
                    } else {
                        "הוסף כאן"
                    },
                    onClick = {
                        insertionIndex = index
                        quickAddActivity = true
                    }
                )

                val isDragging = draggingActivityId == activity.id
                val previewShiftPx = when {
                    draggingActivityId == null ||
                        dragStartIndex < 0 ||
                        dragTargetIndex < 0 ||
                        draggedItemSizePx <= 0 -> 0

                    dragTargetIndex > dragStartIndex &&
                        index in (dragStartIndex + 1)..dragTargetIndex ->
                        -draggedItemSizePx

                    dragTargetIndex < dragStartIndex &&
                        index in dragTargetIndex until dragStartIndex ->
                        draggedItemSizePx

                    else -> 0
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 10f else 0f)
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                x = 0,
                                y = if (isDragging) {
                                    dragOffsetY.toInt()
                                } else {
                                    previewShiftPx
                                }
                            )
                        },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (activity.completed) SoftMint else CardWhite
                    ),
                    border = BorderStroke(
                        if (isDragging) 2.dp else 1.dp,
                        if (isDragging) Sky else Color(0xFFE3E9F0)
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (isDragging) 12.dp else 2.dp
                    )
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
                                    val startIndex =
                                        orderedActivities.indexOfFirst {
                                            it.id == activity.id
                                        }

                                    draggingActivityId = activity.id
                                    dragStartIndex = startIndex
                                    dragTargetIndex = startIndex
                                    dragOffsetY = 0f

                                    val visibleItem =
                                        timelineListState.layoutInfo
                                            .visibleItemsInfo
                                            .firstOrNull {
                                                it.key == activity.id
                                            }

                                    draggedItemSizePx =
                                        visibleItem?.size ?: 140
                                },
                                onDrag = { deltaY ->
                                    if (draggingActivityId != activity.id) {
                                        return@ActivityDragHandle
                                    }

                                    dragOffsetY += deltaY

                                    val layoutInfo =
                                        timelineListState.layoutInfo
                                    val draggedInfo =
                                        layoutInfo.visibleItemsInfo
                                            .firstOrNull {
                                                it.key == activity.id
                                            }

                                    val baseTop =
                                        draggedInfo?.offset
                                            ?: layoutInfo.viewportStartOffset
                                    val itemSize =
                                        draggedInfo?.size
                                            ?: draggedItemSizePx
                                                .coerceAtLeast(140)

                                    draggedItemSizePx = itemSize

                                    val draggedCenter =
                                        baseTop +
                                            dragOffsetY +
                                            itemSize / 2f

                                    val closestVisible =
                                        layoutInfo.visibleItemsInfo
                                            .filter {
                                                orderedActivities.any { item ->
                                                    item.id == it.key
                                                }
                                            }
                                            .minByOrNull { info ->
                                                kotlin.math.abs(
                                                    draggedCenter -
                                                        (
                                                            info.offset +
                                                                info.size / 2f
                                                        )
                                                )
                                            }

                                    closestVisible?.let { info ->
                                        val candidateIndex =
                                            orderedActivities.indexOfFirst {
                                                it.id == info.key
                                            }
                                        if (
                                            candidateIndex in
                                                orderedActivities.indices
                                        ) {
                                            dragTargetIndex =
                                                candidateIndex
                                        }
                                    }

                                    val topEdge =
                                        layoutInfo.viewportStartOffset + 90
                                    val bottomEdge =
                                        layoutInfo.viewportEndOffset - 90

                                    val scrollAmount = when {
                                        draggedCenter > bottomEdge ->
                                            (
                                                draggedCenter -
                                                    bottomEdge
                                                )
                                                .coerceAtMost(34f)

                                        draggedCenter < topEdge ->
                                            -(
                                                topEdge -
                                                    draggedCenter
                                                )
                                                .coerceAtMost(34f)

                                        else -> 0f
                                    }

                                    if (scrollAmount != 0f) {
                                        timelineScope.launch {
                                            val consumed =
                                                timelineListState.scrollBy(
                                                    scrollAmount
                                                )

                                            // Keep the card under the finger while
                                            // the list itself scrolls.
                                            dragOffsetY += consumed

                                            val refreshed =
                                                timelineListState.layoutInfo
                                            val lastVisibleActivity =
                                                refreshed.visibleItemsInfo
                                                    .lastOrNull {
                                                        orderedActivities.any {
                                                            item ->
                                                            item.id == it.key
                                                        }
                                                    }
                                            val firstVisibleActivity =
                                                refreshed.visibleItemsInfo
                                                    .firstOrNull {
                                                        orderedActivities.any {
                                                            item ->
                                                            item.id == it.key
                                                        }
                                                    }

                                            if (scrollAmount > 0) {
                                                lastVisibleActivity?.let {
                                                    val target =
                                                        orderedActivities
                                                            .indexOfFirst {
                                                                item ->
                                                                item.id ==
                                                                    it.key
                                                            }
                                                    if (target >= 0) {
                                                        dragTargetIndex =
                                                            target
                                                    }
                                                }
                                            } else {
                                                firstVisibleActivity?.let {
                                                    val target =
                                                        orderedActivities
                                                            .indexOfFirst {
                                                                item ->
                                                                item.id ==
                                                                    it.key
                                                            }
                                                    if (target >= 0) {
                                                        dragTargetIndex =
                                                            target
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                onDragEnd = {
                                    commitDraggedActivity()
                                    resetDragState()
                                }
                            )

                            Spacer(Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Text(
                                        activityTimeRange(activity),
                                        color = Sky,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (isFixedScheduleActivity(activity)) {
                                            SoftSun
                                        } else {
                                            SoftAqua
                                        }
                                    ) {
                                        Text(
                                            if (isFixedScheduleActivity(activity)) {
                                                "🔒 קבועה"
                                            } else {
                                                "↻ גמישה"
                                            },
                                            modifier = Modifier.padding(
                                                horizontal = 8.dp,
                                                vertical = 3.dp
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isFixedScheduleActivity(activity)) {
                                                Color(0xFF8F6500)
                                            } else {
                                                Aqua
                                            }
                                        )
                                    }
                                }
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
                                    val activityIndex =
                                        orderedActivities.indexOfFirst {
                                            it.id == activity.id
                                        }

                                    val insertIndex = if (
                                        activityIndex >= 0
                                    ) {
                                        activityIndex + 1
                                    } else {
                                        orderedActivities.size
                                    }

                                    val duplicated = activity.copy(
                                        id = UUID.randomUUID().toString(),
                                        name = "${activity.name} – עותק",
                                        time = suggestedTimeAtIndex(
                                            orderedActivities.toList(),
                                            insertIndex
                                        ),
                                        completed = false,
                                        fixedTime = false,
                                        transitionAutomatic = true,
                                        transitionMinutes = 0
                                    )

                                    val updatedActivities =
                                        orderedActivities.toMutableList()
                                    updatedActivities.add(
                                        insertIndex,
                                        duplicated
                                    )

                                    val updatedDay =
                                        validateAndNormalizeDayTimeline(
                                            day.copy(
                                                activities =
                                                    updatedActivities
                                            )
                                        )

                                    orderedActivities.clear()
                                    orderedActivities.addAll(
                                        updatedDay.activities
                                    )

                                    onTripChange(
                                        trip.copy(
                                            days = trip.days.map {
                                                if (it.id == day.id) {
                                                    updatedDay
                                                } else {
                                                    it
                                                }
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
                                    val remainingActivities =
                                        orderedActivities.filterNot {
                                            it.id == activity.id
                                        }

                                    val updatedDay =
                                        validateAndNormalizeDayTimeline(
                                            day.copy(
                                                activities =
                                                    remainingActivities
                                            )
                                        )

                                    orderedActivities.clear()
                                    orderedActivities.addAll(
                                        updatedDay.activities
                                    )

                                    onTripChange(
                                        trip.copy(
                                            days = trip.days.map {
                                                if (it.id == day.id) {
                                                    updatedDay
                                                } else {
                                                    it
                                                }
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
                TimelineInsertButton(
                    label = "הוסף פעילות לסוף היום",
                    prominent = true,
                    onClick = {
                        insertionIndex = orderedActivities.size
                        quickAddActivity = true
                    }
                )
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
                val updatedDay = validateAndNormalizeDayTimeline(
                    day.copy(
                        activities = day.activities.map {
                            if (it.id == activityId) {
                                it.copy(completed = true)
                            } else {
                                it
                            }
                        }
                    )
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
        val targetIndex = (insertionIndex ?: orderedActivities.size)
            .coerceIn(0, orderedActivities.size)
        val prefixActivities = orderedActivities
            .take(targetIndex)

        QuickActivityDialog(
            trip = trip,
            day = day.copy(activities = prefixActivities),
            initialTime = suggestedTimeAtIndex(
                orderedActivities.toList(),
                targetIndex
            ),
            onDismiss = {
                quickAddActivity = false
                insertionIndex = null
            },
            onOpenFullEditor = {
                quickAddActivity = false
                addActivity = true
            },
            onConfirm = { activity ->
                val updatedActivities = orderedActivities.toMutableList()
                updatedActivities.add(targetIndex, activity)

                val updatedDay = validateAndNormalizeDayTimeline(
                    day.copy(
                        activities = updatedActivities
                    )
                )
                onTripChange(
                    trip.copy(
                        days = trip.days.map {
                            if (it.id == day.id) updatedDay else it
                        }
                    )
                )
                quickAddActivity = false
                insertionIndex = null
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
                        day.id -> {
                            validateAndNormalizeDayTimeline(
                                tripDay.copy(
                                    activities =
                                        tripDay.activities.filterNot {
                                            it.id == activity.id
                                        }
                                )
                            )
                        }

                        targetDayId -> {
                            validateAndNormalizeDayTimeline(
                                tripDay.copy(
                                    activities =
                                        tripDay.activities +
                                            activity.copy(
                                                completed = false
                                            )
                                )
                            )
                        }

                        else -> tripDay
                    }
                }

                onTripChange(
                    trip.copy(
                        days = validateAndNormalizeTripDays(
                            updatedDays
                        )
                    )
                )
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
                val targetIndex = (insertionIndex ?: orderedActivities.size)
                    .coerceIn(0, orderedActivities.size)
                val updatedActivities = orderedActivities.toMutableList()
                updatedActivities.add(targetIndex, activity)
                val updatedDay = validateAndNormalizeDayTimeline(
                    day.copy(
                        activities = updatedActivities
                    )
                )
                onTripChange(
                    trip.copy(
                        days = trip.days.map {
                            if (it.id == day.id) updatedDay else it
                        }
                    )
                )
                addActivity = false
                insertionIndex = null
            }
        )
    }

    editingActivity?.let { activity ->
        ActivityEditorDialog(
            title = "עריכת פעילות",
            activity = activity,
            onDismiss = { editingActivity = null },
            onConfirm = { updated ->
                val editedActivities = day.activities.map {
                    if (it.id == updated.id) updated else it
                }
                val updatedDay = validateAndNormalizeDayTimeline(
                    day.copy(
                        activities = editedActivities
                    )
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
private fun TransitionTimeCard(
    previous: ActivityItem,
    current: ActivityItem,
    onEdit: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val minutes = current.transitionMinutes.coerceAtLeast(0)
    val mode = resolvedTransitionMode(previous, current)
    val icon = transitionModeIcon(mode)
    val label = transitionModeLabel(mode)

    val origin = previous.location
        .ifBlank { previous.name }
    val destination = current.location
        .ifBlank { current.name }

    val travelMode = when (mode) {
        "walk" -> "walking"
        "drive" -> "driving"
        else -> "transit"
    }

    val mapsDirectionsUrl =
        "https://www.google.com/maps/dir/?api=1" +
            "&origin=${Uri.encode(origin)}" +
            "&destination=${Uri.encode(destination)}" +
            "&travelmode=$travelMode"

    val wazeUrl =
        "https://waze.com/ul?q=" +
            Uri.encode(destination) +
            "&navigate=yes"

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SoftAqua,
        border = BorderStroke(1.dp, Color(0xFFCFE8E8))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    icon,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (minutes == 0) {
                            "ללא זמן מעבר"
                        } else {
                            "$minutes דקות מעבר"
                        },
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    Text(
                        "$label · ${previous.name} ← ${current.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                    if (current.routeDistanceMeters > 0) {
                        Text(
                            routeDistanceText(current.routeDistanceMeters) +
                                if (current.routeSource == "google") " · Google Maps" else " · הערכה",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (current.routeSource == "google") Color(0xFF2E7D56) else TextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    SmallEditIcon(Modifier.size(27.dp))
                }
            }

            if (mode == "transit") {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = CardWhite,
                    border = BorderStroke(
                        1.dp,
                        Color(0xFFD9E7EA)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 8.dp
                        ),
                        verticalArrangement =
                            Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            "פירוט תחבורה ציבורית",
                            fontWeight = FontWeight.Bold,
                            color = Navy,
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (current.transitionDetails.isNotBlank()) {
                            Text(
                                current.transitionDetails,
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                "פתח Google Maps להצגת קווים, תחנות וזמני יציאה מעודכנים.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            } else if (current.transitionDetails.isNotBlank()) {
                Text(
                    current.transitionDetails,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (current.routeStatus.isNotBlank()) {
                Text(
                    current.routeStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (current.routeSource == "google") Color(0xFF2E7D56) else Coral
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        onOpenUrl(mapsDirectionsUrl)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    GoogleMapsBrandIcon(
                        Modifier.size(25.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Maps")
                }

                FilledTonalButton(
                    onClick = {
                        onOpenUrl(wazeUrl)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    WazeBrandIcon(
                        Modifier.size(25.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Waze")
                }
            }
        }
    }
}

@Composable
private fun ActivityTimePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    supportingText: String = ""
) {
    val context = LocalContext.current
    val minutes = activityTimeMinutes(value) ?: 9 * 60

    OutlinedButton(
        onClick = {
            android.app.TimePickerDialog(
                context,
                { _, hour, minute ->
                    onValueChange("%02d:%02d".format(hour, minute))
                },
                minutes / 60,
                minutes % 60,
                true
            ).show()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text("🕒")
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
                value.ifBlank { "בחירת שעה" },
                fontWeight = FontWeight.Bold,
                color = Navy
            )
            if (supportingText.isNotBlank()) {
                Text(
                    supportingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun TimelineInsertButton(
    label: String,
    prominent: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (prominent) 5.dp else 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFFDDE5EE)
        )

        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            color = if (prominent) SoftBlue else CardWhite,
            border = BorderStroke(
                1.dp,
                if (prominent) Sky else Color(0xFFDDE5EE)
            )
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (prominent) 14.dp else 10.dp,
                    vertical = if (prominent) 8.dp else 5.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    "+",
                    color = Sky,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    label,
                    color = if (prominent) Navy else TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (prominent) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFFDDE5EE)
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
    val background = if (isDragging) Sky else SoftBlue
    val foreground = if (isDragging) Color.White else Sky

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
    initialTime: String,
    onDismiss: () -> Unit,
    onOpenFullEditor: () -> Unit,
    onConfirm: (ActivityItem) -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var selectedPreset by remember {
        mutableStateOf(activityPresets.first())
    }
    var time by remember(initialTime) {
        mutableStateOf(initialTime)
    }
    var fixedTime by remember {
        mutableStateOf(false)
    }
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf(selectedPreset.duration) }
    var transitionMode by remember { mutableStateOf("auto") }
    var transitionAutomatic by remember { mutableStateOf(true) }
    var transitionMinutesText by remember { mutableStateOf("0") }
    var transitionDetails by remember { mutableStateOf("") }
    var selectedLatitude by remember { mutableStateOf<Double?>(null) }
    var selectedLongitude by remember { mutableStateOf<Double?>(null) }
    var selectedSuggestionId by remember { mutableStateOf<String?>(null) }
    var placeSuggestions by remember {
        mutableStateOf<List<FreePlaceSuggestion>>(emptyList())
    }
    var searching by remember { mutableStateOf(false) }
    var searchRequest by remember { mutableStateOf(0) }

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
                    FreePlaceSuggestion(
                        id = "hotel-${hotel.id}",
                        name = hotel.name,
                        address = hotel.address,
                        latitude = null,
                        longitude = null,
                        source = "מלון שמור",
                        category = "hotel"
                    )
                }
                .distinctBy {
                    "${it.name.lowercase()}|${it.address.lowercase()}"
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
        trip.offlineMode,
        searchRequest
    ) {
        placeSuggestions = emptyList()

        val normalized = name.trim()

        val searchCategory = when (selectedPreset.key) {
            "meal" -> PlaceSearchCategory.RESTAURANT
            "attraction" -> PlaceSearchCategory.ATTRACTION
            "shopping" -> PlaceSearchCategory.SHOPPING
            "pool" -> PlaceSearchCategory.POOL
            "walk" -> PlaceSearchCategory.PARK
            "train" -> PlaceSearchCategory.STATION
            "transfer" -> PlaceSearchCategory.STATION
            else -> PlaceSearchCategory.GENERIC
        }

        val searchableType = selectedPreset.key in listOf(
            "attraction",
            "meal",
            "shopping",
            "pool",
            "walk",
            "train",
            "transfer"
        )

        if (
            selectedPreset.key == "hotel" ||
            !searchableType ||
            trip.offlineMode ||
            normalized.length < 2
        ) {
            searching = false
            return@LaunchedEffect
        }

        delay(650)
        searching = true
        placeSuggestions = FreePlaceSearch.search(
            query = normalized,
            destination = dayDestination,
            category = searchCategory
        )
        searching = false
    }

    fun selectPreset(preset: ActivityPreset) {
        selectedPreset = preset
        name = ""
        location = ""
        duration = preset.duration
        fixedTime = preset.key in listOf("flight", "train")
        selectedSuggestionId = null
        selectedLatitude = null
        selectedLongitude = null
        placeSuggestions = emptyList()
    }

    fun selectSuggestion(suggestion: FreePlaceSuggestion) {
        selectedSuggestionId = suggestion.id
        name = suggestion.name
        location = suggestion.address.ifBlank { suggestion.name }
        selectedLatitude = suggestion.latitude
        selectedLongitude = suggestion.longitude
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
                    ActivityTimePickerField(
                        label = "שעת התחלה",
                        value = time,
                        onValueChange = { time = it },
                        supportingText = if (fixedTime) {
                            "השעה לא תוזז בחישוב אוטומטי"
                        } else {
                            "נקבעה לפי סיום הפעילות הקודמת"
                        }
                    )
                }

                item {
                    SectionCard(
                        containerColor = if (fixedTime) {
                            SoftSun
                        } else {
                            SoftAqua
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (fixedTime) {
                                        "🔒 שעה קבועה"
                                    } else {
                                        "↻ שעה גמישה"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = Navy
                                )
                                Text(
                                    if (fixedTime) {
                                        "טיסה, הזמנה, רכבת או אירוע שלא ניתן להזיז"
                                    } else {
                                        "הפעילות תידחף אוטומטית כשמוסיפים משהו לפניה"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = fixedTime,
                                onCheckedChange = { fixedTime = it }
                            )
                        }
                    }
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
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (name.trim().length >= 2) searchRequest += 1
                                keyboardController?.hide()
                            }
                        ),
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
                                        suggestion.name,
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

                if (previousActivity != null) {
                    item {
                        SectionCard(
                            containerColor = SoftAqua
                        ) {
                            Text(
                                "זמן מעבר מהפעילות הקודמת",
                                fontWeight = FontWeight.Bold,
                                color = Navy
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "auto" to "אוטומטי",
                                    "walk" to "הליכה",
                                    "transit" to "תחבורה",
                                    "drive" to "רכב"
                                ).forEach { (value, title) ->
                                    FilterChip(
                                        selected = transitionMode == value,
                                        onClick = {
                                            transitionMode = value
                                            transitionAutomatic = true
                                        },
                                        label = { Text(title) }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "חישוב אוטומטי",
                                    modifier = Modifier.weight(1f),
                                    color = TextSecondary
                                )
                                Switch(
                                    checked = transitionAutomatic,
                                    onCheckedChange = {
                                        transitionAutomatic = it
                                    }
                                )
                            }

                            if (!transitionAutomatic) {
                                OutlinedTextField(
                                    value = transitionMinutesText,
                                    onValueChange = {
                                        transitionMinutesText =
                                            it.filter(Char::isDigit)
                                    },
                                    label = { Text("דקות מעבר") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    "הזמן יחושב לפי המרחק ואמצעי המעבר.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }

                            if (transitionMode == "transit") {
                                OutlinedTextField(
                                    value = transitionDetails,
                                    onValueChange = {
                                        transitionDetails = it
                                    },
                                    label = {
                                        Text(
                                            "קווים / תחנות / הוראות"
                                        )
                                    },
                                    supportingText = {
                                        Text(
                                            "לדוגמה: מטרו M2 מתחנת Deák Ferenc tér, ירידה ב-Batthyány tér"
                                        )
                                    },
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
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
                        selectedPreset.key == "hotel" -> {
                            trip.hotels.firstOrNull {
                                "hotel-${it.id}" == selectedSuggestionId
                            }?.mapsUrl?.takeIf { it.isNotBlank() }
                                ?: "https://www.google.com/maps/search/?api=1&query=" +
                                    Uri.encode(destinationQuery)
                        }
                        selectedSuggestion != null -> {
                            freePlaceMapsUrl(selectedSuggestion)
                        }
                        else -> {
                            "https://www.google.com/maps/search/?api=1&query=" +
                                Uri.encode(destinationQuery)
                        }
                    }

                    onConfirm(
                        ActivityItem(
                            id = UUID.randomUUID().toString(),
                            time = time.trim(),
                            name = name.trim(),
                            location = location.trim(),
                            transport = "",
                            directions = "",
                            duration = duration.trim(),
                            cost = "",
                            notes = selectedPreset.notes,
                            mapsUrl = mapsUrl,
                            completed = false,
                            fixedTime = fixedTime,
                            latitude = selectedLatitude,
                            longitude = selectedLongitude,
                            transitionMode = transitionMode,
                            transitionMinutes =
                                transitionMinutesText.toIntOrNull()
                                    ?.coerceAtLeast(0)
                                    ?: 0,
                            transitionAutomatic =
                                transitionAutomatic,
                            transitionDetails =
                                transitionDetails.trim()
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

private fun validateAndNormalizeDayTimeline(
    day: TripDay
): TripDay {
    return day.copy(
        activities = recalculateActivityTimes(day.activities)
    )
}

private fun validateAndNormalizeTripDays(
    days: List<TripDay>
): List<TripDay> {
    return days.map(::validateAndNormalizeDayTimeline)
}

private fun recalculateActivityTimes(
    activities: List<ActivityItem>
): List<ActivityItem> {
    if (activities.isEmpty()) return emptyList()

    val timelineStart = activities
        .mapNotNull { activityTimeMinutes(it.time) }
        .minOrNull()
        ?: 9 * 60

    var cursor = timelineStart
    var previous: ActivityItem? = null

    return activities.mapIndexed { index, activity ->
        val transition = if (index == 0 || previous == null) {
            0
        } else {
            resolveTransitionMinutes(
                previous = previous!!,
                current = activity
            )
        }

        if (index > 0) {
            cursor += transition
        }

        val originalStart =
            activityTimeMinutes(activity.time)
        val fixed =
            isFixedScheduleActivity(activity)

        val normalizedActivity = when {
            fixed && originalStart != null -> {
                activity.copy(
                    time = minutesToClock(originalStart),
                    transitionMinutes = transition
                )
            }

            else -> {
                activity.copy(
                    time = minutesToClock(cursor),
                    transitionMinutes = transition
                )
            }
        }

        val effectiveStart =
            activityTimeMinutes(normalizedActivity.time)
                ?: cursor

        cursor = effectiveStart +
            activityDurationMinutes(
                normalizedActivity.duration
            )

        previous = normalizedActivity
        normalizedActivity
    }
}

private fun suggestedTimeAtIndex(
    activities: List<ActivityItem>,
    index: Int
): String {
    if (index <= 0 || activities.isEmpty()) {
        return activities.firstOrNull()?.time
            ?.takeIf { it.isNotBlank() }
            ?: "09:00"
    }

    val previous = activities
        .getOrNull(index - 1)
        ?: return "09:00"

    val previousStart = activityTimeMinutes(previous.time)
        ?: 9 * 60
    val end = previousStart +
        activityDurationMinutes(previous.duration)

    return minutesToClock(end)
}

private fun activityTimeRange(
    activity: ActivityItem
): String {
    val start = activityTimeMinutes(activity.time)
        ?: return activity.time.ifBlank { "--:--" }
    val end = start + activityDurationMinutes(activity.duration)
    return "${minutesToClock(start)}–${minutesToClock(end)}"
}

private fun isFixedScheduleActivity(
    activity: ActivityItem
): Boolean {
    if (activity.fixedTime) return true

    return activity.id.startsWith("auto-flight-") ||
        activity.id.startsWith("auto-hotel-stay-") ||
        activity.name.contains("טיסה") ||
        activity.name.contains("צ׳ק־אין") ||
        activity.name.contains("צ׳ק־אאוט")
}

private fun findTimelineConflict(
    activities: List<ActivityItem>
): String? {
    var previousEnd: Int? = null
    var previousName = ""

    activities.forEach { activity ->
        val start = activityTimeMinutes(activity.time)
            ?: return@forEach
        val transition = if (previousEnd == null) {
            0
        } else {
            activity.transitionMinutes.coerceAtLeast(0)
        }
        val requiredStart = (previousEnd ?: start) + transition
        val end = start + activityDurationMinutes(activity.duration)

        val knownPreviousEnd = previousEnd
        if (
            knownPreviousEnd != null &&
            start < requiredStart &&
            isFixedScheduleActivity(activity)
        ) {
            return buildString {
                append(previousName)
                append(" מסתיימת ב־")
                append(minutesToClock(knownPreviousEnd))
                if (transition > 0) {
                    append(" ועוד ")
                    append(transition)
                    append(" דקות מעבר")
                }
                append(", אבל ")
                append(activity.name)
                append(" קבועה ל־")
                append(minutesToClock(start))
                append(". מומלץ לקצר או להעביר את הפעילות שלפניה.")
            }
        }

        previousEnd = maxOf(knownPreviousEnd ?: end, end)
        previousName = activity.name
    }

    return null
}

private fun routeDistanceText(distanceMeters: Int): String {
    return if (distanceMeters < 1000) {
        "$distanceMeters מטר"
    } else {
        "%.1f ק״מ".format(distanceMeters / 1000.0)
    }
}

private fun resolveTransitionMinutes(
    previous: ActivityItem,
    current: ActivityItem
): Int {
    if (!current.transitionAutomatic) {
        return current.transitionMinutes.coerceAtLeast(0)
    }

    if (
        current.routeSource == "google" &&
        current.routeCacheKey.isNotBlank() &&
        current.transitionMinutes > 0
    ) {
        return current.transitionMinutes
    }

    if (
        previous.location.isNotBlank() &&
        current.location.isNotBlank() &&
        previous.location.trim().equals(
            current.location.trim(),
            ignoreCase = true
        )
    ) {
        return 0
    }

    val previousCoordinates =
        activityCoordinates(previous)
    val currentCoordinates =
        activityCoordinates(current)

    val distanceKm = if (
        previousCoordinates != null &&
        currentCoordinates != null
    ) {
        haversineDistanceKm(
            previousCoordinates.first,
            previousCoordinates.second,
            currentCoordinates.first,
            currentCoordinates.second
        )
    } else {
        null
    }

    val mode = resolvedTransitionMode(
        previous,
        current,
        distanceKm
    )

    if (distanceKm == null) {
        return when (mode) {
            "walk" -> 15
            "drive" -> 20
            "transit" -> 25
            else -> 15
        }
    }

    val speedKmH = when (mode) {
        "walk" -> 4.8
        "drive" -> 28.0
        "transit" -> 18.0
        else -> 18.0
    }

    val bufferMinutes = when (mode) {
        "walk" -> 2
        "drive" -> 6
        "transit" -> 8
        else -> 5
    }

    return (
        kotlin.math.ceil(
            distanceKm / speedKmH * 60.0
        ).toInt() + bufferMinutes
    ).coerceIn(3, 180)
}

internal fun resolvedTransitionMode(
    previous: ActivityItem,
    current: ActivityItem,
    knownDistanceKm: Double? = null
): String {
    if (current.transitionMode != "auto") {
        return current.transitionMode
    }

    val distance = knownDistanceKm ?: run {
        val previousCoordinates =
            activityCoordinates(previous)
        val currentCoordinates =
            activityCoordinates(current)

        if (
            previousCoordinates != null &&
            currentCoordinates != null
        ) {
            haversineDistanceKm(
                previousCoordinates.first,
                previousCoordinates.second,
                currentCoordinates.first,
                currentCoordinates.second
            )
        } else {
            null
        }
    }

    return when {
        distance == null -> "walk"
        distance <= 1.8 -> "walk"
        distance <= 9.0 -> "transit"
        else -> "drive"
    }
}

private fun transitionModeIcon(mode: String): String =
    when (mode) {
        "walk" -> "🚶"
        "drive" -> "🚗"
        "transit" -> "🚌"
        else -> "➡️"
    }

private fun transitionModeLabel(mode: String): String =
    when (mode) {
        "walk" -> "הליכה"
        "drive" -> "נסיעה ברכב"
        "transit" -> "תחבורה ציבורית"
        else -> "מעבר"
    }

private fun activityCoordinates(
    activity: ActivityItem
): Pair<Double, Double>? {
    if (
        activity.latitude != null &&
        activity.longitude != null
    ) {
        return activity.latitude to activity.longitude
    }

    val decoded = runCatching {
        java.net.URLDecoder.decode(
            activity.mapsUrl,
            "UTF-8"
        )
    }.getOrDefault(activity.mapsUrl)

    val match = Regex(
        """query=(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)"""
    ).find(decoded) ?: return null

    val latitude =
        match.groupValues[1].toDoubleOrNull()
            ?: return null
    val longitude =
        match.groupValues[2].toDoubleOrNull()
            ?: return null

    return latitude to longitude
}

private fun haversineDistanceKm(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Double {
    val earthRadiusKm = 6371.0
    val latitudeDelta =
        Math.toRadians(lat2 - lat1)
    val longitudeDelta =
        Math.toRadians(lon2 - lon1)

    val a =
        kotlin.math.sin(latitudeDelta / 2)
            .let { it * it } +
            kotlin.math.cos(Math.toRadians(lat1)) *
            kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(longitudeDelta / 2)
                .let { it * it }

    return earthRadiusKm * 2 *
        kotlin.math.atan2(
            kotlin.math.sqrt(a),
            kotlin.math.sqrt(1 - a)
        )
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
    val targetDays = days
        .filter { it.id != currentDayId }
        .sortedBy { it.date }

    var selectedDayId by remember(targetDays) {
        mutableStateOf(targetDays.firstOrNull()?.id.orEmpty())
    }
    var menuOpen by remember { mutableStateOf(false) }

    val selectedDay = targetDays.firstOrNull {
        it.id == selectedDayId
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("העברה ליום אחר") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    activity.name,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )

                if (targetDays.isEmpty()) {
                    Text(
                        "אין תאריך נוסף בטיול שאליו אפשר להעביר את הפעילות.",
                        color = TextSecondary
                    )
                } else {
                    Text(
                        "בחר תאריך יעד",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                selectedDay?.date ?: "בחירת תאריך",
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold
                            )
                            Text("⌄")
                        }

                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            targetDays.forEach { targetDay ->
                                DropdownMenuItem(
                                    text = { Text(targetDay.date) },
                                    onClick = {
                                        selectedDayId = targetDay.id
                                        menuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    selectedDay?.destination
                        ?.takeIf { it.isNotBlank() }
                        ?.let {
                            Text(
                                "📍 $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = Aqua
                            )
                        }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedDayId.isNotBlank(),
                onClick = { onConfirm(selectedDayId) }
            ) {
                Text("העבר לתאריך")
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
    var duration by remember(activity?.id) { mutableStateOf(activity?.duration.orEmpty()) }
    var cost by remember(activity?.id) { mutableStateOf(activity?.cost.orEmpty()) }
    var notes by remember(activity?.id) { mutableStateOf(activity?.notes.orEmpty()) }
    var fixedTime by remember(activity?.id) {
        mutableStateOf(activity?.fixedTime ?: false)
    }
    var transitionMode by remember(activity?.id) {
        mutableStateOf(activity?.transitionMode ?: "auto")
    }
    var transitionAutomatic by remember(activity?.id) {
        mutableStateOf(activity?.transitionAutomatic ?: true)
    }
    var transitionMinutesText by remember(activity?.id) {
        mutableStateOf(
            (activity?.transitionMinutes ?: 0).toString()
        )
    }
    var transitionDetails by remember(activity?.id) {
        mutableStateOf(
            activity?.transitionDetails.orEmpty()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    ActivityTimePickerField(
                        label = "שעה",
                        value = time,
                        onValueChange = { time = it }
                    )
                }
                item { OutlinedTextField(name, { name = it }, label = { Text("שם הפעילות") }) }
                item { OutlinedTextField(location, { location = it }, label = { Text("מיקום") }) }
                item {
                    SectionCard(containerColor = SoftAqua) {
                        Text(
                            "מעבר מהפעילות הקודמת",
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            listOf(
                                "auto" to "אוטומטי",
                                "walk" to "הליכה",
                                "transit" to "תחבורה",
                                "drive" to "רכב"
                            ).forEach { (value, title) ->
                                FilterChip(
                                    selected = transitionMode == value,
                                    onClick = {
                                        transitionMode = value
                                        transitionAutomatic = true
                                    },
                                    label = { Text(title) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "חישוב אוטומטי",
                                modifier = Modifier.weight(1f),
                                color = TextSecondary
                            )
                            Switch(
                                checked = transitionAutomatic,
                                onCheckedChange = {
                                    transitionAutomatic = it
                                }
                            )
                        }

                        if (!transitionAutomatic) {
                            OutlinedTextField(
                                value = transitionMinutesText,
                                onValueChange = {
                                    transitionMinutesText =
                                        it.filter(Char::isDigit)
                                },
                                label = { Text("דקות מעבר") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (transitionMode == "transit") {
                            OutlinedTextField(
                                value = transitionDetails,
                                onValueChange = {
                                    transitionDetails = it
                                },
                                label = {
                                    Text(
                                        "קווים / תחנות / הוראות"
                                    )
                                },
                                supportingText = {
                                    Text(
                                        "הפירוט יוצג בכרטיס המעבר"
                                    )
                                },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            OutlinedTextField(
                                value = transitionDetails,
                                onValueChange = {
                                    transitionDetails = it
                                },
                                label = {
                                    Text("הערת מעבר")
                                },
                                minLines = 1,
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                item { OutlinedTextField(duration, { duration = it }, label = { Text("משך") }) }
                item { OutlinedTextField(cost, { cost = it }, label = { Text("עלות") }) }
                item { OutlinedTextField(notes, { notes = it }, label = { Text("הערות") }) }
                item {
                    SectionCard(
                        containerColor = if (fixedTime) SoftSun else SoftAqua
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (fixedTime) "🔒 שעה קבועה" else "↻ שעה גמישה",
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold
                            )
                            Switch(
                                checked = fixedTime,
                                onCheckedChange = { fixedTime = it }
                            )
                        }
                    }
                }
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
                            transport = "",
                            directions = "",
                            duration = duration,
                            cost = cost,
                            notes = notes,
                            mapsUrl = "https://www.google.com/maps/search/?api=1&query=" +
                                Uri.encode(location.ifBlank { name }),
                            completed = activity?.completed ?: false,
                            fixedTime = fixedTime,
                            latitude = activity?.latitude,
                            longitude = activity?.longitude,
                            transitionMode = transitionMode,
                            transitionMinutes =
                                transitionMinutesText.toIntOrNull()
                                    ?.coerceAtLeast(0)
                                    ?: 0,
                            transitionAutomatic =
                                transitionAutomatic,
                            transitionDetails =
                                transitionDetails.trim()
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
    val keyboardController = LocalSoftwareKeyboardController.current

    var name by remember(hotel?.id) {
        mutableStateOf(hotel?.name.orEmpty())
    }
    var searchText by remember(hotel?.id) {
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
    var mapsUrl by remember(hotel?.id) {
        mutableStateOf(hotel?.mapsUrl.orEmpty())
    }
    var selectedSuggestionId by remember(hotel?.id) {
        mutableStateOf<String?>(null)
    }
    var suggestions by remember {
        mutableStateOf<List<FreeHotelSuggestion>>(emptyList())
    }
    var searching by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var searchRequest by remember { mutableStateOf(0) }

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

    val destinationForSearch = remember(
        checkIn,
        trip.destinationStays,
        trip.destinationStops,
        trip.destination
    ) {
        trip.destinationStays
            .firstOrNull { stay ->
                runCatching {
                    val date = LocalDate.parse(checkIn)
                    val start = LocalDate.parse(stay.startDate)
                    val end = LocalDate.parse(stay.endDate)
                    !date.isBefore(start) && !date.isAfter(end)
                }.getOrDefault(false)
            }
            ?.destination
            ?: trip.destinationStops.firstOrNull()
            ?: trip.destination
    }

    LaunchedEffect(
        searchText,
        destinationForSearch,
        trip.offlineMode,
        searchRequest
    ) {
        suggestions = emptyList()
        searchMessage = null

        val query = searchText.trim()

        if (trip.offlineMode) {
            searching = false
            searchMessage =
                "במצב אופליין ניתן להזין את שם המלון והכתובת ידנית."
            return@LaunchedEffect
        }

        if (query.length < 2 || (query == hotel?.name && searchRequest == 0)) {
            searching = false
            return@LaunchedEffect
        }

        delay(650)
        searching = true

        val results = FreeHotelSearch.search(
            query = query,
            destination = destinationForSearch
        )

        suggestions = results
        searching = false

        if (results.isEmpty()) {
            searchMessage =
                "לא נמצאו תוצאות. נסה רק חלק משם המלון, שם באנגלית או בלי שם הרשת."
        }
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
                    SectionCard(containerColor = SoftBlue) {
                        Text(
                            "חיפוש מלון חינמי",
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )
                        Text(
                            "החיפוש משלב Photon ו-Nominatim לפי היעד: $destinationForSearch",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            "לא נדרש API Key או Billing.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D56)
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = {
                            searchText = it
                            selectedSuggestionId = null
                        },
                        label = { Text("חיפוש שם המלון") },
                        leadingIcon = { Text("🔎") },
                        trailingIcon = {
                            if (searching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        },
                        supportingText = {
                            Text("אפשר להקליד שם מלא או רק חלק ממנו")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (searchText.trim().length >= 2) searchRequest += 1
                                keyboardController?.hide()
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    FilledTonalButton(
                        enabled = searchText.trim().length >= 2 &&
                            !searching &&
                            !trip.offlineMode,
                        onClick = {
                            searchRequest += 1
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            if (searching) "מחפש..." else "חיפוש נוסף"
                        )
                    }
                }

                searchMessage?.let { message ->
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SoftSun
                        ) {
                            Text(
                                message,
                                modifier = Modifier.padding(11.dp),
                                color = Color(0xFF7D5B00),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (suggestions.isNotEmpty()) {
                    item {
                        Text(
                            "תוצאות",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(
                        suggestions,
                        key = { it.id }
                    ) { suggestion ->
                        Surface(
                            onClick = {
                                selectedSuggestionId = suggestion.id
                                name = suggestion.name
                                searchText = suggestion.name
                                address = suggestion.address
                                mapsUrl = buildFreeMapsUrl(suggestion)
                                searchMessage = null
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
                                Text("🏨")
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        suggestion.name,
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
                                    Text(
                                        "✓",
                                        color = Color(0xFF2E7D56),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            selectedSuggestionId = null
                            mapsUrl = ""
                        },
                        label = { Text("שם המלון") },
                        supportingText = {
                            Text("מתמלא אוטומטית לאחר בחירת תוצאה")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = address,
                        onValueChange = {
                            address = it
                            selectedSuggestionId = null
                            mapsUrl = ""
                        },
                        label = { Text("כתובת המלון") },
                        supportingText = {
                            Text("ניתן לתקן את הכתובת ידנית")
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
                                label = { Text("נקודת איסוף") },
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
                                label = { Text("שעת ההסעה HH:mm") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = transferMinutesText,
                                onValueChange = {
                                    transferMinutesText =
                                        it.filter(Char::isDigit)
                                },
                                label = { Text("זמן נסיעה בדקות") },
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
                    val finalMapsUrl = mapsUrl.ifBlank {
                        "https://www.google.com/maps/search/?api=1&query=" +
                            Uri.encode(address.ifBlank { name })
                    }

                    onConfirm(
                        Hotel(
                            id = hotel?.id
                                ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            checkIn = checkIn,
                            checkOut = checkOut,
                            address = address.trim(),
                            mapsUrl = finalMapsUrl,
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

private fun buildFreeMapsUrl(
    suggestion: FreeHotelSuggestion
): String {
    return if (
        suggestion.latitude != null &&
        suggestion.longitude != null
    ) {
        "https://www.google.com/maps/search/?api=1&query=" +
            Uri.encode(
                "${suggestion.latitude},${suggestion.longitude}"
            )
    } else {
        "https://www.google.com/maps/search/?api=1&query=" +
            Uri.encode(
                listOf(
                    suggestion.name,
                    suggestion.address
                )
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
            )
    }
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
        SmartRestaurantDialog(
            trip = trip,
            onDismiss = { add = false },
            onConfirm = { restaurant ->
                onTripChange(
                    trip.copy(
                        restaurants = trip.restaurants + restaurant
                    )
                )
                add = false
            }
        )
    }
}

@Composable
private fun SmartRestaurantDialog(
    trip: Trip,
    onDismiss: () -> Unit,
    onConfirm: (Restaurant) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    var selectedDayId by remember {
        mutableStateOf(trip.days.sortedBy { it.date }.firstOrNull()?.id.orEmpty())
    }
    var dayMenuOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var mapsUrl by remember { mutableStateOf("") }
    var selectedSuggestionId by remember { mutableStateOf<String?>(null) }
    var suggestions by remember {
        mutableStateOf<List<FreePlaceSuggestion>>(emptyList())
    }
    var searching by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var searchRequest by remember { mutableStateOf(0) }

    val selectedDay = trip.days.firstOrNull { it.id == selectedDayId }
    val destination = selectedDay
        ?.destination
        ?.split("→")
        ?.lastOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: trip.destinationStops.firstOrNull()
        ?: trip.destination

    LaunchedEffect(
        searchText,
        destination,
        trip.offlineMode,
        searchRequest
    ) {
        suggestions = emptyList()
        searchMessage = null

        if (trip.offlineMode) {
            searching = false
            searchMessage = "במצב אופליין אפשר להזין את הפרטים ידנית."
            return@LaunchedEffect
        }

        val query = searchText.trim()
        if (query.length < 2) {
            searching = false
            return@LaunchedEffect
        }

        delay(650)
        searching = true
        suggestions = FreePlaceSearch.search(
            query = query,
            destination = destination,
            category = PlaceSearchCategory.RESTAURANT
        )
        searching = false

        if (suggestions.isEmpty()) {
            searchMessage =
                "לא נמצאו תוצאות. נסה חלק מהשם או את שם המסעדה באנגלית."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("מסעדה חדשה") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { dayMenuOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                selectedDay?.let {
                                    "${it.date} · ${it.title}"
                                } ?: "בחירת יום",
                                modifier = Modifier.weight(1f)
                            )
                            Text("⌄")
                        }

                        DropdownMenu(
                            expanded = dayMenuOpen,
                            onDismissRequest = { dayMenuOpen = false }
                        ) {
                            trip.days.sortedBy { it.date }.forEach { day ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${day.date} · ${day.title}")
                                    },
                                    onClick = {
                                        selectedDayId = day.id
                                        dayMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    SectionCard(containerColor = SoftCoral) {
                        Text(
                            "חיפוש לפי יעד היום",
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )
                        Text(
                            destination,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = {
                            searchText = it
                            selectedSuggestionId = null
                        },
                        label = { Text("חיפוש מסעדה") },
                        leadingIcon = { Text("🍽️") },
                        trailingIcon = {
                            if (searching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        },
                        supportingText = {
                            Text("אפשר להקליד שם מלא או חלק ממנו")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (searchText.trim().length >= 2) searchRequest += 1
                                keyboardController?.hide()
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                searchMessage?.let { message ->
                    item {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                items(suggestions, key = { it.id }) { suggestion ->
                    Surface(
                        onClick = {
                            selectedSuggestionId = suggestion.id
                            name = suggestion.name
                            searchText = suggestion.name
                            address = suggestion.address
                            mapsUrl = freePlaceMapsUrl(suggestion)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (
                            selectedSuggestionId == suggestion.id
                        ) SoftMint else CardWhite,
                        border = BorderStroke(
                            if (selectedSuggestionId == suggestion.id) 2.dp else 1.dp,
                            if (selectedSuggestionId == suggestion.id) {
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
                            Text("🍴")
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    suggestion.name,
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
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("שם המסעדה") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = address,
                        onValueChange = {
                            address = it
                            mapsUrl = ""
                        },
                        label = { Text("כתובת") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = type,
                        onValueChange = { type = it },
                        label = { Text("סוג אוכל") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("רמת מחיר") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("הערה") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedDayId.isNotBlank() &&
                    name.isNotBlank() &&
                    address.isNotBlank(),
                onClick = {
                    onConfirm(
                        Restaurant(
                            id = UUID.randomUUID().toString(),
                            dayId = selectedDayId,
                            name = name.trim(),
                            area = address.trim(),
                            type = type.trim(),
                            price = price.trim(),
                            notes = notes.trim(),
                            mapsUrl = mapsUrl.ifBlank {
                                "https://www.google.com/maps/search/?api=1&query=" +
                                    Uri.encode("$name $address")
                            }
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
private fun ExpensesScreen(
    trip: Trip,
    onTripChange: (Trip) -> Unit,
    modifier: Modifier
) {
    var selectedCategory by remember {
        mutableStateOf("הכול")
    }
    var editingExpense by remember {
        mutableStateOf<Expense?>(null)
    }
    var creatingExpense by remember {
        mutableStateOf(false)
    }

    val templates = suggestedBudgetTemplates(trip)

    val normalizedExpenses = remember(
        trip.expenses,
        templates
    ) {
        val byId = trip.expenses.associateBy { it.id }
        val automatic = templates.map { template ->
            byId[template.id] ?: Expense(
                id = template.id,
                title = template.title,
                amount = 0.0,
                currency = template.currency,
                category = template.category,
                date = template.date,
                plannedAmount = 0.0,
                actualAmount = 0.0,
                paymentStatus = "מתוכנן",
                sourceType = "אוטומטי",
                sourceId = template.id,
                exchangeRateToIls = 1.0,
                exchangeRateDate = template.date
            )
        }

        val custom = trip.expenses.filterNot {
            expense ->
            templates.any { it.id == expense.id }
        }

        automatic + custom
    }

    val categories = (
        listOf(
            "טיסות",
            "מלונות",
            "אטרקציות",
            "רכב והסעות",
            "אוכל",
            "קניות",
            "תחבורה ציבורית",
            "ביטוח",
            "שונות"
        ) + normalizedExpenses.map {
            smartBudgetCategory(it.category)
        }
    ).distinct()

    val visibleExpenses = normalizedExpenses
        .map {
            if (
                it.category ==
                    smartBudgetCategory(it.category)
            ) {
                it
            } else {
                it.copy(
                    category =
                        smartBudgetCategory(it.category)
                )
            }
        }
        .filter {
            selectedCategory == "הכול" ||
                it.category == selectedCategory
        }
        .sortedWith(
            compareBy<Expense> { it.date }
                .thenBy { it.title }
        )

    val plannedIls = normalizedExpenses.sumOf {
        smartExpensePlanned(it) *
            it.exchangeRateToIls.coerceAtLeast(0.0)
    }
    val actualIls = normalizedExpenses.sumOf {
        smartExpenseActual(it) *
            it.exchangeRateToIls.coerceAtLeast(0.0)
    }
    val remainingIls =
        (plannedIls - actualIls).coerceAtLeast(0.0)

    val categoryData = categories.map { category ->
        val items = normalizedExpenses.filter {
            smartBudgetCategory(it.category) ==
                category
        }
        Triple(
            category,
            items.sumOf {
                smartExpensePlanned(it) *
                    it.exchangeRateToIls
            },
            items.sumOf {
                smartExpenseActual(it) *
                    it.exchangeRateToIls
            }
        )
    }

    val dailyTotals = normalizedExpenses
        .filter {
            smartExpenseActual(it) > 0
        }
        .groupBy { it.date }
        .mapValues { (_, items) ->
            items.sumOf {
                smartExpenseActual(it) *
                    it.exchangeRateToIls
            }
        }
        .toList()
        .sortedBy { it.first }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = 14.dp,
                vertical = 10.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(12.dp),
        contentPadding =
            PaddingValues(bottom = 28.dp)
    ) {
        item {
            GradientHeader(
                title = "תקציב חכם",
                subtitle =
                    "מתוכנן מול בפועל לפי קטגוריה ויום",
                emoji = "💰",
                start = Sun,
                end = Color(0xFFE79A18)
            )
        }

        item {
            SmartBudgetOverviewCard(
                plannedIls = plannedIls,
                actualIls = actualIls,
                remainingIls = remainingIls
            )
        }

        item {
            AccentButton(
                text = "הוצאה חדשה",
                emoji = "＋",
                onClick = {
                    creatingExpense = true
                },
                color = Color(0xFFE7A62D),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Text(
                "קטגוריות",
                style =
                    MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Navy
            )
        }

        item {
            LazyRow(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected =
                            selectedCategory == "הכול",
                        onClick = {
                            selectedCategory = "הכול"
                        },
                        label = {
                            Text("📊 הכול")
                        }
                    )
                }

                items(categoryData) {
                    (category, planned, actual) ->
                    FilterChip(
                        selected =
                            selectedCategory == category,
                        onClick = {
                            selectedCategory = category
                        },
                        label = {
                            Column {
                                Text(
                                    "${budgetCategoryEmoji(category)} $category"
                                )
                                Text(
                                    "₪${formatBudgetAmount(actual)} / ₪${formatBudgetAmount(planned)}",
                                    style =
                                        MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    )
                }
            }
        }

        item {
            Text(
                if (selectedCategory == "הכול") {
                    "כל סעיפי התקציב"
                } else {
                    selectedCategory
                },
                style =
                    MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Navy
            )
        }

        items(
            visibleExpenses,
            key = { it.id }
        ) { expense ->
            SmartBudgetItemCard(
                expense = expense,
                onEdit = {
                    editingExpense = expense
                },
                onDelete = {
                    onTripChange(
                        trip.copy(
                            expenses =
                                trip.expenses.filterNot {
                                    it.id == expense.id
                                }
                        )
                    )
                }
            )
        }

        if (visibleExpenses.isEmpty()) {
            item {
                SectionCard(
                    containerColor = CardWhite
                ) {
                    Text(
                        "אין סעיפים בקטגוריה הזו",
                        color = TextSecondary
                    )
                }
            }
        }

        if (dailyTotals.isNotEmpty()) {
            item {
                Text(
                    "הוצאות לפי יום",
                    style =
                        MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
            }

            items(dailyTotals) { (date, total) ->
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
                            .padding(12.dp),
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {
                        Text(date, color = TextSecondary)
                        Text(
                            "₪${formatBudgetAmount(total)}",
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )
                    }
                }
            }
        }
    }

    if (creatingExpense) {
        SmartExpenseDialog(
            existing = null,
            categories = categories,
            defaultCurrency =
                destinationCurrency(
                    trip.destination
                ),
            defaultDate = trip.startDate,
            onDismiss = {
                creatingExpense = false
            },
            onConfirm = { expense ->
                onTripChange(
                    trip.copy(
                        expenses =
                            trip.expenses + expense
                    )
                )
                creatingExpense = false
                selectedCategory =
                    smartBudgetCategory(
                        expense.category
                    )
            }
        )
    }

    editingExpense?.let { expense ->
        SmartExpenseDialog(
            existing = expense,
            categories = categories,
            defaultCurrency = expense.currency,
            defaultDate = expense.date,
            onDismiss = {
                editingExpense = null
            },
            onConfirm = { updated ->
                onTripChange(
                    trip.copy(
                        expenses =
                            trip.expenses
                                .filterNot {
                                    it.id == updated.id
                                } + updated
                    )
                )
                editingExpense = null
            }
        )
    }
}

@Composable
private fun SmartBudgetOverviewCard(
    plannedIls: Double,
    actualIls: Double,
    remainingIls: Double
) {
    val progress = if (plannedIls <= 0) {
        0f
    } else {
        (actualIls / plannedIls)
            .toFloat()
            .coerceIn(0f, 1.5f)
    }

    SectionCard(
        containerColor = SoftSun
    ) {
        Text(
            "תמונת מצב",
            style =
                MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Navy
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            SmartBudgetMetric(
                title = "מתוכנן",
                value =
                    "₪${formatBudgetAmount(plannedIls)}",
                modifier = Modifier.weight(1f)
            )
            SmartBudgetMetric(
                title = "שולם",
                value =
                    "₪${formatBudgetAmount(actualIls)}",
                modifier = Modifier.weight(1f)
            )
            SmartBudgetMetric(
                title = "נותר",
                value =
                    "₪${formatBudgetAmount(remainingIls)}",
                modifier = Modifier.weight(1f)
            )
        }

        LinearProgressIndicator(
            progress = {
                progress.coerceAtMost(1f)
            },
            modifier = Modifier.fillMaxWidth(),
            color = if (actualIls > plannedIls) {
                Coral
            } else {
                Color(0xFFE7A62D)
            },
            trackColor = CardWhite
        )

        if (
            plannedIls > 0 &&
            actualIls > plannedIls
        ) {
            Text(
                "⚠️ חריגה של ₪${formatBudgetAmount(actualIls - plannedIls)}",
                color = Coral,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SmartBudgetMetric(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = CardWhite
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style =
                    MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                value,
                fontWeight = FontWeight.Bold,
                color = Navy
            )
        }
    }
}

@Composable
private fun SmartBudgetItemCard(
    expense: Expense,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val planned = smartExpensePlanned(expense)
    val actual = smartExpenseActual(expense)
    val variance = actual - planned
    val statusColor = when (
        expense.paymentStatus
    ) {
        "שולם" -> Color(0xFF2E7D56)
        "שולם חלקית" -> Color(0xFF9A6A00)
        "בוטל" -> Coral
        else -> Sky
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardWhite
        ),
        border = BorderStroke(
            1.dp,
            Color(0xFFE3E9F0)
        ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement =
                Arrangement.spacedBy(9.dp)
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Text(
                    budgetCategoryEmoji(
                        smartBudgetCategory(
                            expense.category
                        )
                    ),
                    style =
                        MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.width(9.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        expense.title,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    Text(
                        "${smartBudgetCategory(expense.category)} · ${expense.date}",
                        style =
                            MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusColor.copy(
                        alpha = 0.12f
                    )
                ) {
                    Text(
                        expense.paymentStatus,
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 5.dp
                        ),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        style =
                            MaterialTheme.typography.labelSmall
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                SmartBudgetMetric(
                    title = "מתוכנן",
                    value =
                        "${expense.currency} ${formatBudgetAmount(planned)}",
                    modifier = Modifier.weight(1f)
                )
                SmartBudgetMetric(
                    title = "בפועל",
                    value =
                        "${expense.currency} ${formatBudgetAmount(actual)}",
                    modifier = Modifier.weight(1f)
                )
            }

            if (
                planned > 0 &&
                actual > 0 &&
                variance != 0.0
            ) {
                Text(
                    if (variance > 0) {
                        "חריגה: ${expense.currency} ${formatBudgetAmount(variance)}"
                    } else {
                        "חיסכון: ${expense.currency} ${formatBudgetAmount(-variance)}"
                    },
                    color = if (variance > 0) {
                        Coral
                    } else {
                        Color(0xFF2E7D56)
                    },
                    fontWeight = FontWeight.Bold,
                    style =
                        MaterialTheme.typography.bodySmall
                )
            }

            Text(
                "שער לשקל: ${expense.exchangeRateToIls} · ${expense.exchangeRateDate.ifBlank { "לא צוין" }}",
                style =
                    MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.End
            ) {
                TextButton(onClick = onEdit) {
                    Text("עריכה")
                }

                if (
                    expense.sourceType != "אוטומטי"
                ) {
                    IconButton(
                        onClick = onDelete
                    ) {
                        SmallDeleteIcon(
                            Modifier.size(27.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartExpenseDialog(
    existing: Expense?,
    categories: List<String>,
    defaultCurrency: String,
    defaultDate: String,
    onDismiss: () -> Unit,
    onConfirm: (Expense) -> Unit
) {
    var title by remember(existing?.id) {
        mutableStateOf(
            existing?.title.orEmpty()
        )
    }
    var plannedText by remember(existing?.id) {
        mutableStateOf(
            smartExpensePlanned(
                existing ?: Expense(
                    "",
                    "",
                    0.0,
                    defaultCurrency,
                    "",
                    defaultDate
                )
            )
                .takeIf { it > 0 }
                ?.toString()
                .orEmpty()
        )
    }
    var actualText by remember(existing?.id) {
        mutableStateOf(
            smartExpenseActual(
                existing ?: Expense(
                    "",
                    "",
                    0.0,
                    defaultCurrency,
                    "",
                    defaultDate
                )
            )
                .takeIf { it > 0 }
                ?.toString()
                .orEmpty()
        )
    }
    var currency by remember(existing?.id) {
        mutableStateOf(
            existing?.currency ?: defaultCurrency
        )
    }
    var category by remember(existing?.id) {
        mutableStateOf(
            smartBudgetCategory(
                existing?.category
                    ?: categories.firstOrNull()
                    ?: "שונות"
            )
        )
    }
    var date by remember(existing?.id) {
        mutableStateOf(
            existing?.date ?: defaultDate
        )
    }
    var status by remember(existing?.id) {
        mutableStateOf(
            existing?.paymentStatus
                ?: "מתוכנן"
        )
    }
    var exchangeText by remember(existing?.id) {
        mutableStateOf(
            (existing?.exchangeRateToIls
                ?: 1.0).toString()
        )
    }
    var exchangeDate by remember(existing?.id) {
        mutableStateOf(
            existing?.exchangeRateDate
                ?.ifBlank { defaultDate }
                ?: defaultDate
        )
    }
    var categoryMenu by remember {
        mutableStateOf(false)
    }
    var currencyMenu by remember {
        mutableStateOf(false)
    }
    var statusMenu by remember {
        mutableStateOf(false)
    }

    val currencies = listOf(
        defaultCurrency,
        currency,
        "ILS",
        "EUR",
        "USD",
        "GBP",
        "HUF"
    ).filter { it.isNotBlank() }.distinct()

    val statuses = listOf(
        "מתוכנן",
        "שולם",
        "שולם חלקית",
        "תשלום ביעד",
        "בוטל"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null) {
                    "הוצאה חדשה"
                } else {
                    "עריכת סעיף תקציב"
                }
            )
        },
        text = {
            LazyColumn(
                verticalArrangement =
                    Arrangement.spacedBy(9.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                        },
                        enabled =
                            existing?.sourceType !=
                                "אוטומטי",
                        label = {
                            Text("שם הסעיף")
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = plannedText,
                            onValueChange = {
                                plannedText =
                                    it.filter {
                                        char ->
                                        char.isDigit() ||
                                            char == '.'
                                    }
                            },
                            label = {
                                Text("מתוכנן")
                            },
                            modifier =
                                Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = actualText,
                            onValueChange = {
                                actualText =
                                    it.filter {
                                        char ->
                                        char.isDigit() ||
                                            char == '.'
                                    }
                            },
                            label = {
                                Text("בפועל")
                            },
                            modifier =
                                Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                categoryMenu = true
                            },
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "קטגוריה: $category",
                                modifier =
                                    Modifier.weight(1f)
                            )
                            Text("⌄")
                        }
                        DropdownMenu(
                            expanded = categoryMenu,
                            onDismissRequest = {
                                categoryMenu = false
                            }
                        ) {
                            categories.forEach {
                                option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(option)
                                    },
                                    onClick = {
                                        category = option
                                        categoryMenu =
                                            false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                currencyMenu = true
                            },
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "מטבע: $currency",
                                modifier =
                                    Modifier.weight(1f)
                            )
                            Text("⌄")
                        }
                        DropdownMenu(
                            expanded = currencyMenu,
                            onDismissRequest = {
                                currencyMenu = false
                            }
                        ) {
                            currencies.forEach {
                                option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(option)
                                    },
                                    onClick = {
                                        currency = option
                                        currencyMenu =
                                            false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                statusMenu = true
                            },
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "סטטוס: $status",
                                modifier =
                                    Modifier.weight(1f)
                            )
                            Text("⌄")
                        }
                        DropdownMenu(
                            expanded = statusMenu,
                            onDismissRequest = {
                                statusMenu = false
                            }
                        ) {
                            statuses.forEach {
                                option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(option)
                                    },
                                    onClick = {
                                        status = option
                                        statusMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = date,
                        onValueChange = {
                            date = it
                        },
                        label = {
                            Text("תאריך")
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = exchangeText,
                            onValueChange = {
                                exchangeText =
                                    it.filter {
                                        char ->
                                        char.isDigit() ||
                                            char == '.'
                                    }
                            },
                            label = {
                                Text("שער לשקל")
                            },
                            modifier =
                                Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = exchangeDate,
                            onValueChange = {
                                exchangeDate = it
                            },
                            label = {
                                Text("תאריך שער")
                            },
                            modifier =
                                Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled =
                    title.isNotBlank() &&
                        (
                            plannedText
                                .toDoubleOrNull()
                                ?: 0.0
                            ) >= 0 &&
                        (
                            actualText
                                .toDoubleOrNull()
                                ?: 0.0
                            ) >= 0,
                onClick = {
                    val planned =
                        plannedText.toDoubleOrNull()
                            ?: 0.0
                    val actual =
                        actualText.toDoubleOrNull()
                            ?: 0.0

                    onConfirm(
                        Expense(
                            id = existing?.id
                                ?: UUID.randomUUID()
                                    .toString(),
                            title = title.trim(),
                            amount = if (
                                actual > 0
                            ) {
                                actual
                            } else {
                                planned
                            },
                            currency = currency,
                            category = category,
                            date = date.ifBlank {
                                defaultDate
                            },
                            plannedAmount = planned,
                            actualAmount = actual,
                            paymentStatus = status,
                            sourceType =
                                existing?.sourceType
                                    ?: "ידני",
                            sourceId =
                                existing?.sourceId
                                    .orEmpty(),
                            exchangeRateToIls =
                                exchangeText
                                    .toDoubleOrNull()
                                    ?.coerceAtLeast(
                                        0.0
                                    )
                                    ?: 1.0,
                            exchangeRateDate =
                                exchangeDate,
                            notes =
                                existing?.notes.orEmpty()
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

private fun smartExpensePlanned(
    expense: Expense
): Double {
    return when {
        expense.plannedAmount > 0 ->
            expense.plannedAmount
        expense.actualAmount <= 0 ->
            expense.amount
        else -> 0.0
    }
}

private fun smartExpenseActual(
    expense: Expense
): Double {
    return when {
        expense.actualAmount > 0 ->
            expense.actualAmount
        expense.paymentStatus in setOf(
            "שולם",
            "שולם חלקית"
        ) -> expense.amount
        else -> 0.0
    }
}

private fun smartBudgetCategory(
    category: String
): String = when (category) {
    "תחבורה" -> "רכב והסעות"
    "כללי" -> "שונות"
    else -> category
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
    val context =
        androidx.compose.ui.platform.LocalContext.current

    var pendingRequirement by remember {
        mutableStateOf<DocumentRequirement?>(null)
    }
    var pendingPassengerName by remember {
        mutableStateOf("")
    }
    var askPassengerName by remember {
        mutableStateOf(false)
    }
    var selectedCategory by remember {
        mutableStateOf("הכול")
    }

    val requirements =
        suggestedDocumentRequirements(trip)

    val categories = listOf("הכול") +
        requirements.map { it.type }
            .distinct()
            .sorted()

    val visibleRequirements = if (
        selectedCategory == "הכול"
    ) {
        requirements
    } else {
        requirements.filter {
            it.type == selectedCategory
        }
    }

    fun matchingDocuments(
        requirement: DocumentRequirement
    ): List<TripDocument> =
        trip.documents.filter { document ->
            document.requirementKey ==
                requirement.key ||
                document.notes ==
                requirement.key ||
                document.name.contains(
                    requirement.title,
                    ignoreCase = true
                )
        }

    val completedCount = requirements.count {
        matchingDocuments(it).isNotEmpty()
    }
    val missingCount =
        requirements.size - completedCount

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val requirement = pendingRequirement

        if (uri != null && requirement != null) {
            runCatching {
                context.contentResolver
                    .takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
            }

            val baseName =
                uri.lastPathSegment ?: requirement.title
            val passenger =
                pendingPassengerName.trim()

            val finalName = if (
                requirement.supportsPassengers &&
                passenger.isNotBlank()
            ) {
                "$passenger - $baseName"
            } else {
                baseName
            }

            val document = TripDocument(
                id = UUID.randomUUID().toString(),
                name = finalName,
                uri = uri.toString(),
                type = requirement.type,
                notes = requirement.key,
                passengerName = passenger,
                requirementKey = requirement.key,
                bookingId = requirement.bookingId,
                documentRole = when {
                    requirement.type == "טיסות" ->
                        "כרטיס / אישור טיסה"
                    requirement.type == "מלונות" ->
                        "Voucher / אישור הזמנה"
                    else -> "מסמך הזמנה"
                },
                offlineAvailable = true,
                addedAt =
                    System.currentTimeMillis()
            )

            onTripChange(
                trip.copy(
                    documents =
                        trip.documents + document
                )
            )
        }

        pendingRequirement = null
        pendingPassengerName = ""
    }

    fun startDocumentUpload(
        requirement: DocumentRequirement
    ) {
        pendingRequirement = requirement

        if (requirement.supportsPassengers) {
            askPassengerName = true
        } else {
            pendingPassengerName = ""
            launcher.launch(arrayOf("*/*"))
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = 14.dp,
                vertical = 10.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(10.dp),
        contentPadding =
            PaddingValues(bottom = 28.dp)
    ) {
        item {
            GradientHeader(
                title = "מרכז מסמכים",
                subtitle =
                    "טיסות, מלונות, כרטיסים ושוברים",
                emoji = "📄",
                start = Mint,
                end = Color(0xFF378A63)
            )
        }

        item {
            SectionCard(containerColor = SoftMint) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "$completedCount מתוך ${requirements.size} מוכנים",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF276B4A)
                        )
                        Text(
                            if (missingCount == 0) {
                                "כל המסמכים הנדרשים קיימים"
                            } else {
                                "$missingCount דרישות עדיין חסרות"
                            },
                            style =
                                MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Text(
                        if (requirements.isEmpty()) {
                            "0%"
                        } else {
                            "${completedCount * 100 / requirements.size}%"
                        },
                        style =
                            MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF276B4A)
                    )
                }

                LinearProgressIndicator(
                    progress = {
                        if (requirements.isEmpty()) {
                            0f
                        } else {
                            completedCount.toFloat() /
                                requirements.size.toFloat()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = Mint,
                    trackColor = CardWhite
                )

                Text(
                    "המסמכים נשמרים עם הרשאת גישה קבועה וזמינים גם לאחר הפעלה מחדש.",
                    style =
                        MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        item {
            LazyRow(
                horizontalArrangement =
                    Arrangement.spacedBy(7.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected =
                            selectedCategory == category,
                        onClick = {
                            selectedCategory = category
                        },
                        label = {
                            Text(
                                "${documentCategoryEmoji(category)} $category"
                            )
                        }
                    )
                }
            }
        }

        val grouped = visibleRequirements
            .groupBy { it.type }

        grouped.forEach {
            (type, categoryRequirements) ->

            item(key = "header-$type") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Text(
                        documentCategoryEmoji(type),
                        style =
                            MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        type,
                        style =
                            MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    Spacer(Modifier.weight(1f))
                    val categoryReady =
                        categoryRequirements.count {
                            requirement ->
                            matchingDocuments(requirement)
                                .isNotEmpty()
                        }
                    Text(
                        "$categoryReady/${categoryRequirements.size}",
                        color = TextSecondary,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                }
            }

            items(
                categoryRequirements,
                key = { it.key }
            ) { requirement ->
                val matching =
                    matchingDocuments(requirement)

                val passengerGroups = matching
                    .filter {
                        it.passengerName.isNotBlank()
                    }
                    .groupBy { it.passengerName }

                val status = when {
                    matching.isEmpty() -> "חסר"
                    requirement.supportsPassengers &&
                        passengerGroups.isNotEmpty() &&
                        matching.size >
                            passengerGroups.size ->
                        "מלא"
                    requirement.supportsPassengers &&
                        passengerGroups.isNotEmpty() ->
                        "חלקי"
                    else -> "מלא"
                }

                val statusColor = when (status) {
                    "מלא" -> Color(0xFF2E7D56)
                    "חלקי" -> Color(0xFF9A6A00)
                    else -> Coral
                }

                val statusBackground =
                    when (status) {
                        "מלא" -> SoftMint
                        "חלקי" -> SoftSun
                        else -> CardWhite
                    }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                statusBackground
                        ),
                    border = BorderStroke(
                        1.dp,
                        when (status) {
                            "מלא" ->
                                Color(0xFFBEE6CF)
                            "חלקי" ->
                                Color(0xFFF0D38A)
                            else ->
                                Color(0xFFE3E9F0)
                        }
                    ),
                    elevation =
                        CardDefaults.cardElevation(
                            defaultElevation = 2.dp
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(9.dp)
                    ) {
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Text(
                                documentCategoryEmoji(
                                    requirement.type
                                ),
                                style =
                                    MaterialTheme.typography.titleLarge
                            )
                            Spacer(Modifier.width(9.dp))

                            Column(
                                modifier =
                                    Modifier.weight(1f)
                            ) {
                                Text(
                                    requirement.title,
                                    fontWeight =
                                        FontWeight.Bold,
                                    color = Navy
                                )
                                Text(
                                    requirement.description,
                                    style =
                                        MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )

                                if (
                                    requirement
                                        .supportsPassengers
                                ) {
                                    Text(
                                        "אפשר לצרף כמה מסמכים לכל נוסע",
                                        style =
                                            MaterialTheme.typography.labelSmall,
                                        color = Sky
                                    )
                                }
                            }

                            Surface(
                                shape =
                                    RoundedCornerShape(10.dp),
                                color = statusColor.copy(
                                    alpha = 0.12f
                                )
                            ) {
                                Text(
                                    status,
                                    modifier =
                                        Modifier.padding(
                                            horizontal = 9.dp,
                                            vertical = 5.dp
                                        ),
                                    color = statusColor,
                                    fontWeight =
                                        FontWeight.Bold,
                                    style =
                                        MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        if (matching.isNotEmpty()) {
                            if (
                                requirement.supportsPassengers &&
                                passengerGroups.isNotEmpty()
                            ) {
                                passengerGroups.forEach {
                                    (passenger, documents) ->
                                    Text(
                                        "$passenger · ${documents.size} מסמכים",
                                        fontWeight =
                                            FontWeight.Bold,
                                        color = Navy,
                                        style =
                                            MaterialTheme.typography.bodySmall
                                    )

                                    documents.forEach {
                                        document ->
                                        DocumentFileRow(
                                            document = document,
                                            onOpen = {
                                                openTripDocument(
                                                    context,
                                                    document
                                                )
                                            },
                                            onDelete = {
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
                                        )
                                    }
                                }

                                matching.filter {
                                    it.passengerName.isBlank()
                                }.forEach { document ->
                                    DocumentFileRow(
                                        document = document,
                                        onOpen = {
                                            openTripDocument(
                                                context,
                                                document
                                            )
                                        },
                                        onDelete = {
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
                                    )
                                }
                            } else {
                                matching.forEach {
                                    document ->
                                    DocumentFileRow(
                                        document = document,
                                        onOpen = {
                                            openTripDocument(
                                                context,
                                                document
                                            )
                                        },
                                        onDelete = {
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
                                    )
                                }
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                startDocumentUpload(
                                    requirement
                                )
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                            shape =
                                RoundedCornerShape(12.dp),
                            colors =
                                ButtonDefaults
                                    .filledTonalButtonColors(
                                        containerColor =
                                            CardWhite,
                                        contentColor =
                                            Color(0xFF2E7D56)
                                    )
                        ) {
                            Text(
                                when {
                                    requirement
                                        .supportsPassengers &&
                                        matching.isNotEmpty() ->
                                        "הוספת מסמך לנוסע"
                                    matching.isNotEmpty() ->
                                        "הוספת מסמך נוסף"
                                    else ->
                                        "הוספת מסמך"
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            val generalRequirement =
                DocumentRequirement(
                    key = "general-document",
                    title = "מסמך כללי",
                    type = "כללי",
                    description =
                        "קובץ שאינו משויך להזמנה"
                )

            AccentButton(
                text = "הוספת מסמך כללי",
                emoji = "＋",
                onClick = {
                    startDocumentUpload(
                        generalRequirement
                    )
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
                pendingPassengerName =
                    passengerName
                askPassengerName = false
                launcher.launch(arrayOf("*/*"))
            }
        )
    }
}

@Composable
private fun DocumentFileRow(
    document: TripDocument,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
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
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                documentFileEmoji(document.name),
                style =
                    MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    document.name,
                    style =
                        MaterialTheme.typography.bodySmall,
                    color = Navy,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        if (
                            document.documentRole
                                .isNotBlank()
                        ) {
                            append(
                                document.documentRole
                            )
                        } else {
                            append(document.type)
                        }
                        if (
                            document.offlineAvailable
                        ) {
                            append(" · זמין אופליין")
                        }
                    },
                    style =
                        MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            TextButton(onClick = onOpen) {
                Text("פתיחה")
            }

            IconButton(onClick = onDelete) {
                SmallDeleteIcon(
                    Modifier.size(27.dp)
                )
            }
        }
    }
}

private fun openTripDocument(
    context: android.content.Context,
    document: TripDocument
) {
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

private fun documentCategoryEmoji(
    category: String
): String = when (category) {
    "טיסות" -> "✈️"
    "מלונות" -> "🏨"
    "אטרקציות" -> "🎟️"
    "הסעות" -> "🚐"
    "תחבורה" -> "🚆"
    "ביטוח" -> "🛡️"
    "מסמכים אישיים" -> "🛂"
    "כללי" -> "📎"
    "הכול" -> "📚"
    else -> "📄"
}

private fun documentFileEmoji(
    name: String
): String {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".pdf") -> "📕"
        lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".png") ||
            lower.endsWith(".webp") -> "🖼️"
        else -> "📄"
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
