package com.shruthi.vault.controller;

import com.shruthi.vault.dto.RegisterRequest;
import com.shruthi.vault.dto.LoginRequest;
import com.shruthi.vault.dto.AuthResponse;
import com.shruthi.vault.service.AuthService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        System.out.println("Register endpoint hit!");
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        System.out.println("Login endpoint hit!");
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
