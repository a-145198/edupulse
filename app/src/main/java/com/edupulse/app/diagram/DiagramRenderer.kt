package com.edupulse.app.diagram

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagramCard(
    diagram: PhysicsDiagram,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(0) } // 0: Motion Track, 1: Free-Body Forces
    val scope = rememberCoroutineScope()
    val animProgress = remember { Animatable(0f) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Title & Tab Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📐", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Physics Diagram",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // View Toggle Pills
                if (diagram is PhysicsDiagram.Kinematics) {
                    Row(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(2.dp)
                    ) {
                        Surface(
                            onClick = { activeTab = 0 },
                            shape = RoundedCornerShape(10.dp),
                            color = if (activeTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.height(26.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    "Track",
                                    fontSize = 11.sp,
                                    fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (activeTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            onClick = { activeTab = 1 },
                            shape = RoundedCornerShape(10.dp),
                            color = if (activeTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.height(26.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    "Forces",
                                    fontSize = 11.sp,
                                    fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (activeTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Canvas Drawing
            when (diagram) {
                is PhysicsDiagram.Kinematics -> {
                    if (activeTab == 0) {
                        KinematicsTrackCanvas(
                            diagram = diagram,
                            progress = animProgress.value
                        )
                    } else {
                        FreeBodyForcesCanvas(
                            mass = diagram.mass ?: "m",
                            force = diagram.force ?: "F",
                            isDecelerating = diagram.isDecelerating
                        )
                    }
                }
                is PhysicsDiagram.FreeBody -> {
                    FreeBodyForcesCanvas(
                        mass = diagram.mass ?: "m",
                        force = diagram.frictionForce ?: diagram.appliedForce ?: "F",
                        isDecelerating = true
                    )
                }
                is PhysicsDiagram.Projectile -> {
                    ProjectileCanvas(diagram = diagram)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Animation Trigger & Chips Row
            if (diagram is PhysicsDiagram.Kinematics && activeTab == 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            scope.launch {
                                animProgress.snapTo(0f)
                                animProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = 1800, easing = FastOutSlowInEasing)
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.height(30.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Text("▶", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Simulate Motion",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    if (animProgress.value > 0f) {
                        Text(
                            text = if (animProgress.value >= 0.99f) "✓ Stopped at Rest" else "Braking...",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (animProgress.value >= 0.99f) Color(0xFF388E3C) else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Key Parameter Badges
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (diagram is PhysicsDiagram.Kinematics) {
                    diagram.mass?.let { ParamChip("m", it) }
                    diagram.initialVelocity?.let { ParamChip("u", it, Color(0xFF2E7D32)) }
                    diagram.finalVelocity?.let { ParamChip("v", it, Color(0xFFC62828)) }
                    diagram.acceleration?.let { ParamChip("a", it) }
                    diagram.force?.let { ParamChip("F", it, Color(0xFFD84315)) }
                    diagram.distance?.let { ParamChip("s", it, Color(0xFF1565C0)) }
                    diagram.time?.let { ParamChip("t", it) }
                }
            }
        }
    }
}

@Composable
private fun ParamChip(label: String, value: String, accentColor: Color? = null) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label = ",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor ?: MaterialTheme.colorScheme.primary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun KinematicsTrackCanvas(
    diagram: PhysicsDiagram.Kinematics,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val velocityColor = Color(0xFF2E7D32) // Green
    val brakingColor = Color(0xFFD32F2F)  // Red
    val distanceColor = Color(0xFF1976D2) // Blue

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
    ) {
        val width = size.width
        val height = size.height

        val groundY = height * 0.62f
        val startX = 70.dp.toPx()
        val endX = width - 80.dp.toPx()
        val trackSpan = endX - startX

        // 1. Draw ground track
        drawLine(
            color = outlineColor.copy(alpha = 0.6f),
            start = Offset(20.dp.toPx(), groundY),
            end = Offset(width - 20.dp.toPx(), groundY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Ground hash lines
        val hashStep = 18.dp.toPx()
        var hx = 24.dp.toPx()
        while (hx < width - 24.dp.toPx()) {
            drawLine(
                color = outlineColor.copy(alpha = 0.25f),
                start = Offset(hx, groundY),
                end = Offset(hx - 8.dp.toPx(), groundY + 8.dp.toPx()),
                strokeWidth = 1.5.dp.toPx()
            )
            hx += hashStep
        }

        // 2. Start and End Position Markers
        // Ghost End Position (where it stops)
        val blockW = 64.dp.toPx()
        val blockH = 40.dp.toPx()
        drawRoundRect(
            color = outlineColor.copy(alpha = 0.2f),
            topLeft = Offset(endX - blockW / 2, groundY - blockH),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        )

        // Stop flag / Rest indicator at end
        drawLine(
            color = brakingColor,
            start = Offset(endX, groundY - blockH - 12.dp.toPx()),
            end = Offset(endX, groundY),
            strokeWidth = 2.dp.toPx()
        )
        drawCircle(
            color = brakingColor,
            radius = 4.dp.toPx(),
            center = Offset(endX, groundY - blockH - 12.dp.toPx())
        )

        // 3. Current Animated Block Position
        val currentBlockX = startX + (trackSpan * progress)
        val currentBlockTop = groundY - blockH

        // Draw active mass block
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(currentBlockX - blockW / 2, currentBlockTop),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(8.dp.toPx())
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.8f),
            topLeft = Offset(currentBlockX - blockW / 2, currentBlockTop),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // 4. Vector Arrows: Initial Velocity & Braking Force
        if (progress < 0.95f) {
            // Velocity Arrow (Right ->)
            val vArrowY = currentBlockTop - 14.dp.toPx()
            val vArrowStart = currentBlockX + 10.dp.toPx()
            val vArrowEnd = currentBlockX + blockW / 2 + 45.dp.toPx()
            drawArrow(
                color = velocityColor,
                start = Offset(vArrowStart, vArrowY),
                end = Offset(vArrowEnd, vArrowY),
                strokeWidth = 2.5.dp.toPx()
            )

            // Braking Force Arrow (Left <-)
            if (diagram.force != null || diagram.acceleration != null) {
                val fArrowY = currentBlockTop + blockH / 2
                val fArrowStart = currentBlockX - blockW / 2
                val fArrowEnd = currentBlockX - blockW / 2 - 42.dp.toPx()
                drawArrow(
                    color = brakingColor,
                    start = Offset(fArrowStart, fArrowY),
                    end = Offset(fArrowEnd, fArrowY),
                    strokeWidth = 2.5.dp.toPx()
                )
            }
        }

        // 5. Dimension Line (Distance 's') below ground track
        val dimY = groundY + 22.dp.toPx()
        val tickH = 6.dp.toPx()

        // Left extension tick
        drawLine(
            color = distanceColor.copy(alpha = 0.8f),
            start = Offset(startX, dimY - tickH),
            end = Offset(startX, dimY + tickH),
            strokeWidth = 2.dp.toPx()
        )
        // Right extension tick
        drawLine(
            color = distanceColor.copy(alpha = 0.8f),
            start = Offset(endX, dimY - tickH),
            end = Offset(endX, dimY + tickH),
            strokeWidth = 2.dp.toPx()
        )
        // Horizontal span with double arrowheads
        drawDoubleArrow(
            color = distanceColor.copy(alpha = 0.8f),
            start = Offset(startX, dimY),
            end = Offset(endX, dimY),
            strokeWidth = 1.8.dp.toPx()
        )
    }
}

@Composable
private fun FreeBodyForcesCanvas(
    mass: String,
    force: String,
    isDecelerating: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val normalColor = Color(0xFF1976D2)  // Blue
    val gravityColor = Color(0xFF7B1FA2) // Purple
    val redColor = Color(0xFFD32F2F)     // Red
    val greenColor = Color(0xFF388E3C)   // Green

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
    ) {
        val cx = size.width / 2
        val cy = size.height / 2
        val boxSize = 54.dp.toPx()

        // Center Box
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(cx - boxSize / 2, cy - boxSize / 2),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(8.dp.toPx())
        )

        // 1. Normal Force (Upwards ↑)
        drawArrow(
            color = normalColor,
            start = Offset(cx, cy - boxSize / 2),
            end = Offset(cx, cy - boxSize / 2 - 45.dp.toPx()),
            strokeWidth = 2.5.dp.toPx()
        )

        // 2. Gravitational Force (Downwards ↓)
        drawArrow(
            color = gravityColor,
            start = Offset(cx, cy + boxSize / 2),
            end = Offset(cx, cy + boxSize / 2 + 45.dp.toPx()),
            strokeWidth = 2.5.dp.toPx()
        )

        // 3. Motion vector (Right →)
        drawArrow(
            color = greenColor,
            start = Offset(cx + boxSize / 2, cy),
            end = Offset(cx + boxSize / 2 + 50.dp.toPx(), cy),
            strokeWidth = 2.5.dp.toPx()
        )

        // 4. Retarding / Friction Force (Left ←)
        drawArrow(
            color = redColor,
            start = Offset(cx - boxSize / 2, cy),
            end = Offset(cx - boxSize / 2 - 50.dp.toPx(), cy),
            strokeWidth = 2.5.dp.toPx()
        )
    }
}

@Composable
private fun ProjectileCanvas(
    diagram: PhysicsDiagram.Projectile,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
    ) {
        val groundY = size.height * 0.8f
        val startX = 40.dp.toPx()
        val endX = size.width - 40.dp.toPx()
        val apexX = (startX + endX) / 2
        val apexY = size.height * 0.25f

        // Ground line
        drawLine(
            color = outlineColor,
            start = Offset(20.dp.toPx(), groundY),
            end = Offset(size.width - 20.dp.toPx(), groundY),
            strokeWidth = 2.dp.toPx()
        )

        // Parabola path
        val path = Path().apply {
            moveTo(startX, groundY)
            quadraticTo(apexX, apexY, endX, groundY)
        }
        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(
                width = 3.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
            )
        )

        // Launch vector arrow
        drawArrow(
            color = Color(0xFF2E7D32),
            start = Offset(startX, groundY),
            end = Offset(startX + 40.dp.toPx(), groundY - 40.dp.toPx()),
            strokeWidth = 2.5.dp.toPx()
        )
    }
}

private fun DrawScope.drawArrow(
    color: Color,
    start: Offset,
    end: Offset,
    strokeWidth: Float = 4f,
    headLength: Float = 22f
) {
    drawLine(color = color, start = start, end = end, strokeWidth = strokeWidth, cap = StrokeCap.Round)

    val dx = end.x - start.x
    val dy = end.y - start.y
    val angle = kotlin.math.atan2(dy, dx)

    val arrowAngle = PI / 6.0 // 30 degrees
    val x1 = end.x - headLength * cos(angle - arrowAngle).toFloat()
    val y1 = end.y - headLength * sin(angle - arrowAngle).toFloat()
    val x2 = end.x - headLength * cos(angle + arrowAngle).toFloat()
    val y2 = end.y - headLength * sin(angle + arrowAngle).toFloat()

    val path = Path().apply {
        moveTo(end.x, end.y)
        lineTo(x1, y1)
        lineTo(x2, y2)
        close()
    }
    drawPath(path = path, color = color)
}

private fun DrawScope.drawDoubleArrow(
    color: Color,
    start: Offset,
    end: Offset,
    strokeWidth: Float = 3f,
    headLength: Float = 16f
) {
    drawLine(color = color, start = start, end = end, strokeWidth = strokeWidth)

    // Left arrowhead (<)
    val leftPath = Path().apply {
        moveTo(start.x, start.y)
        lineTo(start.x + headLength, start.y - headLength * 0.5f)
        lineTo(start.x + headLength, start.y + headLength * 0.5f)
        close()
    }
    drawPath(path = leftPath, color = color)

    // Right arrowhead (>)
    val rightPath = Path().apply {
        moveTo(end.x, end.y)
        lineTo(end.x - headLength, end.y - headLength * 0.5f)
        lineTo(end.x - headLength, end.y + headLength * 0.5f)
        close()
    }
    drawPath(path = rightPath, color = color)
}
