package de.ostfale.greenroom.adapter.out.image;

import de.ostfale.greenroom.application.port.out.ScaleImages;
import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Scaling with what the JDK already has. No library: one user uploads a handful of
 * portraits a year.
 */
@Component
public class ImageIoScaler implements ScaleImages {

    private static final Logger log = LoggerFactory.getLogger(ImageIoScaler.class);

    private static final float QUALITY = 0.85f;

    @Override
    public byte[] toJpegAtMost(byte[] data, int maxEdge) {
        BufferedImage source = read(data);
        BufferedImage scaled = draw(source, maxEdge);
        return encode(scaled);
    }

    private static BufferedImage read(byte[] data) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) {
                throw new RuleViolated(Rule.PHOTO_NOT_A_KIND_WE_SHOW);
            }
            return image;
        } catch (IOException e) {
            // The page only says which kinds we show; why these bytes were unreadable is
            // of no use to it, and of every use in the log.
            log.debug("ImageIoScaler :: the bytes could not be read as a picture", e);
            throw new RuleViolated(Rule.PHOTO_NOT_A_KIND_WE_SHOW);
        }
    }

    /**
     * Onto white, not onto nothing: a PNG with a transparent background would otherwise
     * turn black as a JPEG.
     */
    private static BufferedImage draw(BufferedImage source, int maxEdge) {
        int longer = Math.max(source.getWidth(), source.getHeight());
        double factor = longer <= maxEdge ? 1.0 : (double) maxEdge / longer;
        int width = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(source.getHeight() * factor));

        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D canvas = target.createGraphics();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            canvas.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            canvas.setColor(Color.WHITE);
            canvas.fillRect(0, 0, width, height);
            canvas.drawImage(source, 0, 0, width, height, null);
        } finally {
            canvas.dispose();
        }
        return target;
    }

    private static byte[] encode(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("ImageIoScaler :: this JVM cannot write JPEG");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(QUALITY);
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
