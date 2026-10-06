package com.adela.dto;

import com.adela.entities.ProfesorEstado;
import com.adela.entities.Profesor;
import com.adela.entities.Rol;
import com.adela.entities.UsuarioEstado;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class ProfesorDTO extends UserDTO {
    
    // Obligatoria solo para cuentas UFPS: lo decide ProfesorController.
    @Size(max = 50, message = "La carrera admite un máximo de 50 caracteres")
    private String carrera;
    private String rol;
    private ProfesorEstado estadoProfesor;
    
    public ProfesorDTO(String email, String nombre, String codigo, UsuarioEstado estado, String carrera, Rol rol,
            ProfesorEstado profesorEstado) {
        super(email, nombre, codigo, estado, UserType.PROFESOR);
        this.carrera = carrera == null ? "" : carrera;
        this.rol = rol == null ? "INACTIVO" : rol.getDescripcion();
        this.estadoProfesor = profesorEstado;
    }

    public static ProfesorDTO from(Profesor p) {
        return new ProfesorDTO(p.getEmail(), p.getNombre(), p.getCodigo(), p.getEstado(), p.getCarrera(), p.getRol(),
                p.getEstadoProfesor());
    }
}
