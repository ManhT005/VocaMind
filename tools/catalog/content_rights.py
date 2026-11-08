"""Check a supplied rights record and evidence binding for the frozen QA dataset."""
from datetime import date
from pathlib import Path

import build_release as builder
import catalog_snapshot
from audit_provenance import NGSL_HASH

CHANNELS = {'internal-qa', 'public-repository', 'public-apk'}
EDITORIAL = {'editorial-lexical', 'editorial-meaning', 'editorial-example-pair'}
NGSL_AUTHORS = ['Charles Browne', 'Brent Culligan', 'Joseph Phillips']
NGSL_SOURCE = 'https://www.newgeneralservicelist.com/new-general-service-list'
NGSL_LICENSE = 'https://creativecommons.org/licenses/by-sa/4.0/legalcode'


def present(value):
    return isinstance(value, str) and bool(value.strip())


def verify(snapshot, approval_path, channel, *, root=builder.ROOT):
    root, snapshot, approval_path = (Path(p).resolve() for p in (root, snapshot, approval_path))
    require = builder.require
    require(channel in CHANNELS, 'Unknown distribution channel')
    require(snapshot == root / catalog_snapshot.QA_PATH, 'Select the versioned QA snapshot')
    catalog_snapshot.load(root, snapshot)
    data = builder.read_json(snapshot / 'canonical-preview.json')
    record = builder.read_json(approval_path)
    require(type(record.get('schemaVersion')) is int and record['schemaVersion'] == 1
            and record.get('decision') == 'approved'
            and record.get('role') in {'PO', 'Content Lead'}
            and present(record.get('approvedBy'))
            and record.get('contentReview') == 'approved'
            and record.get('outstandingConditions') == [],
            'Content and rights approval record required')
    date.fromisoformat(record['approvedAt'])
    channels = record.get('channels')
    require(isinstance(channels, list) and all(value in CHANNELS for value in channels)
            and channel in channels, 'Approval scope does not include the requested channel')
    require(record.get('snapshotId') == 'editorial-qa-v2'
            and record.get('snapshotManifestSha256') == builder.checksum(snapshot / 'snapshot-manifest.json')
            and record.get('datasetSha256') == builder.build_quiz_policy.dataset_digest(data),
            'Approval is bound to a different snapshot or dataset')

    for field in ('signedDecision', 'reviewedNotice'):
        artifact = record[field]
        name = artifact['file']
        require(present(name) and Path(name).name == name, 'Evidence must be a sibling file')
        path = (approval_path.parent / name).resolve()
        require(path != approval_path and path.parent == approval_path.parent
                and path.is_file() and path.stat().st_size > 0
                and builder.checksum(path) == artifact['sha256'], 'Approval evidence mismatch: ' + field)

    components = record.get('components', {})
    require(set(components) == EDITORIAL | {'ngsl-reference'}, 'Component decision coverage mismatch')
    source = data['sources'][0]
    source_id = source['id']
    for name in EDITORIAL:
        entry = components[name]
        require(entry.get('sourceId') == source_id and entry.get('status') == 'authorized'
                and entry.get('sourceSha256') == source['snapshotSha256']
                and present(entry.get('permissionBasis')),
                'Missing editorial permission: ' + name)
    ngsl = components['ngsl-reference']
    require(ngsl.get('sourceId') == 'ngsl' and ngsl.get('sourceSha256') == NGSL_HASH
            and present(ngsl.get('permissionBasis')),
            'Reviewed NGSL treatment required')
    if ngsl.get('status') == 'redistributed':
        require(ngsl.get('authors') == NGSL_AUTHORS and ngsl.get('sourceUrl') == NGSL_SOURCE
                and ngsl.get('licenseCode') == 'CC-BY-SA-4.0'
                and ngsl.get('licenseUrl') == NGSL_LICENSE and present(ngsl.get('changesNotice'))
                and present(ngsl.get('shareAlikeTreatment')), 'Incomplete NGSL attribution or treatment')
    else:
        require(ngsl.get('status') == 'history-only', 'Unresolved NGSL scope')
    builder.check_credits.validate_rights_credits(
        record['sourceCredits'], {(row['id'], row['snapshotDate']) for row in data['sources']})
    return {'recordStatus': 'verified', 'channel': channel,
            'datasetSha256': record['datasetSha256'], 'approvalRecordSha256': builder.checksum(approval_path),
            'releaseAuthorized': False}
