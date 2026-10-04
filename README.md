# SignalZero — offline first aid, navigation, and messaging

An Android app for when there is no signal: first-aid triage, direction-finding
without GPS, and phone-to-phone messaging. Everything runs on the device.

---

## Who this is for

A first responder, health worker, or ordinary bystander in a district where
mobile coverage is thin, intermittent, or gone — after a storm, in hill and
forest blocks, or anywhere the tower is simply too far.

**The bottleneck.** Every tool that helps with an injury assumes a network. A
search for what to do about a bleeding wound needs signal. A map to the nearest
hospital needs signal. Calling for help needs signal. The moment those fail,
the person holding the phone is back to memory and guesswork — and that is
precisely the moment when the guidance matters most and when panic makes
recall worst.

**Why solving it is worth something.** The gap is not knowledge; the protocols
are well established and fit in a few hundred kilobytes. The gap is that
nothing carries them to the person who needs them when the network is down. A
phone already in someone's pocket has the storage, the sensors and the radio to
close that gap without any infrastructure at all. What it lacks is an app that
assumes the network is already gone.

That is what this is: triage that runs on the device, a way to find north when
GPS is being jammed or is simply unavailable, an offline list of the hospitals
that actually exist in your district, and messaging that hops phone to phone
when there is nothing to connect to.


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
| **Hybrid — gated composition (evaluated)** | **83.3%** | **0.0%** | 2 |

*Critical-miss rate is the share of life-threatening cases rated less severe.
It is the primary metric because rating a haemorrhage as MODERATE and rating a
graze as MINOR are both "one wrong answer", and only one of them kills someone.*

**Neither approach is better than the other. They fail in opposite directions.**
The deterministic tree is perfect on negation and blind to paraphrase; the LLM is
the reverse and over-triages four times where the tree never does once.

The last row composes them: the LLM answers only where the tree returned UNKNOWN
— 5 of 24 cases — so coverage improves and no CRITICAL can be softened, because
on any case the tree answered the model is never consulted.

**What the app does today is not yet that gate.** The severity chip and, when no
on-device model is present, the directive both come from the tree; where a model
*is* present the app shows the model's answer beneath the tree's severity,
ungated. Wiring the evaluated gate into `TriageOrchestrator` is the next change,
and it is the one the measurement above argues for. The 0.0% row describes the
composition as evaluated, not as shipped.

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
| **Find north without GPS** | Solar compass by day, star plate-solve by night, and a position-source state machine with a simulated spoof trigger (dead reckoning is a state label, not an implemented estimator). A persistent strip shows which position source is currently trusted. |
| **Nearby people** | Phone-to-phone messaging with no network, including a priority SOS that carries position and trust state. Messages are held and passed on when someone new comes into range. |
| **Translate** | Medic-to-casualty phrase screen. The on-device translation model is not implemented; the screen states this. |
| **Nearest hospital** | Offline dataset (87 facilities: Delhi NCR, Jharkhand incl. district CHCs, major Indian metros, SF Bay Area), ranked by great-circle distance with a bearing arrow. Reports no coverage rather than pointing at a facility hundreds of kilometres away. |

## Running it

```bash
bash scripts/mac_setup.sh all      # toolchain, build, eval, emulator
```

Or step by step: `install`, `build`, `eval`, `run`. Full detail, including
Linux/Windows notes and expected output, in
**[docs/REPRODUCTION.md](docs/REPRODUCTION.md)**.

```bash
./gradlew :shared:testDebugUnitTest   # 66 unit tests
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
             66 tests live here.
android/     The Compose app.
eval/        24-case corpus, scorer, LLM baseline, cached responses.
scripts/     Toolchain setup, smoke test, reference implementations.
docs/        Prior work, changelog, reproduction guide, trajectories.
```


---

## The main failure mode

Everything this project found wrong was the same failure wearing different
clothes: **a system answering confidently where it had no basis to answer at
all.**

- The classifier said *"Bleeding is controlled. Watch for shock, keep the
  casualty warm"* to someone whose blood was pumping out, because nobody had
  told it the bleeding was uncontrolled and it had no way to say "I don't know".
- It told a choking casualty to start CPR, because "can't breathe" and "not
  breathing" looked like the same string.
- It offered a 105-day walk to San Francisco to a user in Jharkhand, because
  ranking by distance always returns a nearest item, however far away.
- The first evaluation harness I wrote reported 39 of 39 steps passing while
  the app was not even running — it only checked for crashes, and a program
  that has exited never crashes.

None of these were caught by reading the code. All of them were caught by
measuring it against an answer key that did not come from the code itself.

## Hot take

**An offline safety tool's most dangerous property is not being wrong. It is
being confident.**

A user who sees "no data for your area" goes and finds a local number. A user
who sees "nearest hospital, 12,622 km, head north-east" follows an arrow into
the sea. The second failure looks like a working feature, which is exactly why
it survives review — and why the deterministic core of this app was believed to
be correct for months without anyone running twenty-four sentences through it.

So the change I'd make to how I build: **write the evaluation before the
feature, and make "I don't know" a first-class answer.** Not as a fallback for
when the clever path fails, but as a verdict the system is allowed to reach and
report proudly. Every fix in the changelog is some version of teaching this app
to admit the limits of what it actually knows — and every one of them made it
more useful, not less.


## Licence

MIT. See [LICENSE](LICENSE) — it retains the upstream copyright as that licence
requires.

## 🏆 Honors & Certifications
* **Certificate of Participation – Frontier Engineering Challenge 2026** | *micro1* *(September 2026)*

<img width="793" height="511" alt="image" src="https://github.com/user-attachments/assets/0d493880-a33a-4d0a-8625-b774f8cbb895" />
