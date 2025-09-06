package com.freelance.driver_backend.service.resource;

import com.freelance.driver_backend.model.Resource;
import com.freelance.driver_backend.model.ResourceKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@Profile("production & !dev-resource-mock") 
@Slf4j
public class ResourceServiceWebClientImpl implements ResourceService {

    private final WebClient webClient;
    private final String publicKey;

    public ResourceServiceWebClientImpl(@Qualifier("resourceServiceWebClient") WebClient webClient,
                                        @Value("${freelancedriver.api.public-key}") String publicKey) {
        this.webClient = webClient;
        this.publicKey = publicKey;
    }

    @Override
    public Flux<Resource> getResourcesByOrganisationAndCategory(UUID organisationId, String categoryId) {
        log.info("API Call [ResourceService]: Getting resources for org {} and category {}", organisationId, categoryId);
        return webClient.get()
                .uri("/{organisationId}/resources/search?category_id={categoryId}", organisationId, categoryId)
                .header("Public-Key", this.publicKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .flatMap(errorBody -> {
                            log.error("Error fetching resources for organisation {}: {} - {}", organisationId, response.statusCode(), errorBody);
                            return Mono.error(new RuntimeException("Failed to fetch resources: " + errorBody));
                        }))
                .bodyToFlux(Resource.class);
    }
    
    @Override
    public Flux<Resource> getResourcesByCategory(String categoryId) {
        log.warn("API Call [ResourceService]: getResourcesByCategory is not directly supported by the external API.");
        return Flux.error(new UnsupportedOperationException("Fetching resources by category alone is not supported."));
    }

    // --- CORRECTION DE SIGNATURE ICI ---
    @Override
    public Mono<Resource> getResourceById(ResourceKey key) {
        log.info("API Call [ResourceService]: Getting resource by ID {}", key.getResourceId());
        return webClient.get()
                .uri("/{organisationId}/resources/{resourceId}", key.getOrganizationId(), key.getResourceId())
                .header("Public-Key", this.publicKey)
                .retrieve()
                .bodyToMono(Resource.class)
                .doOnError(e -> log.error("Error fetching resource by ID {}", key.getResourceId(), e));
    }
    
    @Override
    public Mono<Resource> createResource(Resource resource) {
        log.info("API Call [ResourceService]: Creating resource '{}' for org {}", resource.getName(), resource.getOrganizationId());
        return webClient.post()
                .uri("/{organisationId}/resources", resource.getOrganizationId())
                .header("Public-Key", this.publicKey)
                .bodyValue(resource)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .flatMap(errorBody -> {
                            log.error("Error creating resource: {} - {}", response.statusCode(), errorBody);
                            return Mono.error(new RuntimeException("Failed to create resource: " + errorBody));
                        }))
                .bodyToMono(Resource.class);
    }

    // --- CORRECTION DE SIGNATURE ICI ---
    @Override
    public Mono<Resource> updateResource(ResourceKey key, Resource resource) {
        log.info("API Call [ResourceService]: Updating resource {}", key.getResourceId());
        return webClient.put()
                .uri("/{organisationId}/resources/{resourceId}", key.getOrganizationId(), key.getResourceId())
                .header("Public-Key", this.publicKey)
                .bodyValue(resource)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .flatMap(errorBody -> {
                            log.error("Error updating resource {}: {} - {}", key.getResourceId(), response.statusCode(), errorBody);
                            return Mono.error(new RuntimeException("Failed to update resource: " + errorBody));
                        }))
                .bodyToMono(Resource.class);
    }

    // --- CORRECTION DE SIGNATURE ICI ---
    @Override
    public Mono<Void> deleteResource(ResourceKey key) {
        log.info("API Call [ResourceService]: Deleting resource {}", key.getResourceId());
        return webClient.delete()
                .uri("/{organisationId}/resources/{resourceId}", key.getOrganizationId(), key.getResourceId())
                .header("Public-Key", this.publicKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .flatMap(errorBody -> {
                            log.error("Error deleting resource {}: {} - {}", key.getResourceId(), response.statusCode(), errorBody);
                            return Mono.error(new RuntimeException("Failed to delete resource: " + errorBody));
                        }))
                .bodyToMono(Void.class);
    }
}