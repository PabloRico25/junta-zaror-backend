package com.example.pago.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pagos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reserva que se paga. Vacío en los consumos básicos. Es único: un arriendo no se paga dos veces
    // (los consumos no tienen reserva y la base permite varios valores vacíos).
    @Column(unique = true)
    private Long reservaId;

    // ARRIENDO o CONSUMO_BASICO
    @Column(nullable = false)
    private String tipo;

    // ELECTRICIDAD, AGUA o GAS. Vacía ("") en un arriendo, nunca null: la app la declara no nula.
    @Column(nullable = false)
    private String categoria = "";

    // Monto en pesos
    @Column(nullable = false)
    private Integer monto;

    // Formato año-mes-día, por ejemplo "2026-11-20"
    @Column(nullable = false)
    private String fecha;

    // En el MVP solo se guarda la referencia que envía la app; el archivo no se sube
    private String comprobanteUri;

    // Id de la persona de Tesorería que lo registró (viene del token)
    @Column(nullable = false)
    private Long registradoPorId;

    // Vacía ("") si no hay nada, nunca null: la app la declara no nula.
    @Column(nullable = false)
    private String descripcion = "";
}