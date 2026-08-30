# Prior work and what this project adds

*Required by the hackathon's ground rule 02 — "Make it clear what existed
before the competition and what you added."*

## What existed before

This project builds on **[Lodestar](https://github.com/arpan-s-dev/LodeStar)**,
an offline first-aid and navigation Android app, MIT licensed, © 2026 Arpanjeet
Singh and Manjeet Singh. Its original copyright notice is retained in
[`LICENSE`](../LICENSE) as that licence requires.

Measured against that repository at the time of writing, the Android tree here
contains:

| | files |
|---|---:|
| byte-identical to Lodestar | 33 |
| modified from Lodestar | 5 |
| new in this project | 2 |

Everything pre-existing is the app itself: the Compose UI shell, the TCCC/MARCH
first-aid corpus, the solar and star navigation maths, the ExecuTorch/QNN
on-device model plumbing, and the demo scenarios.

**Nothing in the `shared/`, `eval/`, or `scripts/` directories existed before.**

## What this project adds

### 1. An evaluation harness, and a measured comparison

The pre-existing code asserted, in a source comment, that a deterministic safety
tree beats an LLM at triage because "LLMs can be talked into softening a 'this is
critical' judgment by conversational framing." That claim was never tested.

This project tests it. `eval/` contains 24 first-aid vignettes with severities
derived from the TCCC/MARCH protocol — deliberately written in language that does
*not* reuse the classifier's own keyword lists, so the evaluation cannot pass by
matching itself. Four systems run against the same cases and the same scorer.

### 2. A life-safety bug the evaluation found

`BleedingClassifier` returned `SERIOUS` with the rule label `BLEEDING_CONTROLLED`
whenever bleeding was mentioned without a resolution cue — so a casualty with
arterial bleeding was told *"Bleeding is controlled. Watch for shock, keep the
casualty warm."* when nobody had said it was controlled. `arterialCues` also
lacked "pumping", "pouring", and a growing pool, which is ordinary lay phrasing.

Both are fixed, and the fix is quantified in the changelog.

### 3. Offline person-to-person messaging

`shared/.../mesh/` — message model, store-and-forward router with dedup, TTL and
priority eviction, a transport interface, and a simulated transport. 18 tests.
The pre-existing project had no messaging of any kind.

### 4. A Kotlin Multiplatform shared module

The domain logic moved to `shared/`, compiling for Android and iOS from one
source. This also fixed a real hazard: 16 files existed in two copies and **7 had
already diverged**, so the app and the evaluation were running different code.

### 5. Tests

57 unit tests where the pre-existing repository had a small JUnit suite that was
not wired into a runnable Gradle target on this platform.

### 6. UI correctness fixes

- The hospital list hard-coded an up-right arrow for every entry, so "head west"
  displayed an arrow pointing north-east.
- The compass displayed `360° N` above 359.5°.
- The soft keyboard covered the message composer — the field took focus but
  typing was invisible.
- The position-trust `StatusStrip`, described in the pitch as the signature
  element, was only referenced from a shell that nothing called, so it never
  reached the running app.

## Honest boundaries

- The mesh runs on a **simulated transport**. An Android emulator has no
  Bluetooth LE radio and two emulators cannot discover each other, so a real
  radio path could not be tested or demonstrated on the development machine. The
  routing logic is real and tested; the radio is not implemented. The app states
  this on screen.
- On-device LLM generation requires Snapdragon hardware. On the emulator the app
  falls back to a stub, which it labels. The **severity and directive are not
  stubbed** — those come from the safety tree, which is the part under test.
