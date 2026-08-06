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
- **Local store:** Room — playlists only; tracks are never duplicated out of
  `MediaStore` (schemas versioned under `app/schemas/`)
- **Images:** Coil
- **DI:** manual container for now (`AppContainer`); Hilt later
- **Min SDK:** 26 (Android 8.0) · **Target/Compile SDK:** 36

## Project layout

```
app/src/main/java/app/waveflow/
├─ WaveFlowApp.kt          Application + manual DI container
├─ MainActivity.kt         Scaffold → permission gate → LibraryScreen
├─ model/
│  ├─ Song.kt              Domain model, source-agnostic
│  ├─ Album.kt / Artist.kt Derived from the song list
│  ├─ Library.kt           Loaded library; albums / artists / index derived lazily
│  ├─ Playlist.kt          Local playlist + entries
│  └─ Grouping.kt          List<Song> → albums / artists
├─ data/
│  ├─ LibraryStore.kt               Application-scoped library, loaded once
│  ├─ MusicRepository.kt            Library abstraction (Flow<List<Song>>)
│  ├─ MediaStoreMusicRepository.kt  MediaStore query + ContentObserver
│  ├─ PlaylistRepository.kt         Local playlist abstraction
│  ├─ RoomPlaylistRepository.kt     Room-backed implementation
│  └─ local/                        Room entities, DAO, database
├─ playback/
│  ├─ PlaybackService.kt          Media3 MediaSessionService (ExoPlayer)
│  ├─ PlaybackController.kt       Playback facade + PlaybackState
│  ├─ Media3PlaybackController.kt MediaController connection → StateFlow
│  └─ MediaItemMapper.kt          Song ↔ MediaItem
└─ ui/
   ├─ theme/                Material 3 emerald theme
   ├─ DurationFormat.kt     m:ss / h:mm:ss
   ├─ components/           Artwork, SongRow, LibraryStateContainer
   ├─ navigation/
   │  └─ WaveFlowNavigation.kt    Routes + bottom bar
   ├─ browse/
   │  ├─ AlbumsScreen.kt          Adaptive cover grid
   │  ├─ AlbumDetailScreen.kt     Header + tracks
   │  ├─ ArtistsScreen.kt         Artist list
   │  ├─ ArtistDetailScreen.kt    Header + tracks
   │  └─ DetailHeader.kt          Shared header (play / shuffle)
   ├─ playlists/
   │  ├─ PlaylistsViewModel.kt    Playlist state + writes
   │  ├─ PlaylistsScreen.kt       Playlist list + creation
   │  ├─ PlaylistDetailScreen.kt  Header + tracks
   │  ├─ AddToPlaylistSheet.kt    Long-press a song → add
   │  └─ PlaylistMenu.kt          Rename / delete
   ├─ permission/
   │  └─ AudioPermissionGate.kt   Grant / deny / permanently-denied flow
   ├─ player/
   │  ├─ PlayerViewModel.kt    Owns the MediaController; playback commands
   │  ├─ PlayerUiState.kt      Player state
   │  ├─ ArtworkAccent.kt      Dominant colour from cover (Palette)
   │  ├─ MiniPlayer.kt         Compact bar above the library
   │  └─ NowPlayingScreen.kt   Full-screen player
   └─ library/
      ├─ LibraryViewModel.kt   Thin access point to LibraryStore
      └─ LibraryScreen.kt      Song list
```

One `LibraryStore` at the application level holds the loaded library; three
ViewModels read from it — `LibraryViewModel` (browsing), `PlayerViewModel`
(playback), `PlaylistsViewModel` (playlists). Adding a screen means adding a
ViewModel, never a second `MediaStore` query.

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

## Tests

```bash
./gradlew testDebugUnitTest      # everything below, no emulator needed
```

All tests run on the JVM. Robolectric provides a real `android.net.Uri` and an
in-memory SQLite for Room, so the DAO is exercised without a device.

| Suite | Covers |
|---|---|
| `PlaylistDaoTest` | duplicate adds, `updatedAt` bumping, positions, `createWithSong` atomicity, cascade delete |
| `LibraryStoreTest` | loading, read failures, single subscription, retry |
| `PlayerViewModelTest` | contextual play queue, current-song resolution, controller release |
| `PlaylistsViewModelTest` | flow failures, write failures, resolution order, atomic creation |
| `GroupingTest` | album / artist derivation, sorting, missing tags |
| `DurationFormatTest` | `m:ss` / `h:mm:ss` formatting |

Fakes and the `Dispatchers.Main` rule live in `src/test/java/app/waveflow/testing/`.

## Roadmap

- [x] Local file playback (MediaStore + Media3)
- [x] Full-screen player (seek, shuffle, repeat, artwork-tinted background)
- [x] Album / artist browsing (Navigation Compose + bottom bar)
- [x] Local playlists (Room): create, rename, delete, add / remove tracks
- [ ] Drag-to-reorder inside a playlist
- [ ] Search
- [ ] WaveFlow server sync (playlists, liked, ratings)
- [ ] Streaming from the WaveFlow server (HMAC signed URLs)
- [ ] Android Auto (Media3 `MediaLibraryService`)

## License

[GPL-3.0](LICENSE) — same as WaveFlow desktop.
