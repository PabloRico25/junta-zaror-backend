package com.example.pago;

import com.example.pago.model.Pago;
import com.example.pago.repository.PagoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class DataLoader implements CommandLineRunner {

    private final PagoRepository pagoRepository;

    public DataLoader(PagoRepository pagoRepository) {
        this.pagoRepository = pagoRepository;
    }

    // Carga los 6 pagos de ejemplo solo si la tabla está vacía.
    // Las reservas 1, 7 y 8 son las de reserva-service; el id 5 es Luis Rojas, de Tesorería, en auth-service.
    @Override
    public void run(String... args) {
        if (pagoRepository.count() > 0) {
            return;
        }

        pagoRepository.save(arriendo(8L, 60000, -2));
        pagoRepository.save(consumo("ELECTRICIDAD", 38500, -2));
        pagoRepository.save(arriendo(7L, 40000, -3));
        pagoRepository.save(consumo("AGUA", 24200, -4));
        pagoRepository.save(consumo("GAS", 24000, -5));
        pagoRepository.save(arriendo(1L, 45000, -1));
    }

    private Pago arriendo(Long reservaId, int monto, int dias) {
        return new Pago(null, reservaId, "ARRIENDO", "", monto, fechaEn(dias), null, 5L, "");
    }

    private Pago consumo(String categoria, int monto, int dias) {
        return new Pago(null, null, "CONSUMO_BASICO", categoria, monto, fechaEn(dias), null, 5L, "");
    }

    private String fechaEn(int dias) {
        return LocalDate.now(ZoneId.of("America/Santiago")).plusDays(dias).toString();
    }
}