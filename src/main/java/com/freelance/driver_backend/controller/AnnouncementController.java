
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
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
@Slf4j
public class AnnouncementController {

    private final ResourceService resourceService;
    private final ProfileService profileService;
    private final ProductRepository productRepository; 
    private final NotificationTriggerService notificationTriggerService;
    private final SocketIOServer socketIOServer; 

    private static final UUID ANNOUNCEMENT_CATEGORY_ID = UUID.fromString("c1a5b4e0-1234-5678-9abc-def012345678");

    /**
     * PUBLIC: Récupère toutes les annonces publiées par les clients.
     */
    @GetMapping
    public Flux<Product> getPublishedAnnouncements() {
        log.info("Controller: Requête publique pour récupérer les annonces publiées.");
        return productRepository.findByCategoryId(ANNOUNCEMENT_CATEGORY_ID)
                .filter(product -> "Published".equalsIgnoreCase(product.getStatus()))
                .flatMap(this::enrichProductWithAuthorDetails);
    }

    /**
     * SECURISE (CLIENT): Récupère les annonces du client actuellement connecté.
     */
    @GetMapping("/my-announcements")
    public Flux<Product> getMyAnnouncements(
            @AuthenticationPrincipal Mono<Jwt> jwtMono) {
        return jwtMono
            .map(JwtUtil::getUserIdFromToken)
            .flatMapMany(clientId -> 
                productRepository.findByClientIdAndCategoryId(clientId, ANNOUNCEMENT_CATEGORY_ID)
            );
    }

    /**
     * SECURISE (CLIENT): Crée une nouvelle annonce pour le client connecté.
     */
    @PostMapping
    public Mono<ResponseEntity<Product>> createAnnouncement(
            @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                if (!(userContext.getProfile() instanceof ClientProfile clientProfile)) {
                    return Mono.error(new IllegalStateException("Seul un client peut créer une annonce."));
                }
                
                request.setCategoryId(ANNOUNCEMENT_CATEGORY_ID);
                // Ces informations sont maintenant redondantes car elles seront récupérées dynamiquement
                // request.setClientId(clientProfile.getUserId());
                // request.setClientName(clientProfile.getFirstName() + " " + clientProfile.getLastName());
                // request.setClientPhoneNumber(clientProfile.getPhoneNumber());
                // request.setClientProfileImageUrl(clientProfile.getProfileImageUrl());

                return resourceService.createProduct(userContext.getOrganisation().getOrganizationId(), request, authorizationHeader, null);
            })
            .map(createdProduct -> new ResponseEntity<>(createdProduct, HttpStatus.CREATED));
    }

    /**
     * SECURISE (CLIENT): Met à jour une annonce appartenant au client connecté.
     */
    @PutMapping("/{productId}")
    public Mono<ResponseEntity<Product>> updateAnnouncement(
            @PathVariable UUID productId,
            @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {
        
        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                // (Ajouter une vérification pour s'assurer que le client est bien le propriétaire de l'annonce)
                return resourceService.updateProduct(userContext.getOrganisation().getOrganizationId(), productId, request, authorizationHeader, null);
            })
            .doOnSuccess(updatedAnnouncement -> {
                if (updatedAnnouncement != null) {
                    socketIOServer.getBroadcastOperations().sendEvent("updated_announcement", updatedAnnouncement);
                }
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * SECURISE (CLIENT): Supprime une annonce appartenant au client connecté.
     */
    @DeleteMapping("/{productId}")
    public Mono<ResponseEntity<Void>> deleteAnnouncement(
            @PathVariable UUID productId,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> 
                resourceService.deleteProduct(userContext.getOrganisation().getOrganizationId(), productId, authorizationHeader, null)
            )
            .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)));
    }

    /**
     * SECURISE (CONDUCTEUR): Permet à un conducteur d'accepter une annonce.
     */
    @PostMapping("/{announcementId}/accept")
    public Mono<ResponseEntity<Product>> acceptAnnouncement(
            @PathVariable UUID announcementId,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(driverContext -> {
                if (!(driverContext.getProfile() instanceof DriverProfile driverProfile)) {
                    return Mono.error(new IllegalStateException("Seul un chauffeur peut accepter une annonce."));
                }
                // L'ID de l'organisation doit être récupéré du contexte du client qui a posté l'annonce.
                // Pour ce faire, on doit d'abord trouver l'annonce.
                return productRepository.findAll().filter(p -> p.getId().equals(announcementId)).next()
                    .flatMap(announcement -> {
                         ProductKey key = new ProductKey(announcement.getOrganizationId(), announcementId);
                         return productRepository.findById(key)
                            .flatMap(ann -> {
                                if (ann.getReservedByDriverId() != null) {
                                    return Mono.error(new IllegalStateException("Cette annonce a déjà été réservée."));
                                }
                                ann.setReservedByDriverId(driverProfile.getUserId());
                                ann.setReservedByDriverName(driverProfile.getFirstName() + " " + driverProfile.getLastName());
                                ann.setStatus("Confirmed");
                                return productRepository.save(ann);
                            })
                            .flatMap(updatedAnnouncement -> {
                                socketIOServer.getBroadcastOperations().sendEvent("updated_announcement", updatedAnnouncement);
                                return notificationTriggerService.notifyClientOfAcceptedAnnouncement(updatedAnnouncement, driverProfile)
                                    .thenReturn(updatedAnnouncement);
                            });
                    });
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * SECURISE (CONDUCTEUR): Récupère les courses que le conducteur connecté a acceptées.
     */
    @GetMapping("/my-rides")
    public Flux<Product> getMyAcceptedRides(@AuthenticationPrincipal Mono<Jwt> jwtMono) {
        return jwtMono
            .map(JwtUtil::getUserIdFromToken)
            .flatMapMany(driverId -> {
                log.info("Récupération des courses acceptées par le chauffeur {}", driverId);
                return productRepository.findByReservedByDriverId(driverId);
            });
    }

    /**
     * Méthode privée pour enrichir une annonce avec les détails de son auteur (client).
     */
    private Mono<Product> enrichProductWithAuthorDetails(Product product) {
        // On suppose que product.getClientId() contient l'ID du client
        UUID authorId = product.getClientId();
        if (authorId == null) {
            return Mono.just(product); // Retourne le produit tel quel si pas d'auteur
        }

        return profileService.findClientById(authorId)
            .map(clientProfile -> {
                product.setAuthorId(clientProfile.getUserId());
                product.setAuthorName(clientProfile.getFirstName() + " " + clientProfile.getLastName());
                product.setAuthorPhoneNumber(clientProfile.getPhoneNumber());
                product.setAuthorProfileImageUrl(clientProfile.getProfileImageUrl());
                return product;
            })
            .defaultIfEmpty(product); // Si le client n'est pas trouvé, retourne le produit original
    }
}