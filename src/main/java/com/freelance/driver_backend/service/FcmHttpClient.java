// src/main/java/com/freelance/driver_backend/service/FcmHttpClient.java

package com.freelance.driver_backend.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class FcmHttpClient {

    private final WebClient webClient;
    private final FirebaseApp firebaseApp;

    public FcmHttpClient(FirebaseApp firebaseApp) {
        this.firebaseApp = firebaseApp;
        this.webClient = WebClient.builder()
                .baseUrl("https://fcm.googleapis.com")
                .build();
    }

    /**
     * Obtient un token d'accès OAuth2 à partir du fichier de clé de service.
     * Ce token est nécessaire pour authentifier chaque requête à l'API FCM.
     */
    private Mono<String> getAccessToken() {
        return Mono.fromCallable(() -> {
            ClassPathResource resource = new ClassPathResource("firebase-service-account-key.json");
            try (InputStream stream = resource.getInputStream()) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/firebase.messaging"));
                
                credentials.refreshIfExpired();
                return credentials.getAccessToken().getTokenValue();
            }
        }).onErrorMap(IOException.class, e -> new RuntimeException("Impossible d'obtenir le token d'accès Google.", e));
    }

    /**
     * Envoie des notifications à une liste de tokens d'appareils.
     */
    public Mono<Void> sendNotifications(List<String> tokens, String title, String body) {
        if (tokens == null || tokens.isEmpty()) {
            log.warn("Aucun token fourni, l'envoi de notification est annulé.");
            return Mono.empty();
        }
        
        String projectId = firebaseApp.getOptions().getProjectId();
        if (projectId == null || projectId.isBlank()) {
            return Mono.error(new IllegalStateException("L'ID du projet Firebase n'est pas configuré."));
        }

        log.info("▶️ Préparation de l'envoi de la notification '{}' à {} appareil(s).", title, tokens.size());

        return getAccessToken().flatMap(token ->
            // On envoie une requête pour chaque token
            Flux.fromIterable(tokens)
                .flatMap(deviceToken -> {
                    // Construction du corps de la requête JSON pour l'API FCM v1
                    Map<String, Object> notificationPayload = Map.of(
                        "message", Map.of(
                            "token", deviceToken,
                            "notification", Map.of(
                                "title", title,
                                "body", body
                            )
                        )
                    );

                    return webClient.post()
                        .uri("/v1/projects/{projectId}/messages:send", projectId)
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(notificationPayload)
                        .retrieve()
                        .bodyToMono(String.class)
                        .doOnSuccess(response -> log.info("✅ Réponse de FCM pour le token {}...: {}", deviceToken.substring(0, 10), response))
                        .doOnError(e -> log.error("❌ Échec de l'envoi au token {}...: {}", deviceToken.substring(0, 10), e.getMessage()))
                        .onErrorResume(e -> Mono.empty()); // Ne pas faire échouer tout le lot si un seul token est invalide
                })
                .then() // Attend que toutes les requêtes du Flux soient terminées
        );
    }
}