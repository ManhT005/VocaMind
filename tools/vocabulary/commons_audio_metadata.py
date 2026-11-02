"""Research Commons file metadata only. Never download media or grant permission."""
import argparse
import base64
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
from urllib.parse import urlencode
import urllib.request


def metadata_url(title):
    if not title.startswith('File:') or len(title) <= 5 or '|' in title or len(title) > 255:
        raise ValueError('Supply one explicit Commons File: title')
    return 'https://commons.wikimedia.org/w/api.php?' + urlencode({
        'action': 'query', 'format': 'json', 'formatversion': '2', 'prop': 'imageinfo',
        'titles': title, 'iiprop': 'timestamp|sha1|mime|url|extmetadata', 'iilimit': '1',
    })


def fetch_metadata(title):
    url = metadata_url(title)
    request = urllib.request.Request(url, headers={
        'User-Agent': 'VocaMind-source-research/1.0 (https://github.com/ManhT005/VocaMind)'})
    with urllib.request.urlopen(request, timeout=25) as response:
        if not response.url.startswith('https://commons.wikimedia.org/w/api.php'):
            raise ValueError('Unexpected metadata endpoint redirect')
        raw = response.read(2_000_001)
    if len(raw) > 2_000_000:
        raise ValueError('Metadata response too large')
    payload = json.loads(raw)
    if 'error' in payload:
        raise ValueError('Commons API error: ' + str(payload['error'].get('code')))
    pages = payload.get('query', {}).get('pages', [])
    if not pages or not pages[0].get('imageinfo'):
        raise ValueError('File metadata unavailable')
    info = pages[0]['imageinfo'][0]
    metadata = info.get('extmetadata', {})
    missing = [field for field in ('Artist', 'LicenseShortName', 'LicenseUrl')
               if not metadata.get(field, {}).get('value')]
    return {'sourceId': 'commons-audio', 'title': title, 'queriedUrl': url,
            'retrievedAt': datetime.now(timezone.utc).isoformat(),
            'responseSha256': hashlib.sha256(raw).hexdigest(),
            'rawResponseBase64': base64.b64encode(raw).decode('ascii'),
            'rawResponse': payload, 'metadata': metadata,
            'descriptionUrl': info.get('descriptionurl'), 'mediaMime': info.get('mime'),
            'upstreamMediaSha1': info.get('sha1'), 'mediaFetched': False,
            'pipelineEligible': False, 'decision': 'hold',
            'blockers': ['Research only; per-file rights and reviewer approval required'] +
                        ['Missing metadata: ' + field for field in missing] +
                        ([] if str(info.get('mime', '')).startswith('audio/') else ['Not verified as audio MIME'])}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--title', required=True)
    parser.add_argument('--execute', action='store_true')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    try:
        url = metadata_url(args.title)
        if not args.execute:
            print(json.dumps({'mode': 'dry-run', 'metadataUrl': url, 'mediaFetched': False}))
            return
        if args.output and args.output.exists():
            raise ValueError('Output exists; choose a new metadata snapshot')
        result = fetch_metadata(args.title)
        content = json.dumps(result, indent=2) + '\n'
        if args.output:
            with args.output.open('x', encoding='utf-8', newline='\n') as stream:
                stream.write(content)
        else:
            print(content)
    except (ValueError, OSError, KeyError, TypeError) as error:
        parser.exit(1, f'BLOCKED: {error}\n')


if __name__ == '__main__':
    main()
