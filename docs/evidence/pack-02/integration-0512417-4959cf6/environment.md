# Integrated worktree QA environment

- Execution date: 2026-11-06, Asia/Saigon. Executed by the assistant for Trường's
  local review; no reviewer approval asserted.
- HEAD / reviewed PACK-02 parent: `0512417f1e5d398e09f6c9a00707b6a28c4a2543`.
- Incoming develop parent: `4959cf6d29314d7105f4285edd76e3169d912958`.
- **Uncommitted integration**. Source fingerprint SHA256:
  `f0025e557ed0ceb92d540a88b99969176ced36f59bdc40dbe669c7f2fbf04395`.
  See source-manifest.json for exact source/config/schema/test hashes.
- Windows, JDK17, Gradle8.9, compile SDK35. Python3.13.14/jsonschema4.26.0.
- Disposable emulator `pack02-followup`, serial `emulator-5560`, API30,
  `sdk_gphone_x86`; system image and full fingerprint in device/environment.json.
- Configuration copied into `build/pack02-followup-avd/config.ini` with fresh data
  under that directory; no user AVD data/snapshot copied. Headless, no snapshots,
  port5560. Existing Small_Phone AVD unchanged.
- Both app and test APK SHA256 are in device/environment.json and local-evidence.json;
  compared equal to the final build artifacts. No new commit SHA exists by instruction.
- Airplane mode on, Wi-Fi and mobile data off before first app Activity launch.
  Settings checked again after each reboot. No network/account requirement added.

Run the phase driver only on a newly prepared disposable QA AVD with its exact
name/serial. It intentionally clears that QA app for first-launch evidence and
must never target a user's install. The scripts are archived for reproducibility,
not a generic command to run against any connected device.

Android Gradle connected runner uninstalled its test-managed app after the 18-case
regression suite. The final list/Today visual smoke reinstalled the same APK into
the disposable AVD. Persistence/process-restoration snapshots were captured before
that cleanup and remain identified by their separate run logs/hashes.

Raw local run: `build/pack02-device-final-20261106-170259/`.
Evidence text copies normalize Android shell CR/CRLF to LF for readable diffs;
screenshots, source/APK hashes, and JUnit XML evidence are not synthesized.
Full command outputs remain in the raw local run; commands-summary.json records
commands, exits, output hashes and bounded previews.
