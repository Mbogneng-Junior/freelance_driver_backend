/*package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.model.Resource;
import com.freelance.driver_backend.model.ResourceKey; // <-- Nouvel import
import com.freelance.driver_backend.service.ProfileService;
import com.freelance.driver_backend.service.resource.ResourceService;
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

    private static final String PLANNING_CATEGORY_ID = "ba75b2c0-30a8-11f0-a5b5-bb7d33c83c13";

    @GetMapping
    public Flux<Resource> getAllPlanningsForCurrentUser(@AuthenticationPrincipal Mono<Jwt> jwtMono, @RequestHeader("Authorization") String authorizationHeader) {
        return jwtMono
            .flatMap(jwt -> {
                UUID userId = JwtUtil.getUserIdFromToken(jwt);
                return profileService.getUserSessionContext(userId, authorizationHeader, null);
            })
            .flatMapMany(userContext -> {
                if (userContext.getOrganisation() == null) {
                    return Flux.error(new IllegalStateException("User does not have a valid organisation."));
                }
                UUID organizationId = userContext.getOrganisation().getOrganizationId();
                return resourceService.getResourcesByOrganisationAndCategory(organizationId, PLANNING_CATEGORY_ID);
            });
    }

    @PostMapping
    public Mono<ResponseEntity<Resource>> createPlanning(@RequestBody Resource planning, @AuthenticationPrincipal Mono<Jwt> jwtMono, @RequestHeader("Authorization") String authorizationHeader) {
        return jwtMono
            .flatMap(jwt -> {
                UUID userId = JwtUtil.getUserIdFromToken(jwt);
                return profileService.getUserSessionContext(userId, authorizationHeader, null);
            })
            .flatMap(userContext -> {
                if (userContext.getOrganisation() == null) {
                    return Mono.error(new IllegalStateException("Cannot create planning: user has no organisation."));
                }
                planning.setOrganizationId(userContext.getOrganisation().getOrganizationId());
                planning.setCategoryId(PLANNING_CATEGORY_ID);
                return resourceService.createResource(planning);
            })
            .map(createdPlanning -> new ResponseEntity<>(createdPlanning, HttpStatus.CREATED));
    }

    @PutMapping("/{resourceId}")
    public Mono<ResponseEntity<Resource>> updatePlanning(@PathVariable UUID resourceId, @RequestBody Resource planning, @AuthenticationPrincipal Mono<Jwt> jwtMono, @RequestHeader("Authorization") String authorizationHeader) {
        return jwtMono
            .flatMap(jwt -> {
                UUID userId = JwtUtil.getUserIdFromToken(jwt);
                return profileService.getUserSessionContext(userId, authorizationHeader, null);
            })
            .flatMap(userContext -> {
                ResourceKey key = new ResourceKey();
                key.setOrganizationId(userContext.getOrganisation().getOrganizationId());
                key.setCategoryId(PLANNING_CATEGORY_ID);
                key.setResourceId(resourceId);
                return resourceService.updateResource(key, planning);
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{resourceId}")
    public Mono<ResponseEntity<Void>> deletePlanning(@PathVariable UUID resourceId, @AuthenticationPrincipal Mono<Jwt> jwtMono, @RequestHeader("Authorization") String authorizationHeader) {
        return jwtMono
            .flatMap(jwt -> {
                UUID userId = JwtUtil.getUserIdFromToken(jwt);
                return profileService.getUserSessionContext(userId, authorizationHeader, null);
            })
            .flatMap(userContext -> {
                ResourceKey key = new ResourceKey();
                key.setOrganizationId(userContext.getOrganisation().getOrganizationId());
                key.setCategoryId(PLANNING_CATEGORY_ID);
                key.setResourceId(resourceId);
                return resourceService.deleteResource(key);
            })
            .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}*/

package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.dto.CreateProductRequest;
import com.freelance.driver_backend.model.Product;
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
import com.freelance.driver_backend.repository.ProductRepository;

@RestController
@RequestMapping("/api/planning")
@RequiredArgsConstructor
@Slf4j
public class PlanningController {

    private final ResourceService resourceService;
    private final ProfileService profileService;
    private final ProductRepository productRepository;
     private final NotificationTriggerService notificationTriggerService; // Injecter le service

    // L'ID de catégorie fixe pour les plannings
    private static final UUID PLANNING_CATEGORY_ID = UUID.fromString("ba75b2c0-30a8-11f0-a5b5-bb7d33c83c13");

    // ==============================================================================
    //                         MÉTHODES EXISTANTES (Inchangées)
    // ==============================================================================

    @GetMapping
    public Flux<Product> getAllPlanningsForCurrentUser(
            @AuthenticationPrincipal Mono<Jwt> jwtMono, 
            @RequestHeader("Authorization") String authorizationHeader) {
        
        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMapMany(userContext -> {
                if (userContext.getOrganisation() == null) {
                    return Flux.error(new IllegalStateException("User does not have a valid organisation."));
                }
                UUID organizationId = userContext.getOrganisation().getOrganizationId();
                log.info("Fetching plannings (Products) for org {}", organizationId);
                return resourceService.getProductsByCategory(organizationId, PLANNING_CATEGORY_ID, authorizationHeader, null);
            });
    }

    @PostMapping
    public Mono<ResponseEntity<Product>> createPlanning(
            @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                if (userContext.getOrganisation() == null) {
                    return Mono.error(new IllegalStateException("Cannot create planning: user has no organisation."));
                }

                request.setCategoryId(PLANNING_CATEGORY_ID);
                log.info("Controller: Creating a new planning (Product) with name: {}", request.getName());

                // On appelle le service pour créer le produit (planning)
                return resourceService.createProduct(
                    userContext.getOrganisation().getOrganizationId(),
                    request,
                    authorizationHeader,
                    null
                );
            })
            .flatMap(createdProduct -> {
                // Une fois que le produit est créé avec succès, createdProduct contient ses données.
                log.info("Planning {} créé avec succès. Déclenchement des notifications...", createdProduct.getName());

                // On déclenche l'envoi des notifications.
                // Le .subscribe() est crucial pour que l'opération s'exécute.
                // Comme c'est une opération "fire-and-forget", nous n'attendons pas sa fin.
                notificationTriggerService.notifyAllClientsOfNewPlanning(createdProduct)
                    .subscribe(
                        null, // On ne fait rien en cas de succès
                        error -> log.error("Une erreur est survenue lors de la tentative de notification.", error) // On logue l'erreur si elle survient
                    );

                // On retourne immédiatement la réponse HTTP au conducteur, sans attendre les notifications.
                return Mono.just(new ResponseEntity<>(createdProduct, HttpStatus.CREATED));
            });
        }

    // ==============================================================================
    //                       AJOUTEZ CES DEUX NOUVELLES MÉTHODES
    // ==============================================================================

    /**
     * Met à jour un planning existant.
     * @param planningId L'ID du planning à mettre à jour.
     * @param request Le corps de la requête avec les nouvelles données.
     * @return Le planning mis à jour.
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
                if (userContext.getOrganisation() == null) {
                    return Mono.error(new IllegalStateException("User does not have a valid organisation."));
                }
                
                log.info("Controller: Updating planning (Product) ID: {}", planningId);
                return resourceService.updateProduct(
                    userContext.getOrganisation().getOrganizationId(), 
                    planningId,
                    request, 
                    authorizationHeader, 
                    null
                );
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Supprime un planning.
     * @param planningId L'ID du planning à supprimer.
     * @return Une réponse vide avec le statut 204 No Content.
     */
    @DeleteMapping("/{planningId}")
    public Mono<ResponseEntity<Void>> deletePlanning(
            @PathVariable UUID planningId,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                if (userContext.getOrganisation() == null) {
                    return Mono.error(new IllegalStateException("User does not have a valid organisation."));
                }
                
                log.info("Controller: Deleting planning (Product) ID: {}", planningId);
                return resourceService.deleteProduct(
                    userContext.getOrganisation().getOrganizationId(), 
                    planningId,
                    authorizationHeader, 
                    null
                );
            })
            .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)));
    }
    @GetMapping("/published") // On utilise une URL claire : /api/planning/published
    public Flux<Product> getPublishedPlannings() {
        log.info("Controller: Requête publique pour récupérer les plannings publiés.");
        return productRepository.findByCategoryId(PLANNING_CATEGORY_ID)
                .filter(product -> "Published".equalsIgnoreCase(product.getStatus()));
    }
    // ==============================================================================
}