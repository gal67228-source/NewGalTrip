
package com.gal.familytrips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
private data class WeatherDaily(
    val time: List<String> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int> = emptyList(),
    @SerialName("temperature_2m_max") val maxTemp: List<Double> = emptyList(),
    @SerialName("temperature_2m_min") val minTemp: List<Double> = emptyList(),
    @SerialName("precipitation_probability_max") val rainChance: List<Int?> = emptyList()
)

@Serializable
private data class WeatherResponse(
    val daily: WeatherDaily = WeatherDaily()
)

data class DayWeather(
    val emoji: String,
    val description: String,
    val min: Int,
    val max: Int,
    val rainChance: Int?,
    val locationName: String,
    val timezone: String
)

object WeatherService {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(trip: Trip, day: TripDay): DayWeather? = withContext(Dispatchers.IO) {
        val tripDate = runCatching { LocalDate.parse(day.date) }.getOrNull() ?: return@withContext null
        val daysAway = tripDate.toEpochDay() - LocalDate.now().toEpochDay()
        if (daysAway !in 0..16) return@withContext null

        val destination = DayDestinationResolver.resolve(trip, day)
            ?: return@withContext null

        val lat = destination.latitude
        val lon = destination.longitude

        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=${java.net.URLEncoder.encode(destination.timezone, "UTF-8")}" +
                "&forecast_days=16"
        )

        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
        }

        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val response = json.decodeFromString<WeatherResponse>(body)
            val index = response.daily.time.indexOf(day.date)
            if (index < 0) return@withContext null

            val code = response.daily.weatherCode.getOrNull(index) ?: 0
            val (emoji, description) = weatherText(code)
            DayWeather(
                emoji = emoji,
                description = description,
                min = response.daily.minTemp.getOrNull(index)?.toInt() ?: 0,
                max = response.daily.maxTemp.getOrNull(index)?.toInt() ?: 0,
                rainChance = response.daily.rainChance.getOrNull(index),
                locationName = listOf(
                    destination.city,
                    destination.country
                )
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(", "),
                timezone = destination.timezone
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun weatherText(code: Int): Pair<String, String> = when (code) {
        0 -> "☀️" to "בהיר"
        1, 2 -> "🌤️" to "מעונן חלקית"
        3 -> "☁️" to "מעונן"
        45, 48 -> "🌫️" to "ערפל"
        51, 53, 55, 56, 57 -> "🌦️" to "טפטוף"
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️" to "גשם"
        71, 73, 75, 77, 85, 86 -> "❄️" to "שלג"
        95, 96, 99 -> "⛈️" to "סופת רעמים"
        else -> "🌡️" to "תחזית"
    }
}

@Composable
fun DynamicClockBar(
    trip: Trip,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    val destination by produceState<DestinationInfo?>(initialValue = null, trip.destination) {
        value = DestinationResolver.resolve(trip.destination)
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(30_000)
        }
    }

    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val destinationTime = destination?.let {
        runCatching {
            now.withZoneSameInstant(ZoneId.of(it.timezone)).format(formatter)
        }.getOrDefault("--:--")
    } ?: "--:--"
    val israel = now.withZoneSameInstant(ZoneId.of("Asia/Jerusalem")).format(formatter)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ClockCard(
            destination?.flag ?: "🌍",
            destination?.city ?: trip.destination,
            destinationTime,
            Modifier.weight(1f)
        )
        ClockCard("🇮🇱", "ישראל", israel, Modifier.weight(1f))
    }
}

@Composable
fun DualClockBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ClockCard("🌍", "יעד", "--:--", Modifier.weight(1f))
        ClockCard("🇮🇱", "ישראל", "--:--", Modifier.weight(1f))
    }
}

@Composable
private fun ClockCard(flag: String, city: String, time: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = CardWhite,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE4EAF1))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(flag)
            Column {
                Text(city, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(time, fontWeight = FontWeight.Bold, color = Navy)
            }
        }
    }
}

@Composable
fun WeatherCard(trip: Trip, day: TripDay, modifier: Modifier = Modifier) {
    val routeKey = remember(day.activities, day.title, trip.destinationStops) {
        buildString {
            append(day.title)
            append("|")
            append(trip.destinationStops.joinToString(";"))
            append("|")
            append(
                day.activities.joinToString(";") {
                    "${it.name}:${it.location}"
                }
            )
        }
    }

    val weather by produceState<DayWeather?>(
        initialValue = null,
        day.date,
        trip.offlineMode,
        routeKey
    ) {
        value = if (trip.offlineMode) {
            null
        } else {
            runCatching { WeatherService.load(trip, day) }.getOrNull()
        }
    }

    val tripDate = remember(day.date) { runCatching { LocalDate.parse(day.date) }.getOrNull() }
    val daysAway = tripDate?.let { it.toEpochDay() - LocalDate.now().toEpochDay() }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = SoftBlue,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD7E8FA))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (weather != null) {
                Text(weather!!.emoji)
                Column {
                    Text(
                        weather!!.locationName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Sky,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${weather!!.min}°–${weather!!.max}° · ${weather!!.description}",
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    weather!!.rainChance?.let {
                        Text(
                            "סיכוי לגשם: $it%",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                Text("🌦️")
                Text(
                    when {
                        trip.offlineMode -> "תחזית לא מתעדכנת במצב אופליין"
                        daysAway == null -> "תחזית לא זמינה"
                        daysAway < 0 -> "היום כבר עבר"
                        daysAway > 16 -> "התחזית תופיע קרוב יותר למועד"
                        else -> "טוען תחזית…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}
