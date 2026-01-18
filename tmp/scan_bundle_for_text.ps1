param(
  [Parameter(Mandatory=$true)][string]$Path,
  [Parameter(Mandatory=$true)][string]$Text
)

if (!(Test-Path $Path)) {
  Write-Host "MISSING: $Path"
  exit 2
}

# Read as bytes and decode as UTF-8 with replacement to avoid encoding issues.
$bytes = [System.IO.File]::ReadAllBytes($Path)
$str = [System.Text.Encoding]::UTF8.GetString($bytes)

if ($str.Contains($Text)) {
  Write-Host "FOUND: $Text"
  exit 0
} else {
  Write-Host "NOT_FOUND: $Text"
  exit 1
}
