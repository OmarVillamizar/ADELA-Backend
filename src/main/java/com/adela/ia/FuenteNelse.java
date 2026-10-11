package com.adela.ia;

/**
 * Lo que Nelse le dio al modelo, contra lo cual L4 comprueba que no haya inventado.
 * Sin esto el validador solo puede revisar la forma; con esto puede revisar la
 * procedencia: que cada cifra y cada enunciado salgan de algo que la persona aportó.
 *
 * @param documento     texto limpio del PDF, el mismo que se mandó al modelo
 *                      ({@code LimpiadorTexto.unir}); null o en blanco si la persona solo
 *                      dio instrucciones o el PDF no tenía texto
 * @param profesor      las instrucciones de la persona (fuente PROFESOR); puede ser null
 * @param paginasImagen páginas que viajaron como imagen por estar escaneadas: su contenido no
 *                      está en {@code documento} y no se puede comprobar contra él
 */
public record FuenteNelse(String documento, String profesor, int paginasImagen) {

    boolean hayDocumento() {
        return documento != null && !documento.isBlank();
    }
}
