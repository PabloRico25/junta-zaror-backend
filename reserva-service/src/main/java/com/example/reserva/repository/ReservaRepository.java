package com.example.reserva.repository;

import com.example.reserva.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // Cuenta las reservas CONFIRMADAS de la misma sala y día que se cruzan con el horario pedido.
    // Dos horarios se cruzan si inicioA < finB y inicioB < finA.
    // excluirId evita que una reserva choque consigo misma al confirmarla (al crear se pasa 0).
    @Query("select count(r) from Reserva r "
            + "where r.dependenciaId = :dependenciaId and r.fecha = :fecha "
            + "and r.estado = 'CONFIRMADA' and r.id <> :excluirId "
            + "and r.horaInicio < :horaFin and :horaInicio < r.horaFin")
    long contarChoques(@Param("dependenciaId") Long dependenciaId,
                       @Param("fecha") String fecha,
                       @Param("horaInicio") String horaInicio,
                       @Param("horaFin") String horaFin,
                       @Param("excluirId") Long excluirId);
}