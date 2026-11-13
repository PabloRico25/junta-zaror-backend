package com.example.reserva.controller;

import com.example.reserva.dto.Bloque;
import com.example.reserva.dto.ReservaRequest;
import com.example.reserva.exception.GlobalExceptionHandler;
import com.example.reserva.model.Reserva;
import com.example.reserva.service.ReservaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// El servicio es falso (Mockito) y la seguridad también: un resolvedor de argumentos entrega
// un Jwt de prueba, el que haría el filtro de seguridad real con el token de la app.
@ExtendWith(MockitoExtension.class)
class ReservaControllerTest {

    @Mock
    private ReservaService reservaService;

    private MockMvc mockMvc;

    // Quién "llama" en la prueba actual
    private Jwt jwt;

    private void como(String id, String rol, String cargo) {
        jwt = Jwt.withTokenValue("token-de-prueba")
                .header("alg", "HS256")
                .subject(id)
                .claim("rol", rol)
                .claim("cargo", cargo)
                .build();
    }

    @BeforeEach
    void preparar() {
        HandlerMethodArgumentResolver entregaElJwt = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parametro) {
                return Jwt.class.equals(parametro.getParameterType());
            }

            @Override
            public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer contenedor,
                                          NativeWebRequest peticion, WebDataBinderFactory fabrica) {
                return jwt;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(new ReservaController(reservaService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(entregaElJwt)
                .build();
    }

    private Reserva reserva(Long id, String estado) {
        return new Reserva(id, 1L, 3L, "2026-11-23", "18:00", "21:00", estado, "2026-11-12", null);
    }

    @Test
    void listarDevuelve200ConLasReservas() throws Exception {
        como("3", "VECINO", "");
        when(reservaService.listar(3L, "VECINO", null)).thenReturn(List.of(reserva(5L, "PENDIENTE")));

        mockMvc.perform(get("/reservas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].horaInicio").value("18:00"));
    }

    @Test
    void crearDevuelve201ConLaReservaPendiente() throws Exception {
        como("3", "VECINO", "");
        when(reservaService.crear(eq(3L), eq("VECINO"), any(ReservaRequest.class)))
                .thenReturn(reserva(9L, "PENDIENTE"));

        mockMvc.perform(post("/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dependenciaId\":1,\"fecha\":\"2026-11-23\",\"horaInicio\":\"18:00\",\"horaFin\":\"21:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void unErrorDelServicioLlegaConLaFormaDelContrato() throws Exception {
        como("4", "DIRECTIVA", "SECRETARIA");
        when(reservaService.crear(eq(4L), eq("DIRECTIVA"), any(ReservaRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un vecino puede pedir una reserva"));

        mockMvc.perform(post("/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dependenciaId\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Solo un vecino puede pedir una reserva"))
                .andExpect(jsonPath("$.path").value("/reservas"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void confirmarLeeElIdYElCargoDelToken() throws Exception {
        como("4", "DIRECTIVA", "SECRETARIA");
        when(reservaService.confirmar(5L, 4L, "SECRETARIA")).thenReturn(reserva(5L, "CONFIRMADA"));

        mockMvc.perform(put("/reservas/5/confirmar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));

        verify(reservaService).confirmar(5L, 4L, "SECRETARIA");
    }

    @Test
    void unJsonRotoDa400SinMostrarTextosTecnicos() throws Exception {
        como("3", "VECINO", "");

        mockMvc.perform(post("/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dependenciaId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cuerpo de la petición no es válido"));

        verify(reservaService, never()).crear(any(), any(), any());
    }

    @Test
    void unIdQueNoEsNumeroDa400() throws Exception {
        como("3", "VECINO", "");

        mockMvc.perform(get("/reservas/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La petición no es válida"));
    }

    @Test
    void unMetodoNoPermitidoDa405() throws Exception {
        como("3", "VECINO", "");

        mockMvc.perform(delete("/reservas"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("Ese método no está permitido en esta ruta"));
    }

    @Test
    void laOcupacionDevuelveLosBloques() throws Exception {
        como("3", "VECINO", "");
        when(reservaService.ocupacion(1L, "2026-11-23", "VECINO"))
                .thenReturn(List.of(new Bloque("18:00", "21:00", "CONFIRMADA", null)));

        mockMvc.perform(get("/reservas/ocupacion")
                        .param("dependenciaId", "1")
                        .param("fecha", "2026-11-23"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].horaInicio").value("18:00"))
                .andExpect(jsonPath("$[0].estado").value("CONFIRMADA"));
    }

    @Test
    void confirmadaDevuelveUnBooleano() throws Exception {
        como("5", "DIRECTIVA", "TESORERIA");
        when(reservaService.estaConfirmada(1L)).thenReturn(true);

        mockMvc.perform(get("/reservas/1/confirmada"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }
}