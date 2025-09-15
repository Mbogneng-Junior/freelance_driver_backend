package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.dto.external.UploadMediaResponse;
import com.freelance.driver_backend.service.StorageService;
import com.freelance.driver_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// Retirer cet import car publicKey n'est plus directement utilisé ici
// import org.springframework.beans.factory.annotation.Value; 
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Slf4j
public class MediaController {

    private final StorageService storageService;

    // Retirer ceci si ce n'est plus utilisé nulle part dans le contrôleur
    // @Value("${freelancedriver.api.public-key}")
    // private String publicKey;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Map<String, String>>> uploadFile(
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader, // Nous avons toujours besoin de cet en-tête pour le contrôleur lui-même pour vérifier l'utilisateur.
            @RequestPart("file") Mono<FilePart> filePartMono,
            @RequestParam("type") String frontendType,
            @RequestParam("resourceId") String resourceId) {

        return Mono.zip(jwtMono, filePartMono)
            .flatMap(tuple -> {
                Jwt jwt = tuple.getT1();
                FilePart filePart = tuple.getT2();
                UUID userId = JwtUtil.getUserIdFromToken(jwt);
                UUID targetResourceId = UUID.fromString(resourceId);

                log.info("▶️ Téléversement d'un fichier de type logique '{}' pour la ressource '{}' par l'utilisateur '{}'", frontendType, resourceId, userId);

                // Le StorageService s'occupera d'obtenir son propre token M2M
                return storageService.saveFile(
                    "product",
                    frontendType,
                    userId,
                    targetResourceId,
                    filePart.filename(),
                    filePart
                )
                .map(uploadResponse -> {
                    log.info("✅ Fichier téléversé avec succès. URL: {}, URI: {}", uploadResponse.getUrl(), uploadResponse.getUri());
                    return ResponseEntity.ok(Map.of("url", uploadResponse.getUrl(), "uri", uploadResponse.getUri()));
                });
            });
    }
}