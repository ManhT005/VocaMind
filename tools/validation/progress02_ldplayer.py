"""Run isolated PROGRESS-02 acceptance on LD; preserve the device configuration."""
import argparse
import json
import re
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com.vocamind.app.progress02validation"
METHODS = {"flow": "aFullProductionFlow", "probe": "bPersistenceProbe",
           "appearance": "cSmallDarkLargeFont", "rotation": "dActualLandscapeRotation"}
SETTINGS = (("global", "airplane_mode_on"), ("global", "wifi_on"),
            ("system", "font_scale"), ("secure", "ui_night_mode"))


class Device:
    def __init__(self, adb, serial):
        self.adb, self.serial = adb, serial

    def shell(self, command, timeout=60, check=True):
        result = subprocess.run([self.adb, "-s", self.serial, "shell", command],
                                capture_output=True, text=True, timeout=timeout,
                                encoding="utf-8", errors="replace")
        if check and result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
        return result.stdout + result.stderr

    def pull(self, remote, local):
        subprocess.run([self.adb, "-s", self.serial, "pull", remote, str(local)],
                       check=True, timeout=60)


def validate_packages(device):
    packages = {}
    for package in (PACKAGE, PACKAGE + ".test"):
        paths = device.shell("pm path " + package).strip().splitlines()
        if len(paths) != 1 or not paths[0].startswith("package:/data/app/"):
            raise RuntimeError("Isolated QA package missing: " + package)
        path = paths[0][len("package:"):]
        if not re.fullmatch(r"/data/app/[A-Za-z0-9_./+=-]+\.apk", path):
            raise RuntimeError("Unexpected APK path")
        packages[package] = {"path": path, "sha256": device.shell("sha256sum " + path).split()[0]}
    return packages


def snapshot(device):
    values = {namespace + "/" + key: device.shell("settings get " + namespace + " " + key).strip()
              for namespace, key in SETTINGS}
    for kind in ("size", "density"):
        output = device.shell("wm " + kind)
        override = re.search(r"(?m)^Override " + kind + r":\s*(\S+)", output)
        values[kind] = override.group(1) if override else "reset"
    mode = re.search(r"Night mode:\s*(auto|yes|no)", device.shell("cmd uimode night"))
    if not mode:
        raise RuntimeError("Cannot capture night-mode service state")
    values["night_service"] = mode.group(1)
    return {"serial": device.serial, "values": values}


def setting_command(namespace, key, value):
    if value == "null":
        return "settings delete " + namespace + " " + key
    if not re.fullmatch(r"[A-Za-z0-9_.+-]+", value):
        raise RuntimeError("Unexpected setting value")
    return "settings put " + namespace + " " + key + " " + value


def restore(device, saved):
    if saved["serial"] != device.serial:
        raise RuntimeError("Snapshot belongs to another device")
    values = saved["values"]
    commands = ["wm size " + values["size"], "wm density " + values["density"],
                "cmd uimode night " + values["night_service"],
                setting_command("system", "font_scale", values["system/font_scale"]),
                setting_command("secure", "ui_night_mode", values["secure/ui_night_mode"]),
                setting_command("global", "airplane_mode_on", values["global/airplane_mode_on"]),
                "svc wifi " + ("enable" if values["global/wifi_on"] in ("1", "2") else "disable"),
                setting_command("global", "wifi_on", values["global/wifi_on"])]
    errors = []
    for command in commands:
        try:
            device.shell(command)
        except Exception as error:
            errors.append(str(error))  # Attempt every restoration even when one command fails.
    if errors:
        raise RuntimeError("Restoration incomplete; retry --stage restore: " + "; ".join(errors))
    if snapshot(device) != saved:
        raise RuntimeError("Restored configuration differs; recovery snapshot retained")


def run_instrumentation(device, method, remote):
    device.shell("rm -f " + remote)
    command = ("am instrument -w -r -e class com.vocamind.app.feature.progress."
               "ProgressAcceptanceInstrumentedTest#" + method + " " + PACKAGE +
               ".test/androidx.test.runner.AndroidJUnitRunner > " + remote + " 2>&1 < /dev/null &")
    try:
        device.shell(command, timeout=10)
    except subprocess.TimeoutExpired:
        pass  # Android-side instrumentation continues independently of the host connection.
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        time.sleep(3)
        try:
            result = device.shell("cat " + remote, timeout=5, check=False)
            if "INSTRUMENTATION_CODE:" in result:
                return result
        except subprocess.TimeoutExpired:
            # Reconnect the selected transport; never silently switch to another LD instance.
            if re.fullmatch(r"[A-Za-z0-9.-]+:[0-9]+", device.serial):
                subprocess.run([device.adb, "connect", device.serial], capture_output=True, timeout=5)
    raise RuntimeError("Timed out waiting for instrumentation")


def run_stage(device, stage, output, state_file):
    # All validation is read-only and precedes every configuration write.
    packages = validate_packages(device)
    if state_file.exists():
        raise RuntimeError("Pending configuration snapshot; run --stage restore first")
    saved = snapshot(device)
    output.mkdir(parents=True, exist_ok=True)
    state_file.parent.mkdir(parents=True, exist_ok=True)
    temporary = state_file.with_suffix(".tmp")
    temporary.write_text(json.dumps(saved, indent=2), encoding="utf-8")
    temporary.replace(state_file)  # Survives a killed host process for explicit recovery.
    record = {"stage": stage, "method": METHODS[stage], "serial": device.serial,
              "base_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
              "device_apks": packages, "original_configuration": saved}
    try:
        (output / (stage + "-run.json")).write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
        if stage == "appearance":
            for command in ("wm size 720x1280", "wm density 360",
                            "settings put system font_scale 2.0", "cmd uimode night yes"):
                device.shell(command)
            time.sleep(5)
        # The instrumented @Before/@After manages offline mode without forcing the original network online.
        result = run_instrumentation(device, METHODS[stage], "/sdcard/progress02-" + stage + ".log")
        (output / (stage + ".log")).write_text(result, encoding="utf-8")
        print(result)
        if ("FAILURES!!!" in result or "OK (1 test)" not in result
                or "INSTRUMENTATION_STATUS_CODE: -3" in result
                or "INSTRUMENTATION_STATUS_CODE: -4" in result):
            raise RuntimeError("Instrumentation did not pass")
    except BaseException:
        # Stop our background test before rollback so it cannot change settings after restoration.
        try:
            device.shell("am force-stop " + PACKAGE, timeout=10)
        except Exception:
            pass
        raise
    finally:
        restore(device, saved)
        actual = snapshot(device)
        (output / (stage + "-restored.json")).write_text(json.dumps(actual, indent=2) + "\n", encoding="utf-8")
        if actual != saved:
            raise RuntimeError("Restored configuration differs; recovery snapshot retained")
        state_file.unlink()  # Keep the recovery file if any restoration or verification failed.


def collect(device, output):
    validate_packages(device)
    output.mkdir(parents=True, exist_ok=True)
    commands = ("getprop ro.build.version.sdk", "getprop ro.product.model", "getprop ro.build.fingerprint",
                "getprop persist.sys.timezone", "settings get global airplane_mode_on",
                "settings get global wifi_on", "wm size", "wm density")
    (output / "device.txt").write_text("".join(c + "\n" + device.shell(c) + "\n" for c in commands), encoding="utf-8")
    for remote in device.shell("ls /data/local/tmp/progress02-*.png").splitlines():
        if re.fullmatch(r"/data/local/tmp/progress02-[A-Za-z0-9-]+\.png", remote):
            device.pull(remote, output / Path(remote).name)
    for name in ("progress02-expected.txt", "progress02-offline.txt"):
        device.pull("/sdcard/Android/data/" + PACKAGE + "/files/" + name, output / name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--stage", choices=[*METHODS, "collect", "restore"], required=True)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "docs/evidence/progress-02/ldplayer")
    parser.add_argument("--state-file", type=Path, default=ROOT / "build/progress02-ldplayer-state.json")
    args = parser.parse_args()
    device = Device(args.adb, args.serial)
    if args.stage == "restore":
        if not args.state_file.exists():
            print("No pending configuration snapshot; no device settings changed")
            return
        saved = json.loads(args.state_file.read_text(encoding="utf-8"))
        restore(device, saved)
        args.state_file.unlink()
    elif args.stage == "collect":
        collect(device, args.output_dir)
    else:
        run_stage(device, args.stage, args.output_dir, args.state_file)


if __name__ == "__main__":
    main()
