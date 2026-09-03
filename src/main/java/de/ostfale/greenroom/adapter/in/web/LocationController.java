package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.locations.Address;
import de.ostfale.greenroom.domain.locations.ContactPerson;
import de.ostfale.greenroom.domain.locations.Location;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Controller
@RequestMapping("/location")
public class LocationController {

    private final ManageLocations locations;
    private final ErrorMessages errors;

    public LocationController(ManageLocations locations, ErrorMessages errors) {
        this.locations = locations;
        this.errors = errors;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String search,
                       @RequestParam(defaultValue = "false") boolean onlyActive,
                       Model model) {
        fill(model, search, onlyActive);
        return "location/list";
    }

    /** The same route for htmx: only the table comes back. */
    @GetMapping(headers = "HX-Request")
    public String listFragment(@RequestParam(defaultValue = "") String search,
                               @RequestParam(defaultValue = "false") boolean onlyActive,
                               Model model) {
        fill(model, search, onlyActive);
        return "fragments/location-table :: location-table";
    }

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("submitted", submitted("", "", "", "", "", "", "", "", ""));
        return "location/form";
    }

    @PostMapping
    public String add(@RequestParam(defaultValue = "") String name,
                      @RequestParam(defaultValue = "") String street,
                      @RequestParam(defaultValue = "") String postalCode,
                      @RequestParam(defaultValue = "") String city,
                      @RequestParam(defaultValue = "") String capacity,
                      @RequestParam(defaultValue = "") String notes,
                      @RequestParam(defaultValue = "") String contactName,
                      @RequestParam(defaultValue = "") String contactEmail,
                      @RequestParam(defaultValue = "") String contactPhone,
                      Model model) {
        try {
            ContactPerson contact = new ContactPerson(contactName, contactEmail, contactPhone);
            Location location = new Location(null, name, notes, true, List.of(), List.of(contact));
            if (!street.isBlank() || !postalCode.isBlank() || !city.isBlank()) {
                location = location.movedTo(
                        Address.at(street, postalCode, city).withCapacity(seats(capacity)));
            } else if (!capacity.isBlank()) {
                throw new RuleViolated(Rule.CAPACITY_BELONGS_TO_AN_ADDRESS);
            }
            locations.add(location);
            return "redirect:/location";
        } catch (RuleViolated e) {
            // The records know the rules; the form only has to say so in German and keep
            // what was typed.
            model.addAttribute("error", errors.german(e));
            model.addAttribute("submitted", submitted(name, street, postalCode, city, capacity,
                    notes, contactName, contactEmail, contactPhone));
            return "location/form";
        }
    }

    /** Empty means "not counted"; anything that is not a number is a mistake worth naming. */
    private static Integer seats(String capacity) {
        if (capacity == null || capacity.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(capacity.strip());
        } catch (NumberFormatException e) {
            throw new RuleViolated(Rule.CAPACITY_IS_A_NUMBER_OF_SEATS, capacity);
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        return locations.byId(id)
                .map(location -> {
                    show(model, location);
                    return "location/detail";
                })
                .orElse("redirect:/location");
    }

    /**
     * Asks where that address is and keeps the answer. For the addresses that were written
     * down before anybody looked, and for a second try when the lookup was unreachable.
     */
    @PostMapping("/{id}/address/{position}/locate")
    public String locate(@PathVariable Long id, @PathVariable int position, Model model) {
        try {
            Location asked = locations.locate(id, position);
            // Asked and not told is worth saying, or the button looks broken.
            if (!asked.addresses().get(position).isLocated()) {
                model.addAttribute("error",
                        "Zu dieser Adresse hat OpenStreetMap keinen Punkt geliefert. "
                                + "Meist fehlt die Hausnummer oder die Stadt.");
            }
        } catch (RuleViolated e) {
            model.addAttribute("error", errors.german(e));
        }
        locations.byId(id).ifPresent(location -> show(model, location));
        return "fragments/address-list :: address-list-and-summary";
    }

    /** Name and notes. The addresses and the contact people have their own forms. */
    @PostMapping("/{id}")
    public String change(@PathVariable Long id,
                         @RequestParam(defaultValue = "") String name,
                         @RequestParam(defaultValue = "") String notes,
                         @RequestParam(defaultValue = "false") boolean inUse,
                         Model model) {
        try {
            Location known = locations.byId(id).orElseThrow(() ->
                    new RuleViolated(Rule.NOT_FOUND));
            locations.change(new Location(id, name, notes, inUse,
                    known.addresses(), known.contacts()));
        } catch (RuleViolated e) {
            model.addAttribute("error", errors.german(e));
        }
        locations.byId(id).ifPresent(location -> show(model, location));
        return "fragments/location-fields :: location-fields";
    }

    /**
     * A new address. "Moved" retires the earlier ones; without it the place simply has a
     * second site.
     */
    @PostMapping("/{id}/address")
    public String addAddress(@PathVariable Long id,
                             @RequestParam(defaultValue = "") String street,
                             @RequestParam(defaultValue = "") String postalCode,
                             @RequestParam(defaultValue = "") String city,
                             @RequestParam(defaultValue = "") String capacity,
                             @RequestParam(defaultValue = "false") boolean moved,
                             Model model) {
        try {
            show(model, locations.addAddress(id,
                    Address.at(street, postalCode, city).withCapacity(seats(capacity)), moved));
        } catch (RuleViolated e) {
            model.addAttribute("error", errors.german(e));
            locations.byId(id).ifPresent(location -> show(model, location));
        }
        return "fragments/address-list :: address-list-and-summary";
    }

    @PostMapping("/{id}/address/{position}")
    public String setAddressActive(@PathVariable Long id,
                                   @PathVariable int position,
                                   @RequestParam boolean active,
                                   Model model) {
        show(model, locations.setAddressActive(id, position, active));
        return "fragments/address-list :: address-list-and-summary";
    }

    @PostMapping("/{id}/contact")
    public String addContact(@PathVariable Long id,
                             @RequestParam(defaultValue = "") String contactName,
                             @RequestParam(defaultValue = "") String contactEmail,
                             @RequestParam(defaultValue = "") String contactPhone,
                             Model model) {
        return contactFragment(id, model, () -> locations.addContact(id,
                new ContactPerson(contactName, contactEmail, contactPhone)));
    }

    @PostMapping("/{id}/contact/{position}")
    public String changeContact(@PathVariable Long id,
                                @PathVariable int position,
                                @RequestParam(defaultValue = "") String contactName,
                                @RequestParam(defaultValue = "") String contactEmail,
                                @RequestParam(defaultValue = "") String contactPhone,
                                Model model) {
        return contactFragment(id, model, () -> locations.changeContact(id, position,
                new ContactPerson(contactName, contactEmail, contactPhone)));
    }

    @PostMapping("/{id}/contact/{position}/remove")
    public String removeContact(@PathVariable Long id, @PathVariable int position, Model model) {
        return contactFragment(id, model, () -> locations.removeContact(id, position));
    }

    /**
     * Every contact change answers with the same list. On a refusal the stored state comes
     * back unchanged, together with the reason.
     */
    private String contactFragment(Long id, Model model, Supplier<Location> change) {
        try {
            show(model, change.get());
        } catch (RuleViolated e) {
            model.addAttribute("error", errors.german(e));
            locations.byId(id).ifPresent(location -> show(model, location));
        }
        return "fragments/contact-list :: contact-list";
    }

    private void show(Model model, Location location) {
        model.addAttribute("location", location);
        model.addAttribute("mapUrl", MapExcerpt.of(location.currentAddress()));
        model.addAttribute("canLocate", locations.canLocateAddresses());
    }

    private void fill(Model model, String search, boolean onlyActive) {
        model.addAttribute("locations", locations.matching(search).stream()
                .filter(place -> !onlyActive || place.inUse())
                .toList());
        model.addAttribute("search", search);
        model.addAttribute("onlyActive", onlyActive);
    }

    private static Map<String, String> submitted(String name, String street, String postalCode,
                                                 String city, String capacity, String notes,
                                                 String contactName, String contactEmail,
                                                 String contactPhone) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", name);
        values.put("street", street);
        values.put("postalCode", postalCode);
        values.put("city", city);
        values.put("capacity", capacity);
        values.put("notes", notes);
        values.put("contactName", contactName);
        values.put("contactEmail", contactEmail);
        values.put("contactPhone", contactPhone);
        return values;
    }
}
