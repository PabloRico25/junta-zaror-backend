# Contrato de la API de Junta Zaror (versión 1)

Escrito el 2026-11-06 a partir del código de `AppJuntaVecinos` en la rama `parcial-4` (commit `e33c6f5`): cada ruta reemplaza una función de `JuntaRepository`, y cada regla es la que hoy está en los ViewModels y en el repositorio. Es el acuerdo entre la app y el backend; si algo cambia, se cambia aquí primero.

## 1. Convenciones

| Tema | Regla |
|---|---|
| Entrada única | El gateway, `http://<equipo>:9090`. Desde el emulador, `http://10.0.2.2:9090`. La app nunca llama a los servicios directamente. |
| Formato | JSON. Los nombres de los campos son los mismos de los modelos de la app (`horaInicio`, `gestionadaPorId`…), para que Gson los lea sin anotaciones. |
| Fechas y horas | Fecha `"2026-11-20"` (año-mes-día), hora `"18:00"`. La app las muestra en día-mes-año; el servidor nunca. Zona `America/Santiago`. |
| Identificadores | Enteros (`Int` en la app, `Long` en Java: caben los dos). |
| Sesión | `Authorization: Bearer <token>` en todas las rutas salvo `POST /auth/login`. |
| Roles | `rol` es `VECINO` o `DIRECTIVA`. Dentro de la directiva, `cargo` es `SECRETARIA`, `TESORERIA` o `PRESIDENCIA` (vacío para el vecino). |
| Respuestas de error | Cuerpo igual al del ejemplo del docente: `{ "timestamp", "status", "error", "message", "path" }`. El `message` es el texto que la app muestra tal cual. |
| Datos ficticios | Todos. Las claves de prueba son `1234`. |

### Códigos de respuesta

| Código | Cuándo |
|---|---|
| 200 | Lectura o actualización correcta |
| 201 | Creación correcta (`POST /reservas`, `POST /pagos`, `POST /accesos`) |
| 400 | Datos inválidos (falta un campo, fecha pasada, monto 0…) |
| 401 | Sin token, token vencido o clave incorrecta |
| 403 | El token es válido pero el rol o el cargo no puede hacer esto |
| 404 | No existe, o no es tuyo |
| 409 | Choque con otra regla: horario ocupado, reserva ya pagada, QR ya usado, estado que no lo permite |

## 2. Modelos

```
Usuario      { id, nombre, correo, rol, cargo }                       (nunca viaja la clave)
Dependencia  { id, nombre }
Reserva      { id, dependenciaId, vecinoId, fecha, horaInicio, horaFin,
               estado, fechaSolicitud, gestionadaPorId }              (gestionadaPorId puede ser null)
Pago         { id, reservaId, tipo, categoria, monto, fecha,
               comprobanteUri, registradoPorId, descripcion }         (reservaId, comprobanteUri pueden ser null; categoria y descripcion nunca: vacías como "", porque la app las declara no nulas y Gson dejaría null y la app se caería. Corregido el 2026-11-12)
Acceso       { reservaId, fecha, hora }
Bloque       { horaInicio, horaFin, estado, vecinoId }                (vecinoId solo para la directiva)
```

- `estado` de una reserva: `PENDIENTE`, `CONFIRMADA`, `RECHAZADA`, `CANCELADA`.
- `tipo` de un pago: `ARRIENDO` o `CONSUMO_BASICO`. `categoria`: `ELECTRICIDAD`, `AGUA` o `GAS` (vacía en un arriendo).
- `monto` es un entero en pesos.

## 3. auth-service

| Método y ruta | Quién | Cuerpo | Respuesta |
|---|---|---|---|
| `POST /auth/login` | Cualquiera | `{ "correo", "clave", "dispositivo" }` (`dispositivo` es opcional) | 200 `{ "token", "usuario": Usuario }`. 401 con `"Correo o clave incorrectos"`. Con `"dispositivo": "PUERTA"` solo entra la directiva (403 `Solo la directiva puede activar la puerta`) y el token dura 7 días |
| `GET /usuarios` | Cualquier sesión | | 200 lista de `Usuario` |

- El token (JWT) lleva `sub` (id), `rol`, `cargo` y `nombre`, y vence a las 12 horas (7 días el de la puerta, porque el aparato queda activo sin que nadie lo toque).
- La clave se guarda con hash SHA-256 en el servidor (el docente aceptó cualquier hash; el ejemplo usa SHA-1 y aquí se sube a SHA-256, igual que la app). La app deja de calcular el hash: envía la clave tal cual por la red local de desarrollo. En producción iría sobre HTTPS; aquí se documenta como limitación.
- La validación de la app se mantiene (correo con formato, clave de al menos 4 caracteres) y el servidor la repite.

## 4. reserva-service

| Método y ruta | Quién | Qué hace | Reemplaza |
|---|---|---|---|
| `GET /dependencias` | Cualquiera, sin token | Las dos salas. Pública porque la puerta las pide antes de iniciar sesión | `obtenerDependencias` |
| `GET /reservas` | Cualquier sesión | El vecino recibe solo las suyas; la directiva, todas. Orden: fecha y hora de inicio. Filtro opcional `?estado=` | `observarReservas`, `observarTodasLasReservas` |
| `GET /reservas/{id}` | Dueño o directiva | Una reserva | `buscarReserva` |
| `POST /reservas` | `VECINO` | Crea una reserva `PENDIENTE` | `crearReserva` |
| `PUT /reservas/{id}/cancelar` | El dueño | Pasa a `CANCELADA` | `cancelarReserva` |
| `PUT /reservas/{id}/confirmar` | cargo `SECRETARIA` | Pasa a `CONFIRMADA` | `confirmarReserva` |
| `PUT /reservas/{id}/rechazar` | cargo `SECRETARIA` | Pasa a `RECHAZADA` | `rechazarReserva` |
| `GET /reservas/ocupacion?dependenciaId=&fecha=` | Cualquier sesión | Bloques `CONFIRMADA` y `PENDIENTE` de esa sala y día, sin nombres si es vecino | alimenta `calcularBloques` |
| `GET /reservas/{id}/confirmada` | Otro servicio | `true` si existe y está confirmada | nuevo: lo usa `pago-service` |
| `GET /accesos/usadas` | Directiva o puerta | Lista de `reservaId` que ya entraron | `obtenerReservasUsadas` |
| `POST /accesos` | Directiva o puerta | Registra un ingreso | `registrarIngreso` |

### Reglas de `POST /reservas`
Cuerpo: `{ "dependenciaId", "fecha", "horaInicio", "horaFin" }`. El servidor completa `vecinoId` (del token), `estado` = `PENDIENTE` y `fechaSolicitud` = hoy.

| Regla | Mensaje (400 salvo la última) |
|---|---|
| La dependencia debe existir | `Elige una dependencia` |
| La fecha no puede ser pasada | `La fecha no puede ser pasada` |
| La hora de inicio y la de fin existen, en punto, entre `09:00` y `22:00` | `Elige la hora de inicio` / `Elige la hora de fin` |
| `horaFin` mayor que `horaInicio` | `La hora de fin debe ser mayor que la de inicio` |
| No se cruza con una reserva **confirmada** de la misma sala y día (se cruzan si `inicioA < finB` y `inicioB < finA`) | 409 `Ese horario ya está ocupado por una reserva confirmada` |

Solo las reservas confirmadas bloquean el horario. El checkbox del reglamento es solo de la pantalla.

### Reglas de los cambios de estado
- `cancelar`: solo el dueño y solo si está `PENDIENTE`; si no, 409 con `Solo se puede cancelar una reserva pendiente`.
- `confirmar`: solo `SECRETARIA` (403 con `Solo la Secretaría puede confirmar reservas`) y solo `PENDIENTE`. Vuelve a revisar el cruce: 409 con `No se puede confirmar: ese horario ya está ocupado por una reserva confirmada`. Guarda `gestionadaPorId` desde el token.
- `rechazar`: igual, 403 con `Solo la Secretaría puede rechazar reservas`. Guarda `gestionadaPorId`.

### Accesos (puerta y Escanear)
- `POST /accesos` con `{ "reservaId", "fecha", "hora" }`. La reserva debe existir. Si ya tiene un ingreso, **409** con `Este código ya se usó`. Con una restricción única en `reservaId`, dos puertas que lean el mismo QR a la vez no pueden entrar las dos.
- La firma y la ventana de ingreso del QR (de 10 minutos antes a 15 después) se siguen evaluando en la app con `utils` (`evaluarPuerta`, ya probadas). El servidor aporta lo que la app sola no puede: saber qué QR ya se usó en cualquier aparato.

## 5. pago-service

| Método y ruta | Quién | Qué hace | Reemplaza |
|---|---|---|---|
| `GET /pagos` | Cualquier sesión | Todos los pagos, más nuevos primero. Para un vecino vienen en `null` `reservaId`, `comprobanteUri` y `registradoPorId`, y la `descripcion` viene vacía (`""`) | `observarPagos` |
| `GET /pagos/pagadas` | Cualquier sesión | Lista de `reservaId` con pago de arriendo | `observarReservasPagadas`, `reservaPagada` |
| `POST /pagos` | cargo `TESORERIA` | Registra un pago | `crearPago` |

### Reglas de `POST /pagos`
Cuerpo: `{ "reservaId", "tipo", "categoria", "monto", "fecha", "comprobanteUri", "descripcion" }`. El servidor completa `registradoPorId`.

| Regla | Mensaje |
|---|---|
| Solo `TESORERIA` | 403 `Solo la Tesorería puede registrar pagos` |
| `tipo` es `ARRIENDO` o `CONSUMO_BASICO` | `Elige el tipo de pago` |
| Un arriendo exige `reservaId` de una reserva **confirmada** (el servicio pregunta a `reserva-service`) | `Elige la reserva que se paga` |
| Un arriendo no puede repetirse para la misma reserva | 409 `Esa reserva ya tiene un pago de arriendo` |
| Un consumo exige `categoria` válida | `Elige la categoría del gasto` |
| `monto` es mayor que 0 y de hasta 9 dígitos | `Escribe el monto` / `El monto debe ser mayor que 0` |
| `fecha` no es futura | `La fecha del pago no puede ser futura` |

Un pago es solo informativo: no procesa transacciones reales (el caso lo exige). El comprobante guarda en el MVP únicamente la referencia que envía la app; subir la imagen al servidor (`multipart`) queda como mejora.

## 6. Qué se calcula en la app
- **Por cobrar**: `GET /reservas?estado=CONFIRMADA` menos `GET /pagos/pagadas` (hoy es una consulta de Room).
- **Resumen del mes y gastos por categoría**: con `GET /pagos` y las utilidades `ResumenFinanciero`, que ya tienen pruebas.
- **Bloques de disponibilidad**: con `GET /reservas/ocupacion` y `calcularBloques`.

## 7. Datos de ejemplo (los carga el `DataLoader` al arrancar, una sola vez)

- 2 dependencias: `Salón Principal` y `Sala Multiuso`.
- 6 usuarios, todos con clave `1234`:

| Id | Nombre | Correo | Rol y cargo |
|---|---|---|---|
| 1 | Juan Pérez | `juan.perez@zaror.test` | VECINO |
| 2 | Ana Muñoz | `ana.munoz@zaror.test` | VECINO (sin reservas) |
| 3 | Pedro Soto | `pedro.soto@zaror.test` | VECINO |
| 4 | María Torres | `maria.torres@zaror.test` | DIRECTIVA, SECRETARIA |
| 5 | Luis Rojas | `luis.rojas@zaror.test` | DIRECTIVA, TESORERIA |
| 6 | Carmen Vidal | `carmen.vidal@zaror.test` | DIRECTIVA, PRESIDENCIA |

- 8 reservas, con fechas calculadas desde el día en que se arranca: la 1 (confirmada, dentro de 11 días), la 2, la 3 y la 4 (pendientes), la 5 (rechazada), la 6 (cancelada) y la 7 y la 8 (confirmadas, de hace 3 días). Se copian de `cargarDatosDeEjemplo` de la app, que hoy es la fuente.
- 6 pagos: 3 arriendos (reservas 1, 7 y 8) y 3 consumos (electricidad $38.500, agua $24.200, gas $24.000).

## 8. Fuera de este contrato (por ahora)
- Notificaciones o bandeja de avisos: el caso las pide; quedan para el nivel C del plan (`GET /avisos`).
- Subida de comprobantes como `multipart`.
- Registro de nuevos vecinos.
- **QR firmado por el servidor** (mejora de seguridad, opcional): `GET /reservas/{id}/ticket` entregaría el código solo si la reserva está confirmada y pagada, firmado con una clave que nunca sale del servidor, y `POST /accesos/validar` comprobaría firma, ventana y uso. Evita que la clave de firma esté dentro de la app, que es pública. Cuesta unas 3 a 4 horas (reescribir `evaluarPuerta` en Java y cambiar tres pantallas). Se hace solo si sobra tiempo después del APK firmado.

## 9. Decisiones

Decididas el 2026-11-06 (Alex):

1. **La puerta** se activa con inicio de sesión: al activarla, una persona de la directiva escribe su correo y clave, el servidor entrega un token de 7 días y la puerta lo guarda. Solo la directiva puede activarla. La sesión sirve únicamente para llamar a `/accesos`.
2. **Llamadas entre servicios** como en el ejemplo del docente: `pago-service` llama a `reserva-service` con `WebClient` para saber si la reserva está confirmada.
3. **El QR** sigue generándose en la app, pero solo existe cuando la directiva lo habilita: la Secretaría confirma la reserva y la Tesorería registra el pago. La versión con QR firmado por el servidor queda como mejora opcional (sección 8).

Por defecto, mientras no se diga otra cosa:

4. Una base de datos por servicio (`jz_auth`, `jz_reservas`, `jz_pagos`).
5. Repositorio del backend público, como en el Parcial 2. Se cambia si el docente pide privado con su usuario como colaborador.
