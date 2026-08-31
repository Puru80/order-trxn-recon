package com.projects.ordertrxnrecon.controller;

import com.projects.ordertrxnrecon.dto.AuthResponse;
import com.projects.ordertrxnrecon.dto.LoginRequest;
import com.projects.ordertrxnrecon.dto.SignupRequest;
import com.projects.ordertrxnrecon.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(authService.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body(
                    AuthResponse.builder().message("Missing or invalid Authorization header").build()
            );
        }

        String token = authHeader.substring(7);
        AuthResponse response = authService.logout(token);
        return ResponseEntity.ok(response);
    }
}
