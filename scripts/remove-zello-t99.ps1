<#[
.SYNOPSIS
    Removes Zello from the Android user profile on the T99.

.DESCRIPTION
    T99 ships Zello as the privileged system package com.loudtalks. Without root we should not
    modify /system; pm uninstall --user 0 removes it for the normal user and is reversible by an
    OEM reset or by reinstalling/enabling the package.

    The script validates the exact ADB serial and package before changing anything. It is
    intentionally interactive unless -Force is supplied.
#>

[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$Serial = "12344321",
    [int]$AdbPort = 5041,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$PackageName = "com.loudtalks"

$adbCommand = Get-Command adb -ErrorAction Stop
$adbPath = $adbCommand.Source

function Invoke-T99Adb {
    param([Parameter(Mandatory)][string[]]$Arguments)
    & $adbPath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "ADB command failed with exit code $LASTEXITCODE"
    }
}

$deviceLines = & $adbPath -P $AdbPort devices
if ($LASTEXITCODE -ne 0) {
    throw "Could not query the ADB server on port $AdbPort"
}

$deviceLine = $deviceLines | Where-Object { $_ -match "^$([regex]::Escape($Serial))\s+device\s*$" }
if (-not $deviceLine) {
    throw "T99 serial '$Serial' is not connected as an authorized ADB device on port $AdbPort"
}

$packagePath = & $adbPath -P $AdbPort -s $Serial shell pm path $PackageName
if ($LASTEXITCODE -ne 0 -or -not $packagePath) {
    Write-Host "Zello ($PackageName) is not installed for this device user. Nothing to do."
    exit 0
}

Write-Host "Target: T99 / ADB serial $Serial"
Write-Host "Package: $PackageName"
Write-Host "System path: $($packagePath -join ' ')"

if (-not $Force) {
    $answer = Read-Host "Type REMOVE to uninstall Zello for user 0"
    if ($answer -cne "REMOVE") {
        Write-Host "Cancelled."
        exit 0
    }
}

if ($PSCmdlet.ShouldProcess("$Serial / $PackageName", "pm uninstall --user 0")) {
    Invoke-T99Adb -Arguments @("-P", "$AdbPort", "-s", $Serial, "shell", "pm", "uninstall", "--user", "0", $PackageName)

    $visiblePackage = & $adbPath -P $AdbPort -s $Serial shell pm list packages $PackageName
    if ($visiblePackage) {
        throw "Zello still appears in the user package list after uninstall"
    }
    Write-Host "Zello removed from user 0. The read-only system APK may remain under /system."
}
