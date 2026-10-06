package com.adela.services;

import org.springframework.stereotype.Service;

import com.adela.dto.EstiloDTO;
import com.adela.entities.Estilo;
import com.adela.entities.Cuestionario;
import com.adela.repositories.EstiloRepository;

import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
@Service
public class EstiloService {
    
    private final EstiloRepository estiloRepository;
    
    
    public void eliminarEstilo(Estilo estilo) {
        estiloRepository.delete(estilo);
    }
    
    public void guardarEstilos(Iterable<Estilo> estilos) {
        estiloRepository.saveAll(estilos);
    }
    
    public Estilo crearEstilo(Cuestionario cuestionario, EstiloDTO estiloDTO) {
        
        Estilo estilo = new Estilo();
        
        estilo.setCuestionario(cuestionario);
        estilo.setNombre(estiloDTO.getNombre());
        estilo.setValorMaximo(0d);
        estilo.setValorMinimo(0d);
        
        return estiloRepository.save(estilo);
    }
}
