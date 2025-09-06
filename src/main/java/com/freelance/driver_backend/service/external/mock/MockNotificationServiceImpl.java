package com.freelance.driver_backend.service.external.mock;

import com.freelance.driver_backend.dto.external.NotificationRequest;
import com.freelance.driver_backend.repository.DesignTemplateRepository;
import com.freelance.driver_backend.repository.EmailTemplateRepository;
import com.freelance.driver_backend.repository.PushTemplateRepository;
import com.freelance.driver_backend.repository.SmtpSettingRepository;
import com.freelance.driver_backend.service.external.NotificationService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

@Service
@Profile({"dev-mock", "dev-resource-mock"})
@RequiredArgsConstructor
@Slf4j
public class MockNotificationServiceImpl implements NotificationService {

    private final EmailTemplateRepository emailTemplateRepository;
    private final DesignTemplateRepository designTemplateRepository;
    private final SmtpSettingRepository smtpSettingRepository;
    private final JavaMailSender javaMailSender;
    private final SpringTemplateEngine thymeleafTemplateEngine;
    private final PushTemplateRepository pushTemplateRepository;

    
        
    @Override
    public Mono<Boolean> sendEmailNotification(UUID organisationId, NotificationRequest request, String userBearerToken, String publicKey) {
        log.warn("==================== [LOCAL EMAIL SERVICE - REAL SEND] ====================");
        
        if (request.getRecipients() == null || request.getRecipients().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Recipients list cannot be empty."));
        }

        return emailTemplateRepository.findById(request.getTemplateId())
            .flatMap(emailTemplate -> 
                Mono.zip(
                    designTemplateRepository.findById(emailTemplate.getDesignTemplateId()),
                    smtpSettingRepository.findById(emailTemplate.getSettingId())
                )
            )
            .flatMap(tuple -> {
                var designTemplate = tuple.getT1();
                var smtpSetting = tuple.getT2();
                Context thymeleafContext = new Context();
                if (request.getMetadata() != null) {
                    request.getMetadata().forEach(thymeleafContext::setVariable);
                }
                String finalSubject = thymeleafTemplateEngine.process(designTemplate.getSubject(), thymeleafContext);
                String finalHtmlBody = thymeleafTemplateEngine.process(designTemplate.getHtml(), thymeleafContext);

                // ==============================================================================
                //                         LA CORRECTION EST ICI
                // ==============================================================================
                // On utilise Mono.fromCallable pour mieux gérer les exceptions bloquantes
                return Mono.fromCallable(() -> {
                    try {
                        MimeMessage message = javaMailSender.createMimeMessage();
                        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
                        helper.setTo(request.getRecipients().toArray(new String[0]));
                        helper.setSubject(finalSubject);
                        helper.setText(finalHtmlBody, true);
                        helper.setFrom(smtpSetting.getSenderEmail(), smtpSetting.getSenderName());

                        javaMailSender.send(message);
                        log.warn(">>> REAL EMAIL SENT via LOCAL Service to {} <<<", request.getRecipients());
                        return true; // Succès
                    } catch (Exception e) {
                        // On logue l'erreur de manière très visible
                        log.error("==================== ERREUR SMTP ====================");
                        log.error("Échec de l'envoi de l'email. Cause: {}", e.getMessage());
                        log.error("Vérifiez vos identifiants dans application.properties et le mot de passe d'application Google.");
                        log.error("=====================================================");
                        // On propage l'exception pour que le Mono échoue
                        throw new RuntimeException("Failed to send email", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic()) // On exécute sur un thread dédié
                .onErrorReturn(false); // Si une exception est levée, on retourne false
                // ==============================================================================
            })
            .defaultIfEmpty(false);
    }

    @Override
    public Mono<Boolean> sendPushNotification(UUID organisationId, NotificationRequest request, String userBearerToken, String publicKey) {
        log.warn("==================== [LOCAL PUSH SERVICE - REAL SEND] ====================");
        
        return pushTemplateRepository.findById(request.getTemplateId())
            .flatMap(pushTemplate -> {
                // Remplacer les variables dans le titre et le corps
                String finalTitle = replaceMetadata(pushTemplate.getTitle(), request.getMetadata());
                String finalBody = replaceMetadata(pushTemplate.getBody(), request.getMetadata());

                // Construire les messages pour Firebase
                List<Message> messages = request.getRecipients().stream()
                    .map(token -> Message.builder()
                        .setNotification(Notification.builder()
                            .setTitle(finalTitle)
                            .setBody(finalBody)
                            .build())
                        .setToken(token) // Le token de l'appareil
                        .build())
                    .toList();

                // Publier l'envoi sur un thread non-bloquant
                return Mono.fromCallable(() -> {
                        BatchResponse response = FirebaseMessaging.getInstance().sendAll(messages);
                        log.warn(">>> REAL PUSH NOTIFICATIONS SENT via LOCAL Service. Success: {}, Failure: {} <<<", 
                                 response.getSuccessCount(), response.getFailureCount());
                        return response.getFailureCount() == 0;
                    })
                    .subscribeOn(Schedulers.boundedElastic());
            })
            .defaultIfEmpty(false)
            .doOnError(e -> log.error("Error during local push sending process", e))
            .onErrorReturn(false);
    }
    // Petite méthode utilitaire pour remplacer les variables
    private String replaceMetadata(String text, Map<String, String> metadata) {
        if (text == null || metadata == null) return text;
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            text = text.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return text;
    }


        



}