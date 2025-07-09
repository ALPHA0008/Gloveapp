package com.example.gloveapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.ContextCompat
import com.example.gloveapp.ui.theme.GloveAppTheme
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
// IMPORTANT: Ensure your AppScreen.kt defines ADMIN_RESULT and PATIENT_RESULT
import com.example.gloveapp.AppScreen.* // Import all AppScreen enum values
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.Collections
import com.github.mikephil.charting.data.Entry
import com.example.gloveapp.auth.AuthViewModel
import com.example.gloveapp.auth.SignInScreen
import com.example.gloveapp.auth.SignUpScreen
import com.example.gloveapp.auth.UserRole
import com.example.gloveapp.ui.PatientInterface
import com.google.firebase.messaging.FirebaseMessaging
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier



// ADDED / CORRECTED IMPORTS FOR CSV AND NETWORK COMMUNICATION
import com.example.gloveapp.ui.ResultScreen // Import the new ResultScreen
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.text.SimpleDateFormat
import java.util.concurrent.CountDownLatch // For the fetchResultFromFirebase function
import kotlinx.coroutines.launch // Ensure this is imported for coroutines
import kotlinx.coroutines.delay // Ensure this is imported for coroutines
import kotlinx.coroutines.tasks.await // Ensure this is imported for await() calls on Firebase Tasks
import kotlinx.coroutines.withContext // Ensure this is imported for withContext
import android.net.Uri // For .toUri()
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.FirebaseStorage


val Context.dataStore by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "BLE_DEBUG_TIMER"
        private val SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        private val CHARACTERISTIC_NOTIFY_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        private val CHARACTERISTIC_WRITE_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        private val CLIENT_CHAR_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val IS_DARK_MODE_KEY = booleanPreferencesKey("is_dark_mode")
        private const val MAX_DATA_POINTS = 1000 // Reduced from 5000 to prevent memory issues
        private const val CHART_UPDATE_INTERVAL = 100L
        private const val DATA_STREAM_DURATION_MS = 120_000L // 2 minutes
        private const val MAX_BUFFER_SIZE = 500 // Prevent buffer from growing too large

        // Fixed Admin Credentials (WARNING: Hardcoding credentials is a security risk in production)
        const val ADMIN_EMAIL = "admin@gloveapp.com"
        const val ADMIN_PASSWORD = "adminpassword"


        private var currentSessionDocRef: String? = null // To store the Firestore document ID for the current session
        private val FIREBASE_PROCESSING_ENABLED = true // New flag for Firebase flow


    }


    // Bluetooth related fields
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var connectedGatt: BluetoothGatt? = null
    private var controlCharacteristic: BluetoothGattCharacteristic? = null
    private var pNotifyCharacteristic: BluetoothGattCharacteristic? = null
    private val buffer = StringBuilder()
    private val coroutineScope = CoroutineScope(Dispatchers.Default + Job())


    // Chart data management - NOW SYNCHRONIZED
    private val _fsrEntriesInternal = Collections.synchronizedList(List(5) { mutableListOf<Entry>() })
    private val _flexEntriesInternal = Collections.synchronizedList(List(5) { mutableListOf<Entry>() })
    private val _imuBioEntriesInternal = Collections.synchronizedList(List(7) { mutableListOf<Entry>() })

    // These should be mutableStateOf<List<List<Entry>>> to ensure recomposition when the inner lists are replaced
    val fsrEntries: MutableState<List<List<Entry>>> = mutableStateOf(List(5) { emptyList() })
    val flexEntries: MutableState<List<List<Entry>>> = mutableStateOf(List(5) { emptyList() })
    val imuBioEntries: MutableState<List<List<Entry>>> = mutableStateOf(List(7) { emptyList() })

    private var dataUpdateTrigger by mutableStateOf(0)
    private var xIndex = 0f

    // Theme state
    private var isDarkThemeEnabled by mutableStateOf(false)

    // Data buffering for charts
    private val dataBuffer = Collections.synchronizedList(mutableListOf<List<Float>>())
    private var chartUpdateJob: Job? = null

    // Moving average filters
    private val windowSize = 5
    // These internal lists also need to be synchronized if accessed from multiple threads
    private val fsrMovingAverages = Collections.synchronizedList(List(5) { mutableListOf<Float>() })
    private val flexMovingAverages = Collections.synchronizedList(List(5) { mutableListOf<Float>() })
    private val imuBioMovingAverages = Collections.synchronizedList(List(7) { mutableListOf<Float>() })

    // Scan screen states
    private var scanning by mutableStateOf(false)
    private val scannedDevices = mutableStateListOf<ScannedDeviceInfo>()
    private val rssiMap = mutableStateMapOf<String, Int>()

    private var onScanAttemptResult: ((Boolean) -> Unit)? = null

    // Streaming and control states
    private var isDataStreamingEnabled by mutableStateOf(false)
    private var isDeviceReadyForControl by mutableStateOf(false)

    // Timer states
    private var isTimerRunning by mutableStateOf(false)
    private var timerStartTimeMillis by mutableStateOf(0L)
    private var timerAccumulatedMillis by mutableStateOf(0L)
    // ADDED: Job for the 2-minute session duration
    private var sessionDurationJob: Job? = null

    // ADDED: Algorithm Result State
    var resultText by mutableStateOf("Processing results...")
    var isProcessingResult by mutableStateOf(false) // New state for loading indicator


    // Auth ViewModel
    private val authViewModel = AuthViewModel()
    // Local state for fixed admin login
    private var isAdminFixedLoggedIn by mutableStateOf(false)
    // Patient specific navigation state
    private var currentPatientScreen by mutableStateOf<AppScreen>(PATIENT_DASHBOARD)
    // Admin specific navigation state
    private var currentAdminScreen by mutableStateOf<AppScreen>(ADMIN_SCAN)


    // ActivityResultLauncher for permissions
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allPermissionsGranted = permissions.entries.all { it.value }
            Log.d(TAG, "Permissions requested. All granted: $allPermissionsGranted")
            if (allPermissionsGranted) {
                checkBluetoothAndLocationStatus { success ->
                    onScanAttemptResult?.invoke(success)
                }
            } else {
                Toast.makeText(this, "Required permissions denied.", Toast.LENGTH_LONG).show()
                onScanAttemptResult?.invoke(false)
            }
        }

    // ActivityResultLauncher for enabling Bluetooth
    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(TAG, "Bluetooth enable result: ${result.resultCode == RESULT_OK}")
            if (result.resultCode == RESULT_OK) {
                checkLocationServiceStatus { success ->
                    onScanAttemptResult?.invoke(success)
                }
            } else {
                Toast.makeText(this, "Bluetooth not enabled.", Toast.LENGTH_LONG).show()
                onScanAttemptResult?.invoke(false)
            }
        }

    // ActivityResultLauncher for enabling Location
    private val enableLocationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.d(TAG, "Location enable result.")
            checkBluetoothAndLocationStatus { success ->
                onScanAttemptResult?.invoke(success)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate.")
        initializeBluetooth()
        setupChartUpdateJob()

        val isDarkFlow: Flow<Boolean> = dataStore.data
            .map { preferences ->
                preferences[IS_DARK_MODE_KEY] ?: false
            }

        // Fetch FCM token and update it in Firestore
        // This will only apply if a Firebase user is logged in
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d(TAG, "FCM Token: $token")
            token?.let { authViewModel.updateFCMToken(it) }
        }

        handleComposeUI(isDarkFlow)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity onDestroy.")
        disconnectGatt()
        chartUpdateJob?.cancel()
        // ADDED: Cancel sessionDurationJob on destroy
        sessionDurationJob?.cancel()
        coroutineScope.cancel()
    }

    private fun initializeBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        Log.d(TAG, "BluetoothAdapter initialized. Is null: ${bluetoothAdapter == null}")

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // Starts a coroutine job to update charts periodically from the data buffer
    private fun setupChartUpdateJob() {
        chartUpdateJob?.cancel() // Cancel any existing job to prevent duplicates
        chartUpdateJob = coroutineScope.launch {
            Log.d(TAG, "Chart update job started.")
            while (true) {
                delay(CHART_UPDATE_INTERVAL) // Wait for a short interval
                // MODIFIED: Only process if data streaming is enabled and data exists
                if (isDataStreamingEnabled && dataBuffer.isNotEmpty()) {
                    Log.d(TAG, "Processing buffered data. Buffer size: ${dataBuffer.size}")
                    processBufferedData() // Process data if available
                } else if (!isDataStreamingEnabled) {
                    // Log.d(TAG, "Data streaming paused or stopped. Skipping chart update.") // Suppress this log for less spam
                    delay(500) // Longer delay when idle to reduce unnecessary logs
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleComposeUI(isDarkFlow: Flow<Boolean>) {
        setContent {
            val darkThemeFromPrefs by isDarkFlow.collectAsState(initial = isSystemInDarkTheme())
            isDarkThemeEnabled = darkThemeFromPrefs

            // Correctly observe currentUser and isAuthLoading from AuthViewModel
            val currentUser by authViewModel.currentUser
            val isAuthLoading by authViewModel.isAuthLoading // Observe the new loading state

            var currentAuthScreen by remember { mutableStateOf<AppScreen>(SIGN_IN) }
            // currentAdminScreen is now defined as a member variable
            // currentPatientScreen is already defined as a member variable.

            onScanAttemptResult = { success ->
                // This callback is crucial for permission handling in ScanScreen
                // It should update isLoadingPermissions state in ScanScreen
                // and decide whether to proceed with scan or show error.
                // For now, it's tied to the global `onScanAttemptResult` which
                // is passed to ScanScreen.
                // This part might need refinement to correctly propagate the state.
            }

            // MODIFIED: Added isProcessingResult to loading check
            if (isAuthLoading || isProcessingResult) {
                // Show a loading screen while auth state is being determined
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                    // MODIFIED: Dynamic loading text
                    Text(if (isProcessingResult) "Processing results..." else "Loading...", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                // Check if fixed admin is logged in, or if there's a Firebase user
                if (isAdminFixedLoggedIn) {
                    // Fixed Admin is logged in, show Admin interface
                    Crossfade(
                        targetState = currentAdminScreen,
                        animationSpec = tween(durationMillis = 500)
                    ) { targetScreen ->
                        when (targetScreen) {
                            ADMIN_SCAN -> ScanScreen(
                                devices = scannedDevices,
                                rssiValues = rssiMap,
                                isScanning = scanning,
                                onScanClick = {
                                    requestBluetoothPermissions { success ->
                                        if (success) {
                                            toggleScan()
                                        }
                                    }
                                },
                                onDeviceClick = { device ->
                                    connectToDevice(device)
                                    stopScan()
                                    currentAdminScreen = ADMIN_MAIN
                                },
                                scanInitiated = scannedDevices.isNotEmpty() || scanning,
                                isLoadingPermissions = false,
                                isDarkTheme = isDarkThemeEnabled,
                                onToggleTheme = {
                                    coroutineScope.launch {
                                        dataStore.edit { settings ->
                                            settings[IS_DARK_MODE_KEY] = !isDarkThemeEnabled
                                        }
                                    }
                                },
                                onSignOut = {
                                    isAdminFixedLoggedIn = false // Sign out fixed admin
                                    currentAuthScreen = SIGN_IN // Return to sign-in screen
                                },
                                onUserManualClick = { currentAdminScreen = ADMIN_USER_MANUAL } // Ensure this is passed
                            )
                            ADMIN_USER_MANUAL -> AdminUserManualScreen(
                                onBack = { currentAdminScreen = ADMIN_SCAN },
                                isDarkTheme = isDarkThemeEnabled,
                                onToggleTheme = {
                                    coroutineScope.launch {
                                        dataStore.edit { settings ->
                                            settings[IS_DARK_MODE_KEY] = !isDarkThemeEnabled
                                        }
                                    }
                                },
                                onSignOut = {
                                    isAdminFixedLoggedIn = false // Sign out fixed admin
                                    currentAuthScreen = SIGN_IN
                                }
                            )
                            ADMIN_GET_READY -> GetReadyScreen(
                                onCountdownComplete = {
                                    try {
                                        writeDataControl(true)
                                        // ADDED: Start session timer
                                        startSessionTimer()
                                        isTimerRunning = true // Mark timer as running
                                        timerStartTimeMillis = System.currentTimeMillis()
                                        timerAccumulatedMillis = 0L
                                        currentAdminScreen = ADMIN_MAIN
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error in onCountdownComplete: ${e.message}")
                                        mainHandler.post {
                                            Toast.makeText(this@MainActivity, "Error starting data stream: ${e.message}", Toast.LENGTH_LONG).show()
                                            currentAdminScreen = ADMIN_MAIN
                                            isTimerRunning = false
                                            timerStartTimeMillis = 0L
                                        }
                                    }
                                },
                                isDarkTheme = isDarkThemeEnabled,
                                onToggleTheme = {
                                    coroutineScope.launch {
                                        dataStore.edit { settings ->
                                            settings[IS_DARK_MODE_KEY] = !isDarkThemeEnabled
                                        }
                                    }
                                },
                                onSignOut = {
                                    isAdminFixedLoggedIn = false // Sign out fixed admin
                                    currentAuthScreen = SIGN_IN
                                }
                            )
                            ADMIN_MAIN -> MainScreen(
                                onScanClick = {
                                    try {
                                        writeDataControl(false)
                                        // ADDED: Cancel session timer on disconnect
                                        sessionDurationJob?.cancel()
                                        disconnectGatt()
                                        clearData()
                                        scannedDevices.clear()
                                        rssiMap.clear()
                                        currentAdminScreen = ADMIN_SCAN
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error during disconnect: ${e.message}")
                                        mainHandler.post {
                                            Toast.makeText(this@MainActivity, "Error disconnecting: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                fsrEntries = fsrEntries.value, // Access value here
                                flexEntries = flexEntries.value, // Access value here
                                imuBioEntries = imuBioEntries.value, // Access value here
                                dataUpdateTrigger = dataUpdateTrigger,
                                isDataStreamingEnabled = isDataStreamingEnabled,
                                isDeviceReadyForControl = isDeviceReadyForControl,
                                onStartStreaming = { currentAdminScreen = ADMIN_GET_READY },
                                onStopStreaming = {
                                    try {
                                        writeDataControl(false)
                                        // ADDED: Cancel session timer on manual stop
                                        sessionDurationJob?.cancel()
                                        if (isTimerRunning) {
                                            val elapsed = System.currentTimeMillis() - timerStartTimeMillis
                                            timerAccumulatedMillis += elapsed
                                            isTimerRunning = false
                                            timerStartTimeMillis = 0L
                                            Log.d(TAG, "Timer stopped. Accumulated: $timerAccumulatedMillis ms")
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error stopping streaming: ${e.message}")
                                        mainHandler.post {
                                            Toast.makeText(this@MainActivity, "Error stopping streaming", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                isDarkTheme = isDarkThemeEnabled,
                                onToggleTheme = {
                                    coroutineScope.launch {
                                        dataStore.edit { settings ->
                                            settings[IS_DARK_MODE_KEY] = !isDarkThemeEnabled
                                        }
                                    }
                                },
                                isTimerRunning = isTimerRunning,
                                timerStartTimeMillis = timerStartTimeMillis,
                                timerAccumulatedMillis = timerAccumulatedMillis,
                                onViewResult = {
                                    // MODIFIED: Trigger CSV export and transfer for Admin
                                    coroutineScope.launch(Dispatchers.IO) {
                                        triggerResultProcessing()
                                        mainHandler.post {
                                            currentAdminScreen = ADMIN_RESULT // Navigate to result screen
                                        }
                                    }
                                },
                                onSignOut = {
                                    isAdminFixedLoggedIn = false // Sign out fixed admin
                                    currentAuthScreen = SIGN_IN
                                }
                            )
                            // ADDED: Navigation case for ADMIN_RESULT
                            ADMIN_RESULT -> ResultScreen(
                                resultData = resultText,
                                onBackToMain = { currentAdminScreen = ADMIN_MAIN },
                                isDarkTheme = isDarkThemeEnabled,
                                onToggleTheme = {
                                    coroutineScope.launch {
                                        dataStore.edit { settings ->
                                            settings[IS_DARK_MODE_KEY] = !isDarkThemeEnabled
                                        }
                                    }
                                },
                                onSignOut = {
                                    authViewModel.signOut()
                                    currentAuthScreen = SIGN_IN
                                }
                            )
                            else -> {
                                currentAdminScreen = ADMIN_SCAN
                            }
                        }
                    }
                } else if (currentUser == null) {
                    // No Firebase user and not fixed admin logged in, show auth screens
                    Crossfade(
                        targetState = currentAuthScreen,
                        animationSpec = tween(durationMillis = 500)
                    ) { screen ->
                        when (screen) {
                            SIGN_IN -> SignInScreen(
                                viewModel = authViewModel,
                                onSignInSuccess = { userRole ->
                                    currentAuthScreen = when (userRole) {
                                        UserRole.ADMIN -> ADMIN_SCAN
                                        UserRole.DOCTOR -> DOCTOR_DASHBOARD
                                        UserRole.PATIENT -> PATIENT_DASHBOARD
                                    }
                                    Log.d(TAG, "Signed in as ${userRole.name}, navigating to $currentAuthScreen")
                                },
                                onNavigateToSignUp = { currentAuthScreen = SIGN_UP },
                                onFixedAdminLogin = { (email, password) -> // Destructure the pair here
                                    if (email == ADMIN_EMAIL && password == ADMIN_PASSWORD) {
                                        isAdminFixedLoggedIn = true
                                        currentAdminScreen = ADMIN_SCAN // Navigate to Admin dashboard
                                        // Also, sign out any Firebase user if logged in, to avoid conflict
                                        authViewModel.signOut()
                                    } else {
                                        Toast.makeText(this@MainActivity, "Invalid Admin Credentials", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            SIGN_UP -> SignUpScreen(
                                viewModel = authViewModel,
                                onSignUpSuccess = {
                                    currentAuthScreen = SIGN_IN
                                    Log.d(TAG, "Signed up, navigating to SIGN_IN")
                                },
                                onNavigateToSignIn = { currentAuthScreen = SIGN_IN }
                            )
                            else -> { /* Should not happen if logic is correct */ }
                        }
                    }
                } else {
                    // Firebase user is logged in, navigate based on role
                    when (currentUser?.role) {
                        UserRole.ADMIN.name -> { // This case handles actual Firebase 'ADMIN' users
                            Crossfade(
                                targetState = currentAdminScreen,
                                animationSpec = tween(durationMillis = 500)
                            ) { targetScreen ->
                                when (targetScreen) {
                                    ADMIN_SCAN -> ScanScreen(
                                        devices = scannedDevices,
                                        rssiValues = rssiMap,
                                        isScanning = scanning,
                                        onScanClick = {
                                            requestBluetoothPermissions { success ->
                                                if (success) {
                                                    toggleScan()
                                                }
                                            }
                                        },
                                        onDeviceClick = { device ->
                                            connectToDevice(device)
                                            stopScan()
                                            currentAdminScreen = ADMIN_MAIN
                                        },
                                        scanInitiated = scannedDevices.isNotEmpty() || scanning,
                                        isLoadingPermissions = false,
                                        isDarkTheme = isDarkThemeEnabled,
                                        onToggleTheme = {
                                            coroutineScope.launch {
                                                dataStore.edit { settings ->
                                                    settings[IS_DARK_MODE_KEY] = !isDarkThemeEnabled
                                                }
                                            }
                                        },
                                        onSignOut = { authViewModel.signOut(); currentAuthScreen = SIGN_IN },
                                        onUserManualClick = { currentAdminScreen = ADMIN_USER_MANUAL }
                                    )
                                    ADMIN_USER_MANUAL -> AdminUserManualScreen(
                                        onBack = { currentAdminScreen = ADMIN_SCAN },
                                        isDarkTheme = isDarkThemeEnabled,
                                        onToggleTheme = {
                                            coroutineScope.launch {
                                                dataStore.edit { settings ->
                                                    settings[IS_DARK_MODE_KEY] = !isDarkThemeEnabled
                                                }
                                            }
                                        },
                                        onSignOut = { authViewModel.signOut(); currentAuthScreen = SIGN_IN }
                                    )
                                    ADMIN_GET_READY -> GetReadyScreen(
                                        onCountdownComplete = {
                                            try {
                                                writeDataControl(true)
                                                // ADDED: Start session timer
                                                startSessionTimer()
                                                isTimerRunning = true // Mark timer as running
                                                timerStartTimeMillis = System.currentTimeMillis()
                                                timerAccumulatedMillis = 0L
                                                currentAdminScreen = ADMIN_MAIN
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error in onCountdownComplete: ${e.message}")
                                                mainHandler.post {
                                                    Toast.makeText(this@MainActivity, "Error starting data stream: ${e.message}", Toast.LENGTH_LONG).show()
                                                    currentAdminScreen = ADMIN_MAIN
                                                    isTimerRunning = false
                                                    timerStartTimeMillis = 0L
                                                }
                                            }
                                        },
                                        isDarkTheme = isDarkThemeEnabled,
                                        onToggleTheme = {
                                            coroutineScope.launch {
                                                dataStore.edit { settings ->
                                                    settings[IS_DARK_MODE_KEY] = !isDarkThemeEnabled
                                                }
                                            }
                                        },
                                        onSignOut = { authViewModel.signOut(); currentAuthScreen = SIGN_IN }
                                    )
                                    ADMIN_MAIN -> MainScreen(
                                        onScanClick = {
                                            try {
                                                writeDataControl(false)
                                                // ADDED: Cancel session timer on disconnect
                                                sessionDurationJob?.cancel()
                                                disconnectGatt()
                                                clearData()
                                                scannedDevices.clear()
                                                rssiMap.clear()
                                                currentAdminScreen = ADMIN_SCAN
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error during disconnect: ${e.message}")
                                                mainHandler.post {
                                                    Toast.makeText(this@MainActivity, "Error disconnecting: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        fsrEntries = fsrEntries.value, // Access value here
                                        flexEntries = flexEntries.value, // Access value here
                                        imuBioEntries = imuBioEntries.value, // Access value here
                                        dataUpdateTrigger = dataUpdateTrigger,
                                        isDataStreamingEnabled = isDataStreamingEnabled,
                                        isDeviceReadyForControl = isDeviceReadyForControl,
                                        onStartStreaming = { currentAdminScreen = ADMIN_GET_READY },
                                        onStopStreaming = {
                                            try {
                                                writeDataControl(false)
                                                // ADDED: Cancel session timer on manual stop
                                                sessionDurationJob?.cancel()
                                                if (isTimerRunning) {
                                                    val elapsed = System.currentTimeMillis() - timerStartTimeMillis
                                                    timerAccumulatedMillis += elapsed
                                                    isTimerRunning = false
                                                    timerStartTimeMillis = 0L
                                                    Log.d(TAG, "Timer stopped. Accumulated: $timerAccumulatedMillis ms")
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error stopping streaming: ${e.message}")
                                                mainHandler.post {
                                                    Toast.makeText(this@MainActivity, "Error stopping streaming", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        isDarkTheme = isDarkThemeEnabled,
                                        onToggleTheme = {
                                            coroutineScope.launch {
                                                dataStore.edit { settings ->
                                                    settings[IS_DARK_MODE_KEY] = !isDarkThemeEnabled
                                                }
                                            }
                                        },
                                        isTimerRunning = isTimerRunning,
                                        timerStartTimeMillis = timerStartTimeMillis,
                                        timerAccumulatedMillis = timerAccumulatedMillis,
                                        onViewResult = {
                                            // Trigger CSV export and transfer for Admin (Firebase user)
                                            triggerResultProcessing()
                                            mainHandler.post {
                                                currentAdminScreen =
                                                    ADMIN_RESULT // Navigate to result screen
                                            }
                                        },
                                        onSignOut = {
                                            authViewModel.signOut()
                                            currentAuthScreen = SIGN_IN
                                        }
                                    )
                                    // ADDED: Navigation case for ADMIN_RESULT
                                    ADMIN_RESULT -> ResultScreen(
                                        resultData = resultText,
                                        onBackToMain = { currentAdminScreen = ADMIN_MAIN },
                                        isDarkTheme = isDarkThemeEnabled,
                                        onToggleTheme = {
                                            coroutineScope.launch {
                                                dataStore.edit { settings ->
                                                    settings[IS_DARK_MODE_KEY] = !isDarkThemeEnabled
                                                }
                                            }
                                        },
                                        onSignOut = {
                                            authViewModel.signOut()
                                            currentAuthScreen = SIGN_IN
                                        }
                                    )
                                    else -> {
                                        currentAdminScreen = ADMIN_SCAN
                                    }
                                }
                            }
                        }
                        UserRole.DOCTOR.name -> {
                            DoctorInterface(
                                onToggleTheme = {
                                    coroutineScope.launch {
                                        dataStore.edit { settings ->
                                            settings[IS_DARK_MODE_KEY] = !isDarkThemeEnabled
                                        }
                                    }
                                },
                                isDarkTheme = isDarkThemeEnabled,
                                authViewModel = authViewModel,
                                onSignOut = { authViewModel.signOut(); currentAuthScreen = SIGN_IN }
                            )
                        }
                        UserRole.PATIENT.name -> {
                            // Patient's main navigation logic
                            Crossfade(
                                targetState = currentPatientScreen,
                                animationSpec = tween(durationMillis = 500)
                            ) { targetScreen ->
                                when (targetScreen) {
                                    PATIENT_DASHBOARD -> PatientInterface(
                                        onToggleTheme = {
                                            coroutineScope.launch {
                                                dataStore.edit { settings ->
                                                    settings[IS_DARK_MODE_KEY] = !isDarkThemeEnabled
                                                }
                                            }
                                        },
                                        isDarkTheme = isDarkThemeEnabled,
                                        fsrEntries = fsrEntries.value, // Access value here
                                        flexEntries = flexEntries.value, // Access value here
                                        authViewModel = authViewModel,
                                        onScanClick = {
                                            requestBluetoothPermissions { success ->
                                                if (success) {
                                                    toggleScan()
                                                }
                                            }
                                        },
                                        isScanning = scanning,
                                        scannedDevices = scannedDevices,
                                        rssiMap = rssiMap,
                                        connectToDevice = { connectToDevice(it) },
                                        stopScan = { stopScan() },
                                        writeDataControl = { writeDataControl(it) },
                                        disconnectGatt = { disconnectGatt() },
                                        clearData = { clearData() },
                                        isDataStreamingEnabled = isDataStreamingEnabled,
                                        isDeviceReadyForControl = isDeviceReadyForControl,
                                        isTimerRunning = isTimerRunning,
                                        timerStartTimeMillis = timerStartTimeMillis,
                                        timerAccumulatedMillis = timerAccumulatedMillis,
                                        onSignOut = { authViewModel.signOut(); currentAuthScreen = SIGN_IN },
                                        onUserManualClick = {
                                            currentPatientScreen = PATIENT_USER_MANUAL
                                        },
                                        onStartSessionCountdown = {
                                            currentPatientScreen = PATIENT_GET_READY
                                        },
                                        onViewResult = {
                                            // ADDED: Trigger CSV export and transfer for Patient
                                            coroutineScope.launch(Dispatchers.IO) {
                                                triggerPatientResultProcessing()
                                                mainHandler.post {
                                                    currentPatientScreen =
                                                        PATIENT_RESULT // Navigate to result screen
                                                }
                                            }
                                        },
                                        onStopStreaming = {
                                            try {
                                                writeDataControl(false)
                                                sessionDurationJob?.cancel()
                                                if (isTimerRunning) {
                                                    val elapsed =
                                                        System.currentTimeMillis() - timerStartTimeMillis
                                                    timerAccumulatedMillis += elapsed
                                                    isTimerRunning = false
                                                    timerStartTimeMillis = 0L
                                                    Log.d(
                                                        TAG,
                                                        "Timer stopped. Accumulated: $timerAccumulatedMillis ms"
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error stopping streaming: ${e.message}")
                                                mainHandler.post {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "Error stopping streaming",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                    )
                                    PATIENT_USER_MANUAL -> UserManualScreen( // Display the UserManualScreen here
                                        isDarkTheme = isDarkThemeEnabled,
                                        onToggleTheme = {
                                            coroutineScope.launch {
                                                dataStore.edit { settings ->
                                                    settings[IS_DARK_MODE_KEY] = !isDarkThemeEnabled
                                                }
                                            }
                                        },
                                        onNextClick = { /* Manual has no specific 'next' for patient view */ },
                                        onSignOut = { authViewModel.signOut(); currentAuthScreen = SIGN_IN },
                                        onBack = { currentPatientScreen = PATIENT_DASHBOARD } // Go back to patient dashboard
                                    )
                                    PATIENT_GET_READY -> GetReadyScreen(
                                        onCountdownComplete = {
                                            try {
                                                writeDataControl(true)
                                                // ADDED: Start session timer
                                                startSessionTimer()
                                                isTimerRunning = true
                                                timerStartTimeMillis = System.currentTimeMillis()
                                                timerAccumulatedMillis = 0L
                                                currentPatientScreen = PATIENT_DASHBOARD // Go back to dashboard after countdown, main screen will handle data display
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error in onCountdownComplete: ${e.message}")
                                                mainHandler.post {
                                                    Toast.makeText(this@MainActivity, "Error starting data stream: ${e.message}", Toast.LENGTH_LONG).show()
                                                    currentPatientScreen = PATIENT_DASHBOARD
                                                    isTimerRunning = false
                                                    timerStartTimeMillis = 0L
                                                }
                                            }
                                        },
                                        isDarkTheme = isDarkThemeEnabled,
                                        onToggleTheme = {
                                            coroutineScope.launch {
                                                dataStore.edit { settings ->
                                                    settings[IS_DARK_MODE_KEY] = !isDarkThemeEnabled
                                                }
                                            }
                                        },
                                        onSignOut = { authViewModel.signOut(); currentAuthScreen = SIGN_IN }
                                    )
                                    // ADDED: Navigation case for PATIENT_RESULT
                                    PATIENT_RESULT -> ResultScreen(
                                        resultData = resultText,
                                        onBackToMain = { currentPatientScreen = PATIENT_DASHBOARD },
                                        isDarkTheme = isDarkThemeEnabled,
                                        onToggleTheme = {
                                            coroutineScope.launch {
                                                dataStore.edit { settings ->
                                                    settings[IS_DARK_MODE_KEY] = !isDarkThemeEnabled
                                                }
                                            }
                                        },
                                        onSignOut = {
                                            authViewModel.signOut()
                                            currentAuthScreen = SIGN_IN
                                        }
                                    )
                                    else -> {
                                        currentPatientScreen = PATIENT_DASHBOARD
                                    }
                                }
                            }
                        }
                        else -> {
                            currentAuthScreen = SIGN_IN
                            Log.e(TAG, "Unknown user role or unapproved patient: ${currentUser?.role}")
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestBluetoothPermissions(callback: (Boolean) -> Unit) {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            @Suppress("DEPRECATION")
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            @Suppress("DEPRECATION")
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }


        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d(TAG, "All permissions granted.")
            checkBluetoothAndLocationStatus(callback)
        }
    }

    private fun checkBluetoothAndLocationStatus(callback: (Boolean) -> Unit) {
        Log.d(TAG, "Checking Bluetooth and Location Status...")
        if (bluetoothAdapter == null) {
            Log.e(TAG, "BluetoothAdapter null.")
            Toast.makeText(this, "Bluetooth not supported.", Toast.LENGTH_LONG).show()
            callback(false)
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth not enabled.")
            Toast.makeText(this, "Please enable Bluetooth.", Toast.LENGTH_SHORT).show()
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
            return
        }

        checkLocationServiceStatus(callback)
    }

    private fun checkLocationServiceStatus(callback: (Boolean) -> Unit) {
        Log.d(TAG, "Checking Location Service Status...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            if (!isLocationEnabled) {
                Log.w(TAG, "Location Services not enabled.")
                Toast.makeText(this, "Please enable Location Services.", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                enableLocationLauncher.launch(intent)
                return
            }
        }
        Log.d(TAG, "All pre-scan checks passed.")
        callback(true)
    }

    @SuppressLint("MissingPermission")
    private fun toggleScan() {
        if (scanning) stopScan() else startScan()
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        Log.d(TAG, "Starting BLE scan.")
        scannedDevices.clear()
        rssiMap.clear()
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
        scanning = true
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        Log.d(TAG, "Stopping BLE scan.")
        bluetoothLeScanner?.stopScan(scanCallback)
        scanning = false
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val deviceName = device.name ?: "Unknown Device"
                val deviceAddress = device.address
                if (scannedDevices.none { it.address == deviceAddress }) {
                    scannedDevices.add(ScannedDeviceInfo(deviceName, deviceAddress))
                    Log.d(TAG, "Found device: $deviceName (${deviceAddress}), RSSI: ${result.rssi}")
                }
                rssiMap[deviceAddress] = result.rssi ?: 0
            }
        }
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "BLE Scan Failed: $errorCode")
            Toast.makeText(this@MainActivity, "BLE Scan Failed: $errorCode", Toast.LENGTH_LONG).show()
            scanning = false
            onScanAttemptResult?.invoke(false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(deviceInfo: ScannedDeviceInfo) {
        Log.d(TAG, "Connecting to device: ${deviceInfo.address}")
        try {
            val device = bluetoothAdapter.getRemoteDevice(deviceInfo.address)
            connectedGatt = device.connectGatt(this, false, gattCallback)
            isDeviceReadyForControl = false
            timerAccumulatedMillis = 0L
            isTimerRunning = false
            timerStartTimeMillis = 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to device: ${e.message}")
            mainHandler.post {
                Toast.makeText(this, "Error connecting to device.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGatt() {
        Log.d(TAG, "Disconnecting GATT.")
        try {
            if (isTimerRunning) {
                val elapsed = System.currentTimeMillis() - timerStartTimeMillis
                timerAccumulatedMillis += elapsed
                isTimerRunning = false
                timerStartTimeMillis = 0L
                Log.d(TAG, "Timer stopped. Accumulated: $timerAccumulatedMillis ms")
            }
            // ADDED: Cancel sessionDurationJob on GATT disconnect
            sessionDurationJob?.cancel()

            connectedGatt?.apply {
                disconnect()
                close()
            }
            connectedGatt = null
            controlCharacteristic = null
            pNotifyCharacteristic = null
            mainHandler.post {
                isDataStreamingEnabled = false
                isDeviceReadyForControl = false
                isTimerRunning = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during GATT disconnect: ${e.message}")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: device=${gatt.device.address}, status=$status, newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT connected to ${gatt.device.address}.")
                    try {
                        val cacheRefreshed = refreshDeviceCache(gatt)
                        Log.d(TAG, "GATT cache refresh: $cacheRefreshed")

                        Handler(Looper.getMainLooper()).postDelayed({
                            gatt.requestMtu(512)
                            Handler(Looper.getMainLooper()).postDelayed({
                                @SuppressLint("MissingPermission")
                                val discovered = gatt.discoverServices()
                                Log.d(TAG, "discoverServices: $discovered")
                                if (!discovered) {
                                    Log.e(TAG, "Failed to start service discovery")
                                    mainHandler.post { Toast.makeText(this@MainActivity, "Failed to discover services", Toast.LENGTH_SHORT).show() }
                                    disconnectGatt()
                                }
                            }, 500)
                        }, 200)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during GATT setup: ${e.message}")
                        disconnectGatt()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "GATT disconnected from ${gatt.device.address}")
                    mainHandler.post {
                        Toast.makeText(this@MainActivity, "Disconnected from device", Toast.LENGTH_SHORT).show()
                        isDataStreamingEnabled = false
                        isDeviceReadyForControl = false
                        if (isTimerRunning) {
                            val elapsed = System.currentTimeMillis() - timerStartTimeMillis
                            timerAccumulatedMillis += elapsed
                            isTimerRunning = false
                            timerStartTimeMillis = 0L
                            Log.d(TAG, "Timer stopped. Accumulated: $timerAccumulatedMillis ms")
                        }
                    }
                    disconnectGatt()
                }
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "GATT connection error: $status")
                mainHandler.post {
                    Toast.makeText(this@MainActivity, "Connection Error: $status", Toast.LENGTH_LONG).show()
                    disconnectGatt()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "onMtuChanged: mtu=$mtu, status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "MTU updated to $mtu")
            } else {
                Log.e(TAG, "Failed to set MTU: $status")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                try {
                    val service = gatt.getService(SERVICE_UUID)
                    if (service == null) {
                        Log.e(TAG, "Glove Service not found!")
                        mainHandler.post { Toast.makeText(this@MainActivity, "Glove Service not found!", Toast.LENGTH_LONG).show() }
                        disconnectGatt()
                        return
                    }
                    Log.d(TAG, "Glove Service found: ${service.uuid}")

                    val notifyChar = service.getCharacteristic(CHARACTERISTIC_NOTIFY_UUID)
                    val writeChar = service.getCharacteristic(CHARACTERISTIC_WRITE_UUID)

                    if (notifyChar == null) {
                        Log.e(TAG, "Notify Characteristic not found!")
                        mainHandler.post { Toast.makeText(this@MainActivity, "Notify Characteristic not found!", Toast.LENGTH_LONG).show() }
                        disconnectGatt()
                        return
                    }
                    Log.d(TAG, "Notify Characteristic found: ${notifyChar.uuid}")

                    if (writeChar == null) {
                        Log.e(TAG, "Write Characteristic not found!")
                        mainHandler.post { Toast.makeText(this@MainActivity, "Control Characteristic not found!", Toast.LENGTH_LONG).show() }
                        disconnectGatt()
                        return
                    }
                    Log.d(TAG, "Write Characteristic found: ${writeChar.uuid}")

                    pNotifyCharacteristic = notifyChar
                    controlCharacteristic = writeChar

                    // IMPORTANT: Enable notifications first
                    val setNotifySuccess = gatt.setCharacteristicNotification(notifyChar, true)
                    Log.d(TAG, "setCharacteristicNotification: $setNotifySuccess")

                    val descriptor = notifyChar.getDescriptor(CLIENT_CHAR_CONFIG_UUID)
                    if (descriptor == null) {
                        Log.e(TAG, "Client Characteristic Config Descriptor not found!")
                        mainHandler.post { Toast.makeText(this@MainActivity, "Notification descriptor not found!", Toast.LENGTH_LONG).show() }
                        disconnectGatt()
                        return
                    }

                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    Log.d(TAG, "Attempting to write descriptor: ${descriptor.uuid} with value ${BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE.contentToString()}")

                    // Add a small delay before writing the descriptor
                    // This often helps with race conditions or peripheral readiness
                    Handler(Looper.getMainLooper()).postDelayed({
                        val writeDescriptorSuccess = gatt.writeDescriptor(descriptor)
                        Log.d(TAG, "writeDescriptor called: $writeDescriptorSuccess")
                    }, 200) // 200ms delay
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onServicesDiscovered: ${e.message}")
                    disconnectGatt()
                }
            } else {
                Log.e(TAG, "Service discovery failed: $status")
                mainHandler.post { Toast.makeText(this@MainActivity, "Service discovery failed: $status", Toast.LENGTH_LONG).show() }
                disconnectGatt()
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (characteristic?.uuid == CHARACTERISTIC_WRITE_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Control Characteristic write successful")
                    val commandSent = characteristic?.value?.toString(Charsets.UTF_8)
                    mainHandler.post {
                        if (commandSent == "START") {
                            isDataStreamingEnabled = true
                            if (!isTimerRunning) {
                                isTimerRunning = true
                                timerStartTimeMillis = System.currentTimeMillis()
                                timerAccumulatedMillis = 0L
                            }
                        } else if (commandSent == "STOP") {
                            isDataStreamingEnabled = false
                            if (isTimerRunning) {
                                val elapsed = System.currentTimeMillis() - timerStartTimeMillis
                                timerAccumulatedMillis += elapsed
                                isTimerRunning = false
                                timerStartTimeMillis = 0L
                                Log.d(TAG, "Timer stopped. Accumulated: $timerAccumulatedMillis ms")
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Control Characteristic write failed: $status")
                    mainHandler.post { Toast.makeText(this@MainActivity, "Command send failed: $status", Toast.LENGTH_SHORT).show() }
                    isDataStreamingEnabled = false
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(TAG, "onDescriptorWrite CALLED: descriptor=${descriptor.uuid}, status=$status")
            if (descriptor.uuid == CLIENT_CHAR_CONFIG_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Notification descriptor written successfully for ${descriptor.uuid}")
                    mainHandler.post {
                        Toast.makeText(this@MainActivity, "Notifications enabled.", Toast.LENGTH_SHORT).show()
                        isDeviceReadyForControl = true
                    }
                } else {
                    Log.e(TAG, "Failed to write notification descriptor: ${descriptor.uuid}, status=$status")
                    mainHandler.post { Toast.makeText(this@MainActivity, "Failed to enable notifications. Status: $status", Toast.LENGTH_LONG).show() }
                    disconnectGatt()
                }
            } else {
                Log.w(TAG, "onDescriptorWrite called for unknown descriptor: ${descriptor.uuid}")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            // Log.d(TAG, "onCharacteristicChanged CALLED for char: ${characteristic.uuid}") // Suppress this log for less spam
            if (characteristic.uuid == CHARACTERISTIC_NOTIFY_UUID) {
                val receivedData = value.toString(Charsets.UTF_8)
                // Log.d(TAG, "Received raw data from BLE: '$receivedData'") // Suppress this log for less spam
                buffer.append(receivedData)

                // Process lines ending with newline character
                while (buffer.contains("\n")) {
                    val newlineIndex = buffer.indexOf("\n")
                    if (newlineIndex != -1) {
                        val completeLine = buffer.substring(0, newlineIndex).trim()
                        buffer.delete(0, newlineIndex + 1) // Remove the processed line and newline

                        if (completeLine.isNotEmpty()) { // Only process non-empty lines
                            processReceivedLine(completeLine)
                        } else {
                            Log.w(TAG, "Skipping empty line from BLE buffer.")
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshDeviceCache(gatt: BluetoothGatt?): Boolean {
        if (gatt == null) return false
        try {
            val localMethod = gatt.javaClass.getMethod("refresh")
            if (localMethod != null) {
                val bool = localMethod.invoke(gatt) as Boolean
                Log.d(TAG, "refreshDeviceCache: $bool")
                return bool
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing device cache: ${e.message}")
        }
        return false
    }

    private fun processReceivedLine(line: String) {
        try {
            val values = line.split(",").map { it.toFloatOrNull() ?: 0f }
            if (values.size == 17) {
                // ADDED: Validate if this is actual sensor data vs noise
                // Check if we have meaningful values (not all zeros or extreme values)
                val hasValidData = values.any { it != 0f && it > -1000f && it < 1000f }
                if (hasValidData) {
                    synchronized(dataBuffer) {
                        // Prevent buffer overflow
                        if (dataBuffer.size >= MAX_BUFFER_SIZE) {
                            dataBuffer.removeAt(0) // Remove oldest data
                            Log.w(TAG, "Buffer full, removing oldest data point")
                        }
                        dataBuffer.add(values)
                        Log.d(TAG, "Added valid sensor data to buffer (size: ${dataBuffer.size})")
                    }
                } else {
                    Log.d(TAG, "Filtered out noise/invalid data: all zeros or extreme values")
                }
            } else {
                Log.w(TAG, "Malformed data: '$line' (Expected 17, got ${values.size})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing data: ${e.message}, Line: '$line'")
        }
    }

    private fun processBufferedData() {
        val dataToProcess = mutableListOf<List<Float>>()
        synchronized(dataBuffer) {
            dataToProcess.addAll(dataBuffer)
            dataBuffer.clear()
        }

        if (dataToProcess.isEmpty()) {
            // Log.d(TAG, "processBufferedData: No data to process.") // Suppress for less spam
            return
        }
        // Log.d(TAG, "processBufferedData: Processing ${dataToProcess.size} new data points.") // Suppress for less spam

        var chartDataModified = false

        for (values in dataToProcess) {
            if (values.size != 17) {
                Log.w(TAG, "Skipping malformed data in processBufferedData (IMU/BioAmp): $values (expected 17, got ${values.size})")
                continue
            }

            // Update IMU/BioAmp entries
            // Synchronize access to _imuBioEntriesInternal if it's potentially modified from other threads
            // It's already SynchronizedList, so direct access is safe, but ensure its mutable sub-lists are handled.
            // When adding, ensure size limits are respected.
            val imuBioRawValues = listOf(
                values[0], values[11], values[12], values[13], values[14], values[15], values[16]
            )
            for (i in imuBioRawValues.indices) {
                // Synchronize on the individual mutable list to prevent concurrent modification errors
                synchronized(_imuBioEntriesInternal[i]) {
                    imuBioMovingAverages[i].add(imuBioRawValues[i])
                    if (imuBioMovingAverages[i].size > windowSize) {
                        imuBioMovingAverages[i].removeAt(0)
                    }
                    val filteredValue = imuBioMovingAverages[i].average().toFloat()
                    _imuBioEntriesInternal[i].add(Entry(xIndex, filteredValue))
                    if (_imuBioEntriesInternal[i].size > MAX_DATA_POINTS) {
                        _imuBioEntriesInternal[i].removeAt(0)
                    }
                    chartDataModified = true
                }
            }

            // Update Flex entries
            for (i in 0 until 5) {
                // Synchronize on the individual mutable list
                synchronized(_flexEntriesInternal[i]) {
                    val rawValue = values[i + 1]
                    flexMovingAverages[i].add(rawValue)
                    if (flexMovingAverages[i].size > windowSize) {
                        flexMovingAverages[i].removeAt(0)
                    }
                    val filteredValue = flexMovingAverages[i].average().toFloat()
                    _flexEntriesInternal[i].add(Entry(xIndex, filteredValue))
                    // Log.d(TAG, "Flex Sensor $i Filtered Value: $filteredValue") // Suppress for less spam
                    if (_flexEntriesInternal[i].size > MAX_DATA_POINTS) {
                        _flexEntriesInternal[i].removeAt(0)
                    }
                    chartDataModified = true
                }
            }

            // Update FSR entries
            for (i in 0 until 5) {
                // Synchronize on the individual mutable list
                synchronized(_fsrEntriesInternal[i]) {
                    val rawValue = values[i + 6]
                    fsrMovingAverages[i].add(rawValue)
                    if (fsrMovingAverages[i].size > windowSize) {
                        fsrMovingAverages[i].removeAt(0)
                    }
                    val filteredValue = fsrMovingAverages[i].average().toFloat()
                    _fsrEntriesInternal[i].add(Entry(xIndex, filteredValue))
                    // Log.d(TAG, "FSR Sensor $i Filtered Value: $filteredValue") // Suppress for less spam
                    if (_fsrEntriesInternal[i].size > MAX_DATA_POINTS) {
                        _fsrEntriesInternal[i].removeAt(0)
                    }
                    chartDataModified = true
                }
            }
            xIndex += 1
        }


        if (chartDataModified) {
            // Log.d(TAG, "processBufferedData: chartDataModified is TRUE. Attempting UI update.") // Suppress for less spam
            mainHandler.post {
                try {
                    // Update the `MutableState<List<List<Entry>>>` by creating new lists
                    // These assignments create new immutable lists, so `mainHandler.post` is sufficient for UI thread safety.
                    fsrEntries.value = _fsrEntriesInternal.map { it.toList() }
                    flexEntries.value = _flexEntriesInternal.map { it.toList() }
                    imuBioEntries.value = _imuBioEntriesInternal.map { it.toList() }

                    dataUpdateTrigger++ // This will now ensure recomposition is triggered
                    // Log.d(TAG, "Chart data updated. Trigger: $dataUpdateTrigger") // Suppress for less spam
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating chart entries on main thread: ${e.message}")
                }
            }
        }
    }

    private fun clearData() {
        Log.d(TAG, "Clearing chart data and timer")
        try {
            // Clear internal lists within synchronized blocks
            synchronized(_fsrEntriesInternal) { _fsrEntriesInternal.forEach { it.clear() } }
            synchronized(_flexEntriesInternal) { _flexEntriesInternal.forEach { it.clear() } }
            synchronized(_imuBioEntriesInternal) { _imuBioEntriesInternal.forEach { it.clear() } }

            mainHandler.post {
                // Explicitly set to empty lists to trigger recomposition
                fsrEntries.value = List(5) { emptyList() }
                flexEntries.value = List(5) { emptyList() }
                imuBioEntries.value = List(7) { emptyList() }

                dataUpdateTrigger++ // Increment trigger to force recomposition
                Log.d(TAG, "Cleared data trigger: $dataUpdateTrigger")
            }

            // Clear moving average lists within synchronized blocks
            synchronized(fsrMovingAverages) { fsrMovingAverages.forEach { it.clear() } }
            synchronized(flexMovingAverages) { flexMovingAverages.forEach { it.clear() } }
            synchronized(imuBioMovingAverages) { imuBioMovingAverages.forEach { it.clear() } }

            dataBuffer.clear()
            xIndex = 0f

            timerAccumulatedMillis = 0L
            isTimerRunning = false // Reset timer state
            timerStartTimeMillis = 0L

            Log.d(TAG, "After clearData - _fsrEntriesInternal size: ${_fsrEntriesInternal.sumOf { it.size }}")
            Log.d(TAG, "After clearData - fsrEntries.value size: ${fsrEntries.value.sumOf { it.size }}")
            Log.d(TAG, "After clearData - _flexEntriesInternal size: ${_flexEntriesInternal.sumOf { it.size }}")
            Log.d(TAG, "After clearData - flexEntries.value size: ${flexEntries.value.sumOf { it.size }}")
            Log.d(TAG, "After clearData - _imuBioEntriesInternal size: ${_imuBioEntriesInternal.sumOf { it.size }}")
            Log.d(TAG, "After clearData - imuBioEntries.value size: ${imuBioEntries.value.sumOf { it.size }}")

        } catch (e: Exception) {
            Log.e(TAG, "Error clearing data: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeDataControl(enable: Boolean) {
        val command = if (enable) "START" else "STOP"
        Log.d(TAG, "writeDataControl: $command. States: connectedGatt=${connectedGatt != null}, controlCharacteristic=${controlCharacteristic != null}, isDeviceReadyForControl=$isDeviceReadyForControl, isDataStreamingEnabled=$isDataStreamingEnabled, timerRunning=$isTimerRunning, accumulatedMillis=$timerAccumulatedMillis")

        try {
            if (connectedGatt == null) {
                Log.e(TAG, "No GATT connection for $command.")
                mainHandler.post {
                    Toast.makeText(this@MainActivity, "Not connected to device.", Toast.LENGTH_SHORT).show()
                }
                isDataStreamingEnabled = false
                return
            }

            if (controlCharacteristic == null) {
                Log.e(TAG, "Control characteristic not found for $command.")
                mainHandler.post {
                    Toast.makeText(this@MainActivity, "Control characteristic unavailable.", Toast.LENGTH_SHORT).show()
                }
                isDataStreamingEnabled = false
                return
            }

            if (!isDeviceReadyForControl) {
                Log.w(TAG, "Device not ready for $command. Retrying in 500ms.")
                mainHandler.postDelayed({
                    if (isDeviceReadyForControl && connectedGatt != null && controlCharacteristic != null) {
                        Log.d(TAG, "Retry writeDataControl: $command")
                        controlCharacteristic?.value = command.toByteArray(Charsets.UTF_8)
                        connectedGatt?.writeCharacteristic(controlCharacteristic)
                    } else {
                        Log.e(TAG, "Retry failed: Device still not ready or GATT/characteristic null.")
                        mainHandler.post {
                            Toast.makeText(this@MainActivity, "Device not ready, command failed.", Toast.LENGTH_SHORT).show()
                        }
                        isDataStreamingEnabled = false
                    }
                }, 500)
                return
            }

            controlCharacteristic?.value = command.toByteArray(Charsets.UTF_8)
            val writeSuccess = connectedGatt?.writeCharacteristic(controlCharacteristic)
            Log.d(TAG, "writeCharacteristic: $writeSuccess")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing control command: ${e.message}")
            mainHandler.post {
                Toast.makeText(this@MainActivity, "Error sending command: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            isDataStreamingEnabled = false
        }
    }

    /**
     * ADDED: Starts a 2-minute timer for the session duration.
     * When the timer finishes, it stops data streaming and saves data to CSV.
     */
    private fun startSessionTimer() {
        sessionDurationJob?.cancel() // Cancel any existing session timer
        sessionDurationJob = coroutineScope.launch {
            Log.d(TAG, "Session duration timer started for ${DATA_STREAM_DURATION_MS / 1000} seconds.")
            delay(DATA_STREAM_DURATION_MS)
            Log.d(TAG, "Session duration timer finished. Stopping streaming.")
            mainHandler.post {
                Toast.makeText(this@MainActivity, "Session finished! Processing data...", Toast.LENGTH_LONG).show()
            }
            try {
                writeDataControl(false) // Stop BLE data stream
                val elapsed = System.currentTimeMillis() - timerStartTimeMillis
                timerAccumulatedMillis += elapsed
                isTimerRunning = false
                timerStartTimeMillis = 0L
                Log.d(TAG, "Timer stopped. Accumulated: $timerAccumulatedMillis ms (auto-stop)")

                // Trigger result processing based on user type
                val currentUser = authViewModel.currentUser.value
                if (currentUser?.role == UserRole.PATIENT.name) {
                    // Patient user - use patient result processing
                    triggerPatientResultProcessing()
                } else {
                    // Admin user - use admin result processing
                    triggerResultProcessing()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during auto-stop session: ${e.message}", e)
                mainHandler.post {
                    Toast.makeText(this@MainActivity, "Error during session auto-stop: ${e.message}", Toast.LENGTH_LONG).show()
                    isProcessingResult = false // Hide loading if error
                    resultText = "Error during auto-stop: ${e.message}"
                    // Navigate to result screen even on error to show message
                    navigateToResultScreen()
                }
            }
        }
    }

    /**
     * ADDED: Centralized function to handle CSV saving, upload, and result fetching.
     * Called when session ends (auto or manual stop).
     */
    private fun triggerResultProcessing() {
        isProcessingResult = true
        mainHandler.post {
            resultText = "Uploading data to cloud..."
        }

        // Use GlobalScope to avoid cancellation when parent coroutines are cancelled
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                val csvFile = saveDataToCsv()
                if (csvFile != null) {
                    // Upload to Firebase Storage - this will trigger the cloud function
                    val firebasePath = uploadToFirebase(csvFile)

                    mainHandler.post {
                        resultText = "Processing data with AI algorithm..."
                    }

                    // Wait for the cloud function to process and store results
                    val docId = firebasePath.replace("sessions/", "").replace(".csv", "")
                    waitForProcessingResult(docId)
                } else {
                    mainHandler.post {
                        resultText = "No data to process"
                        isProcessingResult = false
                        navigateToResultScreen()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in triggerResultProcessing: ${e.message}", e)
                mainHandler.post {
                    resultText = "Error processing data: ${e.message}"
                    isProcessingResult = false
                    navigateToResultScreen()
                }
            }
        }
    }

    /**
     * ADDED: Centralized function for patients to handle CSV saving, upload, and result fetching.
     * Adds patient info (UID and email) to result document for doctor access.
     */
    private fun triggerPatientResultProcessing() {
        isProcessingResult = true
        mainHandler.post {
            resultText = "Uploading data to cloud..."
        }

        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                val csvFile = saveDataToCsv()
                if (csvFile != null) {
                    val firebasePath = uploadToFirebase(csvFile)
                    mainHandler.post {
                        resultText = "Processing data with AI algorithm..."
                    }
                    val docId = firebasePath.replace("sessions/", "").replace(".csv", "")
                    val user = authViewModel.currentUser.value
                    if (user != null) {
                        // Add patient info for doctor to query
                        val docRef =
                            Firebase.firestore.collection("processingResults").document(docId)
                        val info = hashMapOf(
                            "patientUid" to user.uid,
                            "patientEmail" to user.email,
                            "patientDisplayName" to (user.name ?: ""),
                            "requestedAt" to FieldValue.serverTimestamp()
                        )
                        // Fire and forget, don't wait for success/failure
                        docRef.set(info, com.google.firebase.firestore.SetOptions.merge())
                    }
                    waitForProcessingResult(docId)
                } else {
                    mainHandler.post {
                        resultText = "No data to process"
                        isProcessingResult = false
                        navigateToResultScreen()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in triggerPatientResultProcessing: ${e.message}", e)
                mainHandler.post {
                    resultText = "Error processing data: ${e.message}"
                    isProcessingResult = false
                    navigateToResultScreen()
                }
            }
        }
    }


    /**
     * ADDED: Helper function to navigate to result screen based on user role
     */
    private fun navigateToResultScreen() {
        when {
            isAdminFixedLoggedIn -> currentAdminScreen = ADMIN_RESULT
            authViewModel.currentUser.value?.role == UserRole.ADMIN.name -> currentAdminScreen = ADMIN_RESULT
            authViewModel.currentUser.value?.role == UserRole.PATIENT.name -> currentPatientScreen = PATIENT_RESULT
            else -> Log.e(TAG, "Attempted to navigate to result screen but user role unknown.")
        }
    }


    /**
     * ADDED: Saves the collected sensor data to a CSV file.
     * @return The File object of the saved CSV, or null if an error occurred.
     */
    private fun saveDataToCsv(): File? {
        // Ensure there's data to save
        // Check if all internal lists have data
        val hasData = _fsrEntriesInternal.any { it.isNotEmpty() } ||
                _flexEntriesInternal.any { it.isNotEmpty() } ||
                _imuBioEntriesInternal.any { it.isNotEmpty() }

        if (!hasData) {
            Log.w(TAG, "No data collected to save to CSV.")
            return null
        }

        // Use app-specific external storage. This directory doesn't require WRITE_EXTERNAL_STORAGE permission
        // on modern Android versions (API 29+). It's cleared when the app is uninstalled.
        val outputDir = applicationContext.getExternalFilesDir(null) ?: run {
            Log.e(TAG, "Failed to get external files directory.")
            return null
        }
        val dataDir = File(outputDir, "glove_data")
        if (!dataDir.exists()) {
            dataDir.mkdirs()
            Log.d(TAG, "Created directory: ${dataDir.absolutePath}")
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${timestamp}_glove_session.csv"
        val csvFile = File(dataDir, fileName)

        try {
            BufferedWriter(FileWriter(csvFile)).use { writer ->
                // Write CSV Header
                writer.append("Timestamp,")
                for (i in 1..5) writer.append("Flex$i,")
                for (i in 1..5) writer.append("FSR$i,")
                writer.append("IMU_X,IMU_Y,IMU_Z,IMU_Roll,IMU_Pitch,IMU_Yaw,BioAmp")
                writer.append("\n")

                // Determine the maximum number of data points across all sensor types
                val numDataPoints = xIndex.toInt()
                Log.d(TAG, "Saving CSV with $numDataPoints data points.")

                // Write data rows
                for (x in 0 until numDataPoints) {
                    writer.append("$x,") // Use x as sequential identifier matching chart's X-axis

                    // Flex Sensors - Safely get value or 0f if index out of bounds
                    for (i in 0 until 5) {
                        // _flexEntriesInternal[i] contains Entry objects. We need their Y values.
                        // Ensure we don't go out of bounds for the specific sensor's list if it's shorter
                        val value = _flexEntriesInternal[i].getOrNull(x)?.y ?: 0f
                        writer.append("$value")
                        writer.append(",")
                    }

                    // FSR Sensors - Safely get value or 0f if index out of bounds
                    for (i in 0 until 5) {
                        val value = _fsrEntriesInternal[i].getOrNull(x)?.y ?: 0f
                        writer.append("$value")
                        writer.append(",")
                    }

                    // IMU and BioAmp Sensors - Safely get value or 0f if index out of bounds
                    for (i in 0 until 7) {
                        val value = _imuBioEntriesInternal[i].getOrNull(x)?.y ?: 0f
                        writer.append("$value")
                        if (i < 6) writer.append(",") // No comma after the last IMU/BioAmp value
                    }
                    writer.append("\n")
                }
            }
            Log.d(TAG, "CSV file written successfully to: ${csvFile.absolutePath}")
            return csvFile
        } catch (e: Exception) {
            Log.e(TAG, "Error writing CSV file: ${e.message}", e)
            return null
        }
    }

    private suspend fun uploadToFirebase(csvFile: File): String {
        return withContext(Dispatchers.IO) {
            try {
                // Import Firebase Storage (add this import at the top of the file)
                // import com.google.firebase.storage.FirebaseStorage

                val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
                val storageRef = storage.reference
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "sessions/session_${timestamp}.csv"
                val fileRef = storageRef.child(fileName)

                val uploadTask = fileRef.putFile(Uri.fromFile(csvFile))
                uploadTask.await()

                Log.d(TAG, "CSV file uploaded successfully: $fileName")
                return@withContext fileName
            } catch (e: Exception) {
                Log.e(TAG, "Firebase upload failed", e)
                throw e
            }
        }
    }

    private suspend fun waitForProcessingResult(docId: String) {
        val docRef = Firebase.firestore.collection("processingResults").document(docId)

        // Wait for the result document to be created by the cloud function
        var attempts = 0
        while (attempts < 60) { // 60 attempts with 5s delay = 5 minutes timeout
            delay(5000)
            
            try {
                val snapshot = docRef.get().await()
                if (snapshot.exists()) {
                    val status = snapshot.getString("status")
                    
                    when (status) {
                        "completed" -> {
                            val result = snapshot.getString("result") ?: "No result available"
                            mainHandler.post {
                                resultText = result
                                isProcessingResult = false
                                navigateToResultScreen()
                            }
                            return
                        }
                        "error" -> {
                            val errorResult = snapshot.getString("result") ?: "Processing failed"
                            mainHandler.post {
                                resultText = errorResult
                                isProcessingResult = false
                                navigateToResultScreen()
                            }
                            return
                        }
                        "processing" -> {
                            mainHandler.post {
                                resultText = "AI algorithm processing... ${attempts * 5}s elapsed"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking processing result: ${e.message}")
            }
            
            attempts++
        }
        
        // Timeout
        mainHandler.post {
            resultText = "Processing timeout - please try again later"
            isProcessingResult = false
            navigateToResultScreen()
        }
    }

}
