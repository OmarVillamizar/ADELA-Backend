package com.adela.entities;

import java.util.Objects;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.ToString;
import lombok.Setter;
import lombok.Getter;

@Entity
@Getter
@Setter
@ToString
@Inheritance(strategy = InheritanceType.JOINED)
@Table(name = "Usuario")
public abstract class Usuario {
    
    @Id
    @Column(length = 100)
    @Nullable
    private String email;
    
    @Column(length = 100)
    @Nullable
    private String nombre;
    
    @Column(length = 8, unique = true)
    @Nullable
    private String codigo;
    
    @Enumerated(EnumType.STRING)
    @NotNull
    private UsuarioEstado estado;
    
    public Usuario() {
        
    }
    
    public Usuario(String email, String nombre, String codigo, UsuarioEstado estado) {
        this.email = email;
        this.nombre = nombre;
        this.codigo = codigo;
        this.estado = estado;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getNombre() {
        return nombre;
    }
    
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }
    
    public String getCodigo() {
        return codigo;
    }
    
    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    /**
     * Identidad por @Id, no por todos los campos. El equals de @Data recorria las
     * colecciones perezosas (forzando su carga) y hacia "iguales" a dos filas
     * distintas que coincidieran en el resto de columnas.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Usuario))
            return false;
        Usuario otro = (Usuario) o;
        return getEmail() != null && Objects.equals(getEmail(), otro.getEmail());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getEmail());
    }
}
