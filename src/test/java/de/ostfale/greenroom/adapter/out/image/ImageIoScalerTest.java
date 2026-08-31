package de.ostfale.greenroom.adapter.out.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real ImageIO, no Spring — the bytes that come out are what matters. */
class ImageIoScalerTest {

    private final ImageIoScaler scaler = new ImageIoScaler();

    private static byte[] picture(int width, int height, String format) {
        BufferedImage image = new BufferedImage(width, height,
                "png".equals(format) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D canvas = image.createGraphics();
        canvas.setColor(Color.BLUE);
        canvas.fillRect(0, 0, width / 2, height);
        canvas.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static BufferedImage read(byte[] data) {
        try {
            return ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void aLargePictureIsShrunkToTheLongerEdge() {
        byte[] scaled = scaler.toJpegAtMost(picture(2000, 1000, "jpg"), 600);

        BufferedImage result = read(scaled);
        assertThat(result.getWidth()).isEqualTo(600);
        assertThat(result.getHeight()).isEqualTo(300);
    }

    @Test
    void aPortraitIsMeasuredByItsHeight() {
        BufferedImage result = read(scaler.toJpegAtMost(picture(600, 1200, "jpg"), 600));

        assertThat(result.getHeight()).isEqualTo(600);
        assertThat(result.getWidth()).isEqualTo(300);
    }

    @Test
    void aSmallPictureKeepsItsSize() {
        BufferedImage result = read(scaler.toJpegAtMost(picture(200, 150, "jpg"), 600));

        assertThat(result.getWidth()).isEqualTo(200);
        assertThat(result.getHeight()).isEqualTo(150);
    }

    @Test
    void shrinkingActuallySavesBytes() {
        byte[] original = picture(2000, 2000, "png");
        byte[] scaled = scaler.toJpegAtMost(original, 600);

        assertThat(scaled.length).isLessThan(original.length);
    }

    @Test
    void whatComesOutIsAlwaysJpeg() {
        byte[] scaled = scaler.toJpegAtMost(picture(100, 100, "png"), 600);

        // The JPEG magic number, so nobody has to trust a content type.
        assertThat(scaled[0]).isEqualTo((byte) 0xFF);
        assertThat(scaled[1]).isEqualTo((byte) 0xD8);
    }

    @Test
    void transparencyBecomesWhiteInsteadOfBlack() {
        BufferedImage transparent = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(transparent, "png", bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        BufferedImage result = read(scaler.toJpegAtMost(bytes.toByteArray(), 600));

        assertThat(new Color(result.getRGB(50, 50))).isEqualTo(Color.WHITE);
    }

    @Test
    void somethingThatIsNoPictureIsRefused() {
        assertThatThrownBy(() -> scaler.toJpegAtMost("keine Datei, nur Text".getBytes(), 600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a picture");
    }
}
