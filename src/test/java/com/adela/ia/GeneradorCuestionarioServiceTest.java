package com.adela.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.adela.dto.CuestionarioDTO;
import com.adela.dto.EstiloDTO;
import com.adela.dto.OpcionDTO;
import com.adela.dto.OpcionDTO.PesoDTO;
import com.adela.dto.PreguntaDTO;
import com.adela.calificacion.FormatoItem;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.ia.ClienteLlm.MensajeLlm;
import com.adela.ia.ClienteLlm.PeticionLlm;
import com.adela.ia.ClienteLlm.ProgresoLlm;
import com.adela.ia.ClienteLlm.RespuestaLlm;
import com.adela.ia.ClienteLlm.UsoLlm;
import com.adela.ia.DocumentoExtraido.PaginaExtraida;
import com.adela.ia.EstadoTrabajo.Estado;
import com.adela.ia.EstadoTrabajo.Fase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * La máquina de estados de Nelse con un {@link ClienteLlm} simulado, un ejecutor síncrono
 * (el trabajo termina dentro de {@code crear}) y datos ficticios. El validador y el prompt
 * son los reales: el error duro de las pruebas es uno de verdad (descripción de más de
 * 1000 caracteres) y se corrige con un patch de verdad.
 */
class GeneradorCuestionarioServiceTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final String EMAIL = "admin@ejemplo.test";
    private static final String INSTRUCCIONES = "Crea una escala breve de dos factores.";
    private static final String PATCH_ARREGLA = "{\"patch\":[{\"op\":\"replace\",\"path\":\"/cuestionario/descripcion\","
            + "\"value\":\"Descripción corta.\"}]}";
    private static final String PATCH_NO_ARREGLA = "{\"patch\":[{\"op\":\"replace\",\"path\":\"/cuestionario/descripcion\","
            + "\"value\":\"" + "x".repeat(1001) + "\"}]}";
    private static final String PATCH_VACIO = "{\"patch\":[]}";
    private static final String PATCH_INVALIDO = "{\"patch\":[{\"op\":\"replace\",\"path\":\"/cuestionario/noExiste\","
            + "\"value\":1}]}";

    private final ClienteLlm llm = mock(ClienteLlm.class);
    private final ExtractorDocumento extractor = mock(ExtractorDocumento.class);
    private final PromptIa prompt = new PromptIa(OM, null, false);
    private final ValidadorGeneracion validador = spy(new ValidadorGeneracion(OM, prompt));
    private final TrabajosIa trabajos = new TrabajosIa(30, Runnable::run, Clock.systemUTC());
    private final GeneradorCuestionarioService servicio = new GeneradorCuestionarioService(llm, prompt, validador,
            extractor, trabajos, OM, "high", "low", 32000);

    // ---------------------------------------------------------------- Armado

    private static EstiloDTO estilo(int id, String nombre) {
        EstiloDTO e = new EstiloDTO();
        e.setId(id);
        e.setNombre(nombre);
        return e;
    }

    private static PreguntaDTO likert(int orden, int estiloId) {
        PreguntaDTO p = new PreguntaDTO();
        p.setOrden(orden);
        p.setPregunta("Afirmación " + orden);
        p.setFormato(FormatoItem.UNICA);
        List<OpcionDTO> opciones = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            OpcionDTO o = new OpcionDTO();
            o.setOrden(i);
            o.setRespuesta("Opción " + i);
            o.setPesos(List.of(new PesoDTO(estiloId, (double) i)));
            opciones.add(o);
        }
        p.setOpciones(opciones);
        return p;
    }

    /** El envoltorio {cuestionario, analisis} con una descripción dada (de más de 1000 caracteres = error duro). */
    private static String salida(String descripcion) throws Exception {
        CuestionarioDTO c = new CuestionarioDTO();
        c.setNombre("Escala de prueba");
        c.setSiglas("EP");
        c.setDescripcion(descripcion);
        c.setAutor("Autora");
        c.setVersion("1.0");
        c.setEstilos(List.of(estilo(1, "A"), estilo(2, "B")));
        c.setPreguntas(List.of(likert(1, 1), likert(2, 1), likert(3, 1), likert(4, 2), likert(5, 2), likert(6, 2)));
        return OM.writeValueAsString(Map.of("cuestionario", c, "analisis",
                Map.of("modo", "CREACION", "preguntasAlProfesor", List.of("¿Qué lectura quieres?"))));
    }

    private static String buena() throws Exception {
        return salida("Seis afirmaciones para probar a Nelse.");
    }

    private static String conError() throws Exception {
        return salida("y".repeat(1001));
    }

    private static RespuestaLlm respuesta(String contenido) {
        return new RespuestaLlm(contenido, "stop", new UsoLlm(1000, 500, 800, 200, 100), Duration.ofSeconds(1));
    }

    private static RespuestaLlm truncada() {
        return new RespuestaLlm("{\"cuestionario\":{", "length", new UsoLlm(1000, 32000, 800, 200, 30000),
                Duration.ofSeconds(1));
    }

    private void responde(RespuestaLlm... respuestas) {
        when(llm.completar(any(), any())).thenReturn(respuestas[0],
                java.util.Arrays.copyOfRange(respuestas, 1, respuestas.length));
    }

    private List<PeticionLlm> peticiones(int esperadas) {
        ArgumentCaptor<PeticionLlm> captor = ArgumentCaptor.forClass(PeticionLlm.class);
        verify(llm, times(esperadas)).completar(captor.capture(), any());
        return captor.getAllValues();
    }

    private Trabajo generar() {
        return servicio.crear(EMAIL, null, INSTRUCCIONES);
    }

    private static SolicitudRefinamiento respuestaDelProfesor() {
        return new SolicitudRefinamiento(
                List.of(new SolicitudRefinamiento.Respuesta("¿Qué lectura quieres?", "Niveles bajo, medio y alto")),
                null);
    }

    private static void assertConError(EstadoTrabajo e, ErrorCode codigo) {
        assertEquals(Estado.FALLIDO, e.estado());
        assertEquals(codigo.name(), e.error().codigo());
        assertNull(e.resultado());
        assertNull(e.fase());
    }

    // ---------------------------------------------------------------- Camino feliz

    @Test
    @DisplayName("Salida válida a la primera: LISTO sin llamada de corrección, con mensajes en el orden del plan")
    void caminoFeliz() throws Exception {
        responde(respuesta(buena()));

        EstadoTrabajo e = generar().estado();

        assertEquals(Estado.LISTO, e.estado());
        assertNull(e.fase());
        assertEquals(100, e.progreso());
        assertTrue(e.version() > 0);
        assertFalse(e.refinado());
        assertNull(e.error());
        assertNull(e.aviso());
        assertNotNull(e.resultado().cuestionario());
        assertTrue(e.resultado().erroresDuros().isEmpty());
        assertEquals(List.of("¿Qué lectura quieres?"), e.preguntasAlProfesor());

        PeticionLlm p = peticiones(1).get(0);
        assertEquals("high", p.esfuerzo());
        assertEquals(32000, p.maxTokens());
        assertTrue(p.json());
        assertEquals(2, p.mensajes().size());
        assertEquals(new MensajeLlm("system", prompt.prefijoNelse()), p.mensajes().get(0));
        assertEquals(new MensajeLlm("user", "PROFESOR:\n" + INSTRUCCIONES), p.mensajes().get(1));
    }

    @Test
    @DisplayName("Con PDF: el usuario empieza por DOCUMENTO (texto limpio con marcador de página) y luego PROFESOR")
    void conDocumento() throws Exception {
        when(extractor.extraer(any())).thenReturn(new DocumentoExtraido(
                List.of(new PaginaExtraida(1, "Afirmación uno de un documento ficticio.", false, null, false))));
        responde(respuesta(buena()));

        Trabajo t = servicio.crear(EMAIL, new byte[] { 1 }, null);

        assertEquals(Estado.LISTO, t.estado().estado());
        MensajeLlm usuario = peticiones(1).get(0).mensajes().get(1);
        assertTrue(usuario.contenido().startsWith("DOCUMENTO:\n--- página 1 ---\nAfirmación uno de un documento ficticio."),
                usuario.contenido());
        assertTrue(usuario.contenido().endsWith("\n\nPROFESOR:\n(sin instrucciones)"), usuario.contenido());
    }

    @Test
    @DisplayName("Un PDF solo de texto manda exactamente el mensaje de siempre: una cadena, sin imágenes ni notas")
    void pdfDeTextoNoCambiaElMensaje() throws Exception {
        when(extractor.extraer(any())).thenReturn(new DocumentoExtraido(
                List.of(new PaginaExtraida(1, "Afirmación uno de un documento ficticio.", false, null, false),
                        new PaginaExtraida(2, "Afirmación dos de un documento ficticio.", false, null, false))));
        responde(respuesta(buena()));

        servicio.crear(EMAIL, new byte[] { 1 }, INSTRUCCIONES);

        assertEquals(new MensajeLlm("user", "DOCUMENTO:\n--- página 1 ---\nAfirmación uno de un documento ficticio.\n\n"
                + "--- página 2 ---\nAfirmación dos de un documento ficticio.\n\nPROFESOR:\n" + INSTRUCCIONES),
                peticiones(1).get(0).mensajes().get(1));
        assertTrue(peticiones(1).get(0).mensajes().get(1).imagenesJpeg().isEmpty());
    }

    @Test
    @DisplayName("PDF sin texto, sin imágenes que leer y sin instrucciones: FALLIDO DOCUMENTO_INVALIDO sin llamar al modelo")
    void pdfSinNadaQueLeer() {
        // Una escaneada que no cupo en el tope (ni texto ni imagen) y una de texto que la limpieza deja vacía.
        when(extractor.extraer(any())).thenReturn(new DocumentoExtraido(
                List.of(new PaginaExtraida(1, "3", true, null, true),
                        new PaginaExtraida(2, "https://ejemplo.test/solo-una-direccion-web-sin-contenido", false, null,
                                false))));

        EstadoTrabajo e = servicio.crear(EMAIL, new byte[] { 1 }, null).estado();

        assertConError(e, ErrorCode.DOCUMENTO_INVALIDO);
        assertTrue(e.error().mensaje().contains("texto legible"));
        verifyNoLlm();
    }

    @Test
    @DisplayName("PDF sin texto ni imágenes pero con instrucciones: sigue como si no hubiera PDF")
    void pdfSinNadaQueLeerConInstrucciones() throws Exception {
        when(extractor.extraer(any())).thenReturn(new DocumentoExtraido(
                List.of(new PaginaExtraida(1, "3", true, null, true))));
        responde(respuesta(buena()));

        EstadoTrabajo e = servicio.crear(EMAIL, new byte[] { 1 }, INSTRUCCIONES).estado();

        assertEquals(Estado.LISTO, e.estado());
        assertEquals(new MensajeLlm("user", "PROFESOR:\n" + INSTRUCCIONES), peticiones(1).get(0).mensajes().get(1));
    }

    // ---------------------------------------------------------------- Visión

    private static final byte[] PAGINA_1 = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1 };
    private static final byte[] PAGINA_2 = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 2 };
    private static final byte[] PAGINA_3 = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 3 };

    private static final String TEXTO_PAGINA_1 = "Afirmación de la primera página de un documento ficticio.";
    private static final String TEXTO_PAGINA_3 = "Afirmación de la tercera página de un documento ficticio.";

    private void extrae(PaginaExtraida... paginas) {
        when(extractor.extraer(any())).thenReturn(new DocumentoExtraido(List.of(paginas)));
    }

    @Test
    @DisplayName("PDF solo de imágenes: DOCUMENTO es la nota, las páginas van adjuntas en orden y L4 sabe que son imagen")
    void pdfEscaneado() throws Exception {
        extrae(new PaginaExtraida(1, "", true, PAGINA_1, false), new PaginaExtraida(2, "", true, PAGINA_2, false),
                new PaginaExtraida(3, "3", true, PAGINA_3, false));
        responde(respuesta(buena()));

        EstadoTrabajo e = servicio.crear(EMAIL, new byte[] { 1 }, null).estado();

        assertEquals(Estado.LISTO, e.estado());
        MensajeLlm usuario = peticiones(1).get(0).mensajes().get(1);
        assertEquals("user", usuario.rol());
        assertEquals("DOCUMENTO:\n(sin texto: el documento llega como 3 imágenes de páginas escaneadas, adjuntas a "
                + "continuación en orden: páginas 1 a 3)\n\nPROFESOR:\n(sin instrucciones)", usuario.contenido());
        assertEquals(3, usuario.imagenesJpeg().size());
        assertSame(PAGINA_1, usuario.imagenesJpeg().get(0));
        assertSame(PAGINA_2, usuario.imagenesJpeg().get(1));
        assertSame(PAGINA_3, usuario.imagenesJpeg().get(2));
        ArgumentCaptor<FuenteNelse> fuente = ArgumentCaptor.forClass(FuenteNelse.class);
        verify(validador).validar(any(), fuente.capture());
        assertEquals(3, fuente.getValue().paginasImagen());
        assertEquals(0, fuente.getValue().paginasOmitidas());
        assertFalse(fuente.getValue().hayDocumento());
    }

    @Test
    @DisplayName("PDF escaneado con instrucciones: las imágenes viajan con el mensaje y PROFESOR queda después de la nota")
    void pdfEscaneadoConInstrucciones() throws Exception {
        extrae(new PaginaExtraida(1, "", true, PAGINA_1, false), new PaginaExtraida(2, "", true, PAGINA_2, false));
        responde(respuesta(buena()));

        EstadoTrabajo e = servicio.crear(EMAIL, new byte[] { 1 }, INSTRUCCIONES).estado();

        assertEquals(Estado.LISTO, e.estado());
        MensajeLlm usuario = peticiones(1).get(0).mensajes().get(1);
        assertEquals("DOCUMENTO:\n(sin texto: el documento llega como 2 imágenes de páginas escaneadas, adjuntas a "
                + "continuación en orden: páginas 1 a 2)\n\nPROFESOR:\n" + INSTRUCCIONES, usuario.contenido());
        assertEquals(2, usuario.imagenesJpeg().size());
        ArgumentCaptor<FuenteNelse> fuente = ArgumentCaptor.forClass(FuenteNelse.class);
        verify(validador).validar(any(), fuente.capture());
        assertEquals(2, fuente.getValue().paginasImagen());
        assertFalse(fuente.getValue().hayDocumento());
    }

    @Test
    @DisplayName("PDF mixto: el texto bajo sus marcadores, la página-imagen con el suyo vacío y la nota con su número")
    void pdfMixto() throws Exception {
        extrae(new PaginaExtraida(1, TEXTO_PAGINA_1, false, null, false),
                new PaginaExtraida(2, "", true, PAGINA_2, false),
                new PaginaExtraida(3, TEXTO_PAGINA_3, false, null, false));
        responde(respuesta(buena()));

        servicio.crear(EMAIL, new byte[] { 1 }, null);

        MensajeLlm usuario = peticiones(1).get(0).mensajes().get(1);
        assertEquals("DOCUMENTO:\n--- página 1 ---\n" + TEXTO_PAGINA_1 + "\n\n--- página 2 (imagen adjunta) ---\n\n"
                + "--- página 3 ---\n" + TEXTO_PAGINA_3 + "\n\n(las páginas 2 no tienen texto: llegan como imágenes "
                + "adjuntas a continuación, en ese orden)\n\nPROFESOR:\n(sin instrucciones)", usuario.contenido());
        assertEquals(1, usuario.imagenesJpeg().size());
        assertSame(PAGINA_2, usuario.imagenesJpeg().get(0));
        // L4 compara contra el texto de las páginas con texto, sin marcadores ni nota de imagen.
        ArgumentCaptor<FuenteNelse> fuente = ArgumentCaptor.forClass(FuenteNelse.class);
        verify(validador).validar(any(), fuente.capture());
        assertEquals("--- página 1 ---\n" + TEXTO_PAGINA_1 + "\n\n--- página 3 ---\n" + TEXTO_PAGINA_3,
                fuente.getValue().documento());
        assertEquals(1, fuente.getValue().paginasImagen());
    }

    @Test
    @DisplayName("Las frases de DOCUMENTO con imágenes están fijadas: son las que midió el spike y las de ia-eval")
    void textoDelDocumentoConImagenes() {
        // Solo imágenes, de la 1 a la N.
        assertEquals("(sin texto: el documento llega como 3 imágenes de páginas escaneadas, adjuntas a continuación "
                + "en orden: páginas 1 a 3)",
                GeneradorCuestionarioService.documentoParaElModelo(List.of("", "", ""), List.of(1, 2, 3)));
        // Mixto, con la imagen al final y otra en medio.
        assertEquals("--- página 1 ---\nUno\n\n--- página 2 (imagen adjunta) ---\n\n--- página 3 ---\nTres\n\n"
                + "--- página 4 (imagen adjunta) ---\n\n(las páginas 2, 4 no tienen texto: llegan como imágenes "
                + "adjuntas a continuación, en ese orden)",
                GeneradorCuestionarioService.documentoParaElModelo(List.of("Uno", "", "Tres", ""), List.of(2, 4)));
        // Sin texto pero las imágenes no empiezan en la 1: la nota "1 a N" mentiría, vale la disposición general.
        assertEquals("--- página 2 (imagen adjunta) ---\n\n--- página 3 (imagen adjunta) ---\n\n(las páginas 2, 3 no "
                + "tienen texto: llegan como imágenes adjuntas a continuación, en ese orden)",
                GeneradorCuestionarioService.documentoParaElModelo(List.of("", "", ""), List.of(2, 3)));
        // Sin imágenes: el texto limpio de siempre.
        assertEquals("--- página 1 ---\nUno\n\n--- página 3 ---\nTres",
                GeneradorCuestionarioService.documentoParaElModelo(List.of("Uno", "", "Tres"), List.of()));
    }

    @Test
    @DisplayName("Páginas fuera del tope de imágenes: MEDIO en 'items' avisa cuántas no se enviaron, y no entran al mensaje")
    void paginasOmitidas() throws Exception {
        extrae(new PaginaExtraida(1, "", true, PAGINA_1, false), new PaginaExtraida(2, "", true, null, true),
                new PaginaExtraida(3, "", true, null, true));
        responde(respuesta(buena()));

        EstadoTrabajo e = servicio.crear(EMAIL, new byte[] { 1 }, null).estado();

        assertEquals(Estado.LISTO, e.estado());
        MensajeLlm usuario = peticiones(1).get(0).mensajes().get(1);
        assertEquals(1, usuario.imagenesJpeg().size());
        assertTrue(usuario.contenido().contains("1 imágenes de páginas escaneadas"), usuario.contenido());
        List<Marca> avisos = e.resultado().marcas().stream().filter(m -> m.mensaje().contains("no se enviaron")).toList();
        assertEquals(1, avisos.size());
        assertEquals(Marca.MEDIO, avisos.get(0).riesgo());
        assertEquals(Marca.VALIDADOR, avisos.get(0).origen());
        assertEquals("items", avisos.get(0).seccion());
        assertTrue(avisos.get(0).mensaje().startsWith("2 páginas escaneadas no se enviaron a Nelse (tope de 1 imagen)"),
                avisos.get(0).mensaje());
    }

    @Test
    @DisplayName("Corregir reenvía el mismo prefijo con los mismos bytes de imagen, y el mismo esfuerzo si así se configura")
    void correccionReenviaLasImagenes() throws Exception {
        GeneradorCuestionarioService mismoEsfuerzo = new GeneradorCuestionarioService(llm, prompt, validador, extractor,
                trabajos, OM, "high", "high", 32000);
        extrae(new PaginaExtraida(1, "", true, PAGINA_1, false), new PaginaExtraida(2, "", true, PAGINA_2, false));
        responde(respuesta(conError()), respuesta(PATCH_ARREGLA));

        EstadoTrabajo e = mismoEsfuerzo.crear(EMAIL, new byte[] { 1 }, null).estado();

        assertEquals(Estado.LISTO, e.estado());
        List<PeticionLlm> ps = peticiones(2);
        assertEquals(ps.get(0).esfuerzo(), ps.get(1).esfuerzo());
        assertTrue(ps.get(0).json() && ps.get(1).json());
        List<MensajeLlm> segunda = ps.get(1).mensajes();
        assertEquals(4, segunda.size());
        assertEquals(ps.get(0).mensajes(), segunda.subList(0, 2));
        // El mismo objeto, no una copia: es lo que garantiza bytes idénticos y, con ello, el acierto de caché.
        assertSame(ps.get(0).mensajes().get(1), segunda.get(1));
        assertSame(PAGINA_1, segunda.get(1).imagenesJpeg().get(0));
        assertSame(PAGINA_2, segunda.get(1).imagenesJpeg().get(1));
        assertTrue(segunda.get(2).imagenesJpeg().isEmpty());
        assertTrue(segunda.get(3).imagenesJpeg().isEmpty());
    }

    @Test
    @DisplayName("Refinar también reenvía el mensaje original con sus imágenes")
    void refinarReenviaLasImagenes() throws Exception {
        extrae(new PaginaExtraida(1, "", true, PAGINA_1, false));
        responde(respuesta(buena()), respuesta(PATCH_VACIO));
        Trabajo t = servicio.crear(EMAIL, new byte[] { 1 }, null);

        servicio.refinar(EMAIL, t.id(), respuestaDelProfesor());

        List<PeticionLlm> ps = peticiones(2);
        assertSame(ps.get(0).mensajes().get(1), ps.get(1).mensajes().get(1));
        assertSame(PAGINA_1, ps.get(1).mensajes().get(1).imagenesJpeg().get(0));
        verify(extractor, times(1)).extraer(any());
    }

    private void verifyNoLlm() {
        org.mockito.Mockito.verifyNoInteractions(llm);
    }

    // ---------------------------------------------------------------- Corrección

    @Test
    @DisplayName("Error duro + patch que lo arregla: LISTO tras 1 corrección, con el turno de corrección al final")
    void patchArregla() throws Exception {
        responde(respuesta(conError()), respuesta(PATCH_ARREGLA));

        EstadoTrabajo e = generar().estado();

        assertEquals(Estado.LISTO, e.estado());
        assertTrue(e.resultado().erroresDuros().isEmpty());
        assertEquals("Descripción corta.", e.resultado().cuestionario().getDescripcion());
        assertEquals(1, e.resultado().metricas().rondas());

        List<PeticionLlm> ps = peticiones(2);
        List<MensajeLlm> segunda = ps.get(1).mensajes();
        assertEquals("low", ps.get(1).esfuerzo());
        assertEquals(4, segunda.size());
        // Prefijo idéntico: sistema y usuario no cambian, luego la salida previa tal cual y la corrección.
        assertEquals(ps.get(0).mensajes(), segunda.subList(0, 2));
        assertEquals(new MensajeLlm("assistant", conError()), segunda.get(2));
        assertEquals("user", segunda.get(3).rol());
        assertTrue(segunda.get(3).contenido().startsWith(prompt.correccion()));
        assertTrue(segunda.get(3).contenido().contains("- descripcion: "), segunda.get(3).contenido());
    }

    @Test
    @DisplayName("Patch que no aplica: una regeneración completa con la misma petición, y LISTO")
    void patchInvalidoRegenera() throws Exception {
        responde(respuesta(conError()), respuesta(PATCH_INVALIDO), respuesta(buena()));

        EstadoTrabajo e = generar().estado();

        assertEquals(Estado.LISTO, e.estado());
        assertTrue(e.resultado().erroresDuros().isEmpty());
        List<PeticionLlm> ps = peticiones(3);
        assertEquals(ps.get(0), ps.get(2));
    }

    @Test
    @DisplayName("Patch vacío: también regenera, y solo una vez en todo el trabajo")
    void patchVacioRegeneraUnaVez() throws Exception {
        responde(respuesta(conError()), respuesta(PATCH_VACIO), respuesta(conError()), respuesta(PATCH_VACIO));

        EstadoTrabajo e = generar().estado();

        // Segundo patch vacío: ya no hay regeneración; se entrega con el error para el editor.
        assertEquals(Estado.LISTO, e.estado());
        assertEquals(List.of("descripcion"), List.copyOf(e.resultado().erroresDuros().keySet()));
        peticiones(4);
    }

    @Test
    @DisplayName("Errores que persisten tras 2 rondas: LISTO con erroresDuros, sin regenerar")
    void erroresPersistentes() throws Exception {
        responde(respuesta(conError()), respuesta(PATCH_NO_ARREGLA), respuesta(PATCH_NO_ARREGLA));

        EstadoTrabajo e = generar().estado();

        assertEquals(Estado.LISTO, e.estado());
        assertEquals(List.of("descripcion"), List.copyOf(e.resultado().erroresDuros().keySet()));
        assertEquals(2, e.resultado().metricas().rondas());
        peticiones(3);
    }

    @Test
    @DisplayName("Un patch truncado (finish_reason length) cuenta como patch fallido")
    void patchTruncadoRegenera() throws Exception {
        responde(respuesta(conError()), truncada(), respuesta(buena()));

        EstadoTrabajo e = generar().estado();

        assertEquals(Estado.LISTO, e.estado());
        assertTrue(e.resultado().erroresDuros().isEmpty());
        peticiones(3);
    }

    // ---------------------------------------------------------------- Generación inutilizable

    @Test
    @DisplayName("Generación truncada dos veces: FALLIDO IA_RESPUESTA_INVALIDA tras una sola regeneración")
    void generacionTruncada() {
        responde(truncada(), truncada());

        assertConError(generar().estado(), ErrorCode.IA_RESPUESTA_INVALIDA);
        peticiones(2);
    }

    @Test
    @DisplayName("Salida ilegible y luego válida: LISTO")
    void salidaIlegibleLuegoValida() throws Exception {
        responde(respuesta("Lo siento, no puedo."), respuesta(buena()));

        assertEquals(Estado.LISTO, generar().estado().estado());
        peticiones(2);
    }

    @Test
    @DisplayName("Salida ilegible dos veces: FALLIDO IA_RESPUESTA_INVALIDA")
    void salidaIlegibleDosVeces() {
        responde(respuesta("{\"algo\": 1}"), respuesta("sin json"));

        assertConError(generar().estado(), ErrorCode.IA_RESPUESTA_INVALIDA);
    }

    @Test
    @DisplayName("Generación truncada y regeneración válida: LISTO")
    void truncadaLuegoValida() throws Exception {
        responde(truncada(), respuesta(buena()));

        assertEquals(Estado.LISTO, generar().estado().estado());
    }

    // ---------------------------------------------------------------- Fallos del proveedor

    @Test
    @DisplayName("El cliente falla por tiempo (IA_NO_DISPONIBLE): FALLIDO con ese código y su mensaje")
    void proveedorNoDisponible() {
        when(llm.completar(any(), any()))
                .thenThrow(new AppException(ErrorCode.IA_NO_DISPONIBLE, "El proveedor no respondió a tiempo."));

        EstadoTrabajo e = generar().estado();

        assertConError(e, ErrorCode.IA_NO_DISPONIBLE);
        assertEquals("El proveedor no respondió a tiempo.", e.error().mensaje());
    }

    @Test
    @DisplayName("IA deshabilitada en el cliente: FALLIDO IA_DESHABILITADA")
    void clienteDeshabilitado() {
        when(llm.completar(any(), any()))
                .thenThrow(new AppException(ErrorCode.IA_DESHABILITADA, "El asistente de IA no está habilitado."));

        assertConError(generar().estado(), ErrorCode.IA_DESHABILITADA);
    }

    @Test
    @DisplayName("Un fallo inesperado es ERROR_INTERNO con mensaje genérico, sin el detalle de la excepción")
    void falloInesperado() {
        when(llm.completar(any(), any())).thenThrow(new IllegalStateException("detalle secreto del documento"));

        EstadoTrabajo e = generar().estado();

        assertConError(e, ErrorCode.ERROR_INTERNO);
        assertFalse(e.error().mensaje().contains("secreto"));
    }

    // ---------------------------------------------------------------- Métricas

    @Test
    @DisplayName("Las métricas suman todas las llamadas y calculan USD con los precios de deepseek-flash")
    void metricas() throws Exception {
        responde(respuesta(conError()), respuesta(PATCH_ARREGLA));

        EstadoTrabajo.Metricas m = generar().estado().resultado().metricas();

        assertEquals(3000, m.tokens());
        // 2 llamadas: 400 fallos x 0.15 + 1600 aciertos x 0.003 + 1000 salida x 0.60, por millón.
        assertEquals((400 * 0.15 + 1600 * 0.003 + 1000 * 0.60) / 1e6, m.usd(), 1e-12);
        assertEquals(1, m.rondas());
        assertEquals(prompt.versionPrompt(), m.versionPrompt());
        assertTrue(m.segundos() >= 0);
    }

    // ---------------------------------------------------------------- Avance y cancelación

    /** Ejecutor que guarda el trabajo para correrlo a mano: permite mirar el estado desde dentro de la llamada. */
    private static final class Diferido {
        final Queue<Runnable> pendientes = new ArrayDeque<>();
        final TrabajosIa trabajos = new TrabajosIa(30, pendientes::add, Clock.systemUTC());
    }

    @Test
    @DisplayName("El avance sube con el stream, nunca baja, y cada cambio sube la versión")
    void avanceMonotono() throws Exception {
        Diferido d = new Diferido();
        GeneradorCuestionarioService s = new GeneradorCuestionarioService(llm, prompt, validador, extractor,
                d.trabajos, OM, "high", "low", 32000);
        Trabajo t = s.crear(EMAIL, null, INSTRUCCIONES);
        assertEquals(Estado.EN_CURSO, t.estado().estado());

        List<EstadoTrabajo> vistos = new ArrayList<>();
        when(llm.completar(any(), any())).thenAnswer(inv -> {
            Consumer<ProgresoLlm> progreso = inv.getArgument(1);
            vistos.add(t.estado());
            progreso.accept(new ProgresoLlm(12_500, 0));
            vistos.add(t.estado());
            progreso.accept(new ProgresoLlm(100, 0));
            vistos.add(t.estado());
            progreso.accept(new ProgresoLlm(900_000, 0));
            vistos.add(t.estado());
            return respuesta(buena());
        });
        d.pendientes.poll().run();

        assertEquals(Fase.GENERANDO, vistos.get(0).fase());
        assertEquals(Estado.EN_CURSO, vistos.get(0).estado());
        assertEquals(40, vistos.get(1).progreso());
        assertEquals(40, vistos.get(2).progreso());
        assertEquals(vistos.get(1).version(), vistos.get(2).version());
        // Tope de la fase: 10 + 60. Un stream más largo de lo esperado no pasa de ahí.
        assertEquals(70, vistos.get(3).progreso());
        for (int i = 1; i < vistos.size(); i++) {
            assertTrue(vistos.get(i).progreso() >= vistos.get(i - 1).progreso());
            assertTrue(vistos.get(i).version() >= vistos.get(i - 1).version());
        }
        assertTrue(t.estado().version() > vistos.get(3).version());
    }

    @Test
    @DisplayName("Cancelar a mitad de la llamada descarta el resultado: no se valida, no se corrige, no queda LISTO")
    void cancelarDescarta() throws Exception {
        Diferido d = new Diferido();
        GeneradorCuestionarioService s = new GeneradorCuestionarioService(llm, prompt, validador, extractor,
                d.trabajos, OM, "high", "low", 32000);
        Trabajo t = s.crear(EMAIL, null, INSTRUCCIONES);
        when(llm.completar(any(), any())).thenAnswer(inv -> {
            d.trabajos.cancelar(EMAIL, t.id());
            return respuesta(conError());
        });

        d.pendientes.poll().run();

        // Cancelar interrumpe al hilo que corre (aquí, el de la prueba): se limpia para no contagiar otras.
        assertTrue(Thread.interrupted());
        assertEquals(Estado.EN_CURSO, t.estado().estado());
        assertNull(t.estado().resultado());
        peticiones(1);
        org.mockito.Mockito.verify(validador, org.mockito.Mockito.never()).validar(any(), any());
        AppException e = assertThrows(AppException.class, () -> d.trabajos.obtener(EMAIL, t.id()));
        assertEquals(ErrorCode.RECURSO_NO_ENCONTRADO, e.getCode());
    }

    @Test
    @DisplayName("Cancelar antes de que arranque impide que llegue a llamar al modelo")
    void cancelarAntesDeArrancar() {
        Diferido d = new Diferido();
        GeneradorCuestionarioService s = new GeneradorCuestionarioService(llm, prompt, validador, extractor,
                d.trabajos, OM, "high", "low", 32000);
        Trabajo t = s.crear(EMAIL, null, INSTRUCCIONES);

        d.trabajos.cancelar(EMAIL, t.id());
        d.pendientes.poll().run();

        verifyNoLlm();
    }

    // ---------------------------------------------------------------- Refinamiento

    @Test
    @DisplayName("Refinar: el modelo ve el JSON vigente y las respuestas, y las respuestas cuentan como fuente PROFESOR")
    void refinar() throws Exception {
        responde(respuesta(buena()),
                respuesta("{\"patch\":[{\"op\":\"replace\",\"path\":\"/cuestionario/nombre\",\"value\":\"Escala ajustada\"}]}"));
        Trabajo t = generar();
        assertEquals(Estado.LISTO, t.estado().estado());
        int versionAntes = t.estado().version();

        servicio.refinar(EMAIL, t.id(), respuestaDelProfesor());

        EstadoTrabajo e = t.estado();
        assertEquals(Estado.LISTO, e.estado());
        assertTrue(e.refinado());
        assertNull(e.aviso());
        assertEquals("Escala ajustada", e.resultado().cuestionario().getNombre());
        assertTrue(e.version() > versionAntes);

        PeticionLlm p = peticiones(2).get(1);
        assertEquals("high", p.esfuerzo());
        List<MensajeLlm> m = p.mensajes();
        assertEquals(4, m.size());
        assertEquals(peticiones(2).get(0).mensajes(), m.subList(0, 2));
        assertEquals("assistant", m.get(2).rol());
        assertTrue(m.get(2).contenido().contains("Escala de prueba"));
        assertTrue(m.get(3).contenido().startsWith(prompt.refinamiento()));
        assertTrue(m.get(3).contenido().endsWith("- ¿Qué lectura quieres?: Niveles bajo, medio y alto\n"));

        // La validación del ajuste usa las instrucciones y las respuestas, pero no la pregunta del modelo.
        ArgumentCaptor<FuenteNelse> fuente = ArgumentCaptor.forClass(FuenteNelse.class);
        verify(validador, atLeastOnce()).validar(any(), fuente.capture());
        String profesor = fuente.getAllValues().get(fuente.getAllValues().size() - 1).profesor();
        assertTrue(profesor.contains(INSTRUCCIONES));
        assertTrue(profesor.contains("Niveles bajo, medio y alto"));
        assertFalse(profesor.contains("¿Qué lectura quieres?"));
    }

    @Test
    @DisplayName("Solo se refina una vez por trabajo")
    void refinarUnaVez() throws Exception {
        responde(respuesta(buena()), respuesta(PATCH_VACIO));
        Trabajo t = generar();
        servicio.refinar(EMAIL, t.id(), respuestaDelProfesor());

        AppException e = assertThrows(AppException.class,
                () -> servicio.refinar(EMAIL, t.id(), respuestaDelProfesor()));

        assertEquals(ErrorCode.CONFLICTO_DATOS, e.getCode());
        peticiones(2);
    }

    @Test
    @DisplayName("Solo se refina un trabajo LISTO")
    void refinarSoloListo() {
        Trabajo enCurso = trabajos.iniciar(EMAIL, t -> {
        });

        AppException e = assertThrows(AppException.class,
                () -> servicio.refinar(EMAIL, enCurso.id(), respuestaDelProfesor()));

        assertEquals(ErrorCode.CONFLICTO_DATOS, e.getCode());
        verifyNoLlm();
    }

    @Test
    @DisplayName("Un trabajo FALLIDO tampoco se refina")
    void refinarFallido() {
        when(llm.completar(any(), any())).thenThrow(new AppException(ErrorCode.IA_NO_DISPONIBLE, "caído"));
        Trabajo t = generar();

        AppException e = assertThrows(AppException.class,
                () -> servicio.refinar(EMAIL, t.id(), respuestaDelProfesor()));

        assertEquals(ErrorCode.CONFLICTO_DATOS, e.getCode());
    }

    @Test
    @DisplayName("Patch de refinamiento vacío: LISTO con el resultado anterior y un aviso, y el ajuste queda gastado")
    void refinamientoVacioConservaLoAnterior() throws Exception {
        responde(respuesta(buena()), respuesta(PATCH_VACIO));
        Trabajo t = generar();
        EstadoTrabajo.ResultadoNelse antes = t.estado().resultado();

        servicio.refinar(EMAIL, t.id(), respuestaDelProfesor());

        EstadoTrabajo e = t.estado();
        assertEquals(Estado.LISTO, e.estado());
        assertEquals(antes, e.resultado());
        assertEquals(GeneradorCuestionarioService.AVISO_REFINAMIENTO, e.aviso());
        assertTrue(e.refinado());
        assertNull(e.error());
    }

    @Test
    @DisplayName("Si el proveedor falla durante el ajuste, también se conserva el resultado anterior")
    void refinamientoConProveedorCaido() throws Exception {
        when(llm.completar(any(), any())).thenReturn(respuesta(buena()))
                .thenThrow(new AppException(ErrorCode.IA_NO_DISPONIBLE, "caído"));
        Trabajo t = generar();
        EstadoTrabajo.ResultadoNelse antes = t.estado().resultado();

        servicio.refinar(EMAIL, t.id(), respuestaDelProfesor());

        assertEquals(Estado.LISTO, t.estado().estado());
        assertEquals(antes, t.estado().resultado());
        assertEquals(GeneradorCuestionarioService.AVISO_REFINAMIENTO, t.estado().aviso());
    }

    @Test
    @DisplayName("Un ajuste que deja más errores duros que antes se descarta")
    void refinamientoQueEmpeoraSeDescarta() throws Exception {
        responde(respuesta(buena()), respuesta(PATCH_NO_ARREGLA), respuesta(PATCH_VACIO));
        Trabajo t = generar();
        EstadoTrabajo.ResultadoNelse antes = t.estado().resultado();

        servicio.refinar(EMAIL, t.id(), respuestaDelProfesor());

        // El patch introduce una descripción de 1001 caracteres; la corrección no puede (patch vacío).
        assertEquals(antes, t.estado().resultado());
        assertEquals(GeneradorCuestionarioService.AVISO_REFINAMIENTO, t.estado().aviso());
        peticiones(3);
    }

    @Test
    @DisplayName("Un ajuste que introduce un error y lo corrige por patch sí se acepta")
    void refinamientoConCorreccion() throws Exception {
        responde(respuesta(buena()), respuesta(PATCH_NO_ARREGLA), respuesta(PATCH_ARREGLA));
        Trabajo t = generar();

        servicio.refinar(EMAIL, t.id(), respuestaDelProfesor());

        EstadoTrabajo e = t.estado();
        assertNull(e.aviso());
        assertEquals("Descripción corta.", e.resultado().cuestionario().getDescripcion());
        assertEquals(1, e.resultado().metricas().rondas());
    }

    @Test
    @DisplayName("El estado serializado tiene la forma documentada y no repite las instrucciones")
    void estadoSinCrudo() throws Exception {
        responde(respuesta(buena()));

        String json = OM.writeValueAsString(generar().estado());
        JsonNode raiz = OM.readTree(json);

        assertEquals(List.of("id", "estado", "fase", "progreso", "version", "refinado", "resultado", "error", "aviso",
                "preguntasAlProfesor"), List.copyOf(iterar(raiz)));
        assertEquals(List.of("cuestionario", "analisis", "marcas", "erroresDuros", "metricas"),
                List.copyOf(iterar(raiz.get("resultado"))));
        assertFalse(json.contains(INSTRUCCIONES));
    }

    private static List<String> iterar(JsonNode nodo) {
        List<String> nombres = new ArrayList<>();
        nodo.fieldNames().forEachRemaining(nombres::add);
        return nombres;
    }
}
