# SyncRow: Technical Specification & Modular Architecture

## 1. Project Vision
A lightweight, high-performance Android rowing app for the Pixel ecosystem. Designed for the Skandika Styrke II, featuring a modular backend to support multiple users and fitness platforms. Official Homepage: [syncrow.ing](https://syncrow.ing)

## 2. Recent Progress: Phase 2 Complete
We have successfully implemented:
- **Full Strava Integration**: Seamless OAuth2 flow with custom URI redirects (`syncrow://strava-auth`). Workouts are uploaded as `VirtualRow` activities including Watts, SPM, and HR data.
- **Robust Hardware HAL**: Production-ready FTMS and HRM support via RxAndroidBle, with smart smoothing for Power, Pace, and Heart Rate (handling sensor dropouts).
- **Workout History & Management**: Full local database history with detailed summary views, multi-delete selection mode, and TCX export capability.
- **Project Homepage**: A modern landing page located in `/web`, ready for Firebase Hosting deployment.

## 3. System Architecture
- **HAL**: `FtmsRowingMachine` & `BleHeartRateMonitor` providing reactive streams.
- **Persistence**: Room DB recording high-fidelity `MetricPoints` for every second of every workout. Supports multi-user profiles and persistent hardware pairing.
- **UI**: Jetpack Compose architecture with shared `WorkoutViewModel` for unified state management.
- **Web**: Tailwind-based static landing page for project visibility.

## 4. Current Development Status
**STATUS: Phase 1 & 2 COMPLETE. Entering Phase 3 (Training & Coaching).**

See [STATUS.md](./STATUS.md) for the detailed implementation checklist.

### Next Step: Advanced Training Module
- **Interval Workouts**: Implementation of the structured sessions defined in [Workouts.md](./Workouts.md).
- **Target Tracking**: Visual indicators (Gauges) to help users stay within specific SPM or Watt ranges.
- **Ghost Racing**: Ability to select a previous performance and race against it in real-time.

## 5. Future Roadmap

### Phase 3: Coaching & Engagement
- **Workout Builder**: Define custom intervals (e.g., 4 x 500m with 90s rest).
- **Real-time Prompts**: Audio/Visual feedback for technique and intensity changes.
- **Progress Reporting**: Personal Bests tracking and visual performance trends over time.
- **Ghost Racer**: Interactive UI for racing against historical workout data.

## 6. Licensing Notice

SyncRow is licensed under the Business Source License 1.1.
Forks may not be distributed via public application stores
prior to 2029-02-04 without written permission.
