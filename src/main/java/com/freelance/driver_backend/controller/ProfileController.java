/*package com.freelance.driver_backend.controller;

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

   
    @GetMapping("/me")
    public Mono<ResponseEntity<UserSessionContextDto>> getMyProfile(
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {
        return jwtMono
                .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

   
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
}*/

// PATH: /home/mbogneng-junior/freelance-driver (Copie)/backend/src/main/java/com/freelance/driver_backend/controller/ProfileController.java

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
     * Retourne la nouvelle structure UserSessionContextDto.
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
     * Vérifie que l'utilisateur a bien un profil chauffeur.
     */
    @PutMapping("/driver/me")
    public Mono<ResponseEntity<DriverProfile>> updateDriverProfile(
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestBody DriverProfile updatedProfileData,
            @RequestHeader("Authorization") String authorizationHeader) { // Ajout de l'header pour getUserSessionContext
        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                if (userContext.getDriverProfile() == null) {
                    return Mono.error(new IllegalStateException("L'utilisateur n'est pas un chauffeur. Impossible de mettre à jour le profil chauffeur."));
                }
                UUID userId = userContext.getUserId();
                return profileService.updateDriverProfile(userId, updatedProfileData);
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * SECURISE: Met à jour le profil du client actuellement connecté.
     * Vérifie que l'utilisateur a bien un profil client.
     */
    @PutMapping("/client/me")
    public Mono<ResponseEntity<ClientProfile>> updateClientProfile(
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestBody ClientProfile updatedProfileData,
            @RequestHeader("Authorization") String authorizationHeader) { // Ajout de l'header pour getUserSessionContext
        
        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                if (userContext.getClientProfile() == null) {
                    return Mono.error(new IllegalStateException("L'utilisateur n'est pas un client. Impossible de mettre à jour le profil client."));
                }
                UUID userId = userContext.getUserId();
                return profileService.updateClientProfile(userId, updatedProfileData);
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * PUBLIC: Récupère le profil public d'un utilisateur par son ID.
     * Retourne la nouvelle structure UserSessionContextDto.
     */
    @GetMapping("/user/{userId}")
    public Mono<ResponseEntity<UserSessionContextDto>> getPublicUserProfile(
            @PathVariable UUID userId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        log.info("▶️ [ProfileController] Récupération du profil public pour l'ID: {}", userId);
        return profileService.getUserSessionContext(userId, authorizationHeader, null)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.info("✅ [ProfileController] Profil public de {} récupéré.", userId))
                .doOnError(error -> log.error("❌ Erreur lors de la récupération du profil public pour {}: {}", userId, error.getMessage()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}