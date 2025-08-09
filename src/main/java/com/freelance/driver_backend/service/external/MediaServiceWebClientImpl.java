package com.freelance.driver_backend.service.external;

import com.freelance.driver_backend.dto.external.UploadMediaResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Service
@Profile("production") // NE S'ACTIVE QU'EN PROFIL PRODUCTION
@Slf4j
public class MediaServiceWebClientImpl implements MediaService {

    private final WebClient webClient;

    public MediaServiceWebClientImpl(@Value("${microservices.media-service.url}") String mediaServiceUrl) {
        // Ce WebClient est configuré spécifiquement pour les uploads de fichiers,
        // qui peuvent prendre plus de temps. Il n'ajoute pas de "Content-Type" par défaut.
        this.webClient = WebClient.builder()
            .baseUrl(mediaServiceUrl)
            .clientConnector(new ReactorClientHttpConnector(
                HttpClient.create().responseTimeout(Duration.ofSeconds(30)) // Timeout de 30s pour l'upload
            ))
            .build();
    }

    @Override
    public Mono<UploadMediaResponse> uploadFile(String service, String type, String path, String bearerToken, String publicKey, FilePart filePart) {
        log.info("Relaying file to EXTERNAL Media Service. Path: {}/{}/{}", service, type, path);

        // On prépare le corps de la requête en "multipart/form-data"
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);
        
        return webClient.post()
                .uri("/{service}/{type}/{path}", service, type, path)
                .header("Authorization", bearerToken)
                .header("Public-Key", publicKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body))
                .retrieve()
                .bodyToMono(UploadMediaResponse.class)
                .doOnError(e -> log.error("Error uploading file to external media service", e));
    }

    @Override
    public Mono<Void> deleteFile(String filePath, String bearerToken, String publicKey) {
        log.warn("[EXTERNAL-IMPL-MEDIA] deleteFile - NOT IMPLEMENTED YET. This would call the real delete endpoint.");
        // Pour la production, vous implémenteriez l'appel DELETE ici.
        // Pour le dev, on retourne simplement un succès.
        return Mono.empty();
    }
}