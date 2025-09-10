/*package com.freelance.driver_backend.controller;

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
*/

// PATH: /home/mbogneng-junior/freelance-driver (Copie)/backend/src/main/java/com/freelance/driver_backend/controller/VehicleController.java

package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.dto.CreateProductRequest; // Ajouté pour les méthodes POST/PUT
import com.freelance.driver_backend.dto.UserSessionContextDto; // Nouveau import
import com.freelance.driver_backend.model.DriverProfile; // Nouveau import
import com.freelance.driver_backend.model.Product;
import com.freelance.driver_backend.service.ProfileService;
import com.freelance.driver_backend.service.ResourceService;
import com.freelance.driver_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus; // Ajouté pour les ResponseEntity
import org.springframework.http.ResponseEntity; // Ajouté pour les ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal; // Ajouté pour les méthodes POST/PUT
import org.springframework.security.oauth2.jwt.Jwt; // Ajouté pour les méthodes POST/PUT
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    /**
     * SECURISE: Récupère tous les véhicules du chauffeur actuellement connecté.
     */
    @GetMapping
    public Flux<Product> getMyVehicles(
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMapMany(userContext -> {
                // Seuls les chauffeurs peuvent avoir des véhicules
                if (userContext.getDriverProfile() == null) {
                    return Flux.error(new IllegalStateException("Seuls les chauffeurs peuvent gérer leurs véhicules."));
                }
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Flux.error(new IllegalStateException("L'utilisateur n'a pas d'organisation valide associée."));
                }
                UUID organizationId = userContext.getOrganisation().getOrganizationId();
                // Filtre les produits par categoryId ET clientId (chauffeur connecté)
                return resourceService.getProductsByCategory(organizationId, VEHICLE_CATEGORY_ID, authorizationHeader, null)
                                      .filter(product -> userContext.getUserId().equals(product.getClientId()));
            });
    }

    /**
     * PUBLIC: Récupère les véhicules d'un utilisateur spécifique par son ID.
     * Cette route devrait idéalement être utilisée pour afficher les véhicules d'un CHAUFFEUR public.
     */
    @GetMapping("/user/{userId}")
    public Flux<Product> getVehiclesForUser(
            @PathVariable UUID userId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        log.info("▶️ [VehicleController] Récupération des véhicules pour l'utilisateur ID: {}", userId);

        String token = Optional.ofNullable(authorizationHeader).orElse(null);

        // MODIFIÉ : Récupère l'organisation ID du userId spécifié.
        // Puis, filtre les produits de cette organisation qui appartiennent à ce userId.
        return profileService.findOrganisationIdByUserId(userId)
                .flatMapMany(orgId -> resourceService.getProductsByCategory(orgId, VEHICLE_CATEGORY_ID, token, null)
                                                    .filter(product -> userId.equals(product.getClientId())));
    }

    /**
     * SECURISE (CONDUCTEUR): Crée un nouveau véhicule pour le chauffeur connecté.
     */
    @PostMapping
    public Mono<ResponseEntity<Product>> createVehicle(
            @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                if (userContext.getDriverProfile() == null) {
                    return Mono.error(new IllegalStateException("Seuls les chauffeurs peuvent créer des véhicules."));
                }
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide pour créer un véhicule (organisation manquante)."));
                }
                DriverProfile driverProfile = userContext.getDriverProfile();

                request.setCategoryId(VEHICLE_CATEGORY_ID);
                request.setClientId(driverProfile.getUserId()); // Le chauffeur connecté est le propriétaire du véhicule
                // Les autres champs comme clientName, clientPhoneNumber, clientProfileImageUrl peuvent être définis ici
                // pour enrichir le produit si nécessaire, mais le service de ressources pourrait déjà le faire.
                request.setClientName(driverProfile.getFirstName() + " " + driverProfile.getLastName());
                request.setClientPhoneNumber(driverProfile.getPhoneNumber());
                request.setClientProfileImageUrl(driverProfile.getProfileImageUrl());
                
                log.info("▶️ Controller: Création d'un nouveau véhicule '{}' pour le chauffeur {}", request.getName(), driverProfile.getUserId());
                
                return resourceService.createProduct(
                    userContext.getOrganisation().getOrganizationId(), 
                    request, 
                    authorizationHeader, 
                    null
                );
            })
            .map(createdProduct -> new ResponseEntity<>(createdProduct, HttpStatus.CREATED));
    }

    /**
     * SECURISE (CONDUCTEUR): Met à jour un véhicule existant.
     */
    @PutMapping("/{vehicleId}")
    public Mono<ResponseEntity<Product>> updateVehicle(
            @PathVariable UUID vehicleId,
            @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {
        
        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                if (userContext.getDriverProfile() == null) {
                    return Mono.error(new IllegalStateException("Seuls les chauffeurs peuvent modifier leurs véhicules."));
                }
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide pour mettre à jour un véhicule (organisation manquante)."));
                }
                DriverProfile driverProfile = userContext.getDriverProfile();

                // Vérification cruciale : l'utilisateur connecté est-il le propriétaire du véhicule ?
                return resourceService.getProductsByCategory(userContext.getOrganisation().getOrganizationId(), VEHICLE_CATEGORY_ID, authorizationHeader, null)
                        .filter(product -> product.getId().equals(vehicleId) && driverProfile.getUserId().equals(product.getClientId()))
                        .next() // Prend le premier élément ou vide
                        .switchIfEmpty(Mono.error(new SecurityException("Le chauffeur n'est pas autorisé à modifier ce véhicule ou il n'existe pas.")))
                        .flatMap(existingVehicle -> {
                            request.setCategoryId(VEHICLE_CATEGORY_ID);
                            request.setClientId(driverProfile.getUserId()); // Assure que le propriétaire reste le même
                            request.setClientName(driverProfile.getFirstName() + " " + driverProfile.getLastName());
                            request.setClientPhoneNumber(driverProfile.getPhoneNumber());
                            request.setClientProfileImageUrl(driverProfile.getProfileImageUrl());

                            log.info("▶️ Controller: Mise à jour du véhicule ID: {}", vehicleId);
                            
                            return resourceService.updateProduct(
                                userContext.getOrganisation().getOrganizationId(), 
                                vehicleId,
                                request, 
                                authorizationHeader, 
                                null
                            );
                        });
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * SECURISE (CONDUCTEUR): Supprime un véhicule existant.
     */
    @DeleteMapping("/{vehicleId}")
    public Mono<ResponseEntity<Void>> deleteVehicle(
            @PathVariable UUID vehicleId,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                if (userContext.getDriverProfile() == null) {
                    return Mono.error(new IllegalStateException("Seuls les chauffeurs peuvent supprimer leurs véhicules."));
                }
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide pour supprimer un véhicule (organisation manquante)."));
                }
                DriverProfile driverProfile = userContext.getDriverProfile();

                // Vérification cruciale : l'utilisateur connecté est-il le propriétaire du véhicule ?
                return resourceService.getProductsByCategory(userContext.getOrganisation().getOrganizationId(), VEHICLE_CATEGORY_ID, authorizationHeader, null)
                        .filter(product -> product.getId().equals(vehicleId) && driverProfile.getUserId().equals(product.getClientId()))
                        .next() // Prend le premier élément ou vide
                        .switchIfEmpty(Mono.error(new SecurityException("Le chauffeur n'est pas autorisé à supprimer ce véhicule ou il n'existe pas.")))
                        .flatMap(existingVehicle -> {
                            log.info("▶️ Controller: Suppression du véhicule ID: {}", vehicleId);
                            return resourceService.deleteProduct(
                                userContext.getOrganisation().getOrganizationId(), 
                                vehicleId,
                                authorizationHeader, 
                                null
                            );
                        });
            })
            .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)));
    }
}