package com.edupulse.app.diagram

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Extracts the first signed decimal from strings like "−2.500 m/s²", "10 m/s", "12.5 N". */
private fun parseSignedFloat(input: String?): Float? {
    if (input.isNullOrBlank()) return null
    return Regex("""(-?[0-9]+(?:\.[0-9]+)?)""").find(input.trim())
        ?.groupValues?.getOrNull(1)?.toFloatOrNull()
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagramCard(
    diagram: PhysicsDiagram,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableIntStateOf(0) } // 0: Motion Track, 1: Free-Body Forces
    val scope = rememberCoroutineScope()
    val animProgress = remember { Animatable(0f) }
    var isSimulating by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isLooping by remember { mutableStateOf(true) }
    var animationJob by remember { mutableStateOf<Job?>(null) }

    // Interactive "What-If?" Sandbox State
    var showSandbox by remember { mutableStateOf(false) }

    val isDecel = remember(diagram) {
        if (diagram is PhysicsDiagram.Kinematics) diagram.isDecelerating else true
    }

    val baseU = remember(diagram) {
        if (diagram is PhysicsDiagram.Kinematics) {
            parseSignedFloat(diagram.initialVelocity)?.let { abs(it) }
                ?: if (diagram.isDecelerating) 10f else 0f
        } else 10f
    }
    val baseM = remember(diagram) {
        if (diagram is PhysicsDiagram.Kinematics)
            parseSignedFloat(diagram.mass)?.let { abs(it) } ?: 5f
        else 5f
    }
    // acceleration magnitude (sign carried by isDecel)
    val baseA = remember(diagram) {
        if (diagram is PhysicsDiagram.Kinematics) {
            val a = parseSignedFloat(diagram.acceleration)?.let { abs(it) }
            val f = parseSignedFloat(diagram.force)?.let { abs(it) }
            val m = parseSignedFloat(diagram.mass)?.let { abs(it) }
            a ?: (if (f != null && m != null && m > 0f) f / m else 2.5f)
        } else 2.5f
    }
    val baseT = remember(diagram) {
        if (diagram is PhysicsDiagram.Kinematics) {
            val t = parseSignedFloat(diagram.time)?.let { abs(it) }
            if (t != null && t > 0f) t
            else if (baseA > 0f && baseU > 0f && diagram.isDecelerating) baseU / baseA
            else 4f
        } else 4f
    }
    val baseV = remember(diagram) {
        if (diagram is PhysicsDiagram.Kinematics) {
            parseSignedFloat(diagram.finalVelocity)?.let { abs(it) }
                ?: if (diagram.isDecelerating) 0f else (baseU + baseA * baseT)
        } else 0f
    }
    val baseS = remember(diagram) {
        if (diagram is PhysicsDiagram.Kinematics) {
            val s = parseSignedFloat(diagram.distance)?.let { abs(it) }
            if (s != null && s > 0f) s
            else if (diagram.isDecelerating && baseA > 0f && baseU > 0f) (baseU * baseU) / (2f * baseA)
            else (baseU * baseT + 0.5f * baseA * baseT * baseT).coerceAtLeast(10f)
        } else 20f
    }


    var sandboxU by remember(diagram) { mutableFloatStateOf(baseU) }
    var sandboxM by remember(diagram) { mutableFloatStateOf(baseM) }

    val currentU = if (showSandbox) sandboxU else baseU
    val currentM = if (showSandbox) sandboxM else baseM
    val currentA = baseA
    val currentF = currentM * currentA
    val currentT = baseT
    val currentV = if (isDecel) 0f else (currentU + currentA * currentT)
    val currentS = if (showSandbox) {
        if (isDecel) {
            if (currentA > 0f && currentU > 0f) (currentU * currentU) / (2f * currentA) else 20f
        } else {
            (currentU * currentT + 0.5f * currentA * currentT * currentT).coerceAtLeast(10f)
        }
    } else baseS
    val currentKE0 = 0.5f * currentM * (if (isDecel) currentU * currentU else currentV * currentV)

    fun stopAnimation() {
        animationJob?.cancel()
        animationJob = null
        isSimulating = false
    }

    fun startAnimation() {
        animationJob?.cancel()
        isSimulating = true
        animationJob = scope.launch {
            try {
                if (animProgress.value >= 0.98f) {
                    animProgress.snapTo(0f)
                }
                while (isSimulating) {
                    val remaining = (1f - animProgress.value).coerceAtLeast(0.02f)
                    val baseDurationMs = (currentT * 1000f).coerceIn(1200f, 6000f)
                    val animDuration = ((baseDurationMs / playbackSpeed) * remaining).toInt().coerceAtLeast(100)

                    animProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = animDuration, easing = LinearEasing)
                    )

                    if (isLooping && isSimulating) {
                        delay(900)
                        if (isSimulating) {
                            animProgress.snapTo(0f)
                        }
                    } else {
                        break
                    }
                }
            } finally {
                isSimulating = false
            }
        }
    }

    fun toggleSimulation() {
        if (isSimulating) {
            stopAnimation()
        } else {
            startAnimation()
        }
    }

    fun resetSimulation() {
        stopAnimation()
        scope.launch {
            animProgress.snapTo(0f)
        }
    }

    // Auto-start dynamic simulation when diagram first appears
    LaunchedEffect(diagram) {
        startAnimation()
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Title, Live Status & Tab Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📐", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Physics Simulator",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSimulating) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = if (isSimulating) "● LIVE" else "PAUSED",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = if (isSimulating) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                // View Toggle Pills (Track / Forces)
                if (diagram is PhysicsDiagram.Kinematics) {
                    Row(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(2.dp)
                    ) {
                        Surface(
                            onClick = { activeTab = 0 },
                            shape = RoundedCornerShape(10.dp),
                            color = if (activeTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.height(34.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    "🏎️ Track",
                                    fontSize = 11.sp,
                                    fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (activeTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            onClick = { activeTab = 1 },
                            shape = RoundedCornerShape(10.dp),
                            color = if (activeTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.height(34.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    "⚖️ Forces",
                                    fontSize = 11.sp,
                                    fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (activeTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Canvas Drawing
            when (diagram) {
                is PhysicsDiagram.Kinematics -> {
                    if (activeTab == 0) {
                        KinematicsTrackCanvas(
                            progressProvider = { animProgress.value },
                            currentU = currentU,
                            currentV = currentV,
                            currentA = currentA,
                            currentM = currentM,
                            currentF = currentF,
                            currentS = currentS,
                            isDecelerating = isDecel,
                            isSimulating = isSimulating,
                            onTap = { toggleSimulation() },
                            onSeek = { fraction ->
                                stopAnimation()
                                scope.launch { animProgress.snapTo(fraction) }
                            }
                        )
                    } else {
                        FreeBodyForcesCanvas(
                            mass = "${currentM.toInt()} kg",
                            force = "${String.format(Locale.US, "%.1f", currentF)} N",
                            isDecelerating = isDecel
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

            // Interactive Controls & Dynamic Telemetry (when in Track view)
            if (diagram is PhysicsDiagram.Kinematics && activeTab == 0) {
                // Live Physics Telemetry HUD (decoupled to avoid recomposing buttons)
                LiveTelemetryHud(
                    progressProvider = { animProgress.value },
                    currentT = currentT,
                    currentU = currentU,
                    currentV = currentV,
                    currentS = currentS,
                    currentM = currentM,
                    currentKE0 = currentKE0,
                    isDecelerating = isDecel
                )

                // Interactive Timeline Scrubber Slider (decoupled)
                TimelineScrubber(
                    progressProvider = { animProgress.value },
                    onSeek = { newVal ->
                        stopAnimation()
                        scope.launch { animProgress.snapTo(newVal) }
                    },
                    currentT = currentT,
                    currentS = currentS
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Row 1: Primary Playback Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play / Pause Button
                    Button(
                        onClick = { toggleSimulation() },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(42.dp)
                    ) {
                        Text(
                            text = if (isSimulating) "⏸ Pause" else "▶ Play",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Reset Button
                    OutlinedButton(
                        onClick = { resetSimulation() },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Text("⏮ Reset", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }

                    // Loop Toggle Button
                    OutlinedButton(
                        onClick = { isLooping = !isLooping },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isLooping) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isLooping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(42.dp)
                    ) {
                        Text(
                            if (isLooping) "🔁 Loop ON" else "🔁 Loop OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLooping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Sandbox Toggle Button
                    OutlinedButton(
                        onClick = { showSandbox = !showSandbox },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (showSandbox) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f) else Color.Transparent
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (showSandbox) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(42.dp)
                    ) {
                        Text(
                            if (showSandbox) "🧪 Sandbox" else "🧪 What-If?",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (showSandbox) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Row 2: Speed Multipliers Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Simulation Speed:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(0.5f to "0.5x Slow-Mo", 1.0f to "1.0x Normal", 2.0f to "2.0x Fast").forEach { (speed, label) ->
                            val isSelected = abs(playbackSpeed - speed) < 0.1f
                            Surface(
                                onClick = { playbackSpeed = speed },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier.height(30.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // If Sandbox is opened: Interactive Dynamic Experimentation
                AnimatedVisibility(visible = showSandbox) {
                    Column {
                        Spacer(modifier = Modifier.height(6.dp))
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "🧪 Live What-If? Speed & Mass Lab",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        "s = u² / 2a",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))

                                // Speed Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Initial Velocity (u):", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        String.format(Locale.US, "%.1f m/s", sandboxU),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                                Slider(
                                    value = sandboxU,
                                    onValueChange = {
                                        sandboxU = it
                                        stopAnimation()
                                        scope.launch { animProgress.snapTo(0f) }
                                    },
                                    valueRange = 2f..30f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.tertiary,
                                        activeTrackColor = MaterialTheme.colorScheme.tertiary
                                    )
                                )

                                // Preset Speed Chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(5f, 10f, 15f, 25f).forEach { preset ->
                                        val isSelected = abs(sandboxU - preset) < 0.5f
                                        Surface(
                                            onClick = {
                                                sandboxU = preset
                                                stopAnimation()
                                                scope.launch { animProgress.snapTo(0f) }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 8.dp)
                                            ) {
                                                Text(
                                                    "${preset.toInt()} m/s",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Live Derived Results
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        String.format(Locale.US, "Stopping Distance: %.1f m", currentS),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1565C0)
                                    )
                                    Text(
                                        String.format(Locale.US, "Time to Stop: %.1f s", currentT),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }

            // Key Parameter Badges
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (diagram is PhysicsDiagram.Kinematics) {
                    ParamChip("m", "${currentM.toInt()} kg")
                    ParamChip("u", String.format(Locale.US, "%.1f m/s", currentU), Color(0xFF2E7D32))
                    ParamChip("v", String.format(Locale.US, "%.1f m/s", currentV), if (isDecel) Color(0xFFC62828) else Color(0xFF2E7D32))
                    ParamChip("a", String.format(Locale.US, if (isDecel) "-%.1f m/s²" else "+%.1f m/s²", currentA))
                    ParamChip("F", String.format(Locale.US, if (isDecel) "-%.1f N" else "+%.1f N", currentF), Color(0xFFD84315))
                    ParamChip("s", String.format(Locale.US, "%.1f m", currentS), Color(0xFF1565C0))
                    ParamChip("t", String.format(Locale.US, "%.1f s", currentT))
                }
            }
        }
    }
}

@Composable
private fun LiveTelemetryHud(
    progressProvider: () -> Float,
    currentT: Float,
    currentU: Float,
    currentV: Float,
    currentS: Float,
    currentM: Float,
    currentKE0: Float,
    isDecelerating: Boolean
) {
    val tau = progressProvider()
    val instantT = (tau * currentT).coerceAtLeast(0f)
    val instantV = if (isDecelerating) {
        (currentU * (1f - tau)).coerceAtLeast(0f)
    } else {
        (currentU + (currentV - currentU) * tau).coerceAtLeast(0f)
    }
    val distanceFraction = if (isDecelerating) {
        (2f * tau - tau * tau).coerceIn(0f, 1f)
    } else {
        (tau * tau).coerceIn(0f, 1f)
    }
    val instantS = (currentS * distanceFraction).coerceAtLeast(0f)
    val instantKE = (0.5f * currentM * instantV * instantV).coerceAtLeast(0f)
    val maxKE = maxOf(currentKE0, 1f)
    val keFraction = (instantKE / maxKE).coerceIn(0f, 1f)
    val isDone = tau >= 0.98f

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                TelemetryItem(
                    label = "⏱️ Time",
                    value = String.format(Locale.US, "%.2fs", instantT),
                    subValue = "of ${String.format(Locale.US, "%.1fs", currentT)}"
                )
                TelemetryItem(
                    label = "🚀 Velocity",
                    value = String.format(Locale.US, "%.1f m/s", instantV),
                    subValue = if (isDecelerating) (if (isDone) "Stopped" else "Decelerating") else (if (isDone) "Target Speed" else "Accelerating"),
                    valueColor = if (instantV > 0.5f) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                )
                TelemetryItem(
                    label = "📏 Position",
                    value = String.format(Locale.US, "%.1fm", instantS),
                    subValue = "of ${String.format(Locale.US, "%.1fm", currentS)}",
                    valueColor = Color(0xFF1565C0)
                )
                TelemetryItem(
                    label = "⚡ Kinetic E",
                    value = String.format(Locale.US, "%.0f J", instantKE),
                    subValue = "of ${String.format(Locale.US, "%.0f J", currentKE0)}",
                    valueColor = Color(0xFF7B1FA2)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Work / Energy Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isDecelerating) "⚡ Friction Work (Dissipation):" else "⚡ Kinetic Energy Gain:",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isDecelerating) "${((1f - keFraction) * 100).toInt()}% converted to heat" else "${(keFraction * 100).toInt()}% work done",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (isDone) Color(0xFF2E7D32) else Color(0xFFD84315)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = { (if (isDecelerating) (1f - keFraction) else keFraction).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
                color = if (isDecelerating) Color(0xFFD84315) else Color(0xFF2E7D32),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun TimelineScrubber(
    progressProvider: () -> Float,
    onSeek: (Float) -> Unit,
    currentT: Float,
    currentS: Float
) {
    // Local slider position — tracks animation when idle, holds still during drag
    var sliderPos by remember { mutableFloatStateOf(progressProvider()) }
    var isDragging by remember { mutableStateOf(false) }

    // Sync from animation only when the user isn't dragging
    val liveProgress = progressProvider()
    if (!isDragging) { sliderPos = liveProgress }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "0.0s",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Slider(
            value = sliderPos,
            onValueChange = { v ->
                isDragging = true
                sliderPos = v
                onSeek(v)
            },
            onValueChangeFinished = { isDragging = false },
            modifier = Modifier
                .weight(1f)
                .height(36.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            text = String.format(Locale.US, "%.1fs (%.0fm)", currentT, currentS),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1565C0)
        )
    }
}


@Composable
private fun TelemetryItem(
    label: String,
    value: String,
    subValue: String,
    valueColor: Color = MaterialTheme.colorScheme.primary
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = subValue,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    progressProvider: () -> Float,
    currentU: Float,
    currentV: Float,
    currentA: Float,
    currentM: Float,
    currentF: Float,
    currentS: Float,
    isDecelerating: Boolean,
    isSimulating: Boolean,
    onTap: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val velocityColor = Color(0xFF2E7D32) // Green
    val brakingColor = Color(0xFFD32F2F)  // Red
    val distanceColor = Color(0xFF1976D2) // Blue

    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // startX and endX match the Canvas draw logic (65dp and width-75dp)
                    val startX = 65f * density
                    val endX   = size.width - 75f * density
                    if (endX > startX && offset.x >= startX - 10f && offset.x <= endX + 10f) {
                        // Tap on the track → seek to that fraction
                        val fraction = ((offset.x - startX) / (endX - startX)).coerceIn(0f, 1f)
                        onSeek(fraction)
                    } else {
                        // Tap outside track (cart body area, etc.) → toggle play/pause
                        onTap()
                    }
                }
            }
    ) {

        val tau = progressProvider()
        val maxV = maxOf(currentU, currentV, 1f)
        val distanceFraction = if (isDecelerating) {
            (2f * tau - tau * tau).coerceIn(0f, 1f)
        } else {
            (tau * tau).coerceIn(0f, 1f)
        }
        val instantV = if (isDecelerating) {
            (currentU * (1f - tau)).coerceAtLeast(0f)
        } else {
            (currentU + (currentV - currentU) * tau).coerceAtLeast(0f)
        }
        val instantS = (currentS * distanceFraction).coerceAtLeast(0f)
        val speedFraction = (instantV / maxV).coerceIn(0f, 1f)

        val width = size.width
        val height = size.height

        val groundY = height * 0.60f
        val startX = 65.dp.toPx()
        val endX = width - 75.dp.toPx()
        val trackSpan = endX - startX

        // 1. Draw ground track
        drawLine(
            color = outlineColor.copy(alpha = 0.7f),
            start = Offset(16.dp.toPx(), groundY),
            end = Offset(width - 16.dp.toPx(), groundY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Ground hash lines
        val hashStep = 18.dp.toPx()
        var hx = 22.dp.toPx()
        while (hx < width - 22.dp.toPx()) {
            drawLine(
                color = outlineColor.copy(alpha = 0.25f),
                start = Offset(hx, groundY),
                end = Offset(hx - 8.dp.toPx(), groundY + 8.dp.toPx()),
                strokeWidth = 1.5.dp.toPx()
            )
            hx += hashStep
        }

        // 2. Start and End Position Markers
        val blockW = 66.dp.toPx()
        val blockH = 38.dp.toPx()
        val wheelRadius = 6.dp.toPx()

        // Dynamic Skid / Tire Trail
        val currentBlockX = startX + (trackSpan * distanceFraction)
        if (distanceFraction > 0.01f) {
            drawLine(
                color = if (isDecelerating) Color(0x772B2B2B) else Color(0x551976D2),
                start = Offset(startX, groundY - 1f),
                end = Offset(currentBlockX, groundY - 1f),
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Ghost End Position
        drawRoundRect(
            color = outlineColor.copy(alpha = 0.2f),
            topLeft = Offset(endX - blockW / 2, groundY - blockH - wheelRadius * 2),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        )

        // Target flag / Rest indicator at end
        val flagColor = if (isDecelerating) brakingColor else Color(0xFF2E7D32)
        val flagTopY = groundY - blockH - wheelRadius * 2 - 14.dp.toPx()
        drawLine(
            color = flagColor,
            start = Offset(endX, flagTopY),
            end = Offset(endX, groundY),
            strokeWidth = 2.dp.toPx()
        )
        // Pennant flag
        val flagPath = Path().apply {
            moveTo(endX, flagTopY)
            lineTo(endX + 14.dp.toPx(), flagTopY + 6.dp.toPx())
            lineTo(endX, flagTopY + 12.dp.toPx())
            close()
        }
        drawPath(path = flagPath, color = flagColor)

        // Ripple Wave when Done
        if (tau >= 0.98f) {
            drawCircle(
                color = Color(0xFF2E7D32).copy(alpha = 0.35f),
                radius = 16.dp.toPx(),
                center = Offset(endX, flagTopY),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 3. Dynamic Moving Cart & Rotating Wheels
        val cartTop = groundY - wheelRadius * 2 - blockH

        // Contact Friction Sparks / Smoke Particles behind rear wheel during motion
        if (isSimulating && speedFraction > 0.05f) {
            val rearWheelX = currentBlockX - 18.dp.toPx()
            val particleSeed = (tau * 100).toInt()
            for (i in 0 until 4) {
                val pProgress = ((particleSeed + i * 5) % 20) / 20f
                val px = rearWheelX - (pProgress * 22.dp.toPx())
                val py = groundY - wheelRadius - (pProgress * 10.dp.toPx())
                val pAlpha = (1f - pProgress) * 0.5f * speedFraction
                drawCircle(
                    color = Color(0xFF757575).copy(alpha = pAlpha),
                    radius = (1.5f + pProgress * 2.5f).dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }

        // Draw Cart Body
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(currentBlockX - blockW / 2, cartTop),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(8.dp.toPx())
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(currentBlockX - blockW / 2, cartTop),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Rotating Wheels (Front & Rear)
        val wheelAngle = (instantS * 75f / wheelRadius)
        val wheel1Center = Offset(currentBlockX - 18.dp.toPx(), groundY - wheelRadius)
        val wheel2Center = Offset(currentBlockX + 18.dp.toPx(), groundY - wheelRadius)
        val wheelColor = Color(0xFF263238)

        drawSpokeWheel(wheel1Center, wheelRadius, wheelAngle, wheelColor)
        drawSpokeWheel(wheel2Center, wheelRadius, wheelAngle, wheelColor)

        // 4. Dynamic Vector Arrows: Velocity & Force
        val vArrowY = cartTop - 12.dp.toPx()
        val fArrowY = cartTop + blockH / 2

        if (speedFraction > 0.04f || !isDecelerating) {
            // Velocity Arrow (Right ->) dynamically scaled by instantaneous velocity
            val vArrowLen = (70.dp.toPx() * speedFraction).coerceIn(16.dp.toPx(), 80.dp.toPx())
            val vColor = if (speedFraction > 0.4f) velocityColor else Color(0xFFEF6C00)
            drawArrow(
                color = vColor,
                start = Offset(currentBlockX, vArrowY),
                end = Offset(currentBlockX + vArrowLen, vArrowY),
                strokeWidth = 3.dp.toPx()
            )

            if (isDecelerating) {
                // Braking Force Arrow (Left <-) opposing motion
                val fArrowLen = 42.dp.toPx()
                drawArrow(
                    color = brakingColor,
                    start = Offset(currentBlockX - blockW / 2, fArrowY),
                    end = Offset(currentBlockX - blockW / 2 - fArrowLen, fArrowY),
                    strokeWidth = 3.dp.toPx()
                )
            } else {
                // Accelerating Force Arrow (Right ->) in direction of motion
                val fArrowLen = 42.dp.toPx()
                drawArrow(
                    color = Color(0xFF1976D2),
                    start = Offset(currentBlockX + blockW / 2, fArrowY),
                    end = Offset(currentBlockX + blockW / 2 + fArrowLen, fArrowY),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }

        // 5. Dimension Line (Distance 's') below ground track
        val dimY = groundY + 22.dp.toPx()
        val tickH = 6.dp.toPx()

        drawLine(
            color = distanceColor.copy(alpha = 0.8f),
            start = Offset(startX, dimY - tickH),
            end = Offset(startX, dimY + tickH),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = distanceColor.copy(alpha = 0.8f),
            start = Offset(endX, dimY - tickH),
            end = Offset(endX, dimY + tickH),
            strokeWidth = 2.dp.toPx()
        )
        drawDoubleArrow(
            color = distanceColor.copy(alpha = 0.8f),
            start = Offset(startX, dimY),
            end = Offset(endX, dimY),
            strokeWidth = 1.8.dp.toPx()
        )

        // Native Text Rendering on Canvas for 60/120fps dynamic text
        drawContext.canvas.nativeCanvas.apply {
            // Mass label inside cart body
            textPaint.color = android.graphics.Color.WHITE
            textPaint.textSize = 28f
            drawText("${currentM.toInt()} kg", currentBlockX, cartTop + blockH * 0.65f, textPaint)

            // Dynamic Velocity label above velocity vector
            if (speedFraction > 0.05f || !isDecelerating) {
                textPaint.color = if (speedFraction > 0.4f) 0xFF2E7D32.toInt() else 0xFFEF6C00.toInt()
                textPaint.textSize = 26f
                drawText(
                    "v = ${String.format(Locale.US, "%.1f m/s", instantV)}",
                    currentBlockX + 32.dp.toPx(),
                    vArrowY - 6.dp.toPx(),
                    textPaint
                )

                if (isDecelerating) {
                    textPaint.color = 0xFFD32F2F.toInt()
                    textPaint.textSize = 26f
                    drawText(
                        "F = -${String.format(Locale.US, "%.1f N", currentF)}",
                        currentBlockX - blockW / 2 - 22.dp.toPx(),
                        fArrowY - 8.dp.toPx(),
                        textPaint
                    )
                } else {
                    textPaint.color = 0xFF1976D2.toInt()
                    textPaint.textSize = 26f
                    drawText(
                        "F = +${String.format(Locale.US, "%.1f N", currentF)}",
                        currentBlockX + blockW / 2 + 22.dp.toPx(),
                        fArrowY - 8.dp.toPx(),
                        textPaint
                    )
                }
            }

            // Indicator text at end
            if (tau >= 0.98f) {
                textPaint.color = 0xFF2E7D32.toInt()
                textPaint.textSize = 26f
                drawText(
                    if (isDecelerating) "✓ STOPPED" else "🏁 TARGET REACHED",
                    endX,
                    flagTopY - 6.dp.toPx(),
                    textPaint
                )
            }
        }
    }
}

private fun DrawScope.drawSpokeWheel(
    center: Offset,
    radius: Float,
    rotationRad: Float,
    wheelColor: Color
) {
    // Outer tire
    drawCircle(color = wheelColor, radius = radius, center = center)
    // Rim highlight
    drawCircle(color = Color.LightGray, radius = radius * 0.7f, center = center)
    // Hub
    drawCircle(color = wheelColor, radius = radius * 0.28f, center = center)

    // 4 spinning cross-spokes
    val spokeLen = radius * 0.7f
    for (i in 0 until 4) {
        val angle = rotationRad + (i * (PI / 2.0)).toFloat()
        val sx = center.x + spokeLen * cos(angle)
        val sy = center.y + spokeLen * sin(angle)
        drawLine(
            color = Color.DarkGray,
            start = center,
            end = Offset(sx, sy),
            strokeWidth = 1.5f
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

    var activeForceName by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        activeForceName = when {
                            dy < -20.dp.toPx() -> if (activeForceName == "Normal") null else "Normal"
                            dy > 20.dp.toPx() -> if (activeForceName == "Gravity") null else "Gravity"
                            dx < -20.dp.toPx() -> if (activeForceName == "Friction") null else "Friction"
                            dx > 20.dp.toPx() -> if (activeForceName == "Motion") null else "Motion"
                            else -> if (activeForceName != null) null else "Mass"
                        }
                    }
                }
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
            drawRoundRect(
                color = Color.White.copy(alpha = 0.8f),
                topLeft = Offset(cx - boxSize / 2, cy - boxSize / 2),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 1. Normal Force (Upwards ↑)
            drawArrow(
                color = if (activeForceName == "Normal") Color(0xFF0D47A1) else normalColor,
                start = Offset(cx, cy - boxSize / 2),
                end = Offset(cx, cy - boxSize / 2 - 45.dp.toPx()),
                strokeWidth = if (activeForceName == "Normal") 4.dp.toPx() else 2.5.dp.toPx()
            )

            // 2. Gravitational Force (Downwards ↓)
            drawArrow(
                color = if (activeForceName == "Gravity") Color(0xFF4A148C) else gravityColor,
                start = Offset(cx, cy + boxSize / 2),
                end = Offset(cx, cy + boxSize / 2 + 45.dp.toPx()),
                strokeWidth = if (activeForceName == "Gravity") 4.dp.toPx() else 2.5.dp.toPx()
            )

            // 3. Motion vector (Right →)
            drawArrow(
                color = if (activeForceName == "Motion") Color(0xFF1B5E20) else greenColor,
                start = Offset(cx + boxSize / 2, cy),
                end = Offset(cx + boxSize / 2 + 50.dp.toPx(), cy),
                strokeWidth = if (activeForceName == "Motion") 4.dp.toPx() else 2.5.dp.toPx()
            )

            // 4. Retarding / Friction Force (Left ←)
            drawArrow(
                color = if (activeForceName == "Friction") Color(0xFFB71C1C) else redColor,
                start = Offset(cx - boxSize / 2, cy),
                end = Offset(cx - boxSize / 2 - 50.dp.toPx(), cy),
                strokeWidth = if (activeForceName == "Friction") 4.dp.toPx() else 2.5.dp.toPx()
            )
        }

        // Interactive Vector Inspection Card
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            val text = when (activeForceName) {
                "Normal" -> "⬆ Normal Reaction (N = mg): Perpendicular contact force supporting mass."
                "Gravity" -> "⬇ Gravity Force (W = mg): Downward pull of Earth toward center."
                "Friction" -> "⬅ Friction / Retarding Force ($force): Opposes direction of velocity, slowing block."
                "Motion" -> "➡ Motion Vector: Forward initial velocity direction."
                "Mass" -> "📦 Body of mass $mass: In contact with rough horizontal surface."
                else -> "👆 Tap any force arrow to inspect its role in free-body diagram"
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = if (activeForceName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                fontWeight = if (activeForceName != null) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ProjectileCanvas(
    diagram: PhysicsDiagram.Projectile,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val scope = rememberCoroutineScope()
    val projProgress = remember(diagram) { Animatable(0f) }

    // Parse diagram values (DiagramExtractor already solved these)
    val angleDeg = parseSignedFloat(diagram.angle) ?: 45f
    val v0       = parseSignedFloat(diagram.velocity) ?: 20f
    val rangeM   = parseSignedFloat(diagram.range) ?: 40f
    val hMaxM    = parseSignedFloat(diagram.maxHeight) ?: 10f
    val tFlight  = parseSignedFloat(diagram.timeOfFlight) ?: 2.9f
    // Duration in ms (capped 1s-5s for visual comfort)
    val durationMs = (tFlight * 900f).coerceIn(1000f, 5000f).toInt()

    // angle in radians — used to position launch arrow and apex
    val angleRad = (angleDeg * PI / 180.0).toFloat()

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .pointerInput(Unit) {
                    detectTapGestures {
                        scope.launch {
                            if (projProgress.value >= 0.98f) projProgress.snapTo(0f)
                            projProgress.animateTo(
                                1f,
                                animationSpec = tween(durationMs, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                }
        ) {
            val groundY = size.height * 0.82f
            val startX  = 44.dp.toPx()
            val endX    = size.width - 44.dp.toPx()
            val trackW  = endX - startX

            // Apex X position: proportional to sin(2θ) — at 45° it's centre; lower/higher angles shift it
            // For a symmetric parabola R ∝ sin(2θ), apex is always at R/2
            // We map this onto the canvas track width directly.
            val apexX = startX + trackW / 2f
            // Apex Y: hMax mapped so that 45°/20m/s (≈10m) → top 15% of canvas; scales with hMaxM
            val canvasHeightAvail = groundY - 10.dp.toPx()
            // normalise hMax relative to a "reference" height (v0²/2g) and scale to canvas
            val refH = (v0 * v0) / (2f * 9.8f)  // max possible height (90° launch)
            val hFraction = (hMaxM / refH.coerceAtLeast(1f)).coerceIn(0.1f, 0.95f)
            val apexY = groundY - canvasHeightAvail * hFraction

            // Ground line
            drawLine(
                color = outlineColor,
                start = Offset(20.dp.toPx(), groundY),
                end = Offset(size.width - 20.dp.toPx(), groundY),
                strokeWidth = 2.dp.toPx()
            )

            // Dashed parabolic trajectory
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

            // Launch vector arrow — direction from actual angle
            val arrowLen = 44.dp.toPx()
            drawArrow(
                color = Color(0xFF2E7D32),
                start = Offset(startX, groundY),
                end = Offset(startX + arrowLen * cos(angleRad), groundY - arrowLen * sin(angleRad)),
                strokeWidth = 2.5.dp.toPx()
            )

            // Max height dotted line
            if (projProgress.value > 0.1f) {
                drawLine(
                    color = Color(0xFF7B1FA2).copy(alpha = 0.5f),
                    start = Offset(apexX - 16.dp.toPx(), apexY),
                    end = Offset(apexX, apexY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                )
            }

            // Range dimension line at bottom
            if (projProgress.value >= 0.98f) {
                drawDoubleArrow(
                    color = Color(0xFF1565C0).copy(alpha = 0.7f),
                    start = Offset(startX, groundY + 16.dp.toPx()),
                    end   = Offset(endX, groundY + 16.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // Animated ball along Bezier parabola
            val t = projProgress.value
            val mt = 1f - t
            val ballX = mt * mt * startX + 2 * mt * t * apexX + t * t * endX
            val ballY = mt * mt * groundY + 2 * mt * t * apexY + t * t * groundY

            // Ball velocity arrow (tangent direction)
            if (t in 0.05f..0.95f) {
                val dtBallX = 2f * (mt * (apexX - startX) + t * (endX - apexX))
                val dtBallY = 2f * (mt * (apexY - groundY) + t * (groundY - apexY))
                val len     = kotlin.math.sqrt(dtBallX * dtBallX + dtBallY * dtBallY).coerceAtLeast(1f)
                val arrowScale = 24.dp.toPx()
                drawArrow(
                    color = Color(0xFF2E7D32).copy(alpha = 0.7f),
                    start = Offset(ballX, ballY),
                    end   = Offset(ballX + dtBallX / len * arrowScale, ballY + dtBallY / len * arrowScale),
                    strokeWidth = 2f.dp.toPx()
                )
            }

            drawCircle(color = Color(0xFFD32F2F), radius = 7.dp.toPx(), center = Offset(ballX, ballY))
            drawCircle(color = Color.White,        radius = 2.5.dp.toPx(), center = Offset(ballX, ballY))

            // Native text labels
            drawContext.canvas.nativeCanvas.apply {
                val tp = Paint().apply {
                    isAntiAlias = true; textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                tp.textSize = 26f; tp.color = 0xFF7B1FA2.toInt()
                if (projProgress.value > 0.05f) {
                    drawText("H = %.1fm".format(hMaxM), apexX - 26.dp.toPx(), apexY - 6.dp.toPx(), tp)
                }
                if (projProgress.value >= 0.98f) {
                    tp.textSize = 24f; tp.color = 0xFF1565C0.toInt()
                    drawText("R = %.1fm".format(rangeM), (startX + endX) / 2f, groundY + 28.dp.toPx(), tp)
                }
                tp.textSize = 24f; tp.color = 0xFF2E7D32.toInt()
                drawText("θ = %.0f°".format(angleDeg), startX + 32.dp.toPx(), groundY - 48.dp.toPx(), tp)
            }
        }

        // Controls row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        projProgress.snapTo(0f)
                        projProgress.animateTo(1f, animationSpec = tween(durationMs, easing = FastOutSlowInEasing))
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = when {
                        projProgress.value > 0f && projProgress.value < 0.98f -> "🏹 Flying…"
                        projProgress.value >= 0.98f -> "🔁 Re-Launch"
                        else -> "▶ Launch"
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "v₀ = %.1f m/s  |  t = %.1f s".format(v0, tFlight),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "👆 Tap canvas to launch",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
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
