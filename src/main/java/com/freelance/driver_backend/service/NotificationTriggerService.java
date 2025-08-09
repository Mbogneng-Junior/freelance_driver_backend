package com.freelance.driver_backend.service;

import com.freelance.driver_backend.dto.external.NotificationRequest;
import com.freelance.driver_backend.model.Product;
import com.freelance.driver_backend.repository.ClientProfileRepository;
import com.freelance.driver_backend.repository.DeviceTokenRepository;
import com.freelance.driver_backend.service.external.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux; // <-- AJOUTEZ CETTE LIGNE

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationTriggerService {

    private final ClientProfileRepository clientProfileRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationService notificationService;
    
    private final UUID NEW_PLANNING_TEMPLATE_ID = UUID.fromString("c91e5217-c89d-41ca-bcf4-d94271de5493"); 
    // ==============================================================================

    public Mono<Void> notifyAllClientsOfNewPlanning(Product planning) {
        log.info("Déclenchement des notifications pour le nouveau planning: {}", planning.getName());

        // 1. Récupérer tous les IDs des clients
        return clientProfileRepository.findAll()
            .map(clientProfile -> clientProfile.getUserId())
            .collectList()
            .flatMap(clientUserIds -> {
                if (clientUserIds.isEmpty()) {
                    log.warn("Aucun client à notifier.");
                    return Mono.empty();
                }
                
                // 2. Récupérer tous les tokens pour ces IDs
                return Flux.fromIterable(clientUserIds)
                    .flatMap(deviceTokenRepository::findByUserId)
                    .map(deviceToken -> deviceToken.getToken())
                    .collect(Collectors.toSet()) // Utiliser un Set pour éviter les doublons
                    .flatMap(tokens -> {
                        if (tokens.isEmpty()) {
                            log.warn("Aucun token d'appareil trouvé pour les clients.");
                            return Mono.empty();
                        }
                        
                        // 3. Construire et envoyer la notification
                        log.info("Envoi de la notification push à {} appareils.", tokens.size());
                        
                        // ==============================================================================
                        //      CONFIGURATION DES METADATA POUR LE TEMPLATE (OPTION 3)
                        // ==============================================================================
                        NotificationRequest request = NotificationRequest.builder()
                            .templateId(NEW_PLANNING_TEMPLATE_ID)
                            .recipients(List.copyOf(tokens))
                            .metadata(Map.of(
                                // Variables du template: {{driverName}}, {{destination}}, {{cost}}
                                "driverName", planning.getClientName(),
                                "destination", planning.getDropoffLocation(),
                                "cost", planning.getDefaultSellPrice() != null ? planning.getDefaultSellPrice().toString() : "0"
                            ))
                            .build();
                        // ==============================================================================

                        // On appelle le service de notification (la méthode sendPushNotification)
                        return notificationService.sendPushNotification(planning.getOrganizationId(), request, null, null);
                    });
            })
            .then(); // Retourne un Mono<Void> une fois terminé
    }
}