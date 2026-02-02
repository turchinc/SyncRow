# SyncRow: Technical Specification & Modular Architecture

## 1. Project Vision
A lightweight, high-performance Android rowing app for the Pixel ecosystem. Designed for the Skandika Styrke II, featuring a modular backend to support multiple users and fitness platforms. Domain: [syncrow.ing](https://syncrow.ing)

## 2. Recent Progress: Hardware Mastery
We have successfully implemented:
- **Dynamic FTMS Parser**: Robust bit-flag decoding for the 0x2AD1 characteristic. It dynamically calculates offsets to handle Skandika/FS-style payloads (specifically handling the UINT24 distance shift).
- **Bi-Directional Handshake**: Real-time control point commands to wake the machine and start streams.
- **Persistent Hardware Pairing**: MAC addresses for Rower and HRM are now saved per user profile, enabling auto-connection on app start.
- **Responsive UI**: High-contrast dashboard optimized for both Portrait and Landscape (rower mount).

## 3. System Architecture
- **HAL**: `FtmsRowingMachine` & `BleHeartRateMonitor` providing reactive streams via RxAndroidBle.
- **Persistence**: Room DB recording per-second `MetricPoints` for high-fidelity session history.
- **UI**: Jetpack Compose with a shared `WorkoutViewModel` and instant per-profile language switching.

## 4. Current Development Status
**STATUS: Completing Phase 1 (MVP)**

The app is now functionally capable of discovering hardware, connecting, and accurately displaying/recording a live rowing session with persistent user preferences.

### Next Step: Workout History & TCX Export
The priority is now closing the loop on the "Just Row" MVP by allowing users to access their recorded data and share it with external platforms (Strava, Garmin) via FileProvider.
