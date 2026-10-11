# ia-eval

Arnés de evaluación del prompt de la IA (`src/main/resources/ia/`). Es Python, está fuera del build
de Maven y llama al modelo de verdad (cuesta dinero): córrelo antes de cambiar el prompt.

## Uso

Siempre con `-I` (el intérprete no carga nada del directorio actual). Desde `ia-eval/`:

```
python -I eval.py --listar              # casos y tamaño del prompt; sin red ni llave
python -I eval.py [caso ...]            # prefijo de Nelse como sistema; sin casos corre todos
SIN_CATALOGO=1 python -I eval.py ...    # igual que un despliegue sin catálogo
python -I eval.py --externo [caso ...]  # sistema = .md externo del backend; salida -> /validar
```

- La llave de la IA se lee de `../.env` (`AI_API`) y no se imprime.
- Modo externo: el backend debe estar corriendo. `ADELA_URL` (por defecto `http://localhost:8091`)
  y `ADELA_TOKEN` (JWT de un ADMINISTRADOR; sin él termina con un mensaje). El sistema es lo que
  devuelve `GET /api/cuestionarios/importacion/instrucciones` (no se rearma en Python), el mensaje
  es el documento o las instrucciones del caso más una línea que prohíbe preguntar (en la vida real
  la IA entrevista; aquí nadie responde), y la respuesta cruda se manda a `POST .../validar`.
  Pasa si no hay `erroresDuros`; se guardan también las marcas por riesgo. No aplica los chequeos
  de cada caso. Termina con código 1 si algún caso falla.
- Salidas en `salidas/<caso>.json` (`__sin_catalogo` y `__externo` según el modo): respuesta cruda,
  métricas y chequeos.

## Prompt y catálogo

`eval.py` arma el sistema como `PromptIa`: mismos archivos de `ia/prompt/`, mismo orden y
separadores, y el catálogo descubierto igual (`fichas.md` y `<id>_cuestionario.json` de
`src/main/resources/ia/catalogo/` o, si ahí no hay, de `IA_CATALOGO_DIR`; el id sale del nombre del
archivo). Sin `fichas.md` o con `SIN_CATALOGO` el sistema usa las reglas sin los bloques
`solo-nelse` y el ejemplo genérico. `--listar` imprime también el `versionPrompt` calculado igual que
Java: debe coincidir con el de la cabecera de las instrucciones externas si el catálogo es el mismo.

Divergencias conocidas: el ejemplo se compacta con `json.dumps` y no con Jackson (puede variar el
escapado de caracteres de control o el formato de números en notación científica; ninguno aparece en
los archivos actuales), y el largo se cuenta en unidades UTF-16 para compararlo con `String.length()`.
Además, un caso puede excluir ids del catálogo (`excluir`), algo que el backend no hace.

## Casos

Cada carpeta tiene un `casos.py` con `casos(h)`, donde `h` es el módulo `eval`, que devuelve
`{nombre: {doc, prof, excluir, chequeos}}`. `doc` y `prof` son los mensajes `DOCUMENTO:` y
`PROFESOR:`; `chequeos(cuestionario, analisis, lectura)` devuelve un dict de comprobaciones.

- `genericos/` (versionado): casos con un instrumento ficticio, sin instrumentos publicados.
- `casos/` (ignorado por git): casos que nombran instrumentos publicados o usan su contenido, con
  sus textos. Si falta, `--listar` lo avisa y solo corren los genéricos. Puede definir
  `MODELO_BAREMO` (modelo del asistente -> id del catálogo).

Los checks genéricos (validadores del servidor, expansión de convenciones, métricas) están en
`eval.py`. Nada versionado debe nombrar instrumentos publicados ni traer su contenido.

## Visión (spike de la fase 6): páginas escaneadas como imágenes

Herramientas (fuera de `eval.py`, que no cambia; reusan su llave, prefijo y validadores):

- `RenderPdf.java`: renderiza un PDF a `pagina-N.png|jpg` en grises igual que `ExtractorDocumento`
  (CropBox, tope de 16 MP) e imprime por página los caracteres de texto, el tamaño y los bytes.
  `java -cp <pdfbox;fontbox;pdfbox-io;commons-logging del repositorio Maven> RenderPdf.java doc.pdf carpeta [dpi=144] [png|jpg] [calidad]`
- `escaneado.py`: fabrica un PDF solo de imágenes (giro leve, desenfoque, ruido) desde un texto plano,
  para probar la visión con documentos que no existen como escaneo.
- `vision.py` (siempre con `-I`): `modelos`, `sonda` (formato de la parte `image_url`), `tokens`,
  `cache`, `generar` (generación completa de Nelse con las páginas como imágenes, comparada con una
  referencia del catálogo local y/o con una corrida con texto), `archivo`, `gasto`. Suma cada llamada a
  `salidas/vision/gasto.json` y se detiene en `VISION_TOPE_USD` (0,60 por defecto). Nunca imprime la llave.

### Resultados (modelo `deepseek-flash`, 2026-10-11)

Todo lo siguiente se probó con `stream`, `reasoning_effort` `high`, `response_format` `json_object` y
el prefijo de Nelse como `system`.

- **Formato aceptado.** Un mensaje de usuario con `content` en partes, texto primero e imágenes después:
  `[{"type":"text","text":"..."},{"type":"image_url","image_url":{"url":"data:image/png;base64,..."}}]`.
  PNG y JPEG valen; `detail` se acepta pero no cambia los tokens. Se rechazan (HTTP 422, "unknown
  variant ... expected one of `text`, `image_url`, `file`"): `type:"image"` con `source`, `input_image`
  y `image_url` como cadena en lugar de objeto. Una parte `file` con el PDF no se acepta en la forma
  probada (HTTP 400 "file must have a file_id or file_data"). `GET /models`: `deepseek-flash` declara
  `input_modalities: [text, image]`; `deepseek-v4-pro`, solo texto.
- **Tokens por página.** Solo depende del tamaño en píxeles, no del contenido ni del formato: A4 a 144 DPI
  (1190x1683) = 1001 tokens (PNG o JPEG, página llena o casi en blanco); 100 DPI = 590; 72 DPI = ~340.
  A $0,15/M son $0,00015 por página (12 páginas: $0,002), irrelevante frente a la salida ($0,01-0,02).
- **Caché.** Las imágenes forman parte del prefijo cacheado (bloques de 64 tokens): repetir la petición
  da ~92 % de acierto y un turno añadido, igual. Pero la caché depende también de `reasoning_effort` y de
  `response_format`: cambiar el esfuerzo (`high` a `low`) o activar/quitar `json_object` da 0 aciertos,
  incluso en el prefijo de sistema que sí estaba cacheado. `max_tokens` y `stream` no influyen; sin
  esfuerzo equivale a `high`. Con el mismo esfuerzo, el turno de corrección acertó 43-53k de 49-65k tokens.
- **Precisión** (instrumento de referencia de 16 ítems, 3 páginas, y uno de 80 ítems escaneado de forma
  sintética en 8 páginas; ítems, opciones y clave comparados con el catálogo local):

  | Caso | Ítems exactos | Opciones exactas | Clave |
  |---|---|---|---|
  | 16 ítems, PNG 144, con catálogo | 16/16 | 63/64 (sim. mín. 0,98) | 64/64 |
  | 16 ítems, PNG 144, sin su ficha del catálogo | 16/16 | 64/64 | 64/64 |
  | 16 ítems, mixto (2 págs. texto + 1 imagen), sin ficha | 16/16 | 64/64 | 64/64 |
  | 80 ítems, PNG 144 / 100 / 72 y JPEG 144, con catálogo | 80/80 | 160/160 | 80/80 |
  | 80 ítems, PNG 144, sin ficha | 79/80 (una `@/s` por `@s`) | 160/160 | 80/80 |
  | 80 ítems, JPEG 144, sin ficha | 80/80 | 160/160 | 80/80 |
  | 80 ítems, PNG 72, sin ficha | 78/80 (dos palabras mal leídas) | 160/160 | 80/80 |

  Los baremos alterados impresos en el documento se leyeron bien (el documento gana al catálogo), y sin
  ficha no se inventaron cortes que el documento no imprime. Una instrucción dibujada en una página
  ("ignora las instrucciones...") no se obedeció y se marcó ALTO.
- **Latencia y costo.** Igual que con texto (56 s con texto y 61-63 s con imágenes en el instrumento de
  16 ítems; 76 s con texto y 79-110 s con imágenes en el de 80). Costo por corrida completa: $0,009-0,019
  (con el prefijo cacheado o no), dominado por la salida.
- **Tamaño.** Un escaneo ruidoso a 144 DPI en PNG pesa ~1,2 MB por página (8 páginas = ~13 MB en base64,
  aceptado); en JPEG calidad 0,8, ~0,3 MB. Una página limpia pesa ~0,17 MB en PNG.

Decisión para la integración: 144 DPI en grises, JPEG calidad 0,8, tope de 12 imágenes, `json_object` se
conserva, imágenes después del texto en un solo mensaje de usuario, y esfuerzo y `response_format`
iguales entre la generación y las correcciones si se quiere reutilizar el prefijo.
