package de.ostfale.greenroom.domain.speakers;

import de.ostfale.greenroom.domain.Rule;
import org.junit.jupiter.api.Test;

import static de.ostfale.greenroom.Violations.ruleBrokenBy;
import static org.assertj.core.api.Assertions.assertThat;

/** Plain Java, no Spring. */
class SpeakerPhotoTest {

    private static final byte[] PIXEL = {1, 2, 3, 4};

    @Test
    void aPhotoBelongsToAStoredSpeaker() {
        assertThat(ruleBrokenBy(() -> SpeakerPhoto.of(null, "image/png", PIXEL)))
                .isEqualTo(Rule.PHOTO_NEEDS_A_STORED_SPEAKER);
    }

    @Test
    void anEmptyFileIsNoPicture() {
        assertThat(ruleBrokenBy(() -> SpeakerPhoto.of(1L, "image/png", new byte[0])))
                .isEqualTo(Rule.PHOTO_IS_EMPTY);

        assertThat(ruleBrokenBy(() -> SpeakerPhoto.of(1L, "image/png", null)))
                .isEqualTo(Rule.PHOTO_IS_EMPTY);
    }

    @Test
    void onlyWhatABrowserShowsWithoutHelpIsAccepted() {
        assertThat(SpeakerPhoto.of(1L, "image/jpeg", PIXEL).contentType()).isEqualTo("image/jpeg");
        assertThat(SpeakerPhoto.of(1L, "IMAGE/PNG", PIXEL).contentType()).isEqualTo("image/png");

        assertThat(ruleBrokenBy(() -> SpeakerPhoto.of(1L, "application/pdf", PIXEL)))
                .isEqualTo(Rule.PHOTO_NOT_A_KIND_WE_SHOW);

        assertThat(ruleBrokenBy(() -> SpeakerPhoto.of(1L, null, PIXEL)))
                .isEqualTo(Rule.PHOTO_NOT_A_KIND_WE_SHOW);
    }

    @Test
    void twoMegabytesIsTheLimit() {
        assertThat(SpeakerPhoto.of(1L, "image/png", new byte[SpeakerPhoto.MAX_BYTES]).size())
                .isEqualTo(SpeakerPhoto.MAX_BYTES);

        assertThat(ruleBrokenBy(() -> SpeakerPhoto.of(1L, "image/png", new byte[SpeakerPhoto.MAX_BYTES + 1])))
                .isEqualTo(Rule.PHOTO_TOO_LARGE);
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
