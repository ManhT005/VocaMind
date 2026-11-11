"""Reject false passes and ensure controller changes are not settings-only."""
import sys
from pathlib import Path
import subprocess
import tempfile
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parent))
from run_qa02_device import accepted, Network


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


if __name__ == '__main__':
    unittest.main()
