package com.example.reserva.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "dependencias")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Dependencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;
}
