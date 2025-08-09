package com.freelance.driver_backend.service.external;

import com.freelance.driver_backend.dto.CreateProductRequest;
import com.freelance.driver_backend.model.Product;
import com.freelance.driver_backend.service.ResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Profile("production")
@Slf4j
public class ResourceServiceExternalImpl implements ResourceService {
    
    @Override
    public Mono<Product> createProduct(UUID organizationId, CreateProductRequest request, String bearerToken, String publicKey) {
        log.error("[EXTERNAL-IMPL] createProduct - NOT IMPLEMENTED YET.");
        return Mono.error(new UnsupportedOperationException("External resource service not implemented."));
    }

    @Override
    public Flux<Product> getProductsByCategory(UUID organizationId, UUID categoryId, String bearerToken, String publicKey) {
        log.error("[EXTERNAL-IMPL] getProductsByCategory - NOT IMPLEMENTED YET.");
        return Flux.error(new UnsupportedOperationException("External resource service not implemented."));
    }

    // ==============================================================================
    //           AJOUTEZ LES DEUX MÉTHODES MANQUANTES CI-DESSOUS
    // ==============================================================================
    @Override
    public Mono<Product> updateProduct(UUID organizationId, UUID productId, CreateProductRequest request, String bearerToken, String publicKey) {
        log.error("[EXTERNAL-IMPL] updateProduct - NOT IMPLEMENTED YET.");
        return Mono.error(new UnsupportedOperationException("External resource service not implemented."));
    }

    @Override
    public Mono<Void> deleteProduct(UUID organizationId, UUID productId, String bearerToken, String publicKey) {
        log.error("[EXTERNAL-IMPL] deleteProduct - NOT IMPLEMENTED YET.");
        return Mono.error(new UnsupportedOperationException("External resource service not implemented."));
    }
    // ==============================================================================
}