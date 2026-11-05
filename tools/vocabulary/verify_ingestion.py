"""Run ingestion twice in new directories and compare all four files byte-for-byte."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

from source_gate import ROOT, require


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--fixture', action='store_true')
    args = parser.parse_args()
    output = args.output.resolve()
    try:
        require(output.is_relative_to(ROOT / 'build/vocabulary-ingestion')
                and output != ROOT / 'build/vocabulary-ingestion', 'Unsafe evidence output')
        require(not output.exists(), 'Evidence output already exists')
        output.mkdir(parents=True)
        for name in ('a', 'b'):
            command = [sys.executable, str(ROOT / 'tools/vocabulary/ingest.py'),
                       '--config', str(args.config.resolve()), '--output', str(output / name)]
            if args.fixture:
                command.append('--fixture')
            result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
            (output / (name + '.log')).write_text(result.stdout + result.stderr, encoding='utf-8')
            require(result.returncode == 0, f'Run {name} failed; inspect its log')
        expected = {'canonical-catalog.json', 'normalization.json', 'reject-report.json', 'manifest.json'}
        hashes = {}
        for name in ('a', 'b'):
            require({p.name for p in (output / name).iterdir()} == expected, 'Unexpected output set')
        for filename in sorted(expected):
            raw = (output / 'a' / filename).read_bytes()
            require(raw == (output / 'b' / filename).read_bytes(), 'Different bytes: ' + filename)
            hashes[filename] = hashlib.sha256(raw).hexdigest()
        result = {'identicalFiles': 4, 'sha256': hashes}
        (output / 'comparison.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(result, indent=2))
        return 0
    except (ValueError, OSError) as exc:
        print(str(exc), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
