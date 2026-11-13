package com.example.auth.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

// Forma de todos los errores del servicio: la que pide el contrato de la API
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {

    private OffsetDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
}