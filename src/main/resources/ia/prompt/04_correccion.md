<!-- Turno de corrección de Nelse. Va como mensaje de USUARIO tras el JSON que el modelo generó,
     nunca en el prefijo cacheado. PromptIa quita este comentario. No lleva marcadores: el
     orquestador agrega, después del texto, una línea "- <ubicacion>: <mensaje>" por cada error
     duro. Un patch vacío ({"patch":[]}) significa "no se puede corregir con un patch": el
     orquestador entonces regenera el JSON completo. No hay ninguna otra bandera de fallback. -->
# CORRECTION TURN
The server validated the JSON you produced and rejected it. Fix ONLY the hard errors listed at the
end of this message. Do not regenerate the questionnaire.

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
- A JSON Pointer from the root: /cuestionario/preguntas/2/opciones/0/pesos/1/peso
- Array indexes are 0-based positions. "orden" and "id" values are NOT positions: question
  "orden": 3 is index 2 only while the items are in order, so count from the array you wrote.
- Operations run in order and a remove shifts the later elements: when removing several elements
  of one array, remove from the highest index to the lowest.
- Escape "~" as "~0" and "/" as "~1" inside a key name.

# RULES
- Minimal: the fewest operations that make each listed error disappear. Touch nothing that is not
  listed, not even to improve wording or to "tidy up".
- A fix must still satisfy every ENGINE CONSTRAINT. Do not trade one error for another.
- Same NO-INVENTION RULES as before. If the fix seems to need a number, band, cutoff or label with
  no allowed source, do not write one. Instead: set analisis.lectura.lecturaAsistente (and corte /
  modeloAsistente) and empty the arrays that convention fills; or remove the unsupported bands /
  escalones, choose a scheme that does not need them, and list the gap in analisis.faltantes.
- If your fix changes where a value comes from, keep analisis.origen consistent with it. Add a
  revision entry (at most 10 in total) only when the fix is a judgment call the reviewer should
  check.
- Keep the document's language for any text you write. Never name published instruments in
  "analisis".
- If the errors cannot be fixed with a patch (they need rewriting a large part of the JSON, the
  right fix is unclear, or the listed location does not exist), answer {"patch": []}. An empty
  patch tells ADELA to regenerate from the document. Do not guess.

# HARD ERRORS TO FIX
