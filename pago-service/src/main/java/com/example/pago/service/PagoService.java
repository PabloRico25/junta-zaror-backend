package com.example.pago.service;

import com.example.pago.dto.PagoRequest;
import com.example.pago.model.HistorialPago;
import com.example.pago.model.Pago;
import com.example.pago.repository.HistorialPagoRepository;
import com.example.pago.repository.PagoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

@Service
public class PagoService {

    private static final Logger logger = LoggerFactory.getLogger(PagoService.class);

    private final PagoRepository pagoRepository;
    private final HistorialPagoRepository historialRepository;
    private final WebClient webClient;
    private final String rutaConfirmada;

    public PagoService(PagoRepository pagoRepository, HistorialPagoRepository historialRepository,
                       WebClient webClient, @Value("${api.reserva.confirmada}") String rutaConfirmada) {
        this.pagoRepository = pagoRepository;
        this.historialRepository = historialRepository;
        this.webClient = webClient;
        this.rutaConfirmada = rutaConfirmada;
    }

    // El historial de cambios, lo más nuevo primero. Lo ve cualquier sesión: es la transparencia hacia los
    // vecinos. A un vecino no le llega el id de la persona, solo su cargo.
    public List<HistorialPago> listarHistorial(String rol) {
        List<HistorialPago> historial = historialRepository.findAllByOrderByIdDesc();
        if ("DIRECTIVA".equals(rol)) {
            return historial;
        }
        return historial.stream()
                .map(h -> new HistorialPago(h.getId(), h.getPagoId(), h.getAccion(), h.getTipo(), h.getCategoria(),
                        h.getMontoAnterior(), h.getMontoNuevo(), h.getFechaPago(), h.getMotivo(), h.getCargo(),
                        null, h.getFechaHora()))
                .toList();
    }

    // Todos los pagos, los más nuevos primero. El vecino los ve sin reserva, comprobante ni quién los registró.
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
        logger.info("Registrando pago: usuarioId={}", usuarioId);
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

        int monto = validarMonto(pedido.getMonto());
        LocalDate fecha = validarFecha(pedido.getFecha());
        String descripcion = validarDescripcion(pedido.getDescripcion());
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

        Pago pago = new Pago(null, reservaId, tipo, categoria, monto, fecha.toString(),
                comprobante, usuarioId, descripcion);

        // Si dos pagos de la misma reserva llegan a la vez, la base solo deja pasar uno (reservaId es único)
        Pago guardado;
        try {
            guardado = pagoRepository.saveAndFlush(pago);
        } catch (DataIntegrityViolationException e) {
            throw yaPagada();
        }
        anotar(guardado, "REGISTRADO", null, guardado.getMonto(), "", cargo, usuarioId);
        return guardado;
    }

    // Un pago por su id. Al vecino le llega sin reserva, comprobante ni quién lo registró, como en la lista.
    public Pago buscar(Long id, String rol) {
        Pago pago = pagoRepository.findById(id).orElseThrow(this::noExiste);
        if ("DIRECTIVA".equals(rol)) {
            return pago;
        }
        return new Pago(pago.getId(), null, pago.getTipo(), pago.getCategoria(), pago.getMonto(),
                pago.getFecha(), null, null, "");
    }

    // Corrige un pago ya registrado. Solo Tesorería, y con un motivo que queda en el historial. Se pueden cambiar
    // el monto, la fecha, la descripción y, en un consumo, la categoría. El tipo y la reserva no cambian: para eso
    // se elimina y se registra de nuevo.
    @Transactional
    public Pago actualizar(Long id, Long usuarioId, String cargo, PagoRequest pedido) {
        logger.info("Actualizando pago: id={}", id);
        if (!"TESORERIA".equals(cargo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo la Tesorería puede editar pagos");
        }
        String motivo = validarMotivo(pedido.getMotivo());
        Pago pago = pagoRepository.findById(id).orElseThrow(this::noExiste);
        int montoAnterior = pago.getMonto();

        if ("CONSUMO_BASICO".equals(pago.getTipo())) {
            String categoria = pedido.getCategoria() == null ? "" : pedido.getCategoria();
            if (!List.of("ELECTRICIDAD", "AGUA", "GAS").contains(categoria)) {
                throw invalido("Elige la categoría del gasto");
            }
            pago.setCategoria(categoria);
        }
        pago.setMonto(validarMonto(pedido.getMonto()));
        pago.setFecha(validarFecha(pedido.getFecha()).toString());
        pago.setDescripcion(validarDescripcion(pedido.getDescripcion()));
        Pago guardado = pagoRepository.save(pago);
        anotar(guardado, "EDITADO", montoAnterior, guardado.getMonto(), motivo, cargo, usuarioId);
        return guardado;
    }

    // Elimina un pago mal registrado. Solo Tesorería, y con un motivo que queda en el historial.
    // Si era un arriendo, la reserva vuelve a quedar por cobrar.
    @Transactional
    public void eliminar(Long id, Long usuarioId, String cargo, String motivoTexto) {
        logger.info("Eliminando pago: id={}", id);
        if (!"TESORERIA".equals(cargo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo la Tesorería puede eliminar pagos");
        }
        String motivo = validarMotivo(motivoTexto);
        Pago pago = pagoRepository.findById(id).orElseThrow(this::noExiste);
        pagoRepository.deleteById(id);
        anotar(pago, "ELIMINADO", pago.getMonto(), null, motivo, cargo, usuarioId);
    }

    // Deja la huella del cambio en el historial
    private void anotar(Pago pago, String accion, Integer montoAnterior, Integer montoNuevo, String motivo,
                        String cargo, Long usuarioId) {
        String ahora = LocalDateTime.now(ZoneId.of("America/Santiago")).withNano(0).toString();
        historialRepository.save(new HistorialPago(null, pago.getId(), accion, pago.getTipo(), pago.getCategoria(),
                montoAnterior, montoNuevo, pago.getFecha(), motivo, cargo, usuarioId, ahora));
    }

    // Editar o eliminar exige explicar por qué: es lo que verá la comunidad
    private String validarMotivo(String texto) {
        String motivo = texto == null ? "" : texto.trim();
        if (motivo.length() < 10) {
            throw invalido("Escribe el motivo del cambio (al menos 10 letras)");
        }
        if (motivo.length() > 200) {
            throw invalido("El motivo es muy largo");
        }
        return motivo;
    }

    // ---------- validaciones que comparten registrar y editar ----------

    private int validarMonto(Long monto) {
        if (monto == null) {
            throw invalido("Escribe el monto");
        }
        if (monto <= 0) {
            throw invalido("El monto debe ser mayor que 0");
        }
        if (monto > 999999999L) {
            throw invalido("El monto es demasiado grande");
        }
        return monto.intValue();
    }

    private LocalDate validarFecha(String texto) {
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Santiago"));
        LocalDate fecha = leerFecha(texto);
        if (fecha.isAfter(hoy)) {
            throw invalido("La fecha del pago no puede ser futura");
        }
        return fecha;
    }

    private String validarDescripcion(String texto) {
        String descripcion = texto == null ? "" : texto.trim();
        if (descripcion.length() > 200) {
            throw invalido("La descripción es muy larga");
        }
        return descripcion;
    }

    private ResponseStatusException noExiste() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese pago no existe");
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