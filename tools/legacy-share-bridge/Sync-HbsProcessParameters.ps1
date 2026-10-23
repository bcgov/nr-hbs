<#
.SYNOPSIS
  Copies the HBS processing parameters from the HBS backend's settings
  ConfigMap into the legacy ProcessParam files read by the legacy batch (B2031/B2041 …).

.DESCRIPTION
  Runs on the legacy Windows batch host, scheduled by AutoMate before the
  invoicing jobs. It
    1. gets a client-credentials token for the HBS_LEGACY_BRIDGE service account,
    2. calls GET <ApiBaseUrl>/api/v1/hbs/integrations/legacy/process-parameters,
    3. writes each returned file into -TargetDirectory, only when its content
       changed, atomically (temp file + replace) and keeping a .bak copy.
  Anything unexpected (no token, non-200, malformed JSON, an unexpected file
  name) leaves the existing files untouched and exits non-zero, so AutoMate
  flags the run. See docs/legacy-share-bridge.md.

.PARAMETER ApiBaseUrl
  The new HBS app's backend base URL, e.g. https://<hbs-host>.

.PARAMETER TokenUrl
  The Keycloak realm token endpoint (…/protocol/openid-connect/token).

.PARAMETER ClientId
  The service account's client id.

.PARAMETER ClientSecretFile
  File holding only the client secret, readable by the AutoMate service user
  alone. Not passed on the command line so it never shows in task logs.

.PARAMETER TargetDirectory
  The legacy ProcessParam directory (the batch's process-parameter file path).

.PARAMETER AllowedFile
  The file names the bridge may write. Defaults to the legacy set; anything
  else in the response is refused.

.EXAMPLE
  .\Sync-HbsProcessParameters.ps1 -ApiBaseUrl https://hbs.example -TokenUrl https://sso.example/realms/x/protocol/openid-connect/token `
     -ClientId hbs-legacy-bridge -ClientSecretFile D:\hbs\bridge.secret -TargetDirectory \\server\ProcessParam
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
  [Parameter(Mandatory = $true)][string]$ApiBaseUrl,
  [Parameter(Mandatory = $true)][string]$TokenUrl,
  [Parameter(Mandatory = $true)][string]$ClientId,
  [Parameter(Mandatory = $true)][string]$ClientSecretFile,
  [Parameter(Mandatory = $true)][string]$TargetDirectory,
  [string[]]$AllowedFile = @(),
  [int]$TimeoutSeconds = 60
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

function Write-Log([string]$Message) {
  Write-Output ('{0:yyyy-MM-dd HH:mm:ss} {1}' -f (Get-Date), $Message)
}

function Get-Token {
  $secret = (Get-Content -LiteralPath $ClientSecretFile -Raw).Trim()
  if (-not $secret) { throw "Client secret file $ClientSecretFile is empty" }
  $body = @{ grant_type = 'client_credentials'; client_id = $ClientId; client_secret = $secret }
  $response = Invoke-RestMethod -Method Post -Uri $TokenUrl -Body $body -TimeoutSec $TimeoutSeconds
  if (-not $response.access_token) { throw 'Token endpoint returned no access_token' }
  return $response.access_token
}

function Get-Encoding([string]$Name) {
  if (-not $Name) { return [Text.Encoding]::GetEncoding('ISO-8859-1') }
  if ($Name -match '^utf-?8$') { return New-Object Text.UTF8Encoding($false) } # no BOM: legacy Java readers don't expect one
  return [Text.Encoding]::GetEncoding($Name)
}

function Write-IfChanged([string]$Path, [string]$Content, [Text.Encoding]$Encoding) {
  $bytes = $Encoding.GetBytes($Content)
  if (Test-Path -LiteralPath $Path) {
    $current = [IO.File]::ReadAllBytes($Path)
    if ([Linq.Enumerable]::SequenceEqual([byte[]]$current, [byte[]]$bytes)) {
      Write-Log "unchanged: $Path"
      return $false
    }
  }
  if ($PSCmdlet.ShouldProcess($Path, 'write')) {
    $temp = "$Path.new"
    [IO.File]::WriteAllBytes($temp, $bytes)
    if (Test-Path -LiteralPath $Path) {
      [IO.File]::Replace($temp, $Path, "$Path.bak")
    } else {
      [IO.File]::Move($temp, $Path)
    }
    Write-Log "written: $Path ($($bytes.Length) bytes)"
  }
  return $true
}

try {
  if (-not (Test-Path -LiteralPath $TargetDirectory -PathType Container)) {
    throw "Target directory $TargetDirectory is not reachable"
  }
  $token = Get-Token
  $uri = $ApiBaseUrl.TrimEnd('/') + '/api/v1/hbs/integrations/legacy/process-parameters'
  $payload = Invoke-RestMethod -Method Get -Uri $uri -Headers @{ Authorization = "Bearer $token" } -TimeoutSec $TimeoutSeconds

  if (-not $payload -or -not $payload.files -or @($payload.files).Count -eq 0) {
    throw 'Response has no files; leaving the legacy files untouched'
  }
  # Validate everything before writing anything.
  foreach ($file in $payload.files) {
    $name = [string]$file.name
    if (-not $name -or $name -ne [IO.Path]::GetFileName($name) -or $name.Contains('..')) {
      throw "Refusing file name '$name'"
    }
    if ($AllowedFile.Count -gt 0 -and $AllowedFile -notcontains $name) {
      throw "File '$name' is not in -AllowedFile"
    }
    if ($null -eq $file.content) { throw "File '$name' has no content" }
  }

  $changed = 0
  foreach ($file in $payload.files) {
    $path = Join-Path $TargetDirectory $file.name
    if (Write-IfChanged -Path $path -Content ([string]$file.content) -Encoding (Get-Encoding ([string]$file.encoding))) {
      $changed++
    }
  }
  Write-Log "done: $changed of $(@($payload.files).Count) file(s) updated (generated $($payload.generatedAt))"
  exit 0
} catch {
  Write-Log "FAILED: $($_.Exception.Message)"
  exit 1
}
