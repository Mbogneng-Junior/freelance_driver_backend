// PATH: /home/mbogneng-junior/freelance-driver (Copie)/backend/src/main/java/com/freelance/driver_backend/service/ProfileService.java

package com.freelance.driver_backend.service;

import com.freelance.driver_backend.dto.UserSessionContextDto;
import com.freelance.driver_backend.dto.external.OrganisationDto;
import com.freelance.driver_backend.model.ClientProfile;
import com.freelance.driver_backend.model.DriverProfile;
import com.freelance.driver_backend.repository.ClientProfileRepository;
import com.freelance.driver_backend.repository.DriverProfileRepository;
import com.freelance.driver_backend.service.external.OrganisationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.ArrayList;
import java.util.Collections; // Import nécessaire pour Collections.singletonList
import java.util.List;
import java.util.UUID;
import java.net.URI;
import java.net.URISyntaxException;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProfileService {

    private final DriverProfileRepository driverProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final OrganisationService organisationService; // Nécessaire pour fetcher l'OrganisationDto réel

    public Mono<UserSessionContextDto> getUserSessionContext(UUID userId, String userBearerToken, String publicKey) {
        log.info("ProfileService: Recherche du contexte pour l'utilisateur ID: {}", userId);

        // Récupérer les deux profils en parallèle
        // Utilisez un objet vide si non trouvé, mais assurez-vous qu'il a un getId() qui renvoie null ou un ID non valide
        Mono<DriverProfile> driverProfileMono = driverProfileRepository.findByUserId(userId).defaultIfEmpty(new DriverProfile());
        Mono<ClientProfile> clientProfileMono = clientProfileRepository.findByUserId(userId).defaultIfEmpty(new ClientProfile());

        return Mono.zip(driverProfileMono, clientProfileMono)
            .flatMap(tuple -> {
                DriverProfile driverProfile = tuple.getT1();
                ClientProfile clientProfile = tuple.getT2();

                List<UserSessionContextDto.UserRole> roles = new ArrayList<>();
                OrganisationDto primaryOrganisation = null; // Nous essayerons de déterminer une organisation principale

                // Vérifiez que le profil chauffeur existe réellement (ID non null)
                if (driverProfile != null && driverProfile.getId() != null) {
                    roles.add(UserSessionContextDto.UserRole.DRIVER);
                    if (driverProfile.getOrganisationId() != null) {
                        // Ici, vous devriez appeler l'organisationService.getOrganisationById avec userBearerToken et publicKey
                        // Pour le mock ou le local, nous créons un DTO factice
                        primaryOrganisation = createMockOrganisationDto(driverProfile.getOrganisationId(), driverProfile.getFirstName() + "'s Driving Business");
                        // Exemple pour le vrai service:
                        // primaryOrganisation = organisationService.getOrganisationById(driverProfile.getOrganisationId(), userBearerToken, publicKey).block();
                    }
                }
                // Vérifiez que le profil client existe réellement
                if (clientProfile != null && clientProfile.getId() != null) {
                    roles.add(UserSessionContextDto.UserRole.CLIENT);
                    // Si aucune organisation principale n'a été définie par le profil chauffeur, utilisez celle du client.
                    // Ou vous pouvez avoir une logique plus complexe pour choisir l'organisation principale.
                    if (primaryOrganisation == null && clientProfile.getOrganisationId() != null) {
                         primaryOrganisation = createMockOrganisationDto(clientProfile.getOrganisationId(), clientProfile.getCompanyName());
                        // Exemple pour le vrai service:
                        // primaryOrganisation = organisationService.getOrganisationById(clientProfile.getOrganisationId(), userBearerToken, publicKey).block();
                    }
                }

                if (roles.isEmpty()) {
                    // Ligne 94 ou équivalente - CORRIGÉE
                    roles.add(UserSessionContextDto.UserRole.NO_PROFILE); // Assure qu'il y a au moins un rôle
                    log.warn("ProfileService: Aucun profil (driver ou client) trouvé pour l'utilisateur {}. Renvoi du statut NO_PROFILE.", userId);
                } else {
                     log.info("ProfileService: Profil(s) trouvé(s) pour l'utilisateur {}. Rôles: {}", userId, roles);
                }

                // Ligne 39 ou équivalente - CORRIGÉE (ajout de Mono.just())
                return Mono.just(UserSessionContextDto.builder()
                        .userId(userId)
                        .roles(roles) // Correct, utilise le champ pluriel 'roles' de type List
                        .driverProfile(driverProfile != null && driverProfile.getId() != null ? driverProfile : null)
                        .clientProfile(clientProfile != null && clientProfile.getId() != null ? clientProfile : null)
                        .organisation(primaryOrganisation)
                        .build());
            });
    }

    // Méthode utilitaire pour créer un OrganisationDto factice (à supprimer si vous utilisez le vrai service d'organisation)
    private OrganisationDto createMockOrganisationDto(UUID orgId, String name) {
        OrganisationDto orgDto = new OrganisationDto();
        orgDto.setOrganizationId(orgId); // Assurez-vous que OrganisationDto a un setter pour organizationId
        orgDto.setLongName(name);       // Assurez-vous que OrganisationDto a un setter pour longName
        orgDto.setStatus("ACTIVE");
        return orgDto;
    }

    // Supprimez l'annotation @Override pour cette méthode
    public Mono<UUID> findOrganisationIdByUserId(UUID userId) {
        log.info("Recherche de l'ID d'organisation pour l'utilisateur ID: {}", userId);
        return driverProfileRepository.findByUserId(userId)
                .map(DriverProfile::getOrganisationId)
                .switchIfEmpty(Mono.defer(() ->
                    clientProfileRepository.findByUserId(userId)
                            .map(ClientProfile::getOrganisationId)
                ))
                .switchIfEmpty(Mono.error(new RuntimeException("Aucun profil (conducteur ou client) trouvé pour l'utilisateur " + userId)));
    }


    public Mono<DriverProfile> findDriverById(UUID driverId) {
        log.info("Recherche du profil pour le chauffeur ID: {}", driverId);
        return driverProfileRepository.findByUserId(driverId);
    }

    public Mono<ClientProfile> findClientById(UUID clientId) {
        log.info("Recherche du profil pour le client ID: {}", clientId);
        return clientProfileRepository.findByUserId(clientId);
    }

    public Mono<DriverProfile> updateDriverProfile(UUID userId, DriverProfile updatedData) {
        return driverProfileRepository.findByUserId(userId)
            .flatMap(existingProfile -> {
                log.info("Mise à jour du profil pour le conducteur ID: {}", userId);
                // Mise à jour sélective pour ne pas écraser les champs non fournis
                if (updatedData.getFirstName() != null) existingProfile.setFirstName(updatedData.getFirstName());
                if (updatedData.getLastName() != null) existingProfile.setLastName(updatedData.getLastName());
                if (updatedData.getNickname() != null) existingProfile.setNickname(updatedData.getNickname());
                if (updatedData.getBirthDate() != null) existingProfile.setBirthDate(updatedData.getBirthDate());
                if (updatedData.getPhoneNumber() != null) existingProfile.setPhoneNumber(updatedData.getPhoneNumber());
                if (updatedData.getNationality() != null) existingProfile.setNationality(updatedData.getNationality());
                if (updatedData.getGender() != null) existingProfile.setGender(updatedData.getGender());
                if (updatedData.getLanguage() != null) existingProfile.setLanguage(updatedData.getLanguage());
                if (updatedData.getBiography() != null) existingProfile.setBiography(updatedData.getBiography());
                if (updatedData.getVehicleDetails() != null) existingProfile.setVehicleDetails(updatedData.getVehicleDetails());
                if (updatedData.getProfileImageUrl() != null) existingProfile.setProfileImageUrl(updatedData.getProfileImageUrl());
                
                return driverProfileRepository.save(existingProfile);
            });
    }

    public Mono<ClientProfile> updateClientProfile(UUID userId, ClientProfile updatedData) {
        return clientProfileRepository.findByUserId(userId)
            .flatMap(existingProfile -> {
                // Mettre à jour tous les champs pertinents
                if (updatedData.getCompanyName() != null) existingProfile.setCompanyName(updatedData.getCompanyName());
                if (updatedData.getFirstName() != null) existingProfile.setFirstName(updatedData.getFirstName());
                if (updatedData.getLastName() != null) existingProfile.setLastName(updatedData.getLastName());
                if (updatedData.getNickname() != null) existingProfile.setNickname(updatedData.getNickname());
                if (updatedData.getContactEmail() != null) existingProfile.setContactEmail(updatedData.getContactEmail());
                if (updatedData.getPhoneNumber() != null) existingProfile.setPhoneNumber(updatedData.getPhoneNumber());
                if (updatedData.getBirthDate() != null) existingProfile.setBirthDate(updatedData.getBirthDate());
                if (updatedData.getNationality() != null) existingProfile.setNationality(updatedData.getNationality());
                if (updatedData.getGender() != null) existingProfile.setGender(updatedData.getGender());
                if (updatedData.getLanguage() != null) existingProfile.setLanguage(updatedData.getLanguage());
                if (updatedData.getProfileImageUrl() != null) existingProfile.setProfileImageUrl(updatedData.getProfileImageUrl());
                
                return clientProfileRepository.save(existingProfile);
            });
    }
    
    public Mono<Object> findProfileByUserId(UUID userId) {
        return driverProfileRepository.findByUserId(userId)
                .cast(Object.class)
                .switchIfEmpty(Mono.defer(() -> clientProfileRepository.findByUserId(userId).cast(Object.class)));
    }

    // Ces méthodes devraient idéalement utiliser les objets DriverProfile ou ClientProfile directement
    // passés depuis le UserSessionContextDto pour éviter des appels DB supplémentaires
    public String getAuthorFirstNameFromProfile(Object profile) {
        if (profile instanceof DriverProfile) {
            return ((DriverProfile) profile).getFirstName();
        } else if (profile instanceof ClientProfile) {
            return ((ClientProfile) profile).getFirstName();
        }
        return "Utilisateur";
    }

    public String getAuthorLastNameFromProfile(Object profile) {
        if (profile instanceof DriverProfile) {
            return ((DriverProfile) profile).getLastName();
        } else if (profile instanceof ClientProfile) {
            return ((ClientProfile) profile).getLastName();
        }
        return "Anonyme";
    }

    public String getAvatarUrlFromProfile(Object profile) {
        if (profile instanceof DriverProfile) {
            return ((DriverProfile) profile).getProfileImageUrl();
        } else if (profile instanceof ClientProfile) {
            return ((ClientProfile) profile).getProfileImageUrl();
        }
        return null;
    }

    public Mono<Object> updateAvatarUrl(UUID userId, String newAvatarUrl) {
        // Tente de mettre à jour le profil du chauffeur
        Mono<DriverProfile> updatedDriver = driverProfileRepository.findByUserId(userId)
            .flatMap(driverProfile -> {
                driverProfile.setProfileImageUrl(newAvatarUrl);
                return driverProfileRepository.save(driverProfile);
            })
            .defaultIfEmpty(new DriverProfile()); // S'il n'y a pas de driverProfile, retourne un Mono.empty() via defaultIfEmpty de DriverProfile avec ID null.

        // Tente de mettre à jour le profil du client (indépendamment du chauffeur)
        Mono<ClientProfile> updatedClient = clientProfileRepository.findByUserId(userId)
            .flatMap(clientProfile -> {
                clientProfile.setProfileImageUrl(newAvatarUrl);
                return clientProfileRepository.save(clientProfile);
            })
            .defaultIfEmpty(new ClientProfile()); // S'il n'y a pas de clientProfile, retourne un Mono.empty() via defaultIfEmpty de ClientProfile avec ID null.

        // Combine les résultats et retourne un Mono<Object> (qui peut être DriverProfile ou ClientProfile)
        return Mono.zip(updatedDriver, updatedClient)
            .flatMap(tuple -> {
                if (tuple.getT1() != null && tuple.getT1().getId() != null) return Mono.just(tuple.getT1());
                if (tuple.getT2() != null && tuple.getT2().getId() != null) return Mono.just(tuple.getT2());
                return Mono.empty(); // Aucun profil mis à jour
            })
            .cast(Object.class);
    }
    
    public String extractPathFromUrl(String fullUrl) {
        try {
            URI uri = new URI(fullUrl);
            String path = uri.getPath();
            int bucketNameIndex = path.indexOf('/', 1);
            if (bucketNameIndex != -1) {
                return path.substring(bucketNameIndex);
            }
            return path;
        } catch (URISyntaxException e) {
            log.error("URL invalide, impossible d'extraire le chemin: {}", fullUrl, e);
            return null;
        }
    }
}