package com.example.pago.dto;

import lombok.Data;

// Lo que la app envía al registrar un pago. El id y quién lo registró los completa el servidor.
@Data
public class PagoRequest {

    // Solo para los arriendos
    private Long reservaId;

    // ARRIENDO o CONSUMO_BASICO
    private String tipo;

    // Solo para los consumos: ELECTRICIDAD, AGUA o GAS
    private String categoria;

    private Long monto;

    // Formato año-mes-día
    private String fecha;

    private String comprobanteUri;
    private String descripcion;
}