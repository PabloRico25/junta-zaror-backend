package com.example.auth.dto;

import lombok.Data;

@Data
public class LoginRequest {

    private String correo;
    private String clave;

    // Opcional. Con "PUERTA" el token dura 7 días y solo lo puede pedir la directiva.
    private String dispositivo;
}