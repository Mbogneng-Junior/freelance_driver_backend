// src/main/java/com/freelance/driver_backend/config/SocketIOConfig.java

package com.freelance.driver_backend.config;

import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SocketIOConfig {

    @Value("${socketio.server.host}")
    private String host;

    @Value("${socketio.server.port}")
    private Integer port;

    @Bean
    public SocketIOServer socketIOServer() {
        com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();
        config.setHostname(host);
        config.setPort(port);

        // Cette ligne est cruciale pour autoriser les connexions depuis votre application mobile et le web.
        config.setOrigin("*");

        return new SocketIOServer(config);
    }
}