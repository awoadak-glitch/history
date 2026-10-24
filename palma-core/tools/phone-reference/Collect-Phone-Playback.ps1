[CmdletBinding()]
param(
    [string]$AdbPath = '',
    [ValidateRange(15,300)][int]$Seconds = 90,
    [string]$OutputDirectory = '',
    [switch]$SelfTest
)
$ErrorActionPreference = 'Stop'

function Get-TextHash([string]$Value) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Value)))).Replace('-','').ToLowerInvariant() }
    finally { $sha.Dispose() }
}

function Convert-PlaybackLog([string]$Line) {
    # Original TDM 2.2.2 VideoPlayerActivity.z0 / F0 logs. Never retain the raw
    # line, URL token prefix, query values, cookies, or the device serial number.
    $rawUrl = $null
    $title = $null
    $cookies = $null
    $uaOverride = $null
    $referer = $null
    if ($Line -match 'Deep link parsed - URL: (.+?), Title: (.*?), Ad: .*?, UA-override: (true|false|0|1), Referer: (true|false|0|1), Cookies: (true|false|0|1)\s*$') {
        $rawUrl = $Matches[1]
        $title = $Matches[2]
        $uaOverride = $Matches[3] -in @('true','1')
        $referer = $Matches[4] -in @('true','1')
        $cookies = $Matches[5] -in @('true','1')
    } elseif ($Line -match 'Playing video from URL: (https?://\S+)\s*$') {
        $rawUrl = $Matches[1]
    } else { return $null }
    $uri = $null
    if (-not [Uri]::TryCreate($rawUrl, [UriKind]::Absolute, [ref]$uri)) { return $null }
    if ($uri.Scheme -notin @('http','https') -or $uri.UserInfo) { return $null }
    $storageObject = $null
    # The observed source layout is /t/<expiry>/<signature>/b2/files/...
    # Keep only the named object after /b2/, never the signature-bearing prefix.
    if ($uri.AbsolutePath -match '/b2/(files/[A-Za-z0-9_./% -]+\.(?:css|js|m3u8|mp4))$') {
        $storageObject = $Matches[1]
    }
    $queryNames = @()
    if ($uri.Query) {
        $queryNames = @($uri.Query.TrimStart('?').Split('&') | ForEach-Object {
            $name = ($_ -split '=',2)[0]
            if ($name -match '^[A-Za-z][A-Za-z0-9_-]{0,39}$') { $name }
        } | Select-Object -Unique)
    }
    # Do not copy arbitrary title/log fields. Only flags and URL metadata that
    # are needed to compare the selected source object are exported.
    return [ordered]@{
        observedAtUtc = [DateTime]::UtcNow.ToString('o')
        origin = $uri.GetLeftPart([UriPartial]::Authority)
        storageObject = $storageObject
        pathSha256 = Get-TextHash $uri.AbsolutePath
        pathSegmentCount = @($uri.AbsolutePath.Split('/') | Where-Object { $_ }).Count
        queryParameterNames = $queryNames
        cookiesPresent = $cookies
        userAgentOverridePresent = $uaOverride
        refererPresent = $referer
    }
}

if ($SelfTest) {
    $signedUrl = 'https://cdn.mediaoscar.com/t/expiry/SECRET-PATH-TOKEN/b2/files/Test/Movie/720p/index.css?token=SECRET-QUERY-TOKEN'
    $line = "D/VideoPlayerActivity(123): Deep link parsed - URL: $signedUrl, Title: SECRET-TITLE, Ad: true, Banner: true, Download: true, UA-override: true, Referer: false, Cookies: true"
    $parsed = Convert-PlaybackLog $line
    if ($parsed.storageObject -ne 'files/Test/Movie/720p/index.css' -or -not $parsed.cookiesPresent -or -not $parsed.userAgentOverridePresent) { throw 'Expected TDM fields were not parsed.' }
    $json = $parsed | ConvertTo-Json -Depth 8
    if ($json -match 'SECRET-|expiry') { throw 'Sensitive input leaked to the report.' }
    $plain = Convert-PlaybackLog 'D/VideoPlayerActivity(123): Playing video from URL: https://example.org/private/SECRET-PATH?auth=SECRET-QUERY'
    if (-not $plain -or ($plain | ConvertTo-Json -Depth 8) -match 'SECRET-') { throw 'Opaque URL was not sanitized.' }
    if ($null -ne (Convert-PlaybackLog 'D/Other(123): cookies=SECRET-COOKIE')) { throw 'Unrelated log accepted.' }
    if ($null -ne (Convert-PlaybackLog 'Playing video from URL: https://user:password@example.org/x')) { throw 'Credential URL accepted.' }
    Write-Host 'PASS: reference log parsing and secret redaction. No device or network was used.'
    exit 0
}

if (-not $OutputDirectory) { $OutputDirectory = Join-Path $PSScriptRoot 'phone-report' }
if (-not $AdbPath) {
    $candidates = @((Join-Path $PSScriptRoot 'adb.exe'), (Join-Path $PSScriptRoot 'platform-tools\adb.exe'), 'C:\platform-tools\adb.exe')
    $AdbPath = $candidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
    if (-not $AdbPath) {
        $cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
        if ($cmd) { $AdbPath = $cmd.Source }
    }
}
if (-not $AdbPath -or -not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) {
    throw 'adb.exe was not found. Put Google platform-tools beside this script, or use -AdbPath with its full path.'
}
$AdbPath = (Resolve-Path -LiteralPath $AdbPath).Path
function Invoke-AdbText([string[]]$Arguments) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $result = & $AdbPath @Arguments 2>$null
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($code -ne 0) { throw 'ADB could not complete the requested device read.' }
    return ($result -join "`n")
}

Write-Host 'Connect ONE Android phone by USB. Enable USB debugging and accept its connection prompt.'
Write-Host 'This tool reads only TDM playback logs. It does not install, modify, stop, or clear either app.'
[void](Read-Host 'Press Enter when the phone is connected')
$devices = Invoke-AdbText -Arguments @('devices')
$serials = @([regex]::Matches($devices,'(?m)^([A-Za-z0-9._:-]+)\s+device\s*$') | ForEach-Object { $_.Groups[1].Value })
if ($serials.Count -ne 1) { throw 'Exactly one authorized phone is required. Check USB debugging authorization and disconnect additional devices.' }
$serial = $serials[0]
$packageText = Invoke-AdbText -Arguments @('-s',$serial,'shell','dumpsys','package','com.tdm.manager')
if ($packageText -notmatch '(?m)^\s*userId=(\d+)\s*$') { throw 'The original com.tdm.manager package was not found in the supported Android profile.' }
$uid = $Matches[1]
$tdmVersionName = if ($packageText -match 'versionName=([A-Za-z0-9._+-]+)') { $Matches[1] } else { $null }
$tdmVersionCode = if ($packageText -match 'versionCode=(\d+)') { [long]$Matches[1] } else { $null }
$appText = Invoke-AdbText -Arguments @('-s',$serial,'shell','dumpsys','package','com.drama.mp4')
$appVersionName = if ($appText -match 'versionName=([A-Za-z0-9._+-]+)') { $Matches[1] } else { $null }
$appVersionCode = if ($appText -match 'versionCode=(\d+)') { [long]$Matches[1] } else { $null }

$info = New-Object Diagnostics.ProcessStartInfo
$info.FileName = $AdbPath
# Serial is restricted to the device identifier alphabet above; all other
# arguments are fixed or numeric. No command shell is involved.
$info.Arguments = "-s $serial logcat --uid=$uid -v brief -T 1 VideoPlayerActivity:D *:S"
$info.UseShellExecute = $false
$info.CreateNoWindow = $true
$info.RedirectStandardOutput = $true
$info.RedirectStandardError = $true
$process = New-Object Diagnostics.Process
$process.StartInfo = $info
$records = New-Object 'Collections.Generic.List[object]'
$started = $false
try {
    $started = $process.Start()
    if (-not $started) { throw 'Could not start the TDM log reader.' }
    $errorRead = $process.StandardError.ReadToEndAsync()
    $pending = $process.StandardOutput.ReadLineAsync()
    $deadline = [DateTime]::UtcNow.AddSeconds($Seconds)
    Write-Host "Now open the ORIGINAL phone app and play the same movie through TDM. Recording for $Seconds seconds..."
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($pending.Wait(200)) {
            $line = $pending.Result
            if ($null -eq $line) { break }
            $record = Convert-PlaybackLog $line
            if ($record) { $records.Add($record); Write-Host 'Captured a playback event; session values removed.' }
            $pending = $process.StandardOutput.ReadLineAsync()
        }
    }
} finally {
    if ($started -and -not $process.HasExited) { $process.Kill(); [void]$process.WaitForExit(3000) }
    $process.Dispose()
}

$answer = (Read-Host 'Did the REQUESTED video actually play correctly on the phone? Type YES or NO; otherwise press Enter').Trim().ToUpperInvariant()
$played = if ($answer -eq 'YES') { $true } elseif ($answer -eq 'NO') { $false } else { $null }
$report = [ordered]@{
    schema = 1
    createdAtUtc = [DateTime]::UtcNow.ToString('o')
    source = 'Original TDM VideoPlayerActivity logs scoped to its Android UID'
    tdm = @{ package = 'com.tdm.manager'; versionName = $tdmVersionName; versionCode = $tdmVersionCode }
    application = @{ package = 'com.drama.mp4'; versionName = $appVersionName; versionCode = $appVersionCode }
    requestedContentPlayed = $played
    playbackConfirmation = 'user observation, not inferred from logs'
    observations = @($records.ToArray())
    limitations = @('No API response bodies or cryptographic attestation were captured.', 'The newest retained TDM log may precede the recording; use events from the playback started during this recording.')
}
[void](New-Item -ItemType Directory -Path $OutputDirectory -Force)
$destination = Join-Path $OutputDirectory 'phone-playback-reference.json'
$report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $destination -Encoding UTF8
if ($records.Count -eq 0) { Write-Host 'No matching TDM playback events were available. This is not proof that playback failed.' }
Write-Host "Report saved: $destination"
Write-Host 'Send only phone-playback-reference.json back to the developer.'
