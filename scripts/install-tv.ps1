# Instala o Câmeras ao Vivo na TV Box pela rede (sem cabos).
# Uso: .\scripts\install-tv.ps1 -Ip 192.168.0.50 [-Port 5555] [-PairCode 123456 -PairPort 37000]
param(
  [Parameter(Mandatory = $true)][string]$Ip,
  [int]$Port = 5555,
  [string]$PairCode,
  [int]$PairPort
)
$ErrorActionPreference = "Stop"
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = Join-Path $env:USERPROFILE "Android\Sdk\platform-tools\adb.exe" }
if ($PairCode -and $PairPort) { & $adb pair "$($Ip):$PairPort" $PairCode }
Write-Host "Conectando em $($Ip):$Port (confirme 'Permitir depuração' na TV, marcando 'Sempre permitir')..."
& $adb connect "$($Ip):$Port"
& $adb -s "$($Ip):$Port" wait-for-device
$abi = (& $adb -s "$($Ip):$Port" shell getprop ro.product.cpu.abi).Trim()
$dist = Join-Path (Split-Path $PSScriptRoot -Parent) "dist"
$apk = Get-ChildItem $dist -Filter "CamerasAoVivo-v*-$abi.apk" | Sort-Object Name | Select-Object -Last 1
if (-not $apk) { $apk = Get-ChildItem $dist -Filter "CamerasAoVivo-v*-universal.apk" | Sort-Object Name | Select-Object -Last 1 }
Write-Host "ABI da TV Box: $abi -> instalando $($apk.Name)"
& $adb -s "$($Ip):$Port" install -r $apk.FullName
& $adb -s "$($Ip):$Port" shell am start -n io.github.mbsantos97.camerasaovivo/.MainActivity
Write-Host "Pronto."
