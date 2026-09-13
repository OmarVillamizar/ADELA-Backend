package com.example.chaea.dto;

import com.example.chaea.entities.UsuarioEstado;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public abstract class UserDTO {
    private String email;
    
    private String nombre;
    
    // La longitud la impone Usuario.codigo, que es varchar(8). Antes cada endpoint
    // comprobaba lo suyo a mano y las reglas divergian entre estudiante y profesor.
    @NotBlank(message = "El código es obligatorio")
    @Size(max = 8, message = "El código admite un máximo de 8 caracteres")
    private String codigo;
    
    private UsuarioEstado estado;
    
    private UserType tipoUsuario;
    
    public UserDTO(String email, String nombre, String codigo, UsuarioEstado estado, UserType tipoUsuario) {
        this.email = email;
        this.nombre = nombre;
        this.codigo = codigo == null ? "" : codigo;
        this.estado = estado;
        this.tipoUsuario = tipoUsuario;
    }
    
}
