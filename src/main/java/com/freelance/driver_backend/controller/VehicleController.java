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
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
@Slf4j
public class VehicleController {

    private final ProfileService profileService;
    private final ResourceService resourceService;

    private static final UUID VEHICLE_CATEGORY_ID = UUID.fromString("e2a7f23e-a3a3-4b0c-852a-227a1c1d6a7e");

    @GetMapping("/user/{userId}")
    public Flux<Product> getVehiclesForUser(
            @PathVariable UUID userId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        log.info("▶️ [VehicleController] Récupération des véhicules pour l'utilisateur ID: {}", userId);

        // Le token est nécessaire pour l'appel au service de ressources.
        // Si le token n'est pas fourni pour cette route publique, on passe `null`.
        // Le service de ressources devra gérer le cas où le token est null pour les accès publics.
        String token = Optional.ofNullable(authorizationHeader).orElse(null);

        return profileService.findOrganisationIdByUserId(userId)
                .flatMapMany(orgId -> resourceService.getProductsByCategory(orgId, VEHICLE_CATEGORY_ID, token, null));
    }
}
