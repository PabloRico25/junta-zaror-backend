package com.example.reserva.service;

import com.example.reserva.model.Dependencia;
import com.example.reserva.repository.DependenciaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DependenciaService {

    private final DependenciaRepository dependenciaRepository;

    public DependenciaService(DependenciaRepository dependenciaRepository) {
        this.dependenciaRepository = dependenciaRepository;
    }

    public List<Dependencia> listar() {
        return dependenciaRepository.findAll();
    }
}