package com.adela.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.ia.DocumentoExtraido.PaginaExtraida;

/**
 * Los PDF se fabrican en la prueba con PDFBox: sin archivos de ejemplo ni contenido
 * de instrumentos reales, solo texto ficticio.
 */
class ExtractorDocumentoTest {

    private static final String TEXTO_LARGO = "Pregunta ficticia numero %d: me gusta aprender con ejemplos concretos y practicos.";
    /** Inicio de todo JPEG: marcador SOI (FF D8) y el comienzo del siguiente marcador (FF). */
    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };

    /** Límites holgados; cada prueba usa el constructor de bytes para estrechar el que prueba. */
    private final ExtractorDocumento extractor = con(40, 12, 15L * 1024 * 1024);

    // ---------------------------------------------------------------- Ayudas

    private static ExtractorDocumento con(int maxPaginas, int maxImagenes, long maxBytes) {
        return ExtractorDocumento.conTopeEnBytes(maxBytes, maxPaginas, maxImagenes);
    }

    /** Una página por argumento: texto en Helvetica, o un rectángulo sin texto si es {@code null}. */
    private static byte[] pdf(String... paginas) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (String texto : paginas) {
                PDPage pagina = new PDPage(PDRectangle.A4);
                doc.addPage(pagina);
                try (PDPageContentStream cs = new PDPageContentStream(doc, pagina)) {
                    if (texto == null) {
                        cs.addRect(100, 400, 300, 150);
                        cs.fill();
                    } else {
                        cs.beginText();
                        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        cs.newLineAtOffset(50, 750);
                        for (String linea : texto.split("\n")) {
                            cs.showText(linea);
                            cs.newLineAtOffset(0, -16);
                        }
                        cs.endText();
                    }
                }
            }
            return guardar(doc);
        }
    }

    private static byte[] guardar(PDDocument doc) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        doc.save(salida);
        return salida.toByteArray();
    }

    private static void assertInvalido(Runnable accion) {
        AppException e = assertThrows(AppException.class, accion::run);
        assertEquals(ErrorCode.DOCUMENTO_INVALIDO, e.getCode());
        assertFalse(e.getMessage().isBlank());
    }

    // ---------------------------------------------------------------- Texto

    @Test
    @DisplayName("una página con texto se extrae sin imagen")
    void paginaConTexto() throws IOException {
        DocumentoExtraido doc = extractor.extraer(pdf(TEXTO_LARGO.formatted(1)));

        assertEquals(1, doc.paginas().size());
        PaginaExtraida p = doc.paginas().get(0);
        assertEquals(1, p.numero());
        assertTrue(p.texto().contains("Pregunta ficticia numero 1"));
        assertFalse(p.escaneada());
        assertNull(p.imagen());
        assertFalse(p.imagenOmitida());
    }

    @Test
    @DisplayName("las líneas llegan separadas por salto de línea y en orden")
    void lineasEnOrden() throws IOException {
        PaginaExtraida p = extractor.extraer(pdf("Primera linea del documento de prueba.\nSegunda linea del mismo documento ficticio.")).paginas().get(0);

        assertEquals("Primera linea del documento de prueba.\nSegunda linea del mismo documento ficticio.", p.texto().strip());
    }

    // ---------------------------------------------------------------- Escaneadas

    @Test
    @DisplayName("una página sin texto es escaneada y trae un JPEG en grises")
    void paginaEscaneada() throws IOException {
        PaginaExtraida p = extractor.extraer(pdf((String) null)).paginas().get(0);

        assertTrue(p.escaneada());
        assertFalse(p.imagenOmitida());
        assertNotNull(p.imagen());
        for (int i = 0; i < JPEG.length; i++) {
            assertEquals(JPEG[i], p.imagen()[i], "byte " + i + " de la firma JPEG");
        }
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(p.imagen()));
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, img.getType());
        // A4 = 595 x 842 pt; a 144 DPI (el doble de 72) son ~1190 x 1684 px.
        assertEquals(595 * 2, img.getWidth(), 4);
        assertEquals(842 * 2, img.getHeight(), 4);
    }

    @Test
    @DisplayName("el umbral: poco texto cuenta como escaneada, y conserva ese texto")
    void umbralDeTexto() throws IOException {
        PaginaExtraida corta = extractor.extraer(pdf("Figura 1")).paginas().get(0);
        assertTrue(corta.escaneada());
        assertTrue(corta.texto().contains("Figura 1"));
        assertNotNull(corta.imagen());

        assertTrue(ExtractorDocumento.esEscaneada("a".repeat(ExtractorDocumento.MIN_CARACTERES_TEXTO - 1)));
        assertFalse(ExtractorDocumento.esEscaneada("a".repeat(ExtractorDocumento.MIN_CARACTERES_TEXTO)));
        // Los espacios no cuentan por montón: 100 espacios y un salto son una página vacía.
        assertTrue(ExtractorDocumento.esEscaneada("hola" + " ".repeat(100) + "\n\n mundo"));
    }

    @Test
    @DisplayName("un documento mixto mantiene el orden y solo renderiza las escaneadas")
    void documentoMixto() throws IOException {
        DocumentoExtraido doc = extractor.extraer(pdf(TEXTO_LARGO.formatted(1), null, TEXTO_LARGO.formatted(3)));

        assertEquals(3, doc.paginas().size());
        assertFalse(doc.paginas().get(0).escaneada());
        assertTrue(doc.paginas().get(1).escaneada());
        assertNotNull(doc.paginas().get(1).imagen());
        assertFalse(doc.paginas().get(2).escaneada());
        assertNull(doc.paginas().get(2).imagen());
        assertEquals(2, doc.paginas().get(1).numero());
        assertTrue(doc.paginas().get(2).texto().contains("numero 3"));
    }

    @Test
    @DisplayName("pasado el tope de imágenes las escaneadas se marcan como omitidas, no se descartan")
    void topeDeImagenes() throws IOException {
        ExtractorDocumento conTope = con(40, 2, 15L * 1024 * 1024);

        DocumentoExtraido doc = conTope.extraer(pdf((String) null, (String) null, (String) null, TEXTO_LARGO.formatted(4)));

        assertEquals(4, doc.paginas().size());
        assertNotNull(doc.paginas().get(0).imagen());
        assertNotNull(doc.paginas().get(1).imagen());
        PaginaExtraida omitida = doc.paginas().get(2);
        assertTrue(omitida.escaneada());
        assertTrue(omitida.imagenOmitida());
        assertNull(omitida.imagen());
        assertFalse(doc.paginas().get(3).imagenOmitida());
    }

    @Test
    @DisplayName("una página declarada enorme se renderiza con menos DPI en vez de agotar la memoria")
    void paginaEnorme() throws IOException {
        byte[] enorme;
        try (PDDocument doc = new PDDocument()) {
            // 200 x 200 pulgadas: a 144 DPI serían 830 millones de píxeles.
            doc.addPage(new PDPage(new PDRectangle(14400, 14400)));
            enorme = guardar(doc);
        }

        PaginaExtraida p = extractor.extraer(enorme).paginas().get(0);

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(p.imagen()));
        assertTrue((long) img.getWidth() * img.getHeight() <= 16_100_000L);
    }

    // ---------------------------------------------------------------- Rechazos

    @Test
    @DisplayName("vacío o nulo → DOCUMENTO_INVALIDO")
    void vacio() {
        assertInvalido(() -> extractor.extraer(new byte[0]));
        assertInvalido(() -> extractor.extraer(null));
    }

    @Test
    @DisplayName("bytes que no son un PDF → DOCUMENTO_INVALIDO")
    void noEsPdf() {
        assertInvalido(() -> extractor.extraer("Esto es un texto plano, no un PDF.".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("la cabecera %PDF- se acepta dentro del primer KB, no más allá")
    void ventanaDeCabecera() throws IOException {
        byte[] valido = pdf(TEXTO_LARGO.formatted(1));

        // Basura corta antes de la cabecera: la especificación lo tolera.
        assertEquals(1, extractor.extraer(concatenar("basura\n".getBytes(StandardCharsets.US_ASCII), valido)).paginas().size());

        // Cabecera pasado el KB: no es un PDF.
        byte[] lejos = concatenar(new byte[2000], valido);
        assertInvalido(() -> extractor.extraer(lejos));
    }

    @Test
    @DisplayName("sobre el tamaño máximo → DOCUMENTO_INVALIDO")
    void demasiadoGrande() throws IOException {
        byte[] pdf = pdf(TEXTO_LARGO.formatted(1));
        ExtractorDocumento chico = con(40, 12, pdf.length - 1);
        ExtractorDocumento justo = con(40, 12, pdf.length);

        assertInvalido(() -> chico.extraer(pdf));
        assertEquals(1, justo.extraer(pdf).paginas().size());
    }

    @Test
    @DisplayName("más páginas que el máximo → DOCUMENTO_INVALIDO")
    void demasiadasPaginas() throws IOException {
        byte[] pdf = pdf(TEXTO_LARGO.formatted(1), TEXTO_LARGO.formatted(2), TEXTO_LARGO.formatted(3));

        assertInvalido(() -> con(2, 12, 15L * 1024 * 1024).extraer(pdf));
        assertEquals(3, con(3, 12, 15L * 1024 * 1024).extraer(pdf).paginas().size());
    }

    @Test
    @DisplayName("PDF sin páginas → DOCUMENTO_INVALIDO")
    void sinPaginas() throws IOException {
        byte[] pdf;
        try (PDDocument doc = new PDDocument()) {
            pdf = guardar(doc);
        }
        assertInvalido(() -> extractor.extraer(pdf));
    }

    @Test
    @DisplayName("cifrado con clave de usuario → DOCUMENTO_INVALIDO")
    void cifradoConClave() throws IOException {
        byte[] cifrado = cifrar("clave-de-usuario", new AccessPermission());

        assertInvalido(() -> extractor.extraer(cifrado));
    }

    @Test
    @DisplayName("solo clave de propietario: abre y se lee si permite copiar; se rechaza si lo prohíbe")
    void cifradoSoloPropietario() throws IOException {
        byte[] permiteCopiar = cifrar("", new AccessPermission());
        assertTrue(extractor.extraer(permiteCopiar).paginas().get(0).texto().contains("Pregunta ficticia"));

        AccessPermission sinCopia = new AccessPermission();
        sinCopia.setCanExtractContent(false);
        byte[] prohibeCopiar = cifrar("", sinCopia);
        assertInvalido(() -> extractor.extraer(prohibeCopiar));
    }

    @Test
    @DisplayName("empieza con %PDF- pero está dañado → DOCUMENTO_INVALIDO sin filtrar su contenido")
    void danado() {
        byte[] roto = ("%PDF-1.7\nsecreto-del-documento 1 0 obj << /Type /Catalog >> basura sin estructura").getBytes(StandardCharsets.US_ASCII);

        AppException e = assertThrows(AppException.class, () -> extractor.extraer(roto));
        assertEquals(ErrorCode.DOCUMENTO_INVALIDO, e.getCode());
        assertFalse(e.getMessage().contains("secreto-del-documento"));
    }

    // ---------------------------------------------------------------- Ayudas de cifrado

    private static byte[] cifrar(String claveUsuario, AccessPermission permisos) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            try (PDPageContentStream cs = new PDPageContentStream(doc, pagina)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 750);
                cs.showText(TEXTO_LARGO.formatted(1));
                cs.endText();
            }
            StandardProtectionPolicy politica = new StandardProtectionPolicy("clave-de-propietario", claveUsuario, permisos);
            politica.setEncryptionKeyLength(128);
            doc.protect(politica);
            return guardar(doc);
        }
    }

    private static byte[] concatenar(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
