// src/main/java/com/freelance/driver_backend/controller/MockProductController.java

package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.dto.CreateProductRequest;
import com.freelance.driver_backend.model.Product;
import com.freelance.driver_backend.model.ProductKey;
import com.freelance.driver_backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;




@RestController
@RequestMapping("/api/mock-products/{organizationId}")
@RequiredArgsConstructor
@Slf4j
@Profile("dev-resource-mock") // S'assure qu'il est actif avec votre profil de développement
public class MockProductController {

    private final ProductRepository productRepository;

    /**
     * Crée un nouveau "produit" (annonce, véhicule, adresse, etc.).
     */
    @PostMapping
    public Mono<ResponseEntity<Product>> createProduct(
            @PathVariable UUID organizationId,
            @RequestBody CreateProductRequest request) {

        log.warn("[MOCK-CONTROLLER] Création d'un produit pour l'organisation {}: {}", organizationId, request.getName());

        Product newProduct = new Product();
        ProductKey key = new ProductKey(organizationId, UUID.randomUUID());
        newProduct.setKey(key);

        // On utilise la méthode centralisée pour remplir l'objet
        updateProductFromRequest(newProduct, request);

        return productRepository.save(newProduct)
                .map(savedProduct -> new ResponseEntity<>(savedProduct, HttpStatus.CREATED));
    }

    /**
     * Récupère les "produits" d'une organisation, filtrés par catégorie.
     */
    @GetMapping
    public Flux<Product> getProductsByCategory(
            @PathVariable UUID organizationId, 
            @RequestParam UUID categoryId) {
        log.warn("[MOCK-CONTROLLER] Récupération des produits pour org {} et catégorie {}", organizationId, categoryId);
        return productRepository.findByKeyOrganizationIdAndCategoryId(organizationId, categoryId);
    }

    /**
     * Met à jour un "produit" existant.
     */
    @PutMapping("/{productId}")
    public Mono<ResponseEntity<Product>> updateProduct(
            @PathVariable UUID organizationId,
            @PathVariable UUID productId,
            @RequestBody CreateProductRequest request) {
        
        ProductKey key = new ProductKey(organizationId, productId);
        log.warn("[MOCK-CONTROLLER] Mise à jour du produit avec la clé {}. Données reçues : {}", key, request);

        return productRepository.findById(key)
            .flatMap(existingProduct -> {
                log.info("Produit existant trouvé : {}", existingProduct);
                updateProductFromRequest(existingProduct, request);
                log.info("Produit après mise à jour (avant sauvegarde) : {}", existingProduct);
                return productRepository.save(existingProduct);
            })
            .map(savedProduct -> {
                log.info("Produit sauvegardé avec succès : {}", savedProduct);
                return ResponseEntity.ok(savedProduct);
            })
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Supprime un "produit".
     */
    @DeleteMapping("/{productId}")
    public Mono<ResponseEntity<Void>> deleteProduct(
            @PathVariable UUID organizationId,
            @PathVariable UUID productId) {
            
        ProductKey key = new ProductKey(organizationId, productId);
        log.warn("[MOCK-CONTROLLER] Suppression du produit avec la clé {}", key);

        return productRepository.deleteById(key)
                .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)));
    }

    /**
     * Méthode utilitaire privée pour mapper les champs d'une CreateProductRequest
     * vers un objet Product. Centralise la logique pour éviter la répétition
     * entre les méthodes createProduct et updateProduct.
     */
    private void updateProductFromRequest(Product product, CreateProductRequest request) {
        product.setName(request.getName());
        product.setDefaultSellPrice(request.getDefaultSellPrice());
        product.setShortDescription(request.getShortDescription());
        
        if (request.getCategoryId() != null) {
            product.setCategoryId(request.getCategoryId());
        }
        
        product.setIsActive(request.getIsActive());
        product.setPickupLocation(request.getPickupLocation());
        product.setDropoffLocation(request.getDropoffLocation());
        product.setStartDate(request.getStartDate());
        product.setStartTime(request.getStartTime());
        product.setEndDate(request.getEndDate());
        product.setEndTime(request.getEndTime());
        product.setIsNegotiable(request.isNegotiable());
        product.setPaymentMethod(request.getPaymentMethod());
        product.setClientId(request.getClientId());
        product.setClientName(request.getClientName());
        product.setStatus(request.getStatus());
        product.setClientPhoneNumber(request.getClientPhoneNumber());
        product.setClientProfileImageUrl(request.getClientProfileImageUrl());
        product.setBaggageInfo(request.getBaggageInfo()); 
        product.setMetadata(request.getMetadata());
    }
}