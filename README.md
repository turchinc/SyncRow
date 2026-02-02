# VibeRow: Technical Specification & Modular Architecture

## 1. Project Vision
A lightweight, high-performance Android rowing app for the Pixel ecosystem. The goal is to provide a clean, "no-bloat" interface for the Skandika Styrke II, with a modular backend to support multiple users, hardware types, and fitness platforms.

## 2. System Architecture (Modular Design)
The app follows a **Clean Architecture** pattern to ensure hardware and external services are plug-and-play.

### A. Hardware Abstraction Layer (HAL)
Interfaces define the capabilities, allowing for easy expansion:
- **`IRowingMachine`**: Methods for `getPower()`, `getStrokeRate()`, `getDistance()`, `getPace()`.
    - *Impl 1:* `SkandikaStyrkeII` (via Bluetooth FTMS 0x1826).
    - *Impl 2:* `MockRower` (Synthetic data for UI testing on PC).
- **`IHeartRateMonitor`**: Method for `getBPM()`.
    - *Impl:* `StandardBleHRM` (Standard BLE Heart Rate Service 0x180D).

### B. Storage & Profile Layer
- **Multi-User Management**: Local profiles for switching between users (e.g., Husband/Wife).
- **Local Persistence (Room DB)**:
    - `UserTable`: Profile settings, physical metrics (weight/age), and API Auth Tokens.
    - `WorkoutTable`: High-level summaries (Total time, average split, distance, subjective notes).
    - `MetricPointTable`: Timeseries data (per-second recording) for post-row graphing.
    - `PersonalBestTable`: Stores user PRs for key distances (e.g., 500m, 2000m).

### C. Integration Layer (Pluggable Exporters)
Modular export tasks that can be toggled per user:
- **`StravaExporter`**: REST API integration for activity upload.
- **`GarminExporter`**: (Future) Integration via Garmin Connect API.
- **`GoogleFitExporter`**: Native Android Health Connect integration.
- **`FileExporter`**: Generates `.tcx` or `.fit` files for manual sharing.

---

## 3. Core Feature Phases

### Phase 1: The "Just Row" MVP
- **Hardware Handshake**: Reliable scanning and bonding for FTMS and HRM.
- **Dynamic Dashboard**:
    - Real-time display: Watts, 500m Split, Strokes Per Minute (SPM), Heart Rate.
    - Large, high-contrast UI elements for visibility during high-intensity rows.
- **Session Control**: Start, Pause, and a "Confirm & Save" Stop trigger.

### Phase 2: User Personalization & Analysis
- **Profile Switcher**: Fast-toggle on the main screen to change active user.
- **Auth Flow**: OAuth2 integration for Strava per-profile.
- **Detailed Local History**:
    - An activity feed to view previous workouts.
    - A detailed results screen for each workout, including performance graphs (Power, Pace) and **automatic split analysis** (e.g., every 500m).
    - A **workout logbook** feature to add subjective notes.
- **Personal Best (PB/PR) Tracking**: The app will automatically detect and highlight new personal records for standard rowing distances.
- **Customizable Dashboard**: Allow users to select their primary, large-display metric (e.g., focus on Watts, Pace, or Heart Rate).

### Phase 3: Advanced Coaching & Engagement (Pro Version)
- **Custom Workout Builder**: Define intervals (e.g., 4 x 500m with 90s rest).
- **Flexible Pace Boat / Ghost Racer**:
    - Users can race against a static, predefined pace.
    - Users can select a previous workout from their history to race against as a "ghost," providing a clear measure of progress.
- **Target Tracking**: Visual indicators for staying within specific Watt or SPM ranges during interval training.
- **Zone Training**: Heart rate zone color-coding based on user profile age/max HR.
- **Long-Term Progress Reporting**: Visual charts showing performance trends over time (e.g., "Is my 5000m time improving?").

---

## 4. Technical Stack
- **IDE**: Android Studio.
- **Language**: Kotlin.
- **Build System**: Gradle (Kotlin DSL).
- **UI**: Jetpack Compose (for reactive data binding).
- **Bluetooth**: RxAndroidBle (Reactive stream handling).
- **Database**: Room (SQLite).
- **Networking**: Retrofit / OkHttp.

---

## 5. Data Packet Reference (FTMS - 0x2AD1)
| Metric | Byte Offset | Type | Scaling |
| :--- | :--- | :--- | :--- |
| Stroke Rate | 2 | Uint8 | 0.5 |
| Total Distance| 3-5 | Uint24 | 1 |
| Inst. Pace | 6-7 | Uint16 | 1s/500m |
| Inst. Power | 8-9 | Sint16 | 1 Watt |

---

## 6. Development Status
**STATUS: Data Layer Complete**

The foundational data layer for the application is now fully implemented as of the latest update. This includes:
- **Hardware Abstraction Layer (HAL)**: The `IRowingMachine` interface and `RowerMetrics` data class are defined.
- **Mock Data Source**: A `MockRower` class has been created to generate realistic, synthetic data for UI development.
- **Data Smoothing Utility**: A `DataSmoother` class is available to provide moving averages for noisy sensor data.
- **ViewModel**: The `WorkoutViewModel` is in place, connecting the data source to the UI layer and applying the necessary data smoothing.

**Next Step**: Building the Jetpack Compose UI to observe the `displayMetrics` StateFlow from the `WorkoutViewModel`.