# xray-helper

A lightweight Android Quick Settings utility app designed for rooted Android 17 devices.

## Features

- **Xray QS Tile**:
  - Controls local Xray process (`/data/local/tmp/xray/xray`) and global HTTP proxy (`127.0.0.1:2080`).
  - Single-transaction `su -c` execution without background polling or daemons.
  - Active Tile with states: `On`, `Off`, and `Exited`.
- **Management Panel (MD3)**:
  - Accessible exclusively via long-pressing the Xray tile.
  - Auto-adapts status bar and insets (Edge-to-Edge) to light/dark system themes.
  - Controls: Start, Stop, Restart Xray.
  - Disable Debugging: Instantly turns off USB and Wireless debugging via root commands.
  - Does not open when long-pressing Caffeine or Record tiles.
- **Caffeine QS Tile**:
  - Independent Quick Settings tile to prevent the screen from sleeping.
  - Cycles through: `Off` → `5m` → `10m` → `30m` → `Infinite` → `Off`.
  - Battery-first optimization: automatically releases the WakeLock and terminates background service when the screen turns off.
- **Record QS Tile**:
  - Independent Quick Settings tile to instantly trigger Samsung Voice Recorder (`com.sec.android.app.voicenote`).
  - Calls native Android shortcut with automatic fallback to root execution.
- **Zero Overhead**:
  - No desktop launcher icon.
  - Excluded from recents.
  - Zero background power draw when idle.

## Building

Requires Android SDK 37 (compileSdk 37) and Java 17+.

```sh
./gradlew assembleRelease
```
