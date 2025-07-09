import org.gradle.api.artifacts.dsl.DependencyHandler

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Add the Compose Compiler plugin as required by Kotlin 2.0+
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}
android {
    namespace = "com.example.gloveapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.gloveapp"
        minSdk = 23
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Enable Jetpack Compose
    buildFeatures {
        compose = true
    }

    composeOptions {
        // This is the version of the Compose Compiler Extension
        // The Compose Compiler Plugin version will be tied to your Kotlin version
        kotlinCompilerExtensionVersion = "1.5.0" // Match your Compose version
    }

    // Kotlin compiler options
    kotlinOptions {
        jvmTarget = "1.8" // Ensure compatibility
    }

    // Add the repositories block within the android block
    // This is added here as a fallback, but ideally, all repositories are in settings.gradle.kts

}

dependencies {
    // Core Android libraries
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.5.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0") // Check your current lifecycle version, use the latest stable
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("com.github.PhilJay:MPAndroidChart:3.1.0")
    implementation("androidx.compose.material:material-icons-core:1.6.8") // Example version, check for latest
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")


    implementation ("androidx.compose.ui:ui:1.4.3")
    implementation ("androidx.compose.material:material:1.4.3")
    implementation ("androidx.navigation:navigation-compose:2.5.3")
    implementation ("com.google.accompanist:accompanist-pager:0.30.1")   // For onboarding pager
    implementation ("com.google.accompanist:accompanist-systemuicontroller:0.30.1")

    implementation ("androidx.datastore:datastore-preferences:1.0.0")

    implementation ("androidx.compose.runtime:runtime-livedata:1.6.7")
    implementation("com.google.firebase:firebase-messaging")

    // For network requests (HTTP client)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // For parsing JSON responses
    implementation("org.json:json:20231013")





    // BLE dependencies
    implementation("com.polidea.rxandroidble2:rxandroidble:1.11.1")

    // Jetpack Compose dependencies
    implementation("androidx.compose.ui:ui:1.5.0")
    //  implementation("androidx.compose.material3:material3:1.1.0") // Material Design 3
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.0")
    implementation(libs.androidx.material3.android)
    implementation(libs.firebase.messaging.ktx)
    debugImplementation("androidx.compose.ui:ui-tooling:1.5.0")
    implementation("androidx.activity:activity-compose:1.8.0")

    //google fonts
    implementation("androidx.compose.ui:ui-text-google-fonts:1.5.0")
}
