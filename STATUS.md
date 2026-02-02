# SyncRow Development Status

This document provides a detailed checklist of the project's status, broken down by feature phase and architectural layer.

---

## Phase 1: The "Just Row" MVP

### 1. System Architecture
- [x] **Hardware Abstraction Layer (HAL)**
  - [x] Define `IRowingMachine` interface.
  - [x] Define `RowerMetrics` data class.
  - [x] Implement `MockRower` for UI testing.
  - [x] Implement `FtmsRowingMachine` (Skandika Styrke II compatible).
- [x] **Storage & Profile Layer**
  - [x] Define Room DB Schema (`UserTable`, `WorkoutTable`, `MetricPointTable`).
  - [x] Implement DAOs for data access.
  - [x] Auto-recording of per-second metrics to DB.
- [ ] **Integration Layer**
  - [x] Define `IExporter` interface.
  - [x] Implement `TcxExporter` for file sharing.

### 2. UI Layer (Jetpack Compose)
- [x] **Navigation & Shell**
  - [x] Implement NavGraph for screen transitions.
  - [x] Create Home Screen with action buttons.
- [x] **ViewModel**
  - [x] Create `WorkoutViewModel`.
  - [x] Implement `DataSmoother` for Power and Pace.
  - [x] Session state logic (Idle, Rowing, Paused).
- [x] **Hardware Discovery**
  - [x] Real-time BLE scanning for FTMS and HRM.
  - [x] Device filtering (Rower vs HRM) and selection logic.
  - [x] **Persistent device selection per user profile.**
- [x] **Workout Screen**
  - [x] High-contrast real-time metric display.
  - [x] Orientation-aware responsive layout (Portrait/Landscape).
  - [x] Persistent Heart Rate display (pre and post workout).
- [x] **User Management**
  - [x] Profile Switcher UI.
  - [x] Support for multiple user profiles.
  - [x] **Per-user language preferences (UI language switches immediately).**

### 3. Hardware Handshake
- [x] Implement FTMS Control Point handshake (Request Control + Start).
- [x] **Robust dynamic FTMS parser (verified against Skandika raw bytes).**
- [x] Connection retry logic for GATT 133 errors.

---

## Phase 2: User Personalization & Analysis
- [ ] **Workout History**
  - [x] List past workouts from DB.
  - [x] Detail view with summary statistics.
- [ ] **Data Export**
  - [x] Share TCX/FIT files to other apps via FileProvider.
- [ ] **Auth Flow**
  - [ ] OAuth2 integration for Strava.
