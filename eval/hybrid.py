#!/usr/bin/env python3
"""The shipped architecture: the deterministic tree is authoritative, the LLM
fills the gap where the tree has no opinion.

Composition rule, in one line: if the safety tree matched a rule, its verdict
stands; only when the tree returns UNKNOWN/NO_MATCH does the LLM get a vote.

The asymmetry is deliberate and is the whole safety argument. The tree can only
ever be overruled where it has admitted it has nothing to say, so the LLM can
add coverage but can never soften a CRITICAL the tree has already assigned.

This is a pure function of the two systems' recorded outputs, so it re-runs
offline with no model access.
"""
import json, pathlib

R = pathlib.Path(__file__).resolve().parent / "results"

tree = {r["id"]: r for r in json.loads((R / "raw_safetytree.json").read_text())}
llm = {r["id"]: r for r in json.loads((R / "raw_llm.json").read_text())}

out = []
deferred = []
for cid, t in tree.items():
    if t["severity"] == "UNKNOWN":
        l = llm.get(cid, {"severity": "UNKNOWN"})
        out.append({"id": cid, "severity": l["severity"], "rule": f"LLM_FALLBACK({t['rule']})"})
        deferred.append(cid)
    else:
        out.append({"id": cid, "severity": t["severity"], "rule": t["rule"]})

(R / "raw_hybrid.json").write_text(json.dumps(out, indent=1))
print(f"tree authoritative on {len(out) - len(deferred)}/{len(out)} cases; "
      f"deferred to LLM on {len(deferred)}: {deferred}")
