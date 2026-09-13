package com.example.chaea.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.chaea.dto.CategoriaDTO;
import com.example.chaea.entities.Categoria;
import com.example.chaea.entities.Cuestionario;
import com.example.chaea.repositories.CategoriaRepository;
import com.example.chaea.repositories.CuestionarioRepository;


@Service
public class CategoriaService {
    
    @Autowired
    private CategoriaRepository categoriaRepository;
    
    @Autowired
    private CuestionarioRepository cuestionarioRepository;
    
    
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
