package com.example.auth;

import com.example.auth.model.Usuario;
import com.example.auth.repository.UsuarioRepository;
import com.example.auth.service.HashService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final HashService hashService;

    public DataLoader(UsuarioRepository usuarioRepository, HashService hashService) {
        this.usuarioRepository = usuarioRepository;
        this.hashService = hashService;
    }

    // Carga los 6 usuarios de ejemplo solo si la tabla está vacía.
    // El orden importa: los ids 1 a 6 son los que usan las reservas y los pagos de ejemplo.
    @Override
    public void run(String... args) {
        if (usuarioRepository.count() > 0) {
            return;
        }

        String clave = hashService.sha256("1234");

        usuarioRepository.save(new Usuario(null, "Juan Pérez", "juan.perez@zaror.test", clave, "VECINO", ""));
        usuarioRepository.save(new Usuario(null, "Ana Muñoz", "ana.munoz@zaror.test", clave, "VECINO", ""));
        usuarioRepository.save(new Usuario(null, "Pedro Soto", "pedro.soto@zaror.test", clave, "VECINO", ""));
        usuarioRepository.save(new Usuario(null, "María Torres", "maria.torres@zaror.test", clave, "DIRECTIVA", "SECRETARIA"));
        usuarioRepository.save(new Usuario(null, "Luis Rojas", "luis.rojas@zaror.test", clave, "DIRECTIVA", "TESORERIA"));
        usuarioRepository.save(new Usuario(null, "Carmen Vidal", "carmen.vidal@zaror.test", clave, "DIRECTIVA", "PRESIDENCIA"));
    }
}