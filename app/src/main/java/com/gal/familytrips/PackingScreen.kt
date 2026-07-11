
package com.gal.familytrips

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.UUID

@Composable
fun PackingScreen(
    trip: Trip,
    onTripChange: (Trip) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PackingItem?>(null) }
    var selectedCategory by remember { mutableStateOf("הכול") }

    val categories = listOf("הכול") +
        trip.packingItems.map { it.category }.distinct().sorted()

    val filtered = if (selectedCategory == "הכול") {
        trip.packingItems
    } else {
        trip.packingItems.filter { it.category == selectedCategory }
    }

    val packedCount = trip.packingItems.count { it.packed }
    val totalCount = trip.packingItems.size
    val progress = if (totalCount == 0) 0f else packedCount.toFloat() / totalCount.toFloat()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            GradientHeader(
                title = "ציוד ואריזה",
                subtitle = "רשימת אריזה נפרדת לכל טיול",
                emoji = "🧳",
                start = Color(0xFF7C69D9),
                end = Navy
            )
        }

        item {
            SectionCard(containerColor = SoftLavender) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("התקדמות האריזה", fontWeight = FontWeight.Bold)
                        Text(
                            "$packedCount מתוך $totalCount פריטים נארזו",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(CardWhite),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${(progress * 100).toInt()}%",
                            fontWeight = FontWeight.Bold,
                            color = Lavender
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Lavender,
                    trackColor = CardWhite
                )

                AccentButton(
                    text = "הוספת פריט",
                    emoji = "＋",
                    onClick = { showAdd = true },
                    color = Lavender,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
                edgePadding = 0.dp,
                containerColor = Background,
                divider = {}
            ) {
                categories.forEach { category ->
                    Tab(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        text = {
                            Text(
                                category,
                                maxLines = 1,
                                color = if (selectedCategory == category) Lavender else TextSecondary
                            )
                        }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                SectionCard(containerColor = CardWhite) {
                    Text("אין פריטים בקטגוריה הזו", color = TextSecondary)
                }
            }
        }

        items(filtered, key = { it.id }) { item ->
            PackingItemCard(
                item = item,
                onPackedChange = { packed ->
                    onTripChange(
                        trip.copy(
                            packingItems = trip.packingItems.map {
                                if (it.id == item.id) it.copy(packed = packed) else it
                            }
                        )
                    )
                },
                onEdit = { editing = item },
                onDelete = {
                    onTripChange(
                        trip.copy(
                            packingItems = trip.packingItems.filterNot { it.id == item.id }
                        )
                    )
                }
            )
        }

        item {
            if (trip.packingItems.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        onTripChange(
                            trip.copy(
                                packingItems = trip.packingItems.map { it.copy(packed = false) }
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("איפוס כל הסימונים")
                }
            }
        }
    }

    if (showAdd) {
        PackingEditorDialog(
            title = "פריט חדש",
            item = null,
            onDismiss = { showAdd = false },
            onConfirm = { newItem ->
                onTripChange(
                    trip.copy(packingItems = trip.packingItems + newItem)
                )
                showAdd = false
            }
        )
    }

    editing?.let { item ->
        PackingEditorDialog(
            title = "עריכת פריט",
            item = item,
            onDismiss = { editing = null },
            onConfirm = { updated ->
                onTripChange(
                    trip.copy(
                        packingItems = trip.packingItems.map {
                            if (it.id == updated.id) updated else it
                        }
                    )
                )
                editing = null
            }
        )
    }
}

@Composable
private fun PackingItemCard(
    item: PackingItem,
    onPackedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.packed) SoftMint else CardWhite
        ),
        border = BorderStroke(
            1.dp,
            if (item.packed) Color(0xFFBFE5D0) else Color(0xFFE3E9F0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.packed,
                onCheckedChange = onPackedChange
            )

            Spacer(Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontWeight = FontWeight.Bold,
                    color = if (item.packed) Color(0xFF3B6F55) else Navy
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        item.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = Lavender
                    )
                    if (item.quantity > 1) {
                        Text(
                            "כמות: ${item.quantity}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                if (item.notes.isNotBlank()) {
                    Text(
                        item.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp)
            ) {
                SmallEditIcon(Modifier.size(28.dp))
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                SmallDeleteIcon(Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun PackingEditorDialog(
    title: String,
    item: PackingItem?,
    onDismiss: () -> Unit,
    onConfirm: (PackingItem) -> Unit
) {
    var name by remember(item?.id) { mutableStateOf(item?.name.orEmpty()) }
    var category by remember(item?.id) { mutableStateOf(item?.category ?: "כללי") }
    var quantityText by remember(item?.id) { mutableStateOf((item?.quantity ?: 1).toString()) }
    var notes by remember(item?.id) { mutableStateOf(item?.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("שם הפריט") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("קטגוריה") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter(Char::isDigit) },
                    label = { Text("כמות") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("הערות") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            PackingItem(
                                id = item?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                category = category.trim().ifBlank { "כללי" },
                                packed = item?.packed ?: false,
                                quantity = quantityText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                                notes = notes.trim()
                            )
                        )
                    }
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
