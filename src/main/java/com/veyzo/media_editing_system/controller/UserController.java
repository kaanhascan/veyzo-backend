package com.veyzo.media_editing_system.controller;

import com.veyzo.media_editing_system.service.UserService;
import com.veyzo.media_editing_system.dto.request.LoginRequest;
import com.veyzo.media_editing_system.dto.request.RegisterRequest;
import com.veyzo.media_editing_system.dto.response.AuthResponse;
import com.veyzo.media_editing_system.dto.response.UserProfileDto;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest req){
        userService.register(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@Valid @RequestBody LoginRequest req, HttpServletResponse response) {

        AuthResponse authResponse = userService.login(req);
        String token = authResponse.token();

        ResponseCookie jwtCookie = ResponseCookie.from("jwt_token", token)
                .httpOnly(false)
                .secure(false)
                .path("/")
                .maxAge(24 * 60 * 60)
                .sameSite("Strict")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());

        return ResponseEntity.ok("Giriş başarılı");
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletResponse response) {
        ResponseCookie deleteCookie = ResponseCookie.from("jwt_token", "")
                .httpOnly(false)
                .secure(false)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());
        return ResponseEntity.ok("Çıkış yapıldı");
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> getMyProfile() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        UserProfileDto profileDto = userService.getMyProfile(email);
        return ResponseEntity.ok(profileDto);
    }
}