
package com.gal.familytrips

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.UUID

@Composable
fun FlightsScreen(
    trip: Trip,
    onTripChange: (Trip) -> Unit,
    modifier: Modifier = Modifier
) {
    var addFlight by remember { mutableStateOf(false) }
    var editingFlight by remember { mutableStateOf<Flight?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            GradientHeader(
                title = "טיסות",
                subtitle = "הטיסות בונות אוטומטית את ימי ההמראה והנחיתה",
                emoji = "✈️",
                start = Sky,
                end = Navy
            )

            SectionCard(containerColor = SoftBlue) {
                Text(
                    "מה יתווסף למסלול?",
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
                Text(
                    "הסעה לשדה, הגעה 3 שעות לפני ההמראה, הטיסה וזמן נחיתה ואיסוף מזוודות.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            AccentButton(
                text = "הוספת טיסה",
                emoji = "＋",
                onClick = { addFlight = true },
                color = Sky,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (trip.flights.isEmpty()) {
            item {
                SectionCard(containerColor = CardWhite) {
                    Text(
                        "עדיין לא נוספו טיסות",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "לאחר הוספת טיסה ייווצר אוטומטית שלד ביום המתאים.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        items(
            trip.flights.sortedWith(
                compareBy<Flight> { it.departureDate }
                    .thenBy { it.departureTime }
            ),
            key = { it.id }
        ) { flight ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, Color(0xFFDCE7F3)),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(SoftBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✈️")
                        }

                        Spacer(Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                flight.flightNumber.ifBlank { "טיסה" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Navy
                            )
                            Text(
                                "${flight.departureAirport} → ${flight.arrivalAirport}",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        IconButton(
                            onClick = { editingFlight = flight },
                            modifier = Modifier.size(36.dp)
                        ) {
                            SmallEditIcon(Modifier.size(28.dp))
                        }

                        IconButton(
                            onClick = {
                                val updated = trip.copy(
                                    flights = trip.flights.filterNot {
                                        it.id == flight.id
                                    }
                                )
                                onTripChange(
                                    rebuildAutomaticItinerary(updated)
                                )
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            SmallDeleteIcon(Modifier.size(28.dp))
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetaChip(
                            "${flight.departureDate} ${flight.departureTime}",
                            SoftBlue,
                            Sky
                        )
                        MetaChip(
                            "${flight.arrivalDate} ${flight.arrivalTime}",
                            SoftAqua,
                            Aqua
                        )
                    }

                    if (flight.transferFrom.isNotBlank()) {
                        Text(
                            "איסוף להסעה: ${flight.transferFrom}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Text(
                        "יציאה להסעה תחושב לפי ${flight.transferMinutes} דקות נסיעה ו־3 שעות בשדה.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Sky
                    )
                }
            }
        }
    }

    if (addFlight) {
        FlightEditorDialog(
            trip = trip,
            flight = null,
            onDismiss = { addFlight = false },
            onConfirm = { flight ->
                val updated = trip.copy(
                    flights = trip.flights + flight
                )
                onTripChange(rebuildAutomaticItinerary(updated))
                addFlight = false
            }
        )
    }

    editingFlight?.let { flight ->
        FlightEditorDialog(
            trip = trip,
            flight = flight,
            onDismiss = { editingFlight = null },
            onConfirm = { updatedFlight ->
                val updated = trip.copy(
                    flights = trip.flights.map {
                        if (it.id == updatedFlight.id) {
                            updatedFlight
                        } else {
                            it
                        }
                    }
                )
                onTripChange(rebuildAutomaticItinerary(updated))
                editingFlight = null
            }
        )
    }
}

@Composable
private fun FlightEditorDialog(
    trip: Trip,
    flight: Flight?,
    onDismiss: () -> Unit,
    onConfirm: (Flight) -> Unit
) {
    var flightNumber by remember(flight?.id) {
        mutableStateOf(flight?.flightNumber.orEmpty())
    }
    var departureDate by remember(flight?.id) {
        mutableStateOf(flight?.departureDate ?: trip.startDate)
    }
    var departureTime by remember(flight?.id) {
        mutableStateOf(flight?.departureTime ?: "10:00")
    }
    var arrivalDate by remember(flight?.id) {
        mutableStateOf(flight?.arrivalDate ?: departureDate)
    }
    var arrivalTime by remember(flight?.id) {
        mutableStateOf(flight?.arrivalTime ?: "13:00")
    }
    var departureAirport by remember(flight?.id) {
        mutableStateOf(flight?.departureAirport ?: "נמל התעופה בן גוריון")
    }
    var arrivalAirport by remember(flight?.id) {
        mutableStateOf(flight?.arrivalAirport.orEmpty())
    }
    var transferFrom by remember(flight?.id) {
        mutableStateOf(flight?.transferFrom.orEmpty())
    }
    var transferMinutesText by remember(flight?.id) {
        mutableStateOf((flight?.transferMinutes ?: 45).toString())
    }
    var baggageMinutesText by remember(flight?.id) {
        mutableStateOf((flight?.baggageMinutes ?: 60).toString())
    }
    var notes by remember(flight?.id) {
        mutableStateOf(flight?.notes.orEmpty())
    }

    val valid = departureAirport.isNotBlank() &&
        arrivalAirport.isNotBlank() &&
        isValidClockTime(departureTime) &&
        isValidClockTime(arrivalTime) &&
        runCatching {
            !LocalDate.parse(arrivalDate)
                .isBefore(LocalDate.parse(departureDate))
        }.getOrDefault(false)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (flight == null) "טיסה חדשה" else "עריכת טיסה"
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = flightNumber,
                        onValueChange = { flightNumber = it },
                        label = { Text("מספר טיסה") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    FlightDateField(
                        label = "תאריך המראה",
                        value = departureDate,
                        onValueChange = {
                            departureDate = it
                            if (
                                runCatching {
                                    LocalDate.parse(arrivalDate)
                                        .isBefore(LocalDate.parse(it))
                                }.getOrDefault(false)
                            ) {
                                arrivalDate = it
                            }
                        }
                    )
                }

                item {
                    OutlinedTextField(
                        value = departureTime,
                        onValueChange = { departureTime = it },
                        label = { Text("שעת המראה HH:mm") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    FlightDateField(
                        label = "תאריך נחיתה",
                        value = arrivalDate,
                        minimumDate = departureDate,
                        onValueChange = { arrivalDate = it }
                    )
                }

                item {
                    OutlinedTextField(
                        value = arrivalTime,
                        onValueChange = { arrivalTime = it },
                        label = { Text("שעת נחיתה HH:mm") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = departureAirport,
                        onValueChange = { departureAirport = it },
                        label = { Text("שדה יציאה") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = arrivalAirport,
                        onValueChange = { arrivalAirport = it },
                        label = { Text("שדה נחיתה") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = transferFrom,
                        onValueChange = { transferFrom = it },
                        label = { Text("מקום איסוף להסעה לשדה") },
                        supportingText = {
                            Text("לדוגמה: הבית או המלון")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = transferMinutesText,
                        onValueChange = {
                            transferMinutesText = it.filter(Char::isDigit)
                        },
                        label = { Text("זמן הסעה לשדה בדקות") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = baggageMinutesText,
                        onValueChange = {
                            baggageMinutesText = it.filter(Char::isDigit)
                        },
                        label = { Text("זמן נחיתה ומזוודות בדקות") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("הערות") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    SectionCard(containerColor = SoftBlue) {
                        Text(
                            "המסלול יחושב אוטומטית",
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )
                        Text(
                            "הגעה לשדה תהיה 3 שעות לפני ההמראה, והיציאה להסעה תחושב לפי זמן הנסיעה שהוזן.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(
                        Flight(
                            id = flight?.id
                                ?: UUID.randomUUID().toString(),
                            flightNumber = flightNumber.trim(),
                            departureDate = departureDate,
                            departureTime = departureTime.trim(),
                            arrivalDate = arrivalDate,
                            arrivalTime = arrivalTime.trim(),
                            departureAirport = departureAirport.trim(),
                            arrivalAirport = arrivalAirport.trim(),
                            transferFrom = transferFrom.trim(),
                            transferMinutes = transferMinutesText
                                .toIntOrNull()
                                ?.coerceAtLeast(0)
                                ?: 45,
                            baggageMinutes = baggageMinutesText
                                .toIntOrNull()
                                ?.coerceAtLeast(0)
                                ?: 60,
                            notes = notes.trim()
                        )
                    )
                }
            ) {
                Text("שמירה")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}

@Composable
private fun FlightDateField(
    label: String,
    value: String,
    minimumDate: String = "",
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    val initial = runCatching { LocalDate.parse(value) }
        .getOrElse { LocalDate.now() }

    OutlinedButton(
        onClick = {
            val dialog = DatePickerDialog(
                context,
                { _, year, month, day ->
                    onValueChange(
                        LocalDate.of(year, month + 1, day).toString()
                    )
                },
                initial.year,
                initial.monthValue - 1,
                initial.dayOfMonth
            )

            if (minimumDate.isNotBlank()) {
                runCatching {
                    val minimum = LocalDate.parse(minimumDate)
                    dialog.datePicker.minDate = minimum
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                }
            }
            dialog.show()
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("📅")
        Spacer(Modifier.width(8.dp))
        Text("$label: $value", modifier = Modifier.weight(1f))
    }
}

private fun isValidClockTime(value: String): Boolean {
    return Regex("""(?:[01]\d|2[0-3]):[0-5]\d""")
        .matches(value.trim())
}
