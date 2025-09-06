package com.freelance.driver_backend.service;

import com.freelance.driver_backend.dto.UserSessionContextDto;
import com.freelance.driver_backend.dto.external.OrganisationDto;
import com.freelance.driver_backend.model.ClientProfile;
import com.freelance.driver_backend.model.DriverProfile;
import com.freelance.driver_backend.repository.ClientProfileRepository;
import com.freelance.driver_backend.repository.DriverProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.UUID;
import java.net.URI;
import java.net.URISyntaxException;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProfileService {

    private final DriverProfileRepository driverProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    // On n'a plus besoin de OrganisationService ici, car on ne l'appelle plus.
    // private final OrganisationService organisationService;

   public Mono<UserSessionContextDto> getUserSessionContext(UUID userId, String userBearerToken, String publicKey) {
        log.info("ProfileService: Recherche du contexte pour l'utilisateur ID: {}", userId);
        return driverProfileRepository.findByUserId(userId)
                .flatMap(this::buildContextForDriver)
                .switchIfEmpty(Mono.defer(() ->
                    clientProfileRepository.findByUserId(userId)
                            .flatMap(this::buildContextForClient)
                ))
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    log.warn("ProfileService: Aucun profil trouvé pour l'utilisateur {}. Renvoi du statut NO_PROFILE.", userId);
                    return UserSessionContextDto.builder()
                            .userId(userId)
                            .role(UserSessionContextDto.UserRole.NO_PROFILE)
                            .build();
                }));
    }

    private Mono<UserSessionContextDto> buildContextForDriver(DriverProfile profile) {
        log.info("ProfileService: Profil DRIVER trouvé. Construction du contexte avec les données locales.");
        OrganisationDto orgDto = new OrganisationDto();
        orgDto.setOrganizationId(profile.getOrganisationId());
        orgDto.setLongName(profile.getFirstName() + "'s Business (Local)"); // On peut reconstruire un nom
        orgDto.setStatus("ACTIVE");

        return Mono.just(UserSessionContextDto.builder()
                .userId(profile.getUserId())
                .role(UserSessionContextDto.UserRole.DRIVER)
                .profile(profile)
                .organisation(orgDto)
                .build());
    }

    private Mono<UserSessionContextDto> buildContextForClient(ClientProfile profile) {
        log.info("ProfileService: Profil CLIENT trouvé. Construction du contexte avec les données locales.");
        OrganisationDto orgDto = new OrganisationDto();
        orgDto.setOrganizationId(profile.getOrganisationId());
        orgDto.setLongName(profile.getCompanyName() + " (Local)");
        orgDto.setStatus("ACTIVE");

        return Mono.just(UserSessionContextDto.builder()
                .userId(profile.getUserId())
                .role(UserSessionContextDto.UserRole.CLIENT)
                .profile(profile)
                .organisation(orgDto)
                .build());
    }

    public Mono<DriverProfile> findDriverById(UUID driverId) {
        log.info("Recherche du profil pour le chauffeur ID: {}", driverId);
        return driverProfileRepository.findByUserId(driverId);
    }

    public Mono<ClientProfile> findClientById(UUID clientId) {
        log.info("Recherche du profil pour le client ID: {}", clientId);
        return clientProfileRepository.findByUserId(clientId);
    }

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

    public String getAuthorNameFromProfile(Object profile) {
        if (profile instanceof DriverProfile) {
            return ((DriverProfile) profile).getFirstName() + " " + ((DriverProfile) profile).getLastName();
        } else if (profile instanceof ClientProfile) {
            return ((ClientProfile) profile).getFirstName() + " " + ((ClientProfile) profile).getLastName();
        }
        return "Anonyme";
    }

    public String getAvatarUrlFromProfile(Object profile) {
        if (profile instanceof DriverProfile) {
            // CORRECTION : Le nom du getter est getProfileImageUrl
            return ((DriverProfile) profile).getProfileImageUrl();
        } else if (profile instanceof ClientProfile) {
            // CORRECTION : Le nom du getter est getProfileImageUrl
            return ((ClientProfile) profile).getProfileImageUrl();
        }
        return null;
    }

    public Mono<Object> updateAvatarUrl(UUID userId, String newAvatarUrl) {
        return driverProfileRepository.findByUserId(userId)
            .flatMap(driverProfile -> {
                // CORRECTION : Le nom du setter est setProfileImageUrl
                driverProfile.setProfileImageUrl(newAvatarUrl);
                return driverProfileRepository.save(driverProfile);
            })
            .cast(Object.class)
            .switchIfEmpty(Mono.defer(() -> 
                clientProfileRepository.findByUserId(userId)
                    .flatMap(clientProfile -> {
                        // CORRECTION : Le nom du setter est setProfileImageUrl
                        clientProfile.setProfileImageUrl(newAvatarUrl);
                        return clientProfileRepository.save(clientProfile);
                    })
            ));
    }
    
    public String extractPathFromUrl(String fullUrl) {
        try {
            URI uri = new URI(fullUrl);
            // On retire le nom du bucket du chemin
            String path = uri.getPath();
            // Le chemin de MinIO inclut le bucket, ex: /freelance-driver/avatars/...
            // L'API externe attend probablement le chemin sans le bucket, ex: /avatars/...
            // Cette logique est une supposition et pourrait nécessiter un ajustement.
            int bucketNameIndex = path.indexOf('/', 1); // Trouve le 2ème '/'
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