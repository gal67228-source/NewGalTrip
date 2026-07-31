package com.gal.familytrips

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Builds stable, replaceable packing suggestions with a separate row for every traveler. */
object PersonalizedPacking {
    fun tripDays(trip: Trip): Int = runCatching {
        (ChronoUnit.DAYS.between(
            LocalDate.parse(trip.startDate),
            LocalDate.parse(trip.endDate)
        ) + 1).toInt().coerceAtLeast(1)
    }.getOrDefault(1)

    fun apply(trip: Trip): Trip {
        val manualItems = trip.packingItems.filterNot { it.automaticallyGenerated }
        val days = tripDays(trip)
        val existingGenerated = trip.packingItems
            .filter { it.automaticallyGenerated }
            .associateBy { it.id }
        val context = TripPackingContext.from(trip)
        val generated = trip.travelers
            .flatMap { traveler -> suggestions(traveler, days, context) }
            .map { suggestion ->
                existingGenerated[suggestion.id]?.let { previous ->
                    suggestion.copy(packed = previous.packed)
                } ?: suggestion
            }
        return trip.copy(
            packingItems = manualItems + generated,
            packingCategories = (trip.packingCategories + generated.map { it.category })
                .distinct()
        )
    }

    private fun suggestions(
        traveler: TripTraveler,
        days: Int,
        context: TripPackingContext
    ): List<PackingItem> {
        val clothes = days.coerceAtMost(14)
        val personal = mutableListOf(
            item(traveler, "דרכון", "מסמכים", 1),
            item(traveler, "כרטיס / אישור טיסה", "מסמכים", 1),
            item(traveler, "סטים של בגדים", "בגדים", clothes),
            item(traveler, "פיג'מות", "בגדים", if (days >= 5) 2 else 1),
            item(traveler, "מברשת שיניים", "רחצה", 1),
            item(traveler, "תרופות אישיות", "בריאות", 1)
        )
        if (traveler.age < 12) {
            personal += item(traveler, "בגדים להחלפה בתיק היום", "בגדים", 2)
            personal += item(traveler, "בקבוק מים אישי", "טיול יומי", 1)
        }
        if (traveler.age < 6) {
            personal += item(traveler, "חפץ מעבר / משחק לדרך", "ילדים", 1)
            personal += item(traveler, "תרופות ילדים", "בריאות", 1)
        }
        if (traveler.age < 3) {
            personal += item(traveler, "חיתולים", "ילדים", days * 5)
            personal += item(traveler, "מגבונים", "ילדים", days.coerceAtMost(3))
        }
        if (context.hasSwimming) {
            personal += item(traveler, "בגדי ים", "בגדים", if (days >= 3) 2 else 1)
            personal += item(traveler, "כובע לבריכה / לים", "טיול יומי", 1)
            personal += item(traveler, "כפכפים", "הנעלה", 1)
        }
        if (context.hasOutdoorActivities) {
            personal += item(traveler, "כובע שמש", "טיול יומי", 1)
            personal += item(traveler, "קרם הגנה", "רחצה", 1)
        }
        if (context.isWinterTrip) {
            personal += item(traveler, "מעיל חם", "בגדים", 1)
            personal += item(traveler, "שכבות חמות", "בגדים", days.coerceAtMost(4))
            personal += item(traveler, "מטרייה מתקפלת", "טיול יומי", 1)
        }
        return personal
    }

    private data class TripPackingContext(
        val hasSwimming: Boolean,
        val hasOutdoorActivities: Boolean,
        val isWinterTrip: Boolean
    ) {
        companion object {
            fun from(trip: Trip): TripPackingContext {
                val itineraryText = buildString {
                    append(trip.destination.lowercase())
                    trip.destinationStops.forEach { append(' ').append(it.lowercase()) }
                    trip.days.forEach { day ->
                        append(' ').append(day.title.lowercase())
                        day.activities.forEach { activity ->
                            append(' ').append(activity.name.lowercase())
                            append(' ').append(activity.location.lowercase())
                        }
                    }
                }
                val swimmingWords = listOf("בריכ", "ים", "חוף", "פארק מים", "water", "pool", "beach", "aqua", "spa")
                val outdoorWords = listOf("פארק", "גן חיות", "הליכה", "מסלול", "טיול", "park", "zoo", "hike", "walking")
                val startMonth = runCatching { LocalDate.parse(trip.startDate).monthValue }.getOrNull()
                return TripPackingContext(
                    hasSwimming = swimmingWords.any(itineraryText::contains),
                    hasOutdoorActivities = outdoorWords.any(itineraryText::contains),
                    isWinterTrip = startMonth in setOf(11, 12, 1, 2)
                )
            }
        }
    }

    private fun item(
        traveler: TripTraveler,
        name: String,
        category: String,
        quantity: Int
    ) = PackingItem(
        id = "auto-${traveler.id}-${category.hashCode()}-${name.hashCode()}",
        name = name,
        category = category,
        quantity = quantity,
        travelerId = traveler.id,
        travelerName = traveler.name,
        automaticallyGenerated = true
    )
}
