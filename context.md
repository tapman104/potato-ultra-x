# CONTEXT.md — Potato Player Architecture Reference

# Package: com.potato.player

# Last updated: see PROGRESS.md for current phase

---

# 1. Architecture Layers

```
┌─────────────────────────────────────────────────────────────────┐
│                        COMPOSE UI LAYER                         │
│                                                                 │
│  feature/home/        feature/player/          AppNavigation.kt │
│  HomeScreen.kt        PlayerScreen.kt          MainActivity.kt  │
│  HomeViewModel.kt     PlayerViewModel.kt                        │
│                       PlayerViewModelFactory.kt                 │
│                       PlayerUiState.kt                          │
│                                                                 │
│  feature/player/controls/                                       │
│  PlayerTopBar.kt         PlayerBottomControls.kt                │
│  PlayerQuickActions.kt   PlayerRightSideSheet.kt                │
│  AudioTrackDialog.kt     SubtitleTrackDialog.kt                 │
│  PlayerDecoderDialog.kt  DoubleTapSeekOverlay.kt                │
│  HoldToFastForward.kt    PlayerControlsStyles.kt                │
│                                                                 │
│  KNOWS ABOUT: ViewModels, PlayerUiState, util/, NavController   │
│  NEVER KNOWS: MpvEngine, MpvCommandExecutor, MPVLib, Surface    │
├─────────────────────────────────────────────────────────────────┤
│                      VIEWMODEL LAYER                            │
│                                                                 │
│  HomeViewModel.kt                                               │
│  PlayerViewModel.kt  ←  collects StateFlows from Repository     │
│  PlayerViewModelFactory.kt                                      │
│                                                                 │
│  KNOWS ABOUT: PlayerRepository, PlayerUiState, util/,          │
│               Android Lifecycle, viewModelScope, Dispatchers    │
│  NEVER KNOWS: MpvEngine, MpvCommandExecutor, MpvSurface,       │
│               MPVLib, any androidx.compose.* class, Activity    │
├─────────────────────────────────────────────────────────────────┤
│                      REPOSITORY LAYER                           │
│                                                                 │
│  engine/PlayerRepository.kt                                     │
│                                                                 │
│  KNOWS ABOUT: MpvEngine, MpvCommandExecutor, TrackInfo,        │
│               StateFlow, SharedFlow, Kotlin Coroutines          │
│  NEVER KNOWS: ViewModel, Compose, Activity, Context, View       │
├─────────────────────────────────────────────────────────────────┤
│                       ENGINE LAYER                              │
│                                                                 │
│  engine/MpvEngine.kt            — lifecycle orchestrator        │
│  engine/MpvCommandExecutor.kt   — single-thread JNI dispatcher  │
│  engine/MpvEventDispatcher.kt   — C→Kotlin event bridge         │
│  engine/MpvOptionsConfigurator.kt — MPV property setup          │
│  engine/MpvSurface.kt           — Surface attachment            │
│  engine/MpvConstants.kt         — MpvProp / MpvFmt / MpvEvent  │
│  engine/TrackInfo.kt            — data class only               │
│                                                                 │
│  KNOWS ABOUT: MPVLib (JNI), Android Surface, Context (assets)  │
│  NEVER KNOWS: ViewModel, Compose, NavController, Activity,      │
│               any class from feature/ or util/                  │
├─────────────────────────────────────────────────────────────────┤
│                      NATIVE LAYER                               │
│                                                                 │
│  libs/mpv-android-lib-v0.0.3.aar  (is.xyz.mpv.MPVLib JNI)     │
│  libmpv.so / libavcodec.so / libavformat.so / libavutil.so     │
│  libswresample.so / libswscale.so / libavfilter.so             │
│                                                                 │
│  KNOWS ABOUT: nothing in this codebase                          │
└─────────────────────────────────────────────────────────────────┘
```

Direction of allowed knowledge: **downward only**.
UI → ViewModel → Repository → Engine → Native.
No layer may import anything from a layer above it.

---

# 2. Dependency Rules (What Can See What)

## `engine/` package

| | Rule |
|---|---|
| May import | `is.xyz.mpv.MPVLib`, `android.view.Surface`, `android.content.Context` (for asset copying only in `MpvOptionsConfigurator`), `kotlinx.coroutines.*`, `android.os.Handler` |
| Must never import | `androidx.compose.*`, `androidx.navigation.*`, `androidx.lifecycle.ViewModel`, `androidx.activity.*`, any class from `feature/`, any class from `util/` |
| Permitted Android framework | `Surface`, `SurfaceHolder`, `Context` (asset access only), `Handler`, `Looper` |
| Forbidden Android framework | `Activity`, `Fragment`, `View`, `Window`, `WindowManager` |

Concrete examples:

- `MpvEngine` may call `MPVLib.create(context)` — `Context` is needed for MPV init
- `MpvOptionsConfigurator` may use `context.assets` to copy `Roboto-Regular.ttf` — this is the only permitted `Context` use in engine
- `PlayerRepository` must never import `android.app.Activity`
- `TrackInfo` must contain only primitive types and `String` — no Android classes

## `feature/player/` and `feature/home/` (ViewModel files only)

| | Rule |
|---|---|
| May import | `PlayerRepository`, `TrackInfo`, `StateFlow`, `MutableStateFlow`, `viewModelScope`, `Dispatchers`, `androidx.lifecycle.ViewModel`, `android.content.ContentResolver`, `android.net.Uri` |
| Must never import | `MpvEngine`, `MpvCommandExecutor`, `MpvSurface`, `MpvEventDispatcher`, `MPVLib`, `androidx.compose.*`, `android.app.Activity` |
| Permitted Android framework | `ContentResolver`, `Uri`, `Context` (via `ApplicationContext` only, never `Activity`) |
| Forbidden Android framework | `Activity`, `Window`, `WindowManager`, `requestedOrientation` |

Concrete examples:

- `PlayerViewModel` may call `repository.togglePlay()` — repository is the correct boundary
- `PlayerViewModel` may use `applicationContext.contentResolver` for subtitle resolution — application context is safe
- `PlayerViewModel` must never call `MpvCommandExecutor.seekTo()` directly — all engine commands go through `PlayerRepository`
- `PlayerViewModel.lockToLandscape(activity: Activity?)` is **banned** — see Section 7

## `feature/player/controls/` and `feature/home/` (Composable files only)

| | Rule |
|---|---|
| May import | `PlayerViewModel`, `HomeViewModel`, `PlayerUiState`, `TrackInfo`, `androidx.compose.*`, `androidx.compose.material3.*`, `util/TimeFormatter`, `util/ContextExtensions` |
| Must never import | `PlayerRepository`, `MpvEngine`, `MpvCommandExecutor`, `MpvSurface`, `MPVLib`, `MpvProp`, `MpvEvent`, `MpvFmt` |
| Permitted Android framework | `Activity` (via `LocalContext.current.findActivity()` in UI only), `ContentResolver` (via launcher contracts only — `ActivityResultContracts`) |
| Forbidden Android framework | Direct `ContentResolver.query()` inside `@Composable` body during recomposition |

## `util/` package

| | Rule |
|---|---|
| May import | `android.content.Context`, `android.content.ContextWrapper`, `android.app.Activity`, `android.net.Uri`, `android.content.ContentResolver` |
| Must never import | Any class from `engine/`, any class from `feature/`, `androidx.compose.*` |

## `AppNavigation.kt`

| | Rule |
|---|---|
| May import | `PlayerRepository`, `MpvEngine`, `HomeRoute`, `PlayerRoute`, `NavHostController`, `androidx.navigation.*`, `androidx.compose.*` |
| Note | `AppNavigation` is the one permitted place where engine objects are passed into the Compose tree as constructor arguments. They must not be accessed directly — only forwarded to `ViewModelFactory`. |

---

# 3. State Ownership Rules

## Playback State (source of truth: `PlayerRepository`)

| State | Owner | Readers | Writers | Flow |
|---|---|---|---|---|
| `isPaused` | `PlayerRepository._isPaused: MutableStateFlow<Boolean>` | `PlayerViewModel`, `AppNavigation` | `PlayerRepository` only (via `onPropertyChange`) | `StateFlow` → `collectAsState()` |
| `positionSec` | `PlayerRepository._positionSec: MutableStateFlow<Double>` | `PlayerViewModel` | `PlayerRepository` only | `StateFlow` → `PlayerUiState` |
| `durationSec` | `PlayerRepository._durationSec: MutableStateFlow<Double>` | `PlayerViewModel` | `PlayerRepository` only | `StateFlow` → `PlayerUiState` |
| `playbackSpeed` | `PlayerRepository._playbackSpeed: MutableStateFlow<Double>` | `PlayerViewModel` | `PlayerRepository` only | `StateFlow` → `PlayerUiState` |
| `isFastForwarding` | `PlayerRepository._isFastForwarding: MutableStateFlow<Boolean>` | `PlayerViewModel` | `PlayerRepository` only | `StateFlow` → `PlayerUiState` |
| `fileLoaded` | `PlayerRepository.fileLoaded: StateFlow<Boolean>` | `MainActivity`, `PlayerViewModel` | `PlayerRepository` only | `StateFlow` → `collectAsState()` |
| `tracks` | `PlayerRepository._tracks: MutableStateFlow<List<TrackInfo>>` | `PlayerViewModel` | `PlayerRepository` only | `StateFlow` → `PlayerUiState` |
| `currentAudioTrack` | `PlayerRepository._currentAudioTrack: MutableStateFlow<Int>` | `PlayerViewModel` | `PlayerRepository` only | `StateFlow` → `PlayerUiState` |
| `currentSubtitleTrack` | `PlayerRepository._currentSubtitleTrack: MutableStateFlow<Int>` | `PlayerViewModel` | `PlayerRepository` only | `StateFlow` → `PlayerUiState` |
| `hwdecActive` | `PlayerRepository._hwdecActive: MutableStateFlow<String>` | `PlayerViewModel` | `PlayerRepository` only | `StateFlow` → `PlayerUiState` |

## UI State (source of truth: `PlayerViewModel`)

| State | Owner | Readers | Writers | Flow |
|---|---|---|---|---|
| `uiState: PlayerUiState` | `PlayerViewModel._uiState: MutableStateFlow<PlayerUiState>` | `PlayerScreen`, all controls composables | `PlayerViewModel` only | `StateFlow` → `collectAsState()` in `PlayerScreen` |
| `showAudioDialog` | `PlayerUiState` field via `PlayerViewModel` | `PlayerScreen` → `PlayerModals` | `PlayerViewModel.onShowAudioDialog()` / `onDismissAudioDialog()` | Part of `uiState` StateFlow |
| `showSubtitleDialog` | `PlayerUiState` field via `PlayerViewModel` | `PlayerScreen` → `PlayerModals` | `PlayerViewModel.onShowSubtitleDialog()` / `onDismissSubtitleDialog()` | Part of `uiState` StateFlow |
| `showMoreMenu` | `PlayerUiState` field via `PlayerViewModel` | `PlayerScreen` → `PlayerModals` | `PlayerViewModel.onMoreMenuToggle()` / `onMoreMenuDismiss()` | Part of `uiState` StateFlow |
| `controlsVisible` | Local `var` in `PlayerScreen.kt` | `PlayerScreen` composables only | `PlayerScreen` gesture handler only | `remember { mutableStateOf() }` — local only, not in ViewModel |
| `doubleTapSeekState` | Local `var` in `PlayerScreen.kt` | `DoubleTapSeekOverlay` | `PlayerScreen` gesture handler only | `remember { mutableStateOf() }` — local only |
| `isLongPressActive` | Local `var` in `PlayerScreen.kt` | `HoldToFastForward` | `PlayerScreen` gesture handler only | `remember { mutableStateOf() }` — local only |
| `showDecoderDialog` | Local `var` in `PlayerScreen.kt` | `PlayerModals` | `PlayerQuickActions` callback only | `remember { mutableStateOf() }` — local only |

**Rule:** If state affects only visual presentation within a single screen and has no engine side effect, it lives as local `remember` state in the composable. If state must survive recomposition AND triggers an engine action, it lives in `PlayerViewModel`. If state is a direct reflection of MPV engine property, it lives in `PlayerRepository`.

---

# 4. File Creation Rules

## New screen feature → new file or existing?

- **New top-level screen** (new route in `AppNavigation.kt`): always new files — `FeatureNameScreen.kt` + `FeatureNameViewModel.kt` in `feature/featurename/`
- **New dialog inside the player** (triggered from player controls): new file in `feature/player/controls/` following the pattern of `AudioTrackDialog.kt`, `SubtitleTrackDialog.kt`
- **New section inside `PlayerRightSideSheet`**: new file in `feature/player/controls/sheet/` following the pattern established by `PlayerTracksSection.kt` and `PlayerSpeedSection.kt`
- **New icon button in `PlayerTopBar`**: add to `PlayerQuickActions.kt` — no new file unless `PlayerQuickActions.kt` exceeds 300 lines
- **New overlay animation** (like `DoubleTapSeekOverlay`, `HoldToFastForward`): new file in `feature/player/controls/`

## New engine capability → new file or existing?

- **New MPV property to observe**: add the constant to `MpvConstants.kt` (`MpvProp` object), register observation in `MpvOptionsConfigurator.registerPropertyObservers()`, handle in `MpvEventDispatcher`, expose via `PlayerRepository` — no new files needed
- **New MPV command** (seek, track switch, speed): add method to `MpvCommandExecutor.kt` and expose via `PlayerRepository` — no new files
- **New surface type or rendering backend**: new file in `engine/` following `MpvSurface.kt` pattern
- **New engine subsystem** (e.g., audio output routing, chapter navigation): new file in `engine/` only if it introduces a new stateful component with its own lifecycle. Otherwise extend the closest existing file.

## New util function → existing file or new?

- **Context traversal, Activity lookup, window helpers**: goes into `ContextExtensions.kt`
- **Time/duration formatting**: goes into `TimeFormatter.kt`
- **Media metadata queries** (`ContentResolver`, `OpenableColumns`): new file `MediaMetadataRepository.kt` — already planned in `PROGRESS.md`
- **Orientation management** (`requestedOrientation`, sensor logic): new file `OrientationManager.kt` — already planned in `PROGRESS.md`
- **Any other Android system utility** not fitting the above: new file in `util/` named after what it does (`SubtitleResolver.kt`, `UriValidator.kt`, etc.)

## Hard rule: 300-line ceiling

No Kotlin source file in this project may exceed 300 lines. This is a hard ceiling, not a guideline. When a file approaches 250 lines and a new feature would push it over, split before adding. Examples from this codebase: `PlayerScreen.kt` reached 315 lines and `PlayerRightSideSheet.kt` reached 312 lines — both required splitting before Phase 6 work could begin cleanly.

---

# 5. File Split Rules

## Trigger 1: 300-line hard limit

Any file over 300 lines must be split. No exceptions.

## Trigger 2: Single-responsibility violation

A file must be split when it contains two or more distinct architectural concerns regardless of line count. Examples from this codebase:

- `PlayerScreen.kt` mixed gesture handling + modal dialog hosting + surface lifecycle — three concerns, must be split into `PlayerGestureHandler.kt` + `PlayerModals.kt` + `PlayerScreen.kt`
- `PlayerRightSideSheet.kt` mixed track selection UI + speed control UI — two concerns, split into `PlayerTracksSection.kt` + `PlayerSpeedSection.kt`

## Trigger 3: ViewModel doing two domains

If a ViewModel handles state for two distinct feature areas (e.g., playback control AND subtitle file I/O AND orientation management), the non-playback domains must be extracted into separate managers or repositories.

## How to split without breaking — exact order

1. Create the new file with the extracted composable/class. Do not delete anything from the old file yet.
2. Ensure the new file compiles independently with all required imports.
3. In the old file, replace the extracted block with a call to the new composable/function.
4. Verify the project compiles with zero errors.
5. Run the app and verify the affected screen works correctly.
6. Only after step 5 passes: remove the now-unused code from the old file and clean up imports.
7. Update `PROGRESS.md` to record the split.

Never delete from the old file and create the new file in the same commit step before verifying compilation.

---

# 6. Adding a New Feature — Decision Checklist

Run through every question before writing a single line of code.

**Layer assessment**

- [ ] Which layer does this feature primarily live in? (engine / repository / viewmodel / UI)
- [ ] Does it require a new MPV property to be observed? If yes → `MpvConstants.kt` + `MpvOptionsConfigurator.kt` + `MpvEventDispatcher.kt` + `PlayerRepository.kt`
- [ ] Does it require a new MPV command to be sent? If yes → `MpvCommandExecutor.kt` + `PlayerRepository.kt`
- [ ] Does it expose new reactive state to the UI? If yes → new `StateFlow` in `PlayerRepository`, collected into `PlayerUiState` via `PlayerViewModel`

**State ownership**

- [ ] What new state does this feature introduce?
- [ ] Is it engine state (MPV property) → `PlayerRepository`
- [ ] Is it UI-only transient state (visibility, gesture phase) → local `remember` in composable
- [ ] Is it UI state that survives recomposition and triggers engine action → `PlayerViewModel` + `PlayerUiState`
- [ ] Does any existing `StateFlow` already cover this, or does a new one need to be added?

**File impact**

- [ ] Which existing files will be modified?
- [ ] Will any modified file exceed 300 lines after the change? If yes → split that file first before adding the feature
- [ ] Does this feature introduce a new screen? If yes → new `FeatureScreen.kt` + `FeatureViewModel.kt` in a new `feature/featurename/` package
- [ ] Does this feature add a new dialog/overlay to the player? If yes → new file in `feature/player/controls/`
- [ ] Does this feature add a new section to `PlayerRightSideSheet`? If yes → new file in `feature/player/controls/sheet/`

**Dependency check**

- [ ] Will any new class in `engine/` import from `feature/`? If yes → stop, wrong direction
- [ ] Will any new ViewModel method accept an `Activity` parameter? If yes → stop, use `OrientationManager` or `ApplicationContext` instead
- [ ] Will any new code perform disk I/O or `ContentResolver` queries synchronously on the calling thread? If yes → wrap in `Dispatchers.IO` via `viewModelScope.launch` or dedicated repository
- [ ] Will any new `@Composable` function directly import `MpvEngine` or `PlayerRepository`? If yes → stop, route through ViewModel

**Integration**

- [ ] Does `AppNavigation.kt` need a new route? If yes → new `@Serializable` data object/class in `AppNavigation.kt`
- [ ] Does `AndroidManifest.xml` need a new intent filter? If yes → add as a separate `<intent-filter>` block, never combine schemes
- [ ] Does `PROGRESS.md` need updating before starting? Yes — always mark the feature as in-progress before writing code

---

# 7. Anti-Patterns Banned in This Codebase

## 1. Activity/Context references in ViewModels

**Real example that motivated this ban:**

```kotlin
// HomeViewModel.kt:L15 — BANNED
fun lockToPortrait(activity: Activity?) {
    activity?.requestedOrientation = SCREEN_ORIENTATION_PORTRAIT
}

// PlayerViewModel.kt:L72 — BANNED
fun lockToLandscape(activity: Activity?) {
    activity?.requestedOrientation = SCREEN_ORIENTATION_SENSOR_LANDSCAPE
}
```

**Why banned:** `Activity` passed into a `ViewModel` creates a memory leak — the ViewModel survives configuration changes but holds a reference to the destroyed Activity.

**Correct alternative:** Use `OrientationManager.kt` called from a `DisposableEffect` or `LifecycleEventObserver` in the composable, passing `LocalContext.current` which is safe.

---

## 2. Synchronous I/O on main thread inside ViewModel

**Real example that motivated this ban:**

```kotlin
// PlayerViewModel.kt:L169-L196 — BANNED
fun resolveSubtitlePath(uri: Uri, context: Context): String? {
    val input = context.contentResolver.openInputStream(uri)  // blocks
    input.copyTo(output)  // blocks — can take seconds on large .ass files
}
```

**Why banned:** Blocks the main thread. Causes ANR on files over a few MB.

**Correct alternative:** Wrap in `viewModelScope.launch(Dispatchers.IO) { ... }`, expose loading state via `StateFlow<Boolean>` to show progress in UI.

---

## 3. Compose layer importing engine classes directly

**Pattern that is banned:**

```kotlin
// PlayerScreen.kt — BANNED
import com.potato.player.engine.MpvEngine
import com.potato.player.engine.MpvCommandExecutor
val engine = MpvEngine(context)  // UI layer creating engine directly
```

**Why banned:** Breaks the layer contract. UI layer must never know engine implementation exists.

**Correct alternative:** All engine interaction goes through `PlayerViewModel` → `PlayerRepository` → engine. `PlayerScreen` only calls `viewModel.togglePlay()`, never `repository.engine.command()`.

---

## 4. Duplicate ContentResolver helper logic across screen files

**Real example that motivated this ban:**

```kotlin
// HomeScreen.kt:L68-L87 — resolveTitle()
// PlayerScreen.kt:L291-L314 — resolveFileName()
// Near-identical ContentResolver.query() for OpenableColumns.DISPLAY_NAME
```

**Why banned:** Two files that must be kept in sync. When one is fixed, the other is missed.

**Correct alternative:** Single `MediaMetadataRepository.kt` in `util/` with one implementation, called from both places.

---

## 5. Combined scheme intent filters in AndroidManifest

**Real example that motivated this ban:**

```xml
<!-- AndroidManifest.xml — BANNED (was present, now fixed) -->
<intent-filter>
    <data android:scheme="file" android:mimeType="*/*" />
    <data android:scheme="content" android:mimeType="*/*" />
</intent-filter>
```

**Why banned:** Android's combinatorial matching rejects valid intents intermittently — caused the "sometimes opens, sometimes doesn't" bug from Telegram.

**Correct alternative:** One `<intent-filter>` block per scheme. Never combine schemes in one filter.

---

## 6. Blocking `.get()` on ExecutorService from main thread

**Real example that motivated this ban:**

```kotlin
// MpvCommandExecutor.kt:L114,L128 — RISKY
fun getPropertyInt(property: String): Int? =
    executor.submit<Int?> { MPVLib.getPropertyInt(property) }.get()
    // .get() blocks the calling thread
```

**Why banned:** If called on the main thread, blocks UI rendering. If `engineThread` is busy, risks starvation.

**Correct alternative:** Use callback-based or coroutine-based result delivery. If synchronous access is unavoidable, assert the call is on `Dispatchers.IO` and document it explicitly.

---

## 7. Merging unrelated UI state into one boolean flag

**Real example that motivated this ban:**

```kotlin
// PlayerScreen.kt:L277 — CONFUSING
visible = uiState.showMoreMenu || uiState.showSpeedDialog
// onDismiss calls both:
viewModel.onMoreMenuDismiss()
viewModel.onDismissSpeedDialog()
```

**Why banned:** Creates conflicting state updates — toggling `showMoreMenu` while `showSpeedDialog` is true causes both to be true simultaneously with different semantics.

**Correct alternative:** One boolean per distinct UI state. Sheet visibility is derived from a single `showRightSheet: Boolean` in `PlayerUiState`, not an OR of two unrelated flags.

---

# 8. Engine Layer Contract

## What each engine class is responsible for

**`MpvEngine.kt` — Lifecycle orchestrator**

- Responsible for: creating, initializing, and destroying the MPV instance; holding references to all engine subsystems (`executor`, `dispatcher`, `surface`, `configurator`)
- Responsibility ends at: knowing anything about what is playing or any UI state
- May call: `MPVLib.create()`, `MPVLib.init()`, `MPVLib.destroy()`, `executor.execute {}`, `dispatcher` registration

**`MpvCommandExecutor.kt` — Single-thread JNI dispatcher**

- Responsible for: executing all MPV JNI calls on the dedicated `mpv-engine-thread`, coalesced/debounced seek operations, surface creation tracking
- Responsibility ends at: interpreting what commands mean or why they are sent
- All `MPVLib.*` calls that mutate state must go through here — never call `MPVLib` directly from `PlayerRepository`

**`MpvEventDispatcher.kt` — C→Kotlin event bridge**

- Responsible for: implementing `MPVLib.EventObserver`, routing `eventProperty` and lifecycle events (`FILE_LOADED`, `END_FILE`) to registered `MpvEventListener` callbacks
- Responsibility ends at: deciding what to do with events — that is `PlayerRepository`'s job
- Must never hold or reference `StateFlow` — it only calls listener callbacks

**`MpvOptionsConfigurator.kt` — MPV property setup**

- Responsible for: writing initial MPV options (`hwdec`, cache, rendering), copying font assets to internal storage, registering `MPVLib.observeProperty` calls
- Responsibility ends at: runtime property changes — those go through `MpvCommandExecutor`
- The only engine class permitted to use `Context` (for `assets` access and `filesDir`)

**`MpvSurface.kt` — Surface attachment**

- Responsible for: implementing `SurfaceHolder.Callback` to call `MPVLib.attachSurface()` / `MPVLib.detachSurface()` and report `android-surface-size`
- Responsibility ends at: anything above the surface — no knowledge of what is rendered on it

**`PlayerRepository.kt` — Domain bridge**

- Responsible for: translating UI intent (play, seek, track select) into engine commands via `MpvCommandExecutor`; receiving engine events via `MpvEventListener`; exposing all playback state as `StateFlow`
- Responsibility ends at: ViewModel concerns, UI state, dialog visibility
- The **only** class in `engine/` that `ViewModel` layer may import

## Types that may cross the engine→repository→viewmodel boundary

| Permitted | Banned |
|---|---|
| `StateFlow<T>`, `SharedFlow<T>` | `MutableStateFlow` (keep mutations inside engine layer) |
| `TrackInfo` (data class, primitives only) | Any `View`, `SurfaceView`, `SurfaceHolder` |
| `Boolean`, `Int`, `Double`, `Float`, `String` | `Activity`, `Fragment`, `Context` (above Repository) |
| `List<TrackInfo>` | Any `androidx.compose.*` type |
| `kotlin.coroutines.*` | `android.widget.*`, `android.view.*` |

## How to add a new engine capability

1. Add the MPV property string constant to `MpvConstants.kt` under `MpvProp`
2. Add the format constant to `MpvFmt` if a new observation format is needed
3. Register `MPVLib.observeProperty(id, MpvProp.NEW_PROP, MpvFmt.FORMAT)` in `MpvOptionsConfigurator.registerPropertyObservers()`
4. Handle the incoming value in `MpvEventDispatcher.onPropertyChange()` and forward to listener
5. Add the `StateFlow` to `PlayerRepository` and update it in `onPropertyChange()`
6. Expose a public read-only `StateFlow` from `PlayerRepository`
7. Collect into `PlayerUiState` via `PlayerViewModel`
8. Only then use in composables via `uiState`

Never skip steps — never add a new `MPVLib.observeProperty` call without a matching handler in `MpvEventDispatcher` and a matching `StateFlow` in `PlayerRepository`.

---

# 9. The Golden Rules

1. **Engine layer never imports from `feature/` or `util/` — ever.** If you find an import from `com.potato.player.feature` inside `engine/`, it is wrong.

2. **ViewModels never accept `Activity` as a parameter.** Orientation and window mutations belong in UI-layer `DisposableEffect` blocks or `OrientationManager`, not in ViewModel methods.

3. **All MPV JNI calls go through `MpvCommandExecutor` on `mpv-engine-thread`.** Never call `MPVLib.*` directly from `PlayerRepository` or anywhere else.

4. **All engine state reaches the UI as `StateFlow` from `PlayerRepository`.** No composable reads engine state directly — it reads `PlayerUiState` from `PlayerViewModel`.

5. **No disk I/O or `ContentResolver` queries on the main thread.** Every `openInputStream`, `copyTo`, `query()` call runs on `Dispatchers.IO` inside `viewModelScope.launch` or a repository method.

6. **No file exceeds 300 lines.** Split before adding the feature that would push it over — not after.

7. **One `<intent-filter>` per scheme in `AndroidManifest.xml`.** Never combine `file://` and `content://` in one filter block.

8. **Duplicate logic across screen files is always wrong.** The second time you write a helper that already exists elsewhere, stop and move both to `util/`.

9. **Local `remember` state for UI-only concerns, `PlayerUiState` for cross-composable concerns, `PlayerRepository` for engine-reflected state.** Never promote state to a higher layer than necessary.

10. **`PlayerScreen` composable calls only `viewModel.*` methods.** It never calls `repository.*`, `engine.*`, or `MPVLib.*` directly.

11. **Every new MPV property observation follows the full chain:** `MpvConstants` → `MpvOptionsConfigurator` → `MpvEventDispatcher` → `PlayerRepository` → `PlayerViewModel` → `PlayerUiState`. Never shortcut this chain.

12. **The `controls/sheet/` subdirectory owns every section of `PlayerRightSideSheet`.** When a new settings group is added to the sheet, it gets its own file in `controls/sheet/` — never inline it into `PlayerRightSideSheet.kt`.

13. **`AppNavigation.kt` is the only place where engine objects cross into the Compose tree.** They are passed as constructor arguments to `ViewModelFactory` only — never accessed directly by composables.

14. **Before touching any file, check its current line count.** If adding your change would push it past 300 lines, the split is your first task.

15. **`PROGRESS.md` is updated before starting a task and after completing it.** The agent reads it at the start of every session to know current state. A stale `PROGRESS.md` is a navigation error waiting to happen.
