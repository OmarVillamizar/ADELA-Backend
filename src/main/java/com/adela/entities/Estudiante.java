package com.adela.entities;

import java.sql.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.adela.services.CodigoInstitucional;
import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.annotation.Nullable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;

@Entity
@Data
@AllArgsConstructor
@Table(name = "estudiante")
public class Estudiante extends Usuario {
    
    // Sin EnumType.STRING se guarda el ordinal: reordenar Genero reasignaria en
    // silencio el genero de todos los registros ya guardados.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Nullable
    private Genero genero;
    
    private Date fecha_nacimiento = Date.valueOf("2000-01-01");

    /** True tras el primer guardado del perfil: desde ahí el nombre queda fijo para los reportes. */
    @Column(name = "nombre_confirmado", nullable = false)
    private boolean nombreConfirmado = false;

    @ManyToMany
    @JoinTable(name = "matricula", joinColumns = @JoinColumn(name = "estudiante_id"), inverseJoinColumns = @JoinColumn(name = "curso_id"))
    @JsonBackReference
    private Set<Grupo> grupos = new HashSet<Grupo>();
    
    public Estudiante() {
        super();
    }
    
    public Estudiante(String email, String nombre, String codigo, Genero genero, Date fechaNac, UsuarioEstado estado) {
        super(email, nombre, codigo, estado);
        this.genero = genero;
        this.fecha_nacimiento = fechaNac;
    }
    
    /**
     * Una sola edición del nombre, antes de confirmarlo. Sin nombre (preinscrito por
     * correo) es obligatoria; fuera de la UFPS el nombre de Google puede ser un apodo.
     */
    public boolean nombreEditable() {
        if (nombreConfirmado) {
            return false;
        }
        return getNombre() == null || getNombre().isBlank() || !CodigoInstitucional.requiere(getEmail());
    }

    @Override
    public String toString() {
        return this.getEmail();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Estudiante))
            return false;
        Estudiante that = (Estudiante) o;
        return Objects.equals(getEmail(), that.getEmail());
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(getEmail());
    }
}