# Reproduction guide

Written for someone starting from a clean machine. Every number in the README
and the changelog is reproducible from here.

## What you need

| | |
|---|---|
| Machine | macOS on Apple Silicon (this is what it was built and verified on) |
| Disk | ~12 GB — Android SDK, an emulator system image, Gradle |
| Network | For the one-time toolchain download only. The app and the evaluation run offline. |
| Cost | **$0.** LLM baseline responses are cached in `eval/fixtures/` and committed. |
| Runtime | ~25 min first run (mostly downloads), ~4 min after |

You do **not** need a Claude subscription, an API key, or a physical phone.

## Quickest path

```bash
git clone <this repo> && cd SignalZero
bash scripts/mac_setup.sh all
```

That runs four stages, each of which can be run alone:

| Stage | What it does |
|---|---|
| `install` | JDK 17, Android SDK 35, platform tools, emulator, arm64 system image |
| `build` | Runs the 57 shared unit tests, then builds the debug APK |
| `eval` | Runs the triage evaluation and prints the comparison table |
| `run` | Boots an emulator, installs the app, grants permissions, launches it |

### One note on the JDK

The script installs `openjdk@17` via the Homebrew **formula**, not the
`temurin@17` cask. The cask ships a `.pkg` that requires `sudo`, which fails in a
non-interactive shell — silently, with exit code 0.

## Reproducing the measured results

```bash
bash scripts/mac_setup.sh eval
```

Expected output:

```
naive
  accuracy             12/24  (50.0%)
  CRITICAL missed      7  (63.6%)

safetytree
  accuracy             18/24  (75.0%)
  CRITICAL missed      1  (9.1%)

hybrid
  accuracy             20/24  (83.3%)
  CRITICAL missed      0  (0.0%)
```

### How the evaluation avoids grading itself

Three deliberate choices, each of which would otherwise inflate the result:

1. **It runs the shipped Kotlin**, not a re-implementation. `shared/` exposes a
   `jvm()` target so the harness executes the same code that runs on the device.
   The repository also contains a Python mirror of the safety tree; it is scored
   by nothing. See the changelog for why that matters — 7 files had already
   diverged between two copies of this codebase.
2. **The cases do not reuse the classifier's vocabulary.** Severities come from
   the TCCC/MARCH protocol, and the paraphrase tier deliberately describes
   conditions without the words the tree matches on — "his lips have gone blue
   and his chest is not moving" rather than "not breathing".
3. **The LLM baseline is cached and committed**, so the comparison is
   byte-identical on re-run and needs no credentials.

### Re-running the LLM baseline against a live model

Only needed if you want to regenerate the cache rather than replay it:

```bash
python3 eval/llm_baseline.py            # calls `claude -p`, needs the CLI
python3 eval/llm_baseline.py --offline  # cache only; fails on a miss
```

## Running the app

```bash
bash scripts/mac_setup.sh run
```

The emulator is created from a Pixel 7 profile. A smaller default AVD
(320×640 dp) clips content that lays out correctly on a real device.

### Verifying the app end to end

```bash
bash scripts/smoke_test.sh
```

Drives every screen and control, and asserts three things per step: the app is
still the foreground activity, no `FATAL EXCEPTION` appeared, and the expected
text is on screen. Writes a screenshot and a visible-text dump per step to
`docs/screenshots/smoke/`.

*The foreground and text assertions exist because the first version checked only
for crashes — it navigated out of the app early and reported thirty passes while
tapping the Google launcher.*

## Versions

| | |
|---|---|
| JDK | OpenJDK 17.0.20.1 (Homebrew) |
| Gradle | 8.7 |
| Android Gradle Plugin | 8.6.0 |
| Kotlin | 2.0.20 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |
| Emulator image | `system-images;android-35;google_apis;arm64-v8a` |
| LLM baseline | Claude via `claude -p`, responses cached |

## What will not reproduce, and why

**On-device model generation.** ExecuTorch with the Qualcomm QNN backend needs a
Hexagon NPU. There is none in an emulator. The build sets
`enableQnnBackend=false`; `AiServiceFactory` falls back to a stub, which the UI
labels. Triage severity and directives are unaffected — those are the safety
tree, and that is what the evaluation measures.

**A real Bluetooth mesh.** An emulator has no BLE radio and two emulators cannot
discover each other. The app runs the mesh on a simulated transport and says so
on screen. The routing logic is real and tested; the radio is not implemented.
