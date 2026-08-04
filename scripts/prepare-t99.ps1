<#[
.SYNOPSIS
    Prepares a supported T99/T88 for Minimum radio-client use.

.DESCRIPTION
    This is the canonical provisioning script. It reports the different serial identities,
    removes Zello for user 0, launches Minimum once so its app Device ID can be created, verifies
    that identity, installs a Minimum recovery shortcut into the OEM Launcher3 workspace, verifies
    that the system HOME no longer displays a chooser, and opens the one-icon-per-page radio
    dashboard. It intentionally does not attempt to rewrite the USB/ADB serial:
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
    [switch]$ReportOnly
)

$ErrorActionPreference = "Stop"
$PackageName = "com.loudtalks"
$MinimumPackage = "se.lublin.mumla"
$MinimumActivity = "se.lublin.mumla/.app.MumlaActivity"
$MinimumHomeActivity = "se.lublin.mumla/.radio.MinimumHomeActivity"
$MinimumHomeComponent = "se.lublin.mumla/se.lublin.mumla.radio.MinimumHomeActivity"
$ShortcutProvisionReceiver = "se.lublin.mumla/.radio.RadioProvisionReceiver"
$ShortcutProvisionAction = "se.lublin.mumla.action.PROVISION_LAUNCHER_SHORTCUT"

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
if ($identityMatch.Success) {
    $deviceId = $identityMatch.Groups[1].Value
    if ($deviceId -notmatch '[A-Z]' -or $deviceId -notmatch '\d') {
        throw "Minimum Device ID '$deviceId' does not contain both a letter and a digit"
    }
    Write-Host "Minimum Device ID: $deviceId"
} else {
    Write-Warning "Minimum Device ID is not initialized yet; launch the app once with the normal user."
}

if (-not $ReportOnly -and -not $SkipMinimumHome -and -not $WhatIfPreference) {
    Invoke-TargetAdb -Arguments @(
        "shell", "am", "broadcast",
        "-a", $ShortcutProvisionAction,
        "-n", $ShortcutProvisionReceiver
    ) | Out-Null
    Start-Sleep -Milliseconds 500
    Invoke-TargetAdb -Arguments @(
        "shell", "am", "start", "-W",
        "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.HOME"
    ) | Out-Null
    Start-Sleep -Milliseconds 500
    if (Test-HomeChooserFocused) {
        throw "Android still displayed the HOME chooser after Minimum stopped registering as HOME."
    }
    Write-Host "OEM HOME verified: no chooser is visible; Minimum shortcut was requested."

    Invoke-TargetAdb -Arguments @("shell", "am", "start", "-n", $MinimumHomeActivity) | Out-Null
    Start-Sleep -Milliseconds 500
    if (-not (Test-MinimumHomeFocused)) {
        throw "Minimum radio dashboard did not take focus."
    }
    Write-Host "Minimum radio dashboard opened: swipe between Minimum and Settings."
} elseif ($SkipMinimumHome) {
    Write-Host "Minimum dashboard/shortcut step skipped by request."
}

Write-Host "Preparation report complete. USB/ADB serial remains '$adbSerial'; Minimum identity is the per-device ID."
