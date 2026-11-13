package com.example.pago.service;

import com.example.pago.dto.PagoRequest;
import com.example.pago.model.Pago;
import com.example.pago.repository.PagoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// El repositorio es falso (Mockito) y el otro servicio también: un WebClient de mentira responde lo
// que cada prueba necesita y guarda la petición que recibió. No se usa base de datos ni red.
@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    @Mock
    private PagoRepository pagoRepository;

    // ---------- el otro servicio, de mentira ----------

    // Responde "true" o "false" como lo haría reserva-service. Con null simula que está caído.
    private static class ReservaFalsa {
        final AtomicInteger llamadas = new AtomicInteger();
        final AtomicReference<ClientRequest> ultimaPeticion = new AtomicReference<>();
        final WebClient webClient;

        ReservaFalsa(String respuesta) {
            this.webClient = WebClient.builder().exchangeFunction(peticion -> {
                llamadas.incrementAndGet();
                ultimaPeticion.set(peticion);
                if (respuesta == null) {
                    return Mono.<ClientResponse>error(new IllegalStateException("conexión rechazada"));
                }
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(respuesta)
                        .build());
            }).build();
        }
    }

    private PagoService servicio(ReservaFalsa reserva) {
        return new PagoService(pagoRepository, reserva.webClient, "/reservas/%d/confirmada");
    }

    // ---------- ayudas ----------

    private String fechaEn(int dias) {
        return LocalDate.now(ZoneId.of("America/Santiago")).plusDays(dias).toString();
    }

    private PagoRequest arriendo(Long reservaId, Long monto, String fecha) {
        PagoRequest pedido = new PagoRequest();
        pedido.setTipo("ARRIENDO");
        pedido.setReservaId(reservaId);
        pedido.setMonto(monto);
        pedido.setFecha(fecha);
        return pedido;
    }

    private PagoRequest consumo(String categoria, Long monto, String fecha) {
        PagoRequest pedido = new PagoRequest();
        pedido.setTipo("CONSUMO_BASICO");
        pedido.setCategoria(categoria);
        pedido.setMonto(monto);
        pedido.setFecha(fecha);
        return pedido;
    }

    // Comprueba a la vez el código HTTP y el mensaje que verá la app
    private void esperarError(HttpStatus estado, String mensaje, Executable accion) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, accion);
        assertEquals(estado.value(), error.getStatusCode().value());
        assertEquals(mensaje, error.getReason());
    }

    // Registra como Luis Rojas (id 5, Tesorería)
    private Pago registrar(PagoService servicio, PagoRequest pedido) {
        return servicio.crear(5L, "TESORERIA", "token-de-luis", pedido);
    }

    // ---------- permisos y datos ----------

    @Test
    void registrarSinSerTesoreriaDa403() {
        PagoService servicio = servicio(new ReservaFalsa("true"));

        esperarError(HttpStatus.FORBIDDEN, "Solo la Tesorería puede registrar pagos",
                () -> servicio.crear(4L, "SECRETARIA", "token", consumo("AGUA", 1000L, fechaEn(0))));
        verify(pagoRepository, never()).saveAndFlush(any(Pago.class));
    }

    @Test
    void unTipoQueNoExisteDa400() {
        PagoRequest pedido = consumo("AGUA", 1000L, fechaEn(0));
        pedido.setTipo("OTRO");

        esperarError(HttpStatus.BAD_REQUEST, "Elige el tipo de pago",
                () -> registrar(servicio(new ReservaFalsa("true")), pedido));
    }

    @Test
    void unConsumoSinCategoriaValidaDa400() {
        PagoService servicio = servicio(new ReservaFalsa("true"));

        esperarError(HttpStatus.BAD_REQUEST, "Elige la categoría del gasto",
                () -> registrar(servicio, consumo(null, 1000L, fechaEn(0))));
        esperarError(HttpStatus.BAD_REQUEST, "Elige la categoría del gasto",
                () -> registrar(servicio, consumo("INTERNET", 1000L, fechaEn(0))));
    }

    @Test
    void unArriendoSinReservaDa400() {
        esperarError(HttpStatus.BAD_REQUEST, "Elige la reserva que se paga",
                () -> registrar(servicio(new ReservaFalsa("true")), arriendo(null, 50000L, fechaEn(0))));
    }

    @Test
    void elMontoDebeSerUnNumeroPositivoDeHasta9Digitos() {
        PagoService servicio = servicio(new ReservaFalsa("true"));

        esperarError(HttpStatus.BAD_REQUEST, "Escribe el monto",
                () -> registrar(servicio, consumo("GAS", null, fechaEn(0))));
        esperarError(HttpStatus.BAD_REQUEST, "El monto debe ser mayor que 0",
                () -> registrar(servicio, consumo("GAS", 0L, fechaEn(0))));
        esperarError(HttpStatus.BAD_REQUEST, "El monto debe ser mayor que 0",
                () -> registrar(servicio, consumo("GAS", -5L, fechaEn(0))));
        esperarError(HttpStatus.BAD_REQUEST, "El monto es demasiado grande",
                () -> registrar(servicio, consumo("GAS", 1000000000L, fechaEn(0))));
    }

    @Test
    void laFechaNoPuedeFaltarNiSerFutura() {
        PagoService servicio = servicio(new ReservaFalsa("true"));

        esperarError(HttpStatus.BAD_REQUEST, "Elige la fecha del pago",
                () -> registrar(servicio, consumo("GAS", 1000L, null)));
        esperarError(HttpStatus.BAD_REQUEST, "Elige la fecha del pago",
                () -> registrar(servicio, consumo("GAS", 1000L, "12-11-2026")));
        esperarError(HttpStatus.BAD_REQUEST, "La fecha del pago no puede ser futura",
                () -> registrar(servicio, consumo("GAS", 1000L, fechaEn(1))));
    }

    @Test
    void unaDescripcionMuyLargaDa400() {
        PagoRequest pedido = consumo("GAS", 1000L, fechaEn(0));
        pedido.setDescripcion("x".repeat(201));

        esperarError(HttpStatus.BAD_REQUEST, "La descripción es muy larga",
                () -> registrar(servicio(new ReservaFalsa("true")), pedido));
    }

    // ---------- arriendos: la consulta a reserva-service ----------

    @Test
    void unArriendoDeUnaReservaNoConfirmadaDa400() {
        ReservaFalsa reserva = new ReservaFalsa("false");

        esperarError(HttpStatus.BAD_REQUEST, "Elige la reserva que se paga",
                () -> registrar(servicio(reserva), arriendo(3L, 50000L, fechaEn(0))));
        verify(pagoRepository, never()).saveAndFlush(any(Pago.class));
    }

    // Lo que no pudimos probar a mano: reserva-service apagado
    @Test
    void siReservaServiceEstaCaidoDa503ConUnMensajeClaro() {
        ReservaFalsa reserva = new ReservaFalsa(null);

        esperarError(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo verificar la reserva. Intenta de nuevo",
                () -> registrar(servicio(reserva), arriendo(4L, 50000L, fechaEn(0))));
        verify(pagoRepository, never()).saveAndFlush(any(Pago.class));
    }

    @Test
    void unArriendoYaPagadoDa409() {
        when(pagoRepository.existsByReservaIdAndTipo(4L, "ARRIENDO")).thenReturn(true);

        esperarError(HttpStatus.CONFLICT, "Esa reserva ya tiene un pago de arriendo",
                () -> registrar(servicio(new ReservaFalsa("true")), arriendo(4L, 50000L, fechaEn(0))));
        verify(pagoRepository, never()).saveAndFlush(any(Pago.class));
    }

    // Dos pagos de la misma reserva llegan a la vez: los dos preguntan y ninguno la ve pagada,
    // pero la base solo deja pasar a uno (reservaId es único) y el otro falla.
    @Test
    void siLaBaseRechazaElArriendoDuplicadoDa409() {
        when(pagoRepository.existsByReservaIdAndTipo(4L, "ARRIENDO")).thenReturn(false);
        when(pagoRepository.saveAndFlush(any(Pago.class))).thenThrow(new DataIntegrityViolationException("duplicado"));

        esperarError(HttpStatus.CONFLICT, "Esa reserva ya tiene un pago de arriendo",
                () -> registrar(servicio(new ReservaFalsa("true")), arriendo(4L, 50000L, fechaEn(0))));
    }

    @Test
    void unArriendoBienGuardaElPagoYReenviaElTokenDeQuienLlama() {
        ReservaFalsa reserva = new ReservaFalsa("true");
        when(pagoRepository.existsByReservaIdAndTipo(4L, "ARRIENDO")).thenReturn(false);
        when(pagoRepository.saveAndFlush(any(Pago.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        PagoRequest pedido = arriendo(4L, 55000L, fechaEn(0));
        pedido.setDescripcion("  Transferencia  ");
        pedido.setComprobanteUri("content://comprobante/1");

        Pago guardado = registrar(servicio(reserva), pedido);

        assertEquals("ARRIENDO", guardado.getTipo());
        assertEquals(4L, guardado.getReservaId());
        assertEquals("", guardado.getCategoria());
        assertEquals(55000, guardado.getMonto());
        assertEquals(5L, guardado.getRegistradoPorId());
        assertEquals("Transferencia", guardado.getDescripcion());
        assertEquals("content://comprobante/1", guardado.getComprobanteUri());

        // La consulta a reserva-service lleva el mismo token y la ruta de esa reserva
        assertEquals(1, reserva.llamadas.get());
        assertEquals("Bearer token-de-luis", reserva.ultimaPeticion.get().headers().getFirst("Authorization"));
        assertEquals("/reservas/4/confirmada", reserva.ultimaPeticion.get().url().toString());
    }

    // ---------- consumos ----------

    @Test
    void unConsumoIgnoraLaReservaYNoConsultaAOtroServicio() {
        ReservaFalsa reserva = new ReservaFalsa("true");
        when(pagoRepository.saveAndFlush(any(Pago.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        PagoRequest pedido = consumo("AGUA", 24200L, fechaEn(0));
        pedido.setReservaId(1L);

        Pago guardado = registrar(servicio(reserva), pedido);

        assertNull(guardado.getReservaId());
        assertEquals("AGUA", guardado.getCategoria());
        assertEquals("", guardado.getDescripcion());
        assertNull(guardado.getComprobanteUri());
        assertEquals(0, reserva.llamadas.get());
    }

    @Test
    void unComprobanteEnBlancoSeGuardaVacio() {
        when(pagoRepository.saveAndFlush(any(Pago.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        PagoRequest pedido = consumo("GAS", 24000L, fechaEn(0));
        pedido.setComprobanteUri("   ");

        Pago guardado = registrar(servicio(new ReservaFalsa("true")), pedido);

        assertNull(guardado.getComprobanteUri());
    }

    // ---------- lecturas ----------

    private Pago pagoDeArriendo() {
        return new Pago(1L, 8L, "ARRIENDO", "", 60000, "2026-11-10", "content://comprobante/1", 5L, "Transferencia");
    }

    @Test
    void unVecinoVeLosMontosPeroNoLoPrivado() {
        Pago original = pagoDeArriendo();
        when(pagoRepository.findAllByOrderByFechaDescIdDesc()).thenReturn(List.of(original));

        Pago visto = servicio(new ReservaFalsa("true")).listar("VECINO").get(0);

        assertEquals(60000, visto.getMonto());
        assertEquals("ARRIENDO", visto.getTipo());
        assertNull(visto.getReservaId());
        assertNull(visto.getComprobanteUri());
        assertNull(visto.getRegistradoPorId());
        assertEquals("", visto.getDescripcion());
        assertEquals("", visto.getCategoria());
        // Se entregó una copia: el pago guardado no se tocó
        assertEquals(8L, original.getReservaId());
        assertEquals("Transferencia", original.getDescripcion());
    }

    @Test
    void laDirectivaVeElPagoCompleto() {
        when(pagoRepository.findAllByOrderByFechaDescIdDesc()).thenReturn(List.of(pagoDeArriendo()));

        Pago visto = servicio(new ReservaFalsa("true")).listar("DIRECTIVA").get(0);

        assertEquals(8L, visto.getReservaId());
        assertEquals(5L, visto.getRegistradoPorId());
        assertEquals("Transferencia", visto.getDescripcion());
    }

    @Test
    void lasReservasPagadasSonLasDeLosArriendos() {
        when(pagoRepository.findByTipo("ARRIENDO")).thenReturn(List.of(
                pagoDeArriendo(),
                new Pago(3L, 7L, "ARRIENDO", "", 40000, "2026-11-09", null, 5L, "")));

        assertEquals(List.of(8L, 7L), servicio(new ReservaFalsa("true")).reservasPagadas());
    }
}