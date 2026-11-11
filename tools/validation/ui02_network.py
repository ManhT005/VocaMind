"""Network isolation for UI-02. Mutations and all evidence writes are inside cleanup scope.
Unknown/ambiguous effective state is rejected before changing the device.
"""
from contextlib import contextmanager
import json
from pathlib import Path
import re
import subprocess
import time

KEYS = ('airplane_mode_on', 'wifi_on', 'mobile_data')


def wifi_enabled(dump):
    states = re.findall(r'^\s*Wi-Fi is (enabled|disabled)\s*\.?\s*$', dump, re.MULTILINE)
    if len(states) != 1:
        raise RuntimeError('Expected one unambiguous effective Wi-Fi state in dumpsys wifi')
    return states[0] == 'enabled'


def require_offline(flags, wifi_dump, connectivity):
    if flags != dict(zip(KEYS, ('1', '0', '0'))):
        raise RuntimeError('Offline radio flags not settled: ' + repr(flags))
    if wifi_enabled(wifi_dump):
        raise RuntimeError('Effective Wi-Fi radio is still enabled')
    defaults = re.findall(r'^\s*Active default network:\s*(\S+)\s*$', connectivity, re.MULTILINE)
    if defaults != ['none']:
        raise RuntimeError('Expected exactly one Active default network: none; observed ' + repr(defaults))


class OfflineNetwork:
    def __init__(self, shell, output, *, deadline=20, clock=time.monotonic, pause=time.sleep):
        self.shell, self.output = shell, Path(output)
        self.deadline, self.clock, self.pause = deadline, clock, pause

    def checked(self, *command):
        # A settings write alone does not notify ConnectivityService/telephony.
        # Apply the controller action on setup and restoration, then preserve
        # the exact original setting (including absent keys).
        airplane = None
        if command[:4] == ('settings', 'put', 'global', 'airplane_mode_on'):
            airplane = command[4] == '1'
        elif command == ('settings', 'delete', 'global', 'airplane_mode_on'):
            airplane = False
        if airplane is not None:
            controller = self.shell('cmd', 'connectivity', 'airplane-mode', 'enable' if airplane else 'disable')
            unsupported = ('no shell command implementation' in controller.stdout.lower()
                           or 'unknown command' in controller.stdout.lower())
            if unsupported:
                # AOSP added this command after API26/27. Preserve legacy radio
                # setup there; require_offline still rejects any active network.
                sdk = self.shell('getprop', 'ro.build.version.sdk')
                if sdk.returncode or sdk.stdout.strip() not in ('26', '27'):
                    raise RuntimeError('Airplane controller unavailable on this device: ' + controller.stdout)
            elif controller.returncode:
                raise RuntimeError('Airplane controller failed: ' + controller.stdout)
        result = self.shell(*command)
        if result.returncode:
            raise RuntimeError('ADB shell failed (%s): %s: %s' % (result.returncode, ' '.join(command), result.stdout))
        return result.stdout

    def flags(self):
        return {key: self.checked('settings', 'get', 'global', key).strip() for key in KEYS}

    def setting(self, key, raw):
        if raw == 'null':
            self.checked('settings', 'delete', 'global', key)
        else:
            self.checked('settings', 'put', 'global', key, raw)

    def snapshot(self):
        flags = self.flags()
        if flags['airplane_mode_on'] not in ('0', '1', 'null') or flags['wifi_on'] not in ('0', '1', '2', '3', 'null'):
            raise RuntimeError('Unsupported original network settings; refusing to mutate: ' + repr(flags))
        wifi_dump = self.checked('dumpsys', 'wifi')
        enabled = wifi_enabled(wifi_dump)
        if flags['wifi_on'] != 'null' and enabled != (flags['wifi_on'] in ('1', '2')):
            raise RuntimeError('Original Wi-Fi flags/effective state disagree; refusing to mutate')
        data = flags['mobile_data']
        if data in ('0', '1'):
            data_enabled = data == '1'
        elif data == 'null':
            phone = self.checked('dumpsys', 'phone')
            states = re.findall(r'\bmUserDataEnabled\s*[=:]\s*(true|false)\b', phone)
            if len(states) != 1:
                raise RuntimeError('Absent mobile_data without unambiguous effective data policy; refusing to mutate')
            data_enabled = states[0] == 'true'
        else:
            raise RuntimeError('Unsupported original mobile_data; refusing to mutate')
        return {'flags': flags, 'wifiEnabled': enabled, 'dataEnabled': data_enabled, 'wifiDump': wifi_dump}

    def require_offline(self, folder):
        folder = Path(folder); folder.mkdir(parents=True, exist_ok=True)
        until = self.clock() + self.deadline
        observations = []
        while True:
            try:
                flags = self.flags()
                wifi = self.checked('dumpsys', 'wifi')
                connectivity = self.checked('dumpsys', 'connectivity')
                # Keep raw device evidence even when the precondition fails. Never launch on flags alone.
                (folder/'connectivity-before-launch.txt').write_text(connectivity, encoding='utf-8')
                (folder/'wifi-before-launch.txt').write_text(wifi, encoding='utf-8')
                observation = {'flags': flags}
                try:
                    require_offline(flags, wifi, connectivity)
                except RuntimeError as error:
                    observation['error'] = str(error)
                else:
                    observation['accepted'] = True
                    observations.append(observation)
                    (folder/'offline.json').write_text(json.dumps({'accepted': True, 'observations': observations}, indent=2), encoding='utf-8')
                    return
            except (RuntimeError, TimeoutError, subprocess.TimeoutExpired) as error:
                observation = {'error': str(error)}
            observations.append(observation)
            (folder/'offline.json').write_text(json.dumps({'accepted': False, 'observations': observations}, indent=2), encoding='utf-8')
            left = until - self.clock()
            if left <= 0:
                raise RuntimeError('Offline deadline exceeded: ' + observation['error'])
            self.pause(min(.2, left))

    def restore(self, original):
        errors = []
        actions = [lambda: self.setting('airplane_mode_on', original['flags']['airplane_mode_on']),
                   lambda: self.checked('svc', 'data', 'enable' if original['dataEnabled'] else 'disable'),
                   lambda: self.checked('svc', 'wifi', 'enable' if original['wifiEnabled'] else 'disable')]
        actions += [lambda key=key: self.setting(key, original['flags'][key]) for key in KEYS]
        for action in actions:
            try:
                action()
            except Exception as error:
                # A failed restore command must not prevent the remaining radios/settings from being restored.
                errors.append(repr(error))
        until = self.clock() + self.deadline
        observed = None
        while True:
            try:
                observed = self.snapshot()
                settled = (observed['flags'] == original['flags']
                           and observed['wifiEnabled'] == original['wifiEnabled']
                           and observed['dataEnabled'] == original['dataEnabled'])
                if settled:
                    break
            except Exception as error:
                observed = {'error': repr(error)}
            left = until - self.clock()
            if left <= 0:
                errors.append('Restored state did not match original before deadline')
                break
            self.pause(min(.2, left))
        report = {'accepted': not errors, 'original': original, 'observed': observed, 'errors': errors}
        (self.output/'network-restore.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
        if errors:
            raise RuntimeError('Network restore failed: ' + '; '.join(errors))

    def failure_diagnostics(self, failure):
        # Run after restoration attempts: diagnostics must not delay restoring a changed radio.
        report = {'failure': repr(failure), 'commands': []}
        commands = [('logcat', ('logcat', '-b', 'all', '-d', '-v', 'threadtime')),
                    ('memory', ('dumpsys', 'meminfo')),
                    ('processes', ('dumpsys', 'activity', 'processes')),
                    ('kernel', ('dmesg',)), ('identity', ('id',))]
        for name, command in commands:
            record = {'command': list(command)}
            try:
                result = self.shell(*command)
                record['returnCode'] = result.returncode
                filename = 'network-failure-' + name + '.txt'
                (self.output/filename).write_text(result.stdout, encoding='utf-8')
                record['file'] = filename
            except Exception as error:
                record['error'] = repr(error)
            report['commands'].append(record)
        try:
            (self.output/'network-failure-diagnostics.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
        except Exception as error:
            failure.add_note('Cannot save network failure diagnostics: ' + repr(error))

    @contextmanager
    def isolated(self):
        # Snapshot may fail, but it is read-only. No mutation occurs without a restorable original state.
        original = self.snapshot()
        primary = None
        try:
            (self.output/'network-original.json').write_text(json.dumps(original, indent=2), encoding='utf-8')
            self.checked('settings', 'put', 'global', 'airplane_mode_on', '1')
            self.checked('svc', 'wifi', 'disable')
            self.checked('svc', 'data', 'disable')
            # API35 occasionally acknowledges `svc data disable` while the global
            # policy flag still reads 1 until after our bounded setup deadline.
            # Persist the same disabled policy explicitly; restore() already puts
            # the exact original value back after every outcome.
            self.setting('mobile_data', '0')
            self.require_offline(self.output)
            yield self
        except BaseException as error:
            primary = error
            raise
        finally:
            try:
                self.restore(original)
            except Exception as cleanup_error:
                if primary is None:
                    self.failure_diagnostics(cleanup_error)
                    raise
                primary.add_note('Network cleanup also failed: ' + repr(cleanup_error))
            if primary is not None:
                self.failure_diagnostics(primary)
