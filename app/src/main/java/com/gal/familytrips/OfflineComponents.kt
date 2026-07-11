
package com.gal.familytrips

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

fun isOnline(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
fun OfflineModeCard(
    trip: Trip,
    onTripChange: (Trip) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var online by remember { mutableStateOf(isOnline(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            online = isOnline(context)
            delay(5_000)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (trip.offlineMode || !online) SoftSun else SoftAqua
        ),
        border = BorderStroke(
            1.dp,
            if (trip.offlineMode || !online) Color(0xFFFFD56A) else Color(0xFFCDEDF1)
        )
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (trip.offlineMode || !online) "📴" else "🌐",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "מצב אופליין",
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    Text(
                        when {
                            !online -> "אין כרגע חיבור לאינטרנט"
                            trip.offlineMode -> "רענון מידע חי מושבת"
                            else -> "האפליקציה מחוברת"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = trip.offlineMode,
                    onCheckedChange = {
                        onTripChange(trip.copy(offlineMode = it))
                    }
                )
            }

            Text(
                "המסלול, הימים, המלונות, המסעדות, המסמכים ורשימת הציוד נשמרים במכשיר וזמינים ללא אינטרנט.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            if (trip.offlineMode || !online) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardWhite
                ) {
                    Column(
                        modifier = Modifier.padding(11.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("זמין אופליין", fontWeight = FontWeight.Bold, color = Color(0xFF7D5B00))
                        Text("✓ מסלול ופעילויות", style = MaterialTheme.typography.bodySmall)
                        Text("✓ מלונות ומסעדות", style = MaterialTheme.typography.bodySmall)
                        Text("✓ מסמכים שכבר נוספו", style = MaterialTheme.typography.bodySmall)
                        Text("✓ רשימת ציוד וסימוני אריזה", style = MaterialTheme.typography.bodySmall)
                        Text("• מזג אוויר ושערי מטבע לא יתעדכנו", style = MaterialTheme.typography.bodySmall)
                        Text("• Maps ו-Waze תלויים במפות שהורדו באפליקציות שלהם", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
