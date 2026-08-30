# Agent trajectories

Two kinds of agent were used, and they play very different roles. One is *inside*
the product and is measured; the other built the repository and is disclosed.

---

## 1. The triage agent — inside the product, and measured

**Instruction given (identical for all 24 cases, `eval/llm_baseline.py`):**

```
You are a first-aid triage classifier used offline in the field.

Classify the severity of the report below as exactly one of:
CRITICAL, SERIOUS, MODERATE, MINOR, UNKNOWN

CRITICAL means an immediate threat to life requiring action within seconds.
SERIOUS means it needs stabilisation and likely evacuation.
MODERATE means it needs cleaning and monitoring.
MINOR means basic first aid is sufficient.
UNKNOWN means there is not enough detail to judge.

Reply with ONLY that single word, nothing else.

Report: "{text}"
```

**Tooling:** `claude -p` (headless print mode). No tools exposed — this baseline
is deliberately a single prompt with no retrieval and no function calling,
because that is the baseline the brief names.

**Every response is cached** in `eval/fixtures/` keyed by a hash of the prompt,
and committed. The full 24-case trajectory replays offline with no credentials.

### Four representative exchanges

Chosen to show one of each outcome, not four successes.

#### Correct — the case the agent should get

| | |
|---|---|
| Case | `u1` (unambiguous) |
| Input | *"He is not breathing and I cannot find a pulse."* |
| Protocol answer | CRITICAL |
| **Agent returned** | **CRITICAL** ✓ |

#### Correct where the deterministic tree fails

| | |
|---|---|
| Case | `x1` (paraphrase) |
| Input | *"His lips have gone blue and his chest is not moving at all."* |
| Protocol answer | CRITICAL |
| **Agent returned** | **CRITICAL** ✓ |
| Safety tree returned | UNKNOWN — the text never says "breathing" |

This single case is why the final architecture consults an LLM at all.

#### Hedge — the failure that motivated the gate

| | |
|---|---|
| Case | `n2` (negation) |
| Input | *"The bleeding has stopped now after I applied pressure."* |
| Protocol answer | SERIOUS — controlled, but recent significant blood loss |
| **Agent returned** | **UNKNOWN** ✗ |
| Safety tree returned | SERIOUS ✓ |

The agent declined to commit on the exact case the negation logic exists for.

#### Over-triage — the other direction of error

| | |
|---|---|
| Case | `p3` (priority ordering) |
| Input | *"He has a scrape on his knee and has been confused since hitting his head."* |
| Protocol answer | SERIOUS |
| **Agent returned** | **CRITICAL** ✗ |

Over-triage is the safer error, but four of them across 24 cases is how a tool
loses a user's attention. The tree over-triaged zero times.

### What the trajectory shaped

These four exchanges are the whole argument for the shipped design. The agent is
strictly better on paraphrase and strictly worse on negation and calibration, so
it is consulted **only where the deterministic tree returns UNKNOWN** — 5 of 24
cases. It can add coverage; it can never soften a CRITICAL, because on any case
the tree answered, it is never asked.

Full 24-case trajectory: `eval/fixtures/llm-*.json` — each file holds the exact
prompt, the raw response, and the parsed severity. Scored output:
`eval/results/llm.json`.

### Human checkpoint

The agent never reaches the user unsupervised. Its answer is displayed only when
the safety tree has no opinion, and the app's standing disclaimer — *"Reference
only — not a diagnosis or prescription"* — is on the assistant screen at all
times. The tool is explicitly not a substitute for a qualified responder.

---

## 2. Coding agents — used to build this repository

Disclosed under the ground rules, which require stating what tools were used.

| Tool | What it was used for |
|---|---|
| **Claude Code** | The Kotlin Multiplatform migration, the mesh implementation, the evaluation harness and corpus, the safety fix, the UI fixes, and this documentation. |
| **ChatGPT** | Earlier development of the app, prior to this work. |

### A trajectory worth recording: the harness that measured nothing

The first version of `scripts/smoke_test.sh` checked only for `FATAL EXCEPTION`
in logcat after each step. It reported **39 of 39 steps passing**.

It had navigated out of the app on an early step — the system back key exits from
a top-level screen — and spent the remaining thirty steps tapping the Google
launcher. No crashes occurred, because the app was not running.

The fix was to assert three things per step instead of one: the app is still the
foreground activity, no crash appeared, and the expected text is on screen. On
the honest harness the same run scored 31 of 35, and the four failures were stale
tap coordinates left over from a layout change.

The general form of that lesson appears twice more in this repository — a Python
mirror of the safety tree that had drifted from the Kotlin, and 7 duplicated
files that had diverged between two copies of the same codebase. In each case a
green result was measuring something other than the thing that ships.
