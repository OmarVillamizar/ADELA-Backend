package com.adela.services;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.adela.calificacion.FormatoItem;
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
        FormatoItem formato = preguntaDTO.getFormato();
        preguntaSave.setFormato(formato);
        preguntaSave.setMinSelecciones(
                preguntaDTO.getMinSelecciones() != null ? preguntaDTO.getMinSelecciones() : 0);
        preguntaSave.setMaxSelecciones(preguntaDTO.getMaxSelecciones());
        preguntaSave.setPuntosRepartir(preguntaDTO.getPuntosRepartir());
        preguntaSave.setObligatoria(preguntaDTO.getObligatoria() != null ? preguntaDTO.getObligatoria()
                : formato != FormatoItem.MULTIPLE);

        Pregunta pregunta = preguntaRepository.save(preguntaSave);
        
        Set<Opcion> opciones = new HashSet<>();
        
        for (OpcionDTO opcionDTO : preguntaDTO.getOpciones()) {
            Map<Estilo, Double> pesos = new HashMap<>();
            for (OpcionDTO.PesoDTO peso : opcionDTO.getPesos()) {
                Estilo estilo = mapId.get(peso.estiloId());
                if (estilo == null) {
                    throw new RuntimeException("No existe un estilo con id " + peso.estiloId() + " en la solicitud.");
                }
                pesos.put(estilo, peso.peso());
            }
            opciones.add(opcionService.crearOpcion(pregunta, pesos, opcionDTO));
        }

        pregunta.setOpciones(opciones);
        
        return preguntaRepository.save(pregunta);
    }
}
