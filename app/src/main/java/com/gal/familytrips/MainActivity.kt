package com.gal.familytrips

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GalFamilyTripsApp()
                }
            }
        }
    }
}

data class TripDay(
    val date: String,
    val title: String,
    val activities: List<String>
)

private val budapestDays = listOf(
    TripDay("05.08", "טיסה והגעה ל-Aquaworld", listOf("טיסה W6 2506", "צ'ק-אין", "בריכה וארוחת ערב")),
    TripDay("06.08", "פארק מים וקניות", listOf("פארק המים", "מנוחה", "Auchan Dunakeszi")),
    TripDay("07.08", "יום מלא במלון", listOf("ג'ימבורי", "פארק מים", "פעילויות ילדים")),
    TripDay("08.08", "מעבר למרכז העיר", listOf("הסעה ב-10:00", "MiniPolisz", "Budapest Eye", "רחוב ואצי")),
    TripDay("09.08", "גן החיות ושייט", listOf("Budapest Zoo", "Városliget", "שייט ב-20:20")),
    TripDay("10.08", "Arena Mall ואי מרגיט", listOf("Arena Mall", "חזרה למלון", "Margaret Island")),
    TripDay("11.08", "טיסה חזרה", listOf("הסעה ב-09:40", "טיסה W6 2327"))
)

@Composable
fun GalFamilyTripsApp() {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("🏠") },
                    label = { Text("ראשי") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("📅") },
                    label = { Text("ימים") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Text("⚙️") },
                    label = { Text("בדיקה") }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> HomeScreen(Modifier.padding(innerPadding))
            1 -> DaysScreen(Modifier.padding(innerPadding))
            else -> DiagnosticsScreen(Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Gal Family Trips",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text("בודפשט 2026 · 5–11 באוגוסט")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("האפליקציה נפתחה בהצלחה", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("זוהי גרסת בסיס נקייה ללא התראות, AI או נתונים ישנים.")
            }
        }
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text("המשך פיתוח לאחר בדיקת היציבות")
        }
    }
}

@Composable
private fun DaysScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "ימי הטיול",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }

        items(budapestDays) { day ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(day.date, fontWeight = FontWeight.Bold)
                        Text(day.title)
                    }
                    Spacer(Modifier.height(8.dp))
                    day.activities.forEach { activity ->
                        Text("• $activity")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun DiagnosticsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "בדיקת מערכת",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text("✓ Jetpack Compose")
        Text("✓ AndroidX")
        Text("✓ ללא הרשאת התראות")
        Text("✓ ללא WebView")
        Text("✓ Repository נקי")
    }
}
