package com.freelance.driver_backend.service;

import com.freelance.driver_backend.dto.external.*;
import com.freelance.driver_backend.dto.onboarding.ClientOnboardingRequest;
import com.freelance.driver_backend.dto.onboarding.DriverOnboardingRequest;
import com.freelance.driver_backend.dto.onboarding.OnboardingResponse;
import com.freelance.driver_backend.dto.UserSessionContextDto; // Importez la nouvelle structure
import com.freelance.driver_backend.model.ClientProfile;
import com.freelance.driver_backend.model.DriverProfile;
import com.freelance.driver_backend.repository.ClientProfileRepository;
import com.freelance.driver_backend.repository.DriverProfileRepository;
import com.freelance.driver_backend.repository.OtpVerificationRepository;
import com.freelance.driver_backend.service.external.AuthService;
import com.freelance.driver_backend.service.external.OrganisationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

@Service
@Slf4j
public class OnboardingService {

    // --- Structures internes pour la clarté ---
    private record AllInfo(LoginResponse.UserInfo user, OrganisationDto organisation, LoginResponse loginResponse) {}

    // --- Dépendances ---
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

    /**
     * Orchestre la création complète d'un compte Chauffeur (Auth + Organisation + DriverProfile).
     * Utilisé pour la première inscription.
     */
    public Mono<OnboardingResponse> createDriverAccount(DriverOnboardingRequest dto) {
        return verifyOtp(dto.getEmail(), dto.getOtp())
            .then(createAuthUserAndOrganization(dto.getEmail(), dto.getPassword(), dto.getFirstName(), dto.getLastName(), dto.getPhoneNumber(), dto.getCompanyName(), dto.getCompanyDescription()))
            .flatMap(allInfo -> {
                log.info("Étape 5/6 (Driver): Sauvegarde du profil Driver pour l'utilisateur ID: {}", allInfo.user().getId());
                return createDriverProfile(allInfo.user().getId(), allInfo.organisation().getOrganizationId(), dto, allInfo.user())
                    .then(buildFinalResponse(allInfo.loginResponse()));
            });
    }

    /**
     * Orchestre la création complète d'un compte Client (Auth + Organisation + ClientProfile).
     * Utilisé pour la première inscription.
     */
    public Mono<OnboardingResponse> createClientAccount(ClientOnboardingRequest dto) {
        return verifyOtp(dto.getEmail(), dto.getOtp())
            .then(createAuthUserAndOrganization(dto.getEmail(), dto.getPassword(), dto.getFirstName(), dto.getLastName(), dto.getPhoneNumber(), dto.getCompanyName(), dto.getCompanyDescription()))
            .flatMap(allInfo -> {
                log.info("Étape 5/6 (Client): Sauvegarde du profil Client pour l'utilisateur ID: {}", allInfo.user().getId());
                return createClientProfile(allInfo.user().getId(), allInfo.organisation().getOrganizationId(), dto, allInfo.user())
                    .then(buildFinalResponse(allInfo.loginResponse()));
            });
    }

    /**
     * Crée un profil Driver pour un utilisateur **EXISTANT**.
     * Réutilise l'organisation principale de l'utilisateur ou en crée une par défaut si aucune n'existe.
     * @param userId L'ID de l'utilisateur existant.
     * @param dto Les données du profil chauffeur.
     * @param userBearerToken Le token JWT de l'utilisateur.
     * @return Le DriverProfile créé.
     */
    public Mono<DriverProfile> createDriverProfileForExistingUser(UUID userId, DriverOnboardingRequest dto, String userBearerToken) {
        log.info("Création du profil Driver pour un utilisateur existant: {}", userId);
        // Récupérer le contexte utilisateur existant pour trouver l'organisation ID
        return profileService.getUserSessionContext(userId, userBearerToken, publicKey)
            .flatMap(context -> {
                UUID organizationId = context.getOrganisation() != null ? context.getOrganisation().getOrganizationId() : null;
                if (organizationId == null) {
                    log.warn("L'utilisateur {} n'a pas d'organisation existante. Création d'une organisation par défaut pour le nouveau profil chauffeur.", userId);
                    OrganisationCreationRequest orgRequest = OrganisationCreationRequest.builder()
                        .longName(dto.getFirstName() + " " + dto.getLastName() + "'s Driving Services")
                        .email(dto.getEmail()) // Utiliser l'email du DTO pour l'organisation
                        .build();
                    // Nécessite un token M2M pour appeler le service d'organisation
                    return authService.getClientCredentialsToken(oauthClientId, oauthClientSecret)
                        .flatMap(m2mToken -> organisationService.createOrganisation(orgRequest, "Bearer " + m2mToken.getAccessToken(), publicKey))
                        .map(OrganisationDto::getOrganizationId);
                }
                return Mono.just(organizationId);
            })
            .flatMap(orgId -> {
                // Créer un UserInfo factice pour la création de profil, car l'utilisateur existe déjà
                LoginResponse.UserInfo dummyUser = new LoginResponse.UserInfo();
                dummyUser.setId(userId);
                dummyUser.setFirstName(dto.getFirstName());
                dummyUser.setLastName(dto.getLastName());
                dummyUser.setPhoneNumber(dto.getPhoneNumber()); // Utiliser le numéro du DTO
                dummyUser.setEmail(dto.getEmail());

                return createDriverProfile(userId, orgId, dto, dummyUser);
            });
    }

    /**
     * Crée un profil Client pour un utilisateur **EXISTANT**.
     * Réutilise l'organisation principale de l'utilisateur ou en crée une par défaut si aucune n'existe.
     * @param userId L'ID de l'utilisateur existant.
     * @param dto Les données du profil client.
     * @param userBearerToken Le token JWT de l'utilisateur.
     * @return Le ClientProfile créé.
     */
    public Mono<ClientProfile> createClientProfileForExistingUser(UUID userId, ClientOnboardingRequest dto, String userBearerToken) {
        log.info("Création du profil Client pour un utilisateur existant: {}", userId);
        return profileService.getUserSessionContext(userId, userBearerToken, publicKey)
            .flatMap(context -> {
                UUID organizationId = context.getOrganisation() != null ? context.getOrganisation().getOrganizationId() : null;
                if (organizationId == null) {
                     log.warn("L'utilisateur {} n'a pas d'organisation existante. Création d'une organisation par défaut pour le nouveau profil client.", userId);
                    OrganisationCreationRequest orgRequest = OrganisationCreationRequest.builder()
                        .longName(dto.getCompanyName().isEmpty() ? (dto.getFirstName() + " " + dto.getLastName() + "'s Client Account") : dto.getCompanyName())
                        .email(dto.getEmail()) // Utiliser l'email du DTO pour l'organisation
                        .build();
                     return authService.getClientCredentialsToken(oauthClientId, oauthClientSecret)
                        .flatMap(m2mToken -> organisationService.createOrganisation(orgRequest, "Bearer " + m2mToken.getAccessToken(), publicKey))
                        .map(OrganisationDto::getOrganizationId);
                }
                return Mono.just(organizationId);
            })
            .flatMap(orgId -> {
                LoginResponse.UserInfo dummyUser = new LoginResponse.UserInfo();
                dummyUser.setId(userId);
                dummyUser.setFirstName(dto.getFirstName());
                dummyUser.setLastName(dto.getLastName());
                dummyUser.setPhoneNumber(dto.getPhoneNumber()); // Utiliser le numéro du DTO
                dummyUser.setEmail(dto.getEmail());

                return createClientProfile(userId, orgId, dto, dummyUser);
            });
    }


    /**
     * Helper privé pour créer un DriverProfile dans la DB.
     */
    private Mono<DriverProfile> createDriverProfile(UUID userId, UUID organizationId, DriverOnboardingRequest dto, LoginResponse.UserInfo user) {
        DriverProfile profile = new DriverProfile();
        profile.setId(UUID.randomUUID());
        profile.setUserId(userId);
        profile.setOrganisationId(organizationId);
        profile.setFirstName(user.getFirstName());
        profile.setLastName(user.getLastName());
        profile.setPhoneNumber(user.getPhoneNumber());
        profile.setLicenseNumber(dto.getLicenseNumber());
        profile.setVehicleDetails(dto.getVehicleDetails());
        return driverProfileRepository.save(profile);
    }

    /**
     * Helper privé pour créer un ClientProfile dans la DB.
     */
    private Mono<ClientProfile> createClientProfile(UUID userId, UUID organizationId, ClientOnboardingRequest dto, LoginResponse.UserInfo user) {
        ClientProfile profile = new ClientProfile();
        profile.setId(UUID.randomUUID());
        profile.setUserId(userId);
        profile.setOrganisationId(organizationId);
        profile.setFirstName(dto.getFirstName());
        profile.setLastName(dto.getLastName());
        profile.setCompanyName(dto.getCompanyName());
        profile.setContactEmail(dto.getEmail());
        profile.setPhoneNumber(user.getPhoneNumber()); // Utiliser le téléphone de l'utilisateur Auth pour la cohérence
        return clientProfileRepository.save(profile);
    }


    /**
     * Méthode privée pour vérifier la validité d'un OTP. (Inchangée)
     */
    private Mono<Void> verifyOtp(String email, String otp) {
        log.info("▶️ Étape 1/6: Vérification de l'OTP {} pour l'email {}", otp, email);

        return otpVerificationRepository.findById(email)
            .switchIfEmpty(
                Mono.defer(() -> {
                    log.error("❌ Aucune demande de vérification trouvée pour l'email : {}", email);
                    return Mono.error(new IllegalStateException("Aucune demande de vérification trouvée pour cet email."));
                })
            )
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

    /**
     * Gère la connexion de l'utilisateur (déjà créé à l'étape /api/register)
     * et la création de son organisation via l'API externe réelle. (Inchangée)
     */

    
    //Version originale
    /*private Mono<AllInfo> createAuthUserAndOrganization(String email, String password, String firstName, String lastName, String phoneNumber, String companyName, String companyDescription) {
        LoginRequest loginRequest = new LoginRequest(email, password);

        return authService.getClientCredentialsToken(oauthClientId, oauthClientSecret)
            .doOnSuccess(tokenResponse -> log.info("Étape 2/6: Token de service M2M obtenu."))
            .flatMap(m2mTokenResponse -> {
                String m2mBearerToken = "Bearer " + m2mTokenResponse.getAccessToken();

                //On commentes l'utilisation de m2mBearerToken
                
                return authService.loginUser(loginRequest, m2mBearerToken)
                    .switchIfEmpty(Mono.error(new RuntimeException("Échec de la connexion de l'utilisateur déjà enregistré pour " + email + ". Vérifiez les identifiants.")))
                    .doOnSuccess(loginResponse -> log.info("Étape 3/6: Connexion de l'utilisateur {} réussie. (ID: {})", loginResponse.getUser().getUsername(), loginResponse.getUser().getId()))
                    .flatMap(loginResponse -> {
                        LoginResponse.UserInfo registeredUser = loginResponse.getUser();
                        String userBearerToken = "Bearer " + loginResponse.getAccessToken().getToken();

                        String finalCompanyName = companyName.trim().isEmpty() ?
                                                    (firstName.trim() + " " + lastName.trim() + "'s Business") :
                                                    companyName.trim();
                        String finalCompanyDescription = companyDescription.trim().isEmpty() ?
                                                    ("Freelance services for " + (finalCompanyName.contains("'s Business") ? firstName : finalCompanyName)) :
                                                    companyDescription.trim();

                        OrganisationCreationRequest orgRequest = OrganisationCreationRequest.builder()
                                .longName(finalCompanyName)
                                .shortName(generateShortName(finalCompanyName))
                                .description(finalCompanyDescription)
                                .email(email)
                                .build();

                        log.info("Étape 4/6: Création de l'organisation pour l'utilisateur {} via l'API externe.", email);
                        return organisationService.createOrganisation(orgRequest, userBearerToken, publicKey)
                            .map(createdOrg -> {
                                log.info("✅ Organisation '{}' (provenance: {}) créée. Données: {}",
                                    createdOrg.getLongName(),
                                    organisationService.getClass().getSimpleName(),
                                    createdOrg);
                                return new AllInfo(registeredUser, createdOrg, loginResponse);
                            });
                    });
            });
    }
    */



    //On commentes l'utilisation de m2mBearerToken

    private Mono<AllInfo> createAuthUserAndOrganization(String email, String password, String firstName, String lastName, String phoneNumber, String companyName, String companyDescription) {
        LoginRequest loginRequest = new LoginRequest(email, password);

                return authService.loginUser(loginRequest, "jdjdjdkdke")
                    .switchIfEmpty(Mono.error(new RuntimeException("Échec de la connexion de l'utilisateur déjà enregistré pour " + email + ". Vérifiez les identifiants.")))
                    .doOnSuccess(loginResponse -> log.info("Étape 3/6: Connexion de l'utilisateur {} réussie. (ID: {})", loginResponse.getUser().getUsername(), loginResponse.getUser().getId()))
                    .flatMap(loginResponse -> {
                        LoginResponse.UserInfo registeredUser = loginResponse.getUser();
                        String userBearerToken = "Bearer " + loginResponse.getAccessToken().getToken();

                        String finalCompanyName = companyName.trim().isEmpty() ?
                                                    (firstName.trim() + " " + lastName.trim() + "'s Business") :
                                                    companyName.trim();
                        String finalCompanyDescription = companyDescription.trim().isEmpty() ?
                                                    ("Freelance services for " + (finalCompanyName.contains("'s Business") ? firstName : finalCompanyName)) :
                                                    companyDescription.trim();

                        OrganisationCreationRequest orgRequest = OrganisationCreationRequest.builder()
                                .longName(finalCompanyName)
                                .shortName(generateShortName(finalCompanyName))
                                .description(finalCompanyDescription)
                                .email(email)
                                .build();

                        log.info("Étape 4/6: Création de l'organisation pour l'utilisateur {} via l'API externe.", email);
                        return organisationService.createOrganisation(orgRequest, userBearerToken, publicKey)
                            .map(createdOrg -> {
                                log.info("✅ Organisation '{}' (provenance: {}) créée. Données: {}",
                                    createdOrg.getLongName(),
                                    organisationService.getClass().getSimpleName(),
                                    createdOrg);
                                return new AllInfo(registeredUser, createdOrg, loginResponse);
                            });
                    });
            
    }
    
    /**
     * Construit la réponse finale à envoyer au frontend. (Inchangée)
     */
    private Mono<OnboardingResponse> buildFinalResponse(LoginResponse loginResponse) {
        UUID userId = loginResponse.getUser().getId();
        String userBearerToken = "Bearer " + loginResponse.getAccessToken().getToken();
        log.info("Étape 6/6: Construction du contexte de session final pour l'utilisateur ID: {}", userId);

        return profileService.getUserSessionContext(userId, userBearerToken, null)
            .map(userContext -> {
                log.info("Backend DEBUG: Final UserContext before sending to frontend. Roles: {}", userContext.getRoles());
                return OnboardingResponse.builder()
                    .token(loginResponse.getAccessToken().getToken())
                    .profile(userContext)
                    .chatSession(null)
                    .build();
            });
    }

    private String generateShortName(String longName) { // (Inchangée)
        if (longName == null || longName.trim().isEmpty()) return "NA";
        String cleanedName = longName.trim();
        StringBuilder initials = new StringBuilder();
        for (String s : cleanedName.split("\\s+")) {
            if (!s.isEmpty()) initials.append(s.charAt(0));
        }
        String shortName = initials.toString().toUpperCase();
        return shortName.length() < 2 ? cleanedName.substring(0, Math.min(cleanedName.length(), 3)).toUpperCase() : shortName;
    }
}