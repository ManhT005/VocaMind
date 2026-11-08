"""No-device regression tests for the runner's configuration transaction."""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("progress02_driver", Path(__file__).with_name("progress02_ldplayer.py"))
driver = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(driver)


class FakeDevice:
    serial = "emulator-5554"

    def __init__(self):
        self.values = {"global/airplane_mode_on": "1", "global/wifi_on": "0",
                       "system/font_scale": "1.25", "secure/ui_night_mode": "0"}
        self.size, self.density, self.night = "800x1400", "400", "auto"
        self.commands = []
        self.missing = False
        self.fail_restore_once = False

    def shell(self, command, **kwargs):
        self.commands.append(command)
        parts = command.split()
        if command.startswith("pm path "):
            if self.missing:
                raise RuntimeError("QA package missing")
            return "package:/data/app/qa-test/base.apk\n"
        if command.startswith("sha256sum "):
            return "0" * 64 + "  base.apk\n"
        if parts[:2] == ["settings", "get"]:
            return self.values[parts[2] + "/" + parts[3]] + "\n"
        if parts[:2] == ["settings", "put"]:
            self.values[parts[2] + "/" + parts[3]] = parts[4]
        elif parts[:2] == ["settings", "delete"]:
            self.values[parts[2] + "/" + parts[3]] = "null"
        elif parts[:2] in (["wm", "size"], ["wm", "density"]):
            key = parts[1]
            if len(parts) == 2:
                value = getattr(self, key)
                return "Physical " + key + ": 1080\n" + ("Override " + key + ": " + value + "\n" if value != "reset" else "")
            if command == "wm size 800x1400" and self.fail_restore_once:
                self.fail_restore_once = False
                raise RuntimeError("temporary ADB failure")
            setattr(self, key, parts[2])
        elif parts[:3] == ["cmd", "uimode", "night"]:
            if len(parts) == 3:
                return "Night mode: " + self.night + "\n"
            self.night = parts[3]
            self.values["secure/ui_night_mode"] = "2"
        elif parts[:2] == ["svc", "wifi"]:
            self.values["global/wifi_on"] = "1" if parts[2] == "enable" else "0"
        return ""


class RestorationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name) / "evidence"
        self.state = Path(self.temp.name) / "state.json"
        self.device = FakeDevice()
        self.original = driver.snapshot(self.device)
        self.device.commands.clear()
        self.addCleanup(patch.stopall)
        patch.object(driver.subprocess, "check_output", return_value="test-head\n").start()
        patch.object(driver.time, "sleep").start()
        patch("builtins.print").start()

    def run_stage(self, side_effect=None, result="OK (1 test)\nINSTRUMENTATION_CODE: -1"):
        with patch.object(driver, "run_instrumentation", side_effect=side_effect, return_value=result):
            driver.run_stage(self.device, "appearance", self.output, self.state)

    def assert_restored(self):
        self.assertEqual(self.original, driver.snapshot(self.device))
        self.assertFalse(self.state.exists())

    def test_missing_package_does_not_change_configuration(self):
        self.device.missing = True
        with self.assertRaisesRegex(RuntimeError, "QA package missing"):
            self.run_stage()
        self.assertEqual(self.original, driver.snapshot(self.device))
        self.assertFalse(self.state.exists())
        self.assertNotIn("wm size 720x1280", self.device.commands)

    def test_failure_restores_custom_values_and_keeps_original_offline(self):
        with self.assertRaisesRegex(RuntimeError, "test failed"):
            self.run_stage(side_effect=RuntimeError("test failed"))
        self.assert_restored()
        self.assertNotIn("settings put global airplane_mode_on 0", self.device.commands)
        self.assertNotIn("svc wifi enable", self.device.commands)

    def test_success_also_restores_configuration(self):
        self.run_stage()
        self.assert_restored()

    def test_keyboard_interrupt_restores_and_stops_background_test(self):
        with self.assertRaises(KeyboardInterrupt):
            self.run_stage(side_effect=KeyboardInterrupt())
        self.assert_restored()
        self.assertIn("am force-stop " + driver.PACKAGE, self.device.commands)

    def test_null_settings_and_physical_defaults_are_preserved(self):
        self.device.values["system/font_scale"] = "null"
        self.device.values["secure/ui_night_mode"] = "null"
        self.device.size = self.device.density = "reset"
        self.original = driver.snapshot(self.device)
        self.run_stage()
        self.assert_restored()
        self.assertIn("settings delete system font_scale", self.device.commands)

    def test_incomplete_restore_attempts_remaining_commands_and_retains_recovery(self):
        self.device.fail_restore_once = True
        with self.assertRaisesRegex(RuntimeError, "Restoration incomplete"):
            self.run_stage(side_effect=RuntimeError("test failed"))
        self.assertTrue(self.state.exists())
        self.assertEqual("1.25", self.device.values["system/font_scale"])
        self.assertEqual("400", self.device.density)
        driver.restore(self.device, json.loads(self.state.read_text()))
        self.assertEqual(self.original, driver.snapshot(self.device))

    def test_pending_recovery_is_never_overwritten(self):
        self.state.write_text(json.dumps(self.original))
        with self.assertRaisesRegex(RuntimeError, "Pending configuration"):
            self.run_stage()
        self.assertEqual(self.original, json.loads(self.state.read_text()))
        self.assertEqual(self.original, driver.snapshot(self.device))


if __name__ == "__main__":
    unittest.main()
