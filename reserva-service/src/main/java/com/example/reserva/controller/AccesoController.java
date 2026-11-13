package com.example.reserva.controller;

import com.example.reserva.model.Acceso;
import com.example.reserva.service.AccesoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/accesos")
public class AccesoController {

    private final AccesoService accesoService;

    public AccesoController(AccesoService accesoService) {
        this.accesoService = accesoService;
    }

    // Ids de las reservas que ya entraron, para marcar sus códigos como usados
    @GetMapping("/usadas")
    public ResponseEntity<List<Long>> usadas(@AuthenticationPrincipal Jwt jwt) {
        String rol = jwt.getClaimAsString("rol");
        return ResponseEntity.ok(accesoService.usadas(rol));
    }

    // Registra el ingreso de una reserva; si ya entró, responde 409
    @PostMapping
    public ResponseEntity<Acceso> registrar(@AuthenticationPrincipal Jwt jwt,
                                            @RequestBody Acceso pedido) {
        String rol = jwt.getClaimAsString("rol");
        Acceso registrado = accesoService.registrar(rol, pedido);
        return ResponseEntity.status(HttpStatus.CREATED).body(registrado);
    }
}