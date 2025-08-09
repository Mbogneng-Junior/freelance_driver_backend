package com.freelance.driver_backend.service;

import com.freelance.driver_backend.dto.external.ChatUserLoginPayload;
import com.freelance.driver_backend.dto.external.ChatUserLoginResponse;
import com.freelance.driver_backend.dto.external.LoginRequest;
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
    private final ChatService chatService; // ← Tu peux laisser ça, même si on n'appelle pas le service pour l'instant
    private final DriverProfileRepository driverProfileRepository;
    private final ClientProfileRepository clientProfileRepository;

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
                    /* 
                     ChatUserLoginPayload chatLoginPayload = new ChatUserLoginPayload(null, loginRequest.getUsername(), loginRequest.getPassword());
                     Mono<ChatUserLoginResponse> chatSessionMono = chatService.loginChatUser(chatLoginPayload);
                    */
                    // ==== FIN CODE CHAT TEMPORAIREMENT DÉSACTIVÉ ====

                    Mono<Object> profileMono = driverProfileRepository.findByUserId(loginResponse.getUser().getId())
                            .cast(Object.class)
                            .switchIfEmpty(clientProfileRepository.findByUserId(loginResponse.getUser().getId()));

                    return profileMono.flatMap(profile -> {
                        if (profile == null) {
                            return Mono.error(new RuntimeException("No local profile found for user " + loginResponse.getUser().getId()));
                        }

                        log.info("Profile found for user {}", loginResponse.getUser().getUsername());

                        // ==== Construction de la réponse SANS chat ====
                        return Mono.just(OnboardingResponse.builder()
                                .token(loginResponse.getAccessToken().getToken())
                                .profile(profile)
                                .chatSession(null) // ← On renvoie null pour le chat pour l’instant
                                .build());
                    });

                    /* Variante si tu veux éviter le champ `chatSession` du tout :
                    return Mono.just(OnboardingResponse.builder()
                            .token(loginResponse.getAccessToken().getToken())
                            .profile(profile)
                            .build());
                    */
                });
    }
}
