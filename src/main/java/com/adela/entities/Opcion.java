package com.adela.entities;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.hibernate.annotations.BatchSize;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
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
public class Opcion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "pregunta_id", referencedColumnName = "id", nullable = false)
    @JsonBackReference
    @ToString.Exclude
    private Pregunta pregunta;
    
    @Column(nullable = false)
    private String respuesta;

    /**
     * Peso w(o,e) por id de estilo primario; un estilo ausente pesa 0. Una opción
     * puede sumar a varios estilos (N:M). El lote evita una consulta por opción
     * al armar la clave de calificación.
     */
    @ElementCollection
    @CollectionTable(name = "opcion_peso", joinColumns = @JoinColumn(name = "opcion_id"))
    @MapKeyColumn(name = "estilo_id")
    @Column(name = "peso", nullable = false)
    @BatchSize(size = 256)
    @ToString.Exclude
    private Map<Long, Double> pesos = new HashMap<>();
    
    @Column(nullable = false)
    private int orden;

    /**
     * Identidad por @Id, no por todos los campos. El equals de @Data recorria las
     * colecciones perezosas (forzando su carga) y hacia "iguales" a dos filas
     * distintas que coincidieran en el resto de columnas.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Opcion))
            return false;
        Opcion otro = (Opcion) o;
        return getId() != null && Objects.equals(getId(), otro.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
