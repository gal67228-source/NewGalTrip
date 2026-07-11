
package com.gal.familytrips

data class BudgetTemplate(
    val id: String,
    val title: String,
    val currency: String,
    val category: String,
    val date: String
)

data class DocumentRequirement(
    val key: String,
    val title: String,
    val type: String,
    val description: String
)

fun suggestedBudgetTemplates(trip: Trip): List<BudgetTemplate> {
    val localCurrency = destinationCurrency(trip.destination)
    val result = mutableListOf<BudgetTemplate>()

    // Hotels are always relevant to the trip budget.
    trip.hotels.forEach { hotel ->
        result += BudgetTemplate(
            id = "auto-hotel-${hotel.id}",
            title = "מלון: ${hotel.name}",
            currency = localCurrency,
            category = "מלונות",
            date = hotel.checkIn
        )
    }

    // Only flights and paid attractions are imported automatically.
    trip.days.sortedBy { it.date }.forEach { day ->
        day.activities.forEach { activity ->
            when (budgetCategoryForActivity(activity)) {
                "טיסות" -> {
                    result += BudgetTemplate(
                        id = "auto-flight-${activity.id}",
                        title = activity.name,
                        currency = currencyFromCost(activity.cost) ?: localCurrency,
                        category = "טיסות",
                        date = day.date
                    )
                }

                "אטרקציות" -> {
                    result += BudgetTemplate(
                        id = "auto-attraction-${activity.id}",
                        title = activity.name,
                        currency = currencyFromCost(activity.cost) ?: localCurrency,
                        category = "אטרקציות",
                        date = day.date
                    )
                }
            }
        }
    }

    return result.distinctBy { it.id }
}

fun suggestedDocumentRequirements(trip: Trip): List<DocumentRequirement> {
    val result = mutableListOf(
        DocumentRequirement(
            key = "base-passports",
            title = "צילומי דרכונים",
            type = "מסמכים אישיים",
            description = "עותק מאובטח לכל נוסע"
        ),
        DocumentRequirement(
            key = "base-insurance",
            title = "ביטוח נסיעות",
            type = "ביטוח",
            description = "פוליסה ומספרי חירום"
        )
    )

    trip.hotels.forEach { hotel ->
        result += DocumentRequirement(
            key = "hotel-${hotel.id}",
            title = hotel.name,
            type = "מלונות",
            description = "אישור הזמנה / Voucher"
        )
    }

    trip.days.forEach { day ->
        day.activities.forEach { activity ->
            documentTypeFor(activity)?.let { type ->
                result += DocumentRequirement(
                    key = "activity-${activity.id}",
                    title = activity.name,
                    type = type,
                    description = when (type) {
                        "טיסות" -> "כרטיס טיסה / Boarding Pass"
                        "הסעות" -> "אישור הזמנה ופרטי איסוף"
                        "תחבורה" -> "כרטיס / אישור נסיעה"
                        else -> "כרטיס, Voucher או QR code"
                    }
                )
            }
        }
    }

    return result.distinctBy { it.key }
}

private fun budgetCategoryForActivity(activity: ActivityItem): String? {
    val value = "${activity.name} ${activity.transport} ${activity.cost} ${activity.notes}".lowercase()

    if (containsAny(value, "טיסה", "flight", "boarding")) {
        return "טיסות"
    }

    val attractionKeywords = arrayOf(
        "שייט",
        "cruise",
        "minipolisz",
        "zoo",
        "גן החיות",
        "budapest eye",
        "גלגל ענק",
        "מוזיאון",
        "museum",
        "פארק שעשועים",
        "theme park",
        "אטרקציה",
        "attraction",
        "כרטיס",
        "ticket",
        "voucher",
        "aquaworld"
    )

    val excludedKeywords = arrayOf(
        "ארוח",
        "מסעד",
        "restaurant",
        "food",
        "קניות",
        "shopping",
        "מטרו",
        "metro",
        "אוטובוס",
        "bus",
        "חשמלית",
        "tram",
        "תחבורה ציבורית",
        "public transport",
        "הליכה",
        "walking",
        "מונית",
        "taxi",
        "הסעה",
        "transfer"
    )

    val looksLikeAttraction = attractionKeywords.any { value.contains(it) }
    val isExcluded = excludedKeywords.any { value.contains(it) }

    return if (looksLikeAttraction && !isExcluded) {
        "אטרקציות"
    } else {
        null
    }
}

private fun documentTypeFor(activity: ActivityItem): String? {
    val value = "${activity.name} ${activity.transport} ${activity.notes}".lowercase()
    return when {
        containsAny(value, "טיסה", "flight", "boarding") -> "טיסות"
        containsAny(value, "הסעה", "transfer", "welcome pickups") -> "הסעות"
        containsAny(value, "רכבת", "train", "אוטובוס בין עירוני") -> "תחבורה"
        containsAny(
            value,
            "שייט",
            "cruise",
            "minipolisz",
            "zoo",
            "גן החיות",
            "budapest eye",
            "גלגל ענק",
            "כרטיס",
            "voucher",
            "qr"
        ) -> "אטרקציות"
        activity.cost.isNotBlank() && !containsAny(value, "ארוח", "מסעד", "קניות") -> "אטרקציות"
        else -> null
    }
}

fun destinationCurrency(destination: String): String {
    val value = destination.lowercase()
    return when {
        containsAny(value, "בודפשט", "הונגר", "hungary", "budapest") -> "HUF"
        containsAny(value, "ארצות הברית", "united states", "usa", "ניו יורק", "לוס אנג") -> "USD"
        containsAny(value, "בריט", "united kingdom", "london", "לונדון") -> "GBP"
        containsAny(value, "יפן", "japan", "tokyo", "טוקיו") -> "JPY"
        containsAny(value, "תאילנד", "thailand", "bangkok", "בנגקוק") -> "THB"
        containsAny(value, "דובאי", "איחוד האמירויות", "uae") -> "AED"
        containsAny(value, "צ'כ", "czech", "פראג", "prague") -> "CZK"
        containsAny(value, "פולין", "poland", "ורשה", "קרקוב") -> "PLN"
        containsAny(value, "שוויץ", "switzerland") -> "CHF"
        containsAny(
            value,
            "צרפת", "france", "פריז",
            "איטל", "italy", "רומא",
            "ספרד", "spain", "ברצלונה",
            "יוון", "greece", "גרמניה", "germany",
            "אוסטריה", "austria", "הולנד", "netherlands",
            "פורטוגל", "portugal"
        ) -> "EUR"
        else -> "EUR"
    }
}

private fun currencyFromCost(cost: String): String? = when {
    "€" in cost || "eur" in cost.lowercase() -> "EUR"
    "$" in cost || "usd" in cost.lowercase() -> "USD"
    "₪" in cost || "ils" in cost.lowercase() -> "ILS"
    "huf" in cost.lowercase() || "ft" in cost.lowercase() -> "HUF"
    else -> null
}

private fun containsAny(value: String, vararg terms: String): Boolean =
    terms.any { value.contains(it.lowercase()) }
