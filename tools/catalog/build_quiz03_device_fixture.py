#!/usr/bin/env python3
"""Package the complete, verified accepted-corpus bundle in the isolated device APK."""

import argparse
import json
from pathlib import Path
import shutil

from build_release import OUTPUTS, checksum


def build(candidate, output):
    output.mkdir(parents=True, exist_ok=True)
    for name in (*OUTPUTS, 'manifest.json'):
        shutil.copyfile(candidate / name, output / name)
    metadata = json.loads((output / 'catalog.metadata.json').read_text(encoding='utf-8'))
    evidence = {
        'kind': 'QUIZ-03-isolated-device-test-only-not-release',
        'canonicalSha256': checksum(candidate / 'canonical-catalog.json'),
        'databaseSha256': checksum(output / 'catalog.db'),
        'vocabularyCount': metadata['uniqueVocabularyCount'],
    }
    (output / 'quiz03-fixture-metadata.json').write_text(
        json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(evidence))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('candidate', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    build(args.candidate, args.output)
