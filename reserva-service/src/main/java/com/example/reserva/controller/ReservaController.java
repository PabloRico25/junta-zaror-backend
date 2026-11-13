package com.example.reserva.controller;

import com.example.reserva.dto.ReservaRequest;
import com.example.reserva.model.Reserva;
import com.example.reserva.service.ReservaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
@RequestMapping("/reservas")
public class ReservaController {

    private final ReservaService reservaService;

    public ReservaController(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    // El token ya fue verificado. Su sub es el id del usuario y su claim rol dice VECINO o DIRECTIVA.
    @GetMapping
    public ResponseEntity<List<Reserva>> listar(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(required = false) String estado) {
        Long usuarioId = Long.valueOf(jwt.getSubject());
        String rol = jwt.getClaimAsString("rol");
        return ResponseEntity.ok(reservaService.listar(usuarioId, rol, estado));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Reserva> obtener(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long usuarioId = Long.valueOf(jwt.getSubject());
        String rol = jwt.getClaimAsString("rol");
        return ResponseEntity.ok(reservaService.obtener(id, usuarioId, rol));
    }

    // Un vecino pide una reserva; queda PENDIENTE hasta que la Secretaría la confirme
    @PostMapping
    public ResponseEntity<Reserva> crear(@AuthenticationPrincipal Jwt jwt,
                                         @RequestBody ReservaRequest pedido) {
        Long usuarioId = Long.valueOf(jwt.getSubject());
        String rol = jwt.getClaimAsString("rol");
        Reserva creada = reservaService.crear(usuarioId, rol, pedido);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    // El dueño cancela su reserva pendiente
    @PutMapping("/{id}/cancelar")
    public ResponseEntity<Reserva> cancelar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long usuarioId = Long.valueOf(jwt.getSubject());
        return ResponseEntity.ok(reservaService.cancelar(id, usuarioId));
    }

    // Solo la Secretaría (cargo SECRETARIA) confirma
    @PutMapping("/{id}/confirmar")
    public ResponseEntity<Reserva> confirmar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long usuarioId = Long.valueOf(jwt.getSubject());
        String cargo = jwt.getClaimAsString("cargo");
        return ResponseEntity.ok(reservaService.confirmar(id, usuarioId, cargo));
    }

    // Solo la Secretaría (cargo SECRETARIA) rechaza
    @PutMapping("/{id}/rechazar")
    public ResponseEntity<Reserva> rechazar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long usuarioId = Long.valueOf(jwt.getSubject());
        String cargo = jwt.getClaimAsString("cargo");
        return ResponseEntity.ok(reservaService.rechazar(id, usuarioId, cargo));
    }
}