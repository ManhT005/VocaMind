"""Validate local credits against actual SQLite; consistency is not legal approval."""
import argparse
import json
from pathlib import Path
import sqlite3

ROOT = Path(__file__).resolve().parents[2]


def validate(document, pairs, release=False):
    if type(document.get('schemaVersion')) is not int or document['schemaVersion'] != 1:
        raise ValueError('Unsupported schemaVersion')
    if not isinstance(document.get('sources'), list):
        raise ValueError('sources must be an array')
    seen = set()
    missing = []
    for row in document['sources']:
        if not isinstance(row, dict):
            raise ValueError('Invalid source row')
        key = (row.get('sourceId'), row.get('snapshotDate'))
        if any(not isinstance(v, str) or not v.strip() for v in key):
            raise ValueError('Invalid source/snapshot')
        if key in seen or key not in pairs:
            raise ValueError('Duplicate, unknown source or mismatched snapshot: ' + str(key))
        seen.add(key)
        authors = row.get('authors')
        if not isinstance(authors, list) or any(not isinstance(a, str) or not a.strip() for a in authors):
            raise ValueError('Invalid authors')
        notice = row.get('creditText')
        if notice is not None and (not isinstance(notice, str) or not notice.strip()):
            raise ValueError('Invalid creditText')
        if not authors:
            missing.append(key)
    missing.extend(sorted(pairs - seen))
    if release and missing:
        raise ValueError('Release credit metadata missing: ' + str(missing))
    return {'matchedPairs': len(seen), 'missingAuthors': missing,
            'releaseCreditComplete': not missing, 'licenseApprovalVerified': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--release', action='store_true', help='Fail on missing authors; does not approve licenses')
    args = parser.parse_args()
    asset = ROOT / 'app/src/main/assets/database'
    with sqlite3.connect((asset / 'catalog.db').as_uri() + '?mode=ro', uri=True) as db:
        pairs = set(db.execute('SELECT id, snapshotDate FROM ContentSource'))
    document = json.loads((asset / 'content-credits.json').read_text(encoding='utf-8'))
    print(json.dumps(validate(document, pairs, args.release), indent=2))


if __name__ == '__main__':
    main()
