# Google Fit / Health Connect Integration

Adding "Share to Google Fit" (via the modern **Health Connect** API) is a medium-sized task, likely taking **2-4 days** of development effort depending on how deep you want the integration to be.

## 1. Work Estimation (Integration Effort)

Since you already have a clean architecture with `Workout` and `MetricPoint` data classes and a `TcxExporter`, you have a great head start. You don't need to change your core logic, just add a new "Exporter" or "Syncer".

### Estimated Steps:
1.  **Dependencies**: Add `androidx.health.connect:connect-client` to `app/build.gradle.kts`.
2.  **Permissions**:
    *   Update `AndroidManifest.xml` with `WRITE_HEALTH_DATA` permissions.
    *   Create a dedicated screen or dialog to request these permissions from the user at runtime (Health Connect has its own system UI for this).
3.  **Data Mapping** (The bulk of the work):
    *   Create a `HealthConnectManager`.
    *   Map your `Workout` to an `ExerciseSessionRecord` (Type: `ROWING_MACHINE`).
    *   Map your `MetricPoint` list to Health Connect series data:
        *   `HeartRateRecord` (for HR)
        *   `PowerRecord` (for Watts)
        *   `DistanceRecord` (for Meters)
        *   `SpeedRecord` (Calculated from Pace)
4.  **UI Integration**: Add a "Sync to Google Fit" button in your Workout Detail screen (similar to your Strava integration).

**Complexity:** Moderate. The Health Connect API is strictly typed and modern (Kotlin-first), matching your project style, but testing it requires an emulator with the Health Connect system image or a physical device.

## 2. Play Store Policy (Individual vs. Organization)

**Does this force you to be an "Organization"?**
**No.** You can publish an app with Health Connect integration as an **Individual**.

However, there are stricter requirements for **all** apps using Health Connect:
1.  **Privacy Policy**: Your privacy policy *must* explicitly state that you use Health Connect data and how you handle it.
2.  **App Declaration**: In the Google Play Console, you must complete the "Health Apps" declaration form.
3.  **Developer Verification**: While you don't need to be an Organization, Individual accounts created after November 2023 must run a **Closed Test with 20 testers for 14 days** before they can publish to production. This applies to *any* app you publish, not just because of Google Fit.

### Summary:
*   **Effort:** ~3 days (Coding + Testing).
*   **Account Type:** "Individual" is fine.
*   **Hurdles:** You will face slightly more scrutiny during the Play Store review process (e.g., explaining why you need write access to health data), but it is standard procedure for fitness apps.
