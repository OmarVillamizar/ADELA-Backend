package com.example.chaea.controllers;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.chaea.entities.Estudiante;
import com.example.chaea.entities.Profesor;
import com.example.chaea.entities.ProfesorEstado;
import com.example.chaea.entities.Usuario;
import com.example.chaea.entities.UsuarioEstado;
import com.example.chaea.repositories.UsuarioRepository;
import com.example.chaea.security.JwtUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    private final JwtUtil jwtUtil;
    
    private final UsuarioRepository usuarioRepository;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @GetMapping("/")
    public String index() {
        return "index"; // Return index.html
    }

    /**
     * El parámetro redirect_to llega del cliente a través del state de OAuth2 y se
     * usa como destino de una redirección que lleva el token. Sin lista blanca es un
     * open redirect: basta un enlace para entregar el JWT de un usuario a otro
     * dominio. Se acepta únicamente si su origen está en cors.allowed-origins.
     */
    private boolean esRedirectPermitido(String redirectTo) {
        if (redirectTo == null || redirectTo.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(redirectTo);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return false;
            }
            String origen = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
            return Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                    .map(o -> o.endsWith("/") ? o.substring(0, o.length() - 1) : o).filter(o -> !o.isEmpty())
                    .anyMatch(o -> o.equalsIgnoreCase(origen));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    @GetMapping("/login/success/estud")
    public void loginSuccessEstudiante(HttpServletResponse response, HttpServletRequest request,
            OAuth2AuthenticationToken authentication, @RequestParam String redirect_to) throws IOException {
        if (!esRedirectPermitido(redirect_to)) {
            logger.warn("redirect_to rechazado: {}", redirect_to);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "redirect_to no permitido");
            return;
        }

        String email = authentication.getPrincipal().getAttribute("email");

        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(email);
        Usuario usuario;
        logger.info("will redirecto to " + redirect_to);
        if (usuarioOpt.isEmpty()) {
            // Si el usuario no existe, lo creamos con el rol por defecto
            Estudiante newUsuario = new Estudiante();
            newUsuario.setEmail(email);
            newUsuario.setEstado(UsuarioEstado.INCOMPLETA);
            newUsuario.setNombre(authentication.getPrincipal().getAttribute("name"));
            usuarioRepository.save(newUsuario);
            usuario = newUsuario;
        } else {
            usuario = usuarioOpt.get();
            if (!(usuario instanceof Estudiante)) {
                response.sendRedirect(
                        redirect_to + "?error=El usuario " + email + " no pertenece a una cuenta de estudiante");
                return;
            }
            
        }
        String token = jwtUtil.generateToken(usuario);
        response.sendRedirect(redirect_to + "?token=" + token);
    }
    
    @GetMapping("/login/success/prof")
    public void loginSuccessProfesor(OAuth2AuthenticationToken authentication, HttpServletResponse response,
            @RequestParam String redirect_to) throws IOException {
        if (!esRedirectPermitido(redirect_to)) {
            logger.warn("redirect_to rechazado: {}", redirect_to);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "redirect_to no permitido");
            return;
        }

        String email = authentication.getPrincipal().getAttribute("email");

        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(email);
        Usuario usuario;
        if (usuarioOpt.isEmpty()) {
            // Si el usuario no existe, lo creamos con el rol por defecto
            Profesor newUsuario = new Profesor();
            newUsuario.setEmail(email);
            newUsuario.setEstado(UsuarioEstado.INCOMPLETA);
            newUsuario.setEstadoProfesor(ProfesorEstado.INACTIVA);
            newUsuario.setNombre(authentication.getPrincipal().getAttribute("name"));
            newUsuario.setRol(null);
            usuarioRepository.save(newUsuario);
            usuario = newUsuario;
        } else {
            usuario = usuarioOpt.get();
            if (!(usuario instanceof Profesor)) {
                response.sendRedirect(
                        redirect_to + "?error=El usuario " + email + " no pertenece a una cuenta de profesor");
                return;
            }
        }
        String token = jwtUtil.generateToken(usuario);
        response.sendRedirect(redirect_to + "?token=" + token);
    }
    
}