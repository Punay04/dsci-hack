# Ransomware Detection System

An Android app that defends against ransomware at two points:

1. **Before an app gains risky access:** it intercepts suspicious permission requests and requires a verified human to approve them.
2. **After approval:** it watches for encryption-like file activity and contains the offending app.

Built with Kotlin, Jetpack Compose (Material 3), and Room. All detection runs on-device.

## Features

### Human Intent Verification Shield (latest)
- **Permission dialog detection:** `SecurityAccessibilityService` watches for system permission dialogs, extracts the permissions being requested, and flags apps that appear to abuse accessibility or screen overlays.
- **Risk scoring:** `RiskScorer` assigns a score to each request:

  | Signal | Points |
  |---|---|
  | Dangerous combination (e.g. storage + accessibility, device admin + overlay, accessibility + overlay) | +40 |
  | Unknown or sideloaded app | +25 |
  | Requested from a background context | +20 |
  | Multiple sensitive permissions | +20 |
  | First-time request | +15 |

  A score of 60 or more is **HIGH** risk, 30–59 is **MEDIUM**, and below 30 is **LOW**.
- **Intent verification:** for MEDIUM and HIGH risk requests, the permission dialog is paused and `IntentVerificationActivity` opens a secure screen (shown even over the lock screen) asking the user to confirm.
- **Bot detection:** `BehavioralAnalyzer` checks how the user answered. Presses or responses under 500 ms, perfectly even gesture timing, and press durations that repeat almost exactly across attempts are treated as automation.
- **Decisions:** `PermissionController` combines the risk level and verification result:

  | Situation | Decision |
  |---|---|
  | Interaction looks automated | Block |
  | LOW risk | Allow normally |
  | MEDIUM or HIGH risk, verified by a human | Grant temporarily |
  | MEDIUM or HIGH risk, not verified | Block |

### Runtime ransomware monitoring (latest)
`SecurityMonitoringService` runs in the foreground, watches the app's storage directories with `FileObserver`, and checks the last window of activity every 10 seconds for:
- rapid file access (120+ events)
- mass renames (40+)
- heavy writes or file creation (80+)
- files ending in `.encrypted`, `.locked`, `.enc`, `.crypt`, `.crypto` or `.wncry`

When any of these trips, the monitored app in the foreground has its sensitive runtime permissions revoked, its background processes killed, and the user is alerted.

### Canary files
`CanaryManager` plants decoy files with tempting names (`passwords_backup.txt`, `salary_data.xlsx`, `id_scan.pdf`, …) in 5–10 hidden folders. It stores each file's SHA-256 hash, size and modification time, and reports a threat if any decoy is deleted or changed.

### Security event log
`SecurityLogger` records permission decisions, suspicious behavior, monitoring alerts and blocked apps in a Room database. Recent events appear on the main screen.

### Community
`CommunityActivity` is a feed of security tips where users can post and like. It uses sample data held in memory and has no backend.

## Project structure

```
RansomwareDetectionSystem/app/src/main/java/com/example/ransomwaredetectionsystem/
├── MainActivity.kt              # Dashboard: shield toggle, settings shortcuts, canaries, events
├── CommunityActivity.kt         # Community feed
├── data/                        # Room database, canary entity and DAO
├── util/CanaryManager.kt        # Decoy file creation and integrity checks
├── ui/theme/                    # Compose theme
└── security/
    ├── services/                # Accessibility, permission monitor and file monitoring services
    ├── domain/                  # RiskScorer, BehavioralAnalyzer, PermissionController, models
    ├── ui/                      # IntentVerificationActivity
    ├── viewmodel/               # ShieldViewModel, IntentVerificationViewModel
    ├── repository/              # ShieldRepository
    ├── logging/                 # SecurityLogger
    └── data/                    # Security event entity and DAO
```

## Requirements
- Android Studio with Android Gradle Plugin 8.7
- Kotlin 2.0, JDK 11+
- Device or emulator running Android 8.0 (API 26) or later; targets API 35

## Getting started
1. Open the `RansomwareDetectionSystem` folder in Android Studio.
2. Let Gradle sync, then run the `app` configuration on a device or emulator.
   From the command line: `./gradlew installDebug`
3. In the app:
   - Tap **Accessibility Settings** and enable **Human Intent Verification Shield**.
   - Tap **Usage Access Settings** and grant usage access to the app.
   - Allow notifications when prompted.
   - Tap **Enable Shield** to start the monitoring services.
   - Generate canary files to turn on decoy-based detection.

## Permissions used
| Permission | Why |
|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Detect permission dialogs and pause them for verification |
| `PACKAGE_USAGE_STATS` | Identify which app is in the foreground |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Keep the monitoring services running |
| `KILL_BACKGROUND_PROCESSES` | Stop an app showing ransomware behavior |
| `POST_NOTIFICATIONS` | Alert the user about threats |

## Limitations
- Detection uses fixed rules and thresholds, not a trained model.
- File monitoring covers the app's own storage directories, so it mainly sees activity on the canary files rather than all shared storage.
- Revoking another app's runtime permissions requires system-level privileges, so on a normal device that step may fail silently; killing background processes and alerting still work.
- Only the template unit tests exist so far.
