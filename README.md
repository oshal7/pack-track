# PackTrack — Motorcycle Group Tracker

A zero-cost Android app for keeping your motorcycle pack together on the road.

**Direct APK Download (always latest):**  
https://github.com/oshal7/pack-track/releases/download/latest/

---

## Features
- **Create / Join Ride** with a 4-digit PIN — no accounts needed
- **Live map** showing all riders (MapLibre + OpenStreetMap, completely free)
- **Auto-HALTED detection** — 90 seconds stationary → amber status shown to everyone
- **Glove-friendly Nudge** — 3 oversized buttons: Emergency Stop, Regroup, Acknowledged
- **Background GPS service** — tracks while screen is off
- **Offline caching** — stores GPS in SQLite during dead zones, syncs when back online
- **Auto-versioning** — every GitHub push builds a new APK; installing it updates (not replaces) the existing app

---

## How to Install (First Time)
1. Go to Releases → Latest on GitHub
2. Download the PackTrack-vX.X.X.apk file on your phone
3. Tap it → "Install from unknown sources" → Install
4. For updates: just download + install the new APK → Android auto-updates in place

---

## Firebase Setup (Required for Real-Time Location Sharing)

Without Firebase, the app installs and runs but riders cannot see each other on the map. Setup takes ~5 minutes:

### Step 1 — Create Firebase project
1. Go to console.firebase.google.com
2. Click Add project → name it packtrack
3. Disable Google Analytics → Create project

### Step 2 — Add Android app
1. Click the Android icon → Package name: com.packtrack.app
2. Download google-services.json

### Step 3 — Enable Firestore
1. In Firebase Console → Firestore Database → Create database
2. Choose Start in test mode (fine for personal use)
3. Pick any region

### Step 4 — Add as GitHub Secret
1. Copy the entire content of your google-services.json
2. Go to your GitHub repo → Settings → Secrets and variables → Actions
3. Add secret named GOOGLE_SERVICES_JSON → paste the full JSON content
4. Push any change to main to trigger a new build with real Firebase

---

## Update-in-Place Setup (same signing key = updates instead of reinstall)

For Android to update instead of reinstall, every build must use the same signing key.

### One-time keystore generation (run on any computer with Java installed):

```
keytool -genkey -v -keystore packtrack.jks -alias packtrack -keyalg RSA -keysize 2048 -validity 10000 -storepass YourStorePass -keypass YourKeyPass -dname "CN=PackTrack, O=PackTrack, C=IN"

base64 packtrack.jks | tr -d '\n' > packtrack.jks.b64
```

Then add these 4 GitHub Secrets (Settings → Secrets → Actions):

| Secret Name            | Value                             |
|------------------------|-----------------------------------|
| SIGNING_KEY            | Contents of packtrack.jks.b64     |
| SIGNING_KEY_ALIAS      | packtrack                         |
| SIGNING_STORE_PASSWORD | YourStorePass                     |
| SIGNING_KEY_PASSWORD   | YourKeyPass                       |

After this every build uses the same key and update-in-place works perfectly.

Without this: each build still works but you may need to uninstall before installing a build signed with a different debug key.

---

## Tech Stack (100% Free, Zero Cost)

| Component        | Technology                          |
|------------------|-------------------------------------|
| Maps             | MapLibre Native Android SDK         |
| Map Tiles        | OpenStreetMap                       |
| Routing          | OSRM Public Demo Server             |
| Geocoding        | Nominatim                           |
| Real-time backend| Firebase Firestore (Spark free tier)|
| Offline cache    | Room / SQLite                       |
| Build / CI       | GitHub Actions                      |

---

## Versioning

- versionCode = GitHub Actions run number (auto-increments every build)
- versionName = 1.0.{run_number} (can override via workflow_dispatch)
- Every push to main creates a new APK and updates the "latest" release tag
- The direct download link always points to the newest APK
