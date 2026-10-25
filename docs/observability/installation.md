# RiskBoard — Guide d'installation de l'observabilité

Ce guide couvre le lancement **en local** (développement) et **sur le VPS** (INT/PROD).

---

## Prérequis communs

| Outil | Version minimale | Vérification |
|---|---|---|
| Docker | 24+ | `docker --version` |
| Docker Compose | v2.20+ | `docker compose version` |
| Git | — | `git --version` |

---

## Note sur les ports locaux

Le backend Spring Boot utilise `server.port: 8080` (dans `application.yml`). Si ce port est déjà occupé sur votre machine, définissez `SERVER_PORT` dans votre `.env` local pour en choisir un autre (ex. 8085). **Cela n'affecte pas le VPS** où le port 8080 est libre.

```bash
# .env local — si 8080 est déjà pris
SERVER_PORT=8085
```

Dans toutes les commandes `curl` ci-dessous, remplacez `<BACKEND_PORT>` par le port effectivement utilisé (ex. `8085`). Sur le VPS, ce sera toujours `8081` (port mappé) → `8080` (port interne).

---

## 1. En local (développement)

### 1.0 Démarrage rapide — tout en une commande

Un fichier overlay `docker-compose.observability.yml` regroupe Prometheus, Grafana, Node Exporter et Alertmanager. Il s'ajoute au `docker-compose.yml` de dev et partage automatiquement le même réseau.

```bash
# Premier démarrage (build + stack complète)
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d --build

# Sessions suivantes (pas besoin de rebuild si le code n'a pas changé)
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d

# Arrêt complet
docker compose -f docker-compose.yml -f docker-compose.observability.yml down
# Ne jamais utiliser down -v — les données postgres seraient perdues
```

> Les services Prometheus, Grafana, Node Exporter et Alertmanager rejoignent automatiquement le réseau Docker du projet et peuvent résoudre `backend`, `postgres`, etc. par leur nom de service.

> **macOS** : `node-exporter` démarre sans montages `/proc`/`/sys` — la cible passe UP dans Prometheus mais les métriques infra host (CPU/RAM/disque) ne sont pas disponibles. C'est attendu en local.

Les sections suivantes (1.1 à 1.6) décrivent l'approche manuelle (`docker run`) — utile si vous ne voulez démarrer qu'un seul composant à la fois.

> **Erreur `port is already allocated` sur 9100** : un container `node-exporter` lancé manuellement précédemment occupe le port. Supprimer avec `docker rm -f node-exporter` puis relancer `make up-obs`.

> **Cibles Prometheus DOWN (`no such host`) après `make up-obs`** : des containers `prometheus` et/ou `grafana` lancés manuellement (`docker run`) sont encore actifs sur le réseau Docker et empêchent les nouveaux containers Compose de le rejoindre. Supprimer les anciens containers puis redémarrer :
> ```bash
> docker rm -f prometheus grafana
> make down-obs && make up-obs
> ```

---

### 1.0.1 Test de bout en bout en local

Après `make up-obs`, attendre ~30s que le backend soit healthy, puis exécuter les vérifications dans l'ordre :

```bash
# 1. Backend vivant ?
curl http://localhost:8081/actuator/health
# → {"status":"UP"}

# 2. Métriques exposées ?
curl -s http://localhost:8081/actuator/prometheus | grep -E "^(jvm_memory|http_server)" | head -5
# → des lignes de métriques JVM

# 3. Prometheus scrappe les 3 cibles ?
curl -s http://localhost:9090/api/v1/targets | python3 -m json.tool | grep '"health"'
# → 3x "health": "up"  (riskboard-backend, prometheus, node-exporter)

# 4. Grafana opérationnel + datasource branchée ?
curl -s http://localhost:3000/api/health
# → {"database":"ok","version":"11.6.1"}

curl -s -u admin:admin http://localhost:3000/api/datasources | python3 -m json.tool | grep '"name"'
# → "name": "Prometheus"

# 5. Dashboards provisionnés ?
curl -s -u admin:admin "http://localhost:3000/api/search?type=dash-db" | python3 -m json.tool | grep '"title"'
# → "title": "RiskBoard — Application"
# → "title": "RiskBoard — Infrastructure"

# 6. Alertmanager vivant ?
curl -s http://localhost:9093/-/healthy
# → OK
```

Dans le navigateur : http://localhost:3000 (admin / admin) → ouvrir **Dashboards → RiskBoard — Application**. Si les panels sont vides, générer du trafic :

```bash
for i in $(seq 1 30); do curl -s http://localhost:8081/actuator/health > /dev/null; done
# Attendre 30s (intervalle de scrape) puis rafraîchir Grafana
```

#### Test du cycle alerte firing → resolved

```bash
# Simuler une panne backend
docker compose -f docker-compose.yml stop backend

# ~30s → cible passe DOWN
curl -s http://localhost:9090/api/v1/targets | python3 -m json.tool | grep '"health"'
# → "health": "down"  (riskboard-backend)

# ~2 min → alerte BackendScrapeFailed passe firing
curl -s http://localhost:9090/api/v1/alerts | python3 -m json.tool | grep '"state"'
# → "state": "firing"

# Rétablir
docker compose -f docker-compose.yml start backend
# ~30s → cible repasse UP, alerte resolved
```

#### Résumé des états attendus

| Vérification | Attendu |
|---|---|
| `actuator/health` | `{"status":"UP"}` |
| `actuator/prometheus` | métriques JVM présentes |
| Prometheus targets | 3× `"health": "up"` |
| Grafana health | `{"database":"ok","version":"11.6.1"}` |
| Grafana datasource | `"name": "Prometheus"` |
| Grafana dashboards | 2 dashboards listés |
| Alertmanager | `OK` |
| Après stop backend (~30s) | `riskboard-backend` → `"down"` |
| Après 2 min | `"state": "firing"` |
| Après start backend (~30s) | cible → `"up"`, alerte → `"resolved"` |

```bash
# Tout arrêter proprement
make down-obs
```

---

### 1.1 Cloner et configurer

```bash
git clone <url-du-repo>
cd riskboard

cp .env.example .env
# Éditer .env — renseigner au minimum :
#   DB_USERNAME, DB_PASSWORD
#   GF_SECURITY_ADMIN_PASSWORD=motdepassefort123
#   SERVER_PORT=8085   ← si 8080 est déjà occupé sur votre machine
```

### 1.2 Premier démarrage — construire les images

> **Important** : toujours utiliser `--build` lors du premier lancement ou après une modification du code source. Sans `--build`, Docker réutilise les images en cache qui peuvent être obsolètes.

```bash
docker compose up -d --build
# Construit et démarre : postgres, backend, frontend

# Vérifier que tout est up
docker compose ps
# postgres → healthy, backend → up, frontend → up
```

### 1.3 Vérifier le backend

```bash
# Remplacer <BACKEND_PORT> par votre port effectif (ex. 8085 si SERVER_PORT=8085)
curl http://localhost:<BACKEND_PORT>/actuator/health
# → {"status":"UP"}

curl http://localhost:<BACKEND_PORT>/actuator/prometheus | head -8
# → # HELP application_ready_time_seconds ...
# → # TYPE application_ready_time_seconds gauge
# → application_ready_time_seconds{application="riskboard-backend",...}
```

Si `/actuator/prometheus` retourne 404 :
- Vérifier que `application.yml` contient `include: health,prometheus`
- Vérifier que `micrometer-registry-prometheus` est dans `pom.xml`
- Relancer avec `docker compose up -d --build backend`

### 1.4 Démarrer la stack observabilité en local

Le réseau Docker du dev compose s'appelle `riskboard_default`. Prometheus doit rejoindre ce réseau pour atteindre `backend` par son nom de service.

```bash
# Identifier le nom du réseau
docker network ls | grep riskboard
# → riskboard_default (ou similaire)

# Lancer Prometheus sur le même réseau
# Le backend est accessible via le nom de service "backend" sur son port INTERNE
# (8080 par défaut, ou SERVER_PORT si surchargé)
docker run -d --name prometheus \
  --network riskboard_default \
  -p 9090:9090 \
  -v $(pwd)/observability/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro \
  -v $(pwd)/observability/prometheus/rules:/etc/prometheus/rules:ro \
  prom/prometheus:v3.4.1 \
  --config.file=/etc/prometheus/prometheus.yml \
  --storage.tsdb.retention.time=1d

# Lancer Grafana sur le même réseau
docker run -d --name grafana \
  --network riskboard_default \
  -p 3000:3000 \
  -e GF_SECURITY_ADMIN_PASSWORD=admin \
  -v $(pwd)/observability/grafana/provisioning:/etc/grafana/provisioning:ro \
  grafana/grafana:11.6.1
```

> **Si SERVER_PORT ≠ 8080** : le `prometheus.yml` pointe sur `backend:8080`. Prometheus scrappe le port **interne** du conteneur, pas le port mappé sur l'hôte. Si vous avez changé le port Spring Boot via `SERVER_PORT=8085`, le conteneur backend écoute sur 8085 en interne — modifiez temporairement `prometheus.yml` pour mettre `backend:8085` avant de lancer Prometheus.

### 1.4.1 Lancer Node Exporter en local (optionnel — métriques infra)

`node-exporter` n'est défini que dans `docker-compose.prod.yml` (VPS). En local il faut le démarrer manuellement si vous voulez tester le dashboard Infrastructure.

> **Note macOS** : `/proc` et `/sys` n'existent pas sur Mac. Lancer sans les montages kernel — les métriques CPU/RAM/disque du host ne seront pas disponibles, mais la cible passera UP dans Prometheus.

```bash
# macOS — mode dégradé (cible UP, métriques host limitées)
docker run -d --name node-exporter \
  --network riskboard_default \
  -p 9100:9100 \
  prom/node-exporter:v1.9.1
```

> **Linux local** : ajouter les montages pour avoir les métriques complètes :
> ```bash
> docker run -d --name node-exporter \
>   --network riskboard_default \
>   -p 9100:9100 \
>   --pid=host \
>   -v /proc:/host/proc:ro \
>   -v /sys:/host/sys:ro \
>   -v /:/rootfs:ro \
>   prom/node-exporter:v1.9.1 \
>   --path.procfs=/host/proc \
>   --path.sysfs=/host/sys \
>   --path.rootfs=/rootfs \
>   "--collector.filesystem.mount-points-exclude=^/(sys|proc|dev|host|etc)($$|/)"
> ```

Sur le VPS, `node-exporter` démarre automatiquement via `docker-compose.prod.yml` avec les montages corrects — aucune action manuelle requise.

### 1.4.2 Relancer les containers existants (sessions suivantes)

> Les containers `prometheus` et `grafana` persistent entre les sessions Docker — ils n'ont pas besoin d'être recréés à chaque fois.

```bash
# Vérifier s'ils existent déjà (état Exited = stoppés mais présents)
docker ps -a --filter "name=prometheus" --filter "name=grafana" --filter "name=node-exporter"

# Si présents : simple start suffit
docker start prometheus grafana node-exporter

# Si absents (premier démarrage ou après docker rm) : utiliser les commandes de la section 1.4
```

> **Ne pas relancer `docker run`** si les containers existent déjà — cela échoue avec "Conflict: container name already in use". Un `docker rm -f prometheus grafana node-exporter` suivi du `docker run` de la section 1.4 est nécessaire uniquement si vous voulez repartir d'une config propre.

---

### 1.5 Vérifier en local

| Service | URL | Attendu |
|---|---|---|
| Backend health | http://localhost:`<BACKEND_PORT>`/actuator/health | `{"status":"UP"}` |
| Backend métriques | http://localhost:`<BACKEND_PORT>`/actuator/prometheus | métriques texte |
| Prometheus UI | http://localhost:9090 | Status → Targets → `riskboard-backend` UP |
| Grafana | http://localhost:3000 | Login `admin` / `admin` |

Dans Grafana : **Dashboards → RiskBoard — Application** et **RiskBoard — Infrastructure**.

#### Vérification des cibles Prometheus en ligne de commande

```bash
curl -s http://localhost:9090/api/v1/targets | python3 -m json.tool | grep '"health"'
# Avec node-exporter démarré (section 1.4.1) :
# → "health": "up"   (node-exporter)
# → "health": "up"   (prometheus)
# → "health": "up"   (riskboard-backend)
#
# Sans node-exporter (local macOS minimal) :
# → "health": "down"  (node-exporter — attendu, pas de montages /proc /sys)
# → "health": "up"    (prometheus)
# → "health": "up"    (riskboard-backend)
```

> Sur le VPS (Linux), les 3 cibles passent UP automatiquement — node-exporter a accès aux montages kernel.

### 1.6 Nettoyer l'environnement local

```bash
docker stop prometheus grafana node-exporter && docker rm prometheus grafana node-exporter
docker compose down
# Ne jamais utiliser down -v — les données postgres seraient perdues
```

---

## 2. Sur le VPS — première installation

> **Commandes multi-lignes** : les backslashes de continuation (`\`) ne fonctionnent pas toujours dans tous les shells VPS. Toutes les commandes `docker compose` ci-dessous sont écrites sur une seule ligne.

### 2.1 Connexion SSH

```bash
# Connexion avec clé SSH (la même clé utilisée par GitHub Actions)
ssh -i ~/.ssh/id_ed25519 deploy@194.147.58.6
```

> L'utilisateur de déploiement est `deploy` (pas `root`). Si vous vous connectez en `root` via le panneau de contrôle, faites `su deploy` pour basculer.

### 2.2 Vérifier Docker installé

```bash
docker --version        # Docker 24+
docker compose version  # Docker Compose v2.20+
```

Si Docker n'est pas installé :
```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker  # ou se reconnecter
```

### 2.3 Créer l'arborescence de déploiement

```bash
# Pour INT
sudo mkdir -p /opt/riskboard/int
sudo chown $USER:$USER /opt/riskboard/int

# Pour PROD (si applicable)
sudo mkdir -p /opt/riskboard/prod
sudo chown $USER:$USER /opt/riskboard/prod
```

### 2.4 Copier les fichiers de configuration

Depuis votre machine locale :

```bash
# Copier docker-compose.prod.yml (renommé en compose.yml sur le VPS)
scp -i ~/.ssh/id_ed25519 docker-compose.prod.yml deploy@194.147.58.6:/opt/riskboard/int/compose.yml

# Copier le dossier observability/ complet
scp -i ~/.ssh/id_ed25519 -r observability/ deploy@194.147.58.6:/opt/riskboard/int/
```

> **Si la clé SSH n'est pas autorisée pour `deploy`** : copier en tant que `root` puis corriger les droits sur le VPS :
> ```bash
> # Depuis la machine locale
> scp -i ~/.ssh/id_ed25519 -r observability/ root@194.147.58.6:/opt/riskboard/int/
> # Sur le VPS en root
> chown -R deploy:deploy /opt/riskboard/int/observability/
> ```

> **Important** : le workflow GitHub Actions (`deploy.yml`) synchronise automatiquement
> `docker-compose.prod.yml` à chaque déploiement, mais **pas** le dossier `observability/`.
> Ce dossier doit être copié manuellement une fois (ou à chaque modification de config).
> Voir section 3 pour les mises à jour ultérieures.

> **Si l'application était déjà déployée avant l'ajout de l'observabilité** : le `compose.yml` présent sur le VPS est l'ancienne version sans les services observabilité. Il faut le remplacer manuellement avec la commande `scp` ci-dessus avant de continuer.

### 2.5 Créer ou compléter le fichier .env sur le VPS

**Si le `.env` n'existe pas encore :**

```bash
cd /opt/riskboard/int

cat > .env << 'EOF'
# Application
DB_NAME=riskboard
DB_USERNAME=REMPLACER
DB_PASSWORD=REMPLACER_MOT_DE_PASSE_FORT

# Images
GITHUB_REPOSITORY_OWNER=votre_owner_github

# Ports (adapter selon le VPS — peuvent différer des valeurs par défaut)
BACKEND_PORT=8081
FRONTEND_PORT=4201
CORS_ALLOWED_ORIGINS=http://194.147.58.6:4201

# Observabilité
GF_SECURITY_ADMIN_PASSWORD=REMPLACER_MOT_DE_PASSE_GRAFANA
GRAFANA_PORT=3000
EOF

chmod 600 .env
```

**Si le `.env` existe déjà** (app déjà déployée via GitHub Actions) : vérifier que les variables observabilité sont présentes :

```bash
grep -E "GF_SECURITY_ADMIN_PASSWORD|GRAFANA_PORT" .env
```

Si elles manquent, les ajouter :

```bash
echo "GF_SECURITY_ADMIN_PASSWORD=REMPLACER_MOT_DE_PASSE_GRAFANA" >> .env
echo "GRAFANA_PORT=3000" >> .env
```

> `SERVER_PORT` n'est PAS défini ici — le backend utilise le port 8080 par défaut sur le VPS (port libre).
>
> `BACKEND_PORT` et `FRONTEND_PORT` peuvent différer des valeurs par défaut de la doc selon la configuration choisie lors du premier déploiement (ex. `BACKEND_PORT=8083`, `FRONTEND_PORT=4202`). Vérifier avec `cat .env`.

### 2.6 Vérifier la syntaxe Compose

```bash
cd /opt/riskboard/int
docker compose -p riskboard-int --env-file .env -f compose.yml config --quiet
# Attendu : aucun warning ni erreur
```

### 2.7 Démarrer l'application complète

**Si l'application n'est pas encore démarrée :**

```bash
cd /opt/riskboard/int
docker compose -p riskboard-int --env-file .env -f compose.yml up -d postgres
docker compose -p riskboard-int --env-file .env -f compose.yml ps postgres
docker compose -p riskboard-int --env-file .env -f compose.yml up -d
```

**Si backend et frontend tournent déjà** (déployés via GitHub Actions) — démarrer uniquement les services observabilité :

```bash
cd /opt/riskboard/int
docker compose -p riskboard-int --env-file .env -f compose.yml up -d prometheus node-exporter alertmanager grafana nginx-proxy
```

### 2.8 Suivre les logs au démarrage

```bash
# Tous les services
docker compose -p riskboard-int --env-file .env -f compose.yml logs -f

# Service par service
docker compose -p riskboard-int --env-file .env -f compose.yml logs -f backend
docker compose -p riskboard-int --env-file .env -f compose.yml logs -f prometheus
docker compose -p riskboard-int --env-file .env -f compose.yml logs -f grafana
```

Signe de bon démarrage dans les logs backend :
```
Exposing 2 endpoints beneath base path '/actuator'
Tomcat started on port 8080 (http)
Started RiskBoardApplication in X seconds
```

### 2.9 Vérifications après démarrage

Depuis le VPS (en interne) :

```bash
cd /opt/riskboard/int
BACKEND_PORT=$(grep BACKEND_PORT .env | cut -d= -f2)
GRAFANA_PASS=$(grep GF_SECURITY_ADMIN_PASSWORD .env | cut -d= -f2)

# 1. Backend health
curl http://localhost:$BACKEND_PORT/actuator/health
# → {"status":"UP"}

# 2. Backend métriques (Micrometer actif)
curl http://localhost:$BACKEND_PORT/actuator/prometheus | head -5
# → # HELP application_ready_time_seconds ...

# 3. Prometheus — cibles UP
# ⚠ Prometheus n'expose PAS son port sur le host en prod (9090/tcp sans mapping)
# → interroger via docker exec
docker exec riskboard-int-prometheus-1 wget -qO- 'http://localhost:9090/api/v1/targets' | python3 -m json.tool | grep '"health"'
# → 3x "health": "up"  (riskboard-backend, prometheus, node-exporter)

# 4. Grafana santé
curl -s http://194.147.58.6:3000/api/health
# → {"database":"ok","version":"11.6.1"}

# 5. Grafana — dashboards provisionnés
curl -s -u admin:$GRAFANA_PASS "http://localhost:3000/api/search?type=dash-db" | python3 -m json.tool | grep '"title"'
# → "title": "RiskBoard — Application"
# → "title": "RiskBoard — Infrastructure"

# 6. Alertmanager
curl -s http://localhost:9093/-/healthy
# → OK
```

> **Ports exposés vs internes en prod** :
> - `nginx-proxy` : `0.0.0.0:3000->80` → accessible depuis l'extérieur
> - `backend` : `0.0.0.0:<BACKEND_PORT>->8080` → accessible depuis l'extérieur
> - `prometheus` : `9090/tcp` uniquement → interne réseau Docker, pas accessible depuis l'hôte
> - `alertmanager` : `9093/tcp` uniquement → interne
> - `node-exporter` : `9100/tcp` uniquement → interne
>
> Pour interroger Prometheus ou Alertmanager depuis le VPS, utiliser `docker exec`.

Depuis votre navigateur :
- **Grafana** : http://194.147.58.6:3000 → login `admin` / mot de passe du `.env`
- **Application** : http://194.147.58.6:`<FRONTEND_PORT>` (valeur dans `.env`)

### 2.10 Vérifier les dashboards Grafana

1. Ouvrir http://194.147.58.6:3000
2. **Connections → Data sources → Prometheus** → "Save & test" → "Data source is working"
3. **Dashboards → RiskBoard — Application** → panels non vides
4. **Dashboards → RiskBoard — Infrastructure** → CPU, RAM, disque visibles

Si des panels sont vides, générer du trafic :
```bash
BACKEND_PORT=$(grep BACKEND_PORT .env | cut -d= -f2)
for i in $(seq 1 30); do curl -s http://localhost:$BACKEND_PORT/actuator/health > /dev/null; done
# Attendre 30s (intervalle de scrape) puis rafraîchir Grafana
```

#### Valeurs observées sur VPS INT (référence)

| Panel | Dashboard | Valeur typique |
|---|---|---|
| Backend — état scraping | Application | UP |
| Threads actifs | Application | ~24 |
| CPU processus | Application | < 1% |
| CPU hôte | Infrastructure | ~1-2% |
| RAM utilisée | Infrastructure | ~12% |
| RAM disponible | Infrastructure | ~10 GiB |
| Disque / | Infrastructure | ~4% |

#### Note sur les tags Grafana

Dans la barre de recherche Grafana, des tags apparaissent sous les dashboards (`riskboard`, `spring-boot`, `application`). Ce sont des **étiquettes** définies dans les fichiers JSON des dashboards — elles ne correspondent pas à des dashboards séparés. Ils servent au filtrage quand le nombre de dashboards est élevé.

---

## 3. Mettre à jour les fichiers observabilité sur le VPS

À chaque modification dans `observability/` :

```bash
# Depuis votre machine locale
scp -r observability/ user@194.147.58.6:/opt/riskboard/int/

# Sur le VPS — rechargement à chaud Prometheus (sans perte de données)
# ⚠ Port 9090 non exposé sur le host → utiliser docker exec
ssh -i ~/.ssh/id_ed25519 deploy@194.147.58.6
docker exec riskboard-int-prometheus-1 wget -qO- --post-data='' 'http://localhost:9090/-/reload'
# → (pas de sortie = succès)

# Grafana recharge automatiquement les dashboards (toutes les 30s)
# Forcer si besoin :
docker compose -p riskboard-int --env-file .env -f compose.yml restart grafana
```

---

## 4. Déploiements applicatifs suivants (via GitHub Actions)

Le workflow `deploy.yml` ne touche que `backend` et `frontend` — la stack observabilité tourne en continu.

```
push develop → build.yml → images GHCR → deploy.yml → INT
                                                     ↓ pull + restart backend/frontend uniquement
                                          observabilité : inchangée ✓
```

---

## 5. Arrêt et rollback

### Arrêter l'observabilité sans toucher l'application

```bash
docker compose -p riskboard-int --env-file .env -f compose.yml stop prometheus node-exporter alertmanager grafana nginx-proxy
```

### Redémarrer l'observabilité

```bash
docker compose -p riskboard-int --env-file .env -f compose.yml up -d prometheus node-exporter alertmanager grafana nginx-proxy
```

### Rollback applicatif

```bash
PREV=$(cat /opt/riskboard/int/.previous_tag)
IMAGE_TAG=$PREV docker compose -p riskboard-int --env-file .env -f compose.yml up -d --no-deps backend frontend
# Ne jamais utiliser down -v
```

---

## 6. Vérifier la persistance des données

```bash
docker compose -p riskboard-int --env-file .env -f compose.yml restart prometheus
# Les données historiques restent présentes dans Grafana (graphes continus)

docker volume ls | grep riskboard-int
# riskboard-int_postgres_data
# riskboard-int_prometheus_data
# riskboard-int_grafana_data
# riskboard-int_alertmanager_data
```

---

## 7. Scénario de test alerte (INT uniquement)

```bash
# Arrêter le backend
docker compose -p riskboard-int --env-file .env -f compose.yml stop backend

# ~30s : cible passe DOWN dans Prometheus (Status → Targets)
# ~2 min : BackendScrapeFailed passe firing (Alertmanager UI)

# Redémarrer
docker compose -p riskboard-int --env-file .env -f compose.yml start backend

# ~30s : cible repasse UP, alerte resolved
```

---

## 8. Migration vers HTTPS + domaine (future)

```bash
# 1. DNS A : grafana.mondomaine.tld → 194.147.58.6

# 2. Certbot
sudo apt install certbot python3-certbot-nginx -y
docker compose -p riskboard-int stop nginx-proxy
sudo certbot certonly --standalone -d grafana.mondomaine.tld

# 3. Mettre à jour observability/nginx/grafana.conf (écouter sur 443, certificat)
# 4. Mettre à jour GF_SERVER_ROOT_URL dans .env
# 5. Redémarrer
docker compose -p riskboard-int --env-file .env -f compose.yml up -d nginx-proxy
```

---

## Résumé des URLs

| Service | Local | VPS |
|---|---|---|
| Application frontend | http://localhost:4200 | http://194.147.58.6:4201 |
| Backend API | http://localhost:`<BACKEND_PORT>`/api | http://194.147.58.6:8081/api |
| Backend health | http://localhost:`<BACKEND_PORT>`/actuator/health | interne VPS uniquement |
| Backend métriques | http://localhost:`<BACKEND_PORT>`/actuator/prometheus | interne VPS uniquement |
| Grafana | http://localhost:3000 | http://194.147.58.6:3000 |
| Prometheus UI | http://localhost:9090 | interne VPS uniquement |
| Alertmanager UI | http://localhost:9093 | interne VPS uniquement |

---

## Problèmes connus et corrections appliquées

| Symptôme | Cause | Correction |
|---|---|---|
| Backend crash au démarrage Docker : `cors.allowed-origins is null` | `application-dev.yml` était exclu du build Docker — le profil dev n'avait pas les origines CORS | Retiré `application-dev.yml` du `backend/.dockerignore` |
| Backend utilise `localhost:5432` au lieu de `postgres:5432` dans Docker | `application-dev.yml` hardcodait l'URL datasource | URL datasource remplacée par `${DB_URL:jdbc:postgresql://localhost:5432/riskboard}` (env var + fallback) |
| CORS null malgré profil dev actif (Spring Boot 4.x) | `@Configuration` (proxy CGLIB) + `@ConfigurationProperties` : le binder injecte les propriétés sur l'instance réelle, pas sur le proxy | `@Configuration(proxyBeanMethods = false)` dans `CorsConfig.java` |
| `/actuator/prometheus` vide après `docker compose up` | Image Docker en cache — ancienne version sans Micrometer | Toujours utiliser `docker compose up -d --build backend` après modification du code |
| `curl http://localhost:8081/actuator/prometheus` → "Connection reset by peer" en local | `server.port: 8085` dans `application.yml` alors que docker-compose mappe `8081→8080` — rien n'écoute sur 8080 dans le container | Corrigé : `server.port: 8080` dans `application.yml`, puis `docker compose up -d --build backend` |
| `docker run --name prometheus` → "Conflict: container name already in use" | Le container existait déjà (stoppé mais pas supprimé) | Utiliser `docker start prometheus` pour le relancer. Si recréation nécessaire : `docker rm -f prometheus` puis `docker run …` |
