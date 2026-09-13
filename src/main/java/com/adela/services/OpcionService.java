package com.adela.services;

import org.springframework.stereotype.Service;

import com.adela.dto.OpcionDTO;
import com.adela.entities.Categoria;
import com.adela.entities.Opcion;
import com.adela.entities.Pregunta;
import com.adela.repositories.CategoriaRepository;
import com.adela.repositories.OpcionRepository;
import com.adela.repositories.PreguntaRepository;

import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
@Service
public class OpcionService {
    
    private final CategoriaRepository categoriaRepository;
    
    private final PreguntaRepository preguntaRepository;
    
    private final OpcionRepository opcionRepository;
    
    
    public void eliminarOpcion(Opcion opcion) {
        opcionRepository.delete(opcion);
    }
    
    public Opcion crearOpcion(Pregunta pregunta, Categoria categoria, OpcionDTO opcionDTO) {
        if (pregunta.getCuestionario().getId().equals(categoria.getCuestionario().getId())) {
            Opcion opcion = new Opcion();
            opcion.setPregunta(pregunta);
            opcion.setCategoria(categoria);
            opcion.setValor(opcionDTO.getValor());
            opcion.setOrden(opcionDTO.getOrden());
            opcion.setRespuesta(opcionDTO.getRespuesta());
            
            return opcionRepository.save(opcion);
        }
        throw new RuntimeException("Inconsistencias en los cuestionarios de pregunta ("
                + pregunta.getCuestionario().getId() + ") y categoria(" + categoria.getCuestionario().getId() + ")");
    }
}
