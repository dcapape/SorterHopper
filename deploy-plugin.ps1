param(
    [string]$JarPath = (Join-Path $PSScriptRoot "target/sorterhopper-1.0.0-SNAPSHOT.jar"),
    [string]$RemoteUser = "dcapape",
    [string]$RemoteHost = "185.170.212.218",
    [string]$RemotePath = "/var/games/spigot/plugins/",
    [string]$KeyPath = (Join-Path $env:USERPROFILE ".ssh/id_rsa")
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $JarPath)) {
    throw "No se encontró el jar compilado en '$JarPath'. Compila el plugin antes de desplegar."
}

if (-not (Test-Path $KeyPath)) {
    throw "No se encontró la clave privada en '$KeyPath'. Verifica la ruta o proporciónala con -KeyPath."
}

$remoteSpec = "${RemoteUser}@${RemoteHost}:${RemotePath}"
Write-Host "Copiando $JarPath a $remoteSpec" -ForegroundColor Cyan

$scpArgs = @("-i", $KeyPath, $JarPath, $remoteSpec)

try {
    $process = Start-Process -FilePath "scp" -ArgumentList $scpArgs -NoNewWindow -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "scp finalizó con código $($process.ExitCode)"
    }
}
catch {
    throw "Error ejecutando scp: $_"
}

Write-Host "Despliegue completado" -ForegroundColor Green
