# 🏠 WaveHome — Adaptive Smart Home Automation on Android

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-SDK%2029--35-green.svg?logo=android)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material3-4285F4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean%20%2B%20UDF-orange.svg)]()
[![ML On-Device](https://img.shields.io/badge/ML-TensorFlow%20Lite%20(LSTM)-FF6F00.svg?logo=tensorflow)](https://www.tensorflow.org/lite)

> **WaveHome** is an engineering prototype of a context-aware, adaptive smart home automation system running on Android. The application acts as an intelligent sensory hub: it aggregates multi-modal context (indoor location via RF fingerprinting, activity recognition, sleep, weather, and real-time smart home device states), processes it locally on-device, and predicts automation decisions without compromising user privacy.

---

## 📸 Overview & Key Capabilities

- **Declarative UI**: Built 100% with **Jetpack Compose** and **Material 3 Expressive**, providing a responsive dashboard for manual control, live diagnostics, and automation telemetry.
- **Robust Background Processing**: Orchestrated via Android **WorkManager** with chained periodic workers (`SmartHomeSyncWorker`) and boot recovery (`BootReceiver`).
- **Multi-Modal Context Collection**:
  - **Indoor Positioning Engine**: Room-level estimation using dual-band Wi-Fi (`BSSID`, RSSI) and Bluetooth Low Energy (`BLE`) signal fingerprinting with custom Euclidean distance calculation and overlap penalty.
  - **Activity Recognition**: Uses Google Play Services `Activity Recognition Transition API` for battery-efficient state detection (`STILL`, `WALKING`, `IN_VEHICLE`).
  - **Home Presence**: Fused geofencing (`FusedLocationProviderClient`) combined with home Wi-Fi SSID verification.
  - **Health & Sleep Data**: Sleep segment aggregation and normalization from Google Fit / Health Connect.
  - **External Context**: Current weather conditions via REST API integration.
- **Google Home & Matter Integration**: Communicates directly with Google Home API (`HomeClient`) using standard traits (`OnOff`) across lighting, switches, and media devices.
- **Hybrid On-Device Machine Learning**:
  - **LSTM Sequential Model**: Evaluates temporal dependencies across a rolling window of 12 context snapshots (11 normalized features each, total 132-dimension vector) via **TensorFlow Lite**.
  - **Local Adaptive Centroid Classifier**: Lightweight on-device personalization engine (`LocalSequenceTrainer`) that adapts to unique user habits without expensive cloud model re-training.
  - **Safety User Overrides**: Immediate rule-based priority mechanism preventing the automated system from overriding active manual user decisions.
- **Local Persistence**: **Room Database** with relational schema (`1:N` cascade relations between time logs and device states), type converters, and indexing.

---

## 🏛️ System Architecture

WaveHome follows **Clean Architecture** principles with clear separation of concerns, defensive programming, and **Unidirectional Data Flow (UDF)**:

- **Sensory & Context Layer**: `SmartHomeContextCollector` queries location, RF fingerprints, activity, sleep, weather, and active Google Home device states.
- **Feature Pipeline**: `SmartHomeFeatureBuilder` normalizes incoming readings into a structured 11-feature snapshot vector.
- **Decision Engine**: `SmartHomePredictor` applies a 3-tier hierarchy:
  1. *User Overrides* (Highest priority — respect active manual adjustments).
  2. *Local Centroid Model* (Adaptive clustering trained on device).
  3. *Global TFLite LSTM* (Deep sequential prediction).
- **Actuation Layer**: `HomeController` maps discrete output classes to target `SmartHomeAction` commands and dispatches them via Google Home API.
- **Persistence Layer**: `AppDatabase` stores time-series logs (`TimeLog`), individual device states (`TimeLogDeviceState`), and room profiles (`RoomFingerprintEntity`).

---

## 🛠️ Tech Stack & Android Libraries

| Category | Technology |
|---|---|
| **Language** | **Kotlin 2.0+** (Coroutines, Flow, StateFlow, Serialization) |
| **UI Framework** | **Jetpack Compose**, Material 3, Navigation Compose |
| **Architecture** | **MVVM / Clean Architecture**, Unidirectional Data Flow |
| **Persistence** | **Room 2.6+** (Foreign Keys, Indexes, Type Converters, In-Memory Caching) |
| **Background Work** | **WorkManager**, BroadcastReceivers (`BOOT_COMPLETED`, Activity Transitions) |
| **Hardware & Sensors** | `WifiManager` (Scanning), `BluetoothAdapter` (BLE discovery), `FusedLocationProviderClient` |
| **Google APIs** | **Google Home API (Matter)**, **Google Fit / Health Connect**, Activity Recognition Transition API |
| **Machine Learning** | **TensorFlow Lite / LiteRT** (LSTM on-device inference), Custom Centroid Clustering |
| **Networking** | REST API client, JSON serialization |
| **Build System** | Gradle Kotlin DSL (`build.gradle.kts`), Version Catalogs |

---

## 🗄️ Relational Database Model (Room)

The application utilizes a normalized SQLite database via Room (`time_log.db`):
- `time_logs`: Master timeline storing raw snapshot features, normalized ML vectors, and prediction outcomes.
- `time_log_device_states`: Child entity with `1:N` relationship linked by foreign key (`CASCADE` on delete) recording states of individual smart appliances.
- `room_fingerprints` & `room_detections`: RF fingerprint profiles for calibrated rooms and detection history with confidence scoring.
- `user_overrides`: Ephemeral and persistent manual interventions with TTL to safeguard user autonomy.

---

## 🧪 Research & Thesis Metrics

Developed as an M.Sc. thesis project at the **Faculty of Mathematics, Physics and Computer Science, Maria Curie-Skłodowska University (UMCS)** in Lublin.

- **Collected Context Records**: `10,440` periodic logs
- **Recorded Smart Device States**: `26,624` device state events
- **Logged Physical Activities**: `2,047` transition events
- **Empirical Prediction Alignment**: `95.3%` consistency against observed system states

---

## 🚀 Setup & Getting Started

### Prerequisites
- **Android Studio Ladybug | 2024.2+** or newer
- **JDK 17** or **JDK 21**
- Physical Android device running **Android 10+ (API 29)** to **Android 15/16 (API 35)** with Wi-Fi, Bluetooth, and Google Play Services enabled.

### Configuration
1. Clone the repository:
   ```bash
   git clone https://github.com/WhiteBitFox/WaveHome.git
   ```
2. Create a `local.properties` file in the project root:
   ```properties
   sdk.dir=/path/to/your/android/sdk
   OPEN_WEATHER_API_KEY="your_openweather_api_key"
   GOOGLE_MAPS_API_KEY="your_google_maps_api_key"
   ```
3. Sync Gradle and build the project:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 👨‍💻 Author

**Adrian Krzeszowski**  
*M.Sc. in Computer Science | Android & Kotlin Developer*  
- **GitHub**: [@WhiteBitFox](https://github.com/WhiteBitFox)  
- **LinkedIn**: [adrian-krzeszowski](https://www.linkedin.com/in/adrian-krzeszowski-755148329/)  
- **Email**: [adryyian@gmail.com](mailto:adryyian@gmail.com)
