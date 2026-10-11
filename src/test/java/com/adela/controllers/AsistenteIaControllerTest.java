package com.adela.controllers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import com.adela.controllers.AsistenteIaController.EstadoIa;
import com.adela.entities.Profesor;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.ia.ClienteLlm;
import com.adela.ia.DocumentoExtraido;
import com.adela.ia.DocumentoExtraido.PaginaExtraida;
import com.adela.ia.EstadoTrabajo;
import com.adela.ia.EstadoTrabajo.Estado;
import com.adela.ia.ExtractorDocumento;
import com.adela.ia.GeneradorCuestionarioService;
import com.adela.ia.PromptIa;
import com.adela.ia.SolicitudRefinamiento;
import com.adela.ia.TrabajosIa;
import com.adela.ia.ValidadorGeneracion;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Sin contexto de Spring: se llaman los métodos del controlador directamente con una
 * autenticación como la que arma JwtFilter (el principal es la entidad). Los trabajos
 * quedan en cola (ejecutor diferido) para que sigan "en curso" mientras la prueba mira.
 * El 403 al profesor no se puede ejercitar sin Spring Security: lo reemplaza comprobar
 * por reflexión que cada endpoint exige el rol de administrador.
 */
class AsistenteIaControllerTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final String ANA = "ana@ejemplo.test";
    private static final String BEA = "bea@ejemplo.test";

    private final Queue<Runnable> pendientes = new ArrayDeque<>();
    private final ClienteLlm llm = mock(ClienteLlm.class);
    private final ExtractorDocumento extractor = mock(ExtractorDocumento.class);
    private final TrabajosIa trabajos = new TrabajosIa(30, pendientes::add, Clock.systemUTC());
    private final GeneradorCuestionarioService generador = servicio();
    private final AsistenteIaController controlador = new AsistenteIaController(generador, trabajos, true, "llave",
            15, 40);

    private GeneradorCuestionarioService servicio() {
        PromptIa prompt = new PromptIa(OM, null);
        return new GeneradorCuestionarioService(llm, prompt, new ValidadorGeneracion(OM, prompt), extractor,
                trabajos, OM, "high", "low", 32000);
    }

    private static Authentication como(String email) {
        Profesor p = new Profesor();
        p.setEmail(email);
        return new UsernamePasswordAuthenticationToken(p, null, List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")));
    }

    private UUID crear(String email) {
        ResponseEntity<Map<String, UUID>> r = controlador.crearTrabajo(null, "Una escala breve.", como(email));
        assertEquals(HttpStatus.ACCEPTED, r.getStatusCode());
        return r.getBody().get("id");
    }

    private static AppException falla(Runnable accion) {
        return assertThrows(AppException.class, accion::run);
    }

    // ---------------------------------------------------------------- Estado

    @Test
    @DisplayName("GET /estado informa límites y cuota, y responde aunque Nelse esté apagada")
    void estado() {
        EstadoIa e = controlador.estado(como(ANA)).getBody();
        assertEquals(new EstadoIa(true, 15, 40, 30), e);

        crear(ANA);
        assertEquals(29, controlador.estado(como(ANA)).getBody().cuotaRestante());
        assertEquals(30, controlador.estado(como(BEA)).getBody().cuotaRestante());

        AsistenteIaController apagada = new AsistenteIaController(generador, trabajos, false, "llave", 15, 40);
        assertFalse(apagada.estado(como(BEA)).getBody().habilitada());
    }

    @Test
    @DisplayName("habilitada exige el interruptor Y la llave")
    void habilitadaExigeLlave() {
        for (String llave : new String[] { "", "  ", null }) {
            AsistenteIaController c = new AsistenteIaController(generador, trabajos, true, llave, 15, 40);
            assertFalse(c.estado(como(ANA)).getBody().habilitada(), String.valueOf(llave));
        }
    }

    // ---------------------------------------------------------------- Crear

    @Test
    @DisplayName("Apagada: 503 IA_DESHABILITADA sin leer el archivo ni crear el trabajo")
    void deshabilitada() {
        AsistenteIaController apagada = new AsistenteIaController(generador, trabajos, false, "llave", 15, 40);
        MultipartFile archivo = mock(MultipartFile.class);

        AppException e = falla(() -> apagada.crearTrabajo(archivo, "texto", como(ANA)));

        assertEquals(ErrorCode.IA_DESHABILITADA, e.getCode());
        verifyNoInteractions(archivo);
        assertEquals(30, trabajos.cuotaRestante(ANA));
    }

    @Test
    @DisplayName("Sin archivo ni instrucciones (o solo espacios): 400 VALIDACION")
    void sinNada() {
        for (String instrucciones : new String[] { null, "", "   \n " }) {
            AppException e = falla(() -> controlador.crearTrabajo(null, instrucciones, como(ANA)));
            assertEquals(ErrorCode.VALIDACION, e.getCode());
        }
        AppException vacio = falla(() -> controlador.crearTrabajo(
                new MockMultipartFile("archivo", "a.pdf", "application/pdf", new byte[0]), null, como(ANA)));
        assertEquals(ErrorCode.VALIDACION, vacio.getCode());
        assertEquals(30, trabajos.cuotaRestante(ANA));
    }

    @Test
    @DisplayName("Instrucciones demasiado largas: 400 VALIDACION")
    void instruccionesLargas() {
        AppException e = falla(() -> controlador.crearTrabajo(null, "x".repeat(AsistenteIaController.MAX_INSTRUCCIONES + 1),
                como(ANA)));
        assertEquals(ErrorCode.VALIDACION, e.getCode());

        controlador.crearTrabajo(null, "x".repeat(AsistenteIaController.MAX_INSTRUCCIONES), como(ANA));
    }

    @Test
    @DisplayName("Crear devuelve 202 con el id; los bytes del PDF se leen en la petición y se extraen en el trabajo")
    void crearConArchivo() {
        byte[] pdf = { '%', 'P', 'D', 'F', '-', 1 };
        when(extractor.extraer(any())).thenReturn(new DocumentoExtraido(
                List.of(new PaginaExtraida(1, "Texto ficticio del documento de prueba.", false, null, false))));

        ResponseEntity<Map<String, UUID>> r = controlador.crearTrabajo(
                new MockMultipartFile("archivo", "a.pdf", "application/pdf", pdf), null, como(ANA));

        assertEquals(HttpStatus.ACCEPTED, r.getStatusCode());
        assertNotNull(r.getBody().get("id"));
        // Encolado: todavía no se extrajo nada.
        verifyNoInteractions(extractor);
        pendientes.poll().run();
        org.mockito.ArgumentCaptor<byte[]> bytes = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(extractor).extraer(bytes.capture());
        assertArrayEquals(pdf, bytes.getValue());
    }

    @Test
    @DisplayName("Segundo trabajo mientras hay uno en curso: 429")
    void unTrabajoEnCurso() {
        crear(ANA);

        AppException e = falla(() -> crear(ANA));

        assertEquals(ErrorCode.DEMASIADAS_SOLICITUDES, e.getCode());
        assertNotNull(crear(BEA));
    }

    @Test
    @DisplayName("Cuota agotada: 429")
    void cuotaAgotada() {
        TrabajosIa corta = new TrabajosIa(1, Runnable::run, Clock.systemUTC());
        AsistenteIaController c = new AsistenteIaController(
                new GeneradorCuestionarioService(llm, new PromptIa(OM, null), new ValidadorGeneracion(OM,
                        new PromptIa(OM, null)), extractor, corta, OM, "high", "low", 32000),
                corta, true, "llave", 15, 40);
        c.crearTrabajo(null, "Una escala.", como(ANA));

        AppException e = falla(() -> c.crearTrabajo(null, "Otra escala.", como(ANA)));

        assertEquals(ErrorCode.DEMASIADAS_SOLICITUDES, e.getCode());
        assertEquals(0, c.estado(como(ANA)).getBody().cuotaRestante());
    }

    // ---------------------------------------------------------------- Consultar, refinar y cancelar

    @Test
    @DisplayName("Consultar devuelve el estado del trabajo; ajeno, inexistente o con id mal formado: 404")
    void consultar() {
        UUID id = crear(ANA);

        EstadoTrabajo e = controlador.consultar(id.toString(), como(ANA)).getBody();
        assertEquals(id, e.id());
        assertEquals(Estado.EN_CURSO, e.estado());

        for (String otro : new String[] { id.toString(), UUID.randomUUID().toString(), "no-es-un-uuid" }) {
            AppException ex = falla(() -> controlador.consultar(otro, como(BEA)));
            assertEquals(ErrorCode.RECURSO_NO_ENCONTRADO, ex.getCode());
        }
        assertEquals(ErrorCode.RECURSO_NO_ENCONTRADO,
                falla(() -> controlador.consultar("no-es-un-uuid", como(ANA))).getCode());
    }

    @Test
    @DisplayName("Refinar: cuerpo vacío o enorme es 400; trabajo ajeno es 404; trabajo no listo es 409")
    void refinar() {
        UUID id = crear(ANA);

        assertEquals(ErrorCode.VALIDACION, falla(() -> controlador.refinar(id.toString(),
                new SolicitudRefinamiento(null, "  "), como(ANA))).getCode());
        assertEquals(ErrorCode.VALIDACION, falla(() -> controlador.refinar(id.toString(),
                new SolicitudRefinamiento(null, "x".repeat(4001)), como(ANA))).getCode());
        assertEquals(ErrorCode.RECURSO_NO_ENCONTRADO, falla(() -> controlador.refinar(id.toString(),
                new SolicitudRefinamiento(null, "una nota"), como(BEA))).getCode());
        assertEquals(ErrorCode.CONFLICTO_DATOS, falla(() -> controlador.refinar(id.toString(),
                new SolicitudRefinamiento(null, "una nota"), como(ANA))).getCode());
    }

    @Test
    @DisplayName("Cancelar: 204 solo para el dueño, y después el trabajo no existe")
    void cancelar() {
        UUID id = crear(ANA);

        assertEquals(ErrorCode.RECURSO_NO_ENCONTRADO,
                falla(() -> controlador.cancelar(id.toString(), como(BEA))).getCode());
        assertEquals(HttpStatus.NO_CONTENT, controlador.cancelar(id.toString(), como(ANA)).getStatusCode());
        assertEquals(ErrorCode.RECURSO_NO_ENCONTRADO,
                falla(() -> controlador.consultar(id.toString(), como(ANA))).getCode());
        // Cancelado, ya no cuenta como trabajo en curso.
        assertNotNull(crear(ANA));
    }

    // ---------------------------------------------------------------- Seguridad

    @Test
    @DisplayName("Todo endpoint del controlador exige hasRole('ADMINISTRADOR') (el profesor recibiría 403)")
    void soloAdministrador() {
        int endpoints = 0;
        for (Method m : AsistenteIaController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic()) {
                continue;
            }
            boolean esEndpoint = m.isAnnotationPresent(GetMapping.class) || m.isAnnotationPresent(PostMapping.class)
                    || m.isAnnotationPresent(DeleteMapping.class);
            assertTrue(esEndpoint, "Método público sin mapeo: " + m.getName());
            PreAuthorize pre = m.getAnnotation(PreAuthorize.class);
            assertNotNull(pre, m.getName() + " sin @PreAuthorize");
            assertEquals("hasRole('ADMINISTRADOR')", pre.value(), m.getName());
            endpoints++;
        }
        assertEquals(5, endpoints);
    }
}
