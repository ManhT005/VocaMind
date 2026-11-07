"""Derive the internal QA catalog from the frozen v1 worksheet decisions."""
from pathlib import Path
import json

import accept_editorial
import ingest
import quality
from gate_sources import registry_digest
from source_gate import snapshot_content_digest

QA_PATH = Path('build/vocabulary-corpus/voc06-qa-v2')
QA_VERSION = '1.0.1'
V1_SOURCE_URL = ('https://github.com/ManhT005/VocaMind/blob/'
                 '795c9dc3fd6525321c00eb73d8f565a948175a09/')
DESCRIPTIONS = {
    'pack_toeic_basic': '40 từ tiếng Anh thường dùng trong mua sắm, di chuyển, lưu trú và giao tiếp cơ bản.',
    'pack_toeic_office': '40 từ tiếng Anh về môi trường công sở, giao dịch, tài liệu và công việc.',
    'pack_daily_life': '40 từ tiếng Anh về gia đình, nhà ở, sinh hoạt và hoạt động hằng ngày.',
    'pack_academic': '30 từ tiếng Anh học thuật phổ biến trong học tập, phân tích và nghiên cứu.',
}


def frozen_v1(root):
    files = accept_editorial.compile_accepted(root)
    for name, expected in files.items():
        ingest.require((root / accept_editorial.ACCEPTED / name).read_bytes() == expected,
                       'Frozen accepted artifact changed: ' + name)
    return files


def compile_qa(root):
    original = frozen_v1(root)
    files = dict(original)
    data = json.loads(original['canonical-preview.json'])
    config = json.loads(original['ingestion-config.json'])
    reviews = json.loads(original['reviews.json'])
    policy = json.loads(original['quiz-policy.json'])
    registry = json.loads(original['source-registry.json'])
    inventory = json.loads(original['inventory.json'])
    source = registry['sources'][0]
    source['licenseUrl'] = V1_SOURCE_URL + accept_editorial.DECISION.as_posix()
    artifacts = source['snapshot']['artifacts']
    for artifact in artifacts:
        artifact['url'] = V1_SOURCE_URL + artifact['path']
    source['review']['snapshotSha256'] = snapshot_content_digest(source['snapshot'], source)
    inventory['registrySha256'] = registry_digest(registry)
    inventory['sources'][source['id']] = artifacts
    data['sources'][0]['url'] = next(a['url'] for a in artifacts if a['role'] == 'data')
    data['sources'][0]['licenseUrl'] = source['licenseUrl']
    config['sourceRegistry'] = (QA_PATH / 'source-registry.json').as_posix()
    config['inventory'] = (QA_PATH / 'inventory.json').as_posix()

    data['contentVersion'] = config['contentVersion'] = QA_VERSION
    for pack in data['packs']:
        pack['description'] = DESCRIPTIONS[pack['id']]
        pack['version'] = QA_VERSION
    config['packs'] = data['packs']
    by_id = {entry['id']: entry for entry in data['entries']}
    for entry in by_id.values():
        entry['contentVersion'] = QA_VERSION
    # Only the version changes on each entry. The v1 text, evidence and reviewer
    # decisions remain the basis for QC. New pack wording awaits final review.
    reviews['datasetSha256'] = policy['datasetSha256'] = quality.entry_digest(data)
    for decision in reviews['records']:
        decision['entrySha256'] = quality.entry_digest(by_id[decision['stableId']])
    files.update({
        'canonical-preview.json': ingest.encoded(data),
        'source-registry.json': ingest.encoded(registry),
        'inventory.json': ingest.encoded(inventory),
        'ingestion-config.json': ingest.encoded(config),
        'reviews.json': ingest.encoded(reviews),
        'quiz-policy.json': ingest.encoded(policy),
    })
    files['snapshot-manifest.json'] = ingest.encoded({
        'schemaVersion': 1,
        'snapshotId': 'editorial-qa-v2',
        'scope': 'internal-qa',
        'contentVersion': QA_VERSION,
        'packDescriptionReview': 'pending',
        'inheritedDecision': str(accept_editorial.ACCEPTED / 'acceptance.json'),
        'baselineHashes': {name: ingest.digest(raw) for name, raw in sorted(original.items())},
        'snapshotHashes': {name: ingest.digest(raw) for name, raw in sorted(files.items())},
    })
    return files


def load(root, directory):
    directory = Path(directory).resolve()
    if directory == root / accept_editorial.ACCEPTED:
        return frozen_v1(root)
    ingest.require(directory == root / QA_PATH, 'Unknown catalog snapshot location')
    expected = compile_qa(root)
    ingest.require({p.name for p in directory.iterdir()} == set(expected),
                   'QA snapshot file set mismatch')
    for name, raw in expected.items():
        ingest.require((directory / name).read_bytes() == raw, 'QA snapshot changed: ' + name)
    return expected
