param([string]$Run=$PSScriptRoot)
$ErrorActionPreference='Stop'
$m=Get-Content -Raw (Join-Path $Run 'verification.json')|ConvertFrom-Json
foreach($item in $m.files){$actual=(Get-FileHash -LiteralPath (Join-Path $Run $item.path) -Algorithm SHA256).Hash;if($actual -ne $item.sha256){throw ('Artifact hash mismatch: '+$item.path)}}
$binding=Get-Content -Raw (Join-Path $Run 'build-binding.json')|ConvertFrom-Json
$phases=@('a','b','c','d','e','f','h','g')
foreach($phase in $phases){$p=Get-Content -Raw (Join-Path $Run ('phase-'+$phase+'.json'))|ConvertFrom-Json;if(-not $p.jUnitPassed -or $p.exitCode -ne 0){throw ('Phase not passed: '+$phase)};foreach($v in @($p.before,$p.after)){if($v.probe -ne $binding.probeSha256 -or $v.compilationSource -ne $binding.probeSha256 -or $v.hostAppApk -ne $binding.appApkSha256 -or $v.installedApp.sha256 -ne $binding.appApkSha256 -or $v.hostTestApk -ne $binding.testApkSha256 -or $v.installedTest.sha256 -ne $binding.testApkSha256){throw ('Phase binding mismatch: '+$phase)}}}
'VERIFIED: all manifest artifacts, eight phase records, one probe and one installed app/test APK pair match. Seven functional phases; g export only.'