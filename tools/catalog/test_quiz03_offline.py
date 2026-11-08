import unittest
from check_quiz03_offline import require_offline


class QuizOfflinePreconditionTest(unittest.TestCase):
    def test_disconnected_device_passes(self):
        require_offline('1\r\n', '0\r\n', 'Networks:\n  Active default network: none\n')

    def test_airplane_flag_does_not_hide_active_network(self):
        with self.assertRaises(ValueError):
            require_offline('1', '0', 'Active default network: 101\n')
        with self.assertRaises(ValueError):
            require_offline('1', '1', 'Active default network: 101\n')

    def test_missing_or_ambiguous_network_evidence_fails(self):
        for connectivity in ('', 'Connectivity service unavailable',
                             'Active default network: none\nActive default network: 101\n'):
            with self.subTest(connectivity=connectivity), self.assertRaises(ValueError):
                require_offline('1', '0', connectivity)

    def test_radio_settings_must_be_explicitly_disabled(self):
        for airplane, wifi in [('0', '0'), ('1', '1'), ('1', '2'), ('', '0')]:
            with self.subTest(airplane=airplane, wifi=wifi), self.assertRaises(ValueError):
                require_offline(airplane, wifi, 'Active default network: none\n')
