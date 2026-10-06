package com.adela.calificacion;

/** Fila de la tabla de distancia de paso: si totalMin <= T <= totalMax, la distancia es d. */
public record Escalon(double totalMin, double totalMax, double distancia) {
}
