package com.example.chaea.initialization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.chaea.entities.Rol;
import com.example.chaea.repositories.RolRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Configuration
public class DataInitializer {
    
    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    
    private final RolRepository rolRepository;
    
    @Bean
    CommandLineRunner initializeRoles() {
        return args -> {
            logger.info("Starting role initialization...");
            try {
                createRoleIfNotFound("ADMINISTRADOR");
                createRoleIfNotFound("PROFESOR");
                logger.info("Role initialization completed successfully");
            } catch (Exception e) {
                logger.error("Error during role initialization", e);
                throw e;
            }
        };
    }
    
    private void createRoleIfNotFound(String roleName) {
        if (rolRepository.findByDescripcion(roleName).isEmpty()) {
            Rol role = new Rol();
            role.setDescripcion(roleName);
            rolRepository.save(role);
            logger.info("Created role: {}", roleName);
        } else {
            logger.debug("Role already exists: {}", roleName);
        }
    }
}