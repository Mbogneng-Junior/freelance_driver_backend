package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.dto.UserSessionContextDto;
import com.freelance.driver_backend.model.ClientProfile;
import com.freelance.driver_backend.model.DriverProfile;
import com.freelance.driver_backend.service.ProfileService;
import com.freelance.driver_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    /**
     * SECURISE: Récupère le profil complet de l'utilisateur actuellement connecté.
     */
    @GetMapping("/me")
    public Mono<ResponseEntity<UserSessionContextDto>> getMyProfile(
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {
        return jwtMono
                .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * SECURISE: Met à jour le profil du conducteur actuellement connecté.
     */
    @PutMapping("/driver/me")
    public Mono<ResponseEntity<DriverProfile>> updateDriverProfile(
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestBody DriverProfile updatedProfileData) {
        return jwtMono
            .flatMap(jwt -> {
                UUID userId = JwtUtil.getUserIdFromToken(jwt);
                return profileService.updateDriverProfile(userId, updatedProfileData);
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * SECURISE: Met à jour le profil du client actuellement connecté.
     */
    @PutMapping("/client/me")
    public Mono<ResponseEntity<ClientProfile>> updateClientProfile(
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestBody ClientProfile updatedProfileData) {
        
        return jwtMono
            .flatMap(jwt -> {
                UUID userId = JwtUtil.getUserIdFromToken(jwt);
                return profileService.updateClientProfile(userId, updatedProfileData);
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * PUBLIC: Récupère le profil public d'un utilisateur par son ID.
     */
    @GetMapping("/user/{userId}")
    public Mono<ResponseEntity<UserSessionContextDto>> getPublicUserProfile(
            @PathVariable UUID userId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        log.info("▶️ [ProfileController] Récupération du profil public pour l'ID: {}", userId);
        // Le token est optionnel et peut être null
        return profileService.getUserSessionContext(userId, authorizationHeader, null)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.info("✅ [ProfileController] Profil public de {} récupéré.", userId))
                .doOnError(error -> log.error("❌ Erreur lors de la récupération du profil public pour {}: {}", userId, error.getMessage()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}