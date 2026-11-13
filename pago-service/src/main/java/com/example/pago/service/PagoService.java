package com.example.pago.service;

import com.example.pago.dto.PagoRequest;
import com.example.pago.model.Pago;
import com.example.pago.repository.PagoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

@Service
public class PagoService {

    private final PagoRepository pagoRepository;
    private final WebClient webClient;
    private final String rutaConfirmada;

    public PagoService(PagoRepository pagoRepository, WebClient webClient,
                       @Value("${api.reserva.confirmada}") String rutaConfirmada) {
        this.pagoRepository = pagoRepository;
        this.webClient = webClient;
        this.rutaConfirmada = rutaConfirmada;
    }

    // Todos los pagos, los más nuevos primero. La directiva los ve completos; el vecino ve montos y
    // categorías, pero no la reserva, el comprobante ni quién lo registró. Se arman copias nuevas
    // para no tocar los datos guardados.
    public List<Pago> listar(String rol) {
        List<Pago> pagos = pagoRepository.findAllByOrderByFechaDescIdDesc();

        if ("DIRECTIVA".equals(rol)) {
            return pagos;
        }

        return pagos.stream()
                .map(p -> new Pago(p.getId(), null, p.getTipo(), p.getCategoria(), p.getMonto(),
                        p.getFecha(), null, null, ""))
                .toList();
    }

    // Ids de las reservas que ya tienen un pago de arriendo
    public List<Long> reservasPagadas() {
        return pagoRepository.findByTipo("ARRIENDO").stream()
                .map(Pago::getReservaId)
                .filter(Objects::nonNull)
                .toList();
    }

    // Registra un pago. Solo Tesorería. Un arriendo exige una reserva confirmada (se le pregunta a
    // reserva-service con el mismo token) y no puede repetirse para la misma reserva.
    public Pago crear(Long usuarioId, String cargo, String token, PagoRequest pedido) {
        if (!"TESORERIA".equals(cargo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo la Tesorería puede registrar pagos");
        }

        String tipo = pedido.getTipo();
        if (!"ARRIENDO".equals(tipo) && !"CONSUMO_BASICO".equals(tipo)) {
            throw invalido("Elige el tipo de pago");
        }

        Long reservaId = null;
        String categoria = "";
        if ("ARRIENDO".equals(tipo)) {
            if (pedido.getReservaId() == null) {
                throw invalido("Elige la reserva que se paga");
            }
            reservaId = pedido.getReservaId();
        } else {
            categoria = pedido.getCategoria() == null ? "" : pedido.getCategoria();
            if (!List.of("ELECTRICIDAD", "AGUA", "GAS").contains(categoria)) {
                throw invalido("Elige la categoría del gasto");
            }
        }

        Long monto = pedido.getMonto();
        if (monto == null) {
            throw invalido("Escribe el monto");
        }
        if (monto <= 0) {
            throw invalido("El monto debe ser mayor que 0");
        }
        if (monto > 999999999L) {
            throw invalido("El monto es demasiado grande");
        }

        LocalDate hoy = LocalDate.now(ZoneId.of("America/Santiago"));
        LocalDate fecha = leerFecha(pedido.getFecha());
        if (fecha.isAfter(hoy)) {
            throw invalido("La fecha del pago no puede ser futura");
        }

        String descripcion = pedido.getDescripcion() == null ? "" : pedido.getDescripcion().trim();
        if (descripcion.length() > 200) {
            throw invalido("La descripción es muy larga");
        }
        String comprobante = pedido.getComprobanteUri();
        if (comprobante != null && comprobante.isBlank()) {
            comprobante = null;
        }
        if (comprobante != null && comprobante.length() > 250) {
            throw invalido("El comprobante es muy largo");
        }

        if (reservaId != null) {
            if (!reservaConfirmada(reservaId, token)) {
                throw invalido("Elige la reserva que se paga");
            }
            if (pagoRepository.existsByReservaIdAndTipo(reservaId, "ARRIENDO")) {
                throw yaPagada();
            }
        }

        Pago pago = new Pago(null, reservaId, tipo, categoria, monto.intValue(), fecha.toString(),
                comprobante, usuarioId, descripcion);

        // Si dos pagos de la misma reserva llegan a la vez, la base solo deja pasar uno (reservaId es único)
        try {
            return pagoRepository.saveAndFlush(pago);
        } catch (DataIntegrityViolationException e) {
            throw yaPagada();
        }
    }

    // Le pregunta a reserva-service, por el gateway, si la reserva existe y está confirmada.
    // Reenvía el token de quien llama. Si el otro servicio no responde, es un error 503.
    private boolean reservaConfirmada(Long reservaId, String token) {
        try {
            Boolean confirmada = webClient.get()
                    .uri(String.format(rutaConfirmada, reservaId))
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            return Boolean.TRUE.equals(confirmada);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo verificar la reserva. Intenta de nuevo");
        }
    }

    private ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }

    private ResponseStatusException yaPagada() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Esa reserva ya tiene un pago de arriendo");
    }

    // Convierte "2026-11-20" en fecha. Si viene vacía o mal escrita, es un error 400.
    private LocalDate leerFecha(String texto) {
        if (texto == null || texto.isBlank()) {
            throw invalido("Elige la fecha del pago");
        }
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            throw invalido("Elige la fecha del pago");
        }
    }
}