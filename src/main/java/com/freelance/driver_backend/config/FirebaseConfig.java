// src/main/java/com/freelance/driver_backend/config/FirebaseConfig.java

package com.freelance.driver_backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
@Slf4j
public class FirebaseConfig {

    /**
     * Ce @Bean lit votre fichier firebase-service-account-key.example,
     * initialise le SDK Admin de Firebase, et retourne l'objet FirebaseApp.
     * Spring mettra cet objet à disposition de tous les autres composants qui en ont besoin.
     * @return L'instance initialisée de FirebaseApp.
     * @throws IOException Si le fichier de clé n'est pas trouvé.
     */
    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        // On vérifie s'il n'a pas déjà été initialisé (utile pour les rechargements à chaud)
        if (FirebaseApp.getApps().isEmpty()) {
            log.info("Initialisation du SDK Admin de Firebase...");

            // On charge le fichier de clé depuis le dossier src/main/resources
            ClassPathResource resource = new ClassPathResource("firebase-service-account-key.example");
            if (!resource.exists()) {
                log.error("Le fichier 'firebase-service-account-key.example' est introuvable dans src/main/resources/");
                throw new IOException("Le fichier firebase-service-account-key.example est introuvable.");
            }

            try (InputStream serviceAccount = resource.getInputStream()) {
                FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

                FirebaseApp app = FirebaseApp.initializeApp(options);
                log.info("✅ SDK Admin de Firebase initialisé avec succès pour le projet : {}", app.getName());
                return app;
            }
        } else {
            log.warn("Le SDK Admin de Firebase est déjà initialisé. Récupération de l'instance existante.");
            return FirebaseApp.getInstance();
        }
    }

    /**
     * Ce @Bean crée et fournit l'instance de FirebaseMessaging, qui est
     * utilisée pour envoyer des notifications. Il dépend du bean FirebaseApp.
     * @param firebaseApp L'instance initialisée par le bean ci-dessus.
     * @return L'instance de FirebaseMessaging.
     */
    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}