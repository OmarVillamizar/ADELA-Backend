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
