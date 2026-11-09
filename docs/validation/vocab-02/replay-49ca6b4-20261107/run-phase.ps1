param([Parameter(Mandatory=$true)][ValidateSet('a','b','c','d','e','f','h','g')][string]$Phase,[string]$Serial='emulator-5554',[string]$Adb='C:\Users\LMT\AppData\Local\Android\Sdk\platform-tools\adb.exe')
$ErrorActionPreference='Stop'
$run=$PSScriptRoot;$root=(Resolve-Path (Join-Path $run '../../../..')).Path
$binding=Get-Content -Raw (Join-Path $run 'build-binding.json')|ConvertFrom-Json
$methods=@{a='aReadOfflineAndFirstSave';b='bSavedAfterForceStop';c='cSavedAfterRebootThenUnsave';d='dUnsavedAfterForceStop';e='eUnsavedAfterRebootAndExistingProgress';f='fControlledErrorsRetryAndIpaRendering';h='hLoadingRotationAndReadableExamples';g='gExportEvidence'}
function Fingerprint([string]$package){$path=(& $Adb -s $Serial shell pm path $package|Select-Object -First 1).Trim().Substring(8);if(-not $path){throw 'Missing installed QA package'};$line=& $Adb -s $Serial shell sha256sum $path;if($LASTEXITCODE -ne 0){throw 'Cannot hash installed package'};return [pscustomobject]@{package=$package;path=$path;sha256=($line -split '\s+')[0].ToUpperInvariant()}}
function CheckBinding {
 $probe=(Get-FileHash (Join-Path $run 'VocabularyDeviceQa.java')).Hash
 $compiled=(Get-FileHash (Join-Path $root 'build/vocab02-device/src/com/vocamind/app/feature/vocabulary/VocabularyDeviceQa.java')).Hash
 $app=(Get-FileHash (Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk')).Hash
 $test=(Get-FileHash (Join-Path $root 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')).Hash
 $ia=Fingerprint 'com.vocamind.app.vocab02qa';$it=Fingerprint 'com.vocamind.app.vocab02qa.test'
 if($probe -ne $binding.probeSha256 -or $compiled -ne $probe -or $app -ne $binding.appApkSha256 -or $test -ne $binding.testApkSha256 -or $ia.sha256 -ne $app -or $it.sha256 -ne $test){throw 'Binding mismatch: refusing to run/relabel test'}
 return [pscustomobject]@{probe=$probe;compilationSource=$compiled;hostAppApk=$app;hostTestApk=$test;installedApp=$ia;installedTest=$it}
}
$record=[ordered]@{phase=$Phase;method=$methods[$Phase];startedAt=[string](Get-Date -Format o);before=CheckBinding;bootId=(& $Adb -s $Serial shell cat /proc/sys/kernel/random/boot_id).Trim();airplaneMode=(& $Adb -s $Serial shell settings get global airplane_mode_on).Trim()}
if($record.airplaneMode -ne '1'){throw 'Must be offline before launch'}
$selector='com.vocamind.app.feature.vocabulary.VocabularyDeviceQa#'+$methods[$Phase]
$psi=[Diagnostics.ProcessStartInfo]::new();$psi.FileName=$Adb;$psi.Arguments='-s '+$Serial+' shell am instrument -w -r -e class '+$selector+' com.vocamind.app.vocab02qa.test/androidx.test.runner.AndroidJUnitRunner';$psi.UseShellExecute=$false;$psi.CreateNoWindow=$true;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
$p=[Diagnostics.Process]::Start($psi);$log=Join-Path $run ('phase-'+$Phase+'.log');$stream=[IO.File]::Create($log)
try{$p.StandardOutput.BaseStream.CopyTo($stream)}finally{$stream.Dispose()};$stderr=$p.StandardError.ReadToEnd();$p.WaitForExit();[IO.File]::WriteAllText((Join-Path $run ('phase-'+$Phase+'-stderr.log')),$stderr,[Text.UTF8Encoding]::new($false))
$text=[IO.File]::ReadAllText($log);$record.exitCode=$p.ExitCode;$record.jUnitPassed=($text -match 'OK \(1 test\)' -and $text -notmatch 'FAILURES|INSTRUMENTATION_FAILED|Process crashed');$record.after=CheckBinding;$record.finishedAt=[string](Get-Date -Format o);$record.logSha256=(Get-FileHash $log).Hash
[IO.File]::WriteAllText((Join-Path $run ('phase-'+$Phase+'.json')),($record|ConvertTo-Json -Depth 7),[Text.UTF8Encoding]::new($false))
Get-Content -Encoding UTF8 $log -Tail 12
if($p.ExitCode -ne 0 -or -not $record.jUnitPassed){throw ('Instrumentation phase '+$Phase+' failed; do not proceed')}