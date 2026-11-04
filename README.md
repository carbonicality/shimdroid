# openAnalytics

A standalone **Wear OS** app that shows your **Google Analytics 4** stats on your wrist. You build and sideload it yourself.

| Card | What it shows |
| --- | --- |
| **Active now** | Realtime active users (last 30 min) with a per-minute sparkline |
| **Today** | Users, change vs. yesterday, sessions, views, new users |
| **Last 7 days** | Users, change vs. the previous 7 days, daily bar chart |
| **Top pages now / 7d** | The most-viewed pages and screens |
| **Top countries · 7d** | Where your users are |

### Tile

Swipe left from the watch face, tap **+**, and add the **Analytics** tile. It shows:

- **Active now** (realtime users, large)
- **Today** and **7 days** users, each with ▲/▼ vs. the previous period
- When it last updated (red "offline" means it's showing cached numbers)

Tap the numbers to refresh, or tap **Open** to launch the full app. The tile refreshes itself about every 15 min. While the app is open it pushes fresh numbers to the tile, so the two stay in sync.

### Refresh

Realtime numbers refresh every 60 s while the app is open, and the summary refreshes every 10 min. Tap **Refresh** to update everything immediately. Polling stops when the app is in the background.

It talks to the [GA4 Data API](https://developers.google.com/analytics/devguides/reporting/data/v1) directly over HTTPS. It does not need a phone companion app or Google Play Services.

## How auth works

Watches can't run a normal Google sign-in, and Google's limited-input device flow doesn't allow the Analytics scope. So openAnalytics uses a **service account**, which is a robot Google account with read-only access to your GA4 property. The app signs a JWT with the account's key on the watch and exchanges it for an access token. It requests only the `analytics.readonly` scope.

### 1. Create a service account (one-off)

1. In [Google Cloud Console](https://console.cloud.google.com/), choose or create a project.
2. Enable the **Google Analytics Data API**. Optionally also enable the **Google Analytics Admin API**, which the app uses to show the property's name.
3. Go to **IAM & Admin → Service accounts → Create service account**. It needs no Cloud roles.
4. Open the account, go to **Keys → Add key → JSON**, and download the file.
5. In **Google Analytics**, go to **Admin → Property access management → +**. Add the service account's email (`…@….iam.gserviceaccount.com`) with the **Viewer** role.
6. Note your **property ID**. It's under **Admin → Property details** and is all digits (e.g. `123456789`). Your `G-XXXX` measurement ID won't work here.

> Keep the JSON key private. It is git-ignored here, and it only grants read access to the GA properties you added it to.

## Build

You need either the Android Studio JDK (17+) and Android SDK, or no local tooling at all:

- **Locally:** `./gradlew assembleDebug` creates `app/build/outputs/apk/debug/app-debug.apk`.
- **GitHub Actions:** every push builds a debug APK. Download it from the run's **Artifacts** (`openAnalytics-debug-apk`).

APKs never contain credentials unless you choose to bundle them (see *Alternative* below).

## Sideload onto the watch

1. On the watch, go to **Settings → System → About → Versions** and tap *Build number* 7 times. Then go to **Settings → Developer options** and turn on **ADB debugging** and **Debug over Wi-Fi**.
2. Connect from your computer:
   ```sh
   adb pair <ip>:<pairing-port>     # Wear OS 4+: "Pair new device" on the watch
   adb connect <ip>:<port>
   ```
3. Install, then provision the credentials:
   ```sh
   adb install -r app-debug.apk
   scripts/provision.sh ~/Downloads/my-sa-key.json 123456789
   ```
   The script copies the key into the app's **private** storage (via `run-as`, which works for debug builds) and relaunches the app. To remove the credentials, run `scripts/provision.sh --clear`.

To switch properties, run `provision.sh` again with a different ID.

### Alternative: bundle at build time

Put the key at `app/src/main/assets/service-account.json` and add `ga.propertyId=123456789` to `local.properties`. Both are git-ignored. Then build locally. The credentials will be inside the APK, so don't share that APK or upload it anywhere.

### Lookup order

The app looks for `service-account.json` and `property-id.txt` in these places, in order:

1. Private storage `files/`
2. `/sdcard/Android/data/dev.openanalytics.wear/files/`
3. Bundled assets
4. For the property ID only, `ga.propertyId` from the build

## Troubleshooting

The error card on the watch shows the API's message.

- **HTTP 403 / insufficient permissions**: Either the service account isn't a Viewer on that property, or the Data API isn't enabled in the key's Cloud project.
- **HTTP 400 Invalid JWT Signature / invalid_grant**: The key was deleted or revoked, or the watch clock is wrong.
- **Invalid GA4 property ID**: You used a `G-…` measurement ID. Use the numeric property ID instead.
- **Network error**: The watch has no connectivity. On Bluetooth-only watches, the paired phone needs internet access.

## Project layout

```
app/src/main/java/dev/openanalytics/wear/
  data/      GA4 + OAuth client (plain JVM code, unit tested)
  config/    finds credentials on the device
  ui/        Compose for Wear OS screens, charts and the ViewModel
  tile/      the Analytics tile (ProtoLayout)
scripts/provision.sh   pushes credentials over ADB
```

Run the unit tests with `./gradlew testDebugUnitTest`.
