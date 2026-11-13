# reiniciar-datos.ps1: deja las bases con los datos de ejemplo recién cargados.
# Apaga los servicios, borra las tablas de reservas, accesos y pagos, y vuelve a encenderlos:
# al arrancar, cada DataLoader las llena de nuevo con las fechas de hoy. Los usuarios no se tocan.
# Enciende MySQL si está apagado. Pide la clave de root (está en C:\Herramientas\mysql\credenciales_root.txt).

$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
& "$raiz\parar.ps1"

if (-not (Get-NetTCPConnection -State Listen -LocalPort 3306 -ErrorAction SilentlyContinue)) {
    Write-Host "MySQL estaba apagado; encendiéndolo..."
    Start-Process "C:\Herramientas\mysql\Iniciar MySQL.bat" -WindowStyle Minimized
    for ($i = 0; $i -lt 30; $i += 2) {
        if (Get-NetTCPConnection -State Listen -LocalPort 3306 -ErrorAction SilentlyContinue) { break }
        Start-Sleep -Seconds 2
    }
}

$sql = "DROP TABLE IF EXISTS jz_reservas.accesos, jz_reservas.reservas, jz_reservas.dependencias, jz_pagos.pagos;"
& "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -u root -p -e $sql
if ($LASTEXITCODE -ne 0) {
    Write-Host "No se pudieron borrar las tablas; los servicios quedaron apagados." -ForegroundColor Red
    exit 1
}
Write-Host "Tablas borradas. Encendiendo todo de nuevo..." -ForegroundColor Cyan

& "$raiz\levantar.ps1"
