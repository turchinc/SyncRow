# VibeRow Development Status

This document provides a detailed checklist of the project's status, broken down by feature phase and architectural layer.

---

## Phase 1: The "Just Row" MVP

### 1. System Architecture
- [x] **Hardware Abstraction Layer (HAL)**
  - [x] Define `IRowingMachine` interface.
  - [x] Define `RowerMetrics` data class.
  - [x] Implement `MockRower` for UI testing.
  - [ ] Implement `SkandikaStyrkeII` for real hardware connection.
- [ ] **Storage & Profile Layer**
  - [ ] Define Room DB Schema (`UserTable`, `WorkoutTable`, `MetricPointTable`).
  - [ ] Implement DAOs for data access.
- [ ] **Integration Layer**
  - [ ] Define `IExporter` interface.
  - [ ] Implement `FileExporter` for `.tcx`/`.fit`.

### 2. UI Layer (Jetpack Compose)
- [x] **ViewModel**
  - [x] Create `WorkoutViewModel`.
  - [x] Implement `DataSmoother` for Power and Pace.
  - [x] Connect ViewModel to `MockRower` data source.
  - [x] Expose `displayMetrics` `StateFlow` for the UI.
- [ ] **Workout Screen**
  - [ ] Create main workout screen Composable.
  - [ ] Design and implement real-time metric display components.
  - [ ] Implement Start/Pause/Stop session controls.
  - [ ] Observe `displayMetrics` from the ViewModel.

### 3. Hardware Handshake
- [ ] Implement BLE scanning and connection logic.
- [ ] Implement FTMS service discovery and characteristic reading.
- [ ] Handle connection lifecycle (disconnects, errors).

---

## Phase 2: User Personalization
- [ ] **UI Layer**
  - [ ] Design and implement Profile Switcher UI.
  - [ ] Create OAuth2 login flow for Strava.
  - [ ] Build local workout history/feed screen.
- [ ] **Storage & Profile Layer**
  - [ ] Implement multi-user CRUD operations.
  - [ ] Securely store API auth tokens.

---

## Phase 3: Advanced Coaching
- [ ] **UI Layer**
  - [ ] Design Custom Workout Builder screen.
  - [ ] Implement visual target-tracking elements in the dashboard.
  - [ ] Add heart rate zone display.
- [ ] **ViewModel**
  - [ ] Add logic for managing custom workout intervals.
  - [ ] Add state for target tracking (e.g., `isWithinTargetRange`).