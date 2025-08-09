package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.dto.UserSessionContextDto;
import com.freelance.driver_backend.model.ClientProfile;
import com.freelance.driver_backend.model.DriverProfile;
import com.freelance.driver_backend.service.ProfileService;
import com.freelance.driver_backend.service.StorageService;
import com.freelance.driver_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part; // <-- Import essentiel
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap; // <-- Import essentiel
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/profiles")

public class ProfileController {

    private final ProfileService profileService;
    private final StorageService storageService;

    @Value("${minio.bucket-name}")
    private final String bucketName;

    @Value("${freelancedriver.api.public-key}")
    private final String publicKey;

    public ProfileController(
            ProfileService profileService,
            StorageService storageService,
            @Value("${minio.bucket-name}") String bucketName,
            @Value("${freelancedriver.api.public-key}") String publicKey) {
        
        this.profileService = profileService;
        this.storageService = storageService;
        this.bucketName = bucketName;
        this.publicKey = publicKey;
    }

    @GetMapping("/me")
    public Mono<ResponseEntity<UserSessionContextDto>> getMyProfile(
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) {
        return jwtMono
                .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, publicKey))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/driver/me")
    public Mono<ResponseEntity<DriverProfile>> updateDriverProfile(
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestBody DriverProfile updatedProfileData) {
        return jwtMono
            .flatMap(jwt -> {
                UUID userId = JwtUtil.getUserIdFromToken(jwt);
                log.info("Requête de mise à jour du profil pour le conducteur ID: {}", userId);
                return profileService.updateDriverProfile(userId, updatedProfileData);
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    @PutMapping("/client/me")
    public Mono<ResponseEntity<ClientProfile>> updateClientProfile(
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestBody ClientProfile updatedProfileData) {
        
        return jwtMono
            .flatMap(jwt -> {
                UUID userId = JwtUtil.getUserIdFromToken(jwt);
                log.info("Requête de mise à jour du profil pour le client ID: {}", userId);
                return profileService.updateClientProfile(userId, updatedProfileData);
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // ==============================================================================
    //                       VERSION CORRIGÉE DE uploadAvatar
    // ==============================================================================
    @PostMapping("/me/avatar")
    public Mono<ResponseEntity<String>> uploadAvatar(
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestBody Mono<MultiValueMap<String, Part>> partsMono) { // <-- On reçoit toutes les parties

        Mono<UUID> userIdMono = jwtMono.map(JwtUtil::getUserIdFromToken).cache();

        return userIdMono.flatMap(userId -> 
            partsMono.flatMap(parts -> {
                // On cherche la partie qui s'appelle "file"
                Part part = parts.getFirst("file");

                // On vérifie que la partie existe et qu'elle est bien un fichier
                if (part == null || !(part instanceof FilePart)) {
                    log.error("Aucune partie de fichier valide nommée 'file' n'a été trouvée.");
                    return Mono.just(ResponseEntity.badRequest().body("Le fichier est manquant ou invalide."));
                }
                
                FilePart filePart = (FilePart) part;
                String basePath = "avatars/" + userId.toString();

                return profileService.findProfileByUserId(userId).defaultIfEmpty(new Object())
                    .flatMap(profile -> {
                        String oldAvatarUrl = profileService.getAvatarUrlFromProfile(profile);
                        
                        Mono<Void> deleteMono = Mono.empty();
                        if (oldAvatarUrl != null && !oldAvatarUrl.isBlank()) {
                            String objectName = extractObjectNameFromUrl(oldAvatarUrl);
                            if (objectName != null) {
                                log.info("Ancien avatar trouvé, suppression de : {}", objectName);
                                deleteMono = storageService.deleteFile(objectName);
                            }
                        }
                        
                        return deleteMono.then(storageService.saveFile(basePath, filePart.filename(), filePart));
                    })
                    .flatMap(newAvatarUrl -> 
                        profileService.updateAvatarUrl(userId, newAvatarUrl)
                            .thenReturn(ResponseEntity.ok(newAvatarUrl))
                    );
            })
        );
    }
    
    private String extractObjectNameFromUrl(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            String prefix = "/" + this.bucketName + "/";
            if (path.startsWith(prefix)) {
                return path.substring(prefix.length());
            }
        } catch (URISyntaxException e) {
            log.error("URL d'avatar invalide : {}", url, e);
        }
        return null;
    }
}