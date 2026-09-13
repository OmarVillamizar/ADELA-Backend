package com.example.chaea.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.chaea.dto.OpcionDTO;
import com.example.chaea.entities.Categoria;
import com.example.chaea.entities.Opcion;
import com.example.chaea.entities.Pregunta;
import com.example.chaea.repositories.CategoriaRepository;
import com.example.chaea.repositories.OpcionRepository;
import com.example.chaea.repositories.PreguntaRepository;


@Service
public class OpcionService {
    
    @Autowired
    private CategoriaRepository categoriaRepository;
    
    @Autowired
    private PreguntaRepository preguntaRepository;
    
    @Autowired
    private OpcionRepository opcionRepository;
    
    
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
