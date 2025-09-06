

package com.freelance.driver_backend.service;

import com.freelance.driver_backend.dto.external.*;
import com.freelance.driver_backend.dto.onboarding.ClientOnboardingRequest;
import com.freelance.driver_backend.dto.onboarding.DriverOnboardingRequest;
import com.freelance.driver_backend.dto.onboarding.OnboardingResponse;
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
import java.util.Collections; // Ajout de l'import manquant
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
     * Orchestre la création complète d'un compte Chauffeur.
     */
    public Mono<OnboardingResponse> createDriverAccount(DriverOnboardingRequest dto) {
        // 1. Vérifier l'OTP. Si invalide, la chaîne s'arrête et renvoie une erreur.
        return verifyOtp(dto.getEmail(), dto.getOtp())
            // 2. Si l'OTP est valide, continuer avec la création générique du compte.
            .then(createGenericAccount(dto.getEmail(), dto.getPassword(), dto.getFirstName(), dto.getLastName(), dto.getPhoneNumber(), dto.getCompanyName(), dto.getCompanyDescription()))
            // 3. Une fois l'utilisateur et l'organisation créés, sauvegarder le profil spécifique au chauffeur.
            .flatMap(allInfo -> {
                log.info("Étape 5/6 (Driver): Sauvegarde du profil Driver.");
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

    /**
     * Orchestre la création complète d'un compte Client.
     */
    public Mono<OnboardingResponse> createClientAccount(ClientOnboardingRequest dto) {
        return verifyOtp(dto.getEmail(), dto.getOtp())
            .then(createGenericAccount(dto.getEmail(), dto.getPassword(), dto.getFirstName(), dto.getLastName(), dto.getPhoneNumber(), dto.getCompanyName(), dto.getCompanyDescription()))
            .flatMap(allInfo -> {
                log.info("Étape 5/6 (Client): Sauvegarde du profil Client.");
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

    /**
     * Méthode privée pour vérifier la validité d'un OTP.
     * C'est la première barrière de sécurité avant de créer un compte.
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
     * et la création de son organisation via l'API externe réelle.
     */
    private Mono<AllInfo> createGenericAccount(String email, String password, String firstName, String lastName, String phoneNumber, String companyName, String companyDescription) {
        // L'utilisateur est DÉJÀ enregistré par AuthController::registerUserAndInitiateOtp.
        // Nous avons seulement besoin de le connecter pour obtenir ses informations et son token.
        LoginRequest loginRequest = new LoginRequest(email, password); 

        return authService.getClientCredentialsToken(oauthClientId, oauthClientSecret)
            .doOnSuccess(tokenResponse -> log.info("Étape 2/6: Token de service M2M obtenu."))
            .flatMap(m2mTokenResponse -> {
                String m2mBearerToken = "Bearer " + m2mTokenResponse.getAccessToken();
                
                // Connexion de l'utilisateur déjà enregistré
                return authService.loginUser(loginRequest, m2mBearerToken)
                    .switchIfEmpty(Mono.error(new RuntimeException("Échec de la connexion de l'utilisateur déjà enregistré pour " + email + ". Vérifiez les identifiants.")))
                    .doOnSuccess(loginResponse -> log.info("Étape 3/6: Connexion de l'utilisateur {} réussie. (ID: {})", loginResponse.getUser().getUsername(), loginResponse.getUser().getId()))
                    .flatMap(loginResponse -> {
                        // Maintenant, nous avons le UserDto à partir de loginResponse.getUser()
                        LoginResponse.UserInfo registeredUser = loginResponse.getUser(); 
                        String userBearerToken = "Bearer " + loginResponse.getAccessToken().getToken();
                        
                        // Utiliser la même logique pour companyName et companyDescription que dans SignUp.tsx
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
                                .email(email) // Important pour la création d'organisation réelle
                                .build();
                        
                        log.info("Étape 4/6: Création de l'organisation pour l'utilisateur {} via l'API externe.", email);
                        // Appel de l'implémentation réelle de OrganisationService (OrganisationServiceWebClientImpl)
                        return organisationService.createOrganisation(orgRequest, userBearerToken, publicKey)
                            .map(createdOrg -> {
                                log.info("✅ Organisation '{}' (provenance: {}) créée. Données: {}", 
                                    createdOrg.getLongName(), 
                                    organisationService.getClass().getSimpleName(), 
                                    createdOrg);
                                // Passer le 'registeredUser' (issu de la connexion) à l'enregistrement AllInfo
                                return new AllInfo(registeredUser, createdOrg, loginResponse);
                            });
                    });
            });
    }

    /**
     * Construit la réponse finale à envoyer au frontend.
     */
    private Mono<OnboardingResponse> buildFinalResponse(LoginResponse loginResponse) {
        UUID userId = loginResponse.getUser().getId();
        String userBearerToken = "Bearer " + loginResponse.getAccessToken().getToken();
        log.info("Étape 6/6: Construction du contexte de session final pour l'utilisateur ID: {}", userId);
        
        return profileService.getUserSessionContext(userId, userBearerToken, null)
            .map(userContext -> {
                log.info("Backend DEBUG: Final UserContext before sending to frontend. Role: {}", userContext.getRole()); // AJOUTEZ CE LOG
                return OnboardingResponse.builder()
                    .token(loginResponse.getAccessToken().getToken())
                    .profile(userContext) // <-- userContext EST DÉJÀ UN UserSessionContextDto complet
                    .chatSession(null)
                    .build();
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
        return shortName.length() < 2 ? cleanedName.substring(0, Math.min(cleanedName.length(), 3)).toUpperCase() : shortName;
    }
}