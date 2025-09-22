/* 

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

   
    @GetMapping
    public Flux<Product> getPublishedAnnouncements() {
        log.info("Controller: Requête publique pour récupérer les annonces publiées.");
        return productRepository.findByCategoryId(ANNOUNCEMENT_CATEGORY_ID)
                .filter(product -> "Published".equalsIgnoreCase(product.getStatus()))
                .flatMap(this::enrichProductWithAuthorDetails);
    }

    
    @GetMapping("/my-announcements")
    public Flux<Product> getMyAnnouncements(
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) { 
        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMapMany(userContext -> {
                if (userContext.getClientProfile() == null) {
                     return Flux.error(new IllegalStateException("L'utilisateur n'est pas un client."));
                }
                return productRepository.findByClientIdAndCategoryId(userContext.getUserId(), ANNOUNCEMENT_CATEGORY_ID);
            });
    }

    
    @PostMapping
    public Mono<ResponseEntity<Product>> createAnnouncement(
            @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                if (userContext.getClientProfile() == null) {
                    return Mono.error(new IllegalStateException("Seul un client peut créer une annonce."));
                }
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide pour créer une annonce (organisation manquante)."));
                }
                ClientProfile clientProfile = userContext.getClientProfile(); 
                
                request.setCategoryId(ANNOUNCEMENT_CATEGORY_ID);
                request.setClientId(clientProfile.getUserId());
                request.setClientName(clientProfile.getFirstName() + " " + clientProfile.getLastName());
                request.setClientPhoneNumber(clientProfile.getPhoneNumber());
                request.setClientProfileImageUrl(clientProfile.getProfileImageUrl());

                return resourceService.createProduct(userContext.getOrganisation().getOrganizationId(), request, authorizationHeader, null);
            })
            .map(createdProduct -> new ResponseEntity<>(createdProduct, HttpStatus.CREATED));
    }

    
    @PutMapping("/{productId}")
    public Mono<ResponseEntity<Product>> updateAnnouncement(
            @PathVariable UUID productId,
            @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {
        
        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                if (userContext.getClientProfile() == null) {
                    return Mono.error(new IllegalStateException("Seuls les clients peuvent modifier leurs annonces."));
                }
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide pour mettre à jour une annonce (organisation manquante)."));
                }
                ClientProfile clientProfile = userContext.getClientProfile();

                return productRepository.findById(new ProductKey(userContext.getOrganisation().getOrganizationId(), productId))
                        .filter(product -> clientProfile.getUserId().equals(product.getClientId()))
                        .switchIfEmpty(Mono.error(new SecurityException("Le client n'est pas autorisé à modifier cette annonce ou elle n'existe pas.")))
                        .flatMap(existingAnnouncement -> {
                            return resourceService.updateProduct(userContext.getOrganisation().getOrganizationId(), productId, request, authorizationHeader, null);
                        });
            })
            .doOnSuccess(updatedAnnouncement -> {
                if (updatedAnnouncement != null) {
                    socketIOServer.getBroadcastOperations().sendEvent("updated_announcement", updatedAnnouncement);
                }
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    
    @DeleteMapping("/{productId}")
    public Mono<ResponseEntity<Void>> deleteAnnouncement(
            @PathVariable UUID productId,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                if (userContext.getClientProfile() == null) {
                    return Mono.error(new IllegalStateException("Seuls les clients peuvent supprimer leurs annonces."));
                }
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide pour supprimer une annonce (organisation manquante)."));
                }
                ClientProfile clientProfile = userContext.getClientProfile();

                return productRepository.findById(new ProductKey(userContext.getOrganisation().getOrganizationId(), productId))
                        .filter(product -> clientProfile.getUserId().equals(product.getClientId()))
                        .switchIfEmpty(Mono.error(new SecurityException("Le client n'est pas autorisé à supprimer cette annonce ou elle n'existe pas.")))
                        .flatMap(existingAnnouncement ->
                            resourceService.deleteProduct(userContext.getOrganisation().getOrganizationId(), productId, authorizationHeader, null)
                        );
            })
            .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)));
    }

  
    @PostMapping("/{announcementId}/apply")
    public Mono<ResponseEntity<Product>> applyToAnnouncement(
            @PathVariable UUID announcementId,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(driverContext -> {
                if (driverContext.getDriverProfile() == null) {
                    return Mono.error(new IllegalStateException("Seul un chauffeur peut postuler à une annonce."));
                }
                DriverProfile driverProfile = driverContext.getDriverProfile();
                
                return productRepository.findAll().filter(p -> p.getId().equals(announcementId)).next()
                    .flatMap(announcement -> {
                         ProductKey key = new ProductKey(announcement.getOrganizationId(), announcementId);
                         return productRepository.findById(key)
                            .flatMap(ann -> {
                                if (ann.getReservedByDriverId() != null) {
                                    return Mono.error(new IllegalStateException("Cette annonce a déjà été postulée/réservée."));
                                }
                                ann.setReservedByDriverId(driverProfile.getUserId());
                                ann.setReservedByDriverName(driverProfile.getFirstName() + " " + driverProfile.getLastName());
                                ann.setStatus("PendingConfirmation");
                                log.info("Chauffeur {} a postulé pour l'annonce {}. Statut mis à jour à 'PendingConfirmation'.", driverProfile.getUserId(), announcementId);
                                return productRepository.save(ann);
                            })
                            .flatMap(updatedAnnouncement -> {
                                socketIOServer.getBroadcastOperations().sendEvent("updated_announcement", updatedAnnouncement);
                                log.info("Déclenchement de la notification pour le client de l'annonce {} (postulation par chauffeur {}).", announcementId, driverProfile.getUserId());
                                return notificationTriggerService.notifyClientOfAcceptedAnnouncement(updatedAnnouncement, driverProfile)
                                    .thenReturn(updatedAnnouncement);
                            });
                    });
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

   
    @PostMapping("/{announcementId}/cancel-postulation") // <-- NOUVEL ENDPOINT POUR L'ANNULATION
    public Mono<ResponseEntity<Product>> cancelPostulation(
            @PathVariable UUID announcementId,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(driverContext -> {
                if (driverContext.getDriverProfile() == null) {
                    return Mono.error(new IllegalStateException("Seul un chauffeur peut annuler une postulation."));
                }
                DriverProfile driverProfile = driverContext.getDriverProfile();

                return productRepository.findAll().filter(p -> p.getId().equals(announcementId)).next()
                    .flatMap(announcement -> {
                        // Vérifier si le chauffeur est bien celui qui a postulé et si le statut est "PendingConfirmation" ou "Ongoing"
                        if (!driverProfile.getUserId().equals(announcement.getReservedByDriverId())) {
                            return Mono.error(new SecurityException("Vous n'avez pas postulé à cette annonce ou n'êtes pas le chauffeur concerné."));
                        }
                        if (!"PendingConfirmation".equalsIgnoreCase(announcement.getStatus()) && !"Ongoing".equalsIgnoreCase(announcement.getStatus())) {
                            return Mono.error(new IllegalStateException("L'annonce n'est pas dans un état permettant l'annulation de postulation (statut actuel: " + announcement.getStatus() + ")."));
                        }

                        ProductKey key = new ProductKey(announcement.getOrganizationId(), announcementId);
                        return productRepository.findById(key)
                            .flatMap(ann -> {
                                ann.setReservedByDriverId(null);
                                ann.setReservedByDriverName(null);
                                ann.setStatus("Published"); // Revenir au statut "Published"
                                log.info("Chauffeur {} a annulé sa postulation pour l'annonce {}. Statut remis à 'Published'.", driverProfile.getUserId(), announcementId);
                                return productRepository.save(ann);
                            })
                            .flatMap(updatedAnnouncement -> {
                                socketIOServer.getBroadcastOperations().sendEvent("updated_announcement", updatedAnnouncement);
                                log.info("Déclenchement de la notification pour le client de l'annonce {} (annulation de postulation par chauffeur {}).", announcementId, driverProfile.getUserId());
                                // Remplacez la ligne suivante par la méthode correcte de notification
                                return notificationTriggerService.notifyClientOfAcceptedAnnouncement(updatedAnnouncement, driverProfile)
                                    .thenReturn(updatedAnnouncement);
                            });
                    });
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    
    @PostMapping("/{announcementId}/confirm")
    public Mono<ResponseEntity<Product>> confirmDriverForAnnouncement(
            @PathVariable UUID announcementId,
            @RequestParam UUID driverId, 
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(clientContext -> {
                if (clientContext.getClientProfile() == null) {
                    return Mono.error(new IllegalStateException("Seul un client peut confirmer un chauffeur pour son annonce."));
                }
                ClientProfile clientProfile = clientContext.getClientProfile();

                return productRepository.findAll().filter(p -> p.getId().equals(announcementId)).next()
                    .flatMap(announcement -> {
                        if (!announcement.getClientId().equals(clientProfile.getUserId())) {
                            return Mono.error(new SecurityException("Le client n'est pas l'auteur de cette annonce."));
                        }
                        if (!"PendingConfirmation".equalsIgnoreCase(announcement.getStatus()) || !announcement.getReservedByDriverId().equals(driverId)) {
                             return Mono.error(new IllegalStateException("Cette annonce n'est pas en attente de confirmation pour ce chauffeur, ou le statut est incorrect."));
                        }

                        ProductKey key = new ProductKey(announcement.getOrganizationId(), announcementId);
                        return productRepository.findById(key)
                            .flatMap(ann -> {
                                ann.setStatus("Ongoing");
                                log.info("Client {} a confirmé le chauffeur {} pour l'annonce {}. Statut mis à jour à 'Ongoing'.", clientProfile.getUserId(), driverId, announcementId);
                                return productRepository.save(ann);
                            })
                            .flatMap(updatedAnnouncement -> 
                                profileService.findDriverById(driverId)
                                    .flatMap(driverProfile -> notificationTriggerService.notifyDriverOfConfirmedAnnouncement(updatedAnnouncement, driverProfile))
                                    .thenReturn(updatedAnnouncement)
                            );
                    });
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/my-rides")
    public Flux<Product> getMyAcceptedRides(@AuthenticationPrincipal Mono<Jwt> jwtMono,
                                            @RequestHeader("Authorization") String authorizationHeader) {
        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMapMany(userContext -> {
                 if (userContext.getDriverProfile() == null) {
                     return Flux.error(new IllegalStateException("L'utilisateur n'est pas un chauffeur."));
                }
                UUID driverId = userContext.getUserId();
                log.info("Récupération des courses acceptées/postulées par le chauffeur {}", driverId);
                
                return productRepository.findByReservedByDriverId(driverId) // Récupère les produits où ce driver est réservé
                           .flatMap(this::enrichProductWithAuthorDetails) // <-- AJOUT CRUCIAL : Enrichir avec les détails du CLIENT auteur
                           .doOnNext(product -> {
                               // Debugging log pour voir les produits enrichis avant de les envoyer
                               log.info("DEBUG: getMyAcceptedRides - Produit enrichi pour driver {}: Title='{}', AuthorName='{}', Cost={}", 
                                        driverId, product.getName(), product.getAuthorName(), product.getDefaultSellPrice());
                           });
            });
    }


  
    private Mono<Product> enrichProductWithAuthorDetails(Product product) {
        UUID authorId = product.getClientId();
        if (authorId == null) {
            return Mono.just(product);
        }

        return profileService.findClientById(authorId)
            .map(clientProfile -> {
                product.setAuthorId(clientProfile.getUserId());
                product.setAuthorName(clientProfile.getFirstName() + " " + clientProfile.getLastName());
                product.setAuthorPhoneNumber(clientProfile.getPhoneNumber());
                product.setAuthorProfileImageUrl(clientProfile.getProfileImageUrl());
                return product;
            })
            .defaultIfEmpty(product); 
    }
}
*/

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
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) { 
        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMapMany(userContext -> {
                if (userContext.getClientProfile() == null) {
                     return Flux.error(new IllegalStateException("L'utilisateur n'est pas un client."));
                }
                return productRepository.findByClientIdAndCategoryId(userContext.getUserId(), ANNOUNCEMENT_CATEGORY_ID);
            });
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
                if (userContext.getClientProfile() == null) {
                    return Mono.error(new IllegalStateException("Seul un client peut créer une annonce."));
                }
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide pour créer une annonce (organisation manquante)."));
                }
                ClientProfile clientProfile = userContext.getClientProfile(); 
                
                request.setCategoryId(ANNOUNCEMENT_CATEGORY_ID);
                request.setClientId(clientProfile.getUserId());
                request.setClientName(clientProfile.getFirstName() + " " + clientProfile.getLastName());
                request.setClientPhoneNumber(clientProfile.getPhoneNumber());
                request.setClientProfileImageUrl(clientProfile.getProfileImageUrl());

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
                if (userContext.getClientProfile() == null) {
                    return Mono.error(new IllegalStateException("Seuls les clients peuvent modifier leurs annonces."));
                }
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide pour mettre à jour une annonce (organisation manquante)."));
                }
                ClientProfile clientProfile = userContext.getClientProfile();

                return productRepository.findById(new ProductKey(userContext.getOrganisation().getOrganizationId(), productId))
                        .filter(product -> clientProfile.getUserId().equals(product.getClientId()))
                        .switchIfEmpty(Mono.error(new SecurityException("Le client n'est pas autorisé à modifier cette annonce ou elle n'existe pas.")))
                        .flatMap(existingAnnouncement -> {
                            return resourceService.updateProduct(userContext.getOrganisation().getOrganizationId(), productId, request, authorizationHeader, null);
                        });
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
            .flatMap(userContext -> {
                if (userContext.getClientProfile() == null) {
                    return Mono.error(new IllegalStateException("Seuls les clients peuvent supprimer leurs annonces."));
                }
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide pour supprimer une annonce (organisation manquante)."));
                }
                ClientProfile clientProfile = userContext.getClientProfile();

                return productRepository.findById(new ProductKey(userContext.getOrganisation().getOrganizationId(), productId))
                        .filter(product -> clientProfile.getUserId().equals(product.getClientId()))
                        .switchIfEmpty(Mono.error(new SecurityException("Le client n'est pas autorisé à supprimer cette annonce ou elle n'existe pas.")))
                        .flatMap(existingAnnouncement ->
                            resourceService.deleteProduct(userContext.getOrganisation().getOrganizationId(), productId, authorizationHeader, null)
                        );
            })
            .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)));
    }

    /**
     * SECURISE (CONDUCTEUR): Permet à un conducteur de POSTULER à une annonce.
     * Le statut de l'annonce passe à "PendingConfirmation". Une notification est envoyée au client.
     */
    @PostMapping("/{announcementId}/apply")
    public Mono<ResponseEntity<Product>> applyToAnnouncement(
            @PathVariable UUID announcementId,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(driverContext -> {
                if (driverContext.getDriverProfile() == null) {
                    return Mono.error(new IllegalStateException("Seul un chauffeur peut postuler à une annonce."));
                }
                DriverProfile driverProfile = driverContext.getDriverProfile();
                
                return productRepository.findAll().filter(p -> p.getId().equals(announcementId)).next()
                    .flatMap(announcement -> {
                         ProductKey key = new ProductKey(announcement.getOrganizationId(), announcementId);
                         return productRepository.findById(key)
                            .flatMap(ann -> {
                                if (ann.getReservedByDriverId() != null) {
                                    return Mono.error(new IllegalStateException("Cette annonce a déjà été postulée/réservée."));
                                }
                                ann.setReservedByDriverId(driverProfile.getUserId());
                                ann.setReservedByDriverName(driverProfile.getFirstName() + " " + driverProfile.getLastName());
                                ann.setStatus("PendingConfirmation");
                                log.info("Chauffeur {} a postulé pour l'annonce {}. Statut mis à jour à 'PendingConfirmation'.", driverProfile.getUserId(), announcementId);
                                return productRepository.save(ann);
                            })
                            .flatMap(updatedAnnouncement -> {
                                socketIOServer.getBroadcastOperations().sendEvent("updated_announcement", updatedAnnouncement);
                                log.info("Déclenchement de la notification pour le client de l'annonce {} (postulation par chauffeur {}).", announcementId, driverProfile.getUserId());
                                return notificationTriggerService.notifyClientOfAcceptedAnnouncement(updatedAnnouncement, driverProfile)
                                    .thenReturn(updatedAnnouncement);
                            });
                    });
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * SECURISE (CONDUCTEUR): Permet à un CONDUCTEUR d'annuler une postulation.
     * Le statut de l'annonce revient à "Published". Une notification est envoyée au client.
     */
    @PostMapping("/{announcementId}/cancel-postulation") // <-- NOUVEL ENDPOINT POUR L'ANNULATION
    public Mono<ResponseEntity<Product>> cancelPostulation(
            @PathVariable UUID announcementId,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(driverContext -> {
                if (driverContext.getDriverProfile() == null) {
                    return Mono.error(new IllegalStateException("Seul un chauffeur peut annuler une postulation."));
                }
                DriverProfile driverProfile = driverContext.getDriverProfile();

                return productRepository.findAll().filter(p -> p.getId().equals(announcementId)).next()
                    .flatMap(announcement -> {
                        if (!driverProfile.getUserId().equals(announcement.getReservedByDriverId())) {
                            return Mono.error(new SecurityException("Vous n'avez pas postulé à cette annonce ou n'êtes pas le chauffeur concerné."));
                        }
                        if (!"PendingConfirmation".equalsIgnoreCase(announcement.getStatus()) && !"Ongoing".equalsIgnoreCase(announcement.getStatus())) {
                            return Mono.error(new IllegalStateException("L'annonce n'est pas dans un état permettant l'annulation de postulation (statut actuel: " + announcement.getStatus() + ")."));
                        }

                        ProductKey key = new ProductKey(announcement.getOrganizationId(), announcementId);
                        return productRepository.findById(key)
                            .flatMap(ann -> {
                                ann.setReservedByDriverId(null);
                                ann.setReservedByDriverName(null);
                                ann.setStatus("Published"); // Revenir au statut "Published"
                                log.info("Chauffeur {} a annulé sa postulation pour l'annonce {}. Statut remis à 'Published'.", driverProfile.getUserId(), announcementId);
                                return productRepository.save(ann);
                            })
                            .flatMap(updatedAnnouncement -> {
                                socketIOServer.getBroadcastOperations().sendEvent("updated_announcement", updatedAnnouncement);
                                log.info("Déclenchement de la notification pour le client de l'annonce {} (annulation de postulation par chauffeur {}).", announcementId, driverProfile.getUserId());
                                return notificationTriggerService.notifyClientOfCancelledPostulation(updatedAnnouncement, driverProfile)
                                    .thenReturn(updatedAnnouncement);
                            });
                    });
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * SECURISE (CLIENT): Permet au CLIENT de confirmer (accepter) un chauffeur qui a postulé à son annonce.
     * Le statut de l'annonce passe à "Ongoing" et une notification est envoyée au chauffeur.
     */
    @PostMapping("/{announcementId}/confirm")
    public Mono<ResponseEntity<Product>> confirmDriverForAnnouncement(
            @PathVariable UUID announcementId,
            @RequestParam UUID driverId, 
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(clientContext -> {
                if (clientContext.getClientProfile() == null) {
                    return Mono.error(new IllegalStateException("Seul un client peut confirmer un chauffeur pour son annonce."));
                }
                ClientProfile clientProfile = clientContext.getClientProfile();

                return productRepository.findAll().filter(p -> p.getId().equals(announcementId)).next()
                    .flatMap(announcement -> {
                        if (!announcement.getClientId().equals(clientProfile.getUserId())) {
                            return Mono.error(new SecurityException("Le client n'est pas l'auteur de cette annonce."));
                        }
                        if (!"PendingConfirmation".equalsIgnoreCase(announcement.getStatus()) || !announcement.getReservedByDriverId().equals(driverId)) {
                             return Mono.error(new IllegalStateException("Cette annonce n'est pas en attente de confirmation pour ce chauffeur, ou le statut est incorrect."));
                        }

                        ProductKey key = new ProductKey(announcement.getOrganizationId(), announcementId);
                        return productRepository.findById(key)
                            .flatMap(ann -> {
                                ann.setStatus("Ongoing");
                                log.info("Client {} a confirmé le chauffeur {} pour l'annonce {}. Statut mis à jour à 'Ongoing'.", clientProfile.getUserId(), driverId, announcementId);
                                return productRepository.save(ann);
                            })
                            .flatMap(updatedAnnouncement -> 
                                profileService.findDriverById(driverId)
                                    .flatMap(driverProfile -> notificationTriggerService.notifyDriverOfConfirmedAnnouncement(updatedAnnouncement, driverProfile))
                                    .thenReturn(updatedAnnouncement)
                            );
                    });
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * SECURISE (CONDUCTEUR): Récupère les courses que le conducteur connecté a acceptées.
     */
    @GetMapping("/my-rides")
    public Flux<Product> getMyAcceptedRides(@AuthenticationPrincipal Mono<Jwt> jwtMono,
                                            @RequestHeader("Authorization") String authorizationHeader) {
        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMapMany(userContext -> {
                 if (userContext.getDriverProfile() == null) {
                     return Flux.error(new IllegalStateException("L'utilisateur n'est pas un chauffeur."));
                }
                UUID driverId = userContext.getUserId();
                log.info("Récupération des courses acceptées/postulées par le chauffeur {}", driverId);
                
                return productRepository.findByReservedByDriverId(driverId)
                           .flatMap(this::enrichProductWithAuthorDetails)
                           .doOnNext(product -> {
                               // Debugging log pour voir les produits enrichis avant de les envoyer
                               log.info("DEBUG_BACKEND_OFFERS: Produit complet enrichi envoyé pour driver {}: Id='{}', Title='{}', AuthorName='{}', Cost={}, ReservedByDriverName='{}'", 
                                        driverId, product.getId(), product.getName(), product.getAuthorName(), product.getDefaultSellPrice(), product.getReservedByDriverName());
                           });
            });
    }


    /**
     * Méthode privée pour enrichir une annonce avec les détails de son auteur (client).
     */
    private Mono<Product> enrichProductWithAuthorDetails(Product product) {
        UUID authorId = product.getClientId();
        if (authorId == null) {
            return Mono.just(product);
        }

        return profileService.findClientById(authorId)
            .map(clientProfile -> {
                product.setAuthorId(clientProfile.getUserId());
                product.setAuthorName(clientProfile.getFirstName() + " " + clientProfile.getLastName());
                product.setAuthorPhoneNumber(clientProfile.getPhoneNumber());
                product.setAuthorProfileImageUrl(clientProfile.getProfileImageUrl());
                return product;
            })
            .defaultIfEmpty(product); 
    }
}