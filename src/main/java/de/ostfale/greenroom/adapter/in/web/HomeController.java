package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ShowDashboard;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

/**
 * The overview the tool opens with: what is next, what is still missing, and how much of
 * everything there is. The list of evenings used to be the front page — it says what
 * exists, not what has to be done.
 */
@Controller
public class HomeController {

    private final ShowDashboard dashboard;

    public HomeController(ShowDashboard dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("dashboard", dashboard.asOf(LocalDate.now()));
        return "home";
    }
}
