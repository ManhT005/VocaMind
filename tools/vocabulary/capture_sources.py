"""Capture fixed research inputs without approving any source for ingestion."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
INPUTS = {
    'ngsl': [
        ('data', 'words.csv', 'https://www.newgeneralservicelist.com/s/NGSL_12_stats.csv'),
        ('license-evidence', 'source.html', 'https://www.newgeneralservicelist.com/new-general-service-list'),
        ('license-text', 'license.html', 'https://creativecommons.org/licenses/by-sa/4.0/legalcode.en'),
    ],
    'tsl': [
        ('data', 'words.csv', 'https://www.newgeneralservicelist.com/s/TSL_12_stats.csv'),
        ('license-evidence', 'source.html', 'https://www.newgeneralservicelist.com/toeic-service-list'),
        ('license-text', 'license.html', 'https://creativecommons.org/licenses/by-sa/4.0/legalcode.en'),
    ],
    'kaikki-viwiktionary': [
        ('license-evidence', 'source.html', 'https://kaikki.org/viwiktionary/'),
        ('download-index', 'rawdata.html', 'https://kaikki.org/viwiktionary/rawdata.html'),
        ('license-evidence', 'wikimedia-terms.html', 'https://foundation.wikimedia.org/wiki/Policy:Terms_of_Use'),
    ],
    'tatoeba-text': [
        ('license-evidence', 'terms.html', 'https://tatoeba.org/en/terms_of_use'),
        ('license-text', 'license.html', 'https://creativecommons.org/licenses/by/2.0/fr/legalcode.fr'),
        ('download-index', 'downloads.html', 'https://tatoeba.org/en/downloads'),
    ],
}


def capture(destination, inputs=INPUTS):
    destination.mkdir(parents=True, exist_ok=False)
    manifest = {'capturedAt': datetime.now(timezone.utc).isoformat(), 'sources': {}, 'errors': []}
    for sid, entries in inputs.items():
        artifacts = []
        manifest['sources'][sid] = artifacts
        for role, name, url in entries:
            try:
                request = urllib.request.Request(url, headers={'User-Agent': 'VocaMind-source-research/1.0'})
                with urllib.request.urlopen(request, timeout=25) as response:
                    content = response.read(10_000_001)
                    if len(content) > 10_000_000:
                        raise ValueError('Research artifact exceeds 10 MB limit')
                    final_url = response.url
                    if not final_url.startswith('https://'):
                        raise ValueError('Non-HTTPS redirect')
                    content_type = response.headers.get_content_type()
                    if role == 'data' and content_type == 'text/html':
                        raise ValueError('Expected data, received HTML')
                path = destination / sid / name
                path.parent.mkdir(exist_ok=True)
                path.write_bytes(content)
                artifacts.append({'role': role, 'requestedUrl': url, 'url': final_url,
                                  'path': path.relative_to(ROOT).as_posix(),
                                  'bytes': len(content), 'sha256': hashlib.sha256(content).hexdigest(),
                                  'contentType': content_type,
                                  'retrievedAt': datetime.now(timezone.utc).isoformat()})
                print(f'Captured {sid}/{name}: {len(content)} bytes', flush=True)
            except (OSError, ValueError) as error:
                manifest['errors'].append({'sourceId': sid, 'url': url, 'error': str(error)})
                print(f'Unavailable {sid}/{name}: {error}', flush=True)
    (destination / 'capture.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--id', default=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
    args = parser.parse_args()
    if not args.id or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_' for c in args.id):
        parser.error('Capture ID must contain only letters, digits, hyphen or underscore')
    manifest = capture(ROOT / 'docs/data/evidence' / args.id)
    print(f"Captured evidence; {len(manifest['errors'])} unavailable inputs. No source approval changed.")


if __name__ == '__main__':
    main()
