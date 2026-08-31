package de.ostfale.greenroom.adapter.in.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** The evenings are what the tool is for, so they are the front page. */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/event";
    }
}
