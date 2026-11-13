package com.example.reserva.service;

import com.example.reserva.model.Acceso;
import com.example.reserva.repository.AccesoRepository;
import com.example.reserva.repository.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Los repositorios son falsos (Mockito): estas pruebas no usan base de datos.
@ExtendWith(MockitoExtension.class)
class AccesoServiceTest {

    @Mock
    private AccesoRepository accesoRepository;

    @Mock
    private ReservaRepository reservaRepository;

    @InjectMocks
    private AccesoService accesoService;

    // Comprueba a la vez el código HTTP y el mensaje que verá la app
    private void esperarError(HttpStatus estado, String mensaje, Executable accion) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, accion);
        assertEquals(estado.value(), error.getStatusCode().value());
        assertEquals(mensaje, error.getReason());
    }

    private Acceso ingreso(Long reservaId) {
        return new Acceso(reservaId, "2026-11-23", "18:05");
    }

    @Test
    void registrarComoVecinoDa403() {
        esperarError(HttpStatus.FORBIDDEN, "Solo la directiva puede registrar ingresos",
                () -> accesoService.registrar("VECINO", ingreso(1L)));
    }

    @Test
    void consultarLasUsadasComoVecinoDa403() {
        esperarError(HttpStatus.FORBIDDEN, "Solo la directiva puede ver los ingresos",
                () -> accesoService.usadas("VECINO"));
    }

    @Test
    void registrarSinReservaDa404() {
        esperarError(HttpStatus.NOT_FOUND, "La reserva no existe",
                () -> accesoService.registrar("DIRECTIVA", ingreso(null)));
    }

    @Test
    void registrarUnaReservaInexistenteDa404() {
        when(reservaRepository.existsById(99L)).thenReturn(false);

        esperarError(HttpStatus.NOT_FOUND, "La reserva no existe",
                () -> accesoService.registrar("DIRECTIVA", ingreso(99L)));
    }

    @Test
    void registrarSinFechaDa400() {
        when(reservaRepository.existsById(1L)).thenReturn(true);

        esperarError(HttpStatus.BAD_REQUEST, "Falta la fecha del ingreso",
                () -> accesoService.registrar("DIRECTIVA", new Acceso(1L, null, "18:05")));
    }

    @Test
    void registrarSinHoraDa400() {
        when(reservaRepository.existsById(1L)).thenReturn(true);

        esperarError(HttpStatus.BAD_REQUEST, "Falta la hora del ingreso",
                () -> accesoService.registrar("DIRECTIVA", new Acceso(1L, "2026-11-23", " ")));
    }

    @Test
    void registrarUnCodigoYaUsadoDa409() {
        when(reservaRepository.existsById(1L)).thenReturn(true);
        when(accesoRepository.existsById(1L)).thenReturn(true);

        esperarError(HttpStatus.CONFLICT, "Este código ya se usó",
                () -> accesoService.registrar("DIRECTIVA", ingreso(1L)));
        verify(accesoRepository, never()).saveAndFlush(any(Acceso.class));
    }

    // Dos puertas leen el mismo QR a la vez: las dos preguntan y ninguna lo ve usado,
    // pero la base solo deja pasar a una (la reserva es la clave primaria) y la otra falla.
    @Test
    void siLaBaseRechazaPorClaveDuplicadaDa409() {
        when(reservaRepository.existsById(1L)).thenReturn(true);
        when(accesoRepository.existsById(1L)).thenReturn(false);
        when(accesoRepository.saveAndFlush(any(Acceso.class))).thenThrow(new DataIntegrityViolationException("clave duplicada"));

        esperarError(HttpStatus.CONFLICT, "Este código ya se usó",
                () -> accesoService.registrar("DIRECTIVA", ingreso(1L)));
    }

    @Test
    void registrarBienGuardaElIngreso() {
        when(reservaRepository.existsById(1L)).thenReturn(true);
        when(accesoRepository.existsById(1L)).thenReturn(false);
        when(accesoRepository.saveAndFlush(any(Acceso.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Acceso guardado = accesoService.registrar("DIRECTIVA", ingreso(1L));

        assertEquals(1L, guardado.getReservaId());
        assertEquals("18:05", guardado.getHora());
        verify(accesoRepository).saveAndFlush(any(Acceso.class));
    }

    @Test
    void lasUsadasDevuelvenSoloLosIdsDeReserva() {
        when(accesoRepository.findAll()).thenReturn(List.of(ingreso(11L), ingreso(12L)));

        List<Long> usadas = accesoService.usadas("DIRECTIVA");

        assertEquals(List.of(11L, 12L), usadas);
    }
}