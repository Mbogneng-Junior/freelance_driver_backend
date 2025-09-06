package com.freelance.driver_backend.controller;

import com.corundumstudio.socketio.SocketIOServer;
import com.freelance.driver_backend.dto.CreateProductRequest;
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
                .filter(product -> "Published".equalsIgnoreCase(product.getStatus()))
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
     */
    @GetMapping
    public Flux<Product> getAllPlanningsForCurrentUser(@AuthenticationPrincipal Mono<Jwt> jwtMono) {
        return jwtMono
            .map(JwtUtil::getUserIdFromToken)
            .flatMapMany(driverId -> 
                productRepository.findByClientIdAndCategoryId(driverId, PLANNING_CATEGORY_ID)
            );
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
                if (!(userContext.getProfile() instanceof DriverProfile driverProfile)) {
                    return Mono.error(new IllegalStateException("Seul un chauffeur peut créer un planning."));
                }
                
                request.setCategoryId(PLANNING_CATEGORY_ID);
                // Ces informations sont maintenant redondantes car elles seront récupérées dynamiquement
                // request.setClientId(driverProfile.getUserId());
                // request.setClientName(driverProfile.getFirstName() + " " + driverProfile.getLastName());
                // request.setClientPhoneNumber(driverProfile.getPhoneNumber());
                // request.setClientProfileImageUrl(driverProfile.getProfileImageUrl());
                
                return resourceService.createProduct(userContext.getOrganisation().getOrganizationId(), request, authorizationHeader, null);
            })
            .map(createdProduct -> new ResponseEntity<>(createdProduct, HttpStatus.CREATED));
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
            .flatMap(userContext -> 
                resourceService.updateProduct(userContext.getOrganisation().getOrganizationId(), planningId, request, authorizationHeader, null)
            )
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
            .flatMap(userContext -> 
                resourceService.deleteProduct(userContext.getOrganisation().getOrganizationId(), planningId, authorizationHeader, null)
            )
            .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)));
    }
    
    /**
     * SECURISE (CLIENT): Permet à un client de réserver un planning.
     */
    @PostMapping("/{planningId}/accept")
    public Mono<ResponseEntity<Product>> acceptPlanning(
            @PathVariable UUID planningId,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(clientContext -> {
                if (!(clientContext.getProfile() instanceof ClientProfile clientProfile)) {
                    return Mono.error(new IllegalStateException("Seul un client peut accepter un planning."));
                }
                
                return productRepository.findAll().filter(p -> p.getId().equals(planningId)).next()
                    .flatMap(planning -> {
                        ProductKey key = new ProductKey(planning.getOrganizationId(), planningId);
                        return productRepository.findById(key)
                            .flatMap(plan -> {
                                if (plan.getReservedByDriverId() != null) {
                                    return Mono.error(new IllegalStateException("Ce planning a déjà été réservé."));
                                }
                                plan.setReservedByDriverId(clientProfile.getUserId());
                                plan.setReservedByDriverName(clientProfile.getFirstName() + " " + clientProfile.getLastName());
                                plan.setStatus("Confirmed");
                                return productRepository.save(plan);
                            })
                            .doOnSuccess(updatedPlanning -> {
                                socketIOServer.getBroadcastOperations().sendEvent("updated_planning", updatedPlanning);
                                // TODO: Notifier le CHAUFFEUR que son planning a été réservé
                            });
                    });
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * SECURISE (CLIENT): Récupère les plannings que le client connecté a réservés.
     */
    @GetMapping("/my-reservations")
    public Flux<Product> getMyReservedRides(@AuthenticationPrincipal Mono<Jwt> jwtMono) {
        return jwtMono
            .map(JwtUtil::getUserIdFromToken)
            .flatMapMany(clientId -> {
                log.info("Récupération des courses réservées par le client {}", clientId);
                return productRepository.findByReservedByDriverId(clientId);
            });
    }

    /**
     * Méthode privée pour enrichir un produit avec les détails de son auteur (chauffeur).
     */
    private Mono<Product> enrichProductWithAuthorDetails(Product product) {
        // On suppose que product.getClientId() contient l'ID du chauffeur
        UUID authorId = product.getClientId();
        if (authorId == null) {
            return Mono.just(product); // Retourne le produit tel quel si pas d'auteur
        }

        return profileService.findDriverById(authorId)
            .map(driverProfile -> {
                product.setAuthorId(driverProfile.getUserId());
                product.setAuthorName(driverProfile.getFirstName() + " " + driverProfile.getLastName());
                product.setAuthorPhoneNumber(driverProfile.getPhoneNumber());
                product.setAuthorProfileImageUrl(driverProfile.getProfileImageUrl());
                return product;
            })
            .defaultIfEmpty(product); // Si le chauffeur n'est pas trouvé, retourne le produit original
    }
}