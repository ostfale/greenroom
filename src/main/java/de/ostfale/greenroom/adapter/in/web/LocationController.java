package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.domain.location.Address;
import de.ostfale.greenroom.domain.location.ContactPerson;
import de.ostfale.greenroom.domain.location.Location;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/location")
public class LocationController {

    private final ManageLocations locations;

    public LocationController(ManageLocations locations) {
        this.locations = locations;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String search, Model model) {
        fill(model, search);
        return "location/list";
    }

    /** The same route for htmx: only the table comes back. */
    @GetMapping(headers = "HX-Request")
    public String listFragment(@RequestParam(defaultValue = "") String search, Model model) {
        fill(model, search);
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
            Location location = new Location(null, name, notes, List.of(), List.of(contact));
            if (!street.isBlank() || !postalCode.isBlank() || !city.isBlank()) {
                location = location.movedTo(
                        Address.at(street, postalCode, city).withCapacity(seats(capacity)));
            } else if (!capacity.isBlank()) {
                throw new IllegalArgumentException("Location :: a capacity belongs to an address");
            }
            locations.add(location);
            return "redirect:/location";
        } catch (IllegalArgumentException e) {
            // The records know the rules; the form only has to say so in German and keep
            // what was typed.
            model.addAttribute("error", message(e));
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
            throw new IllegalArgumentException("Address :: capacity is not a number: " + capacity);
        }
    }

    private static String message(IllegalArgumentException e) {
        String reason = e.getMessage() == null ? "" : e.getMessage();
        if (reason.contains("capacity belongs to an address")) {
            return "Die Plätze gehören zu einer Adresse — bitte auch die Adresse angeben.";
        }
        if (reason.contains("capacity")) {
            return "Die Plätze müssen eine Zahl größer als null sein.";
        }
        return "Name des Ortes sowie Name und E-Mail-Adresse des Ansprechpartners sind Pflichtfelder.";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        return locations.byId(id)
                .map(location -> {
                    model.addAttribute("location", location);
                    return "location/detail";
                })
                .orElse("redirect:/location");
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
            model.addAttribute("location", locations.addAddress(id,
                    Address.at(street, postalCode, city).withCapacity(seats(capacity)), moved));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage() != null && e.getMessage().contains("capacity")
                    ? "Die Plätze müssen eine Zahl größer als null sein."
                    : "Eine Adresse braucht mindestens Straße oder Stadt.");
            locations.byId(id).ifPresent(location -> model.addAttribute("location", location));
        }
        return "fragments/address-list :: address-list";
    }

    @PostMapping("/{id}/address/{position}")
    public String setAddressActive(@PathVariable Long id,
                                   @PathVariable int position,
                                   @RequestParam boolean active,
                                   Model model) {
        model.addAttribute("location", locations.setAddressActive(id, position, active));
        return "fragments/address-list :: address-list";
    }

    private void fill(Model model, String search) {
        model.addAttribute("locations", locations.matching(search));
        model.addAttribute("search", search);
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
