package com.example.auth.controller;

import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.LoginResponse;
import com.example.auth.dto.UsuarioDTO;
import com.example.auth.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AuthController {

    @Autowired
    private UsuarioService usuarioService;

    @PostMapping("/auth/login")
    public LoginResponse login(@RequestBody LoginRequest pedido) {
        return usuarioService.login(pedido);
    }

    @GetMapping("/usuarios")
    public List<UsuarioDTO> listar() {
        return usuarioService.listar();
    }
}