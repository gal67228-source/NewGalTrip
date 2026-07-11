
package com.gal.familytrips

import android.net.Uri
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val AUTO_FLIGHT_PREFIX = "auto-flight-"
private const val AUTO_MEAL_PREFIX = "auto-meal-"

fun rebuildAutomaticItinerary(trip: Trip): Trip {
    val cleanedDays = trip.days.map { day ->
        day.copy(
            activities = day.activities.filterNot { activity ->
                activity.id.startsWith(AUTO_FLIGHT_PREFIX) ||
                    activity.id.startsWith(AUTO_MEAL_PREFIX)
            }
        )
    }

    var result = trip.copy(days = cleanedDays)

    trip.flights.forEach { flight ->
        result = addFlightSkeleton(result, flight)
    }

    trip.hotels.forEach { hotel ->
        result = addHotelMealSkeleton(result, hotel)
    }

    return result.copy(
        days = result.days.map { day ->
            day.copy(
                activities = day.activities.sortedWith(
                    compareBy<ActivityItem> {
                        itineraryTimeMinutes(it.time) ?: Int.MAX_VALUE
                    }.thenBy { it.name }
                )
            )
        }
    )
}

private fun addFlightSkeleton(
    trip: Trip,
    flight: Flight
): Trip {
    val departureTime = parseTime(flight.departureTime)
    val arrivalTime = parseTime(flight.arrivalTime)

    val airportArrival = departureTime?.minusHours(3)
    val transferDeparture = airportArrival?.minusMinutes(
        flight.transferMinutes.toLong().coerceAtLeast(0)
    )

    val departureActivities = buildList {
        if (transferDeparture != null) {
            add(
                ActivityItem(
                    id = "$AUTO_FLIGHT_PREFIX${flight.id}-transfer",
                    time = transferDeparture.format(TIME_FORMAT),
                    name = "יציאה לשדה התעופה",
                    location = flight.departureAirport,
                    transport = "הסעה / מונית",
                    directions = buildString {
                        if (flight.transferFrom.isNotBlank()) {
                            append("מ-")
                            append(flight.transferFrom)
                            append(" אל ")
                        }
                        append(flight.departureAirport)
                    },
                    duration = "${flight.transferMinutes} דקות",
                    notes = "נוצר אוטומטית לפי שעת הטיסה",
                    mapsUrl = googleDirectionsUrl(
                        origin = flight.transferFrom,
                        destination = flight.departureAirport
                    )
                )
            )
        }

        if (airportArrival != null) {
            add(
                ActivityItem(
                    id = "$AUTO_FLIGHT_PREFIX${flight.id}-airport",
                    time = airportArrival.format(TIME_FORMAT),
                    name = "הגעה לשדה התעופה",
                    location = flight.departureAirport,
                    transport = "טרמינל",
                    duration = "3 שעות לפני ההמראה",
                    notes = "צ׳ק־אין, בידוק ביטחוני וביקורת גבולות",
                    mapsUrl = googleSearchUrl(flight.departureAirport)
                )
            )
        }

        add(
            ActivityItem(
                id = "$AUTO_FLIGHT_PREFIX${flight.id}-flight",
                time = listOf(
                    flight.departureTime,
                    if (flight.arrivalDate == flight.departureDate) {
                        flight.arrivalTime
                    } else {
                        null
                    }
                ).filterNotNull().joinToString("–"),
                name = buildString {
                    append("טיסה")
                    if (flight.flightNumber.isNotBlank()) {
                        append(" ")
                        append(flight.flightNumber)
                    }
                },
                location = "${flight.departureAirport} → ${flight.arrivalAirport}",
                transport = "טיסה",
                directions = flight.flightNumber,
                duration = flightDurationText(flight),
                notes = flight.notes,
                mapsUrl = googleSearchUrl(flight.departureAirport)
            )
        )
    }

    var result = appendActivities(
        trip = trip,
        date = flight.departureDate,
        activities = departureActivities
    )

    val arrivalActivities = buildList {
        if (flight.arrivalDate != flight.departureDate) {
            add(
                ActivityItem(
                    id = "$AUTO_FLIGHT_PREFIX${flight.id}-arrival-marker",
                    time = flight.arrivalTime,
                    name = "נחיתה",
                    location = flight.arrivalAirport,
                    transport = "טיסה",
                    duration = "",
                    notes = flight.flightNumber,
                    mapsUrl = googleSearchUrl(flight.arrivalAirport)
                )
            )
        }

        if (arrivalTime != null) {
            add(
                ActivityItem(
                    id = "$AUTO_FLIGHT_PREFIX${flight.id}-baggage",
                    time = arrivalTime.format(TIME_FORMAT),
                    name = "נחיתה ואיסוף מזוודות",
                    location = flight.arrivalAirport,
                    transport = "הליכה בטרמינל",
                    duration = "${flight.baggageMinutes} דקות",
                    notes = "זמן משוער לביקורת דרכונים ולאיסוף מזוודות",
                    mapsUrl = googleSearchUrl(flight.arrivalAirport)
                )
            )
        }
    }

    result = appendActivities(
        trip = result,
        date = flight.arrivalDate,
        activities = arrivalActivities
    )

    return result
}

private fun addHotelMealSkeleton(
    trip: Trip,
    hotel: Hotel
): Trip {
    val checkIn = runCatching { LocalDate.parse(hotel.checkIn) }.getOrNull()
        ?: return trip
    val checkOut = runCatching { LocalDate.parse(hotel.checkOut) }.getOrNull()
        ?: return trip

    if (checkOut.isBefore(checkIn)) return trip

    var result = trip
    var date = checkIn

    while (!date.isAfter(checkOut)) {
        val meals = mutableListOf<ActivityItem>()
        val isCheckInDay = date == checkIn
        val isCheckOutDay = date == checkOut

        val includesBreakfast = hotel.boardBasis in listOf(
            "ארוחת בוקר",
            "חצי פנסיון",
            "פנסיון מלא"
        )
        val includesDinner = hotel.boardBasis in listOf(
            "חצי פנסיון",
            "פנסיון מלא"
        )
        val includesLunch = hotel.boardBasis == "פנסיון מלא"

        // Breakfast is normally available after the first night, including checkout day.
        if (includesBreakfast && !isCheckInDay) {
            meals += mealActivity(
                hotel = hotel,
                date = date,
                key = "breakfast",
                time = "08:00",
                title = "ארוחת בוקר במלון"
            )
        }

        // Lunch is added for full-board stay days, excluding checkout day.
        if (includesLunch && !isCheckOutDay) {
            meals += mealActivity(
                hotel = hotel,
                date = date,
                key = "lunch",
                time = "13:00",
                title = "ארוחת צהריים במלון"
            )
        }

        // Dinner is added on check-in and following nights, excluding checkout day.
        if (includesDinner && !isCheckOutDay) {
            meals += mealActivity(
                hotel = hotel,
                date = date,
                key = "dinner",
                time = "19:00",
                title = "ארוחת ערב במלון"
            )
        }

        result = appendActivities(
            trip = result,
            date = date.toString(),
            activities = meals
        )

        date = date.plusDays(1)
    }

    return result
}

private fun mealActivity(
    hotel: Hotel,
    date: LocalDate,
    key: String,
    time: String,
    title: String
): ActivityItem {
    return ActivityItem(
        id = "$AUTO_MEAL_PREFIX${hotel.id}-${date}-$key",
        time = time,
        name = title,
        location = hotel.name,
        transport = "במלון",
        duration = "שעה",
        notes = "נוצר אוטומטית לפי בסיס האירוח: ${hotel.boardBasis}",
        mapsUrl = hotel.mapsUrl.ifBlank {
            googleSearchUrl(hotel.address.ifBlank { hotel.name })
        }
    )
}

private fun appendActivities(
    trip: Trip,
    date: String,
    activities: List<ActivityItem>
): Trip {
    if (activities.isEmpty()) return trip

    return trip.copy(
        days = trip.days.map { day ->
            if (day.date == date) {
                day.copy(activities = day.activities + activities)
            } else {
                day
            }
        }
    )
}

private fun flightDurationText(flight: Flight): String {
    val departureDate = runCatching {
        LocalDate.parse(flight.departureDate)
    }.getOrNull() ?: return ""
    val arrivalDate = runCatching {
        LocalDate.parse(flight.arrivalDate)
    }.getOrNull() ?: return ""
    val departureTime = parseTime(flight.departureTime) ?: return ""
    val arrivalTime = parseTime(flight.arrivalTime) ?: return ""

    val departureMinutes = departureDate.toEpochDay() * 1440 +
        departureTime.hour * 60 +
        departureTime.minute
    val arrivalMinutes = arrivalDate.toEpochDay() * 1440 +
        arrivalTime.hour * 60 +
        arrivalTime.minute

    val duration = arrivalMinutes - departureMinutes
    if (duration <= 0) return ""

    return "${duration / 60} שעות ו-${duration % 60} דקות"
}

private fun parseTime(value: String): LocalTime? {
    return runCatching {
        LocalTime.parse(value.trim(), TIME_FORMAT)
    }.getOrNull()
}

private fun itineraryTimeMinutes(value: String): Int? {
    val match = Regex("""(\d{1,2}):(\d{2})""").find(value)
        ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    return hour * 60 + minute
}

private fun googleSearchUrl(query: String): String {
    return "https://www.google.com/maps/search/?api=1&query=" +
        Uri.encode(query)
}

private fun googleDirectionsUrl(
    origin: String,
    destination: String
): String {
    return if (origin.isBlank()) {
        googleSearchUrl(destination)
    } else {
        "https://www.google.com/maps/dir/?api=1" +
            "&origin=${Uri.encode(origin)}" +
            "&destination=${Uri.encode(destination)}"
    }
}

private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm")
