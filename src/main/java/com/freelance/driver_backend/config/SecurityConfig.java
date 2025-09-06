package com.freelance.driver_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;


@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    // ==============================================================================
    //                       CHAÎNE DE FILTRES DE SÉCURITÉ UNIQUE
    // Une seule chaîne de filtres pour gérer toutes les routes de l'API.
    // ==============================================================================
    
    @Bean
    @Order(0) // Priorité la plus haute
    public SecurityWebFilterChain apiFilterChain(ServerHttpSecurity http) throws Exception {
        http
            // Appliquer cette configuration à toutes les routes sous /api/
            .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/**"))
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                // --- Routes Publiques (permitAll) ---
                .pathMatchers(HttpMethod.POST, 
                    "/api/register", 
                    "/api/auth/login", 
                    "/api/onboarding/**"
                ).permitAll()
                
                .pathMatchers(HttpMethod.GET,
                    "/api/announcements", 
                    "/api/planning/published", 
                    "/api/planning/user/**", // Ajout de cette ligne
                    "/api/reviews/user/**", 
                    "/api/profiles/user/**", 
                    "/api/vehicles/user/**", 
                    "/api/addresses/user/**", 
                    "/api/experiences/user/**"
                ).permitAll()

                // Routes pour les mocks de développement
                .pathMatchers("/api/mock-**/**").permitAll()

                // --- Routes Sécurisées (authenticated) ---
                // Toutes les autres requêtes nécessitent une authentification
                .anyExchange().authenticated()
            )
            // Configurer le serveur de ressources OAuth2 pour valider les tokens JWT
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(jwtDecoder())));

        return http.build();
    }

    // ==============================================================================
    //                       CONFIGURATION CORS ET JWT
    // ==============================================================================

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Spécifiez les origines exactes de votre frontend en production
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:19006", "http://localhost:8081", "exp://*", "http://192.168.43.4:8081")); // IP à adapter
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(this.jwkSetUri).build();
        // Pour le développement, on peut désactiver la validation de la signature si nécessaire,
        // mais il est préférable de la garder.
        OAuth2TokenValidator<Jwt> noOpValidator = (token) -> OAuth2TokenValidatorResult.success();
        jwtDecoder.setJwtValidator(noOpValidator);
        return jwtDecoder;
    }
}