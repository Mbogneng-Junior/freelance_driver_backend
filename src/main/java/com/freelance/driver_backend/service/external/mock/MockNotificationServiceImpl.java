package com.freelance.driver_backend.service.external.mock;

import com.freelance.driver_backend.dto.external.NotificationRequest;
import com.freelance.driver_backend.repository.DesignTemplateRepository;
import com.freelance.driver_backend.repository.EmailTemplateRepository;
import com.freelance.driver_backend.repository.PushTemplateRepository;
import com.freelance.driver_backend.repository.SmtpSettingRepository;
import com.freelance.driver_backend.service.external.NotificationService;
import com.freelance.driver_backend.service.FcmHttpClient;
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
import java.util.stream.Collectors;

import com.google.firebase.FirebaseApp;

@Service

@RequiredArgsConstructor
@Slf4j
public class MockNotificationServiceImpl implements NotificationService {

    private final EmailTemplateRepository emailTemplateRepository;
    private final DesignTemplateRepository designTemplateRepository;
    private final SmtpSettingRepository smtpSettingRepository;
    private final JavaMailSender javaMailSender;
    private final SpringTemplateEngine thymeleafTemplateEngine;
    private final PushTemplateRepository pushTemplateRepository;
    private final FirebaseApp firebaseApp; 
    private final FcmHttpClient fcmHttpClient;

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
                        return true;
                    } catch (Exception e) {
                        log.error("==================== ERREUR SMTP ====================");
                        log.error("Échec de l'envoi de l'email. Cause: {}", e.getMessage());
                        log.error("Vérifiez vos identifiants dans application.properties et le mot de passe d'application Google.");
                        log.error("=====================================================");
                        throw new RuntimeException("Failed to send email", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(false);
            })
            .defaultIfEmpty(false);
    }

    @Override
    public Mono<Boolean> sendPushNotification(UUID organisationId, NotificationRequest request, String userBearerToken, String publicKey) {
        log.warn("==================== [LOCAL PUSH SERVICE - REAL SEND VIA FCM HTTP CLIENT] ====================");
        
        if (request.getRecipients() == null || request.getRecipients().isEmpty()) {
            log.warn("[LOCAL PUSH SERVICE] Aucuns destinataires pour la notification push.");
            return Mono.just(false);
        }

        return pushTemplateRepository.findById(request.getTemplateId())
            .flatMap(pushTemplate -> {
                String finalTitle = replaceMetadata(pushTemplate.getTitle(), request.getMetadata());
                String finalBody = replaceMetadata(pushTemplate.getBody(), request.getMetadata());

                // --- MODIFICATION ICI : Appeler FcmHttpClient avec le dataPayload ---
                return fcmHttpClient.sendNotifications(request.getRecipients(), finalTitle, finalBody, request.getData()) // <-- AJOUT DE request.getData()
                           .then(Mono.just(true)) 
                           .onErrorResume(e -> { 
                               log.error("❌ Erreur lors de l'envoi de notifications push via FcmHttpClient: {}", e.getMessage());
                               return Mono.just(false);
                           });
            })
            .defaultIfEmpty(false)
            .doOnError(e -> log.error("Error during local push sending process (outside flatMap)", e))
            .onErrorReturn(false);
    }

    private String replaceMetadata(String text, Map<String, String> metadata) {
        if (text == null || metadata == null) return text;
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            text = text.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return text;
    }
}