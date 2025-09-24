package com.freelance.driver_backend.service.internal;

import com.freelance.driver_backend.dto.CreateProductRequest;
import com.freelance.driver_backend.model.Product;
import com.freelance.driver_backend.service.ResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;





@Service
@Slf4j
public class ResourceServiceLocalImpl implements ResourceService {
    private final WebClient localApiClient;

    public ResourceServiceLocalImpl(@Qualifier("localApiWebClient") WebClient localApiClient) {
        this.localApiClient = localApiClient;
    }

    @Override
    public Mono<Product> createProduct(UUID organizationId, CreateProductRequest request, String bearerToken, String publicKey) {
        log.warn("[LOCAL-IMPL] Appel de MockProductController pour créer un produit/adresse.");
        return localApiClient.post()
                .uri("/api/mock-products/{organizationId}", organizationId)
                .header("Authorization", bearerToken)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Product.class);
    }

    @Override
    public Flux<Product> getProductsByCategory(UUID organizationId, UUID categoryId, String bearerToken, String publicKey) {
        log.warn("[LOCAL-IMPL] Appel de MockProductController pour lister les produits/adresses.");
        return localApiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/mock-products/{organizationId}")
                        .queryParam("categoryId", categoryId)
                        .build(organizationId))
                .header("Authorization", bearerToken)
                .retrieve()
                .bodyToFlux(Product.class);
    }

    @Override
    public Mono<Product> updateProduct(UUID organizationId, UUID productId, CreateProductRequest request, String bearerToken, String publicKey) {
        log.warn("[LOCAL-IMPL] Appel de MockProductController pour mettre à jour le produit/adresse {}.", productId);
        return localApiClient.put()
                .uri("/api/mock-products/{organizationId}/{productId}", organizationId, productId)
                .header("Authorization", bearerToken)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Product.class);
    }

    @Override
    public Mono<Void> deleteProduct(UUID organizationId, UUID productId, String bearerToken, String publicKey) {
        log.warn("[LOCAL-IMPL] Appel de MockProductController pour supprimer le produit/adresse {}.", productId);
        return localApiClient.delete()
                .uri("/api/mock-products/{organizationId}/{productId}", organizationId, productId)
                .header("Authorization", bearerToken)
                .retrieve()
                .bodyToMono(Void.class);
    }
}