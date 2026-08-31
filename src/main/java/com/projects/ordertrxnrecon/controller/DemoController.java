package com.projects.ordertrxnrecon.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    @GetMapping("/hello")
    public ResponseEntity<Map<String, String>> hello(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
                "message", "Hello, " + authentication.getName() + "! This is a protected endpoint.",
                "email", authentication.getName()
        ));
    }
}
