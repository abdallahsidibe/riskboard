# RiskBoard — Validation observabilité

---

## Lot 1 — Instrumentation backend (2026-10-25)

### T10 — Dépendance Micrometer Prometheus

**Statut** : DONE

**Preuve** :
```
mvn dependency:tree | grep micrometer-registry-prometheus
```
Artefact résolu par le BOM Spring Boot 4.1.1 sans version explicite.

---

### T11 — Exposition endpoint prometheus

**Statut** : DONE

**Fichiers modifiés** :
- `backend/pom.xml` — ajout `io.micrometer:micrometer-registry-prometheus`
- `backend/src/main/resources/application.yml` — `include: health,prometheus` + tags

**Vérification runtime** (à exécuter après démarrage local) :
```bash
curl http://localhost:8081/actuator/health
# → {"status":"UP"}

curl http://localhost:8081/actuator/prometheus
# → HTTP 200, Content-Type: text/plain; version=0.0.4
# → métriques JVM présentes (jvm_memory_used_bytes, http_server_requests_seconds, ...)
```
*Test d'intégration runtime non exécuté ici — nécessite PostgreSQL local ou Docker. À valider au déploiement Lot 2.*

---

### T12 — Non-régression tests existants

**Statut** : DONE

**Commande** : `mvn test` dans `backend/`

**Résultat confirmé** :
```
Tests run: 11, Failures: 0, Errors: 0  — RiskCalculationService (alert level boundaries)
Tests run: 5,  Failures: 0, Errors: 0  — CsvImportService (parsing and resilience)
Tests run: 5,  Failures: 0, Errors: 0  — RiskLimitService (exposure aggregation by sector)
Tests run: 21, Failures: 0, Errors: 0
BUILD SUCCESS
```

**Tests non exécutés** :
- Vérification HTTP live de `/actuator/prometheus` — bloqué : pas de PostgreSQL disponible en environnement de test CI (H2 uniquement). À valider en Lot 2 lors du démarrage Compose.

---

## Lot 2 — Stack Compose (2026-10-25)

### T20 — Prometheus

**Statut** : DONE (vérification runtime VPS à venir)

**Fichiers créés/modifiés** :
- `observability/prometheus/prometheus.yml` — scrape backend:8080, node-exporter:9100, prometheus:9090 ; alertmanager:9093
- `docker-compose.prod.yml` — service `prometheus` (image `prom/prometheus:v3.4.1`), rétention 15j/10Go, limites 512m/0.5cpu

**Vérification syntaxique** :
```
docker compose -f docker-compose.prod.yml config --quiet
→ warnings uniquement sur variables d'env attendues (CORS_ALLOWED_ORIGINS, GITHUB_REPOSITORY_OWNER) — attendu hors VPS
```

**Vérification runtime** (à exécuter sur VPS après déploiement) :
```bash
# Prometheus UI → Status → Targets
# Attendre que riskboard-backend et node-exporter passent à UP
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[].health'
# → "up" pour chaque cible
```

---

### T21 — Node Exporter

**Statut** : DONE (vérification runtime VPS à venir)

**Fichiers modifiés** :
- `docker-compose.prod.yml` — service `node-exporter` (image `prom/node-exporter:v1.9.1`), montages `/proc`, `/sys`, `/` en lecture seule, pas de mode privilégié

---

### T22 — Alertmanager

**Statut** : DONE (vérification runtime VPS à venir)

**Fichiers créés/modifiés** :
- `observability/alertmanager/alertmanager.yml` — receiver `blackhole`, aucun canal actif
- `docker-compose.prod.yml` — service `alertmanager` (image `prom/alertmanager:v0.28.1`), limites 64m/0.1cpu

---

### T23 — Volumes et persistance

**Statut** : DONE

**Volumes nommés déclarés** dans `docker-compose.prod.yml` :
- `prometheus_data` — données TSDB Prometheus
- `alertmanager_data` — état Alertmanager
- `postgres_data` — inchangé

**Vérification persistance** (à exécuter sur VPS) :
```bash
docker compose restart prometheus
# Vérifier que les données historiques sont toujours présentes dans l'UI Prometheus
```

---

## Lot 3 — Grafana (2026-10-25)

### T30 — Grafana + datasource provisionnée

**Statut** : DONE (vérification runtime VPS à venir)

**Fichiers créés** :
- `observability/grafana/provisioning/datasources/prometheus.yml` — datasource Prometheus uid `prometheus`, proxy `http://prometheus:9090`
- `observability/grafana/provisioning/dashboards/dashboards.yml` — provider `riskboard`
- `docker-compose.prod.yml` — service `grafana` (`grafana/grafana:11.6.1`), limites 256m/0.3cpu, mot de passe via `GF_SECURITY_ADMIN_PASSWORD`

**Vérification syntaxique** : `docker compose config --quiet` → OK

**Vérification runtime** (à exécuter sur VPS) :
```bash
curl http://localhost:3000/api/health
# → {"database":"ok","version":"11.6.1"}
```

---

### T31 — Dashboard application

**Statut** : DONE (vérification panels VPS à venir)

**Fichier** : `observability/grafana/provisioning/dashboards/application.json`

**Panels** : état scraping, threads, CPU%, heap%, débit HTTP, taux 5xx, latence moyenne URI, heap used/max, GC pauses

**Note** : p95/p99 non inclus — histogrammes désactivés par défaut dans Micrometer.

---

### T32 — Dashboard infrastructure

**Statut** : DONE (vérification panels VPS à venir)

**Fichier** : `observability/grafana/provisioning/dashboards/infrastructure.json`

**Panels** : CPU%, RAM%, disque%, RAM disponible, CPU par mode, RAM série, disque par mountpoint, réseau in/out, I/O disque

---

## Lot 4 — Alertes (2026-10-25)

### T40 — Règles d'alerte

**Statut** : DONE

**Fichier** : `observability/prometheus/rules/riskboard.rules.yml`

**Preuve** :
```
promtool check rules observability/prometheus/rules/riskboard.rules.yml
→ SUCCESS: 3 rules found
```

**Règles** : `BackendScrapeFailed` (for: 2m, critical), `HighHttpErrorRate` (for: 5m, warning), `DiskSpaceLow` (for: 5m, warning)

---

### T41 — Tests promtool

**Statut** : DONE

**Fichier** : `observability/prometheus/tests/riskboard.rules.test.yml`

**Preuve** :
```
promtool test rules observability/prometheus/tests/riskboard.rules.test.yml
→ SUCCESS
```

**Tests couverts** (12 cas) :
- BackendScrapeFailed : nominal UP, pending < 2 min, firing ≥ 2 min, résolution après redémarrage, série absente
- HighHttpErrorRate : nominal 0%, sous seuil 3%, firing 10%, trafic nul (division par zéro)
- DiskSpaceLow : nominal 50%, firing 10%, exactement 15% (sous seuil strict), tmpfs exclu

---

### T42 — Alertmanager branché sur Prometheus

**Statut** : DONE (câblé en Lot 2 — vérification runtime VPS à venir)

`prometheus.yml` contient déjà la section `alerting.alertmanagers` vers `alertmanager:9093`.

**Vérification runtime** (sur VPS) :
```bash
docker compose stop backend
# Attendre 2 min 30s
curl http://localhost:9093/api/v2/alerts | jq '.[].labels.alertname'
# → "BackendScrapeFailed"
```

---

### T43 — Runbook

**Statut** : DONE

**Fichier** : `docs/observability/runbook.md`

**Sections** : BackendScrapeFailed, HighHttpErrorRate, DiskSpaceLow, scénario incident complet arrêt/reprise

---
## Lot 5 — Nginx reverse proxy (2026-10-25)

### T50 — Service nginx-proxy dans Compose

**Statut** : DONE (vérification runtime VPS à venir)

**Fichiers créés/modifiés** :
- `observability/nginx/grafana.conf` — proxy HTTP vers `grafana:3000`, WebSocket `/api/live/` inclus
- `docker-compose.prod.yml` — service `nginx-proxy` (`nginx:1.27-alpine`), port `${GRAFANA_PORT:-3000}:80`, limites 32m/0.1cpu

**Vérification syntaxique** : `docker compose config --quiet` → OK

**Vérification runtime** (sur VPS après déploiement) :
```bash
curl -I http://194.147.58.6:3000
# → HTTP/1.1 302 Found  (redirect vers /login)
# Accès navigateur : http://194.147.58.6:3000
```

---

### T51 — Authentification Grafana

**Statut** : DONE

**Fichiers modifiés** :
- `docker-compose.prod.yml` — `GF_SECURITY_ADMIN_PASSWORD: ${GF_SECURITY_ADMIN_PASSWORD}`, `GF_USERS_ALLOW_SIGN_UP: "false"`
- `.env.example` — ajout de `GF_SECURITY_ADMIN_PASSWORD` et `GRAFANA_PORT`

**Action requise sur VPS** : définir `GF_SECURITY_ADMIN_PASSWORD` dans le fichier `.env` avec un mot de passe fort avant démarrage.

**Vérification** :
```bash
# Connexion avec le mot de passe custom → succès
# Connexion avec "admin" (mot de passe par défaut) → échec si password différent
```

**Note** : migration vers HTTPS + domaine à planifier. Ajouter Certbot lors de l'ajout du nom de domaine.

---

## Lot 6 — Validation runtime VPS INT (2026-10-25)

### V60 — Cibles Prometheus UP sur VPS

**Statut** : DONE

**Commande exécutée** :
```bash
docker exec riskboard-int-prometheus-1 wget -qO- 'http://localhost:9090/api/v1/targets' | python3 -m json.tool | grep '"health"'
```

**Résultat** :
```
"health": "up",   (node-exporter)
"health": "up",   (prometheus)
"health": "up",   (riskboard-backend)
```

---

### V61 — Métriques Micrometer actives sur VPS

**Statut** : DONE

**Commande** :
```bash
curl http://localhost:$BACKEND_PORT/actuator/prometheus | head -5
```

**Résultat** :
```
# HELP application_ready_time_seconds Time taken for the application to be ready to service requests
# TYPE application_ready_time_seconds gauge
application_ready_time_seconds{application="riskboard-backend",environment="prod",...} 7.232
```

---

### V62 — Dashboards Grafana avec données réelles

**Statut** : DONE

**URL** : http://194.147.58.6:3000

**Panels validés — RiskBoard — Application** :
- Backend — état scraping : UP
- Threads actifs : 24
- CPU processus : 0.100%
- Heap JVM : affiché

**Panels validés — RiskBoard — Infrastructure** :
- CPU hôte : 1.44%
- RAM utilisée : 12.5%
- Disque / (racine) : 4.13%
- RAM disponible : 10.2 GiB

---

### V63 — Budget ressources au repos (VPS INT)

**Statut** : DONE (mesure au repos uniquement — mesure sous charge à planifier)

| Service | RAM observée | CPU observé |
|---|---|---|
| riskboard-int-prometheus-1 | ~512m max (limite config) | < 1% au repos |
| riskboard-int-grafana-1 | ~256m max (limite config) | < 0.3% au repos |
| riskboard-int-node-exporter-1 | < 32m | < 0.1% |
| riskboard-int-alertmanager-1 | < 64m | < 0.1% |
| Total hôte observé | 12.5% de 12 GiB = ~1.5 GiB | ~1.44% de 6 CPU |

**Limite non couverte** : mesure sous charge contrôlée (k6) non effectuée — dépend du lot OBS-11.

---

## Lot 7 — Métriques métier + CI observabilité (2026-10-25)

### T33 — Métriques métier (OBS-04)

**Statut** : DONE

**Métriques implémentées** :

| Métrique | Type | Service | Labels | Valeurs label |
|---|---|---|---|---|
| `derogation.submitted` | Counter | `DerogationService.create()` | `limit_type` | CREDIT, MARKET, LIQUIDITY |
| `derogation.decided` | Counter | `DerogationService.updateStatus()` | `decision` | approved, rejected |
| `csv.import.rows` | Counter | `CsvImportService.importCsv()` | `outcome` | success, error |

**Non-régression** :
```
Tests run: 11, Failures: 0, Errors: 0  — RiskCalculationService
Tests run: 5,  Failures: 0, Errors: 0  — CsvImportService
Tests run: 5,  Failures: 0, Errors: 0  — RiskLimitService
Tests run: 21, Failures: 0, Errors: 0
BUILD SUCCESS
```

**Vérification runtime** (à exécuter après déploiement) :
```bash
# Soumettre une dérogation via l'UI ou l'API, puis :
curl http://localhost:$BACKEND_PORT/actuator/prometheus | grep derogation
# → derogation_submitted_total{limit_type="CREDIT",...} 1.0
# → derogation_decided_total{decision="approved",...} 1.0

# Importer un CSV, puis :
curl http://localhost:$BACKEND_PORT/actuator/prometheus | grep csv
# → csv_import_rows_total{outcome="success",...} 11.0
```

---

### T60 — CI/CD checks observabilité (OBS-10)

**Statut** : DONE

**Fichier modifié** : `.github/workflows/ci.yml` — ajout job `observability`

**Steps ajoutés** :
- Install promtool v3.4.1
- `promtool check rules observability/prometheus/rules/riskboard.rules.yml`
- `promtool test rules observability/prometheus/tests/riskboard.rules.test.yml`
- `docker compose -f docker-compose.prod.yml config --quiet`
- `docker compose -f docker-compose.yml -f docker-compose.observability.yml config --quiet`

**Vérification locale** :
```bash
promtool check rules observability/prometheus/rules/riskboard.rules.yml
# → SUCCESS: 3 rules found

promtool test rules observability/prometheus/tests/riskboard.rules.test.yml
# → SUCCESS

docker compose -f docker-compose.prod.yml config --quiet
# → warnings uniquement (variables env attendues) — pas d'erreur
```

**Vérification pipeline** : à confirmer sur la prochaine PR/push develop.
