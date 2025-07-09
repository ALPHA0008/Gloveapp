package com.example.gloveapp.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gloveapp.AppScreen
import com.example.gloveapp.GetReadyScreen
import com.example.gloveapp.ScanScreen
import com.example.gloveapp.ScannedDeviceInfo
import com.example.gloveapp.TimerDisplay
import com.example.gloveapp.auth.AuthViewModel
import com.example.gloveapp.ui.theme.GloveAppTheme
import com.github.mikephil.charting.data.Entry
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.ui.viewinterop.AndroidView
import android.widget.VideoView
import android.net.Uri
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun PatientInterface(
    onToggleTheme: () -> Unit,
    isDarkTheme: Boolean,
    fsrEntries: List<List<Entry>>,
    flexEntries: List<List<Entry>>,
    authViewModel: AuthViewModel,
    onScanClick: () -> Unit,
    isScanning: Boolean,
    scannedDevices: List<ScannedDeviceInfo>,
    rssiMap: Map<String, Int>,
    connectToDevice: (ScannedDeviceInfo) -> Unit,
    stopScan: () -> Unit,
    writeDataControl: (Boolean) -> Unit,
    disconnectGatt: () -> Unit,
    clearData: () -> Unit,
    isDataStreamingEnabled: Boolean,
    isDeviceReadyForControl: Boolean,
    isTimerRunning: Boolean,
    timerStartTimeMillis: Long,
    timerAccumulatedMillis: Long,
    onSignOut: () -> Unit,
    onUserManualClick: () -> Unit,
    onStartSessionCountdown: () -> Unit,
    onViewResult: () -> Unit,
    onStopStreaming: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(AppScreen.ADMIN_SCAN) }
    var scanInitiatedState by remember { mutableStateOf(false) }
    var isLoadingPermissions by remember { mutableStateOf(false) }
    val context = LocalContext.current

    GloveAppTheme(darkTheme = isDarkTheme) {
        Crossfade(
            targetState = currentScreen,
            animationSpec = tween(durationMillis = 500)
        ) { targetScreen ->
            when (targetScreen) {
                AppScreen.ADMIN_SCAN -> ScanScreen(
                    devices = scannedDevices,
                    rssiValues = rssiMap,
                    isScanning = isScanning,
                    onScanClick = {
                        scanInitiatedState = true
                        isLoadingPermissions = true
                        onScanClick()
                    },
                    onDeviceClick = { device ->
                        connectToDevice(device)
                        stopScan()
                        currentScreen = AppScreen.ADMIN_GET_READY
                    },
                    scanInitiated = scanInitiatedState,
                    isLoadingPermissions = isLoadingPermissions,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onSignOut = onSignOut,
                    onUserManualClick = {
                        Toast.makeText(context, "User Manual not available for Patient role yet.", Toast.LENGTH_SHORT).show()
                    }
                )
                AppScreen.ADMIN_GET_READY -> GetReadyScreen(
                    onCountdownComplete = {
                        try {
                            writeDataControl(true)
                            currentScreen = AppScreen.ADMIN_MAIN
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error starting data stream: ${e.message}", Toast.LENGTH_LONG).show()
                            currentScreen = AppScreen.ADMIN_MAIN
                        }
                    },
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onSignOut = onSignOut
                )
                AppScreen.ADMIN_MAIN -> PatientMainScreen(
                    onScanClick = {
                        try {
                            writeDataControl(false)
                            disconnectGatt()
                            clearData()
                            currentScreen = AppScreen.ADMIN_SCAN
                            scanInitiatedState = false
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Error disconnecting: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    fsrEntries = fsrEntries,
                    flexEntries = flexEntries,
                    onStartStreaming = { currentScreen = AppScreen.ADMIN_GET_READY },
                    onStopStreaming = onStopStreaming,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    isDataStreamingEnabled = isDataStreamingEnabled,
                    isDeviceReadyForControl = isDeviceReadyForControl,
                    isTimerRunning = isTimerRunning,
                    timerStartTimeMillis = timerStartTimeMillis,
                    timerAccumulatedMillis = timerAccumulatedMillis,
                    onViewResult = onViewResult,
                    onSignOut = onSignOut
                )
                else -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientMainScreen(
    onScanClick: () -> Unit,
    fsrEntries: List<List<Entry>>,
    flexEntries: List<List<Entry>>,
    onStartStreaming: () -> Unit,
    onStopStreaming: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    isDataStreamingEnabled: Boolean,
    isDeviceReadyForControl: Boolean,
    isTimerRunning: Boolean,
    timerStartTimeMillis: Long,
    timerAccumulatedMillis: Long,
    onViewResult: () -> Unit,
    onSignOut: () -> Unit
) {
    var isGrasp by remember { mutableStateOf(true) }
    var isTimerCompleted by remember { mutableStateOf(false) }

    LaunchedEffect(isTimerRunning, timerStartTimeMillis, timerAccumulatedMillis) {
        if (isTimerRunning) {
            val startTime = timerStartTimeMillis
            val elapsedSinceStart = System.currentTimeMillis() - startTime + timerAccumulatedMillis
            if (elapsedSinceStart >= 120_000L) {
                onStopStreaming()
                isTimerCompleted = true
            } else {
                isTimerCompleted = false
                delay(120_000L - elapsedSinceStart)
                if (isTimerRunning) {
                    onStopStreaming()
                    isTimerCompleted = true
                }
            }
        } else {
            isTimerCompleted = timerAccumulatedMillis >= 120_000L
        }
    }

    LaunchedEffect(isDataStreamingEnabled, timerStartTimeMillis) {
        if (isDataStreamingEnabled && isTimerRunning) {
            while (isDataStreamingEnabled) {
                isGrasp = true
                delay(5000L)
                if (!isDataStreamingEnabled) break
                isGrasp = false
                delay(5000L)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Patient Data", color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = onScanClick) {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = "Disconnect and Scan",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    TimerDisplay(
                        isRunning = isTimerRunning,
                        startTimeMillis = timerStartTimeMillis,
                        accumulatedMillis = timerAccumulatedMillis,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Rounded.NightlightRound else Icons.Rounded.WbSunny,
                            contentDescription = if (isDarkTheme) "Dark Mode" else "Light Mode",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(4.dp))
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { onToggleTheme() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.secondary,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(
                            Icons.Default.Logout,
                            contentDescription = "Sign Out",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { if (isDataStreamingEnabled) onStopStreaming() else onStartStreaming() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = isDeviceReadyForControl,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDataStreamingEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = if (isDataStreamingEnabled) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(
                    imageVector = if (isDataStreamingEnabled) Icons.Default.Stop else Icons.Default.Bluetooth,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isDataStreamingEnabled) "Stop Data" else "Start Data")
            }

            AnimatedVisibility(
                visible = isDataStreamingEnabled,
                enter = fadeIn(tween(500)) + scaleIn(tween(500)),
                exit = fadeOut(tween(300)) + scaleOut(tween(300))
            ) {
                GraspReleaseAnimation(isGrasp = isGrasp)
            }

            AnimatedVisibility(
                visible = isTimerCompleted,
                enter = slideInVertically(tween(500)) { it / 2 } + fadeIn(tween(500)),
                exit = slideOutVertically(tween(300)) { it / 2 } + fadeOut(tween(300))
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Session Complete!",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "2-minute session has finished.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onViewResult,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "View Result",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GraspReleaseAnimation(isGrasp: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Try to get video resources, fallback to images if videos don't exist
    val graspVideoRes = try {
        context.resources.getIdentifier("grasp_animation", "raw", context.packageName)
    } catch (e: Exception) {
        0
    }

    val releaseVideoRes = try {
        context.resources.getIdentifier("release_animation", "raw", context.packageName)
    } catch (e: Exception) {
        0
    }

    val hasVideos = graspVideoRes != 0 && releaseVideoRes != 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (hasVideos) {
                // Video playback
                val videoUri = remember(isGrasp) {
                    val resourceId = if (isGrasp) graspVideoRes else releaseVideoRes
                    Uri.parse("android.resource://${context.packageName}/$resourceId")
                }

                var videoView: VideoView? by remember { mutableStateOf(null) }

                // Handle lifecycle events for video
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_PAUSE -> {
                                videoView?.pause()
                            }

                            Lifecycle.Event.ON_RESUME -> {
                                videoView?.start()
                            }

                            Lifecycle.Event.ON_DESTROY -> {
                                videoView?.stopPlayback()
                            }

                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            videoView = this
                            setVideoURI(videoUri)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                            }
                            setOnErrorListener { _, what, extra ->
                                android.util.Log.e(
                                    "VideoPlayback",
                                    "Video error: what=$what, extra=$extra"
                                )
                                false // Return false to let default error handling occur
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        // Update video URI when isGrasp changes
                        view.setVideoURI(videoUri)
                        view.setOnPreparedListener { mp ->
                            mp.isLooping = true
                            mp.start()
                        }
                    }
                )

                // Video overlay text
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isGrasp) "Grasp" else "Release",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Fallback to icon animation
                Crossfade(
                    targetState = isGrasp,
                    animationSpec = tween(500)
                ) { grasp ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (grasp) Icons.Default.PanTool else Icons.Default.Handyman,
                            contentDescription = null,
                            tint = if (grasp) Color(0xFF34A853) else Color(0xFFEA4335),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (grasp) "Grasp" else "Release",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (grasp) Color(0xFF34A853) else Color(0xFFEA4335),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
