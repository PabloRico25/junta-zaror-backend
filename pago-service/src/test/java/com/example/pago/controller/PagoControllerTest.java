package com.example.pago.controller;

import com.example.pago.dto.PagoRequest;
import com.example.pago.exception.GlobalExceptionHandler;
import com.example.pago.model.HistorialPago;
import com.example.pago.model.Pago;
import com.example.pago.service.PagoService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// El servicio es falso (Mockito) y la seguridad también: un resolvedor de argumentos entrega
// un Jwt de prueba, el que haría el filtro de seguridad real con el token de la app.
@ExtendWith(MockitoExtension.class)
class PagoControllerTest {

    @Mock
    private PagoService pagoService;

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

        mockMvc = MockMvcBuilders.standaloneSetup(new PagoController(pagoService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(entregaElJwt)
                .build();
    }

    private Pago consumo() {
        return new Pago(7L, null, "CONSUMO_BASICO", "AGUA", 24200, "2026-11-12", null, 5L, "");
    }

    @Test
    void listarDevuelve200ConLosPagos() throws Exception {
        como("1", "VECINO", "");
        when(pagoService.listar("VECINO")).thenReturn(List.of(consumo()));

        mockMvc.perform(get("/pagos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("CONSUMO_BASICO"))
                .andExpect(jsonPath("$[0].categoria").value("AGUA"))
                .andExpect(jsonPath("$[0].monto").value(24200))
                .andExpect(jsonPath("$[0].descripcion").value(""));
    }

    @Test
    void pagadasDevuelveLosIdsDeReserva() throws Exception {
        como("1", "VECINO", "");
        when(pagoService.reservasPagadas()).thenReturn(List.of(8L, 7L, 1L));

        mockMvc.perform(get("/pagos/pagadas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(8))
                .andExpect(jsonPath("$[2]").value(1));
    }

    @Test
    void registrarDevuelve201YPasaElIdElCargoYElTokenDeQuienLlama() throws Exception {
        como("5", "DIRECTIVA", "TESORERIA");
        when(pagoService.crear(eq(5L), eq("TESORERIA"), eq("token-de-prueba"), any(PagoRequest.class)))
                .thenReturn(consumo());

        mockMvc.perform(post("/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"CONSUMO_BASICO\",\"categoria\":\"AGUA\",\"monto\":24200,\"fecha\":\"2026-11-12\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.registradoPorId").value(5));

        verify(pagoService).crear(eq(5L), eq("TESORERIA"), eq("token-de-prueba"), any(PagoRequest.class));
    }

    @Test
    void unErrorDelServicioLlegaConLaFormaDelContrato() throws Exception {
        como("1", "VECINO", "");
        when(pagoService.crear(eq(1L), eq(""), eq("token-de-prueba"), any(PagoRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo la Tesorería puede registrar pagos"));

        mockMvc.perform(post("/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"CONSUMO_BASICO\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Solo la Tesorería puede registrar pagos"))
                .andExpect(jsonPath("$.path").value("/pagos"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unMontoQueNoEsNumeroDa400SinMostrarTextosTecnicos() throws Exception {
        como("5", "DIRECTIVA", "TESORERIA");

        mockMvc.perform(post("/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"CONSUMO_BASICO\",\"categoria\":\"GAS\",\"monto\":\"mucho\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cuerpo de la petición no es válido"));

        verify(pagoService, never()).crear(any(), any(), any(), any());
    }

    @Test
    void unJsonRotoDa400() throws Exception {
        como("5", "DIRECTIVA", "TESORERIA");

        mockMvc.perform(post("/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cuerpo de la petición no es válido"));
    }

    @Test
    void obtenerUnPagoPorSuIdDevuelve200() throws Exception {
        como("5", "DIRECTIVA", "TESORERIA");
        when(pagoService.buscar(7L, "DIRECTIVA")).thenReturn(consumo());

        mockMvc.perform(get("/pagos/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.categoria").value("AGUA"));
    }

    @Test
    void unPagoQueNoExisteDa404() throws Exception {
        como("5", "DIRECTIVA", "TESORERIA");
        when(pagoService.buscar(99L, "DIRECTIVA"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese pago no existe"));

        mockMvc.perform(get("/pagos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ese pago no existe"));
    }

    @Test
    void editarDevuelve200ConElPagoCorregido() throws Exception {
        como("5", "DIRECTIVA", "TESORERIA");
        Pago corregido = new Pago(7L, null, "CONSUMO_BASICO", "GAS", 30000, "2026-11-12", null, 5L, "");
        when(pagoService.actualizar(eq(7L), eq(5L), eq("TESORERIA"), any(PagoRequest.class))).thenReturn(corregido);

        mockMvc.perform(put("/pagos/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoria\":\"GAS\",\"monto\":30000,\"fecha\":\"2026-11-12\",\"motivo\":\"Boleta corregida\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monto").value(30000))
                .andExpect(jsonPath("$.categoria").value("GAS"));
    }

    @Test
    void eliminarDevuelve204SinCuerpo() throws Exception {
        como("5", "DIRECTIVA", "TESORERIA");

        mockMvc.perform(delete("/pagos/7").param("motivo", "Pago duplicado por error"))
                .andExpect(status().isNoContent());

        verify(pagoService).eliminar(7L, 5L, "TESORERIA", "Pago duplicado por error");
    }

    @Test
    void eliminarSinSerTesoreriaDa403() throws Exception {
        como("4", "DIRECTIVA", "SECRETARIA");
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo la Tesorería puede eliminar pagos"))
                .when(pagoService).eliminar(7L, 4L, "SECRETARIA", null);

        mockMvc.perform(delete("/pagos/7"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Solo la Tesorería puede eliminar pagos"));
    }

    @Test
    void elHistorialDevuelve200ParaCualquierSesion() throws Exception {
        como("1", "VECINO", "");
        when(pagoService.listarHistorial("VECINO")).thenReturn(List.of(new HistorialPago(3L, 7L, "ELIMINADO",
                "CONSUMO_BASICO", "AGUA", 24200, null, "2026-11-12", "Pago duplicado por error", "TESORERIA", null,
                "2026-11-19T21:00:00")));

        mockMvc.perform(get("/pagos/historial"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accion").value("ELIMINADO"))
                .andExpect(jsonPath("$[0].motivo").value("Pago duplicado por error"))
                .andExpect(jsonPath("$[0].usuarioId").isEmpty());
    }
}
