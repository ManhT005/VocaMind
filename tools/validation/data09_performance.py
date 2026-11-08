"""DATA-09 evidence collector. Runtime measurements require an explicitly selected ADB device."""
import argparse
import json
import math
import re
import shlex
import statistics
import subprocess
import time
import uuid
from pathlib import Path

from data09_apk_size import sha256, inspect_apk

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com.vocamind.app.data09validation"
COMPONENT = PACKAGE + "/com.vocamind.app.MainActivity"
FTS_CLASS = "com.vocamind.app.data.catalog.FtsSearchBenchmarkInstrumentedTest"
WORKLOADS = [("benchword04999", 1), ("nghĩa kiểm thử 04999", 1),
             ("benchword000", 100), ("notpresentbenchmark", 0), ("benchword", 5000)]


def write_json(path, value):
    Path(path).write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def distribution(samples):
    if not samples or any(isinstance(x, bool) or not isinstance(x, (int, float))
                          or not math.isfinite(x) or x < 0 for x in samples):
        raise ValueError("Expected nonempty, finite, nonnegative raw samples")
    ordered = sorted(samples)
    return {"count": len(samples), "minMs": ordered[0], "meanMs": statistics.mean(samples),
            "p50Ms": ordered[math.ceil(len(samples) * .5) - 1],
            "p95Ms": ordered[math.ceil(len(samples) * .95) - 1], "maxMs": ordered[-1]}


def startup_sample(output, display_log):
    if not re.search(r"(?m)^Status:\s*ok\s*$", output) or "Warning:" in output or "Error:" in output:
        raise ValueError("Launch did not start a new activity successfully")
    launch = re.search(r"(?m)^LaunchState:\s*(\S+)", output)
    if launch and launch[1] != "COLD":
        raise ValueError("Expected a cold process start")
    total = re.search(r"(?m)^TotalTime:\s*(\d+)\s*$", output)
    if not total:
        raise ValueError("Missing TotalTime; host command duration is not startup latency")
    component = re.escape(PACKAGE) + r"/(?:\.MainActivity|com\.vocamind\.app\.MainActivity)"
    displayed = re.search(r"Displayed " + component + r"(?:\s+for user \d+)?:\s*\+([^\s]+)", display_log)
    if not displayed:
        raise ValueError("Missing system Displayed frame evidence for MainActivity")
    parts = re.fullmatch(r"(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?(?:(\d+)ms)?", displayed[1])
    if not parts or not any(x is not None for x in parts.groups()):
        raise ValueError("Malformed system Displayed duration")
    rendered = sum(int(x or 0) * scale for x, scale in zip(parts.groups(), (3600000, 60000, 1000, 1)))
    return {"launchToInitialDisplayMs": int(total[1]), "displayedActivityMs": rendered}


def qualification(path, inventory, metric, apk_hash):
    if path is None:
        return {"verified": False, "reason": "No reviewer-confirmed device/configuration evidence"}
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    valid = qualification_data(data, inventory, metric, apk_hash)
    return {"verified": bool(valid), "reviewerDeclaration": data,
            "reason": "Reviewer declaration matched inventory" if valid else "Qualification does not match device/metric"}


def qualification_data(data, inventory, metric, apk_hash):
    return (data.get("kind") in ("reference", "approved-substitute")
             and data.get("fingerprint") == inventory.get("fingerprint")
             and bool(inventory.get("fingerprint")) and inventory.get("emulator") is False
             and data.get("apkSha256") == apk_hash
             and isinstance(data.get("reviewer"), str) and bool(data["reviewer"].strip())
             and isinstance(data.get("evidenceRef"), str) and bool(data["evidenceRef"].strip())
             and isinstance(data.get("metrics"), list) and metric in data["metrics"])


def fts_analysis(report, expected_hash):
    if report.get("datasetWords") != 5000 or report.get("datasetSha256") != expected_hash:
        raise ValueError("FTS dataset/hash mismatch")
    if report.get("warmupIterations") != 20 or report.get("sampleCount") != 100:
        raise ValueError("FTS protocol changed")
    cases = report.get("cases", [])
    if len(cases) != len(WORKLOADS):
        raise ValueError("All five FTS workloads are required")
    results = []
    for case, (query, matches) in zip(cases, WORKLOADS):
        raw = case.get("samplesNs", [])
        if case.get("query") != query or case.get("matches") != matches or len(raw) != 100:
            raise ValueError("FTS workload/hit count/sample count mismatch")
        if any(type(x) is not int or x < 0 for x in raw):
            raise ValueError("Invalid raw nanoseconds")
        stats = distribution([x / 1_000_000 for x in raw])
        results.append({"query": query, "matches": matches, **stats,
                        "observedGate": "PASS" if stats["maxMs"] < 50 else "FAIL",
                        "meanBelow15MsTarget": stats["meanMs"] < 15})
    return {"cases": results, "observedGate": "PASS" if all(x["observedGate"] == "PASS" for x in results) else "FAIL"}


class DeviceCommandError(RuntimeError):
    def __init__(self, output):
        super().__init__(output)
        self.output = output


class Device:
    def __init__(self, adb, serial):
        self.adb, self.serial = adb, serial

    def shell(self, *args, timeout=60, allow_nonzero=False):
        # ADB shell invokes a remote shell: quote every argument, not just the host argv.
        result = subprocess.run([self.adb, "-s", self.serial, "shell", shlex.join(args)],
                                capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
        if result.returncode and not allow_nonzero:
            raise DeviceCommandError(result.stdout + result.stderr)
        return result.stdout + result.stderr

    def inventory(self):
        props = {key: self.shell("getprop", key).strip() for key in
                 ("ro.product.model", "ro.product.manufacturer", "ro.hardware", "ro.soc.model",
                  "ro.build.version.sdk", "ro.build.version.release", "ro.build.fingerprint", "ro.kernel.qemu")}
        mem = self.shell("cat", "/proc/meminfo")
        airplane = self.shell("settings", "get", "global", "airplane_mode_on").strip()
        wifi = self.shell("settings", "get", "global", "wifi_on").strip()
        connectivity = self.shell("dumpsys", "connectivity")
        active = re.findall(r"(?mi)^\s*Active default network:\s*(\S+)\s*$", connectivity)
        return {"serial": self.serial, "properties": props, "fingerprint": props["ro.build.fingerprint"],
                "emulator": props["ro.kernel.qemu"] == "1", "meminfo": mem,
                "battery": self.shell("dumpsys", "battery"),
                "thermal": self.shell("dumpsys", "thermalservice", allow_nonzero=True),
                "airplaneMode": airplane, "wifi": wifi, "connectivity": connectivity,
                "offlineVerified": airplane == "1" and wifi == "0" and active == ["none"]}

    def verify_apk(self, package, apk):
        output = self.shell("pm", "path", package)
        paths = re.findall(r"(?m)^package:(/[^\r\n]+\.apk)\s*$", output)
        if len(paths) != 1:
            raise ValueError("Expected one installed isolated APK, no splits: " + package)
        installed = self.shell("sha256sum", paths[0]).split()[0]
        if installed != sha256(apk):
            raise ValueError("Installed APK differs from supplied artifact: " + package)
        return {"path": paths[0], "sha256": installed}


def apk_identity(tool, apk, expected_package):
    def query(*args):
        return subprocess.run([tool, "manifest", *args, str(apk)], check=True,
                              capture_output=True, text=True, timeout=30).stdout.strip()
    package = query("application-id")
    if package != expected_package:
        raise ValueError("Wrong APK package; refusing installation/measurement: " + package)
    debuggable = query("debuggable")
    if debuggable not in ("true", "false"):
        raise ValueError("Cannot determine APK debuggable flag")
    if expected_package == PACKAGE and debuggable != "false":
        raise ValueError("Startup/FTS app must use the release-based data09 profile")
    return {"package": package, "debuggable": debuggable == "true", "sha256": sha256(apk)}


def source_manifest():
    revision = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True, text=True, check=True).stdout.strip()
    dirty = subprocess.run(["git", "status", "--porcelain"], cwd=ROOT, capture_output=True, text=True, check=True).stdout
    paths = [Path(__file__), ROOT / "tools/validation/data09_apk_size.py", ROOT / "app/build.gradle", ROOT / "app/src/main/java/com/vocamind/app/MainActivity.java",
             ROOT / "app/src/main/java/com/vocamind/app/feature/home/HomeFragment.java",
             ROOT / "app/src/main/res/navigation/nav_graph.xml",
             ROOT / "app/src/catalogTest/java/com/vocamind/app/data/catalog/FtsSearchBenchmarkContract.java",
             ROOT / "app/src/androidTest/java/com/vocamind/app/data/catalog/FtsSearchBenchmarkInstrumentedTest.java"]
    return {"commit": revision, "dirty": bool(dirty),
            "sourceSha256": {str(p.relative_to(ROOT)): sha256(p) for p in paths}}


def observed_gate(samples, expected):
    valid = [x for x in samples if isinstance(x, dict) and "error" not in x
             and type(x.get("launchToInitialDisplayMs")) is int and x["launchToInitialDisplayMs"] >= 0]
    if any(x["launchToInitialDisplayMs"] >= 1500 for x in valid):
        return "FAIL"
    return "PASS" if len(samples) == expected and len(valid) == expected else "PENDING"


def preserve_command_output(directory, name, error):
    """TimeoutExpired may contain bytes even when subprocess was called with text=True."""
    parts = []
    for field in ("output", "stderr"):
        value = getattr(error, field, None)
        if value:
            parts.append(value.decode("utf-8", errors="replace") if isinstance(value, bytes) else str(value))
    if parts:
        (Path(directory) / name).write_text("\n".join(parts), encoding="utf-8")


def validate_summary_pass(report, metric, path):
    """Never promote a claimed PASS without complete, consistent raw evidence."""
    if report.get("srsGate") != "PASS":
        return
    if metric == "apk-size":
        raise ValueError("Diagnostic QA size report cannot certify the final release APK")
    before, after = report.get("inventoryBefore", {}), report.get("inventoryAfter", {})
    apk_hash = report.get("apk", {}).get("sha256", "")
    if (report.get("error") or not report.get("measurementValidated")
            or not re.fullmatch(r"[0-9a-f]{64}", apk_hash)
            or not before.get("offlineVerified") or not after.get("offlineVerified")
            or not before.get("fingerprint") or before.get("fingerprint") != after.get("fingerprint")
            or before.get("emulator") is not False
            or report.get("installedApk", {}).get("sha256") != apk_hash):
        raise ValueError("PASS lacks complete device/artifact evidence")
    declared = report.get("qualification", {}).get("reviewerDeclaration", {})
    if not qualification_data(declared, before, metric, apk_hash):
        raise ValueError("PASS lacks matching reviewer qualification")
    if metric == "startup":
        samples = report.get("samples", [])
        if (report.get("dataState") != "fresh-install-then-cold-reopens" or len(samples) < 11
                or not samples[0].get("firstDataLaunch")
                or any(x.get("firstDataLaunch") for x in samples[1:])
                or observed_gate(samples, len(samples)) != "PASS"):
            raise ValueError("Startup PASS lacks first-data and ten valid cold reopens")
        for index, sample in enumerate(samples, 1):
            if sample.get("index") != index or type(sample.get("firstDataLaunch")) is not bool:
                raise ValueError("Invalid startup sample identity")
            launch = Path(path).parent / f"startup-{index:02}-launch.txt"
            display = Path(path).parent / f"startup-{index:02}-display.txt"
            if (not launch.is_file() or not display.is_file()
                    or sha256(launch) != sample.get("launchLogSha256")
                    or sha256(display) != sample.get("displayLogSha256")):
                raise ValueError("Startup PASS lacks matching system launch/display logs")
            parsed = startup_sample(launch.read_text(encoding="utf-8"), display.read_text(encoding="utf-8"))
            if any(sample.get(key) != value for key, value in parsed.items()):
                raise ValueError("Startup PASS contradicts system timing logs")
    else:
        raw_path = Path(path).parent / "fts-raw.json"
        if not raw_path.is_file() or report.get("rawReportSha256") != sha256(raw_path):
            raise ValueError("FTS PASS lacks matching raw samples")
        raw = json.loads(raw_path.read_text(encoding="utf-8"))
        expected = sha256(ROOT / "app/src/catalogTest/assets/benchmark/catalog-5000.db")
        if (fts_analysis(raw, expected)["observedGate"] != "PASS"
                or raw.get("fingerprint") != before["fingerprint"]
                or raw.get("latencyAssertionsEnabled") is not True):
            raise ValueError("FTS PASS contradicts raw measurements")


def collect(args):
    out = args.output_dir
    out.mkdir(parents=True, exist_ok=False)  # Never overwrite a previous run.
    report = {"schemaVersion": 1, "metric": args.command, "source": source_manifest(),
              "observedGate": "PENDING", "srsGate": "PENDING", "releaseAuthorized": False}
    device = Device(args.adb, args.serial)
    code = 0
    try:
        report["apk"] = apk_identity(args.apkanalyzer, args.apk, PACKAGE)
        report["apkSize"] = inspect_apk(args.apk, "data09", args.asset_profile)
        if args.command == "fts":
            report["testApk"] = apk_identity(args.apkanalyzer, args.test_apk, PACKAGE + ".test")
        inventory = device.inventory()
        report["inventoryBefore"] = inventory
        if not inventory["offlineVerified"]:
            raise ValueError("Require airplane mode, Wi-Fi disabled and no active default network; device settings are not changed automatically")
        if args.command == "startup" and args.fresh_install:
            if device.shell("pm", "path", PACKAGE, allow_nonzero=True).strip():
                raise ValueError("Fresh-install mode requires the isolated package to be absent; no data is erased automatically")
            installed = subprocess.run([args.adb, "-s", args.serial, "install", "-t", str(args.apk)],
                                       capture_output=True, text=True, timeout=180)
            (out / "install.txt").write_text(installed.stdout + installed.stderr, encoding="utf-8")
            if installed.returncode or "Success" not in installed.stdout:
                raise ValueError("Isolated APK installation failed")
            cleared = device.shell("pm", "clear", PACKAGE)
            (out / "fresh-data.txt").write_text(cleared, encoding="utf-8")
            if cleared.strip() != "Success":
                raise ValueError("Cannot establish fresh data in the isolated QA package")
        report["installedApk"] = device.verify_apk(PACKAGE, args.apk)
        qualified = qualification(args.qualification, inventory, args.command, report["apk"]["sha256"])
        report["qualification"] = qualified
        if args.command == "startup":
            report["timingScope"] = "System launch-to-initial-display TotalTime, corroborated by Displayed MainActivity; Home is static, not database readiness"
            report["dataState"] = "fresh-install-then-cold-reopens" if args.fresh_install else "existing-install-cold-reopens"
            report["samples"] = []
            expected = args.runs + int(args.fresh_install)
            for index in range(expected):
                sample = {"index": index + 1, "firstDataLaunch": args.fresh_install and index == 0}
                report["samples"].append(sample)
                try:
                    device.shell("am", "force-stop", PACKAGE)
                    if device.shell("pidof", PACKAGE, allow_nonzero=True).strip():
                        raise ValueError("Process survived force-stop")
                    marker = "DATA09-" + uuid.uuid4().hex
                    device.shell("log", "-p", "i", "-t", "DATA09", marker)
                    launched = device.shell("am", "start", "-W", "-n", COMPONENT)
                    (out / f"startup-{index + 1:02}-launch.txt").write_text(launched, encoding="utf-8")
                    deadline = time.monotonic() + 15
                    while True:
                        logs = device.shell("logcat", "-d", "-v", "brief", "-s", "DATA09:I", "ActivityTaskManager:I", "ActivityManager:I")
                        logs = logs.rsplit(marker, 1)[-1] if marker in logs else ""
                        (out / f"startup-{index + 1:02}-display.txt").write_text(logs, encoding="utf-8")
                        try:
                            sample.update(startup_sample(launched, logs))
                            sample["launchLogSha256"] = sha256(out / f"startup-{index + 1:02}-launch.txt")
                            sample["displayLogSha256"] = sha256(out / f"startup-{index + 1:02}-display.txt")
                            break
                        except ValueError:
                            if time.monotonic() >= deadline:
                                raise
                            time.sleep(.2)
                except Exception as error:
                    sample["error"] = str(error)
                    preserve_command_output(out, f"startup-{index + 1:02}-partial.txt", error)
                write_json(out / "report.json", report)
            values = [x["launchToInitialDisplayMs"] for x in report["samples"] if "error" not in x]
            report["distribution"] = distribution(values) if values else None
            report["observedGate"] = observed_gate(report["samples"], expected)
            reopened = [x["launchToInitialDisplayMs"] for x in report["samples"]
                        if "error" not in x and not x["firstDataLaunch"]]
            report["coldReopenDistribution"] = distribution(reopened) if reopened else None
            report["measurementValidated"] = report["observedGate"] != "PENDING"
        else:
            report["installedTestApk"] = device.verify_apk(PACKAGE + ".test", args.test_apk)
            output = device.shell("am", "instrument", "-w", "-r", "-e", "class", FTS_CLASS,
                                  "-e", "data09Report", "true", "-e", "data05EnforceLatency", "true",
                                  PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=240)
            (out / "instrumentation.txt").write_text(output, encoding="utf-8")
            matches = re.findall(r"(?m)^INSTRUMENTATION_STATUS: data09Report=(.+)$", output)
            if len(matches) != 1:
                raise ValueError("Missing or ambiguous raw FTS report")
            raw = json.loads(matches[0])
            write_json(out / "fts-raw.json", raw)
            report["rawReportSha256"] = sha256(out / "fts-raw.json")
            fixture = ROOT / "app/src/catalogTest/assets/benchmark/catalog-5000.db"
            report.update(fts_analysis(raw, sha256(fixture)))
            if raw.get("fingerprint") != inventory["fingerprint"] or not raw.get("latencyAssertionsEnabled"):
                raise ValueError("FTS device/protocol mismatch")
            report["measurementValidated"] = True
            if not re.search(r"OK \(1 test\)", output) or "INSTRUMENTATION_CODE: -1" not in output:
                raise ValueError("Instrumentation did not complete successfully; raw samples retained")
        report["inventoryAfter"] = device.inventory()
        if not report["inventoryAfter"]["offlineVerified"]:
            raise ValueError("Offline conditions changed during run")
        complete = args.command != "startup" or args.fresh_install
        report["srsGate"] = report["observedGate"] if qualified["verified"] and complete else "PENDING"
        report["reason"] = ("Complete qualified measurement" if qualified["verified"] and complete
                            else "Requires qualified device/configuration and first-data startup evidence")
        if report["observedGate"] != "PASS":
            report["srsGate"] = ("FAIL" if report["observedGate"] == "FAIL"
                                 and qualified["verified"] else "PENDING")
            code = 1
    except Exception as error:
        report["error"] = str(error)
        preserve_command_output(out, "command-partial.txt", error)
        report["srsGate"] = ("FAIL" if report.get("observedGate") == "FAIL"
                             and report.get("measurementValidated")
                             and report.get("qualification", {}).get("verified") else "PENDING")
        code = 1
    finally:
        write_json(out / "report.json", report)
    print(json.dumps(report, ensure_ascii=False))
    return code


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    for name in ("startup", "fts"):
        sub = commands.add_parser(name)
        sub.add_argument("--adb", default="adb")
        sub.add_argument("--serial", required=True)
        sub.add_argument("--apkanalyzer", default="apkanalyzer")
        sub.add_argument("--asset-profile", choices=("fixture", "internal-qa"), default="fixture")
        sub.add_argument("--apk", required=True, type=Path)
        sub.add_argument("--qualification", type=Path)
        sub.add_argument("--output-dir", required=True, type=Path)
        if name == "startup":
            sub.add_argument("--runs", type=int, default=10)
            sub.add_argument("--fresh-install", action="store_true")
        else:
            sub.add_argument("--test-apk", required=True, type=Path)
    summary = commands.add_parser("summary")
    summary.add_argument("--startup", type=Path)
    summary.add_argument("--fts", type=Path)
    summary.add_argument("--apk-size", type=Path)
    summary.add_argument("--output", type=Path, required=True)
    summary.add_argument("--require-pass", action="store_true")
    args = parser.parse_args()
    if args.command == "startup" and args.runs < 10:
        parser.error("At least 10 starts are required; failed samples are retained, not retried away")
    if args.command != "summary":
        return collect(args)
    if args.output.exists():
        parser.error("Summary output already exists")
    gates = {}
    artifacts = set()
    for name, path in (("startup", args.startup), ("fts", args.fts), ("apk-size", args.apk_size)):
        report = json.loads(path.read_text(encoding="utf-8")) if path else None
        if report and report.get("metric") != name:
            parser.error("Report metric mismatch: " + name)
        if report:
            artifact = report.get("apkSha256") or report.get("apk", {}).get("sha256")
            if artifact: artifacts.add(artifact)
        gate = report.get("srsGate", "PENDING") if report else "PENDING"
        if gate not in ("PASS", "FAIL", "PENDING"):
            parser.error("Invalid gate: " + name)
        if report:
            try:
                validate_summary_pass(report, name, path)
            except (ValueError, KeyError, TypeError, AttributeError) as error:
                parser.error(str(error))
        gates[name] = {"gate": gate, "reportSha256": sha256(path) if path else None,
                       "reason": report.get("reason", report.get("error")) if report else "No device/artifact measurement"}
    if len(artifacts) > 1:
        parser.error("Reports refer to different APKs; do not combine different artifacts into one acceptance summary")
    result = {"schemaVersion": 1, "gates": gates, "releaseAuthorized": False,
              "allPassed": all(x["gate"] == "PASS" for x in gates.values())}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_json(args.output, result)
    print(json.dumps(result, indent=2))
    return int(args.require_pass and not result["allPassed"])


if __name__ == "__main__":
    raise SystemExit(main())
