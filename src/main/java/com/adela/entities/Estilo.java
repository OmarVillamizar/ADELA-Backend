package com.adela.entities;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.adela.calificacion.TipoEstilo;
import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
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
public class Estilo {
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoEstilo tipo = TipoEstilo.PRIMARIO;

    /** Solo COMPUESTO: coeficiente c(k,e) por id de estilo primario (AC-CE = AC +1, CE -1). */
    @ElementCollection
    @CollectionTable(name = "estilo_compuesto_coef", joinColumns = @JoinColumn(name = "compuesto_id"))
    @MapKeyColumn(name = "primario_id")
    @Column(name = "coeficiente", nullable = false)
    @ToString.Exclude
    private Map<Long, Double> coeficientes = new HashMap<>();

    /**
     * Identidad por @Id, no por todos los campos. El equals de @Data recorria las
     * colecciones perezosas (forzando su carga) y hacia "iguales" a dos filas
     * distintas que coincidieran en el resto de columnas.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Estilo))
            return false;
        Estilo otro = (Estilo) o;
        return getId() != null && Objects.equals(getId(), otro.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
