# Moto G35 5G Dynamic Island (Android 15 Edition)

A complete, native Kotlin + Jetpack Compose Dynamic Island overlay app engineered for the **Motorola Moto G35 5G** running **Android 15 (API 35)** (`1080 x 2400` FHD+ 120Hz LTPS LCD with center 16MP punch-hole cutout).

## Included Features
- **120Hz Spring-Physics Overlay (`DynamicIslandAccessibilityService`)**: Attaches a hardware-accelerated Jetpack Compose window (`TYPE_ACCESSIBILITY_OVERLAY`) directly over the Moto G35 top-center punch-hole camera.
- **Notification Interception & Inline Replies (`IslandNotificationListenerService`)**: Reads notifications from WhatsApp, Telegram, Messages, Instagram, and Gmail, and supports direct `RemoteInput` inline text replies from the expanded island card.
- **Live Media Session Controller & Waveform Visualizer**: Connects to `MediaSessionManager` to show album art, track progress, Play/Pause/Next/Prev controls, and an animated 4-bar waveform visualizer.
- **Incoming & Active Call Pill**: Displays caller name, call status, and one-tap Answer/Decline buttons for phone and VoIP calls.
- **Hardware Event Listeners**: Displays live pills for 18W TurboPower charging, battery level, Ringer mode switches (Silent/Vibrate/Ring), and Flashlight toggle.
- **Idle Cutout Telemetry**: Shows real-time network speed (`TrafficStats` KB/s), clock, and battery percentage around the camera hole when idle.

---

## Option A: Build the APK on Your Phone via GitHub Actions (No PC Required)
1. Create a new repository on [GitHub](https://github.com/new) and upload the contents of this project folder.
2. Open the **Actions** tab in your GitHub repository and select **Build Moto G35 Dynamic Island APK (Android 15)**.
3. Tap **Run workflow**. In ~2 minutes, GitHub's cloud server compiles the project using JDK 17 and Android SDK 35 and outputs **`MotoG35-DynamicIsland-Android15-APK.zip`** containing `app-debug.apk`.

## Option B: Build Locally in Android Studio
1. Extract this project and open the folder in **Android Studio Ladybug (2024.2.1) or newer** with **JDK 17** and **Android SDK 35**.
2. Connect your Moto G35 5G with **USB Debugging** enabled, or build the APK from the terminal:
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## Enabling on Moto G35 (Android 15 Restricted Settings)
1. Install `app-debug.apk` on your Moto G35 5G.
2. Open **Settings > Apps > See all apps > Moto G35 Dynamic Island**.
3. Tap the **three-dot menu (⋮)** in the top-right corner and tap **Allow restricted settings**.
4. Open the **Moto G35 Dynamic Island** app and use the 4 setup buttons to enable **Accessibility Overlay**, **Notification & Media Listener**, **Overlay Permission**, and **Unrestricted Battery**.
