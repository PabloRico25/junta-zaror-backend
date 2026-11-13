package com.example.reserva.service;

import com.example.reserva.dto.Bloque;
import com.example.reserva.dto.ReservaRequest;
import com.example.reserva.model.Reserva;
import com.example.reserva.repository.DependenciaRepository;
import com.example.reserva.repository.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// El repositorio es falso (Mockito): estas pruebas no usan base de datos.
@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private DependenciaRepository dependenciaRepository;

    @InjectMocks
    private ReservaService reservaService;

    // ---------- ayudas ----------

    private String fechaEn(int dias) {
        return LocalDate.now(ZoneId.of("America/Santiago")).plusDays(dias).toString();
    }

    private ReservaRequest pedido(String fecha, String inicio, String fin) {
        ReservaRequest pedido = new ReservaRequest();
        pedido.setDependenciaId(1L);
        pedido.setFecha(fecha);
        pedido.setHoraInicio(inicio);
        pedido.setHoraFin(fin);
        return pedido;
    }

    // Una reserva de la sala 1, dentro de 5 días, de 10:00 a 12:00
    private Reserva reserva(Long id, Long vecinoId, String estado) {
        return new Reserva(id, 1L, vecinoId, fechaEn(5), "10:00", "12:00", estado, fechaEn(0), null);
    }

    // Comprueba a la vez el código HTTP y el mensaje que verá la app
    private void esperarError(HttpStatus estado, String mensaje, Executable accion) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, accion);
        assertEquals(estado.value(), error.getStatusCode().value());
        assertEquals(mensaje, error.getReason());
    }

    // ---------- crear ----------

    @Test
    void crearComoDirectivaDa403() {
        esperarError(HttpStatus.FORBIDDEN, "Solo un vecino puede pedir una reserva",
                () -> reservaService.crear(4L, "DIRECTIVA", pedido(fechaEn(5), "10:00", "12:00")));
    }

    @Test
    void crearConDependenciaInexistenteDa400() {
        when(dependenciaRepository.existsById(1L)).thenReturn(false);

        esperarError(HttpStatus.BAD_REQUEST, "Elige una dependencia",
                () -> reservaService.crear(1L, "VECINO", pedido(fechaEn(5), "10:00", "12:00")));
    }

    @Test
    void crearConFechaPasadaDa400() {
        when(dependenciaRepository.existsById(1L)).thenReturn(true);

        esperarError(HttpStatus.BAD_REQUEST, "La fecha no puede ser pasada",
                () -> reservaService.crear(1L, "VECINO", pedido(fechaEn(-1), "10:00", "12:00")));
    }

    @Test
    void crearConFechaMalEscritaDa400() {
        when(dependenciaRepository.existsById(1L)).thenReturn(true);

        esperarError(HttpStatus.BAD_REQUEST, "Elige la fecha",
                () -> reservaService.crear(1L, "VECINO", pedido("20-11-2026", "10:00", "12:00")));
    }

    @Test
    void crearConHoraDeInicioAntesDeLas9Da400() {
        when(dependenciaRepository.existsById(1L)).thenReturn(true);

        esperarError(HttpStatus.BAD_REQUEST, "Elige la hora de inicio",
                () -> reservaService.crear(1L, "VECINO", pedido(fechaEn(5), "08:00", "12:00")));
    }

    @Test
    void crearConHoraQueNoEstaEnPuntoDa400() {
        when(dependenciaRepository.existsById(1L)).thenReturn(true);

        esperarError(HttpStatus.BAD_REQUEST, "Elige la hora de inicio",
                () -> reservaService.crear(1L, "VECINO", pedido(fechaEn(5), "10:30", "12:00")));
    }

    @Test
    void crearConHoraDeFinDespuesDeLas22Da400() {
        when(dependenciaRepository.existsById(1L)).thenReturn(true);

        esperarError(HttpStatus.BAD_REQUEST, "Elige la hora de fin",
                () -> reservaService.crear(1L, "VECINO", pedido(fechaEn(5), "20:00", "23:00")));
    }

    @Test
    void crearConFinMenorQueInicioDa400() {
        when(dependenciaRepository.existsById(1L)).thenReturn(true);

        esperarError(HttpStatus.BAD_REQUEST, "La hora de fin debe ser mayor que la de inicio",
                () -> reservaService.crear(1L, "VECINO", pedido(fechaEn(5), "14:00", "12:00")));
    }

    @Test
    void crearQueChocaConUnaConfirmadaDa409() {
        when(dependenciaRepository.existsById(1L)).thenReturn(true);
        when(reservaRepository.contarChoques(1L, fechaEn(5), "10:00", "12:00", 0L)).thenReturn(1L);

        esperarError(HttpStatus.CONFLICT, "Ese horario ya está ocupado por una reserva confirmada",
                () -> reservaService.crear(1L, "VECINO", pedido(fechaEn(5), "10:00", "12:00")));
        verify(reservaRepository, never()).save(any(Reserva.class));
    }

    @Test
    void crearBienGuardaUnaReservaPendienteDelVecinoDelToken() {
        when(dependenciaRepository.existsById(1L)).thenReturn(true);
        when(reservaRepository.contarChoques(anyLong(), anyString(), anyString(), anyString(), anyLong())).thenReturn(0L);
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Reserva creada = reservaService.crear(3L, "VECINO", pedido(fechaEn(5), "10:00", "12:00"));

        assertEquals("PENDIENTE", creada.getEstado());
        assertEquals(3L, creada.getVecinoId());
        assertEquals(fechaEn(0), creada.getFechaSolicitud());
        assertNull(creada.getGestionadaPorId());
        verify(reservaRepository).save(any(Reserva.class));
    }

    // ---------- cancelar ----------

    @Test
    void cancelarLaReservaDeOtroVecinoDa404() {
        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva(5L, 3L, "PENDIENTE")));

        esperarError(HttpStatus.NOT_FOUND, "La reserva no existe",
                () -> reservaService.cancelar(5L, 1L));
    }

    @Test
    void cancelarUnaReservaQueNoEstaPendienteDa409() {
        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva(5L, 3L, "CONFIRMADA")));

        esperarError(HttpStatus.CONFLICT, "Solo se puede cancelar una reserva pendiente",
                () -> reservaService.cancelar(5L, 3L));
    }

    @Test
    void cancelarUnaReservaPendienteLaDejaCancelada() {
        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva(5L, 3L, "PENDIENTE")));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Reserva cancelada = reservaService.cancelar(5L, 3L);

        assertEquals("CANCELADA", cancelada.getEstado());
    }

    // ---------- confirmar ----------

    @Test
    void confirmarSinSerSecretariaDa403() {
        esperarError(HttpStatus.FORBIDDEN, "Solo la Secretaría puede confirmar reservas",
                () -> reservaService.confirmar(5L, 5L, "TESORERIA"));
        verify(reservaRepository, never()).save(any(Reserva.class));
    }

    @Test
    void confirmarUnaReservaQueNoEstaPendienteDa409() {
        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva(5L, 3L, "CANCELADA")));

        esperarError(HttpStatus.CONFLICT, "Solo se puede confirmar una reserva pendiente",
                () -> reservaService.confirmar(5L, 4L, "SECRETARIA"));
    }

    @Test
    void confirmarQueChocaConOtraConfirmadaDa409() {
        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva(5L, 3L, "PENDIENTE")));
        // Se excluye la propia reserva (id 5) para que no choque consigo misma
        when(reservaRepository.contarChoques(1L, fechaEn(5), "10:00", "12:00", 5L)).thenReturn(1L);

        esperarError(HttpStatus.CONFLICT,
                "No se puede confirmar: ese horario ya está ocupado por una reserva confirmada",
                () -> reservaService.confirmar(5L, 4L, "SECRETARIA"));
        verify(reservaRepository, never()).save(any(Reserva.class));
    }

    @Test
    void confirmarGuardaQuienLoHizo() {
        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva(5L, 3L, "PENDIENTE")));
        when(reservaRepository.contarChoques(1L, fechaEn(5), "10:00", "12:00", 5L)).thenReturn(0L);
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Reserva confirmada = reservaService.confirmar(5L, 4L, "SECRETARIA");

        assertEquals("CONFIRMADA", confirmada.getEstado());
        assertEquals(4L, confirmada.getGestionadaPorId());
    }

    // ---------- rechazar ----------

    @Test
    void rechazarSinSerSecretariaDa403() {
        esperarError(HttpStatus.FORBIDDEN, "Solo la Secretaría puede rechazar reservas",
                () -> reservaService.rechazar(5L, 3L, ""));
    }

    @Test
    void rechazarGuardaQuienLoHizo() {
        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva(5L, 3L, "PENDIENTE")));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Reserva rechazada = reservaService.rechazar(5L, 4L, "SECRETARIA");

        assertEquals("RECHAZADA", rechazada.getEstado());
        assertEquals(4L, rechazada.getGestionadaPorId());
    }

    // ---------- listar y obtener ----------

    @Test
    void listarComoVecinoDevuelveSoloSusReservas() {
        when(reservaRepository.findByVecinoIdOrderByFechaAscHoraInicioAsc(3L))
                .thenReturn(List.of(reserva(5L, 3L, "PENDIENTE")));

        List<Reserva> reservas = reservaService.listar(3L, "VECINO", null);

        assertEquals(1, reservas.size());
        verify(reservaRepository, never()).findAllByOrderByFechaAscHoraInicioAsc();
    }

    @Test
    void listarComoDirectivaConEstadoFiltraPorEstado() {
        when(reservaRepository.findByEstadoOrderByFechaAscHoraInicioAsc("PENDIENTE"))
                .thenReturn(List.of(reserva(5L, 3L, "PENDIENTE")));

        List<Reserva> reservas = reservaService.listar(4L, "DIRECTIVA", "PENDIENTE");

        assertEquals(1, reservas.size());
    }

    @Test
    void obtenerLaReservaDeOtroVecinoDa404() {
        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva(5L, 3L, "PENDIENTE")));

        esperarError(HttpStatus.NOT_FOUND, "La reserva no existe",
                () -> reservaService.obtener(5L, 1L, "VECINO"));
    }

    @Test
    void obtenerLaReservaDeOtroVecinoComoDirectivaFunciona() {
        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva(5L, 3L, "PENDIENTE")));

        Reserva reserva = reservaService.obtener(5L, 4L, "DIRECTIVA");

        assertEquals(5L, reserva.getId());
    }

    // ---------- ocupación y consulta de confirmada ----------

    @Test
    void laOcupacionNoMuestraElVecinoALosVecinosPeroSiALaDirectiva() {
        when(dependenciaRepository.existsById(1L)).thenReturn(true);
        when(reservaRepository.findByDependenciaIdAndFechaAndEstadoInOrderByHoraInicioAsc(
                eq(1L), eq(fechaEn(5)), anyList()))
                .thenReturn(List.of(reserva(5L, 3L, "CONFIRMADA")));

        List<Bloque> paraVecino = reservaService.ocupacion(1L, fechaEn(5), "VECINO");
        List<Bloque> paraDirectiva = reservaService.ocupacion(1L, fechaEn(5), "DIRECTIVA");

        assertNull(paraVecino.get(0).getVecinoId());
        assertEquals(3L, paraDirectiva.get(0).getVecinoId());
        assertEquals("10:00", paraVecino.get(0).getHoraInicio());
    }

    @Test
    void laOcupacionConUnaDependenciaInexistenteDa400() {
        when(dependenciaRepository.existsById(99L)).thenReturn(false);

        esperarError(HttpStatus.BAD_REQUEST, "Elige una dependencia",
                () -> reservaService.ocupacion(99L, fechaEn(5), "VECINO"));
    }

    @Test
    void estaConfirmadaSoloEsVerdaderaParaUnaReservaConfirmada() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reserva(1L, 3L, "CONFIRMADA")));
        when(reservaRepository.findById(2L)).thenReturn(Optional.of(reserva(2L, 3L, "PENDIENTE")));
        when(reservaRepository.findById(99L)).thenReturn(Optional.empty());

        assertTrue(reservaService.estaConfirmada(1L));
        assertFalse(reservaService.estaConfirmada(2L));
        assertFalse(reservaService.estaConfirmada(99L));
    }
}