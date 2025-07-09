# Gloveapp Information

## Summary
Gloveapp is an Android application designed for interfacing with a Bluetooth-enabled glove device. It provides functionality for data collection, visualization, and analysis through a Bluetooth Low Energy (BLE) connection. The app supports different user roles (admin, patient) and integrates with Firebase for authentication, data storage, and messaging.

## Structure
- **app/**: Main application module
  - **src/main/java/com/example/gloveapp/**: Core application code
    - **auth/**: Authentication-related components
    - **ui/**: UI components and theme definitions
  - **src/main/res/**: Android resources (layouts, strings, drawables)
  - **src/test/**: Unit test files
  - **src/androidTest/**: Instrumentation test files (currently commented out)

## Language & Runtime
**Language**: Kotlin
**Version**: 2.0.21
**Build System**: Gradle (Kotlin DSL)
**Package Manager**: Gradle
**Android SDK**: 
- **Compile SDK**: 34
- **Target SDK**: 33
- **Min SDK**: 23
**JVM Target**: 1.8

## Dependencies
**Main Dependencies**:
- **Jetpack Compose**: UI toolkit (1.5.0)
- **Material3**: Material Design components (1.3.2)
- **Firebase**: Authentication, Firestore, Messaging, Cloud Messaging
- **Bluetooth LE**: RxAndroidBle (1.11.1)
- **Networking**: Retrofit (2.9.0), OkHttp (4.12.0), Gson
- **Charts**: MPAndroidChart (3.1.0)
- **Coroutines**: Kotlin Coroutines (1.7.3)
- **Navigation**: Compose Navigation (2.8.3)
- **DataStore**: Preferences (1.0.0)
- **Accompanist**: Pager (0.30.1), SystemUIController (0.30.1)

**Development Dependencies**:
- JUnit (4.13.2)
- Espresso (3.5.1)
- Compose UI Testing

## Build & Installation
```bash
./gradlew assembleDebug
```
For release build:
```bash
./gradlew assembleRelease
```
To install on a connected device:
```bash
./gradlew installDebug
```

## Main Components
**Entry Point**: MainActivity.kt
**Application Class**: GloveApplication.kt
**Key Features**:
- Bluetooth device scanning and connection
- Real-time data visualization with charts
- User authentication (Firebase)
- Role-based interfaces (Admin/Patient)
- Data collection and analysis
- Push notifications via Firebase Cloud Messaging
- Data persistence with DataStore

## Architecture
- **UI Layer**: Jetpack Compose for declarative UI
- **ViewModel Layer**: AuthViewModel for authentication logic
- **Data Layer**: Firebase Firestore for remote data storage
- **Service Layer**: MessagingService for handling push notifications
- **BLE Communication**: RxAndroidBle for Bluetooth Low Energy interactions

## Permissions
- Bluetooth (scan, connect, advertise)
- Location (for BLE scanning)
- Internet
- Network state
- Notifications

## Testing
**Framework**: JUnit
**Test Location**: src/test/java/com/example/gloveapp/
**Instrumentation Tests**: src/androidTest/java/com/example/gloveapp/ (currently disabled)
**Run Command**:
```bash
./gradlew test
```
For instrumentation tests (when enabled):
```bash
./gradlew connectedAndroidTest
```