# SignalZero — offline first aid, navigation, and messaging

An Android app for when there is no signal: first-aid triage, direction-finding
without GPS, and phone-to-phone messaging. Everything runs on the device.

> **Built on prior work.** This project extends
> [Lodestar](https://github.com/arpan-s-dev/LodeStar) (MIT, © Arpanjeet Singh and
> Manjeet Singh). What pre-existed and what was added here is set out precisely
> in **[docs/PRIOR_WORK.md](docs/PRIOR_WORK.md)**.

---

## The headline

The app it started from contained this claim, in a source comment:

> *"This engine is AUTHORITATIVE over the LLM … LLMs can be talked into softening
> a 'this is critical' judgment by conversational framing; a regex/keyword state
> machine cannot."*

A reasonable belief, asserted and never tested. This project tested it, and the
answer is more interesting than the claim:

| System | Accuracy | **Critical-miss rate** | Over-triage |
|---|---:|---:|---:|
| Naive keyword matching | 50.0% | 63.6% | 0 |
| LLM only (one prompt) | 66.7% | 9.1% | 4 |
| Safety tree, as it shipped | 66.7% | 27.3% | 0 |
| Safety tree, after the fix below | 75.0% | 9.1% | 0 |
| **Hybrid — what ships now** | **83.3%** | **0.0%** | 2 |

*Critical-miss rate is the share of life-threatening cases rated less severe.
It is the primary metric because rating a haemorrhage as MODERATE and rating a
graze as MINOR are both "one wrong answer", and only one of them kills someone.*

**Neither approach is better than the other. They fail in opposite directions.**
The deterministic tree is perfect on negation and blind to paraphrase; the LLM is
the reverse and over-triages. The shipping architecture lets the LLM speak only
where the tree returned UNKNOWN — 5 of 24 cases — so coverage improves and no
CRITICAL can ever be softened.

## The bug the evaluation found

Running the pre-existing safety tree against a protocol-derived corpus for the
first time surfaced a life-safety defect:

> A casualty with blood *"pumping out of her calf"* was told:
> **"Bleeding is controlled. Watch for shock, keep the casualty warm."**

Nobody had said the bleeding was controlled. The classifier's no-status
fall-through was labelled `BLEEDING_CONTROLLED`, and its arterial cue list was
missing ordinary lay phrasing — *pumping*, *pouring*, a growing pool.

Fixed by separating *how severe it is* from *what the text actually says*.
Critical-miss rate **27.3% → 9.1%**, and then to **0.0%** with the gated fallback.

Full evidence: **[docs/IMPROVEMENT_CHANGELOG.md](docs/IMPROVEMENT_CHANGELOG.md)**.

---

## What it does

| | |
|---|---|
| **Triage** | Describe an injury in plain language; get a severity and an ordered directive from a deterministic TCCC/MARCH safety tree. |
| **Find north without GPS** | Solar compass by day, star plate-solve by night, dead-reckoning between fixes. A persistent strip shows which position source is currently trusted. |
| **Nearby people** | Phone-to-phone messaging with no network, including a priority SOS that carries position and trust state. Messages are held and passed on when someone new comes into range. |
| **Translate** | Medic-to-casualty phrases, on device. |
| **Nearest hospital** | Offline dataset, ranked by great-circle distance with a bearing arrow. |

## Running it

```bash
bash scripts/mac_setup.sh all      # toolchain, build, eval, emulator
```

Or step by step: `install`, `build`, `eval`, `run`. Full detail, including
Linux/Windows notes and expected output, in
**[docs/REPRODUCTION.md](docs/REPRODUCTION.md)**.

```bash
./gradlew :shared:testDebugUnitTest   # 57 unit tests
bash scripts/smoke_test.sh            # drives every screen on a live emulator
```

## Two things this does not claim

**The mesh runs on a simulated transport.** An Android emulator has no Bluetooth
LE radio and two emulators cannot discover each other, so a real radio path could
not be tested or demonstrated on the development machine. The routing logic —
dedup, TTL, store-and-forward, priority eviction — is real and covered by 18
tests. The radio is not implemented. The app says so on screen rather than
implying live traffic.

**On-device LLM generation needs Snapdragon hardware.** Without it the app falls
back to a stub, which it labels. The severity and directive are *not* stubbed —
those come from the safety tree, which is the part under measurement.

## Layout

```
shared/      Kotlin Multiplatform domain logic — safety tree, navigation,
             mesh routing, retrieval. Compiles for Android, iOS and JVM.
             57 tests live here.
android/     The Compose app.
eval/        24-case corpus, scorer, LLM baseline, cached responses.
scripts/     Toolchain setup, smoke test, reference implementations.
docs/        Prior work, changelog, reproduction guide, trajectories.
```

## Licence

MIT. See [LICENSE](LICENSE) — it retains the upstream copyright as that licence
requires.
