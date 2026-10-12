package com.adela.ia;

/**
 * Lo que Nelse le dio al modelo, contra lo cual L4 comprueba que no haya inventado.
 * Sin esto el validador solo puede revisar la forma; con esto puede revisar la
 * procedencia: que cada cifra y cada enunciado salgan de algo que la persona aportó.
 *
 * @param documento       texto limpio de las páginas del PDF que tienen texto
 *                        ({@code LimpiadorTexto.unir}); null o en blanco si la persona solo
 *                        dio instrucciones o todo el PDF eran imágenes. No incluye los
 *                        marcadores ni la nota de las páginas adjuntas como imagen: no es
 *                        contenido del documento y sus números no deben contar como cifras.
 * @param profesor        las instrucciones de la persona (fuente PROFESOR); puede ser null
 * @param paginasImagen   páginas que viajaron como imagen por estar escaneadas: su contenido no
 *                        está en {@code documento} y no se puede comprobar contra él
 * @param paginasOmitidas páginas escaneadas que NO llegaron al modelo (ni como texto ni como
 *                        imagen) por el tope de imágenes. Solo ocurre con el tope lleno, así que
 *                        {@code paginasImagen} es ese tope.
 */
public record FuenteNelse(String documento, String profesor, int paginasImagen, int paginasOmitidas) {

    /** Sin páginas omitidas: la fuente de casi todos los trabajos. */
    public FuenteNelse(String documento, String profesor, int paginasImagen) {
        this(documento, profesor, paginasImagen, 0);
    }

    boolean hayDocumento() {
        return documento != null && !documento.isBlank();
    }
}
