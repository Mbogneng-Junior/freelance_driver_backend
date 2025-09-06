// src/main/java/com/freelance/driver_backend/service/SocketIOService.java

package com.freelance.driver_backend.service;

import com.corundumstudio.socketio.SocketIOServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SocketIOService {

    private final SocketIOServer server;

    // Cette méthode est appelée automatiquement par Spring après la création du bean.
    @PostConstruct
    private void startServer() {
        server.start();
        log.info("✅ Serveur Socket.IO démarré sur le port {}.", server.getConfiguration().getPort());
    }

    // Cette méthode est appelée lorsque l'application s'arrête.
    @PreDestroy
    private void stopServer() {
        server.stop();
        log.info("🛑 Serveur Socket.IO arrêté.");
    }
    
    // Vous pouvez ajouter ici des méthodes pour envoyer des événements si vous le souhaitez,
    // mais il est souvent plus simple d'injecter directement le SocketIOServer dans les contrôleurs.
}