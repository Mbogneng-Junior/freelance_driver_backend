package com.freelance.driver_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher; // <-- Nouvel import
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.core.annotation.Order; 

import java.util.Arrays;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    // ==============================================================================
    //                         LA CORRECTION EST ICI
    // ==============================================================================
    
    @Bean
    @Order(1) // Priorité la plus haute
    public SecurityWebFilterChain publicApiFilterChain(ServerHttpSecurity http) {
        return http
            .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/auth/**"))
            .csrf(csrf -> csrf.disable())
            .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
            .build();
    }

    @Bean
    @Order(2) // Priorité suivante
    public SecurityWebFilterChain onboardingFilterChain(ServerHttpSecurity http) {
        return http
            .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/onboarding/**"))
            .csrf(csrf -> csrf.disable())
            .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
            .build();
    }

    @Bean
    @Order(3) // Priorité suivante
    public SecurityWebFilterChain mockNotificationFilterChain(ServerHttpSecurity http) {
        return http
            .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/mock-notifications/**"))
            .csrf(csrf -> csrf.disable())
            .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
            .build();
    }

    @Bean
    @Order(4) // Il sera vérifié AVANT la règle générale
    public SecurityWebFilterChain mockProductsFilterChain(ServerHttpSecurity http) {
        return http
            .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/mock-products/**"))
            .csrf(csrf -> csrf.disable())
            .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
            .build();
    }
    
    // ==============================================================================
    @Bean
    @Order(5) // Assurez-vous que l'ordre est correct
    public SecurityWebFilterChain mockMediaFilterChain(ServerHttpSecurity http) {
        return http
            // On autorise toutes les requêtes qui commencent par /media/
            .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/media/**"))
            .csrf(csrf -> csrf.disable())
            .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll()) // On le rend public
            .build();
    }

    @Bean
    @Order(6) // <-- ATTENTION : Changer le numéro d'ordre de 4 à 5
    public SecurityWebFilterChain securedApiFilterChain(ServerHttpSecurity http) throws Exception {
        http
            .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/**"))
            .csrf(csrf -> csrf.disable())
            .authorizeExchange(exchanges -> exchanges
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(jwtDecoder())));

        return http.build();
    }
    // ==============================================================================


    // La configuration CORS n'est plus nécessaire dans la chaîne de sécurité car
    // elle sera gérée globalement si vous l'avez configurée. 
    // Mais il est plus propre de l'avoir ici. On va la laisser, mais séparée.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:19006", "http://localhost:8081", "http://localhost:8082", "http://192.168.43.4:8081", // Expo Go
  "http://192.168.43.4:19006", // Expo devtools via navigateur (facultatif)
  "http://192.168.43.4:8080"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Appliquer à toutes les routes
        return source;
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(this.jwkSetUri).build();
        OAuth2TokenValidator<Jwt> noOpValidator = (token) -> OAuth2TokenValidatorResult.success();
        jwtDecoder.setJwtValidator(noOpValidator);
        return jwtDecoder;
    }
}