# Firebase & Firestore Configuration

## Data Residency

SyncRow stores all cloud data in **European regions** to comply with GDPR and data protection regulations.

### Setting up Firebase Project

When creating your Firebase project:

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project
3. **IMPORTANT**: When configuring Cloud Firestore, select a European region:
   - Recommended: `europe-west3` (Frankfurt)
   - Alternatives: `europe-west1` (Belgium), `europe-west2` (London)
4. Download `google-services.json` and place it in `app/` directory

### Security Rules

The `firestore.rules` file enforces strict data privacy:

```
- Users can only access their own data
- Authentication is required for all operations
- Security rules enforce `request.auth.uid == userId`
```

Deploy security rules with:

```bash
firebase deploy --only firestore:rules
```

### Data Structure

All Firestore documents include a `syncVersion` field for schema versioning:

- **Users**: Profile data (name, weight, height, preferences)
- **Workouts**: Rowing sessions with splits
- **Training Plans**: Custom workout plans with blocks and segments

The app uses safe parsing with default values when restoring data to handle older schema versions gracefully.

## Anonymous vs Google Auth

- **Anonymous Auth**: Device-only backup (data tied to app installation)
- **Google Sign-In**: Cross-device sync (same data across multiple devices)

Anonymous users can upgrade to Google Sign-In without losing data (the app links accounts automatically).
