package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.TestcontainersConfiguration;
import de.ostfale.greenroom.domain.location.Address;
import de.ostfale.greenroom.domain.location.ContactPerson;
import de.ostfale.greenroom.domain.location.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Against a real Postgres — the mapping of the contact list is the part worth proving. */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class LocationRepositoryTest {

    private static final ContactPerson HOST = ContactPerson.of("Max Muster", "max@example.org");

    @Autowired
    private LocationRepository locations;

    @BeforeEach
    void emptyTheTable() {
        locations.deleteAll();
    }

    @Test
    void storesAndReadsBackALocation() {
        Location saved = locations.save(Location.of("Musterfirma GmbH", HOST)
                .movedTo(Address.at("Musterweg 1", "22179", "Hamburg").withCapacity(80))
                .withNotes("Parken im Hof, Beamer vorhanden."));

        assertThat(saved.id()).isNotNull();

        Location loaded = locations.findById(saved.id()).orElseThrow();
        assertThat(loaded.name()).isEqualTo("Musterfirma GmbH");
        assertThat(loaded.addressLine()).isEqualTo("Musterweg 1, 22179 Hamburg");
        assertThat(loaded.currentCapacity()).isEqualTo(80);
        assertThat(loaded.notes()).isEqualTo("Parken im Hof, Beamer vorhanden.");
    }

    @Test
    void keepsTheOrderOfTheContacts() {
        Location saved = locations.save(Location.of("Musterfirma GmbH", HOST).withContacts(List.of(
                new ContactPerson("Anna Albers", "anna@example.org", "040 123456"),
                ContactPerson.of("Zoe Zimmer", "zoe@example.org"))));

        Location loaded = locations.findById(saved.id()).orElseThrow();

        assertThat(loaded.contacts()).extracting(ContactPerson::name)
                .containsExactly("Anna Albers", "Zoe Zimmer");
        assertThat(loaded.contacts().getFirst().phone()).isEqualTo("040 123456");
        assertThat(loaded.contacts().getLast().phone()).isNull();
    }

    @Test
    void replacingTheContactsLeavesNoOrphansBehind() {
        Location saved = locations.save(Location.of("Musterfirma GmbH", HOST));

        Location updated = locations.save(saved.withContacts(
                List.of(ContactPerson.of("Anna Albers", "anna@example.org"))));

        assertThat(locations.findById(updated.id()).orElseThrow().contacts())
                .extracting(ContactPerson::name)
                .containsExactly("Anna Albers");
    }

    @Test
    void deletingALocationTakesTheContactsWithIt() {
        Location saved = locations.save(Location.of("Musterfirma GmbH", HOST));

        locations.deleteById(saved.id());

        assertThat(locations.findById(saved.id())).isEmpty();
    }

    @Test
    void keepsEveryAddressAPlaceEverHadAndRemembersWhichOneCountsNow() {
        Location saved = locations.save(Location.of("Kühne + Nagel", HOST)
                .withAddress("Großer Grasbrook 11", "20457", "Hamburg")
                .movedTo(Address.at("Neuer Weg 2", "20095", "Hamburg")));

        Location loaded = locations.findById(saved.id()).orElseThrow();

        assertThat(loaded.addresses()).extracting(Address::line).containsExactly(
                "Großer Grasbrook 11, 20457 Hamburg",
                "Neuer Weg 2, 20095 Hamburg");
        assertThat(loaded.addresses()).extracting(Address::active).containsExactly(false, true);
        assertThat(loaded.addressLine()).isEqualTo("Neuer Weg 2, 20095 Hamburg");
    }

    @Test
    void aPlaceCanHaveTwoActiveSites() {
        Location saved = locations.save(Location.of("Musterfirma GmbH", HOST)
                .withAddress("Musterweg 1", "22179", "Hamburg")
                .withAdditionalAddress(Address.at("Zweigweg 5", "21073", "Hamburg")));

        Location loaded = locations.findById(saved.id()).orElseThrow();

        assertThat(loaded.activeAddresses()).hasSize(2);
    }

    @Test
    void deletingALocationTakesTheAddressesWithIt() {
        Location saved = locations.save(Location.of("Musterfirma GmbH", HOST)
                .withAddress("Musterweg 1", "22179", "Hamburg"));

        locations.deleteById(saved.id());

        assertThat(locations.findById(saved.id())).isEmpty();
    }

    @Test
    void listsAlphabetically() {
        locations.save(Location.of("Zeise Kinos", HOST));
        locations.save(Location.of("Adobe Hamburg", HOST));

        assertThat(locations.findAllByOrderByNameAsc())
                .extracting(Location::name)
                .containsExactly("Adobe Hamburg", "Zeise Kinos");
    }

    @Test
    void searchesNameAndTownIgnoringCase() {
        locations.save(Location.of("Zeise Kinos", HOST).withAddress(null, null, "Hamburg"));
        locations.save(Location.of("Adobe", HOST).withAddress(null, null, "Lüneburg"));

        assertThat(locations.search("zeise")).extracting(Location::name).containsExactly("Zeise Kinos");
        assertThat(locations.search("LÜNEBURG")).extracting(Location::name).containsExactly("Adobe");
        assertThat(locations.search("burg")).hasSize(2);
    }
}
