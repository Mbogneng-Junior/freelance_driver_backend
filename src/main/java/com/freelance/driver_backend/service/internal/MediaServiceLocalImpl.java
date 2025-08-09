package com.freelance.driver_backend.service.internal;

import com.freelance.driver_backend.dto.external.UploadMediaResponse;
import com.freelance.driver_backend.service.external.MediaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Profile("dev-resource-mock")
@Slf4j
public class MediaServiceLocalImpl implements MediaService {

    // ==============================================================================
    //                         LA CORRECTION EST ICI
    // ==============================================================================
    private final WebClient localApiClient;

    public MediaServiceLocalImpl(@Qualifier("localApiWebClient") WebClient localApiClient) {
        this.localApiClient = localApiClient;
    }
    // ==============================================================================

    @Override
    public Mono<UploadMediaResponse> uploadFile(String service, String type, String path, String bearerToken, String publicKey, FilePart filePart) {
        log.warn("[LOCAL-IMPL-MEDIA] Appel du MockMediaController local pour téléversement...");

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);
        
        return localApiClient.post()
                .uri("/media/{service}/{type}/{path}", service, type, path)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body))
                .retrieve()
                .bodyToMono(UploadMediaResponse.class);
    }

    @Override
    public Mono<Void> deleteFile(String filePath, String bearerToken, String publicKey) {
        log.warn("[LOCAL-IMPL-MEDIA] Appel du MockMediaController local pour suppression du fichier : {}", filePath);
        // On construit l'URI en supposant que filePath est de la forme /service/type/path/filename
        return localApiClient.delete()
                .uri("/media" + filePath) 
                .retrieve()
                .bodyToMono(Void.class);
    }
}