/* 

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
                // Assurez-vous que sendPushNotification peut prendre le dataPayload si nécessaire
                return notificationService.sendPushNotification(organizationId, request, null, null); 
            });
    }
}*/

package com.freelance.driver_backend.service;

import com.freelance.driver_backend.dto.external.NotificationRequest;
import com.freelance.driver_backend.model.ClientProfile;
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
     * Notifie le client qu'un chauffeur a annulé sa postulation.
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

                UUID templateId = UUID.fromString(dotenv.get("TEMPLATE_PUSH_POSTULATION_CANCELLED_ID")); 
                
                Map<String, String> dataPayload = new HashMap<>();
                dataPayload.put("screen", "MyAnnouncements"); 
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
     * NOUVEAU : Notifie le CHAUFFEUR qu'un client a DEMANDÉ à réserver son planning.
     */
    public Mono<Void> notifyDriverOfPlanningBookingRequest(Product planning, ClientProfile clientProfile) {
        UUID targetDriverId = planning.getClientId(); // L'auteur du planning est le chauffeur
        if (targetDriverId == null) return Mono.empty();

        log.info("Déclenchement de la notification pour le chauffeur {} (demande de réservation de planning par client {}) pour le planning {}.", targetDriverId, clientProfile.getUserId(), planning.getName());

        return deviceTokenRepository.findByUserId(targetDriverId)
            .map(DeviceToken::getToken)
            .collectList()
            .flatMap(tokens -> {
                if (tokens.isEmpty()) {
                    log.warn("Aucun token d'appareil trouvé pour le chauffeur {}.", targetDriverId);
                    return Mono.empty();
                }

                UUID templateId = UUID.fromString(dotenv.get("TEMPLATE_PUSH_PLANNING_BOOKING_REQUESTED_TO_DRIVER_ID"));
                
                Map<String, String> dataPayload = new HashMap<>();
                dataPayload.put("screen", "MyPlannings"); // Écran où le chauffeur voit ses plannings ou demandes
                dataPayload.put("planningId", planning.getId().toString());
                dataPayload.put("clientId", clientProfile.getUserId().toString()); // Pour que le chauffeur puisse confirmer
                dataPayload.put("source", "notification");

                NotificationRequest request = NotificationRequest.builder()
                    .templateId(templateId)
                    .recipients(tokens)
                    .metadata(Map.of(
                        "clientName", clientProfile.getFirstName() + " " + clientProfile.getLastName(),
                        "tripTitle", planning.getName()
                    ))
                    .data(dataPayload)
                    .build();
                return notificationService.sendPushNotification(planning.getOrganizationId(), request, null, null);
            })
            .then();
    }

    /**
     * NOUVEAU : Notifie le CLIENT qu'un chauffeur a ACCEPTÉ sa demande de réservation de planning.
     */
    public Mono<Void> notifyClientOfPlanningBookingAccepted(Product planning, DriverProfile driverProfile) {
        UUID targetClientId = planning.getReservedByDriverId(); // C'est le client qui avait initié la réservation
        if (targetClientId == null) return Mono.empty();

        log.info("Déclenchement de la notification pour le client {} (réservation de planning ACCEPTÉE par chauffeur {}) pour le planning {}.", targetClientId, driverProfile.getUserId(), planning.getName());

        return deviceTokenRepository.findByUserId(targetClientId)
            .map(DeviceToken::getToken)
            .collectList()
            .flatMap(tokens -> {
                if (tokens.isEmpty()) {
                    log.warn("Aucun token d'appareil trouvé pour le client {}.", targetClientId);
                    return Mono.empty();
                }

                UUID templateId = UUID.fromString(dotenv.get("TEMPLATE_PUSH_PLANNING_BOOKING_ACCEPTED_TO_CLIENT_ID"));
                
                Map<String, String> dataPayload = new HashMap<>();
                dataPayload.put("screen", "MyReservations"); // Écran où le client voit ses réservations
                dataPayload.put("planningId", planning.getId().toString());
                dataPayload.put("driverId", driverProfile.getUserId().toString()); // Infos du chauffeur si besoin
                dataPayload.put("source", "notification");

                NotificationRequest request = NotificationRequest.builder()
                    .templateId(templateId)
                    .recipients(tokens)
                    .metadata(Map.of(
                        "driverName", driverProfile.getFirstName() + " " + driverProfile.getLastName(),
                        "tripTitle", planning.getName()
                    ))
                    .data(dataPayload)
                    .build();
                return notificationService.sendPushNotification(planning.getOrganizationId(), request, null, null);
            })
            .then();
    }

    /**
     * NOUVEAU : Notifie le CHAUFFEUR qu'un client a ANNULÉ sa réservation ou sa demande de réservation de planning.
     */
    public Mono<Void> notifyDriverOfCancelledPlanningReservation(Product planning, ClientProfile clientProfile) {
        UUID targetDriverId = planning.getClientId(); // L'auteur du planning est le chauffeur
        if (targetDriverId == null) return Mono.empty();

        log.info("Déclenchement de la notification pour le chauffeur {} (réservation/demande ANNULÉE par client {}) pour le planning {}.", targetDriverId, clientProfile.getUserId(), planning.getName());

        return deviceTokenRepository.findByUserId(targetDriverId)
            .map(DeviceToken::getToken)
            .collectList()
            .flatMap(tokens -> {
                if (tokens.isEmpty()) {
                    log.warn("Aucun token d'appareil trouvé pour le chauffeur {}.", targetDriverId);
                    return Mono.empty();
                }

                UUID templateId = UUID.fromString(dotenv.get("TEMPLATE_PUSH_PLANNING_RESERVATION_CANCELLED_ID"));
                
                Map<String, String> dataPayload = new HashMap<>();
                dataPayload.put("screen", "MyPlannings"); // Écran où le chauffeur voit ses plannings
                dataPayload.put("planningId", planning.getId().toString());
                dataPayload.put("source", "notification");

                NotificationRequest request = NotificationRequest.builder()
                    .templateId(templateId)
                    .recipients(List.copyOf(tokens))
                    .metadata(Map.of(
                        "clientName", clientProfile.getFirstName() + " " + clientProfile.getLastName(),
                        "tripTitle", planning.getName()
                    ))
                    .data(dataPayload)
                    .build();
                return notificationService.sendPushNotification(planning.getOrganizationId(), request, null, null);
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