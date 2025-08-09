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
@RequestMapping("/api/mock-products/{organizationId}") // Chemin pour notre clone local
@RequiredArgsConstructor
@Slf4j
@Profile("dev-resource-mock") // IMPORTANT: Ce contrôleur ne s'active qu'avec ce profil
public class MockProductController {

    private final ProductRepository productRepository;

   
    @PostMapping
    public Mono<ResponseEntity<Product>> createProduct(
            @PathVariable UUID organizationId,
            @RequestBody CreateProductRequest request) {

        log.warn("[MOCK-CONTROLLER] Request to create product for org {}: {}", organizationId, request.getName());

        Product newProduct = new Product();
        ProductKey key = new ProductKey(organizationId, UUID.randomUUID());
        newProduct.setKey(key);

        // ==============================================================================
        //                         MAPPAGE COMPLET 1:1
        // ==============================================================================
        newProduct.setName(request.getName());
        newProduct.setDefaultSellPrice(request.getDefaultSellPrice());
        newProduct.setShortDescription(request.getShortDescription()); // Vient de baggageInfo
        newProduct.setCategoryId(request.getCategoryId());
        newProduct.setIsActive(true); // Une annonce publiée est toujours active

        newProduct.setPickupLocation(request.getPickupLocation());
        newProduct.setDropoffLocation(request.getDropoffLocation());
        newProduct.setStartDate(request.getStartDate());
        newProduct.setStartTime(request.getStartTime());
        newProduct.setIsNegotiable(request.isNegotiable());
        newProduct.setPaymentMethod(request.getPaymentMethod());
        newProduct.setClientId(request.getClientId());
        newProduct.setClientName(request.getClientName());
        newProduct.setEndDate(request.getEndDate());
        newProduct.setEndTime(request.getEndTime());
        newProduct.setStatus(request.getStatus());
        newProduct.setClientPhoneNumber(request.getClientPhoneNumber());
        newProduct.setPaymentOption(request.getPaymentOption());
        newProduct.setRegularAmount(request.getRegularAmount());
        newProduct.setDiscountPercentage(request.getDiscountPercentage());
        newProduct.setDiscountedAmount(request.getDiscountedAmount());
// 
        // ==============================================================================

        return productRepository.save(newProduct)
                .map(savedProduct -> new ResponseEntity<>(savedProduct, HttpStatus.CREATED));
    }

    @GetMapping
    public Flux<Product> getProductsByCategory(@PathVariable UUID organizationId, @RequestParam UUID categoryId) {
        log.warn("[MOCK-CONTROLLER] Request to get products for org {} and category {}", organizationId, categoryId);
        return productRepository.findByKeyOrganizationIdAndCategoryId(organizationId, categoryId);
    }

    @PutMapping("/{productId}")
    public Mono<ResponseEntity<Product>> updateProduct(
            @PathVariable UUID organizationId,
            @PathVariable UUID productId,
            @RequestBody CreateProductRequest request) {
        
        ProductKey key = new ProductKey(organizationId, productId);
        log.warn("[MOCK-CONTROLLER] Request to update product with key {}", key);

        return productRepository.findById(key)
            .flatMap(existingProduct -> {
                // Mettre à jour tous les champs de l'entité existante avec les données de la requête
                existingProduct.setName(request.getName());
                existingProduct.setDefaultSellPrice(request.getDefaultSellPrice());
                existingProduct.setShortDescription(request.getShortDescription());
                existingProduct.setPickupLocation(request.getPickupLocation());
                existingProduct.setDropoffLocation(request.getDropoffLocation());
                existingProduct.setStartDate(request.getStartDate());
                existingProduct.setStartTime(request.getStartTime());
                existingProduct.setEndDate(request.getEndDate());
                existingProduct.setEndTime(request.getEndTime());
                existingProduct.setIsNegotiable(request.isNegotiable());
                existingProduct.setPaymentMethod(request.getPaymentMethod());
                existingProduct.setClientName(request.getClientName());
                existingProduct.setStatus(request.getStatus());
                existingProduct.setClientPhoneNumber(request.getClientPhoneNumber());
                existingProduct.setPaymentOption(request.getPaymentOption());
                existingProduct.setRegularAmount(request.getRegularAmount());
                existingProduct.setDiscountPercentage(request.getDiscountPercentage());
                existingProduct.setDiscountedAmount(request.getDiscountedAmount());
                
                return productRepository.save(existingProduct);
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{productId}")
    public Mono<ResponseEntity<Void>> deleteProduct(
            @PathVariable UUID organizationId,
            @PathVariable UUID productId) {
            
        ProductKey key = new ProductKey(organizationId, productId);
        log.warn("[MOCK-CONTROLLER] Request to delete product with key {}", key);

        return productRepository.deleteById(key)
                .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)));
    }
}