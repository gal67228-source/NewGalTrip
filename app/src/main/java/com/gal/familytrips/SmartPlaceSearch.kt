
package com.gal.familytrips

import android.content.Context
import android.location.Address
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class SmartPlaceSuggestion(
    val id: String,
    val title: String,
    val address: String,
    val source: String,
    val mapsUrl: String = ""
)

object AndroidPlaceSearch {
    @Suppress("DEPRECATION")
    suspend fun search(
        context: Context,
        query: String,
        destinationContext: String,
        limit: Int = 5
    ): List<SmartPlaceSuggestion> = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent() || query.trim().length < 3) {
            return@withContext emptyList()
        }

        val fullQuery = listOf(query.trim(), destinationContext.trim())
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")

        val geocoder = Geocoder(context, Locale.getDefault())

        runCatching {
            geocoder.getFromLocationName(fullQuery, limit)
                .orEmpty()
                .mapIndexedNotNull { index, address ->
                    address.toSuggestion(index)
                }
                .distinctBy { it.address.lowercase() }
        }.getOrDefault(emptyList())
    }

    private fun Address.toSuggestion(index: Int): SmartPlaceSuggestion? {
        val fullAddress = buildList {
            for (line in 0..maxAddressLineIndex) {
                getAddressLine(line)
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }.joinToString(", ")
            .ifBlank {
                listOfNotNull(
                    featureName,
                    thoroughfare,
                    locality,
                    adminArea,
                    countryName
                )
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(", ")
            }

        if (fullAddress.isBlank()) return null

        val title = featureName
            ?.takeIf { it.isNotBlank() }
            ?: thoroughfare
            ?.takeIf { it.isNotBlank() }
            ?: locality
            ?.takeIf { it.isNotBlank() }
            ?: fullAddress.substringBefore(",")

        return SmartPlaceSuggestion(
            id = "geocoder-$index-$latitude-$longitude",
            title = title,
            address = fullAddress,
            source = "חיפוש מקומות"
        )
    }
}
