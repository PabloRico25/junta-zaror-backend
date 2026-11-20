package com.example.pago.repository;

import com.example.pago.model.HistorialPago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialPagoRepository extends JpaRepository<HistorialPago, Long> {

    // Todo el historial, lo más nuevo primero
    List<HistorialPago> findAllByOrderByIdDesc();
}
