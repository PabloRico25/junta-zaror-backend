package com.example.reserva.service;

import com.example.reserva.model.Acceso;
import com.example.reserva.repository.AccesoRepository;
import com.example.reserva.repository.ReservaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AccesoService {

    private final AccesoRepository accesoRepository;
    private final ReservaRepository reservaRepository;

    public AccesoService(AccesoRepository accesoRepository, ReservaRepository reservaRepository) {
        this.accesoRepository = accesoRepository;
        this.reservaRepository = reservaRepository;
    }

    // Ids de las reservas que ya entraron por la puerta. Solo la directiva (o la puerta) lo consulta.
    public List<Long> usadas(String rol) {
        soloDirectiva(rol, "Solo la directiva puede ver los ingresos");
        return accesoRepository.findAll().stream()
                .map(Acceso::getReservaId)
                .toList();
    }

    // Registra el ingreso de una reserva. Un mismo código no puede entrar dos veces.
    public Acceso registrar(String rol, Acceso pedido) {
        soloDirectiva(rol, "Solo la directiva puede registrar ingresos");

        if (pedido.getReservaId() == null || !reservaRepository.existsById(pedido.getReservaId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La reserva no existe");
        }
        if (pedido.getFecha() == null || pedido.getFecha().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta la fecha del ingreso");
        }
        if (pedido.getHora() == null || pedido.getHora().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta la hora del ingreso");
        }

        // Hay que preguntar antes: con una clave ya asignada, guardar sobre una existente la sobrescribiría.
        if (accesoRepository.existsById(pedido.getReservaId())) {
            throw yaUsado();
        }

        // Si dos puertas llegan a la vez, la base solo deja pasar a una: la otra falla por la clave primaria.
        try {
            return accesoRepository.saveAndFlush(pedido);
        } catch (DataIntegrityViolationException e) {
            throw yaUsado();
        }
    }

    private void soloDirectiva(String rol, String mensaje) {
        if (!"DIRECTIVA".equals(rol)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, mensaje);
        }
    }

    private ResponseStatusException yaUsado() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Este código ya se usó");
    }
}