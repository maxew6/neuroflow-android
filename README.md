# NeuroFlow for Android

**Fewer Reels. Brighter Mind.** NeuroFlow is a native Android focus companion that helps users stay intentional while using short-form video. It detects supported Reels and Shorts surfaces, displays a floating usage island, tracks daily activity, and offers an optional intervention when a user-set limit is reached.

[![Android CI](https://github.com/maxew6/neuroflow-android/actions/workflows/android.yml/badge.svg)](https://github.com/maxew6/neuroflow-android/actions/workflows/android.yml)
[![Latest release](https://img.shields.io/github/v/release/maxew6/neuroflow-android)](https://github.com/maxew6/neuroflow-android/releases/latest)

> **Portfolio build:** v1.1.1 · `com.neuroflow.app` · Kotlin · Jetpack Compose · Material 3
>
> [Download the latest debug APK](https://github.com/maxew6/neuroflow-android/releases/latest) · [Browse the source](https://github.com/maxew6/neuroflow-android/tree/main/app/src/main)

## Product overview

NeuroFlow combines a native Compose dashboard with Android accessibility and overlay capabilities. The user chooses which apps to monitor and configures the experience on-device. Instagram Reels and YouTube Shorts are supported; the project also includes TikTok package handling in its intervention logic.

Key functionality:

- A draggable, spring-positioned floating island with a live counter and mascot.
- Accessibility-based surface detection that prioritizes known view identifiers and selected tabs, with a conservative geometry fallback.
- Daily usage tracking, configurable limits, and an optional strict intervention flow.
- A math challenge that can grant a small, finite extension when the limit is reached.
- Sensor-health diagnostics, including service state and a copyable diagnostic report.
- A dashboard, analytics, settings, onboarding, and preview/testing flows.
- Local persistence using Android SharedPreferences; no account or remote backend is required by this project.

## Screenshots

Screenshots are not included in this source snapshot. The APK release is available from the [Releases page](https://github.com/maxew6/neuroflow-android/releases/latest).

## Download and install

1. Open [Releases](https://github.com/maxew6/neuroflow-android/releases/latest) and download `app-debug.apk`.
2. Install it on an Android device (Android 8.0 / API 26 or later). If prompted, allow installation from the app used to open the APK.
3. Follow in-app setup to grant only the capabilities you want to use.

Android may flag sideloaded APKs because they are not distributed through Google Play. The attached APK is a **debug build**, not a Play Store release. For a verifiable build, compile it yourself from this repository using the steps below.

## Build from source

### Requirements

- JDK 17
- Android SDK Platform 34 and Build Tools 34.0.0
- Internet access for the first dependency download

The repository includes the Gradle 8.5 wrapper, so a separately installed Gradle is not required.

```bash
git clone https://github.com/maxew6/neuroflow-android.git
cd neuroflow-android
./gradlew assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

On Windows, use `gradlew.bat assembleDebug`. GitHub Actions also runs the debug build on pushes and pull requests; downloadable CI artifacts are available from successful workflow runs.

## How the app is organized

```text
app/src/main/
├── AndroidManifest.xml
└── java/com/neuroflow/app/
    ├── MainActivity.kt
    ├── data/          # Local repository and diagnostic log
    ├── service/       # Overlay, accessibility sensor, boot receiver
    ├── ui/components/ # Island, interventions, mascot, challenges
    ├── ui/screens/    # Home, analytics, settings, sensor health
    ├── ui/theme/      # Compose theme and visual styling
    └── util/          # Permission and haptic helpers
```

## Technical stack

| Area | Implementation |
|---|---|
| Language | Kotlin 1.9.22 |
| UI | Jetpack Compose, Material 3 |
| Android Gradle Plugin | 8.2.2 |
| Gradle wrapper | 8.5 |
| Compile / target SDK | 34 / 34 |
| Minimum SDK | 26 |
| State and persistence | Kotlin coroutines/StateFlow and SharedPreferences |
| Build automation | GitHub Actions + Gradle wrapper |

## Permissions and user control

The app's core features require Android capabilities that are powerful and user-controlled:

- **Accessibility service:** observes the active app's UI to recognize supported short-video surfaces and, if strict blocking is enabled, perform the configured navigation action. Android requires the user to enable the service in system settings.
- **Display over other apps:** draws the floating island above other apps. This special access must be granted in system settings.
- **Notifications:** supports the foreground service notification where applicable.
- **Boot completed:** allows the app to restore its monitoring service after a device restart when monitoring is enabled.

These permissions are used for the app's stated on-device features. Review the code and Android permission prompts before enabling them. The app does not claim to bypass Android controls.

## Project status and limitations

This is an independently built portfolio project, not an official product affiliated with Instagram, YouTube, TikTok, or their owners. Third-party apps can change their UI identifiers, so detection behavior may vary across app versions and Android device manufacturers. Use the in-app **Sensor health** screen to diagnose accessibility-service state and observed events. Background-service behavior and sideload installation can also vary by device and OS release.

## Version history

- **1.1.1** — Improves supported-surface detection and accessibility scan performance; adds sensor-health diagnostics, battery guidance, and a strict-blocking test switch; resets stale daily counts on upgrade.
- **1.1** — Adds configurable island placement, Reels/Shorts surface checks, strict interventions, and the math-refresh extension challenge.
- **1.0** — Initial dashboard, overlay, accessibility sensor, mascot, and usage tracking.

See [Releases](https://github.com/maxew6/neuroflow-android/releases) for versioned APKs and notes.

## License

No open-source license has been applied yet. Unless a license is added, the source is publicly viewable but reuse, redistribution, and modification are not granted by default.
