package com.freelance.driver_backend.service;

import com.freelance.driver_backend.dto.external.*;
import com.freelance.driver_backend.dto.onboarding.ClientOnboardingRequest;
import com.freelance.driver_backend.dto.onboarding.DriverOnboardingRequest;
import com.freelance.driver_backend.dto.onboarding.OnboardingResponse;
import com.freelance.driver_backend.model.ClientProfile;
import com.freelance.driver_backend.model.DriverProfile;
import com.freelance.driver_backend.repository.ClientProfileRepository;
import com.freelance.driver_backend.repository.DriverProfileRepository;
import com.freelance.driver_backend.service.external.AuthService;
import com.freelance.driver_backend.service.external.ChatService;
import com.freelance.driver_backend.service.external.OrganisationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;

@Service
@Slf4j
public class OnboardingService {

    // --- Structures de données internes pour le flux réactif ---
    /*private record UserAndLoginInfo(UserDto user, LoginResponse loginResponse) {}
    private record AllInfo(UserDto user, OrganisationDto organisation, LoginResponse loginResponse) {}

    // --- Dépendances injectées ---
    private final AuthService authService;
    private final OrganisationService organisationService;
    private final ChatService chatService;
    private final DriverProfileRepository driverProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final String publicKey;
    private final String oauthClientId;
    private final String oauthClientSecret;*/


    private record UserAndLoginInfo(UserDto user, LoginResponse loginResponse) {}
    private record AllInfo(UserDto user, OrganisationDto organisation, LoginResponse loginResponse) {}

    private final AuthService authService;
    private final OrganisationService organisationService;
    private final DriverProfileRepository driverProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final ProfileService profileService;
    private final OtpVerificationRepository otpVerificationRepository;
    private final String publicKey;
    private final String oauthClientId;
    private final String oauthClientSecret;




    public OnboardingService(
            AuthService authService,
            OrganisationService organisationService,
            DriverProfileRepository driverProfileRepository,
            ClientProfileRepository clientProfileRepository,
            ProfileService profileService,
            OtpVerificationRepository otpVerificationRepository,
            @Value("${freelancedriver.api.public-key}") String publicKey,
            @Value("${freelancedriver.oauth2.client-id}") String oauthClientId,
            @Value("${freelancedriver.oauth2.client-secret}") String oauthClientSecret
    ) {
        this.authService = authService;
        this.organisationService = organisationService;
        this.driverProfileRepository = driverProfileRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.profileService = profileService;
        this.otpVerificationRepository = otpVerificationRepository;
        this.publicKey = publicKey;
        this.oauthClientId = oauthClientId;
        this.oauthClientSecret = oauthClientSecret;
    }





    public Mono<OnboardingResponse> createDriverAccount(DriverOnboardingRequest dto) {
        return verifyOtp(dto.getEmail(), dto.getOtp())
            .then(createGenericAccount(dto.getEmail(), dto.getPassword(), dto.getFirstName(), dto.getLastName(), dto.getPhoneNumber(), dto.getCompanyName(), dto.getCompanyDescription()))
            .flatMap(allInfo -> {
                log.info("Étape 5/5 (Driver): Sauvegarde du profil Driver.");
                DriverProfile profile = new DriverProfile();
                profile.setId(UUID.randomUUID());
                profile.setUserId(allInfo.user().getId());
                profile.setOrganisationId(allInfo.organisation().getOrganizationId());
                profile.setFirstName(allInfo.user().getFirstName());
                profile.setLastName(allInfo.user().getLastName());
                profile.setPhoneNumber(allInfo.user().getPhoneNumber());
                profile.setLicenseNumber(dto.getLicenseNumber());
                profile.setVehicleDetails(dto.getVehicleDetails());
                return driverProfileRepository.save(profile)
                        .then(buildFinalResponse(allInfo.loginResponse()));
            });
    }

    public Mono<OnboardingResponse> createClientAccount(ClientOnboardingRequest dto) {
        return verifyOtp(dto.getEmail(), dto.getOtp())
            .then(createGenericAccount(dto.getEmail(), dto.getPassword(), dto.getFirstName(), dto.getLastName(), dto.getPhoneNumber(), dto.getCompanyName(), dto.getCompanyDescription()))
            .flatMap(allInfo -> {
                log.info("Étape 5/5 (Client): Sauvegarde du profil Client.");
                ClientProfile profile = new ClientProfile();
                profile.setId(UUID.randomUUID());
                profile.setUserId(allInfo.user().getId());
                profile.setOrganisationId(allInfo.organisation().getOrganizationId());
                profile.setFirstName(dto.getFirstName());
                profile.setLastName(dto.getLastName());
                profile.setCompanyName(dto.getCompanyName());
                profile.setContactEmail(dto.getEmail());
                profile.setPhoneNumber(allInfo.user().getPhoneNumber());
                return clientProfileRepository.save(profile)
                        .then(buildFinalResponse(allInfo.loginResponse()));
            });
    }

    private Mono<Void> verifyOtp(String email, String otp) {
        log.info("▶️ Vérification de l'OTP {} pour l'email {}", otp, email);
        return otpVerificationRepository.findById(email)
            .switchIfEmpty(Mono.defer(() -> {
                log.error("❌ Aucune demande de vérification trouvée pour l'email : {}", email);
                return Mono.error(new IllegalStateException("Aucune demande de vérification trouvée pour cet email."));
            }))
            .flatMap(verification -> {
                if (verification.getExpiresAt().isBefore(Instant.now())) {
                    log.warn("❌ Tentative avec un OTP expiré pour {}", email);
                    return otpVerificationRepository.delete(verification)
                        .then(Mono.error(new IllegalStateException("Le code de vérification a expiré.")));
                }
                if (!verification.getOtpCode().equals(otp)) {
                    log.warn("❌ Tentative avec un OTP invalide pour {}", email);
                    return Mono.error(new IllegalStateException("Le code de vérification est invalide."));
                }
                log.info("✅ OTP validé pour {}. Suppression de l'entrée.", email);
                return otpVerificationRepository.delete(verification);
            });
    }




   


    /*public Mono<OnboardingResponse> createDriverAccount(DriverOnboardingRequest dto) {
        return createGenericAccount(dto.getEmail(), dto.getPassword(), dto.getFirstName(), dto.getLastName(), dto.getPhoneNumber(), dto.getCompanyName(), dto.getCompanyDescription())
                .flatMap(allInfo -> {
                    log.info("Étape 5/5 (Driver): Sauvegarde du profil Driver spécifique.");
                    DriverProfile profile = new DriverProfile();
                    profile.setId(UUID.randomUUID());
                    profile.setUserId(allInfo.user().getId());
                    profile.setOrganisationId(allInfo.organisation().getOrganizationId());
                    profile.setFirstName(allInfo.user().getFirstName());
                    profile.setLastName(allInfo.user().getLastName());
                    profile.setPhoneNumber(allInfo.user().getPhoneNumber());
                    profile.setLicenseNumber(dto.getLicenseNumber());
                    profile.setVehicleDetails(dto.getVehicleDetails());
                    
                    return driverProfileRepository.save(profile)
                            .map(savedProfile -> {
                                log.info("Onboarding Driver terminé pour le profil ID: {}.", savedProfile.getId());
                                return OnboardingResponse.builder()
                                    .token(allInfo.loginResponse().getAccessToken().getToken())
                                    .profile(savedProfile)
                                    .build();
                            });
                });
    }

    public Mono<OnboardingResponse> createClientAccount(ClientOnboardingRequest dto) {
        return createGenericAccount(dto.getEmail(), dto.getPassword(), dto.getFirstName(), dto.getLastName(), dto.getPhoneNumber(), dto.getCompanyName(), dto.getCompanyDescription())
                .flatMap(allInfo -> {
                    log.info("Étape 5/5 (Client): Sauvegarde du profil Client spécifique.");
                    ClientProfile profile = new ClientProfile();
                    profile.setId(UUID.randomUUID());
                    profile.setUserId(allInfo.user().getId());
                    profile.setOrganisationId(allInfo.organisation().getOrganizationId());
                    profile.setCompanyName(dto.getCompanyName());
                    profile.setContactEmail(dto.getEmail());
                    
                    String phoneNumberFromAuth = allInfo.user().getPhoneNumber();
                    log.info(">>>> [ONBOARDING] Numéro de téléphone à sauvegarder pour le client : '{}'", phoneNumberFromAuth);
                    profile.setPhoneNumber(phoneNumberFromAuth);

                    return clientProfileRepository.save(profile)
                            .map(savedProfile -> {
                                log.info("Onboarding Client terminé pour le profil ID: {}.", savedProfile.getId());
                                return OnboardingResponse.builder()
                                    .token(allInfo.loginResponse().getAccessToken().getToken())
                                    .profile(savedProfile)
                                    .build();
                            });
                });
    }*/


    private Mono<AllInfo> createGenericAccount(String email, String password, String firstName, String lastName, String phoneNumber, String companyName, String companyDescription) {
    
        RegistrationRequest registrationRequest = RegistrationRequest.builder()
                .username(email).email(email).password(password)
                .firstName(firstName).lastName(lastName).phoneNumber(phoneNumber)
                .build();
    
        return authService.getClientCredentialsToken(oauthClientId, oauthClientSecret)
            .doOnSuccess(tokenResponse -> log.info("Étape 0/5: Token de service M2M obtenu."))
            .flatMap(m2mTokenResponse -> {
                String m2mBearerToken = "Bearer " + m2mTokenResponse.getAccessToken();
    
                return authService.registerUser(registrationRequest, m2mBearerToken)

                
                    
                    // ==============================================================================
                    //          SECTION DE CRÉATION DU COMPTE CHAT (MISE EN COMMENTAIRE)
                    // ==============================================================================
                    /* .doOnSuccess(createdUser -> {
                        log.info("Étape 1.5/5: Déclenchement de la création du compte chat (actuellement désactivé).");
                         ChatUserCreationPayload chatPayload = ChatUserCreationPayload.builder()
                             .externalId(createdUser.getPhoneNumber())
                             .displayName(firstName + " " + lastName)
                            .email(email).phoneNumber(phoneNumber).login(email).secret(password)
                             .build();
                        
                         chatService.registerAndLoginChatUser(chatPayload)
                             .doOnSuccess(chatLogin -> log.info("Compte chat pour {} créé et connecté avec succès.", email))
                             .doOnError(e -> log.error("Échec de la création du compte chat pour {}.", email, e))
                             .subscribe();
                    })*/



                    
                    .flatMap(createdUser -> {
                        log.info("Étape 1/5: Utilisateur {} créé avec succès (ID: {}).", createdUser.getEmail(), createdUser.getId());
                        LoginRequest loginRequest = new LoginRequest(email, password);
                        return authService.loginUser(loginRequest, m2mBearerToken)
                            .map(loginResponse -> new UserAndLoginInfo(createdUser, loginResponse));
                    });
            })
            .doOnSuccess(data -> log.info("Étape 2/5: Connexion de l'utilisateur {} réussie.", data.user().getEmail()))
            .flatMap(userAndLoginInfo -> {
                String userBearerToken = "Bearer " + userAndLoginInfo.loginResponse().getAccessToken().getToken();
    
                log.info("Étape 3/5: Création de l'organisation pour l'utilisateur {}", email);
                OrganisationCreationRequest orgRequest = OrganisationCreationRequest.builder()
                        .longName(companyName)
                        .shortName(generateShortName(companyName))
                        .legalForm("11")
                        .businessDomains(Collections.singletonList("3fa85f64-5717-4562-b3fc-2c963f66afa6"))
                        .email(email).description(companyDescription).ceoName(firstName + " " + lastName)
                        .registrationDate(OffsetDateTime.now()).yearFounded(OffsetDateTime.now())
                        .build();
    
                // ==============================================================================
                //      NOUVELLE LOGIQUE SIMPLIFIÉE : APPEL DIRECT SANS RETRY
                // ==============================================================================
                // Cet appel va maintenant utiliser l'interface OrganisationService.
                // En profil "dev-resource-mock", c'est MockOrganisationServiceImpl qui répondra.
                // En profil "production", ce sera OrganisationServiceWebClientImpl.
                return organisationService.createOrganisation(orgRequest, userBearerToken, publicKey)
                    .map(createdOrg -> {
                        log.info("Étape 4/5: Organisation '{}' (provenance: {}) créée. Données: {}", 
                            createdOrg.getLongName(), 
                            organisationService.getClass().getSimpleName(), 
                            createdOrg);
                        
                        return new AllInfo(userAndLoginInfo.user(), createdOrg, userAndLoginInfo.loginResponse());
                    });
            });
    }

    private String generateShortName(String longName) {
        if (longName == null || longName.trim().isEmpty()) return "NA";
        String cleanedName = longName.trim();
        StringBuilder initials = new StringBuilder();
        for (String s : cleanedName.split("\\s+")) {
            if (!s.isEmpty()) initials.append(s.charAt(0));
        }
        String shortName = initials.toString().toUpperCase();
        if (shortName.length() < 2 && cleanedName.length() >= 2) {
            return cleanedName.substring(0, Math.min(cleanedName.length(), 3)).toUpperCase();
        }
        return shortName;
    }
}