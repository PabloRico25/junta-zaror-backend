package com.example.pago.model;

import jakarta.persistence.*;
import lombok.*;

// Registro de cada cambio en los pagos: quién lo hizo (por cargo), qué cambió y por qué. Lo escribe solo
// el servidor al registrar, editar o eliminar un pago, y no hay ninguna ruta para editarlo ni borrarlo:
// así la comunidad puede revisar lo que hizo la Tesorería, aunque el pago ya no exista.
@Entity
@Table(name = "historial_pago")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistorialPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Id del pago afectado. No es una clave foránea: el pago puede haberse eliminado.
    @Column(nullable = false)
    private Long pagoId;

    // REGISTRADO, EDITADO o ELIMINADO
    @Column(nullable = false)
    private String accion;

    // ARRIENDO o CONSUMO_BASICO, y la categoría de un consumo ("" en un arriendo)
    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false)
    private String categoria = "";

    // Monto antes y después del cambio. Al registrar no hay anterior; al eliminar no hay nuevo.
    private Integer montoAnterior;

    private Integer montoNuevo;

    // Fecha del pago (año-mes-día)
    @Column(nullable = false)
    private String fechaPago;

    // Por qué se editó o eliminó. Obligatorio en esos casos; vacío al registrar.
    @Column(nullable = false, length = 200)
    private String motivo = "";

    // Cargo de quien hizo el cambio (TESORERIA). Es lo que ven los vecinos.
    @Column(nullable = false)
    private String cargo;

    // Id de la persona. Solo lo recibe la directiva.
    private Long usuarioId;

    // Cuándo se hizo el cambio, hora de Santiago (año-mes-díaThora:minuto:segundo)
    @Column(nullable = false)
    private String fechaHora;
}
