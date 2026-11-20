package com.example.pago.controller;

import com.example.pago.dto.PagoRequest;
import com.example.pago.model.HistorialPago;
import com.example.pago.model.Pago;
import com.example.pago.service.PagoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/pagos")
public class PagoController {

    private final PagoService pagoService;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    // Cualquier sesión puede ver los pagos; el vecino los recibe con los datos privados vacíos
    @GetMapping
    public ResponseEntity<List<Pago>> listar(@AuthenticationPrincipal Jwt jwt) {
        String rol = jwt.getClaimAsString("rol");
        return ResponseEntity.ok(pagoService.listar(rol));
    }

    // Ids de las reservas que ya tienen pago de arriendo
    @GetMapping("/pagadas")
    public ResponseEntity<List<Long>> pagadas() {
        return ResponseEntity.ok(pagoService.reservasPagadas());
    }

    // Solo Tesorería registra pagos. Se pasa el token para que pago-service pueda
    // preguntarle a reserva-service si la reserva está confirmada.
    @PostMapping
    public ResponseEntity<Pago> registrar(@AuthenticationPrincipal Jwt jwt,
                                          @RequestBody PagoRequest pedido) {
        Long usuarioId = Long.valueOf(jwt.getSubject());
        String cargo = jwt.getClaimAsString("cargo");
        Pago creado = pagoService.crear(usuarioId, cargo, jwt.getTokenValue(), pedido);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    // Un pago por su id
    @GetMapping("/{id}")
    public ResponseEntity<Pago> obtener(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok(pagoService.buscar(id, jwt.getClaimAsString("rol")));
    }

    // Solo Tesorería corrige un pago: monto, fecha, descripción y, en un consumo, la categoría.
    // El motivo es obligatorio y queda en el historial.
    @PutMapping("/{id}")
    public ResponseEntity<Pago> actualizar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                           @RequestBody PagoRequest pedido) {
        Long usuarioId = Long.valueOf(jwt.getSubject());
        return ResponseEntity.ok(pagoService.actualizar(id, usuarioId, jwt.getClaimAsString("cargo"), pedido));
    }

    // Solo Tesorería elimina un pago mal registrado, con el motivo en la dirección (?motivo=...).
    // Responde 204, sin cuerpo.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                         @RequestParam(required = false) String motivo) {
        Long usuarioId = Long.valueOf(jwt.getSubject());
        pagoService.eliminar(id, usuarioId, jwt.getClaimAsString("cargo"), motivo);
        return ResponseEntity.noContent().build();
    }

    // El historial de cambios de los pagos, para toda la comunidad
    @GetMapping("/historial")
    public ResponseEntity<List<HistorialPago>> historial(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(pagoService.listarHistorial(jwt.getClaimAsString("rol")));
    }
}