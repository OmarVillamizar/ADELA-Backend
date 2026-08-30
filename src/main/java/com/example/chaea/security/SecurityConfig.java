package com.example.chaea.security;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.Builder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.chaea.dto.ApiError;
import com.example.chaea.exceptions.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired
    private JwtFilter jwtFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        // Público: raíz, sondas de salud y el arranque del flujo OAuth2.
                        // /error es necesario porque Spring Security 6 filtra también el
                        // dispatch ERROR: sin permitirlo, cualquier fallo en una petición sin
                        // autenticar se convierte en una respuesta confusa.
                        .requestMatchers("/", "/error", "/health/**", "/login/**", "/oauth2/**").permitAll()
                        // Documentación: en producción springdoc la desactiva por completo
                        // (springdoc.*.enabled, ver application.yaml), así que estos matchers
                        // solo tienen efecto en los entornos donde sigue publicada.
                        .requestMatchers("/docs/**", "/api-docs/**", "/swagger-ui/**").permitAll()
                        // Todo lo demás exige autenticación. /api/** ya no está en la lista
                        // blanca: olvidar un @PreAuthorize deja el endpoint cerrado, no abierto.
                        // /auth/login/success/** queda cubierto por la sesión que crea oauth2Login.
                        .anyRequest().authenticated())
                .exceptionHandling(exc -> exc.authenticationEntryPoint((request, response, authException) -> {
                    // Mismo cuerpo que GlobalExceptionHandler: un único formato de error
                    // en toda la API, también para el 401 que emite la cadena de filtros.
                    String traceId = UUID.randomUUID().toString().substring(0, 8);
                    logger.warn("[{}] Petición sin autenticar a {}: {}", traceId, request.getRequestURI(),
                            authException.getMessage());

                    ApiError body = new ApiError(Instant.now(), HttpServletResponse.SC_UNAUTHORIZED,
                            ErrorCode.NO_AUTENTICADO.name(), "Necesitas iniciar sesión para acceder a este recurso.",
                            null, traceId, request.getRequestURI());

                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    objectMapper.writeValue(response.getWriter(), body);
                }))
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization -> authorization.authorizationRequestResolver(
                                customAuthorizationRequestResolver(clientRegistrationRepository)))
                        .successHandler(customAuthenticationSuccessHandler()));
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    
    // Custom Authorization Request Resolver para modificar el estado
    private OAuth2AuthorizationRequestResolver customAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        DefaultOAuth2AuthorizationRequestResolver defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, "/oauth2/authorization");
        
        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                OAuth2AuthorizationRequest authorizationRequest = defaultResolver.resolve(request);
                return customizeAuthorizationRequest(request, authorizationRequest);
            }
            
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                OAuth2AuthorizationRequest authorizationRequest = defaultResolver.resolve(request,
                        clientRegistrationId);
                return customizeAuthorizationRequest(request, authorizationRequest);
            }
            
            // Customización para agregar el userType al state
            private OAuth2AuthorizationRequest customizeAuthorizationRequest(HttpServletRequest request,
                    OAuth2AuthorizationRequest authorizationRequest) {
                if (authorizationRequest != null) {
                    // Obtener el userType desde la URL o los parámetros de la consulta
                    String userType = request.getParameter("userType");
                    if (userType == null) {
                        // Asignar userType por defecto si no se recibe en la solicitud
                        userType = "default";
                    }
                    String redirectTo = request.getParameter("redirect_to");
                    
                    if (redirectTo == null) {
                        redirectTo = "default";
                    }
                    
                    // Combinar userType con el estado (state)
                    String state = "userType=" + userType + "&redirect_to=" + redirectTo;
                    
                    // Crear la nueva solicitud con el estado modificado
                    Builder authorizationRequestBuilder = OAuth2AuthorizationRequest.from(authorizationRequest);
                    authorizationRequestBuilder.state(state); // Añadir el userType al state
                    return authorizationRequestBuilder.build();
                }
                return authorizationRequest;
            }
        };
    }
    
    // Custom Success Handler para recibir el estado en la respuesta
    private AuthenticationSuccessHandler customAuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            // Obtener el valor del state de la redirección de Google
            String state = request.getParameter("state");
            
            if (state == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Error: null state in request");
            }
            
            // Parsear el state para obtener el userType
            String userType = null, redirectTo = null;
            
            Map<String, String> parameters = new HashMap<String, String>();
            
            String[] elems = state.split("&");
            
            for (String elem : elems) {
                String[] x = elem.split("=");
                if (x.length == 1) {
                    parameters.put(x[0], "");
                    continue;
                } else if (x.length == 0) {
                    continue;
                }
                String val = x[1];
                for (int i = 2; i < x.length; ++i)
                    val += "=" + x[i];
                parameters.put(x[0], val);
            }
            
            if (state != null) {
                userType = parameters.get("userType"); // Extraer el valor de userType
                redirectTo = parameters.get("redirect_to");
                if (userType.equals("default"))
                    userType = null;
                if (redirectTo.equals("default"))
                    redirectTo = null;
            }
            
            List<String> falta = new LinkedList<String>();
            
            if (userType == null)
                falta.add("userType");
            if (redirectTo == null)
                falta.add("redirect_to");
            
            if (falta.size() > 0) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Error: not found required parameters " + falta.toString());
                response.flushBuffer();
                return;
            }
            
            // Lógica personalizada para manejar diferentes tipos de usuarios
            if ("profesor".equals(userType)) {
                System.out.println("Usuario es un profesor");
                // Redirigir al dashboard de profesor
                response.sendRedirect("/auth/login/success/prof?redirect_to=" + redirectTo);
            } else if ("estudiante".equals(userType)) {
                System.out.println("Usuario es un estudiante");
                // Redirigir al dashboard de estudiante
                response.sendRedirect("/auth/login/success/estud?redirect_to=" + redirectTo);
            } else {
                // Si no se encuentra userType, redirigir a un dashboard genérico
                response.sendRedirect("/");
            }
        };
    }
    
    @Bean
    JwtDecoder jwtDecoder() {
        // Usa NimbusJwtDecoder para decodificar el token JWT, debes configurar la URI
        // del emisor
        return NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
    }
    
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        // Configura aquí la conversión de claims, roles o cualquier otra lógica
        return converter;
    }
    
}
