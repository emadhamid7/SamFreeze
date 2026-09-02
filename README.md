# SamFreeze (MVP)

A standalone, offline, root-based Android utility that lets users on rooted
devices **freeze** (disable) and **unfreeze** (re-enable) installed
applications without uninstalling them or touching `/system`. Built for
Samsung One UI / Exynos devices but works on any rooted Android 8.0+ (API 26+)
device with a working `su` (KernelSU, KernelSU-Next, Magisk, APatch, etc).

This is a deliberately scoped **MVP**: it implements the core freeze/unfreeze
loop end-to-end, reliably, with the safety rails called out in the spec
(protected packages, no arbitrary shell execution, no partition writes).
Profiles, boot automation, favorites-as-a-full-feature, import/export, and a
Quick Settings tile are intentionally left as architectural stubs / not yet
built, so they can be added later without restructuring the app — see
"What's deliberately not in this build" below.

## What's new in this revision

- **Rebranded** com.freeze.manager → `com.samfreeze.app`, teal/slate-blue
  palette (no more Android-default blue, no dynamic/wallpaper color).
- **Freeze levels**: Basic / Adequate / Hardcore presets (package lists
  currently empty placeholders — see `util/LevelPackageLists.kt`, ready
  for the real Samsung package names), plus a user-built **Custom** list
  and an **Everything** mode. Every level, including Everything, still
  respects the hidden safety list and requires a confirmation dialog
  showing exactly how many apps it will touch before doing anything.
- **Hidden safety list** replaces the old visible "Protected packages"
  settings screen — those packages are now excluded from every list
  entirely (never shown, locked or otherwise) rather than displayed with
  a lock icon. Still fully enforced client-side before any root command.
- **Unfreeze All** panic button in the main menu.
- **Main menu reordered**: User → System → All → Frozen (rightmost).
- **Toggle direction fixed**: switch ON = frozen, switch OFF = active.
- Settings: removed the non-functional "Show system apps" toggle, fixed
  "Show package names" (was stored but never applied) and "Confirm
  before freezing" (now defaults OFF, actually gates the freeze action).
- **Advanced settings**: an opt-in soft-uninstall button on the app
  details screen (current-user only, never touches a partition — the
  erofs/ext4 check on `/system` is informational, not a gate), a
  settings-only backup export/import (JSON via Storage Access Framework —
  never touches app data), and a one-tap diagnostic-log-to-clipboard
  button.
- **Sort by storage size / RAM (PSS)** — computed on demand via root
  (`du` for size, `dumpsys meminfo` for RAM, and RAM is only ever queried
  for apps already known to be running).
- Small **Samsung / Google / AOSP** source label per app.
- Footer: "Made with love 🤍 by Emad", tap to open the repo.

## What it does

- Detects root via `su -c id`, with a clear "Root access required" screen
  and retry if unavailable — never crashes if `su` is missing.
- Lists **every** installed application (not just launchable ones), with
  icon, label, package name, system/user flag, and live enabled/disabled
  state read straight from `PackageManager.getApplicationEnabledSetting()`
  — never from a local cache — so it's always correct after reboots,
  system updates, or another tool changing state.
- Freeze = `pm disable-user --user 0 <package>`
  Unfreeze = `pm enable --user 0 <package>`
  Both run through a single persistent `su` shell (`RootShell`), off the
  main thread, and the UI re-queries actual state after every operation.
- Search (name + package), filters (All / User / System / Frozen),
  favorites, and sorting.
- Multi-select with batch freeze/unfreeze, progress dialog, and a
  success/failure summary.
- A built-in, expandable **protected package** list (SystemUI, Settings,
  package installer, permission controller, the launcher) that blocks the
  freeze command before it's ever executed — with an explicit
  "removing protection could brick your device" warning before a critical
  default can be unprotected.
- App details screen: status, type, version, UID, freeze/unfreeze,
  open app, native "App info" screen, and a clearly-labeled force-stop
  that's explicit about **not** being the same thing as freezing.
- Settings: theme (system/light/dark), display toggles, confirm-before-
  freezing-system-apps, refresh-on-resume, root status/test, and the
  protected-packages manager.
- Zero permissions beyond `QUERY_ALL_PACKAGES` (needed to see all system
  apps on API 33+) and an inert `RECEIVE_BOOT_COMPLETED` stub. **No
  INTERNET permission at all** — nothing here can phone home.

## Why it's safe

- **No generic shell.** `RootShell.execute()` is a plumbing class; the only
  place that ever builds a command string is `root/Commands.kt`, and every
  function there validates its package-name argument against
  `PackageUtils.isValidPackageName()` (a strict
  `^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$` check) before it's
  allowed anywhere near `su`. There's no "run arbitrary command" entry
  point anywhere in the app.
- **Protected packages are checked before the command is built**, not after
  — a protected package never reaches `RootShell` at all.
- **State is always re-verified**, never assumed. After every freeze/unfreeze
  (single or batch), the app re-reads `getApplicationEnabledSetting()`
  before updating the UI, so a failed or partially-applied command can
  never leave the UI showing a false status.

## Architecture

```
app/src/main/java/com/freeze/manager/
├── root/
│   ├── RootShell.kt        — persistent su process, timeout handling, retry-once-on-death
│   ├── Commands.kt          — the ONLY place shell strings are built; validates every arg
│   ├── RootResult.kt        — success/exitCode/stdout/stderr result type
│   └── BootCompletedReceiver.kt  — registered, currently a no-op stub (see below)
├── model/
│   └── AppInfo.kt           — AppInfo, AppState, AppFilter, SortOrder
├── data/
│   ├── PackageRepository.kt — PackageManager discovery + state resolution + freeze/unfreeze
│   └── PreferencesRepository.kt — DataStore-backed favorites/protection-overrides/settings
├── util/
│   ├── PackageUtils.kt      — strict package-name validation
│   └── ProtectedPackages.kt — expandable default protection set
└── ui/
    ├── MainActivity.kt + MainViewModel.kt — list, search, filters, multi-select
    ├── AppDetailsActivity.kt — per-app detail/actions screen
    ├── SettingsActivity.kt   — settings + protected-packages manager
    └── theme/                — Material 3 theme, light/dark/dynamic color
```

`SamFreezeApp` (the `Application` subclass) owns the single
`RootShell`/`PackageRepository`/`PreferencesRepository` instances and hands
them to each Activity's `MainViewModel` via a simple `ViewModelProvider.Factory`
— no DI framework needed for an app this size.

## How root execution works

`RootShell` starts one `su` process and keeps its stdin/stdout/stderr streams
open (cheaper than spawning `su -c "..."` per command). Each call writes the
command followed by two `echo` sentinels carrying a unique marker + exit
code, so the reader knows exactly where that command's output ends even
with multi-line stdout. Every call has a timeout (`withTimeoutOrNull`); on
timeout or a dead process, the shell is torn down and restarted once
automatically. All of this runs on `Dispatchers.IO`, guarded by a `Mutex` so
concurrent calls (e.g. during a batch operation) queue safely instead of
corrupting the shared stream.

## How package-state detection works

State is **never** inferred from what the app itself last did. Every read —
on load, after an operation, on `onResume()`, on pull-to-refresh — calls
`PackageManager.getApplicationEnabledSetting(pkg)` directly and maps
`COMPONENT_ENABLED_STATE_ENABLED`/`DEFAULT` → `ACTIVE` and
`DISABLED`/`DISABLED_USER`/`DISABLED_UNTIL_USED` → `FROZEN`. This is what
makes the UI correct after a reboot, a system update, or another tool
(including a future ROM's own settings) changing a package's state outside
this app entirely.

## What's deliberately not in this build

Per the spec's own "don't implement yet" list, and to keep the MVP small and
reliable: freeze **profiles**, **boot automation** (the receiver is wired
into the manifest and does nothing — flip it on later without a rewrite),
**import/export**, a **Quick Settings tile**, and **home-screen shortcuts**.
Favorites are implemented as real, persisted state (so the "Favorites"
filter has something to show later) even though the filter chip for it
isn't wired into the main filter row yet — one line in `FilterRow` to add it
when you want it. None of these require restructuring `PackageRepository`,
`RootShell`, or the protection system to add.

## Building it

This is a standard Gradle/Kotlin project — open the `SamFreeze/` folder
in Android Studio (Koala/2024.1+ recommended) and let it sync, or from a
terminal with the Android SDK + JDK 17 installed:

```bash
cd SamFreeze
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release-unsigned.apk
```

> **Note on this delivery:** this sandbox has no Android SDK and no network
> access, so I could not run Gradle here to produce compiled `.apk` files —
> what's included is the complete, buildable source project. The first
> `./gradlew` invocation will download the Gradle 8.7 distribution and the
> Android/Kotlin Gradle plugins, so run it somewhere with internet access at
> least once.

To sign the release build, add your keystore info to
`app/build.gradle.kts` under a `signingConfigs` block (omitted here since no
keystore should ever be committed to source).

### Minimum requirements to build
- Android Studio Koala (2024.1.1) or newer, or command-line Gradle 8.7+
- JDK 17
- Android SDK Platform 35, Build-Tools 35.x

### Runtime requirements (on the phone)
- Android 8.0 (API 26) or newer
- A working root solution (KernelSU, KernelSU-Next, Magisk, APatch, …) that
  grants `su` to the app when it asks
- Nothing else — no Termux, no ADB, no PC, no companion app

## Testing checklist (matches the spec's Test 1–9)

All nine scenarios from the spec are handled by the code paths described
above: no-root shows the retry screen without crashing; root-available
shows the ✓ status and loads the list; freezing/unfreezing a user app
round-trips through `pm disable-user`/`pm enable` and updates the pill;
state survives reboot because it's re-read from `PackageManager` on every
launch; search matches both name and package; protected packages are
blocked client-side before any command runs; multi-select batches with a
progress dialog and a final success/fail count; and a `pm` rejection
surfaces a non-crashing error dialog with the actual reason, while the UI
re-syncs to the real state afterward rather than trusting the failed
operation's intended outcome.
