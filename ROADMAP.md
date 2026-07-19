# Potato Ultra X - Architecture & Development Roadmap

Welcome to the **Potato Ultra X** roadmap. This document serves as the single source of truth for all recently completed features, current architectural layers, and the prioritized list of upcoming features and refinements.

---

## 🛠️ Currently Completed & Added Features (`ALL THINGS WE ADDED`)

We have built a robust, reactive video player foundation powered by the native **MPV C engine** and modern **Jetpack Compose**. Below is the detailed breakdown of what is already implemented across the codebase:

### 1. Core Engine & MPV Integration (`com.potato.player.engine`)

* **`MpvEngine.kt` & `MpvSurface.kt`**: Low-level lifecycle management and hardware surface attachment (`mpv_create`, `mpv_initialize`, `mpv_set_option_string`, `attachSurface`, `detachSurface`).
* **`MpvCommandExecutor.kt`**: Type-safe Kotlin wrapper around `mpv_command` and property setters for precise playback control (`pause`, `seek`, `volume`, `speed`, `aid`, `sid`, `hwdec`).
* **`MpvEventDispatcher.kt`**: Thread-safe bridging of MPV C-level callbacks (`MPV_EVENT_PROPERTY_CHANGE`, `MPV_EVENT_SHUTDOWN`) into Kotlin Coroutines (`SharedFlow` & `StateFlow`).
* **`MpvOptionsConfigurator.kt`**: Bootstrapping initial hardware acceleration (`hwdec=mediacodec`), audio output configurations, GPU context, and OSD styling.
* **`PlayerRepository.kt`**: Single source of truth unifying command execution, options configuration, and observable player state (`timePos`, `duration`, `paused`, `hwdecCurrent`).

### 2. Video Player & UI Components (`com.potato.player.feature.player`)

* **`PlayerScreen.kt` & `PlayerViewModel.kt`**: Reactive Compose player view managing overlay visibility (`showControls`), lock toggle state (`isLocked`), and surface rendering.
* **`PlayerTopBar.kt`**: Top navigation header featuring back button, control lock toggle, quick hardware decoder status/selector button (`HW` / `SW`), and overflow **Three-Dot (`MoreVert`) button**.
* **`PlayerBottomControls.kt` & `TimeFormatter.kt`**: Play/Pause toggle button, progress `Slider`, and formatted `CurrentTime / TotalDuration` display (`HH:mm:ss`).
* **`PlayerQuickActions.kt`**: Bottom quick-action row for fast utility access.
* **`PlayerDecoderDialog.kt`**: Interactive modal dialog allowing users to dynamically switch between hardware decoding (`mediacodec`), copy (`mediacodec-copy`), and software decoding (`no`) on the fly.
* **`PlayerControlsStyles.kt`**: Curated dark-mode design tokens and typography for overlay controls.

### 3. Application Entry & Home Foundation (`com.potato.player.feature.home`)

* **`MainActivity.kt`**: Android `ComponentActivity` hosting Compose layout and managing window flags (`FLAG_KEEP_SCREEN_ON`, edge-to-edge system bars).
* **`HomeScreen.kt` & `HomeViewModel.kt`**: Initial scaffolding for media browsing and entry point to the player.

---

## 🚀 Upcoming Development Roadmap (`THINGS WE NEED TO ADD`)

> [!NOTE]
> **Swipe Gestures Deferred (`IGNORE GESTURE`) vs Touch Controls Included**:
>
> * **Deferred (`IGNORE GESTURE`)**: Vertical swipe gestures along the left/right screen edges (for screen brightness and volume control) are set aside for this development phase.
> * **Included Touch Controls (`WE DO THAT`)**: **Double-tap to seek** (-10s left / +10s right) and **Hold for 2x Speed** (long press anywhere on the video surface) are treated as primary touch interactions and **are actively included** in this roadmap for immediate implementation.

---

### Phase 1: Three-Dot Overflow Menu & Track Switch Dialogs

We will expand the existing Three-Dot (`MoreVert`) menu in `PlayerTopBar.kt` to trigger comprehensive track selection dialogs:

* [x] **Audio Track Switch Dialog (`AudioTrackDialog.kt`)**
  * **Track Discovery**: Query `track-list/N/id`, `track-list/N/type`, `track-list/N/title`, and `track-list/N/lang` from MPV via `PlayerRepository` to list all available audio streams.
  * **Interactive Selection**: Present a clean radio-button list of audio tracks (e.g., *English (AC3)*, *Japanese (AAC)*).
  * **Real-Time Switching**: Execute `mpv_set_property_string("aid", selectedId)` instantly when picked, indicating the active track with a highlight.

* [x] **Subtitle Switch Dialog (`SubtitleTrackDialog.kt`)**
  * **Track Discovery**: Query all embedded subtitle tracks (`track-list/N/type = "sub"`).
  * **External Subtitle Support**: Provide a *"Load External Subtitle (.srt / .ass / .vtt)"* button utilizing Android's document picker (`sub-add`).
  * **Interactive Selection & Off Mode**: Allow switching between available subtitle tracks (`sid = id`) or completely turning subtitles off (`sid = no`).
  * **Styling Options (Optional Phase)**: Basic font size and background box customization via MPV `sub-*` properties.

---

### Phase 2: Playback Speed Control Dialog

* [x] **Playback Speed Dialog (`PlaybackSpeedDialog.kt`)**
  * **Preset Selector**: Quick chips/buttons for standard speeds: `0.25x`, `0.5x`, `0.75x`, `1.0x` (Normal), `1.25x`, `1.5x`, `2.0x`, and `3.0x`.
  * **Fine-Tuning Slider**: Smooth continuous slider allowing step-by-step adjustments (e.g., `1.1x`, `1.35x`) from `0.25x` to `4.0x`.
  * **Integration**: Accessible directly via the quick actions bar or the Three-Dot overflow menu, updating `mpv_set_property_string("speed", value)` via `PlayerRepository`.

---

### Phase 3: Seek Bar Refinement

* [x] **Scrubber UX & Precision Improvements (`PlayerBottomControls.kt`)**
  * **Debounced Scrubbing**: Separate continuous touch dragging (`onValueChange`) from final seek confirmation (`onValueChangeFinished`). While dragging, perform fast/approximate keyframe seeks (`seek <val> absolute+keyframes`) and on release perform exact seek (`seek <val> absolute+exact`) to eliminate decoder lag/stuttering.
  * **Live Time Preview**: Show a floating bubble indicating the target seek timestamp (`TimeFormatter.formatTime(targetTime)`) right above the thumb while scrubbing.
  * **Visual Buffer Indicator**: Display cached/buffered duration (`demuxer-cache-duration`) as a secondary progress track beneath the seek slider.

---

### Phase 4: Touch Interaction Controls (Double-Tap Seek & Hold 2x Speed)

We will implement direct touch interactions on the video surface (`PlayerScreen.kt`) to make navigation and fast-forwarding instant and intuitive:

* [x] **Double-Tap to Seek (`DoubleTapSeekOverlay.kt`)**
  * **Zone Detection**: Divide the video touch surface into left and right double-tap zones using `pointerInput` and `detectTapGestures(onDoubleTap = { ... })`.
  * **Instant Relative Seek**:
    * **Left Zone Double-Tap**: Execute `mpv_command("seek", "-10", "relative+exact")` to jump back 10 seconds.
    * **Right Zone Double-Tap**: Execute `mpv_command("seek", "10", "relative+exact")` to jump forward 10 seconds.
  * **Visual Ripple Feedback**: Show animated circular ripple overlays (`-10s` / `+10s` with backward/forward skip icons) over the tapped zone upon activation.

* [x] **Hold for 2x Speed (`HoldToFastForward.kt`)**
  * **Long Press Detection**: Detect long press gestures (`onLongPress` / gesture press state) anywhere across the video playback surface.
  * **Instant Speed Boost**: Immediately apply `mpv_set_property_string("speed", "2.0")` when held down, bypassing normal playback rate.
  * **Top Banner Feedback**: Display a sleek, glowing pill banner ("⚡ 2x Fast Forwarding") at top-center of the screen while held.
  * **Revert on Release**: When the user lifts their finger (`onRelease`), instantly revert the speed back to normal (`1.0x` or the user's previously configured speed).

---

### Phase 5: Native Compose Navigation & App Engine Flow

* [x] **Type-Safe Jetpack Compose Navigation (`AppNavigation.kt`)**
  * **Routes Definition**: Replace manual activity transitions or conditional rendering with type-safe `NavHost` routing (`HomeRoute` vs `PlayerRoute(val videoUri: String, val title: String)`).
  * **Engine Lifecycle Decoupling**: Ensure `MpvEngine` initializes cleanly upon application launch or upon entering `PlayerRoute`, and enters standby/idle when navigating back to `HomeRoute`.
  * **Clean Shutdown & Memory Management**: Prevent zombie MPV audio threads and surface leaks during rapid back/forward navigation or app backgrounding (`onPause` / `onDestroy`).

---

### Phase 6: App and Video Orientation & Sensor Logic

* [ ] **Smart Screen Orientation Handling (`OrientationManager.kt`)**
  * **Home Screen Locking**: Lock `HomeScreen` to portrait (or respect user's global device setting) for comfortable browsing and scrolling.
  * **Auto-Orientation for Video (`PlayerScreen`)**: Inspect video metadata (`video-params/aspect` or `video-params/w` vs `video-params/h`):
    * **16:9 / Widescreen Videos**: Automatically switch `Activity` to `SCREEN_ORIENTATION_SENSOR_LANDSCAPE` upon playback start.
    * **9:16 / Vertical Shorts**: Automatically switch to `SCREEN_ORIENTATION_SENSOR_PORTRAIT`.
  * **Manual Rotation Lock Button**: Add a dedicated rotation toggle button in `PlayerTopBar.kt` allowing users to force landscape, force portrait, or follow device physical tilt sensor at will.
