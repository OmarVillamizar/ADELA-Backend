package com.adela.ia;

import java.util.List;

/**
 * Lo que sale de leer un PDF: una entrada por página, en orden y sin saltos.
 *
 * Se conserva por página (no un solo texto) porque quien consume decide distinto
 * según la página: el texto se limpia y se manda como DOCUMENTO, y las páginas
 * escaneadas viajan como imagen al modelo.
 */
public record DocumentoExtraido(List<PaginaExtraida> paginas) {

    public DocumentoExtraido {
        paginas = List.copyOf(paginas);
    }

    /**
     * @param numero        posición en el PDF, desde 1 (la misma que verá la persona)
     * @param texto         texto tal como lo extrajo PDFBox, sin limpiar; puede ser corto
     *                      o vacío si la página es escaneada
     * @param escaneada     tiene tan poco texto que el contenido está en una imagen
     * @param imagen        JPEG en grises de la página, solo si es escaneada y entró en el
     *                      tope de imágenes; si no, {@code null}. El nombre no dice el formato
     *                      a propósito: es el que decide {@link ExtractorDocumento}, y quien lo
     *                      manda al modelo ({@link ClienteLlm.MensajeLlm}) lo declara como JPEG.
     * @param imagenOmitida escaneada pero sin imagen por el tope de imágenes. Se marca en vez
     *                      de descartarla en silencio: ese contenido no llegará al modelo y
     *                      la persona debe saberlo.
     */
    public record PaginaExtraida(int numero, String texto, boolean escaneada, byte[] imagen,
            boolean imagenOmitida) {
    }
}
