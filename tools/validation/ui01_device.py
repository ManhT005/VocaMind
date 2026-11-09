"""Focused UI-01 smoke on a fresh, isolated QA install; restore device network settings."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

PACKAGE = "com.vocamind.app.quiz03validation"
TEST = "com.vocamind.app.feature.ui.Ui01SmokeInstrumentedTest"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--app-apk", type=Path, required=True)
    parser.add_argument("--test-apk", type=Path, required=True)
    parser.add_argument("--evidence", type=Path, required=True)
    args = parser.parse_args()
    args.evidence.mkdir(parents=True, exist_ok=True)
    for apk, package in ((args.app_apk, PACKAGE), (args.test_apk, PACKAGE + ".test")):
        badging = subprocess.run([args.aapt, "dump", "badging", str(apk)], capture_output=True,
                                 text=True, encoding="utf-8", errors="replace", check=True).stdout
        if not badging.startswith("package: name='" + package + "'"):
            raise RuntimeError("Refusing to install an APK outside the UI-01 QA package: " + str(apk))
    prefix = [args.adb, "-s", args.serial]

    def adb(*command, check=True, timeout=60):
        result = subprocess.run(prefix + list(command), capture_output=True, text=True,
                                encoding="utf-8", errors="replace", timeout=timeout)
        if check and result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
        return result.stdout + result.stderr

    def shell(command, **kwargs):
        return adb("shell", command, **kwargs)

    if adb("get-state").strip() != "device":
        raise RuntimeError("Selected Android device is not online")
    if shell("pm path " + PACKAGE, check=False).strip():
        raise RuntimeError("Requires a fresh UI-01 QA installation. Existing data was left untouched.")
    shell("dumpsys connectivity", timeout=15)  # Reject an unresponsive system before changing settings.
    original = {key: shell("settings get global " + key).strip()
                for key in ("airplane_mode_on", "wifi_on")}
    metadata = {"started": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                "serial": args.serial, "original_network": original,
                "smoke": "NOT_RUN_OR_FAILED", "force_stop_reopen": "NOT_RUN_OR_FAILED",
                "reboot": "PENDING: reference-device QA acceptance",
                "device": shell("getprop ro.build.version.sdk") + shell("wm size") + shell("wm density"),
                "apk_sha256": {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                               for p in (args.app_apk, args.test_apk)}}
    summary = args.evidence / "device-summary.json"
    summary.write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    recorder = None
    try:
        shell("settings put global airplane_mode_on 1")
        shell("svc wifi disable")
        for _ in range(30):
            connectivity = shell("dumpsys connectivity")
            if "Active default network: none" in connectivity and shell("settings get global wifi_on").strip() == "0":
                break
            time.sleep(1)
        else:
            raise RuntimeError("Could not verify offline mode; no smoke was run.")
        (args.evidence / "offline-before-first-launch.txt").write_text(
            shell("settings get global airplane_mode_on") + connectivity, encoding="utf-8")
        (args.evidence / "install.txt").write_text(
            adb("install", str(args.app_apk), timeout=120) + adb("install", str(args.test_apk), timeout=120),
            encoding="utf-8")
        started = shell("date '+%m-%d %H:%M:%S.000'").strip()
        recorder = subprocess.Popen(prefix + ["shell", "screenrecord --time-limit 120 /sdcard/ui01-smoke.mp4 & "
                                             "echo $! > /data/local/tmp/ui01-screenrecord.pid; wait"],
                                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                    creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        for method, name in (("aTenCardsAndQuizKeepCommittedDataThroughRecreationOffline", "smoke"),
                             ("bCommittedDataSurvivesReopenProbe", "reopen")):
            if name == "reopen":
                shell("am force-stop " + PACKAGE)
            output = shell("am instrument -w -r -e class " + TEST + "#" + method + " "
                           + PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=240)
            (args.evidence / (name + ".txt")).write_text(output, encoding="utf-8")
            if "OK (1 test)" not in output or "FAILURES" in output:
                raise RuntimeError(name + " failed; inspect its instrumentation output")
            metadata["smoke" if name == "smoke" else "force_stop_reopen"] = "PASS"
    finally:
        try:
            if recorder is not None:
                pid = shell("cat /data/local/tmp/ui01-screenrecord.pid", check=False).strip()
                if pid.isdigit():
                    shell("kill -2 " + pid, check=False)
                try:
                    recorder.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    recorder.terminate()
                adb("pull", "/sdcard/ui01-smoke.mp4", str(args.evidence / "smoke.mp4"), check=False)
                adb("pull", "/sdcard/Android/data/" + PACKAGE + "/files/.", str(args.evidence), check=False)
                (args.evidence / "logcat.txt").write_text(
                    adb("logcat", "-d", "-v", "threadtime", "-T", started, "AndroidRuntime:E", "TestRunner:I", "*:S"),
                    encoding="utf-8")
        finally:
            restore_commands = ["settings " + ("delete global airplane_mode_on" if original["airplane_mode_on"] == "null"
                                               else "put global airplane_mode_on " + original["airplane_mode_on"])]
            if original["wifi_on"] in ("1", "2"):
                restore_commands.append("svc wifi enable")
            failures = []
            for command in restore_commands:
                try:
                    shell(command, timeout=15)
                except (RuntimeError, subprocess.TimeoutExpired) as error:
                    failures.append(str(error))
            metadata["network_restore_errors"] = failures
            metadata["finished"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
            summary.write_text(json.dumps(metadata, indent=2), encoding="utf-8")
            if failures:
                raise RuntimeError("Network restore needs attention; see device-summary.json")


if __name__ == "__main__":
    main()
