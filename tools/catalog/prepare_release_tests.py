"""Generate isolated RC assets for host/device tests, without touching APK main assets."""

import argparse
from pathlib import Path
import shutil
import tempfile

import build_release as builder


def prepare(output_dir):
    root = builder.ROOT
    output_dir = Path(output_dir).resolve()
    allowed = root / 'app/build/generated/catalog-release-test-assets'
    builder.require(output_dir == allowed, 'Only the generated test asset directory is allowed')
    snapshot = root / builder.accept_editorial.ACCEPTED
    config = snapshot / 'ingestion-config.json'
    parents = [root / 'build' / name for name in (
        'vocabulary-ingestion', 'vocabulary-quality', 'catalog-release',
    )]
    for parent in parents:
        parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix='android-', dir=parents[0]) as ingestion_tmp, \
            tempfile.TemporaryDirectory(prefix='android-', dir=parents[1]) as qc_tmp, \
            tempfile.TemporaryDirectory(prefix='android-', dir=parents[2]) as candidate_tmp:
        ingestion = Path(ingestion_tmp) / 'batch'
        qc = Path(qc_tmp) / 'batch'
        candidate = Path(candidate_tmp) / 'batch'
        builder.require(builder.ingest.run(config, ingestion) == 0, 'Test ingestion failed')
        builder.require(builder.quality.run(ingestion, config, snapshot / 'reviews.json', qc) == 0,
                        'Test production QC failed')
        builder.build(qc, candidate)

        target = output_dir / 'release-candidate'
        target.mkdir(parents=True, exist_ok=True)
        for source in sorted(candidate.iterdir()):
            shutil.copyfile(source, target / source.name)
        shutil.copyfile(qc / 'canonical-catalog.json', target / 'canonical-catalog.json')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-dir', type=Path, required=True)
    prepare(parser.parse_args().output_dir)
