package com.freelance.driver_backend.service.internal;

import com.freelance.driver_backend.dto.external.MediaApiResponseDto;
// SUPPRESSION : import com.freelance.driver_backend.dto.external.OAuthTokenResponse;
import com.freelance.driver_backend.dto.external.UploadMediaResponse;
import com.freelance.driver_backend.service.StorageService;
// SUPPRESSION : import com.freelance.driver_backend.service.external.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
// SUPPRESSION : import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@Primary
@Slf4j
public class ExternalMediaStorageService implements StorageService {

    private final WebClient externalMediaServiceWebClient;
    private final String mediaServiceBaseUrl;

    public ExternalMediaStorageService(
            @Qualifier("externalMediaServiceWebClient") WebClient externalMediaServiceWebClient,
            @Value("${microservices.media-service.url}") String mediaServiceBaseUrl
    ) {
        this.externalMediaServiceWebClient = externalMediaServiceWebClient;
        this.mediaServiceBaseUrl = mediaServiceBaseUrl.endsWith("/") ? mediaServiceBaseUrl.substring(0, mediaServiceBaseUrl.length() - 1) : mediaServiceBaseUrl;
    }

    // ==============================================================================
    //                       MÉTHODE saveFile SIMPLIFIÉE
    // ==============================================================================
    @Override
    public Mono<UploadMediaResponse> saveFile(String serviceContext, String frontendLogicalType, UUID uploaderUserId,
            UUID targetResourceId, String originalFileName, FilePart file) {

        log.info("[ADAPTED & SIMPLIFIED] Appel à la NOUVELLE API publique de médias.");
        
        String location = String.format("%s/%s", frontendLogicalType, targetResourceId.toString());

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file);
        body.add("service", serviceContext);
        body.add("location", location);

        return externalMediaServiceWebClient.post()
                .uri("/media/upload")
                // SUPPRESSION : Plus besoin de header d'autorisation
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .flatMap(errorBody -> {
                            log.error("❌ Erreur de la NOUVELLE API (upload) : {}", errorBody);
                            // On vérifie le cas où la réponse est du HTML (signe d'une erreur non-JSON)
                            if (response.headers().contentType().orElse(MediaType.APPLICATION_JSON).isCompatibleWith(MediaType.TEXT_HTML)) {
                                 log.error("La réponse d'erreur est du HTML, ce qui est inattendu. Le service de médias est peut-être mal configuré ou en panne.");
                                 return Mono.error(new RuntimeException("Le service de médias a retourné une page d'erreur HTML."));
                            }
                            return Mono.error(new RuntimeException("Échec du téléversement : " + errorBody));
                        }))
                // Ajout d'un log pour le type de contenu de la réponse
                .bodyToMono(MediaApiResponseDto.class)
                .flatMap(apiResponse -> {
                    UploadMediaResponse responseForApp = new UploadMediaResponse();
                    responseForApp.setId(apiResponse.getId());
                    responseForApp.setResourceId(targetResourceId);
                    responseForApp.setUri(apiResponse.getUri());
                    responseForApp.setUrl(apiResponse.getPublicUrl(this.mediaServiceBaseUrl));
                    
                    log.info("✅ [ADAPTED] Upload réussi. URL publique générée : {}", responseForApp.getUrl());
                    return Mono.just(responseForApp);
                });
    }

    // ==============================================================================
    //                       MÉTHODE deleteFile SIMPLIFIÉE
    // ==============================================================================
    @Override
    public Mono<Void> deleteFile(String mediaUrl) {
        log.info("[ADAPTED & SIMPLIFIED] Appel à la NOUVELLE API publique pour suppression.");

        if (mediaUrl == null || mediaUrl.isBlank()) {
            return Mono.error(new IllegalArgumentException("L'URL du média ne peut pas être nulle."));
        }

        try {
            String[] segments = mediaUrl.split("/");
            String mediaId = segments[segments.length - 1];
            UUID.fromString(mediaId);
            
            log.info("[ADAPTER] ID du média extrait pour suppression : {}", mediaId);

            return externalMediaServiceWebClient.delete()
                    .uri("/media/{id}", mediaId)
                    // SUPPRESSION : Plus besoin de header d'autorisation
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("❌ Erreur de la NOUVELLE API (delete) : {}", errorBody);
                                return Mono.error(new RuntimeException("Échec de la suppression : " + errorBody));
                            }))
                    .bodyToMono(Void.class)
                    .doOnSuccess(v -> log.info("✅ [ADAPTER] Fichier avec ID {} supprimé via la nouvelle API.", mediaId));

        } catch (Exception e) {
            log.error("❌ Impossible d'extraire l'ID du média depuis l'URL '{}'.", mediaUrl);
            return Mono.error(new IllegalArgumentException("Format d'URL de média invalide: " + mediaUrl));
        }
    }
}