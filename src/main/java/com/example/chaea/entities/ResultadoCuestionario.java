package com.example.chaea.entities;

import java.util.Objects;

import java.sql.Date;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.ToString;
import lombok.Setter;
import lombok.Getter;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
public class ResultadoCuestionario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "cuestionario_id", referencedColumnName = "id", nullable = false)
    @JsonBackReference
    private Cuestionario cuestionario;
    
    @ManyToOne
    @JoinColumn(name = "estudiante_email", referencedColumnName = "email", nullable = false)
    @JsonBackReference
    private Estudiante estudiante;
    
    private Date fechaAplicacion;
    
    @ManyToOne
    @JoinColumn(name = "grupo_id", referencedColumnName = "id", nullable = true)
    @JsonBackReference
    private Grupo grupo;
    
    private boolean bloqueado = false;
    
    @Nullable
    private Date fechaResolucion;
    
    @OneToMany(mappedBy = "cuestionario", cascade = CascadeType.ALL)
    @EqualsAndHashCode.Exclude
    @JsonManagedReference
    private Set<ResultadoPregunta> preguntas = new HashSet<>();

    /**
     * Identidad por @Id, no por todos los campos. El equals de @Data recorria las
     * colecciones perezosas (forzando su carga) y hacia "iguales" a dos filas
     * distintas que coincidieran en el resto de columnas.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ResultadoCuestionario))
            return false;
        ResultadoCuestionario otro = (ResultadoCuestionario) o;
        return getId() != null && Objects.equals(getId(), otro.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
