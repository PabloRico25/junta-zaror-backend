package com.example.reserva.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Un tramo de horario ocupado en una sala. El vecinoId solo se informa a la directiva.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Bloque {

    private String horaInicio;
    private String horaFin;
    private String estado;
    private Long vecinoId;
}