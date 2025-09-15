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
import org.springframework.http.HttpStatusCode;
import java.util.HashMap;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FcmHttpClient {

    private final WebClient webClient;
    private final FirebaseApp firebaseApp; 
    private final String projectId;        

    public FcmHttpClient(FirebaseApp firebaseApp) { 
        this.firebaseApp = firebaseApp;
        this.projectId = firebaseApp.getOptions().getProjectId(); 
        
        if (this.projectId == null || this.projectId.isBlank()) {
            throw new IllegalStateException("Firebase Project ID not configured in FirebaseApp options.");
        }

        this.webClient = WebClient.builder()
                .baseUrl("https://fcm.googleapis.com") 
                .build();
    }

    private Mono<String> getAccessToken() {
        return Mono.fromCallable(() -> {
            ClassPathResource resource = new ClassPathResource("firebase-service-account-key.json"); 
            try (InputStream stream = resource.getInputStream()) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/firebase.messaging"));
                
                credentials.refreshIfExpired(); 
                return credentials.getAccessToken().getTokenValue();
            }
        }).onErrorMap(IOException.class, e -> new RuntimeException("Impossible d'obtenir le token d'accès Google pour FCM.", e));
    }

    // NOUVELLE SIGNATURE : Accepte un dataPayload
    public Mono<Void> sendNotifications(List<String> tokens, String title, String body, Map<String, String> dataPayload) {
        if (tokens == null || tokens.isEmpty()) {
            log.warn("[FCM HTTP CLIENT] Aucun token fourni, l'envoi de notification est annulé.");
            return Mono.empty();
        }
        
        log.info("▶️ [FCM HTTP CLIENT] Préparation de l'envoi de la notification '{}' à {} appareil(s). Projet: {}", title, tokens.size(), this.projectId);

        return getAccessToken().flatMap(token ->
            Flux.fromIterable(tokens)
                .flatMap(singleToken -> { 
                    // Construction du corps de la requête JSON pour l'API FCM v1
                    Map<String, Object> messageContent = new HashMap<>();
                    messageContent.put("token", singleToken);
                    messageContent.put("notification", Map.of("title", title, "body", body));
                    
                    // AJOUT : Inclure le dataPayload si présent
                    if (dataPayload != null && !dataPayload.isEmpty()) {
                        messageContent.put("data", dataPayload);
                    }

                    Map<String, Object> notificationPayload = Map.of(
                        "message", messageContent
                    );

                    return webClient.post()
                        .uri("/v1/projects/{projectId}/messages:send", this.projectId)
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(notificationPayload)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, response -> 
                            response.bodyToMono(String.class)
                                    .map(errorBody -> {
                                        log.error("❌ [FCM HTTP CLIENT] Erreur HTTP lors de l'envoi à FCM (Status: {}): {}", response.statusCode(), errorBody);
                                        return new RuntimeException("Échec d'envoi FCM: " + errorBody);
                                    })
                                    .switchIfEmpty(Mono.defer(() -> {
                                        log.error("❌ [FCM HTTP CLIENT] Échec d'envoi FCM: corps d'erreur vide. Status: {}", response.statusCode());
                                        return Mono.just(new RuntimeException("Échec d'envoi FCM: corps d'erreur vide. Status: " + response.statusCode()));
                                    }))
                        )
                        // <-- LA PARENTHÈSE EN TROP ÉTAIT ICI, JE L'AI SUPPRIMÉE !
                        .bodyToMono(String.class) 
                        .doOnSuccess(responseBody -> log.info("✅ [FCM HTTP CLIENT] Réponse de FCM pour token {}: {}", singleToken.substring(0, 10) + "...", responseBody)) 
                        .doOnError(e -> log.error("❌ [FCM HTTP CLIENT] Échec de l'envoi à FCM pour token {}: {}", singleToken.substring(0, 10) + "...", e.getMessage())) 
                        .onErrorResume(e -> Mono.empty()); 
                })
                .then() // <-- C'est ici que le Mono<Void> est généré.
        );
    }
}