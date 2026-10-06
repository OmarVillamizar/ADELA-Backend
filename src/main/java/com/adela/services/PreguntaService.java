package com.adela.services;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

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
        
        Pregunta pregunta = preguntaRepository.save(preguntaSave);
        
        Set<Opcion> opciones = new HashSet<>();
        Map<Integer, Double> max = new TreeMap<Integer, Double>();
        Map<Integer, Double> min = new TreeMap<Integer, Double>();
        
        for (OpcionDTO opcionDTO : preguntaDTO.getOpciones()) {
            int estiloId = opcionDTO.getEstiloId();
            Estilo estilo = mapId.get(opcionDTO.getEstiloId());
            
            if (!mapId.containsKey(estiloId)) {
                throw new RuntimeException(
                        "No existe un estilo con id " + opcionDTO.getEstiloId() + " en la solicitud.");
            }
            Opcion opcion = opcionService.crearOpcion(pregunta, estilo, opcionDTO);
            opciones.add(opcion);
            if (pregunta.isOpcionMultiple()) {
                if (max.containsKey(estiloId)) {
                    max.put(estiloId, Math.max(max.get(estiloId), max.get(estiloId) + opcion.getValor()));
                    min.put(estiloId, Math.min(min.get(estiloId), min.get(estiloId) + opcion.getValor()));
                } else {
                    max.put(estiloId, opcion.getValor());
                    min.put(estiloId, 0d);
                }
            } else {
                if (max.containsKey(estiloId)) {
                    max.put(estiloId, Math.max(max.get(estiloId), opcion.getValor()));
                    min.put(estiloId, Math.min(min.get(estiloId), opcion.getValor()));
                } else {
                    max.put(estiloId, opcion.getValor());
                    min.put(estiloId, opcion.getValor());
                }
            }
            
        }
        
        for (Entry<Integer, Double> pair : max.entrySet()) {
            Estilo estilo = mapId.get(pair.getKey());
            estilo.setValorMaximo(estilo.getValorMaximo() + pair.getValue());
        }
        
        for (Entry<Integer, Double> pair : min.entrySet()) {
            Estilo estilo = mapId.get(pair.getKey());
            estilo.setValorMinimo(estilo.getValorMinimo() + pair.getValue());
        }
        
        pregunta.setOpciones(opciones);
        
        return preguntaRepository.save(pregunta);
    }
}
