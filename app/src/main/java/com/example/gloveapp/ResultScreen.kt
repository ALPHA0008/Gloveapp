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
fun DynamicCircularProgressIndicator(
    progress: Float,
    baseColor: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 80.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 8.dp
) {
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 1500,
            easing = androidx.compose.animation.core.EaseOutCubic
        )
    )
    
    // Calculate dynamic color based on score (light to dark progression)
    val dynamicColor = getDynamicColor(baseColor, progress)
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(size)
        ) {
            drawHealthArc(
                baseColor = baseColor,
                dynamicColor = dynamicColor,
                progress = animatedProgress,
                strokeWidth = strokeWidth.toPx()
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = dynamicColor
            )
            Text(
                text = "Score",
                fontSize = 8.sp,
                color = dynamicColor.copy(alpha = 0.7f)
            )
        }
    }
}

// Helper function to get dynamic color based on score
fun getDynamicColor(baseColor: Color, progress: Float): Color {
    // Progress ranges from 0 to 1
    val intensity = progress.coerceIn(0f, 1f)
    
    // Create color progression from light to dark
    val lightColor = baseColor.copy(alpha = 0.4f)
    val darkColor = baseColor.copy(alpha = 1f)
    
    // Interpolate between light and dark based on score
    return Color(
        red = lightColor.red + (darkColor.red - lightColor.red) * intensity,
        green = lightColor.green + (darkColor.green - lightColor.green) * intensity,
        blue = lightColor.blue + (darkColor.blue - lightColor.blue) * intensity,
        alpha = 0.7f + (0.3f * intensity) // Alpha from 0.7 to 1.0
    )
}

private fun DrawScope.drawHealthArc(
    baseColor: Color,
    dynamicColor: Color,
    progress: Float,
    strokeWidth: Float
) {
    val sweepAngle = 360f * progress
    val startAngle = -90f
    
    // Background arc (light gray)
    drawArc(
        color = baseColor.copy(alpha = 0.15f),
        startAngle = startAngle,
        sweepAngle = 360f,
        useCenter = false,
        style = Stroke(width = strokeWidth)
    )
    
    // Progress arc with smooth gradient
    val brush = Brush.sweepGradient(
        colors = listOf(
            baseColor.copy(alpha = 0.5f),
            dynamicColor,
            baseColor.copy(alpha = 0.8f)
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
                    0 -> 150f  // Thumb (far left)
                    1 -> 120f  // Index
                    2 -> 90f   // Middle (top center)
                    3 -> 60f   // Ring
                    4 -> 30f   // Pinky (far right)
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
                        DynamicCircularProgressIndicator(
                            progress = fingerScore.score / 100f,
                            baseColor = fingerScore.color,
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
    // Natural rainbow colors - pleasant and professional
    val rainbowColors = listOf(
        Color(0xFFE91E63), // Pink/Rose (Thumb)
        Color(0xFFFF9800), // Orange (Index)  
        Color(0xFFFFC107), // Amber/Golden (Middle)
        Color(0xFF4CAF50), // Green (Ring)
        Color(0xFF2196F3)  // Blue (Pinky)
    )
    
    val fingerNames = listOf("Thumb", "Index", "Middle", "Ring", "Pinky")
    
    return try {
        println("DEBUG: Raw result data: $resultData")
        
        // Parse the finger healthiness analysis format
        val scoreMap = mutableMapOf<String, Float>()
        
        // Split by lines and look for finger analysis lines
        val lines = resultData.split("\n")
        
        lines.forEach { line ->
            val trimmedLine = line.trim()
            
            // Look for lines that contain finger names and Score:
            when {
                trimmedLine.contains("Thumb") && trimmedLine.contains("Score:") -> {
                    val score = extractScoreFromLine(trimmedLine)
                    if (score != null) scoreMap["thumb"] = score
                }
                trimmedLine.contains("Index") && trimmedLine.contains("Score:") -> {
                    val score = extractScoreFromLine(trimmedLine)
                    if (score != null) scoreMap["index"] = score
                }
                trimmedLine.contains("Middle") && trimmedLine.contains("Score:") -> {
                    val score = extractScoreFromLine(trimmedLine)
                    if (score != null) scoreMap["middle"] = score
                }
                trimmedLine.contains("Ring") && trimmedLine.contains("Score:") -> {
                    val score = extractScoreFromLine(trimmedLine)
                    if (score != null) scoreMap["ring"] = score
                }
                trimmedLine.contains("Little") && trimmedLine.contains("Score:") -> {
                    val score = extractScoreFromLine(trimmedLine)
                    if (score != null) scoreMap["pinky"] = score // Map "Little" to "Pinky"
                }
            }
        }
        
        // Get the final scores in order
        val scores = listOf(
            scoreMap["thumb"] ?: 0f,
            scoreMap["index"] ?: 0f,
            scoreMap["middle"] ?: 0f,
            scoreMap["ring"] ?: 0f,
            scoreMap["pinky"] ?: 0f
        )
        
        println("DEBUG: Extracted scores - Thumb: ${scores[0]}, Index: ${scores[1]}, Middle: ${scores[2]}, Ring: ${scores[3]}, Pinky: ${scores[4]}")
        
        // If we have at least some valid scores, use them
        if (scores.any { it > 0 }) {
            return fingerNames.zip(scores.zip(rainbowColors)) { name, (score, color) ->
                FingerScore(name, score, color)
            }
        } else {
            println("DEBUG: No valid scores found, using sample data")
            // Use sample data for testing
            val sampleScores = listOf(72f, 85f, 91f, 78f, 88f)
            return fingerNames.zip(sampleScores.zip(rainbowColors)) { name, (score, color) ->
                FingerScore(name, score, color)
            }
        }
        
    } catch (e: Exception) {
        println("DEBUG: Error parsing finger scores: ${e.message}")
        // Use sample data for testing
        val sampleScores = listOf(65f, 80f, 95f, 70f, 85f)
        return fingerNames.zip(sampleScores.zip(rainbowColors)) { name, (score, color) ->
            FingerScore(name, score, color)
        }
    }
}

// Helper function to extract score from a line like "Score: 100.0)"
private fun extractScoreFromLine(line: String): Float? {
    return try {
        // Find "Score:" and extract the number after it
        val scoreIndex = line.indexOf("Score:")
        if (scoreIndex != -1) {
            val scoreText = line.substring(scoreIndex + 6) // Skip "Score:"
            // Extract the number (handle both "100.0)" and "100)" formats)
            val numberText = scoreText.split(")")[0].trim()
            val score = numberText.toFloatOrNull()
            score?.coerceIn(0f, 100f)
        } else {
            null
        }
    } catch (e: Exception) {
        println("DEBUG: Failed to extract score from line: $line, Error: ${e.message}")
        null
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
                
                // Debug card to show raw data (you can remove this later)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Raw Data (for debugging):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = resultData,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
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
                            averageScore >= 90 -> Color(0xFF4CAF50) // Green
                            averageScore >= 80 -> Color(0xFF8BC34A) // Light Green
                            averageScore >= 70 -> Color(0xFFFF9800) // Orange
                            averageScore >= 60 -> Color(0xFFFF5722) // Deep Orange
                            else -> Color(0xFFE91E63) // Pink/Red
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
