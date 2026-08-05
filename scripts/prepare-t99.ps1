<#[
.SYNOPSIS
    Prepares a supported T99/T88 for Minimum radio-client use.

.DESCRIPTION
    This is the canonical provisioning script. It reports the different serial identities,
    removes Zello for user 0, launches Minimum once so its app Device ID can be created, verifies
    that identity, provisions the lab Wi-Fi through a temporary helper that is removed immediately,
    installs a Minimum recovery shortcut into the OEM Launcher3 workspace, verifies that the system
    HOME no longer displays a chooser, and opens the one-icon-per-page radio dashboard. It
    intentionally does not attempt to rewrite the USB/ADB serial:
    the T99 exposes that value through a root-owned USB gadget node and this non-root device
    cannot safely change it.

    Use -TransportId when several identical devices expose the same ADB serial.
#>

[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$Serial = "12344321",
    [int]$TransportId = 0,
    [int]$AdbPort = 5041,
    [switch]$Force,
    [switch]$SkipZello,
    [switch]$SkipMinimumHome,
    [switch]$SkipLabWifi,
    [switch]$RefreshLabWifi,
    [switch]$ReportOnly,
    [Alias("DeviceId")]
    [string]$DeviceProfile = "",
    [string]$RadioConfigPath = "",
    [string]$LabWifiSsid = "..@EmergencyTU",
    [System.Management.Automation.PSCredential]$LabWifiCredential,
    [string]$LabWifiCredentialPath = ""
)

$ErrorActionPreference = "Stop"
$PackageName = "com.loudtalks"
$MinimumPackage = "se.lublin.mumla"
$MinimumActivity = "se.lublin.mumla/.radio.RadioShellActivity"
$MinimumHomeActivity = "se.lublin.mumla/.radio.MinimumHomeActivity"
$MinimumHomeComponent = "se.lublin.mumla/se.lublin.mumla.radio.MinimumHomeActivity"
$MinimumRadioComponent = "se.lublin.mumla/se.lublin.mumla.radio.RadioShellActivity"
$ShortcutProvisionReceiver = "se.lublin.mumla/.radio.RadioProvisionReceiver"
$ShortcutProvisionAction = "se.lublin.mumla.action.PROVISION_LAUNCHER_SHORTCUT"
$DeviceProfileProvisionAction = "se.lublin.mumla.action.PROVISION_DEVICE_PROFILE"
$WifiHelperPackage = "dev.minimum.wifiprovisioner"
$WifiHelperActivity = "dev.minimum.wifiprovisioner/.WifiProvisionActivity"
if (-not $LabWifiCredentialPath) {
    $LabWifiCredentialPath = Join-Path $PSScriptRoot ".secrets\t99-lab-wifi.credential.xml"
}

$adbCommand = Get-Command adb -ErrorAction Stop
$adbPath = $adbCommand.Source
$serverArgs = @("-P", "$AdbPort")
$deviceLines = & $adbPath @serverArgs devices -l
if ($LASTEXITCODE -ne 0) {
    throw "Could not query the ADB server on port $AdbPort"
}

if ($TransportId -gt 0) {
    $deviceLine = $deviceLines | Where-Object {
        $_ -match "\btransport_id:$TransportId\b\s*$" -or $_ -match "\btransport_id:$TransportId\b\s"
    }
    if (-not $deviceLine) {
        throw "No authorized device with transport id $TransportId is connected on port $AdbPort"
    }
    $targetArgs = $serverArgs + @("-t", "$TransportId")
    $targetLabel = "transport id $TransportId"
} else {
    $serialPattern = "^$([regex]::Escape($Serial))\s+device\s+"
    $matchingLines = @($deviceLines | Where-Object { $_ -match $serialPattern })
    if ($matchingLines.Count -eq 0) {
        throw "Authorized ADB device '$Serial' is not connected on port $AdbPort"
    }
    if ($matchingLines.Count -gt 1) {
        throw "ADB serial '$Serial' is duplicated. Re-run with -TransportId from 'adb devices -l'."
    }
    $targetArgs = $serverArgs + @("-s", $Serial)
    $targetLabel = "ADB serial $Serial"
}

function Invoke-TargetAdb {
    param([Parameter(Mandatory)][string[]]$Arguments)
    $commandArgs = $targetArgs + $Arguments
    & $adbPath @commandArgs
    if ($LASTEXITCODE -ne 0) {
        throw "ADB command failed for $targetLabel with exit code $LASTEXITCODE"
    }
}

function Get-TargetProperty {
    param([Parameter(Mandatory)][string]$Name)
    $value = Invoke-TargetAdb -Arguments @("shell", "getprop", $Name)
    return ($value -join "").Trim()
}

function Get-TargetFile {
    param([Parameter(Mandatory)][string]$Path)
    $value = Invoke-TargetAdb -Arguments @("shell", "cat", $Path)
    return ($value -join "`n")
}

function Get-MinimumPreferences {
    $value = Invoke-TargetAdb -Arguments @(
        "shell", "run-as", $MinimumPackage, "cat",
        "shared_prefs/se.lublin.mumla_preferences.xml"
    )
    return ($value -join "`n")
}

function Test-MinimumHomeFocused {
    $windowState = Invoke-TargetAdb -Arguments @("shell", "dumpsys", "window", "windows")
    return ($windowState -join "`n").Contains($MinimumHomeComponent)
}

function Test-HomeChooserFocused {
    $windowState = Invoke-TargetAdb -Arguments @("shell", "dumpsys", "window", "windows")
    return ($windowState -join "`n").Contains("android/com.android.internal.app.ResolverActivity")
}

function Test-MinimumRadioFocused {
    $windowState = Invoke-TargetAdb -Arguments @("shell", "dumpsys", "window", "windows")
    return ($windowState -join "`n").Contains($MinimumRadioComponent)
}

function Test-LabWifiConnected {
    param([Parameter(Mandatory)][string]$Ssid)
    $connectivity = Invoke-TargetAdb -Arguments @("shell", "dumpsys", "connectivity")
    $escapedSsid = [regex]::Escape($Ssid)
    return (($connectivity -join "`n") -match
            "(?s)type:\s*WIFI.*?state:\s*CONNECTED/CONNECTED.*?extra:\s*`"$escapedSsid`"")
}

function Get-LabWifiCredential {
    if ($LabWifiCredential) {
        return $LabWifiCredential
    }
    if (Test-Path -LiteralPath $LabWifiCredentialPath) {
        $stored = Import-Clixml -LiteralPath $LabWifiCredentialPath
        if ($stored -isnot [System.Management.Automation.PSCredential]) {
            throw "Lab Wi-Fi credential file is not a Windows PSCredential: $LabWifiCredentialPath"
        }
        return $stored
    }
    throw "Lab Wi-Fi is not connected and no credential was supplied. Pass -LabWifiCredential or create the ignored DPAPI credential at $LabWifiCredentialPath."
}

function Invoke-LabWifiProvisioning {
    param(
        [Parameter(Mandatory)][string]$Ssid,
        [Parameter(Mandatory)][System.Management.Automation.PSCredential]$Credential
    )

    $helperRoot = Join-Path $PSScriptRoot "..\tools\t99-wifi-provisioner"
    $gradleWrapper = Join-Path $PSScriptRoot "..\gradlew.bat"
    $helperApk = Join-Path $helperRoot "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path -LiteralPath $gradleWrapper)) {
        throw "Gradle wrapper is missing; cannot build the temporary Wi-Fi provisioner."
    }

    & $gradleWrapper -p $helperRoot :app:assembleDebug
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $helperApk)) {
        throw "Temporary Wi-Fi provisioner build failed."
    }

    $temporaryDirectory = Join-Path ([IO.Path]::GetTempPath()) (
            "minimum-wifi-" + [guid]::NewGuid().ToString("N"))
    $requestPath = Join-Path $temporaryDirectory "request.json"
    $remoteRequest = "/data/local/tmp/minimum-wifi-$PID.json"
    $helperInstalled = $false
    New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null
    try {
        $plainPassword = $Credential.GetNetworkCredential().Password
        $requestJson = [ordered]@{ ssid = $Ssid; psk = $plainPassword } |
            ConvertTo-Json -Compress
        [IO.File]::WriteAllText(
                $requestPath,
                $requestJson,
                (New-Object Text.UTF8Encoding($false)))
        $plainPassword = $null
        $requestJson = $null

        Invoke-TargetAdb -Arguments @("install", "-r", $helperApk) | Out-Null
        $helperInstalled = $true
        Invoke-TargetAdb -Arguments @("push", $requestPath, $remoteRequest) | Out-Null
        Invoke-TargetAdb -Arguments @(
            "shell", "run-as", $WifiHelperPackage, "mkdir", "-p", "files"
        ) | Out-Null
        Invoke-TargetAdb -Arguments @(
            "shell", "run-as", $WifiHelperPackage, "cp", $remoteRequest, "files/request.json"
        ) | Out-Null
        Invoke-TargetAdb -Arguments @(
            "shell", "run-as", $WifiHelperPackage, "chmod", "600", "files/request.json"
        ) | Out-Null
        Invoke-TargetAdb -Arguments @("shell", "rm", "-f", $remoteRequest) | Out-Null
        Invoke-TargetAdb -Arguments @("shell", "am", "start", "-n", $WifiHelperActivity) |
            Out-Null

        $resultText = ""
        $deadline = (Get-Date).AddSeconds(25)
        while ((Get-Date) -lt $deadline) {
            Start-Sleep -Milliseconds 500
            $resultArgs = $targetArgs + @(
                "shell", "run-as", $WifiHelperPackage, "cat", "files/result.json"
            )
            $resultOutput = & $adbPath @resultArgs 2>$null
            if ($LASTEXITCODE -eq 0 -and $resultOutput) {
                $resultText = ($resultOutput -join "")
                break
            }
        }
        if (-not $resultText) {
            throw "Temporary Wi-Fi provisioner did not return a result."
        }
        $result = $resultText | ConvertFrom-Json
        if (-not $result.ok) {
            throw "Lab Wi-Fi provisioning failed: $($result.error)"
        }
        Write-Host "Lab Wi-Fi profile saved for SSID '$Ssid'; credential value was not displayed."
    } finally {
        & $adbPath @($targetArgs + @("shell", "rm", "-f", $remoteRequest)) 1>$null 2>$null
        if ($helperInstalled) {
            & $adbPath @($targetArgs + @("uninstall", $WifiHelperPackage)) 1>$null 2>$null
        }
        if (Test-Path -LiteralPath $temporaryDirectory) {
            Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force
        }
    }

    $deadline = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $deadline -and -not (Test-LabWifiConnected -Ssid $Ssid)) {
        Start-Sleep -Seconds 1
    }
    if (Test-LabWifiConnected -Ssid $Ssid) {
        Write-Host "Lab Wi-Fi connected and available for automatic reconnection: '$Ssid'."
    } else {
        Write-Warning "Lab Wi-Fi profile is saved but '$Ssid' was not reachable within 30 seconds."
    }
}

Write-Host "Target: T99/T88 / $targetLabel"
Write-Host "Manufacturer/model: $(Get-TargetProperty -Name ro.product.manufacturer) / $(Get-TargetProperty -Name ro.product.model)"
$adbSerial = (& $adbPath @targetArgs get-serialno) -join ""
$systemSerial = Get-TargetProperty -Name ro.serialno
$bootSerial = Get-TargetProperty -Name ro.boot.serialno
$usbSerial = (Get-TargetFile -Path /sys/class/android_usb/android0/iSerial).Trim()
Write-Host "ADB serial: $adbSerial"
Write-Host "Android ro.serialno: $systemSerial"
Write-Host "Android ro.boot.serialno: $bootSerial"
Write-Host "USB gadget iSerial: $usbSerial"
Write-Host "Serial rewrite: NOT ATTEMPTED (requires root/firmware-level provisioning)"

$packagePath = Invoke-TargetAdb -Arguments @("shell", "pm", "path", $PackageName)
if (-not $SkipZello -and $packagePath) {
    Write-Host "Zello system path: $($packagePath -join ' ')"
}

$minimumInstalled = Invoke-TargetAdb -Arguments @("shell", "pm", "list", "packages", $MinimumPackage)
if (-not $minimumInstalled) {
    throw "Minimum ($MinimumPackage) is not installed; install the APK before provisioning."
}

$labWifiConnected = Test-LabWifiConnected -Ssid $LabWifiSsid
if ($labWifiConnected -and -not $RefreshLabWifi) {
    Write-Host "Lab Wi-Fi is connected: '$LabWifiSsid'."
} elseif ($ReportOnly) {
    Write-Host "Lab Wi-Fi connected=$labWifiConnected refresh=$RefreshLabWifi (report-only): '$LabWifiSsid'."
} elseif ($SkipLabWifi) {
    Write-Host "Lab Wi-Fi provisioning skipped by request."
} elseif ($PSCmdlet.ShouldProcess("$targetLabel / $LabWifiSsid", "save and enable lab Wi-Fi")) {
    if (-not $WhatIfPreference) {
        $credential = Get-LabWifiCredential
        Invoke-LabWifiProvisioning -Ssid $LabWifiSsid -Credential $credential
    }
}

if (-not $ReportOnly -and -not $WhatIfPreference) {
    # Managed radio provisioning should not leave a tiny-screen Android permission dialog for the
    # operator. This only grants a permission already declared by Minimum.
    $apiLevelOutput = @(Invoke-TargetAdb -Arguments @(
        "shell", "getprop", "ro.build.version.sdk"
    ))
    $apiLevelMatch = [regex]::Match(($apiLevelOutput -join " "), '\b\d+\b')
    if (-not $apiLevelMatch.Success) {
        throw "Could not determine the Android API level for microphone provisioning."
    }
    $androidApiLevel = [int]$apiLevelMatch.Value
    if ($androidApiLevel -ge 23) {
        Invoke-TargetAdb -Arguments @(
            "shell", "pm", "grant", $MinimumPackage, "android.permission.RECORD_AUDIO"
        ) | Out-Null
        Write-Host "Minimum microphone permission granted for managed PTT use."
    } else {
        Write-Host "Minimum microphone permission is install-time on Android API $androidApiLevel."
    }
}

if (-not $ReportOnly -and -not $SkipZello -and $packagePath) {
    if (-not $Force -and -not $WhatIfPreference) {
        $answer = Read-Host "Type PREPARE to remove Zello for user 0 and initialize Minimum"
        if ($answer -cne "PREPARE") {
            Write-Host "Cancelled. No device changes were made."
            exit 0
        }
    }
    if ($PSCmdlet.ShouldProcess("$targetLabel / $PackageName", "pm uninstall --user 0")) {
        Invoke-TargetAdb -Arguments @("shell", "pm", "uninstall", "--user", "0", $PackageName)
        $visiblePackage = Invoke-TargetAdb -Arguments @("shell", "pm", "list", "packages", $PackageName)
        if ($visiblePackage) {
            throw "Zello still appears in the user package list after uninstall"
        }
        Write-Host "Zello removed from user 0. The read-only system APK may remain."
    }
} elseif ($SkipZello) {
    Write-Host "Zello step skipped by request."
} elseif (-not $packagePath) {
    Write-Host "Zello is already absent from this device user."
}

if (-not $ReportOnly -and -not $WhatIfPreference) {
    Invoke-TargetAdb -Arguments @("shell", "am", "start", "-n", $MinimumActivity) | Out-Null
    Start-Sleep -Seconds 2
}

$preferences = Get-MinimumPreferences
$identityMatch = [regex]::Match($preferences, '<string name="radio_device_id">([A-Z0-9]{6})</string>')
if ($DeviceProfile) {
    if (($DeviceProfile -cnotmatch '^[A-Z0-9]{6}$') -or
            ($DeviceProfile -notmatch '[A-Z]') -or
            ($DeviceProfile -notmatch '\d')) {
        throw "DeviceProfile must be six uppercase A-Z/0-9 characters with a letter and digit."
    }
    if (-not $identityMatch.Success) {
        throw "Minimum Device ID is not initialized; launch Minimum once before assigning a profile."
    }
    if (($identityMatch.Groups[1].Value -cne $DeviceProfile) -and
            (-not $ReportOnly) -and
            $PSCmdlet.ShouldProcess(
                "$targetLabel / Minimum identity",
                "assign config profile/deviceId $DeviceProfile")) {
        Invoke-TargetAdb -Arguments @(
            "shell", "am", "force-stop", $MinimumPackage
        ) | Out-Null
        Invoke-TargetAdb -Arguments @(
            "shell", "am", "broadcast",
            "-a", $DeviceProfileProvisionAction,
            "-n", $ShortcutProvisionReceiver,
            "--es", "deviceProfile", $DeviceProfile
        ) | Out-Null
        $preferences = Get-MinimumPreferences
        $identityMatch = [regex]::Match(
                $preferences,
                '<string name="radio_device_id">([A-Z0-9]{6})</string>')
        if ((-not $identityMatch.Success) -or
                ($identityMatch.Groups[1].Value -cne $DeviceProfile)) {
            throw "Minimum config profile/deviceId assignment did not persist."
        }
        Write-Host "Minimum config profile assigned: $DeviceProfile"
    } elseif ($ReportOnly -and $identityMatch.Groups[1].Value -cne $DeviceProfile) {
        Write-Host "Requested config profile: $DeviceProfile (report-only; not applied)"
    }
}
if ($identityMatch.Success) {
    $deviceId = $identityMatch.Groups[1].Value
    if ($deviceId -notmatch '[A-Z]' -or $deviceId -notmatch '\d') {
        throw "Minimum Device ID '$deviceId' does not contain both a letter and a digit"
    }
    Write-Host "Minimum Device ID: $deviceId"
} else {
    Write-Warning "Minimum Device ID is not initialized yet; launch the app once with the normal user."
}

if (-not $ReportOnly -and $RadioConfigPath -and -not $WhatIfPreference) {
    $resolvedConfigPath = (Resolve-Path -LiteralPath $RadioConfigPath).Path
    $configFile = Get-Item -LiteralPath $resolvedConfigPath
    if ($configFile.Length -gt 262144) {
        throw "Radio config exceeds the 262144-byte application limit."
    }
    $configObject = Get-Content -LiteralPath $resolvedConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($configObject.schemaVersion -ne 2 -or $configObject.configVersion -lt 1) {
        throw "Radio config has an unsupported schema/config version."
    }
    if ((-not $configObject.mumble.host) -or
            (-not $configObject.mumble.username) -or
            (-not $configObject.mumble.defaultRoom) -or
            (-not $configObject.rooms) -or
            ($configObject.rooms.Count -lt 1)) {
        throw "Radio config is missing Mumble host/username/defaultRoom/rooms."
    }
    if ($identityMatch.Success -and $configObject.deviceId -ne "*" -and $configObject.deviceId -ne $deviceId) {
        throw "Radio config deviceId does not match Minimum Device ID $deviceId."
    }

    $remoteTemporary = "/data/local/tmp/minimum-radio-config-$PID.json"
    Invoke-TargetAdb -Arguments @("push", $resolvedConfigPath, $remoteTemporary) | Out-Null
    try {
        Invoke-TargetAdb -Arguments @(
            "shell", "run-as", $MinimumPackage, "mkdir", "-p", "files/radio-config"
        ) | Out-Null
        Invoke-TargetAdb -Arguments @(
            "shell", "run-as", $MinimumPackage, "chmod", "700", "files/radio-config"
        ) | Out-Null
        Invoke-TargetAdb -Arguments @(
            "shell", "run-as", $MinimumPackage, "cp", $remoteTemporary,
            "files/radio-config/active-config.json"
        ) | Out-Null
        Invoke-TargetAdb -Arguments @(
            "shell", "run-as", $MinimumPackage, "chmod", "600",
            "files/radio-config/active-config.json"
        ) | Out-Null
    } finally {
        Invoke-TargetAdb -Arguments @("shell", "rm", "-f", $remoteTemporary) | Out-Null
    }
    Invoke-TargetAdb -Arguments @("shell", "am", "force-stop", $MinimumPackage) | Out-Null
    Write-Host "Private Last Known Good radio config installed; token values were not displayed."
}

if (-not $ReportOnly -and -not $SkipMinimumHome -and -not $WhatIfPreference) {
    Invoke-TargetAdb -Arguments @(
        "shell", "am", "broadcast",
        "-a", $ShortcutProvisionAction,
        "-n", $ShortcutProvisionReceiver
    ) | Out-Null
    Start-Sleep -Milliseconds 500
    Invoke-TargetAdb -Arguments @(
        "shell", "am", "start",
        "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.HOME"
    ) | Out-Null
    Start-Sleep -Milliseconds 500
    if (Test-HomeChooserFocused) {
        throw "Android still displayed the HOME chooser after Minimum stopped registering as HOME."
    }
    Write-Host "OEM HOME verified: no chooser is visible; Minimum shortcut was requested."

    $finalActivity = if ($RadioConfigPath) { $MinimumActivity } else { $MinimumHomeActivity }
    Invoke-TargetAdb -Arguments @("shell", "am", "start", "-n", $finalActivity) | Out-Null
    Start-Sleep -Milliseconds 500
    if ($RadioConfigPath -and -not (Test-MinimumRadioFocused)) {
        throw "Minimum radio client did not take focus after config provisioning."
    }
    if (-not $RadioConfigPath -and -not (Test-MinimumHomeFocused)) {
        throw "Minimum recovery dashboard did not take focus."
    }
    if ($RadioConfigPath) {
        Write-Host "Minimum radio client opened with the private Last Known Good config."
    } else {
        Write-Host "Minimum recovery dashboard opened: swipe between Minimum and Settings."
    }
} elseif ($SkipMinimumHome) {
    Write-Host "Minimum dashboard/shortcut step skipped by request."
}

Write-Host "Preparation report complete. USB/ADB serial remains '$adbSerial'; Minimum identity is the per-device ID."
