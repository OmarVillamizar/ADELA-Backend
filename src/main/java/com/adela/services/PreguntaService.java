package com.adela.services;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.adela.dto.OpcionDTO;
import com.adela.dto.PreguntaDTO;
import com.adela.entities.Categoria;
import com.adela.entities.Cuestionario;
import com.adela.entities.Opcion;
import com.adela.entities.Pregunta;
import com.adela.repositories.CuestionarioRepository;
import com.adela.repositories.PreguntaRepository;
import com.adela.repositories.ResultadoPreguntaRepository;

import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
@Service
public class PreguntaService {
    
    private final PreguntaRepository preguntaRepository;
    
    private final CuestionarioRepository cuestionarioRepository;
    
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
    
    public Pregunta crearPregunta(Cuestionario cuestionario, Map<Integer, Categoria> mapId, PreguntaDTO preguntaDTO) {
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
            int cateId = opcionDTO.getCategoriaId();
            Categoria categoria = mapId.get(opcionDTO.getCategoriaId());
            
            if (!mapId.containsKey(cateId)) {
                throw new RuntimeException(
                        "No existe una categoría con id " + opcionDTO.getCategoriaId() + " en la solicitud.");
            }
            Opcion opcion = opcionService.crearOpcion(pregunta, categoria, opcionDTO);
            opciones.add(opcion);
            if (pregunta.isOpcionMultiple()) {
                if (max.containsKey(cateId)) {
                    max.put(cateId, Math.max(max.get(cateId), max.get(cateId) + opcion.getValor()));
                    min.put(cateId, Math.min(min.get(cateId), min.get(cateId) + opcion.getValor()));
                } else {
                    max.put(cateId, opcion.getValor());
                    min.put(cateId, 0d);
                }
            } else {
                if (max.containsKey(cateId)) {
                    max.put(cateId, Math.max(max.get(cateId), opcion.getValor()));
                    min.put(cateId, Math.min(min.get(cateId), opcion.getValor()));
                } else {
                    max.put(cateId, opcion.getValor());
                    min.put(cateId, opcion.getValor());
                }
            }
            
        }
        
        for (Entry<Integer, Double> pair : max.entrySet()) {
            Categoria categoria = mapId.get(pair.getKey());
            categoria.setValorMaximo(categoria.getValorMaximo() + pair.getValue());
        }
        
        for (Entry<Integer, Double> pair : min.entrySet()) {
            Categoria categoria = mapId.get(pair.getKey());
            categoria.setValorMinimo(categoria.getValorMinimo() + pair.getValue());
        }
        
        pregunta.setOpciones(opciones);
        
        return preguntaRepository.save(pregunta);
    }
}
