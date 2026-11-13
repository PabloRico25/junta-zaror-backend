package com.example.reserva.service;

import com.example.reserva.dto.Bloque;
import com.example.reserva.dto.ReservaRequest;
import com.example.reserva.model.Reserva;
import com.example.reserva.repository.DependenciaRepository;
import com.example.reserva.repository.ReservaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class ReservaService {

    private final ReservaRepository reservaRepository;
    private final DependenciaRepository dependenciaRepository;

    public ReservaService(ReservaRepository reservaRepository,
                          DependenciaRepository dependenciaRepository) {
        this.reservaRepository = reservaRepository;
        this.dependenciaRepository = dependenciaRepository;
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
        Reserva reserva = buscar(id);

        boolean esDirectiva = "DIRECTIVA".equals(rol);
        boolean esDuena = reserva.getVecinoId().equals(usuarioId);
        if (!esDirectiva && !esDuena) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La reserva no existe");
        }
        return reserva;
    }

    // Crea una reserva PENDIENTE. El vecino sale del token; el estado y la fecha de solicitud los pone el servidor.
    public Reserva crear(Long vecinoId, String rol, ReservaRequest pedido) {
        if (!"VECINO".equals(rol)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un vecino puede pedir una reserva");
        }

        if (pedido.getDependenciaId() == null
                || !dependenciaRepository.existsById(pedido.getDependenciaId())) {
            throw pedidoInvalido("Elige una dependencia");
        }

        LocalDate hoy = LocalDate.now(ZoneId.of("America/Santiago"));
        LocalDate fecha = leerFecha(pedido.getFecha());
        if (fecha.isBefore(hoy)) {
            throw pedidoInvalido("La fecha no puede ser pasada");
        }

        int inicio = leerHora(pedido.getHoraInicio(), "Elige la hora de inicio");
        int fin = leerHora(pedido.getHoraFin(), "Elige la hora de fin");
        if (fin <= inicio) {
            throw pedidoInvalido("La hora de fin debe ser mayor que la de inicio");
        }

        long choques = reservaRepository.contarChoques(pedido.getDependenciaId(), fecha.toString(),
                pedido.getHoraInicio(), pedido.getHoraFin(), 0L);
        if (choques > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ese horario ya está ocupado por una reserva confirmada");
        }

        Reserva reserva = new Reserva(null, pedido.getDependenciaId(), vecinoId, fecha.toString(),
                pedido.getHoraInicio(), pedido.getHoraFin(), "PENDIENTE", hoy.toString(), null);
        return reservaRepository.save(reserva);
    }

    // Solo el dueño puede cancelar, y solo mientras la reserva está PENDIENTE.
    public Reserva cancelar(Long id, Long usuarioId) {
        Reserva reserva = buscar(id);

        if (!reserva.getVecinoId().equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La reserva no existe");
        }
        if (!"PENDIENTE".equals(reserva.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede cancelar una reserva pendiente");
        }

        reserva.setEstado("CANCELADA");
        return reservaRepository.save(reserva);
    }

    // Solo la Secretaría confirma. Antes de confirmar se vuelve a revisar el choque,
    // porque otra reserva pudo confirmarse mientras esta esperaba.
    public Reserva confirmar(Long id, Long usuarioId, String cargo) {
        if (!"SECRETARIA".equals(cargo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo la Secretaría puede confirmar reservas");
        }

        Reserva reserva = buscar(id);
        if (!"PENDIENTE".equals(reserva.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede confirmar una reserva pendiente");
        }

        long choques = reservaRepository.contarChoques(reserva.getDependenciaId(), reserva.getFecha(),
                reserva.getHoraInicio(), reserva.getHoraFin(), reserva.getId());
        if (choques > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede confirmar: ese horario ya está ocupado por una reserva confirmada");
        }

        reserva.setEstado("CONFIRMADA");
        reserva.setGestionadaPorId(usuarioId);
        return reservaRepository.save(reserva);
    }

    // Solo la Secretaría rechaza, y solo una reserva PENDIENTE.
    public Reserva rechazar(Long id, Long usuarioId, String cargo) {
        if (!"SECRETARIA".equals(cargo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo la Secretaría puede rechazar reservas");
        }

        Reserva reserva = buscar(id);
        if (!"PENDIENTE".equals(reserva.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede rechazar una reserva pendiente");
        }

        reserva.setEstado("RECHAZADA");
        reserva.setGestionadaPorId(usuarioId);
        return reservaRepository.save(reserva);
    }

    // Bloques ocupados (CONFIRMADA y PENDIENTE) de una sala en un día.
    // El vecino ve solo las horas; el id del vecino solo lo ve la directiva.
    public List<Bloque> ocupacion(Long dependenciaId, String fecha, String rol) {
        if (dependenciaId == null || !dependenciaRepository.existsById(dependenciaId)) {
            throw pedidoInvalido("Elige una dependencia");
        }
        LocalDate dia = leerFecha(fecha);

        boolean esDirectiva = "DIRECTIVA".equals(rol);
        return reservaRepository
                .findByDependenciaIdAndFechaAndEstadoInOrderByHoraInicioAsc(
                        dependenciaId, dia.toString(), List.of("CONFIRMADA", "PENDIENTE"))
                .stream()
                .map(r -> new Bloque(r.getHoraInicio(), r.getHoraFin(), r.getEstado(),
                        esDirectiva ? r.getVecinoId() : null))
                .toList();
    }

    // Lo usa pago-service: dice si la reserva existe y está confirmada.
    public boolean estaConfirmada(Long id) {
        return reservaRepository.findById(id)
                .map(r -> "CONFIRMADA".equals(r.getEstado()))
                .orElse(false);
    }

    private Reserva buscar(Long id) {
        return reservaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La reserva no existe"));
    }

    private ResponseStatusException pedidoInvalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }

    // Convierte "2026-11-20" en fecha. Si viene vacía o mal escrita, es un error 400.
    private LocalDate leerFecha(String texto) {
        if (texto == null || texto.isBlank()) {
            throw pedidoInvalido("Elige la fecha");
        }
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            throw pedidoInvalido("Elige la fecha");
        }
    }

    // La hora debe ser en punto ("18:00") y estar entre 09:00 y 22:00. Devuelve solo el número de la hora.
    private int leerHora(String texto, String mensaje) {
        if (texto == null || texto.length() != 5 || !texto.endsWith(":00")) {
            throw pedidoInvalido(mensaje);
        }
        int hora;
        try {
            hora = Integer.parseInt(texto.substring(0, 2));
        } catch (NumberFormatException e) {
            throw pedidoInvalido(mensaje);
        }
        if (hora < 9 || hora > 22) {
            throw pedidoInvalido(mensaje);
        }
        return hora;
    }
}