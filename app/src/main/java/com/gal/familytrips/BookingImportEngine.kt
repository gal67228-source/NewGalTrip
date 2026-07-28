package com.gal.familytrips

import java.util.Locale
import java.util.UUID

object BookingImportEngine {
    fun parse(raw: String): List<BookingImportCandidate> {
        val text = raw.trim()
        if (text.isBlank()) return emptyList()
        val lower = text.lowercase(Locale.ROOT)
        val reference = Regex(
            "(?i)(?:booking|reservation|confirmation|reference|pnr|מספר הזמנה|אישור הזמנה)\\s*(?:number|no\\.?|#|:)?\\s*([A-Z0-9-]{4,20})"
        ).find(text)?.groupValues?.getOrNull(1).orEmpty()
        val dates = Regex(
            "\\b(20\\d{2}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/.]\\d{1,2}[-/.]20\\d{2})\\b"
        ).findAll(text).map { normalizeDate(it.value) }.distinct().toList()
        val times = Regex("\\b([01]?\\d|2[0-3]):[0-5]\\d\\b")
            .findAll(text).map { it.value.padStart(5, '0') }.distinct().toList()
        val result = mutableListOf<BookingImportCandidate>()

        if (listOf("flight","airline","departure","arrival","boarding","טיסה","המראה","נחיתה").any(lower::contains)) {
            val number = Regex("\\b([A-Z]{2,3}\\s?\\d{2,4})\\b")
                .find(text)?.groupValues?.getOrNull(1).orEmpty()
            val airports = Regex("\\b[A-Z]{3}\\b").findAll(text)
                .map { it.value }.filterNot { it in setOf("THE","AND","FOR","PNR") }
                .distinct().toList()
            result += BookingImportCandidate(
                type = "flight",
                title = if (number.isBlank()) "טיסה" else "טיסה $number",
                bookingReference = reference,
                startDate = dates.getOrElse(0) { "" },
                endDate = dates.getOrElse(1) { dates.getOrElse(0) { "" } },
                startTime = times.getOrElse(0) { "" },
                endTime = times.getOrElse(1) { "" },
                origin = airports.getOrElse(0) { "" },
                destination = airports.getOrElse(1) { "" },
                notes = compact(text),
                confidence = confidence(55, reference, number, dates.firstOrNull().orEmpty())
            )
        }

        if (listOf("hotel","check-in","check in","check-out","check out","accommodation","מלון","צ'ק אין","צ׳ק אין").any(lower::contains)) {
            val name = text.lines().map(String::trim)
                .firstOrNull { it.length in 3..100 && listOf("hotel","מלון","resort","hostel","apartments").any { key -> it.contains(key, true) } }
                .orEmpty().ifBlank { "מלון" }
            result += BookingImportCandidate(
                type = "hotel",
                title = name,
                bookingReference = reference,
                startDate = dates.getOrElse(0) { "" },
                endDate = dates.getOrElse(1) { "" },
                address = findAddress(text),
                notes = compact(text),
                confidence = confidence(50, reference, name, dates.firstOrNull().orEmpty())
            )
        }

        if (listOf("transfer","shuttle","private driver","pickup","ferry","train","העברה","הסעה","שאטל","נהג פרטי","רכבת","מעבורת").any(lower::contains)) {
            result += BookingImportCandidate(
                type = "transfer",
                title = "העברה / תחבורה מוזמנת",
                bookingReference = reference,
                startDate = dates.getOrElse(0) { "" },
                startTime = times.getOrElse(0) { "" },
                notes = compact(text),
                confidence = confidence(50, reference, dates.firstOrNull().orEmpty())
            )
        }

        if (listOf("ticket","voucher","tour","museum","attraction","admission","כרטיס","שובר","סיור","מוזיאון","אטרקציה","כניסה").any(lower::contains)
            && result.none { it.type in setOf("flight","hotel") }) {
            val title = text.lines().map(String::trim)
                .firstOrNull { it.length in 3..100 && listOf("ticket","voucher","tour","museum","כרטיס","שובר","סיור","מוזיאון").any { key -> it.contains(key, true) } }
                .orEmpty().ifBlank { "אטרקציה מוזמנת" }
            result += BookingImportCandidate(
                type = "attraction",
                title = title,
                bookingReference = reference,
                startDate = dates.getOrElse(0) { "" },
                startTime = times.getOrElse(0) { "" },
                address = findAddress(text),
                notes = compact(text),
                confidence = confidence(45, reference, title, dates.firstOrNull().orEmpty())
            )
        }
        return result.distinctBy { "${it.type}:${it.title}:${it.startDate}" }
    }

    fun apply(trip: Trip, c: BookingImportCandidate): Trip = when (c.type) {
        "flight" -> trip.copy(flights = trip.flights + Flight(
            id = UUID.randomUUID().toString(),
            flightNumber = c.title.removePrefix("טיסה ").takeIf { it != "טיסה" }.orEmpty(),
            departureDate = c.startDate,
            departureTime = c.startTime,
            arrivalDate = c.endDate.ifBlank { c.startDate },
            arrivalTime = c.endTime,
            departureAirport = c.origin,
            arrivalAirport = c.destination,
            notes = notes(c)
        ))
        "hotel" -> trip.copy(hotels = trip.hotels + Hotel(
            id = UUID.randomUUID().toString(),
            name = c.title,
            checkIn = c.startDate,
            checkOut = c.endDate,
            address = c.address,
            notes = notes(c)
        ))
        "transfer", "attraction" -> {
            if (trip.days.isEmpty()) trip else {
                val index = trip.days.indexOfFirst { it.date == c.startDate }.takeIf { it >= 0 } ?: 0
                val day = trip.days[index]
                val item = ActivityItem(
                    id = UUID.randomUUID().toString(),
                    time = c.startTime,
                    name = c.title,
                    location = c.address,
                    transport = if (c.type == "transfer") c.title else "",
                    notes = notes(c),
                    fixedTime = c.startTime.isNotBlank()
                )
                trip.copy(days = trip.days.mapIndexed { i, d ->
                    if (i == index) day.copy(activities = day.activities + item) else d
                })
            }
        }
        else -> trip
    }

    private fun notes(c: BookingImportCandidate) = buildString {
        if (c.bookingReference.isNotBlank()) append("מספר הזמנה: ${c.bookingReference}")
        if (c.notes.isNotBlank()) {
            if (isNotEmpty()) append("\n")
            append(c.notes)
        }
    }
    private fun confidence(base: Int, vararg values: String) =
        (base + values.count(String::isNotBlank) * 10).coerceAtMost(95)
    private fun compact(text: String) = text.lines().map(String::trim)
        .filter(String::isNotBlank).take(10).joinToString(" · ").take(700)
    private fun normalizeDate(value: String): String {
        val parts = value.replace("/", "-").replace(".", "-").split("-")
        return when {
            parts.size != 3 -> value
            parts[0].length == 4 -> "${parts[0]}-${parts[1].padStart(2,'0')}-${parts[2].padStart(2,'0')}"
            else -> "${parts[2]}-${parts[1].padStart(2,'0')}-${parts[0].padStart(2,'0')}"
        }
    }
    private fun findAddress(text: String) = text.lines().map(String::trim)
        .firstOrNull { Regex("(?i).*(street|st\\.|road|rd\\.|avenue|ave\\.|boulevard|blvd|רחוב|דרך).*").matches(it) }
        .orEmpty()
}
