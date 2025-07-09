package com.example.gloveapp.auth

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.Timestamp

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    val currentUser = mutableStateOf<AppUser?>(null)
    val authError = mutableStateOf<String?>(null)
    val isLoading = mutableStateOf(false)
    val isAuthLoading = mutableStateOf(true) // New state to track initial auth loading

    init {
        viewModelScope.launch {
            try {
                auth.currentUser?.let { user ->
                    val fetchedUser = fetchUserData(user.uid)
                    currentUser.value = fetchedUser
                    // If a patient was approved while offline, this ensures their status is loaded
                    if (fetchedUser?.role == UserRole.PATIENT.name && fetchedUser.isApproved == false) { // Explicitly check for false
                        authError.value = "Patient account not approved by doctor."
                        auth.signOut()
                        currentUser.value = null
                    }
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Error during initial auth check: ${e.message}")
                authError.value = "Failed to load session."
                auth.signOut() // Ensure no partial session remains
                currentUser.value = null
            } finally {
                isAuthLoading.value = false // Set to false once initial check is complete
            }
        }
    }

    private suspend fun fetchUserData(uid: String): AppUser? {
        return try {
            val userDoc = firestore.collection(FirestoreCollections.USERS).document(uid).get().await()
            val data = userDoc.data // Get the raw data map

            // Robust approval logic:
            // 1. Try to read 'isApproved' first (our current canonical field).
            // 2. If 'isApproved' is null/missing, try to read the legacy 'approved' field.
            // 3. Default to false if neither is explicitly true.
            val isApprovedStatus = (data?.get("isApproved") as? Boolean)
                ?: (data?.get("approved") as? Boolean)
                ?: false // Default to false if neither field exists

            val appUser = AppUser(
                uid = userDoc.id, // Use document ID as UID
                email = data?.get("email") as? String ?: "",
                role = data?.get("role") as? String ?: "",
                doctorUsername = data?.get("doctorUsername") as? String,
                isApproved = isApprovedStatus, // Use the robustly determined status
                name = data?.get("name") as? String,
                fcmToken = data?.get("fcmToken") as? String
            )

            if (appUser != null) {
                Log.d("AuthViewModel", "Fetched user data for ${appUser.uid}: Role=${appUser.role}, isApproved=${appUser.isApproved}")
            } else {
                Log.w("AuthViewModel", "No user data found for UID: $uid")
            }
            appUser
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Error fetching user data for UID $uid: ${e.message}")
            null
        }
    }

    fun signIn(email: String, password: String, onSuccess: (UserRole) -> Unit) {
        isLoading.value = true
        authError.value = null
        viewModelScope.launch {
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                val user = result.user ?: throw Exception("User not found")
                Log.d("AuthViewModel", "Firebase Auth signIn successful for user: ${user.email}")

                val appUser = fetchUserData(user.uid)
                if (appUser != null) {
                    Log.d("AuthViewModel", "Fetched AppUser for sign-in: Role=${appUser.role}, isApproved=${appUser.isApproved}")
                    if (appUser.role == UserRole.PATIENT.name && !appUser.isApproved) {
                        authError.value = "Patient account not approved by doctor."
                        auth.signOut() // Sign out patient if not approved
                        currentUser.value = null
                        Log.d("AuthViewModel", "Patient not approved. Signing out.")
                    } else {
                        currentUser.value = appUser
                        onSuccess(UserRole.valueOf(appUser.role))
                        Log.d("AuthViewModel", "User ${appUser.email} logged in successfully as ${appUser.role}.")
                    }
                } else {
                    authError.value = "User data not found for signed-in user."
                    auth.signOut() // Sign out if user data is missing
                    currentUser.value = null
                    Log.e("AuthViewModel", "User data not found in Firestore after successful Firebase Auth sign-in.")
                }
            } catch (e: Exception) {
                authError.value = e.message
                Log.e("AuthViewModel", "Error during sign-in: ${e.message}")
            } finally {
                isLoading.value = false
            }
        }
    }

    fun signUpDoctor(email: String, password: String, name: String, onSuccess: () -> Unit) {
        isLoading.value = true
        authError.value = null
        viewModelScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user ?: throw Exception("User creation failed")
                Log.d("AuthViewModel", "Firebase Auth Doctor signUp successful for user: ${user.email}")

                val appUser = AppUser(
                    uid = user.uid,
                    email = email,
                    role = UserRole.DOCTOR.name,
                    name = name,
                    isApproved = true // Doctors are automatically approved
                )
                firestore.collection(FirestoreCollections.USERS).document(user.uid).set(appUser).await()
                Log.d("AuthViewModel", "Doctor AppUser created in Firestore for UID: ${user.uid}")
                onSuccess()
            } catch (e: Exception) {
                authError.value = e.message
                Log.e("AuthViewModel", "Error signing up doctor: ${e.message}")
            } finally {
                isLoading.value = false
            }
        }
    }

    fun signUpPatient(email: String, password: String, doctorEmail: String, name: String, onSuccess: () -> Unit) {
        isLoading.value = true
        authError.value = null
        viewModelScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user ?: throw Exception("User creation failed")
                Log.d("AuthViewModel", "Firebase Auth Patient signUp successful for user: ${user.email}")

                val appUser = AppUser(
                    uid = user.uid,
                    email = email,
                    role = UserRole.PATIENT.name,
                    doctorUsername = doctorEmail,
                    isApproved = false, // Patients are initially not approved
                    name = name
                )
                firestore.collection(FirestoreCollections.USERS).document(user.uid).set(appUser).await()
                Log.d("AuthViewModel", "Patient AppUser created with isApproved: false for UID: ${user.uid}")

                val patientRequest = PatientRequest(
                    patientUid = user.uid,
                    patientName = name,
                    patientEmail = email,
                    doctorEmail = doctorEmail,
                    status = "pending",
                    timestamp = Timestamp.now()
                )
                firestore.collection(FirestoreCollections.PATIENT_REQUESTS).add(patientRequest).await()
                Log.d("AuthViewModel", "Patient request created for patient: $name, doctor: $doctorEmail")

                sendNotificationToDoctor(doctorEmail, name, email)

                onSuccess()
            } catch (e: Exception) {
                authError.value = e.message
                Log.e("AuthViewModel", "Error signing up patient: ${e.message}")
            } finally {
                isLoading.value = false
            }
        }
    }

    private suspend fun sendNotificationToDoctor(doctorEmail: String, patientName: String, patientEmail: String) {
        try {
            val doctorDocs = firestore.collection(FirestoreCollections.USERS)
                .whereEqualTo("email", doctorEmail)
                .get()
                .await()

            val doctorUser = doctorDocs.documents.firstOrNull()?.toObject(AppUser::class.java)
            val doctorFCMToken = doctorUser?.fcmToken

            if (doctorFCMToken != null) {
                Log.d("AuthViewModel", "Simulating sending FCM to doctor $doctorEmail with token: $doctorFCMToken")
                // In a real app, this would trigger a backend service to send the push notification.
            } else {
                Log.w("AuthViewModel", "Doctor $doctorEmail not found or has no FCM token to send notification.")
            }
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Error in sendNotificationToDoctor: ${e.message}")
        }
    }

    fun approvePatientRequest(patientUid: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d("AuthViewModel", "Attempting to approve patient with UID: $patientUid")

                // 1. Find and update patient request status to "approved"
                val requestQuery = firestore.collection(FirestoreCollections.PATIENT_REQUESTS)
                    .whereEqualTo("patientUid", patientUid)
                    .whereEqualTo("status", "pending")
                    .get()
                    .await()

                if (requestQuery.isEmpty) {
                    val errorMessage = "Pending request for patient $patientUid not found. It might already be approved/rejected or never existed."
                    Log.e("AuthViewModel", errorMessage)
                    onError(errorMessage)
                    return@launch
                }

                val requestId = requestQuery.documents.first().id
                firestore.collection(FirestoreCollections.PATIENT_REQUESTS)
                    .document(requestId)
                    .update("status", "approved")
                    .await()
                Log.d("AuthViewModel", "Patient request $requestId status updated to 'approved' for UID: $patientUid.")


                // 2. Update patient's AppUser document to set isApproved to true
                firestore.collection(FirestoreCollections.USERS)
                    .document(patientUid)
                    .update("isApproved", true)
                    .await()
                Log.d("AuthViewModel", "Patient AppUser document for UID $patientUid updated: isApproved = true.")

                onSuccess()
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Failed to approve patient"
                Log.e("AuthViewModel", "Critical error approving patient request for UID $patientUid: $errorMessage")
                onError(errorMessage)
            }
        }
    }

    fun rejectPatientRequest(patientUid: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d("AuthViewModel", "Attempting to reject patient with UID: $patientUid")
                val requestQuery = firestore.collection(FirestoreCollections.PATIENT_REQUESTS)
                    .whereEqualTo("patientUid", patientUid)
                    .whereEqualTo("status", "pending")
                    .get()
                    .await()

                if (requestQuery.isEmpty) {
                    val errorMessage = "Pending request for patient $patientUid not found. It might already be approved/rejected or never existed."
                    Log.e("AuthViewModel", errorMessage)
                    onError(errorMessage)
                    return@launch
                }

                val requestId = requestQuery.documents.first().id
                firestore.collection(FirestoreCollections.PATIENT_REQUESTS)
                    .document(requestId)
                    .update("status", "rejected")
                    .await()
                Log.d("AuthViewModel", "Patient request $requestId status updated to 'rejected' for UID: $patientUid.")

                onSuccess()
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Failed to reject patient"
                Log.e("AuthViewModel", "Error rejecting patient request for UID $patientUid: $errorMessage")
                onError(errorMessage)
            }
        }
    }

    fun saveSession(patientUid: String, sessionData: Map<String, Any>, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                firestore.collection(FirestoreCollections.USERS)
                    .document(patientUid)
                    .collection("sessions")
                    .add(sessionData)
                    .await()
                Log.d("AuthViewModel", "Session saved for patient UID: $patientUid")
                onSuccess()
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Failed to save session"
                Log.e("AuthViewModel", "Error saving session for UID $patientUid: $errorMessage")
                onError(errorMessage)
            }
        }
    }

    fun signOut() {
        auth.signOut()
        currentUser.value = null
        Log.d("AuthViewModel", "User signed out. currentUser is now null.")
    }

    fun updateFCMToken(token: String) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                try {
                    firestore.collection(FirestoreCollections.USERS).document(uid)
                        .update("fcmToken", token)
                        .await()
                    Log.d("AuthViewModel", "FCM token updated for user $uid")
                } catch (e: Exception) {
                    Log.e("AuthViewModel", "Error updating FCM token for user $uid: ${e.message}")
                }
            }
        } else {
            Log.w("AuthViewModel", "Cannot update FCM token: No current user logged in.")
        }
    }
}
