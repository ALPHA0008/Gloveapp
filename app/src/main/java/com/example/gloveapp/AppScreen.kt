package com.example.gloveapp

enum class AppScreen {
    SIGN_IN,
    SIGN_UP,
    ADMIN_SCAN,
    ADMIN_USER_MANUAL,
    ADMIN_GET_READY,
    ADMIN_MAIN,
    ADMIN_RESULT,
    DOCTOR_DASHBOARD,
    DOCTOR_PATIENT_LIST,
    DOCTOR_PATIENT_DETAILS,
    PATIENT_DASHBOARD,
    PATIENT_USER_MANUAL,
    PATIENT_SCAN,      // New: Patient-specific scan screen
    PATIENT_GET_READY, // New: Patient-specific get ready screen
    PATIENT_MAIN,      // New: Patient-specific main data streaming screen
    PATIENT_RESULT
}
