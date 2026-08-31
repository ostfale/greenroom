package de.ostfale.greenroom.application.port.out;

/**
 * Shrinks a picture before it is stored. A portrait is shown at a few hundred pixels; the
 * eight megapixels a phone produces would only sit in the database and travel the wire.
 */
public interface ScaleImages {

    /**
     * The picture as JPEG, no longer than {@code maxEdge} on its longer side. A picture
     * that is already smaller keeps its size — it is only re-encoded.
     *
     * @throws IllegalArgumentException if the bytes are not a picture we can read
     */
    byte[] toJpegAtMost(byte[] data, int maxEdge);
}
