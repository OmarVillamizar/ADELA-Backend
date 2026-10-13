# ENGINE FORMAT ("cuestionario")
{
 "nombre": str, "siglas": str, "descripcion": str (<= 1000), "autor": str, "version": str,
 "estilos": [{"id": int, "nombre": str, "tipo": "PRIMARIO"|"COMPUESTO",
              "coeficientes": null | [{"estiloId": int, "coeficiente": number}]}],
 "preguntas": [{"pregunta": str, "orden": int, "formato": "UNICA"|"MULTIPLE"|"JERARQUIA"|"REPARTO",
                "obligatoria": bool, "minSelecciones": int|null, "maxSelecciones": int|null,
                "puntosRepartir": int|null,
                "opciones": [{"orden": int, "respuesta": str,
                              "pesos": [{"estiloId": int, "peso": number}]}]}],
 "interpretacion": {"esquema": str, "delta": number, "esIpsativo": null,
                    "bandas": [{"estilo": str, "escala": "BRUTO"|"POMP", "limiteInferior": number,
                                "limiteSuperior": number, "etiqueta": str, "orden": int}],
                    "escalones": [{"totalMin": number, "totalMax": number, "distancia": number}],
                    "plano": null | {"ejeX": str, "ejeY": str, "corteX": number|null,
                                     "corteY": number|null, "xAltoYAlto": str, "xBajoYAlto": str,
                                     "xBajoYBajo": str, "xAltoYBajo": str,
                                     "invertirX": bool, "invertirY": bool},
                    "complementaria": null | {...as in the catalog example}}
}
nombre, siglas, descripcion, autor, version: required, non-empty. Unknown author or version:
write what the document says ("Adaptación", "s. f.") and add a revision entry.
descripcion is what the student reads before answering: what the questionnaire is about and how to
answer (e.g. how to rank or mark the options). Drop any instruction to add up, total, score or
interpret ("sume los puntajes de cada columna", "EC = OR =", cutoff tables): ADELA scores
automatically and the student never sees that step.
esIpsativo: always null (ADELA derives it from the formats).

# SCORING (how ADELA computes)
- Raw score of a style = sum over items of (answer amount x option weight for that style).
  UNICA / MULTIPLE: amount 1 per marked option. JERARQUIA: amount = rank 1..K given to the
  option, K = "most like me". REPARTO: amount = points given to the option.
- COMPUESTO = sum(coeficiente x raw score of each primary). Contrast if a coefficient is negative
  (range has negatives), additive otherwise.
- Theoretical range per item: UNICA min..max option weight; MULTIPLE best/worst sums within
  min/max selections; JERARQUIA highest rank to the heaviest option; REPARTO points x min/max
  weight; obligatoria false widens the range to include 0. Style range = sum of item ranges.
- POMP = 100 x (raw - min) / (max - min). It is NOT a percentile.
- Likert: UNICA with weight = scale value on its style (1..5). Reverse item: 5..1.
  Yes/No or "me identifico": the scoring option weighs 1, the other has pesos [].
  Forced choice a/b between two poles: a -> pole 1, b -> pole 2; read with a COMPUESTO pole1 - pole2.
- An option may weigh on several primaries.

# INTERPRETATION SCHEMES (exact engine behaviour)
- NINGUNA: scores only. Bands, if any, are shown as labels.
- BAREMO: each score gets its band label. No dominant style.
- RELATIVO: dominant = primaries with POMP >= max POMP - delta.
- RELATIVO_ESCALONADO (stepping distance): sort primaries by raw score desc; add the next while its gap
  to the previous <= distancia; distancia = escalon whose [totalMin, totalMax] contains the total
  of all primary raw scores. No escalon for that total -> no profile.
- NIVEL_SUPERIOR: a primary is dominant when its score is inside its band with the highest orden.
  Profile: SIMPLE / DOBLE / TRIPLE / CUADRUPLE by the number of dominants, plus a level code.
- CUADRANTES: the two axis scores against corteX / corteY -> one of four corners. Equal to the
  cut counts as the low side. invertirX / invertirY only flip the drawing.
- Band lookup: first band in orden whose closed interval contains the value (BRUTO raw or POMP).

# ENGINE CONSTRAINTS (the server rejects violations)
Estilos:
- At least one PRIMARIO. ids unique ints (use 1..n). Names unique and non-empty.
- PRIMARIO: coeficientes null. COMPUESTO: >= 1 coefficient, only PRIMARIO ids, non-zero, no
  repeated id. One level only (no compound of compounds).
- Every PRIMARIO must receive a non-zero weight from at least one option.
Preguntas:
- formato required. opciones >= 2 (MULTIPLE may have 1). JERARQUIA needs >= 2.
- minSelecciones / maxSelecciones only with MULTIPLE: 0 <= min <= K; max null (= all) or
  max(min,1) <= max <= K. Other formats: both null.
- puntosRepartir only with REPARTO, integer > 0. Other formats: null.
- Every option has "pesos" (may be []). Weights only to PRIMARIO ids, finite numbers, no repeated
  estiloId inside one option.
- orden: 1..n for items and 1..K for options, in document order.
- obligatoria true unless the source allows leaving items blank.
Interpretacion:
- esquema required. delta 0..100 (10 unless a source says otherwise).
- banda.estilo = the exact nombre of an existing estilo (primary or compound). escala required.
  limiteInferior <= limiteSuperior. etiqueta 1..60 chars. orden required and unique per
  (estilo, escala); ascending from lowest level to highest.
- No overlap per (estilo, escala): BRUTO bands must not share a limit (0-6, 7-8);
  POMP bands may share one (0-33.3, 33.3-66.7).
- Bands should cover the whole theoretical range of their style without gaps (integer steps for
  BRUTO). Report any gap the source itself leaves.
- escalones: totalMin <= totalMax, distancia >= 0, no overlap, covering 1..max possible total.
  RELATIVO_ESCALONADO requires escalones.
- NIVEL_SUPERIOR requires bands on the primaries.
- CUADRANTES requires plano: ejeX and ejeY = two distinct existing style names (normally the two
  contrast COMPUESTOS); four distinct corner names, 1..60 chars each.
- complementaria only with RELATIVO or RELATIVO_ESCALONADO; titulo <= 150, introduccion <= 500,
  enunciado <= 500, nota <= 300, >= 2 opciones (texto <= 200, descripcion <= 1000,
  resultado <= 100 and distinct, resultadoDescripcion <= 1000).
Not expressible at all: conditional items, timing, per-person weights, non-linear formulas,
more than one esquema, more than one plane, narrative texts per level.

# CONVENTIONS (lecturaAsistente). ADELA computes the numbers; you only choose.
- SOLO_PUNTAJES -> NINGUNA.
- NIVELES -> BAREMO; every style gets POMP thirds 0-33.3 / 33.3-66.7 / 66.7-100 labeled
  Bajo / Medio / Alto (contrast compounds: "Tiende a <pole2>" / Equilibrado / "Tiende a <pole1>").
- PREDOMINANTE -> RELATIVO, delta 10.
- DOMINANCIA -> NIVEL_SUPERIOR; primaries get POMP 0-49.99 Terciaria, 50-74.99 Secundaria,
  75-100 Primaria (equals 60 / 80 of 100 in the original level profile).
- BAREMO_MODELO (+ modeloAsistente CUATRO_ESTILOS or POLOS) -> BAREMO; the model's original
  levels converted to POMP proportionally (cut halfway between levels). Same structure as the
  original -> identical results; other item counts keep the proportions.
- ESCALONADO -> RELATIVO_ESCALONADO; the reference table (1-21:1, 22-27:2, 28-32:3, 33-64:4,
  built for 64 possible marks) scaled to the real number of marks; distancia never below 1.
- CUADRANTES + corte EQUILIBRIO -> cuts at 0. corte REFERENCIA -> cuts +6 / +7 scaled by
  n x (k - 1) / 36 (n items, k primaries); valid for ranking items.
Use a convention only when the source gives the RULE but not the numbers, or the teacher asks for
the reading. Always mark it CONVENCION and add a revision entry.
