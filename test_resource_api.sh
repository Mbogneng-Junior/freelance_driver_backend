
# ===============================================
# IDENTIFICATION DE L'APPLICATION
# ===============================================
spring.application.name=driver-backend

# ===============================================
# CONFIGURATION DU SERVEUR WEB
# ===============================================
server.port=8080

# ===============================================
# PROFIL ACTIF
# Activer le mode hybride: services réels sauf pour les ressources.
# ===============================================
spring.profiles.active=dev-resource-mock

# ===============================================
# CONFIGURATION DES MICROSERVICES EXTERNES
# ===============================================
microservices.auth-service.url=https://gateway.yowyob.com/auth-service
microservices.organisation-service.url=https://gateway.yowyob.com/organization-service
microservices.chat-service.url=http://88.198.150.195:8613
microservices.notification-service.url=https://gateway.yowyob.com/notification-service
microservices.resource-service.url=https://gateway.yowyob.com/resource-service
microservices.media-service.url=https://gateway.yowyob.com/media-service

# ===============================================
# CONFIGURATION SPÉCIFIQUE À FREELANCE DRIVER
# ===============================================
freelancedriver.api.public-key=api_1752647119025_8d6e5340.ieGsWPxnE9eY0xBQ7n8htlTiQP3n4009
freelancedriver.chat.project-id=25cf4dfd-e847-4b59-91c7-034aa5afc200

# ===============================================
# CONFIGURATION OAUTH2 CLIENT CREDENTIALS
# ===============================================
freelancedriver.oauth2.client-id=test-client
freelancedriver.oauth2.client-secret=secret
freelancedriver.oauth2.token-url=${microservices.auth-service.url}/oauth/token

# ===============================================
# CONFIGURATION DU SERVEUR DE RESSOURCES OAUTH2
# ===============================================
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://gateway.yowyob.com/auth-service/openid/.well-known/jwks.json

# ===============================================
# CONFIGURATION DES DEVTOOLS
# ===============================================
spring.devtools.restart.enabled=true

# ===============================================
# CONFIGURATION AVANCÉE DU DRIVER CASSANDRA
# ===============================================
spring.data.cassandra.request.timeout=20s
spring.data.cassandra.connection.connect-timeout=20s
spring.data.cassandra.connection.init-query-timeout=20s

# ===============================================
# CONFIGURATION POUR L'ENVOI D'EMAIL (POUR LE MOCK)
# ===============================================
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=mbognengj@gmail.com
spring.mail.password=wxupcsgfxdwotdeu
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.debug=true

# ===============================================
# CONFIGURATION DU STOCKAGE DE FICHIERS (MinIO)
# ===============================================
minio.endpoint=http://192.168.43.4:9000
minio.access-key=junioradmin
minio.secret-key=YourStrongPassword2025
minio.bucket-name=freelance-driver





















#!/bin/bash

# ==============================================================================
# SCRIPT DE TEST POUR L'API DES RESSOURCES (PRODUITS/SERVICES)
# ==============================================================================

# --- CONFIGURATION ---
# MODIFIEZ CES 3 VALEURS AVEC VOS PROPRES INFORMATIONS
export VOTRE_TOKEN_UTILISATEUR="eyJraWQiOiIzODdjOWEyOC0zODBlLTRmZWUtYTVjOC0wYTEwMzE2ZTAwNGYiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJodHRwOi8vODguMTk4LjE1MC4xOTU6ODA4OC9vcGVuaWQiLCJzdWIiOiJub3RpZmljYXRpb24iLCJleHAiOjE3NTQwNDg3MTcsImlhdCI6MTc1Mzk2MjMxNywidXNlciI6eyJpZCI6ImMxOTY4NWYwLTZlMDMtMTFmMC04ODk3LTI1MTE0NDZkYzMxYSIsImZpcnN0TmFtZSI6InN0cmluZyIsImxhc3ROYW1lIjoic3RyaW5nIiwidXNlcm5hbWUiOiJub3RpZmljYXRpb24iLCJlbWFpbCI6Im5vdGlmaWNhdGlvbkBnbWFpbC5jb20iLCJlbWFpbFZlcmlmaWVkIjpmYWxzZSwicGhvbmVOdW1iZXIiOiI2NzY3Njc2NzYiLCJwaG9uZU51bWJlclZlcmlmaWVkIjpmYWxzZX0sImF1dGhvcml0aWVzIjpbIlVTRVIiXX0.A96-pnByIgPWp-awnxWVD-7w-yWlLP6mdYRSve-tVIbEYpXpn04dHYgY-XWTyjotvpzKjALbIPbcfK5l6tbr1lBmOWI2Nr_mngFF_kwuRg7x2i-sQV-8c1AYTFLI6GCpyauI3dZAEvMxZtsc61gnL2Y4XSKJ1Cyx14VnvP9wFmwxL3KoJGAoRQxsZB6hr9ZSyOCNUA1dX1-FuHx3m09NuwBuh2xtiEwdTo72PVkMb8_asVuwFw92WoUZOeRrNvmOYMoxwMOe0TSxpwQofzNbQcIQjE-Sr3IbTalo1sdGnjvdgr6Ek2r7_C2_wlRvapwPgPZ85_b_fRfX0chyCbnsXQ"
export VOTRE_ORGANIZATION_ID="18b38b0d-2421-428e-a7f3-9710ac260687"
export VOTRE_CLE_PUBLIQUE="api_1752647119025_8d6e5340.ieGsWPxnE9eY0xBQ7n8htlTiQP3n4009"
# --- FIN DE LA CONFIGURATION ---


# --- VARIABLES INTERNES (NE PAS MODIFIER) ---
API_BASE_URL="https://gateway.yowyob.com/resource-service"
# On va tester plusieurs endpoints possibles
ENDPOINTS_A_TESTER=("/services" "/products" "/items" "/resources")
RESPONSE_FILE=$(mktemp) # Fichier temporaire pour stocker la réponse de l'API

# --- FONCTIONS UTILITAIRES ---
step() {
    echo -e "\n\e[1;34m--- $1 ---\e[0m"
}

check_success() {
    echo -e "\e[32m✅ $1\e[0m"
}

check_failure() {
    echo -e "\e[1;31m❌ $1\e[0m"
}

# --- DÉBUT DU SCRIPT ---

# Vérification des prérequis
if [[ "$VOTRE_TOKEN_UTILISATEUR" == "collez_votre_token_utilisateur_ici" || "$VOTRE_ORGANIZATION_ID" == "collez_votre_id_organisation_ici" || "$VOTRE_CLE_PUBLIQUE" == "collez_votre_clé_publique_ici" ]]; then
    check_failure "ERREUR : Veuillez modifier les variables de configuration en haut du script."
    exit 1
fi

# Boucle pour tester chaque endpoint possible
for endpoint in "${ENDPOINTS_A_TESTER[@]}"; do
    
    step "TENTATIVE DE CRÉATION SUR L'ENDPOINT: ${endpoint}"

    # Construction de l'URL complète
    FULL_URL="${API_BASE_URL}/${VOTRE_ORGANIZATION_ID}${endpoint}"
    echo "URL de la requête : ${FULL_URL}"

    # Corps de la requête JSON
    JSON_PAYLOAD='{
      "name": "Planning Test via Script SH",
      "category_id": "ba75b2c0-30a8-11f0-a5b5-bb7d33c83c13",
      "brand_id": null,
      "unit_id": null,
      "base_price": 25000,
      "state": "AVAILABLE",
      "start_date": "2025-09-10T08:00:00Z",
      "end_date": "2025-09-10T18:00:00Z",
      "short_description": "Trajet Bafoussam - Dschang",
      "long_description": "Véhicule 4x4 climatisé, 3 places disponibles, bagages légers uniquement.",
      "metadata": {
        "payment_option": "fixed",
        "departure_location": "Bafoussam"
      }
    }'

    echo "Payload JSON envoyé :"
    echo "${JSON_PAYLOAD}" | jq . # Affiche le JSON formaté (nécessite l'outil 'jq')
    
    # Exécution de la commande cURL
    # -s : silencieux (pas de barre de progression)
    # -w "%{http_code}" : écrit le code de statut HTTP à la fin
    # -o ${RESPONSE_FILE} : écrit le corps de la réponse dans notre fichier temporaire
    HTTP_STATUS=$(curl -s -w "%{http_code}" -o ${RESPONSE_FILE} -X POST \
      "${FULL_URL}" \
      -H "Authorization: Bearer ${VOTRE_TOKEN_UTILISATEUR}" \
      -H "Public-Key: ${VOTRE_CLE_PUBLIQUE}" \
      -H "Content-Type: application/json" \
      -d "${JSON_PAYLOAD}")

    echo ""
    step "ANALYSE DE LA RÉPONSE POUR ${endpoint}"
    echo "Code de statut HTTP reçu : ${HTTP_STATUS}"
    
    echo "Corps de la réponse :"
    # Utilise 'jq' pour formater joliment le JSON si 'jq' est installé, sinon affiche le texte brut.
    if command -v jq &> /dev/null; then
        cat "${RESPONSE_FILE}" | jq .
    else
        cat "${RESPONSE_FILE}"
    fi
    echo "" # Ligne vide pour la lisibilité

    # Interprétation du résultat
    case ${HTTP_STATUS} in
        200|201)
            check_success "SUCCÈS ! L'endpoint '${endpoint}' a fonctionné. La ressource a été créée."
            echo "Vous pouvez maintenant adapter votre code Java pour utiliser cet endpoint et cette structure JSON."
            rm ${RESPONSE_FILE} # Nettoyage du fichier temporaire
            exit 0 # Arrête le script car nous avons trouvé le bon endpoint
            ;;
        400)
            check_failure "ERREUR 400 (Bad Request) : L'endpoint '${endpoint}' est PROBABLEMENT le bon, mais le format du JSON est incorrect."
            echo "Analysez le 'Corps de la réponse' ci-dessus pour voir quels champs sont manquants ou invalides, puis ajustez le JSON_PAYLOAD dans le script."
            ;;
        404)
            check_failure "ERREUR 404 (Not Found) : L'endpoint '${endpoint}' n'existe pas. Passage au suivant..."
            ;;
        401|403)
            check_failure "ERREUR ${HTTP_STATUS} (Unauthorized/Forbidden) : Votre token est probablement invalide ou expiré, ou votre clé publique est incorrecte."
            echo "Veuillez vérifier les variables de configuration en haut du script."
            ;;
        *)
            check_failure "ERREUR INCONNUE (${HTTP_STATUS}) : Une erreur inattendue est survenue."
            ;;
    esac
done

# Si on arrive ici, aucun endpoint n'a fonctionné avec succès
check_failure "FIN DES TESTS : Aucun des endpoints testés n'a permis de créer une ressource avec succès."
echo "Veuillez vérifier la documentation de l'API ou contacter l'équipe backend pour connaître le bon endpoint et le format attendu."

# Nettoyage final
rm ${RESPONSE_FILE}



curl -X 'POST' \
  'https://gateway.yowyob.com/notification-service/organizations/d4a0c630-6c5d-11f0-b56e-55aad7a60dfa/smtp-settings' \
  -H 'accept: */*' \
  -H 'Authorization: Bearer eyJraWQiOiIzODdjOWEyOC0zODBlLTRmZWUtYTVjOC0wYTEwMzE2ZTAwNGYiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJodHRwOi8vODguMTk4LjE1MC4xOTU6ODA4OC9vcGVuaWQiLCJzdWIiOiJ0ZXRzaWVib3UiLCJleHAiOjE3NTM5NjA3NzUsImlhdCI6MTc1Mzg3NDM3NSwidXNlciI6eyJpZCI6ImQzZmM0ZDAwLTZjNTYtMTFmMC1iZWZmLWU1OTM3NjA1MjczYyIsImZpcnN0TmFtZSI6InN0cmluZyIsImxhc3ROYW1lIjoic3RyaW5nIiwidXNlcm5hbWUiOiJ0ZXRzaWVib3UiLCJlbWFpbCI6InRldHNpZWJvdUBnbWFpbC5jb20iLCJlbWFpbFZlcmlmaWVkIjpmYWxzZSwicGhvbmVOdW1iZXIiOiI2NzY3Mjc2MjciLCJwaG9uZU51bWJlclZlcmlmaWVkIjpmYWxzZX0sImF1dGhvcml0aWVzIjpbIlVTRVIiXX0.o5glRk6gTawqz51R9q_x4hO_wlTH_x-LbeSqqkfDYfHxMOrIV4KUKx0a-PrTpUBX_FnrFyU7gDm4J5-HIYYc8aa26oDH650DUzrVoKLWc1Y4oNeMupQUNhkHMpMRZ0hGXuTM6VGQmzAPL0B224-mblviJbJxDDaGa6591NWtVpnX-p6AWlgEah_plQuX7ZCKvJxs9NSN1-8gGes3viHmIVJ5QoUJbjhsN0EuqRxTGabQnitZ_25H4UldqMg16w4X_d_JDu2hQtlO57qGE2Fe_0S6-OGocLULwd3wfeJIAcpMf3R0meCjf34y7Pf0teYj3oU7-_SBmO33U5J6-Dfq_A' \
  -H 'Public-Key: api_1752647119025_8d6e5340.ieGsWPxnE9eY0xBQ7n8htlTiQP3n4009' \
  -H 'Content-Type: application/json' \
  -d '{
  "host": "smtp.ethereal.email",
  "port": 587,
  "encryption": "TLS",
  "username": "evert.langosh93@ethereal.email",
  "password": "XFtnEUFHpSHWtnCxk5",
  "sender_email": "noreply@freelancedriver.com",
  "sender_name": "Freelance Driver App"
}'

Request URL

https://gateway.yowyob.com/notification-service/organizations/d4a0c630-6c5d-11f0-b56e-55aad7a60dfa/smtp-settings

Server response
Code	Details
200	
Response body

{
  "created_at": "2025-07-30T11:50:41.367732986Z",
  "updated_at": "2025-07-30T11:50:41.367800946Z",
  "deleted_at": null,
  "created_by": null,
  "updated_by": null,
  "organization_id": "d4a0c630-6c5d-11f0-b56e-55aad7a60dfa",
  "id": "6af8e580-6d3b-11f0-b349-e5f890374136",
  "host": "smtp.ethereal.email",
  "port": 587,
  "encryption": "TLS",
  "username": "evert.langosh93@ethereal.email",
  "password": "XFtnEUFHpSHWtnCxk5",
  "sender_email": "noreply@freelancedriver.com",
  "sender_name": "Freelance Driver App"
}


curl -X 'POST' \
  'https://gateway.yowyob.com/notification-service/organizations/d4a0c630-6c5d-11f0-b56e-55aad7a60dfa/design-templates' \
  -H 'accept: */*' \
  -H 'Authorization: Bearer eyJraWQiOiIzODdjOWEyOC0zODBlLTRmZWUtYTVjOC0wYTEwMzE2ZTAwNGYiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJodHRwOi8vODguMTk4LjE1MC4xOTU6ODA4OC9vcGVuaWQiLCJzdWIiOiJ0ZXRzaWVib3UiLCJleHAiOjE3NTM5NjA3NzUsImlhdCI6MTc1Mzg3NDM3NSwidXNlciI6eyJpZCI6ImQzZmM0ZDAwLTZjNTYtMTFmMC1iZWZmLWU1OTM3NjA1MjczYyIsImZpcnN0TmFtZSI6InN0cmluZyIsImxhc3ROYW1lIjoic3RyaW5nIiwidXNlcm5hbWUiOiJ0ZXRzaWVib3UiLCJlbWFpbCI6InRldHNpZWJvdUBnbWFpbC5jb20iLCJlbWFpbFZlcmlmaWVkIjpmYWxzZSwicGhvbmVOdW1iZXIiOiI2NzY3Mjc2MjciLCJwaG9uZU51bWJlclZlcmlmaWVkIjpmYWxzZX0sImF1dGhvcml0aWVzIjpbIlVTRVIiXX0.o5glRk6gTawqz51R9q_x4hO_wlTH_x-LbeSqqkfDYfHxMOrIV4KUKx0a-PrTpUBX_FnrFyU7gDm4J5-HIYYc8aa26oDH650DUzrVoKLWc1Y4oNeMupQUNhkHMpMRZ0hGXuTM6VGQmzAPL0B224-mblviJbJxDDaGa6591NWtVpnX-p6AWlgEah_plQuX7ZCKvJxs9NSN1-8gGes3viHmIVJ5QoUJbjhsN0EuqRxTGabQnitZ_25H4UldqMg16w4X_d_JDu2hQtlO57qGE2Fe_0S6-OGocLULwd3wfeJIAcpMf3R0meCjf34y7Pf0teYj3oU7-_SBmO33U5J6-Dfq_A' \
  -H 'Public-Key: api_1752647119025_8d6e5340.ieGsWPxnE9eY0xBQ7n8htlTiQP3n4009' \
  -H 'Content-Type: application/json' \
  -d '{
  "title": "Design Template de Test Simple",
  "subject": "Test - {{titre}}",
  "html": "<h1>Test</h1><p>Message de test : {{contenu}}.</p>"
}'

Request URL

https://gateway.yowyob.com/notification-service/organizations/d4a0c630-6c5d-11f0-b56e-55aad7a60dfa/design-templates

Server response
Code	Details
200	
Response body

{
  "organizationId": "d4a0c630-6c5d-11f0-b56e-55aad7a60dfa",
  "id": "37173e10-6d40-11f0-b349-e5f890374136",
  "title": "Design Template de Test Simple",
  "html": "<h1>Test</h1><p>Message de test : {{contenu}}.</p>",
  "subject": "Test - {{titre}}",
  "created_at": "2025-07-30T12:25:01.808647586Z",
  "updated_at": "2025-07-30T12:25:01.808693586Z",
  "deleted_at": null,
  "created_by": null,
  "updated_by": null
}


curl -X 'POST' \
  'https://gateway.yowyob.com/notification-service/organizations/d4a0c630-6c5d-11f0-b56e-55aad7a60dfa/email-templates' \
  -H 'accept: */*' \
  -H 'Authorization: Bearer eyJraWQiOiIzODdjOWEyOC0zODBlLTRmZWUtYTVjOC0wYTEwMzE2ZTAwNGYiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJodHRwOi8vODguMTk4LjE1MC4xOTU6ODA4OC9vcGVuaWQiLCJzdWIiOiJ0ZXRzaWVib3UiLCJleHAiOjE3NTM5NjA3NzUsImlhdCI6MTc1Mzg3NDM3NSwidXNlciI6eyJpZCI6ImQzZmM0ZDAwLTZjNTYtMTFmMC1iZWZmLWU1OTM3NjA1MjczYyIsImZpcnN0TmFtZSI6InN0cmluZyIsImxhc3ROYW1lIjoic3RyaW5nIiwidXNlcm5hbWUiOiJ0ZXRzaWVib3UiLCJlbWFpbCI6InRldHNpZWJvdUBnbWFpbC5jb20iLCJlbWFpbFZlcmlmaWVkIjpmYWxzZSwicGhvbmVOdW1iZXIiOiI2NzY3Mjc2MjciLCJwaG9uZU51bWJlclZlcmlmaWVkIjpmYWxzZX0sImF1dGhvcml0aWVzIjpbIlVTRVIiXX0.o5glRk6gTawqz51R9q_x4hO_wlTH_x-LbeSqqkfDYfHxMOrIV4KUKx0a-PrTpUBX_FnrFyU7gDm4J5-HIYYc8aa26oDH650DUzrVoKLWc1Y4oNeMupQUNhkHMpMRZ0hGXuTM6VGQmzAPL0B224-mblviJbJxDDaGa6591NWtVpnX-p6AWlgEah_plQuX7ZCKvJxs9NSN1-8gGes3viHmIVJ5QoUJbjhsN0EuqRxTGabQnitZ_25H4UldqMg16w4X_d_JDu2hQtlO57qGE2Fe_0S6-OGocLULwd3wfeJIAcpMf3R0meCjf34y7Pf0teYj3oU7-_SBmO33U5J6-Dfq_A' \
  -H 'Public-Key: api_1752647119025_8d6e5340.ieGsWPxnE9eY0xBQ7n8htlTiQP3n4009' \
  -H 'Content-Type: application/json' \
  -d '
{
  "design_template_id": "37173e10-6d40-11f0-b349-e5f890374136",
  "setting_id": "6af8e580-6d3b-11f0-b349-e5f890374136",
  "title": "Règle d'\''envoi de Test Simple"
}'

Request URL

https://gateway.yowyob.com/notification-service/organizations/d4a0c630-6c5d-11f0-b56e-55aad7a60dfa/email-templates

Server response
Code	Details
200	
Response body

{
  "created_at": "2025-07-30T12:29:35.615640508Z",
  "updated_at": "2025-07-30T12:29:35.615704808Z",
  "deleted_at": null,
  "created_by": null,
  "updated_by": null,
  "organization_id": "d4a0c630-6c5d-11f0-b56e-55aad7a60dfa",
  "id": "da4ae000-6d40-11f0-b349-e5f890374136",
  "setting_id": "6af8e580-6d3b-11f0-b349-e5f890374136",
  "design_template_id": "37173e10-6d40-11f0-b349-e5f890374136",
  "title": "Règle d'envoi de Test Simple",
  "cc": [],
  "is_default": null
}

curl -X 'POST' \
  'https://gateway.yowyob.com/notification-service/organizations/d4a0c630-6c5d-11f0-b56e-55aad7a60dfa/mailer' \
  -H 'accept: */*' \
  -H 'Authorization: Bearer eyJraWQiOiIzODdjOWEyOC0zODBlLTRmZWUtYTVjOC0wYTEwMzE2ZTAwNGYiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJodHRwOi8vODguMTk4LjE1MC4xOTU6ODA4OC9vcGVuaWQiLCJzdWIiOiJ0ZXRzaWVib3UiLCJleHAiOjE3NTM5NjA3NzUsImlhdCI6MTc1Mzg3NDM3NSwidXNlciI6eyJpZCI6ImQzZmM0ZDAwLTZjNTYtMTFmMC1iZWZmLWU1OTM3NjA1MjczYyIsImZpcnN0TmFtZSI6InN0cmluZyIsImxhc3ROYW1lIjoic3RyaW5nIiwidXNlcm5hbWUiOiJ0ZXRzaWVib3UiLCJlbWFpbCI6InRldHNpZWJvdUBnbWFpbC5jb20iLCJlbWFpbFZlcmlmaWVkIjpmYWxzZSwicGhvbmVOdW1iZXIiOiI2NzY3Mjc2MjciLCJwaG9uZU51bWJlclZlcmlmaWVkIjpmYWxzZX0sImF1dGhvcml0aWVzIjpbIlVTRVIiXX0.o5glRk6gTawqz51R9q_x4hO_wlTH_x-LbeSqqkfDYfHxMOrIV4KUKx0a-PrTpUBX_FnrFyU7gDm4J5-HIYYc8aa26oDH650DUzrVoKLWc1Y4oNeMupQUNhkHMpMRZ0hGXuTM6VGQmzAPL0B224-mblviJbJxDDaGa6591NWtVpnX-p6AWlgEah_plQuX7ZCKvJxs9NSN1-8gGes3viHmIVJ5QoUJbjhsN0EuqRxTGabQnitZ_25H4UldqMg16w4X_d_JDu2hQtlO57qGE2Fe_0S6-OGocLULwd3wfeJIAcpMf3R0meCjf34y7Pf0teYj3oU7-_SBmO33U5J6-Dfq_A' \
  -H 'Public-Key: api_1752647119025_8d6e5340.ieGsWPxnE9eY0xBQ7n8htlTiQP3n4009' \
  -H 'Content-Type: application/json' \
  -d '{
  "template_id": "da4ae000-6d40-11f0-b349-e5f890374136",
  "recipients": [ "juniortakos4@gmail.com" ],
  "priority": "LEVEL_1",
  "metadata": {
    "titre": "Mon Titre de Test",
    "contenu": "Ceci est le contenu du message de test."
  }
}'

Request URL

https://gateway.yowyob.com/notification-service/organizations/d4a0c630-6c5d-11f0-b56e-55aad7a60dfa/mailer

Server response
Code	Details
200	
Response body

{
  "status": "FAILED",
  "message": "Map values cannot be null",
  "data": null,
  "ok": false
}




Post http://localhost:8080/api/mock-notifications/d4a0c630-6c5d-11f0-b56e-55aad7a60dfa/smtp-settings

{
  "host": "smtp.gmail.com",
  "port": 587,
  "encryption": "TLS",
  "username": "mbognengj@gmail.com",
  "password": "wxupcsgfxdwotdeu",
  "sender_email": "clientfreelance@gmail.com",    
  "sender_name": "Freelance Driver (Test)"   
}

REPONSE
{
    "id": "f5690160-600a-4180-b89d-3bcf30d6f980",
    "host": "smtp.gmail.com",
    "port": 587,
    "encryption": "TLS",
    "username": "mbognengj@gmail.com",
    "password": "wxupcsgfxdwotdeu",
    "organization_id": "d4a0c630-6c5d-11f0-b56e-55aad7a60dfa",
    "sender_email": "clientfreelance@gmail.com",
    "sender_name": "Freelance Driver (Test)"
}

POST http://localhost:8080/api/mock-notifications/d4a0c630-6c5d-11f0-b56e-55aad7a60dfa/design-templates

Corps
{
  "title": "Design pour Email Réel",
  "html": "<!DOCTYPE html><html xmlns:th=\"http://www.thymeleaf.org\"><body><h1 style=\"color: #007BFF;\">Bienvenue, <span th:text=\"${recipientName}\"></span>!</h1><p>Ceci est un email de test envoyé depuis le backend Freelance Driver via Gmail.</p></body></html>",
  "subject": "Email de Test Réel - ${recipientName}"
}

REPONSE
{
    "id": "d11ef854-3b00-44aa-b4ad-c889806664c4",
    "organizationId": "d4a0c630-6c5d-11f0-b56e-55aad7a60dfa",
    "title": "Design pour Email Réel",
    "html": "<!DOCTYPE html><html xmlns:th=\"http://www.thymeleaf.org\"><body><h1 style=\"color: #007BFF;\">Bienvenue, <span th:text=\"${recipientName}\"></span>!</h1><p>Ceci est un email de test envoyé depuis le backend Freelance Driver via Gmail.</p></body></html>",
    "subject": "Email de Test Réel - ${recipientName}"
}



REPONSE http://localhost:8080/api/mock-notifications/d4a0c630-6c5d-11f0-b56e-55aad7a60dfa/email-templates

{
  "setting_id": "f5690160-600a-4180-b89d-3bcf30d6f980",
  "design_template_id": "d11ef854-3b00-44aa-b4ad-c889806664c4",
  "title": "Règle pour envoi réel aligné"
}

REPONSE
{
    "id": "99f0fa9f-80bd-4e54-8385-a3e0dee99770",
    "title": "Règle pour envoi réel aligné",
    "organization_id": "d4a0c630-6c5d-11f0-b56e-55aad7a60dfa",
    "setting_id": "f5690160-600a-4180-b89d-3bcf30d6f980",
    "design_template_id": "d11ef854-3b00-44aa-b4ad-c889806664c4"
}

Post http://localhost:8080/api/mock-notifications/d4a0c630-6c5d-11f0-b56e-55aad7a60dfa/send-test-email

corps
{
{
  "template_id": "99f0fa9f-80bd-4e54-8385-a3e0dee99770",
  "subject": "Email de Test Réel (Aligné)",
  "body": "Corps de secours",
  "recipients": [
    "juniortakos4@gmail.com" 
  ],
  "type": "EMAIL",
  "priority": "LEVEL_1",
  "metadata": {
    "recipientName": "Junior"
  }
}
REPONSE

Test email sent successfully!

started on port 8080 (http)
2025-08-01T11:43:11.691+01:00  INFO 279197 --- [driver-backend] [  restartedMain] c.f.d.DriverBackendApplication           : Started DriverBackendApplication in 24.739 seconds (process running for 26.487)
2025-08-01T11:58:20.869+01:00  WARN 279197 --- [driver-backend] [or-http-epoll-1] c.f.d.s.e.m.MockNotificationServiceImpl  : ==================== [LOCAL EMAIL SERVICE - REAL SEND] ====================
2025-08-01T11:58:44.013+01:00  WARN 279197 --- [driver-backend] [oundedElastic-4] c.f.d.s.e.m.MockNotificationServiceImpl  : >>> REAL EMAIL SENT via LOCAL Service to [juniortakos4@gmail.com] <<<
2025-08-01T12:01:39.636+01:00  WARN 279197 --- [driver-backend] [or-http-epoll-3] c.f.d.s.e.m.MockNotificationServiceImpl  : ==================== [LOCAL EMAIL SERVICE - REAL SEND] ====================
2025-08-01T12:01:53.691+01:00  WARN 279197 --- [driver-backend] [oundedElastic-5] c.f.d.s.e.m.MockNotificationServiceImpl  : >>> REAL EMAIL SENT via LOCAL Service to [juniortakos4@gmail.com] <<<




POST
http://localhost:8080/api/mock-notifications/18b38b0d-2421-428e-a7f3-9710ac260687/firebase-settings

body
{
  "projectId": "freelancedriver-system",
  "privateKey": "{\"type\": \"service_account\", ...}"
}

REPONSE

123456
{    "id": "1c630f26-5482-4335-8408-29e93c10e739",    "organizationId": "18b38b0d-2421-428e-a7f3-9710ac260687",    "projectId": "freelancedriver-system",    "privateKey": "{\"type\": \"service_account\", ...}"}



POST 

http://localhost:8080/api/mock-notifications/18b38b0d-2421-428e-a7f3-9710ac260687/push-templates
{
  "settingId": "1c630f26-5482-4335-8408-29e93c10e739",
  "title": " Trajet vers {{destination}} à {{cost}} FCFA !",
  "body": "Le chauffeur {{driverName}} propose un nouveau voyage. Réservez votre place avant qu'il ne soit trop tard."
}

REPONSE
{
    "id": "c91e5217-c89d-41ca-bcf4-d94271de5493",
    "organizationId": "18b38b0d-2421-428e-a7f3-9710ac260687",
    "settingId": "1c630f26-5482-4335-8408-29e93c10e739",
    "title": " Trajet vers {{destination}} à {{cost}} FCFA !",
    "body": "Le chauffeur {{driverName}} propose un nouveau voyage. Réservez votre place avant qu'il ne soit trop tard."
}