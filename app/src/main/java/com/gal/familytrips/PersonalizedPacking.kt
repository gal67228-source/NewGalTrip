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
        val generated = trip.travelers.flatMap { traveler -> suggestions(traveler, days) }
        return trip.copy(
            packingItems = manualItems + generated,
            packingCategories = (trip.packingCategories + listOf("מסמכים", "בגדים", "רחצה", "בריאות"))
                .distinct()
        )
    }

    private fun suggestions(traveler: TripTraveler, days: Int): List<PackingItem> {
        val clothes = days.coerceAtMost(14)
        val swimwear = if (days >= 3) 2 else 1
        val personal = mutableListOf(
            item(traveler, "דרכון", "מסמכים", 1),
            item(traveler, "כרטיס / אישור טיסה", "מסמכים", 1),
            item(traveler, "סטים של בגדים", "בגדים", clothes),
            item(traveler, "בגדי ים", "בגדים", swimwear),
            item(traveler, "פיג'מות", "בגדים", if (days >= 5) 2 else 1),
            item(traveler, "מברשת שיניים", "רחצה", 1)
        )
        if (traveler.age < 12) personal += item(traveler, "בגדים להחלפה בתיק היום", "בגדים", 2)
        if (traveler.age < 3) personal += item(traveler, "חיתולים", "ילדים", days * 5)
        return personal
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
