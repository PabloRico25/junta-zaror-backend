package com.example.reserva.service;

import com.example.reserva.model.Reserva;
import com.example.reserva.repository.ReservaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ReservaService {

    private final ReservaRepository reservaRepository;

    public ReservaService(ReservaRepository reservaRepository) {
        this.reservaRepository = reservaRepository;
    }

    // La directiva ve todas las reservas; el vecino, solo las suyas.
    // El estado es opcional: si viene vacío no se filtra.
    public List<Reserva> listar(Long usuarioId, String rol, String estado) {
        boolean filtrar = estado != null && !estado.isBlank();

        if ("DIRECTIVA".equals(rol)) {
            if (filtrar) {
                return reservaRepository.findByEstadoOrderByFechaAscHoraInicioAsc(estado);
            }
            return reservaRepository.findAllByOrderByFechaAscHoraInicioAsc();
        }

        if (filtrar) {
            return reservaRepository.findByVecinoIdAndEstadoOrderByFechaAscHoraInicioAsc(usuarioId, estado);
        }
        return reservaRepository.findByVecinoIdOrderByFechaAscHoraInicioAsc(usuarioId);
    }

    // La puede ver su dueño o la directiva. A un vecino que pide la de otro se le dice
    // que no existe, para no revelar que esa reserva está en el sistema.
    public Reserva obtener(Long id, Long usuarioId, String rol) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La reserva no existe"));

        boolean esDirectiva = "DIRECTIVA".equals(rol);
        boolean esDuena = reserva.getVecinoId().equals(usuarioId);
        if (!esDirectiva && !esDuena) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La reserva no existe");
        }
        return reserva;
    }
}