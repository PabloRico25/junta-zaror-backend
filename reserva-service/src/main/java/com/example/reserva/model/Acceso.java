package com.example.reserva.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "accesos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Acceso {

    // La clave primaria es la reserva: una reserva solo puede entrar una vez.
    @Id
    private Long reservaId;

    @Column(nullable = false)
    private String fecha;

    @Column(nullable = false)
    private String hora;
}