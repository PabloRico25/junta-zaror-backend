package com.example.reserva;

import com.example.reserva.model.Dependencia;
import com.example.reserva.model.Reserva;
import com.example.reserva.repository.DependenciaRepository;
import com.example.reserva.repository.ReservaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class DataLoader implements CommandLineRunner {

    private final DependenciaRepository dependenciaRepository;
    private final ReservaRepository reservaRepository;

    public DataLoader(DependenciaRepository dependenciaRepository, ReservaRepository reservaRepository) {
        this.dependenciaRepository = dependenciaRepository;
        this.reservaRepository = reservaRepository;
    }

    // Carga las 2 dependencias y las 8 reservas de ejemplo solo si la tabla de dependencias está vacía.
    // Los ids de vecino (1 y 3) y de la Secretaria (4) son los de los usuarios de auth-service.
    @Override
    public void run(String... args) {
        if (dependenciaRepository.count() > 0) {
            return;
        }

        dependenciaRepository.save(new Dependencia(null, "Salón Principal"));
        dependenciaRepository.save(new Dependencia(null, "Sala Multiuso"));

        reservaRepository.save(nueva(1L, 1L, 11, "18:00", "21:00", "CONFIRMADA", -5, 4L));
        reservaRepository.save(nueva(2L, 1L, 12, "11:00", "14:00", "PENDIENTE", -2, null));
        reservaRepository.save(nueva(1L, 3L, 11, "19:00", "21:00", "PENDIENTE", -1, null));
        reservaRepository.save(nueva(2L, 3L, 25, "10:00", "13:00", "PENDIENTE", -1, null));
        reservaRepository.save(nueva(1L, 1L, 18, "15:00", "18:00", "RECHAZADA", -9, 4L));
        reservaRepository.save(nueva(2L, 1L, 4, "10:00", "12:00", "CANCELADA", -8, null));
        reservaRepository.save(nueva(2L, 3L, -3, "10:00", "13:00", "CONFIRMADA", -10, 4L));
        reservaRepository.save(nueva(1L, 3L, -3, "12:00", "16:00", "CONFIRMADA", -10, 4L));
    }

    // Arma una reserva: la fecha de la reserva y la de la solicitud se dan en días desde hoy.
    private Reserva nueva(Long dependenciaId, Long vecinoId, int diasHastaLaReserva,
                          String horaInicio, String horaFin, String estado,
                          int diasDesdeLaSolicitud, Long gestionadaPorId) {
        return new Reserva(null, dependenciaId, vecinoId, fechaEn(diasHastaLaReserva),
                horaInicio, horaFin, estado, fechaEn(diasDesdeLaSolicitud), gestionadaPorId);
    }

    private String fechaEn(int dias) {
        return LocalDate.now(ZoneId.of("America/Santiago")).plusDays(dias).toString();
    }
}