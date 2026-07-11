
package com.gal.familytrips

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("gal_family_trips")
private val STATE_KEY = stringPreferencesKey("state_json")

class TripStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun load(): AppState {
        val raw = context.dataStore.data.first()[STATE_KEY]
        return if (raw.isNullOrBlank()) defaultState() else runCatching {
            json.decodeFromString<AppState>(raw)
        }.getOrElse { defaultState() }
    }

    suspend fun save(state: AppState) {
        context.dataStore.edit { prefs ->
            prefs[STATE_KEY] = json.encodeToString(AppState.serializer(), state)
        }
    }

    fun exportTrip(trip: Trip): String = json.encodeToString(Trip.serializer(), trip)

    fun importTrip(raw: String): Trip = json.decodeFromString(Trip.serializer(), raw)

    private fun defaultState(): AppState {
        val days = listOf(
            TripDay("d1","2026-08-05","טיסה והגעה ל-Aquaworld", listOf(
                ActivityItem("a1","10:10","טיסה W6 2506","Ben Gurion Airport","","https://www.google.com/maps/search/?api=1&query=Ben+Gurion+Airport"),
                ActivityItem("a2","14:30","צ'ק-אין והתארגנות","Aquaworld Resort Budapest","","https://www.google.com/maps/search/?api=1&query=Aquaworld+Resort+Budapest"),
                ActivityItem("a3","15:15","בריכה ופעילויות ילדים","Aquaworld Resort Budapest")
            )),
            TripDay("d2","2026-08-06","פארק מים וקניות", listOf(
                ActivityItem("a4","09:00","פארק המים","Aquaworld Resort Budapest"),
                ActivityItem("a5","13:00","ארוחת צהריים ומנוחה","Aquaworld Resort Budapest"),
                ActivityItem("a6","15:10","Auchan Dunakeszi","Auchan Dunakeszi","","https://www.google.com/maps/search/?api=1&query=Auchan+Dunakeszi")
            )),
            TripDay("d3","2026-08-07","יום מלא במלון", listOf(
                ActivityItem("a7","09:00","ג'ימבורי","Aquaworld Resort Budapest"),
                ActivityItem("a8","10:30","פארק המים","Aquaworld Resort Budapest"),
                ActivityItem("a9","15:00","פעילויות ילדים","Aquaworld Resort Budapest")
            )),
            TripDay("d4","2026-08-08","מעבר למרכז העיר", listOf(
                ActivityItem("a10","10:00","הסעה ל-7Seasons","7Seasons Apartments Budapest"),
                ActivityItem("a11","11:15","MiniPolisz","MiniPolisz Budapest"),
                ActivityItem("a12","14:20","Budapest Eye","Budapest Eye"),
                ActivityItem("a13","15:00","רחוב ואצי","Vaci Street Budapest")
            )),
            TripDay("d5","2026-08-09","גן החיות ושייט", listOf(
                ActivityItem("a14","09:40","Budapest Zoo","Budapest Zoo"),
                ActivityItem("a15","13:45","Városliget","Varosliget Budapest"),
                ActivityItem("a16","20:20","שייט על הדנובה","Jane Haining rakpart 7 Budapest")
            )),
            TripDay("d6","2026-08-10","Arena Mall ואי מרגיט", listOf(
                ActivityItem("a17","10:00","Arena Mall","Arena Mall Budapest"),
                ActivityItem("a18","15:35","האי מרגיט","Margaret Island Budapest")
            )),
            TripDay("d7","2026-08-11","טיסה חזרה", listOf(
                ActivityItem("a19","09:40","הסעה לשדה","Budapest Airport"),
                ActivityItem("a20","13:40","טיסה W6 2327","Budapest Airport")
            ))
        )
        val trip = Trip(
            id = "budapest-2026",
            name = "Budapest 2026",
            destination = "בודפשט, הונגריה",
            startDate = "2026-08-05",
            endDate = "2026-08-11",
            days = days,
            hotels = listOf(
                Hotel("h1","Aquaworld Resort","2026-08-05","2026-08-08","Íves út 16, Budapest","https://www.google.com/maps/search/?api=1&query=Aquaworld+Resort+Budapest"),
                Hotel("h2","7Seasons Apartments","2026-08-08","2026-08-11","Király u. 8, Budapest","https://www.google.com/maps/search/?api=1&query=7Seasons+Apartments+Budapest")
            )
        )
        return AppState(listOf(trip), trip.id)
    }
}
