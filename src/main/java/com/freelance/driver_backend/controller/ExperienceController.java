
// PATH: /home/mbogneng-junior/freelance-driver (Copie)/backend/src/main/java/com/freelance/driver_backend/controller/ExperienceController.java

package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.dto.CreateProductRequest; // Ajouté pour les méthodes POST/PUT
import com.freelance.driver_backend.dto.UserSessionContextDto; // Nouveau import
import com.freelance.driver_backend.model.DriverProfile; // Nouveau import
import com.freelance.driver_backend.model.Product;
import com.freelance.driver_backend.service.ProfileService;
import com.freelance.driver_backend.service.ResourceService;
import com.freelance.driver_backend.util.JwtUtil; // Ajouté pour les méthodes POST/PUT
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
@RequestMapping("/api/experiences")
@RequiredArgsConstructor
@Slf4j
public class ExperienceController {

    private final ProfileService profileService;
    private final ResourceService resourceService;

    // UUID fixe pour la catégorie "expériences"
    private static final UUID EXPERIENCE_CATEGORY_ID = UUID.fromString("e1f2a3b4-c5d6-7890-1234-567890abcdef");
    private static final UUID DRIVER_LICENSE_CATEGORY_ID = UUID.fromString("f1c2b3d4-e5f6-7890-1234-567890abcdef"); // Du frontend
    private static final UUID CV_CATEGORY_ID = UUID.fromString("a1b2c3d4-e5f6-7890-1234-567890fedcba"); // Du frontend


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

        // MODIFIÉ : Récupère l'organisation ID du userId spécifié.
        // Puis, filtre les produits de cette organisation qui appartiennent à ce userId.
        return profileService.findOrganisationIdByUserId(userId)
                .flatMapMany(orgId -> resourceService.getProductsByCategory(orgId, EXPERIENCE_CATEGORY_ID, token, null)
                                                    .filter(product -> userId.equals(product.getClientId())));
    }
    
    /**
     * SECURISE (CONDUCTEUR): Crée une nouvelle expérience/document pour le chauffeur connecté.
     */
    @PostMapping
    public Mono<ResponseEntity<Product>> createExperienceOrDocument(
            @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                if (userContext.getDriverProfile() == null) {
                    return Mono.error(new IllegalStateException("Seuls les chauffeurs peuvent gérer leurs expériences/documents."));
                }
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide (organisation manquante)."));
                }
                DriverProfile driverProfile = userContext.getDriverProfile();

                // Assurez-vous que la catégorie est définie dans la requête.
                if (request.getCategoryId() == null ||
                    (!request.getCategoryId().equals(EXPERIENCE_CATEGORY_ID) &&
                     !request.getCategoryId().equals(DRIVER_LICENSE_CATEGORY_ID) &&
                     !request.getCategoryId().equals(CV_CATEGORY_ID))) {
                    return Mono.error(new IllegalArgumentException("Catégorie d'expérience/document invalide ou manquante."));
                }

                request.setClientId(driverProfile.getUserId()); // L'ID du chauffeur est le propriétaire
                request.setClientName(driverProfile.getFirstName() + " " + driverProfile.getLastName());
                request.setClientPhoneNumber(driverProfile.getPhoneNumber());
                request.setClientProfileImageUrl(driverProfile.getProfileImageUrl());
                
                log.info("▶️ Controller: Création d'une expérience/document (catégorie: {}) pour le chauffeur {}", request.getCategoryId(), driverProfile.getUserId());
                
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
     * SECURISE (CONDUCTEUR): Met à jour une expérience/document existant.
     */
    @PutMapping("/{productId}")
    public Mono<ResponseEntity<Product>> updateExperienceOrDocument(
            @PathVariable UUID productId,
            @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {
        
        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                if (userContext.getDriverProfile() == null) {
                    return Mono.error(new IllegalStateException("Seuls les chauffeurs peuvent modifier leurs expériences/documents."));
                }
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide (organisation manquante)."));
                }
                DriverProfile driverProfile = userContext.getDriverProfile();

                 // Assurez-vous que la catégorie est définie dans la requête.
                if (request.getCategoryId() == null ||
                    (!request.getCategoryId().equals(EXPERIENCE_CATEGORY_ID) &&
                     !request.getCategoryId().equals(DRIVER_LICENSE_CATEGORY_ID) &&
                     !request.getCategoryId().equals(CV_CATEGORY_ID))) {
                    return Mono.error(new IllegalArgumentException("Catégorie d'expérience/document invalide ou manquante."));
                }
                
                // Vérification cruciale : l'utilisateur connecté est-il le propriétaire ?
                return resourceService.getProductsByCategory(userContext.getOrganisation().getOrganizationId(), request.getCategoryId(), authorizationHeader, null)
                        .filter(product -> product.getId().equals(productId) && driverProfile.getUserId().equals(product.getClientId()))
                        .next()
                        .switchIfEmpty(Mono.error(new SecurityException("Le chauffeur n'est pas autorisé à modifier cet élément ou il n'existe pas.")))
                        .flatMap(existingProduct -> {
                            request.setClientId(driverProfile.getUserId()); // Assure que le propriétaire reste le même
                            request.setClientName(driverProfile.getFirstName() + " " + driverProfile.getLastName());
                            request.setClientPhoneNumber(driverProfile.getPhoneNumber());
                            request.setClientProfileImageUrl(driverProfile.getProfileImageUrl());

                            log.info("▶️ Controller: Mise à jour de l'expérience/document ID: {}", productId);
                            
                            return resourceService.updateProduct(
                                userContext.getOrganisation().getOrganizationId(), 
                                productId,
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
     * SECURISE (CONDUCTEUR): Supprime une expérience/document existant.
     */
    @DeleteMapping("/{productId}")
    public Mono<ResponseEntity<Void>> deleteExperienceOrDocument(
            @PathVariable UUID productId,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                if (userContext.getDriverProfile() == null) {
                    return Mono.error(new IllegalStateException("Seuls les chauffeurs peuvent supprimer leurs expériences/documents."));
                }
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide (organisation manquante)."));
                }
                DriverProfile driverProfile = userContext.getDriverProfile();

                // On ne peut pas facilement récupérer la catégorie ici sans re-fetch.
                // Une meilleure approche serait d'inclure la categoryId dans le path ou comme query param pour DELETE.
                // Pour l'instant, on fait un fetch large puis filtre. C'est moins performant.
                return resourceService.getProductsByCategory(userContext.getOrganisation().getOrganizationId(), EXPERIENCE_CATEGORY_ID, authorizationHeader, null)
                        .filter(product -> product.getId().equals(productId) && driverProfile.getUserId().equals(product.getClientId()))
                        .next()
                        .switchIfEmpty(Mono.error(new SecurityException("Le chauffeur n'est pas autorisé à supprimer cet élément ou il n'existe pas.")))
                        .flatMap(existingProduct -> {
                            log.info("▶️ Controller: Suppression de l'expérience/document ID: {}", productId);
                            return resourceService.deleteProduct(
                                userContext.getOrganisation().getOrganizationId(), 
                                productId,
                                authorizationHeader, 
                                null
                            );
                        });
            })
            .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)));
    }
}