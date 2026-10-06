package com.adela.dto;

import com.adela.entities.UsuarioEstado;

import com.adela.services.CodigoInstitucional;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public abstract class UserDTO {
    private String email;
    
    private String nombre;
    
    // La longitud la impone Usuario.codigo, que es varchar(12). Antes cada endpoint
    // comprobaba lo suyo a mano y las reglas divergian entre estudiante y profesor.
    // Si es obligatorio depende del correo: lo decide CodigoInstitucional.
    @Pattern(regexp = "\\d*", message = "El código solo admite dígitos")
    @Size(max = 12, message = "El código admite un máximo de 12 dígitos")
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

    /** Solo de salida: el frontend lo usa para mostrar u ocultar el campo código. */
    public boolean isRequiereCodigo() {
        return CodigoInstitucional.requiere(email);
    }
    
}
