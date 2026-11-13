package com.example.pago.controller;

import com.example.pago.dto.PagoRequest;
import com.example.pago.exception.GlobalExceptionHandler;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}