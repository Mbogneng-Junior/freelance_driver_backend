package com.freelance.driver_backend.service.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freelance.driver_backend.dto.external.OAuthTokenResponse;
import com.freelance.driver_backend.dto.external.UploadMediaResponse;
import com.freelance.driver_backend.service.StorageService;
import com.freelance.driver_backend.service.external.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

@Service
@Primary
@Slf4j
public class ExternalMediaStorageService implements StorageService {

    private final WebClient externalMediaServiceWebClient;
    private final AuthService authService;
    private final String oauthClientId;
    private final String oauthClientSecret;

    public ExternalMediaStorageService(
            @Qualifier("externalMediaServiceWebClient") WebClient externalMediaServiceWebClient,
            AuthService authService,
            @Value("${freelancedriver.oauth2.client-id}") String oauthClientId,
            @Value("${freelancedriver.oauth2.client-secret}") String oauthClientSecret) {
        this.externalMediaServiceWebClient = externalMediaServiceWebClient;
        this.authService = authService;
        this.oauthClientId = oauthClientId;
        this.oauthClientSecret = oauthClientSecret;
    }

    private Mono<String> getM2MBearerToken() {
        return authService.getClientCredentialsToken(oauthClientId, oauthClientSecret)
                .map(OAuthTokenResponse::getAccessToken)
                .map(token -> "Bearer " + token)
                .doOnError(e -> log.error("❌ Échec de l'obtention du token M2M pour l'API de médias: {}",
                        e.getMessage(), e))
                .switchIfEmpty(
                        Mono.error(new RuntimeException("Impossible d'obtenir un token M2M pour l'API de médias.")));
    }

    private String determineExternalMediaType(MediaType contentType) {
        if (contentType == null)
            return "unknown";
        if (contentType.isCompatibleWith(MediaType.IMAGE_JPEG) || contentType.isCompatibleWith(MediaType.IMAGE_PNG)
                || contentType.isCompatibleWith(MediaType.IMAGE_GIF)) {
            return "image";
        }
        if (contentType.isCompatibleWith(MediaType.APPLICATION_PDF)) {
            return "pdf";
        }
        if (contentType.isCompatibleWith(MediaType.parseMediaType("audio/*"))) {
            return "audio";
        }
        if (contentType.isCompatibleWith(MediaType.parseMediaType("video/*"))) {
            return "video";
        }
        return "file";
    }

    @Override
    public Mono<UploadMediaResponse> saveFile(String serviceContext, String frontendLogicalType, UUID uploaderUserId,
            UUID targetResourceId, String originalFileName, FilePart file) {
        log.info(
                "[ExternalMediaStorageService] Téléversement vers l'API externe de médias. FrontendType: {}, ResourceId: {}, UserId: {}",
                frontendLogicalType, targetResourceId, uploaderUserId);

        String externalMediaType = determineExternalMediaType(file.headers().getContentType());
        if ("unknown".equals(externalMediaType)) {
            return Mono.error(new IllegalArgumentException("Unsupported media type for file: " + originalFileName));
        }

        // ---------- CORRECTION DE LA CONSTRUCTION DE L'URI POUR POST ----------
        // L'API s'attend à: /media/{service}/{type}/{path}/{resource_id}
        // où {path} est un segment simple (ex: "avatars")
        // et {resource_id} est l'ID de la ressource associée (ex: l'ID de
        // l'utilisateur)
        String apiUri = String.format("/media/%s/%s/%s/%s",
                serviceContext, // ex: "product"
                externalMediaType, // ex: "image"
                frontendLogicalType, // C'est le {path} simple, ex: "avatars"
                targetResourceId.toString()); // C'est le {resource_id}, ex: l'UUID de l'utilisateur
        // -------------------------------------------------------------------

        log.info("[ExternalMediaStorageService] URI de l'API externe pour POST : {}", apiUri);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file);

        return getM2MBearerToken()
                .flatMap(m2mBearerToken -> externalMediaServiceWebClient.post()
                        .uri(apiUri)
                        .header(HttpHeaders.AUTHORIZATION, m2mBearerToken)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(BodyInserters.fromMultipartData(body))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("❌ Erreur HTTP lors du téléversement vers l'API externe (Status: {}): {}",
                                            response.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException(
                                            "Échec du téléversement vers le service de médias externe: " + errorBody));
                                })
                                .switchIfEmpty(Mono.defer(() -> Mono.error(new RuntimeException(
                                        "Échec du téléversement vers le service de médias externe: corps d'erreur vide. Status: "
                                                + response.statusCode()))))
                                .cast(Throwable.class))
                        .bodyToMono(String.class)
                        .flatMap(rawResponseBody -> {
                            log.info("[ExternalMediaStorageService] Réponse brute de l'API de médias externe: {}",
                                    rawResponseBody);

                            if (rawResponseBody == null || rawResponseBody.trim().isEmpty()) {
                                log.error(
                                        "[ExternalMediaStorageService] L'API de médias externe a renvoyé une réponse vide ou nulle APRÈS un statut 2xx. Ceci est inattendu.");
                                return Mono.error(new RuntimeException(
                                        "L'API de médias externe a renvoyé une réponse vide après un statut de succès."));
                            }

                            try {
                                ObjectMapper objectMapper = new ObjectMapper();
                                objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                                UploadMediaResponse uploadResponse = objectMapper.readValue(rawResponseBody,
                                        UploadMediaResponse.class);

                                if (uploadResponse == null || uploadResponse.getId() == null
                                        || uploadResponse.getResourceId() == null || uploadResponse.getUri() == null
                                        || uploadResponse.getUrl() == null || uploadResponse.getUrl().isEmpty()) {
                                    log.error(
                                            "[ExternalMediaStorageService] La désérialisation a réussi, mais la réponse de l'API de médias externe est INVALIDE (champs essentiels manquants ou nuls): {}. Réponse brute: {}",
                                            uploadResponse, rawResponseBody);
                                    return Mono.error(new RuntimeException(
                                            "La réponse de l'API de médias externe est incomplète ou invalide."));
                                }

                                return Mono.just(uploadResponse);
                            } catch (JsonProcessingException e) {
                                log.error(
                                        "[ExternalMediaStorageService] Erreur de désérialisation JSON de la réponse de l'API de médias externe: {} - Réponse brute: {}",
                                        e.getMessage(), rawResponseBody, e);
                                return Mono.error(new RuntimeException(
                                        "Échec de l'analyse JSON de la réponse du service de médias externe.", e));
                            } catch (Exception e) {
                                log.error(
                                        "[ExternalMediaStorageService] Erreur inattendue lors du traitement de la réponse de l'API de médias externe: {} - Réponse brute: {}",
                                        e.getMessage(), rawResponseBody, e);
                                return Mono.error(new RuntimeException(
                                        "Erreur lors du traitement de la réponse du service de médias externe.", e));
                            }
                        })
                        .doOnSuccess(uploadResponse -> log.info(
                                "✅ [ExternalMediaStorageService] Upload réussi. URL: {}, URI: {}",
                                uploadResponse.getUrl(), uploadResponse.getUri()))
                        .onErrorResume(e -> {
                            log.error(
                                    "[ExternalMediaStorageService] Erreur fatale dans la chaîne de traitement de l'upload: {}",
                                    e.getMessage(), e);
                            return Mono
                                    .error(new RuntimeException("Échec de l'upload de fichier: " + e.getMessage(), e));
                        }));
    }

    @Override
    public Mono<Void> deleteFile(String mediaUri) {
        log.info("[ExternalMediaStorageService] Suppression du fichier via l'API externe: {}", mediaUri);

        try {
            URI uri = new URI(mediaUri);
            String path = uri.getPath();

            String[] segments = path.split("/");
            // On s'attend à ce que l'URI après /media-service/ soit de la forme:
            // /service/type/path/resource_id/filename.jpg
            // ex: /product/image/avatars/user_id_uuid/unique_filename.jpg
            if (segments.length < 6) { // "", "service", "type", "path", "resource_id", "filename"
                log.error(
                        "URI de média invalide pour la suppression (segments insuffisants): {}. Attendu au moins 6 segments.",
                        mediaUri);
                return Mono.error(new IllegalArgumentException("URI de média invalide pour la suppression: " + mediaUri
                        + ". Format attendu: /service/type/{path}/{resource_id}/filename"));
            }

            String service = segments[1]; // ex: "product"
            String type = segments[2]; // ex: "image"
            String pathSegment = segments[3]; // ex: "avatars"
            String resourceIdSegment = segments[4]; // ex: "user_id_uuid"
            String filename = segments[5]; // ex: "unique_filename.jpg"

            // ---------- CORRECTION DE LA CONSTRUCTION DE L'URI POUR DELETE ----------
            // L'API DELETE attend: /media/{service}/{type}/{path}/{filename}
            // où {path} représente le chemin complet jusqu'au dossier du fichier, SANS le
            // nom de fichier.
            // Donc, notre {path} doit être "avatars/user_id_uuid"
            String fullPathForDelete = String.format("%s/%s", pathSegment, resourceIdSegment); // Ceci crée
                                                                                               // "avatars/user_id_uuid"
            // -------------------------------------------------------------------

            String apiUrl = String.format("/media/%s/%s/%s/%s", service, type, fullPathForDelete, filename);

            log.info("[ExternalMediaStorageService] URI de l'API externe pour DELETE : {}", apiUrl);

            return getM2MBearerToken()
                    .flatMap(m2mBearerToken -> externalMediaServiceWebClient.delete()
                            .uri(apiUrl)
                            .header(HttpHeaders.AUTHORIZATION, m2mBearerToken)
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error(
                                                "❌ Erreur HTTP lors de la suppression de l'API externe (Status: {}): {}",
                                                response.statusCode(), errorBody);
                                        return Mono.just(new RuntimeException(
                                                "Échec de la suppression du fichier sur le service de médias externe: "
                                                        + errorBody));
                                    })
                                    .switchIfEmpty(Mono.defer(() -> Mono.just(new RuntimeException(
                                            "Échec de la suppression du fichier sur le service de médias externe: corps d'erreur vide. Status: "
                                                    + response.statusCode())))))
                            .bodyToMono(Void.class)
                            .doOnError(e -> log.error(
                                    "[ExternalMediaStorageService] Erreur inattendue lors de la suppression de l'API externe : {}",
                                    e.getMessage(), e)));

        } catch (URISyntaxException e) {
            log.error("URI de média invalide fournie pour la suppression: {}", mediaUri, e);
            return Mono.error(new IllegalArgumentException("URI de média invalide: " + mediaUri, e));
        }
    }
}