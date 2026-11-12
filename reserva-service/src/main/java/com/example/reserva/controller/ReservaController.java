package com.example.reserva.controller;

import com.example.reserva.model.Reserva;
import com.example.reserva.service.ReservaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}