package com.example.auth.service;

import com.example.auth.model.Usuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;

    // El secreto viene de jwt.secret (la variable JWT_SECRET del archivo .env).
    public JwtService(@Value("${jwt.secret}") String secreto) {
        this.key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
    }

    // Token normal: 12 horas. Token de la puerta: 7 días.
    public String generarToken(Usuario usuario, boolean puerta) {
        Date ahora = new Date();
        long duracion = puerta ? 1000L * 60 * 60 * 24 * 7 : 1000L * 60 * 60 * 12;

        return Jwts.builder()
                .subject(String.valueOf(usuario.getId()))
                .claim("nombre", usuario.getNombre())
                .claim("correo", usuario.getCorreo())
                .claim("rol", usuario.getRol())
                .claim("cargo", usuario.getCargo())
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + duracion))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}