package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.dto.external.*;
import com.freelance.driver_backend.service.MockUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/mock_user")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MockUserController {

    private final MockUserService mockUserService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserDto> register(@RequestBody  RegistrationRequest request) {
        return mockUserService.register(request);
    }

    @PostMapping("/login")
    public Mono<LoginResponse> login(@RequestBody LoginRequest request) {
        return mockUserService.login(request);
    }
}