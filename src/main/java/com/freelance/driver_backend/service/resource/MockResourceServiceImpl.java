package com.freelance.driver_backend.service.resource;

import com.freelance.driver_backend.model.Resource;
import com.freelance.driver_backend.model.ResourceKey;
import com.freelance.driver_backend.repository.ClientProfileRepository;
import com.freelance.driver_backend.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class MockResourceServiceImpl implements ResourceService {

    private final ResourceRepository resourceRepository;
     private final ClientProfileRepository clientProfileRepository; 

     @Override
     public Mono<Resource> createResource(Resource resourceFromController) {
         log.warn("[MOCK] Creating new resource. Data received from controller: {}", resourceFromController.getName());
         
         // ==============================================================================
         //                         LA CORRECTION FINALE EST ICI
         // ==============================================================================
         // On s'assure de copier TOUS les champs pertinents de l'objet reçu du contrôleur
         // avant de le sauvegarder.
         
         Resource resourceToSave = new Resource();
         
         // Copie de la clé et des IDs
         resourceToSave.setKey(resourceFromController.getKey());
         
         // Copie des champs principaux
         resourceToSave.setName(resourceFromController.getName());
         resourceToSave.setStorageCondition(resourceFromController.getStorageCondition());
         resourceToSave.setLongDescription(resourceFromController.getLongDescription());
         resourceToSave.setShortDescription(resourceFromController.getShortDescription());
         resourceToSave.setBasePrice(resourceFromController.getBasePrice());
         resourceToSave.setSkuCode(resourceFromController.getSkuCode());
         resourceToSave.setExpiresAt(resourceFromController.getExpiresAt());
 
         // Copie des métadonnées (LE PLUS IMPORTANT)
         resourceToSave.setMetadata(resourceFromController.getMetadata());
 
         // Logique du Mock : on force le statut à "publiée"
         resourceToSave.setState("AVAILABLE");
         log.warn("[MOCK] Forcing resource state to 'AVAILABLE'.");
         
         // Timestamps
         resourceToSave.setCreatedAt(Instant.now());
         resourceToSave.setUpdatedAt(Instant.now());
 
         log.warn("[MOCK] Final object being saved to DB: {}", resourceToSave);
         
         return resourceRepository.save(resourceToSave);
     }
 

    // Le reste des méthodes est inchangé et fonctionnera correctement
    // car le service lira depuis la même base de données "mock" (Cassandra/ScyllaDB en l'occurrence).
    
   
    @Override
    public Flux<Resource> getResourcesByCategory(String categoryId) {
        log.warn("[MOCK] Fetching all resources for category {} and starting enrichment process.", categoryId);
        return resourceRepository.findByKeyCategoryId(categoryId)
                .flatMap(this::enrichResourceWithClientDetails);
    }
    
    private Mono<Resource> enrichResourceWithClientDetails(Resource resource) {
        if (resource.getMetadata() == null || resource.getMetadata().get("clientId") == null) {
            log.warn("!!!! [ENRICH] Annonce ID {} - ÉCHEC : Pas de clientId dans les métadonnées.", resource.getResourceId());
            return Mono.just(resource);
        }

        try {
            String clientIdStr = resource.getMetadata().get("clientId");
            UUID clientId = UUID.fromString(clientIdStr);
            log.info(">>>> [ENRICH] Annonce ID {} - Trouvé clientId : {}. Recherche du profil client...", resource.getResourceId(), clientId);
            
            // On cherche le profil et on loggue ce qu'on trouve (ou pas)
            return clientProfileRepository.findByUserId(clientId)
                .flatMap(clientProfile -> {
                    // ==============================================================================
                    //                         LOG DE VÉRIFICATION
                    // ==============================================================================
                    log.info(">>>> [ENRICH] Annonce ID {} - SUCCÈS : Profil trouvé pour clientId {}. Numéro de téléphone en BDD : '{}'", resource.getResourceId(), clientId, clientProfile.getPhoneNumber());
                    // ==============================================================================
                    
                    Map<String, String> metadata = resource.getMetadata();
                    metadata.put("clientName", clientProfile.getCompanyName());
                    metadata.put("clientPhoneNumber", clientProfile.getPhoneNumber());
                    resource.setMetadata(metadata);
                    return Mono.just(resource);
                })
                // Ce bloc est exécuté si `findByUserId` ne trouve RIEN
                .defaultIfEmpty(resource)
                .doOnSuccess(result -> {
                    // Ce log s'exécutera que le profil soit trouvé ou non.
                    // Il nous montrera si le numéro a été ajouté.
                    if (result.getMetadata().get("clientPhoneNumber") == null || result.getMetadata().get("clientPhoneNumber").isEmpty()) {
                        log.warn("!!!! [ENRICH] Annonce ID {} - FIN : Enrichissement terminé, MAIS le numéro de téléphone est TOUJOURS manquant. Le profil n'a probablement pas été trouvé en BDD.", resource.getResourceId());
                    }
                });

        } catch (IllegalArgumentException e) {
            log.error("!!!! [ENRICH] Le clientId '{}' pour l'annonce {} n'est pas un UUID valide.", resource.getMetadata().get("clientId"), resource.getResourceId());
            return Mono.just(resource);
        }
    }
    // On fait de même pour la recherche par organisation
    @Override
    public Flux<Resource> getResourcesByOrganisationAndCategory(UUID organisationId, String categoryId) {
        log.warn("[MOCK] Fetching resources for org {} and category {} and ENRICHING them.", organisationId, categoryId);
        return resourceRepository.findByKeyOrganizationIdAndKeyCategoryId(organisationId, categoryId)
                .flatMap(this::enrichResourceWithClientDetails);
    }

    /**
     * Méthode privée (copiée du contrôleur) pour enrichir une ressource.
     * C'est maintenant le RÔLE du service de le faire.
     */
    


    @Override
    public Mono<Resource> getResourceById(ResourceKey key) {
        log.warn("[MOCK] Fetching resource by composite key: {}", key);
        return resourceRepository.findById(key);
    }

    @Override
    public Mono<Resource> updateResource(ResourceKey key, Resource updatedResource) {
        log.warn("[MOCK] Updating resource with key: {}", key);
        return resourceRepository.findById(key)
                .flatMap(existingResource -> {
                    existingResource.setName(updatedResource.getName());
                    // ... mettez à jour les autres champs si nécessaire
                    existingResource.setState(updatedResource.getState());
                    existingResource.setUpdatedAt(Instant.now());
                    return resourceRepository.save(existingResource);
                });
    }

    @Override
    public Mono<Void> deleteResource(ResourceKey key) {
        log.warn("[MOCK] Deleting resource with key: {}", key);
        return resourceRepository.deleteById(key);
    }
}