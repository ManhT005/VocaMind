#!/usr/bin/env python3
"""Audit historical lexical provenance without mutating accepted data or granting rights.

Usage:
  python docs/licensing/voc-data-06/validate_rights.py
  python docs/licensing/voc-data-06/validate_rights.py --require-release
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[3]
BASE = Path(__file__).resolve().parent
DRAFT = ROOT / "docs/data/voc-data-04/corpus-v1/candidate-catalog.json"
ACCEPTED = ROOT / "docs/data/voc-data-04/accepted-v1/canonical-preview.json"
NGSL = ROOT / "docs/data/evidence/voc-data-01-research-20261025/ngsl/words.csv"
SOURCE_REGISTRY = ROOT / "docs/data/source-registry.json"
AUDIT = BASE / "historical-provenance-audit.json"
RIGHTS = BASE / "release-rights-decision.json"
NGSL_HASH = "2098bab8955a120a9766c6282a51d7d578c6cb0a7d946600d2ffb73ba25a0b44"
DATASET_HASH = "c066d4a363d58c4c7226b716f1080b25a9e47b124a4007f4c3d16d67283a7fb8"
PACKS = {
    "pack_toeic_basic": (40, 34),
    "pack_toeic_office": (40, 34),
    "pack_daily_life": (40, 37),
    "pack_academic": (30, 30),
}


class RightsAuditError(ValueError):
    """A frozen input, provenance statement, or release gate failed."""


def insist(condition: bool, reason: str) -> None:
    if not condition:
        raise RightsAuditError(reason)


def load(path: Path) -> dict:
    with path.open(encoding="utf-8") as stream:
        return json.load(stream)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def audit() -> dict:
    draft, accepted, restored, rights, registry = [
        load(path) for path in (DRAFT, ACCEPTED, AUDIT, RIGHTS, SOURCE_REGISTRY)
    ]
    insist(sha256(NGSL) == NGSL_HASH, "NGSL archived CSV checksum changed")
    insist(accepted["fixtureOnly"] is False, "Accepted corpus unexpectedly fixture")
    insist(rights["acceptedDatasetSha256"] == DATASET_HASH, "Rights dataset binding changed")
    insist(restored["acceptedDatasetDigest"] == DATASET_HASH, "Audit dataset binding changed")

    with NGSL.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        ngsl_by_rank = {int(rec["SFI Rank"]): rec["Lemma"] for rec in reader}
    insist(len(ngsl_by_rank) >= 135, "NGSL source file incomplete")

    draft_entries = {x["id"]: x for x in draft["entries"]}
    current_entries = {x["id"]: x for x in accepted["entries"]}
    restored_entries = {x["stableId"]: x for x in restored["rows"]}
    insist(len(draft_entries) == len(current_entries) == len(restored_entries) == 150,
           "Expected 150 distinct stable IDs in all three inputs")
    insist(set(draft_entries) == set(current_entries) == set(restored_entries),
           "Draft/current/restored stable IDs diverge")
    assert_membership = {(x["packId"], x["vocabularyId"], x["orderNo"])
                         for x in accepted["packWords"]}
    insist(len(assert_membership) == 150, "Duplicate pack membership/order")
    draft_membership = {(x["packId"], x["vocabularyId"], x["orderNo"])
                        for x in draft["packWords"]}
    insist(draft_membership == assert_membership, "Pack order or membership changed")
    membership = {word_id: (pack_id, order_no) for pack_id, word_id, order_no in assert_membership}
    insist(len(membership) == 150, "Vocab ID appears more than once in pack membership")

    stats = {name: [0, 0] for name in PACKS}
    cited = 0
    for stable_id, word in current_entries.items():
        old = draft_entries[stable_id]
        row = restored_entries[stable_id]
        for field in ("lemma", "pos", "senseIndex", "meaningVi", "exampleEn", "exampleVi"):
            insist(word[field] == old[field], f"{stable_id}: frozen {field} changed")
        insist((row["lemma"], row["packId"], row["packOrder"]) ==
               (word["lemma"], *membership[stable_id]), f"{stable_id}: audit row mismatch")
        insist(word["sourceId"] == "vocamind-ai-editorial-v1",
               f"{stable_id}: accepted source no longer editorial")
        insist({p["component"] for p in word["provenance"]} ==
               {"lexical", "meaningVi", "examplePair"} and
               {p["sourceId"] for p in word["provenance"]} ==
               {"vocamind-ai-editorial-v1"}, f"{stable_id}: accepted components mismatch")
        editorial = [
            {"component": p["component"], "sourceId": p["sourceId"], "locator": p["locator"]}
            for p in old["provenance"] if p["sourceId"] == "vocamind-editorial-draft-v1"
        ]
        insist(set(p["component"] for p in editorial) ==
               {"lexical", "meaningVi", "examplePair"},
               f"{stable_id}: missing original editorial component")
        editorial.sort(key=lambda p: p["component"])
        insist(editorial == row["historicalEditorialComponents"],
               f"{stable_id}: historical editorial evidence mismatch")
        links = [p for p in old["provenance"]
                 if p["sourceId"] == "ngsl-12-20261025"]
        insist(all(p["component"] == "lexical" for p in links) and len(links) <= 1,
               f"{stable_id}: unexpected external component or duplicated NGSL")
        pack_id, _ = membership[stable_id]
        insist(pack_id in stats, f"{stable_id}: unknown pack")
        stats[pack_id][0] += 1
        ref = row["lexicalReference"]
        if links:
            cited += 1
            match = re.fullmatch(r".*ngsl/words\.csv#rank=(\d+)", links[0]["locator"])
            insist(match is not None, f"{stable_id}: malformed NGSL locator")
            rank = int(match.group(1))
            insist(ngsl_by_rank.get(rank, "").casefold() == word["lemma"].casefold(),
                   f"{stable_id}: NGSL rank mismatch")
            insist(ref["sourceId"] == links[0]["sourceId"] and
                   ref["locator"] == links[0]["locator"] and
                   ref["rank"] == rank and
                   ref["licenseCode"] == "CC-BY-SA-4.0",
                   f"{stable_id}: restored NGSL provenance mismatch")
            stats[pack_id][1] += 1
        else:
            insist(ref["sourceId"] is None and ref["rank"] is None and
                   ref["licenseCode"] is None,
                   f"{stable_id}: invented NGSL provenance")
    insist(cited == 135, f"Expected 135 NGSL historical references, got {cited}")
    insist({k: tuple(v) for k, v in stats.items()} == PACKS, "Pack audit counts changed")
    insist(restored["count"] == {
        "entries": 150,
        "ngslHistoricalLexicalReferences": 135,
        "noNgslHistoricalReference": 15,
        "editorialMeanings": 150,
        "editorialExamplePairs": 150,
    }, "Audit count header changed")
    sources = {x["id"]: x for x in registry["sources"]}
    insist(sources["ngsl"]["decision"] == "hold" and
           sources["ngsl"]["packagingStatus"] == "pending",
           "Original NGSL approval changed: re-review and update audit")
    insist(rights["releaseAuthorized"] is False and rights["status"] == "HOLD",
           "Unexpected authorization without an approved new snapshot")
    insist(rights["sourceSnapshots"][0]["dataSha256"] == NGSL_HASH,
           "Source rights manifest no longer matches NGSL")
    insist(rights["approvedBy"] is None and rights["approvedAt"] is None,
           "Approval fields edited without signed release procedure")

    return {"status": "AUDIT_PASS_RELEASE_HOLD", "entries": 150,
            "historicalNgslVerified": cited, "editorialOnly": 15,
            "packStats": stats, "releaseAuthorized": False}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-release", action="store_true",
                        help="Fail closed until a separate signed rights/promotion process")
    args = parser.parse_args()
    try:
        report = audit()
        print(json.dumps(report, ensure_ascii=False, indent=2))
        if args.require_release:
            raise RightsAuditError(
                "RELEASE_BLOCKED: unsigned editorial public license, NGSL scope review, "
                "new approved snapshot, Android offline attribution and QA/DATA-08"
            )
        return 0
    except (RightsAuditError, KeyError, TypeError, OSError, json.JSONDecodeError) as exc:
        print(f"RIGHTS_AUDIT_BLOCKED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
