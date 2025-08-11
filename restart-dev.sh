#!/bin/bash
# Un script simple pour réinitialiser complètement l'environnement de développement.
# Exécutez-le depuis la racine de votre projet driver-backend.

# --- ÉTAPE 1: ARRÊT ET NETTOYAGE COMPLET ---
echo "🛑 Étape 1/5: Arrêt et suppression des conteneurs et volumes Docker..."
docker compose down -v
echo "✅ Conteneurs et volumes supprimés."
echo ""


# --- ÉTAPE 2: RELANCE DE L'INFRASTRUCTURE ---
echo "🚀 Étape 2/5: Démarrage des nouveaux conteneurs Docker (ScyllaDB & MinIO)..."
docker compose up -d
echo "✅ Conteneurs démarrés. Attente de 10 secondes pour leur stabilisation..."
sleep 10
echo ""


# --- ÉTAPE 3: CONFIGURATION DE SCYLLADB ---
echo "🗄️ Étape 3/5: Création du keyspace dans ScyllaDB..."
docker exec -it scylla-node-dev cqlsh -e "CREATE KEYSPACE IF NOT EXISTS freelanceBd WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };"
echo "✅ Keyspace 'freelanceBd' créé."
echo ""


# --- ÉTAPE 4: CONFIGURATION DE MINIO ---
echo "🪣 Étape 4/5: Création du bucket MinIO et application de la politique d'accès..."

# Créer le bucket
aws --endpoint-url http://192.168.43.4:9000 s3 mb s3://freelance-driver --profile minio
echo "✅ Bucket 'freelance-driver' créé."

# Préparer le fichier de politique (au cas où il aurait été supprimé)
cat <<EOF > policy.json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": "*",
            "Action": [
                "s3:GetObject"
            ],
            "Resource": [
                "arn:aws:s3:::freelance-driver/*"
            ]
        }
    ]
}
EOF

# Appliquer la politique
aws --endpoint-url http://192.168.43.4:9000 s3api put-bucket-policy --bucket freelance-driver --policy file://policy.json --profile minio
echo "✅ Politique d'accès public en lecture appliquée au bucket."
echo ""


# --- ÉTAPE 5: LANCEMENT DU BACKEND SPRING BOOT ---
echo "☕ Étape 5/5: Nettoyage du projet Maven et lancement de l'application Spring Boot..."
./mvnw clean
./mvnw spring-boot:run