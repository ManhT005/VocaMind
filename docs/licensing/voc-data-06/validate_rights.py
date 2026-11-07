#!/usr/bin/env python3
"""Verify the historical audit, content-rights record, or app-release status."""
import argparse
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools/catalog"))
from audit_provenance import audit, insist, RightsAuditError


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--verify-audit", action="store_true")
    mode.add_argument("--verify-content-rights", action="store_true")
    mode.add_argument("--verify-app-release", "--require-release", action="store_true")
    parser.add_argument("--snapshot", type=Path)
    parser.add_argument("--approval", type=Path)
    parser.add_argument("--channel")
    args = parser.parse_args()
    try:
        report = audit()
        if args.verify_content_rights:
            insist(all((args.snapshot, args.approval, args.channel)),
                   "Content rights requires --snapshot, --approval and --channel")
            import content_rights
            report = content_rights.verify(args.snapshot, args.approval, args.channel)
        if args.verify_app_release:
            raise RightsAuditError(
                "APP_RELEASE_BLOCKED: distribution decision, device QA, DATA-08 and DATA-09 pending"
            )
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0
    except (ValueError, KeyError, TypeError, OSError, AttributeError) as exc:
        print(f"RIGHTS_AUDIT_BLOCKED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
