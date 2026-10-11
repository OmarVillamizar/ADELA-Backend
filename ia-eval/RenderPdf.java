import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Renderiza un PDF a imágenes en grises igual que ExtractorDocumento (CropBox, tope de 16 MP).
 * Uso: java -cp <jars de pdfbox> RenderPdf.java <pdf> <carpeta> [dpi=144] [png|jpg=png] [calidad jpg=0.8]
 * Escribe pagina-N.png|jpg e imprime por página: número, caracteres de texto y bytes del archivo.
 */
public class RenderPdf {
    private static final double MAX_PIXELES = 16_000_000d;

    public static void main(String[] args) throws IOException {
        Path pdf = Path.of(args[0]);
        Path dir = Path.of(args[1]);
        float dpiBase = args.length > 2 ? Float.parseFloat(args[2]) : 144f;
        String fmt = args.length > 3 ? args[3] : "png";
        float calidad = args.length > 4 ? Float.parseFloat(args[4]) : 0.8f;
        Files.createDirectories(dir);
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PDFRenderer r = new PDFRenderer(doc);
            PDFTextStripper ts = new PDFTextStripper();
            ts.setSortByPosition(true);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                ts.setStartPage(i + 1);
                ts.setEndPage(i + 1);
                int chars = ts.getText(doc).strip().replaceAll("\\s+", " ").length();
                PDRectangle caja = doc.getPage(i).getCropBox();
                float dpi = dpiBase;
                double px = (caja.getWidth() * dpi / 72d) * (caja.getHeight() * dpi / 72d);
                if (px > MAX_PIXELES) {
                    dpi = (float) (dpi * Math.sqrt(MAX_PIXELES / px));
                }
                BufferedImage img = r.renderImageWithDPI(i, dpi, ImageType.GRAY);
                File out = dir.resolve("pagina-" + (i + 1) + "." + fmt).toFile();
                if (fmt.equals("png")) {
                    ImageIO.write(img, "png", out);
                } else {
                    ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
                    ImageWriteParam p = w.getDefaultWriteParam();
                    p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    p.setCompressionQuality(calidad);
                    try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                        w.setOutput(ios);
                        w.write(null, new IIOImage(img, null, null), p);
                    }
                    w.dispose();
                }
                System.out.println((i + 1) + "\t" + chars + "\t" + img.getWidth() + "x" + img.getHeight() + "\t" + out.length());
            }
        }
    }
}
