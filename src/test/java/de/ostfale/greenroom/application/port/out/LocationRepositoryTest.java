package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.TestDatabase;
import de.ostfale.greenroom.TestcontainersConfiguration;
import de.ostfale.greenroom.domain.locations.Address;
import de.ostfale.greenroom.domain.locations.ContactPerson;
import de.ostfale.greenroom.domain.locations.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.util.List;

import static de.ostfale.greenroom.Fixtures.aContact;
import static de.ostfale.greenroom.Fixtures.aLocation;
import static org.assertj.core.api.Assertions.assertThat;

/** Against a real Postgres — the mapping of the contact list is the part worth proving. */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class LocationRepositoryTest {

    @Autowired
    private LocationRepository locations;

    @Autowired
    private TestDatabase database;

    @BeforeEach
    void emptyTheTable() {
        database.empty();
    }

    @Test
    void storesAndReadsBackALocation() {
        Location saved = locations.save(aLocation()
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
        Location saved = locations.save(aLocation().withContacts(List.of(
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
        Location saved = locations.save(aLocation());

        Location updated = locations.save(saved.withContacts(
                List.of(ContactPerson.of("Anna Albers", "anna@example.org"))));

        assertThat(locations.findById(updated.id()).orElseThrow().contacts())
                .extracting(ContactPerson::name)
                .containsExactly("Anna Albers");
    }

    @Test
    void deletingALocationTakesTheContactsWithIt() {
        Location saved = locations.save(aLocation());

        locations.deleteById(saved.id());

        assertThat(locations.findById(saved.id())).isEmpty();
    }

    @Test
    void keepsEveryAddressAPlaceEverHadAndRemembersWhichOneCountsNow() {
        Location saved = locations.save(Location.of("Kühne + Nagel", aContact())
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
        Location saved = locations.save(aLocation()
                .withAddress("Musterweg 1", "22179", "Hamburg")
                .withAdditionalAddress(Address.at("Zweigweg 5", "21073", "Hamburg")));

        Location loaded = locations.findById(saved.id()).orElseThrow();

        assertThat(loaded.activeAddresses()).hasSize(2);
    }

    @Test
    void deletingALocationTakesTheAddressesWithIt() {
        Location saved = locations.save(aLocation()
                .withAddress("Musterweg 1", "22179", "Hamburg"));

        locations.deleteById(saved.id());

        assertThat(locations.findById(saved.id())).isEmpty();
    }

    @Test
    void listsAlphabetically() {
        locations.save(Location.of("Zeise Kinos", aContact()));
        locations.save(Location.of("Adobe Hamburg", aContact()));

        assertThat(locations.findAllByOrderByNameAsc())
                .extracting(Location::name)
                .containsExactly("Adobe Hamburg", "Zeise Kinos");
    }

    @Test
    void searchesNameAndTownIgnoringCase() {
        locations.save(Location.of("Zeise Kinos", aContact()).withAddress(null, null, "Hamburg"));
        locations.save(Location.of("Adobe", aContact()).withAddress(null, null, "Lüneburg"));

        assertThat(locations.search("zeise")).extracting(Location::name).containsExactly("Zeise Kinos");
        assertThat(locations.search("LÜNEBURG")).extracting(Location::name).containsExactly("Adobe");
        assertThat(locations.search("burg")).hasSize(2);
    }
}
