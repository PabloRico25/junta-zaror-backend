package com.example.reserva.controller;

import com.example.reserva.model.Dependencia;
import com.example.reserva.service.DependenciaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/dependencias")
public class DependenciaController {

    private final DependenciaService dependenciaService;

    public DependenciaController(DependenciaService dependenciaService) {
        this.dependenciaService = dependenciaService;
    }

    // Cualquier sesión con token válido puede ver las dependencias
    @GetMapping
    public ResponseEntity<List<Dependencia>> listar() {
        return ResponseEntity.ok(dependenciaService.listar());
    }
}