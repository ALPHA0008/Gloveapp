package com.example.gloveapp

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.DefaultValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

data class ScannedDeviceInfo(
    val name: String?,
    val address: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    devices: List<ScannedDeviceInfo>,
    rssiValues: Map<String, Int>,
    isScanning: Boolean,
    onScanClick: () -> Unit,
    onDeviceClick: (ScannedDeviceInfo) -> Unit,
    scanInitiated: Boolean = false,
    isLoadingPermissions: Boolean = false,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSignOut: () -> Unit,
    onUserManualClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard", color = MaterialTheme.colorScheme.onPrimary) },
                actions = {
                    IconButton(onClick = onUserManualClick) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = "User Manual",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode,
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (scanInitiated) Arrangement.Top else Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = tween(500))
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Scan Bluetooth Devices",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Button(
                        onClick = onScanClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            contentColor = if (isScanning) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (isLoadingPermissions) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Checking Permissions...")
                        } else {
                            Icon(
                                imageVector = if (isScanning) Icons.Filled.BluetoothDisabled else Icons.Filled.Bluetooth,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isScanning) "Stop Scanning" else "Start Scan")
                        }
                    }

                    if (isScanning) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Scanning for devices...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = scanInitiated,
                enter = fadeIn(tween(500, delayMillis = 200)),
                exit = fadeOut(tween(200))
            ) {
                Spacer(Modifier.height(24.dp))
            }

            AnimatedVisibility(
                visible = scanInitiated,
                enter = slideInVertically(
                    animationSpec = tween(500, delayMillis = 200),
                    initialOffsetY = { it / 2 }
                ) + fadeIn(tween(500, delayMillis = 200)),
                exit = slideOutVertically(
                    animationSpec = tween(300),
                    targetOffsetY = { it / 2 }
                ) + fadeOut(tween(300))
            ) {
                if (devices.isEmpty() && !isScanning) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Spacer(Modifier.height(48.dp))
                        Text(
                            text = "No Bluetooth devices found.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Ensure devices are ON, discoverable, and within range.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(devices) { device ->
                            DeviceCard(
                                device = device,
                                rssi = rssiValues[device.address],
                                onClick = { onDeviceClick(device) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: ScannedDeviceInfo,
    rssi: Int?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .clip(MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier
                .padding(16.dp)
        ) {
            Text(
                text = device.name ?: "Unknown Device",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "MAC: ${device.address}",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            if (rssi != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "RSSI: $rssi dBm",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .width(50.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFFEA4335),
                                        Color(0xFFFBBC04),
                                        Color(0xFF34A853)
                                    )
                                )
                            )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text("Connect")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GetReadyScreen(
    onCountdownComplete: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSignOut: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Get Ready", color = MaterialTheme.colorScheme.onPrimary) },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode,
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
        var countdown by remember { mutableStateOf(5) }

        LaunchedEffect(Unit) {
            Log.d("BLE_DEBUG_TIMER", "GetReadyScreen: Countdown started at 5 seconds")
            try {
                while (countdown > 0) {
                    delay(1000L)
                    countdown--
                    Log.d("BLE_DEBUG_TIMER", "GetReadyScreen: Countdown at $countdown seconds")
                }
                Log.d("BLE_DEBUG_TIMER", "GetReadyScreen: Countdown complete, calling onCountdownComplete")
                onCountdownComplete()
            } catch (e: Exception) {
                Log.e("BLE_DEBUG_TIMER", "Error in countdown: ${e.message}")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Get Ready To Grasp in ${countdown}sec",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onScanClick: () -> Unit,
    fsrEntries: List<List<Entry>>,
    flexEntries: List<List<Entry>>,
    imuBioEntries: List<List<Entry>>,
    dataUpdateTrigger: Int,
    isDataStreamingEnabled: Boolean,
    isDeviceReadyForControl: Boolean,
    onStartStreaming: () -> Unit,
    onStopStreaming: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
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
                Log.d("BLE_DEBUG_TIMER", "Timer reached 2 minutes, stopping streaming")
                onStopStreaming()
                isTimerCompleted = true
            } else {
                isTimerCompleted = false
                delay(120_000L - elapsedSinceStart)
                if (isTimerRunning) {
                    Log.d("BLE_DEBUG_TIMER", "Timer reached 2 minutes after delay, stopping streaming")
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
                Log.d("BLE_DEBUG_TIMER", "Grasp state: Grasp")
                delay(5000L)
                if (!isDataStreamingEnabled) break
                isGrasp = false
                Log.d("BLE_DEBUG_TIMER", "Grasp state: Release")
                delay(5000L)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Data", color = MaterialTheme.colorScheme.onPrimary) },
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
                            imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
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
                .verticalScroll(rememberScrollState())
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

            GraspReleaseIndicator(isGrasp = isGrasp)

            val chartColors = listOf(
                "#FFA500".toColorInt(),
                "#4285F4".toColorInt(),
                "#34A853".toColorInt(),
                "#800080".toColorInt(),
                "#FFD700".toColorInt(),
                "#DB4437".toColorInt(),
                "#607D8B".toColorInt(),
                "#795548".toColorInt(),
                "#9C27B0".toColorInt(),
                "#00BCD4".toColorInt(),
                "#FF9800".toColorInt(),
                "#E91E63".toColorInt()
            )
            val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

            ChartCard(
                title = "Flex Sensors",
                entriesList = flexEntries,
                labels = listOf("Thumb", "Index", "Middle", "Ring", "Pinky"),
                colors = chartColors.subList(0, 5),
                textColor = textColor,
                dataUpdateTrigger = dataUpdateTrigger
            )

            ChartCard(
                title = "Force Sensors",
                entriesList = fsrEntries,
                labels = listOf("Thumb", "Index", "Middle", "Ring", "Pinky"),
                colors = chartColors.subList(0, 5),
                textColor = textColor,
                dataUpdateTrigger = dataUpdateTrigger
            )

            val imuEntries = if (imuBioEntries.size > 1) imuBioEntries.subList(1, imuBioEntries.size) else emptyList()
            ChartCard(
                title = "IMU Sensors",
                entriesList = imuEntries,
                labels = listOf("AccelX", "AccelY", "AccelZ", "GyroX", "GyroY", "GyroZ"),
                colors = chartColors.subList(6, 12),
                textColor = textColor,
                dataUpdateTrigger = dataUpdateTrigger
            )

            val bioAmpEntries = if (imuBioEntries.isNotEmpty()) listOf(imuBioEntries[0]) else emptyList()
            ChartCard(
                title = "BioAmp Sensor",
                entriesList = bioAmpEntries,
                labels = listOf("BioAmp"),
                colors = listOf(chartColors[5]),
                textColor = textColor,
                dataUpdateTrigger = dataUpdateTrigger
            )

            AnimatedVisibility(
                visible = isTimerCompleted,
                enter = slideInVertically(
                    animationSpec = tween(500),
                    initialOffsetY = { it / 2 }
                ) + fadeIn(tween(500)),
                exit = slideOutVertically(
                    animationSpec = tween(300),
                    targetOffsetY = { it / 2 }
                ) + fadeOut(tween(300))
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
                            text = "Data Collection Complete!",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "2-minute data collection has finished.",
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
fun GraspReleaseIndicator(isGrasp: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Grasp-Release State",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isGrasp) "Grasp" else "Release",
                style = MaterialTheme.typography.titleMedium,
                color = if (isGrasp) Color(0xFF34A853) else Color(0xFFEA4335),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TimerDisplay(
    isRunning: Boolean,
    startTimeMillis: Long,
    accumulatedMillis: Long,
    color: Color
) {
    var displayTime by remember { mutableStateOf(accumulatedMillis) }

    LaunchedEffect(isRunning, startTimeMillis, accumulatedMillis) {
        if (isRunning) {
            val initialSystemTime = System.currentTimeMillis()
            val initialDisplayTime = accumulatedMillis + (initialSystemTime - startTimeMillis)
            while (isRunning) {
                val elapsedSinceStart = System.currentTimeMillis() - initialSystemTime
                displayTime = initialDisplayTime + elapsedSinceStart
                Log.d("BLE_DEBUG_TIMER", "TimerDisplay: Running, time=$displayTime ms")
                delay(100)
            }
            displayTime = accumulatedMillis + (System.currentTimeMillis() - startTimeMillis)
            Log.d("BLE_DEBUG_TIMER", "TimerDisplay: Stopped, final time=$displayTime ms")
        } else {
            displayTime = accumulatedMillis
            Log.d("BLE_DEBUG_TIMER", "TimerDisplay: Not running, time=$displayTime ms")
        }
    }

    val minutes = TimeUnit.MILLISECONDS.toMinutes(displayTime) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(displayTime) % 60
    val millis = (displayTime % 1000) / 10

    val timeString = String.format("%02d:%02d:%02d", minutes, seconds, millis)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Timer,
            contentDescription = "Timer",
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = timeString,
            color = color,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun ChartCard(
    title: String,
    entriesList: List<List<Entry>>,
    labels: List<String>,
    colors: List<Int>,
    textColor: Int,
    dataUpdateTrigger: Int
) {
    Log.d("BLE_DEBUG_TIMER", "ChartCard recomposed: $title, trigger=$dataUpdateTrigger")
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            AndroidView(
                factory = { context ->
                    LineChart(context).apply {
                        description.isEnabled = false
                        setTouchEnabled(true)
                        isDragEnabled = true
                        setScaleEnabled(true)
                        setDrawGridBackground(false)
                        setPinchZoom(true)
                        setBackgroundColor(Color.Transparent.toArgb())

                        legend.apply {
                            form = Legend.LegendForm.LINE
                            setTextColor(textColor)
                            verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                            horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
                            orientation = Legend.LegendOrientation.HORIZONTAL
                            setDrawInside(false)
                            isWordWrapEnabled = true
                        }

                        xAxis.apply {
                            setTextColor(textColor)
                            position = XAxis.XAxisPosition.BOTTOM
                            setDrawGridLines(false)
                            axisLineColor = textColor
                            granularity = 1f
                        }

                        axisLeft.apply {
                            setTextColor(textColor)
                            setDrawGridLines(true)
                            axisLineColor = textColor
                            gridColor = Color(textColor).copy(alpha = 0.2f).toArgb()
                        }
                        axisRight.isEnabled = false
                    }
                },
                update = { chart ->
                    try {
                        updateChartDataOptimized(chart, entriesList, labels, colors, textColor)
                        Log.d("BLE_DEBUG_TIMER", "Chart updated: $title")
                    } catch (e: Exception) {
                        Log.e("BLE_DEBUG_TIMER", "Error updating chart $title: ${e.message}")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
        }
    }
}

private fun updateChartDataOptimized(
    chart: LineChart,
    entriesList: List<List<Entry>>,
    labels: List<String>,
    colors: List<Int>,
    textColor: Int
) {
    if (entriesList.isEmpty() || entriesList.all { it.isEmpty() }) {
        chart.clear()
        chart.invalidate()
        return
    }

    val lineData = chart.data

    if (lineData != null && lineData.dataSetCount == entriesList.size) {
        var dataSetChanged = false
        for (i in entriesList.indices) {
            val entries = entriesList[i]
            val dataSet = lineData.getDataSetByIndex(i) as? LineDataSet

            if (dataSet != null) {
                if (dataSet.values !== entries) {
                    dataSet.values = entries
                    dataSetChanged = true
                }
            } else {
                Log.e("BLE_DEBUG_TIMER", "DataSet at index $i is null or wrong type!")
                chart.clear()
                chart.invalidate()
                return
            }
        }

        if (dataSetChanged) {
            lineData.notifyDataChanged()
            chart.notifyDataSetChanged()
            chart.invalidate()
        }
    } else {
        val dataSets = ArrayList<ILineDataSet>()
        for (i in entriesList.indices) {
            val entries = entriesList[i]
            val label = labels.getOrElse(i) { "Data ${i + 1}" }
            val color = colors.getOrElse(i) { Color.Gray.toArgb() }

            val dataSet = LineDataSet(entries, label).apply {
                this.color = color
                this.valueTextColor = textColor
                lineWidth = 1.5f
                setDrawValues(false)
                setDrawCircles(false)
                mode = LineDataSet.Mode.LINEAR
                valueFormatter = DefaultValueFormatter(1)
                highLightColor = Color.White.toArgb()
            }
            dataSets.add(dataSet)
        }

        if (dataSets.isNotEmpty()) {
            chart.data = LineData(dataSets)
            chart.invalidate()
        } else {
            chart.clear()
            chart.invalidate()
        }
    }
}


@Composable
fun GloveAppTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        MaterialTheme.colorScheme.copy(
            primary = Color(0xFFBB86FC),
            onPrimary = Color.Black,
            background = Color(0xFF121212),
            onBackground = Color.White,
            surface = Color(0xFF1E1E1E),
            onSurface = Color.White,
            secondary = Color(0xFF03DAC6),
            onSecondary = Color.Black,
            error = Color(0xFFCF6679),
            onError = Color.Black
        )
    } else {
        MaterialTheme.colorScheme.copy(
            primary = Color(0xFF6200EE),
            onPrimary = Color.White,
            background = Color.White,
            onBackground = Color.Black,
            surface = Color(0xFFF5F5F5),
            onSurface = Color.Black,
            secondary = Color(0xFF03DAC6),
            onSecondary = Color.Black,
            error = Color(0xFFB00020),
            onError = Color.White
        )
    }
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}
