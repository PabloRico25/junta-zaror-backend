package com.example.reserva.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    // El mismo secreto que usa auth-service para firmar los tokens (JWT_SECRET del archivo .env)
    @Value("${jwt.secret}")
    private String secreto;

    // Toda petición necesita un token válido; sin él, la respuesta es 401. La única excepción es leer la lista de salas
    // (GET /dependencias): un aparato que va a ser la puerta la pide antes de que nadie inicie sesión en él.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/dependencias").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        return http.build();
    }

    // Verifica la firma del token con la clave compartida (HS256)
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec clave = new SecretKeySpec(
                secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(clave).build();
    }
}