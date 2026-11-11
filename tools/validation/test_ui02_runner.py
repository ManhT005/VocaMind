"""Host-only fake ADB regression. These are runner checks, not Android integration evidence."""
import copy
import io
import json
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from ui02_network import OfflineNetwork, require_offline
from ui02_artifacts import collect_artifacts, validate_artifacts
import run_ui02_device as runner


class Clock:
    def __init__(self): self.value = 0
    def now(self): return self.value
    def pause(self, seconds): self.value += seconds


class FakeAdb:
    def __init__(self, *, wifi='2', airplane='0', data='1'):
        self.raw = {'airplane_mode_on': airplane, 'wifi_on': wifi, 'mobile_data': data}
        self.wifi = wifi in ('1', '2')
        self.data = data != '0'
        self.initial = (copy.deepcopy(self.raw), self.wifi, self.data)
        self.commands = []
        self.failure = None
        self.ignore_disable = False
        self.connectivity = None
        self.phone = None
        self.instrumentations = 0

    def shell(self, *args):
        self.commands.append(args)
        if self.failure:
            error = self.failure(args)
            if error:
                if isinstance(error, BaseException): raise error
                return SimpleNamespace(returncode=1, stdout='injected ADB failure')
        if args[:3] == ('settings', 'get', 'global'):
            return self.result(self.raw.get(args[3], '1'))
        if args[:3] == ('settings', 'put', 'global'):
            self.raw[args[3]] = args[4]
            return self.result('')
        if args[:3] == ('settings', 'delete', 'global'):
            self.raw[args[3]] = 'null'
            return self.result('Deleted 1 rows')
        if args[:2] == ('svc', 'wifi'):
            if not (self.ignore_disable and args[2] == 'disable'):
                self.wifi = args[2] == 'enable'; self.raw['wifi_on'] = '1' if self.wifi else '0'
            return self.result('')
        if args[:2] == ('svc', 'data'):
            self.data = args[2] == 'enable'; self.raw['mobile_data'] = '1' if self.data else '0'
            return self.result('')
        if args == ('dumpsys', 'wifi'):
            return self.result('Wi-Fi is ' + ('enabled' if self.wifi else 'disabled') + '\n')
        if args == ('dumpsys', 'phone'):
            return self.result(self.phone if self.phone is not None else 'mUserDataEnabled=' + str(self.data).lower())
        if args == ('dumpsys', 'connectivity'):
            return self.result(self.connectivity if self.connectivity is not None else 'Active default network: none\n')
        if args[:2] == ('pm', 'clear'): return self.result('Success')
        if args == ('getprop',): return self.result('[ro.build.version.sdk]: [mock]')
        if args in [('logcat','-b','all','-d','-v','threadtime'),('dumpsys','meminfo'),('dumpsys','activity','processes'),('dmesg',),('id',)]:
            return self.result('mock-only network failure diagnostics')
        if args[:2] == ('am', 'instrument'):
            self.instrumentations += 1
            return self.result('INSTRUMENTATION_STATUS: class=MockOnly\nINSTRUMENTATION_STATUS: test=mock\nINSTRUMENTATION_STATUS_CODE: 0\nINSTRUMENTATION_CODE: -1\n')
        raise AssertionError('Unexpected fake ADB command: ' + repr(args))

    @staticmethod
    def result(stdout): return SimpleNamespace(returncode=0, stdout=stdout)

    def run(self, command, **kwargs):
        if command[:1] == ['git']:
            return self.result('mock-head\n' if command[1] == 'rev-parse' else '')
        if command[:1] == ['java']: return self.result('mock Java; no Android executed')
        assert command[:4] == ['fake-adb', '-s', 'mock-only', 'shell'], command
        return self.shell(*command[4:])

    def assert_restored(self, test): test.assertEqual(self.initial, (self.raw, self.wifi, self.data))


class NetworkTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.out = Path(self.temp.name); self.clock = Clock()

    def session(self, adb):
        return OfflineNetwork(adb.shell, self.out, deadline=.4, clock=self.clock.now, pause=self.clock.pause)

    def test_success_restores_wifi_two_and_effective_radio(self):
        adb=FakeAdb()
        with self.session(adb).isolated() as network:
            network.require_offline(self.out/'case')
            self.assertFalse(adb.wifi)
        adb.assert_restored(self)
        self.assertTrue(json.loads((self.out/'network-restore.json').read_text())['accepted'])
        self.assertIn(('svc','wifi','enable'), adb.commands)
        self.assertIn(('settings','put','global','mobile_data','0'), adb.commands)
        self.assertEqual('Active default network: none\n',(self.out/'case/connectivity-before-launch.txt').read_text())

    def test_all_supported_wifi_values_restore_exact_flags_and_effective_state(self):
        for value in ['0','1','2','3','null']:
            with self.subTest(value=value):
                adb=FakeAdb(wifi=value)
                with self.session(adb).isolated(): pass
                adb.assert_restored(self)
                if value=='null': self.assertIn(('settings','delete','global','wifi_on'),adb.commands)

    def test_null_airplane_and_data_restore_absent_keys_with_known_effective_policy(self):
        adb=FakeAdb(airplane='null',data='null',wifi='null')
        with self.session(adb).isolated(): pass
        adb.assert_restored(self)
        for key in adb.raw: self.assertIn(('settings','delete','global',key), adb.commands)

    def test_unknown_null_data_policy_refuses_mutation(self):
        adb=FakeAdb(data='null'); adb.phone='no effective policy available'
        with self.assertRaisesRegex(RuntimeError,'unambiguous effective data policy'):
            with self.session(adb).isolated(): self.fail('must not enter')
        adb.assert_restored(self)
        self.assertFalse(any(c[:1]==('svc',) or c[:2]==('settings','put') for c in adb.commands))

    def test_setup_radio_flags_failure_restores_before_raise(self):
        adb=FakeAdb(); adb.ignore_disable=True
        with self.assertRaisesRegex(RuntimeError,'Offline deadline'):
            with self.session(adb).isolated(): self.fail('must not enter')
        adb.assert_restored(self)

    def test_setup_shell_nonzero_restores_every_radio(self):
        adb=FakeAdb(); adb.failure=lambda c: c==('svc','wifi','disable')
        with self.assertRaisesRegex(RuntimeError,'ADB shell failed'):
            with self.session(adb).isolated(): self.fail('must not enter')
        adb.assert_restored(self)

    def test_setup_timeout_restores_every_radio(self):
        adb=FakeAdb(); adb.failure=lambda c: subprocess.TimeoutExpired('fake-adb',10) if c==('svc','data','disable') else None
        with self.assertRaises(subprocess.TimeoutExpired):
            with self.session(adb).isolated(): self.fail('must not enter')
        adb.assert_restored(self)

    def test_failure_diagnostics_run_after_restoration_and_keep_primary_error(self):
        adb=FakeAdb()
        with self.assertRaisesRegex(ValueError,'original failure'):
            with self.session(adb).isolated(): raise ValueError('original failure')
        adb.assert_restored(self)
        report=json.loads((self.out/'network-failure-diagnostics.json').read_text())
        self.assertIn('original failure',report['failure'])
        self.assertTrue(all(c['returnCode']==0 for c in report['commands']))
        self.assertLess(adb.commands.index(('settings','put','global','wifi_on','2')),
                        adb.commands.index(('logcat','-b','all','-d','-v','threadtime')))

    def test_offline_evidence_write_failure_still_restores(self):
        adb=FakeAdb(); write=Path.write_text
        def writing(p,*a,**kw):
            if p.name=='offline.json': raise OSError('injected disk error')
            return write(p,*a,**kw)
        with patch.object(Path,'write_text',writing):
            with self.assertRaisesRegex(OSError,'injected disk error'):
                with self.session(adb).isolated(): self.fail('must not enter')
        adb.assert_restored(self)

    def test_test_body_failure_restores(self):
        adb=FakeAdb()
        with self.assertRaisesRegex(ValueError,'primary test failure'):
            with self.session(adb).isolated(): raise ValueError('primary test failure')
        adb.assert_restored(self)

    def test_restore_error_is_reported_and_other_radios_still_attempted(self):
        adb=FakeAdb(); adb.failure=lambda c: c==('svc','wifi','enable')
        with self.assertRaisesRegex(RuntimeError,'Network restore failed'):
            with self.session(adb).isolated(): pass
        self.assertEqual('0',adb.raw['airplane_mode_on']); self.assertEqual('1',adb.raw['mobile_data'])
        self.assertFalse(json.loads((self.out/'network-restore.json').read_text())['accepted'])
        self.assertIn(('settings','put','global','wifi_on','2'),adb.commands)

    def test_primary_error_not_masked_by_restore_failure(self):
        adb=FakeAdb(); adb.failure=lambda c: c==('svc','wifi','enable')
        with self.assertRaisesRegex(ValueError,'original failure') as caught:
            with self.session(adb).isolated(): raise ValueError('original failure')
        self.assertTrue(any('cleanup also failed' in n for n in caught.exception.__notes__))

    def test_rejects_active_missing_ambiguous_network_and_contradictory_radios(self):
        valid={'airplane_mode_on':'1','wifi_on':'0','mobile_data':'0'}
        for connectivity in ['Active default network: 101\n','', 'Active default network: none\nActive default network: none\n','Active default network: none extra\n']:
            with self.subTest(connectivity=connectivity), self.assertRaises(RuntimeError):
                require_offline(valid,'Wi-Fi is disabled\n',connectivity)
        for bad in [dict(valid,wifi_on='2'),dict(valid,mobile_data='1'),dict(valid,airplane_mode_on='0')]:
            with self.subTest(flags=bad),self.assertRaises(RuntimeError):
                require_offline(bad,'Wi-Fi is disabled\n','Active default network: none\n')
        with self.assertRaisesRegex(RuntimeError,'Effective Wi-Fi'):
            require_offline(valid,'Wi-Fi is enabled\n','Active default network: none\n')

    def test_active_network_blocks_body_and_is_kept_as_evidence(self):
        adb=FakeAdb(); adb.connectivity='Active default network: 101\n'
        with self.assertRaisesRegex(RuntimeError,'Offline deadline'):
            with self.session(adb).isolated(): self.fail('must not enter')
        adb.assert_restored(self)
        self.assertIn('101',(self.out/'connectivity-before-launch.txt').read_text())
        self.assertFalse(json.loads((self.out/'offline.json').read_text())['accepted'])

    def test_pending_network_can_settle_before_deadline(self):
        adb=FakeAdb(); original=adb.shell; pending=[True]
        def shell(*args):
            if args==('dumpsys','connectivity') and pending:
                pending.pop()
                return adb.result('Active default network: 101\n')
            return original(*args)
        network=OfflineNetwork(shell,self.out,deadline=.4,clock=self.clock.now,pause=self.clock.pause)
        with network.isolated(): pass
        adb.assert_restored(self)

    def main_with_fake_adb(self, adb):
        for path in ['app/build/outputs/apk/discovery/app-discovery.apk','app/build/outputs/apk/androidTest/discovery/app-discovery-androidTest.apk']:
            p=self.out/path; p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(b'mock APK; not installed')
        import os
        old=Path.cwd()
        try:
            os.chdir(self.out)
            with patch.object(runner,'run',adb.run), patch.object(runner,'collect_artifacts',return_value=False), patch.object(runner,'OfflineNetwork',side_effect=lambda shell,out: OfflineNetwork(shell,out,deadline=.4,clock=self.clock.now,pause=self.clock.pause)), patch.object(sys,'argv',['runner','--adb','fake-adb','--serial','mock-only','--skip-install','--output',str(self.out/'result'),'--only','MockOnly#mock']):
                return runner.main()
        finally: os.chdir(old)

    def test_real_main_control_flow_restores_wifi_two(self):
        adb=FakeAdb()
        self.assertEqual(0,self.main_with_fake_adb(adb))
        adb.assert_restored(self); self.assertEqual(1,adb.instrumentations)

    def test_real_main_setup_timeout_restores_without_launching(self):
        adb=FakeAdb(); adb.failure=lambda c: subprocess.TimeoutExpired('fake-adb',10) if c==('svc','data','disable') else None
        with self.assertRaises(subprocess.TimeoutExpired): self.main_with_fake_adb(adb)
        adb.assert_restored(self); self.assertEqual(0,adb.instrumentations)

    def test_real_main_rejects_active_default_network_before_instrumentation(self):
        adb=FakeAdb(); adb.connectivity='Active default network: 101\n'
        with self.assertRaisesRegex(RuntimeError,'Offline deadline'): self.main_with_fake_adb(adb)
        adb.assert_restored(self); self.assertEqual(0,adb.instrumentations)

    def test_real_main_rejects_reused_output_before_any_device_operation(self):
        adb=FakeAdb(); old=self.out/'result'; old.mkdir(); (old/'stale.json').write_text('{}')
        with patch.object(sys,'stderr',io.StringIO()), self.assertRaises(SystemExit): self.main_with_fake_adb(adb)
        adb.assert_restored(self); self.assertEqual([],adb.commands)


PNG=b'\x89PNG\r\n\x1a\nmock\x00\x00\x00\x00IEND\xaeB`\x82'

def archive(metadata=True, screenshot=True):
    data=io.BytesIO()
    with tarfile.open(fileobj=data,mode='w') as tar:
        files={'screenshot.png':PNG} if screenshot else {}
        if metadata: files.update({'logcat.txt':b'11-09 01:02:03.123  123 456 I Test: mock only\n','metadata.json':json.dumps({'testedRevision':'mock-revision','test':'Mock#probe','artifactErrors':[]}).encode()})
        for name,body in files.items():
            info=tarfile.TarInfo('files/ui02-failures/probe/'+name); info.size=len(body); tar.addfile(info,io.BytesIO(body))
    return data.getvalue()


class ArtifactTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup); self.folder=Path(self.temp.name); self.clock=Clock()
    def collect(self,responses):
        self.calls=0
        def execute(*a,**kw):
            self.calls+=1; response=responses[min(self.calls-1,len(responses)-1)]
            return SimpleNamespace(returncode=0,stdout=response,stderr=b'')
        return collect_artifacts(['fake-adb'],'mock-package',self.folder,'mock-revision',required=True,expected_test='Mock#probe',execute=execute,deadline=.4,clock=self.clock.now,pause=self.clock.pause)
    def test_complete_transfer_accepts_matching_probe(self):
        self.assertTrue(self.collect([archive()])); self.assertEqual(1,self.calls)
    def test_partial_or_screenshot_only_transfer_is_retried_not_accepted(self):
        self.assertTrue(self.collect([archive()[:700],archive(False),archive()])); self.assertEqual(3,self.calls)
        report=json.loads((self.folder/'artifact-collection.json').read_text()); self.assertFalse(report['attempts'][0]['accepted'])
    def test_persistent_incomplete_archive_fails(self):
        self.assertFalse(self.collect([archive(False)]))
    def test_partial_archives_cannot_be_combined_into_a_false_pass(self):
        self.assertFalse(self.collect([archive(screenshot=False),archive(metadata=False)]))
        self.assertFalse((self.folder/'artifacts').exists())
    def test_offline_transport_response_fails_closed(self):
        self.assertFalse(self.collect([b'adb: device offline\n']))
    def test_stale_identity_or_rule_collection_error_is_not_accepted(self):
        self.assertTrue(self.collect([archive()]))
        meta=next((self.folder/'artifacts').rglob('metadata.json'))
        for metadata in [{'testedRevision':'old'}, {'testedRevision':'mock-revision','test':'Wrong#probe'}, {'testedRevision':'mock-revision','test':'Mock#probe','artifactErrors':['logcat failed']}]:
            meta.write_text(json.dumps(metadata))
            self.assertFalse(validate_artifacts(self.folder/'artifacts','mock-revision','Mock#probe')[0])


if __name__=='__main__': unittest.main()
