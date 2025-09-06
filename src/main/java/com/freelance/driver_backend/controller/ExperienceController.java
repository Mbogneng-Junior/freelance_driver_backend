package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.model.Product;
import com.freelance.driver_backend.service.ProfileService;
import com.freelance.driver_backend.service.ResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/experiences")
@RequiredArgsConstructor
@Slf4j
public class ExperienceController {

    private final ProfileService profileService;
    private final ResourceService resourceService;

    // UUID fixe pour la catégorie "expériences"
    private static final UUID EXPERIENCE_CATEGORY_ID = UUID.fromString("e1f2a3b4-c5d6-7890-1234-567890abcdef");

    /**
     * PUBLIC: Récupère les expériences d'un utilisateur spécifique par son ID.
     */
    @GetMapping("/user/{userId}")
    public Flux<Product> getExperiencesForUser(
            @PathVariable UUID userId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        log.info("▶️ [ExperienceController] Récupération des expériences pour l'utilisateur ID: {}", userId);

        String token = Optional.ofNullable(authorizationHeader).orElse(null);

        return profileService.findOrganisationIdByUserId(userId)
                .flatMapMany(orgId -> resourceService.getProductsByCategory(orgId, EXPERIENCE_CATEGORY_ID, token, null))
                .doOnError(error -> log.error("❌ Erreur lors de la récupération des expériences pour {}: {}", userId, error.getMessage()));
    }
}
