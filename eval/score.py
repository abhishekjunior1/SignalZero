#!/usr/bin/env python3
"""Score a system's triage output against the protocol-derived answer key.

Two numbers matter, and the second matters more:

  accuracy            exact severity match
  critical-miss rate  share of CRITICAL cases rated less severe

Under-triage is the error that kills someone. A system that shouts CRITICAL at
everything has a zero critical-miss rate and is useless, so over-triage is
reported alongside it rather than hidden.
"""
import json, sys, collections, pathlib

ROOT = pathlib.Path(__file__).resolve().parent
RANK = {"CRITICAL": 4, "SERIOUS": 3, "MODERATE": 2, "MINOR": 1, "UNKNOWN": 0}


def score(cases, results, label):
    got = {r["id"]: r for r in results}
    rows = []
    for c in cases:
        g = got.get(c["id"], {"severity": "UNKNOWN", "rule": "(no output)"})
        exp, act = c["expected"], g["severity"]
        rows.append({
            "id": c["id"], "tier": c["tier"], "expected": exp, "actual": act,
            "correct": exp == act,
            "under": RANK[act] < RANK[exp],
            "over": RANK[act] > RANK[exp],
            "rule": g.get("rule", ""),
            "text": c["text"],
        })

    crit = [r for r in rows if r["expected"] == "CRITICAL"]
    crit_missed = [r for r in crit if r["under"]]
    by_tier = collections.OrderedDict()
    for r in rows:
        t = by_tier.setdefault(r["tier"], {"n": 0, "ok": 0})
        t["n"] += 1
        t["ok"] += r["correct"]

    return {
        "system": label,
        "accuracy": round(sum(r["correct"] for r in rows) / len(rows), 3),
        "correct": sum(r["correct"] for r in rows),
        "total": len(rows),
        "critical_miss_rate": round(len(crit_missed) / len(crit), 3) if crit else 0.0,
        "critical_missed": [r["id"] for r in crit_missed],
        "over_triage": sum(r["over"] for r in rows),
        "by_tier": {k: {**v, "accuracy": round(v["ok"] / v["n"], 3)} for k, v in by_tier.items()},
        "rows": rows,
    }


def render(rep):
    print(f"\n{rep['system']}")
    print(f"  accuracy             {rep['correct']}/{rep['total']}  ({rep['accuracy']:.1%})")
    print(f"  CRITICAL missed      {len(rep['critical_missed'])}  ({rep['critical_miss_rate']:.1%})"
          f"  {rep['critical_missed'] or ''}")
    print(f"  over-triaged         {rep['over_triage']}")
    print(f"\n  {'tier':14s} {'ok':>7}  accuracy")
    for t, v in rep["by_tier"].items():
        print(f"  {t:14s} {v['ok']:>2}/{v['n']:<4} {v['accuracy']:>8.0%}")


if __name__ == "__main__":
    cases = json.loads((ROOT / "cases/triage.json").read_text())
    results = json.loads(pathlib.Path(sys.argv[1]).read_text())
    label = sys.argv[2]
    rep = score(cases, results, label)
    (ROOT / "results" / f"{label}.json").write_text(json.dumps(rep, indent=1))
    render(rep)
