# WaveFlow for Android

Native Android client for [WaveFlow](https://github.com/InstaZDLL/WaveFlow) — a
local-first music player. Kotlin + Jetpack Compose + Media3.

> **Status:** local-only. Plays, browses, searches and organises the device's
> own files. Nothing talks to a WaveFlow server yet — see
> [Server sync](#server-sync) for what that will take.

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
│  ├─ Grouping.kt          List<Song> → albums / artists
│  └─ Search.kt            Accent-insensitive filtering over a Library
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
   ├─ Labels.kt             Unknown artist / album fallbacks
   ├─ components/           Artwork, MediaRow, SongRow, LibraryStateContainer
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
   │  ├─ PlaylistsUiState.kt      Playlists + resolved tracks
   │  ├─ PlaylistsScreen.kt       Playlist list + creation
   │  ├─ PlaylistDetailScreen.kt  Header + tracks + drag-to-reorder
   │  ├─ AddToPlaylistSheet.kt    Long-press a song → add
   │  ├─ PlaylistNameDialog.kt    Create / rename prompt
   │  └─ PlaylistMenu.kt          Rename / delete
   ├─ search/
   │  ├─ SearchViewModel.kt       Query → filtered library
   │  ├─ SearchScreen.kt          Songs / albums / artists sections
   │  └─ SearchField.kt           Query input
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

One `LibraryStore` at the application level holds the loaded library; four
ViewModels read from it — `LibraryViewModel` (browsing), `PlayerViewModel`
(playback), `PlaylistsViewModel` (playlists), `SearchViewModel` (search).
Adding a screen means adding a ViewModel, never a second `MediaStore` query.

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
| `PlaylistDaoTest` | duplicate adds, `updatedAt` bumping, positions, `reorder` normalisation, `createWithSong` atomicity, cascade delete |
| `LibraryStoreTest` | loading, read failures, single subscription, retry |
| `PlayerViewModelTest` | contextual play queue, current-song resolution, controller release |
| `PlaylistsViewModelTest` | flow failures, write failures, resolution order, atomic creation, reorder rollback and staleness |
| `DragStateTest` | drag arithmetic: target rank, visual offset, bounds, `moved` |
| `PlaylistDetailScreenTest` | reorder accessibility actions, order restored after a failed write |
| `SearchTest` | matching by title / album / artist, accent and case folding, prefix ranking |
| `SearchViewModelTest` | query → results, clearing, following the library |
| `GroupingTest` | album / artist derivation, sorting, missing tags |
| `DurationFormatTest` | `m:ss` / `h:mm:ss` formatting |

Fakes and the `Dispatchers.Main` rule live in `src/test/java/app/waveflow/testing/`.

Compose tests run on the JVM too: `createComposeRule()` works under Robolectric,
so a screen can be composed and driven without a device. What still cannot be
covered that way is the pointer gesture itself — its arithmetic is extracted
into `DragState` and tested there instead.

## Roadmap

- [x] Local file playback (MediaStore + Media3)
- [x] Full-screen player (seek, shuffle, repeat, artwork-tinted background)
- [x] Album / artist browsing (Navigation Compose + bottom bar)
- [x] Local playlists (Room): create, rename, delete, add / remove tracks
- [x] Drag-to-reorder inside a playlist
- [x] Search across songs, albums and artists
- [x] Compose UI tests (Robolectric, no device)
- [ ] WaveFlow server as a remote source: browse and stream its catalogue
- [ ] Server user-data sync (playlists, favorites, ratings) — see below
- [ ] Android Auto (Media3 `MediaLibraryService`)

### Server sync

[WaveFlow Server](https://github.com/InstaZDLL/waveflow-server) ships the sync
protocol today — `/api/v2/sync/snapshot`, `/changes`, `/ack` and a wake-up
socket, specified in its `RFC-003`. Bearer tokens for native clients and
authorized streaming (`/api/v2/tracks/{id}/stream`) are already there too, so
consuming the server as a *remote source* needs no server-side work.

Syncing the **local** library is a different matter, and it is not blocked on
this app: the protocol carries server track UUIDs, and RFC-003 states it "never
guesses a local/server track match" — reconciliation is a later milestone with
its own RFC. Until that exists, local playlists stay local.

Whenever it does land, `playlist_songs` will need a Room migration: it keys on
`MediaStore` ids, which do not survive a device re-index, let alone identify a
track to a server.

## License

[GPL-3.0](LICENSE) — same as WaveFlow desktop.
