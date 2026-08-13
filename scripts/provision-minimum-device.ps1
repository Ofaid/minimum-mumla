<#
.SYNOPSIS
    Provisions one supported Minimum radio from APK installation through reboot acceptance.

.DESCRIPTION
    This is the operator-facing one-shot workflow for known T99, T56 and RYKS hardware. It selects one
    authorized ADB target, verifies the hardware model, optionally builds the FOSS debug APK,
    installs the APK without clearing app data, runs the guarded model preparation, waits for the
    Device ID profile to become available from the portal, waits for Ready, reboots the radio, and
    waits for Ready again.

    The script opens the Minimum portal after displaying the six-character Device ID. Create that
    Device Profile once; no bearer token has to be copied to the radio. Unknown hardware is reported
    and rejected before any APK installation or provisioning change.

    Connect only one unit of a given model while using the reboot acceptance step. ADB transport IDs
    can change across reboot, so the script must be able to identify exactly one returning unit.
#>

[CmdletBinding()]
param(
    [string]$Serial = "",
    [int]$TransportId = 0,
    [ValidateRange(0, 65535)][int]$AdbPort = 0,
    [string]$ApkPath = "",
    [switch]$BuildApk,
    [Alias("DeviceId")]
    [string]$DeviceProfile = "",
    [string]$PortalUrl = "https://minimum.vra.or.th/",
    [switch]$SkipOpenPortal,
    [switch]$NonInteractive,
    [switch]$SkipZello,
    [switch]$SkipMinimumHome,
    [switch]$SkipLabWifi,
    [switch]$SkipLocation,
    [switch]$DisableDataRoaming,
    [switch]$RequestNetworkLocationConsent,
    [switch]$RefreshLabWifi,
    [string]$LabWifiSsid = "..@EmergencyTU",
    [string]$LabWifiCredentialPath = "",
    [ValidateRange(30, 900)][int]$ReadyTimeoutSeconds = 180,
    [ValidateRange(30, 900)][int]$BootTimeoutSeconds = 180,
    [switch]$SkipReboot
)

$ErrorActionPreference = "Stop"
$GuidedMode = $PSBoundParameters.Count -eq 0
$MinimumPackage = "se.lublin.mumla"
$MinimumActivity = "se.lublin.mumla/.radio.RadioShellActivity"
$ProvisionReceiver = "se.lublin.mumla/.radio.RadioProvisionReceiver"
$IdentityReportAction = "se.lublin.mumla.action.PROVISION_REPORT_IDENTITY"
$ProvisionStatusAction = "se.lublin.mumla.action.PROVISION_REPORT_STATUS"
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$BundledApkPath = Join-Path $RepositoryRoot "minimum-foss.apk"
$SourceBuildApkPath = Join-Path $RepositoryRoot "app\build\outputs\apk\foss\debug\mumla-foss-debug.apk"
$SourceBuildAvailable = Test-Path -LiteralPath (Join-Path $RepositoryRoot "gradlew.bat") -PathType Leaf
$DefaultApkPath = if (Test-Path -LiteralPath $BundledApkPath -PathType Leaf) {
    $BundledApkPath
} else {
    $SourceBuildApkPath
}
try {
    $adbPath = (Get-Command adb -ErrorAction Stop).Source
} catch {
    throw "ADB was not found. Install Android Platform Tools or add adb.exe to PATH, then double-click the launcher again."
}
$serverArgs = @()
$script:targetArgs = @()
$script:targetLabel = ""
$script:targetRecord = $null
$script:CellularReadinessWarning = $false

if ($DeviceProfile -and (($DeviceProfile -cnotmatch '^[A-Z0-9]{6}$') -or
        ($DeviceProfile -notmatch '[A-Z]') -or ($DeviceProfile -notmatch '\d'))) {
    throw "DeviceProfile must be six uppercase A-Z/0-9 characters with a letter and digit."
}
if ($SkipLocation -and $RequestNetworkLocationConsent) {
    throw "-SkipLocation and -RequestNetworkLocationConsent cannot be used together."
}

function Get-ListeningAdbPorts {
    $ports = @()
    try {
        $ports = @(Get-NetTCPConnection -State Listen -ErrorAction Stop |
            Where-Object { $_.LocalPort -in @(5037, 5041) } |
            Select-Object -ExpandProperty LocalPort -Unique)
    } catch {
        $netstat = & netstat.exe -ano -p TCP 2>$null
        foreach ($line in $netstat) {
            if ($line -match '^\s*TCP\s+\S+:(5037|5041)\s+\S+\s+LISTENING\s+') {
                $ports += [int]$Matches[1]
            }
        }
    }
    return @($ports | Sort-Object -Unique)
}

function Get-AuthorizedDeviceCount {
    param([Parameter(Mandatory)][int]$Port)
    $lines = & $adbPath -P $Port devices 2>$null
    if ($LASTEXITCODE -ne 0) {
        return 0
    }
    return @($lines | Where-Object { $_ -match '^[^\s]+\s+device(?:\s|$)' }).Count
}

function Select-AdbServerPort {
    if ($AdbPort -gt 0) {
        Write-Host "ADB port: $AdbPort (selected by advanced command-line option)."
        return $AdbPort
    }

    $listening = @(Get-ListeningAdbPorts)
    if ($listening.Count -eq 0) {
        Write-Host "ADB port: 5037 (standard port; the ADB server will start automatically)."
        return 5037
    }

    $withDevices = foreach ($port in $listening) {
        $count = Get-AuthorizedDeviceCount -Port $port
        if ($count -gt 0) {
            [pscustomobject]@{ Port = $port; Count = $count }
        }
    }
    $withDevices = @($withDevices)
    if ($withDevices.Count -eq 1) {
        Write-Host "ADB port: $($withDevices[0].Port) (auto-detected $($withDevices[0].Count) authorized device(s))."
        return $withDevices[0].Port
    }
    if ($listening.Count -eq 1) {
        Write-Host "ADB port: $($listening[0]) (existing ADB server; connect and authorize the device if it is not listed yet)."
        return $listening[0]
    }
    if ($NonInteractive) {
        throw "More than one ADB server is active. Pass -AdbPort 5037 or -AdbPort 5041."
    }

    Write-Host ""
    Write-Host "More than one ADB server is active. Choose the server that owns the device:"
    Write-Host "  [1] Port 5037 - Android standard (recommended for a new workstation)"
    Write-Host "  [2] Port 5041 - Existing Minimum lab setup"
    while ($true) {
        $choice = (Read-Host "Select 1 or 2").Trim()
        if ($choice -eq "1") { return 5037 }
        if ($choice -eq "2") { return 5041 }
        Write-Host "Please enter 1 or 2."
    }
}

function Show-GuidedSetupMenu {
    param([Parameter(Mandatory)][string]$Profile)

    Write-Host ""
    Write-Host "Recommended setup will:"
    if ($SourceBuildAvailable) {
        Write-Host "  - build and install the latest Minimum test APK"
    } elseif ($Profile -eq "RYKS") {
        $script:SkipLocation = $true
        Write-Host "Location tracking is not enabled for the RYKS profile."
    } else {
        Write-Host "  - install the signed Minimum APK included in this Release bundle"
    }
    Write-Host "  - configure lab Wi-Fi and managed Location"
    Write-Host "  - remove Zello for Android user 0"
    Write-Host "  - open the Portal to register the displayed Device ID"
    Write-Host "  - verify Ready, reboot, and verify Ready again"
    Write-Host ""
    while ($true) {
        $mode = (Read-Host "Press Enter to start, C for custom choices, or Q to quit").Trim()
        if (-not $mode -or $mode -ieq "C" -or $mode -ieq "Q") { break }
        Write-Host "Please press Enter, C or Q."
    }
    if ($mode -ieq "Q") {
        Write-Host "Cancelled. No APK was installed and no provisioning change was made."
        exit 0
    }
    $script:BuildApk = $SourceBuildAvailable
    if (-not $mode) {
        return
    }

    if ($SourceBuildAvailable) {
        $answer = (Read-Host "Build the latest APK? [Y/n]").Trim()
        if ($answer -ieq "N") { $script:BuildApk = $false }
    } else {
        Write-Host "Source build tools are not included; the bundled signed APK will be used."
    }

    $answer = (Read-Host "Configure/verify lab Wi-Fi? [Y/n]").Trim()
    if ($answer -ieq "N") { $script:SkipLabWifi = $true }

    if ($Profile -eq "T56") {
        Write-Host "Location: [1] GPS only (recommended)  [2] GPS + network consent  [3] Skip"
        while ($true) {
            $answer = (Read-Host "Select 1, 2 or 3 [1]").Trim()
            if (-not $answer -or $answer -eq "1") { break }
            if ($answer -eq "2") {
                $script:RequestNetworkLocationConsent = $true
                break
            }
            if ($answer -eq "3") {
                $script:SkipLocation = $true
                break
            }
            Write-Host "Please enter 1, 2 or 3."
        }
    } else {
        $answer = (Read-Host "Configure managed Location? [Y/n]").Trim()
        if ($answer -ieq "N") { $script:SkipLocation = $true }
    }

    $answer = (Read-Host "Remove Zello for Android user 0? [Y/n]").Trim()
    if ($answer -ieq "N") { $script:SkipZello = $true }

    $answer = (Read-Host "Reboot and verify unattended startup? [Y/n]").Trim()
    if ($answer -ieq "N") { $script:SkipReboot = $true }
}

function Get-ConnectedDevices {
    $lines = & $adbPath @serverArgs devices -l
    if ($LASTEXITCODE -ne 0) {
        throw "Could not query ADB on port $AdbPort."
    }
    $records = foreach ($line in $lines) {
        if ($line -match '^([^\s]+)\s+device(?:\s|$)') {
            $deviceSerial = $Matches[1]
            $transport = 0
            if ($line -match '\btransport_id:(\d+)\b') {
                $transport = [int]$Matches[1]
            }
            [pscustomobject]@{
                Serial = $deviceSerial
                TransportId = $transport
                Line = $line
            }
        }
    }
    return @($records)
}

function Get-RecordAdbArguments {
    param([Parameter(Mandatory)]$Record)
    if ($Record.TransportId -gt 0) {
        return $serverArgs + @("-t", "$($Record.TransportId)")
    }
    return $serverArgs + @("-s", $Record.Serial)
}

function Invoke-AdbForRecord {
    param(
        [Parameter(Mandatory)]$Record,
        [Parameter(Mandatory)][string[]]$Arguments
    )
    $recordArgs = Get-RecordAdbArguments -Record $Record
    $commandArgs = $recordArgs + $Arguments
    $output = & $adbPath @commandArgs
    if ($LASTEXITCODE -ne 0) {
        throw "ADB command failed for connected device '$($Record.Serial)' with exit code $LASTEXITCODE."
    }
    return $output
}

function Get-RecordProperty {
    param(
        [Parameter(Mandatory)]$Record,
        [Parameter(Mandatory)][string]$Name
    )
    return ((Invoke-AdbForRecord -Record $Record -Arguments @("shell", "getprop", $Name)) -join "").Trim()
}

function Add-RecordHardwareIdentity {
    param([Parameter(Mandatory)]$Record)
    $manufacturer = Get-RecordProperty -Record $Record -Name "ro.product.manufacturer"
    $model = Get-RecordProperty -Record $Record -Name "ro.product.model"
    $profile = if ($manufacturer -ieq "Youdotech" -and $model -ieq "QM011") {
        "T99"
    } elseif ($manufacturer -ieq "UNIPRO" -and $model -ieq "ZX") {
        "T56"
    } elseif ($manufacturer -ieq "ELINK" -and $model -ieq "ym_258") {
        "RYKS"
    } else {
        ""
    }
    $Record | Add-Member -NotePropertyName Manufacturer -NotePropertyValue $manufacturer -Force
    $Record | Add-Member -NotePropertyName Model -NotePropertyValue $model -Force
    $Record | Add-Member -NotePropertyName Profile -NotePropertyValue $profile -Force
    return $Record
}

function Select-InitialTarget {
    $devices = @(Get-ConnectedDevices)
    while ($devices.Count -eq 0 -and -not $NonInteractive) {
        Write-Host ""
        Write-Host "No authorized Android device was found on ADB port $AdbPort."
        Write-Host "1. Connect USB and unlock the radio."
        Write-Host "2. Enable USB debugging."
        Write-Host "3. Accept the 'Allow USB debugging' message on the radio."
        $choice = (Read-Host "Press Enter to check again, P to change ADB port, or Q to quit").Trim()
        if ($choice -ieq "Q") {
            Write-Host "Cancelled. No device change was made."
            exit 0
        }
        if ($choice -ieq "P") {
            Write-Host "  [1] Port 5037 - Android standard"
            Write-Host "  [2] Port 5041 - Minimum lab setup"
            $portChoice = (Read-Host "Select 1 or 2").Trim()
            if ($portChoice -eq "1") { $script:AdbPort = 5037 }
            if ($portChoice -eq "2") { $script:AdbPort = 5041 }
            $script:serverArgs = @("-P", "$AdbPort")
        }
        $devices = @(Get-ConnectedDevices)
    }
    if ($devices.Count -eq 0) {
        throw "No authorized Android device was found on ADB port $AdbPort."
    }
    if ($TransportId -gt 0) {
        $matches = @($devices | Where-Object { $_.TransportId -eq $TransportId })
        if ($matches.Count -ne 1) {
            throw "Authorized ADB transport id $TransportId was not found on port $AdbPort."
        }
        return Add-RecordHardwareIdentity -Record $matches[0]
    }
    if ($Serial) {
        $pattern = "^$([regex]::Escape($Serial))$"
        $matches = @($devices | Where-Object { $_.Serial -match $pattern })
        if ($matches.Count -ne 1) {
            throw "Expected exactly one authorized device with serial '$Serial'; use -TransportId when duplicated."
        }
        return Add-RecordHardwareIdentity -Record $matches[0]
    }

    $identifiedDevices = @($devices | ForEach-Object { Add-RecordHardwareIdentity -Record $_ })
    if ($identifiedDevices.Count -eq 1) {
        return $identifiedDevices[0]
    }
    if ($NonInteractive) {
        throw "More than one authorized device is connected; pass -Serial or -TransportId."
    }

    Write-Host ""
    Write-Host "More than one Android device is connected. Select the radio to provision:"
    for ($index = 0; $index -lt $identifiedDevices.Count; $index++) {
        $device = $identifiedDevices[$index]
        $support = if ($device.Profile) { $device.Profile } else { "unsupported - inventory only" }
        Write-Host ("  [{0}] {1}/{2} - {3}" -f ($index + 1),
            $device.Manufacturer, $device.Model, $support)
    }
    while ($true) {
        $choice = (Read-Host "Enter device number or Q to quit").Trim()
        if ($choice -ieq "Q") {
            Write-Host "Cancelled. No device change was made."
            exit 0
        }
        $number = 0
        if ([int]::TryParse($choice, [ref]$number) -and
                $number -ge 1 -and $number -le $identifiedDevices.Count) {
            return $identifiedDevices[$number - 1]
        }
        Write-Host "Please enter a number shown in the list."
    }
}

function Set-Target {
    param([Parameter(Mandatory)]$Record)
    $script:targetRecord = $Record
    $script:targetArgs = Get-RecordAdbArguments -Record $Record
    $script:targetLabel = if ($Record.TransportId -gt 0) {
        "transport id $($Record.TransportId)"
    } else {
        "ADB serial $($Record.Serial)"
    }
}

function Invoke-TargetAdb {
    param([Parameter(Mandatory)][string[]]$Arguments)
    $commandArgs = $script:targetArgs + $Arguments
    $output = & $adbPath @commandArgs
    if ($LASTEXITCODE -ne 0) {
        throw "ADB command failed for $script:targetLabel with exit code $LASTEXITCODE."
    }
    return $output
}

function Get-MinimumDeviceId {
    $output = Invoke-TargetAdb -Arguments @(
        "shell", "am", "broadcast", "-W",
        "-a", $IdentityReportAction,
        "-n", $ProvisionReceiver
    )
    $match = [regex]::Match(($output -join "`n"), 'data="?([A-Z0-9]{6})"?')
    if (-not $match.Success) {
        throw "Minimum did not return a six-character Device ID."
    }
    return $match.Groups[1].Value
}

function Get-MinimumProvisioningStatus {
    $output = Invoke-TargetAdb -Arguments @(
        "shell", "am", "broadcast", "-W",
        "-a", $ProvisionStatusAction,
        "-n", $ProvisionReceiver
    )
    $text = $output -join "`n"
    $match = [regex]::Match($text,
        'data="?deviceId=([A-Z0-9]{6});activeDeviceId=([A-Z0-9*]{1,6});configVersion=(-?\d+);pending=(true|false);lastSuccessMs=(\d+)"?')
    if (-not $match.Success) {
        return $null
    }
    return [pscustomobject]@{
        DeviceId = $match.Groups[1].Value
        ActiveDeviceId = $match.Groups[2].Value
        ConfigVersion = [int]$match.Groups[3].Value
        Pending = $match.Groups[4].Value -eq "true"
        LastSuccessMs = [long]$match.Groups[5].Value
    }
}

function Build-MinimumApk {
    $buildRoot = $RepositoryRoot
    $temporaryJunction = ""
    try {
        if ($RepositoryRoot -match '\s') {
            $temporaryJunction = Join-Path ([IO.Path]::GetTempPath()) (
                "minimum-build-{0}" -f [guid]::NewGuid().ToString("N"))
            New-Item -ItemType Junction -Path $temporaryJunction `
                -Target $RepositoryRoot -ErrorAction Stop | Out-Null
            $buildRoot = $temporaryJunction
            Write-Host "Using a temporary path without spaces for the Android NDK build."
        }
        $gradleWrapper = Join-Path $buildRoot "gradlew.bat"
        if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
            throw "Gradle wrapper is missing: $gradleWrapper"
        }
        Write-Host "Building the FOSS debug APK from the current source..."
        Push-Location $buildRoot
        try {
            & $gradleWrapper :app:assembleFossDebug --no-daemon
            if ($LASTEXITCODE -ne 0) {
                throw "FOSS debug APK build failed with exit code $LASTEXITCODE."
            }
        } finally {
            Pop-Location
        }
    } finally {
        if ($temporaryJunction -and (Test-Path -LiteralPath $temporaryJunction)) {
            $junction = Get-Item -LiteralPath $temporaryJunction -Force
            if ($junction.LinkType -ne "Junction") {
                throw "Refusing to remove unexpected temporary build path: $temporaryJunction"
            }
            $junctionTarget = (Resolve-Path -LiteralPath @($junction.Target)[0]).Path
            if ($junctionTarget -ne $RepositoryRoot) {
                throw "Refusing to remove temporary junction with unexpected target: $junctionTarget"
            }
            [IO.Directory]::Delete($temporaryJunction)
        }
    }
}

function Test-TargetLabWifiConnected {
    $connectivity = (Invoke-TargetAdb -Arguments @("shell", "dumpsys", "connectivity")) -join "`n"
    $escapedSsid = [regex]::Escape($LabWifiSsid)
    return $connectivity -match
        "(?s)type:\s*WIFI.*?state:\s*CONNECTED/CONNECTED.*?extra:\s*`"$escapedSsid`""
}

function Ensure-LabWifiCredential {
    param([Parameter(Mandatory)][string]$Profile)
    if ($SkipLabWifi -or (Test-TargetLabWifiConnected)) {
        return
    }
    if (-not $LabWifiCredentialPath) {
        $credentialName = "{0}-lab-wifi.credential.xml" -f $Profile.ToLowerInvariant()
        $script:LabWifiCredentialPath = Join-Path $PSScriptRoot ".secrets\$credentialName"
    }
    if (Test-Path -LiteralPath $LabWifiCredentialPath) {
        return
    }
    if ($NonInteractive) {
        throw "Lab Wi-Fi is not connected. Create the DPAPI credential or pass -SkipLabWifi."
    }

    Write-Host "Lab Wi-Fi '$LabWifiSsid' is not connected. Windows will request its password."
    $credential = Get-Credential -UserName $LabWifiSsid `
        -Message "Enter the Minimum lab Wi-Fi password"
    if (-not $credential) {
        throw "Lab Wi-Fi credential entry was cancelled."
    }
    $credentialDirectory = Split-Path -Parent $LabWifiCredentialPath
    New-Item -ItemType Directory -Path $credentialDirectory -Force | Out-Null
    $credential | Export-Clixml -LiteralPath $LabWifiCredentialPath
    $credential = $null
    Write-Host "Lab Wi-Fi credential saved with Windows DPAPI for this account."
}

function Invoke-ModelPreparation {
    param([Parameter(Mandatory)][string]$Profile)
    $prepareScript = switch ($Profile) {
        "T56" { Join-Path $PSScriptRoot "prepare-t56.ps1" }
        "RYKS" { Join-Path $PSScriptRoot "prepare-ryks.ps1" }
        default { Join-Path $PSScriptRoot "prepare-t99.ps1" }
    }
    $arguments = @(
        "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $prepareScript,
        "-AdbPort", "$AdbPort", "-Force", "-LabWifiSsid", $LabWifiSsid
    )
    if ($script:targetRecord.TransportId -gt 0) {
        $arguments += @("-TransportId", "$($script:targetRecord.TransportId)")
    } else {
        $arguments += @("-Serial", $script:targetRecord.Serial)
    }
    if ($DeviceProfile) { $arguments += @("-DeviceProfile", $DeviceProfile) }
    if ($SkipZello) { $arguments += "-SkipZello" }
    if ($SkipMinimumHome) { $arguments += "-SkipMinimumHome" }
    if ($SkipLabWifi) { $arguments += "-SkipLabWifi" }
    if ($SkipLocation) { $arguments += "-SkipLocation" }
    if ($DisableDataRoaming) { $arguments += "-DisableDataRoaming" }
    if ($RequestNetworkLocationConsent) { $arguments += "-RequestNetworkLocationConsent" }
    if ($RefreshLabWifi) { $arguments += "-RefreshLabWifi" }
    if ($LabWifiCredentialPath) {
        $arguments += @("-LabWifiCredentialPath", $LabWifiCredentialPath)
    }

    Write-Host "Running guarded $Profile preparation..."
    & powershell.exe @arguments
    if ($LASTEXITCODE -eq 2 -and $Profile -eq "T56") {
        $script:CellularReadinessWarning = $true
        Write-Warning "T56 preparation completed with a documented cellular-readiness warning."
    } elseif ($LASTEXITCODE -ne 0) {
        throw "$Profile preparation failed with exit code $LASTEXITCODE."
    }
}

function Ensure-DisplayAwake {
    $powerState = (Invoke-TargetAdb -Arguments @("shell", "dumpsys", "power")) -join "`n"
    if ($powerState -notmatch 'Display Power: state=OFF' -and
            $powerState -notmatch 'mWakefulness=(Asleep|Dozing)') {
        return
    }
    Invoke-TargetAdb -Arguments @("shell", "input", "keyevent", "224") | Out-Null
    Start-Sleep -Seconds 1
    $powerState = (Invoke-TargetAdb -Arguments @("shell", "dumpsys", "power")) -join "`n"
    if ($powerState -match 'Display Power: state=OFF' -or
            $powerState -match 'mWakefulness=(Asleep|Dozing)') {
        Invoke-TargetAdb -Arguments @("shell", "input", "keyevent", "26") | Out-Null
        Start-Sleep -Seconds 2
    }
}

function Wait-MinimumReady {
    param(
        [Parameter(Mandatory)][string]$Phase,
        [Parameter(Mandatory)][string]$ExpectedDeviceId,
        [Parameter(Mandatory)][int]$TimeoutSeconds
    )
    $remoteUi = "/sdcard/minimum-provision-ready-$PID.xml"
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        Ensure-DisplayAwake
        try {
            Invoke-TargetAdb -Arguments @("shell", "uiautomator", "dump", $remoteUi) | Out-Null
            $ui = (Invoke-TargetAdb -Arguments @("shell", "cat", $remoteUi)) -join "`n"
            if ($ui -match 'content-desc="minimum-state-ready"') {
                $status = Get-MinimumProvisioningStatus
                if ($status -and $status.DeviceId -eq $ExpectedDeviceId -and
                        $status.ActiveDeviceId -eq $ExpectedDeviceId -and
                        -not $status.Pending -and $status.ConfigVersion -gt 0 -and
                        $status.LastSuccessMs -gt 0) {
                    Write-Host "Checkpoint: Minimum reached Ready $Phase with managed config v$($status.ConfigVersion)."
                    return
                }
            }
        } finally {
            $cleanupArgs = $script:targetArgs + @("shell", "rm", "-f", $remoteUi)
            & $adbPath @cleanupArgs 1>$null 2>$null
        }
        Start-Sleep -Seconds 5
    }
    throw "Minimum did not reach Ready $Phase within $TimeoutSeconds seconds. Check network, Portal registration and device config."
}

function Wait-ForReturningTarget {
    param(
        [Parameter(Mandatory)][string]$Manufacturer,
        [Parameter(Mandatory)][string]$Model,
        [Parameter(Mandatory)][string]$OriginalSerial,
        [Parameter(Mandatory)][int]$TimeoutSeconds
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 2
        $devices = @(Get-ConnectedDevices)
        $serialMatches = @($devices | Where-Object { $_.Serial -eq $OriginalSerial })
        if ($serialMatches.Count -eq 1) {
            $candidate = Add-RecordHardwareIdentity -Record $serialMatches[0]
            if ($candidate.Manufacturer -ieq $Manufacturer -and $candidate.Model -ieq $Model) {
                return $candidate
            }
        }
        $modelMatches = foreach ($device in $devices) {
            $candidate = Add-RecordHardwareIdentity -Record $device
            if ($candidate.Manufacturer -ieq $Manufacturer -and $candidate.Model -ieq $Model) {
                $candidate
            }
        }
        $modelMatches = @($modelMatches)
        if ($modelMatches.Count -eq 1) {
            return $modelMatches[0]
        }
    }
    throw "Could not identify exactly one returning $Manufacturer/$Model after reboot. Connect only one unit of this model and rerun Ready verification."
}

function Wait-AndroidBootCompleted {
    param([Parameter(Mandatory)][int]$TimeoutSeconds)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $completed = ((Invoke-TargetAdb -Arguments @(
            "shell", "getprop", "sys.boot_completed")) -join "").Trim()
        if ($completed -eq "1") {
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "Android did not finish booting within $TimeoutSeconds seconds."
}

$Host.UI.RawUI.WindowTitle = "Minimum One-Shot Provisioning"
if ($GuidedMode) {
    Write-Host "============================================================"
    Write-Host " Minimum radio - one-shot setup"
    Write-Host " No command-line parameters are required in guided mode."
    Write-Host "============================================================"
}
$AdbPort = Select-AdbServerPort
$serverArgs = @("-P", "$AdbPort")
$target = Select-InitialTarget
Set-Target -Record $target
Write-Host "Target: $($target.Manufacturer)/$($target.Model) via $script:targetLabel"
if (-not $target.Profile) {
    $apiLevel = Get-RecordProperty -Record $target -Name "ro.build.version.sdk"
    $buildId = Get-RecordProperty -Record $target -Name "ro.build.display.id"
    Write-Host "Unsupported hardware inventory: manufacturer='$($target.Manufacturer)' model='$($target.Model)' API='$apiLevel' build='$buildId'."
    throw "Unknown hardware is not provisioned automatically. Complete physical key/PTT commissioning and add a guarded model profile first."
}
if ($RequestNetworkLocationConsent -and $target.Profile -ne "T56") {
    throw "-RequestNetworkLocationConsent is supported only for T56."
}
if ($GuidedMode) {
    Show-GuidedSetupMenu -Profile $target.Profile
}

if (-not $ApkPath) { $ApkPath = $DefaultApkPath }
if ($BuildApk -or -not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) {
    Build-MinimumApk
}
$resolvedApkPath = (Resolve-Path -LiteralPath $ApkPath).Path
if ($target.Profile -eq "RYKS") {
    # ELINK's PackageManager accepts third-party APKs only when this documented build-policy
    # property is 1. The property is absent on factory ym_258 images and resets on reboot.
    $installPolicy = Get-RecordProperty -Record $target -Name "ro.build.install"
    if ($installPolicy -ne "1") {
        Write-Host "Enabling the RYKS OEM APK-install policy for this boot..."
        Invoke-TargetAdb -Arguments @("shell", "setprop", "ro.build.install", "1") | Out-Null
        $installPolicy = Get-RecordProperty -Record $target -Name "ro.build.install"
        if ($installPolicy -ne "1") {
            throw "RYKS firmware did not enable ro.build.install=1; APK installation remains blocked."
        }
    }
}

function Install-MinimumApk {
    param([Parameter(Mandatory)][string]$Path)

    # Capture only the exit/result needed to classify installation failures. In particular, do not
    # invoke `pm clear` or uninstall here: a failed signature upgrade must preserve app data.
    $installArgs = $script:targetArgs + @("install", "-r", $Path)
    # Windows PowerShell 5.1 can turn native stderr merged with 2>&1 into a
    # terminating ErrorRecord when EAP is Stop. Temporarily keep native output
    # non-terminating, then stringify ErrorRecord entries before classification.
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = @(& $adbPath @installArgs 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $resultText = (($output | ForEach-Object {
        if ($_ -is [System.Management.Automation.ErrorRecord]) {
            $_.ToString()
        } else {
            [string]$_
        }
    }) -join "`n").Trim()
    if ($exitCode -ne 0 -or $resultText -notmatch '(?im)^Success\s*$') {
        if ($resultText -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
            throw "APK installation failed: INSTALL_FAILED_UPDATE_INCOMPATIBLE. The installed app and this APK use different signing keys. No app data was cleared; preserve the existing identity/configuration and obtain explicit approval before uninstalling the old build."
        }
        throw "APK installation failed (adb exit code $exitCode). No app data was cleared; correct the installation issue and retry."
    }
}
Write-Host "Installing Minimum APK without clearing app data..."
Install-MinimumApk -Path $resolvedApkPath
$installed = Invoke-TargetAdb -Arguments @("shell", "pm", "path", $MinimumPackage)
if (-not $installed) {
    throw "Minimum package verification failed after APK installation."
}

Ensure-LabWifiCredential -Profile $target.Profile
Invoke-ModelPreparation -Profile $target.Profile
$deviceId = Get-MinimumDeviceId
Write-Host "Minimum Device ID: $deviceId"

if (-not $NonInteractive) {
    $portalModel = $target.Profile.ToLowerInvariant()
    Write-Host "Register Device ID $deviceId as model '$portalModel' in the Minimum Portal. No device token is required."
    if (-not $SkipOpenPortal) {
        try {
            Start-Process $PortalUrl
        } catch {
            Write-Warning "Could not open the Portal automatically. Open $PortalUrl manually."
        }
    }
}

Invoke-TargetAdb -Arguments @("shell", "am", "start", "-n", $MinimumActivity) | Out-Null
Wait-MinimumReady -Phase "before reboot" -ExpectedDeviceId $deviceId `
    -TimeoutSeconds $ReadyTimeoutSeconds

if ($SkipReboot) {
    Write-Host "INCOMPLETE: reboot and same-ID Ready verification were skipped; final acceptance was not completed."
    exit 2
}

$originalSerial = $target.Serial
$manufacturer = $target.Manufacturer
$model = $target.Model
Write-Host "Rebooting for unattended startup acceptance..."
Invoke-TargetAdb -Arguments @("reboot") | Out-Null
$returningTarget = Wait-ForReturningTarget -Manufacturer $manufacturer -Model $model `
    -OriginalSerial $originalSerial -TimeoutSeconds $BootTimeoutSeconds
Set-Target -Record $returningTarget
Wait-AndroidBootCompleted -TimeoutSeconds $BootTimeoutSeconds
if ($target.Profile -eq "T56") {
    $cellularScript = Join-Path $PSScriptRoot "manage-cellular.ps1"
    $cellularArguments = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
        $cellularScript, "-AdbPort", "$AdbPort")
    if ($returningTarget.TransportId -gt 0) {
        $cellularArguments += @("-TransportId", "$($returningTarget.TransportId)")
    } else {
        $cellularArguments += @("-Serial", $returningTarget.Serial)
    }
    if ($DisableDataRoaming) { $cellularArguments += "-DisableDataRoaming" }
    $cellularArguments += "-VerifyOnly"
    Write-Host "Verifying T56 cellular-policy persistence after reboot without rewriting settings..."
    & powershell.exe @cellularArguments
    $cellularExit = $LASTEXITCODE
    if ($cellularExit -eq 2) {
        $script:CellularReadinessWarning = $true
        Write-Warning "Post-reboot cellular verification remains WARN; no persistence PASS is claimed."
    } elseif ($cellularExit -ne 0) {
        throw "Post-reboot T56 cellular verification failed with exit code $cellularExit."
    }
}
Wait-MinimumReady -Phase "after reboot" -ExpectedDeviceId $deviceId `
    -TimeoutSeconds $ReadyTimeoutSeconds

if ($script:CellularReadinessWarning) {
    Write-Warning "WARN: $($target.Profile) Device ID $deviceId is provisioned and Ready, but cellular readiness is not fully accepted."
    exit 2
}
Write-Host "PASS: $($target.Profile) Device ID $deviceId is provisioned and Ready."
