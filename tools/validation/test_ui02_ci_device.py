"""Host-only CI bootstrap permission regression; no real ADB/device is accessed."""
import json
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from ui02_ci_device import bootstrap_ci_emulator


class CiBootstrapTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup); self.output=Path(self.temp.name)
        self.properties={'ro.kernel.qemu':'1','ro.debuggable':'1','ro.build.version.sdk':'26'}
        self.uid='2000'; self.calls=[]; self.root_code=0; self.root_effective=True; self.wait_timeout=False
    def execute(self,command,**kwargs):
        args=command[3:]; self.calls.append((args,kwargs))
        value=''
        if args[:2]==['shell','getprop']: value=self.properties[args[2]]
        elif args==['shell','id','-u']: value=self.uid
        elif args==['root']:
            if self.root_code: return SimpleNamespace(returncode=self.root_code,stdout='root refused')
            if self.root_effective: self.uid='0'
            value='restarting adbd as root'
        elif args==['wait-for-device']:
            if self.wait_timeout: raise subprocess.TimeoutExpired('fake ADB wait',30)
        else: raise AssertionError(args)
        return SimpleNamespace(returncode=0,stdout=value)
    def bootstrap(self,api='26'): bootstrap_ci_emulator(['fake-adb','-s','mock-only'],api,self.output,self.execute)
    def test_api26_requires_verified_uid_zero_and_bounded_transport_wait(self):
        self.bootstrap()
        self.assertEqual('0',self.uid)
        self.assertIn((['wait-for-device'],{'timeout':30}),self.calls)
        self.assertTrue(json.loads((self.output/'ci-device-bootstrap.json').read_text())['accepted'])
    def test_api35_does_not_request_root(self):
        self.properties['ro.build.version.sdk']='35'; self.bootstrap('35')
        self.assertEqual('2000',self.uid); self.assertFalse(any(c==['root'] for c,_ in self.calls))
    def test_physical_device_refused_before_root(self):
        self.properties['ro.kernel.qemu']='0'
        with self.assertRaisesRegex(RuntimeError,'matching disposable'): self.bootstrap()
        self.assertFalse(any(c==['root'] for c,_ in self.calls))
    def test_non_debuggable_image_refused_before_root(self):
        self.properties['ro.debuggable']='0'
        with self.assertRaisesRegex(RuntimeError,'matching disposable'): self.bootstrap()
        self.assertFalse(any(c==['root'] for c,_ in self.calls))
    def test_wrong_sdk_refused_before_root(self):
        self.properties['ro.build.version.sdk']='35'
        with self.assertRaisesRegex(RuntimeError,'matching disposable'): self.bootstrap()
        self.assertFalse(any(c==['root'] for c,_ in self.calls))
    def test_root_command_failure_not_ignored(self):
        self.root_code=1
        with self.assertRaisesRegex(RuntimeError,'bootstrap command failed'): self.bootstrap()
        self.assertFalse(json.loads((self.output/'ci-device-bootstrap.json').read_text())['accepted'])
    def test_root_success_message_without_uid_zero_is_rejected(self):
        self.root_effective=False
        with self.assertRaisesRegex(RuntimeError,'UID verification failed'): self.bootstrap()
    def test_wait_timeout_is_failure_before_any_radio_change(self):
        self.wait_timeout=True
        with self.assertRaises(subprocess.TimeoutExpired): self.bootstrap()
        self.assertFalse(any('svc' in c or 'settings' in c for c,_ in self.calls))


if __name__=='__main__': unittest.main()
