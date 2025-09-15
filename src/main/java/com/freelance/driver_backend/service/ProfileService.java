// PATH: /home/mbogneng-junior/freelance-driver (Copie)/backend/src/main/java/com/freelance/driver_backend/service/ProfileService.java

/*package com.freelance.driver_backend.service;

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
}*/

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
import java.util.Collections;
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
    private final OrganisationService organisationService;
    private final StorageService storageService;

    public Mono<UserSessionContextDto> getUserSessionContext(UUID userId, String userBearerToken, String publicKey) {
        log.info("ProfileService: Recherche du contexte pour l'utilisateur ID: {}", userId);

        Mono<DriverProfile> driverProfileMono = driverProfileRepository.findByUserId(userId)
                .doOnNext(
                        dp -> log.info("DEBUG_CONTEXT: findByUserId(DRIVER) a retourné un profil. ID: {}", dp.getId()))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("DEBUG_CONTEXT: findByUserId(DRIVER) a retourné vide. Retourne DriverProfile vide.");
                    return Mono.just(new DriverProfile());
                }));

        Mono<ClientProfile> clientProfileMono = clientProfileRepository.findByUserId(userId)
                .doOnNext(
                        cp -> log.info("DEBUG_CONTEXT: findByUserId(CLIENT) a retourné un profil. ID: {}", cp.getId()))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("DEBUG_CONTEXT: findByUserId(CLIENT) a retourné vide. Retourne ClientProfile vide.");
                    return Mono.just(new ClientProfile());
                }));

        return Mono.zip(driverProfileMono, clientProfileMono)
                .flatMap(tuple -> {
                    DriverProfile driverProfile = tuple.getT1();
                    ClientProfile clientProfile = tuple.getT2();

                    List<UserSessionContextDto.UserRole> roles = new ArrayList<>();
                    OrganisationDto primaryOrganisation = null;

                    log.info("DEBUG_CONTEXT: driverProfile.getId() obtenu dans tuple: {}", driverProfile.getId());
                    log.info("DEBUG_CONTEXT: clientProfile.getId() obtenu dans tuple: {}", clientProfile.getId());

                    if (driverProfile != null && driverProfile.getId() != null) {
                        roles.add(UserSessionContextDto.UserRole.DRIVER);
                        if (driverProfile.getOrganisationId() != null) {
                            primaryOrganisation = createMockOrganisationDto(driverProfile.getOrganisationId(),
                                    driverProfile.getFirstName() + "'s Driving Business");
                        }
                    }
                    if (clientProfile != null && clientProfile.getId() != null) {
                        roles.add(UserSessionContextDto.UserRole.CLIENT);
                        if (primaryOrganisation == null && clientProfile.getOrganisationId() != null) {
                            primaryOrganisation = createMockOrganisationDto(clientProfile.getOrganisationId(),
                                    clientProfile.getCompanyName());
                        }
                    }

                    if (roles.isEmpty()) {
                        roles.add(UserSessionContextDto.UserRole.NO_PROFILE);
                        log.warn(
                                "ProfileService: Aucun profil (driver ou client) trouvé pour l'utilisateur {}. Renvoi du statut NO_PROFILE.",
                                userId);
                    } else {
                        log.info("ProfileService: Profil(s) trouvé(s) pour l'utilisateur {}. Rôles: {}", userId, roles);
                    }

                    return Mono.just(UserSessionContextDto.builder()
                            .userId(userId)
                            .roles(roles)
                            .driverProfile(
                                    driverProfile != null && driverProfile.getId() != null ? driverProfile : null)
                            .clientProfile(
                                    clientProfile != null && clientProfile.getId() != null ? clientProfile : null)
                            .organisation(primaryOrganisation)
                            .build());
                });
    }

    private OrganisationDto createMockOrganisationDto(UUID orgId, String name) {
        OrganisationDto orgDto = new OrganisationDto();
        orgDto.setOrganizationId(orgId);
        orgDto.setLongName(name);
        orgDto.setStatus("ACTIVE");
        return orgDto;
    }

    public Mono<UUID> findOrganisationIdByUserId(UUID userId) {
        log.info("Recherche de l'ID d'organisation pour l'utilisateur ID: {}", userId);
        return driverProfileRepository.findByUserId(userId)
                .map(DriverProfile::getOrganisationId)
                .switchIfEmpty(Mono.defer(() -> clientProfileRepository.findByUserId(userId)
                        .map(ClientProfile::getOrganisationId)))
                .switchIfEmpty(Mono.error(new RuntimeException(
                        "Aucun profil (conducteur ou client) trouvé pour l'utilisateur " + userId)));
    }

    public Mono<DriverProfile> findDriverById(UUID driverId) {
        log.info("Recherche du profil pour le chauffeur ID: {}", driverId);
        return driverProfileRepository.findByUserId(driverId)
                .doOnNext(dp -> log.info("✅ Profil DRIVER trouvé par findDriverById pour userId: {}. Détails: {}",
                        driverId, dp))
                .doOnError(e -> log.error("❌ Erreur lors de la recherche du profil DRIVER pour userId {}: {}", driverId,
                        e.getMessage()))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("⚠️ Aucun profil DRIVER trouvé par findDriverById pour userId: {}. (Mono.empty)",
                            driverId);
                    return Mono.empty();
                }));
    }

    public Mono<ClientProfile> findClientById(UUID clientId) {
        log.info("Recherche du profil pour le client ID: {}", clientId);
        return clientProfileRepository.findByUserId(clientId)
                .doOnNext(cp -> log.info("✅ Profil CLIENT trouvé par findClientById pour userId: {}. Détails: {}",
                        clientId, cp))
                .doOnError(e -> log.error("❌ Erreur lors de la recherche du profil CLIENT pour userId {}: {}", clientId,
                        e.getMessage()))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("⚠️ Aucun profil CLIENT trouvé par findClientById pour userId: {}. (Mono.empty)",
                            clientId);
                    return Mono.empty();
                }));
    }

    // --- LOGIQUE DE MISE À JOUR POUR LES CHAMPS COMMUNS ---
    private <T extends DriverProfile> Mono<T> updateCommonDriverFields(T existingProfile, DriverProfile updatedData) {
        if (updatedData.getFirstName() != null)
            existingProfile.setFirstName(updatedData.getFirstName());
        if (updatedData.getLastName() != null)
            existingProfile.setLastName(updatedData.getLastName());
        if (updatedData.getPhoneNumber() != null)
            existingProfile.setPhoneNumber(updatedData.getPhoneNumber());
        if (updatedData.getNickname() != null)
            existingProfile.setNickname(updatedData.getNickname());
        if (updatedData.getBirthDate() != null)
            existingProfile.setBirthDate(updatedData.getBirthDate());
        if (updatedData.getNationality() != null)
            existingProfile.setNationality(updatedData.getNationality());
        if (updatedData.getGender() != null)
            existingProfile.setGender(updatedData.getGender());
        if (updatedData.getLanguage() != null)
            existingProfile.setLanguage(updatedData.getLanguage());
        // Note: profileImageUrl est géré par updateAvatarUrl, pas ici directement.
        return Mono.just(existingProfile);
    }

    private <T extends ClientProfile> Mono<T> updateCommonClientFields(T existingProfile, ClientProfile updatedData) {
        if (updatedData.getFirstName() != null)
            existingProfile.setFirstName(updatedData.getFirstName());
        if (updatedData.getLastName() != null)
            existingProfile.setLastName(updatedData.getLastName());
        if (updatedData.getPhoneNumber() != null)
            existingProfile.setPhoneNumber(updatedData.getPhoneNumber());
        if (updatedData.getNickname() != null)
            existingProfile.setNickname(updatedData.getNickname());
        if (updatedData.getBirthDate() != null)
            existingProfile.setBirthDate(updatedData.getBirthDate());
        if (updatedData.getNationality() != null)
            existingProfile.setNationality(updatedData.getNationality());
        if (updatedData.getGender() != null)
            existingProfile.setGender(updatedData.getGender());
        if (updatedData.getLanguage() != null)
            existingProfile.setLanguage(updatedData.getLanguage());
        // Note: profileImageUrl est géré par updateAvatarUrl, pas ici directement.
        // contactEmail, companyName sont spécifiques au Client, et déjà gérés dans
        // updateClientProfile
        return Mono.just(existingProfile);
    }

    public Mono<UserSessionContextDto> updateDriverProfile(UUID userId, DriverProfile updatedData) {
        return driverProfileRepository.findByUserId(userId)
                .flatMap(existingDriverProfile -> {
                    log.info("Mise à jour du profil DRIVER pour l'utilisateur ID: {}", userId);

                    // 1. Mettre à jour les champs communs dans le DriverProfile existant
                    // Puis mettre à jour les champs spécifiques au Driver
                    return updateCommonDriverFields(existingDriverProfile, updatedData)
                            .flatMap(profile -> {
                                if (updatedData.getBiography() != null)
                                    profile.setBiography(updatedData.getBiography());
                                if (updatedData.getVehicleDetails() != null)
                                    profile.setVehicleDetails(updatedData.getVehicleDetails());
                                if (updatedData.getLicenseNumber() != null)
                                    profile.setLicenseNumber(updatedData.getLicenseNumber());
                                return driverProfileRepository.save(profile)
                                        .doOnSuccess(
                                                dp -> log.info("✅ DriverProfile mis à jour pour userId: {}", userId))
                                        .doOnError(e -> log.error(
                                                "❌ Échec de la mise à jour du DriverProfile pour userId {}: {}", userId,
                                                e.getMessage()));
                            })
                            // 2. Synchroniser les champs communs vers le ClientProfile (s'il existe)
                            .then(clientProfileRepository.findByUserId(userId)
                                    .flatMap(existingClientProfile -> updateCommonClientFields(existingClientProfile,
                                            new ClientProfile() {
                                                { // Crée un ClientProfile "factice" avec les données de updatedData
                                                  // pour la synchronisation
                                                    setFirstName(updatedData.getFirstName());
                                                    setLastName(updatedData.getLastName());
                                                    setPhoneNumber(updatedData.getPhoneNumber());
                                                    setNickname(updatedData.getNickname());
                                                    setBirthDate(updatedData.getBirthDate());
                                                    setNationality(updatedData.getNationality());
                                                    setGender(updatedData.getGender());
                                                    setLanguage(updatedData.getLanguage());
                                                }
                                            })
                                            .flatMap(profile -> clientProfileRepository.save(profile))
                                            .doOnSuccess(cp -> log.info(
                                                    "✅ ClientProfile synchronisé depuis DriverProfile pour userId: {}",
                                                    userId))
                                            .doOnError(e -> log.error(
                                                    "❌ Échec de la synchronisation du ClientProfile depuis DriverProfile pour userId {}: {}",
                                                    userId, e.getMessage())))
                                    .then() // Transformer en Mono<Void>
                                    .switchIfEmpty(Mono.defer(() -> {
                                        log.info("Pas de ClientProfile à synchroniser pour l'utilisateur {}.", userId);
                                        return Mono.empty();
                                    })))
                            // 3. Après toutes les mises à jour, récupérer le contexte complet
                            .then(Mono.defer(() -> getUserSessionContext(userId, null, null)));
                })
                .switchIfEmpty(Mono.defer(() -> { // Gérer le cas où aucun DriverProfile n'est trouvé
                    log.warn("Tentative de mise à jour DriverProfile pour userId {} mais aucun DriverProfile trouvé.",
                            userId);
                    return Mono
                            .error(new IllegalStateException("DriverProfile non trouvé pour l'utilisateur " + userId));
                }));
    }

    public Mono<UserSessionContextDto> updateClientProfile(UUID userId, ClientProfile updatedData) { // Changement du
                                                                                                     // type de retour
        return clientProfileRepository.findByUserId(userId)
                .flatMap(existingClientProfile -> {
                    log.info("Mise à jour du profil CLIENT pour l'utilisateur ID: {}", userId);

                    // 1. Mettre à jour les champs communs dans le ClientProfile existant
                    // Puis mettre à jour les champs spécifiques au Client
                    return updateCommonClientFields(existingClientProfile, updatedData)
                            .flatMap(profile -> {
                                if (updatedData.getCompanyName() != null)
                                    profile.setCompanyName(updatedData.getCompanyName());
                                if (updatedData.getContactEmail() != null)
                                    profile.setContactEmail(updatedData.getContactEmail());
                                return clientProfileRepository.save(profile)
                                        .doOnSuccess(
                                                cp -> log.info("✅ ClientProfile mis à jour pour userId: {}", userId))
                                        .doOnError(e -> log.error(
                                                "❌ Échec de la mise à jour du ClientProfile pour userId {}: {}", userId,
                                                e.getMessage()));
                            })
                            // 2. Synchroniser les champs communs vers le DriverProfile (s'il existe)
                            .then(driverProfileRepository.findByUserId(userId)
                                    .flatMap(existingDriverProfile -> updateCommonDriverFields(existingDriverProfile,
                                            new DriverProfile() {
                                                { // Crée un DriverProfile "factice" avec les données de updatedData
                                                  // pour la synchronisation
                                                    setFirstName(updatedData.getFirstName());
                                                    setLastName(updatedData.getLastName());
                                                    setPhoneNumber(updatedData.getPhoneNumber());
                                                    setNickname(updatedData.getNickname());
                                                    setBirthDate(updatedData.getBirthDate());
                                                    setNationality(updatedData.getNationality());
                                                    setGender(updatedData.getGender());
                                                    setLanguage(updatedData.getLanguage());
                                                }
                                            })
                                            .flatMap(profile -> driverProfileRepository.save(profile))
                                            .doOnSuccess(dp -> log.info(
                                                    "✅ DriverProfile synchronisé depuis ClientProfile pour userId: {}",
                                                    userId))
                                            .doOnError(e -> log.error(
                                                    "❌ Échec de la synchronisation du DriverProfile depuis ClientProfile pour userId {}: {}",
                                                    userId, e.getMessage())))
                                    .then() // Transformer en Mono<Void>
                                    .switchIfEmpty(Mono.defer(() -> {
                                        log.info("Pas de DriverProfile à synchroniser pour l'utilisateur {}.", userId);
                                        return Mono.empty();
                                    })))
                            // 3. Après toutes les mises à jour, récupérer le contexte complet
                            .then(Mono.defer(() -> getUserSessionContext(userId, null, null)));
                })
                .switchIfEmpty(Mono.defer(() -> { // Gérer le cas où aucun ClientProfile n'est trouvé
                    log.warn("Tentative de mise à jour ClientProfile pour userId {} mais aucun ClientProfile trouvé.",
                            userId);
                    return Mono
                            .error(new IllegalStateException("ClientProfile non trouvé pour l'utilisateur " + userId));
                }));
    }

    /**
     * Met à jour l'URL de l'avatar pour TOUS les profils (Driver et Client) de
     * l'utilisateur spécifié,
     * S'ILS EXISTENT.
     * Si un ancien avatar existe (sur n'importe quel profil), il est supprimé du
     * stockage externe.
     *
     * @param userId       L'ID de l'utilisateur.
     * @param newAvatarUrl L'URL publique du nouvel avatar.
     * @return Un Mono<UserSessionContextDto> contenant le contexte mis à jour de
     *         l'utilisateur.
     */
    public Mono<UserSessionContextDto> updateAvatarUrl(UUID userId, String newAvatarUrl) {
        // Étape 1: Déterminer l'ancienne URI de l'avatar pour la suppression (peut
        // provenir de n'importe quel profil)
        Mono<Void> deleteOldAvatarAction = findProfileByUserId(userId) // Obtient le premier profil trouvé
                .flatMap(profile -> {
                    String oldUrl = getAvatarUrlFromProfile(profile);
                    if (oldUrl != null && !oldUrl.isEmpty() && !oldUrl.equals(newAvatarUrl)) {
                        log.info("Ancien avatar trouvé pour suppression : {}", oldUrl);
                        String oldUri = extractUriFromUrl(oldUrl); // Assurez-vous que oldUri est le bon chemin pour la
                                                                   // suppression
                        if (oldUri != null) {
                            return storageService.deleteFile(oldUri)
                                    .onErrorResume(e -> {
                                        log.error("Échec de la suppression de l'ancien avatar {}: {}", oldUri,
                                                e.getMessage());
                                        return Mono.empty(); // Ne pas bloquer la mise à jour si la suppression échoue
                                    });
                        }
                    }
                    return Mono.empty(); // Pas d'ancien avatar ou le même avatar, pas de suppression nécessaire
                })
                .then(); // Attendre la fin de l'action de suppression (peut être vide)

        // Étape 2: Mettre à jour le DriverProfile (s'il existe)
        Mono<Void> updateDriverAvatarMono = driverProfileRepository.findByUserId(userId)
                .flatMap(driverProfile -> {
                    driverProfile.setProfileImageUrl(newAvatarUrl);
                    return driverProfileRepository.save(driverProfile)
                            .doOnSuccess(dp -> log.info("✅ DriverProfile.profileImageUrl mis à jour pour userId: {}",
                                    userId))
                            .doOnError(e -> log.error(
                                    "❌ Échec de la mise à jour DriverProfile.profileImageUrl pour userId {}: {}",
                                    userId, e.getMessage()))
                            .then(); // Transformer en Mono<Void>
                })
                .switchIfEmpty(Mono.defer(() -> { // S'il n'y a pas de DriverProfile, renvoyer un Mono<Void> vide
                    log.info(
                            "Pas de DriverProfile trouvé pour l'utilisateur {}. Pas de mise à jour de l'avatar Driver.",
                            userId);
                    return Mono.empty();
                }));

        // Étape 3: Mettre à jour le ClientProfile (s'il existe)
        Mono<Void> updateClientAvatarMono = clientProfileRepository.findByUserId(userId)
                .flatMap(clientProfile -> {
                    clientProfile.setProfileImageUrl(newAvatarUrl);
                    return clientProfileRepository.save(clientProfile)
                            .doOnSuccess(cp -> log.info("✅ ClientProfile.profileImageUrl mis à jour pour userId: {}",
                                    userId))
                            .doOnError(e -> log.error(
                                    "❌ Échec de la mise à jour ClientProfile.profileImageUrl pour userId {}: {}",
                                    userId, e.getMessage()))
                            .then(); // Transformer en Mono<Void>
                })
                .switchIfEmpty(Mono.defer(() -> { // S'il n'y a pas de ClientProfile, renvoyer un Mono<Void> vide
                    log.info(
                            "Pas de ClientProfile trouvé pour l'utilisateur {}. Pas de mise à jour de l'avatar Client.",
                            userId);
                    return Mono.empty();
                }));

        // Étape 4: Exécuter la suppression et les mises à jour de profils en parallèle
        // Puis, récupérer le contexte complet
        return deleteOldAvatarAction
                .then(Mono.when(updateDriverAvatarMono, updateClientAvatarMono)) // Exécute les deux mises à jour en
                                                                                 // parallèle
                .then(Mono.defer(() -> { // Utilise Mono.defer pour s'assurer que getUserSessionContext est appelé après
                                         // les mises à jour
                    log.info(
                            "✅ Avatar mis à jour pour l'utilisateur ID: {}. Récupération du contexte de session mis à jour.",
                            userId);
                    return getUserSessionContext(userId, null, null); // userBearerToken et publicKey ne sont pas
                                                                      // nécessaires pour getUserSessionContext si les
                                                                      // appels sous-jacents gèrent leur propre auth (ou
                                                                      // mock)
                }))
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Aucun profil (Driver ou Client) trouvé ou mis à jour pour l'utilisateur ID: " + userId)))
                .doOnError(e -> log.error("❌ Erreur finale dans updateAvatarUrl pour userId {}: {}", userId,
                        e.getMessage()));
    }

    public String extractUriFromUrl(String fullUrl) {
        try {
            URI uri = new URI(fullUrl);
            String path = uri.getPath();

            String mediaServiceSegment = "/media-service";
            int servicePathIndex = path.indexOf(mediaServiceSegment);
            if (servicePathIndex != -1) {
                return path.substring(servicePathIndex + mediaServiceSegment.length());
            } else {
                log.warn(
                        "L'URL '{}' ne semble pas provenir du service de médias externe. La suppression ne sera pas tentée via l'API externe.",
                        fullUrl);
                return null;
            }
        } catch (URISyntaxException e) {
            log.error("URL invalide, impossible d'extraire l'URI: {}", fullUrl, e);
            return null;
        }
    }

    public Mono<Object> findProfileByUserId(UUID userId) {
        return driverProfileRepository.findByUserId(userId)
                .cast(Object.class)
                .switchIfEmpty(Mono.defer(() -> clientProfileRepository.findByUserId(userId).cast(Object.class)));
    }

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

    /**
     * Met à jour l'URL de l'avatar pour TOUS les profils (Driver et Client) de
     * l'utilisateur spécifié,
     * S'ILS EXISTENT.
     * Si un ancien avatar existe (sur n'importe quel profil), il est supprimé du
     * stockage externe.
     *
     * @param userId       L'ID de l'utilisateur.
     * @param newAvatarUrl L'URL publique du nouvel avatar.
     * @return Un Mono<UserSessionContextDto> contenant le contexte mis à jour de
     *         l'utilisateur.
     */

}