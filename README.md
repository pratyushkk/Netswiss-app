# NetSwiss App

NetSwiss is an Android network utility toolkit built with Kotlin and Jetpack Compose.  
It combines mock GPS tools, cellular network diagnostics, real-time speed monitoring, and a per-app firewall in one app.

## Features

### 1) Mock GPS
- Full-screen map (OSMDroid) with tap-to-place marker.
- Location search with live suggestions and manual search action.
- Manual latitude/longitude input with validation.
- Start/stop mock location foreground service.
- "Locate me" action to center on current device location.

### 2) Network Mode
- Live network type and signal information.
- Signal strength in dBm with quality indicator.
- Quick launch button for device radio settings (if supported by OEM ROM).

### 3) Speed Monitor
- Real-time download/upload monitoring using `TrafficStats`.
- Foreground notification with dynamic speed icon for status bar visibility.
- Session stats: peak speeds and total transferred data.
- Optional latency monitor (ping + jitter stability view).

### 4) App Firewall
- Per-app internet blocking using native `VpnService`.
- Lists installed third-party apps with icon, label, and package.
- Toggle internet block per app.
- Foreground VPN service with notification action to stop firewall.

### 5) UI and Theme
- Compose-based UI with reusable glass-style components.
- Dark/Light theme toggle from Home screen.
- Material 3 theming with centralized tokens (`Color`, `Spacing`, `Radius`, `Motion`, `Glass`).

## Tech Stack
- Kotlin
- Jetpack Compose + Material 3
- Android Navigation Compose
- OSMDroid (map)
- Android Foreground Services
- Android `VpnService`
- Kotlin Coroutines + Flow

## Requirements
- Android Studio Hedgehog+ recommended
- JDK 17
- Android SDK:
  - `compileSdk = 34`
  - `targetSdk = 34`
  - `minSdk = 26`

## Build and Run

```bash
./gradlew assembleDebug
./gradlew installDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

## Important Setup Notes

### Mock GPS setup
1. Enable Developer Options on the device.
2. Open Developer Options -> Select mock location app.
3. Choose `NetSwiss`.
4. Grant location permission when prompted.

If mock app selection is missing, Mock GPS cannot be started.

### Firewall setup
1. Open Firewall tab.
2. Tap "Start Firewall".
3. Accept system VPN permission (first time only unless revoked).
4. Toggle apps to block internet access.

The firewall uses local VPN routing; no external VPN server is used.

## Permissions Used
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
- `ACCESS_MOCK_LOCATION` (developer option workflow)
- `READ_PHONE_STATE`
- `ACCESS_NETWORK_STATE`
- `INTERNET`
- `POST_NOTIFICATIONS` (Android 13+)
- `QUERY_ALL_PACKAGES` (to list installed apps)
- Foreground service permissions and `BIND_VPN_SERVICE`

## Project Structure

```text
app/src/main/java/com/netswiss/app
  |- navigation/
  |- service/
  |- ui/
  |   |- components/
  |   |- screens/
  |   |- theme/
  |- util/
```

## Disclaimer
- Some device/OEM ROM restrictions can limit hidden radio settings launch.
- VPN/firewall behavior can vary by Android build and vendor network stack.
- Use mock location and firewall features responsibly and according to local policy and app terms.
