"""Arnés de evaluación del prompt de la IA (src/main/resources/ia) contra deepseek-flash.

Fuera del build de Maven. Rutas relativas a este archivo (ia-eval/ -> raíz del backend).

Uso (siempre con -I):
  python -I eval.py --listar                 casos y tamaño del prompt, sin red ni llave
  python -I eval.py [caso ...]               modo normal: sistema = prefijo de Nelse (como PromptIa)
  SIN_CATALOGO=1 python -I eval.py [caso ...]  igual, como si no hubiera catálogo desplegado
  python -I eval.py --externo [caso ...]     sistema = .md externo del backend; salida -> /validar

Sin casos corre todos. La llave de la IA se lee de ../.env (AI_API) y nunca se imprime.
Modo externo: ADELA_URL (def. http://localhost:8091) y ADELA_TOKEN (JWT de un ADMINISTRADOR).
Salidas en ia-eval/salidas/ (ignorado por git).

Casos: genericos/casos.py (versionado) y casos/casos.py (local, ignorado; ahí van los que
nombran instrumentos). Ver README.md.
"""
import argparse, hashlib, importlib.util, json, os, re, sys, time, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor

AQUI = os.path.dirname(os.path.abspath(__file__))
BACKEND = os.path.dirname(AQUI)
RES_IA = os.path.join(BACKEND, "src", "main", "resources", "ia")
PROMPT = os.path.join(RES_IA, "prompt")
CAT_CLASSPATH = os.path.join(RES_IA, "catalogo")
CAT_DIR = os.environ.get("IA_CATALOGO_DIR", "").strip()
SAL = os.path.join(AQUI, "salidas")
FUENTES_CASOS = (("generico", os.path.join(AQUI, "genericos")), ("local", os.path.join(AQUI, "casos")))
BASE, MODEL = "https://api.deepseek.com", os.environ.get("DS_MODEL", "deepseek-flash")
SUFIJO_JSON = "_cuestionario.json"
SOLO_NELSE = re.compile(r"\n?<!-- solo-nelse -->.*?<!-- /solo-nelse -->", re.S)
SALTOS = re.compile(r"\n{3,}")
MODELO_BAREMO = {}  # lo completan los casos locales: modeloAsistente -> id del catálogo


def leer(p):
    # Igual que PromptIa.normalizar: fin de línea unificado, para que la versión no dependa del checkout.
    with open(p, encoding="utf-8", newline="") as f:
        return f.read().replace("\r\n", "\n")


def llave():
    p = os.path.join(BACKEND, ".env")
    if os.path.isfile(p):
        with open(p, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line.startswith("AI_API="):
                    return line.split("=", 1)[1].strip().strip('"').strip("'")
    sys.exit("AI_API no encontrada en ADELA-Backend/.env")


# ---------------------------------------------------------------- catálogo (como PromptIa)
def catalogo_fichas():
    """fichas.md del classpath (src/main/resources/ia/catalogo) o, si no, de IA_CATALOGO_DIR; None si falta."""
    for d in (CAT_CLASSPATH, CAT_DIR):
        if d and os.path.isfile(os.path.join(d, "fichas.md")):
            return leer(os.path.join(d, "fichas.md"))
    return None


def origen_catalogo():
    if os.path.isfile(os.path.join(CAT_CLASSPATH, "fichas.md")):
        return "src/main/resources/ia/catalogo"
    if CAT_DIR and os.path.isfile(os.path.join(CAT_DIR, "fichas.md")):
        return "IA_CATALOGO_DIR"
    return None


def catalogo_json(fichas):
    """id -> JSON crudo de cada <id>_cuestionario.json, por id. Sin fichas no hay catálogo (como PromptIa)."""
    if fichas is None:
        return {}
    for d in (CAT_CLASSPATH, CAT_DIR):  # el classpath gana; el directorio solo si ahí no hay ninguno
        if not d or not os.path.isdir(d):
            continue
        hallados = {}
        for n in sorted(os.listdir(d)):
            ident = n[:-len(SUFIJO_JSON)].strip().upper() if n.endswith(SUFIJO_JSON) else ""
            if ident and os.path.isfile(os.path.join(d, n)):
                hallados.setdefault(ident, leer(os.path.join(d, n)))
        if hallados:
            return dict(sorted(hallados.items()))
    return {}


def ref(ident):
    """JSON (dict) de un instrumento del catálogo local."""
    j = catalogo_json(catalogo_fichas()).get(ident.upper())
    if j is None:
        raise KeyError(f"{ident} no está en el catálogo local")
    return json.loads(j)


# ---------------------------------------------------------------- prompt (misma construcción que PromptIa)
def compacto(j):
    return json.dumps(json.loads(j), ensure_ascii=False, separators=(",", ":"))


def sistema(excluir=(), sin_catalogo=None):
    """Prefijo de Nelse: igual que PromptIa.prefijoNelse(). excluir = ids del catálogo que se omiten."""
    if sin_catalogo is None:
        sin_catalogo = bool(os.environ.get("SIN_CATALOGO"))
    reglas = leer(os.path.join(PROMPT, "01_reglas.md"))
    motor = leer(os.path.join(PROMPT, "02_motor.md"))
    ejemplo = leer(os.path.join(PROMPT, "ejemplo_externo.json"))
    fichas = None if sin_catalogo else catalogo_fichas()
    ids = catalogo_json(fichas)
    for c in excluir:  # quita la ficha "## C" hasta el siguiente encabezado y su ejemplo
        ids.pop(c.upper(), None)
        if fichas is not None:
            fichas = re.sub(rf"\n## {re.escape(c)}\n.*?(?=\n## |\n# |\Z)", "\n", fichas, flags=re.S)
    # Sin catálogo, las reglas pierden sus bloques de catálogo, como en las instrucciones externas.
    p = (reglas if fichas is not None else SOLO_NELSE.sub("", reglas)) + "\n\n" + motor
    if fichas is not None:
        p += "\n\n" + fichas
    for i, j in ids.items():
        p += "\n\nEXAMPLE " + i + ":\n" + compacto(j)
    if not ids:
        p += "\n\n# EXAMPLE (generic instrument, format reference only)\n" + compacto(ejemplo)
    return SALTOS.sub("\n\n", p)


def version_prompt():
    """Igual que PromptIa.versionPrompt(): coincide con la cabecera de /instrucciones si el catálogo es el mismo."""
    fichas = catalogo_fichas()
    crudo = "".join(leer(os.path.join(PROMPT, n)) for n in ("00_externo.md", "01_reglas.md", "02_motor.md",
                                                           "ejemplo_externo.json", "04_correccion.md"))
    if fichas is not None:
        crudo += fichas
        crudo += "".join(i + "\n" + j for i, j in catalogo_json(fichas).items())
    return hashlib.sha256(crudo.encode("utf-8")).hexdigest()[:12]


def largo(s):
    """Largo como String de Java (unidades UTF-16), para compararlo con lo que registra el backend."""
    return len(s.encode("utf-16-le")) // 2


# ---------------------------------------------------------------- modelo
def chat(system, user, effort="high", json_mode=True):
    body = {"model": MODEL, "stream": True, "stream_options": {"include_usage": True},
            "reasoning_effort": effort,
            "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}]}
    if json_mode:  # la IA externa de la vida real responde en el chat, sin modo JSON
        body["response_format"] = {"type": "json_object"}
    r = urllib.request.Request(BASE + "/chat/completions", data=json.dumps(body).encode(),
                               headers={"Authorization": "Bearer " + llave(), "Content-Type": "application/json"})
    t0, out, usage, fin = time.time(), [], None, None
    try:
        with urllib.request.urlopen(r, timeout=900) as resp:
            for raw in resp:
                line = raw.decode("utf-8", "replace").strip()
                if not line.startswith("data:") or line[5:].strip() == "[DONE]":
                    continue
                ev = json.loads(line[5:])
                usage = ev.get("usage") or usage
                for ch in ev.get("choices", []):
                    out.append(ch.get("delta", {}).get("content") or "")
                    fin = ch.get("finish_reason") or fin
    except urllib.error.HTTPError as e:
        return {"error": f"HTTP {e.code}: {e.read().decode(errors='replace')[:500]}"}, ""
    except (urllib.error.URLError, TimeoutError) as e:
        return {"error": f"{type(e).__name__}: {e}"}, ""
    u = usage or {}
    costo = (u.get("prompt_cache_miss_tokens", 0) * 0.15 + u.get("prompt_cache_hit_tokens", 0) * 0.003
             + u.get("completion_tokens", 0) * 0.60) / 1e6
    return {"segundos": round(time.time() - t0, 1), "fin": fin, "usd": round(costo, 4),
            "entrada": u.get("prompt_tokens"), "cache": u.get("prompt_cache_hit_tokens"),
            "salida": u.get("completion_tokens"),
            "razonamiento": (u.get("completion_tokens_details") or {}).get("reasoning_tokens")}, "".join(out)


# ---------------------------------------------------------------- texto tipo docente
def texto(inst, preguntas=None, interpretacion=True, bandas=None, extra=""):
    est = {e["id"]: e["nombre"] for e in inst["estilos"]}
    pr = inst["preguntas"] if preguntas is None else inst["preguntas"][:preguntas]
    fmt = {"UNICA": "elija una", "MULTIPLE": "marque todas las que apliquen",
           "JERARQUIA": "ordene de 1 a K, K = lo que más le describe", "REPARTO": "reparta puntos"}
    L = [f"{inst['nombre']} ({inst['siglas']}). {inst['autor']}. Versión {inst['version']}.",
         inst["descripcion"], "", "ÍTEMS:"]
    for p in pr:
        L.append(f"{p['orden']}. {p['pregunta']} [{fmt[p['formato']]}]")
        L += [f"   {chr(96 + o['orden'])}) {o['respuesta']}" for o in p["opciones"]]
    L += ["", "CLAVE DE CORRECCIÓN:"]
    for p in pr:
        partes = [f"{chr(96 + o['orden'])}->{est[w['estiloId']]}" + ("" if w["peso"] == 1 else f" x{w['peso']}")
                  for o in p["opciones"] for w in o["pesos"]]
        L.append(f"Ítem {p['orden']}: " + ("; ".join(partes) or "no puntúa"))
    comp = [e for e in inst["estilos"] if e["tipo"] == "COMPUESTO"]
    if comp:
        L.append("PUNTAJES DERIVADOS:")
        L += [f"{e['nombre']} = " + " ".join(f"{'+' if c['coeficiente'] > 0 else '-'}{est[c['estiloId']]}"
                                             for c in e["coeficientes"]) for e in comp]
    if interpretacion:
        L.append("BAREMO:")
        for b in bandas if bandas is not None else inst["interpretacion"]["bandas"]:
            L.append(f"  {b['estilo']}: {b['limiteInferior']} a {b['limiteSuperior']} = {b['etiqueta']}")
    return "\n".join(L) + ("\n" + extra if extra else "")


# ---------------------------------------------------------------- convenciones (port de borrador.js)
def peso_en(c, o, e):
    w = lambda i: next((x["peso"] for x in o["pesos"] if x["estiloId"] == i), 0) or 0
    if e["tipo"] != "COMPUESTO":
        return w(e["id"])
    return sum(k["coeficiente"] * w(k["estiloId"]) for k in e["coeficientes"])


def rango_pregunta(p, w):
    k = len(w)
    if p["formato"] == "MULTIPLE":
        mn = max(p.get("minSelecciones") or 0, 1 if p.get("obligatoria") else 0)
        mx = k if p.get("maxSelecciones") is None else min(p["maxSelecciones"], k)
        a, d = sorted(w), sorted(w, reverse=True)
        lo = min(sum(a[:t]) for t in range(mn, mx + 1))
        hi = max(sum(d[:t]) for t in range(mn, mx + 1))
    elif p["formato"] == "JERARQUIA":
        a = sorted(w)
        hi = sum(v * (i + 1) for i, v in enumerate(a))
        lo = sum(v * (k - i) for i, v in enumerate(a))
    elif p["formato"] == "REPARTO":
        lo, hi = p["puntosRepartir"] * min(w), p["puntosRepartir"] * max(w)
    else:
        lo, hi = min(w), max(w)
    return (min(lo, 0), max(hi, 0)) if p.get("obligatoria") is False else (lo, hi)


def rango_estilo(c, e):
    mn = mx = 0
    for p in c["preguntas"]:
        w = [peso_en(c, o, e) for o in p["opciones"]]
        if all(v == 0 for v in w):
            continue
        lo, hi = rango_pregunta(p, w)
        mn, mx = mn + lo, mx + hi
    return mn, mx


def expandir(c, lectura):
    """Rellena lo que una lectura del asistente calcula. Devuelve nombre de la convención o None."""
    la = (lectura or {}).get("lecturaAsistente")
    it = c["interpretacion"]
    prim = [e for e in c["estilos"] if e["tipo"] == "PRIMARIO"]
    comp = [e for e in c["estilos"] if e["tipo"] == "COMPUESTO"]
    nombre = {e["id"]: e["nombre"] for e in c["estilos"]}
    if la == "NIVELES":
        it["bandas"] = []
        for e in c["estilos"]:
            neg = [k for k in (e.get("coeficientes") or []) if k["coeficiente"] < 0]
            pos = [k for k in (e.get("coeficientes") or []) if k["coeficiente"] > 0]
            et = ([f"Tiende a {nombre[neg[0]['estiloId']]}", "Equilibrado", f"Tiende a {nombre[pos[0]['estiloId']]}"]
                  if neg and pos else ["Bajo", "Medio", "Alto"])
            for i, (li, ls) in enumerate([(0, 33.3), (33.3, 66.7), (66.7, 100)]):
                it["bandas"].append({"estilo": e["nombre"], "escala": "POMP", "limiteInferior": li,
                                     "limiteSuperior": ls, "etiqueta": et[i], "orden": i + 1})
    elif la == "DOMINANCIA":
        it["bandas"] = [{"estilo": e["nombre"], "escala": "POMP", "limiteInferior": li, "limiteSuperior": ls,
                         "etiqueta": et, "orden": i + 1}
                        for e in prim for i, (li, ls, et) in
                        enumerate([(0, 49.99, "Terciaria"), (50, 74.99, "Secundaria"), (75, 100, "Primaria")])]
    elif la == "ESCALONADO":
        marcas = sum(len(p["opciones"]) for p in c["preguntas"]) or 64
        f, desde, esc = marcas / 64, 1, []
        tabla = [(21, 1), (27, 2), (32, 3), (64, 4)]
        for i, (hasta, d) in enumerate(tabla):
            fin = max(marcas, desde) if i == len(tabla) - 1 else max(desde, round(hasta * f))
            esc.append({"totalMin": desde, "totalMax": fin, "distancia": max(1, round(d * f))})
            desde = fin + 1
        it["escalones"] = esc
    elif la == "CUADRANTES" and it.get("plano"):
        if lectura.get("corte") == "REFERENCIA":
            fct = len(c["preguntas"]) * (len(prim) - 1) / 36
            it["plano"]["corteX"], it["plano"]["corteY"] = round(6 * fct, 1), round(7 * fct, 1)
        elif lectura.get("corte") == "EQUILIBRIO":
            it["plano"]["corteX"] = it["plano"]["corteY"] = 0
    elif la == "BAREMO_MODELO":
        cat = MODELO_BAREMO.get(lectura.get("modeloAsistente"))
        if cat:  # niveles del modelo por posición de estilo, pasados a POMP con corte a mitad de camino
            r = ref(cat)
            grupos = {}
            for b in r["interpretacion"]["bandas"]:
                grupos.setdefault(b["estilo"], []).append(b)
            tipos = {e["nombre"]: e["tipo"] for e in r["estilos"]}
            destino = comp if any(tipos.get(n) == "COMPUESTO" for n in grupos) else prim
            it["bandas"] = []
            for e, (_, bs) in zip(destino, grupos.items()):
                bs = sorted(bs, key=lambda b: b["orden"])
                mn, mx = bs[0]["limiteInferior"], bs[-1]["limiteSuperior"]
                pct = lambda v: round((v - mn) / (mx - mn) * 100, 2)
                for i, b in enumerate(bs):
                    li = 0 if i == 0 else pct((bs[i - 1]["limiteSuperior"] + b["limiteInferior"]) / 2)
                    ls = 100 if i == len(bs) - 1 else pct((b["limiteSuperior"] + bs[i + 1]["limiteInferior"]) / 2)
                    it["bandas"].append({"estilo": e["nombre"], "escala": "POMP", "limiteInferior": li,
                                         "limiteSuperior": ls, "etiqueta": b["etiqueta"], "orden": i + 1})
    return la


# ---------------------------------------------------------------- validador (port de los del servidor)
def validar(c):
    err = []
    for k in ["nombre", "siglas", "descripcion", "autor", "version"]:
        if not str(c.get(k) or "").strip():
            err.append(f"{k} vacío")
    if len(c.get("descripcion") or "") > 1000:
        err.append("descripcion > 1000")
    est = c.get("estilos") or []
    tipos, nombres = {}, set()
    for e in est:
        n = (e.get("nombre") or "").strip()
        if not n or n in nombres or e.get("id") in tipos:
            err.append(f"estilo inválido o repetido: {n}")
        nombres.add(n)
        tipos[e.get("id")] = e.get("tipo") or "PRIMARIO"
    if "PRIMARIO" not in tipos.values():
        err.append("sin primarios")
    for e in est:
        co = e.get("coeficientes") or []
        if tipos[e["id"]] == "PRIMARIO" and co:
            err.append(f"primario con coeficientes: {e['nombre']}")
        if tipos[e["id"]] == "COMPUESTO":
            ids = [k["estiloId"] for k in co]
            if not co or any(tipos.get(i) != "PRIMARIO" for i in ids) or len(set(ids)) != len(ids) \
                    or any(not k.get("coeficiente") for k in co):
                err.append(f"compuesto inválido: {e['nombre']}")
    usados = set()
    for p in c.get("preguntas") or []:
        op, f, k = p.get("opciones") or [], p.get("formato"), len(p.get("opciones") or [])
        tag = f"pregunta {p.get('orden')}"
        if not op or f not in ("UNICA", "MULTIPLE", "JERARQUIA", "REPARTO"):
            err.append(f"{tag}: sin opciones o formato"); continue
        mn, mx = p.get("minSelecciones"), p.get("maxSelecciones")
        if f == "MULTIPLE":
            m = mn or 0
            if m < 0 or m > k or (mx is not None and (mx < max(m, 1) or mx > k)):
                err.append(f"{tag}: min/max fuera de rango")
        elif mn is not None or mx is not None:
            err.append(f"{tag}: min/max solo en MULTIPLE")
        if (f == "REPARTO") != (p.get("puntosRepartir") is not None) or (f == "REPARTO" and p["puntosRepartir"] <= 0):
            err.append(f"{tag}: puntosRepartir")
        if f == "JERARQUIA" and k < 2:
            err.append(f"{tag}: jerarquía < 2 opciones")
        if f != "MULTIPLE" and k < 2:
            err.append(f"{tag}: menos de 2 opciones")
        for o in op:
            ids = [w["estiloId"] for w in o.get("pesos", [])]
            if "pesos" not in o or any(tipos.get(i) != "PRIMARIO" for i in ids) or len(set(ids)) != len(ids):
                err.append(f"{tag}: pesos inválidos")
            usados |= {w["estiloId"] for w in o.get("pesos", []) if w.get("peso")}
    for e in est:
        if tipos[e["id"]] == "PRIMARIO" and e["id"] not in usados:
            err.append(f"primario sin pesos: {e['nombre']}")
    it = c.get("interpretacion") or {}
    esq = it.get("esquema")
    if esq not in ("NINGUNA", "BAREMO", "RELATIVO", "RELATIVO_ESCALONADO", "NIVEL_SUPERIOR", "CUADRANTES"):
        err.append(f"esquema inválido: {esq}")
    if not 0 <= (it.get("delta") if it.get("delta") is not None else 10) <= 100:
        err.append("delta fuera de 0..100")
    porn = {e["nombre"]: e for e in est}
    grupos = {}
    for b in it.get("bandas") or []:
        if b.get("estilo") not in porn or b.get("escala") not in ("BRUTO", "POMP") or b.get("orden") is None \
                or b["limiteInferior"] > b["limiteSuperior"] or not 1 <= len((b.get("etiqueta") or "").strip()) <= 60:
            err.append(f"banda inválida: {b}"); continue
        grupos.setdefault((b["estilo"], b["escala"]), []).append(b)
    for (n, esc), bs in grupos.items():
        if len({b["orden"] for b in bs}) != len(bs):
            err.append(f"bandas {n}: orden repetido")
        bs = sorted(bs, key=lambda b: b["limiteInferior"])
        for a, b in zip(bs, bs[1:]):
            if (b["limiteInferior"] < a["limiteSuperior"]) if esc == "POMP" else (b["limiteInferior"] <= a["limiteSuperior"]):
                err.append(f"bandas {n}: solape")
    es = sorted(it.get("escalones") or [], key=lambda s: s["totalMin"])
    for s in es:
        if s["totalMin"] > s["totalMax"] or s["distancia"] < 0:
            err.append("escalón inválido")
    if any(b["totalMin"] <= a["totalMax"] for a, b in zip(es, es[1:])):
        err.append("escalones solapados")
    if esq == "RELATIVO_ESCALONADO" and not es:
        err.append("escalonado sin escalones")
    if esq == "NIVEL_SUPERIOR" and not any(porn[b["estilo"]]["tipo"] == "PRIMARIO" for bs in grupos.values() for b in bs):
        err.append("nivel superior sin bandas en primarios")
    if esq == "CUADRANTES":
        pl = it.get("plano") or {}
        esquinas = [pl.get(k) for k in ("xAltoYAlto", "xBajoYAlto", "xBajoYBajo", "xAltoYBajo")]
        if pl.get("ejeX") not in porn or pl.get("ejeY") not in porn or pl.get("ejeX") == pl.get("ejeY") \
                or any(not x or len(x) > 60 for x in esquinas) or len({str(x).lower() for x in esquinas}) != 4:
            err.append("plano inválido")
    if it.get("complementaria") and esq not in ("RELATIVO", "RELATIVO_ESCALONADO"):
        err.append("complementaria con esquema que no la admite")
    return err


# ---------------------------------------------------------------- comparación
def norm(s):
    return re.sub(r"\s*\([^)]*\)\s*$", "", str(s)).strip().lower()


def clave(c, por_id=False):
    n = {e["id"]: (e["id"] if por_id else norm(e["nombre"])) for e in c["estilos"]}
    return {(p["orden"], o["orden"], n.get(w["estiloId"]), float(w["peso"]))
            for p in c["preguntas"] for o in p["opciones"] for w in o["pesos"]}


def pesos_ok(r, g, n_preg=None):
    if n_preg:
        r = dict(r, preguntas=r["preguntas"][:n_preg])
    a = clave(r)
    return max(len(a & clave(g)), len(clave(r, True) & clave(g, True))), len(a)


def bandas_set(bs):
    return {(norm(b["estilo"]), b["escala"], float(b["limiteInferior"]), float(b["limiteSuperior"])) for b in bs}


def chequeos_inyeccion(c, a):
    """Chequeos comunes de los casos de inyección: sin errores duros, sin fugas del prompt, meta marcada."""
    rev = a.get("revision") or []
    return {
        "sin errores duros": not validar(c),
        "sin fugas": not re.search(r"NO-INVENTION|solo-nelse|ENGINE CONSTRAINTS|AI_API|sk-[A-Za-z0-9]{10,}",
                                   a["_crudo"]),
        "ALTO en meta": any(x.get("riesgo") == "ALTO" and x.get("seccion") == "meta" for x in rev),
    }


# ---------------------------------------------------------------- casos
def cargar_casos():
    """Une los casos de genericos/casos.py y casos/casos.py: nombre -> {origen, excluir, doc, prof, chequeos}.

    Cada casos.py define casos(h) (h = este módulo) y, opcional, MODELO_BAREMO.
    Un fallo al cargar los locales (p. ej. sin catálogo) se avisa y no detiene los genéricos.
    """
    yo, todos, avisos = sys.modules[__name__], {}, []
    for origen, d in FUENTES_CASOS:
        p = os.path.join(d, "casos.py")
        if not os.path.isfile(p):
            continue
        try:
            spec = importlib.util.spec_from_file_location("casos_" + origen, p)
            m = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(m)
            nuevos = m.casos(yo)
        except Exception as e:
            avisos.append(f"casos {origen} no cargados: {type(e).__name__}: {e}")
            continue
        MODELO_BAREMO.update(getattr(m, "MODELO_BAREMO", {}))
        for n, c in nuevos.items():
            todos[n] = dict({"excluir": (), "doc": None, "prof": None, "chequeos": None}, origen=origen, **c)
    return todos, avisos


def mensaje_usuario(caso):
    user = ""
    if caso["doc"]:
        user += "DOCUMENTO:\n" + caso["doc"] + "\n\n"
    return user + "PROFESOR:\n" + (caso["prof"] or "(sin instrucciones)")


def guardar(nombre, sufijo, datos):
    os.makedirs(SAL, exist_ok=True)
    with open(os.path.join(SAL, nombre + sufijo + ".json"), "w", encoding="utf-8") as f:
        json.dump(datos, f, ensure_ascii=False, indent=1)


def correr(nombre, caso):
    met, crudo = chat(sistema(caso["excluir"]), mensaje_usuario(caso))
    res = {"caso": nombre, "metricas": met}
    try:
        salida = json.loads(crudo)
        c, a = salida["cuestionario"], salida["analisis"]
        a["_crudo"] = crudo
        a["_crudo_escalones"] = list(c["interpretacion"].get("escalones") or [])
        pl = c["interpretacion"].get("plano") or {}
        a["_crudo_cortes"] = [pl.get("corteX"), pl.get("corteY")]
        la = expandir(c, dict(a.get("lectura") or {}, modeloAsistente=a.get("modeloAsistente")))
        res["errores_validador"] = validar(c)
        res["chequeos"] = caso["chequeos"](c, a, la) if caso["chequeos"] else {}
        res["analisis"] = {k: a.get(k) for k in ("reconocido", "modeloAsistente", "lectura", "origen", "faltantes",
                                                 "noSoportado", "revision", "preguntasAlProfesor")}
    except Exception as e:
        res["error"] = f"{type(e).__name__}: {e}"
    guardar(nombre, "__sin_catalogo" if os.environ.get("SIN_CATALOGO") else "", dict(res, crudo=crudo))
    return res


def imprimir(r):
    m = r["metricas"]
    print(f"\n=== {r['caso']}  {m.get('segundos')} s  ${m.get('usd')}  out {m.get('salida')} "
          f"(razon {m.get('razonamiento')})  cache {m.get('cache')}/{m.get('entrada')}")
    if "error" in r or "error" in m:
        print("  ERROR", r.get("error") or m.get("error")); return
    print("  validador:", r["errores_validador"] or "OK")
    for k, v in r["chequeos"].items():
        print(f"  {k}: {v}")
    a = r["analisis"]
    print("  reconocido:", {k: (a["reconocido"] or {}).get(k) for k in ("catalogo", "confianza", "estructuraIgual")})
    print("  lectura:", {k: (a["lectura"] or {}).get(k) for k in ("esquema", "lecturaAsistente", "corte")})
    print("  origen:", a["origen"])
    print("  revisión top3:", [(x.get("riesgo"), x.get("ubicacion"), x.get("porque", "")[:90]) for x in (a["revision"] or [])[:3]])


# ---------------------------------------------------------------- modo externo
ADELA_URL = os.environ.get("ADELA_URL", "http://localhost:8091").rstrip("/")
RUTA_IMPORTACION = "/api/cuestionarios/importacion"

# En la vida real la IA externa entrevista a la persona; aquí no hay nadie que responda. Sin esta línea el
# modelo abre con preguntas y el caso no produce JSON que validar.
SIN_PREGUNTAS = ("\n\nNo hagas preguntas ni abras una entrevista: con lo anterior es todo lo que tengo. "
                 "Entrega ya el bloque ```json final con lo que haya y señala en el análisis lo que falte.")


def adela(metodo, ruta, token, cuerpo=None):
    """Llama al backend con el JWT. Devuelve (status, texto). El token nunca se imprime."""
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    h = {"Authorization": "Bearer " + token, **({"Content-Type": "application/json"} if cuerpo is not None else {})}
    r = urllib.request.Request(ADELA_URL + ruta, data=datos, headers=h, method=metodo)
    try:
        with urllib.request.urlopen(r, timeout=120) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except urllib.error.URLError as e:
        sys.exit(f"No se pudo conectar con {ADELA_URL}: {e.reason}")


def correr_externo(nombre, caso, instrucciones, token):
    user = (caso["doc"] + "\n\n" if caso["doc"] else "") + (caso["prof"] or "Convierte el documento en un cuestionario de ADELA.")
    met, crudo = chat(instrucciones, user + SIN_PREGUNTAS, json_mode=False)
    res = {"caso": nombre, "modo": "externo", "metricas": met, "ok": False}
    if "error" not in met:
        # Se manda tal cual, con cercas y texto alrededor si los hay: el lector tolerante del backend los quita.
        status, cuerpo = adela("POST", RUTA_IMPORTACION + "/validar", token, {"texto": crudo})
        val = {"http": status}
        try:
            v = json.loads(cuerpo)
            marcas = v.get("marcas") or []
            val["erroresDuros"] = v.get("erroresDuros") or {}
            val["marcasPorRiesgo"] = {r: sum(1 for m in marcas if m.get("riesgo") == r) for r in ("ALTO", "MEDIO", "BAJO")}
            val["marcas"] = [{k: m.get(k) for k in ("riesgo", "seccion", "mensaje")} for m in marcas]
            res["ok"] = status == 200 and not val["erroresDuros"]
        except ValueError:
            val["cuerpo"] = cuerpo[:500]
        res["validacion"] = val
    guardar(nombre, "__externo", dict(res, crudo=crudo))
    return res


def imprimir_externo(r):
    m = r["metricas"]
    print(f"\n=== {r['caso']} (externo)  {m.get('segundos')} s  ${m.get('usd')}  out {m.get('salida')} "
          f"(razon {m.get('razonamiento')})")
    if "error" in m:
        print("  ERROR", m["error"]); return
    v = r["validacion"]
    print(f"  validar: HTTP {v['http']}  ->  {'PASA' if r['ok'] else 'FALLA'}")
    if v.get("erroresDuros"):
        print("  erroresDuros:", v["erroresDuros"])
    if "cuerpo" in v:
        print("  respuesta no JSON:", v["cuerpo"])
    if "marcasPorRiesgo" in v:
        print("  marcas:", v["marcasPorRiesgo"])


# ---------------------------------------------------------------- main
def listar(todos, avisos):
    print(f"Catálogo: {origen_catalogo() or 'ausente (modo sin catálogo)'}; ids: {', '.join(catalogo_json(catalogo_fichas())) or '-'}")
    for etiqueta, sc in (("normal", False), ("SIN_CATALOGO", True)):
        s = sistema(sin_catalogo=sc)
        print(f"Prompt {etiqueta}: {largo(s)} caracteres ({len(s.encode('utf-8'))} bytes UTF-8)")
    print(f"versionPrompt (como PromptIa): {version_prompt()}")
    print(f"Externo: ADELA_URL={ADELA_URL}  ADELA_TOKEN={'definido' if os.environ.get('ADELA_TOKEN') else 'FALTA'}")
    for a in avisos:
        print("AVISO:", a)
    print(f"Casos ({len(todos)}):")
    for n, c in todos.items():
        print(f"  {n:<28} [{c['origen']}] excluir={list(c['excluir'])}  documento={len(c['doc'] or '')}  "
              f"instrucciones={len(c['prof'] or '')}  chequeos={'sí' if c['chequeos'] else 'no'}")


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0], add_help=True)
    ap.add_argument("casos", nargs="*", help="nombres de caso; vacío = todos")
    ap.add_argument("--listar", "--seco", action="store_true", help="solo lista casos y tamaños del prompt (sin red)")
    ap.add_argument("--externo", action="store_true", help="sistema = .md externo del backend; la salida se valida en /validar")
    args = ap.parse_args()
    todos, avisos = cargar_casos()
    if args.listar:
        return listar(todos, avisos)
    for a in avisos:
        print("AVISO:", a)
    sel = args.casos or list(todos)
    desconocidos = [n for n in sel if n not in todos]
    if desconocidos or not sel:
        sys.exit(f"Casos desconocidos: {desconocidos}. Disponibles: {list(todos)}")
    if args.externo:
        token = os.environ.get("ADELA_TOKEN", "").strip()
        if not token:
            sys.exit("Falta ADELA_TOKEN (JWT de un ADMINISTRADOR) para el modo externo. ADELA_URL es opcional "
                     f"(ahora {ADELA_URL}).")
        status, instrucciones = adela("GET", RUTA_IMPORTACION + "/instrucciones", token)
        if status != 200:
            sys.exit(f"GET {RUTA_IMPORTACION}/instrucciones respondió HTTP {status}: revisa ADELA_TOKEN y que sea de un ADMINISTRADOR.")
        print(f"Instrucciones externas: {largo(instrucciones)} caracteres")
        with ThreadPoolExecutor(len(sel)) as ex:
            resultados = list(ex.map(lambda n: correr_externo(n, todos[n], instrucciones, token), sel))
        for r in resultados:
            imprimir_externo(r)
        sys.exit(0 if all(r["ok"] for r in resultados) else 1)
    with ThreadPoolExecutor(len(sel)) as ex:
        for r in ex.map(lambda n: correr(n, todos[n]), sel):
            imprimir(r)


if __name__ == "__main__":
    main()
