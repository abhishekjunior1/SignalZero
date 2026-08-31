# Video script — ~4 minutes

Read it in your own rhythm. Short sentences on purpose; they're easier to say
without stumbling. Timings are a guide, not a target.

---

## 1 · The problem (0:00 – 0:35)
**Show:** your face, or the app's home screen sitting idle.

> I'm based in Jharkhand. Out in the district blocks, mobile coverage drops out
> all the time — after storms, in the hills, or just because the tower is too
> far.
>
> Here's the thing that bothers me. Every tool that helps you with an injury
> assumes you have a network. Looking up what to do about a bleeding wound needs
> signal. Finding the nearest hospital needs signal. Calling for help needs
> signal.
>
> So the moment the network goes, you're back to memory and guesswork. And
> that's exactly the moment it matters most.

---

## 2 · The baseline (0:35 – 1:05)
**Show:** the four-row table in the README, or just talk over the app.

> This app already existed, and it made a claim in a source comment — that a
> deterministic safety tree beats a language model at triage, because a model
> can be talked out of a critical judgement and a state machine can't.
>
> Sounds reasonable. Nobody had ever tested it.
>
> So I built the test. Twenty-four first-aid cases, severities taken from the
> TCCC MARCH protocol, deliberately written in words the classifier doesn't
> match on. Then I ran four systems against the same cases with the same scorer.

---

## 3 · One real run (1:05 – 2:35)
**Show:** the app, live. Do these three, unhurried.

**Assistant** — type: `the bleeding has not stopped`

> Watch this. Negation. It comes back CRITICAL, and the instruction underneath
> is the actual protocol directive — apply pressure or a tourniquet now.
>
> Now the opposite.

Type: `the bleeding has stopped now`

> SERIOUS, not critical. Controlled, but still shock risk. The language model
> baseline hedged and said UNKNOWN on that one.

**Nearby people** — open it.

> No network here at all. This is phone-to-phone. Messages hop between devices,
> and if someone walks into range later, they get what they missed. There's a
> priority SOS that carries your position and how much you trust it.
>
> I should be straight about this — the peers you're seeing are simulated. An
> emulator has no Bluetooth radio, so I couldn't test a real one honestly. The
> routing logic is real and it's got eighteen tests behind it.

**Nearby hospital** — show it from your location.

> And this one's personal. I'm in Khunti. It knows Sadar Hospital is right
> here. It didn't used to.

---

## 4 · What the measurement showed (2:35 – 3:20)
**Show:** the comparison table.

> Four systems, same cases, same scorer.
>
> Keyword matching gets fifty percent, and misses sixty-four percent of the
> critical cases. The language model on its own is better overall, but it
> over-triages four times and hedges on the negation cases. The safety tree is
> perfect on negation and nearly blind to paraphrase.
>
> They fail in opposite directions. That's the whole finding.
>
> So the last row composes them — the model only answers where the tree admits
> it doesn't know. Eighty-three percent, and zero critical misses. And to be
> clear, that's the evaluated composition. The app doesn't wire that gate in
> yet, and the README says so.

---

## 5 · The bug, and the take (3:20 – 4:00)
**Show:** your face, or the changelog.

> Here's what the measurement actually found.
>
> If you told this app your blood was pumping out of your leg, it replied:
> "Bleeding is controlled. Watch for shock, keep the casualty warm." Nobody had
> said it was controlled. It just had no way to say I don't know.
>
> It told a choking person to start CPR. And from my own town, it offered a
> hundred-and-five-day walk to a hospital in San Francisco.
>
> Every one of those is the same failure. The system answering confidently when
> it had no basis to answer at all.
>
> So my take is this. For an offline safety tool, the dangerous property isn't
> being wrong. It's being confident. "No data for your area" makes you go find a
> local number. "Nearest hospital, twelve thousand kilometres, head north-east"
> makes you follow an arrow into the sea. The second one looks like a working
> feature — that's why it survives.
>
> Write the evaluation before the feature. And make "I don't know" an answer the
> system is allowed to give.

---

## Practical notes

- **Don't demo** the night-sky star fix or "sight the sun". Both are broken —
  the star solver's epoch constant is thirty years off and the sun calibration
  is a no-op. Not worth a judge finding it live.
- If a screen shows `[STUB RESPONSE]`, you're on an old build. Reinstall.
- Say the simulated-mesh line out loud. It reads as rigour, not weakness, and
  a judge who reads the code will find it anyway.
- Rewrite a couple of sentences in your own words before recording — especially
  in section 5. It only has to sound like you.
