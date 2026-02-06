# SyncRow: Technical Specification & Modular Architecture

## 1. Project Vision
A lightweight, high-performance Android rowing app for the Pixel ecosystem. Designed for the Skandika Styrke II, featuring a modular backend to support multiple users and fitness platforms. Official Homepage: [syncrow.ing](https://syncrow.ing)

## 2. Recent Progress: Phase 3 In Progress
We have successfully implemented:
- **Advanced Training Module**: A full-featured training system including a plan editor, pre-loaded sample workouts, and a guided "Do Workout" mode with audible and visual cues.
- **Full Strava Integration**: Seamless OAuth2 flow with custom URI redirects (`syncrow://strava-auth`). Workouts are uploaded as `VirtualRow` activities including Watts, SPM, and HR data.
- **Robust Hardware HAL**: Production-ready FTMS and HRM support via RxAndroidBle, with smart smoothing for Power, Pace, and Heart Rate (handling sensor dropouts).
- **Workout History & Management**: Full local database history with detailed summary views, multi-delete selection mode, and TCX export capability.
- **User-Centric Homepage**: The project homepage has been overhauled with a modern design, screenshot carousel, and user-focused feature descriptions across all supported languages.

## 3. System Architecture
- **HAL**: `FtmsRowingMachine` & `BleHeartRateMonitor` providing reactive streams.
- **Persistence**: Room DB recording high-fidelity `MetricPoints` for every second of every workout. Supports multi-user profiles and persistent hardware pairing.
- **UI**: Jetpack Compose architecture with shared `WorkoutViewModel` for unified state management.
- **Web**: Tailwind-based static landing page for project visibility.

## 4. Current Development Status
**STATUS: Phase 1 & 2 COMPLETE. Phase 3 (Training & Coaching) is nearing completion.**

See [STATUS.md](./STATUS.md) for the detailed implementation checklist.

### Completed Training Module Features
- **Workout Builder**: Define custom intervals (e.g., 4 x 500m with 90s rest) with a powerful editor.
- **Guided Workouts**: A new dashboard guides users through each segment with countdowns and target displays.
- **Pre-loaded Content**: 5 sample workouts are included for immediate use.
- **Audible Cues**: Interval changes are announced with beeps and Text-to-Speech.
- **Automatic Splits**: Each training segment is automatically recorded as a distinct split.
- **Start sound and 3 second countdown**: A countdown provides a clear start to workouts.
- **Manual Split**: A "Save Split" feature for free rows is available.

### Next Steps
- **Ghost Racing**: Ability to select a previous performance and race against it in real-time.
- **Personal Bests**: Track and display personal records for common distances and times.
- **Startup experience**: Implement startup sounds and visuals.

## 5. Future Roadmap

### Phase 3: Coaching & Engagement
- [x] **Workout Builder**: Define custom intervals (e.g., 4 x 500m with 90s rest).
- [x] **Real-time Prompts**: Audio/Visual feedback for technique and intensity changes.
- [ ] **Progress Reporting**: Personal Bests tracking and visual performance trends over time.
- [ ] **Ghost Racer**: Interactive UI for racing against historical workout data.

## 6. Licensing Notice

SyncRow is licensed under the Business Source License 1.1.
Forks may not be distributed via public application stores
prior to 2029-02-04 without written permission.
