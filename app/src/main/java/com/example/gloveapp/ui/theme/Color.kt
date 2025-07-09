package com.example.gloveapp.ui.theme

import androidx.compose.ui.graphics.Color

// Base Palette (Your existing definitions)
val TechBlue = Color(0xFF2962FF)        // Vivid Blue
val CyberPurple = Color(0xFFAA00FF)     // Bright Purple
val NeonGreen = Color(0xFF00C853)       // Vibrant Green
val SignalOrange = Color(0xFFFF6F00)    // Strong Orange

// --- Light Theme Colors ---
val LightPrimary = TechBlue
val LightOnPrimary = Color.White        // Text/icons on TechBlue

val LightPrimaryContainer = Color(0xFFD8E2FF) // A lighter variant of TechBlue for containers
val LightOnPrimaryContainer = Color(0xFF001A40) // Dark text for on LightPrimaryContainer

val LightSecondary = CyberPurple
val LightOnSecondary = Color.White      // Text/icons on CyberPurple

val LightSecondaryContainer = Color(0xFFF5D8FF) // A lighter variant of CyberPurple for containers
val LightOnSecondaryContainer = Color(0xFF310048) // Dark text for on LightSecondaryContainer

// Optional: Tertiary colors if you use them in your M3 theme
// val LightTertiary = Color(0xFFYOUR_TERTIARY_COLOR_LIGHT)
// val LightOnTertiary = Color(YOUR_ON_TERTIARY_COLOR_LIGHT)
// val LightTertiaryContainer = Color(YOUR_TERTIARY_CONTAINER_COLOR_LIGHT)
// val LightOnTertiaryContainer = Color(YOUR_ON_TERTIARY_CONTAINER_COLOR_LIGHT)

val LightError = Color(0xFFB00020)      // Standard Material Red for errors
val LightOnError = Color.White          // White text/icons on Error Red
val LightErrorContainer = Color(0xFFFFDAD6) // Light red for error container backgrounds
val LightOnErrorContainer = Color(0xFF410002) // Dark red text for on LightErrorContainer

val LightBackground = Color(0xFFF5F5F5) // Your Light Gray Background
val LightOnBackground = Color(0xFF1A1C1E) // Dark text/icons on Light Gray Background

val LightSurface = Color.White          // Your White Surface
val LightOnSurface = Color(0xFF1A1C1E)   // Dark text/icons on White Surface

// For Material 3, these are also important:
val LightSurfaceVariant = Color(0xFFE0E2EC) // Slightly different shade from Surface
val LightOnSurfaceVariant = Color(0xFF44474F) // Text/icons on SurfaceVariant
val LightOutline = Color(0xFF74777F)        // For borders, dividers

// --- Dark Theme Colors ---
val DarkPrimary = NeonGreen             // Use NeonGreen as Primary in Dark Theme
val DarkOnPrimary = Color.Black         // Text/icons on NeonGreen (NeonGreen is light enough for black text)

val DarkPrimaryContainer = Color(0xFF00522A) // A darker variant of NeonGreen for containers
val DarkOnPrimaryContainer = Color(0xFF78F89A) // Light green text for on DarkPrimaryContainer

val DarkSecondary = SignalOrange        // Use SignalOrange as Secondary in Dark Theme
val DarkOnSecondary = Color.Black       // Text/icons on SignalOrange (SignalOrange is light enough for black text)

val DarkSecondaryContainer = Color(0xFF8C3F00) // A darker variant of SignalOrange for containers
val DarkOnSecondaryContainer = Color(0xFFFFDBC8) // Light orange text for on DarkSecondaryContainer

// Optional: Tertiary colors if you use them in your M3 theme
// val DarkTertiary = Color(YOUR_TERTIARY_COLOR_DARK)
// val DarkOnTertiary = Color(YOUR_ON_TERTIARY_COLOR_DARK)
// val DarkTertiaryContainer = Color(YOUR_TERTIARY_CONTAINER_COLOR_DARK)
// val DarkOnTertiaryContainer = Color(YOUR_ON_TERTIARY_CONTAINER_COLOR_DARK)

val DarkError = Color(0xFFCF6679)      // Standard Material Red for errors (dark theme variant)
val DarkOnError = Color.Black           // Black text/icons on Dark Error Red
val DarkErrorContainer = Color(0xFF93000A) // Dark red for error container backgrounds
val DarkOnErrorContainer = Color(0xFFFFDAD6) // Light red text for on DarkErrorContainer

val DarkBackground = Color(0xFF121212)  // Your Very Dark Background
val DarkOnBackground = Color(0xFFE2E2E6) // Light text/icons on Dark Background

val DarkSurface = Color(0xFF1E1E1E)    // Your Slightly lighter Dark Surface
val DarkOnSurface = Color(0xFFE2E2E6)   // Light text/icons on Dark Surface

// For Material 3, these are also important:
val DarkSurfaceVariant = Color(0xFF44474F) // Slightly different shade from Surface in dark theme
val DarkOnSurfaceVariant = Color(0xFFC4C6CF) // Text/icons on DarkSurfaceVariant
val DarkOutline = Color(0xFF8E9099)        // For borders, dividers in dark theme






