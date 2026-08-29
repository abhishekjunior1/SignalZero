#!/usr/bin/env python3
"""LLM-only triage baseline, run through `claude -p` (headless print mode).

This is the "one direct prompt with basic instructions" baseline the hackathon
brief names. It is a fair one: the model is told the severity scale and the
task, and nothing is done to sabotage it.

Every response is cached under eval/fixtures/ keyed by a hash of the prompt, and
the cache is committed. A judge with no Claude subscription re-runs the harness
offline and gets exactly the numbers in the README — the same reproducibility
trick the eval uses for everything else.

Usage:
  python3 eval/llm_baseline.py            # cached where possible, calls otherwise
  python3 eval/llm_baseline.py --offline  # cache only; fail on a miss
"""
import hashlib, json, pathlib, subprocess, sys, time

ROOT = pathlib.Path(__file__).resolve().parent
REPO = ROOT.parent
FIXTURES = ROOT / "fixtures"
VALID = {"CRITICAL", "SERIOUS", "MODERATE", "MINOR", "UNKNOWN"}

PROMPT = """You are a first-aid triage classifier used offline in the field.

Classify the severity of the report below as exactly one of:
CRITICAL, SERIOUS, MODERATE, MINOR, UNKNOWN

CRITICAL means an immediate threat to life requiring action within seconds.
SERIOUS means it needs stabilisation and likely evacuation.
MODERATE means it needs cleaning and monitoring.
MINOR means basic first aid is sufficient.
UNKNOWN means there is not enough detail to judge.

Reply with ONLY that single word, nothing else.

Report: "{text}"
"""


def ask(text: str, offline: bool) -> tuple[str, str]:
    """Return (severity, raw). Cached by prompt hash."""
    prompt = PROMPT.format(text=text)
    key = hashlib.sha256(prompt.encode()).hexdigest()[:20]
    path = FIXTURES / f"llm-{key}.json"

    if path.exists():
        return json.loads(path.read_text())["severity"], "cache"
    if offline:
        raise SystemExit(f"offline cache miss for: {text[:60]!r}\nexpected {path.name}")

    # cwd=REPO so the run does not inherit unrelated permission config from
    # whatever directory the harness happens to be invoked from.
    proc = subprocess.run(
        ["claude", "-p", prompt],
        capture_output=True, text=True, cwd=REPO, timeout=180,
    )
    raw = proc.stdout.strip()
    # The model is asked for one word; take the last valid token so any
    # preamble is ignored rather than silently scored as UNKNOWN.
    sev = next((w for w in reversed(raw.replace("\n", " ").split())
                if w.strip(".,*`").upper() in VALID), None)
    sev = sev.strip(".,*`").upper() if sev else "UNKNOWN"

    FIXTURES.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(
        {"prompt": prompt, "raw": raw, "severity": sev}, indent=1))
    return sev, raw


def main() -> None:
    offline = "--offline" in sys.argv
    cases = json.loads((ROOT / "cases/triage.json").read_text())
    out, hits = [], 0
    for i, c in enumerate(cases, 1):
        sev, raw = ask(c["text"], offline)
        hits += raw == "cache"
        out.append({"id": c["id"], "severity": sev, "rule": "LLM"})
        print(f"  [{i:2d}/{len(cases)}] {c['id']:4s} -> {sev:9s}"
              f"{' (cached)' if raw == 'cache' else ''}", file=sys.stderr)
        if raw != "cache":
            time.sleep(0.5)

    (ROOT / "results/raw_llm.json").write_text(json.dumps(out, indent=1))
    print(f"\n{hits}/{len(cases)} served from cache", file=sys.stderr)


if __name__ == "__main__":
    main()
