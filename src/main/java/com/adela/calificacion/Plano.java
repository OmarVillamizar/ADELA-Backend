package com.adela.calificacion;

/**
 * Plano de dos ejes (estilos, normalmente compuestos) cortado en cuatro
 * esquinas, como en el ciclo de aprendizaje de Kolb: X = AE - RO, Y = AC - CE.
 * Un valor igual al corte cae en el lado bajo; el lado alto empieza
 * estrictamente por encima. invertirX / invertirY solo afectan el dibujo.
 */
public record Plano(long ejeX, long ejeY, double corteX, double corteY, String xAltoYAlto, String xBajoYAlto,
        String xBajoYBajo, String xAltoYBajo, boolean invertirX, boolean invertirY) {

    /** Nombre de la esquina donde cae el punto (x, y). */
    public String esquina(double x, double y) {
        boolean xAlto = x > corteX + Calculos.EPS;
        boolean yAlto = y > corteY + Calculos.EPS;
        if (xAlto)
            return yAlto ? xAltoYAlto : xAltoYBajo;
        return yAlto ? xBajoYAlto : xBajoYBajo;
    }
}
