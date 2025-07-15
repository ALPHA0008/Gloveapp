# GloveApp

GloveApp is an Android application designed to interface with a wearable smart glove via Bluetooth, visualize real-time sensor data, and leverage cloud AI for session result processing.

## Features

- BLE device scanning and connection
- Real-time charting of glove sensor data (Flex, FSR, IMU, Bio-Amp)
- Patient and Admin login with different dashboards
- Session management with timers
- Save, upload, and cloud-processing of sensor data (Firebase integration)
- Theme switch (Dark/Light mode)
- Doctor/Patient/Administrator roles
- Result screen with AI-powered analysis

## Getting Started

### Prerequisites

- Android Studio (Giraffe or newer recommended)
- Android device with Bluetooth support (API 23+)

### Building & Running

1. Clone the repository:
    ```sh
    git clone https://github.com/ALPHA0008/Gloveapp.git
    cd Gloveapp
    ```

2. Open the project in Android Studio.

3. Configure Firebase:
    - Add your `google-services.json` file to `app/`.

4. Build and run the app on your device.

### Note

- Ensure Bluetooth and Location services are enabled on your device.
- The app will prompt for runtime Bluetooth and Location permissions.

## Dependencies

- [Firebase Firestore & Storage](https://firebase.google.com/)
- [Coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)

Check `build.gradle` for the full list.

## Contributing

Pull requests are welcome! For major changes, please open an issue first to discuss what you would like to change.
