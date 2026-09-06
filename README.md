**NOTE:** I am looking for someone to take over this repository, my Mi 11 Ultra has officially died and I can no longer do any work on this due to me having a 14 Ultra. Anyone who's interested can feel free to contact me. Thank you.

![Mirror2RearUltra](https://i.imgur.com/wpv8kID.png)
# Mirror2RearUltra
Rear-screen mirroring and calibration app for Xiaomi Mi 11 Ultra.

## Features

- Quick Settings tile for starting and stopping mirroring.
- Camera, navigation, video, and user-created profiles with independent settings.
- Fit, fill, and stretch scaling; 0/90/180/270-degree rotation; horizontal mirroring.
- Per-profile rear-screen brightness, zoom, and horizontal/vertical offsets.
- Adaptive sharp projection mode: 1× at 100% zoom, 1.5× at 105–150%, and 2× at 155–200%; economy mode stays at 1×.
- User-selectable app assignments with optional automatic profile switching.
- Automatic rear-output pause/resume for assigned apps during an approved mirroring session.
- Root-assisted control of the physical rear-panel backlight, with safe software dimming when root is unavailable.
- Optional calibration grid during mirroring and a 30-second rear-display test pattern.
- Configurable temperature and low-battery protection with automatic recovery hysteresis.
- Live status for root access, rear-backlight support, battery level, and device temperature.
- Rear dashboard with mirror-only, dashboard-only, and hybrid modes; clock, date, battery, temperature, and city-based weather widgets.
- Optional widgets for the next alarm, current media, compass, GPS speed and altitude, session time, active profile, and custom text.
- Automatic widget paging keeps dense configurations readable on the compact rear panel.
- Customizable widget layout, color style, text size, background opacity, and OLED burn-in drift.
- Versioned settings backup and restore through Android's system file picker.

Works on Android 11+ with any MIUI version after 12.

# Usage
Open the app to configure profiles, then press the quick tile to enable rear screen mirroring. Home screen shortcuts must be enabled in permissions for the quick tile to show. Make sure MIUI power saver is off so that the app isn't killed.

Automatic profiles require Usage Access. Android requires the user to approve each new screen-sharing session; after approval, automatic output pause/resume works without additional prompts until that session ends. Dashboard-only mode does not capture the main screen and therefore does not request screen-sharing consent. Weather uses the city entered in settings and does not request location access. Physical rear-panel brightness requires root access for Mirror2RearUltra; without it, only the rear output is dimmed and the main-screen brightness is not changed.

# Bugs
- Sometimes the confirmation dialog will appear on the rear screen. (fixed with 1.0-final, report to me if it does happen again.)
- You may have to wake up the screen by double tapping on it. (fixed with 1.0-final)
- Double tapping will increase brightness. (fixed with 1.0-final)

# Special Thanks To...
1. Xiaomi for making the Mi 11 Ultra...
2. Google for providing the tools necessary to make this...
3. And you for downloading the app; without you, this app would of never existed.
