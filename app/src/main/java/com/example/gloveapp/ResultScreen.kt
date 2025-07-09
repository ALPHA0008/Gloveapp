package com.example.gloveapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*
import com.example.gloveapp.ui.theme.GloveAppTheme

data class FingerScore(
    val name: String,
    val score: Float,
    val color: Color
)

@Composable
fun NeonCircularProgressIndicator(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 80.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 8.dp
) {
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 1000,
            easing = androidx.compose.animation.core.EaseOutCubic
        )
    )
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(size)
                .blur(4.dp)
        ) {
            drawNeonArc(
                color = color,
                progress = animatedProgress,
                strokeWidth = strokeWidth.toPx() * 1.5f,
                alpha = 0.6f
            )
        }
        
        Canvas(
            modifier = Modifier.size(size)
        ) {
            drawNeonArc(
                color = color,
                progress = animatedProgress,
                strokeWidth = strokeWidth.toPx(),
                alpha = 1f
            )
        }
        
        Text(
            text = "${(animatedProgress * 100).toInt()}%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color.copy(alpha = 0.9f)
        )
    }
}

private fun DrawScope.drawNeonArc(
    color: Color,
    progress: Float,
    strokeWidth: Float,
    alpha: Float
) {
    val sweepAngle = 360f * progress
    val startAngle = -90f
    
    // Background arc
    drawArc(
        color = color.copy(alpha = 0.2f * alpha),
        startAngle = startAngle,
        sweepAngle = 360f,
        useCenter = false,
        style = Stroke(width = strokeWidth)
    )
    
    // Progress arc with gradient
    val brush = Brush.sweepGradient(
        colors = listOf(
            color.copy(alpha = 0.3f * alpha),
            color.copy(alpha = 1f * alpha),
            color.copy(alpha = 0.8f * alpha)
        ),
        center = center
    )
    
    drawArc(
        brush = brush,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round
        )
    )
}

@Composable
fun FingerHealthArcDisplay(
    fingerScores: List<FingerScore>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main rainbow arc with circular indicators
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            fingerScores.forEachIndexed { index, fingerScore ->
                // Create proper rainbow arc from left to right with better spacing
                val angle = when(index) {
                    0 -> 160f  // Thumb (far left)
                    1 -> 130f  // Index
                    2 -> 90f   // Middle (top center)
                    3 -> 50f   // Ring
                    4 -> 20f   // Pinky (far right)
                    else -> 90f
                }
                
                val radius = 140.dp // Increased radius for better spacing
                val angleRad = Math.toRadians(angle.toDouble())
                val x = (radius.value * cos(angleRad)).dp
                val y = -(radius.value * sin(angleRad)).dp // Negative for upward arc
                
                Box(
                    modifier = Modifier
                        .offset(x = x, y = y)
                        .size(90.dp), // Reduced size to prevent overlap
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        NeonCircularProgressIndicator(
                            progress = fingerScore.score / 100f,
                            color = fingerScore.color,
                            size = 65.dp,
                            strokeWidth = 5.dp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = fingerScore.name,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private fun parseFingerScores(resultData: String): List<FingerScore> {
    val neonColors = listOf(
        Color(0xFFFF006E), // Neon Pink (Thumb)
        Color(0xFFFF8500), // Neon Orange (Index)
        Color(0xFFFFFF00), // Neon Yellow (Middle)
        Color(0xFF00FF41), // Neon Green (Ring)
        Color(0xFF0099FF)  // Neon Blue (Pinky)
    )
    
    val fingerNames = listOf("Thumb", "Index", "Middle", "Ring", "Pinky")
    
    return try {
        // Parse data - expecting format like "thumb:22.8\nindex:83.7\nmiddle:100\nring:100\npinky:100"
        val scoreMap = mutableMapOf<String, Float>()
        
        // Split by both newlines and commas to handle different formats
        val entries = resultData.split("\n", ",")
        
        entries.forEach { entry ->
            val parts = entry.trim().split(":")
            if (parts.size == 2) {
                val fingerKey = parts[0].trim().lowercase()
                val score = parts[1].trim().toFloatOrNull()
                if (score != null) {
                    scoreMap[fingerKey] = score
                }
            }
        }
        
        // Map to standard finger names and get scores
        val scores = listOf(
            scoreMap["thumb"] ?: 0f,
            scoreMap["index"] ?: 0f,
            scoreMap["middle"] ?: 0f,
            scoreMap["ring"] ?: 0f,
            scoreMap["pinky"] ?: 0f
        )
        
        if (scores.any { it > 0 }) {
            fingerNames.zip(scores.zip(neonColors)) { name, (score, color) ->
                FingerScore(name, score, color)
            }
        } else {
            // Fallback with sample data for testing
            val sampleScores = listOf(85f, 92f, 78f, 88f, 90f)
            fingerNames.zip(sampleScores.zip(neonColors)) { name, (score, color) ->
                FingerScore(name, score, color)
            }
        }
    } catch (e: Exception) {
        // Fallback with sample data for testing
        val sampleScores = listOf(85f, 92f, 78f, 88f, 90f)
        fingerNames.zip(sampleScores.zip(neonColors)) { name, (score, color) ->
            FingerScore(name, score, color)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    resultData: String, // The raw string result from the Jetson Nano
    onBackToMain: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSignOut: () -> Unit
) {
    GloveAppTheme(darkTheme = isDarkTheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Session Result", color = MaterialTheme.colorScheme.onPrimaryContainer) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    actions = {
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle theme",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(onClick = onSignOut) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Sign out",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackToMain) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Finger Health Analysis",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Overall Health Scores",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
                
                // Parse and display finger scores
                val fingerScores = remember(resultData) { parseFingerScores(resultData) }
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FingerHealthArcDisplay(
                            fingerScores = fingerScores,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(Modifier.height(24.dp))
                        
                        // Overall health summary
                        val averageScore = fingerScores.map { it.score }.average()
                        val healthStatus = when {
                            averageScore >= 90 -> "Excellent"
                            averageScore >= 80 -> "Good"
                            averageScore >= 70 -> "Fair"
                            averageScore >= 60 -> "Poor"
                            else -> "Critical"
                        }
                        
                        val statusColor = when {
                            averageScore >= 90 -> Color(0xFF00FF41) // Neon Green
                            averageScore >= 80 -> Color(0xFFFFFF00) // Neon Yellow
                            averageScore >= 70 -> Color(0xFFFF8500) // Neon Orange
                            else -> Color(0xFFFF006E) // Neon Pink/Red
                        }
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = statusColor.copy(alpha = 0.1f)
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Overall Health Status",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = healthStatus,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                                Text(
                                    text = "${averageScore.toInt()}% Average",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onBackToMain,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to Main Screen")
                }
            }
        }
    }
}
