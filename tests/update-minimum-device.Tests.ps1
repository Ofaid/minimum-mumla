$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "..\scripts\update-minimum-device.ps1") -LibraryOnly

$script:Passed = 0
$script:Failed = 0

function Assert-Equal {
    param($Expected, $Actual, [string]$Name)
    if (($Expected -is [array]) -or ($Actual -is [array])) {
        if ((@($Expected) -join "|") -cne (@($Actual) -join "|")) { throw "$Name expected '$(@($Expected) -join '|')' but got '$(@($Actual) -join '|')'." }
    } elseif ($Expected -cne $Actual) { throw "$Name expected '$Expected' but got '$Actual'." }
}

function Assert-True { param([bool]$Value, [string]$Name); if (-not $Value) { throw "$Name expected true." } }

function Assert-ThrowsCode {
    param([scriptblock]$Action, [string]$Code, [string]$Name)
    try { & $Action; throw "$Name did not throw." } catch {
        if ($_.Exception.Message -notmatch "^\[$([regex]::Escape($Code))\]") { throw "$Name threw unexpected error: $($_.Exception.Message)" }
    }
}

function Test-Case {
    param([string]$Name, [scriptblock]$Action)
    try { & $Action; $script:Passed++; Write-Host "PASS $Name" } catch { $script:Failed++; Write-Host "FAIL $Name - $($_.Exception.Message)" }
}

Test-Case "supported model source of truth" {
    Assert-Equal "T56" (Get-DeviceProfile "UNIPRO" "ZX") "T56"
    Assert-Equal "T99" (Get-DeviceProfile "Youdotech" "QM011") "T99"
    Assert-Equal "RYKS" (Get-DeviceProfile "ELINK" "ym_258") "RYKS"
    Assert-Equal "" (Get-DeviceProfile "Other" "ZX") "unknown"
}

Test-Case "version comparison and downgrade gate primitive" {
    Assert-Equal -1 (Compare-VersionCode 10 11) "upgrade"
    Assert-Equal 0 (Compare-VersionCode 11 11) "same"
    Assert-Equal 1 (Compare-VersionCode 12 11) "downgrade"
}

Test-Case "device transcript selection" {
    $one = Convert-AdbDeviceLines @("List of devices attached", "abc device product:x transport_id:7")
    Assert-Equal 1 @($one).Count "one count"
    Assert-Equal 7 $one[0].TransportId "transport"
    Assert-Equal "abc" (Select-TargetRecord $one).Serial "selected"
    $multiple = Convert-AdbDeviceLines @("a device transport_id:1", "b device transport_id:2")
    Assert-ThrowsCode { Select-TargetRecord $multiple } "TARGET_COUNT" "multiple"
    Assert-Equal "b" (Select-TargetRecord $multiple -RequestedTransportId 2).Serial "explicit transport"
    $bad = Convert-AdbDeviceLines @("a unauthorized transport_id:1")
    Assert-ThrowsCode { Select-TargetRecord $bad } "TARGET_NOT_AUTHORIZED" "unauthorized"
}

Test-Case "duplicate serial safely refused" {
    $records = Convert-AdbDeviceLines @("same device transport_id:1", "same device transport_id:2")
    Assert-ThrowsCode { Select-TargetRecord $records -RequestedSerial "same" } "SERIAL_AMBIGUOUS" "duplicate serial"
}

Test-Case "reboot transport change chooses unique same profile" {
    $records = @(
        [pscustomobject]@{ Serial="new"; State="device"; TransportId=9; Manufacturer="UNIPRO"; Model="ZX" },
        [pscustomobject]@{ Serial="other"; State="device"; TransportId=10; Manufacturer="Other"; Model="Other" }
    )
    Assert-Equal "new" (Find-ReturningCandidate $records "UNIPRO" "ZX" "old").Serial "returning"
    $ambiguous = @($records[0], [pscustomobject]@{ Serial="new2"; State="device"; TransportId=11; Manufacturer="UNIPRO"; Model="ZX" })
    Assert-Equal $null (Find-ReturningCandidate $ambiguous "UNIPRO" "ZX" "old") "ambiguous return"
}

Test-Case "package and managed-status transcripts" {
    $package = Parse-PackageState "Packages:`n  versionCode=3070300 minSdk=21 targetSdk=36`n  versionName=3.7.3-minimum.1-debug"
    Assert-Equal ([long]3070300) $package.VersionCode "package code"
    Assert-Equal "3.7.3-minimum.1-debug" $package.VersionName "package name"
    $status = Parse-ProvisioningStatus 'Broadcast completed: result=0, data="deviceId=A1B2C3;activeDeviceId=A1B2C3;configVersion=14;pending=false;lastSuccessMs=123"'
    Assert-Equal "A1B2C3" $status.DeviceId "device id"
    Assert-Equal 14 $status.ConfigVersion "config"
    Assert-True (-not $status.Pending) "pending false"
}

Test-Case "debug to release signer mismatch is refused before install" {
    $debugSigner = "168F42ED412DA80ADAF27BED0984DBEE191168E9DF04F08AFA240A3F9DE45972"
    $releaseSigner = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    Assert-ThrowsCode { Assert-SignerCompatibility @($debugSigner) $releaseSigner } "SIGNER_MISMATCH" "debug release mismatch"
}

Test-Case "matching signer accepted" {
    $signer = "168F42ED412DA80ADAF27BED0984DBEE191168E9DF04F08AFA240A3F9DE45972"
    Assert-SignerCompatibility @($signer) $signer
}

Test-Case "migration dispatch refuses unknown requested behavior" {
    $migration = [pscustomobject]@{ id="issue-11-cellular"; fromVersionCodeMax=3070300; toVersionCode=3070400; profiles=@("T56"); rebootRequired=$true; irreversible=$false }
    Assert-ThrowsCode { Get-RequiredMigrations @($migration) 3070300 3070400 "T56" } "MIGRATION_NOT_IMPLEMENTED" "unapproved migration"
    Assert-ThrowsCode { Get-RequiredMigrations @($migration) 3070300 3070400 "T99" } "MIGRATION_NOT_IMPLEMENTED" "unknown migration on other model"
}

Test-Case "unsafe relative bundle paths are refused" {
    Assert-ThrowsCode { Assert-SafeRelativePath "../minimum-foss.apk" } "BUNDLE_PATH_UNSAFE" "parent traversal"
    Assert-ThrowsCode { Assert-SafeRelativePath "scripts\update-minimum-device.ps1" } "BUNDLE_PATH_UNSAFE" "backslash"
    Assert-ThrowsCode { Assert-SafeRelativePath "/minimum-foss.apk" } "BUNDLE_PATH_UNSAFE" "rooted path"
}

Test-Case "idempotent outcome and summary" {
    Assert-Equal "ALREADY_OK" (New-MigrationResult "APK_VERSION" "ALREADY_OK").Outcome "already ok"
    $results = @(
        [pscustomobject]@{ Profile="T56"; DeviceId="A1B2C3"; Result="PASS"; Detail="ALREADY_OK" },
        [pscustomobject]@{ Profile="T99"; DeviceId="D4E5F6"; Result="FAIL"; Detail="signer mismatch" }
    )
    $summary = Format-SessionSummary $results "3.7.4"
    Assert-True ($summary -match 'Totals: 1 PASS, 0 WARN, 1 FAIL') "summary totals"
    Assert-True ($summary -notmatch 'serial') "summary privacy"
}

Test-Case "secret and identifier redaction" {
    $safe = ConvertTo-SafeMessage "serial=usb123 token=ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ123456 password=hunter2 Bearer abc.def.ghi"
    Assert-True ($safe -notmatch 'usb123|ABCDEFGHIJKLMNOPQRSTUVWXYZ|hunter2|abc\.def') "redacted values"
    Assert-True ($safe -match 'serial=<redacted>') "redaction marker"
}

Test-Case "bundle allowlist and checksum reject tampering" {
    $root = Join-Path ([IO.Path]::GetTempPath()) ("minimum-updater-test-{0}" -f [guid]::NewGuid().ToString("N"))
    try {
        New-Item -ItemType Directory -Path $root | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $root "scripts") | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $root "assets") | Out-Null
        $approved = @(
            "Provision Minimum Device.cmd", "README.txt", "UPDATER-README.md", "Update Minimum Device.cmd",
            "VERSION.txt", "assets/t99-wifi-provisioner.apk", "minimum-foss.apk", "minimum-foss.apk.sha256",
            "scripts/prepare-ryks.ps1", "scripts/prepare-t56.ps1", "scripts/prepare-t99.ps1",
            "scripts/provision-minimum-device.ps1", "scripts/update-minimum-device.ps1"
        )
        foreach ($relative in $approved) {
            Set-Content -LiteralPath (Join-Path $root $relative.Replace('/', '\')) -Value "fixture-$relative" -NoNewline -Encoding ASCII
        }
        Set-Content -LiteralPath (Join-Path $root "VERSION.txt") -Value "3.7.4" -NoNewline -Encoding ASCII
        Set-Content -LiteralPath (Join-Path $root "minimum-foss.apk") -Value "fixture" -NoNewline -Encoding ASCII
        $apkHash = Get-FileSha256 (Join-Path $root "minimum-foss.apk")
        Set-Content -LiteralPath (Join-Path $root "minimum-foss.apk.sha256") -Value "$apkHash  minimum-foss.apk" -NoNewline -Encoding ASCII
        $files = $approved | ForEach-Object {
            [ordered]@{ path=$_; sha256=Get-FileSha256 (Join-Path $root $_) }
        }
        $manifest = [ordered]@{
            schemaVersion=1; releaseTag="3.7.4"; applicationId="se.lublin.mumla"; versionCode=3070400
            versionName="3.7.4"; apkFile="minimum-foss.apk"; apkSha256=$apkHash
            signerSha256=("A" * 64); rebootRequired=$false; migrations=@(); files=$files
        }
        $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $root "RELEASE-MANIFEST.json") -Encoding UTF8
        $bundle = Read-ReleaseBundle $root
        Assert-Equal "3.7.4" $bundle.Manifest.releaseTag "valid bundle"
        Add-Content -LiteralPath (Join-Path $root "minimum-foss.apk") -Value "tamper"
        Assert-ThrowsCode { Read-ReleaseBundle $root } "BUNDLE_CHECKSUM" "tampered file"
        Set-Content -LiteralPath (Join-Path $root "extra.txt") -Value "extra"
        Assert-ThrowsCode { Read-ReleaseBundle $root } "BUNDLE_ALLOWLIST" "extra file"
    } finally {
        if (Test-Path -LiteralPath $root) { Remove-Item -LiteralPath $root -Recurse -Force }
    }
}

$realApkPath = Join-Path $PSScriptRoot "..\app\build\outputs\apk\foss\debug\mumla-foss-debug.apk"
if (Test-Path -LiteralPath $realApkPath -PathType Leaf) {
    Test-Case "real built APK identity and signer parsing" {
        $identity = Get-ApkManifestIdentity -ApkPath $realApkPath
        Assert-Equal "se.lublin.mumla" $identity.ApplicationId "real APK package"
        Assert-Equal ([long]3070300) $identity.VersionCode "real APK version code"
        Assert-True ($identity.VersionName -match '-debug$') "real APK debug version"
        $signers = @(Get-ApkV1SignerDigests -ApkPath $realApkPath)
        Assert-True ($signers.Count -ge 1) "real APK signer count"
        Assert-True (@($signers | Where-Object { $_ -notmatch '^[0-9A-F]{64}$' }).Count -eq 0) "real APK signer format"
    }
}

Write-Host "Updater tests: $script:Passed passed, $script:Failed failed"
if ($script:Failed -gt 0) { exit 1 }
