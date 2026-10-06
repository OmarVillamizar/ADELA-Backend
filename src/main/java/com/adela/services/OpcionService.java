package com.adela.services;

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
    
    public Opcion crearOpcion(Pregunta pregunta, Estilo estilo, OpcionDTO opcionDTO) {
        if (pregunta.getCuestionario().getId().equals(estilo.getCuestionario().getId())) {
            Opcion opcion = new Opcion();
            opcion.setPregunta(pregunta);
            opcion.setEstilo(estilo);
            opcion.setValor(opcionDTO.getValor());
            opcion.setOrden(opcionDTO.getOrden());
            opcion.setRespuesta(opcionDTO.getRespuesta());
            
            return opcionRepository.save(opcion);
        }
        throw new RuntimeException("Inconsistencias en los cuestionarios de pregunta ("
                + pregunta.getCuestionario().getId() + ") y estilo(" + estilo.getCuestionario().getId() + ")");
    }
}
