package com.gal.familytrips

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
    trip: Trip,
    onTripChange: (Trip) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val sortedDays = trip.days.sortedBy { it.date }

    val selectedDay = remember(trip.days, today) {
        sortedDays.firstOrNull { it.date == today.toString() }
            ?: sortedDays.firstOrNull {
                runCatching {
                    LocalDate.parse(it.date).isAfter(today)
                }.getOrDefault(false)
            }
            ?: sortedDays.lastOrNull()
    }

    var showDelayDialog by remember(selectedDay?.id) {
        mutableStateOf(false)
    }

    if (selectedDay == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("אין ימים בטיול")
        }
        return
    }

    val activities = selectedDay.activities
    val completedCount = activities.count {
        it.completed && !it.skipped
    }
    val skippedCount = activities.count { it.skipped }
    val totalCount = activities.size
    val progress = if (totalCount == 0) {
        0f
    } else {
        completedCount.toFloat() / totalCount.toFloat()
    }

    val selectedDayDate = runCatching {
        LocalDate.parse(selectedDay.date)
    }.getOrNull()

    val isActualToday = selectedDay.date == today.toString()
    val dayHasArrived = selectedDayDate?.let {
        !it.isAfter(today)
    } ?: false
    val isFutureDay = selectedDayDate?.isAfter(today) == true

    val nowMinutes = LocalTime.now().hour * 60 + LocalTime.now().minute

    val currentActivity = remember(
        activities,
        nowMinutes,
        isActualToday
    ) {
        activities.firstOrNull {
            it.liveStatus == "active" &&
                !it.completed &&
                !it.skipped
        } ?: if (!isActualToday) {
            null
        } else {
            activities.firstOrNull { activity ->
                if (activity.completed || activity.skipped) {
                    false
                } else {
                    val start = activityClockMinutesToday(activity.time)
                    val end = start?.plus(
                        activityDurationMinutesToday(activity.duration)
                    )
                    start != null &&
                        end != null &&
                        nowMinutes in start until end
                }
            }
        }
    }

    val nextActivity = remember(
        activities,
        currentActivity,
        nowMinutes,
        isActualToday
    ) {
        val currentIndex = currentActivity?.let { current ->
            activities.indexOfFirst { it.id == current.id }
        } ?: -1

        when {
            currentIndex >= 0 -> {
                activities.drop(currentIndex + 1)
                    .firstOrNull {
                        !it.completed && !it.skipped
                    }
            }

            isActualToday -> {
                activities.firstOrNull { activity ->
                    !activity.completed &&
                        !activity.skipped &&
                        (
                            activityClockMinutesToday(activity.time)
                                ?: Int.MAX_VALUE
                            ) >= nowMinutes
                } ?: activities.firstOrNull {
                    !it.completed && !it.skipped
                }
            }

            else -> activities.firstOrNull {
                !it.completed && !it.skipped
            }
        }
    }

    val currentOrPrevious = currentActivity ?: run {
        val nextIndex = nextActivity?.let { next ->
            activities.indexOfFirst { it.id == next.id }
        } ?: -1

        if (nextIndex > 0) {
            activities[nextIndex - 1]
        } else {
            null
        }
    }

    val departureMinutes = nextActivity?.let { next ->
        activityClockMinutesToday(next.time)
            ?.minus(next.transitionMinutes.coerceAtLeast(0))
    }

    val departureStatus = remember(
        departureMinutes,
        nowMinutes,
        isActualToday
    ) {
        if (!isActualToday || departureMinutes == null) {
            null
        } else {
            val delta = departureMinutes - nowMinutes
            when {
                delta > 1 -> "כדאי לצאת בעוד $delta דקות"
                delta in 0..1 -> "צא עכשיו"
                else -> "איחור של ${-delta} דקות"
            }
        }
    }

    val activeHotel = trip.hotels.firstOrNull { hotel ->
        runCatching {
            val dayDate = LocalDate.parse(selectedDay.date)
            val checkIn = LocalDate.parse(hotel.checkIn)
            val checkOut = LocalDate.parse(hotel.checkOut)
            !dayDate.isBefore(checkIn) &&
                !dayDate.isAfter(checkOut)
        }.getOrDefault(false)
    }

    val flightsToday = trip.flights.filter {
        it.departureDate == selectedDay.date ||
            it.arrivalDate == selectedDay.date
    }

    val weather by produceState<DayWeather?>(
        initialValue = null,
        trip.id,
        selectedDay.id,
        trip.offlineMode
    ) {
        value = if (trip.offlineMode) {
            null
        } else {
            runCatching {
                WeatherService.load(trip, selectedDay)
            }.getOrNull()
        }
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
                title = "היום",
                subtitle = "${selectedDay.title} · ${selectedDay.date}",
                emoji = "☀️",
                start = Sky,
                end = Navy
            )
        }

        item {
            DynamicClockBar(trip)
        }

        item {
            TodayProgressCard(
                completedCount = completedCount,
                skippedCount = skippedCount,
                totalCount = totalCount,
                progress = progress,
                selectedDay = selectedDay,
                sortedDays = sortedDays
            )
        }

        item {
            TodayCommandCard(
                currentActivity = currentActivity,
                nextActivity = nextActivity,
                previousActivity = currentOrPrevious,
                departureStatus = departureStatus,
                departureMinutes = departureMinutes,
                dayHasArrived = dayHasArrived,
                isFutureDay = isFutureDay,
                onOpenUrl = onOpenUrl,
                onStart = { activity ->
                    val nowText = LocalTime.now()
                        .format(DateTimeFormatter.ofPattern("HH:mm"))

                    val updatedDay = selectedDay.copy(
                        activities = selectedDay.activities.map {
                            when {
                                it.id == activity.id -> it.copy(
                                    liveStatus = "active",
                                    actualStartTime = nowText,
                                    completed = false,
                                    skipped = false
                                )
                                it.liveStatus == "active" -> it.copy(
                                    liveStatus = "waiting"
                                )
                                else -> it
                            }
                        }
                    )

                    onTripChange(
                        trip.copy(
                            days = trip.days.map {
                                if (it.id == selectedDay.id) {
                                    updatedDay
                                } else {
                                    it
                                }
                            }
                        )
                    )
                },
                onFinish = { activity ->
                    val nowText = LocalTime.now()
                        .format(DateTimeFormatter.ofPattern("HH:mm"))

                    val updatedDay = selectedDay.copy(
                        activities = selectedDay.activities.map {
                            if (it.id == activity.id) {
                                it.copy(
                                    liveStatus = "completed",
                                    actualEndTime = nowText,
                                    completed = true,
                                    skipped = false
                                )
                            } else {
                                it
                            }
                        }
                    )

                    onTripChange(
                        trip.copy(
                            days = trip.days.map {
                                if (it.id == selectedDay.id) {
                                    updatedDay
                                } else {
                                    it
                                }
                            }
                        )
                    )
                },
                onSkip = { activity ->
                    val index = selectedDay.activities.indexOfFirst {
                        it.id == activity.id
                    }
                    val updatedActivities = selectedDay.activities
                        .map {
                            if (it.id == activity.id) {
                                it.copy(
                                    liveStatus = "skipped",
                                    skipped = true,
                                    completed = false,
                                    actualEndTime = LocalTime.now()
                                        .format(
                                            DateTimeFormatter.ofPattern(
                                                "HH:mm"
                                            )
                                        )
                                )
                            } else {
                                it
                            }
                        }
                        .toMutableList()

                    if (index >= 0) {
                        updatedActivities.removeAt(index)
                        updatedActivities.add(
                            index,
                            activity.copy(
                                liveStatus = "skipped",
                                skipped = true,
                                completed = false
                            )
                        )
                    }

                    val normalized = normalizeLiveTimeline(
                        updatedActivities
                    )

                    val updatedDay = selectedDay.copy(
                        activities = normalized
                    )

                    onTripChange(
                        trip.copy(
                            days = trip.days.map {
                                if (it.id == selectedDay.id) {
                                    updatedDay
                                } else {
                                    it
                                }
                            }
                        )
                    )
                },
                onDelay = {
                    showDelayDialog = true
                }
            )
        }

        weather?.let { currentWeather ->
            item {
                SectionCard(containerColor = SoftAqua) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            currentWeather.emoji,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                currentWeather.locationName,
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

        activeHotel?.let { hotel ->
            item {
                SectionCard(containerColor = SoftMint) {
                    Text(
                        "המלון היום",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D56)
                    )
                    Text(
                        hotel.name,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    Text(
                        hotel.address,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (flightsToday.isNotEmpty()) {
            item {
                Text(
                    "טיסות היום",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(flightsToday, key = { it.id }) { flight ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SoftBlue,
                    border = BorderStroke(
                        1.dp,
                        Color(0xFFD6E6F8)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "✈️ ${flight.flightNumber.ifBlank { "טיסה" }}",
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )
                        Text(
                            "${flight.departureAirport} → ${flight.arrivalAirport}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            "${flight.departureTime}–${flight.arrivalTime}",
                            color = Sky,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Text(
                "ציר היום",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(
            activities,
            key = { it.id }
        ) { activity ->
            TodayActivityRow(
                activity = activity,
                isCurrent = currentActivity?.id == activity.id,
                isNext = nextActivity?.id == activity.id,
                canResetCompletion = canResetCompletionToday(
                    activity = activity,
                    selectedDayDate = selectedDayDate,
                    today = today,
                    nowMinutes = nowMinutes
                ),
                onResetCompletion = {
                    val updatedDay = selectedDay.copy(
                        activities = selectedDay.activities.map {
                            if (it.id == activity.id) {
                                it.copy(completed = false)
                            } else {
                                it
                            }
                        }
                    )

                    onTripChange(
                        trip.copy(
                            days = trip.days.map {
                                if (it.id == selectedDay.id) {
                                    updatedDay
                                } else {
                                    it
                                }
                            }
                        )
                    )
                }
            )
        }
    }

    if (showDelayDialog) {
        DelayDayDialog(
            onDismiss = {
                showDelayDialog = false
            },
            onConfirm = { delayMinutes ->
                val updatedDay = selectedDay.copy(
                    activities = applyLiveDelay(
                        activities = selectedDay.activities,
                        delayMinutes = delayMinutes
                    )
                )

                onTripChange(
                    trip.copy(
                        days = trip.days.map {
                            if (it.id == selectedDay.id) {
                                updatedDay
                            } else {
                                it
                            }
                        }
                    )
                )
                showDelayDialog = false
            }
        )
    }
}

@Composable
private fun DelayDayDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var customMinutes by remember {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("כמה זמן האיחור?")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(10, 15, 30, 45, 60).forEach { minutes ->
                    OutlinedButton(
                        onClick = {
                            onConfirm(minutes)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("$minutes דקות")
                    }
                }

                OutlinedTextField(
                    value = customMinutes,
                    onValueChange = {
                        customMinutes = it.filter(Char::isDigit)
                    },
                    label = {
                        Text("זמן מותאם בדקות")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = (
                    customMinutes.toIntOrNull()
                        ?: 0
                    ) > 0,
                onClick = {
                    onConfirm(
                        customMinutes.toIntOrNull()
                            ?.coerceAtLeast(1)
                            ?: 1
                    )
                }
            ) {
                Text("החל")
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
private fun TodayProgressCard(
    completedCount: Int,
    skippedCount: Int,
    totalCount: Int,
    progress: Float,
    selectedDay: TripDay,
    sortedDays: List<TripDay>
) {
    val dayNumber = sortedDays.indexOfFirst {
        it.id == selectedDay.id
    }.let { if (it >= 0) it + 1 else 1 }

    SectionCard(containerColor = CardWhite) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "יום $dayNumber מתוך ${sortedDays.size}",
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
                Text(
                    "$completedCount הושלמו · $skippedCount דולגו · $totalCount סה״כ",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                "${(progress * 100).toInt()}%",
                fontWeight = FontWeight.Bold,
                color = Sky
            )
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = Sky,
            trackColor = SoftBlue
        )
    }
}

@Composable
private fun TodayCommandCard(
    currentActivity: ActivityItem?,
    nextActivity: ActivityItem?,
    previousActivity: ActivityItem?,
    departureStatus: String?,
    departureMinutes: Int?,
    dayHasArrived: Boolean,
    isFutureDay: Boolean,
    onOpenUrl: (String) -> Unit,
    onStart: (ActivityItem) -> Unit,
    onFinish: (ActivityItem) -> Unit,
    onSkip: (ActivityItem) -> Unit,
    onDelay: () -> Unit
) {
    SectionCard(
        containerColor = when {
            currentActivity != null -> SoftBlue
            nextActivity != null -> SoftAqua
            else -> SoftMint
        }
    ) {
        if (currentActivity == null && nextActivity == null) {
            Text(
                "כל הפעילויות הושלמו 🎉",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D56)
            )
            return@SectionCard
        }

        currentActivity?.let { current ->
            Text(
                "עכשיו",
                style = MaterialTheme.typography.labelSmall,
                color = Sky
            )
            Text(
                "${activityTimeRangeToday(current)} · ${current.name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Navy
            )
            if (current.location.isNotBlank()) {
                Text(
                    current.location,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (currentActivity == null && nextActivity != null) {
            Text(
                "הפעילות הקרובה",
                style = MaterialTheme.typography.labelSmall,
                color = Sky
            )
            Text(
                "${activityTimeRangeToday(nextActivity)} · ${nextActivity.name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Navy
            )
        }

        nextActivity?.let { next ->
            HorizontalDivider(
                color = Color(0xFFD6E6F8)
            )

            Text(
                "הבא",
                style = MaterialTheme.typography.labelSmall,
                color = Aqua
            )
            Text(
                "${next.time} · ${next.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Navy
            )

            if (next.transitionMinutes > 0) {
                Text(
                    "${transitionEmoji(next.transitionMode)} ${next.transitionMinutes} דקות מעבר",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            departureMinutes?.let {
                Text(
                    "שעת יציאה מומלצת: ${minutesToClockToday(it)}",
                    color = Navy,
                    fontWeight = FontWeight.Bold
                )
            }

            departureStatus?.let { status ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        status.startsWith("צא עכשיו") ->
                            Color(0xFFD9F3E4)
                        status.startsWith("איחור") ->
                            Color(0xFFFFE5E1)
                        else ->
                            SoftSun
                    }
                ) {
                    Text(
                        status,
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 7.dp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = when {
                            status.startsWith("צא עכשיו") ->
                                Color(0xFF2E7D56)
                            status.startsWith("איחור") ->
                                Coral
                            else ->
                                Color(0xFF8F6500)
                        }
                    )
                }
            }

            val origin = previousActivity
                ?.location
                ?.ifBlank { previousActivity.name }
                .orEmpty()
            val destination = next.location
                .ifBlank { next.name }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        onOpenUrl(
                            "https://www.google.com/maps/dir/?api=1" +
                                (
                                    if (origin.isNotBlank()) {
                                        "&origin=${Uri.encode(origin)}"
                                    } else {
                                        ""
                                    }
                                    ) +
                                "&destination=${Uri.encode(destination)}"
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    GoogleMapsBrandIcon(Modifier.size(24.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Maps")
                }

                FilledTonalButton(
                    onClick = {
                        onOpenUrl(
                            "https://waze.com/ul?q=" +
                                Uri.encode(destination) +
                                "&navigate=yes"
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    WazeBrandIcon(Modifier.size(24.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Waze")
                }
            }
        }

        val actionActivity =
            currentActivity ?: nextActivity

        actionActivity?.let { activity ->
            if (!dayHasArrived) {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        if (isFutureDay) {
                            "הפעולות ייפתחו ביום הפעילות"
                        } else {
                            "לא ניתן לעדכן פעילות"
                        }
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (activity.liveStatus != "active") {
                        Button(
                            onClick = { onStart(activity) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("התחל")
                        }
                    } else {
                        Button(
                            onClick = { onFinish(activity) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("סיים")
                        }
                    }

                    OutlinedButton(
                        onClick = { onSkip(activity) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("דלג")
                    }
                }

                OutlinedButton(
                    onClick = onDelay,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("אני מאחר")
                }
            }
        }

    }
}

@Composable
private fun TodayActivityRow(
    activity: ActivityItem,
    isCurrent: Boolean,
    isNext: Boolean,
    canResetCompletion: Boolean,
    onResetCompletion: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = when {
            activity.completed -> SoftMint
            isCurrent -> SoftBlue
            isNext -> SoftAqua
            else -> CardWhite
        },
        border = BorderStroke(
            if (isCurrent || isNext) 2.dp else 1.dp,
            when {
                activity.completed -> Color(0xFFBFE5D0)
                isCurrent -> Sky
                isNext -> Aqua
                else -> Color(0xFFE3E9F0)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    activity.completed -> Color(0xFFD9F3E4)
                    isCurrent -> Color(0xFFDCEBFB)
                    else -> SoftBlue
                }
            ) {
                Text(
                    text = activity.time.ifBlank { "--:--" },
                    modifier = Modifier.padding(
                        horizontal = 10.dp,
                        vertical = 7.dp
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        activity.completed -> Color(0xFF2E7D56)
                        else -> Sky
                    },
                    maxLines = 1
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    activity.name,
                    fontWeight = FontWeight.Bold,
                    color = Navy,
                    maxLines = 2
                )

                if (activity.location.isNotBlank()) {
                    Text(
                        activity.location,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 2
                    )
                }

                when {
                    activity.skipped -> Text(
                        "דולגה",
                        color = Coral,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    activity.completed -> Text(
                        "הושלמה",
                        color = Color(0xFF2E7D56),
                        style = MaterialTheme.typography.labelSmall
                    )
                    activity.liveStatus == "active" || isCurrent -> Text(
                        "מתבצעת עכשיו",
                        color = Sky,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    isNext -> Text(
                        "הפעילות הבאה",
                        color = Aqua,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    else -> Text(
                        "ממתינה",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (
                    activity.actualStartTime.isNotBlank() ||
                    activity.actualEndTime.isNotBlank()
                ) {
                    Text(
                        buildString {
                            append("בפועל: ")
                            append(
                                activity.actualStartTime.ifBlank {
                                    "--:--"
                                }
                            )
                            append("–")
                            append(
                                activity.actualEndTime.ifBlank {
                                    "--:--"
                                }
                            )
                        },
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (activity.completed) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFD9F3E4)
                    ) {
                        Text(
                            "✓",
                            modifier = Modifier.padding(
                                horizontal = 8.dp,
                                vertical = 4.dp
                            ),
                            color = Color(0xFF2E7D56),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (canResetCompletion) {
                        TextButton(
                            onClick = onResetCompletion,
                            contentPadding = PaddingValues(
                                horizontal = 6.dp,
                                vertical = 0.dp
                            )
                        ) {
                            Text(
                                "איפוס",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun applyLiveDelay(
    activities: List<ActivityItem>,
    delayMinutes: Int
): List<ActivityItem> {
    val delay = delayMinutes.coerceAtLeast(0)
    var delayApplied = false

    return activities.map { activity ->
        when {
            activity.completed || activity.skipped ->
                activity

            isFixedActivityToday(activity) ->
                activity

            else -> {
                val original =
                    activityClockMinutesToday(activity.time)
                        ?: return@map activity

                val shifted = original + delay
                delayApplied = true

                activity.copy(
                    time = minutesToClockToday(shifted)
                )
            }
        }
    }.let {
        if (delayApplied) normalizeLiveTimeline(it) else it
    }
}

private fun normalizeLiveTimeline(
    activities: List<ActivityItem>
): List<ActivityItem> {
    if (activities.isEmpty()) {
        return emptyList()
    }

    val activeActivities = activities.filterNot {
        it.skipped
    }

    if (activeActivities.isEmpty()) {
        return activities
    }

    val firstStart = activeActivities
        .mapNotNull {
            activityClockMinutesToday(it.time)
        }
        .minOrNull()
        ?: 9 * 60

    var cursor = firstStart

    val normalizedById = activeActivities.associate { activity ->
        val fixed = isFixedActivityToday(activity)
        val originalStart =
            activityClockMinutesToday(activity.time)

        val normalized = if (
            fixed && originalStart != null
        ) {
            activity
        } else {
            activity.copy(
                time = minutesToClockToday(cursor)
            )
        }

        val effectiveStart =
            activityClockMinutesToday(normalized.time)
                ?: cursor

        cursor = effectiveStart +
            activityDurationMinutesToday(
                normalized.duration
            ) +
            normalized.transitionMinutes.coerceAtLeast(0)

        activity.id to normalized
    }

    return activities.map { activity ->
        normalizedById[activity.id] ?: activity
    }
}

private fun isFixedActivityToday(
    activity: ActivityItem
): Boolean {
    return activity.fixedTime ||
        activity.name.contains("טיסה") ||
        activity.name.contains("צ׳ק־אין") ||
        activity.name.contains("צ׳ק־אאוט")
}

private fun canResetCompletionToday(
    activity: ActivityItem,
    selectedDayDate: LocalDate?,
    today: LocalDate,
    nowMinutes: Int
): Boolean {
    if (!activity.completed || selectedDayDate == null) {
        return false
    }

    if (selectedDayDate.isAfter(today)) {
        return true
    }

    if (selectedDayDate.isBefore(today)) {
        return false
    }

    val endMinutes =
        activityClockMinutesToday(activity.time)
            ?.plus(
                activityDurationMinutesToday(
                    activity.duration
                )
            )
            ?: return false

    return nowMinutes < endMinutes
}

private fun activityClockMinutesToday(value: String): Int? {
    val match = Regex("""(\d{1,2}):(\d{2})""").find(value)
        ?: return null

    val hour = match.groupValues[1].toIntOrNull()
        ?: return null
    val minute = match.groupValues[2].toIntOrNull()
        ?: return null

    return hour * 60 + minute
}

private fun activityDurationMinutesToday(value: String): Int {
    val normalized = value.trim().lowercase()

    val hours = Regex("""(\d+(?:\.\d+)?)\s*(?:שעות|שעה|hours?|hrs?|h)""")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.toDoubleOrNull()
        ?: 0.0

    val minutes = Regex("""(\d+)\s*(?:דקות|דקה|minutes?|mins?|m)""")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: 0

    val total = (hours * 60).toInt() + minutes
    return if (total > 0) total else 60
}

private fun activityTimeRangeToday(
    activity: ActivityItem
): String {
    val start = activityClockMinutesToday(activity.time)
        ?: return activity.time.ifBlank { "--:--" }
    val end = start +
        activityDurationMinutesToday(activity.duration)

    return "${minutesToClockToday(start)}–${minutesToClockToday(end)}"
}

private fun minutesToClockToday(value: Int): String {
    val normalized = ((value % (24 * 60)) + (24 * 60)) % (24 * 60)
    return "%02d:%02d".format(
        normalized / 60,
        normalized % 60
    )
}

private fun transitionEmoji(mode: String): String =
    when (mode) {
        "walk" -> "🚶"
        "drive" -> "🚗"
        "transit" -> "🚌"
        else -> "➡️"
    }
