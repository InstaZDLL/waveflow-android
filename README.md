# WaveFlow for Android

Native Android client for [WaveFlow](https://github.com/InstaZDLL/WaveFlow) — a
local-first music player. Kotlin + Jetpack Compose + Media3.

> **Status:** local-first. Plays, browses, searches and organises the device's
> own files, and streams from a WaveFlow server — see [Server](#server).

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
- **Server:** OkHttp + kotlinx.serialization; session tokens in DataStore
- **DI:** manual container for now (`AppContainer`); Hilt later
- **Min SDK:** 26 (Android 8.0) · **Target SDK:** 36 · **Compile SDK:** 37

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
│  ├─ local/                        Room entities, DAO, database
│  └─ remote/                       WaveFlow server: HTTP, auth, session, catalogue
├─ playback/
│  ├─ PlaybackService.kt          Media3 MediaSessionService (ExoPlayer)
│  ├─ PlaybackController.kt       Playback facade + PlaybackState
│  ├─ Media3PlaybackController.kt MediaController connection → StateFlow
│  ├─ MediaItemMapper.kt          Song / RemoteSong → MediaItem, and back
│  ├─ PlayingTrack.kt             What the player holds, whatever its source
│  └─ RemoteStreamResolver.kt     Marker URI → ticketed stream URL
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
   ├─ server/
   │  ├─ ServerViewModel.kt       Sign in / out, error mapping
   │  ├─ ServerUiState.kt         Session + progress + last failure
   │  ├─ ServerScreen.kt          Sign-in form and account screens
   │  └─ catalog/                 Remote albums / artists, paginated
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
| `PlayerViewModelTest` | contextual play queue, local vs remote queue, controller release |
| `PlaylistsViewModelTest` | flow failures, write failures, resolution order, atomic creation, reorder rollback and staleness |
| `DragStateTest` | drag arithmetic: target rank, visual offset, bounds, `moved` |
| `PlaylistDetailScreenTest` | reorder accessibility actions, order restored after a failed write |
| `SearchTest` | matching by title / album / artist, accent and case folding, prefix ranking |
| `SearchViewModelTest` | query → results, clearing, following the library |
| `GroupingTest` | album / artist derivation, sorting, missing tags |
| `DurationFormatTest` | `m:ss` / `h:mm:ss` formatting |
| `HttpServerApiTest` | request shapes, error classes, URL handling, unknown fields |
| `ServerSessionRepositoryTest` | token refresh and rotation, session lifetime, sign-out |
| `ServerViewModelTest` | validation, error wording, connection progress |
| `ServerScreenTest` | sign-in form, connected account, no token on screen |
| `HttpCatalogApiTest` | paging params, flattened details, track ordering |
| `CatalogRepositoryTest` | token plumbing, retry after a refused token |
| `CatalogViewModelTest` | paging, end of list, in-flight guard, clear on sign-out |
| `MediaItemMapperTest` | local vs remote track identity, unreachable marker URI |
| `RemoteStreamResolverTest` | ticket swap, local passthrough, DataSpec preserved |

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
- [x] Sign in to a WaveFlow server (session, refresh, sign-out)
- [x] Browse the server catalogue (albums, artists, paginated)
- [x] Stream from the server (ticketed URLs, seeking)
- [ ] Server user-data sync (playlists, favorites, ratings) — see below
- [ ] Android Auto (Media3 `MediaLibraryService`)

## Server

The **Server** tab signs in to a [WaveFlow
Server](https://github.com/InstaZDLL/waveflow-server), keeps the session alive
and browses its catalogue — albums, artists, and what each contains. Tapping a
remote track plays it. Nothing of the local library is sent anywhere.

The two sources stay separate by design: the tab is its own section rather than
a filter over the existing screens, and `RemoteAlbum` / `RemoteArtist` /
`RemoteSong` are distinct types from their local counterparts. Their ids are
UUIDs rather than `MediaStore` integers, and nothing can currently say that a
remote track is the same file as a local one.

Playback goes through a **stream ticket**: `POST /tracks/{id}/stream-ticket`
returns a URL that needs no `Authorization` header, which is what lets ExoPlayer
consume it directly — range requests for seeking included. The ticket is minted
when the player opens the track, not when the queue is built: it lives an hour,
and a long queue would outlast it before reaching its last tracks. A
`ResolvingDataSource` does the swap, so local files and remote tracks share one
player and one queue mechanism.

Listing endpoints return a bare array — no total, no cursor — so the end of a
list is inferred from a page shorter than requested. Cover art is not shown:
the v2 API exposes an `artwork_hash` but no endpoint serving the image; only
the Subsonic facade does, behind its own separate credential.

Sign-in posts to `/api/v2/auth/login` with the device model as the session
name, so the server lists it among the account's devices. The access token
lasts fifteen minutes and is renewed through `/api/v2/auth/refresh`; the
refresh token **rotates** on every use, which is why all token work is
serialised behind one mutex — two concurrent renewals would start from the same
token and one would be rejected, dropping a session that was perfectly valid.

Tokens live in a DataStore, protected by the app sandbox rather than by
encryption: `security-crypto` never left alpha and is no longer maintained. A
rooted or unlocked device therefore exposes the refresh token — the mitigation
is that it can be revoked from the server. The file is excluded from cloud
backup and device transfer, so a stored token is not copied onto another
device; signing in there simply asks for the password again.

Cleartext HTTP is permitted, because a self-hosted server usually sits on a LAN
without a certificate. An address typed without a scheme is joined over HTTPS.

### Sync

The sync protocol — `/api/v2/sync/snapshot`, `/changes`, `/ack` and a wake-up
socket — already exists server-side, specified in its `RFC-003`. Syncing the
**local** library is nonetheless out of reach, and not because of this app: the
protocol carries server track UUIDs, and RFC-003 states it "never guesses a
local/server track match". Reconciliation is a later milestone with its own
RFC. Until that exists, local playlists stay local.

Whenever it does land, `playlist_songs` will need a Room migration: it keys on
`MediaStore` ids, which do not survive a device re-index, let alone identify a
track to a server.

## License

[GPL-3.0](LICENSE) — same as WaveFlow desktop.
