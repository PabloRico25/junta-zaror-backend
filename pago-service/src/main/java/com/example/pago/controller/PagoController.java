package com.example.pago.controller;

import com.example.pago.model.Pago;
import com.example.pago.service.PagoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}