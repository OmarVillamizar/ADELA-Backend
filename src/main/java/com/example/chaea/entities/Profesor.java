package com.example.chaea.entities;

import java.util.Objects;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@Table(name = "profesor")
public class Profesor extends Usuario {
    
    @Column(length = 50)
    @Getter
    @Setter
    @Nullable
    private String carrera;
    
    @ManyToOne
    @JoinColumn(name = "rol_id")
    @Nullable
    private Rol rol;
    
    @Column(length = 100)
    @Getter
    @Setter
    @Enumerated(EnumType.STRING)
    private ProfesorEstado estadoProfesor;
    
    public Profesor() {
        
    }
    
    /**
     * BUG-08: @EqualsAndHashCode(callSuper = false) excluia el @Id heredado, asi
     * que dos profesores distintos con la misma carrera y rol eran "iguales" para
     * cualquier Set, Map o contains. La identidad es el correo, como en Estudiante.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Profesor))
            return false;
        Profesor otro = (Profesor) o;
        return Objects.equals(getEmail(), otro.getEmail());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getEmail());
    }
}
