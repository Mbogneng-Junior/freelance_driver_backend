package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.dto.onboarding.ClientOnboardingRequest;
import com.freelance.driver_backend.dto.onboarding.DriverOnboardingRequest;

import com.freelance.driver_backend.service.OnboardingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import com.freelance.driver_backend.dto.onboarding.OnboardingResponse;

@RestController
@RequestMapping("/api/onboarding")
@Slf4j
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/driver")
    public Mono<ResponseEntity<OnboardingResponse>> onboardDriver(@RequestBody DriverOnboardingRequest request) {
        return onboardingService.createDriverAccount(request)
                .doOnNext(response -> { // AJOUT DU LOG ICI
                    log.info("▶️ Backend DEBUG: Réponse Onboarding (DRIVER) envoyée au frontend: {}", response);
                })
                .map(response -> new ResponseEntity<>(response, HttpStatus.CREATED));
    }

    @PostMapping("/client")
    public Mono<ResponseEntity<OnboardingResponse>> onboardClient(@RequestBody ClientOnboardingRequest request) {
        return onboardingService.createClientAccount(request)
                .doOnNext(response -> { // AJOUT DU LOG ICI
                    log.info("▶️ Backend DEBUG: Réponse Onboarding (CLIENT) envoyée au frontend: {}", response);
                })
                .map(response -> new ResponseEntity<>(response, HttpStatus.CREATED));
    }
}