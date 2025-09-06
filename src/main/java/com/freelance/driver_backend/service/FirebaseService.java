package com.freelance.driver_backend.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@Slf4j
public class FirebaseService {

    @PostConstruct // Cette méthode sera exécutée au démarrage de Spring
    public void initialize() {
        try {
            // Assurez-vous que votre fichier de clé de service Firebase
            // est placé dans src/main/resources
            ClassPathResource resource = new ClassPathResource("firebase-service-account-key.example");
            InputStream serviceAccount = resource.getInputStream();

            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK a été initialisé avec succès.");
            } else {
                log.warn("Firebase Admin SDK est déjà initialisé.");
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'initialisation du Firebase Admin SDK. Les notifications push ne fonctionneront pas.", e);
        }
    }
}