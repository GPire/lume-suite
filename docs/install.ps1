# Lume Suite for Windows: downloads TV Doctor, checks its SHA-256 against the release's SHA256SUMS, unzips it into
# %USERPROFILE%\LumeSuite and starts it in automatic mode. If Python is missing it asks Windows (winget) to install it.
# Read it first if you like: https://github.com/GPire/lume-suite/blob/main/docs/install.ps1
$ErrorActionPreference = "Stop"
$base = "https://github.com/GPire/lume-suite/releases/latest/download"
$dir = Join-Path $env:USERPROFILE "LumeSuite"
New-Item -ItemType Directory -Force $dir | Out-Null
Set-Location $dir
Write-Host "Scarico TV Doctor..."
Invoke-WebRequest "$base/TV-Doctor.zip" -OutFile TV-Doctor.zip
Invoke-WebRequest "$base/SHA256SUMS" -OutFile SHA256SUMS
$want = ((Get-Content SHA256SUMS | Where-Object { $_ -match "TV-Doctor.zip" }) -split "\s+")[0]
$got = (Get-FileHash TV-Doctor.zip -Algorithm SHA256).Hash.ToLower()
if (-not $want -or $want.ToLower() -ne $got) { Remove-Item TV-Doctor.zip; throw "La firma SHA-256 non corrisponde: non lo apro." }
if (Test-Path tv-doctor) { Remove-Item tv-doctor -Recurse -Force }
Expand-Archive TV-Doctor.zip -DestinationPath . -Force
$py = Get-Command py -ErrorAction SilentlyContinue
if (-not $py) { $py = Get-Command python -ErrorAction SilentlyContinue }
if (-not $py) {
    Write-Host "Manca Python: lo installo con Windows (winget)..."
    winget install --id Python.Python.3.12 -e --accept-package-agreements --accept-source-agreements
    Write-Host "Python installato. Richiudi e riapri PowerShell, poi incolla di nuovo il comando."
    exit 0
}
Set-Location tv-doctor
if ($py.Name -like "py*") { & $py.Source -3 tvdoctor_app.py --auto } else { & $py.Source tvdoctor_app.py --auto }
