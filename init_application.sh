#!/bin/bash

# ==============================================================================
#           SCRIPT D'INITIALISATION DE L'ENVIRONNEMENT FREELANCE DRIVER
# Ce script configure une organisation système par défaut et tous les templates
# de notification nécessaires au fonctionnement de l'application.
#
# USAGE :
# 1. Assurez-vous que vos conteneurs Docker (ScyllaDB, MinIO) sont en cours d'exécution.
# 2. Assurez-vous que votre backend Spring Boot est en cours d'exécution sur le port 8080.
# 3. Rendez ce script exécutable une fois avec : chmod +x init_application.sh
# 4. Exécutez ce script depuis la racine de votre projet : ./init_application.sh
# ==============================================================================

echo "🚀 Démarrage du script d'initialisation de l'application..."

# --- CONFIGURATION PRINCIPALE ---
# On utilise des UUIDs constants et prédéfinis.
# Ces IDs DOIVENT correspondre à ceux définis dans le code Java (ex: NotificationTriggerService).
SYS_ORG_ID="73ba467d-9b2e-481a-827e-edbddc4f775d"
SMTP_SETTING_ID="bf1cc922-3e8c-4dd5-b6fb-7a69c0ba0250"
FIREBASE_SETTING_ID="c3389947-110e-4f79-925c-ce19072a6e2a"

DESIGN_EMAIL_OTP_ID="d11ef854-3b00-44aa-b4ad-c889806664c4"
TEMPLATE_EMAIL_OTP_ID="99f0fa9f-80bd-4e54-8385-a3e0dee99770"

TEMPLATE_PUSH_NEW_PLANNING_ID="c4af6e04-2dd8-4e58-9895-140cf3f6fa96"
TEMPLATE_PUSH_NEW_ANNOUNCEMENT_ID="d77bd848-e94c-4a6d-985c-e1d7688928d6"
TEMPLATE_PUSH_ANNOUNCEMENT_ACCEPTED_ID="1a7b8f5c-2d3e-4f6a-8b9c-0a1b2c3d4e5f"

API_BASE_URL="http://localhost:8080"
ENV_FILE=".env"

# --- FONCTIONS UTILITAIRES ---
log_step() { echo -e "\n🔷 --- ÉTAPE $1: $2 ---"; }
# Fonction pour afficher la réponse JSON de manière lisible (nécessite l'outil 'jq')
log_response() { 
    if command -v jq &> /dev/null; then
        echo -e "  ➡️  Réponse:\n$(echo "$1" | jq .)\n"
    else
        echo -e "  ➡️  Réponse (brute):\n$1\n"
    fi
}
log_success() { echo -e "✅ $1"; }
log_error() { echo -e "❌ ERREUR: $1"; exit 1; }

# ==============================================================================
#                         DÉBUT DU SCRIPT
# ==============================================================================

log_step 1 "Création du fichier de configuration .env..."
rm -f $ENV_FILE
touch $ENV_FILE
{
    echo "# Ce fichier est auto-généré par init_application.sh"
    echo "SYSTEM_ORGANIZATION_ID=$SYS_ORG_ID"
    echo "SMTP_SETTING_ID=$SMTP_SETTING_ID"
    echo "FIREBASE_SETTING_ID=$FIREBASE_SETTING_ID"
    echo "DESIGN_EMAIL_OTP_ID=$DESIGN_EMAIL_OTP_ID"
    echo "TEMPLATE_EMAIL_OTP_ID=$TEMPLATE_EMAIL_OTP_ID"
    echo "TEMPLATE_PUSH_NEW_PLANNING_ID=$TEMPLATE_PUSH_NEW_PLANNING_ID"
    echo "TEMPLATE_PUSH_NEW_ANNOUNCEMENT_ID=$TEMPLATE_PUSH_NEW_ANNOUNCEMENT_ID"
    echo "TEMPLATE_PUSH_ANNOUNCEMENT_ACCEPTED_ID=$TEMPLATE_PUSH_ANNOUNCEMENT_ACCEPTED_ID"
} >> $ENV_FILE
log_success "Fichier .env créé avec toutes les IDs de configuration."

# --- INSERTION DIRECTE EN BASE DE DONNÉES ---
log_step 2 "Insertion de l'organisation 'Système' dans la base de données..."
docker exec -it scylla-node-dev cqlsh -e "INSERT INTO freelancebd.mock_organisations (organization_id, long_name, description) VALUES ($SYS_ORG_ID, 'System Default', 'Organisation par défaut pour les templates globaux');"
if [ $? -ne 0 ]; then log_error "Échec de l'insertion de l'organisation système dans ScyllaDB."; fi
log_success "Organisation 'Système' insérée/mise à jour dans la base de données."

# --- CRÉATION DES TEMPLATES VIA L'API ---
log_step 3 "Configuration des templates de notification via l'API..."

# 3.1 Création du Setting SMTP
SMTP_PAYLOAD="{ \"id\": \"$SMTP_SETTING_ID\", \"host\": \"smtp.gmail.com\", \"port\": 587, \"encryption\": \"TLS\", \"username\": \"mbognengj@gmail.com\", \"password\": \"VOTRE_MOT_DE_PASSE_APPLICATION_GMAIL\", \"sender_email\": \"no-reply@freelancedriver.com\", \"sender_name\": \"Freelance Driver App\" }"
echo "  Requête POST vers /smtp-settings"
SMTP_RESPONSE=$(curl -s -X POST "${API_BASE_URL}/api/mock-notifications/${SYS_ORG_ID}/smtp-settings" -H "Content-Type: application/json" -d "$SMTP_PAYLOAD")
log_response "$SMTP_RESPONSE"
log_success "Template SMTP créé/mis à jour."

# 3.2 Création du Setting Firebase
FIREBASE_KEY_JSON=$(cat src/main/resources/firebase-service-account-key.json | tr -d '\n\r' | sed 's/"/\\"/g')
FIREBASE_PAYLOAD="{ \"id\": \"$FIREBASE_SETTING_ID\", \"projectId\": \"freelance-driver-app\", \"privateKey\": \"$FIREBASE_KEY_JSON\" }"
echo "  Requête POST vers /firebase-settings"
FIREBASE_RESPONSE=$(curl -s -X POST "${API_BASE_URL}/api/mock-notifications/${SYS_ORG_ID}/firebase-settings" -H "Content-Type: application/json" -d "$FIREBASE_PAYLOAD")
log_response "$FIREBASE_RESPONSE"
log_success "Template Firebase créé/mis à jour."

# 3.3 Création du Design de l'email OTP
DESIGN_OTP_PAYLOAD="{ \"id\": \"$DESIGN_EMAIL_OTP_ID\", \"title\": \"Email OTP\", \"subject\": \"Votre code de vérification : [[\${otpCode}]]\", \"html\": \"<!DOCTYPE html><html xmlns:th=\\\"http://www.thymeleaf.org\\\"><body><h1>Bonjour <span th:text=\\\"\${firstName}\\\"></span>,</h1><p>Votre code de vérification est :</p><h2 style='color: #007AFF;' th:text=\\\"\${otpCode}\\\"></h2></body></html>\" }"
echo "  Requête POST vers /design-templates (OTP)"
DESIGN_OTP_RESPONSE=$(curl -s -X POST "${API_BASE_URL}/api/mock-notifications/${SYS_ORG_ID}/design-templates" -H "Content-Type: application/json" -d "$DESIGN_OTP_PAYLOAD")
log_response "$DESIGN_OTP_RESPONSE"
log_success "DesignTemplate (OTP) créé/mis à jour."

# 3.4 Création de l'EmailTemplate (Règle d'envoi OTP)
EMAIL_OTP_PAYLOAD="{ \"id\": \"$TEMPLATE_EMAIL_OTP_ID\", \"setting_id\": \"$SMTP_SETTING_ID\", \"design_template_id\": \"$DESIGN_EMAIL_OTP_ID\", \"title\": \"Règle envoi OTP\" }"
echo "  Requête POST vers /email-templates (OTP)"
EMAIL_OTP_RESPONSE=$(curl -s -X POST "${API_BASE_URL}/api/mock-notifications/${SYS_ORG_ID}/email-templates" -H "Content-Type: application/json" -d "$EMAIL_OTP_PAYLOAD")
log_response "$EMAIL_OTP_RESPONSE"
log_success "EmailTemplate (OTP) créé/mis à jour."

# 3.5 Création des PushTemplates
PUSH_NEW_PLANNING_PAYLOAD="{ \"id\": \"$TEMPLATE_PUSH_NEW_PLANNING_ID\", \"settingId\": \"$FIREBASE_SETTING_ID\", \"title\": \"Nouveau planning disponible !\", \"body\": \"Le chauffeur {{driverName}} propose un trajet vers {{destination}} à {{cost}} FCFA.\" }"
echo "  Requête POST vers /push-templates (New Planning)"
PUSH_NEW_PLANNING_RESPONSE=$(curl -s -X POST "${API_BASE_URL}/api/mock-notifications/${SYS_ORG_ID}/push-templates" -H "Content-Type: application/json" -d "$PUSH_NEW_PLANNING_PAYLOAD")
log_response "$PUSH_NEW_PLANNING_RESPONSE"
log_success "PushTemplate (New Planning) créé/mis à jour."

PUSH_NEW_ANNOUNCEMENT_PAYLOAD="{ \"id\": \"$TEMPLATE_PUSH_NEW_ANNOUNCEMENT_ID\", \"settingId\": \"$FIREBASE_SETTING_ID\", \"title\": \"Nouvelle course client !\", \"body\": \"Un client a posté un nouveau trajet : {{tripTitle}}\" }"
echo "  Requête POST vers /push-templates (New Announcement)"
PUSH_NEW_ANNOUNCEMENT_RESPONSE=$(curl -s -X POST "${API_BASE_URL}/api/mock-notifications/${SYS_ORG_ID}/push-templates" -H "Content-Type: application/json" -d "$PUSH_NEW_ANNOUNCEMENT_PAYLOAD")
log_response "$PUSH_NEW_ANNOUNCEMENT_RESPONSE"
log_success "PushTemplate (New Announcement) créé/mis à jour."

PUSH_ACCEPTED_PAYLOAD="{ \"id\": \"$TEMPLATE_PUSH_ANNOUNCEMENT_ACCEPTED_ID\", \"settingId\": \"$FIREBASE_SETTING_ID\", \"title\": \"Votre course a un chauffeur !\", \"body\": \"Le chauffeur {{driverName}} a accepté votre trajet \\\"{{tripTitle}}\\\". Appuyez pour voir son profil.\" }"
echo "  Requête POST vers /push-templates (Announcement Accepted)"
PUSH_ACCEPTED_RESPONSE=$(curl -s -X POST "${API_BASE_URL}/api/mock-notifications/${SYS_ORG_ID}/push-templates" -H "Content-Type: application/json" -d "$PUSH_ACCEPTED_PAYLOAD")
log_response "$PUSH_ACCEPTED_RESPONSE"
log_success "PushTemplate (Announcement Accepted) créé/mis à jour."


echo -e "\n🎉 --- INITIALISATION TERMINÉE ---"
echo "Le fichier '$ENV_FILE' a été créé/mis à jour avec succès."
echo "Veuillez REDÉMARRER votre backend Java pour qu'il prenne en compte les nouvelles variables d'environnement."