package com.example.reserva.repository;

import com.example.reserva.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    // Todas las reservas, de la fecha más próxima a la más lejana (para la directiva)
    List<Reserva> findAllByOrderByFechaAscHoraInicioAsc();

    // Solo las de un estado, por ejemplo PENDIENTE
    List<Reserva> findByEstadoOrderByFechaAscHoraInicioAsc(String estado);

    // Solo las de un vecino (para el vecino)
    List<Reserva> findByVecinoIdOrderByFechaAscHoraInicioAsc(Long vecinoId);

    // Las de un vecino con un estado
    List<Reserva> findByVecinoIdAndEstadoOrderByFechaAscHoraInicioAsc(Long vecinoId, String estado);
}