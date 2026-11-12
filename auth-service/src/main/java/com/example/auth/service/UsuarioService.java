package com.example.auth.service;

import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.LoginResponse;
import com.example.auth.dto.UsuarioDTO;
import com.example.auth.model.Usuario;
import com.example.auth.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private HashService hashService;

    public LoginResponse login(LoginRequest pedido) {
        if (pedido.getCorreo() == null || pedido.getCorreo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe tu correo");
        }
        if (pedido.getClave() == null || pedido.getClave().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe tu clave");
        }

        Usuario usuario = usuarioRepository.findByCorreo(pedido.getCorreo());
        if (usuario == null || !hashService.sha256(pedido.getClave()).equals(usuario.getClave())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Correo o clave incorrectos");
        }

        // La puerta solo la puede activar la directiva.
        boolean puerta = "PUERTA".equals(pedido.getDispositivo());
        if (puerta && !"DIRECTIVA".equals(usuario.getRol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo la directiva puede activar la puerta");
        }

        String token = jwtService.generarToken(usuario, puerta);
        return new LoginResponse(token, UsuarioDTO.fromModel(usuario));
    }

    public List<UsuarioDTO> listar() {
        return usuarioRepository.findAll().stream()
                .map(UsuarioDTO::fromModel)
                .collect(Collectors.toList());
    }
}