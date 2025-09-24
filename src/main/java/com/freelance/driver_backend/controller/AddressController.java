

// PATH: /home/mbogneng-junior/freelance-driver (Copie)/backend/src/main/java/com/freelance/driver_backend/controller/AddressController.java

package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.dto.CreateProductRequest;
import com.freelance.driver_backend.dto.UserSessionContextDto; // Nouveau import
import com.freelance.driver_backend.model.Product;
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

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
@Slf4j
public class AddressController {

    private final ProfileService profileService;
    private final ResourceService resourceService;

    // Un UUID fixe et unique pour identifier toutes les adresses dans la table "products"
    private static final UUID ADDRESS_CATEGORY_ID = UUID.fromString("4a6f8b90-1234-5678-9abc-def012345678");

    /**
     * SECURISE: Récupère toutes les adresses de l'utilisateur actuellement connecté.
     */
    @GetMapping
    public Flux<Product> getUserAddresses(
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {
        
        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMapMany(userContext -> {
                // Un utilisateur devrait toujours avoir une organisation après l'onboarding initial
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Flux.error(new IllegalStateException("L'utilisateur n'a pas d'organisation valide associée."));
                }
                UUID organizationId = userContext.getOrganisation().getOrganizationId();
                // Utilise le service de ressources pour obtenir les produits par catégorie et par client_id (l'utilisateur connecté)
                // NOTE: getProductsByCategory ne filtre pas par clientId, il faut le faire après ou modifier le service de ressources
                return resourceService.getProductsByCategory(organizationId, ADDRESS_CATEGORY_ID, authorizationHeader, null)
                                      .filter(product -> userContext.getUserId().equals(product.getClientId())); // Filtrer par l'ID de l'utilisateur connecté
            });
    }

    /**
     * PUBLIC: Récupère les adresses d'un utilisateur spécifique par son ID.
     */
    @GetMapping("/user/{userId}")
    public Flux<Product> getAddressesForUser(
        @PathVariable UUID userId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        log.info("▶️ [AddressController] Récupération des adresses publiques pour l'utilisateur ID: {}", userId);

        String token = Optional.ofNullable(authorizationHeader).orElse(null);

        // MODIFIÉ : Récupère l'organisation ID du userId spécifié.
        // Puis, filtre les produits de cette organisation qui appartiennent à ce userId.
        return profileService.findOrganisationIdByUserId(userId)
             .flatMapMany(orgId ->
                resourceService.getProductsByCategory(orgId, ADDRESS_CATEGORY_ID, token, null)
                               .filter(product -> userId.equals(product.getClientId())) // Filtrer par l'ID de l'utilisateur demandé
             )
             .doOnComplete(() -> log.info("✅ [addressService] Adresses publiques trouvées pour l'utilisateur {}.", userId))
             .doOnError(error -> log.error("❌ Erreur lors de la récupération des adresses pour {}: {}", userId, error.getMessage()));
    }


    /**
     * SECURISE: Crée une nouvelle adresse pour l'utilisateur connecté.
     */
    @PostMapping
    public Mono<ResponseEntity<Product>> createAddress(
            @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide pour créer une adresse (organisation manquante)."));
                }
                
                // On s'assure que la bonne catégorie et l'ID du propriétaire sont définis
                request.setCategoryId(ADDRESS_CATEGORY_ID);
                request.setClientId(userContext.getUserId()); // L'ID de l'utilisateur connecté est le propriétaire
                
                log.info("▶️ Controller: Création d'une nouvelle adresse '{}' pour l'utilisateur {}", request.getName(), userContext.getUserId());
                
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
     * SECURISE: Met à jour une adresse existante.
     */
    @PutMapping("/{addressId}")
    public Mono<ResponseEntity<Product>> updateAddress(
            @PathVariable UUID addressId,
            @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {
        
        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide pour mettre à jour une adresse (organisation manquante)."));
                }
                // Vérification cruciale : l'utilisateur connecté est-il le propriétaire de l'adresse ?
                return resourceService.getProductsByCategory(userContext.getOrganisation().getOrganizationId(), ADDRESS_CATEGORY_ID, authorizationHeader, null)
                        .filter(product -> product.getId().equals(addressId) && userContext.getUserId().equals(product.getClientId()))
                        .next() // Prend le premier élément ou vide
                        .switchIfEmpty(Mono.error(new SecurityException("L'utilisateur n'est pas autorisé à modifier cette adresse ou elle n'existe pas.")))
                        .flatMap(existingAddress -> {
                            request.setCategoryId(ADDRESS_CATEGORY_ID);
                            request.setClientId(userContext.getUserId()); // Assure que le propriétaire reste le même
                            log.info("▶️ Controller: Mise à jour de l'adresse ID: {}", addressId);
                            
                            return resourceService.updateProduct(
                                userContext.getOrganisation().getOrganizationId(), 
                                addressId,
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
     * SECURISE: Supprime une adresse existante.
     */
    @DeleteMapping("/{addressId}")
    public Mono<ResponseEntity<Void>> deleteAddress(
            @PathVariable UUID addressId,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                if (userContext.getOrganisation() == null || userContext.getOrganisation().getOrganizationId() == null) {
                    return Mono.error(new IllegalStateException("Contexte utilisateur invalide pour supprimer une adresse (organisation manquante)."));
                }
                // Vérification cruciale : l'utilisateur connecté est-il le propriétaire de l'adresse ?
                return resourceService.getProductsByCategory(userContext.getOrganisation().getOrganizationId(), ADDRESS_CATEGORY_ID, authorizationHeader, null)
                        .filter(product -> product.getId().equals(addressId) && userContext.getUserId().equals(product.getClientId()))
                        .next() // Prend le premier élément ou vide
                        .switchIfEmpty(Mono.error(new SecurityException("L'utilisateur n'est pas autorisé à supprimer cette adresse ou elle n'existe pas.")))
                        .flatMap(existingAddress -> {
                            log.info("▶️ Controller: Suppression de l'adresse ID: {}", addressId);
                            return resourceService.deleteProduct(
                                userContext.getOrganisation().getOrganizationId(), 
                                addressId,
                                authorizationHeader, 
                                null
                            );
                        });
            })
            .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)));
    }
}