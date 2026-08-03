# WaveFlow for Android

Native Android client for [WaveFlow](https://github.com/InstaZDLL/WaveFlow) — a
local-first music player. Kotlin + Jetpack Compose + Media3.

> **Status:** early foundation. Plays local files from the device today; sync
> with the WaveFlow server (playlists, liked, streaming) comes later, once the
> server side is finalised.

## Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3 (WaveFlow emerald theme)
- **Audio:** [Media3](https://developer.android.com/media/media3) / ExoPlayer via
  a `MediaSessionService` — background playback + lock-screen / notification
  controls out of the box
- **Library source:** `MediaStore` (device audio, read-only for now)
- **Images:** Coil
- **DI:** manual container for now (`AppContainer`); Hilt later
- **Min SDK:** 26 (Android 8.0) · **Target/Compile SDK:** 36

## Project layout

```
app/src/main/java/app/waveflow/
├─ WaveFlowApp.kt          Application + manual DI container
├─ MainActivity.kt         Scaffold → permission gate → LibraryScreen
├─ model/
│  └─ Song.kt              Domain model, source-agnostic
├─ data/
│  ├─ MusicRepository.kt            Library abstraction (Flow<List<Song>>)
│  └─ MediaStoreMusicRepository.kt  MediaStore query + ContentObserver
├─ playback/
│  ├─ PlaybackService.kt          Media3 MediaSessionService (ExoPlayer)
│  ├─ PlaybackController.kt       Playback facade + PlaybackState
│  ├─ Media3PlaybackController.kt MediaController connection → StateFlow
│  └─ MediaItemMapper.kt          Song ↔ MediaItem
└─ ui/
   ├─ theme/                Material 3 emerald theme
   ├─ components/Artwork.kt Album art with placeholder
   ├─ DurationFormat.kt     m:ss / h:mm:ss
   ├─ permission/
   │  └─ AudioPermissionGate.kt   Grant / deny / permanently-denied flow
   ├─ player/
   │  ├─ PlayerUiState.kt      Player state
   │  ├─ ArtworkAccent.kt      Dominant colour from cover (Palette)
   │  ├─ MiniPlayer.kt         Compact bar above the library
   │  └─ NowPlayingScreen.kt   Full-screen player
   └─ library/
      ├─ LibraryUiState.kt     Screen state
      ├─ LibraryViewModel.kt   Orchestrates repository + playback
      └─ LibraryScreen.kt      Song list
```

## Build

Open in **Android Studio** (uses its bundled JBR 21) and run the `app` config,
or from the command line:

```bash
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:installDebug       # install on a connected device/emulator
```

> The build requires JDK 21 (Android Studio's bundled JBR). Newer system JDKs
> (e.g. 25) are not yet supported by the Android Gradle Plugin — build via
> Studio or point `org.gradle.java.home` at a JDK 21.

## Roadmap

- [x] Local file playback (MediaStore + Media3)
- [x] Full-screen player (seek, shuffle, repeat, artwork-tinted background)
- [ ] Album / artist / playlist browsing
- [ ] Room index + search
- [ ] WaveFlow server sync (playlists, liked, ratings)
- [ ] Streaming from the WaveFlow server (HMAC signed URLs)
- [ ] Android Auto (Media3 `MediaLibraryService`)

## License

[GPL-3.0](LICENSE) — same as WaveFlow desktop.
