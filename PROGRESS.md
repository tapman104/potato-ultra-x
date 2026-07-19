# Section 1 — Current State

- **Current Package Name**: `com.potato.player`
- **App Version**: `1.0.0` (versionCode `1`, minSdk `24`, targetSdk `35`, compileSdk `35` defined in `app/build.gradle.kts`)
- **Tech Stack Summary**: Android native video player built with Kotlin `2.0.21`, Jetpack Compose (`composeBom = "2024.12.01"`, `activityCompose = "1.9.3"`, `navigationCompose = "2.8.5"`), Material 3 (`material3`, `material-icons-extended`), AndroidX Lifecycle & ViewModel (`2.8.7`), Kotlin Coroutines & Flows (`StateFlow` / `SharedFlow`), `kotlinx-serialization-json` (`1.7.3`), and the native **MPV C engine** via local Android AAR (`mpv-android-lib-v0.0.3.aar` / `is.xyz.mpv.MPVLib` JNI wrapper).

### Project Source Files (`app/src/main/`) & Build Configuration
- `app/build.gradle.kts`: Configures Android application build settings, splits (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`), Compose compiler features, and dependency wiring including `libs/` AAR repository.
- `gradle/libs.versions.toml`: Version catalog defining AndroidX libraries, Compose BOM (`2024.12.01`), Navigation (`2.8.5`), and Kotlin plugins (`2.0.21`).
- `app/src/main/AndroidManifest.xml`: Declares Android permissions (`READ_EXTERNAL_STORAGE`, `READ_MEDIA_VIDEO`), `MainActivity` configuration (`configChanges="orientation|screenSize|keyboardHidden"`, `adjustNothing`), and `ACTION_VIEW` intent filters for `video/*` and `file://` / `content://` schemes.
- `ROADMAP.md`: Project documentation tracking completed architectural features (Phases 1–5) and upcoming milestones (`Phase 6: App and Video Orientation & Sensor Logic`).
- `app/src/main/java/com/potato/player/AppNavigation.kt`: Implements type-safe navigation routing (`HomeRoute`, `PlayerRoute`) via `NavHost` and manages application-level backgrounding lifecycle (`ON_PAUSE`) by invoking `repository.pause()` and `repository.enterStandby()`.
- `app/src/main/java/com/potato/player/MainActivity.kt`: Entry point `ComponentActivity` hosting edge-to-edge Compose content, managing global `MpvEngine` / `PlayerRepository` instances, window flags (`FLAG_KEEP_SCREEN_ON`), and `ACTION_VIEW` intent dispatching.
- `app/src/main/java/com/potato/player/engine/MpvCommandExecutor.kt`: Manages a single-thread executor (`mpv-engine-thread`) executing thread-safe MPV JNI commands, property setters/getters, surface generation tracking, and coalesced/debounced seek operations.
- `app/src/main/java/com/potato/player/engine/MpvConstants.kt`: Internal object constants defining MPV property strings (`MpvProp`), property observation data formats (`MpvFmt`), and event IDs (`MpvEvent`).
- `app/src/main/java/com/potato/player/engine/MpvEngine.kt`: Orchestrates core MPV engine lifecycle (`create`, `init`, `enterStandby`, `destroy`), holding shared references to `executor`, `dispatcher`, `surface`, and `configurator`.
- `app/src/main/java/com/potato/player/engine/MpvEventDispatcher.kt`: Implements `MPVLib.EventObserver` to bridge asynchronous C-level property changes (`eventProperty`) and lifecycle events (`FILE_LOADED`, `PLAYBACK_RESTART`, `END_FILE`) to Kotlin `MpvEventListener` callbacks.
- `app/src/main/java/com/potato/player/engine/MpvOptionsConfigurator.kt`: Handles initial MPV engine configuration (`hwdec`, cache limits, rendering optimizations), copies font assets (`Roboto-Regular.ttf`) from APK to internal storage, and registers property observers (`pause`, `time-pos`, `duration`, `speed`, `hwdec-current`, etc.).
- `app/src/main/java/com/potato/player/engine/MpvSurface.kt`: Implements `SurfaceHolder.Callback` to attach and detach the Android `Surface` to MPV (`MPVLib.attachSurface` / `detachSurface`) and report surface dimensions (`android-surface-size`).
- `app/src/main/java/com/potato/player/engine/PlayerRepository.kt`: Central domain repository bridging `MpvEngine` actions (`loadFile`, `togglePlay`, `seekCommit`, track selection) and reactive playback state (`StateFlow`s for `isPaused`, `positionSec`, `durationSec`, `tracks`, etc.).
- `app/src/main/java/com/potato/player/engine/TrackInfo.kt`: Data class representing MPV audio or subtitle tracks (`id`, `type`, `title`, `lang`, `isExternal`) with a `displayLabel()` formatting helper.
- `app/src/main/java/com/potato/player/feature/home/HomeScreen.kt`: Compose UI for the home screen featuring an "Open Video" button using `ActivityResultContracts.GetContent()` and locking screen orientation to portrait on resume.
- `app/src/main/java/com/potato/player/feature/home/HomeViewModel.kt`: ViewModel holding selected video URI state and exposing `lockToPortrait(Activity?)` to request `SCREEN_ORIENTATION_PORTRAIT`.
- `app/src/main/java/com/potato/player/feature/player/PlayerScreen.kt`: Main video player screen composable (`315 lines`) hosting the video `SurfaceView`, touch/gesture detectors (double-tap seek, long-press 2x speed, single-tap toggle), top/bottom controls, right-side options sheet, and track selection dialogs.
- `app/src/main/java/com/potato/player/feature/player/PlayerViewModel.kt`: ViewModel (`PlayerViewModel`) collecting `PlayerRepository` flows into a unified `PlayerUiState` (`StateFlow`), exposing player control methods, dialog visibility states, and external subtitle `content://` to file resolution (`resolveSubtitlePath`).
- `app/src/main/java/com/potato/player/feature/player/PlayerViewModelFactory.kt`: Custom `ViewModelProvider.Factory` for constructing `PlayerViewModel` with the singleton `PlayerRepository`.
- `app/src/main/java/com/potato/player/feature/player/controls/AudioTrackDialog.kt`: Compose modal dialog displaying a radio-button list of available audio streams (`TrackInfo`) and reporting selection to switch active audio track (`aid`).
- `app/src/main/java/com/potato/player/feature/player/controls/DoubleTapSeekOverlay.kt`: Animated visual overlay rendering curved left/right ripple bubbles (`-10s` / `+10s` icons) during double-tap seek gestures (`DoubleTapSeekState`).
- `app/src/main/java/com/potato/player/feature/player/controls/HoldToFastForward.kt`: Animated top-center glowing card banner (`⚡ 2x Fast Forwarding`) displayed during long-press fast forward gestures.
- `app/src/main/java/com/potato/player/feature/player/controls/PlayerBottomControls.kt`: Bottom control overlay (`222 lines`) containing current time / duration text, floating live time preview bubble during scrubbing, progress `Slider` with visual buffer indicator track, and play/pause button.
- `app/src/main/java/com/potato/player/feature/player/controls/PlayerControlsStyles.kt`: Internal design tokens and shared composable styles (circular black translucent `iconButtonModifier`, white/transparent `rememberSliderColors()`).
- `app/src/main/java/com/potato/player/feature/player/controls/PlayerDecoderDialog.kt`: Modal dialog presenting video decoder choices (`mediacodec,mediacodec-copy,no`, `mediacodec`, `no`) mapped to user-friendly options (`HW+`, `HW`, `SW`).
- `app/src/main/java/com/potato/player/feature/player/controls/PlayerQuickActions.kt`: Horizontal row of top-right quick action icon buttons (`AudioTrack`, `SubtitleTrack`, `HW/SW status`, `MoreVert`) displayed inside `PlayerTopBar`.
- `app/src/main/java/com/potato/player/feature/player/controls/PlayerRightSideSheet.kt`: Animated right-side drawer (`312 lines`) for player settings including quick audio/subtitle dialog triggers, preset speed buttons (`0.25x` to `3.0x`), continuous fine-tuning speed slider, and speed reset button.
- `app/src/main/java/com/potato/player/feature/player/controls/PlayerTopBar.kt`: Top navigation header composable showing back arrow, single-line truncated video title (`fileName`), and `PlayerQuickActions` icons.
- `app/src/main/java/com/potato/player/feature/player/controls/SubtitleTrackDialog.kt`: Compose modal dialog displaying available subtitle streams (`TrackInfo`), an "Off" option (`sid = -1`), and a document picker launcher (`ActivityResultContracts.OpenDocument()`) for loading external subtitle files.
- `app/src/main/java/com/potato/player/util/ContextExtensions.kt`: Extension function `Context.findActivity()` recursively traversing `ContextWrapper` hierarchy to retrieve the host `Activity`.
- `app/src/main/java/com/potato/player/util/TimeFormatter.kt`: Utility object formatting milliseconds into `MM:SS` or `HH:MM:SS` strings (`formatMs`).

---

# Section 2 — Architecture Assessment

- **Files already too large and needing a split (`> 300 lines`)**:
  - `app/src/main/java/com/potato/player/feature/player/PlayerScreen.kt` (`315 lines`): Exceeds the 300-line limit. It combines `AndroidView` surface creation, multi-pointer gesture processing (`pointerInput` for taps, double-taps, and long-presses), UI overlay state (`controlsVisible`, `doubleTapSeekState`), four distinct modal dialog/sheet invocations (`PlayerDecoderDialog`, `AudioTrackDialog`, `SubtitleTrackDialog`, `PlayerRightSideSheet`), lifecycle orientation locking (`lockToLandscape`), and synchronous `ContentResolver` file name resolution (`resolveFileName`).
  - `app/src/main/java/com/potato/player/feature/player/controls/PlayerRightSideSheet.kt` (`312 lines`): Exceeds the 300-line limit. It packs a multi-section settings drawer (header, quick track selection buttons, 2x4 playback speed presets grid, continuous fine-tuning speed slider, speed reset button, slide/fade layout animations, and touch-trapping `Surface`) into a single file.

- **Missing abstraction layers visible from the current code**:
  - **Orientation & System Window Controller Layer**: Currently, `HomeScreen.kt:L33,L37` and `PlayerScreen.kt:L58,L62` directly grab `Activity` via `LocalContext.current.findActivity()` and mutate `requestedOrientation` on `ON_RESUME`. In addition, `MainActivity.kt:L28-L33,L50-L59` directly manipulates `WindowInsetsControllerCompat` and `FLAG_KEEP_SCREEN_ON`. A dedicated abstraction (`OrientationManager` / `SystemWindowHelper`) is missing to encapsulate `requestedOrientation`, screen keep-alive flags, and sensor/aspect-ratio auto-rotation without passing `Activity` instances around.
  - **Async Media Metadata & File Resolution Service (`MediaMetadataRepository`)**: Duplicated synchronous `ContentResolver` query logic exists across `HomeScreen.kt:L68-L87` (`resolveTitle`) and `PlayerScreen.kt:L291-L314` (`resolveFileName`). Furthermore, `PlayerViewModel.kt:L169-L196` (`resolveSubtitlePath`) directly queries `ContentResolver` and performs blocking synchronous disk I/O (`input.copyTo(output)`) copying `content://` streams to `cacheDir` right inside the ViewModel. A dedicated background repository/service using `Dispatchers.IO` is missing.
  - **Gesture Overlay Layer (`PlayerGestureOverlay`)**: `PlayerScreen.kt:L127-L177` embeds raw `pointerInput` detectors (`detectTapGestures` and `awaitEachGesture`) directly inside the main box layout alongside UI overlays and modals. Separating gesture processing from visual presentation would decouple touch state (`DoubleTapSeekState`, `isLongPressActive`) from screen structure.

- **Technical debt & anti-patterns**:
  - **Code Duplication across Screens**: `HomeScreen.kt:L68-L87` (`resolveTitle`) and `PlayerScreen.kt:L291-L314` (`resolveFileName`) contain nearly identical copy-pasted code querying `ContentResolver` for `OpenableColumns.DISPLAY_NAME` and parsing URI path segments.
  - **Blocking Disk I/O inside ViewModel / UI Thread**: In `PlayerViewModel.kt:L163-L196`, `onLoadExternalSubtitle(uri, context)` calls `resolveSubtitlePath(uri, context)` directly on the calling thread. When `uri.scheme == "content"`, `resolveSubtitlePath` opens `contentResolver.openInputStream(uri)` and copies the stream to `cacheDir` (`input.copyTo(output)`) synchronously without switching to `Dispatchers.IO`, risking UI hangs and ANRs on large external `.srt` / `.ass` files.
  - **ViewModels Holding & Mutating Android `Activity` (`HomeViewModel`, `PlayerViewModel`)**: `HomeViewModel.kt:L15-L17` (`lockToPortrait(activity: Activity?)`) and `PlayerViewModel.kt:L72-L74` (`lockToLandscape(activity: Activity?)`) accept Android `Activity` references to mutate `activity?.requestedOrientation`. Passing `Activity` references into ViewModels creates severe memory leak risks across configuration changes and violates clean architecture boundaries.
  - **Direct Surface & Engine Exposure (`PlayerScreen` & `PlayerViewModel.surface`)**: `PlayerViewModel.kt:L46` (`val surface: MpvSurface get() = repository.engine.surface`) exposes the low-level `MpvSurface` object directly to `PlayerScreen.kt:L101,L120`, allowing UI layer composables to register `SurfaceHolder.Callback` (`sv.holder.addCallback(viewModel.surface)`) directly.
  - **Redundant / Overlapping Modal State (`showMoreMenu` vs `showSpeedDialog`)**: `PlayerUiState` (`PlayerViewModel.kt:L36-L37`) maintains both `showSpeedDialog` and `showMoreMenu`. However, `PlayerRightSideSheet` (`PlayerScreen.kt:L277`) opens when `visible = uiState.showMoreMenu || uiState.showSpeedDialog`. When dismissed, `onDismiss` calls both `viewModel.onMoreMenuDismiss()` and `viewModel.onDismissSpeedDialog()` (`L284-L285`). When opened via top bar quick action (`onMoreOptions = { viewModel.onMoreMenuToggle() }`), `showMoreMenu` toggles independently of `showSpeedDialog`, creating awkward state synchronization.
  - **Synchronous Thread Blocking in `MpvCommandExecutor` (`getPropertyInt` / `getPropertyString`)**: In `MpvCommandExecutor.kt:L114,L128`, `getPropertyInt()` and `getPropertyString()` check `if (Thread.currentThread() == engineThread)` and otherwise execute `executor.submit<Int?> { ... }.get()`. Calling `.get()` blocks the calling thread waiting for the single `mpv-engine-thread`. If called on the main thread, this causes frame drops; if called when the single-thread executor is busy with long tasks, it risks deadlocks.

---

# Section 3 — Feature Roadmap

### 1. Player Screen & Right Side Sheet Structural Split
- Why this order: Both `PlayerScreen.kt` (`315 lines`) and `PlayerRightSideSheet.kt` (`312 lines`) currently exceed the `300 lines` refactor trigger. They must be split before adding new orientation or sensor logic to avoid creating unmaintainable God classes.
- Split first: `app/src/main/java/com/potato/player/feature/player/PlayerScreen.kt` — extract all modal dialog and sheet invocations (`PlayerDecoderDialog`, `AudioTrackDialog`, `SubtitleTrackDialog`, `PlayerRightSideSheet` at `L250-L288`) into `PlayerModals.kt`. Also split `app/src/main/java/com/potato/player/feature/player/controls/PlayerRightSideSheet.kt` by extracting the tracks selection section (`L119-L168`) and playback speed section (`L173-L306`) into individual section composables.
- New files:
  - `app/src/main/java/com/potato/player/feature/player/PlayerModals.kt` — Dedicated composable hosting `PlayerDecoderDialog`, `AudioTrackDialog`, `SubtitleTrackDialog`, and `PlayerRightSideSheet`.
  - `app/src/main/java/com/potato/player/feature/player/controls/sheet/PlayerTracksSection.kt` — Composable rendering the quick Audio/Subtitle track selection buttons (`L119-L168` from `PlayerRightSideSheet`).
  - `app/src/main/java/com/potato/player/feature/player/controls/sheet/PlayerSpeedSection.kt` — Composable rendering the speed presets grid (`speedPresets`), fine-tuning slider, and reset button (`L173-L306` from `PlayerRightSideSheet`).
- Modified files:
  - `app/src/main/java/com/potato/player/feature/player/PlayerScreen.kt` — Replace inline dialog declarations (`L250-L288`) with a single call to `PlayerModals(...)`, bringing `PlayerScreen.kt` well under 250 lines.
  - `app/src/main/java/com/potato/player/feature/player/controls/PlayerRightSideSheet.kt` — Delegate internal body sections to `PlayerTracksSection` and `PlayerSpeedSection`, reducing line count to ~130 lines.

### 2. Async Media Metadata & Subtitle I/O Repository (`MediaMetadataRepository`)
- Why this order: Resolves the explicit `TODO (Phase 6): Move title resolution to a background dispatcher/coroutine...` (`HomeScreen.kt:L67`), eliminates synchronous `ContentResolver` queries (`HomeScreen.kt:L68-L87`, `PlayerScreen.kt:L291-L314`), and fixes the main-thread disk blocking bug during external subtitle loading (`PlayerViewModel.kt:L169-L196`).
- New files:
  - `app/src/main/java/com/potato/player/util/MediaMetadataRepository.kt` — Coroutine-based utility executing `OpenableColumns.DISPLAY_NAME` queries and external subtitle file copying (`contentResolver.openInputStream(uri) -> copyTo`) asynchronously on `Dispatchers.IO`.
- Modified files:
  - `app/src/main/java/com/potato/player/feature/home/HomeScreen.kt` — Remove private `resolveTitle()` helper (`L68-L87`) and delegate title resolution to `MediaMetadataRepository` inside a `LaunchedEffect` or background task.
  - `app/src/main/java/com/potato/player/feature/player/PlayerScreen.kt` — Remove duplicated `resolveFileName()` helper (`L291-L314`).
  - `app/src/main/java/com/potato/player/feature/player/PlayerViewModel.kt` — Move `resolveSubtitlePath()` (`L169-L196`) to run within `viewModelScope.launch(Dispatchers.IO)` using `MediaMetadataRepository`, keeping UI thread completely responsive.

### 3. Smart Screen Orientation Handling & Auto-Rotation (`OrientationManager.kt`)
- Why this order: Fulfills `Phase 6: App and Video Orientation & Sensor Logic` from `ROADMAP.md`. Builds cleanly upon refactored `PlayerScreen.kt` and removes the anti-pattern of passing `Activity` to ViewModels (`HomeViewModel.kt:L15`, `PlayerViewModel.kt:L72`).
- New files:
  - `app/src/main/java/com/potato/player/util/OrientationManager.kt` — Encapsulates `Activity.requestedOrientation` switching (`SCREEN_ORIENTATION_PORTRAIT`, `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`, `SCREEN_ORIENTATION_SENSOR_PORTRAIT`, `SCREEN_ORIENTATION_SENSOR`) directly from UI layer context without exposing `Activity` to ViewModels.
- Modified files:
  - `app/src/main/java/com/potato/player/engine/MpvConstants.kt` — Add property constants `VIDEO_PARAMS_ASPECT = "video-params/aspect"`, `VIDEO_PARAMS_W = "video-params/w"`, and `VIDEO_PARAMS_H = "video-params/h"`.
  - `app/src/main/java/com/potato/player/engine/MpvOptionsConfigurator.kt` — Register `MPVLib.observeProperty` for video aspect ratio and dimension properties inside `registerPropertyObservers()`.
  - `app/src/main/java/com/potato/player/engine/PlayerRepository.kt` — Observe aspect ratio updates inside `onPropertyChange()` and expose `videoAspect: StateFlow<Float?>` and `autoOrientationMode: StateFlow<Int>` (`SENSOR_LANDSCAPE` vs `SENSOR_PORTRAIT`).
  - `app/src/main/java/com/potato/player/feature/home/HomeScreen.kt` — Replace `viewModel.lockToPortrait(activity)` (`L33,L37`) with `OrientationManager.lockToPortrait(context)`.
  - `app/src/main/java/com/potato/player/feature/home/HomeViewModel.kt` — Remove `lockToPortrait(Activity?)` (`L15-L17`) to eliminate `Activity` leak vector.
  - `app/src/main/java/com/potato/player/feature/player/PlayerScreen.kt` — Replace static `viewModel.lockToLandscape(activity)` (`L58,L62`) with reactive orientation observation (`OrientationManager.setOrientation(context, uiState.orientationMode)`).
  - `app/src/main/java/com/potato/player/feature/player/PlayerViewModel.kt` — Remove `lockToLandscape(Activity?)` (`L72-L74`), observe `repository.autoOrientationMode`, and expose `orientationMode: StateFlow<OrientationMode>` (`Auto`, `ForceLandscape`, `ForcePortrait`, `Sensor`).

### 4. Manual Rotation Lock Toggle in Top Bar & Right Side Sheet
- Why this order: Completes the manual rotation lock requirement of `Phase 6` (`ROADMAP.md:L115`), building directly on `OrientationManager` and `orientationMode` state introduced in Feature 3.
- New files:
  - `app/src/main/java/com/potato/player/feature/player/controls/sheet/PlayerOrientationSection.kt` — Composable section inside `PlayerRightSideSheet` providing chips/buttons for `Auto (Aspect)`, `Force Landscape`, `Force Portrait`, and `Sensor`.
- Modified files:
  - `app/src/main/java/com/potato/player/feature/player/controls/PlayerQuickActions.kt` — Add an orientation toggle button (`IconButton` showing screen rotation/lock icon) between decoder selector and more (`MoreVert`) button (`L52-L63`).
  - `app/src/main/java/com/potato/player/feature/player/controls/PlayerTopBar.kt` — Accept and forward `onToggleOrientation` callback to `PlayerQuickActions`.
  - `app/src/main/java/com/potato/player/feature/player/controls/PlayerRightSideSheet.kt` — Include `PlayerOrientationSection` in the settings drawer.
  - `app/src/main/java/com/potato/player/feature/player/PlayerScreen.kt` — Wire `viewModel.toggleOrientationMode()` down to `PlayerTopBar`.
  - `app/src/main/java/com/potato/player/feature/player/PlayerViewModel.kt` — Add `toggleOrientationMode()` action cycling through `Auto -> ForceLandscape -> ForcePortrait -> Sensor`.

---

# Section 4 — Refactor Triggers

1. **File Line Count Trigger (`> 300 Lines`)**: Any Kotlin source file exceeding **300 lines** MUST be split into smaller, focused component files before new features are added. (Observed trigger cases: `PlayerScreen.kt` at `315 lines` and `PlayerRightSideSheet.kt` at `312 lines`).
2. **Multi-Domain Composable Screen Trigger (`Single Responsibility Principle`)**: When a top-level `@Composable` screen (such as `PlayerScreen.kt`) directly hosts more than two distinct architectural responsibilities (e.g., raw `pointerInput` gesture handling, top/bottom overlay rendering, and modal dialog state management), these domains must be extracted into standalone composable files (`PlayerGestureOverlay.kt`, `PlayerModals.kt`).
3. **Multi-Section Bottom/Side Sheet Trigger (`PlayerRightSideSheet.kt`)**: When a drawer or sheet composable expands beyond two distinct logical configuration groups (`TRACKS` and `PLAYBACK SPEED` in `PlayerRightSideSheet.kt:L119-L306`), every distinct configuration section must be extracted into its own composable file (`PlayerTracksSection.kt`, `PlayerSpeedSection.kt`).
4. **ViewModel Context/Activity Reference Trigger (`Anti-Leak Rule`)**: Any `ViewModel` method that accepts, retains, or manipulates an `Activity` or `Context` parameter (`HomeViewModel.lockToPortrait(Activity?)` at `L15` and `PlayerViewModel.lockToLandscape(Activity?)` at `L72`) MUST be refactored immediately. ViewModels must only expose reactive state (`StateFlow`), while `Activity` and `Window` mutations (`requestedOrientation`, `window.addFlags`) must be handled by UI layer observers (`DisposableEffect` / `LifecycleEventObserver`) or utility helpers (`OrientationManager`).
5. **Main-Thread Synchronous I/O Trigger (`Dispatchers.IO` Rule)**: Any file reading, disk copying, `ContentResolver` querying (`PlayerViewModel.resolveSubtitlePath` at `L169-L196`, `PlayerScreen.resolveFileName` at `L291-L314`), or blocking synchronous `.get()` calls on `ExecutorService` (`MpvCommandExecutor.getPropertyInt/String` at `L114,L128`) occurring on the main/UI thread must be extracted into an asynchronous repository or background task executing on `Dispatchers.IO`.
6. **Code Duplication Trigger (`DRY` across Screens)**: When identical helper methods or logic blocks appear across two or more screen files (`HomeScreen.resolveTitle` at `L68-L87` and `PlayerScreen.resolveFileName` at `L291-L314`), they must be consolidated into a shared utility class in `com.potato.player.util`.

---

# Section 5 — Known Bugs & Fragile Code

- `app/src/main/java/com/potato/player/feature/player/PlayerViewModel.kt:L163-L196` (`onLoadExternalSubtitle` / `resolveSubtitlePath`)
  - *Risk*: Synchronous disk I/O (`input.copyTo(output)`) on `contentResolver.openInputStream(uri)` executes directly inside `ViewModel` on the main/calling thread without `Dispatchers.IO`, risking severe UI stutter or ANR when copying large external `.srt` / `.ass` subtitle files.
- `app/src/main/java/com/potato/player/engine/MpvCommandExecutor.kt:L114,L128` (`getPropertyInt` / `getPropertyString`)
  - *Risk*: Synchronous `executor.submit<T> { ... }.get()` blocks whatever thread invokes `getPropertyInt` / `getPropertyString` while waiting for `mpv-engine-thread`. If invoked on the main thread, it causes frame drops; if `engineThread` is busy, it risks thread starvation or deadlocks.
- `app/src/main/java/com/potato/player/feature/player/PlayerScreen.kt:L291-L314` & `app/src/main/java/com/potato/player/feature/home/HomeScreen.kt:L68-L87` (`resolveFileName` / `resolveTitle`)
  - *Risk*: Synchronous `contentResolver.query()` runs directly inside `@Composable` functions during layout/recomposition, violating Compose performance guidelines and risking main-thread frame drops during navigation.
- `app/src/main/java/com/potato/player/feature/player/PlayerScreen.kt:L147-L157` (`onDoubleTap` inside `pointerInput`)
  - *Risk*: Race condition in `DoubleTapSeekState` accumulation (`doubleTapSeekState!!.totalSeconds + 10`); if `doubleTapSeekState` resets to null mid-gesture (`L88-L91`) or across alternating left/right zone taps, non-atomic state updates can lose seek steps or throw `NullPointerException` if non-null assertion `!!` fails right after null check.
- `app/src/main/java/com/potato/player/feature/player/PlayerScreen.kt:L133-L142,L165-L174` & `app/src/main/java/com/potato/player/engine/PlayerRepository.kt:L80-L94` (`HoldToFastForward` / `startFastForward` / `stopFastForward`)
  - *Risk*: If the user initiates a long-press while already running at a custom speed (e.g., `1.5x`), `normalPlaybackSpeed` stores `1.5x` (`L82`). However, if an `onPropertyChange(MpvProp.SPEED)` event arrives while `_isFastForwarding.value == true` (`L223`) or if `setPlaybackSpeed()` is invoked mid-hold (`L101`), `normalPlaybackSpeed` can be overwritten, failing to restore the correct original speed on release.
- `app/src/main/java/com/potato/player/feature/player/PlayerScreen.kt:L166-L175` (`awaitEachGesture` / `isLongPressActive`)
  - *Risk*: If a long-press touch gesture is interrupted by system dialogs, incoming calls, or multi-touch pointer cancellation, `awaitPointerEvent()` exits its loop when pointers are cancelled, but `isLongPressActive = false` and `viewModel.stopFastForward()` (`L170-L173`) might not execute reliably if the pointer event stream terminates via `PointerEventPass` cancellation without triggering `onRelease`.
- `app/src/main/java/com/potato/player/feature/home/HomeViewModel.kt:L15-L17` & `app/src/main/java/com/potato/player/feature/player/PlayerViewModel.kt:L72-L74` (`lockToPortrait` / `lockToLandscape`)
  - *Risk*: ViewModels directly accepting and operating on Android `Activity` references (`activity?.requestedOrientation`) create memory leak vectors across activity recreation (`Activity` destruction during screen rotation or theme changes).
- `app/src/main/java/com/potato/player/MainActivity.kt:L88-L104` (`handleViewIntent`)
  - *Risk*: Taking persistable URI permissions via `takePersistableUriPermission` (`L92`) without verifying `Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION` first can throw `SecurityException` (currently caught and swallowed at `L95`), and checking readability via `openFileDescriptor(uri, "r")?.close()` (`L100`) performs main-thread disk I/O immediately before `navController.navigate()`.
- `app/src/main/java/com/potato/player/engine/MpvEngine.kt:L72-L91` (`destroy`)
  - *Risk*: In `destroy()`, `executor.execute { ... }` (`L80`) submits engine teardown (`MPVLib.destroy()`), and `executor.shutdown()` (`L90`) is executed immediately on the calling thread. If any pending tasks or late MPV callbacks try to submit tasks right when `shutdown()` is called, they throw `RejectedExecutionException`, or teardown might interlock with late property observers (`dispatcher`).
- `app/src/main/java/com/potato/player/feature/player/PlayerScreen.kt:L277-L287` & `app/src/main/java/com/potato/player/feature/player/PlayerViewModel.kt:L115-L145` (`showMoreMenu` vs `showSpeedDialog`)
  - *Risk*: When `PlayerRightSideSheet` is visible (`visible = uiState.showMoreMenu || uiState.showSpeedDialog`), closing the sheet via `onDismiss` invokes both `viewModel.onMoreMenuDismiss()` and `viewModel.onDismissSpeedDialog()` (`L284-L285`). When `showSpeedDialog` is true and the user taps the more button on `PlayerTopBar` (`onMoreOptions = { viewModel.onMoreMenuToggle() }`), `showMoreMenu` is toggled (`L116`) while `showSpeedDialog` remains true, causing redundant and conflicting state updates.

---

# Section 6 — Next Immediate Action

**Task: Extract Modal Dialogs & Sheets from `PlayerScreen.kt` into `PlayerModals.kt` and Split `PlayerRightSideSheet.kt` into Section Composables**

- **Target Files**:
  - Modify: `app/src/main/java/com/potato/player/feature/player/PlayerScreen.kt` (`lines 250–288`)
  - Create: `app/src/main/java/com/potato/player/feature/player/PlayerModals.kt`
  - Modify: `app/src/main/java/com/potato/player/feature/player/controls/PlayerRightSideSheet.kt` (`lines 119–306`)
  - Create: `app/src/main/java/com/potato/player/feature/player/controls/sheet/PlayerTracksSection.kt`
  - Create: `app/src/main/java/com/potato/player/feature/player/controls/sheet/PlayerSpeedSection.kt`

- **Exact Changes Required**:
  1. Create `PlayerModals.kt` in `com.potato.player.feature.player` containing a new `@Composable fun PlayerModals(uiState: PlayerUiState, viewModel: PlayerViewModel, showDecoderDialog: Boolean, onDismissDecoderDialog: () -> Unit)`.
  2. Move lines `250–288` of `PlayerScreen.kt` (`PlayerDecoderDialog`, `AudioTrackDialog`, `SubtitleTrackDialog`, and `PlayerRightSideSheet` invocations along with their callbacks to `viewModel.setDecoder`, `viewModel.onSelectAudioTrack`, `viewModel.onLoadExternalSubtitle`, `viewModel.setPlaybackSpeed`, and dismissal methods) directly into `PlayerModals.kt`.
  3. In `PlayerScreen.kt` (around lines `250`), replace the extracted 39 lines of modal declarations with a single clean invocation: `PlayerModals(uiState = uiState, viewModel = viewModel, showDecoderDialog = showDecoderDialog, onDismissDecoderDialog = { showDecoderDialog = false })`. This immediately drops `PlayerScreen.kt` from `315 lines` down to `~276 lines` (below our `300-line` Refactor Trigger).
  4. Create `PlayerTracksSection.kt` in package `com.potato.player.feature.player.controls.sheet` with `@Composable fun PlayerTracksSection(onShowAudioDialog: () -> Unit, onShowSubtitleDialog: () -> Unit, accentColor: Color)`. Move lines `119–168` from `PlayerRightSideSheet.kt` into this composable.
  5. Create `PlayerSpeedSection.kt` in package `com.potato.player.feature.player.controls.sheet` with `@Composable fun PlayerSpeedSection(currentSpeed: Double, onSelectSpeed: (Double) -> Unit, accentColor: Color)`. Move `private val speedPresets` (`lines 31–34`) and lines `173–306` from `PlayerRightSideSheet.kt` into this composable.
  6. In `PlayerRightSideSheet.kt`, replace lines `119–168` with `PlayerTracksSection(onShowAudioDialog = onShowAudioDialog, onShowSubtitleDialog = onShowSubtitleDialog, accentColor = accentColor)` and lines `173–306` with `PlayerSpeedSection(currentSpeed = currentSpeed, onSelectSpeed = onSelectSpeed, accentColor = accentColor)`. This immediately drops `PlayerRightSideSheet.kt` from `312 lines` down to `~130 lines`.

- **Why this unblocks everything after it**:
  By bringing both `PlayerScreen.kt` (`315 lines -> ~276 lines`) and `PlayerRightSideSheet.kt` (`312 lines -> ~130 lines`) below the `300-line` Refactor Trigger, we establish a clean, modular component boundary. When we subsequently begin `Phase 6: App and Video Orientation & Sensor Logic`, the new `OrientationManager.kt` auto-rotation logic and the new `PlayerOrientationSection.kt` manual rotation controls can be seamlessly plugged into `PlayerScreen` and `PlayerRightSideSheet` without creating bloated God classes or interlocked state conflicts.
