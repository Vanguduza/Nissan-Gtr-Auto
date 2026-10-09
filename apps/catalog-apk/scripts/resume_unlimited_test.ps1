#!/usr/bin/env pwsh
# Resume unlimited Catalog APK depth test after WiFi ADB is back.
$ErrorActionPreference = "Stop"
$adb = "C:\Users\j\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$apk = "C:\Nissan GTR auto\apps\catalog-apk\app\build\outputs\apk\debug\app-debug.apk"
$pkg = "co.zw.nissangtr.catalogapk"

Write-Host "Waiting for device..."
for ($i = 1; $i -le 30; $i++) {
  $svc = & $adb mdns services 2>&1 | Out-String
  $m = [regex]::Match($svc, "(\d+\.\d+\.\d+\.\d+:\d+)")
  if ($m.Success) {
    & $adb connect $m.Groups[1].Value | Out-Null
  }
  if ((& $adb devices) -match "\tdevice") { break }
  Start-Sleep -Seconds 5
}
if (-not ((& $adb devices) -match "\tdevice")) {
  throw "No ADB device. Toggle Wireless debugging on the phone, then re-run."
}

& $adb install -r $apk
& $adb reverse tcp:8191 tcp:8191
& $adb reverse tcp:8192 tcp:8192
& $adb shell "run-as $pkg sh -c 'rm -rf files/chaquopy'"
& $adb shell "am force-stop $pkg"

$sh = @"
#!/system/bin/sh
PKG=$pkg
RECV=`$PKG/.worker.DebugEnqueueReceiver
ACT=`$PKG.DEBUG_ENQUEUE
am start -n `$PKG/.MainActivity >/dev/null 2>&1
sleep 5
am broadcast -a `$ACT -n `$RECV --es resume_job_id 4974b7d1-9ddf-4e7a-b343-de7781d860ac --ei max_pages 0 --ei max_concurrent 2
sleep 2
am broadcast -a `$ACT -n `$RECV --es profile_id partsouq --es maker Nissan --es model_slug patrol-y61 --es model_name PatrolY61 --es chassis Y61 --ei max_pages 0 --ei max_concurrent 2 --ez pause_others false
sleep 2
am broadcast -a `$ACT -n `$RECV --es profile_id 7zap --es maker Nissan --es model_slug patrol-y61 --es model_name PatrolY61 --es chassis Y61 --ei max_pages 0 --ei max_concurrent 2 --ez pause_others false
echo DONE
"@
$tmp = "$env:TEMP\_resume_unlimited.sh"
[System.IO.File]::WriteAllText($tmp, ($sh -replace "`r`n", "`n" -replace "`r", "`n"))
& $adb push $tmp /data/local/tmp/_resume_unlimited.sh
& $adb shell "sh /data/local/tmp/_resume_unlimited.sh"
Write-Host "Enqueued. Verify with run-as $pkg cat files/catalog-jobs/*/out/argv.txt"
