Telecharger aws et configurer

curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"

unzip awscliv2.zip
sudo ./aws/install

aws --version

profile
aws configure --profile minio

docker-compose.yml :

AWS Access Key ID [None]:

    Tapez : junioradmin et appuyez sur Entrée.

    (Source : la variable MINIO_ROOT_USER de votre docker-compose.yml)

AWS Secret Access Key [None]:

    Tapez : YourStrongPassword2025 et appuyez sur Entrée.

    (Source : la variable MINIO_ROOT_PASSWORD de votre docker-compose.yml)

Default region name [None]:

    Tapez : us-east-1 et appuyez sur Entrée.

    (MinIO n'utilise pas de région, mais le CLI exige une valeur. us-east-1 est une valeur par défaut standard et sans danger.)

Default output format [None]:

    Tapez : json et appuyez sur Entrée.

    (C'est le format de sortie le plus lisible et le plus facile à utiliser dans des scripts.)



# --- ÉTAPE 4: CONFIGURATION DE MINIO ---
echo "🪣 Étape 4/5: Création du bucket MinIO et application de la politique d'accès..."

# Créer le bucket
aws --endpoint-url http://localhost:9000 s3 mb s3://freelance-driver --profile minio
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

echo "✅ Politique d'accès public en lecture appliquée au bucket."
echo ""


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




# --- ÉTAPE 5: LANCEMENT DU BACKEND SPRING BOOT ---
echo "☕ Étape 5/5: Nettoyage du projet Maven et lancement de l'application Spring Boot..."
./mvnw clean
./mvnw spring-boot:run



ssh root@167.71.176.127
Reconstruisez l'image Docker :
code Bash

    
docker build -t freelance-driver-app:latest .

  

Arrêtez et supprimez l'ancien conteneur :
code Bash

    
docker stop freelance-driver-service && docker rm freelance-driver-service

  

Relancez le conteneur avec la nouvelle image :
code Bash

    
docker run -d \
  --name freelance-driver-service \
  -p 8080:8080 \
  -p 8081:8081 \
  -e SERVER_PORT=8081 \
  --env-file .env \
  --network=freelance_driver_backend_freelance-net \
  --restart always \
  freelance-driver-app:latest
  

docker logs -f freelance-driver-service



sudo ufw allow 8080/tcp
sudo ufw allow 8081/tcp
sudo ufw allow 9092/tcp
sudo ufw allow out 587/tcp
sudo ufw reload
