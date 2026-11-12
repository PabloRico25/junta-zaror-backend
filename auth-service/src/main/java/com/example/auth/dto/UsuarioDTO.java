package com.example.auth.dto;

import com.example.auth.model.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioDTO {

    private Long id;
    private String nombre;
    private String correo;
    private String rol;
    private String cargo;

    // Nunca se incluye la clave.
    public static UsuarioDTO fromModel(Usuario usuario) {
        return new UsuarioDTO(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getCorreo(),
                usuario.getRol(),
                usuario.getCargo()
        );
    }
}