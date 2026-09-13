package com.example.chaea.dto;

import com.example.chaea.entities.ProfesorEstado;
import com.example.chaea.entities.Profesor;
import com.example.chaea.entities.Rol;
import com.example.chaea.entities.UsuarioEstado;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class ProfesorDTO extends UserDTO {
    
    @NotBlank(message = "La carrera es obligatoria")
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
