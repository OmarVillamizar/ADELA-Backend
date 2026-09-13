package com.adela.services;

import org.springframework.stereotype.Service;

import com.adela.dto.CategoriaDTO;
import com.adela.entities.Categoria;
import com.adela.entities.Cuestionario;
import com.adela.repositories.CategoriaRepository;
import com.adela.repositories.CuestionarioRepository;

import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
@Service
public class CategoriaService {
    
    private final CategoriaRepository categoriaRepository;
    
    private final CuestionarioRepository cuestionarioRepository;
    
    
    public void eliminarCategoria(Categoria categoria) {
        categoriaRepository.delete(categoria);
    }
    
    public void guardarCategorias(Iterable<Categoria> categorias) {
        categoriaRepository.saveAll(categorias);
    }
    
    public Categoria crearCategoria(Cuestionario cuestionario, CategoriaDTO categoriaDTO) {
        
        Categoria categoria = new Categoria();
        
        categoria.setCuestionario(cuestionario);
        categoria.setNombre(categoriaDTO.getNombre());
        categoria.setValorMaximo(0d);
        categoria.setValorMinimo(0d);
        
        return categoriaRepository.save(categoria);
    }
}
