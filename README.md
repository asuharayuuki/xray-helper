# Xray Helper

A lightweight, high-performance Android utility app tailored for rooted Android devices (Android 11+). Designed with a modern, elegant **Xiaomi HyperOS / MIUI Design System** using [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) for Jetpack Compose, featuring zero background battery consumption.

---

## ✨ Features

### Languages
- Supports English, Chinese (Simplified), and Japanese; follows the system language, with English as the fallback.
- On Android 13+, select a language for Xray in the system's per-app language settings.
- Interface labels, accessibility descriptions, Quick Settings tile states, and built-in dashboard names are localized. Custom names, shell commands, and command output are preserved.
- Product and technical names such as Xray, Caffeine, Android, Root, HTTP, DNS, VPN, and Wi-Fi retain their original spelling.

### Configuration import and export
- Settings → **Export configuration** creates a JSON backup and opens the Android system share sheet. Only that cache file is shared through a temporary read-only content URI.
- Settings → **Import configuration** opens the system file picker. After validating the file, the app asks before replacing all settings and dashboard items. Cancelling or selecting an invalid file leaves the current configuration unchanged.
- Backups contain Xray start/stop commands, process name, HTTP proxy address, and all dashboard sections/items (including commands and built-in localization metadata). They do not include the external Xray binary or its own configuration files.
- Format: `xray-helper-config`, version `1`, UTF-8 JSON, up to 1 MiB. Unknown versions, invalid field types, duplicate IDs, and missing or duplicate core controls are rejected. Imported commands are stored; state checks and actions execute when the dashboard or corresponding controls are used.
- Existing dashboard configurations are read automatically from the older preferences store; new saves keep the settings and dashboard together so imports can be applied in a single transaction.

Validation: `python tools/check_localization.py`, `./gradlew testDebugUnitTest assembleRelease lintRelease`.

### 1. Modern Miuix / HyperOS Compose UI
- **Powered by Miuix**: Built with [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix), bringing Xiaomi HyperOS's sleek design language to Android.
- **Large Collapsing TopAppBar**: Smooth inertial scrolling with `MiuixScrollBehavior`, dynamically collapsing from a large prominent title to a compact top bar.
- **Continuous Curvature Squircle Cards**: Grouped preference items enclosed within Miuix squircle `Card` containers with adaptive surface elevation and system dark mode integration.
- **Native Miuix Preference Components**: Smooth toggle controls (`SwitchPreference`), interactive action preferences (`ArrowPreference`), and single-choice selectors (`RadioButtonPreference`).
- **Dual Launch Entry**: Launch directly from the desktop launcher icon or via long-pressing the Quick Settings tile.

### 2. Fully Customizable Dashboard
- **Dynamic Sections**: Organize switches and buttons into custom sections (`Proxy`, `Actions`, `Developer`, `Network`, etc.).
- **Switches & Action Buttons**:
  - **Toggle Switch**: Define independent `Trigger / On` and `Off` commands, with a custom `State Check` command (exit code 0 = Active).
  - **Action Button**: One-tap execution of root commands with output notifications via system Toasts.
- **Dynamic Command Output Summaries**: Subtitle descriptions dynamically fetch stdout from commands (e.g. status or output). When output is empty or whitespace, description views automatically hide.
- **Exit Code Disable Conditions**: Automatically disable and dim any button or switch when a specified verification command evaluates to true (exit code 0).
- **Official Google Material Icons**: Integrated visual grid icon picker with 40+ official icons from [google/material-design-icons](https://github.com/google/material-design-icons) (`shield`, `terminal`, `dns`, `globe`, `router`, `speed`, `fingerprint`, `code`, `storage`, `tune`, and more).

### 3. Dedicated Full-Page Item Editor (`EditItemActivity`)
- Completely eliminates cramped multi-layer dialogs and box-in-box nesting.
- **Spacious Command Inputs**: All command text fields support multi-line scripts with **unlimited vertical height** (no restrictive line limits).
- **Core Integration**: Direct access to Core start/stop commands, process name, and the HTTP proxy address (`host:port`). Leaving the proxy address empty restores `127.0.0.1:2080`; custom addresses are included in configuration imports and exports.
- **Visual Icon Picker Dialog**: Instant 4-column `WindowDialog` grid selector with clear English labels and selection highlights.
- **Save & Delete**: Save via the top-right checkmark icon (`MiuixIcons.Ok`), with dedicated destructive actions for custom items.

### 4. Smart Core State Synchronization
- **Four Distinct Operational States**:
  1. `Running, proxy active` — Core process running + global HTTP proxy active.
  2. `Running, proxy disabled` — Core process running + global HTTP proxy inactive.
  3. `Stopped, proxy active` — Global HTTP proxy configured without active core process.
  4. `Stopped, proxy disabled` — Completely stopped and disconnected.
- **No PID Pollution**: Standard comma formatting for clean system status reporting.
- **Active QS Tile**: Bidirectional instant synchronization with `XrayTileService`.

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
- **minSdk**: 30 (Android 11+)
- **targetSdk**: 37
- **Kotlin**: 2.0.21 / 2.4+ Compose Compiler
- **UI Library**: `top.yukonga.miuix.kmp:miuix-ui-android:0.9.4`
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
