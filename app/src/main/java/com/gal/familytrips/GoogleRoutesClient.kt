package com.gal.familytrips

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.ceil

private const val ROUTE_CACHE_MAX_AGE_MS = 12L * 60L * 60L * 1000L

data class GoogleRouteResult(
    val durationMinutes: Int,
    val distanceMeters: Int,
    val details: String,
    val source: String,
    val status: String,
    val cacheKey: String
)

object GoogleRoutesClient {
    fun isConfigured(): Boolean =
        BuildConfig.ROUTES_WORKER_URL.isNotBlank() &&
            BuildConfig.ROUTES_APP_TOKEN.isNotBlank()

    suspend fun refreshDay(day: TripDay): TripDay = withContext(Dispatchers.IO) {
        if (!isConfigured() || day.activities.size < 2) {
            return@withContext day
        }

        val updated = day.activities.toMutableList()
        var previous = updated.first()

        for (index in 1 until updated.size) {
            val current = updated[index]
            if (!current.transitionAutomatic) {
                previous = current
                continue
            }

            val mode = resolvedTransitionMode(previous, current)
            val cacheKey = routeCacheKey(previous, current, mode)
            val fresh = current.routeSource == "google" &&
                current.routeCacheKey == cacheKey &&
                System.currentTimeMillis() - current.routeUpdatedAt <
                    ROUTE_CACHE_MAX_AGE_MS

            if (fresh) {
                previous = current
                continue
            }

            val result = fetchRoute(previous, current, mode, cacheKey)
            if (result != null) {
                updated[index] = current.copy(
                    transitionMode = mode,
                    transitionMinutes = result.durationMinutes,
                    transitionDetails = result.details,
                    routeDistanceMeters = result.distanceMeters,
                    routeSource = result.source,
                    routeStatus = result.status,
                    routeCacheKey = result.cacheKey,
                    routeUpdatedAt = System.currentTimeMillis()
                )
            } else {
                updated[index] = current.copy(
                    routeStatus = "לא ניתן היה לקבל מסלול Google; מוצגת הערכה.",
                    routeSource = "estimate",
                    routeCacheKey = cacheKey,
                    routeUpdatedAt = System.currentTimeMillis()
                )
            }
            previous = updated[index]
        }

        day.copy(activities = updated)
    }

    private fun fetchRoute(
        previous: ActivityItem,
        current: ActivityItem,
        mode: String,
        cacheKey: String
    ): GoogleRouteResult? {
        val endpoint = BuildConfig.ROUTES_WORKER_URL.trimEnd('/') + "/route"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 25_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${BuildConfig.ROUTES_APP_TOKEN}")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val body = JSONObject().apply {
                put("origin", waypoint(previous))
                put("destination", waypoint(current))
                put("mode", mode)
                put("languageCode", "he")
                put("cacheKey", cacheKey)
            }
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            } ?: return null
            val payload = stream.bufferedReader().use { it.readText() }
            val json = JSONObject(payload)
            if (!json.optBoolean("ok", false)) return null

            val seconds = json.optInt("durationSeconds", 0)
            val minutes = ceil(seconds / 60.0).toInt().coerceAtLeast(1)
            val steps = json.optJSONArray("steps") ?: JSONArray()
            val details = buildRouteDetails(steps, mode)

            GoogleRouteResult(
                durationMinutes = minutes,
                distanceMeters = json.optInt("distanceMeters", 0),
                details = details,
                source = "google",
                status = if (json.optBoolean("cached", false)) {
                    "מסלול Google שמור"
                } else {
                    "מסלול Google מעודכן"
                },
                cacheKey = cacheKey
            )
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun waypoint(activity: ActivityItem): JSONObject {
        return if (activity.latitude != null && activity.longitude != null) {
            JSONObject().put(
                "latLng",
                JSONObject()
                    .put("latitude", activity.latitude)
                    .put("longitude", activity.longitude)
            )
        } else {
            JSONObject().put(
                "address",
                activity.location.ifBlank { activity.name }
            )
        }
    }

    private fun buildRouteDetails(steps: JSONArray, mode: String): String {
        val lines = mutableListOf<String>()
        for (index in 0 until steps.length()) {
            val step = steps.optJSONObject(index) ?: continue
            val travelMode = step.optString("travelMode")
            val instruction = step.optString("instruction")
            val transit = step.optJSONObject("transit")

            if (transit != null) {
                val line = listOf(
                    transit.optString("vehicleName"),
                    transit.optString("lineShort").ifBlank {
                        transit.optString("lineName")
                    }
                ).filter { it.isNotBlank() }.joinToString(" ")
                val from = transit.optString("departureStop")
                val to = transit.optString("arrivalStop")
                val stops = transit.optInt("stopCount", 0)
                val headsign = transit.optString("headsign")
                lines += buildString {
                    append("🚉 ")
                    append(line.ifBlank { "תחבורה ציבורית" })
                    if (headsign.isNotBlank()) append(" לכיוון $headsign")
                    if (from.isNotBlank() || to.isNotBlank()) {
                        append(" · ${from.ifBlank { "תחנה" }} → ${to.ifBlank { "תחנה" }}")
                    }
                    if (stops > 0) append(" · $stops תחנות")
                }
            } else if (instruction.isNotBlank()) {
                val icon = when (travelMode.uppercase(Locale.ROOT)) {
                    "WALK" -> "🚶"
                    "DRIVE" -> "🚗"
                    "BICYCLE" -> "🚲"
                    else -> if (mode == "transit") "➡️" else "🧭"
                }
                lines += "$icon $instruction"
            }
        }
        return lines.take(8).joinToString("\n").ifBlank {
            when (mode) {
                "transit" -> "פתח Google Maps להצגת התחנות והקווים."
                "walk" -> "מסלול הליכה של Google Maps."
                else -> "מסלול נסיעה של Google Maps."
            }
        }
    }

    private fun routeCacheKey(
        previous: ActivityItem,
        current: ActivityItem,
        mode: String
    ): String {
        val raw = listOf(
            coordinateOrAddress(previous),
            coordinateOrAddress(current),
            mode
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun coordinateOrAddress(activity: ActivityItem): String =
        if (activity.latitude != null && activity.longitude != null) {
            "${activity.latitude},${activity.longitude}"
        } else {
            activity.location.ifBlank { activity.name }.trim().lowercase(Locale.ROOT)
        }
}
