package com.adela.entities;

import java.util.Objects;

import java.util.HashSet;
import java.util.Set;

import com.adela.calificacion.EsquemaInterpretacion;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.Getter;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Cuestionario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String nombre;
    
    @Column(nullable = false, length = 1000)
    private String descripcion;
    
    @Column(nullable = false)
    private String autor;
    
    @Column(nullable = false)
    private String version;
    
    @Column(nullable = false)
    private String siglas;

    @Enumerated(EnumType.STRING)
    @Column(name = "esquema_interpretacion", nullable = false, length = 30)
    private EsquemaInterpretacion esquemaInterpretacion = EsquemaInterpretacion.NINGUNA;

    /** Margen en puntos de POMP para el esquema RELATIVO. */
    @Column(name = "delta_relativo", nullable = false)
    private double deltaRelativo = 10;

    /**
     * Jerarquización o reparto: los puntajes de un estudiante dependen entre sí,
     * así que la distribución de perfiles informa más que el promedio.
     */
    @Column(name = "es_ipsativo", nullable = false)
    private boolean esIpsativo = false;

    @OneToMany(mappedBy = "cuestionario", cascade = CascadeType.ALL, orphanRemoval = true)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @JsonManagedReference
    private Set<Pregunta> preguntas = new HashSet<>();
    
    @OneToMany(mappedBy = "cuestionario", cascade = CascadeType.ALL, orphanRemoval = true)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @JsonManagedReference
    private Set<Estilo> estilos = new HashSet<>();

    /**
     * Identidad por @Id, no por todos los campos. El equals de @Data recorria las
     * colecciones perezosas (forzando su carga) y hacia "iguales" a dos filas
     * distintas que coincidieran en el resto de columnas.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Cuestionario))
            return false;
        Cuestionario otro = (Cuestionario) o;
        return getId() != null && Objects.equals(getId(), otro.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
