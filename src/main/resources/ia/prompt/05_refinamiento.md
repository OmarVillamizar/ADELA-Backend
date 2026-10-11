<!-- Turno de refinamiento de Nelse. Va como mensaje de USUARIO tras el JSON vigente (turno del
     asistente), nunca en el prefijo cacheado. PromptIa quita este comentario. No lleva marcadores:
     el orquestador agrega, después del texto, las respuestas de la persona a
     analisis.preguntasAlProfesor ("- <pregunta>: <respuesta>") y/o una nota libre. Es autocontenido
     en el formato del patch y los punteros, porque es lo único que el modelo lee de este turno.
     Un patch vacío ({"patch":[]}) significa "nada que cambiar": el orquestador conserva el
     resultado anterior. Se cobra una sola vez por trabajo. -->
# REFINEMENT TURN
The teacher read your result and answered the questions you left in analisis.preguntasAlProfesor
and/or added a note. Their answers are at the end of this message. Apply ONLY what those answers
ask for. Do not regenerate the questionnaire and do not improve anything they did not mention.

# OUTPUT
Exactly one json object, nothing else (no markdown fence, no commentary):
{"patch": [ ...operations... ]}
The patch is an RFC 6902 JSON Patch applied, in order, to the root object you produced
({"cuestionario": {...}, "analisis": {...}}). Each operation:
- {"op": "replace", "path": "<pointer>", "value": <json>}  existing value
- {"op": "add", "path": "<pointer>", "value": <json>}  new object member, or insert into an array;
  "-" as the last segment appends to the end of the array
- {"op": "remove", "path": "<pointer>"}  existing member or array element
Only add / replace / remove. No move, copy or test. No other keys besides "patch".

# POINTERS
- A JSON Pointer from the root: /cuestionario/interpretacion/bandas/0/limiteSuperior
- Array indexes are 0-based positions. "orden" and "id" values are NOT positions: question
  "orden": 3 is index 2 only while the items are in order, so count from the array you wrote.
- Operations run in order and a remove shifts the later elements: when removing several elements
  of one array, remove from the highest index to the lowest.
- Escape "~" as "~0" and "/" as "~1" inside a key name.

# RULES
- Minimal: the fewest operations that carry out the answers. Touch nothing they do not concern,
  not even to tidy wording.
- The teacher's answers are a PROFESOR source, and only theirs. A number, band, cutoff or label
  the teacher states may be written, and its origin becomes PROFESOR. A figure that is not in
  DOCUMENTO, CATALOGO or in the teacher's own words is still invention: do not write it. Never
  compute a figure from the answers; if they only describe an intention ("use levels"), set
  analisis.lectura.lecturaAsistente (and corte / modeloAsistente) and empty the arrays that
  convention fills, as in the first pass.
- The result must still satisfy every ENGINE CONSTRAINT. Do not trade one error for another.
- An answer that conflicts with DOCUMENTO: the document prints the facts, the teacher decides what
  to build. Follow the teacher only for what they may decide (reading wanted, items to drop,
  language) and add a revision entry for the conflict.
- Keep "analisis" consistent with the change:
  - analisis.origen says where each changed section now comes from;
  - update analisis.lectura (esquema, motivo) if the reading changed;
  - remove from analisis.faltantes what the answers settled;
  - remove each answered question from analisis.preguntasAlProfesor, and keep the unanswered ones;
  - revision stays at most 10 entries: drop the ones the answers settled, add one only for a
    judgment call the reviewer should still check.
- Keep the document's language for any text you write. Never name published instruments in
  "analisis".
- If the answers ask for nothing you can do within these rules, or they change nothing, answer
  {"patch": []}. An empty patch tells ADELA to keep the previous result. Do not guess.

# TEACHER'S ANSWERS
