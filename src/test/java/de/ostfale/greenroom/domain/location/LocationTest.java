package de.ostfale.greenroom.domain.location;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain Java, no Spring: that is the point of keeping the rules in the records. */
class LocationTest {

    private static final ContactPerson HOST = ContactPerson.of("Max Muster", "max@example.org");

    @Test
    void aLocationNeedsAName() {
        assertThatThrownBy(() -> Location.of("  ", HOST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void aLocationNeedsSomebodyToAsk() {
        assertThatThrownBy(() -> new Location(null, "Musterfirma GmbH", null, null, null, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contact person");

        assertThatThrownBy(() -> new Location(null, "Musterfirma GmbH", null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contact person");
    }

    @Test
    void theContactPersonCannotBeTakenAwayAgain() {
        assertThatThrownBy(() -> Location.of("Musterfirma GmbH", HOST).withContacts(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contact person");
    }

    @Test
    void aContactPersonNeedsANameAndAnAddress() {
        assertThatThrownBy(() -> ContactPerson.of(" ", "max@example.org"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");

        assertThatThrownBy(() -> ContactPerson.of("Max Muster", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void theAddressMayStillBeMissing() {
        Location location = Location.of("Musterfirma GmbH", HOST);

        assertThat(location.street()).isNull();
        assertThat(location.city()).isNull();
        assertThat(location.addressLine()).isEmpty();
    }

    @Test
    void theAddressLineLeavesOutWhatIsNotKnownYet() {
        Location location = Location.of("Musterfirma GmbH", HOST);

        assertThat(location.withAddress("Musterweg 1", "22179", "Hamburg").addressLine())
                .isEqualTo("Musterweg 1, 22179 Hamburg");
        assertThat(location.withAddress(null, null, "Hamburg").addressLine())
                .isEqualTo("Hamburg");
        assertThat(location.withAddress("Musterweg 1", null, null).addressLine())
                .isEqualTo("Musterweg 1");
    }

    @Test
    void aCapacityIsANumberOfSeatsOrNothingAtAll() {
        Location location = Location.of("Musterfirma GmbH", HOST);

        assertThat(location.withCapacity(null).capacity()).isNull();
        assertThat(location.withCapacity(80).capacity()).isEqualTo(80);
        assertThatThrownBy(() -> location.withCapacity(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacity");
    }

    @Test
    void blankOptionalFieldsBecomeNull() {
        Location location = Location.of("Musterfirma GmbH", HOST)
                .withAddress("  ", "", null)
                .withNotes(" ");

        assertThat(location.street()).isNull();
        assertThat(location.postalCode()).isNull();
        assertThat(location.notes()).isNull();
    }

    @Test
    void surroundingWhitespaceIsStripped() {
        Location location = Location.of("  Musterfirma GmbH ", HOST)
                .withAddress(" Musterweg 1 ", null, " Hamburg ");

        assertThat(location.name()).isEqualTo("Musterfirma GmbH");
        assertThat(location.street()).isEqualTo("Musterweg 1");
        assertThat(location.city()).isEqualTo("Hamburg");
    }

    @Test
    void contactsAreNeverSharedWithTheCaller() {
        List<ContactPerson> mutable = new ArrayList<>(List.of(HOST));
        Location location = Location.of("Musterfirma GmbH", HOST).withContacts(mutable);
        mutable.clear();

        assertThat(location.contacts()).containsExactly(HOST);
    }
}
