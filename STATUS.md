# SyncRow Development Status

This document provides a detailed checklist of the project's status, broken down by feature phase and architectural layer.

---

## Phase 1: The "Just Row" MVP (COMPLETE)

### 1. System Architecture
- [x] **Hardware Abstraction Layer (HAL)**
  - [x] Define `IRowingMachine` interface.
  - [x] Define `RowerMetrics` data class.
  - [x] ~~Implement temporary `MockRower` for UI testing.~~ (Removed: Production hardware only)
  - [x] Implement `FtmsRowingMachine` (Skandika Styrke II compatible).
  - [x] Implement `BleHeartRateMonitor`.
- [x] **Storage & Profile Layer**
  - [x] Define Room DB Schema (`UserTable`, `WorkoutTable`, `MetricPointTable`).
  - [x] Implement DAOs for data access.
  - [x] Auto-recording of per-second metrics to DB.
- [x] **Integration Layer**
  - [x] Implement `TcxExporter` for file sharing via Android FileProvider.

### 2. UI Layer (Jetpack Compose)
- [x] **Navigation & Shell**
  - [x] Implement NavGraph for screen transitions.
  - [x] Create Home Screen with action buttons.
- [x] **ViewModel**
  - [x] Create `WorkoutViewModel`.
  - [x] Implement `DataSmoother` for Power, Pace, and Heart Rate (filtering 0s).
  - [x] Session state logic (Idle, Rowing, Paused).
- [x] **Hardware Discovery**
  - [x] Real-time BLE scanning for FTMS and HRM.
  - [x] Device filtering (Rower vs HRM) and selection logic.
  - [x] Persistent device selection per user profile.
- [x] **Workout Screen**
  - [x] High-contrast real-time metric display.
  - [x] Orientation-aware responsive layout (Portrait/Landscape).
  - [x] Persistent Heart Rate display (pre and post workout).
- [x] **User Management**
  - [x] Profile Switcher UI.
  - [x] Support for multiple user profiles.
  - [x] Per-user language preferences (EN, FR, DE, ES, IT).

---

## Phase 2: User Personalization & Strava Sync (COMPLETE)

### 1. Activity History
- [x] **Workout History List**
  - [x] List past workouts from DB with summary cards.
  - [x] Long-press to delete (Selection mode).
  - [x] Visual indicators for Strava sync status.
- [x] **Workout Detail View**
  - [x] Full summary statistics.
  - [x] TCX Export trigger.
  - [x] Manual/Re-sync to Strava.

### 2. Platform Integration
- [x] **Strava OAuth2 Flow**
  - [x] Custom URI scheme (`syncrow://strava-auth`) for clean redirects.
  - [x] Token persistence and auto-refresh logic.
  - [x] Disconnect functionality in Profile.
- [x] **Activity Upload**
  - [x] Upload workouts as `VirtualRow` (sport_type).
  - [x] Include Power (Watts), Cadence (SPM), and Heart Rate data.
  - [x] Server-side de-duplication handling (409 Conflict).

### 3. Presence
- [x] **Project Homepage**
  - [x] Modern single-page landing at `web/public`.
  - [x] Optimized for Firebase Hosting.

---

## Phase 3: Workout and Training Features (IN PROGRESS)

- [ ] **Training Module**
  - [ ] Training editor for designing rowing intervals.
  - [ ] Pre-defined training sessions (Foundation, SPM Ladder, The Pyramid, etc.).
  - [ ] See `Workouts.md` for full technical definitions and goals.
- [ ] **Real-time Coaching**
  - [ ] Target gauges for SPM and Watts during intervals.
  - [ ] Audible/Visual prompts for posture and intensity changes.
- [ ] **Advanced Analysis**
  - [ ] Personal Bests tracking (`personal_bests` table).
  - [ ] Row against "ghosts" from workout history.
