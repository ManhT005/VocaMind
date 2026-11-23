"""Reject false passes and ensure controller changes are not settings-only."""
import sys
from pathlib import Path
import subprocess
import tempfile
import unittest
import os
import shlex
import zipfile
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parent))
from run_qa02_device import accepted, Network, ManualNetwork, OfflineBlocked, classify_device, prepare_fresh_data, qa_installed, PACKAGE
from test_ui02_runner import FakeAdb


class RunnerTest(unittest.TestCase):
    success = 'INSTRUMENTATION_STATUS_CODE: 1\nINSTRUMENTATION_STATUS_CODE: 0\nINSTRUMENTATION_CODE: -1\n'

    def test_missing_skipped_crashed_or_duplicate_tests_are_rejected(self):
        self.assertTrue(accepted(self.success, 0))
        for output, code in [('', 0), (self.success, 1),
                             (self.success.replace('CODE: 0', 'CODE: -3'), 0),
                             (self.success.replace('CODE: 0', 'CODE: -2'), 0),
                             (self.success.replace('INSTRUMENTATION_CODE: -1', ''), 0),
                             (self.success + self.success, 0),
                             (self.success + 'INSTRUMENTATION_FAILED', 0)]:
            self.assertFalse(accepted(output, code), output)

    def test_airplane_controller_precedes_setting_and_restores_null(self):
        commands = []
        def shell(*command):
            commands.append(command)
            return subprocess.CompletedProcess(command, 0, '')
        with tempfile.TemporaryDirectory() as directory:
            network = Network(shell, directory)
            network.setting('airplane_mode_on', '1')
            network.setting('airplane_mode_on', 'null')
        self.assertEqual(commands, [
            ('cmd', 'connectivity', 'airplane-mode', 'enable'),
            ('settings', 'put', 'global', 'airplane_mode_on', '1'),
            ('cmd', 'connectivity', 'airplane-mode', 'disable'),
            ('settings', 'delete', 'global', 'airplane_mode_on')])

    def test_failed_controller_never_claims_flag_only_isolation(self):
        commands = []
        def shell(*command):
            commands.append(command)
            return subprocess.CompletedProcess(command, 1, 'controller unavailable')
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(RuntimeError, 'controller unavailable'):
                Network(shell, directory).setting('airplane_mode_on', '1')
        self.assertEqual(len(commands), 1)

    def test_suite_requires_all_expected_tests_and_no_skip(self):
        output = 'INSTRUMENTATION_STATUS_CODE: 1\nINSTRUMENTATION_STATUS_CODE: 0\n' * 39 + 'INSTRUMENTATION_CODE: -1\n'
        self.assertTrue(accepted(output, 0, 39))
        self.assertFalse(accepted(output, 0, 40))
        self.assertFalse(accepted(output.replace('CODE: 0', 'CODE: -3', 1), 0, 39))

    def test_api26_legacy_controller_is_not_rejected_by_modern_help(self):
        device = FakeAdb()
        def shell(*command):
            if command[:3] == ('cmd', 'connectivity', 'airplane-mode'):
                return subprocess.CompletedProcess(command, 0, 'No shell command implementation.')
            if command == ('getprop', 'ro.build.version.sdk'):
                return subprocess.CompletedProcess(command, 0, '26\n')
            return device.shell(*command)
        with tempfile.TemporaryDirectory() as directory:
            with Network(shell, directory, deadline=0).isolated():
                self.assertEqual(device.raw, dict(airplane_mode_on='1', wifi_on='0', mobile_data='0'))
            self.assertEqual((device.raw, device.wifi, device.data), device.initial)
        commands=[]
        def modern_unsupported(*command):
            commands.append(command)
            text='30' if command == ('getprop','ro.build.version.sdk') else 'Unknown command'
            return subprocess.CompletedProcess(command,0,text)
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(OfflineBlocked,'unavailable'):
                Network(modern_unsupported,directory).setting('airplane_mode_on','1')
        self.assertFalse(any(c[:2] == ('settings','put') for c in commands))

    def test_manual_api26_never_uses_controller_or_radio_writes(self):
        device = FakeAdb(wifi='0', airplane='1', data='0')
        with tempfile.TemporaryDirectory() as directory:
            with ManualNetwork(device.shell, directory, deadline=0).isolated() as network:
                network.require_offline(Path(directory)/'after-reboot')
            self.assertFalse(any(c[0] in ('cmd', 'svc') or c[:2] == ('settings', 'put') for c in device.commands))
            import json
            report=json.loads((Path(directory)/'network-restore.json').read_text())
            self.assertTrue(report['accepted'])
            self.assertFalse(report['mutatedByRunner'])

    def test_manual_rejects_flags_only_effective_online_ambiguous_and_unreadable(self):
        for case in ('wifi', 'default', 'ambiguous', 'flags', 'denied'):
            with self.subTest(case=case), tempfile.TemporaryDirectory() as directory:
                device=FakeAdb(wifi='0', airplane='1', data='0')
                if case == 'wifi': device.wifi=True; device.raw['wifi_on']='1'
                if case == 'default': device.connectivity='Active default network: 100\n'
                if case == 'ambiguous': device.connectivity='Active default network: none\nActive default network: 100\n'
                if case == 'flags': device.raw['mobile_data']='1'; device.data=True
                if case == 'denied': device.failure=lambda c: c == ('dumpsys', 'connectivity')
                with self.assertRaises(OfflineBlocked):
                    with ManualNetwork(device.shell,directory,deadline=0).isolated():
                        self.fail('Must never launch test')
                self.assertFalse(any(c[0] in ('cmd','svc') for c in device.commands))

    def test_manual_cannot_mutate_even_if_caller_requests_it(self):
        with tempfile.TemporaryDirectory() as directory:
            for command in [('settings','put','global','airplane_mode_on','1'),('svc','wifi','disable')]:
                with self.assertRaisesRegex(RuntimeError,'refuses mutation'):
                    ManualNetwork(lambda *c: self.fail('No shell mutation allowed'),directory).checked(*command)

    def test_manual_external_change_fails_exit_check_without_auto_restoring(self):
        device=FakeAdb(wifi='0',airplane='1',data='0')
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(RuntimeError,'operator restoration required'):
                with ManualNetwork(device.shell,directory,deadline=0).isolated():
                    device.raw['mobile_data']='1';device.data=True
        self.assertFalse(any(c[0] in ('cmd','svc') for c in device.commands))

    def test_device_classification_requires_evidence_not_model_or_serial_name(self):
        cases=[({'ro.kernel.qemu':'1'},'usb:1','EMULATOR'),
               ({'ro.hardware':'ranchu'},'','EMULATOR'),
               ({'ro.product.model':'Realme','ro.hardware':'mt6835'},'usb:1','UNKNOWN'),
               ({'ro.kernel.qemu':'0','ro.hardware':'mt6835'},'usb:1','PHYSICAL'),
               ({'ro.kernel.qemu':'0','ro.hardware':'mt6835'},'local','UNKNOWN'),
               ({},'','UNKNOWN')]
        for inventory,transport,expected in cases:
            self.assertEqual(classify_device(inventory,transport)['kind'],expected)

    def test_new_install_cannot_relabel_existing_data_and_reset_has_no_fallback(self):
        writes=[]; calls=[]
        def write(*args): writes.append(args)
        def shell(*args):
            calls.append(args)
            return subprocess.CompletedProcess(args,1,'CLEAR_APP_USER_DATA denied')
        with self.assertRaisesRegex(RuntimeError,'absent'):
            prepare_fresh_data(shell,write,'new-install',True)
        self.assertEqual(calls,[])
        prepare_fresh_data(shell,write,'new-install',False)
        self.assertEqual(calls,[])
        with self.assertRaisesRegex(RuntimeError,'no run-as deletion'):
            prepare_fresh_data(shell,write,'reset',True)
        self.assertEqual(calls,[('pm','clear','com.vocamind.app.qa02validation.qa')])

    def test_fresh_provenance_requires_successful_exact_current_user_inventory(self):
        for text,code,expected in [('',0,False),('package:'+PACKAGE+'.test\n',0,False),
                                   ('package:'+PACKAGE+'\n',0,True),('',1,None),
                                   ('Error: permission denied',0,None)]:
            calls=[]
            def shell(*c):
                calls.append(c);return subprocess.CompletedProcess(c,code,text)
            if expected is None:
                with self.assertRaisesRegex(RuntimeError,'Cannot prove'): qa_installed(shell)
            else: self.assertEqual(qa_installed(shell),expected)
            self.assertEqual(calls,[('pm','list','packages','--user','current',PACKAGE)])

    def test_cli_preflight_api26_managed_manual_and_offline_block_before_app_mutation(self):
        import run_qa02_device as runner
        for mode, online in [('managed',False),('manual',False),('manual',True)]:
            with self.subTest(mode=mode, online=online), tempfile.TemporaryDirectory() as tmp:
                original=Path.cwd(); os.chdir(tmp)
                try:
                    for path in ['app/build/outputs/apk/catalogQa/app-catalogQa.apk',
                                 'app/build/outputs/apk/androidTest/catalogQa/app-catalogQa-androidTest.apk']:
                        target=Path(path);target.parent.mkdir(parents=True,exist_ok=True)
                        with zipfile.ZipFile(target,'w') as z: z.writestr('assets/database/catalog.db',b'fixture-not-real-apk')
                    device=FakeAdb(wifi='0' if mode=='manual' else '1',airplane='1' if mode=='manual' else '0',data='0' if mode=='manual' else '1')
                    if online: device.connectivity='Active default network: 100\n'
                    calls=[]
                    def run(command, **kwargs):
                        command=list(map(str,command));calls.append(command)
                        result=lambda text='',code=0: subprocess.CompletedProcess(command,code,text)
                        if command[0]=='aapt':
                            suffix='.test' if 'androidTest' in command[-1] else ''
                            return result("package: name='"+runner.PACKAGE+suffix+"'\n")
                        if command[0]=='git': return result('1f1defb' if 'rev-parse' in command else '')
                        if command[3]=='get-devpath': return result('local')
                        args=tuple(shlex.split(command[4]))
                        if args[:3]==('cmd','connectivity','airplane-mode'): return result('No shell command implementation.')
                        if args[:1]==('getprop',):
                            return result({'ro.build.version.sdk':'26','ro.hardware':'ranchu','ro.kernel.qemu':'1'}.get(args[1],''))
                        if args[0] in ('wm','head','date'): return result('fake inventory')
                        return device.shell(*args)
                    argv=['runner','--adb','adb','--serial','selected','--aapt','aapt','--output','build/qa02/preflight','--network-mode',mode,'--preflight-only']
                    # Keep the negative case deterministic rather than waiting the real deadline.
                    real_init=runner.ManualNetwork.__init__
                    def init(network,*args,**kwargs): real_init(network,*args,deadline=0,**kwargs)
                    with patch.object(sys,'argv',argv), patch.object(runner.subprocess,'run',side_effect=run), patch.object(runner.subprocess,'Popen') as record, patch.object(runner.ManualNetwork,'__init__',init):
                        if online:
                            with self.assertRaises(OfflineBlocked): runner.main()
                            self.assertTrue(Path('build/qa02/preflight/blocked.json').exists())
                        else: self.assertEqual(runner.main(),0)
                        record.assert_not_called()
                    self.assertFalse(any(c[3]=='install' for c in calls if c[0]=='adb'))
                    self.assertFalse(any(c[:2]==('pm','clear') for c in device.commands))
                    self.assertTrue(Path('build/qa02/preflight/network-restore.json').exists())
                finally: os.chdir(original)


if __name__ == '__main__':
    unittest.main()
