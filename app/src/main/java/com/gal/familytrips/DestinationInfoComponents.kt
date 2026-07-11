
package com.gal.familytrips

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneId
import java.util.Currency
import java.util.Locale

@Serializable
private data class GeoResult(
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timezone: String = "UTC",
    val country: String = "",
    @SerialName("country_code") val countryCode: String = ""
)

@Serializable
private data class GeoResponse(
    val results: List<GeoResult> = emptyList()
)

@Serializable
private data class RateResponse(
    val rate: Double = 0.0
)

data class DestinationInfo(
    val city: String,
    val country: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val currencyCode: String,
    val currencySymbol: String,
    val language: String,
    val callingCode: String,
    val emergencyNumber: String,
    val plugType: String,
    val drivingSide: String,
    val flag: String,
    val phrases: List<Pair<String, String>>,
    val tips: List<String>
)

object DestinationResolver {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, DestinationInfo>()

    suspend fun resolve(destination: String): DestinationInfo? = withContext(Dispatchers.IO) {
        cache[destination]?.let { return@withContext it }

        val query = destination
            .substringBefore(",")
            .trim()
            .ifBlank { destination.trim() }

        val encoded = Uri.encode(query)
        val endpoint =
            "https://geocoding-api.open-meteo.com/v1/search" +
                "?name=$encoded&count=1&language=en&format=json"

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
        }

        try {
            if (connection.responseCode !in 200..299) {
                return@withContext fallback(destination)
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val geo = json.decodeFromString<GeoResponse>(body).results.firstOrNull()
                ?: return@withContext fallback(destination)

            val code = geo.countryCode.uppercase()
            val currency = currencyFor(code)
            val info = DestinationInfo(
                city = geo.name.ifBlank { query },
                country = geo.country.ifBlank { destination.substringAfter(",", "").trim() },
                countryCode = code,
                latitude = geo.latitude,
                longitude = geo.longitude,
                timezone = geo.timezone.ifBlank { "UTC" },
                currencyCode = currency?.currencyCode ?: "USD",
                currencySymbol = currency?.getSymbol(Locale("he", "IL")) ?: "$",
                language = languageFor(code),
                callingCode = callingCodeFor(code),
                emergencyNumber = emergencyFor(code),
                plugType = plugFor(code),
                drivingSide = if (code in leftDrivingCountries) "שמאל" else "ימין",
                flag = flagFor(code),
                phrases = phrasesFor(code),
                tips = tipsFor(code)
            )
            cache[destination] = info
            info
        } catch (_: Exception) {
            fallback(destination)
        } finally {
            connection.disconnect()
        }
    }

    private fun fallback(destination: String): DestinationInfo? {
        val lower = destination.lowercase()
        val code = when {
            "בודפשט" in lower || "hungary" in lower || "הונגר" in lower -> "HU"
            "ארצות הברית" in lower || "usa" in lower || "united states" in lower || "ניו יורק" in lower -> "US"
            "לונדון" in lower || "united kingdom" in lower || "בריט" in lower -> "GB"
            "פריז" in lower || "france" in lower || "צרפת" in lower -> "FR"
            "רומא" in lower || "italy" in lower || "איטל" in lower -> "IT"
            "ברצלונה" in lower || "spain" in lower || "ספרד" in lower -> "ES"
            "יוון" in lower || "greece" in lower || "אתונה" in lower -> "GR"
            "גרמניה" in lower || "germany" in lower || "ברלין" in lower -> "DE"
            "אוסטריה" in lower || "austria" in lower || "וינה" in lower -> "AT"
            "פראג" in lower || "czech" in lower || "צ'כ" in lower -> "CZ"
            "הולנד" in lower || "netherlands" in lower || "אמסטרדם" in lower -> "NL"
            "יפן" in lower || "japan" in lower || "טוקיו" in lower -> "JP"
            "תאילנד" in lower || "thailand" in lower || "בנגקוק" in lower -> "TH"
            "איחוד האמירויות" in lower || "dubai" in lower || "דובאי" in lower -> "AE"
            else -> return null
        }

        val coordinates = defaultCoordinates[code] ?: (0.0 to 0.0)
        val timezone = defaultTimezones[code] ?: "UTC"
        val currency = currencyFor(code)

        return DestinationInfo(
            city = destination.substringBefore(",").trim(),
            country = countryNameFor(code),
            countryCode = code,
            latitude = coordinates.first,
            longitude = coordinates.second,
            timezone = timezone,
            currencyCode = currency?.currencyCode ?: "USD",
            currencySymbol = currency?.getSymbol(Locale("he", "IL")) ?: "$",
            language = languageFor(code),
            callingCode = callingCodeFor(code),
            emergencyNumber = emergencyFor(code),
            plugType = plugFor(code),
            drivingSide = if (code in leftDrivingCountries) "שמאל" else "ימין",
            flag = flagFor(code),
            phrases = phrasesFor(code),
            tips = tipsFor(code)
        )
    }

    private fun currencyFor(code: String): Currency? = runCatching {
        Currency.getInstance(Locale.Builder().setRegion(code).build())
    }.getOrNull()

    private fun flagFor(code: String): String =
        code.uppercase().map { char ->
            Character.toChars(0x1F1E6 + (char.code - 'A'.code)).concatToString()
        }.joinToString("")

    private fun countryNameFor(code: String): String =
        Locale("", code).getDisplayCountry(Locale("he", "IL")).ifBlank { code }

    private fun languageFor(code: String): String = mapOf(
        "HU" to "הונגרית", "US" to "אנגלית", "GB" to "אנגלית",
        "FR" to "צרפתית", "IT" to "איטלקית", "ES" to "ספרדית",
        "GR" to "יוונית", "DE" to "גרמנית", "AT" to "גרמנית",
        "CZ" to "צ'כית", "NL" to "הולנדית", "JP" to "יפנית",
        "TH" to "תאית", "AE" to "ערבית", "PT" to "פורטוגזית",
        "CH" to "גרמנית / צרפתית / איטלקית", "CA" to "אנגלית / צרפתית"
    )[code] ?: "לפי המדינה"

    private fun callingCodeFor(code: String): String = mapOf(
        "HU" to "+36", "US" to "+1", "GB" to "+44", "FR" to "+33",
        "IT" to "+39", "ES" to "+34", "GR" to "+30", "DE" to "+49",
        "AT" to "+43", "CZ" to "+420", "NL" to "+31", "JP" to "+81",
        "TH" to "+66", "AE" to "+971", "PT" to "+351", "CH" to "+41",
        "CA" to "+1", "PL" to "+48", "RO" to "+40"
    )[code] ?: "בדיקה מקומית"

    private fun emergencyFor(code: String): String = when (code) {
        "US", "CA" -> "911"
        "JP" -> "110 משטרה / 119 אמבולנס וכבאות"
        "AE" -> "999 משטרה / 998 אמבולנס"
        "TH" -> "191 משטרה / 1669 אמבולנס"
        else -> "112"
    }

    private fun plugFor(code: String): String = when (code) {
        "US", "CA", "JP" -> "A / B"
        "GB" -> "G"
        "AE" -> "G"
        "TH" -> "A / B / C"
        "CH" -> "J"
        "IT" -> "C / F / L"
        else -> "C / F"
    }

    private fun phrasesFor(code: String): List<Pair<String, String>> = when (code) {
        "HU" -> listOf(
            "שלום" to "Szia",
            "תודה" to "Köszönöm",
            "בבקשה" to "Kérem",
            "כן / לא" to "Igen / Nem",
            "עזרה" to "Segítség"
        )
        "FR" -> listOf(
            "שלום" to "Bonjour",
            "תודה" to "Merci",
            "בבקשה" to "S'il vous plaît",
            "כן / לא" to "Oui / Non",
            "עזרה" to "Aidez-moi"
        )
        "IT" -> listOf(
            "שלום" to "Ciao",
            "תודה" to "Grazie",
            "בבקשה" to "Per favore",
            "כן / לא" to "Sì / No",
            "עזרה" to "Aiuto"
        )
        "ES" -> listOf(
            "שלום" to "Hola",
            "תודה" to "Gracias",
            "בבקשה" to "Por favor",
            "כן / לא" to "Sí / No",
            "עזרה" to "Ayuda"
        )
        "DE", "AT" -> listOf(
            "שלום" to "Hallo",
            "תודה" to "Danke",
            "בבקשה" to "Bitte",
            "כן / לא" to "Ja / Nein",
            "עזרה" to "Hilfe"
        )
        "GR" -> listOf(
            "שלום" to "Γεια σας",
            "תודה" to "Ευχαριστώ",
            "בבקשה" to "Παρακαλώ",
            "כן / לא" to "Ναι / Όχι",
            "עזרה" to "Βοήθεια"
        )
        "JP" -> listOf(
            "שלום" to "こんにちは",
            "תודה" to "ありがとう",
            "בבקשה" to "お願いします",
            "כן / לא" to "はい / いいえ",
            "עזרה" to "助けてください"
        )
        else -> listOf(
            "שלום" to "Hello",
            "תודה" to "Thank you",
            "בבקשה" to "Please",
            "כן / לא" to "Yes / No",
            "עזרה" to "Help"
        )
    }

    private fun tipsFor(code: String): List<String> {
        val base = mutableListOf(
            "לשמור צילום של הדרכון והביטוח בנפרד מהמקור.",
            "לבדוק שעות פתיחה וכרטיסים סמוך ליום הביקור.",
            "להוריד מפות אופליין לפני היציאה."
        )
        when (code) {
            "US" -> base += listOf(
                "מחירים בחנויות ובמסעדות מוצגים לעיתים לפני מס.",
                "נהוג להשאיר טיפ במסעדות עם שירות לשולחן."
            )
            "HU" -> base += listOf(
                "המטבע המקומי הוא פורינט, אף שהונגריה חברה באיחוד האירופי.",
                "בתחבורה הציבורית חשוב לתקף כרטיס בהתאם לסוגו."
            )
            "GB" -> base += "כלי רכב נוסעים בצד שמאל."
            "JP" -> base += listOf(
                "נהוג לשמור על שקט בתחבורה הציבורית.",
                "מזומן עדיין שימושי בעסקים קטנים."
            )
            "AE" -> base += "מומלץ לבוש מכבד במקומות ציבוריים ובאתרים דתיים."
        }
        return base
    }

    private val leftDrivingCountries = setOf(
        "GB", "IE", "JP", "TH", "AU", "NZ", "CY", "MT", "SG", "ZA"
    )

    private val defaultCoordinates = mapOf(
        "HU" to (47.4979 to 19.0402),
        "US" to (40.7128 to -74.0060),
        "GB" to (51.5074 to -0.1278),
        "FR" to (48.8566 to 2.3522),
        "IT" to (41.9028 to 12.4964),
        "ES" to (41.3874 to 2.1686),
        "GR" to (37.9838 to 23.7275),
        "DE" to (52.5200 to 13.4050),
        "AT" to (48.2082 to 16.3738),
        "CZ" to (50.0755 to 14.4378),
        "NL" to (52.3676 to 4.9041),
        "JP" to (35.6762 to 139.6503),
        "TH" to (13.7563 to 100.5018),
        "AE" to (25.2048 to 55.2708)
    )

    private val defaultTimezones = mapOf(
        "HU" to "Europe/Budapest",
        "US" to "America/New_York",
        "GB" to "Europe/London",
        "FR" to "Europe/Paris",
        "IT" to "Europe/Rome",
        "ES" to "Europe/Madrid",
        "GR" to "Europe/Athens",
        "DE" to "Europe/Berlin",
        "AT" to "Europe/Vienna",
        "CZ" to "Europe/Prague",
        "NL" to "Europe/Amsterdam",
        "JP" to "Asia/Tokyo",
        "TH" to "Asia/Bangkok",
        "AE" to "Asia/Dubai"
    )
}

object ExchangeRateService {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun rate(from: String, to: String): Double? = withContext(Dispatchers.IO) {
        if (from == to) return@withContext 1.0

        val endpoint = "https://api.frankfurter.dev/v2/rate/$from/$to"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
        }

        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString<RateResponse>(body).rate.takeIf { it > 0 }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
fun GeneralInfoScreen(
    trip: Trip,
    modifier: Modifier = Modifier
) {
    val info by produceState<DestinationInfo?>(initialValue = null, trip.destination) {
        value = DestinationResolver.resolve(trip.destination)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            GradientHeader(
                title = "מידע על היעד",
                subtitle = "מידע אוטומטי לפי הטיול שנבחר",
                emoji = "🧭",
                start = Lavender,
                end = Navy
            )
        }

        if (info == null) {
            item {
                SectionCard(containerColor = SoftLavender) {
                    CircularProgressIndicator()
                    Text("מזהה את היעד ומכין מידע מקומי…")
                }
            }
        } else {
            val destination = info!!

            item {
                DestinationSummaryCard(destination)
            }

            item {
                DynamicClockBar(trip = trip)
            }

            item {
                CurrencyConverterCard(destination)
            }

            item {
                SectionTitle("תחזית לכל ימי הטיול", "🌦️")
            }

            items(trip.days.sortedBy { it.date }, key = { it.id }) { day ->
                SectionCard(containerColor = CardWhite) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DayThumbnail(day.imageKey, Modifier.size(46.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(day.title, fontWeight = FontWeight.Bold)
                            Text(day.date, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                    WeatherCard(trip = trip, day = day, modifier = Modifier.fillMaxWidth())
                }
            }

            item {
                PracticalInfoCard(destination)
            }

            item {
                LanguageCard(destination)
            }

            item {
                TipsCard(destination)
            }
        }
    }
}

@Composable
private fun DestinationSummaryCard(info: DestinationInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, Color(0xFFE1E7F0)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(SoftLavender),
                contentAlignment = Alignment.Center
            ) {
                Text(info.flag, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(info.city, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(info.country, color = TextSecondary)
                Text(info.timezone, style = MaterialTheme.typography.labelSmall, color = Lavender)
            }
        }
    }
}

@Composable
private fun CurrencyConverterCard(info: DestinationInfo) {
    var amountText by remember { mutableStateOf("100") }
    var directionToLocal by remember { mutableStateOf(true) }
    var rate by remember { mutableStateOf<Double?>(null) }

    val from = if (directionToLocal) "ILS" else info.currencyCode
    val to = if (directionToLocal) info.currencyCode else "ILS"

    LaunchedEffect(from, to) {
        rate = ExchangeRateService.rate(from, to)
    }

    val amount = amountText.toDoubleOrNull() ?: 0.0
    val converted = rate?.let { amount * it }

    SectionCard(containerColor = SoftSun) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💱", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("מחשבון המרה", fontWeight = FontWeight.Bold)
                Text(
                    "שערי ייחוס יומיים · $from → $to",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { char -> char.isDigit() || char == '.' } },
            label = { Text("סכום ב-$from") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardWhite
        ) {
            Text(
                text = if (converted != null) {
                    String.format(Locale.US, "%.2f %s", converted, to)
                } else {
                    "טוען שער המרה…"
                },
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Navy
            )
        }

        FilledTonalButton(
            onClick = {
                directionToLocal = !directionToLocal
                amountText = "100"
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = CardWhite,
                contentColor = Color(0xFF8F6500)
            )
        ) {
            Text("⇄ החלפת כיוון")
        }

        Text(
            "השער מיועד לתכנון בלבד. חברת האשראי או הצ'יינג' עשויים להשתמש בשער שונה ולהוסיף עמלה.",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun PracticalInfoCard(info: DestinationInfo) {
    SectionCard(containerColor = SoftBlue) {
        SectionTitle("מידע שימושי", "ℹ️")
        InfoGridItem("מטבע", "${info.currencyCode} ${info.currencySymbol}")
        InfoGridItem("שפה", info.language)
        InfoGridItem("קידומת", info.callingCode)
        InfoGridItem("חירום", info.emergencyNumber)
        InfoGridItem("שקע חשמל", info.plugType)
        InfoGridItem("צד נהיגה", info.drivingSide)
    }
}

@Composable
private fun LanguageCard(info: DestinationInfo) {
    SectionCard(containerColor = SoftAqua) {
        SectionTitle("מילים שימושיות", "🗣️")
        info.phrases.forEach { (hebrew, local) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(hebrew, color = TextSecondary)
                Text(local, fontWeight = FontWeight.Bold, color = Navy)
            }
            HorizontalDivider(color = Color(0xFFD9EEF1))
        }
    }
}

@Composable
private fun TipsCard(info: DestinationInfo) {
    SectionCard(containerColor = SoftMint) {
        SectionTitle("המלצות ליעד", "✅")
        info.tips.forEach { tip ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("•", color = Color(0xFF2E7D56), fontWeight = FontWeight.Bold)
                Text(tip, modifier = Modifier.weight(1f), color = TextSecondary)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, emoji: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji)
        Spacer(Modifier.width(7.dp))
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun InfoGridItem(label: String, value: String) {
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
            Text(label, color = TextSecondary)
            Text(value, fontWeight = FontWeight.Bold, color = Navy)
        }
    }
}
