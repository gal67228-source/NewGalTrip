
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
    val completed: Boolean = false,
    val fixedTime: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val transitionMode: String = "auto",
    val transitionMinutes: Int = 0,
    val transitionAutomatic: Boolean = true,
    val transitionDetails: String = "",
    val routeDistanceMeters: Int = 0,
    val routeSource: String = "estimate",
    val routeStatus: String = "",
    val routeCacheKey: String = "",
    val routeUpdatedAt: Long = 0L,
    val liveStatus: String = "waiting",
    val actualStartTime: String = "",
    val actualEndTime: String = "",
    val skipped: Boolean = false,
    val departureTimeActual: String = "",
    val arrivalTimeActual: String = ""
)

@Serializable
data class DestinationStay(
    val id: String,
    val destination: String,
    val startDate: String,
    val endDate: String
)

@Serializable
data class TripDay(
    val id: String,
    val date: String,
    val title: String,
    val imageKey: String = "city",
    val activities: List<ActivityItem> = emptyList(),
    val destination: String = ""
)

@Serializable
data class Flight(
    val id: String,
    val flightNumber: String = "",
    val departureDate: String,
    val departureTime: String,
    val arrivalDate: String,
    val arrivalTime: String,
    val departureAirport: String,
    val arrivalAirport: String,
    val transferFrom: String = "",
    val transferMinutes: Int = 45,
    val baggageMinutes: Int = 60,
    val notes: String = ""
)

@Serializable
data class Hotel(
    val id: String,
    val name: String,
    val checkIn: String,
    val checkOut: String,
    val address: String = "",
    val mapsUrl: String = "",
    val notes: String = "",
    val boardBasis: String = "לינה בלבד",
    val includeTransfer: Boolean = false,
    val transferFrom: String = "",
    val transferTime: String = "15:00",
    val transferMinutes: Int = 45
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
    val notes: String = "",
    val passengerName: String = ""
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
    val destinationStops: List<String> = emptyList(),
    val startDate: String,
    val endDate: String,
    val days: List<TripDay> = emptyList(),
    val hotels: List<Hotel> = emptyList(),
    val restaurants: List<Restaurant> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val documents: List<TripDocument> = emptyList(),
    val packingItems: List<PackingItem> = emptyList(),
    val packingCategories: List<String> = emptyList(),
    val offlineMode: Boolean = false,
    val destinationStays: List<DestinationStay> = emptyList(),
    val flights: List<Flight> = emptyList()
)

@Serializable
data class AppState(
    val trips: List<Trip>,
    val currentTripId: String
)
