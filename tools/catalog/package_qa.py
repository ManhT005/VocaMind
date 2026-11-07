"""Generate and verify an internal QA asset bundle and the APK that carries it."""
import argparse
import json
import os
from pathlib import Path
import shutil
import tempfile
from zipfile import BadZipFile, ZipFile

import build_release as builder
import catalog_snapshot
from audit_provenance import audit

OUTPUT = Path('app/build/generated/voc06-qa')
ASSET_PREFIX = 'assets/database/'
FIXTURE = Path('app/src/main/assets/database')


def file_hashes(directory):
    return {p.relative_to(directory).as_posix(): builder.checksum(p)
            for p in sorted(directory.rglob('*')) if p.is_file()}


def publish(stage, target):
    """Publish a fresh tree in one rename or reuse an identical verified tree."""
    if target.exists() or target.is_symlink():
        builder.require(target.is_dir() and not target.is_symlink()
                        and file_hashes(stage) == file_hashes(target),
                        f'Existing output differs. Use a clean generated directory: {target}')
        return
    os.rename(stage, target)


def prepare(*, root=builder.ROOT):
    root = root.resolve()
    provenance = audit(root)
    snapshot = root / catalog_snapshot.QA_PATH
    snapshot.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.voc06-snapshot-', dir=snapshot.parent) as tmp:
        stage = Path(tmp) / 'snapshot'
        stage.mkdir()
        for name, raw in catalog_snapshot.compile_qa(root).items():
            (stage / name).write_bytes(raw)
        publish(stage, snapshot)
    catalog_snapshot.load(root, snapshot)

    target = root / OUTPUT
    target.parent.mkdir(parents=True, exist_ok=True)
    stages = [root / 'build' / name for name in (
        'vocabulary-ingestion', 'vocabulary-quality', 'catalog-release',
    )]
    for parent in stages:
        parent.mkdir(parents=True, exist_ok=True)
    with (tempfile.TemporaryDirectory(prefix='.voc06-qa-', dir=target.parent) as tmp,
          tempfile.TemporaryDirectory(prefix='voc06-', dir=stages[0]) as ingestion_tmp,
          tempfile.TemporaryDirectory(prefix='voc06-', dir=stages[1]) as qc_tmp,
          tempfile.TemporaryDirectory(prefix='voc06-', dir=stages[2]) as candidate_tmp):
        config = snapshot / 'ingestion-config.json'
        staged_ingestion = Path(ingestion_tmp) / 'batch'
        ingestion = stages[0] / 'voc06-qa-v2'
        qc = Path(qc_tmp) / 'batch'
        candidate = Path(candidate_tmp) / 'batch'
        builder.require(builder.ingest.run(config, staged_ingestion, root=root) == 0, 'QA ingestion failed')
        publish(staged_ingestion, ingestion)
        builder.require(builder.quality.run(ingestion, config, snapshot / 'reviews.json', qc,
                                            root=root) == 0, 'QA QC failed')
        builder.build(qc, candidate, root=root, snapshot_dir=snapshot)

        stage = Path(tmp) / 'bundle'
        assets = stage / 'assets/database'
        shutil.copytree(candidate, assets)
        shutil.copytree(qc, stage / 'qc')
        shutil.copyfile(snapshot / 'snapshot-manifest.json', stage / 'snapshot-manifest.json')
        builder.write_json(stage / 'provenance-report.json', provenance)
        builder.verify(qc, assets, root=root, snapshot_dir=snapshot)
        publish(stage, target)
    return target


def verify_apk(apk, *, root=builder.ROOT, fixture=False):
    root = root.resolve()
    expected = root / FIXTURE if fixture else root / OUTPUT / 'assets/database'
    names = set(file_hashes(expected))
    with ZipFile(apk) as archive:
        found = [name[len(ASSET_PREFIX):] for name in archive.namelist()
                 if name.startswith(ASSET_PREFIX) and not name.endswith('/')]
        builder.require(set(found) == names and len(found) == len(names),
                        'APK database asset set mismatch or duplicate ZIP entries')
        hashes = {}
        for name in sorted(names):
            raw = archive.read(ASSET_PREFIX + name)
            hashes[name] = builder.ingest.digest(raw)
            builder.require(hashes[name] == builder.checksum(expected / name),
                            'APK asset checksum mismatch: ' + name)
        if fixture:
            return {'profile': 'debug-fixture', 'apkSha256': builder.checksum(apk), 'assetHashes': hashes}
        with tempfile.TemporaryDirectory(prefix='voc06-apk-') as tmp:
            extracted = Path(tmp)
            for name in names:
                (extracted / name).write_bytes(archive.read(ASSET_PREFIX + name))
            metadata = builder.verify(root / OUTPUT / 'qc', extracted, root=root,
                                      snapshot_dir=root / catalog_snapshot.QA_PATH)
            report = builder.read_json(extracted / 'release-gate-report.json')
        return {'profile': 'internal-qa', 'apkSha256': builder.checksum(apk),
                'apkBytes': Path(apk).stat().st_size, 'assetHashes': hashes,
                'sqlQa': report['sqlQa'], 'datasetSha256': metadata['datasetSha256'],
                'releaseAuthorized': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--prepare', action='store_true')
    mode.add_argument('--verify-apk', type=Path)
    parser.add_argument('--fixture', action='store_true')
    args = parser.parse_args()
    try:
        if args.prepare:
            builder.require(not args.fixture, 'Preparation requires the internal QA profile')
            print(prepare())
        else:
            print(json.dumps(verify_apk(args.verify_apk, fixture=args.fixture), indent=2))
    except (ValueError, KeyError, TypeError, OSError, BadZipFile, builder.sqlite3.Error) as exc:
        parser.exit(1, f'QA_PACKAGING_BLOCKED: {exc}\n')


if __name__ == '__main__':
    main()
