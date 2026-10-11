package com.adela.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Texto ficticio. Cada regla lleva su caso de "no tocar": lo que más importa es que la
 * limpieza no se coma contenido ni cifras, porque contra este texto se verifican los números
 * que devuelve el modelo.
 */
class LimpiadorTextoTest {

    /** Limpia una sola página. */
    private static String una(String texto) {
        return LimpiadorTexto.limpiar(Collections.singletonList(texto)).get(0);
    }

    // ---------------------------------------------------------------- Encabezados y pies

    @Test
    @DisplayName("encabezado y pie repetidos: el contador de página se va, el título queda una vez")
    void encabezadoYPie() {
        List<String> paginas = List.of(
                "Universidad Ficticia - Cuestionario de prueba\nMe gusta aprender con ejemplos concretos.\nPágina 1 de 4",
                "Universidad Ficticia - Cuestionario de prueba\nPrefiero trabajar en equipo cada semana.\nPágina 2 de 4",
                "Universidad Ficticia - Cuestionario de prueba\nDisfruto leer antes de las clases.\nPágina 3 de 4",
                "Universidad Ficticia - Cuestionario de prueba\nPlanifico mis tareas con anticipación.\nPágina 4 de 4");

        List<String> limpias = LimpiadorTexto.limpiar(paginas);

        assertEquals("Universidad Ficticia - Cuestionario de prueba\nMe gusta aprender con ejemplos concretos.", limpias.get(0));
        assertEquals("Prefiero trabajar en equipo cada semana.", limpias.get(1));
        assertEquals("Disfruto leer antes de las clases.", limpias.get(2));
        assertEquals("Planifico mis tareas con anticipación.", limpias.get(3));
    }

    @Test
    @DisplayName("una línea idéntica con dígitos distintos por página (contador) se quita de todas")
    void contadorDeHoja() {
        List<String> limpias = LimpiadorTexto.limpiar(List.of(
                "Cuestionario ficticio hoja 1 de 3\nMe gusta aprender con ejemplos concretos.",
                "Cuestionario ficticio hoja 2 de 3\nPrefiero trabajar en equipo cada semana.",
                "Cuestionario ficticio hoja 3 de 3\nDisfruto leer antes de las clases."));

        assertEquals(List.of("Me gusta aprender con ejemplos concretos.", "Prefiero trabajar en equipo cada semana.",
                "Disfruto leer antes de las clases."), limpias);
    }

    @Test
    @DisplayName("con 2 páginas hace falta que la línea esté en las dos; con 5, en al menos 3")
    void umbralDePaginas() {
        List<String> dos = LimpiadorTexto.limpiar(List.of(
                "Encabezado repetido del instrumento\nPrimer contenido de la primera hoja.",
                "Otro encabezado distinto del anexo\nSegundo contenido de la segunda hoja."));
        assertTrue(dos.get(0).contains("Encabezado repetido") && dos.get(1).contains("Otro encabezado"));

        List<String> cincoConDos = LimpiadorTexto.limpiar(List.of(
                "Encabezado repetido del instrumento\nContenido uno de la hoja.",
                "Encabezado repetido del instrumento\nContenido dos de la hoja.",
                "Contenido tres de la hoja.", "Contenido cuatro de la hoja.", "Contenido cinco de la hoja."));
        assertTrue(cincoConDos.get(1).contains("Encabezado repetido"), "2 de 5 páginas no alcanza");

        List<String> cincoConTres = LimpiadorTexto.limpiar(List.of(
                "Encabezado repetido del instrumento\nContenido uno de la hoja.",
                "Encabezado repetido del instrumento\nContenido dos de la hoja.",
                "Encabezado repetido del instrumento\nContenido tres de la hoja.",
                "Contenido cuatro de la hoja.", "Contenido cinco de la hoja."));
        assertEquals("Contenido dos de la hoja.", cincoConTres.get(1));
        assertEquals("Contenido tres de la hoja.", cincoConTres.get(2));
    }

    @Test
    @DisplayName("no toca una opción que se repite en cada ítem aunque cierre cada página")
    void opcionRepetidaNoSeQuita() {
        String opciones = "\nNunca\nA veces\nTotalmente de acuerdo\n";
        List<String> paginas = List.of(
                "1. Me gusta planificar mis tareas con anticipación." + opciones + "2. Prefiero estudiar acompañado de otras personas." + opciones.stripTrailing(),
                "3. Disfruto leer antes de las clases de la mañana." + opciones + "4. Reviso mis apuntes al terminar cada semana." + opciones.stripTrailing(),
                "5. Explico lo aprendido a mis compañeros de curso." + opciones + "6. Busco ejemplos nuevos cuando algo no me queda claro." + opciones.stripTrailing());

        assertEquals(paginas, LimpiadorTexto.limpiar(paginas));
    }

    @Test
    @DisplayName("no toca líneas cortas repetidas al borde de cada página")
    void lineasCortasNoSeQuitan() {
        List<String> paginas = List.of(
                "Me gusta aprender con ejemplos concretos y reales.\nSiempre",
                "Prefiero trabajar en equipo cada semana del curso.\nSiempre",
                "Disfruto leer antes de las clases de la mañana.\nSiempre");

        assertEquals(paginas, LimpiadorTexto.limpiar(paginas));
    }

    @Test
    @DisplayName("la leyenda de la escala repetida arriba de cada hoja se conserva en la primera")
    void leyendaDeEscalaSeConservaUnaVez() {
        List<String> limpias = LimpiadorTexto.limpiar(List.of(
                "Responda de 1 (nunca) a 5 (siempre)\nMe gusta aprender con ejemplos concretos.",
                "Responda de 1 (nunca) a 5 (siempre)\nPrefiero trabajar en equipo cada semana.",
                "Responda de 1 (nunca) a 5 (siempre)\nDisfruto leer antes de las clases."));

        assertTrue(limpias.get(0).startsWith("Responda de 1 (nunca) a 5 (siempre)"));
        assertFalse(limpias.get(1).contains("Responda"));
    }

    // ---------------------------------------------------------------- Números de página

    @Test
    @DisplayName("quita el número suelto solo si es la primera o la última línea con texto")
    void numeroSueltoEnLosExtremos() {
        assertEquals("Me gusta aprender con ejemplos.", una("Me gusta aprender con ejemplos.\n3"));
        assertEquals("Me gusta aprender con ejemplos.", una("12\nMe gusta aprender con ejemplos."));
        assertEquals("Me gusta aprender con ejemplos.", una("Me gusta aprender con ejemplos.\n\n- 3 -\n"));
        assertEquals("Me gusta aprender con ejemplos.", una("Me gusta aprender con ejemplos.\n3/10"));
    }

    @Test
    @DisplayName("deja el número suelto del medio de la página (valor de escala, ítem partido)")
    void numeroSueltoEnElMedio() {
        String texto = "Me gusta aprender con ejemplos.\n5\nPrefiero trabajar en equipo.";
        assertEquals(texto, una(texto));
    }

    @Test
    @DisplayName("deja las escalas y los ítems numerados")
    void escalasEItems() {
        String texto = "Indique su acuerdo:\n1 2 3 4 5\n1. Me gusta aprender con ejemplos.\n2. Prefiero trabajar en equipo.\n"
                + "10. Planifico mis tareas con anticipación.";
        assertEquals(texto, una(texto));
    }

    @Test
    @DisplayName("quita 'Página N' y 'Page N of M' en cualquiera de las líneas de borde, pero no en el medio")
    void paginaExplicita() {
        assertEquals("Contenido uno de la hoja.\nContenido dos.", una("Contenido uno de la hoja.\nContenido dos.\nPage 3 of 10\n"));
        assertEquals("Contenido uno de la hoja.\nContenido dos.", una("Contenido uno de la hoja.\nPágina 3\nContenido dos."));
        assertEquals("Contenido uno de la hoja.\nContenido dos.", una("Contenido uno de la hoja.\nContenido dos.\nPág. 3 de 10"));

        String medio = "Uno de los ítems\nDos de los ítems\nTres de los ítems\nCuatro de los ítems\nPágina 3\nCinco de los ítems\nSeis de los ítems\nSiete de los ítems";
        assertEquals(medio, una(medio));
    }

    // ---------------------------------------------------------------- URL y copyright

    @Test
    @DisplayName("quita las URL; una línea que era solo una URL desaparece")
    void urls() {
        assertEquals("Más información en y en más.", una("Más información en https://ejemplo.org/ruta?x=1 y en www.ejemplo.org más."));
        assertEquals("Contenido uno de la hoja.\nContenido dos.", una("Contenido uno de la hoja.\n(https://ejemplo.org/a)\nContenido dos."));
        assertEquals("Contenido uno de la hoja.\nContenido dos.", una("Contenido uno de la hoja.\nwww.ejemplo.org\nContenido dos."));
    }

    @Test
    @DisplayName("quita las líneas de copyright pero no una opción '(c)'")
    void copyright() {
        String texto = "© 2024 Editorial Ficticia\n(c) 2023 Universidad Ficticia\nCopyright Editorial Ficticia\n"
                + "Todos los derechos reservados.\nAll rights reserved.\nContenido uno de la hoja.";
        assertEquals("Contenido uno de la hoja.", una(texto));

        String opciones = "(a) Nunca\n(b) A veces\n(c) Siempre\nEl copyright es un tema del curso.";
        assertEquals(opciones, una(opciones));
    }

    // ---------------------------------------------------------------- Palabras partidas

    @Test
    @DisplayName("une la palabra partida con guion a fin de línea")
    void guionDeFinDeLinea() {
        assertEquals("Me gusta la palabra completa en clase.", una("Me gusta la pala-\nbra completa en clase."));
        assertEquals("Una expresión fraccionada así.", una("Una expre-\nsión fraccio-\nnada así."));
        // guion blando de fin de línea, que PDFBox emite en algunos PDF
        assertEquals("Me gusta la palabra completa.", una("Me gusta la pala­\nbra completa."));
    }

    @Test
    @DisplayName("no une guiones legítimos: a media línea, antes de mayúscula o número, o con espacio")
    void guionesLegitimos() {
        String medio = "Aprendizaje socio-afectivo en el aula";
        assertEquals(medio, una(medio));
        assertEquals("Prefijo ex-\nBeta", una("Prefijo ex-\nBeta"));
        assertEquals("Rango de edad 18-\n25 años", una("Rango de edad 18-\n25 años"));
        assertEquals("Opción -\nsiguiente", una("Opción -\nsiguiente"));
        assertEquals("Fin de la frase-\n\nnueva", una("Fin de la frase-\n\nnueva"));
    }

    // ---------------------------------------------------------------- Espacios

    @Test
    @DisplayName("colapsa espacios, tabulaciones y NBSP; quita invisibles y soft hyphen")
    void espacios() {
        assertEquals("Me gusta aprender con ejemplos.", una("  Me gusta \t aprender​ con   ejem­plos.  "));
        assertEquals("a b", una("a  b﻿"));
    }

    @Test
    @DisplayName("deja a lo sumo una línea en blanco entre bloques y recorta los extremos")
    void lineasEnBlanco() {
        assertEquals("Uno de los ítems.\n\nDos de los ítems.\nTres de los ítems.",
                una("\n\n\nUno de los ítems.\n\n\n\n\nDos de los ítems.\r\nTres de los ítems.\n\n\n"));
        assertEquals("", una(" \n\t\n"));
        assertEquals("", una(null));
    }

    @Test
    @DisplayName("no altera dígitos ni numeración dentro del contenido")
    void digitosIntactos() {
        String texto = "3. Tengo 18 años y estudio 2 horas al día.\n4. Marque 1 si nunca, 5 si siempre (rango 1-5).\n"
                + "a) 12\nb) 2024\nc) 3,5 puntos";
        assertEquals(texto, una(texto));
    }

    // ---------------------------------------------------------------- Unir

    @Test
    @DisplayName("unir pone el marcador con el número de página real y omite las vacías")
    void unir() {
        String unido = LimpiadorTexto.unir(List.of("Texto de la uno.", "", "Texto de la tres."));

        assertEquals("--- página 1 ---\nTexto de la uno.\n\n--- página 3 ---\nTexto de la tres.", unido);
        assertEquals("", LimpiadorTexto.unir(List.of("", " ")));
    }
}
