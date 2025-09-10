/*package com.freelance.driver_backend.service;

import com.freelance.driver_backend.dto.external.ChatUserLoginPayload;
import com.freelance.driver_backend.dto.external.ChatUserLoginResponse;
import com.freelance.driver_backend.dto.external.LoginRequest;
import com.freelance.driver_backend.dto.external.LoginResponse; // Ajout de l'import LoginResponse
import com.freelance.driver_backend.dto.onboarding.ChatSessionInfo;
import com.freelance.driver_backend.dto.onboarding.OnboardingResponse;
import com.freelance.driver_backend.repository.ClientProfileRepository;
import com.freelance.driver_backend.repository.DriverProfileRepository;
import com.freelance.driver_backend.service.external.AuthService;
import com.freelance.driver_backend.service.external.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginService {

    private final AuthService authService;
    private final ChatService chatService; 
    private final DriverProfileRepository driverProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final ProfileService profileService; // AJOUT DE CETTE INJECTION

    @Value("${freelancedriver.oauth2.client-id}")
    private String oauthClientId;
    @Value("${freelancedriver.oauth2.client-secret}")
    private String oauthClientSecret;

    public Mono<OnboardingResponse> loginAndGetContext(LoginRequest loginRequest) {
        log.info("Login process started for user: {}", loginRequest.getUsername());

        return authService.getClientCredentialsToken(oauthClientId, oauthClientSecret)
                .flatMap(m2mToken -> authService.loginUser(loginRequest, "Bearer " + m2mToken.getAccessToken()))
                .flatMap(loginResponse -> {
                    if (loginResponse.getUser() == null || loginResponse.getUser().getId() == null) {
                        return Mono.error(new RuntimeException("Incomplete login response from auth service."));
                    }

                    log.info("User {} successfully logged in. Chat login temporarily disabled.", loginResponse.getUser().getUsername());

                    // ==== DÉBUT CODE CHAT TEMPORAIREMENT DÉSACTIVÉ ====
                    
                     //ChatUserLoginPayload chatLoginPayload = new ChatUserLoginPayload(null, loginRequest.getUsername(), loginRequest.getPassword());
                     //Mono<ChatUserLoginResponse> chatSessionMono = chatService.loginChatUser(chatLoginPayload);
                    
                    // ==== FIN CODE CHAT TEMPORAIREMENT DÉSACTIVÉ ====

                    // La correction est ici: Utilisez profileService pour obtenir le contexte complet
                    // y compris le rôle correctement déterminé par votre backend.
                    // Le token de l'utilisateur est nécessaire pour appeler getOrganisationById si nécessaire dans ProfileService
                    String userBearerToken = "Bearer " + loginResponse.getAccessToken().getToken();

                    return profileService.getUserSessionContext(loginResponse.getUser().getId(), userBearerToken, null)
                            .flatMap(userContextDto -> {
                                if (userContextDto == null) {
                                    return Mono.error(new RuntimeException("No local profile context found for user " + loginResponse.getUser().getId()));
                                }

                                log.info("Profile context found for user {}. Role: {}", loginResponse.getUser().getUsername(), userContextDto.getRole());

                                // Construction de la réponse AVEC le userContextDto complet
                                return Mono.just(OnboardingResponse.builder()
                                        .token(loginResponse.getAccessToken().getToken())
                                        .profile(userContextDto) // <-- C'EST ICI LA CLÉ: ENVOYER userContextDto complet
                                        .chatSession(null)
                                        .build());
                            });
                });
    }
} */

// PATH: /home/mbogneng-junior/freelance-driver (Copie)/backend/src/main/java/com/freelance/driver_backend/service/LoginService.java

package com.freelance.driver_backend.service;

import com.freelance.driver_backend.dto.external.ChatUserLoginPayload;
import com.freelance.driver_backend.dto.external.ChatUserLoginResponse;
import com.freelance.driver_backend.dto.external.LoginRequest;
import com.freelance.driver_backend.dto.external.LoginResponse;
import com.freelance.driver_backend.dto.onboarding.ChatSessionInfo;
import com.freelance.driver_backend.dto.onboarding.OnboardingResponse;
import com.freelance.driver_backend.repository.ClientProfileRepository;
import com.freelance.driver_backend.repository.DriverProfileRepository;
import com.freelance.driver_backend.service.external.AuthService;
import com.freelance.driver_backend.service.external.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginService {

    private final AuthService authService;
    private final ChatService chatService;
    private final DriverProfileRepository driverProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final ProfileService profileService;

    @Value("${freelancedriver.oauth2.client-id}")
    private String oauthClientId;
    @Value("${freelancedriver.oauth2.client-secret}")
    private String oauthClientSecret;

    public Mono<OnboardingResponse> loginAndGetContext(LoginRequest loginRequest) {
        log.info("Login process started for user: {}", loginRequest.getUsername());

        return authService.getClientCredentialsToken(oauthClientId, oauthClientSecret)
                .flatMap(m2mToken -> authService.loginUser(loginRequest, "Bearer " + m2mToken.getAccessToken()))
                .flatMap(loginResponse -> {
                    if (loginResponse.getUser() == null || loginResponse.getUser().getId() == null) {
                        return Mono.error(new RuntimeException("Incomplete login response from auth service."));
                    }

                    log.info("User {} successfully logged in. Chat login temporarily disabled.", loginResponse.getUser().getUsername());

                    String userBearerToken = "Bearer " + loginResponse.getAccessToken().getToken();

                    return profileService.getUserSessionContext(loginResponse.getUser().getId(), userBearerToken, null)
                            .flatMap(userContextDto -> {
                                if (userContextDto == null) {
                                    return Mono.error(new RuntimeException("No local profile context found for user " + loginResponse.getUser().getId()));
                                }

                                // MODIFIÉ : Utilise getRoles() qui retourne une List<UserRole>
                                log.info("Profile context found for user {}. Roles: {}", loginResponse.getUser().getUsername(), userContextDto.getRoles());

                                return Mono.just(OnboardingResponse.builder()
                                        .token(loginResponse.getAccessToken().getToken())
                                        .profile(userContextDto)
                                        .chatSession(null)
                                        .build());
                            });
                });
    }
}