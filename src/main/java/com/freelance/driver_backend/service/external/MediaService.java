package com.freelance.driver_backend.service.external;

import com.freelance.driver_backend.dto.external.UploadMediaResponse;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public interface MediaService {
    Mono<UploadMediaResponse> uploadFile(String service, String type, String path, String bearerToken, String publicKey, FilePart filePart);
    Mono<Void> deleteFile(String fullPath, String bearerToken, String publicKey); // <-- AJOUTEZ CETTE LIGNE
}