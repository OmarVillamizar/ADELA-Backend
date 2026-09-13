package com.example.chaea;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] allowedOrigins = allowedOriginsString.split(",");
        logger.info("Loading allowed origins: {}", Arrays.toString(allowedOrigins));
        registry.addMapping("/**").allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS").allowedHeaders("*")
                .allowCredentials(true);
    }
}