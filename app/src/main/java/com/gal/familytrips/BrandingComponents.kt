
package com.gal.familytrips

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Offset
import androidx.compose.ui.graphics.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
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

    val emoji = when (imageKey) {
        "flight" -> "✈️"
        "return" -> "🛫"
        "water" -> "🌊"
        "hotel" -> "🏨"
        "ferris" -> "🎡"
        "zoo" -> "🦁"
        "island" -> "🌳"
        else -> "🏙️"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji)
    }
}

@Composable
fun GoogleMapsBrandIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        drawCircle(Color.White, radius = r)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -45f,
            sweepAngle = 115f,
            useCenter = true,
            topLeft = Offset.Zero,
            size = size
        )
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 70f,
            sweepAngle = 105f,
            useCenter = true,
            topLeft = Offset.Zero,
            size = size
        )
        drawArc(
            color = Color(0xFFFBBC04),
            startAngle = 175f,
            sweepAngle = 85f,
            useCenter = true,
            topLeft = Offset.Zero,
            size = size
        )
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 260f,
            sweepAngle = 55f,
            useCenter = true,
            topLeft = Offset.Zero,
            size = size
        )
        drawCircle(Color.White, radius = size.minDimension * .20f, center = center)
        drawCircle(Color(0xFF4285F4), radius = size.minDimension * .10f, center = center)
    }
}

@Composable
fun WazeBrandIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val blue = Color(0xFF33CCFF)
        val dark = Color(0xFF334155)

        drawCircle(
            color = blue,
            radius = size.minDimension * .43f,
            center = center
        )
        drawCircle(
            color = Color.White,
            radius = size.minDimension * .33f,
            center = center
        )

        val wheelY = size.height * .82f
        drawCircle(dark, size.width * .07f, Offset(size.width * .34f, wheelY))
        drawCircle(dark, size.width * .07f, Offset(size.width * .68f, wheelY))
        drawCircle(dark, size.width * .025f, Offset(size.width * .40f, size.height * .43f))
        drawCircle(dark, size.width * .025f, Offset(size.width * .61f, size.height * .43f))
        drawArc(
            color = dark,
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
        Text("✎", color = Sky, fontWeight = FontWeight.Bold)
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
        Text("×", color = Coral, fontWeight = FontWeight.Bold)
    }
}
