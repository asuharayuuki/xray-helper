# Xray Helper

A lightweight, high-performance Android utility app tailored for rooted Android devices (Android 14+ / Android 17). Designed following authentic **Material You / System Settings** design principles, with zero background battery consumption.

---

## ✨ Features

### 1. Authentic Android System Settings Style
- **Collapsible Large Header**: Native `CollapsingToolbarLayout` headline (`Xray`) that expands on pull-down and smoothly transitions into the top app bar upon scrolling.
- **Flat Borderless Surface Cards**: Surface container cards with zero border stroke and zero elevation (`strokeWidth="0dp"`, `cardElevation="0dp"`, `cardCornerRadius="24dp"`), relying entirely on system dynamic theme contrast.
- **Standard Title Case Headers**: Clean category labels (`Proxy`, `Actions`, etc.) styled as system preference subheadings.
- **Dual Launch Entry**: Launch directly from the home screen launcher icon or via long-pressing the Quick Settings tile.

### 2. Fully Customizable Dashboard
- **Dynamic Sections**: Organize switches and buttons into custom sections (`Proxy`, `Actions`, `Developer`, `Network`, etc.).
- **Switches & Action Buttons**:
  - **Toggle Switch**: Define independent `Trigger / On` and `Off` commands, with a custom `State Check` command (exit code 0 = Active).
  - **Action Button**: One-tap execution of root commands with output notifications via system Toasts.
- **Dynamic Command Output Summaries**: Subtitle descriptions dynamically fetch stdout from commands (e.g. status or output). When output is empty or whitespace, description views automatically hide (`View.GONE`).
- **Exit Code Disable Conditions**: Automatically disable and dim any button or switch when a specified verification command evaluates to true (exit code 0).
- **Official Google Material Design Icons**: Integrated visual grid icon picker with 40+ official icons fetched directly from [google/material-design-icons](https://github.com/google/material-design-icons) (`shield`, `terminal`, `dns`, `globe`, `router`, `speed`, `fingerprint`, `code`, `storage`, `tune`, and more).

### 3. Dedicated Full-Page Item Editor (`EditItemActivity`)
- Completely eliminates cramped multi-layer dialogs and box-in-box nesting.
- **Spacious Command Inputs**: All command text fields support multi-line scripts with **unlimited vertical height** (no maxLines restrictions).
- **Core Integration**: Direct access to Core start/stop commands and process name without redundant proxy settings.
- **Visual Icon Picker Dialog**: Instant 4-column `RecyclerView` grid selector with clear English labels and selection highlights.
- **Save & Delete**: Save via the top-right Material checkmark icon (`ic_check`), with dedicated destructive actions for custom items.

### 4. Smart Core State Synchronization
- **Four Distinct Operational States**:
  1. `Running, proxy active` — Core process running + global HTTP proxy active.
  2. `Running, proxy disabled` — Core process running + global HTTP proxy inactive.
  3. `Stopped, proxy active` — Global HTTP proxy configured without active core process.
  4. `Stopped, proxy disabled` — Completely stopped and disconnected.
- **No PID Pollution**: Standard comma formatting for clean system status reporting.
- **Active QS Tile**: Bidirectional instant synchronization with the `XrayTileService`.

### 5. Auxiliary Quick Settings Tiles
- **Caffeine Tile**:
  - Cycles screen awake timeout: `Off` → `5m` → `10m` → `30m` → `Infinite` → `Off`.
  - Battery optimization: automatically releases `WakeLock` and shuts down the service immediately when the screen is turned off.
- **Record Tile**:
  - Instant one-tap shortcut to trigger voice recording (Samsung Voice Recorder or generic audio capture) with root shell fallback.

### 6. Zero Overhead & Battery-First Architecture
- **No Background Services**: No daemons, persistent polling threads, or broadcast receivers running in the background.
- **Single-Transaction Root Execution**: Minimizes `su` invocation overhead by executing batch status checks and state toggles in a single execution pipe.

---

## 🛠️ Building & Requirements

- **compileSdk**: 37 (Android 17)
- **minSdk**: 24 (Android 7.0+)
- **targetSdk**: 37
- **Java Version**: Java 17+
- **Root Solution**: Compatible with KernelSU, APatch, and Magisk.

### Build Release APK

```sh
./gradlew assembleRelease
```

The output signed APK will be generated at:
```
app/build/outputs/apk/release/app-release.apk
```
A ready-to-install release build is also maintained in the project root as `Xray-release.apk`.

---

## 📄 License

Open source under the [Apache 2.0 License](LICENSE).
