package com.example.gloveapp.auth

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class AppUser(
    @PropertyName("uid") val uid: String = "",
    @PropertyName("email") val email: String = "",
    @PropertyName("role") val role: String = "",
    @PropertyName("doctorUsername") val doctorUsername: String? = null,
    @PropertyName("isApproved") val isApproved: Boolean = false,
    @PropertyName("name") val name: String? = null,
    @PropertyName("fcmToken") val fcmToken: String? = null // Added FCM token field
)

// Updated PatientRequest data class
data class PatientRequest(
    @PropertyName("patientUid") val patientUid: String = "",
    @PropertyName("patientName") val patientName: String = "",
    @PropertyName("patientEmail") val patientEmail: String = "",
    @PropertyName("doctorEmail") val doctorEmail: String = "",
    @PropertyName("status") val status: String = "pending",
    @PropertyName("timestamp") val timestamp: Timestamp? = null,
    var id: String = "" // Added for document ID handling in Firestore queries/updates
)

data class Session(
    val id: String = "", // Document ID
    val timestamp: String = "",
    val graspData: List<Float> = emptyList(),
    val releaseData: List<Float> = emptyList(),
    val duration: Long = 0L
)

enum class UserRole {
    ADMIN, DOCTOR, PATIENT
}

object FirestoreCollections {
    const val USERS = "users"
    const val PATIENT_REQUESTS = "patient_requests"
    const val PATIENT_SESSION_RESULTS = "patient_session_results"
}
