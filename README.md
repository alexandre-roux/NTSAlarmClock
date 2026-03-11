# NTS Alarm Clock

An Android alarm clock app that wakes you up using the live NTS Radio stream.
I love to wake up to NTS in the morning and couldn't find an app to do it so I decided to develop one!
Feel free to contribute and don't forget to [support our beloved NTS Radio](https://www.nts.live/supporters).

## Warnings

1. This app needs your permission to display notifications so the alarm can actually start. It will be asked when you start it.
2. This app also needs internet to play the stream, so put your phone in a silent mode when sleeping so the stream can run.
3. I tested this app on Android 9 and 16 but I advise you to run a classic alarm at the same time in case the app doesn't work, at least for the first time.

## Installation

This app isn't available on the Google Play store

## Architecture

The app follows a strict MVVM architecture and separates concerns clearly between UI, ViewModel, Android system components, and playback logic. Here is a logical diagram:
![Logical diagram for the NTS Alarm Clock app](assets/logical-diagram.png)

Main building blocks:
- Compose UI for the presentation layer
- ViewModels for state and orchestration
- AlarmManager and BroadcastReceiver for scheduling
- Foreground Service for audio playback
- Media3 (ExoPlayer) for streaming audio
- DataStore for persistence (planned)

## Tech stack

- Kotlin
- Jetpack Compose
- Android ViewModel
- AlarmManager
- Foreground Services
- Media3 (ExoPlayer)
- Gradle Version Catalog
