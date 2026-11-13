package com.example.pago.repository;

import com.example.pago.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    // Todos los pagos, los más nuevos primero
    List<Pago> findAllByOrderByFechaDescIdDesc();

    // Los pagos de un tipo, por ejemplo ARRIENDO
    List<Pago> findByTipo(String tipo);

    // Para no pagar dos veces el arriendo de una reserva
    boolean existsByReservaIdAndTipo(Long reservaId, String tipo);
}