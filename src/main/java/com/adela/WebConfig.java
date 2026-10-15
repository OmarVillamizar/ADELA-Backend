package com.adela;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;

/**
 * Sin @EnableWebMvc: esa anotacion registra WebMvcConfigurationSupport y con ello
 * desactiva WebMvcAutoConfiguration entera, que es @ConditionalOnMissingBean de
 * esa clase. Implementar WebMvcConfigurer basta para añadir la config de CORS
 * conservando los valores por defecto de Spring Boot.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    Logger logger = LoggerFactory.getLogger(WebConfig.class);
    
    @Value("${cors.allowed-origins}")
    private String allowedOriginsString;

    private String[] allowedOrigins;

    /**
     * Falla al arrancar si la lista no sirve. Con allowCredentials(true), un "*"
     * deja a cualquier sitio hacer peticiones con la sesión del usuario, y una
     * lista vacía rompe el login sin decir por qué.
     */
    @PostConstruct
    void validarOrigenes() {
        allowedOrigins = origenes(allowedOriginsString);
    }

    static String[] origenes(String lista) {
        String[] origenes = Arrays.stream(lista == null ? new String[0] : lista.split(","))
                .map(String::trim).filter(o -> !o.isEmpty()).toArray(String[]::new);
        if (origenes.length == 0) {
            throw new IllegalStateException("ALLOWED_ORIGINS está vacío: define el origen del frontend.");
        }
        if (Arrays.stream(origenes).anyMatch(o -> o.contains("*"))) {
            throw new IllegalStateException(
                    "ALLOWED_ORIGINS no admite comodines: con credenciales, lista los orígenes exactos.");
        }
        return origenes;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        logger.info("Loading allowed origins: {}", Arrays.toString(allowedOrigins));
        registry.addMapping("/**").allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS").allowedHeaders("*")
                .allowCredentials(true);
    }
}