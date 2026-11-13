package com.example.pago.service;

import com.example.pago.model.Pago;
import com.example.pago.repository.PagoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class PagoService {

    private final PagoRepository pagoRepository;

    public PagoService(PagoRepository pagoRepository) {
        this.pagoRepository = pagoRepository;
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
}