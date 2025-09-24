
package com.freelance.driver_backend.controller;

import com.corundumstudio.socketio.SocketIOServer;
import com.freelance.driver_backend.dto.CreateProductRequest;
import com.freelance.driver_backend.dto.UserSessionContextDto;
import com.freelance.driver_backend.model.ClientProfile;
import com.freelance.driver_backend.model.DriverProfile;
import com.freelance.driver_backend.model.Product;
import com.freelance.driver_backend.model.ProductKey;
import com.freelance.driver_backend.repository.ProductRepository;
import com.freelance.driver_backend.service.NotificationTriggerService;
import com.freelance.driver_backend.service.ProfileService;
import com.freelance.driver_backend.service.ResourceService;
import com.freelance.driver_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@RequestMapping("/api/planning")
@RequiredArgsConstructor
@Slf4j
public class PlanningController {

    private final ResourceService resourceService;
    private final ProfileService profileService;
    private final ProductRepository productRepository;
    private final NotificationTriggerService notificationTriggerService;
    private final SocketIOServer socketIOServer;

    private static final UUID PLANNING_CATEGORY_ID = UUID.fromString("ba75b2c0-30a8-11f0-a5b5-bb7d33c83c13");

    /**
     * PUBLIC: Récupère tous les plannings publiés par les chauffeurs.
     */
    @GetMapping("/published")
    public Flux<Product> getPublishedPlannings() {
        log.info("Controller: Requête publique pour récupérer les plannings publiés.");
        return productRepository.findByCategoryId(PLANNING_CATEGORY_ID)
                .filter(product -> "Published".equalsIgnoreCase(product.getStatus())) // Seuls les plannings "Published"
                .flatMap(this::enrichProductWithAuthorDetails);
    }

    /**
     * PUBLIC: Récupère les plannings publiés d'un chauffeur spécifique.
     */
    @GetMapping("/user/{userId}")
    public Flux<Product> getPlanningsForUser(@PathVariable UUID userId) {
        log.info("Récupération des plannings publiés pour le chauffeur ID: {}", userId);
        return productRepository.findByClientIdAndCategoryId(userId, PLANNING_CATEGORY_ID)
                .filter(product -> "Published".equalsIgnoreCase(product.getStatus()))
                .flatMap(this::enrichProductWithAuthorDetails);
    }

    /**
     * SECURISE (CONDUCTEUR): Récupère les plannings du conducteur actuellement connecté.
     * Inclut aussi ceux en attente de confirmation ou confirmés.
     */
    @GetMapping
    public Flux<Product> getAllPlanningsForCurrentUser(@AuthenticationPrincipal Mono<Jwt> jwtMono,
                                                        @RequestHeader("Authorization") String authorizationHeader) {
        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMapMany(userContext -> {
                if (userContext.getDriverProfile() == null) {
                     return Flux.error(new IllegalStateException("L'utilisateur n'est pas un chauffeur."));
                }
                // Récupère tous les plannings où ce chauffeur est le client_id (auteur)
                return productRepository.findByClientIdAndCategoryId(userContext.getUserId(), PLANNING_CATEGORY_ID);
            });
    }

    /**
     * SECURISE (CONDUCTEUR): Crée un nouveau planning pour le conducteur connecté.
     */
    @PostMapping
    public Mono<ResponseEntity<Product>> createPlanning(
            @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal Mono<Jwt> jwtMono, 
            @RequestHeader("Authorization") String authorizationHeader) {
        
        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                if (userContext.getDriverProfile() == null) {
                    return Mono.error(new IllegalStateException("Seul un chauffeur peut créer un planning."));
                }
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide pour créer un planning (organisation manquante)."));
                }
                DriverProfile driverProfile = userContext.getDriverProfile();
                
                request.setCategoryId(PLANNING_CATEGORY_ID);
                request.setClientId(driverProfile.getUserId());
                request.setClientName(driverProfile.getFirstName() + " " + driverProfile.getLastName());
                request.setClientPhoneNumber(driverProfile.getPhoneNumber());
                request.setClientProfileImageUrl(driverProfile.getProfileImageUrl());
                
                // Le statut par défaut d'un nouveau planning est "Published"
                request.setStatus("Published");

                log.info("▶️ [PlanningController.createPlanning] Création d'un planning par le chauffeur ID: {} pour l'organisation ID: {}",
                         driverProfile.getUserId(), userContext.getOrganisation().getOrganizationId());
                log.info("▶️ [PlanningController.createPlanning] Request payload avant envoi au resourceService: {}", request);

                return resourceService.createProduct(userContext.getOrganisation().getOrganizationId(), request, authorizationHeader, null);
            })
            .map(createdProduct -> {
                log.info("✅ [PlanningController.createPlanning] Planning créé avec succès. ID: {}", createdProduct.getId());
                return new ResponseEntity<>(createdProduct, HttpStatus.CREATED);
            });
    }

    /**
     * SECURISE (CONDUCTEUR): Met à jour un planning appartenant au conducteur connecté.
     */
    @PutMapping("/{planningId}")
    public Mono<ResponseEntity<Product>> updatePlanning(
            @PathVariable UUID planningId,
            @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {
        
        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                 if (userContext.getDriverProfile() == null) {
                    return Mono.error(new IllegalStateException("Seuls les chauffeurs peuvent modifier leurs plannings."));
                }
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide pour mettre à jour un planning (organisation manquante)."));
                }
                DriverProfile driverProfile = userContext.getDriverProfile();

                return productRepository.findById(new ProductKey(userContext.getOrganisation().getOrganizationId(), planningId))
                        .filter(product -> driverProfile.getUserId().equals(product.getClientId()))
                        .switchIfEmpty(Mono.error(new SecurityException("Le chauffeur n'est pas autorisé à modifier ce planning ou il n'existe pas.")))
                        .flatMap(existingPlanning ->
                            resourceService.updateProduct(userContext.getOrganisation().getOrganizationId(), planningId, request, authorizationHeader, null)
                        );
            })
            .doOnSuccess(updatedPlanning -> {
                if (updatedPlanning != null) {
                    socketIOServer.getBroadcastOperations().sendEvent("updated_planning", updatedPlanning);
                }
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * SECURISE (CONDUCTEUR): Supprime un planning appartenant au conducteur connecté.
     */
    @DeleteMapping("/{planningId}")
    public Mono<ResponseEntity<Void>> deletePlanning(
            @PathVariable UUID planningId,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                if (userContext.getDriverProfile() == null) {
                    return Mono.error(new IllegalStateException("Seuls les chauffeurs peuvent supprimer leurs plannings."));
                }
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide pour supprimer un planning (organisation manquante)."));
                }
                DriverProfile driverProfile = userContext.getDriverProfile();

                return productRepository.findById(new ProductKey(userContext.getOrganisation().getOrganizationId(), planningId))
                        .filter(product -> driverProfile.getUserId().equals(product.getClientId()))
                        .switchIfEmpty(Mono.error(new SecurityException("Le chauffeur n'est pas autorisé à supprimer ce planning ou il n'existe pas.")))
                        .flatMap(existingPlanning ->
                            resourceService.deleteProduct(userContext.getOrganisation().getOrganizationId(), planningId, authorizationHeader, null)
                        );
            })
            .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)));
    }
    
    /**
     * SECURISE (CLIENT): Permet à un client de DEMANDER à réserver un planning.
     * Le statut de l'annonce passe à "PendingDriverConfirmation". Une notification est envoyée au chauffeur.
     * (Anciennement /accept)
     */
    @PostMapping("/{planningId}/request-booking")
    public Mono<ResponseEntity<Product>> requestPlanningBooking( // RENOMMÉ
            @PathVariable UUID planningId,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(clientContext -> {
                if (clientContext.getClientProfile() == null) {
                    return Mono.error(new IllegalStateException("Seul un client peut demander la réservation d'un planning."));
                }
                if (clientContext.getOrganisation() == null || clientContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide pour demander la réservation d'un planning (organisation manquante)."));
                }
                ClientProfile clientProfile = clientContext.getClientProfile();
                
                return productRepository.findById(new ProductKey(clientContext.getOrganisation().getOrganizationId(), planningId))
                    .flatMap(planning -> {
                        if (planning.getReservedByDriverId() != null) {
                            return Mono.error(new IllegalStateException("Ce planning a déjà une demande de réservation ou est déjà réservé."));
                        }
                        
                        // Met l'ID du client demandeur dans reservedByDriverId (qui représente ici le client ayant fait la demande)
                        planning.setReservedByDriverId(clientProfile.getUserId());
                        planning.setReservedByDriverName(clientProfile.getFirstName() + " " + clientProfile.getLastName());
                        planning.setStatus("PendingDriverConfirmation"); // NOUVEAU STATUT
                        log.info("Client {} demande la réservation du planning {}. Statut mis à jour à 'PendingDriverConfirmation'.", clientProfile.getUserId(), planningId);
                        
                        return productRepository.save(planning);
                    })
                    .flatMap(updatedPlanning -> 
                        profileService.findDriverById(updatedPlanning.getClientId()) // Le client_id du planning est l'ID du chauffeur
                            .flatMap(driverProfile -> notificationTriggerService.notifyDriverOfPlanningBookingRequest(updatedPlanning, clientProfile))
                            .thenReturn(updatedPlanning)
                    )
                    .doOnSuccess(updatedPlanning -> {
                        socketIOServer.getBroadcastOperations().sendEvent("updated_planning", updatedPlanning);
                    });
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * SECURISE (CONDUCTEUR): Permet au CHAUFFEUR de confirmer (accepter) une demande de réservation d'un client.
     * Le statut du planning passe à "Ongoing". Une notification est envoyée au client.
     */
    @PostMapping("/{planningId}/confirm-booking") // NOUVEL ENDPOINT
    public Mono<ResponseEntity<Product>> confirmPlanningBooking(
            @PathVariable UUID planningId,
            @RequestParam UUID clientId, // Le client à confirmer
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {
        
        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(driverContext -> {
                if (driverContext.getDriverProfile() == null) {
                    return Mono.error(new IllegalStateException("Seul un chauffeur peut confirmer la réservation d'un planning."));
                }
                if (driverContext.getOrganisation() == null || driverContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide pour confirmer une réservation de planning (organisation manquante)."));
                }
                DriverProfile driverProfile = driverContext.getDriverProfile();

                return productRepository.findById(new ProductKey(driverContext.getOrganisation().getOrganizationId(), planningId))
                    .flatMap(planning -> {
                        // Vérifier que le chauffeur connecté est bien l'auteur du planning
                        if (!planning.getClientId().equals(driverContext.getUserId())) {
                            return Mono.error(new SecurityException("Vous n'êtes pas l'auteur de ce planning."));
                        }
                        // Vérifier que le statut est bien "PendingDriverConfirmation" et que le client à confirmer est celui qui a fait la demande
                        if (!"PendingDriverConfirmation".equalsIgnoreCase(planning.getStatus()) || !planning.getReservedByDriverId().equals(clientId)) {
                             return Mono.error(new IllegalStateException("Ce planning n'est pas en attente de confirmation pour ce client, ou le statut est incorrect."));
                        }

                        planning.setStatus("Ongoing"); // Statut final
                        log.info("Chauffeur {} a confirmé la réservation du client {} pour le planning {}. Statut mis à jour à 'Ongoing'.", driverProfile.getUserId(), clientId, planningId);
                        
                        return productRepository.save(planning);
                    })
                    .flatMap(updatedPlanning -> 
                        profileService.findClientById(clientId) // Retrouver le profil du client pour la notification
                            .flatMap(clientProfile -> notificationTriggerService.notifyClientOfPlanningBookingAccepted(updatedPlanning, driverProfile))
                            .thenReturn(updatedPlanning)
                    )
                    .doOnSuccess(updatedPlanning -> {
                        socketIOServer.getBroadcastOperations().sendEvent("updated_planning", updatedPlanning);
                    });
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * SECURISE (CLIENT): Permet à un client d'ANNULER sa demande de réservation ou une réservation en cours.
     * Le statut du planning revient à "Published". Une notification est envoyée au chauffeur.
     */
    @PostMapping("/{planningId}/cancel-reservation") // NOUVEL ENDPOINT
    public Mono<ResponseEntity<Product>> cancelPlanningReservation(
            @PathVariable UUID planningId,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(clientContext -> {
                if (clientContext.getClientProfile() == null) {
                    return Mono.error(new IllegalStateException("Seul un client peut annuler une réservation de planning."));
                }
                if (clientContext.getOrganisation() == null || clientContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide pour annuler une réservation de planning (organisation manquante)."));
                }
                ClientProfile clientProfile = clientContext.getClientProfile();

                return productRepository.findById(new ProductKey(clientContext.getOrganisation().getOrganizationId(), planningId))
                    .flatMap(planning -> {
                        // Vérifier que le client connecté est bien celui qui a demandé ou réservé
                        if (!clientProfile.getUserId().equals(planning.getReservedByDriverId())) {
                            return Mono.error(new SecurityException("Vous n'avez pas demandé ou réservé ce planning."));
                        }
                        // Autoriser l'annulation si le statut est "PendingDriverConfirmation" ou "Ongoing"
                        if (!"PendingDriverConfirmation".equalsIgnoreCase(planning.getStatus()) && !"Ongoing".equalsIgnoreCase(planning.getStatus())) {
                            return Mono.error(new IllegalStateException("Le planning n'est pas dans un état permettant l'annulation de réservation (statut actuel: " + planning.getStatus() + ")."));
                        }

                        planning.setReservedByDriverId(null);
                        planning.setReservedByDriverName(null);
                        planning.setStatus("Published"); // Revenir au statut "Published"
                        log.info("Client {} a annulé sa demande/réservation pour le planning {}. Statut remis à 'Published'.", clientProfile.getUserId(), planningId);
                        
                        return productRepository.save(planning);
                    })
                    .flatMap(updatedPlanning -> 
                        profileService.findDriverById(updatedPlanning.getClientId()) // L'auteur du planning est le chauffeur
                            .flatMap(driverProfile -> notificationTriggerService.notifyDriverOfCancelledPlanningReservation(updatedPlanning, clientProfile))
                            .thenReturn(updatedPlanning)
                    )
                    .doOnSuccess(updatedPlanning -> {
                        socketIOServer.getBroadcastOperations().sendEvent("updated_planning", updatedPlanning);
                    });
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * SECURISE (CLIENT): Récupère les plannings que le client connecté a réservés ou demandés.
     */
    @GetMapping("/my-reservations")
    public Flux<Product> getMyReservedRides(@AuthenticationPrincipal Mono<Jwt> jwtMono,
                                            @RequestHeader("Authorization") String authorizationHeader) {
        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMapMany(userContext -> {
                 if (userContext.getClientProfile() == null) {
                     return Flux.error(new IllegalStateException("L'utilisateur n'est pas un client."));
                }
                log.info("Récupération des courses réservées/demandées par le client {}", userContext.getUserId());
                // Filtrer les plannings où ce client est le "reservedByDriverId"
                return productRepository.findByReservedByDriverId(userContext.getUserId())
                            .flatMap(this::enrichProductWithAuthorDetails); // Enrichir avec les détails du chauffeur (auteur du planning)
            });
    }

    /**
     * Méthode privée pour enrichir un produit avec les détails de son auteur (chauffeur).
     */
    private Mono<Product> enrichProductWithAuthorDetails(Product product) {
        UUID authorId = product.getClientId();
        if (authorId == null) {
            return Mono.just(product);
        }

        return profileService.findDriverById(authorId)
            .map(driverProfile -> {
                product.setAuthorId(driverProfile.getUserId());
                product.setAuthorName(driverProfile.getFirstName() + " " + driverProfile.getLastName());
                product.setAuthorPhoneNumber(driverProfile.getPhoneNumber());
                product.setAuthorProfileImageUrl(driverProfile.getProfileImageUrl());
                return product;
            })
            .defaultIfEmpty(product);
    }
}