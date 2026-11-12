package com.example.auth.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, unique = true)
    private String correo;

    // Se guarda el hash SHA-256 de la clave, nunca la clave.
    @Column(nullable = false)
    private String clave;

    // VECINO o DIRECTIVA
    @Column(nullable = false)
    private String rol;

    // SECRETARIA, TESORERIA, PRESIDENCIA o vacío para un vecino
    @Column(nullable = false)
    private String cargo;
}