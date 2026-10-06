package com.adela.services;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.adela.dto.OpcionDTO;
import com.adela.dto.PreguntaDTO;
import com.adela.entities.Estilo;
import com.adela.entities.Cuestionario;
import com.adela.entities.Opcion;
import com.adela.entities.Pregunta;
import com.adela.repositories.PreguntaRepository;
import com.adela.repositories.ResultadoPreguntaRepository;

import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
@Service
public class PreguntaService {
    
    private final PreguntaRepository preguntaRepository;
    
    private final ResultadoPreguntaRepository resultadoPreguntaRepository;
    
    private final OpcionService opcionService;
    
    
    public void eliminarPregunta(Pregunta pregunta) {
        for (Opcion opcion : pregunta.getOpciones()) {
            eliminarRespuestasAsociadas(opcion);
            opcionService.eliminarOpcion(opcion);
        }
        pregunta.getOpciones().clear();
        preguntaRepository.delete(pregunta);
    }
    
    private void eliminarRespuestasAsociadas(Opcion opcion) {
        resultadoPreguntaRepository.deleteByOpcion(opcion);
    }
    
    public Pregunta crearPregunta(Cuestionario cuestionario, Map<Integer, Estilo> mapId, PreguntaDTO preguntaDTO) {
        Pregunta preguntaSave = new Pregunta();
        preguntaSave.setCuestionario(cuestionario);
        preguntaSave.setPregunta(preguntaDTO.getPregunta());
        preguntaSave.setOrden(preguntaDTO.getOrden());
        preguntaSave.setOpcionMultiple(preguntaDTO.isOpcionMultiple());
        preguntaSave.setObligatoria(preguntaDTO.getObligatoria() != null ? preguntaDTO.getObligatoria()
                : !preguntaDTO.isOpcionMultiple());

        Pregunta pregunta = preguntaRepository.save(preguntaSave);
        
        Set<Opcion> opciones = new HashSet<>();
        
        for (OpcionDTO opcionDTO : preguntaDTO.getOpciones()) {
            int estiloId = opcionDTO.getEstiloId();
            Estilo estilo = mapId.get(opcionDTO.getEstiloId());
            
            if (!mapId.containsKey(estiloId)) {
                throw new RuntimeException(
                        "No existe un estilo con id " + opcionDTO.getEstiloId() + " en la solicitud.");
            }
            opciones.add(opcionService.crearOpcion(pregunta, estilo, opcionDTO));
        }

        pregunta.setOpciones(opciones);
        
        return preguntaRepository.save(pregunta);
    }
}
