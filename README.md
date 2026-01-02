# UpDogScoreKeeper KMP

This project has been migrated from an Expo React Native app to Kotlin Multiplatform.

## Prerequisites
- JDK 17
- Android Studio (Koala or newer recommended)
- Xcode (for iOS)

## Getting Started
1. Open this directory (`UpDogScoreKeeper`) in Android Studio.
2. Wait for Gradle Sync to complete.
3. Run the `androidApp` configuration or `desktopApp`.

## Architecture
- **UI**: Compose Multiplatform
- **Navigation**: Voyager
- **DI**: Koin
- **Auth**: Firebase (Interface defined in `AuthService.kt`)

## Note on Gradle
The Gradle Wrapper (`gradlew`) was not generated automatically. Opening the project in Android Studio will prompt to generate it.
