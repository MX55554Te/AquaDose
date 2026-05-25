# AquaDose Android Source

This folder is a self-contained copy of the AquaDose Android app source.

## Build

From this directory:

```powershell
$env:JAVA_HOME='<path-to-jdk-17>'
$env:ANDROID_HOME='<path-to-android-sdk>'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
.\gradlew.bat :app:assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release signing is not configured in this source copy. Configure signing outside the repository before producing a production release build.

## Included Projects

- `app/`: Android app using Kotlin and Jetpack Compose.
- `android-api-contract/`: local API/model client dependency used by the app through Gradle composite build.

## Android Requirements

- Minimum SDK: 26
- Target SDK: 36
- Compile SDK: 36
- Java: 17
