package com.freelance.driver_backend.service.external;

import com.freelance.driver_backend.dto.external.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component

@Slf4j
public class ChatServiceWebClientImpl implements ChatService {

    private final WebClient webClient;
    private final String projectId;

    public ChatServiceWebClientImpl(@Qualifier("chatServiceWebClient") WebClient webClient,
                                  @Value("${freelancedriver.chat.project-id}") String projectId) {
        this.webClient = webClient;
        this.projectId = projectId;
    }

    private Mono<Void> createUserInChatSystem(ChatUserCreationPayload payload) {
        log.info("Chat API Call: Creating user {}", payload.getLogin());
        payload.setProjectId(this.projectId);
        return webClient.post().uri("/users/create")
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .flatMap(error -> Mono.error(new RuntimeException("Chat user creation failed: " + error))))
                .bodyToMono(Void.class);
    }

    @Override
    public Mono<ChatUserLoginResponse> registerAndLoginChatUser(ChatUserCreationPayload payload) {
        return createUserInChatSystem(payload)
                .then(loginChatUser(new ChatUserLoginPayload(null, payload.getLogin(), payload.getSecret())));
    }

    
    @Override
    public Mono<ChatUserLoginResponse> loginChatUser(ChatUserLoginPayload payload) {
        log.info("Chat API Call: Logging in user {}", payload.getLogin());
        payload.setProjectId(this.projectId);
        return webClient.post().uri("/auth/user")
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .flatMap(error -> Mono.error(new RuntimeException("Chat login failed: " + error))))
                .bodyToMono(ChatUserLoginResponse.class);
    }
}