package com.adela.entities;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Table(name = "estilo")
public class Categoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "cuestionario_id", referencedColumnName = "id", nullable = false)
    @JsonBackReference
    @ToString.Exclude
    private Cuestionario cuestionario;
    
    @Column(nullable = false)
    private String nombre;
    
    @Column(nullable = false)
    private Double valorMinimo;
    
    @Column(nullable = false)
    private Double valorMaximo;

    /**
     * Identidad por @Id, no por todos los campos. El equals de @Data recorria las
     * colecciones perezosas (forzando su carga) y hacia "iguales" a dos filas
     * distintas que coincidieran en el resto de columnas.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Categoria))
            return false;
        Categoria otro = (Categoria) o;
        return getId() != null && Objects.equals(getId(), otro.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
