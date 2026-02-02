# SyncRow: Technical Specification & Modular Architecture

## 1. Project Vision
A lightweight, high-performance Android rowing app for the Pixel ecosystem. Designed for the Skandika Styrke II, featuring a modular backend to support multiple users and fitness platforms. Domain: [syncrow.ing](https://syncrow.ing)

## 2. Recent Progress: MVP Completion
We have successfully implemented:
- **Dynamic FTMS Parser**: Robust bit-flag decoding for the 0x2AD1 characteristic. Verified against Skandika/FS hardware, correctly handling UINT24 distance shifts.
- **Persistent Profile System**: Multi-user support with auto-loading of last-used profile and saved hardware (MAC addresses).
- **Localization Engine**: Instant UI language switching (EN, FR, DE, ES, IT) saved per-user.
- **History & Export**: Full workout history with per-second metric recording and TCX file export via Android FileProvider.

## 3. System Architecture
- **HAL**: `FtmsRowingMachine` & `BleHeartRateMonitor` providing reactive streams via RxAndroidBle.
- **Persistence**: Room DB recording high-fidelity `MetricPoints` for every second of every workout.
- **UI**: Jetpack Compose architecture with a shared `AndroidViewModel` for unified session management.

## 4. Current Development Status
**STATUS: Phase 1 (MVP) COMPLETE. Entering Phase 2.**

The app is now a fully functional standalone rowing computer capable of recording and sharing standard fitness data.

### Next Step: Platform Integration & Customization
- **Strava Integration**: OAuth2 flow to upload TCX files directly from the app.
- **Customizable Dashboard**: Allow users to select their primary, large-display metric (e.g., focus on Watts, Pace, or Heart Rate).

## 5. Future Roadmap

### Phase 3: Advanced Coaching & Engagement (Pro Version)
- **Custom Workout Builder**: Define intervals (e.g., 4 x 500m with 90s rest).
- **Flexible Pace Boat / Ghost Racer**:
    - Users can race against a static, predefined pace.
    - Users can select a previous workout from their history to race against as a "ghost," providing a clear measure of progress.
- **Target Tracking**: Visual indicators for staying within specific Watt or SPM ranges during interval training.
- **Zone Training**: Heart rate zone color-coding based on user profile age/max HR.
- **Long-Term Progress Reporting**: Visual charts showing performance trends over time (e.g., "Is my 5000m time improving?").
