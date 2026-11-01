# Mirror2RearUltra

**English** · [Русский](README.ru.md)

Rear-screen mirroring, calibration and widget dashboard for the **Xiaomi Mi 11 Ultra**.

The app is built for this one phone and its 294 × 126 rear panel. Other devices are not tested and not supported.

> **This branch (`HyperOS4`) is adapted for HyperOS 4.**

<p align="center">
  <img src="docs/images/mirror.png" width="22%" alt="Mirror screen">
  <img src="docs/images/image.png" width="22%" alt="Image and calibration">
  <img src="docs/images/profiles.png" width="22%" alt="Profiles and apps">
  <img src="docs/images/widgets.png" width="22%" alt="Widget layout">
</p>

<p align="center">
  <img src="docs/images/rear-panel.png" width="300" alt="Rear panel showing the dashboard">
  <br>
  <sub>The rear panel running a dashboard page.</sub>
</p>

## Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Usage](#usage)
- [Permissions](#permissions)
- [Root](#root)
- [Building](#building)
- [Troubleshooting](#troubleshooting)
- [Known issues](#known-issues)

## Features

### Mirroring
- Quick Settings tile and a floating button for starting and stopping mirroring.
- **Silent capture:** mirroring without the system confirmation dialog. It starts only from the floating button and releases the capture 1.5 seconds after it is no longer needed.
- Camera, navigation, video and user-created profiles, each with independent settings.
- Fit, fill and stretch scaling; 0/90/180/270° rotation; horizontal mirroring.
- Per-profile rear brightness, zoom and horizontal/vertical offset.
- Adaptive sharp projection: 1× at 100% zoom, 1.5× at 105–150%, 2× at 155–200%. Economy mode stays at 1×.
- Rule-of-thirds grid and a level over the mirrored camera.
- Countdown on the panel before a shot taken by tapping it.
- Merged Mirror screen with profiles, an apps page and a page-slide transition.

### Profiles and apps
- Assign apps to profiles and switch profiles automatically when an assigned app opens.
- Rear output pauses and resumes for assigned apps during a mirroring session.

### Dashboard
- Dashboard-only, mirror-only and hybrid modes; several pages with automatic paging.
- Widget layout editor with a live preview: drag to move, pinch to resize, per-widget settings and sample variants.
- Widgets: clock and date (with variants), battery level, battery temperature, charging and time left, weather, next alarm, current media, notifications, calendar (ongoing events and more), compass, GPS speed and altitude, steps (kept across reboots), sunrise and sunset, horizon level, sound mode, world clocks and second time zone, QR code, Bluetooth and Wi-Fi, session time, active profile and custom text.
- Severity colours for temperature and battery, and a configurable colour style, text size and background opacity.
- Optional notification text on the panel, notification rings, and lighting the panel for a notification while the main screen is off.
- Bluetooth and Wi-Fi triggers.
- OLED burn-in drift.
- Quiet hours for the rear display.

### Protection and diagnostics
- Configurable temperature and low-battery protection with recovery hysteresis.
- Live status of root access, rear-backlight support, battery level and temperature.
- Calibration grid during mirroring and a 30-second rear-display test pattern.
- Versioned settings backup and restore through the system file picker.

## Requirements

- Xiaomi Mi 11 Ultra (rear panel 294 × 126).
- Android 11 or newer (`minSdk 30`). This branch targets HyperOS 4.
- Root is optional; see [Root](#root).

## Installation

Download the APK from the repository's Releases page, or [build it yourself](#building).

## Usage

1. Open the app and set up your profiles on the **Mirror** tab.
2. Add the Quick Settings tile and press it to start mirroring. Home-screen shortcuts must be allowed in the app's permissions for the tile to appear.
3. Build the dashboard on the widgets tab: pick widgets, arrange them on pages and set their sizes.
4. Turn MIUI power saver off for the app, so the system does not kill it.

Automatic profiles need Usage Access. Dashboard-only mode does not capture the main screen and does not ask for screen-sharing consent.

## Permissions

| Permission | Used for |
|---|---|
| Screen capture (per session) | Mirroring the main screen to the rear panel |
| Usage Access | Switching profiles by the foreground app |
| Display over other apps | The floating start button and overlays |
| Notification access | Notification widget and rings |
| Calendar | Calendar widget |
| Location (coarse and fine) | GPS speed and altitude, sunrise and sunset |
| Physical activity | Steps widget |
| Bluetooth (connect) | Bluetooth trigger and widget |
| Network and Wi-Fi state | Wi-Fi trigger, widget and weather |
| Internet | Weather |
| Foreground service | Keeping mirroring and the dashboard alive |

Weather uses the city entered in settings and does not request location.

## Root

| Feature | Without root | With root |
|---|---|---|
| Mirroring and dashboard | Works | Works |
| Rear brightness | Software dimming of the rear output only | Controls the physical rear backlight |
| Main-screen brightness | Never changed | Never changed |

## Building

```bash
./gradlew assembleDebug
```

The Gradle plugin needs JDK 11 or newer. If the system Java is older, point `JAVA_HOME` at a newer JDK, for example the one bundled with Android Studio. Run the unit tests with `./gradlew testDebugUnitTest`.

## Troubleshooting

- **The tile does not appear:** allow home-screen shortcuts in the app's permissions.
- **Mirroring stops by itself:** turn off MIUI power saver and allow autostart for the app.
- **Automatic profiles do nothing:** grant Usage Access.
- **The backlight flickers when the phone is held upright:** the system rear-screen service also controls the backlight; the app pauses it for the length of a session.

## Known issues

- On a locked screen the system refuses to start anything on the rear display.

## Special thanks

1. Xiaomi for making the Mi 11 Ultra.
2. Google for providing the tools necessary to make this.
3. And you for downloading the app; without you, this app would never have existed.
