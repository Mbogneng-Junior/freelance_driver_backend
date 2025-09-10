package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.dto.onboarding.ClientOnboardingRequest;
import com.freelance.driver_backend.dto.onboarding.DriverOnboardingRequest;
import com.freelance.driver_backend.dto.UserSessionContextDto; // Importez la nouvelle structure
import com.freelance.driver_backend.model.ClientProfile;
import com.freelance.driver_backend.model.DriverProfile;
import com.freelance.driver_backend.service.OnboardingService; // Réutiliser le service Onboarding
import com.freelance.driver_backend.service.ProfileService;
import com.freelance.driver_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;


@RestController
@RequestMapping("/api/profiles/roles")
@RequiredArgsConstructor
@Slf4j
public class ProfileRoleController {

    private final ProfileService profileService;
    private final OnboardingService onboardingService;

    
    @PostMapping("/become-driver")
    public Mono<ResponseEntity<UserSessionContextDto>> becomeDriver(
            @RequestBody DriverOnboardingRequest request, // Réutilise le DTO d'onboarding
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> {
                UUID userId = JwtUtil.getUserIdFromToken(jwt);
                // Vérifier d'abord si le profil chauffeur existe déjà pour cet utilisateur
                return profileService.findDriverById(userId)
                    .flatMap(existingDriver -> {
                        log.warn("L'utilisateur {} a déjà un DriverProfile. Impossible d'en créer un autre.", userId);
                        return Mono.error(new IllegalStateException("Vous êtes déjà enregistré en tant que chauffeur."));
                    })
                    .switchIfEmpty(
                        // Si aucun profil chauffeur n'existe, procéder à sa création
                        onboardingService.createDriverProfileForExistingUser(userId, request, authorizationHeader)
                    )
                    .flatMap(driverProfile -> // driverProfile est le DriverProfile nouvellement créé
                         // Puis, récupérer le contexte complet mis à jour pour l'utilisateur
                         profileService.getUserSessionContext(userId, authorizationHeader, null)
                    )
                    .map(context -> new ResponseEntity<>(context, HttpStatus.CREATED));
            });
    }

    
    @PostMapping("/become-client")
    public Mono<ResponseEntity<UserSessionContextDto>> becomeClient(
            @RequestBody ClientOnboardingRequest request, // Réutilise le DTO d'onboarding
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> {
                UUID userId = JwtUtil.getUserIdFromToken(jwt);
                // Vérifier d'abord si le profil client existe déjà pour cet utilisateur
                return profileService.findClientById(userId)
                    .flatMap(existingClient -> {
                        log.warn("L'utilisateur {} a déjà un ClientProfile. Impossible d'en créer un autre.", userId);
                        return Mono.error(new IllegalStateException("Vous êtes déjà enregistré en tant que client."));
                    })
                    .switchIfEmpty(
                        // Si aucun profil client n'existe, procéder à sa création
                        onboardingService.createClientProfileForExistingUser(userId, request, authorizationHeader)
                    )
                    .flatMap(clientProfile -> // clientProfile est le ClientProfile nouvellement créé
                         // Puis, récupérer le contexte complet mis à jour pour l'utilisateur
                         profileService.getUserSessionContext(userId, authorizationHeader, null)
                    )
                    .map(context -> new ResponseEntity<>(context, HttpStatus.CREATED));
            });
    }
}


// PATH: /home/mbogneng-junior/freelance-driver (Copie)/backend/src/main/java/com/freelance/driver_backend/controller/ProfileController.java

