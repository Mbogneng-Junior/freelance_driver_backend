package com.freelance.driver_backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging; 
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value; 

import java.io.IOException;
import java.io.InputStream;

@Configuration
@Slf4j
public class FirebaseConfig {

    // NOUVELLE INJECTION : ID du projet Firebase pour les notifications
    @Value("${freelancedriver.firebase.project-id}") 
    private String firebaseProjectId;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            log.info("Initialisation du SDK Admin de Firebase...");
            ClassPathResource resource = new ClassPathResource("firebase-service-account-key.json");
            if (!resource.exists()) {
                log.error("Le fichier 'firebase-service-account-key.json' est introuvable dans src/main/resources/");
                throw new IOException("Le fichier firebase-service-account-key.json est introuvable.");
            }

            try (InputStream serviceAccount = resource.getInputStream()) {
                FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setProjectId(firebaseProjectId) // <-- UTILISE LE NOUVEAU NOM
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

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}