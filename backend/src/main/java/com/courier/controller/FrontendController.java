package com.courier.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendController {
    @GetMapping({"/", "/dashboard", "/shipments", "/manual-booking", "/hubs", "/regions", "/customers", "/reports", "/tracking"})
    public String frontend() {
        return "forward:/index.html";
    }
}
