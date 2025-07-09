package com.example.gloveapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.* // Contains remember, mutableStateOf, LaunchedEffect, DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gloveapp.auth.AppUser
import com.example.gloveapp.auth.AuthViewModel
import com.example.gloveapp.auth.FirestoreCollections
import com.example.gloveapp.auth.PatientRequest
import com.example.gloveapp.auth.Session
import com.example.gloveapp.ui.theme.GloveAppTheme
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QueryDocumentSnapshot

// Data class for patient session results
data class PatientSessionResult(
    val sessionId: String,
    val patientUid: String,
    val patientEmail: String,
    val patientName: String,
    val status: String, // "processing", "completed", "error"
    val result: String?, // AI analysis result
    val requestedAt: com.google.firebase.Timestamp?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorInterface(
    onToggleTheme: () -> Unit,
    isDarkTheme: Boolean,
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit
) {
    val firestore = FirebaseFirestore.getInstance()
    val coroutineScope = rememberCoroutineScope()
    var pendingRequests by remember { mutableStateOf<List<Pair<AppUser, PatientRequest>>>(emptyList()) }
    var approvedPatients by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var selectedPatient by remember { mutableStateOf<AppUser?>(null) }
    var sessions by remember { mutableStateOf<List<Session>>(emptyList()) }
    var patientSessionResults by remember { mutableStateOf<List<PatientSessionResult>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Pending Requests", "Patients")
    val context = LocalContext.current

    // Using DisposableEffect for listeners that require cleanup based on key changes
    DisposableEffect(authViewModel.currentUser.value) {
        val currentUser = authViewModel.currentUser.value
        var pendingRequestsListener: ListenerRegistration? = null
        var approvedPatientsListener: ListenerRegistration? = null

        if (currentUser != null) {
            // Listener for pending patient requests
            pendingRequestsListener = firestore.collection(FirestoreCollections.PATIENT_REQUESTS)
                .whereEqualTo("doctorEmail", currentUser.email) // Use doctorEmail from PatientRequest
                .whereEqualTo("status", "pending")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        error = "Listen failed: ${e.message}"
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        coroutineScope.launch {
                            val requests = snapshot.toObjects(PatientRequest::class.java)
                            val newPending = requests.mapNotNull { request ->
                                val userDoc = firestore.collection(FirestoreCollections.USERS)
                                    .document(request.patientUid)
                                    .get()
                                    .await()
                                userDoc.toObject(AppUser::class.java)?.let { user ->
                                    Pair(user, request)
                                }
                            }
                            pendingRequests = newPending
                        }
                    } else {
                        pendingRequests = emptyList()
                    }
                }

            // Listener for approved patients
            approvedPatientsListener = firestore.collection(FirestoreCollections.USERS)
                .whereEqualTo("doctorUsername", currentUser.email)
                .whereEqualTo("isApproved", true)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        error = "Listen failed: ${e.message}"
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        approvedPatients = snapshot.toObjects(AppUser::class.java)
                    } else {
                        approvedPatients = emptyList()
                    }
                }
        } else {
            // If currentUser is null, clear all lists
            pendingRequests = emptyList()
            approvedPatients = emptyList()
            selectedPatient = null
            sessions = emptyList()
            patientSessionResults = emptyList()
        }

        // Cleanup block: remove snapshot listeners when the effect leaves the composition
        onDispose {
            pendingRequestsListener?.remove()
            approvedPatientsListener?.remove()
        }
    }

    DisposableEffect(selectedPatient) {
        var sessionsListener: ListenerRegistration? = null
        var patientSessionResultsListener: ListenerRegistration? = null
        val patient = selectedPatient

        if (patient != null) {
            // Listener for selected patient's sessions
            sessionsListener = firestore.collection(FirestoreCollections.USERS)
                .document(patient.uid)
                .collection("sessions")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        error = "Listen failed: ${e.message}"
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        sessions = snapshot.documents.mapNotNull { doc ->
                            Session(
                                id = doc.id,
                                timestamp = doc.getString("timestamp") ?: "N/A",
                                graspData = doc.get("graspData") as? List<Float> ?: emptyList(),
                                releaseData = doc.get("releaseData") as? List<Float> ?: emptyList(),
                                duration = doc.getLong("duration") ?: 0L
                            )
                        }
                    } else {
                        sessions = emptyList()
                    }
                }

            // Listener for patient session results
            patientSessionResultsListener =
                firestore.collection("processingResults")
                    .whereEqualTo("patientUid", patient.uid)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            error = "Listen failed: ${e.message}"
                            return@addSnapshotListener
                        }
                        if (snapshot != null && !snapshot.isEmpty) {
                            patientSessionResults = snapshot.documents.mapNotNull { doc ->
                                PatientSessionResult(
                                    sessionId = doc.id,
                                    patientUid = doc.getString("patientUid") ?: "",
                                    patientEmail = patient.email,
                                    patientName = patient.name ?: "",
                                    status = doc.getString("status") ?: "unknown",
                                    result = doc.getString("result"),
                                    requestedAt = doc.getTimestamp("requestedAt")
                                )
                            }
                        } else {
                            patientSessionResults = emptyList()
                        }
                    }
        } else {
            sessions = emptyList() // Clear sessions if no patient is selected
            patientSessionResults = emptyList()
        }

        // Cleanup block: remove session listener when selectedPatient changes or effect leaves
        onDispose {
            sessionsListener?.remove()
            patientSessionResultsListener?.remove()
        }
    }

    GloveAppTheme(darkTheme = isDarkTheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Doctor Dashboard", color = MaterialTheme.colorScheme.onPrimary) },
                    actions = {
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedPatient == null) {
                    TabRow(selectedTabIndex = selectedTab) {
                        tabTitles.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title) }
                            )
                        }
                    }
                    when (selectedTab) {
                        0 -> PendingRequestsTab(
                            pendingRequests = pendingRequests,
                            onApprove = { patientUid ->
                                authViewModel.approvePatientRequest(
                                    patientUid = patientUid,
                                    onSuccess = {
                                        Toast.makeText(context, "Patient approved!", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        error = err
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            onReject = { patientUid ->
                                authViewModel.rejectPatientRequest(
                                    patientUid = patientUid,
                                    onSuccess = {
                                        Toast.makeText(context, "Patient request rejected.", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        error = err
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        )
                        1 -> PatientsTab(
                            patients = approvedPatients,
                            onPatientClick = { patient ->
                                selectedPatient = patient
                            }
                        )
                    }
                } else {
                    PatientDetailsScreen(
                        patient = selectedPatient!!,
                        sessions = sessions,
                        patientSessionResults = patientSessionResults,
                        onBack = { selectedPatient = null }
                    )
                }

                error?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun PendingRequestsTab(
    pendingRequests: List<Pair<AppUser, PatientRequest>>,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    if (pendingRequests.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No pending requests.",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(pendingRequests, key = { it.first.uid }) { (user, request) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = "Patient: ${user.name ?: "Unknown"}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Email: ${user.email}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { onApprove(user.uid) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Approve")
                            }
                            Button(
                                onClick = { onReject(user.uid) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Reject")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PatientsTab(
    patients: List<AppUser>,
    onPatientClick: (AppUser) -> Unit
) {
    if (patients.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No approved patients.",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(patients, key = { it.uid }) { patient ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPatientClick(patient) },
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = patient.name ?: "Unknown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = patient.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PatientDetailsScreen(
    patient: AppUser,
    sessions: List<Session>,
    patientSessionResults: List<PatientSessionResult>,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Patient: ${patient.name ?: "Unknown"}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Email: ${patient.email}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Session History",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (sessions.isEmpty()) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(200.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "No sessions recorded for this patient.",
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(sessions, key = { it.id }) { session ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = "Session: ${session.timestamp}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Duration: ${session.duration / 1000} seconds",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Grasp Data Points: ${session.graspData.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Release Data Points: ${session.releaseData.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Session Results",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (patientSessionResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No session results available.",
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(patientSessionResults, key = { it.sessionId }) { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = "Session ID: ${result.sessionId}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Status: ${result.status}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Result: ${result.result ?: "N/A"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("Back to Patients")
        }
    }
}
