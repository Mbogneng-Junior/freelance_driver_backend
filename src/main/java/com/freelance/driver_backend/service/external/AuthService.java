package com.freelance.driver_backend.service.external;

import com.freelance.driver_backend.dto.external.*;
import reactor.core.publisher.Mono;

/**
 * Interface définissant le contrat pour les interactions avec le service d'authentification externe.
 * Cela permet de découpler la logique métier des implémentations concrètes (API réelle vs Mock local).
 */
public interface AuthService {

    /**
     * Obtient un token de type Machine-to-Machine (M2M) en utilisant les client credentials.
     * @param clientId L'ID du client OAuth2.
     * @param clientSecret Le secret du client OAuth2.
     * @return Un Mono contenant la réponse avec le token.
     */
    Mono<OAuthTokenResponse> getClientCredentialsToken(String clientId, String clientSecret);

    /**
     * Enregistre un nouvel utilisateur auprès du service d'authentification.
     * @param request Les données d'inscription de l'utilisateur.
     * @param m2mBearerToken Le token M2M nécessaire pour autoriser cette opération.
     * @return Un Mono contenant les informations de l'utilisateur créé.
     */
   

     Mono<UserDto> registerUser(RegistrationRequest request, String m2mBearerToken);

    /**
     * Connecte un utilisateur pour obtenir un token d'accès personnel.
     * @param request Les identifiants de l'utilisateur.
     * @param m2mBearerToken Le token M2M nécessaire pour autoriser cette opération.
     * @return Un Mono contenant la réponse de connexion complète (token, infos utilisateur, etc.).
     */
     Mono<LoginResponse> loginUser(LoginRequest request, String m2mBearerToken);



    
}