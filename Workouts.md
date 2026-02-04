# Workout Selection

## 1. Beginner: "The Foundation" (Technique & Consistency)
*This workout is designed to get the user comfortable with maintaining a steady SPM regardless of their effort.*

*   **Goal:** Build aerobic base and stroke consistency.

### Structure
*   **Warm-up:** 3 mins @ 18-20 SPM (Very Light).
*   **Main Set:** 15 mins steady rowing @ 20-22 SPM.
    *   **Focus:** Every 3 minutes, the app prompts the user to check their posture.
*   **Cool-down:** 2 mins @ 18 SPM.

---

## 2. Beginner: "SPM Ladder" (Introduction to Rate Changes)
*Teaches the user how to change speed by adjusting the stroke rate, which is a core rowing skill.*

*   **Goal:** Learn "Gears" (shifting intensity).

### Structure
*   **Warm-up:** 5 mins ramping from 18 to 22 SPM.
*   **Intervals:** 3 rounds of:
    *   2 mins @ 20 SPM (Easy).
    *   2 mins @ 22 SPM (Moderate).
    *   1 min @ 24 SPM (Challenging).
    *   1 min Rest (Full stop or "paddle" at 16 SPM).
*   **Cool-down:** 3 mins @ 18 SPM.

---

## 3. Intermediate: "The Pyramid" (Intensity Management)
*This uses your app's ability to track total time and power. It challenges the user to push hard and then recover while still moving.*

*   **Goal:** Anaerobic threshold and recovery under load.

### Structure
*   **Warm-up:** 5 mins steady.
*   **Main Set:**
    *   1 min @ 24 SPM (High Intensity).
    *   2 mins @ 26 SPM.
    *   3 mins @ 28 SPM.
    *   2 mins @ 26 SPM.
    *   1 min @ 24 SPM.
*   **Rest:** 90 seconds of light rowing between each step.
*   **Cool-down:** 5 mins light.

---

## 4. Intermediate: "Power 10s" (Wattage Focus)
*This session specifically utilizes the Watts data from your Skandika.*

*   **Goal:** Explosive power and leg drive.

### Structure
*   **Warm-up:** 10 mins @ 20 SPM.
*   **Main Set:** 20 mins total. Every 2 minutes, perform 10 "Power Strokes" (Max Watts possible) at 28+ SPM.
*   **Rest:** The remainder of the 2-minute block is "Active Recovery" @ 18 SPM.
*   **Success Metric:** Your app should track the "Peak Watts" of each Power 10 set.

---

## 5. Advanced: "2K Simulator" (The Gold Standard)
*The 2,000m row is the universal benchmark for rowers. This workout prepares them for the "test."*

*   **Goal:** High-intensity endurance and mental toughness.

### Structure
*   **Warm-up:** 10-15 mins including three 30-second "bursts" at race pace.
*   **Main Set:** 4 x 500m Sprints.
    *   **Target:** Race pace (e.g., Target Split 1:55 /500m).
    *   **Rate:** 28-32 SPM.
*   **Rest:** 2 mins static rest between sprints to allow HR to drop.
*   **Cool-down:** 10 mins @ 18 SPM to flush lactic acid.

---

## UI Implementation Tip
Since you are using Jetpack Compose, you can create a "Target Gauge" component. If a workout segment specifies `targetSPM = 24`, your gauge can show the user's live `currentSPM` relative to that target (e.g., turning green when they are ±1 SPM from the goal).

> **Would you like me to show you how to structure the `WorkoutSegment` data class in Kotlin to handle these different targets (Time vs. Distance, SPM vs. Watts)?**

## References
*   [Beginner Rowing Routine](https://www.youtube.com/watch?v=example): This video provides a practical walkthrough of an alternating interval workout that fits the "SPM Ladder" concept perfectly for beginners and experienced rowers alike.
