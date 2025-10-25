package com.freelance.driver_backend.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Value("${microservices.chat-service.url}")
    private String chatServiceUrl;

    @Value("${microservices.auth-service.url}")
    private String authServiceUrl;

    @Value("${microservices.organisation-service.url}")
    private String organisationServiceUrl;

    @Value("${server.port}")
    private String localServerPort;

    @Value("${microservices.resource-service.url}")
    private String resourceServiceUrl;

    @Value("${microservices.media-service.url}") // URL du service de médias, nouvellement ajoutée
    private String mediaServiceUrl;

    /**
     * Crée un HttpClient réutilisable avec des timeouts standards.
     * 
     * @return HttpClient configuré
     */
    private HttpClient createConfiguredHttpClient() {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 20000) // 20 secondes
                .responseTimeout(Duration.ofSeconds(20)) // 20 secondes
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(20, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(20, TimeUnit.SECONDS)));
    }

    /**
     * Crée un HttpClient configuré avec des timeouts plus longs pour les opérations
     * de média.
     * Les téléversements de fichiers peuvent prendre plus de temps.
     * 
     * @return HttpClient configuré pour les médias
     */
    private HttpClient createMediaServiceHttpClient() {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000) // 30 secondes pour la connexion
                .responseTimeout(Duration.ofSeconds(60)) // 60 secondes pour la réponse complète
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(60, TimeUnit.SECONDS)) // 60s read
                                                                                                         // timeout
                        .addHandlerLast(new WriteTimeoutHandler(60, TimeUnit.SECONDS))); // 60s write timeout
    }

    @Bean
    @Qualifier("authServiceWebClient")
    public WebClient authServiceWebClient() {
        return WebClient.builder()
                .baseUrl(authServiceUrl)
                .clientConnector(new ReactorClientHttpConnector(createConfiguredHttpClient()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    @Qualifier("organisationServiceWebClient")
    public WebClient organisationServiceWebClient() {
        return WebClient.builder()
                .baseUrl(organisationServiceUrl)
                .clientConnector(new ReactorClientHttpConnector(createConfiguredHttpClient()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    @Qualifier("chatServiceWebClient")
    public WebClient chatServiceWebClient() {
        return WebClient.builder()
                .baseUrl(chatServiceUrl)
                .clientConnector(new ReactorClientHttpConnector(createConfiguredHttpClient()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    @Qualifier("localApiWebClient")
    public WebClient localApiWebClient() {
        return WebClient.builder().baseUrl("http://localhost:" + localServerPort).build();
    }

    @Bean
    @Qualifier("externalResourceWebClient")
    public WebClient externalResourceWebClient() {
        return WebClient.builder().baseUrl(resourceServiceUrl).build();
    }

    /**
     * Crée un WebClient pour interagir avec le service de médias externe.
     * Utilise des timeouts plus longs pour les opérations de fichiers.
     * 
     * @return WebClient configuré pour le service de médias
     */
    @Bean
    @Qualifier("externalMediaServiceWebClient")
    public WebClient externalMediaServiceWebClient() {
        return WebClient.builder()
                .baseUrl(mediaServiceUrl)
                .clientConnector(new ReactorClientHttpConnector(createMediaServiceHttpClient()))
                // Le Content-Type est défini par BodyInserters.fromMultipartData() pour les
                // uploads de fichiers
                .build();
    }

    

    @Bean
    @Qualifier("mockWebClient")
    public WebClient mockWebClient() {
        return WebClient.builder()
                .baseUrl("http://localhost:" + localServerPort)
                .clientConnector(new ReactorClientHttpConnector(createConfiguredHttpClient()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

}
