# SyncRow Workout Exchange Format (WEF)

This document defines the JSON structure used for exchanging Training Plans between:
1.  **Cloud Stream** (Firebase "New Workouts").
2.  **Peer-to-Peer Sharing** (Deep Links).
3.  **AI Generation** (Gemini output).

## JSON Structure

The format is a hierarchical JSON object representing a `TrainingPlan`, which contains a list of `TrainingBlock`s, which in turn contain `TrainingSegment`s.

```json
{
  "globalId": "String (UUID, Optional but Recommended)",
  "name": "String (Required)",
  "description": "String (Required)",
  "difficulty": "String (Beginner|Intermediate|Advanced)",
  "intensity": "String (Easy|Medium|Hard)",
  "activityType": "String (ROWING|CYCLING|RUNNING|ELLIPTICAL)",
  "blocks": [
    {
      "orderIndex": "Int (0-based)",
      "name": "String (e.g., 'Warm Up')",
      "repeatCount": "Int (Default: 1)",
      "segments": [
        {
          "orderIndex": "Int (0-based)",
          "segmentType": "String (ACTIVE|RECOVERY|WARMUP|COOLDOWN)",
          "durationType": "String (TIME|DISTANCE)",
          "durationValue": "Int (Seconds or Meters)",
          "targetSpm": "Int (Optional)",
          "targetWatts": "Int (Optional)",
          "targetPace": "Int (Optional, Seconds/500m)",
          "targetHr": "Int (Optional)"
        }
      ]
    }
  ]
}
```

## Example: "5x500m Intervals"

```json
{
  "globalId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "5x500m Power Builder",
  "description": "Classic interval session to build anaerobic power.",
  "difficulty": "Intermediate",
  "intensity": "Hard",
  "activityType": "ROWING",
  "blocks": [
    {
      "orderIndex": 0,
      "name": "Warm Up",
      "repeatCount": 1,
      "segments": [
        {
          "orderIndex": 0,
          "segmentType": "WARMUP",
          "durationType": "TIME",
          "durationValue": 300,
          "targetSpm": 20
        }
      ]
    },
    {
      "orderIndex": 1,
      "name": "Intervals",
      "repeatCount": 5,
      "segments": [
        {
          "orderIndex": 0,
          "segmentType": "ACTIVE",
          "durationType": "DISTANCE",
          "durationValue": 500,
          "targetSpm": 28,
          "targetPace": 120
        },
        {
          "orderIndex": 1,
          "segmentType": "RECOVERY",
          "durationType": "TIME",
          "durationValue": 120
        }
      ]
    },
    {
      "orderIndex": 2,
      "name": "Cool Down",
      "repeatCount": 1,
      "segments": [
        {
          "orderIndex": 0,
          "segmentType": "COOLDOWN",
          "durationType": "TIME",
          "durationValue": 300
        }
      ]
    }
  ]
}
```

## Field Definitions

### Training Plan
| Field | Type | Description |
| :--- | :--- | :--- |
| `globalId` | String | A Unique ID (UUID). Allows deduplication of plans. |
| `name` | String | Title of the workout. |
| `description` | String | Brief summary of the goal. |
| `difficulty` | String | User-facing difficulty level. |
| `intensity` | String | User-facing intensity level. |
| `activityType` | String | Sport type. Defaults to ROWING if missing. |

### Training Block
| Field | Type | Description |
| :--- | :--- | :--- |
| `repeatCount` | Int | Number of times to loop this block (e.g., 5 for 5x500m). |

### Training Segment
| Field | Type | Description |
| :--- | :--- | :--- |
| `segmentType` | Enum | `ACTIVE`: Work interval. `RECOVERY`: Rest interval. |
| `durationType` | Enum | `TIME` (seconds) or `DISTANCE` (meters). |
| `durationValue` | Int | The value for the duration type. |
| `targetPace` | Int | For Rowing: Seconds per 500m. |
