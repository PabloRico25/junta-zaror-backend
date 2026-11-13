# levantar.ps1: enciende MySQL y los cuatro servicios del backend, cada uno en su propia ventana.
# Orden: base de datos, auth, reserva, pago y al final el gateway. Se puede correr varias veces:
# lo que ya esté encendido se deja como está.

$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
$jdk = "C:\Users\alexa\.jdks\temurin-21.0.13"

function Esta-Encendido($puerto) {
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $puerto -ErrorAction SilentlyContinue)
}

function Esperar-Puerto($nombre, $puerto, $segundos) {
    for ($i = 0; $i -lt $segundos; $i += 2) {
        if (Esta-Encendido $puerto) { Write-Host "  $nombre listo en el puerto $puerto" -ForegroundColor Green; return }
        Start-Sleep -Seconds 2
    }
    Write-Host "  $nombre NO respondió en $segundos segundos: revisa su ventana" -ForegroundColor Red
}

if (Esta-Encendido 3306) {
    Write-Host "MySQL ya estaba encendido"
} else {
    Write-Host "Encendiendo MySQL..."
    Start-Process "C:\Herramientas\mysql\Iniciar MySQL.bat" -WindowStyle Minimized
    Esperar-Puerto "MySQL" 3306 30
}

$servicios = @(
    @{ nombre = "auth-service";    puerto = 9094 },
    @{ nombre = "reserva-service"; puerto = 9092 },
    @{ nombre = "pago-service";    puerto = 9095 },
    @{ nombre = "api-gateway";     puerto = 9090 }
)

foreach ($s in $servicios) {
    if (Esta-Encendido $s.puerto) {
        Write-Host "$($s.nombre) ya estaba encendido"
        continue
    }
    Write-Host "Encendiendo $($s.nombre)..."
    $orden = "`$env:JAVA_HOME = '$jdk'; `$Host.UI.RawUI.WindowTitle = '$($s.nombre)'; Set-Location '$raiz\$($s.nombre)'; .\mvnw spring-boot:run"
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $orden
    Esperar-Puerto $s.nombre $s.puerto 120
}

Write-Host ""
Write-Host "Todo encendido. Gateway en http://localhost:9090" -ForegroundColor Cyan
