package com.adela.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.adela.calificacion.EscalaBanda;
import com.adela.calificacion.EsquemaInterpretacion;
import com.adela.calificacion.FormatoItem;
import com.adela.dto.CuestionarioDTO;
import com.adela.dto.EstiloDTO;
import com.adela.dto.InterpretacionDTO;
import com.adela.dto.InterpretacionDTO.BandaDTO;
import com.adela.dto.InterpretacionDTO.EscalonDTO;
import com.adela.dto.InterpretacionDTO.PlanoDTO;
import com.adela.dto.OpcionDTO;
import com.adela.dto.OpcionDTO.PesoDTO;
import com.adela.dto.PreguntaDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * L4 con fuente (vía Nelse): las cifras salen del documento o de las instrucciones, lo que se
 * dice copiado del catálogo es idéntico a él, y los enunciados son los del documento. Los
 * instrumentos y el catálogo son ficticios.
 */
class ProcedenciaTest {

    private static final ObjectMapper OM = new ObjectMapper();

    /** Sin catálogo cargado. */
    private final ValidadorGeneracion validador = new ValidadorGeneracion(OM, new PromptIa(OM, null, false));

    // ---------------------------------------------------------------- Armado

    private static EstiloDTO estilo(int id, String nombre) {
        EstiloDTO e = new EstiloDTO();
        e.setId(id);
        e.setNombre(nombre);
        return e;
    }

    private static OpcionDTO opcion(int orden, String texto, int estiloId, double peso) {
        OpcionDTO o = new OpcionDTO();
        o.setOrden(orden);
        o.setRespuesta(texto);
        o.setPesos(List.of(new PesoDTO(estiloId, peso)));
        return o;
    }

    private static PreguntaDTO likert(int orden, int estiloId) {
        PreguntaDTO p = new PreguntaDTO();
        p.setOrden(orden);
        p.setPregunta("Afirmación " + orden);
        p.setFormato(FormatoItem.UNICA);
        p.setOpciones(List.of(opcion(1, "Opción 1", estiloId, 1), opcion(2, "Opción 2", estiloId, 2),
                opcion(3, "Opción 3", estiloId, 3)));
        return p;
    }

    /** n afirmaciones Likert de tres opciones: la primera mitad puntúa a A y el resto a B. */
    private static CuestionarioDTO base(int n) {
        CuestionarioDTO c = new CuestionarioDTO();
        c.setNombre("Escala de prueba");
        c.setSiglas("EP");
        c.setDescripcion("Afirmaciones para probar la procedencia.");
        c.setAutor("Autora");
        c.setVersion("1.0");
        c.setEstilos(List.of(estilo(1, "A"), estilo(2, "B")));
        List<PreguntaDTO> preguntas = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            preguntas.add(likert(i, i <= n / 2 ? 1 : 2));
        }
        c.setPreguntas(preguntas);
        return c;
    }

    private static CuestionarioDTO base() {
        return base(6);
    }

    private static BandaDTO banda(String estilo, double desde, double hasta, int orden) {
        return new BandaDTO(estilo, EscalaBanda.BRUTO, desde, hasta, "Nivel " + orden, orden);
    }

    private static CuestionarioDTO conBandas(BandaDTO... bandas) {
        CuestionarioDTO dto = base();
        dto.setInterpretacion(new InterpretacionDTO(EsquemaInterpretacion.BAREMO, 10d, null, List.of(bandas), List.of()));
        return dto;
    }

    private static CuestionarioDTO conEscalones(EscalonDTO... escalones) {
        CuestionarioDTO dto = base();
        dto.setInterpretacion(new InterpretacionDTO(EsquemaInterpretacion.RELATIVO_ESCALONADO, 10d, null, List.of(),
                List.of(escalones)));
        return dto;
    }

    private static PlanoDTO plano(Double corteX, Double corteY) {
        return new PlanoDTO("A", "B", corteX, corteY, "Uno", "Dos", "Tres", "Cuatro", false, false);
    }

    private static CuestionarioDTO conPlano(Double corteX, Double corteY) {
        CuestionarioDTO dto = base();
        dto.setInterpretacion(new InterpretacionDTO(EsquemaInterpretacion.CUADRANTES, 10d, null, List.of(), List.of(),
                plano(corteX, corteY), null, null));
        return dto;
    }

    private static CuestionarioDTO copia(CuestionarioDTO dto) throws IOException {
        return OM.readValue(OM.writeValueAsString(dto), CuestionarioDTO.class);
    }

    /** Texto como lo manda Nelse al modelo: limpio y con el marcador de página. */
    private static String pagina(String texto) {
        return LimpiadorTexto.unir(List.of(texto));
    }

    /** El cuestionario impreso: enunciados numerados y la escala al final. */
    private static String documentoDe(CuestionarioDTO dto) {
        StringBuilder sb = new StringBuilder();
        List<String> opciones = new ArrayList<>();
        for (PreguntaDTO p : dto.getPreguntas()) {
            sb.append(p.getOrden()).append(". ").append(p.getPregunta()).append('\n');
            for (OpcionDTO o : p.getOpciones()) {
                if (!opciones.contains(o.getRespuesta())) {
                    opciones.add(o.getRespuesta());
                }
            }
        }
        sb.append("Escala: ").append(String.join(" | ", opciones));
        return pagina(sb.toString());
    }

    private static FuenteNelse fuente(String documento) {
        return new FuenteNelse(documento, null, 0);
    }

    private ResultadoImportacion nelse(ValidadorGeneracion v, CuestionarioDTO dto, String analisis, FuenteNelse f)
            throws IOException {
        JsonNode a = analisis == null ? null : OM.readTree(analisis);
        return v.validar(new LectorJsonTolerante.Entrada(OM.valueToTree(dto), a, null), f);
    }

    private ResultadoImportacion nelse(CuestionarioDTO dto, String analisis, FuenteNelse f) throws IOException {
        return nelse(validador, dto, analisis, f);
    }

    private static List<Marca> marcas(ResultadoImportacion r, String riesgo, String seccion) {
        return r.marcas().stream().filter(m -> Marca.VALIDADOR.equals(m.origen()))
                .filter(m -> riesgo == null || m.riesgo().equals(riesgo))
                .filter(m -> seccion == null || m.seccion().equals(seccion)).toList();
    }

    private static Marca unica(List<Marca> marcas) {
        assertEquals(1, marcas.size(), marcas.toString());
        return marcas.get(0);
    }

    private static String origenes(String clave, String valor) {
        return "{\"origen\": {\"" + clave + "\": \"" + valor + "\"}}";
    }

    // ---------------------------------------------------------------- Cifras: origen DOCUMENTO

    @Test
    @DisplayName("Las cifras de las bandas que aparecen en el documento no generan marca")
    void cifrasPresentes() throws IOException {
        CuestionarioDTO dto = conBandas(banda("A", 3, 5, 1), banda("A", 6, 9, 2));

        ResultadoImportacion r = nelse(dto, origenes("bandas", "DOCUMENTO"),
                fuente(pagina("Puntaje directo: de 3 a 5 nivel bajo; de 6 a 9 nivel alto.")));

        assertTrue(marcas(r, Marca.ALTO, "bandas").isEmpty(), r.marcas().toString());
    }

    @Test
    @DisplayName("Una cifra ausente del documento: una marca ALTO por sección, con la cifra y el estilo")
    void cifraAusente() throws IOException {
        CuestionarioDTO dto = conBandas(banda("A", 3, 5, 1), banda("A", 6, 9, 2));

        ResultadoImportacion r = nelse(dto, origenes("bandas", "DOCUMENTO"), fuente(pagina("Nivel bajo de 3 a 5.")));

        Marca m = unica(marcas(r, Marca.ALTO, "bandas"));
        assertTrue(m.mensaje().endsWith(": 6."), m.mensaje());
        assertEquals(List.of("estilo:A"), m.refs());
        assertFalse(m.comoVerificar().isBlank());
    }

    @Test
    @DisplayName("Los extremos de cada grupo de bandas no se exigen: 'hasta 6' o '9 o más' no imprimen el tope")
    void extremosExentos() throws IOException {
        CuestionarioDTO dto = conBandas(banda("A", 3, 5, 1), banda("A", 6, 9, 2));

        ResultadoImportacion r = nelse(dto, origenes("bandas", "DOCUMENTO"), fuente(pagina("Bajo hasta 5; alto desde 6.")));

        assertTrue(marcas(r, Marca.ALTO, "bandas").isEmpty(), r.marcas().toString());
    }

    @Test
    @DisplayName("Las cifras se buscan como número completo: 14 no está en 2014 ni 3,5 en 13,5")
    void limitesDeNumero() throws IOException {
        CuestionarioDTO dto = conBandas(banda("A", 3, 5, 1), banda("A", 6, 14, 2), banda("A", 15, 20, 3));

        Marca dentroDeOtro = unica(marcas(nelse(dto, origenes("bandas", "DOCUMENTO"),
                fuente(pagina("Cortes 5, 6, 2014 y 15 (edición 2014)."))), Marca.ALTO, "bandas"));
        assertTrue(dentroDeOtro.mensaje().endsWith(": 14."), dentroDeOtro.mensaje());

        ResultadoImportacion presente = nelse(dto, origenes("bandas", "DOCUMENTO"),
                fuente(pagina("Cortes 5, 6, 14 y 15.")));
        assertTrue(marcas(presente, Marca.ALTO, "bandas").isEmpty(), presente.marcas().toString());

        CuestionarioDTO decimal = conBandas(banda("A", 3, 3.5, 1), banda("A", 4, 9, 2));
        Marca enOtroDecimal = unica(marcas(nelse(decimal, origenes("bandas", "DOCUMENTO"),
                fuente(pagina("Puntos de corte 13,5 y 4."))), Marca.ALTO, "bandas"));
        assertTrue(enOtroDecimal.mensaje().endsWith(": 3.5."), enOtroDecimal.mensaje());
    }

    @Test
    @DisplayName("Coma o punto decimal y '4.0' o '4' son la misma cifra")
    void normalizaNumeros() throws IOException {
        CuestionarioDTO dto = conBandas(banda("A", 3, 3.5, 1), banda("A", 4, 9, 2));

        assertTrue(marcas(nelse(dto, origenes("bandas", "DOCUMENTO"), fuente(pagina("Cortes 3,5 y 4."))),
                Marca.ALTO, "bandas").isEmpty());
        assertTrue(marcas(nelse(dto, origenes("bandas", "DOCUMENTO"), fuente(pagina("Cortes 3.50 y 4.0."))),
                Marca.ALTO, "bandas").isEmpty());
    }

    @Test
    @DisplayName("El número del marcador de página no cuenta como cifra del documento")
    void marcadorDePagina() throws IOException {
        CuestionarioDTO dto = conBandas(banda("A", 3, 5, 1), banda("A", 6, 9, 2));
        // Sin texto con un 6, el "--- página 6 ---" del sexto folio no la respalda.
        String documento = LimpiadorTexto.unir(List.of("", "", "", "", "", "Nivel bajo 3 a 5"));

        Marca m = unica(marcas(nelse(dto, origenes("bandas", "DOCUMENTO"), fuente(documento)), Marca.ALTO, "bandas"));

        assertTrue(m.mensaje().endsWith(": 6."), m.mensaje());
    }

    @Test
    @DisplayName("Una cifra que está en las instrucciones de la persona también vale, aunque el documento no la traiga")
    void cifraEnLasInstrucciones() throws IOException {
        CuestionarioDTO dto = conBandas(banda("A", 3, 5, 1), banda("A", 6, 9, 2));

        ResultadoImportacion conProfesor = nelse(dto, origenes("bandas", "DOCUMENTO"),
                new FuenteNelse(pagina("Nivel bajo de 3 a 5."), "El nivel alto empieza en 6.", 0));
        assertTrue(marcas(conProfesor, Marca.ALTO, "bandas").isEmpty(), conProfesor.marcas().toString());

        // Solo instrucciones, sin documento.
        ResultadoImportacion soloProfesor = nelse(dto, origenes("bandas", "DOCUMENTO"),
                new FuenteNelse(null, "Bajo: 3 a 5. Alto: 6 a 9.", 0));
        assertTrue(marcas(soloProfesor, Marca.ALTO, "bandas").isEmpty(), soloProfesor.marcas().toString());
    }

    @Test
    @DisplayName("Sin documento ni instrucciones con cifras, todas las no extremas faltan")
    void sinNingunaFuente() throws IOException {
        CuestionarioDTO dto = conBandas(banda("A", 3, 5, 1), banda("A", 6, 9, 2));

        Marca m = unica(marcas(nelse(dto, origenes("bandas", "DOCUMENTO"), new FuenteNelse(null, null, 0)),
                Marca.ALTO, "bandas"));

        assertTrue(m.mensaje().endsWith(": 5, 6."), m.mensaje());
    }

    @Test
    @DisplayName("Los escalones comprueban totalMin, totalMax y distancia, salvo el primer mínimo y el último máximo")
    void escalonesContraElDocumento() throws IOException {
        CuestionarioDTO dto = conEscalones(new EscalonDTO(1d, 6d, 1d), new EscalonDTO(7d, 18d, 2d));
        String origen = origenes("escalones", "DOCUMENTO");

        assertTrue(marcas(nelse(dto, origen, fuente(pagina("Total 1-6: distancia 1; total 7-18: distancia 2."))),
                Marca.ALTO, "escalones").isEmpty());

        Marca m = unica(marcas(nelse(dto, origen, fuente(pagina("Total hasta 6: distancia 1; el resto distancia 2."))),
                Marca.ALTO, "escalones"));
        assertTrue(m.mensaje().endsWith(": 7."), m.mensaje());
        assertTrue(m.refs().isEmpty());
    }

    @Test
    @DisplayName("El plano comprueba los cortes distintos de null y de 0 (la convención de equilibrio)")
    void planoContraElDocumento() throws IOException {
        String origen = origenes("plano", "DOCUMENTO");

        Marca m = unica(marcas(nelse(conPlano(4.5, 0d), origen, fuente(pagina("Corte del eje: 5."))), Marca.ALTO, "plano"));
        assertTrue(m.mensaje().endsWith(": 4.5."), m.mensaje());

        assertTrue(marcas(nelse(conPlano(4.5, 0d), origen, fuente(pagina("Corte del eje: 4,5."))), Marca.ALTO, "plano")
                .isEmpty());
        assertTrue(marcas(nelse(conPlano(0d, null), origen, fuente(pagina("Sin cortes."))), Marca.ALTO, "plano").isEmpty());
    }

    @Test
    @DisplayName("Con muchas cifras ausentes el mensaje las resume: 'y N más'")
    void listaAcotada() throws IOException {
        List<BandaDTO> bandas = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            bandas.add(banda("A", 100 + 10 * i, 105 + 10 * i, i + 1));
        }
        CuestionarioDTO dto = conBandas(bandas.toArray(new BandaDTO[0]));

        Marca m = unica(marcas(nelse(dto, origenes("bandas", "DOCUMENTO"), fuente(pagina("Nada que ver."))),
                Marca.ALTO, "bandas"));

        // 24 cifras, menos los dos extremos del grupo.
        assertTrue(m.mensaje().contains("y 14 más"), m.mensaje());
        assertTrue(m.mensaje().length() < 300, m.mensaje());
    }

    @Test
    @DisplayName("Solo se comprueba lo que el origen dice que es del documento (o de las instrucciones)")
    void otrosOrigenesNoSeComprueban() throws IOException {
        CuestionarioDTO dto = conBandas(banda("A", 3, 5, 1), banda("A", 6, 9, 2));
        String analisis = """
                {"origen": {"bandas": "CONVENCION", "clave": "DOCUMENTO", "compuestos": "DOCUMENTO",
                            "items": "DOCUMENTO", "escalones": "VACIO"}}""";

        ResultadoImportacion r = nelse(dto, analisis, fuente(pagina("Texto sin cifras.")));

        // Los pesos 1 a 3 de la clave son estructura, no cifras de baremo.
        assertTrue(marcas(r, Marca.ALTO, null).isEmpty(), r.marcas().toString());
    }

    @Test
    @DisplayName("Origen PROFESOR: la cifra debe estar en las instrucciones, no basta que esté en el documento")
    void origenProfesor() throws IOException {
        CuestionarioDTO dto = conBandas(banda("A", 3, 5, 1), banda("A", 6, 9, 2));
        String origen = origenes("bandas", "PROFESOR");

        Marca m = unica(marcas(nelse(dto, origen, new FuenteNelse(pagina("Cortes 5 y 6."), "Usa niveles bajo y alto.", 0)),
                Marca.ALTO, "bandas"));
        assertTrue(m.mensaje().contains("instrucciones"), m.mensaje());

        assertTrue(marcas(nelse(dto, origen, new FuenteNelse(null, "Bajo hasta 5, alto desde 6.", 0)), Marca.ALTO,
                "bandas").isEmpty());
    }

    @Test
    @DisplayName("En modo CREACION las cifras también se comprueban (contra las instrucciones)")
    void creacionComprobaCifras() throws IOException {
        CuestionarioDTO dto = conBandas(banda("A", 3, 5, 1), banda("A", 6, 9, 2));
        String analisis = """
                {"modo": "CREACION", "origen": {"bandas": "PROFESOR"}}""";

        ResultadoImportacion r = nelse(dto, analisis, new FuenteNelse(null, "Quiero un cuestionario de dos escalas.", 0));

        assertEquals(1, marcas(r, Marca.ALTO, "bandas").size(), r.marcas().toString());
    }

    @Test
    @DisplayName("Con una lectura del asistente las bandas de la IA se vacían y no queda nada que comprobar")
    void convencionConArreglosLlenos() throws IOException {
        CuestionarioDTO dto = conBandas(banda("A", 3, 5, 1), banda("A", 6, 9, 2));
        String analisis = """
                {"lectura": {"lecturaAsistente": "NIVELES"}, "origen": {"bandas": "DOCUMENTO"}}""";

        ResultadoImportacion r = nelse(dto, analisis, fuente(pagina("Texto sin cifras.")));

        assertTrue(r.cuestionario().getInterpretacion().bandas().isEmpty());
        assertEquals(1, marcas(r, Marca.BAJO, "bandas").size(), r.marcas().toString());
        assertTrue(marcas(r, Marca.ALTO, "bandas").isEmpty(), r.marcas().toString());
    }

    @Test
    @DisplayName("Las marcas no copian fragmentos del documento ni de las instrucciones")
    void noRepiteElTexto() throws IOException {
        CuestionarioDTO dto = conBandas(banda("A", 3, 5, 1), banda("A", 6, 9, 2));
        dto.getPreguntas().get(0).setPregunta("Enunciado que no está");
        String secreto = "QQ-FRASE-DEL-DOCUMENTO-QQ";

        ResultadoImportacion r = nelse(dto, origenes("bandas", "DOCUMENTO"),
                new FuenteNelse(pagina("1. " + secreto + "\nCorte 5."), secreto + " instrucciones", 0));

        assertFalse(r.marcas().isEmpty());
        assertTrue(r.marcas().stream().noneMatch(
                m -> (m.mensaje() + m.comoVerificar() + m.refs()).contains(secreto)), r.marcas().toString());
    }

    @Test
    @DisplayName("La importación no cambia: sin fuente no hay marcas de procedencia aunque el origen diga DOCUMENTO")
    void importacionSinCambios() throws IOException {
        CuestionarioDTO dto = conBandas(banda("A", 3, 5, 1), banda("A", 6, 9, 2));
        dto.getPreguntas().get(0).setPregunta("Enunciado cualquiera");
        JsonNode analisis = OM.readTree("""
                {"modo": "TRANSCRIPCION", "origen": {"bandas": "DOCUMENTO"},
                 "reconocido": {"catalogo": "ZETA", "estructuraIgual": true}}""");
        LectorJsonTolerante.Entrada entrada = new LectorJsonTolerante.Entrada(OM.valueToTree(dto), analisis, null);

        ResultadoImportacion sola = validador.validar(entrada);
        ResultadoImportacion nula = validador.validar(entrada, null);

        assertEquals(sola.marcas(), nula.marcas());
        assertTrue(sola.marcas().isEmpty() || marcas(sola, Marca.ALTO, null).isEmpty(), sola.marcas().toString());
        assertTrue(marcas(sola, Marca.MEDIO, "meta").isEmpty(), sola.marcas().toString());
        assertTrue(marcas(sola, Marca.MEDIO, "items").isEmpty(), sola.marcas().toString());
    }

    // ---------------------------------------------------------------- Catálogo

    /** Catálogo ficticio: seis ítems, bandas, escalones y plano. */
    private static CuestionarioDTO instrumentoAlfa() {
        CuestionarioDTO dto = base();
        dto.setInterpretacion(new InterpretacionDTO(EsquemaInterpretacion.BAREMO, 10d, null,
                List.of(banda("A", 3, 5, 1), banda("A", 6, 9, 2), banda("B", 3, 9, 1)),
                List.of(new EscalonDTO(1d, 6d, 1d), new EscalonDTO(7d, 18d, 2d)), plano(4.5d, 5.5d), null, null));
        return dto;
    }

    private ValidadorGeneracion conCatalogo(Path dir) throws IOException {
        Files.writeString(dir.resolve("alfa_cuestionario.json"), OM.writeValueAsString(instrumentoAlfa()));
        Files.writeString(dir.resolve("fichas.md"), "## ALFA\n- Ficha.\n");
        return new ValidadorGeneracion(OM, new PromptIa(OM, dir.toString(), false));
    }

    private static String reconoce(String catalogo, boolean estructuraIgual, String origen) {
        return "{\"reconocido\": {\"catalogo\": \"" + catalogo + "\", \"estructuraIgual\": " + estructuraIgual
                + "}, \"origen\": " + origen + "}";
    }

    @Test
    @DisplayName("Un instrumento reconocido que no está cargado: MEDIO, sin repetir lo que escribió el modelo")
    void catalogoDesconocido() throws IOException {
        ResultadoImportacion r = nelse(base(), reconoce("ZETA", true, "{}"), fuente(documentoDe(base())));

        Marca m = unica(marcas(r, Marca.MEDIO, "meta"));
        assertTrue(m.mensaje().contains("no está cargado"), m.mensaje());
        assertFalse(m.mensaje().contains("ZETA"), m.mensaje());
    }

    @Test
    @DisplayName("NINGUNO, vacío o sin 'reconocido' no se compara con nada")
    void sinReconocimiento(@TempDir Path dir) throws IOException {
        ValidadorGeneracion v = conCatalogo(dir);

        for (String analisis : new String[] { reconoce("NINGUNO", true, "{}"), reconoce("", true, "{}"), "{}", null }) {
            ResultadoImportacion r = nelse(v, base(), analisis, fuente(documentoDe(base())));
            assertTrue(marcas(r, null, "meta").isEmpty(), r.marcas().toString());
        }
    }

    @Test
    @DisplayName("estructuraIgual verdadero con otro número de ítems: ALTO con las dos cantidades")
    void estructuraIgualConOtroNumeroDeItems(@TempDir Path dir) throws IOException {
        CuestionarioDTO dto = base();
        dto.setPreguntas(List.copyOf(dto.getPreguntas().subList(0, 5)));

        ResultadoImportacion r = nelse(conCatalogo(dir), dto, reconoce("alfa", true, "{}"), fuente(documentoDe(dto)));

        Marca m = unica(marcas(r, Marca.ALTO, "meta"));
        assertTrue(m.mensaje().contains("número de ítems (5 frente a 6)"), m.mensaje());
    }

    @Test
    @DisplayName("estructuraIgual verdadero con otras opciones por ítem, otro formato u otras escalas: ALTO")
    void estructuraIgualConOtrasOpcionesFormatoOEscalas(@TempDir Path dir) throws IOException {
        ValidadorGeneracion v = conCatalogo(dir);

        CuestionarioDTO opciones = base();
        opciones.getPreguntas().get(2).setOpciones(opciones.getPreguntas().get(2).getOpciones().subList(0, 2));
        Marca m1 = unica(marcas(nelse(v, opciones, reconoce("ALFA", true, "{}"), fuente(documentoDe(opciones))),
                Marca.ALTO, "meta"));
        assertTrue(m1.mensaje().contains("las opciones por ítem (ítems 3)"), m1.mensaje());

        CuestionarioDTO formato = base();
        formato.getPreguntas().get(4).setFormato(FormatoItem.MULTIPLE);
        Marca m2 = unica(marcas(nelse(v, formato, reconoce("ALFA", true, "{}"), fuente(documentoDe(formato))),
                Marca.ALTO, "meta"));
        assertTrue(m2.mensaje().contains("el formato de respuesta (ítems 5)"), m2.mensaje());

        CuestionarioDTO escalas = base();
        escalas.getEstilos().get(1).setNombre("Otra");
        Marca m3 = unica(marcas(nelse(v, escalas, reconoce("ALFA", true, "{}"), fuente(documentoDe(escalas))),
                Marca.ALTO, "meta"));
        assertTrue(m3.mensaje().contains("las escalas"), m3.mensaje());
    }

    @Test
    @DisplayName("Si la estructura es idéntica, o la IA no afirma que lo sea, no hay marca de estructura")
    void estructuraIdenticaOSinAfirmar(@TempDir Path dir) throws IOException {
        ValidadorGeneracion v = conCatalogo(dir);
        CuestionarioDTO corto = base();
        corto.setPreguntas(List.copyOf(corto.getPreguntas().subList(0, 5)));

        assertTrue(marcas(nelse(v, base(), reconoce("ALFA", true, "{}"), fuente(documentoDe(base()))), null, "meta")
                .isEmpty());
        assertTrue(marcas(nelse(v, corto, reconoce("ALFA", false, "{}"), fuente(documentoDe(corto))), null, "meta")
                .isEmpty());
    }

    @Test
    @DisplayName("Bandas, escalones y plano copiados idénticos del catálogo no generan marca ALTO")
    void seccionesIdenticas(@TempDir Path dir) throws IOException {
        String origen = "{\"bandas\": \"CATALOGO\", \"escalones\": \"CATALOGO\", \"plano\": \"CATALOGO\"}";

        ResultadoImportacion r = nelse(conCatalogo(dir), instrumentoAlfa(), reconoce("ALFA", true, origen),
                fuente(documentoDe(base())));

        assertTrue(marcas(r, Marca.ALTO, null).isEmpty(), r.marcas().toString());
    }

    @Test
    @DisplayName("Una banda cambiada respecto del catálogo: ALTO en bandas con cuántas difieren")
    void bandaDistinta(@TempDir Path dir) throws IOException {
        CuestionarioDTO dto = instrumentoAlfa();
        dto.setInterpretacion(copia(dto).getInterpretacion());
        List<BandaDTO> bandas = new ArrayList<>(dto.getInterpretacion().bandas());
        bandas.set(1, banda("A", 7, 9, 2));
        dto.setInterpretacion(new InterpretacionDTO(EsquemaInterpretacion.BAREMO, 10d, null, bandas,
                dto.getInterpretacion().escalones(), dto.getInterpretacion().plano(), null, null));

        ResultadoImportacion r = nelse(conCatalogo(dir), dto, reconoce("ALFA", true, "{\"bandas\": \"CATALOGO\"}"),
                fuente(documentoDe(base())));

        Marca m = unica(marcas(r, Marca.ALTO, "bandas"));
        assertTrue(m.mensaje().contains("1 distintas o de más y 1 que faltan"), m.mensaje());
    }

    @Test
    @DisplayName("Escalones y plano distintos del catálogo marcan ALTO; sin origen CATALOGO no se comparan")
    void escalonesYPlanoDistintos(@TempDir Path dir) throws IOException {
        ValidadorGeneracion v = conCatalogo(dir);
        CuestionarioDTO dto = base();
        dto.setInterpretacion(new InterpretacionDTO(EsquemaInterpretacion.BAREMO, 10d, null, List.of(),
                List.of(new EscalonDTO(1d, 6d, 1d), new EscalonDTO(7d, 18d, 3d)), plano(4.5d, 6d), null, null));

        ResultadoImportacion r = nelse(v, dto, reconoce("ALFA", true, "{\"escalones\": \"CATALOGO\", \"plano\": \"CATALOGO\"}"),
                fuente(documentoDe(dto)));
        assertEquals(1, marcas(r, Marca.ALTO, "escalones").size(), r.marcas().toString());
        assertEquals(1, marcas(r, Marca.ALTO, "plano").size(), r.marcas().toString());

        ResultadoImportacion otroOrigen = nelse(v, dto,
                reconoce("ALFA", true, "{\"escalones\": \"INFERIDO\", \"plano\": \"PROFESOR\"}"), fuente(documentoDe(dto)));
        assertTrue(marcas(otroOrigen, Marca.ALTO, "escalones").stream().noneMatch(m -> m.mensaje().contains("catálogo")));
        assertTrue(marcas(otroOrigen, Marca.ALTO, "plano").stream().noneMatch(m -> m.mensaje().contains("catálogo")));
    }

    @Test
    @DisplayName("La clave copiada del catálogo se compara por estilo: idéntica pasa, un peso cambiado marca el ítem")
    void claveContraElCatalogo(@TempDir Path dir) throws IOException {
        ValidadorGeneracion v = conCatalogo(dir);
        String analisis = reconoce("ALFA", true, "{\"clave\": \"CATALOGO\"}");

        assertTrue(marcas(nelse(v, base(), analisis, fuente(documentoDe(base()))), Marca.ALTO, "clave").isEmpty());

        CuestionarioDTO dto = base();
        dto.getPreguntas().get(2).getOpciones().get(0).setPesos(List.of(new PesoDTO(1, 3d)));
        dto.getPreguntas().get(4).getOpciones().get(1).setPesos(List.of());
        Marca m = unica(marcas(nelse(v, dto, analisis, fuente(documentoDe(dto))), Marca.ALTO, "clave"));
        assertEquals(List.of("pregunta:3", "pregunta:5"), m.refs());
        assertTrue(m.mensaje().contains("2 de 6"), m.mensaje());
    }

    @Test
    @DisplayName("Una clave 'del catálogo' con otro número de ítems no aplica: ALTO")
    void claveConOtroNumeroDeItems(@TempDir Path dir) throws IOException {
        CuestionarioDTO dto = base();
        dto.setPreguntas(List.copyOf(dto.getPreguntas().subList(0, 4)));

        ResultadoImportacion r = nelse(conCatalogo(dir), dto, reconoce("ALFA", false, "{\"clave\": \"CATALOGO\"}"),
                fuente(documentoDe(dto)));

        Marca m = unica(marcas(r, Marca.ALTO, "clave"));
        assertTrue(m.mensaje().contains("4 ítems") && m.mensaje().contains("6"), m.mensaje());
    }

    // ---------------------------------------------------------------- Fidelidad

    @Test
    @DisplayName("Enunciados y opciones iguales a los del documento no generan marcas de items")
    void fidelidadCorrecta() throws IOException {
        ResultadoImportacion r = nelse(base(), null, fuente(documentoDe(base())));

        assertTrue(marcas(r, null, "items").isEmpty(), r.marcas().toString());
    }

    @Test
    @DisplayName("Mayúsculas, puntuación, acentos descompuestos y numeración inicial no cuentan como diferencia")
    void fidelidadNormalizada() throws IOException {
        String impreso = Normalizer.normalize(documentoDe(base()).toUpperCase().replace(". ", ": "), Normalizer.Form.NFD);
        CuestionarioDTO dto = base();
        dto.getPreguntas().get(0).setPregunta("12. Afirmación 1");
        dto.getPreguntas().get(1).setPregunta("b) Afirmación 2.");

        ResultadoImportacion r = nelse(dto, null, fuente(impreso));

        assertTrue(marcas(r, null, "items").isEmpty(), r.marcas().toString());
    }

    @Test
    @DisplayName("Tabla de PDF: la escala '1 2 3 4 5' entre las dos mitades de un enunciado partido no es una diferencia")
    void enunciadoPartidoPorLaEscala() throws IOException {
        CuestionarioDTO dto = base();
        dto.getPreguntas().get(0).setPregunta("Mientras estudio compruebo si estoy entendiendo lo que leo.");
        dto.getPreguntas().get(1).setPregunta("Repaso lo estudiado al terminar cada sesión de trabajo.");
        String impreso = pagina("""
                1. Mientras estudio compruebo si estoy entendiendo lo que       1  2  3  4  5
                leo.
                2. Repaso lo estudiado al terminar       1  2  3  4  5
                cada sesión de trabajo.
                3. Afirmación 3
                4. Afirmación 4
                5. Afirmación 5
                6. Afirmación 6
                Escala: Opción 1 | Opción 2 | Opción 3""");

        ResultadoImportacion r = nelse(dto, null, fuente(impreso));

        assertTrue(marcas(r, null, "items").isEmpty(), r.marcas().toString());

        // Un enunciado realmente distinto sigue marcándose aunque la escala esté en medio.
        dto.getPreguntas().get(1).setPregunta("Repaso lo estudiado antes de dormir cada noche.");
        Marca m = unica(marcas(nelse(dto, null, fuente(impreso)), Marca.MEDIO, "items"));
        assertEquals(List.of("pregunta:2"), m.refs());
    }

    @Test
    @DisplayName("Un enunciado que no está en el documento: MEDIO en items con su pregunta")
    void enunciadoAusente() throws IOException {
        String impreso = documentoDe(base());
        CuestionarioDTO dto = base();
        dto.getPreguntas().get(2).setPregunta("Un enunciado que nadie escribió");

        Marca m = unica(marcas(nelse(dto, null, fuente(impreso)), Marca.MEDIO, "items"));

        assertEquals(List.of("pregunta:3"), m.refs());
        assertTrue(m.mensaje().contains("1 pregunta"), m.mensaje());
    }

    @Test
    @DisplayName("Con muchos enunciados ausentes las referencias se acotan a 10 y el mensaje resume el resto")
    void referenciasAcotadas() throws IOException {
        CuestionarioDTO dto = base(30);
        String impreso = documentoDe(dto);
        for (int i = 0; i < 12; i++) {
            dto.getPreguntas().get(i).setPregunta("Reescrito " + i);
        }

        Marca m = unica(marcas(nelse(dto, null, fuente(impreso)), Marca.MEDIO, "items"));

        assertEquals(10, m.refs().size());
        assertEquals("pregunta:1", m.refs().get(0));
        assertTrue(m.mensaje().contains("12 preguntas") && m.mensaje().contains("y 4 más"), m.mensaje());
    }

    @Test
    @DisplayName("Traducción: si más de la mitad no coincide, una sola marca general y no una por ítem")
    void traduccion() throws IOException {
        CuestionarioDTO original = base();
        for (PreguntaDTO p : original.getPreguntas()) {
            p.setPregunta("Statement " + p.getOrden());
            p.getOpciones().forEach(o -> o.setRespuesta("Option " + o.getOrden()));
        }
        String impreso = documentoDe(original);

        List<Marca> items = marcas(nelse(base(), null, fuente(impreso)), Marca.MEDIO, "items");

        Marca m = unica(items);
        assertTrue(m.mensaje().contains("¿traducidos o reescritos?"), m.mensaje());
        assertTrue(m.refs().isEmpty());
    }

    @Test
    @DisplayName("Una opción ausente se marca una sola vez aunque se repita en todos los ítems")
    void opcionAusente() throws IOException {
        CuestionarioDTO dto = base();
        for (PreguntaDTO p : dto.getPreguntas()) {
            p.setOpciones(List.of(opcion(1, "Nunca", 1, 1), opcion(2, "A veces", 1, 2), opcion(3, "Casi siempre", 1, 3),
                    opcion(4, "Siempre", 1, 4)));
        }
        String impreso = documentoDe(dto);
        dto.getPreguntas().forEach(p -> p.getOpciones().get(2).setRespuesta("Frecuentemente"));

        Marca m = unica(marcas(nelse(dto, null, fuente(impreso)), Marca.MEDIO, "items"));

        assertTrue(m.mensaje().startsWith("1 texto de opción"), m.mensaje());
        assertEquals(List.of("pregunta:1"), m.refs());
    }

    @Test
    @DisplayName("Si casi ninguna opción coincide, una marca general de opciones")
    void opcionesTraducidas() throws IOException {
        CuestionarioDTO dto = base();
        String impreso = documentoDe(dto);
        dto.getPreguntas().forEach(p -> p.getOpciones().forEach(o -> o.setRespuesta("Choice " + o.getOrden())));

        Marca m = unica(marcas(nelse(dto, null, fuente(impreso)), Marca.MEDIO, "items"));

        assertTrue(m.mensaje().startsWith("Las opciones de respuesta no coinciden"), m.mensaje());
    }

    @Test
    @DisplayName("Con páginas escaneadas no se listan enunciados ausentes: una sola marca MEDIO 'leído desde imagen'")
    void paginasEscaneadas() throws IOException {
        String impreso = documentoDe(base());
        CuestionarioDTO dto = base();
        dto.getPreguntas().get(2).setPregunta("Este viene de una imagen");
        dto.getPreguntas().get(3).setPregunta("Este también");

        List<Marca> items = marcas(nelse(dto, null, new FuenteNelse(impreso, null, 2)), Marca.MEDIO, "items");

        Marca m = unica(items);
        assertTrue(m.mensaje().contains("2 páginas") && m.mensaje().contains("imagen"), m.mensaje());
        assertTrue(m.refs().isEmpty());
        assertTrue(m.comoVerificar().contains("original"));
    }

    @Test
    @DisplayName("Un documento solo de imágenes (sin texto) también deja la marca de imagen")
    void documentoSoloImagenes() throws IOException {
        Marca m = unica(marcas(nelse(base(), null, new FuenteNelse("", null, 1)), Marca.MEDIO, "items"));

        assertTrue(m.mensaje().contains("1 página") && m.mensaje().contains("imagen"), m.mensaje());
    }

    @Test
    @DisplayName("En CREACION, o sin texto del documento, no se comprueba la fidelidad")
    void sinFidelidad() throws IOException {
        CuestionarioDTO dto = base();
        dto.getPreguntas().get(0).setPregunta("Inventado");

        ResultadoImportacion creacion = nelse(dto, "{\"modo\": \"CREACION\"}",
                new FuenteNelse(pagina("Otro texto cualquiera"), "Haz un cuestionario", 0));
        assertTrue(marcas(creacion, null, "items").isEmpty(), creacion.marcas().toString());

        assertTrue(marcas(nelse(dto, null, new FuenteNelse(null, "Haz un cuestionario", 0)), null, "items").isEmpty());
        assertTrue(marcas(nelse(dto, null, new FuenteNelse("  \n ", null, 0)), null, "items").isEmpty());
    }

    // ---------------------------------------------------------------- Ítems numerados

    @Test
    @DisplayName("El documento numera más ítems de los que tiene el cuestionario: MEDIO con ambas cantidades")
    void numeradosDistintos() throws IOException {
        CuestionarioDTO documento = base(8);
        CuestionarioDTO dto = base(6);
        // Los seis enunciados están en el documento, que además trae el 7 y el 8.

        Marca m = unica(marcas(nelse(dto, null, fuente(documentoDe(documento))), Marca.MEDIO, "items"));

        assertTrue(m.mensaje().contains("numera 8 ítems") && m.mensaje().contains("tiene 6 preguntas"), m.mensaje());
        assertTrue(m.comoVerificar().contains("noSoportado"), m.comoVerificar());
    }

    @Test
    @DisplayName("Si la cantidad coincide, o la numeración es corta, no hay marca")
    void numeradosCoinciden() throws IOException {
        assertTrue(marcas(nelse(base(6), null, fuente(documentoDe(base(6)))), null, "items").isEmpty());

        // Cuatro ítems: una numeración menor de cinco no basta para opinar.
        CuestionarioDTO cuatro = base(4);
        CuestionarioDTO tres = base(3);
        assertTrue(marcas(nelse(tres, null, fuente(documentoDe(cuatro))), Marca.MEDIO, "items").isEmpty());
    }

    @Test
    @DisplayName("Una leyenda vertical de opciones numeradas no se toma por los ítems")
    void leyendaNumerada() throws IOException {
        CuestionarioDTO dto = base();
        dto.getPreguntas().forEach(p -> p.setOpciones(List.of(opcion(1, "Nunca", 1, 1), opcion(2, "Pocas veces", 1, 2),
                opcion(3, "A veces", 1, 3), opcion(4, "Muchas veces", 1, 4), opcion(5, "Siempre", 1, 5))));
        StringBuilder texto = new StringBuilder("Escala\n1. Nunca\n2. Pocas veces\n3. A veces\n4. Muchas veces\n5. Siempre\n");
        for (PreguntaDTO p : dto.getPreguntas()) {
            texto.append("• ").append(p.getPregunta()).append('\n');
        }

        ResultadoImportacion r = nelse(dto, null, fuente(pagina(texto.toString())));

        assertTrue(marcas(r, null, "items").isEmpty(), r.marcas().toString());
    }

    @Test
    @DisplayName("Si la numeración se reinicia por parte, vale la suma de las partes")
    void numeracionPorPartes() throws IOException {
        CuestionarioDTO dto = base(10);
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            texto.append(i % 5 + 1).append(". ").append(dto.getPreguntas().get(i).getPregunta()).append('\n');
        }
        texto.append("Escala: Opción 1 | Opción 2 | Opción 3");
        String impreso = pagina(texto.toString());

        assertTrue(marcas(nelse(dto, null, fuente(impreso)), null, "items").isEmpty());

        CuestionarioDTO otro = base(7);
        otro.getPreguntas().forEach(p -> p.setPregunta(dto.getPreguntas().get(p.getOrden() - 1).getPregunta()));
        Marca m = unica(marcas(nelse(otro, null, fuente(impreso)), Marca.MEDIO, "items"));
        assertTrue(m.mensaje().contains("5 ítems seguidos (10 si cada parte reinicia la numeración)"), m.mensaje());
    }

    @Test
    @DisplayName("Con páginas escaneadas no se cuenta la numeración del texto")
    void numeradosConImagenes() throws IOException {
        List<Marca> items = marcas(nelse(base(6), null, new FuenteNelse(documentoDe(base(8)), null, 1)), null, "items");

        assertEquals(1, items.size(), items.toString());
        assertTrue(items.get(0).mensaje().contains("imagen"), items.get(0).mensaje());
    }
}
