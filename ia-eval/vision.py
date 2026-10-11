"""Spike de visión: ¿el modelo acepta páginas como imágenes y a qué costo/precisión?

Fuera del build de Maven. Reusa eval.py (llave, prefijo de Nelse, validadores). Siempre con -I.

  python -I vision.py modelos                       GET /models (qué dice la API de modalidades)
  python -I vision.py sonda                         formato de la parte image_url con una imagen minúscula
  python -I vision.py tokens <carpeta> [...] [--por-pagina]   tokens por página: con y sin imágenes
  python -I vision.py cache <carpeta> [--esfuerzos low,low,low]  ¿se reutiliza el prefijo con imágenes?
  python -I vision.py generar <nombre> <carpeta> [--ref ID --vs salida.json --chequeos CASO --excluir ID ...]
                                                    generación completa de Nelse con las páginas como imágenes
                                                    (--documento/--imagenes: PDF mixto; --turno: mide la caché)
  python -I vision.py archivo <pdf>                 ¿acepta la API un PDF entero como parte 'file'?
  python -I vision.py gasto                         gasto acumulado en llamadas de este spike

Las páginas son carpetas con pagina-N.png|jpg: salen de RenderPdf.java (render igual al de
ExtractorDocumento; ver su cabecera) o, para un escaneo sintético, de escaneado.py + RenderPdf.java.

Cada llamada suma su costo a salidas/vision/gasto.json y el spike se detiene al acercarse al tope
(TOPE_USD). La llave se lee de ../.env a través de eval.py y nunca se imprime ni se guarda: los
cuerpos de error se truncan y se les quita la llave por si la API la repitiera.
"""
import argparse, base64, importlib.util, json, os, re, sys, time, urllib.request, urllib.error

AQUI = os.path.dirname(os.path.abspath(__file__))
SAL = os.path.join(AQUI, "salidas", "vision")
GASTO = os.path.join(SAL, "gasto.json")
TOPE_USD = float(os.environ.get("VISION_TOPE_USD", "0.60"))
_spec = importlib.util.spec_from_file_location("evalmod", os.path.join(AQUI, "eval.py"))
h = importlib.util.module_from_spec(_spec)
sys.modules["evalmod"] = h  # eval.cargar_casos se busca a sí mismo en sys.modules
_spec.loader.exec_module(h)


# ---------------------------------------------------------------- gasto
def gastado():
    try:
        with open(GASTO, encoding="utf-8") as f:
            return sum(x["usd"] for x in json.load(f))
    except (OSError, ValueError):
        return 0.0


def registrar(etiqueta, usd):
    os.makedirs(SAL, exist_ok=True)
    try:
        with open(GASTO, encoding="utf-8") as f:
            datos = json.load(f)
    except (OSError, ValueError):
        datos = []
    datos.append({"etiqueta": etiqueta, "usd": round(usd, 6)})
    with open(GASTO, "w", encoding="utf-8") as f:
        json.dump(datos, f, ensure_ascii=False, indent=1)
    return sum(x["usd"] for x in datos)


def costo(u):
    return (u.get("prompt_cache_miss_tokens", 0) * 0.15 + u.get("prompt_cache_hit_tokens", 0) * 0.003
            + u.get("completion_tokens", 0) * 0.60) / 1e6


# ---------------------------------------------------------------- llamada
def limpio(txt):
    """Sin la llave ni las imágenes (el error de la API puede repetir el cuerpo de la petición)."""
    k = h.llave()
    txt = txt.replace(k, "***") if k else txt
    return re.sub(r"data:image/[a-z]+;base64,[A-Za-z0-9+/=]{20,}", "data:image/...;base64,<...>", txt)


def llamar(etiqueta, mensajes, effort=None, json_mode=False, stream=False, max_tokens=None, extra=None,
           reserva=0.0):
    """POST /chat/completions. Devuelve (metricas, texto). Una respuesta de error trae 'error' truncado."""
    if gastado() + reserva >= TOPE_USD:
        sys.exit(f"TOPE: gastado {gastado():.4f} + reserva {reserva} >= {TOPE_USD}")
    cuerpo = {"model": h.MODEL, "messages": mensajes}
    if stream:
        cuerpo.update(stream=True, stream_options={"include_usage": True})
    if effort:
        cuerpo["reasoning_effort"] = effort
    if json_mode:
        cuerpo["response_format"] = {"type": "json_object"}
    if max_tokens:
        cuerpo["max_tokens"] = max_tokens
    cuerpo.update(extra or {})
    r = urllib.request.Request(h.BASE + "/chat/completions", data=json.dumps(cuerpo).encode(),
                               headers={"Authorization": "Bearer " + h.llave(), "Content-Type": "application/json"})
    t0, out, razon, usage, fin = time.time(), [], [], None, None
    try:
        with urllib.request.urlopen(r, timeout=900) as resp:
            if stream:
                for raw in resp:
                    line = raw.decode("utf-8", "replace").strip()
                    if not line.startswith("data:") or line[5:].strip() == "[DONE]":
                        continue
                    ev = json.loads(line[5:])
                    usage = ev.get("usage") or usage
                    for ch in ev.get("choices", []):
                        out.append(ch.get("delta", {}).get("content") or "")
                        razon.append(ch.get("delta", {}).get("reasoning_content") or "")
                        fin = ch.get("finish_reason") or fin
            else:
                d = json.loads(resp.read())
                usage = d.get("usage")
                ch = d["choices"][0]
                out.append(ch["message"].get("content") or "")
                razon.append(ch["message"].get("reasoning_content") or "")
                fin = ch.get("finish_reason")
    except urllib.error.HTTPError as e:
        return {"etiqueta": etiqueta, "error": f"HTTP {e.code}: {limpio(e.read().decode(errors='replace'))[:600]}"}, ""
    except (urllib.error.URLError, TimeoutError) as e:
        return {"etiqueta": etiqueta, "error": f"{type(e).__name__}: {limpio(str(e))[:300]}"}, ""
    u = usage or {}
    usd = costo(u)
    total = registrar(etiqueta, usd)
    return {"etiqueta": etiqueta, "segundos": round(time.time() - t0, 1), "fin": fin, "usd": round(usd, 5),
            "entrada": u.get("prompt_tokens"), "acierto": u.get("prompt_cache_hit_tokens"),
            "fallo": u.get("prompt_cache_miss_tokens"), "salida": u.get("completion_tokens"),
            "razonamiento": (u.get("completion_tokens_details") or {}).get("reasoning_tokens"),
            "gasto_total": round(total, 4)}, "".join(out)


def mostrar(met, texto=None):
    if "error" in met:
        print(f"  [{met['etiqueta']}] ERROR {met['error']}")
    else:
        print("  [{etiqueta}] {segundos}s ${usd} entrada={entrada} (acierto {acierto} / fallo {fallo}) "
              "salida={salida} razon={razonamiento} fin={fin}  gasto_total=${gasto_total}".format(**met))
        if texto is not None:
            print("    ->", repr(texto[:160]))


# ---------------------------------------------------------------- imágenes
def b64(ruta):
    with open(ruta, "rb") as f:
        return base64.b64encode(f.read()).decode()


def mime(ruta):
    return "image/jpeg" if ruta.lower().endswith((".jpg", ".jpeg")) else "image/png"


def parte_imagen(ruta, detalle=None):
    iu = {"url": f"data:{mime(ruta)};base64,{b64(ruta)}"}
    if detalle:
        iu["detail"] = detalle
    return {"type": "image_url", "image_url": iu}


def paginas(carpeta):
    """Archivos pagina-N.png|jpg de una carpeta, en orden numérico."""
    ps = [n for n in os.listdir(carpeta) if n.startswith("pagina-")]
    ps.sort(key=lambda n: int(n.split("-")[1].split(".")[0]))
    return [os.path.join(carpeta, n) for n in ps]


def mensaje_imagenes(texto, rutas, detalle=None):
    """Mensaje de usuario: la parte de texto primero y las páginas después, en orden."""
    return {"role": "user", "content": [{"type": "text", "text": texto}] + [parte_imagen(r, detalle) for r in rutas]}


# ---------------------------------------------------------------- subcomandos
def cmd_modelos(_):
    r = urllib.request.Request(h.BASE + "/models", headers={"Authorization": "Bearer " + h.llave()})
    try:
        with urllib.request.urlopen(r, timeout=60) as resp:
            print(limpio(resp.read().decode())[:2000])
    except urllib.error.HTTPError as e:
        print("HTTP", e.code, limpio(e.read().decode(errors="replace"))[:500])


def imagen_sonda():
    """PNG minúsculo con un código legible, para ver si el modelo 've'. Se crea con Pillow."""
    from PIL import Image, ImageDraw, ImageFont
    os.makedirs(SAL, exist_ok=True)
    ruta = os.path.join(SAL, "sonda.png")
    img = Image.new("L", (160, 56), 255)
    ImageDraw.Draw(img).text((12, 12), "ADELA 7391", fill=0, font=ImageFont.load_default(size=26))
    img.save(ruta)
    return ruta


def cmd_sonda(_):
    ruta = imagen_sonda()
    jpg = ruta[:-4] + ".jpg"
    from PIL import Image
    Image.open(ruta).convert("L").save(jpg, quality=85)
    texto = "Lee el texto de la imagen y responde solo con ese texto."
    uri = lambda r: f"data:{mime(r)};base64,{b64(r)}"
    T = {"type": "text", "text": texto}
    variantes = {
        "A image_url data-uri png": [T, {"type": "image_url", "image_url": {"url": uri(ruta)}}],
        "B detail=low": [T, {"type": "image_url", "image_url": {"url": uri(ruta), "detail": "low"}}],
        "C jpeg": [T, {"type": "image_url", "image_url": {"url": uri(jpg)}}],
        "D image_url como cadena": [T, {"type": "image_url", "image_url": uri(ruta)}],
        "E tipo image (base64)": [T, {"type": "image", "source": {"type": "base64", "media_type": "image/png",
                                                                    "data": b64(ruta)}}],
        "F input_image": [T, {"type": "input_image", "image_url": uri(ruta)}],
    }
    res, ok = {}, False
    for n, partes in variantes.items():
        # max_tokens incluye el razonamiento: con un tope bajo el contenido llega vacío (fin=length).
        met, txt = llamar("sonda " + n, [{"role": "user", "content": partes}], effort="low", max_tokens=400)
        mostrar(met, txt)
        res[n] = {"metricas": met, "texto": txt}
        ok = ok or "error" not in met
    if ok:
        base = variantes["A image_url data-uri png"]
        T2 = {"type": "text", "text": 'Lee el texto de la imagen y responde en JSON: {"texto": "..."}'}
        casos = {
            "G json_object + effort=high": dict(effort="high", json_mode=True, max_tokens=800),
            "H stream": dict(effort="low", stream=True, max_tokens=400),
            "I stream + json_object + effort": dict(effort="high", json_mode=True, stream=True, max_tokens=800),
        }
        for n, kw in casos.items():
            partes = [T2] + base[1:] if "json" in n else base
            met, txt = llamar("sonda " + n, [{"role": "user", "content": partes}], **kw)
            mostrar(met, txt)
            res[n] = {"metricas": met, "texto": txt}
        met, txt = llamar("sonda J con system", [{"role": "system", "content": "Eres un lector de imágenes."},
                                                  {"role": "user", "content": base}], effort="low", max_tokens=400)
        mostrar(met, txt)
        res["J con system"] = {"metricas": met, "texto": txt}
    with open(os.path.join(SAL, "sonda.json"), "w", encoding="utf-8") as f:
        json.dump(res, f, ensure_ascii=False, indent=1)


def dims(ruta):
    from PIL import Image
    return Image.open(ruta).size


def cmd_tokens(a):
    """Tokens que suma cada página: prompt_tokens con imágenes menos prompt_tokens del mismo texto sin ellas."""
    p = "Responde solo con la palabra OK."
    kw = dict(effort="low", max_tokens=300)
    met, _ = llamar("tokens base (sin imagen)", [{"role": "user", "content": p}], **kw)
    mostrar(met)
    n0, filas = met["entrada"], []
    for d in a.carpetas:
        rutas = paginas(d)
        etiqueta = os.path.basename(os.path.normpath(d))
        conjuntos = [(f"{etiqueta} ({len(rutas)} págs)", rutas)]
        if a.por_pagina:
            conjuntos += [(f"{etiqueta} pág {i + 1}", [r]) for i, r in enumerate(rutas)]
        for nombre, rs in conjuntos:
            m, _ = llamar("tokens " + nombre, [mensaje_imagenes(p, rs)], **kw)
            mostrar(m)
            if "error" in m:
                continue
            extra = m["entrada"] - n0
            filas.append({"conjunto": nombre, "paginas": len(rs), "bytes": sum(os.path.getsize(r) for r in rs),
                          "px": [list(dims(r)) for r in rs], "tokens_extra": extra,
                          "tokens_por_pagina": round(extra / len(rs), 1)})
    print("\nbase sin imagen:", n0, "tokens")
    for f in filas:
        print(f"  {f['conjunto']:<28} +{f['tokens_extra']:>5} tokens  ({f['tokens_por_pagina']}/pág)  "
              f"{f['bytes'] // 1024} KB  px={f['px'][0]}")
    with open(os.path.join(SAL, "tokens.json"), "w", encoding="utf-8") as f:
        json.dump({"base": n0, "filas": filas}, f, ensure_ascii=False, indent=1)


def cmd_cache(a):
    """Misma petición dos veces y luego con un turno añadido (como una corrección): ¿se cachea el prefijo con imágenes?"""
    rutas = paginas(a.carpeta)
    nonce = f"[{int(time.time())}] "  # al inicio: ningún prefijo anterior coincide
    texto = nonce + "Responde solo con la palabra OK."
    conv = [mensaje_imagenes(texto, rutas)]
    e1, e2, e3 = a.esfuerzos.split(",")
    m1, t1 = llamar(f"cache 1 (nueva, {e1})", conv, effort=e1, max_tokens=300)
    mostrar(m1)
    time.sleep(a.espera)
    m2, _ = llamar(f"cache 2 (idéntica, {e2})", conv, effort=e2, max_tokens=300)
    mostrar(m2)
    time.sleep(a.espera)
    m3, _ = llamar(f"cache 3 (mismo prefijo + turno, {e3})",
                   conv + [{"role": "assistant", "content": t1 or "OK"},
                           {"role": "user", "content": "Ahora responde solo con la palabra LISTO."}],
                   effort=e3, max_tokens=300)
    mostrar(m3)
    with open(os.path.join(SAL, "cache.json"), "w", encoding="utf-8") as f:
        json.dump([m1, m2, m3], f, ensure_ascii=False, indent=1)


# ---------------------------------------------------------------- comparación contra una referencia
def nz(s):
    return re.sub(r"\s+", " ", str(s or "")).strip().casefold()


def sim(a, b):
    import difflib
    return difflib.SequenceMatcher(None, nz(a), nz(b)).ratio()


def comparar(c, r):
    """c = candidato (cuestionario), r = referencia. Texto de ítems y opciones, clave, estilos e interpretación."""
    pc, pr = {p["orden"]: p for p in c["preguntas"]}, {p["orden"]: p for p in r["preguntas"]}
    s_item, s_op, dif, n_op_ok, formato_ok = [], [], [], 0, 0
    for k, p in pr.items():
        q = pc.get(k)
        if not q:
            continue
        s_item.append(sim(q["pregunta"], p["pregunta"]))
        if s_item[-1] < 0.9:
            dif.append((k, None, q["pregunta"], p["pregunta"]))
        n_op_ok += len(q["opciones"]) == len(p["opciones"])
        formato_ok += all(q.get(x) == p.get(x) for x in ("formato", "obligatoria", "minSelecciones", "maxSelecciones"))
        po = {o["orden"]: o for o in p["opciones"]}
        for o in q["opciones"]:
            if o["orden"] in po:
                s_op.append(sim(o["respuesta"], po[o["orden"]]["respuesta"]))
                if s_op[-1] < 0.9:
                    dif.append((k, o["orden"], o["respuesta"], po[o["orden"]]["respuesta"]))
    ok, total = h.pesos_ok(r, c)
    ic, ir = c["interpretacion"], r["interpretacion"]
    media = lambda v: round(sum(v) / len(v), 4) if v else None
    return {
        "items": [len(c["preguntas"]), len(r["preguntas"])],
        "sim_items": {"media": media(s_item), "min": round(min(s_item), 3) if s_item else None,
                      "exactos(>=0.99)": sum(x >= 0.99 for x in s_item)},
        "sim_opciones": {"media": media(s_op), "min": round(min(s_op), 3) if s_op else None,
                         "n": len(s_op), "exactas(>=0.99)": sum(x >= 0.99 for x in s_op)},
        "items_con_mismo_n_opciones": f"{n_op_ok}/{len(pr)}",
        "items_con_mismo_formato": f"{formato_ok}/{len(pr)}",
        "pesos_iguales": f"{ok}/{total} (candidato tiene {len(h.clave(c))})",
        "estilos": [sorted(e["nombre"] for e in c["estilos"]), sorted(e["nombre"] for e in r["estilos"])],
        "interpretacion": {"esquema": [ic.get("esquema"), ir.get("esquema")],
                           "escalones_iguales": (ic.get("escalones") or []) == (ir.get("escalones") or []),
                           "bandas_iguales": h.bandas_set(ic.get("bandas") or []) == h.bandas_set(ir.get("bandas") or []),
                           "complementaria": [bool(ic.get("complementaria")), bool(ir.get("complementaria"))]},
        "meta": {k: [c.get(k), r.get(k)] for k in ("nombre", "siglas", "autor", "version")},
        "textos_distintos(<0.9)": dif[:25], "n_textos_distintos": len(dif),
    }


def analizar(crudo, caso_chequeos=None):
    """Como eval.correr: parseo, expansión de la convención, validador y chequeos del caso (si hay)."""
    res = {}
    try:
        salida = json.loads(crudo)
        c, an = salida["cuestionario"], salida["analisis"]
        an["_crudo"] = crudo
        an["_crudo_escalones"] = list(c["interpretacion"].get("escalones") or [])
        pl = c["interpretacion"].get("plano") or {}
        an["_crudo_cortes"] = [pl.get("corteX"), pl.get("corteY")]
        la = h.expandir(c, dict(an.get("lectura") or {}, modeloAsistente=an.get("modeloAsistente")))
        res["errores_validador"] = h.validar(c)
        res["chequeos"] = caso_chequeos(c, an, la) if caso_chequeos else {}
        res["analisis"] = {k: an.get(k) for k in ("reconocido", "modeloAsistente", "lectura", "origen", "faltantes",
                                                 "noSoportado", "revision", "preguntasAlProfesor")}
        return c, res
    except Exception as e:  # noqa: BLE001  (se informa y se sigue: el spike quiere ver el fallo)
        res["error"] = f"{type(e).__name__}: {e}"
        return None, res


def texto_documento(n, ya_texto=None, imagenes=None):
    """Mensaje DOCUMENTO/PROFESOR. Solo imágenes: nota de lo que llega. Mixto: texto con marcadores + nota."""
    if ya_texto is None:
        return f"(sin texto: el documento llega como {n} imágenes de páginas escaneadas, adjuntas a continuación en orden: páginas 1 a {n})"
    lista = ", ".join(str(i) for i in imagenes)
    return ya_texto + f"\n\n(las páginas {lista} no tienen texto: llegan como imágenes adjuntas a continuación, en ese orden)"


def cmd_generar(a):
    rutas = paginas(a.carpeta)
    n = len(rutas)
    caso = None
    if a.chequeos:
        todos, _ = h.cargar_casos()
        caso = todos.get(a.chequeos)
    doc = texto_documento(n)
    if a.documento:
        with open(a.documento, encoding="utf-8") as f:
            doc = texto_documento(n, f.read().rstrip(), a.imagenes)
    texto = "DOCUMENTO:\n" + doc + "\n\nPROFESOR:\n" + (a.profesor or "(sin instrucciones)")
    if a.etiquetar:  # un texto "Página k:" antes de cada imagen
        partes = [{"type": "text", "text": texto}]
        nums = a.imagenes or list(range(1, n + 1))
        for num, r in zip(nums, rutas):
            partes += [{"type": "text", "text": f"Página {num}:"}, parte_imagen(r)]
        msg = {"role": "user", "content": partes}
    else:
        msg = mensaje_imagenes(texto, rutas)
    excluir = tuple(caso["excluir"] if caso else ()) + tuple(a.excluir or ())
    conv = [{"role": "system", "content": h.sistema(excluir)}, msg]
    met, crudo = llamar(a.nombre, conv, effort=a.effort, json_mode=not a.sin_json, stream=True,
                        max_tokens=32000, reserva=0.08)
    mostrar(met)
    salida = {"caso": a.nombre, "carpeta": a.carpeta, "paginas": n, "metricas": met, "mensaje_usuario": texto}
    if "error" not in met:
        c, res = analizar(crudo, caso and caso["chequeos"])
        salida.update(res)
        print("  validador:", res.get("errores_validador") or ("OK" if "error" not in res else res["error"]))
        for k, v in (res.get("chequeos") or {}).items():
            print(f"  {k}: {v}")
        if c:
            refs = {}
            if a.ref:
                refs["catalogo " + a.ref] = h.ref(a.ref)
            if a.vs:
                with open(a.vs, encoding="utf-8") as f:
                    refs["corrida de texto"] = json.loads(json.load(f)["crudo"])["cuestionario"]
            salida["comparacion"] = {}
            for nombre, r in refs.items():
                cmp = comparar(c, r)
                salida["comparacion"][nombre] = cmp
                print(f"  == vs {nombre}")
                for k, v in cmp.items():
                    if k != "textos_distintos(<0.9)":
                        print(f"     {k}: {v}")
    salida["crudo"] = crudo
    os.makedirs(SAL, exist_ok=True)
    with open(os.path.join(SAL, a.nombre + ".json"), "w", encoding="utf-8") as f:
        json.dump(salida, f, ensure_ascii=False, indent=1)
    if a.turno and "error" not in met:  # una corrección típica: ¿el prefijo con imágenes se reutiliza?
        conv2 = conv + [{"role": "assistant", "content": crudo},
                        {"role": "user", "content": 'Devuelve solo {"patch":[]}'}]
        m2, t2 = llamar(a.nombre + " turno", conv2, effort=a.effort_turno, json_mode=True, stream=True, max_tokens=2000)
        mostrar(m2, t2)
        salida["turno"] = m2
        with open(os.path.join(SAL, a.nombre + ".json"), "w", encoding="utf-8") as f:
            json.dump(salida, f, ensure_ascii=False, indent=1)


def cmd_archivo(a):
    """¿Acepta la API un PDF entero como parte 'file' (el error de las partes lo lista como tipo válido)?"""
    datos = b64(a.pdf)
    T = {"type": "text", "text": "¿Qué dice el título del documento? Responde en una frase corta."}
    variantes = {
        "file con data-uri": {"type": "file", "file": {"filename": "doc.pdf", "file_data": "data:application/pdf;base64," + datos}},
        "file con base64 crudo": {"type": "file", "file": {"filename": "doc.pdf", "file_data": datos}},
    }
    res = {}
    for n, parte in variantes.items():
        met, txt = llamar("archivo " + n, [{"role": "user", "content": [T, parte]}], effort="low", max_tokens=500)
        mostrar(met, txt)
        res[n] = {"metricas": met, "texto": txt}
    with open(os.path.join(SAL, "archivo.json"), "w", encoding="utf-8") as f:
        json.dump(res, f, ensure_ascii=False, indent=1)


def cmd_gasto(_):
    print(f"gastado ${gastado():.4f} de ${TOPE_USD}")


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    for n, f in (("modelos", cmd_modelos), ("sonda", cmd_sonda), ("gasto", cmd_gasto)):
        sub.add_parser(n).set_defaults(f=f)
    ar = sub.add_parser("archivo")
    ar.add_argument("pdf")
    ar.set_defaults(f=cmd_archivo)
    t = sub.add_parser("tokens")
    t.add_argument("carpetas", nargs="+")
    t.add_argument("--por-pagina", action="store_true")
    t.set_defaults(f=cmd_tokens)
    c = sub.add_parser("cache")
    c.add_argument("carpeta")
    c.add_argument("--espera", type=float, default=5)
    c.add_argument("--esfuerzos", default="low,low,low", help="esfuerzo de las llamadas 1,2,3 (low|high)")
    c.set_defaults(f=cmd_cache)
    g = sub.add_parser("generar")
    g.add_argument("nombre")
    g.add_argument("carpeta", help="carpeta con pagina-N.png|jpg")
    g.add_argument("--effort", default="high")
    g.add_argument("--sin-json", action="store_true", help="sin response_format json_object")
    g.add_argument("--profesor", help="texto de PROFESOR (por defecto, sin instrucciones)")
    g.add_argument("--documento", help="archivo con el texto de las páginas que sí lo tienen (PDF mixto)")
    g.add_argument("--imagenes", type=lambda s: [int(x) for x in s.split(",")],
                   help="números de página de las imágenes (PDF mixto), p. ej. 2,3")
    g.add_argument("--etiquetar", action="store_true", help="un texto 'Página k:' antes de cada imagen")
    g.add_argument("--chequeos", help="caso de eval.py cuyos chequeos y exclusiones se aplican")
    g.add_argument("--ref", help="id del catálogo local contra el que se compara")
    g.add_argument("--excluir", nargs="*", help="ids del catálogo que se quitan del prefijo (leer sin el instrumento en el catálogo)")
    g.add_argument("--vs", help="salida de eval.py (salidas/<caso>.json) de la corrida con texto")
    g.add_argument("--turno", action="store_true", help="tras generar, un turno de corrección vacío (mide la caché)")
    g.add_argument("--effort-turno", default="low", help="esfuerzo del turno (la caché solo se reutiliza con el mismo)")
    g.set_defaults(f=cmd_generar)
    a = ap.parse_args()
    a.f(a)


if __name__ == "__main__":
    main()
