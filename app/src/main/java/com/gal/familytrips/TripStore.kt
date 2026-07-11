
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
        val loaded = if (raw.isNullOrBlank()) {
            defaultState()
        } else {
            runCatching { json.decodeFromString<AppState>(raw) }.getOrElse { defaultState() }
        }
        return upgradeBudapestRoute(loaded)
    }

    suspend fun save(state: AppState) {
        context.dataStore.edit { prefs ->
            prefs[STATE_KEY] = json.encodeToString(AppState.serializer(), state)
        }
    }

    fun exportTrip(trip: Trip): String = json.encodeToString(Trip.serializer(), trip)
    fun importTrip(raw: String): Trip = json.decodeFromString(Trip.serializer(), raw)

    private fun upgradeBudapestRoute(state: AppState): AppState {
        val full = defaultBudapestTrip()
        val upgradedTrips = state.trips.map { trip ->
            if (trip.id == "budapest-2026" && trip.days.sumOf { it.activities.size } < 45) {
                full.copy(
                    expenses = trip.expenses,
                    documents = trip.documents,
                    restaurants = if (trip.restaurants.isEmpty()) full.restaurants else trip.restaurants
                )
            } else trip
        }
        return state.copy(trips = upgradedTrips)
    }

    private fun a(
        id: String,
        time: String,
        name: String,
        location: String,
        transport: String = "",
        directions: String = "",
        duration: String = "",
        cost: String = "",
        notes: String = ""
    ) = ActivityItem(
        id = id,
        time = time,
        name = name,
        location = location,
        transport = transport,
        directions = directions,
        duration = duration,
        cost = cost,
        notes = notes,
        mapsUrl = "https://www.google.com/maps/search/?api=1&query=" +
            android.net.Uri.encode(location.ifBlank { name })
    )

    private fun defaultBudapestTrip(): Trip {
        val days = listOf(
            TripDay(
                id = "d1",
                date = "2026-08-05",
                title = "טיסה והגעה ל-Aquaworld",
                imageKey = "flight",
                activities = listOf(
                    a("a1","07:10","הגעה מומלצת לנתב״ג","Ben Gurion Airport","הגעה עצמאית","","כ-3 שעות לפני הטיסה","","לוודא צ'ק-אין ומסמכים"),
                    a("a2","10:10–12:40","טיסה W6 2506 לבודפשט","תל אביב → בודפשט","טיסה","W6 2506","2:30 שעות"),
                    a("a3","12:40–13:30","נחיתה, ביקורת דרכונים ואיסוף מזוודות","Budapest Airport","הליכה בתוך הטרמינל","","כ-50 דקות","","זמן משוער"),
                    a("a4","13:30–14:30","נסיעה למלון Aquaworld","Aquaworld Resort Budapest","העברה / מונית","","כ-45–60 דקות","","התנועה יכולה להשפיע"),
                    a("a5","14:30–15:15","צ'ק-אין והתארגנות","Aquaworld Resort Budapest","הליכה","","45 דקות"),
                    a("a6","15:15–18:30","בריכה ופעילויות ילדים","Aquaworld Resort Budapest","בתוך המלון","","כ-3 שעות","","בגדי ים ובגדי החלפה"),
                    a("a7","19:00","ארוחת ערב","Aquaworld Resort Budapest","בתוך המלון")
                )
            ),
            TripDay(
                id = "d2",
                date = "2026-08-06",
                title = "פארק מים ו-Auchan Dunakeszi",
                imageKey = "water",
                activities = listOf(
                    a("a8","08:00–09:00","ארוחת בוקר","Aquaworld Resort Budapest","בתוך המלון","","שעה"),
                    a("a9","09:00–13:00","פארק המים במלון","Aquaworld Resort Budapest","בתוך המלון","","4 שעות"),
                    a("a10","13:00–15:00","ארוחת צהריים ומנוחת צהריים","Aquaworld Resort Budapest","בתוך המלון","","שעתיים","","מנוחה קבועה"),
                    a("a11","15:10","יציאה ל-Auchan Dunakeszi","Aquaworld → Auchan Dunakeszi","תחבורה ציבורית","לבדוק ב-BudapestGO ביום הנסיעה","כ-20–30 דקות","","קווי פרברים עשויים להשתנות"),
                    a("a12","15:40–18:00","קניות, אוכל וגלידה","Auchan Dunakeszi","הליכה","","כ-2:20 שעות"),
                    a("a13","18:00–18:30","חזרה למלון","Auchan Dunakeszi → Aquaworld","תחבורה ציבורית","לבדוק ב-BudapestGO","כ-20–30 דקות"),
                    a("a14","19:00","ארוחת ערב","Aquaworld Resort Budapest","בתוך המלון")
                )
            ),
            TripDay(
                id = "d3",
                date = "2026-08-07",
                title = "יום מלא במלון",
                imageKey = "hotel",
                activities = listOf(
                    a("a15","08:00–09:00","ארוחת בוקר","Aquaworld Resort Budapest","בתוך המלון","","שעה"),
                    a("a16","09:00–10:30","ג'ימבורי / מתחם ילדים","Aquaworld Resort Budapest","בתוך המלון","","שעה וחצי"),
                    a("a17","10:30–12:30","פארק המים","Aquaworld Resort Budapest","בתוך המלון","","שעתיים"),
                    a("a18","12:30–13:00","ארוחת צהריים","Aquaworld Resort Budapest","בתוך המלון","","30 דקות"),
                    a("a19","13:00–15:00","מנוחת צהריים","חדר המלון","בתוך המלון","","שעתיים"),
                    a("a20","15:00–16:30","פארק המים","Aquaworld Resort Budapest","בתוך המלון","","שעה וחצי"),
                    a("a21","16:30–18:00","ג'ימבורי / פעילויות ילדים","Aquaworld Resort Budapest","בתוך המלון","","שעה וחצי"),
                    a("a22","19:00","ארוחת ערב","Aquaworld Resort Budapest","בתוך המלון")
                )
            ),
            TripDay(
                id = "d4",
                date = "2026-08-08",
                title = "MiniPolisz ומרכז העיר",
                imageKey = "ferris",
                activities = listOf(
                    a("a23","08:00–09:00","ארוחת בוקר","Aquaworld Resort Budapest","בתוך המלון","","שעה"),
                    a("a24","09:00–09:35","צ'ק-אאוט ואיסוף מזוודות","Aquaworld Resort Budapest","בתוך המלון","","35 דקות"),
                    a("a25","09:45","המתנה בלובי","Aquaworld Resort Budapest","בתוך המלון","","15 דקות"),
                    a("a26","10:00–10:40","הסעה פרטית ל-7Seasons Apartments","Aquaworld → Király u. 8","הסעה מוזמנת","Welcome Pickups","כ-35–45 דקות","€44 שולם","4 נוסעים, 2 מזוודות"),
                    a("a27","10:40–11:10","השארת מזוודות / צ'ק-אין אם אפשר","7Seasons Apartments","הליכה","","30 דקות"),
                    a("a28","11:15–13:15","MiniPolisz","Király u. 8-10","הליכה קצרה","","כשעתיים","","מומלץ לבדוק ולהזמין"),
                    a("a29","13:15–14:15","ארוחת צהריים באזור","מרכז העיר בודפשט","הליכה","","שעה","","אפשרויות במסך המסעדות"),
                    a("a30","14:20–15:00","Budapest Eye","Erzsébet tér Budapest","הליכה","","כ-40 דקות"),
                    a("a31","15:00–16:00","כיכר Deák ורחוב ואצי","Deák Ferenc tér / Váci utca","הליכה","","שעה"),
                    a("a32","16:00–16:30","כיכר Vörösmarty","Vörösmarty tér Budapest","הליכה","","30 דקות"),
                    a("a33","16:30–17:30","טיילת הדנובה ותצפית על גשר השלשלאות","Danube Promenade Budapest","הליכה","","שעה"),
                    a("a34","17:30–18:15","גלידה / קפה והמשך שיטוט","מרכז העיר בודפשט","הליכה","","45 דקות"),
                    a("a35","18:15–19:00","חזרה למלון והתרעננות","7Seasons Apartments Budapest","הליכה","","45 דקות"),
                    a("a36","19:15","ארוחת ערב – בחירה בזמן אמת","אזור 7Seasons Budapest","הליכה","","","","רשימת אפשרויות במסך המסעדות")
                )
            ),
            TripDay(
                id = "d5",
                date = "2026-08-09",
                title = "גן החיות, Városliget ושייט",
                imageKey = "zoo",
                activities = listOf(
                    a("a37","08:00–09:00","ארוחת בוקר","7Seasons Apartments Budapest","במלון / באזור","","שעה"),
                    a("a38","09:10–09:35","נסיעה לגן החיות","Deák Ferenc tér → Széchenyi fürdő","מטרו M1","M1 לכיוון Mexikói út","כ-25 דקות כולל הליכה","","לרדת ב-Széchenyi fürdő"),
                    a("a39","09:40–12:45","Budapest Zoo & Botanical Garden","Állatkerti krt. 6-12 Budapest","הליכה","","כ-3 שעות","","לבדוק שעות פתיחה סמוך לנסיעה"),
                    a("a40","12:45–13:45","ארוחת צהריים באזור הפארק","Városliget Budapest","הליכה","","שעה","","אפשרויות במסך המסעדות"),
                    a("a41","13:45–14:15","טירת Vajdahunyad","Vajdahunyad sétány Budapest","הליכה","","30 דקות"),
                    a("a42","14:15–14:40","האגם והטיילת","Városligeti-tó Budapest","הליכה","","25 דקות"),
                    a("a43","14:40–15:35","מגרש המשחקים הגדול","Városliget Main Playground","הליכה","","55 דקות"),
                    a("a44","15:35–16:00","כיכר הגיבורים","Hősök tere Budapest","הליכה","","25 דקות"),
                    a("a45","16:05–16:35","חזרה למלון","Hősök tere → Deák Ferenc tér","מטרו M1","M1 לכיוון Vörösmarty tér","כ-30 דקות כולל הליכה"),
                    a("a46","16:35–18:30","התרעננות והתארגנות במלון","7Seasons Apartments Budapest","ללא נסיעה","","כשעתיים"),
                    a("a47","18:30–19:15","ארוחת ערב מוקדמת","ליד 7Seasons Budapest","הליכה","","45 דקות","","לבחור מרשימת המסעדות"),
                    a("a48","19:30–19:55","הליכה לרציף","7Seasons → Jane Haining rakpart 7","הליכה","","כ-20–25 דקות","","לצאת עם מרווח"),
                    a("a49","20:05","הגעה לצ'ק-אין בשייט","Jane Haining rakpart 7 Budapest","הליכה","","","","חובה להגיע עד 20:05"),
                    a("a50","20:20–21:20","שייט על הדנובה","Jane Haining rakpart 7 Budapest","סירה","","שעה","","כרטיסים בטלפון ושכבה דקה"),
                    a("a51","21:20–21:45","חזרה למלון","Jane Haining rakpart 7 → 7Seasons","הליכה","","כ-20–25 דקות")
                )
            ),
            TripDay(
                id = "d6",
                date = "2026-08-10",
                title = "Arena Mall ואי מרגיט",
                imageKey = "island",
                activities = listOf(
                    a("a52","08:00–09:00","ארוחת בוקר","7Seasons Apartments Budapest","במלון / באזור","","שעה"),
                    a("a53","09:20–09:50","נסיעה ל-Arena Mall","Deák Ferenc tér → Keleti pályaudvar","מטרו M2 + הליכה","M2 לכיוון Örs vezér tere","כ-30 דקות","","הקניון נפתח ב-10:00"),
                    a("a54","10:00–13:00","קניות ב-Arena Mall","Kerepesi út 9 Budapest","הליכה","","3 שעות"),
                    a("a55","13:00–13:45","ארוחת צהריים בקניון","Arena Mall Budapest","הליכה","","45 דקות","","אפשרויות במסך המסעדות"),
                    a("a56","13:45–14:20","חזרה למלון","Keleti → Deák Ferenc tér","מטרו M2 + הליכה","M2 לכיוון Déli pályaudvar","כ-35 דקות"),
                    a("a57","14:20–15:00","הורדת קניות והתארגנות","7Seasons Apartments Budapest","ללא נסיעה","","40 דקות"),
                    a("a58","15:00–15:35","נסיעה לאי מרגיט","7Seasons → Margitsziget","M1 / הליכה + חשמלית 4/6","Deák→Oktogon ואז 4/6 ל-Margitsziget","כ-35 דקות","","לבדוק BudapestGO"),
                    a("a59","15:35–16:00","המזרקה המוזיקלית","Margaret Island Musical Fountain","הליכה","","25 דקות","","מופעים משתנים לפי שעה"),
                    a("a60","16:00–16:45","רכב פדלים משפחתי / הליכה בשבילי האי","Margaret Island Budapest","הליכה / השכרה","","45 דקות","","אופציונלי"),
                    a("a61","16:45–17:25","מגרש משחקים","Margaret Island Playground","הליכה","","40 דקות"),
                    a("a62","17:25–17:45","גן הוורדים","Margaret Island Rose Garden","הליכה","","20 דקות"),
                    a("a63","17:45–18:10","מגדל המים מבחוץ","Margaret Island Water Tower","הליכה","","25 דקות","","עלייה רק אם פתוח ומתאים"),
                    a("a64","18:10–18:40","מיני גן החיות","Margitsziget Mini Zoo","הליכה","","30 דקות","","כניסה חופשית לאזור החיצוני"),
                    a("a65","18:40–19:10","הגן היפני","Margaret Island Japanese Garden","הליכה","","30 דקות"),
                    a("a66","19:15","ארוחת ערב – בחירה בזמן אמת","Margaret Island / Margit híd","הליכה","","","","אפשרויות במסך המסעדות"),
                    a("a67","20:15–20:50","חזרה למלון","Margitsziget → 7Seasons","חשמלית 4/6 + M1 / הליכה","","כ-35 דקות")
                )
            ),
            TripDay(
                id = "d7",
                date = "2026-08-11",
                title = "הסעה לשדה וטיסה חזרה",
                imageKey = "return",
                activities = listOf(
                    a("a68","07:00–08:00","ארוחת בוקר","7Seasons Apartments Budapest","הליכה","","שעה"),
                    a("a69","08:00–09:00","אריזה אחרונה וצ'ק-אאוט","7Seasons Apartments Budapest","ללא נסיעה","","שעה"),
                    a("a70","09:15","המתנה בלובי עם המזוודות","7Seasons Apartments Budapest","ללא נסיעה","","25 דקות"),
                    a("a71","09:40–10:25","הסעה פרטית לשדה התעופה","Király u. 8 → Budapest Airport","הסעה מוזמנת","Welcome Pickups","כ-35–45 דקות","€49 שולם","לפי אישור ההזמנה"),
                    a("a72","10:25–13:00","צ'ק-אין, ביטחון והמתנה","Budapest Airport","הליכה בטרמינל","","כ-2:35 שעות"),
                    a("a73","13:40–17:55","טיסה W6 2327 לישראל","בודפשט → ישראל","טיסה","W6 2327","4:15 שעות לפי השעות המקומיות")
                )
            )
        )

        val restaurants = listOf(
            Restaurant("r1","d4",null,"VakVarjú Restaurant","MiniPolisz / 7Seasons","הונגרי ובינלאומי","בינוני","תפריט מגוון וקרוב למלון",mapsUrl="https://maps.google.com/?q=VakVarju+Restaurant+Budapest"),
            Restaurant("r2","d4",null,"Bob's Kitchen Budapest","MiniPolisz / 7Seasons","אירופאי ביתי","בינוני","אווירה רגועה ומנות פשוטות",mapsUrl="https://maps.google.com/?q=Bob%27s+Kitchen+Budapest"),
            Restaurant("r3","d4",null,"Jamie Oliver's Diner Budapest","מרכז העיר","פיצה, פסטה והמבורגרים","בינוני","מנות שילדים אוהבים",mapsUrl="https://maps.google.com/?q=Jamie+Oliver%27s+Diner+Budapest"),
            Restaurant("r4","d4",null,"Hard Rock Cafe Budapest","Váci / Budapest Eye","אמריקאי","בינוני-גבוה","אווירה חווייתית לילדים",mapsUrl="https://maps.google.com/?q=Hard+Rock+Cafe+Budapest"),
            Restaurant("r5","d5",null,"Gundel Cafe","גן החיות / Városliget","הונגרי קלאסי","גבוה","ממש ליד גן החיות",mapsUrl="https://maps.google.com/?q=Gundel+Budapest"),
            Restaurant("r6","d5",null,"Városliget Café","אגם City Park","בית קפה ומסעדה","בינוני","נוף לאגם והפסקה נוחה",mapsUrl="https://maps.google.com/?q=Varosliget+Cafe+Budapest"),
            Restaurant("r7","d6",null,"Arena Mall Food Court","Arena Mall","מגוון","נמוך-בינוני","בחירה קלה עם ילדים",mapsUrl="https://maps.google.com/?q=Arena+Mall+Budapest+restaurants"),
            Restaurant("r8","d6",null,"Hippie Island","Margaret Island","בינלאומי","בינוני","מתאים לעצירה באי",mapsUrl="https://maps.google.com/?q=Hippie+Island+Budapest")
        )

        return Trip(
            id = "budapest-2026",
            name = "Budapest 2026",
            destination = "בודפשט, הונגריה",
            startDate = "2026-08-05",
            endDate = "2026-08-11",
            days = days,
            hotels = listOf(
                Hotel("h1","Aquaworld Resort","2026-08-05","2026-08-08","Íves út 16, Budapest","https://www.google.com/maps/search/?api=1&query=Aquaworld+Resort+Budapest"),
                Hotel("h2","7Seasons Apartments","2026-08-08","2026-08-11","Király u. 8, Budapest","https://www.google.com/maps/search/?api=1&query=7Seasons+Apartments+Budapest")
            ),
            restaurants = restaurants
        )
    }

    private fun defaultState(): AppState {
        val trip = defaultBudapestTrip()
        return AppState(listOf(trip), trip.id)
    }
}
