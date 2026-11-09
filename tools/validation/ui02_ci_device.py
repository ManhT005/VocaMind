"""Explicit CI bootstrap for the disposable AOSP API26 image whose shell lacks CHANGE_WIFI_STATE.
Normal runner invocations never request root. No radio is changed by this bootstrap.
"""
import json
from pathlib import Path


def bootstrap_ci_emulator(adb, expected_api, output, execute):
    if expected_api not in ('26', '35'):
        raise ValueError('Only the declared CI matrix APIs are supported')
    evidence = {'expectedApi': expected_api, 'accepted': False, 'rootRequested': False}

    def checked(*arguments, timeout=10):
        result = execute(adb + list(arguments), timeout=timeout)
        if result.returncode:
            raise RuntimeError('CI device bootstrap command failed (%s): %s: %s' % (result.returncode, ' '.join(arguments), result.stdout))
        return result.stdout.strip()

    try:
        for name in ['ro.kernel.qemu', 'ro.debuggable', 'ro.build.version.sdk']:
            evidence[name] = checked('shell', 'getprop', name)
        if evidence['ro.kernel.qemu'] != '1' or evidence['ro.debuggable'] != '1' or evidence['ro.build.version.sdk'] != expected_api:
            raise RuntimeError('CI bootstrap requires the matching disposable, debuggable emulator; no root requested')
        evidence['uidBefore'] = checked('shell', 'id', '-u')
        if expected_api == '26':
            evidence['rootRequested'] = True
            evidence['rootOutput'] = checked('root')
            # ADB transport restarts, not the device or Activity. Bound the condition wait.
            checked('wait-for-device', timeout=30)
            evidence['uidAfter'] = checked('shell', 'id', '-u')
            if evidence['uidAfter'] != '0':
                raise RuntimeError('API26 Wi-Fi control requires root on this CI image; UID verification failed')
        else:
            evidence['uidAfter'] = evidence['uidBefore']
        evidence['accepted'] = True
    except Exception as error:
        evidence['error'] = repr(error)
        raise
    finally:
        (Path(output)/'ci-device-bootstrap.json').write_text(json.dumps(evidence, indent=2), encoding='utf-8')
