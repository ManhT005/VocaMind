"""Evidence must not become PASS through malformed input, missing samples or missing hardware."""
import json
from pathlib import Path
import tempfile
import sqlite3
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from zipfile import ZipFile

import data09_performance as perf
import data09_apk_size as apk


class Data09Test(unittest.TestCase):
    def fts_report(self):
        return {"datasetWords": 5000, "datasetSha256": "hash", "warmupIterations": 20,
                "sampleCount": 100, "cases": [dict(query=q, matches=n, samplesNs=[1_000_000] * 100)
                                             for q, n in perf.WORKLOADS]}

    def test_fts_50ms_boundary_fails_even_when_mean_is_fast(self):
        raw = self.fts_report()
        raw["cases"][0]["samplesNs"][-1] = 50_000_000
        result = perf.fts_analysis(raw, "hash")
        self.assertEqual("FAIL", result["observedGate"])
        self.assertTrue(result["cases"][0]["meanBelow15MsTarget"])
        self.assertEqual(50, result["cases"][0]["maxMs"])

    def test_fts_15ms_target_is_separate_from_mandatory_gate(self):
        raw = self.fts_report()
        raw["cases"][4]["samplesNs"] = [15_000_000] * 100
        result = perf.fts_analysis(raw, "hash")
        self.assertEqual("PASS", result["observedGate"])
        self.assertFalse(result["cases"][4]["meanBelow15MsTarget"])

    def test_fts_rejects_missing_samples_wrong_hits_hash_or_workload(self):
        for mutation in (lambda r: r["cases"].pop(),
                         lambda r: r["cases"][0]["samplesNs"].pop(),
                         lambda r: r["cases"][4].update(matches=100),
                         lambda r: r.update(datasetSha256="other"),
                         lambda r: r["cases"][0]["samplesNs"].__setitem__(0, True),
                         lambda r: r.update(warmupIterations=0)):
            raw = self.fts_report(); mutation(raw)
            with self.assertRaises(ValueError): perf.fts_analysis(raw, "hash")

    def test_startup_requires_system_first_frame_and_new_launch(self):
        output = "Status: ok\nLaunchState: COLD\nTotalTime: 1234\nWaitTime: 1300\n"
        display = "I/ActivityTaskManager: Displayed " + perf.COMPONENT + ": +1s230ms"
        self.assertEqual(1234, perf.startup_sample(output, display)["launchToInitialDisplayMs"])
        self.assertEqual(1230, perf.startup_sample(output, display)["displayedActivityMs"])
        for bad_output, bad_display in [(output, ""), (output, display.replace(perf.PACKAGE, "other")),
                                       (output.replace("COLD", "WARM"), display),
                                       (output + "Warning: Activity not started", display),
                                       ("Status: ok\nWaitTime: 100\n", display)]:
            with self.assertRaises(ValueError): perf.startup_sample(bad_output, bad_display)

    def test_startup_all_samples_and_strict_1500ms_boundary(self):
        samples = [{"launchToInitialDisplayMs": 1499} for _ in range(10)]
        self.assertEqual("PASS", perf.observed_gate(samples, 10))
        self.assertEqual("PENDING", perf.observed_gate(samples[:9], 10))
        samples[-1] = {"error": "timeout"}
        self.assertEqual("PENDING", perf.observed_gate(samples, 10))
        samples[-1] = {"launchToInitialDisplayMs": 1500}
        self.assertEqual("FAIL", perf.observed_gate(samples, 10))

    def test_distribution_retains_outliers_and_rejects_nonfinite(self):
        self.assertEqual(100, perf.distribution([1, 1, 1, 100])["maxMs"])
        for invalid in ([], [float("nan")], [float("inf")], [-1], [True]):
            with self.assertRaises(ValueError): perf.distribution(invalid)

    def test_qualification_is_bound_to_physical_device_metric_and_apk(self):
        inventory = {"fingerprint": "device", "emulator": False}
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "qualification.json"
            data = dict(kind="reference", fingerprint="device", reviewer="reviewer",
                        evidenceRef="inventory-record", metrics=["fts"], apkSha256="artifact")
            path.write_text(json.dumps(data))
            self.assertTrue(perf.qualification(path, inventory, "fts", "artifact")["verified"])
            self.assertFalse(perf.qualification(path, inventory, "startup", "artifact")["verified"])
            self.assertFalse(perf.qualification(path, inventory, "fts", "other")["verified"])
            self.assertFalse(perf.qualification(path, dict(inventory, emulator=True), "fts", "artifact")["verified"])
            data["fingerprint"] = "other"; path.write_text(json.dumps(data))
            self.assertFalse(perf.qualification(path, inventory, "fts", "artifact")["verified"])
        self.assertFalse(perf.qualification(None, inventory, "fts", "artifact")["verified"])

    @patch.object(apk.package_qa, "verify_apk", return_value={"profile": "debug-fixture"})
    def test_apk_reports_compressed_db_and_keeps_release_pending(self, _):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "app.apk"
            database = Path(directory) / "catalog.db"
            with sqlite3.connect(database) as db:
                db.execute("CREATE TABLE VocabularyEntry(id TEXT)")
                db.execute("CREATE TABLE LearningPack(id TEXT)")
                db.execute("INSERT INTO VocabularyEntry VALUES ('stable-id')")
            with ZipFile(path, "w") as archive:
                archive.writestr("assets/database/catalog.db", database.read_bytes())
                archive.writestr("lib/arm64-v8a/example.so", b"native")
            report = apk.inspect_apk(path, "data09", "fixture")
            self.assertEqual("PASS", report["observedGate"])
            self.assertEqual("PENDING", report["srsGate"])
            self.assertEqual(["arm64-v8a"], report["nativeAbis"])
            self.assertEqual(database.stat().st_size, report["databaseAssets"][0]["uncompressedBytes"])
            self.assertEqual(1, report["dataset"]["uniqueWords"])
            with ZipFile(path, "a") as archive: archive.writestr("benchmark/catalog-5000.db", b"test-only")
            with self.assertRaises(ValueError): apk.inspect_apk(path, "data09", "fixture")

    @patch.object(perf.subprocess, "run")
    def test_adb_remote_arguments_are_shell_quoted(self, run):
        run.return_value.returncode = 0; run.return_value.stdout = ""; run.return_value.stderr = ""
        perf.Device("adb", "device").shell("echo", "literal; not-a-command")
        self.assertEqual("echo 'literal; not-a-command'", run.call_args.args[0][-1])

    @patch.object(perf.subprocess, "run")
    def test_wrong_package_is_rejected_before_any_install(self, run):
        run.return_value.stdout = "com.vocamind.app\n"
        with self.assertRaises(ValueError): perf.apk_identity("apkanalyzer", Path("app.apk"), perf.PACKAGE)
        self.assertEqual(1, run.call_count)

    def test_summary_cannot_pass_when_evidence_is_absent(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "summary.json"
            with patch("sys.argv", ["runner", "summary", "--output", str(path), "--require-pass"]):
                self.assertEqual(1, perf.main())
            self.assertFalse(json.loads(path.read_text())["allPassed"])

    def collect_with_fake(self, directory, command="startup", failed_start=None, fts_failure=False, fresh=False, existing=False):
        raw = self.fts_report()
        raw.update(fingerprint="device", latencyAssertionsEnabled=True)
        if fts_failure: raw["cases"][0]["samplesNs"][0] = 50_000_000
        class FakeDevice:
            marker = ""
            starts = 0
            def inventory(self):
                return dict(fingerprint="device", emulator=False, offlineVerified=True)
            def verify_apk(self, package, apk): return {"sha256": "artifact"}
            def shell(self, *args, **kwargs):
                if args[:2] == ("pm", "path"): return "package:/already-installed.apk" if existing else ""
                if args[:2] == ("pm", "clear"):
                    assert not existing, "Must not clear an existing QA installation"
                    return "Success"
                if args[0] == "pidof":
                    assert kwargs.get("allow_nonzero"), "pidof exits 1 when correctly stopped"
                    return ""
                if args[0] == "log": self.marker = args[-1]
                if args[:2] == ("am", "start"):
                    self.starts += 1
                    if self.starts == failed_start: raise RuntimeError("device timeout")
                    return "Status: ok\nTotalTime: 1000\n"
                if args[0] == "logcat": return self.marker + "\nDisplayed " + perf.COMPONENT + ": +1s0ms"
                if args[:2] == ("am", "instrument"):
                    ending = "FAILURES!!!" if fts_failure else "OK (1 test)"
                    return "INSTRUMENTATION_STATUS: data09Report=" + json.dumps(raw) + "\n" + ending + "\nINSTRUMENTATION_CODE: -1\n"
                return ""
        args = SimpleNamespace(command=command, output_dir=Path(directory)/"run", adb="adb", serial="serial",
                               apk=Path("app.apk"), test_apk=Path("test.apk"), apkanalyzer="apkanalyzer",
                               asset_profile="fixture", qualification=None, runs=10, fresh_install=fresh)
        device = FakeDevice()
        with patch.object(perf, "Device", return_value=device), patch.object(perf, "source_manifest", return_value={}), \
                patch.object(perf, "apk_identity", return_value={"sha256": "artifact"}), \
                patch.object(perf, "inspect_apk", return_value={}), patch.object(perf, "sha256", return_value="hash"), \
                patch.object(perf, "qualification", return_value={"verified": True}), patch("builtins.print"), \
                patch.object(perf.subprocess, "run", return_value=SimpleNamespace(returncode=0, stdout="Success", stderr="")):
            code = perf.collect(args)
        return code, json.loads((args.output_dir / "report.json").read_text())

    def test_collector_retains_failed_start_without_replacing_it(self):
        with tempfile.TemporaryDirectory() as directory:
            code, report = self.collect_with_fake(directory, failed_start=3)
            self.assertEqual(1, code)
            self.assertEqual(10, len(report["samples"]))
            self.assertIn("error", report["samples"][2])
            self.assertEqual("PENDING", report["observedGate"])
            self.assertEqual("PENDING", report["srsGate"])

    def test_cold_reopens_do_not_certify_missing_first_data_launch(self):
        with tempfile.TemporaryDirectory() as directory:
            code, report = self.collect_with_fake(directory)
            self.assertEqual(0, code)
            self.assertEqual("PASS", report["observedGate"])
            self.assertEqual("PENDING", report["srsGate"])

    def test_fts_failure_exports_raw_data_and_stays_fail_on_qualified_device(self):
        with tempfile.TemporaryDirectory() as directory:
            code, report = self.collect_with_fake(directory, command="fts", fts_failure=True)
            self.assertEqual(1, code)
            self.assertTrue((Path(directory)/"run/fts-raw.json").exists())
            self.assertEqual("FAIL", report["srsGate"])

    def test_fts_success_can_pass_only_when_qualified_and_complete(self):
        with tempfile.TemporaryDirectory() as directory:
            code, report = self.collect_with_fake(directory, command="fts")
            self.assertEqual(0, code)
            self.assertEqual("PASS", report["srsGate"])

    def test_first_data_launch_is_separate_from_ten_cold_reopens(self):
        with tempfile.TemporaryDirectory() as directory:
            code, report = self.collect_with_fake(directory, fresh=True)
            self.assertEqual(0, code)
            self.assertEqual(11, len(report["samples"]))
            self.assertTrue(report["samples"][0]["firstDataLaunch"])
            self.assertEqual(10, report["coldReopenDistribution"]["count"])
            self.assertEqual("PASS", report["srsGate"])

    def test_fresh_mode_never_clears_an_existing_installation(self):
        with tempfile.TemporaryDirectory() as directory:
            code, report = self.collect_with_fake(directory, fresh=True, existing=True)
            self.assertEqual(1, code)
            self.assertIn("requires the isolated package to be absent", report["error"])
            self.assertNotIn("samples", report)

    def test_summary_rejects_reports_for_different_apks(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first, second = root/"startup.json", root/"size.json"
            first.write_text(json.dumps(dict(metric="startup", apk={"sha256":"one"}, srsGate="PENDING")))
            second.write_text(json.dumps(dict(metric="apk-size", apkSha256="two", srsGate="PENDING")))
            with patch("sys.argv", ["runner", "summary", "--startup", str(first), "--apk-size", str(second),
                                    "--output", str(root/"summary.json")]), self.assertRaises(SystemExit) as error:
                perf.main()
            self.assertEqual(2, error.exception.code)
            self.assertFalse((root/"summary.json").exists())


if __name__ == "__main__":
    unittest.main()
