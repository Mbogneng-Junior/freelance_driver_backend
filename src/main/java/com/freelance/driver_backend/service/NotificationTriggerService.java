/*package com.freelance.driver_backend.service;

import com.freelance.driver_backend.dto.external.NotificationRequest;
import com.freelance.driver_backend.model.DeviceToken;
import com.freelance.driver_backend.model.DriverProfile;
import com.freelance.driver_backend.model.Product;
import com.freelance.driver_backend.repository.ClientProfileRepository;
import com.freelance.driver_backend.repository.DeviceTokenRepository;
import com.freelance.driver_backend.repository.DriverProfileRepository;
import com.freelance.driver_backend.service.external.NotificationService;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap; // <-- NOUVEL IMPORT
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationTriggerService {

    private final ClientProfileRepository clientProfileRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationService notificationService;
    private final Dotenv dotenv;

    
    public Mono<Void> notifyClientOfAcceptedAnnouncement(Product announcement, DriverProfile driverProfile) {
        UUID targetClientId = announcement.getClientId();
        if (targetClientId == null) return Mono.empty();
        
        log.info("Déclenchement de la notification pour le client {} (postulation par chauffeur {}) pour l'annonce {}.", targetClientId, driverProfile.getUserId(), announcement.getName());

        return deviceTokenRepository.findByUserId(targetClientId)
            .map(DeviceToken::getToken)
            .collectList()
            .flatMap(tokens -> {
                if (tokens.isEmpty()) {
                    log.warn("Aucun token d'appareil trouvé pour le client {}.", targetClientId);
                    return Mono.empty();
                }
                
                UUID templateId = UUID.fromString(dotenv.get("TEMPLATE_PUSH_ANNOUNCEMENT_ACCEPTED_ID"));
                
                // --- AJOUT DU PAYLOAD DE DONNÉES POUR LE ROUTAGE FRONTEND ---
                Map<String, String> dataPayload = new HashMap<>();
                dataPayload.put("screen", "DriverDetails"); // Nom de l'écran cible dans le frontend
                dataPayload.put("driverId", driverProfile.getUserId().toString());
                dataPayload.put("announcementId", announcement.getId().toString()); // L'ID de l'annonce en question
                dataPayload.put("source", "notification"); // Pour indiquer la provenance
                // -----------------------------------------------------------

                NotificationRequest request = NotificationRequest.builder()
                    .templateId(templateId)
                    .recipients(tokens)
                    .metadata(Map.of(
                        "driverName", driverProfile.getFirstName() + " " + driverProfile.getLastName(),
                        "tripTitle", announcement.getName(),
                        "driverId", driverProfile.getUserId().toString() 
                    ))
                    .data(dataPayload) // <-- AJOUT DU DATA PAYLOAD
                    .build();
                
                return notificationService.sendPushNotification(announcement.getOrganizationId(), request, null, null);
            })
            .then();
    }

    
    public Mono<Void> notifyDriverOfConfirmedAnnouncement(Product announcement, DriverProfile driverProfile) {
        UUID targetDriverId = driverProfile.getUserId();
        if (targetDriverId == null) return Mono.empty();

        log.info("Déclenchement de la notification pour le chauffeur {} (postulation ACCEPTÉE) pour l'annonce {}.", targetDriverId, announcement.getName());

        return deviceTokenRepository.findByUserId(targetDriverId)
            .map(DeviceToken::getToken)
            .collectList()
            .flatMap(tokens -> {
                if (tokens.isEmpty()) {
                    log.warn("Aucun token d'appareil trouvé pour le chauffeur {}.", targetDriverId);
                    return Mono.empty();
                }

                UUID templateId = UUID.fromString(dotenv.get("TEMPLATE_PUSH_ANNOUNCEMENT_CONFIRMED_ID")); 
                
                return clientProfileRepository.findByUserId(announcement.getClientId())
                    .flatMap(clientProfile -> {
                        // --- AJOUT DU PAYLOAD DE DONNÉES POUR LE ROUTAGE FRONTEND ---
                        Map<String, String> dataPayload = new HashMap<>();
                        dataPayload.put("screen", "MyAcceptedRides"); // Exemple: écran où le chauffeur voit ses courses acceptées
                        dataPayload.put("tripId", announcement.getId().toString());
                        dataPayload.put("source", "notification");
                        // -----------------------------------------------------------

                        NotificationRequest request = NotificationRequest.builder()
                            .templateId(templateId)
                            .recipients(tokens)
                            .metadata(Map.of(
                                "clientName", clientProfile.getFirstName() + " " + clientProfile.getLastName(),
                                "tripTitle", announcement.getName(),
                                "tripId", announcement.getId().toString()
                            ))
                            .data(dataPayload) // <-- AJOUT DU DATA PAYLOAD
                            .build();
                        return notificationService.sendPushNotification(announcement.getOrganizationId(), request, null, null);
                    })
                    .then();
            })
            .then();
    }
    
    
    public Mono<Void> notifyAllClientsOfNewPlanning(Product planning) {
        log.info("Déclenchement des notifications à tous les clients pour le nouveau planning: {}", planning.getName());
        
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

    
    public Mono<Void> notifyAllDriversOfNewAnnouncement(Product announcement) {
        log.info("Déclenchement des notifications à tous les chauffeurs pour la nouvelle annonce: {}", announcement.getName());
        
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
}*/


package com.freelance.driver_backend.service;

import com.freelance.driver_backend.dto.external.NotificationRequest;
import com.freelance.driver_backend.model.DeviceToken;
import com.freelance.driver_backend.model.DriverProfile;
import com.freelance.driver_backend.model.Product;
import com.freelance.driver_backend.repository.ClientProfileRepository;
import com.freelance.driver_backend.repository.DeviceTokenRepository;
import com.freelance.driver_backend.repository.DriverProfileRepository;
import com.freelance.driver_backend.service.external.NotificationService;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationTriggerService {

    private final ClientProfileRepository clientProfileRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationService notificationService;
    private final Dotenv dotenv;

    /**
     * Notifie un client qu'un chauffeur a postulé à son annonce.
     */
    public Mono<Void> notifyClientOfAcceptedAnnouncement(Product announcement, DriverProfile driverProfile) {
        UUID targetClientId = announcement.getClientId();
        if (targetClientId == null) return Mono.empty();
        
        log.info("Déclenchement de la notification pour le client {} (postulation par chauffeur {}) pour l'annonce {}.", targetClientId, driverProfile.getUserId(), announcement.getName());

        return deviceTokenRepository.findByUserId(targetClientId)
            .map(DeviceToken::getToken)
            .collectList()
            .flatMap(tokens -> {
                if (tokens.isEmpty()) {
                    log.warn("Aucun token d'appareil trouvé pour le client {}.", targetClientId);
                    return Mono.empty();
                }
                
                UUID templateId = UUID.fromString(dotenv.get("TEMPLATE_PUSH_ANNOUNCEMENT_ACCEPTED_ID"));
                
                Map<String, String> dataPayload = new HashMap<>();
                dataPayload.put("screen", "DriverDetails");
                dataPayload.put("driverId", driverProfile.getUserId().toString());
                dataPayload.put("announcementId", announcement.getId().toString());
                dataPayload.put("source", "notification");
                
                NotificationRequest request = NotificationRequest.builder()
                    .templateId(templateId)
                    .recipients(tokens)
                    .metadata(Map.of(
                        "driverName", driverProfile.getFirstName() + " " + driverProfile.getLastName(),
                        "tripTitle", announcement.getName(),
                        "driverId", driverProfile.getUserId().toString() 
                    ))
                    .data(dataPayload)
                    .build();
                
                return notificationService.sendPushNotification(announcement.getOrganizationId(), request, null, null);
            })
            .then();
    }

    /**
     * Notifie un chauffeur que sa postulation a été acceptée par le client.
     */
    public Mono<Void> notifyDriverOfConfirmedAnnouncement(Product announcement, DriverProfile driverProfile) {
        UUID targetDriverId = driverProfile.getUserId();
        if (targetDriverId == null) return Mono.empty();

        log.info("Déclenchement de la notification pour le chauffeur {} (postulation ACCEPTÉE) pour l'annonce {}.", targetDriverId, announcement.getName());

        return deviceTokenRepository.findByUserId(targetDriverId)
            .map(DeviceToken::getToken)
            .collectList()
            .flatMap(tokens -> {
                if (tokens.isEmpty()) {
                    log.warn("Aucun token d'appareil trouvé pour le chauffeur {}.", targetDriverId);
                    return Mono.empty();
                }

                UUID templateId = UUID.fromString(dotenv.get("TEMPLATE_PUSH_ANNOUNCEMENT_CONFIRMED_ID")); 
                
                return clientProfileRepository.findByUserId(announcement.getClientId())
                    .flatMap(clientProfile -> {
                        Map<String, String> dataPayload = new HashMap<>();
                        dataPayload.put("screen", "MyAcceptedRides"); 
                        dataPayload.put("tripId", announcement.getId().toString());
                        dataPayload.put("source", "notification");

                        NotificationRequest request = NotificationRequest.builder()
                            .templateId(templateId)
                            .recipients(tokens)
                            .metadata(Map.of(
                                "clientName", clientProfile.getFirstName() + " " + clientProfile.getLastName(),
                                "tripTitle", announcement.getName(),
                                "tripId", announcement.getId().toString()
                            ))
                            .data(dataPayload)
                            .build();
                        return notificationService.sendPushNotification(announcement.getOrganizationId(), request, null, null);
                    })
                    .then();
            })
            .then();
    }

    /**
     * NOUVELLE FONCTION : Notifie le client qu'un chauffeur a annulé sa postulation.
     */
    public Mono<Void> notifyClientOfCancelledPostulation(Product announcement, DriverProfile driverProfile) {
        UUID targetClientId = announcement.getClientId();
        if (targetClientId == null) return Mono.empty();

        log.info("Déclenchement de la notification pour le client {} (postulation ANNULÉE par chauffeur {}) pour l'annonce {}.", targetClientId, driverProfile.getUserId(), announcement.getName());

        return deviceTokenRepository.findByUserId(targetClientId)
            .map(DeviceToken::getToken)
            .collectList()
            .flatMap(tokens -> {
                if (tokens.isEmpty()) {
                    log.warn("Aucun token d'appareil trouvé pour le client {}.", targetClientId);
                    return Mono.empty();
                }

                UUID templateId = UUID.fromString(dotenv.get("TEMPLATE_PUSH_POSTULATION_CANCELLED_ID")); // <-- NOUVEAU TEMPLATE ID
                
                Map<String, String> dataPayload = new HashMap<>();
                dataPayload.put("screen", "MyAnnouncements"); // Écran où le client voit ses annonces
                dataPayload.put("announcementId", announcement.getId().toString());
                dataPayload.put("source", "notification");

                NotificationRequest request = NotificationRequest.builder()
                    .templateId(templateId)
                    .recipients(tokens)
                    .metadata(Map.of(
                        "driverName", driverProfile.getFirstName() + " " + driverProfile.getLastName(),
                        "tripTitle", announcement.getName()
                    ))
                    .data(dataPayload)
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
                // Assurez-vous que sendPushNotification peut prendre le dataPayload si nécessaire
                return notificationService.sendPushNotification(organizationId, request, null, null); 
            });
    }
}