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
