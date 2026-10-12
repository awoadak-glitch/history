param([string]$LocalArchive = '')

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$playerDir = Join-Path $root 'player'
$cacheDir = Join-Path $env:LOCALAPPDATA 'AWR-MX-Desktop\download-cache'
$logFile = Join-Path $root 'Setup-MPV.log'
New-Item -ItemType Directory -Force -Path $playerDir, $cacheDir | Out-Null

function Log([string]$Message) {
    $stamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    Write-Host $Message
    try { Add-Content -LiteralPath $logFile -Value "[$stamp] $Message" -Encoding UTF8 } catch {}
}

function Test-Pe([string]$path) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { return $false }
    try {
        $fs = [IO.File]::OpenRead($path)
        try { return ($fs.Length -gt 100000 -and $fs.ReadByte() -eq 77 -and $fs.ReadByte() -eq 90) }
        finally { $fs.Dispose() }
    } catch { return $false }
}

function Test-Engine([string]$exe) {
    if (-not (Test-Pe $exe)) { return $false }
    try {
        $proc = Start-Process -FilePath $exe -ArgumentList '--version' -PassThru -WindowStyle Hidden -ErrorAction Stop
        if (-not $proc.WaitForExit(20000)) {
            try { $proc.Kill() } catch {}
            Log 'mpv.exe --version timed out.'
            return $false
        }
        Log ("mpv.exe --version exit code: " + $proc.ExitCode)
        return ($proc.ExitCode -eq 0)
    } catch {
        Log ("mpv.exe could not launch: " + $_.Exception.Message)
        return $false
    }
}

# Do not rely on ZipFile.Entries for validation. Some ZIP encodings / paths
# differ among Windows mpv builds and older PowerShell/.NET versions.
# Instead try multiple extractors and inspect the result.
function Extract-Zip([string]$archive, [string]$stage) {
    $extractors = @('Windows tar.exe', 'PowerShell Expand-Archive', '7-Zip')
    foreach ($method in $extractors) {
        $mpv = $null
        try {
            Get-ChildItem -LiteralPath $stage -Force -ErrorAction SilentlyContinue |
                Remove-Item -Force -Recurse -ErrorAction Stop
            Log ("Trying extractor: " + $method)
            if ($method -eq 'Windows tar.exe') {
                $tar = Get-Command 'tar.exe' -ErrorAction SilentlyContinue
                if (-not $tar) { continue }
                & $tar.Source -xf $archive -C $stage 2>&1 | Out-Null
                if ($LASTEXITCODE -ne 0) { throw ("tar exit " + $LASTEXITCODE) }
            } elseif ($method -eq 'PowerShell Expand-Archive') {
                $zipPath = $archive
                if (-not $archive.EndsWith('.zip', [StringComparison]::OrdinalIgnoreCase)) {
                    # Expand-Archive requires a .zip extension; cached files may end in .zip.part.
                    $zipPath = Join-Path $cacheDir ('expand-' + [Guid]::NewGuid().ToString('N') + '.zip')
                    Copy-Item -LiteralPath $archive -Destination $zipPath -Force
                }
                try {
                    Expand-Archive -LiteralPath $zipPath -DestinationPath $stage -Force -ErrorAction Stop
                } finally {
                    if ($zipPath -ne $archive) { Remove-Item -LiteralPath $zipPath -Force -ErrorAction SilentlyContinue }
                }
            } else {
                $paths = @()
                if ($env:ProgramFiles) { $paths += (Join-Path $env:ProgramFiles '7-Zip\7z.exe') }
                if (${env:ProgramFiles(x86)}) { $paths += (Join-Path ${env:ProgramFiles(x86)} '7-Zip\7z.exe') }
                $sevenZip = $paths | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
                if (-not $sevenZip) { continue }
                & $sevenZip x -y "-o$stage" $archive 2>&1 | Out-Null
                if ($LASTEXITCODE -ne 0) { throw ("7-Zip exit " + $LASTEXITCODE) }
            }
            $mpv = Get-ChildItem -LiteralPath $stage -Filter mpv.exe -File -Recurse -ErrorAction Stop |
                   Where-Object { (Test-Pe $_.FullName) } | Select-Object -First 1
            if (-not $mpv) {
                # The first-party release wraps the Windows build in a second ZIP.
                $innerArchives = @(Get-ChildItem -LiteralPath $stage -Filter '*.zip' -File -Recurse | Select-Object -First 3)
                foreach ($inner in $innerArchives) {
                    $nested = Join-Path $stage ('nested-' + [Guid]::NewGuid().ToString('N'))
                    Expand-Archive -LiteralPath $inner.FullName -DestinationPath $nested -Force -ErrorAction Stop
                }
                $mpv = Get-ChildItem -LiteralPath $stage -Filter mpv.exe -File -Recurse |
                    Where-Object { (Test-Pe $_.FullName) } | Select-Object -First 1
            }
            if ($mpv) { return $mpv.FullName }
            Log 'No usable mpv.exe found in extracted content.'
        } catch {
            Log ("Extractor failed (" + $method + "): " + $_.Exception.Message)
        }
    }
    return ''
}

function Try-Install([string]$archive) {
    if (-not (Test-Path -LiteralPath $archive -PathType Leaf)) { return $false }
    $info = Get-Item -LiteralPath $archive
    Log ("Archive: " + $info.FullName + " (" + $info.Length + " bytes)")
    if ($info.Length -lt 12000000) {
        Log 'File too small for this mpv build; treating it as a partial download.'
        return $false
    }
    $stage = Join-Path $cacheDir ('extract-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $stage | Out-Null
    try {
        # Extract any ZIP, including .zip.part downloaded by the previous installer.
        # For tar.exe the filename suffix does not determine the archive format.
        $exe = Extract-Zip $archive $stage
        if (-not $exe) {
            Log 'Could not extract a valid Windows mpv.exe. Keeping archive for diagnosis.'
            return $false
        }
        Log ("Found mpv.exe at: " + $exe)
        # Copy all DLLs, subdirectories and resources belonging to this build.
        $sourceDir = Split-Path -Parent $exe
        Get-ChildItem -LiteralPath $sourceDir -Force -ErrorAction Stop | ForEach-Object {
            Copy-Item -LiteralPath $_.FullName -Destination $playerDir -Force -Recurse -ErrorAction Stop
        }
        $dstExe = Join-Path $playerDir 'mpv.exe'
        if (-not (Test-Pe $dstExe)) { throw 'Extracted mpv.exe is not a valid Windows executable.' }
        if (-not (Test-Engine $dstExe)) {
            Log 'mpv.exe was extracted, but failed to start on this PC. Missing runtime or security block may be involved.'
            Log 'The extracted engine is kept in the player folder for inspection.'
            return $false
        }
        Log 'SUCCESS: mpv engine is now installed and tested.'
        return $true
    } catch {
        Log ("Install error: " + $_.Exception.Message)
        return $false
    } finally {
        Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue
    }
}

try {
    Log 'AWR MX Desktop - mpv installer v7 (nested release archive supported)'
    $dstExe = Join-Path $playerDir 'mpv.exe'
    if (Test-Engine $dstExe) {
        Log 'Already installed and working. You can start AWR-MX-Desktop.exe.'
        exit 0
    }

    # Check user ZIP and previous installer caches BEFORE spending bandwidth.
    $local = @()
    if ($LocalArchive) { $local += $LocalArchive }
    $local += @(Get-ChildItem -LiteralPath $root -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '(?i)^mpv.*\.(zip|7z)$' } |
        ForEach-Object { $_.FullName })
    $cached = @(Get-ChildItem -LiteralPath $cacheDir -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '(?i)\.zip(\.part)?$' } |
        Sort-Object Length -Descending | ForEach-Object { $_.FullName })
    $seen = @{}
    foreach ($archive in ($local + $cached)) {
        if (-not $archive -or $seen.ContainsKey($archive)) { continue }
        $seen[$archive] = $true
        Log ('Trying existing file first: ' + $archive)
        if (Try-Install $archive) { exit 0 }
    }

    $curl = Get-Command 'curl.exe' -ErrorAction SilentlyContinue
    if (-not $curl) { throw 'curl.exe is unavailable. Download official mpv ZIP from browser and place it next to installer.' }
    $sources = @(
        @{ Id='source-1'; Url='https://github.com/mpv-player/mpv/releases/download/v0.41.0/mpv-v0.41.0-x86_64-w64-mingw32.zip'; Name='mpv-v0.41.0-x86_64-w64-mingw32.zip' },
        @{ Id='source-2'; Url='https://downloads.sourceforge.net/project/mpv.mirror/v0.41.0/mpv-v0.41.0-x86_64-w64-mingw32.zip'; Name='mpv-v0.41.0-x86_64-w64-mingw32.zip' },
        @{ Id='source-msvc'; Url='https://github.com/mpv-player/mpv/releases/download/v0.41.0/mpv-v0.41.0-x86_64-pc-windows-msvc.zip'; Name='mpv-v0.41.0-x86_64-pc-windows-msvc.zip' }
    )

    # Prefer resuming the user's existing partial download if one exists.
    $ordered = @($sources | Sort-Object -Property @{Expression={
        $file = Join-Path $cacheDir ($_.Id + '-' + $_.Name + '.part')
        if (Test-Path -LiteralPath $file) { (Get-Item -LiteralPath $file).Length } else { 0 }
    };Descending=$true})

    foreach ($src in $ordered) {
        $target = Join-Path $cacheDir ($src.Id + '-' + $src.Name)
        $part = $target + '.part'
        if (Test-Path -LiteralPath $target) {
            if (Try-Install $target) { exit 0 }
        }
        Log ('Downloading: ' + $src.Url)
        for ($attempt = 1; $attempt -le 3; $attempt++) {
            Log ('Attempt ' + $attempt + ' of 3; resuming partial ZIP if available.')
            $args = @('-f', '-L', '--show-error', '--retry', '2', '--retry-delay', '3', '--connect-timeout', '25', '--speed-limit', '10240', '--speed-time', '90')
            if (Test-Path -LiteralPath $part) { $args += @('-C', '-') }
            $args += @('-o', $part, $src.Url)
            & $curl.Source @args
            $code = $LASTEXITCODE
            if ($code -eq 33) {
                Log 'Server did not accept resume; a fresh transfer is required.'
                # Keep a copy for investigation rather than silently deleting it.
                $saved = $part + '.resume-rejected'
                Move-Item -LiteralPath $part -Destination $saved -Force -ErrorAction SilentlyContinue
                continue
            }
            if ($code -ne 0) {
                Log ('Transfer interrupted (curl exit ' + $code + '); partial file retained.')
                Start-Sleep -Seconds 2
                continue
            }
            if (Try-Install $part) {
                Move-Item -LiteralPath $part -Destination $target -Force
                exit 0
            }
            Log 'Transfer finished but extraction/startup failed; not repeating this full download.'
            break
        }
    }
    throw 'Could not obtain/install mpv. Please send the new Setup-MPV.log for a precise diagnosis.'
} catch {
    Log ('FAILED: ' + $_.Exception.Message)
    Log 'Manual installation: download official ZIP into this folder, then run Setup-MPV.cmd again.'
    Log 'Official release: https://github.com/mpv-player/mpv/releases/tag/v0.41.0'
    exit 1
}
