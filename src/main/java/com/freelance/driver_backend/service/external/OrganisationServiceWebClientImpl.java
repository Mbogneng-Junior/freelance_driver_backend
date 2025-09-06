package com.freelance.driver_backend.service.external;

import com.freelance.driver_backend.dto.external.OrganisationCreationRequest;
import com.freelance.driver_backend.dto.external.OrganisationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Component
@Profile("production & !dev-resource-mock")
@Slf4j
public class OrganisationServiceWebClientImpl implements OrganisationService {

    private final WebClient webClient;
    public OrganisationServiceWebClientImpl(@Qualifier("organisationServiceWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<OrganisationDto> createOrganisation(OrganisationCreationRequest request, String bearerToken, String publicKey) {
        log.info("Sending real API request to create organisation '{}'", request.getLongName());
        return webClient.post()
                .uri("/organizations")
                .header("Authorization", bearerToken)
                .header("Public-Key", publicKey)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Error during organisation creation: {} - {}", response.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException("Organisation creation failed: " + errorBody));
                                })
                )
                .bodyToMono(OrganisationDto.class); // <-- On attend un OrganisationDto en retour
    }

    @Override
    public Mono<List<OrganisationDto>> getUserOrganisations(String userBearerToken, String publicKey) {
        log.info("Sending real API request to get user organisations");
        return webClient.get()
                .uri("/organizations/user")
                .header("Authorization", userBearerToken)
                .header("Public-Key", publicKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Error getting user organisations: {} - {}", response.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException("Could not get user organisations: " + errorBody));
                                })
                )
                .bodyToFlux(OrganisationDto.class)
                .collectList();
    }

    @Override
    public Mono<OrganisationDto> getOrganisationById(UUID organisationId, String userBearerToken, String publicKey) {
        log.info("Sending real API request to get organisation by ID: {}", organisationId);
        return webClient.get()
                .uri("/organizations/{id}", organisationId)
                .header("Authorization", userBearerToken)
                .header("Public-Key", publicKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                    response.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("Failed to retrieve organisation {}. Status: {}. Response: {}",
                                        organisationId, response.statusCode(), errorBody);
                                return Mono.error(new RuntimeException("Failed to retrieve organisation: " + errorBody));
                            })
                )
                .bodyToMono(OrganisationDto.class);
    }
}