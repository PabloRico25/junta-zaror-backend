# parar.ps1: apaga los cuatro servicios del backend (y solo ellos, por su puerto).
# MySQL se deja encendido; para apagarlo usa "Parar MySQL.bat". Con -ConMySQL se apaga también.

param([switch]$ConMySQL)

$puertos = @{ 9090 = "api-gateway"; 9092 = "reserva-service"; 9094 = "auth-service"; 9095 = "pago-service" }

foreach ($puerto in $puertos.Keys) {
    $conexion = Get-NetTCPConnection -State Listen -LocalPort $puerto -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($conexion) {
        Stop-Process -Id $conexion.OwningProcess -Force
        Write-Host "$($puertos[$puerto]) apagado"
    } else {
        Write-Host "$($puertos[$puerto]) ya estaba apagado"
    }
}

if ($ConMySQL) {
    & "C:\Herramientas\mysql\Parar MySQL.bat"
    Write-Host "MySQL apagado"
}
