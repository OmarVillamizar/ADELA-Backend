package com.adela.ia;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.ia.DocumentoExtraido.PaginaExtraida;

/**
 * Lee el PDF que sube el administrador: texto por página y, para las páginas que son
 * una foto, un JPEG en grises que se le manda al modelo (que tiene visión).
 *
 * Todo lo que no sirve (vacío, grande, otro formato, protegido, ilegible) sale como
 * {@code DOCUMENTO_INVALIDO} con un mensaje para la persona. Los mensajes y los logs
 * nunca llevan contenido del documento: son su material, puede ser confidencial.
 */
@Component
public class ExtractorDocumento {

    private static final Logger logger = LoggerFactory.getLogger(ExtractorDocumento.class);

    /**
     * Una página con menos caracteres que esto (recortada y con los espacios colapsados) se
     * trata como escaneada. Una página de cuestionario con texto real trae cientos; lo que
     * queda por debajo es un número de página, un logo con leyenda o el ruido de un OCR
     * ajeno sobre una foto, y ahí lo que vale está en la imagen.
     */
    static final int MIN_CARACTERES_TEXTO = 50;

    /** 144 DPI = 2x el espacio de 72 pt del PDF: legible para el modelo sin disparar los tokens. */
    private static final float DPI_IMAGEN = 144f;

    /**
     * Techo de píxeles por página (una A4 a 144 DPI tiene ~3,9 M). Sin él, un PDF con una
     * página declarada de varios metros pide gigas de memoria al renderizar: se baja el DPI.
     */
    private static final double MAX_PIXELES = 16_000_000d;

    /**
     * JPEG y no PNG: en el spike dio los mismos tokens y la misma lectura, y un cuarto del peso en
     * escaneos con ruido (~0,3 MB frente a ~1,2 MB por página; con 12 páginas el cuerpo de la
     * petición pasa de ~20 MB a ~5 MB).
     */
    private static final float CALIDAD_JPEG = 0.8f;

    private static final long MB = 1024L * 1024L;

    private static final byte[] CABECERA = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    /** La especificación admite basura antes de la cabecera, pero solo dentro del primer KB. */
    private static final int VENTANA_CABECERA = 1024;

    private final long maxBytes;
    private final int maxPaginas;
    private final int maxImagenes;

    @Autowired
    public ExtractorDocumento(@Value("${adela.ia.max-mb}") int maxMb,
            @Value("${adela.ia.max-paginas}") int maxPaginas,
            @Value("${adela.ia.max-imagenes}") int maxImagenes) {
        this(maxMb * MB, maxPaginas, maxImagenes);
    }

    private ExtractorDocumento(long maxBytes, int maxPaginas, int maxImagenes) {
        this.maxBytes = maxBytes;
        this.maxPaginas = maxPaginas;
        this.maxImagenes = maxImagenes;
    }

    /**
     * Con el tope de tamaño en bytes, para probar el límite sin fabricar megas. Es una
     * fábrica y no un segundo constructor: {@code (int, int, int)} y {@code (long, int, int)}
     * se confunden en silencio al llamar con literales.
     */
    static ExtractorDocumento conTopeEnBytes(long maxBytes, int maxPaginas, int maxImagenes) {
        return new ExtractorDocumento(maxBytes, maxPaginas, maxImagenes);
    }

    public DocumentoExtraido extraer(byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            throw invalido("El archivo está vacío.");
        }
        if (pdf.length > maxBytes) {
            throw invalido("El archivo supera el tamaño máximo permitido de " + ((maxBytes + MB - 1) / MB) + " MB.");
        }
        if (!tieneCabeceraPdf(pdf)) {
            throw invalido("El archivo no es un PDF.");
        }

        // Cache de flujos en memoria (el valor por defecto de Loader): el PDF ya está en memoria
        // y acotado por max-mb; un cache en disco dejaría el documento en archivos temporales.
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            // Un PDF con solo clave de propietario abre con clave vacía y se lee sin problema,
            // aunque aparezca como "cifrado". Se rechaza solo lo que no se puede abrir (clave de
            // usuario, abajo) o lo que su dueño marcó como no copiable: no se elude esa marca.
            if (doc.isEncrypted() && !doc.getCurrentAccessPermission().canExtractContent()) {
                throw invalido("El PDF no permite copiar su contenido. Sube una versión sin esa restricción.");
            }
            int total = doc.getNumberOfPages();
            if (total == 0) {
                throw invalido("El PDF no tiene páginas.");
            }
            if (total > maxPaginas) {
                throw invalido("El PDF tiene " + total + " páginas y el máximo permitido es " + maxPaginas + ".");
            }
            return leer(doc, total);
        } catch (AppException e) {
            throw e;
        } catch (InvalidPasswordException e) {
            throw invalido("El PDF está protegido con contraseña. Sube una versión sin protección.");
        } catch (IOException | RuntimeException e) {
            // PDFBox lanza también excepciones no verificadas ante estructuras rotas. Solo la
            // clase: el mensaje de PDFBox puede citar fragmentos del archivo.
            logger.warn("No se pudo leer el PDF ({})", e.getClass().getSimpleName());
            throw invalido("No se pudo leer el PDF: está dañado o tiene un formato no soportado.");
        }
    }

    private DocumentoExtraido leer(PDDocument doc, int total) throws IOException {
        PDFTextStripper extractor = new PDFTextStripper();
        // Orden visual: sin él, las columnas y las tablas salen en el orden de dibujo del PDF,
        // que a veces mezcla un ítem con las opciones de otro.
        extractor.setSortByPosition(true);
        extractor.setLineSeparator("\n");
        PDFRenderer renderizador = new PDFRenderer(doc);

        List<PaginaExtraida> paginas = new ArrayList<>(total);
        int imagenes = 0;
        for (int numero = 1; numero <= total; numero++) {
            extractor.setStartPage(numero);
            extractor.setEndPage(numero);
            String texto = extractor.getText(doc);

            if (!esEscaneada(texto)) {
                paginas.add(new PaginaExtraida(numero, texto, false, null, false));
            } else if (imagenes < maxImagenes) {
                paginas.add(new PaginaExtraida(numero, texto, true, renderizarJpeg(doc, renderizador, numero - 1), false));
                imagenes++;
            } else {
                paginas.add(new PaginaExtraida(numero, texto, true, null, true));
            }
        }
        return new DocumentoExtraido(paginas);
    }

    static boolean esEscaneada(String texto) {
        return texto.strip().replaceAll("\\s+", " ").length() < MIN_CARACTERES_TEXTO;
    }

    private byte[] renderizarJpeg(PDDocument doc, PDFRenderer renderizador, int indice) throws IOException {
        PDRectangle caja = doc.getPage(indice).getCropBox();
        float dpi = DPI_IMAGEN;
        double pixeles = (caja.getWidth() * dpi / 72d) * (caja.getHeight() * dpi / 72d);
        if (pixeles > MAX_PIXELES) {
            dpi = (float) (dpi * Math.sqrt(MAX_PIXELES / pixeles));
        }
        BufferedImage imagen = renderizador.renderImageWithDPI(indice, dpi, ImageType.GRAY);
        return codificarJpeg(imagen);
    }

    /**
     * Calidad explícita (el valor por defecto del escritor es 0,75). El flujo es de memoria a
     * propósito: {@code ImageIO.write} y {@code createImageOutputStream} usan un archivo temporal
     * como caché de forma predeterminada, y la página es material del documento, no debe tocar disco.
     */
    private static byte[] codificarJpeg(BufferedImage imagen) throws IOException {
        Iterator<ImageWriter> escritores = ImageIO.getImageWritersByFormatName("jpeg");
        if (!escritores.hasNext()) {
            throw new IOException("No hay codificador JPEG");
        }
        ImageWriter escritor = escritores.next();
        ImageWriteParam parametros = escritor.getDefaultWriteParam();
        parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parametros.setCompressionQuality(CALIDAD_JPEG);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ImageOutputStream flujo = new MemoryCacheImageOutputStream(salida)) {
            escritor.setOutput(flujo);
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
        } finally {
            escritor.dispose();
        }
        return salida.toByteArray();
    }

    private static boolean tieneCabeceraPdf(byte[] datos) {
        int limite = Math.min(datos.length, VENTANA_CABECERA) - CABECERA.length;
        for (int i = 0; i <= limite; i++) {
            int j = 0;
            while (j < CABECERA.length && datos[i + j] == CABECERA[j]) {
                j++;
            }
            if (j == CABECERA.length) {
                return true;
            }
        }
        return false;
    }

    private static AppException invalido(String mensaje) {
        return new AppException(ErrorCode.DOCUMENTO_INVALIDO, mensaje);
    }
}
