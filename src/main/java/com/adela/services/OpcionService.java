package com.adela.services;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.adela.dto.OpcionDTO;
import com.adela.entities.Estilo;
import com.adela.entities.Opcion;
import com.adela.entities.Pregunta;
import com.adela.repositories.OpcionRepository;

import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
@Service
public class OpcionService {
    
    private final OpcionRepository opcionRepository;
    
    
    public void eliminarOpcion(Opcion opcion) {
        opcionRepository.delete(opcion);
    }
    
    /** pesos: estilo primario -> peso. Todos deben ser del cuestionario de la pregunta. */
    public Opcion crearOpcion(Pregunta pregunta, Map<Estilo, Double> pesos, OpcionDTO opcionDTO) {
        Long cuestionarioId = pregunta.getCuestionario().getId();
        Map<Long, Double> porId = new HashMap<>();
        pesos.forEach((estilo, peso) -> {
            if (!cuestionarioId.equals(estilo.getCuestionario().getId())) {
                throw new RuntimeException("Inconsistencias en los cuestionarios de pregunta (" + cuestionarioId
                        + ") y estilo(" + estilo.getCuestionario().getId() + ")");
            }
            porId.put(estilo.getId(), peso);
        });
        Opcion opcion = new Opcion();
        opcion.setPregunta(pregunta);
        opcion.setPesos(porId);
        opcion.setOrden(opcionDTO.getOrden());
        opcion.setRespuesta(opcionDTO.getRespuesta());

        return opcionRepository.save(opcion);
    }
}
