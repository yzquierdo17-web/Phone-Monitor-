# Vitals — phone monitor for Pixel

A native Android app (Kotlin + Jetpack Compose) that monitors everything Android lets an app see, plus a floating HUD overlay.

## Tabs
- **Overview**: Battery (level, live watts, mA, voltage, temp, health, cycle count, estimated capacity), CPU (clock per core, average, top core GHz), Thermals (status, headroom before throttling, thermal zones if exposed), RAM, Storage. Each has a 2‑minute live graph.
- **Network**: Live download/upload speed and graphs, connection type, internet check, link bandwidth, Wi‑Fi signal/band/link speed, cellular bars/dBm.
- **Apps**: Today's screen time, unlocks, and data used per app, plus which apps hold camera/mic/location/etc. Needs *Usage access*.
- **Sensors**: Every hardware sensor with live readings.
- **System**: Model, SoC, GPU, display, Android/patch level, kernel, uptime and deep‑sleep %.
- **HUD**: A floating overlay over any app or game. Drag to move, tap to switch between compact and full.

## Build the APK (from your phone)
1. Create a new GitHub repo and upload this folder's contents, keeping the folders as they are.
2. GitHub runs **Actions → Build APK** on every push to `main`. You can also start it by hand with *Run workflow*.
3. Open the finished run and download the **vitals-apk** artifact. Unzip it and install `app-debug.apk`. You'll need to allow installs from your browser or files app.

## First run
- Allow notifications so the HUD service can run.
- **Overview → Start HUD**, then allow *Display over other apps*.
- **Apps → Open Usage access**, then turn on Vitals.

## What Android doesn't allow apps to see (without root)
- True CPU % and GPU load: `/proc/stat` has been blocked since Android 8, so the app shows clock speed as a share of max instead.
- Battery use per app: only the system can see it. The Battery card has a button that opens Android's own screen for it.
- FPS in other apps.
