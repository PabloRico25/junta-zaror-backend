package com.example.reserva.dto;

import lombok.Data;

// Lo que la app envía al pedir una reserva. El vecino, el estado y la fecha
// de solicitud los completa el servidor.
@Data
public class ReservaRequest {

    private Long dependenciaId;

    // Formato año-mes-día, por ejemplo "2026-11-20"
    private String fecha;

    // Formato "18:00"
    private String horaInicio;
    private String horaFin;
}