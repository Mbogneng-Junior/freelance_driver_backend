package com.freelance.driver_backend.service;

import com.freelance.driver_backend.dto.external.NotificationRequest;
import com.freelance.driver_backend.model.DeviceToken;
import com.freelance.driver_backend.model.DriverProfile;
import com.freelance.driver_backend.model.Product;
import com.freelance.driver_backend.repository.ClientProfileRepository;
import com.freelance.driver_backend.repository.DeviceTokenRepository;
import com.freelance.driver_backend.repository.DriverProfileRepository;
import com.freelance.driver_backend.service.external.NotificationService;
import io.github.cdimascio.dotenv.Dotenv; // <-- NOUVEL IMPORT
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationTriggerService {

    // --- Dépendances ---
    private final ClientProfileRepository clientProfileRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationService notificationService;
    private final Dotenv dotenv; // <-- INJECTION DE DOTENV POUR LIRE LE FICHIER .env

    // --- Les IDs de templates ne sont plus des constantes fixes dans ce fichier ---

    /**
     * Notifie un client que son annonce a été acceptée par un chauffeur.
     */
    public Mono<Void> notifyClientOfAcceptedAnnouncement(Product announcement, DriverProfile driverProfile) {
        UUID targetClientId = announcement.getClientId();
        if (targetClientId == null) return Mono.empty();
        
        log.info("Déclenchement de la notification pour le client {} (annonce acceptée)", targetClientId);

        return deviceTokenRepository.findByUserId(targetClientId)
            .map(DeviceToken::getToken)
            .collectList()
            .flatMap(tokens -> {
                if (tokens.isEmpty()) {
                    log.warn("Aucun token d'appareil trouvé pour le client {}.", targetClientId);
                    return Mono.empty();
                }
                
                // On lit l'ID du template dynamiquement depuis le fichier .env
                UUID templateId = UUID.fromString(dotenv.get("TEMPLATE_PUSH_ANNOUNCEMENT_ACCEPTED_ID"));
                
                NotificationRequest request = NotificationRequest.builder()
                    .templateId(templateId)
                    .recipients(tokens)
                    .metadata(Map.of(
                        "driverName", driverProfile.getFirstName(),
                        "tripTitle", announcement.getName(),
                        "driverId", driverProfile.getUserId().toString() 
                    ))
                    .build();
                
                return notificationService.sendPushNotification(announcement.getOrganizationId(), request, null, null);
            })
            .then();
    }
    
    /**
     * Notifie TOUS les clients de la publication d'un nouveau planning par un chauffeur.
     */
    public Mono<Void> notifyAllClientsOfNewPlanning(Product planning) {
        log.info("Déclenchement des notifications à tous les clients pour le nouveau planning: {}", planning.getName());
        
        // On lit l'ID du template dynamiquement depuis le fichier .env
        UUID templateId = UUID.fromString(dotenv.get("TEMPLATE_PUSH_NEW_PLANNING_ID"));
        
        return clientProfileRepository.findAll()
            .map(client -> client.getUserId())
            .collectList()
            .flatMap(clientIds -> this.sendBroadcastNotification(
                clientIds, 
                templateId, 
                planning.getOrganizationId(), 
                Map.of(
                    "driverName", planning.getClientName(),
                    "destination", planning.getDropoffLocation(),
                    "cost", planning.getDefaultSellPrice() != null ? planning.getDefaultSellPrice().toString() : "0"
                )
            )).then();
    }

    /**
     * Notifie TOUS les chauffeurs de la publication d'une nouvelle annonce par un client.
     */
    public Mono<Void> notifyAllDriversOfNewAnnouncement(Product announcement) {
        log.info("Déclenchement des notifications à tous les chauffeurs pour la nouvelle annonce: {}", announcement.getName());
        
        // On lit l'ID du template dynamiquement depuis le fichier .env
        UUID templateId = UUID.fromString(dotenv.get("TEMPLATE_PUSH_NEW_ANNOUNCEMENT_ID"));
        
        return driverProfileRepository.findAll()
            .map(driver -> driver.getUserId())
            .collectList()
            .flatMap(driverIds -> this.sendBroadcastNotification(
                driverIds, 
                templateId, 
                announcement.getOrganizationId(), 
                Map.of("tripTitle", announcement.getName())
            )).then();
    }
    
    /**
     * Méthode utilitaire privée pour envoyer une notification à une liste d'utilisateurs.
     */
    private Mono<Boolean> sendBroadcastNotification(List<UUID> userIds, UUID templateId, UUID organizationId, Map<String, String> metadata) {
        if (userIds.isEmpty()) {
            log.warn("La liste d'utilisateurs à notifier est vide. Annulation.");
            return Mono.just(true);
        }
        return Flux.fromIterable(userIds)
            .flatMap(deviceTokenRepository::findByUserId)
            .map(DeviceToken::getToken)
            .collect(Collectors.toSet())
            .flatMap(tokens -> {
                if (tokens.isEmpty()) {
                    log.warn("Aucun token d'appareil trouvé pour la liste d'utilisateurs.");
                    return Mono.just(true);
                }
                log.info("Envoi de la notification (template {}) à {} appareils.", templateId, tokens.size());
                NotificationRequest request = NotificationRequest.builder()
                    .templateId(templateId)
                    .recipients(List.copyOf(tokens))
                    .metadata(metadata)
                    .build();
                return notificationService.sendPushNotification(organizationId, request, null, null);
            });
    }
}