"""Fabrica un PDF "escaneado" (solo imágenes, sin capa de texto) a partir de un texto plano.

Sirve para probar la visión con documentos que no existen como escaneo: el texto se dibuja en páginas
A4, con un giro leve, desenfoque y ruido de papel, y se guarda como PDF de imágenes. Luego se pasa por
RenderPdf.java (el mismo render de ExtractorDocumento) para obtener las páginas que vería el modelo.

  python -I escaneado.py entrada.txt salida.pdf [--dpi 200] [--lineas 50] [--giro 0.4] [--ruido 7]

Con semilla fija (--semilla) el resultado se repite.
"""
import argparse, os, random, sys, textwrap

FUENTES = ("C:/Windows/Fonts/times.ttf", "C:/Windows/Fonts/arial.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf")


def fuente(px):
    from PIL import ImageFont
    for f in FUENTES:
        if os.path.isfile(f):
            return ImageFont.truetype(f, px)
    return ImageFont.load_default(size=px)


def paginas(texto, dpi, lineas, giro, ruido, semilla):
    import numpy as np
    from PIL import Image, ImageDraw, ImageFilter
    rnd, rng = random.Random(semilla), np.random.default_rng(semilla)
    ancho, alto = int(8.27 * dpi), int(11.69 * dpi)
    px = round(11 * dpi / 72)
    f, margen, paso = fuente(px), int(0.9 * dpi), round(px * 1.32)
    # ancho de línea en caracteres: lo que cabe entre márgenes con el ancho medio de la fuente
    cols = max(40, int((ancho - 2 * margen) / (f.getlength("abcdefghijklmnopqrstuvwxyz") / 26)))
    filas = []
    for parrafo in texto.replace("\r\n", "\n").split("\n"):
        filas += textwrap.wrap(parrafo, cols, subsequent_indent="    ") or [""]
    paginas = []
    for i in range(0, len(filas), lineas):
        img = Image.new("L", (ancho, alto), 250)
        d = ImageDraw.Draw(img)
        for k, linea in enumerate(filas[i:i + lineas]):
            d.text((margen + rnd.randint(-1, 1), margen + k * paso), linea, fill=15, font=f)
        d.text((ancho // 2, alto - margen // 2), str(len(paginas) + 1), fill=60, font=f, anchor="mm")
        img = img.rotate(rnd.uniform(-giro, giro), resample=Image.BICUBIC, fillcolor=250)
        img = img.filter(ImageFilter.GaussianBlur(0.7))
        a = np.asarray(img, dtype=np.float32) + rng.normal(0, ruido, (alto, ancho))
        paginas.append(Image.fromarray(np.clip(a, 0, 255).astype("uint8"), "L"))
    return paginas


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("entrada")
    ap.add_argument("salida")
    ap.add_argument("--dpi", type=int, default=200)
    ap.add_argument("--lineas", type=int, default=50, help="líneas por página")
    ap.add_argument("--giro", type=float, default=0.4, help="giro máximo en grados")
    ap.add_argument("--ruido", type=float, default=7, help="desviación del ruido gaussiano (0-255)")
    ap.add_argument("--semilla", type=int, default=1)
    a = ap.parse_args()
    with open(a.entrada, encoding="utf-8") as f:
        pgs = paginas(f.read(), a.dpi, a.lineas, a.giro, a.ruido, a.semilla)
    pgs[0].save(a.salida, "PDF", resolution=a.dpi, save_all=True, append_images=pgs[1:])
    print(f"{len(pgs)} páginas -> {a.salida}")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
