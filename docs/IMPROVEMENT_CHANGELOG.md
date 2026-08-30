# Improvement Changelog

Every row was produced by the same harness against the same 24 cases with the
same scorer. Reproduce any of them with `bash scripts/mac_setup.sh eval`.

**Primary metric: critical-miss rate** — the share of CRITICAL cases rated less
severe. Accuracy alone is the wrong headline for a triage tool: rating a
haemorrhage as MODERATE and rating a graze as MINOR are both "one wrong answer",
and only one of them kills someone. Over-triage is reported alongside it, because
a system that shouts CRITICAL at everything scores a perfect zero on the primary
metric and is useless.

| Stage | What was tried, and why | Accuracy | Critical-miss | Over-triage | Decision |
|---|---|---:|---:|---:|---|
| **Baseline A** | Keyword matching, no negation handling. What a competent engineer writes in an afternoon, and a fair statement of the prior art. | 50.0% | **63.6%** | 0 | Kept as the floor |
| **Baseline B** | One direct prompt to Claude with the severity scale and the task. The "one direct prompt" baseline the brief names. | 66.7% | 9.1% | **4** | Kept as the comparison |
| **Iteration 1** | Ran the pre-existing safety tree against the corpus for the first time. | 66.7% | 27.3% | 0 | **Exposed a bug — see below** |
| **Iteration 2** | Fixed the two defects iteration 1 surfaced. | 75.0% | 9.1% | 0 | Kept |
| **Iteration 3** | Let the LLM answer only where the tree returns UNKNOWN. | **83.3%** | **0.0%** | 2 | **Final** |

## Per-tier accuracy — the result that decided the architecture

| Tier | Keyword | LLM only | Safety tree | Hybrid |
|---|---:|---:|---:|---:|
| Unambiguous | 100% | 83% | 100% | 100% |
| **Negation** | 50% | 62% | **100%** | **100%** |
| Priority ordering | 50% | 75% | 75% | 75% |
| **Paraphrase** | 0% | **50%** | 17% | **50%** |

The two approaches fail in opposite directions, and neither is a general
improvement on the other.

The tree is perfect on negation — *"the bleeding hasn't stopped"* versus *"has
stopped now"* — and nearly blind to paraphrase: it cannot see that "his lips have
gone blue and his chest is not moving" is respiratory arrest, because it does not
contain the word "breathing". The LLM is the mirror image: it handles the
paraphrases and then hedges to UNKNOWN on *"the bleeding has stopped now"*, the
exact case the negation logic exists for. It also over-triages four times where
the tree never does once.

That asymmetry is what the final architecture is built on, and it was measured
before it was designed, not after.

## Iteration 1 in detail: what the evaluation found

Two defects in shipped code, in the same path.

**The arterial cue list was incomplete.** `arterialCues` held *spurting,
pulsing, bright red, gushing* — but not *pumping*, *pouring*, or a growing pool.
"Blood is pumping out of her calf" was therefore classified as ordinary venous
bleeding.

**Worse, the fall-through lied.** When bleeding was mentioned with no resolution
cue, the classifier returned `SERIOUS` under the rule label
`BLEEDING_CONTROLLED`, and the user was shown:

> *"Bleeding is controlled. Watch for shock, keep the casualty warm, and monitor
> the dressing/tourniquet."*

Nobody had said the bleeding was controlled. For a casualty who is actively
haemorrhaging, that directive is dangerous.

The fix separates *how severe it is* from *what the text actually says*:
`UNCONTROLLED`, `CONTROLLED`, and `STATUS_NOT_STATED` are now distinct, and the
third produces "Treat it as active until you can see that it has stopped."

Critical-miss rate: **27.3% → 9.1%**.

## Iteration 3 in detail: why the gate is one-directional

The tree stays authoritative on **19 of 24** cases. The LLM is consulted only on
the 5 where the tree returned UNKNOWN — that is, only where the deterministic
system has explicitly admitted it has nothing to say.

The asymmetry is the entire safety argument. The LLM can *add* coverage where
there was none. It can never *soften* a CRITICAL the tree has already assigned,
because on any case the tree answered, the LLM is never asked. Baseline B
over-triaged 4 times and hedged on a controlled haemorrhage; neither failure can
reach the user through this gate.

Critical-miss rate: **9.1% → 0.0%**.

## An experiment that was removed

**Scoring a Python reference implementation instead of the shipped Kotlin.**

The repository already contained `scripts/verify_safety_tree.py`, a hand-written
Python mirror of the safety tree, created when no Kotlin compiler was available.
Scoring it would have been far easier than building a JVM target for the
multiplatform module.

It was removed after the Kotlin and Python versions were found to disagree, and
the reason turned out to be structural rather than a one-off slip: **16 files
existed in two copies in this repository and 7 had already diverged.** The app
and any harness scoring a copy were running different code. The eval now runs the
shipped Kotlin through a `jvm()` target, and the duplicates are deleted.

What it taught: a harness that scores a copy of the system measures the copy. The
Python mirror is kept as a cross-check, and is scored by nothing.

## Failure mode that remains

**Paraphrase is still the weak axis at 50%.** The hybrid resolves 3 of 6
paraphrase cases. "She cannot feel her legs since she fell off the ladder" is a
possible spinal injury that neither system reliably reaches. The honest position
is that this corpus has 24 cases and one hard tier, and 50% on that tier is a
measurement, not a solution.
