// src/main/java/com/freelance/driver_backend/controller/MediaController.java

package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.service.StorageService;
import com.freelance.driver_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * Endpoint générique et sécurisé pour téléverser des fichiers.
     * Le chemin de stockage est structuré pour éviter les conflits et organiser les fichiers.
     * @param jwtMono Le token JWT de l'utilisateur authentifié.
     * @param filePartMono Le fichier envoyé par le client.
     * @param type Le type de ressource (ex: "avatars", "vehicles", "documents").
     * @param resourceId L'ID de la ressource à laquelle le fichier est associé (ex: ID du véhicule).
     * @return Une réponse JSON contenant l'URL publique du fichier téléversé.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Map<String, String>>> uploadFile(
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestPart("file") Mono<FilePart> filePartMono,
            @RequestParam("type") String type,
            @RequestParam("resourceId") String resourceId) {

        return Mono.zip(jwtMono, filePartMono)
            .flatMap(tuple -> {
                Jwt jwt = tuple.getT1();
                FilePart filePart = tuple.getT2();
                UUID userId = JwtUtil.getUserIdFromToken(jwt);

                // Chemin de stockage structuré : {type}/{userId}/{resourceId}/{nom_fichier_unique}
                // Ex: "vehicles/uuid-de-l-user/uuid-du-vehicule/fichier-aleatoire.jpg"
                String basePath = String.format("%s/%s/%s", type, userId.toString(), resourceId);

                log.info("▶️ Téléversement d'un fichier de type '{}' pour la ressource '{}' par l'utilisateur '{}'", type, resourceId, userId);

                return storageService.saveFile(basePath, filePart.filename(), filePart)
                    .map(fileUrl -> {
                        log.info("✅ Fichier téléversé avec succès. URL: {}", fileUrl);
                        return ResponseEntity.ok(Map.of("url", fileUrl));
                    });
            });
    }
}