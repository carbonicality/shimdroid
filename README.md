# openAnalytics

A standalone **Wear OS** app and tile that show your **Google Analytics 4** stats on your wrist. You build and sideload it yourself. It signs in with the Google account that's already on your watch.

| | |
| --- | --- |
| **Active now** | Realtime active users (last 30 min), with a per-minute sparkline |
| **Today** | Users, change vs. yesterday, sessions, views, new users |
| **Last 7 days** | Users, change vs. the previous 7 days, daily bar chart |
| **Top pages now / 7d** | The most-viewed pages and screens |
| **Top countries · 7d** | Where your users are |
| **Tile** | Active now, plus Today and 7-day users with ▲/▼ |

<p align="center">
  <img src="docs/screenshots/galaxy-watch7-44mm/01-active-now.png" width="200" alt="Active now">
  <img src="docs/screenshots/galaxy-watch7-44mm/02-today.png" width="200" alt="Today">
  <img src="docs/screenshots/galaxy-watch7-44mm/03-last-7-days.png" width="200" alt="Last 7 days">
  <img src="docs/screenshots/galaxy-watch7-44mm/09-tile.png" width="200" alt="Tile">
</p>

These are rendered with sample data at Galaxy Watch 7 44 mm size (480 px). CI re-renders them on every push. The full set, including 40 mm, is in [`docs/screenshots/`](docs/screenshots/).

## How sign-in works

The app uses the Google account on the watch, through Google Play services, and asks for read-only Analytics access (`analytics.readonly`). After you sign in, it lists every GA4 property your account can see, and you pick one on the watch. There are no keys or files to copy.

Google only issues tokens to apps it knows about, so you need to register the app in Google Cloud once. It takes about 5 minutes and you only do it once.

### Google Cloud setup (one-off)

1. Open [Google Cloud Console](https://console.cloud.google.com/) and create a project, or pick an existing one.
2. **APIs & Services → Library**: enable **Google Analytics Data API** and **Google Analytics Admin API**.
3. **Google Auth Platform → Branding / Audience** (the OAuth consent screen):
   - User type **External**, any app name, your email as support contact.
   - Under **Data access**, add the scope `https://www.googleapis.com/auth/analytics.readonly`.
   - Under **Audience**, either:
     - add yourself as a **test user**. In *Testing* mode, Google expires the access after 7 days and you'll have to sign in again; or
     - click **Publish app**. Personal use doesn't need verification. You'll see a "Google hasn't verified this app" warning once; choose *Advanced → Go to openAnalytics*. Access then doesn't expire.
4. **Clients → Create client → Android**:
   - Package name: `dev.openanalytics.wear`
   - SHA-1: `20:67:F3:20:7C:FB:70:08:C7:D9:E5:95:38:02:5B:9D:5E:C4:03:2F`

The app doesn't need a client ID or any secret. Google matches it by package name and SHA-1.

> **About the SHA-1:** every build, local or CI, is signed with `app/openanalytics-debug.keystore`, which is committed to this repo, so the fingerprint above always matches. Because the key is public, anyone could build an APK that installs as an update over yours. That's acceptable for a personal sideloaded app. If you want your own key, replace the keystore, update `signingConfigs` in `app/build.gradle.kts`, and register the new SHA-1. Get it with `keytool -list -v -keystore <file>`.

## Build

- **GitHub Actions:** every push builds `openAnalytics-debug-apk` and the `screenshots` artifact.
- **Locally:** run `./gradlew assembleDebug`. You need JDK 17+ and the Android SDK.

## Install on the watch (Galaxy Watch 7 or any Wear OS 3+ watch)

1. On the watch: go to **Settings → About watch → Software → Software version** and tap it 5 times. Then go to **Settings → Developer options** and turn on **ADB debugging** and **Wireless debugging**.
2. From your computer:
   ```sh
   adb pair <ip>:<pairing-port>     # "Pair new device" under Wireless debugging
   adb connect <ip>:<port>
   adb install -r app-debug.apk
   ```
3. Open **openAnalytics** on the watch, tap **Sign in with Google**, allow access, and pick your property.
4. To add the tile, swipe left from the watch face, then **+ → Analytics**.

Use **Change property** or **Sign out** at the bottom of the app to switch.

## The tile

The tile shows realtime users, plus Today and 7-day users with change indicators, and when it last updated. If it's red and says "offline", it's showing the last saved numbers. Tap the numbers to refresh, or tap **Open** for the full app.

The system refreshes the tile about every 15 min. While the app is open it pushes fresh numbers to the tile. Realtime numbers in the app refresh every 60 s and the rest every 10 min. Nothing polls in the background.

## Troubleshooting

| Message | Fix |
| --- | --- |
| Sign-in rejected (DEVELOPER_ERROR) | The Android OAuth client is missing, or its package name or SHA-1 doesn't match. See step 4. |
| HTTP 403 | Your account lacks Viewer access to the property, or the Data/Admin APIs aren't enabled in the project from step 2. |
| Signed out after a week | The consent screen is still in *Testing*. Publish it (step 3). |
| Network error | The watch has no connection. On Bluetooth-only watches, check that the phone is online. |

## Project layout

```
app/src/main/java/dev/openanalytics/wear/
  data/      GA4 Data/Admin API client (plain JVM code, unit tested)
  auth/      Google account sign-in and tokens (Play services)
  config/    selected property, cached snapshot for the tile
  ui/        Compose for Wear OS screens, charts and the ViewModel
  tile/      the Analytics tile (ProtoLayout)
app/src/test/  unit tests, plus Robolectric/Roborazzi screenshot tests
```

- Unit tests: `./gradlew testDebugUnitTest`
- Screenshots: `./gradlew recordRoborazziDebug` writes them to `app/screenshots/`
