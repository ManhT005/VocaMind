"""Bounded, read-only retrieval of UI-02 failure evidence; never reruns instrumentation."""
import io
import json
import re
import shutil
import subprocess
import tarfile
import tempfile
import time
from pathlib import Path


def validate_artifacts(folder, tested, expected_test=None):
    found = {name: list(Path(folder).rglob(name)) for name in ('screenshot.png', 'logcat.txt', 'metadata.json')}
    if not all(len(paths) == 1 for paths in found.values()):
        return False, 'Expected one screenshot, logcat and metadata from the app rule'
    try:
        screenshot = found['screenshot.png'][0].read_bytes()
        log = found['logcat.txt'][0].read_text(encoding='utf-8', errors='replace')
        metadata = json.loads(found['metadata.json'][0].read_text(encoding='utf-8'))
        if not isinstance(metadata, dict):
            return False, 'Artifact metadata is not an object'
        valid = (screenshot.startswith(b'\x89PNG\r\n\x1a\n') and screenshot.endswith(b'\x00\x00\x00\x00IEND\xaeB`\x82')
                 and re.search(r'^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+', log, re.MULTILINE) is not None
                 and metadata.get('testedRevision') == tested and not metadata.get('artifactErrors')
                 and (expected_test is None or metadata.get('test') == expected_test))
        return bool(valid), 'Artifact contents verified' if valid else 'Artifact content or identity check failed: ' + repr(metadata.get('artifactErrors', []))
    except (OSError, UnicodeError, ValueError) as error:
        return False, repr(error)


def collect_artifacts(adb, package, folder, tested, *, required=False, expected_test=None,
                      execute=subprocess.run, deadline=20, clock=time.monotonic, pause=time.sleep):
    folder = Path(folder)
    until = clock() + deadline
    attempts = []
    while True:
        attempt = {'attempt': len(attempts) + 1}
        try:
            pull = execute(adb + ['exec-out', 'run-as', package, 'tar', '-cf', '-', 'files/ui02-failures'],
                           stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=max(.1, min(10, until-clock())))
            attempt.update({'returnCode': pull.returncode, 'bytes': len(pull.stdout),
                            'stderr': pull.stderr.decode('utf-8', errors='replace')})
            (folder/'artifact-pull.txt').write_bytes(pull.stderr)
            (folder/('artifact-pull-%02d.tar' % attempt['attempt'])).write_bytes(pull.stdout)
            if pull.returncode:
                raise RuntimeError('ADB artifact transfer failed: ' + attempt['stderr'])
            # A transport cut can otherwise look like a valid TAR containing only the first PNG.
            if len(pull.stdout) < 1024 or not pull.stdout.endswith(bytes(1024)):
                raise tarfile.ReadError('Missing TAR end-of-archive blocks (incomplete ADB transfer)')
            with tarfile.open(fileobj=io.BytesIO(pull.stdout)) as archive:
                attempt['members'] = archive.getnames()
                stage = Path(tempfile.mkdtemp(prefix='pull-%02d-' % attempt['attempt'], dir=folder))
                attempt['directory'] = stage.name
                archive.extractall(stage, filter='data')
            # Never combine partial reads with files left by an earlier attempt.
            accepted, reason = validate_artifacts(stage, tested, expected_test)
            if accepted:
                shutil.copytree(stage, folder/'artifacts')
            attempt.update({'accepted': accepted, 'reason': reason})
        except (OSError, RuntimeError, tarfile.TarError, subprocess.TimeoutExpired) as error:
            accepted = False
            attempt.update({'accepted': False, 'reason': repr(error)})
        attempts.append(attempt)
        (folder/'artifact-collection.json').write_text(json.dumps({'required': required, 'accepted': accepted, 'attempts': attempts}, indent=2), encoding='utf-8')
        if accepted or not required or clock() >= until:
            return accepted
        # Retry the read, not the test. All failed attempts remain visible in the report.
        pause(max(0, min(.2, until-clock())))
