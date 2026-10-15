package com.adela.security;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.Builder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.adela.dto.ApiError;
import com.adela.exceptions.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtFilter jwtFilter;

    private final ObjectMapper objectMapper;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        http.csrf(csrf -> csrf.disable())
                // La configuracion CORS vive en WebConfig, pero addCorsMappings se aplica en
                // el DispatcherServlet, es decir despues de esta cadena de filtros. Sin
                // habilitarla aqui el preflight OPTIONS llega a anyRequest().authenticated()
                // y responde 401: el navegador cancela toda peticion que lleve Authorization.
                .cors(Customizer.withDefaults())
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
                        // Cápsulas: se responden por enlace o QR sin cuenta. Solo este
                        // prefijo, acotado por LimiteTasaFilter; los recursos se buscan por
                        // código aleatorio, nunca por id.
                        .requestMatchers("/api/publico/**").permitAll()
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
        // Antes del JwtFilter para que una ráfaga se corte sin tocar la base de
        // datos; después de CORS para que el navegador pueda leer el 429.
        http.addFilterBefore(new LimiteTasaFilter(objectMapper, Clock.systemUTC()), JwtFilter.class);
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
                    
                    // Combinar userType con el estado (state). Codificados: un "&" dentro de
                    // redirect_to se leía como otro parámetro y podía sobrescribir userType.
                    String state = "userType=" + codificar(userType) + "&redirect_to=" + codificar(redirectTo);
                    
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
            // Obtener el valor del state de la redirección de Google. Sin state, o sin
            // alguno de sus parámetros, se responde el 400 de abajo; antes un state
            // nulo o incompleto terminaba en NullPointerException.
            Map<String, String> parameters = leerState(request.getParameter("state"));
            String userType = parameters.get("userType");
            String redirectTo = parameters.get("redirect_to");

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
                logger.debug("Login OAuth2 de tipo profesor");
                // Redirigir al dashboard de profesor
                response.sendRedirect("/auth/login/success/prof?redirect_to=" + codificar(redirectTo));
            } else if ("estudiante".equals(userType)) {
                logger.debug("Login OAuth2 de tipo estudiante");
                // Redirigir al dashboard de estudiante
                response.sendRedirect("/auth/login/success/estud?redirect_to=" + codificar(redirectTo));
            } else {
                // Si no se encuentra userType, redirigir a un dashboard genérico
                response.sendRedirect("/");
            }
        };
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    /**
     * Lee el state que armó customizeAuthorizationRequest. Un parámetro ausente,
     * vacío o "default" no aparece en el mapa; un state nulo da un mapa vacío.
     */
    static Map<String, String> leerState(String state) {
        Map<String, String> parametros = new HashMap<>();
        if (state == null) {
            return parametros;
        }
        for (String par : state.split("&")) {
            String[] claveValor = par.split("=", 2);
            if (claveValor.length < 2) {
                continue;
            }
            String valor = URLDecoder.decode(claveValor[1], StandardCharsets.UTF_8);
            if (!valor.isEmpty() && !"default".equals(valor)) {
                parametros.put(claveValor[0], valor);
            }
        }
        return parametros;
    }

    /**
     * JwtFilter es un @Component para poder inyectarle repositorios, y Spring Boot
     * registra todo Filter bean también en el contenedor de servlets: corría dos
     * veces por petición, recargando el usuario de la base en cada una. Aquí se
     * apaga ese registro y queda solo en la cadena de seguridad.
     */
    @Bean
    FilterRegistrationBean<JwtFilter> jwtFilterSoloEnLaCadena(JwtFilter filtro) {
        FilterRegistrationBean<JwtFilter> registro = new FilterRegistrationBean<>(filtro);
        registro.setEnabled(false);
        return registro;
    }
    
    /**
     * SUPERADMIN hereda todo lo de ADMINISTRADOR. JwtFilter concede una sola
     * autoridad por rol, así que sin esta jerarquía cada hasRole('ADMINISTRADOR')
     * rechazaría al superadmin. @EnableMethodSecurity toma este bean.
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("SUPERADMIN").implies("ADMINISTRADOR")
                .build();
    }
}
