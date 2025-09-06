// Dans src/main/java/com/freelance/driver_backend/controller/NotificationConfigController.java

package com.freelance.driver_backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freelance.driver_backend.dto.external.NotificationRequest;
import com.freelance.driver_backend.model.DesignTemplate;
import com.freelance.driver_backend.model.EmailTemplate;
import com.freelance.driver_backend.model.FirebaseSetting;
import com.freelance.driver_backend.model.PushTemplate;
import com.freelance.driver_backend.model.SmtpSetting;
import com.freelance.driver_backend.repository.DesignTemplateRepository;
import com.freelance.driver_backend.repository.EmailTemplateRepository;
import com.freelance.driver_backend.repository.FirebaseSettingRepository;
import com.freelance.driver_backend.repository.PushTemplateRepository;
import com.freelance.driver_backend.repository.SmtpSettingRepository;
import com.freelance.driver_backend.service.external.NotificationService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/mock-notifications/{organizationId}")
@RequiredArgsConstructor
public class NotificationConfigController {

    private final SmtpSettingRepository smtpSettingRepository;
    private final DesignTemplateRepository designTemplateRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final NotificationService notificationService;
    private final FirebaseSettingRepository firebaseSettingRepository;
    private final PushTemplateRepository pushTemplateRepository;

    @PostMapping("/smtp-settings")
    public Mono<SmtpSetting> createSmtpSetting(@PathVariable UUID organizationId, @RequestBody SmtpSetting setting) {
        // Utilise l'ID fourni par le client, ou génère-en un si aucun n'est fourni
        if (setting.getId() == null) {
            setting.setId(UUID.randomUUID());
        }
        setting.setOrganizationId(organizationId);
        return smtpSettingRepository.save(setting);
    }

    @PostMapping("/design-templates")
    public Mono<DesignTemplate> createDesignTemplate(@PathVariable UUID organizationId, @RequestBody DesignTemplate template) {
        // Utilise l'ID fourni par le client, ou génère-en un si aucun n'est fourni
        if (template.getId() == null) {
            template.setId(UUID.randomUUID());
        }
        template.setOrganizationId(organizationId);
        return designTemplateRepository.save(template);
    }

    @PostMapping("/email-templates")
    public Mono<EmailTemplate> createEmailTemplate(@PathVariable UUID organizationId, @RequestBody EmailTemplate template) {
        // Utilise l'ID fourni par le client, ou génère-en un si aucun n'est fourni
        if (template.getId() == null) {
            template.setId(UUID.randomUUID());
        }
        template.setOrganizationId(organizationId);
        return emailTemplateRepository.save(template);
    }

     @PostMapping("/send-test-email")
    public Mono<ResponseEntity<String>> sendTestEmail(
            @PathVariable UUID organizationId,
            @RequestBody NotificationRequest request) {
        
        // Nous n'avons pas besoin du token ou de la clé publique ici car c'est un test local
        // qui appelle notre service "mock".
        return notificationService.sendEmailNotification(organizationId, request, "mock-token", "mock-key")
            .map(success -> {
                if (Boolean.TRUE.equals(success)) {
                    return ResponseEntity.ok("Test email sent successfully!");
                } else {
                    return ResponseEntity.status(500).body("Failed to send test email.");
                }
            })
            .onErrorResume(e -> Mono.just(ResponseEntity.status(500).body("Error: " + e.getMessage())));
    }


    @PostMapping("/firebase-settings")
    public Mono<FirebaseSetting> createFirebaseSetting(@PathVariable UUID organizationId, @RequestBody FirebaseSetting setting) {
        // Utilise l'ID fourni par le client, ou génère-en un si aucun n'est fourni
        if (setting.getId() == null) {
            setting.setId(UUID.randomUUID());
        }
        setting.setOrganizationId(organizationId);
        return firebaseSettingRepository.save(setting);
    }

    @PostMapping("/push-templates")
    public Mono<PushTemplate> createPushTemplate(@PathVariable UUID organizationId, @RequestBody PushTemplate template) {
        // Utilise l'ID fourni par le client, ou génère-en un si aucun n'est fourni
        if (template.getId() == null) {
            template.setId(UUID.randomUUID());
        }
        template.setOrganizationId(organizationId);
        return pushTemplateRepository.save(template);
    }
    
}