
package com.gal.familytrips

import kotlinx.serialization.Serializable

@Serializable
data class ActivityItem(
    val id: String,
    val time: String = "",
    val name: String,
    val location: String = "",
    val transport: String = "",
    val directions: String = "",
    val duration: String = "",
    val cost: String = "",
    val notes: String = "",
    val mapsUrl: String = "",
    val completed: Boolean = false
)

@Serializable
data class TripDay(
    val id: String,
    val date: String,
    val title: String,
    val imageKey: String = "city",
    val activities: List<ActivityItem> = emptyList()
)

@Serializable
data class Hotel(
    val id: String,
    val name: String,
    val checkIn: String,
    val checkOut: String,
    val address: String = "",
    val mapsUrl: String = "",
    val notes: String = ""
)

@Serializable
data class Restaurant(
    val id: String,
    val dayId: String? = null,
    val activityId: String? = null,
    val name: String,
    val area: String = "",
    val type: String = "",
    val price: String = "",
    val notes: String = "",
    val mapsUrl: String = "",
    val siteUrl: String = ""
)

@Serializable
data class Expense(
    val id: String,
    val title: String,
    val amount: Double,
    val currency: String,
    val category: String,
    val date: String
)

@Serializable
data class TripDocument(
    val id: String,
    val name: String,
    val uri: String,
    val type: String = "כללי",
    val notes: String = ""
)

@Serializable
data class PackingItem(
    val id: String,
    val name: String,
    val category: String = "כללי",
    val packed: Boolean = false,
    val quantity: Int = 1,
    val notes: String = ""
)

@Serializable
data class Trip(
    val id: String,
    val name: String,
    val destination: String,
    val startDate: String,
    val endDate: String,
    val days: List<TripDay> = emptyList(),
    val hotels: List<Hotel> = emptyList(),
    val restaurants: List<Restaurant> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val documents: List<TripDocument> = emptyList(),
    val packingItems: List<PackingItem> = emptyList(),
    val offlineMode: Boolean = false
)

@Serializable
data class AppState(
    val trips: List<Trip>,
    val currentTripId: String
)
