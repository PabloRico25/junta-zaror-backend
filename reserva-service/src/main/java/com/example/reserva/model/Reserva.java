package com.example.reserva.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "reservas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long dependenciaId;

    // Id del vecino, que viene del token. No se guarda el nombre: eso vive en auth-service.
    @Column(nullable = false)
    private Long vecinoId;

    // Formato año-mes-día, por ejemplo "2026-11-20"
    @Column(nullable = false)
    private String fecha;

    // Formato "18:00"
    @Column(nullable = false)
    private String horaInicio;

    @Column(nullable = false)
    private String horaFin;

    // PENDIENTE, CONFIRMADA, RECHAZADA o CANCELADA
    @Column(nullable = false)
    private String estado;

    @Column(nullable = false)
    private String fechaSolicitud;

    // Id de la persona de la directiva que confirmó o rechazó. Vacío mientras esté pendiente.
    private Long gestionadaPorId;
}