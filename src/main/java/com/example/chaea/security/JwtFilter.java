package com.example.chaea.security;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.chaea.entities.Estudiante;
import com.example.chaea.entities.Profesor;
import com.example.chaea.entities.ProfesorEstado;
import com.example.chaea.entities.Usuario;
import com.example.chaea.entities.UsuarioEstado;
import com.example.chaea.repositories.UsuarioRepository;

import com.example.chaea.dto.ApiError;
import com.example.chaea.exceptions.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String authorizationHeader = request.getHeader("Authorization");
        String token = null;
        String email = null;
        
        try {
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                token = authorizationHeader.substring(7);
                email = jwtUtil.extractEmail(token);
            }
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Optional<Usuario> userDetails = usuarioRepository.findByEmail(email);
                if (userDetails.isPresent() && jwtUtil.validateToken(token, userDetails.get())) {
                    
                    Usuario user = userDetails.get();
                    
                    UsernamePasswordAuthenticationToken authenticationToken;
                    List<SimpleGrantedAuthority> permisos = new LinkedList<SimpleGrantedAuthority>();
                    if (user instanceof Profesor) {
                        Profesor profe = (Profesor) user;
                        if (profe.getEstado() == UsuarioEstado.ACTIVA
                                && profe.getEstadoProfesor() == ProfesorEstado.ACTIVA) {
                            if (profe.getRol() != null)
                                permisos.add(new SimpleGrantedAuthority("ROLE_" + profe.getRol().getDescripcion()));
                        }
                        if (profe.getEstado() == UsuarioEstado.INCOMPLETA) {
                            permisos.add(new SimpleGrantedAuthority("ROLE_PROFESOR_INCOMPLETO"));
                        }
                        if (profe.getEstadoProfesor() == ProfesorEstado.INACTIVA) {
                            permisos.add(new SimpleGrantedAuthority("ROLE_PROFESOR_INACTIVO"));
                        }
                        authenticationToken = new UsernamePasswordAuthenticationToken(userDetails.get(), null,
                                permisos);
                    } else if (user instanceof Estudiante) {
                        Estudiante estud = (Estudiante) user;
                        if (estud.getEstado() == UsuarioEstado.ACTIVA) {
                            permisos.add(new SimpleGrantedAuthority("ROLE_ESTUDIANTE"));
                        }
                        if (estud.getEstado() == UsuarioEstado.INCOMPLETA) {
                            permisos.add(new SimpleGrantedAuthority("ROLE_ESTUDIANTE_INCOMPLETO"));
                        }
                        authenticationToken = new UsernamePasswordAuthenticationToken(userDetails.get(), null,
                                permisos);
                    } else {
                        throw new RuntimeException("El usuario no está relacionado a ninguna cuenta");
                    }
                    // System.out.println(authenticationToken);
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    
                }
                
            }
            filterChain.doFilter(request, response);
        } catch (SignatureException e) {
            responder(request, response, ErrorCode.TOKEN_FIRMA_INVALIDA, "La firma del token no es válida.", e);
        } catch (ExpiredJwtException e) {
            responder(request, response, ErrorCode.TOKEN_EXPIRADO, "Tu sesión expiró. Inicia sesión de nuevo.", e);
        } catch (MalformedJwtException | IllegalArgumentException e) {
            // Caso típico: pegar "Bearer <token>" en el diálogo Authorize de Swagger,
            // que ya antepone "Bearer " por su cuenta y deja el prefijo duplicado.
            responder(request, response, ErrorCode.TOKEN_MALFORMADO,
                    "El token no tiene un formato válido. Envíalo sin el prefijo 'Bearer'.", e);
        } catch (Exception e) {
            responder(request, response, ErrorCode.ERROR_INTERNO, "Ocurrió un error inesperado al validar la sesión.",
                    e);
        }

    }

    /**
     * Escribe el mismo cuerpo que GlobalExceptionHandler. El filtro queda fuera del
     * @RestControllerAdvice, así que serializa por su cuenta para que el cliente vea
     * un único formato de error en toda la API.
     */
    private void responder(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String mensaje,
            Exception causa) throws IOException {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        logger.warn("[{}] {} en {}: {}", traceId, code, request.getRequestURI(), causa.getMessage());

        ApiError body = new ApiError(Instant.now(), code.getStatus().value(), code.name(), mensaje, null, traceId,
                request.getRequestURI());

        response.setStatus(code.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}