# 🎮 Gyro Overlay

External gyroscope input for any Android game — no game modifications needed.

## Requirements
- Android 8.0+ (tested on Android 16)
- **Root access** (KernelSU / Magisk)
- SELinux **Permissive** mode
- Device: `/dev/input/event6` as touchscreen node

> ⚠️ If your touchscreen is NOT on `event6`, edit `InputInjector.kt` line:
> `private const val EVENT_NODE = "/dev/input/event6"`

## Build via GitHub Actions (no PC needed)

1. Fork or upload this repo to GitHub
2. Go to **Actions** tab → **Build APK** → **Run workflow**
3. Wait ~5 minutes
4. Download `GyroOverlay-debug.apk` from the build artifacts
5. Install APK on your rooted phone

## How It Works

```
Phone tilt → Gyroscope sensor → High-pass filter (removes drift)
           → Delta calculation → sendevent injection → Game camera moves
```

## Usage

1. Open **Gyro Overlay** app
2. Tap **Check Root Access** — confirm it shows ✅
3. Tap **START GYRO OVERLAY**
4. Grant overlay permission if asked
5. Open your game
6. You'll see:
   - 🔵 **Blue zone** — drag to camera/aiming area of your game, resize as needed
   - 🎛️ **Control panel** — toggle ON/OFF, adjust sensitivity

## Controls

| Control | Function |
|---|---|
| Drag blue zone top bar | Move the gyro zone |
| Drag ⤡ corner | Resize the gyro zone |
| ON/OFF button | Enable/disable gyro |
| Sensitivity slider | 1 (slow) to 20 (fast) |
| Drag "GYRO CTRL" bar | Move the control panel |

## Troubleshooting

**Gyro not working / no camera movement**
- Check root: run `su -c "getenforce"` in Termux — must be `Permissive`
- Check event node: run `getevent -l` while touching screen, find your node
- Update `EVENT_NODE` in `InputInjector.kt` if different from `event6`

**Camera drifts on its own**
- Lower sensitivity
- The high-pass filter handles most drift automatically

**Zone not visible**
- Grant overlay permission in Settings → Apps → Gyro Overlay → Display over other apps

## Tech Stack
- Kotlin + Android SDK
- `sendevent` via root shell for touch injection
- MT Protocol Type B for multi-touch
- High-pass filter for gyro drift compensation
