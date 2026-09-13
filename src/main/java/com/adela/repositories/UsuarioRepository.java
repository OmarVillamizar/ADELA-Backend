package com.adela.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.adela.entities.Usuario;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, String> {
    // Aquí el identificador (ID) es de tipo String (email)
    
    // Si necesitas un método personalizado para buscar usuarios por email
    Optional<Usuario> findByEmail(String email);
    
    Optional<Usuario> findByCodigo(String codigo);
}
