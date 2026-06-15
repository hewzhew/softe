package com.bupt.charging.controller;

import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.dto.AuthDtos;
import com.bupt.charging.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResult<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ApiResult.ok(authService.login(request.loginName(), request.password()));
    }

    @GetMapping("/me")
    public ApiResult<AuthDtos.CurrentUserResponse> me(@RequestHeader("X-Session-Token") String token) {
        return ApiResult.ok(authService.currentUser(token));
    }

    @PostMapping("/logout")
    public ApiResult<Boolean> logout(@RequestHeader("X-Session-Token") String token) {
        authService.logout(token);
        return ApiResult.ok(true);
    }
}
