package com.freelance.driver_backend.service.external;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freelance.driver_backend.dto.external.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class AuthServiceWebClientImpl implements AuthService {

    private final WebClient webClient;

    // Le Qualifier pointe toujours vers le Bean défini dans WebClientConfig
    public AuthServiceWebClientImpl(@Qualifier("authServiceWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<OAuthTokenResponse> getClientCredentialsToken(String clientId, String clientSecret) {
        log.info("Requesting M2M token with client_id: {}", clientId);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("scope", "read write manage_api");

        return webClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Error obtaining client credentials token: {} - {}", response.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException("Could not obtain M2M token: " + errorBody));
                                })
                )
                .bodyToMono(OAuthTokenResponse.class);
    }

    @Override
    public Mono<UserDto> registerUser(RegistrationRequest request, String m2mBearerToken) {
        log.info("Étape 1: Sending registration request for {}", request.getEmail());
        return webClient.post()
                .uri("/api/register")
                .header("Authorization", m2mBearerToken)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Error during user registration for {}: {} - {}", request.getEmail(), response.statusCode(), errorBody);

                                    String clientErrorMessage = "Registration failed with status " + response.statusCode();
                                    
                                    // AJOUT ICI : Log la réponse brute pour le débogage côté frontend
                                    clientErrorMessage += ": External service responded with: " + (errorBody != null && !errorBody.isEmpty() ? errorBody : "[Empty or Null Body]");

                                    try {
                                        ObjectMapper mapper = new ObjectMapper();
                                        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // Ignorer les champs inconnus
                                        JsonNode root = mapper.readTree(errorBody);

                                        // Tentative d'extraction de messages d'erreur plus spécifiques
                                        if (root.has("message") && root.get("message").isTextual()) {
                                            clientErrorMessage = root.get("message").asText();
                                        } else if (root.has("error") && root.get("error").isTextual()) {
                                            clientErrorMessage = root.get("error").asText();
                                        }
                                        
                                        if (root.has("errors") && root.get("errors").isObject()) {
                                            // Ajoute les détails des erreurs de validation si présents
                                            clientErrorMessage += " (Details: " + root.get("errors").toString() + ")";
                                        }
                                        
                                    } catch (JsonProcessingException e) {
                                        log.warn("Could not parse errorBody from external auth service as JSON: {}", errorBody, e);
                                        // Le clientErrorMessage inclut déjà le corps brut, donc pas besoin de l'ajouter ici
                                    } catch (Exception e) {
                                        log.error("Unexpected error while processing errorBody from external auth service: {}", errorBody, e);
                                        // Le clientErrorMessage inclut déjà le corps brut
                                    }
    
                                    return Mono.error(new RuntimeException(clientErrorMessage));
                                })
                )
                .bodyToMono(UserDto.class);
    }

    @Override
    public Mono<LoginResponse> loginUser(LoginRequest request, String m2mBearerToken) {
        log.info("Étape 2: Sending login request for {}", request.getUsername());
        return webClient.post()
                .uri("/api/login")
                .header("Authorization", m2mBearerToken)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("HTTP Error during login for user {}: {} - {}", request.getUsername(), response.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException("Login failed with HTTP status: " + response.statusCode()));
                                })
                )
                .bodyToMono(String.class)
                .flatMap(rawBody -> {
                    log.info("Raw JSON response from /api/login for user {}: {}", request.getUsername(), rawBody);
                    try {
                        ObjectMapper objectMapper = new ObjectMapper();
                        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                        JsonNode rootNode = objectMapper.readTree(rawBody);
                        
                        // --- DÉBUT DE LA CORRECTION ---
                        // Si l'API renvoie un statut FAILED (identifiants invalides)
                        if (rootNode.has("status") && "FAILED".equals(rootNode.get("status").asText())) {
                            String errorMessage = rootNode.get("message").asText("Invalid Credentials");
                            log.warn("Login failed for user '{}': {}", request.getUsername(), errorMessage);
                            // On retourne un Mono vide pour signaler l'échec sans crasher l'application
                            return Mono.empty(); 
                        }
                        // --- FIN DE LA CORRECTION ---

                        // Si le statut est un succès, on parse la réponse complète
                        LoginResponse loginResponse = objectMapper.readValue(rawBody, LoginResponse.class);
                        return Mono.just(loginResponse);

                    } catch (JsonProcessingException e) {
                        log.error("Failed to parse login response JSON", e);
                        return Mono.error(new RuntimeException("Failed to parse login response JSON", e));
                    }
                });
    }
}