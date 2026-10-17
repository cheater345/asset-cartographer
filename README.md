# Asset Cartographer

Read-only documentation toolkit for Unity-based Android game clients.
Built to study asset delivery and container layouts on devices you own —
without ever writing to, injecting into, or modifying the target.

## What it does

- Imports an APK file (Storage Access Framework) and statically scans `assets/`
  for UnityFS containers, parsing each container preamble: engine version,
  compression mode, block layout, sizes, CRC.
- Lists directory metadata on-device via Shizuku (shell, read-only) or via SAF,
  for locations such as a target app's external data directory on Android 11+.
- Exports bundle tables and device listings as JSON + CSV reports.
- Journals every operation; tool-created artifacts can be rolled back any time.

## What it deliberately does NOT do

- No write, patch, override, or injection capability anywhere. The device layer
  exposes two typed read operations and nothing else (`device/ReadOnlyShell.kt`).
  Pull requests that add a write path to that layer will be rejected — the
  read-only boundary is the point of the project.
- No interaction with a live game process, ever.

## Modules

| module    | role                                                        |
|-----------|-------------------------------------------------------------|
| `core`    | serializable models (`BundleRecord`, `FileRecord`, `JournalEntry`) |
| `unityfs` | UnityFS preamble parser + APK scanner                       |
| `device`  | Shizuku shell (typed, read-only) + SAF walker               |
| `report`  | journal, JSON/CSV exports, rollback                         |
| `app`     | Compose UI                                                  |

## Build

CI (recommended): push this repo to GitHub → Actions tab → `android` workflow
→ download the `asset-cartographer-debug` artifact.

Local: open the folder in Android Studio, or install Gradle 8.10.2 and run
`gradle assembleDebug`. No wrapper jar is checked in; generate one with
`gradle wrapper --gradle-version 8.10.2` if you want `./gradlew` locally.

## Shizuku setup (for reading Android/data on Android 11+)

1. Install Shizuku (Google Play, or github.com/RikkaApps/Shizuku).
2. Start the Shizuku service using its in-app instructions (wireless debugging
   or root).
3. Open Asset Cartographer → Device tab → "Grant Shizuku".
4. Browse, e.g. `/storage/emulated/0/Android/data/com.mobile.legends/files`.

## Reports and journal

Reports land in the app's private `files/reports` directory and are journaled
with their absolute paths. The Reports tab lists the journal; every reversible
entry has a "Roll back" action that deletes only artifacts this tool created.

## Docs

- `docs/journal-schema.md` — every journal field and its semantics.
- `docs/experiment-protocol.md` — baseline/override/revert protocol for the
  companion Unity lab (where override experiments belong).
