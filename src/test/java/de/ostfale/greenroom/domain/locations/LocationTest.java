package de.ostfale.greenroom.domain.locations;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static de.ostfale.greenroom.Fixtures.aContact;
import static de.ostfale.greenroom.Fixtures.aLocation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain Java, no Spring: that is the point of keeping the rules in the records. */
class LocationTest {

    @Test
    void aLocationNeedsAName() {
        assertThatThrownBy(() -> Location.of("  ", aContact()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void aLocationNeedsSomebodyToAsk() {
        assertThatThrownBy(() -> new Location(null, "Musterfirma GmbH", null, true, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contact person");

        assertThatThrownBy(() -> new Location(null, "Musterfirma GmbH", null, true, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contact person");
    }

    @Test
    void theContactPersonCannotBeTakenAwayAgain() {
        assertThatThrownBy(() -> aLocation().withContacts(List.of()))
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
        Location location = aLocation();

        assertThat(location.addresses()).isEmpty();
        assertThat(location.currentAddress()).isNull();
        assertThat(location.addressLine()).isEmpty();
    }

    @Test
    void theAddressLineLeavesOutWhatIsNotKnownYet() {
        Location location = aLocation();

        assertThat(location.withAddress("Musterweg 1", "22179", "Hamburg").addressLine())
                .isEqualTo("Musterweg 1, 22179 Hamburg");
        assertThat(location.withAddress(null, null, "Hamburg").addressLine())
                .isEqualTo("Hamburg");
        assertThat(location.withAddress("Musterweg 1", null, null).addressLine())
                .isEqualTo("Musterweg 1");
    }

    @Test
    void theSeatsBelongToTheAddress() {
        Location location = aLocation();

        assertThat(location.currentCapacity()).isNull();
        assertThat(location.withAddress("Musterweg 1", "22179", "Hamburg").currentCapacity()).isNull();
        assertThat(location.movedTo(Address.at("Musterweg 1", "22179", "Hamburg").withCapacity(80))
                .currentCapacity()).isEqualTo(80);
    }

    @Test
    void aCapacityIsANumberOfSeatsOrNothingAtAll() {
        Address address = Address.at("Musterweg 1", "22179", "Hamburg");

        assertThat(address.withCapacity(null).capacity()).isNull();
        assertThat(address.withCapacity(80).capacity()).isEqualTo(80);
        assertThatThrownBy(() -> address.withCapacity(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacity");
    }

    @Test
    void aMoveCanBringADifferentNumberOfSeats() {
        Location location = Location.of("Kuehne + Nagel", aContact())
                .movedTo(Address.at("Grosser Grasbrook 11", "20457", "Hamburg").withCapacity(40))
                .movedTo(Address.at("Neuer Weg 2", "20095", "Hamburg").withCapacity(120));

        assertThat(location.currentCapacity()).isEqualTo(120);
        assertThat(location.addresses().getFirst().capacity()).isEqualTo(40);
    }

    @Test
    void blankOptionalFieldsBecomeNull() {
        Location location = aLocation()
                .withAddress("  ", "", "Hamburg")
                .withNotes(" ");

        assertThat(location.currentAddress().street()).isNull();
        assertThat(location.currentAddress().postalCode()).isNull();
        assertThat(location.notes()).isNull();
    }

    @Test
    void surroundingWhitespaceIsStripped() {
        Location location = Location.of("  Musterfirma GmbH ", aContact())
                .withAddress(" Musterweg 1 ", null, " Hamburg ");

        assertThat(location.name()).isEqualTo("Musterfirma GmbH");
        assertThat(location.currentAddress().street()).isEqualTo("Musterweg 1");
        assertThat(location.currentAddress().city()).isEqualTo("Hamburg");
    }

    // --- a place that changed over the years -----------------------------------------

    @Test
    void anAddressNeedsAStreetOrATown() {
        assertThatThrownBy(() -> Address.at("  ", "", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("street or a town");
    }

    @Test
    void movingRetiresTheEarlierAddressInsteadOfDeletingIt() {
        Location location = Location.of("Kuehne + Nagel", aContact())
                .withAddress("Grosser Grasbrook 11", "20457", "Hamburg")
                .movedTo(Address.at("Neuer Weg 2", "20095", "Hamburg"));

        assertThat(location.addresses()).hasSize(2);
        assertThat(location.addresses().getFirst().active()).isFalse();
        assertThat(location.addresses().getFirst().line()).isEqualTo("Grosser Grasbrook 11, 20457 Hamburg");
        assertThat(location.currentAddress().line()).isEqualTo("Neuer Weg 2, 20095 Hamburg");
        assertThat(location.activeAddresses()).hasSize(1);
    }

    @Test
    void aSecondSiteDoesNotRetireTheFirst() {
        Location location = aLocation()
                .withAddress("Musterweg 1", "22179", "Hamburg")
                .withAdditionalAddress(Address.at("Zweigweg 5", "21073", "Hamburg"));

        assertThat(location.activeAddresses()).hasSize(2);
        assertThat(location.addressLine()).isEqualTo("Musterweg 1, 22179 Hamburg");
    }

    @Test
    void anAddressCanBeSwitchedOffAndOnAgain() {
        Location location = aLocation()
                .withAddress("Musterweg 1", "22179", "Hamburg");

        Location quiet = location.withAddressActive(0, false);
        assertThat(quiet.activeAddresses()).isEmpty();
        assertThat(quiet.addressLine()).isEmpty();
        assertThat(quiet.addresses()).hasSize(1);

        assertThat(quiet.withAddressActive(0, true).activeAddresses()).hasSize(1);
    }

    @Test
    void thereIsNoAddressAtAPositionThatDoesNotExist() {
        Location location = aLocation();

        assertThatThrownBy(() -> location.withAddressActive(0, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("position");
    }

    // --- keeping the contacts up to date ---------------------------------------------

    @Test
    void aContactCanBeAddedAndChanged() {
        Location location = aLocation()
                .withAdditionalContact(ContactPerson.of("Anna Albers", "anna@example.org"));

        assertThat(location.contacts()).extracting(ContactPerson::name)
                .containsExactly("Max Muster", "Anna Albers");

        Location changed = location.withContactChanged(1,
                new ContactPerson("Anna Albers", "anna@nordsee.example", "040 123456"));
        assertThat(changed.contacts().getLast().email()).isEqualTo("anna@nordsee.example");
        assertThat(changed.contacts().getLast().phone()).isEqualTo("040 123456");
    }

    @Test
    void aContactCanBeRemovedAsLongAsOneIsLeft() {
        Location location = aLocation()
                .withAdditionalContact(ContactPerson.of("Anna Albers", "anna@example.org"));

        assertThat(location.withContactRemoved(0).contacts())
                .extracting(ContactPerson::name)
                .containsExactly("Anna Albers");
    }

    @Test
    void theLastContactCannotBeRemoved() {
        Location location = aLocation();

        assertThatThrownBy(() -> location.withContactRemoved(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one contact person");
    }

    @Test
    void thereIsNoContactAtAPositionThatDoesNotExist() {
        Location location = aLocation();

        assertThatThrownBy(() -> location.withContactRemoved(3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("position");
        assertThatThrownBy(() -> location.withContactChanged(3, aContact()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("position");
    }

    @Test
    void contactsAreNeverSharedWithTheCaller() {
        List<ContactPerson> mutable = new ArrayList<>(List.of(aContact()));
        Location location = aLocation().withContacts(mutable);
        mutable.clear();

        assertThat(location.contacts()).containsExactly(aContact());
    }

    /**
     * Not the same flag as on the address: that one says where they are now, this one
     * whether we still go there at all.
     */
    @Test
    void aNewLocationIsOneWeStillUse() {
        assertThat(aLocation().inUse()).isTrue();
    }

    @Test
    void aPlaceWeGaveUpKeepsItsAddressesAndItsContacts() {
        Location moved = aLocation().withAddress("Musterweg 1", "22179", "Hamburg");

        Location given = moved.withInUse(false);

        assertThat(given.inUse()).isFalse();
        assertThat(given.addressLine()).isEqualTo(moved.addressLine());
        assertThat(given.activeAddresses()).hasSize(1);
        assertThat(given.contacts()).isEqualTo(moved.contacts());
    }

    /** Giving a place up and taking one of its addresses out of service are two things. */
    @Test
    void theTwoFlagsDoNotTouchEachOther() {
        Location closed = aLocation().withAddress("Musterweg 1", "22179", "Hamburg")
                .withAddressActive(0, false);

        assertThat(closed.inUse()).isTrue();
        assertThat(closed.activeAddresses()).isEmpty();
        assertThat(closed.withInUse(false).addresses()).hasSize(1);
    }

    @Test
    void aPlaceCanBeTakenBack() {
        assertThat(aLocation().withInUse(false).withInUse(true).inUse()).isTrue();
    }

    /** A point or no point — a latitude without a longitude is neither. */
    @Test
    void halfAPositionIsNoPosition() {
        assertThatThrownBy(() -> new Address("Musterweg 1", null, null, null, true, 53.55, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("half a position");
        assertThatThrownBy(() -> new Address("Musterweg 1", null, null, null, true, null, 9.99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("half a position");
    }

    @Test
    void aPositionIsOnThisPlanet() {
        assertThatThrownBy(() -> Address.at("Musterweg 1", null, "Hamburg").at(91.0, 9.99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("point on this planet");
        assertThatThrownBy(() -> Address.at("Musterweg 1", null, "Hamburg").at(53.55, 181.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("point on this planet");
    }

    @Test
    void anAddressIsWrittenDownBeforeAnybodyKnowsWhereItIs() {
        Address written = Address.at("Musterweg 1", "22179", "Hamburg");

        assertThat(written.isLocated()).isFalse();
        assertThat(written.at(53.5511, 9.9937).isLocated()).isTrue();
    }

    /** The point belongs to the address, so it survives everything else about it. */
    @Test
    void thePositionOutlivesTheOtherChanges() {
        Address placed = Address.at("Musterweg 1", "22179", "Hamburg").at(53.5511, 9.9937);

        assertThat(placed.withCapacity(60).latitude()).isEqualTo(53.5511);
        assertThat(placed.deactivated().longitude()).isEqualTo(9.9937);
        assertThat(placed.deactivated().activated().isLocated()).isTrue();
    }
}
