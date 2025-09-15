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

import java.util.Map;
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
                .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt),
                        authorizationHeader, null))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * SECURISE: Met à jour le profil du conducteur actuellement connecté.
     * Vérifie que l'utilisateur a bien un profil chauffeur.
     * // --- MODIFICATION ICI : Le type de retour du Mono ---
     * 
     * @return Le UserSessionContextDto complet et mis à jour.
     */
    @PutMapping("/driver/me")
    public Mono<ResponseEntity<UserSessionContextDto>> updateDriverProfile( // <-- CHANGEMENT DU TYPE DE RETOUR
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestBody DriverProfile updatedProfileData,
            @RequestHeader("Authorization") String authorizationHeader) {
        return jwtMono
                .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt),
                        authorizationHeader, null))
                .flatMap(userContext -> {
                    if (userContext.getDriverProfile() == null) {
                        return Mono.error(new IllegalStateException(
                                "L'utilisateur n'est pas un chauffeur. Impossible de mettre à jour le profil chauffeur."));
                    }
                    UUID userId = userContext.getUserId();
                    return profileService.updateDriverProfile(userId, updatedProfileData); // <--
                                                                                           // profileService.updateDriverProfile
                                                                                           // retourne
                                                                                           // UserSessionContextDto
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * SECURISE: Met à jour le profil du client actuellement connecté.
     * Vérifie que l'utilisateur a bien un profil client.
     * // --- MODIFICATION ICI : Le type de retour du Mono ---
     * 
     * @return Le UserSessionContextDto complet et mis à jour.
     */
    @PutMapping("/client/me")
    public Mono<ResponseEntity<UserSessionContextDto>> updateClientProfile( // <-- CHANGEMENT DU TYPE DE RETOUR
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestBody ClientProfile updatedProfileData,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
                .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt),
                        authorizationHeader, null))
                .flatMap(userContext -> {
                    if (userContext.getClientProfile() == null) {
                        return Mono.error(new IllegalStateException(
                                "L'utilisateur n'est pas un client. Impossible de mettre à jour le profil client."));
                    }
                    UUID userId = userContext.getUserId();
                    return profileService.updateClientProfile(userId, updatedProfileData); // <--
                                                                                           // profileService.updateClientProfile
                                                                                           // retourne
                                                                                           // UserSessionContextDto
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
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        log.info("▶️ [ProfileController] Récupération du profil public pour l'ID: {}", userId);
        return profileService.getUserSessionContext(userId, authorizationHeader, null)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.info("✅ [ProfileController] Profil public de {} récupéré.", userId))
                .doOnError(error -> log.error("❌ Erreur lors de la récupération du profil public pour {}: {}", userId,
                        error.getMessage()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * SECURISE: Met à jour l'URL de l'avatar pour l'utilisateur connecté.
     * Cette route déclenche la mise à jour de TOUS les profils associés
     * (Driver/Client)
     * et la suppression de l'ancien avatar du stockage.
     * 
     * @param jwtMono             Le token JWT de l'utilisateur authentifié.
     * @param requestPayload      Un Map contenant "profileImageUrl".
     * @param authorizationHeader L'en-tête Authorization.
     * @return Le UserSessionContextDto complet et mis à jour.
     */
    @PutMapping("/me/avatar")
    public Mono<ResponseEntity<UserSessionContextDto>> updateMyAvatar(
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestBody Map<String, String> requestPayload,
            @RequestHeader("Authorization") String authorizationHeader) {

        String newAvatarUrl = requestPayload.get("profileImageUrl");
        if (newAvatarUrl == null || newAvatarUrl.isEmpty()) {
            log.error("Requête de mise à jour de l'avatar avec une URL nulle ou vide.");
            return Mono.just(ResponseEntity.badRequest().body(null));
        }

        return jwtMono
                .flatMap(jwt -> {
                    UUID userId = JwtUtil.getUserIdFromToken(jwt);
                    log.info("Requête de mise à jour de l'avatar pour l'utilisateur ID: {} avec la nouvelle URL: {}",
                            userId, newAvatarUrl);
                    return profileService.updateAvatarUrl(userId, newAvatarUrl);
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnError(e -> log.error("❌ Erreur lors de la mise à jour de l'avatar de l'utilisateur: {}",
                        e.getMessage(), e));
    }
}