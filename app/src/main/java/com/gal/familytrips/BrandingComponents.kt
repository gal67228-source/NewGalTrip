
package com.gal.familytrips

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun DayThumbnail(imageKey: String, modifier: Modifier = Modifier) {
    val colors = when (imageKey) {
        "flight", "return" -> listOf(Color(0xFFB9D9FF), Color(0xFF4F8FD8))
        "water" -> listOf(Color(0xFFC5F7FF), Color(0xFF20AFC4))
        "hotel" -> listOf(Color(0xFFE1D8FF), Color(0xFF8A6DE9))
        "ferris" -> listOf(Color(0xFFFFE4C6), Color(0xFFFF8C61))
        "zoo" -> listOf(Color(0xFFDDF3D2), Color(0xFF65A85A))
        "island" -> listOf(Color(0xFFD8F6E7), Color(0xFF36A77B))
        else -> listOf(Color(0xFFE8EEFF), Color(0xFF6F7FD8))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        when (imageKey) {
            "flight", "return" -> Icon(Icons.Default.Flight, null, tint = Color.White, modifier = Modifier.size(34.dp))
            "water" -> Icon(Icons.Default.Pool, null, tint = Color.White, modifier = Modifier.size(34.dp))
            "hotel" -> Icon(Icons.Default.Hotel, null, tint = Color.White, modifier = Modifier.size(34.dp))
            "ferris" -> Icon(Icons.Default.Attractions, null, tint = Color.White, modifier = Modifier.size(34.dp))
            "zoo" -> Icon(Icons.Default.Pets, null, tint = Color.White, modifier = Modifier.size(34.dp))
            "island" -> Icon(Icons.Default.Park, null, tint = Color.White, modifier = Modifier.size(34.dp))
            else -> Icon(Icons.Default.LocationCity, null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
fun GoogleMapsBrandIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(Color.White, radius = w / 2)
        drawArc(Color(0xFF4285F4), -40f, 115f, true)
        drawArc(Color(0xFF34A853), 75f, 105f, true)
        drawArc(Color(0xFFFBBC04), 180f, 80f, true)
        drawArc(Color(0xFFEA4335), 260f, 60f, true)
        drawCircle(Color.White, radius = w * .20f, center = center)
        drawCircle(Color(0xFF4285F4), radius = w * .10f, center = center)
    }
}

@Composable
fun WazeBrandIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val blue = Color(0xFF33CCFF)
        drawCircle(blue, radius = size.minDimension * .43f, center = center)
        drawCircle(Color.White, radius = size.minDimension * .33f, center = center)
        val wheelY = size.height * .82f
        drawCircle(Color(0xFF334155), radius = size.width * .07f, center = Offset(size.width * .34f, wheelY))
        drawCircle(Color(0xFF334155), radius = size.width * .07f, center = Offset(size.width * .68f, wheelY))
        drawCircle(Color(0xFF334155), radius = size.width * .025f, center = Offset(size.width * .40f, size.height * .43f))
        drawCircle(Color(0xFF334155), radius = size.width * .025f, center = Offset(size.width * .61f, size.height * .43f))
        drawArc(
            color = Color(0xFF334155),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(size.width * .38f, size.height * .48f),
            size = Size(size.width * .25f, size.height * .18f),
            style = Stroke(width = size.width * .035f)
        )
    }
}

@Composable
fun SmallEditIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(SoftBlue),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Edit, "עריכה", tint = Sky, modifier = Modifier.size(17.dp))
    }
}

@Composable
fun SmallDeleteIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(SoftCoral),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.DeleteOutline, "מחיקה", tint = Coral, modifier = Modifier.size(17.dp))
    }
}
