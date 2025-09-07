package com.freelance.driver_backend.initializer;

import com.freelance.driver_backend.model.DesignTemplate;
import com.freelance.driver_backend.model.EmailTemplate;
import com.freelance.driver_backend.model.FirebaseSetting;
import com.freelance.driver_backend.model.PushTemplate;
import com.freelance.driver_backend.model.SmtpSetting;
import com.freelance.driver_backend.model.mock.MockOrganisation;
import com.freelance.driver_backend.repository.DesignTemplateRepository;
import com.freelance.driver_backend.repository.EmailTemplateRepository;
import com.freelance.driver_backend.repository.FirebaseSettingRepository;
import com.freelance.driver_backend.repository.PushTemplateRepository;
import com.freelance.driver_backend.repository.SmtpSettingRepository;
import com.freelance.driver_backend.repository.mock.MockOrganisationRepository;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value; // Garder si des @Value sont nécessaires pour d'autres beans ici
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Component
@Profile({"dev-resource-mock", "dev-mock"}) // Active cette initialisation seulement en dev ou mock
@RequiredArgsConstructor
@Slf4j
public class ApplicationInitializer implements CommandLineRunner {

    private final Dotenv dotenv;
    // ... (injection des repositories et des @Value pour les paramètres mail) ...
    @Value("${spring.mail.host}")
    private String mailHost;
    @Value("${spring.mail.port}")
    private int mailPort;
    @Value("${spring.mail.username}")
    private String mailUsername;
    @Value("${spring.mail.password}")
    private String mailPassword;

    private final MockOrganisationRepository mockOrganisationRepository;
    private final SmtpSettingRepository smtpSettingRepository;
    private final FirebaseSettingRepository firebaseSettingRepository;
    private final DesignTemplateRepository designTemplateRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final PushTemplateRepository pushTemplateRepository;


    @Override
    public void run(String... args) {
        log.info("🚀 Démarrage du processus d'initialisation des templates et configs internes de l'application...");

        UUID sysOrgId = getUuidFromEnv("SYSTEM_ORGANIZATION_ID");
        UUID smtpSettingId = getUuidFromEnv("SMTP_SETTING_ID");
        UUID firebaseSettingId = getUuidFromEnv("FIREBASE_SETTING_ID");
        UUID designEmailOtpId = getUuidFromEnv("DESIGN_EMAIL_OTP_ID");
        UUID templateEmailOtpId = getUuidFromEnv("TEMPLATE_EMAIL_OTP_ID");
        UUID templatePushNewPlanningId = getUuidFromEnv("TEMPLATE_PUSH_NEW_PLANNING_ID");
        UUID templatePushNewAnnouncementId = getUuidFromEnv("TEMPLATE_PUSH_NEW_ANNOUNCEMENT_ID");
        UUID templatePushAnnouncementAcceptedId = getUuidFromEnv("TEMPLATE_PUSH_ANNOUNCEMENT_ACCEPTED_ID");
        
        log.info("ℹ️ Les configurations UUID pour les templates sont lues depuis les variables d'environnement/fichier .env.");

        // Étape 2: Insertion de l'organisation 'Système' (inchangée)
        log.info("🔷 ÉTAPE 2: Insertion de l'organisation 'Système' dans la base de données...");
        MockOrganisation systemOrg = new MockOrganisation();
        systemOrg.setOrganizationId(sysOrgId);
        systemOrg.setLongName("System Default");
        systemOrg.setDescription("Organisation par défaut pour les templates globaux");
        mockOrganisationRepository.save(systemOrg)
            .doOnSuccess(org -> log.info("✅ Organisation 'Système' insérée/mise à jour: {}", org.getLongName()))
            .doOnError(e -> log.error("❌ Échec de l'insertion de l'organisation système dans ScyllaDB: {}", e.getMessage()))
            .block();


        log.info("🔷 ÉTAPE 3: Configuration des templates de notification via les dépôts...");

        // 3.1 Création du Setting SMTP (inchangée)
        SmtpSetting smtpSetting = new SmtpSetting();
        smtpSetting.setId(smtpSettingId);
        smtpSetting.setOrganizationId(sysOrgId);
        smtpSetting.setHost(mailHost);
        smtpSetting.setPort(mailPort);
        smtpSetting.setEncryption("TLS");
        smtpSetting.setUsername(mailUsername);
        smtpSetting.setPassword(mailPassword);
        smtpSetting.setSenderEmail("no-reply@freelancedriver.com");
        smtpSetting.setSenderName("Freelance Driver App");

        smtpSettingRepository.save(smtpSetting)
            .doOnSuccess(s -> log.info("✅ Template SMTP créé/mis à jour."))
            .doOnError(e -> log.error("❌ Échec création/mise à jour SMTP: {}", e.getMessage()))
            .block();

        // 3.2 Création du Setting Firebase (inchangée)
        FirebaseSetting firebaseSetting = new FirebaseSetting();
        firebaseSetting.setId(firebaseSettingId);
        firebaseSetting.setOrganizationId(sysOrgId);
        firebaseSetting.setProjectId("freelance-driver-app"); 

        ClassPathResource resource = new ClassPathResource("firebase-service-account-key.json");
        try (InputStream serviceAccount = resource.getInputStream()) {
            String privateKeyJson = new String(serviceAccount.readAllBytes());
            privateKeyJson = privateKeyJson.replace("\"", "\\\"").replace("\n", "").replace("\r", "");
            firebaseSetting.setPrivateKey(privateKeyJson);
        } catch (IOException e) {
            log.error("❌ Erreur de lecture du fichier Firebase private key: {}", e.getMessage());
            throw new RuntimeException("Could not load Firebase service account key.", e);
        }

        firebaseSettingRepository.save(firebaseSetting)
            .doOnSuccess(fs -> log.info("✅ Template Firebase créé/mis à jour."))
            .doOnError(e -> log.error("❌ Échec création/mise à jour Firebase: {}", e.getMessage()))
            .block();

        // 3.3 Création du Design de l'email OTP
        DesignTemplate designOtp = new DesignTemplate();
        designOtp.setId(designEmailOtpId);
        designOtp.setOrganizationId(sysOrgId);
        designOtp.setTitle("Email OTP");
        designOtp.setSubject("Votre code de vérification : [[${otpCode}]]"); // Le sujet était déjà en inline expression, c'est bien.
        
        // C'est ICI la modification clé : Utiliser l'inline expression pour le HTML
        designOtp.setHtml("<!DOCTYPE html><html xmlns:th=\"http://www.thymeleaf.org\"><body><h1>Bonjour [[${firstName}]],</h1><p>Votre code de vérification est :</p><h2 style='color: #007AFF;'>[[${otpCode}]]</h2></body></html>");

        designTemplateRepository.save(designOtp)
            .doOnSuccess(dt -> log.info("✅ DesignTemplate (OTP) créé/mis à jour."))
            .doOnError(e -> log.error("❌ Échec création/mise à jour DesignTemplate (OTP): {}", e.getMessage()))
            .block();

        // ... (reste des créations de EmailTemplate et PushTemplates inchangées) ...
        EmailTemplate emailOtpTemplate = new EmailTemplate();
        emailOtpTemplate.setId(templateEmailOtpId);
        emailOtpTemplate.setOrganizationId(sysOrgId);
        emailOtpTemplate.setSettingId(smtpSettingId);
        emailOtpTemplate.setDesignTemplateId(designEmailOtpId);
        emailOtpTemplate.setTitle("Règle envoi OTP");

        emailTemplateRepository.save(emailOtpTemplate)
            .doOnSuccess(et -> log.info("✅ EmailTemplate (OTP) créé/mis à jour."))
            .doOnError(e -> log.error("❌ Échec création/mise à jour EmailTemplate (OTP): {}", e.getMessage()))
            .block();

        PushTemplate pushNewPlanning = new PushTemplate();
        pushNewPlanning.setId(templatePushNewPlanningId);
        pushNewPlanning.setOrganizationId(sysOrgId);
        pushNewPlanning.setSettingId(firebaseSettingId);
        pushNewPlanning.setTitle("Nouveau planning disponible !");
        pushNewPlanning.setBody("Le chauffeur {{driverName}} propose un trajet vers {{destination}} à {{cost}} FCFA.");
        pushTemplateRepository.save(pushNewPlanning)
            .doOnSuccess(pt -> log.info("✅ PushTemplate (New Planning) créé/mis à jour."))
            .doOnError(e -> log.error("❌ Échec création/mise à jour PushTemplate (New Planning): {}", e.getMessage()))
            .block();

        PushTemplate pushNewAnnouncement = new PushTemplate();
        pushNewAnnouncement.setId(templatePushNewAnnouncementId);
        pushNewAnnouncement.setOrganizationId(sysOrgId);
        pushNewAnnouncement.setSettingId(firebaseSettingId);
        pushNewAnnouncement.setTitle("Nouvelle course client !");
        pushNewAnnouncement.setBody("Un client a posté un nouveau trajet : {{tripTitle}}");
        pushTemplateRepository.save(pushNewAnnouncement)
            .doOnSuccess(pt -> log.info("✅ PushTemplate (New Announcement) créé/mis à jour."))
            .doOnError(e -> log.error("❌ Échec création/mise à jour PushTemplate (New Announcement): {}", e.getMessage()))
            .block();

        PushTemplate pushAnnouncementAccepted = new PushTemplate();
        pushAnnouncementAccepted.setId(templatePushAnnouncementAcceptedId);
        pushAnnouncementAccepted.setOrganizationId(sysOrgId);
        pushAnnouncementAccepted.setSettingId(firebaseSettingId);
        pushAnnouncementAccepted.setTitle("Votre course a un chauffeur !");
        pushAnnouncementAccepted.setBody("Le chauffeur {{driverName}} a accepté votre trajet \\\"{{tripTitle}}\\\". Appuyez pour voir son profil.");
        pushTemplateRepository.save(pushAnnouncementAccepted)
            .doOnSuccess(pt -> log.info("✅ PushTemplate (Announcement Accepted) créé/mis à jour."))
            .doOnError(e -> log.error("❌ Échec création/mise à jour PushTemplate (Announcement Accepted): {}", e.getMessage()))
            .block();

        log.info("\n🎉 --- INITIALISATION INTERNE TERMINÉE ---");
    }

    private UUID getUuidFromEnv(String key) {
        String value = dotenv.get(key);
        if (value == null || value.isEmpty()) {
            log.error("❌ Variable d'environnement UUID '{}' non trouvée ou vide dans .env ou les variables système. L'initialisation va échouer.", key);
            throw new IllegalStateException("Missing or empty UUID environment variable: " + key);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.error("❌ La valeur '{}' pour la variable d'environnement '{}' n'est pas un UUID valide. L'initialisation va échouer.", value, key);
            throw new IllegalStateException("Invalid UUID format for environment variable: " + key, e);
        }
    }
}