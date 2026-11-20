package com.example.pago.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.OffsetDateTime;

// Atrapa los errores y los devuelve siempre con la misma forma (ApiErrorResponse).
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Error imprevisto: se registra en el servidor y la app recibe un mensaje genérico
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneral(Exception ex, WebRequest request) {
        logger.error("Error no controlado: {}", ex.getMessage(), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error interno", request);
    }

    // Aquí llegan los errores de Spring y los ResponseStatusException
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        return construir(statusCode, mensajeDe(ex, statusCode), request);
    }

    // Se conservan nuestros mensajes; los técnicos de Spring se cambian
    private String mensajeDe(Exception ex, HttpStatusCode status) {
        if (ex instanceof ResponseStatusException rse && rse.getReason() != null) {
            return rse.getReason();
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return "El cuerpo de la petición no es válido";
        }
        return switch (status.value()) {
            case 400 -> "La petición no es válida";
            case 404 -> "La ruta no existe";
            case 405 -> "Ese método no está permitido en esta ruta";
            case 415 -> "El formato de la petición no es válido";
            default -> "No se pudo atender la petición";
        };
    }

    private ResponseEntity<Object> construir(HttpStatusCode status, String mensaje, WebRequest request) {
        HttpStatus http = HttpStatus.valueOf(status.value());
        String ruta = request instanceof ServletWebRequest web ? web.getRequest().getRequestURI() : "";
        ApiErrorResponse cuerpo = new ApiErrorResponse(OffsetDateTime.now(), http.value(),
                http.getReasonPhrase(), mensaje, ruta);
        return ResponseEntity.status(status).body(cuerpo);
    }
}