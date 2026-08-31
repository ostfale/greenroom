package de.ostfale.greenroom.adapter.in.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Until there is an event overview, the speakers are the front page. */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/speaker";
    }
}
