
package com.gal.familytrips

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime

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

    if (selectedDay == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("אין ימים בטיול")
        }
        return
    }

    val completedCount = selectedDay.activities.count { it.completed }
    val totalCount = selectedDay.activities.size
    val progress = if (totalCount == 0) 0f
    else completedCount.toFloat() / totalCount.toFloat()

    val currentMinutes = LocalTime.now().hour * 60 + LocalTime.now().minute

    val nextActivity = selectedDay.activities.firstOrNull {
        !it.completed &&
            (today.toString() != selectedDay.date ||
                activityClockMinutesToday(it.time) == null ||
                activityClockMinutesToday(it.time)!! >= currentMinutes)
    } ?: selectedDay.activities.firstOrNull { !it.completed }

    val activeHotel = trip.hotels.firstOrNull { hotel ->
        runCatching {
            val dayDate = LocalDate.parse(selectedDay.date)
            val checkIn = LocalDate.parse(hotel.checkIn)
            val checkOut = LocalDate.parse(hotel.checkOut)
            !dayDate.isBefore(checkIn) && !dayDate.isAfter(checkOut)
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
        value = if (trip.offlineMode) null
        else runCatching {
            WeatherService.load(trip, selectedDay)
        }.getOrNull()
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
            SectionCard(
                containerColor = if (nextActivity == null) SoftMint else SoftBlue
            ) {
                if (nextActivity == null) {
                    Text(
                        "כל הפעילויות הושלמו 🎉",
                        style = MaterialTheme.typography.titleMedium,
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )

                    if (nextActivity.location.isNotBlank()) {
                        Text(
                            nextActivity.location,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val destination = nextActivity.location
                                    .ifBlank { nextActivity.name }
                                onOpenUrl(
                                    "https://www.google.com/maps/dir/?api=1" +
                                        "&destination=${Uri.encode(destination)}"
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Maps")
                        }

                        FilledTonalButton(
                            onClick = {
                                val destination = nextActivity.location
                                    .ifBlank { nextActivity.name }
                                onOpenUrl(
                                    "https://waze.com/ul?q=" +
                                        Uri.encode(destination) +
                                        "&navigate=yes"
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Waze")
                        }
                    }

                    Button(
                        onClick = {
                            val updatedDay = selectedDay.copy(
                                activities = selectedDay.activities.map {
                                    if (it.id == nextActivity.id) {
                                        it.copy(completed = true)
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
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("סיימתי את הפעילות")
                    }
                }
            }
        }

        item {
            SectionCard(containerColor = CardWhite) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "התקדמות היום",
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )
                        Text(
                            "$completedCount מתוך $totalCount פעילויות",
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
                    border = BorderStroke(1.dp, Color(0xFFD6E6F8))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
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
                "המשך היום",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(
            selectedDay.activities,
            key = { it.id }
        ) { activity ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (activity.completed) SoftMint else CardWhite,
                border = BorderStroke(
                    1.dp,
                    if (activity.completed) {
                        Color(0xFFBFE5D0)
                    } else {
                        Color(0xFFE3E9F0)
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
                        color = if (activity.completed) {
                            Color(0xFFD9F3E4)
                        } else {
                            SoftBlue
                        },
                        border = BorderStroke(
                            1.dp,
                            if (activity.completed) {
                                Color(0xFFBFE5D0)
                            } else {
                                Color(0xFFD6E6F8)
                            }
                        )
                    ) {
                        Text(
                            text = activity.time.ifBlank { "--:--" },
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 7.dp
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (activity.completed) {
                                Color(0xFF2E7D56)
                            } else {
                                Sky
                            },
                            maxLines = 1
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                activity.name,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold,
                                color = Navy,
                                maxLines = 2
                            )

                            if (activity.completed) {
                                Spacer(Modifier.width(6.dp))
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
                            }
                        }

                        if (activity.location.isNotBlank()) {
                            Text(
                                activity.location,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun activityClockMinutesToday(value: String): Int? {
    val match = Regex("""(\d{1,2}):(\d{2})""").find(value)
        ?: return null

    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null

    return hour * 60 + minute
}
