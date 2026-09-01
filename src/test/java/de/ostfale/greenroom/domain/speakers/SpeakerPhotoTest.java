package de.ostfale.greenroom.domain.speakers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain Java, no Spring. */
class SpeakerPhotoTest {

    private static final byte[] PIXEL = {1, 2, 3, 4};

    @Test
    void aPhotoBelongsToAStoredSpeaker() {
        assertThatThrownBy(() -> SpeakerPhoto.of(null, "image/png", PIXEL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stored speaker");
    }

    @Test
    void anEmptyFileIsNoPicture() {
        assertThatThrownBy(() -> SpeakerPhoto.of(1L, "image/png", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no picture");

        assertThatThrownBy(() -> SpeakerPhoto.of(1L, "image/png", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no picture");
    }

    @Test
    void onlyWhatABrowserShowsWithoutHelpIsAccepted() {
        assertThat(SpeakerPhoto.of(1L, "image/jpeg", PIXEL).contentType()).isEqualTo("image/jpeg");
        assertThat(SpeakerPhoto.of(1L, "IMAGE/PNG", PIXEL).contentType()).isEqualTo("image/png");

        assertThatThrownBy(() -> SpeakerPhoto.of(1L, "application/pdf", PIXEL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a picture");

        assertThatThrownBy(() -> SpeakerPhoto.of(1L, null, PIXEL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a picture");
    }

    @Test
    void twoMegabytesIsTheLimit() {
        assertThat(SpeakerPhoto.of(1L, "image/png", new byte[SpeakerPhoto.MAX_BYTES]).size())
                .isEqualTo(SpeakerPhoto.MAX_BYTES);

        assertThatThrownBy(() -> SpeakerPhoto.of(1L, "image/png", new byte[SpeakerPhoto.MAX_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void theBytesAreNeverSharedWithTheCaller() {
        byte[] mine = {1, 2, 3, 4};
        SpeakerPhoto photo = SpeakerPhoto.of(1L, "image/png", mine);

        mine[0] = 99;
        assertThat(photo.data()[0]).isEqualTo((byte) 1);

        photo.data()[0] = 99;
        assertThat(photo.data()[0]).isEqualTo((byte) 1);
    }
}
