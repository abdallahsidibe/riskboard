# RiskBoard — Conception observabilité

**Version** : 1.0
**Date** : 2026-10-25
**Statut** : Proposé — en attente de validation

---

## 1. Architecture MVP

```
                        ┌──────────────────────────────────────────────────────┐
                        │  VPS Linux amd64 — 194.147.58.6                     │
                        │  6 CPU · 12 GB RAM · 200 GB disque                  │
                        │                                                      │
                        │  ┌──────────────── réseau internal ───────────────┐  │
                        │  │                                                │  │
                        │  │  backend:8080       postgres:5432              │  │
                        │  │  frontend:80                                   │  │
                        │  │                                                │  │
                        │  │  prometheus:9090  ←──── scrape ────┐           │  │
                        │  │       │                             │           │  │
                        │  │       │ alerting              backend:8080      │  │
                        │  │       ↓                       node-exporter:9100│  │
                        │  │  alertmanager:9093                              │  │
                        │  │                                                │  │
                        │  │  grafana:3000  ←──── query ── prometheus:9090  │  │
                        │  │                                                │  │
                        │  │  nginx-proxy:80/443 ──── proxy ── grafana:3000 │  │
                        │  └────────────────────────────────────────────────┘  │
                        │                          │                           │
                        │                    port 443 public                   │
                        └──────────────────────────┼───────────────────────────┘
                                                   │
                                          grafana.mondomaine.tld
                                          (DNS A → 194.147.58.6)
```

**Principe d'isolation** : Prometheus, Node Exporter et Alertmanager n'ont aucun port publié. Seul Nginx publie le port 80/443 et proxifie vers Grafana.

---

## 2. Versions retenues

| Composant | Version | Image / Artefact | Source |
|---|---|---|---|
| Java | 21 | (existant) | — |
| Spring Boot | 4.1.1 | (existant) | — |
| micrometer-registry-prometheus | géré par BOM | `io.micrometer:micrometer-registry-prometheus` | BOM Spring Boot 4.1.1 |
| Prometheus | 3.4.1 | `prom/prometheus:v3.4.1` | hub.docker.com/r/prom/prometheus |
| Node Exporter | 1.9.1 | `prom/node-exporter:v1.9.1` | hub.docker.com/r/prom/node-exporter |
| Alertmanager | 0.28.1 | `prom/alertmanager:v0.28.1` | hub.docker.com/r/prom/alertmanager |
| Grafana | 11.6.1 | `grafana/grafana:11.6.1` | hub.docker.com/r/grafana/grafana |
| Nginx | alpine | `nginx:alpine` | (existant frontend) |

Toutes les images sont `amd64`-compatibles.

---

## 3. Intégration Docker Compose

**Stratégie** : ajout dans `docker-compose.prod.yml` (décision utilisateur).

**Nouveaux services ajoutés :**
- `prometheus`
- `node-exporter`
- `alertmanager`
- `grafana`
- `nginx-proxy` (reverse proxy Grafana)

**Nouveaux volumes nommés :**
- `prometheus_data`
- `grafana_data`
- `alertmanager_data`

**Réseau** : tous les nouveaux services rejoignent le réseau `internal` existant. Aucun réseau supplémentaire.

**Commandes inchangées** : le démarrage habituel `docker compose -f docker-compose.prod.yml up -d` continue à fonctionner ; les services observabilité démarrent en parallèle.

---

## 4. Propriétés Spring — surcharge dans `application.yml`

### Avant (actuel)
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
```

### Après (Lot 1)
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
  endpoint:
    health:
      show-details: never
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: riskboard-backend
      environment: ${SPRING_PROFILES_ACTIVE:dev}
```

**Aucun profil Spring dédié.** La configuration s'applique à tous les profils. En dev, `/actuator/prometheus` sera aussi accessible (non problématique — port 8081 non exposé publiquement).

---

## 5. Accès Actuator et sécurité

Pas de Spring Security active dans le projet. La protection repose sur l'isolation réseau :

| Endpoint | Accès réseau | Accès public |
|---|---|---|
| `/actuator/health` | interne + port 8081 en dev | Non (port non publié en prod) |
| `/actuator/prometheus` | interne uniquement (scrape Prometheus) | Non |
| Prometheus UI `:9090` | interne uniquement | Non |
| Alertmanager UI `:9093` | interne uniquement | Non |
| Node Exporter `:9100` | interne uniquement | Non |
| Grafana `:3000` | interne uniquement (via Nginx) | Via Nginx uniquement |

---

## 6. Inventaire métriques — après exposition (à compléter en Lot 1)

Métriques garanties par Spring Boot Actuator + Micrometer :

| Métrique | Type | Labels connus |
|---|---|---|
| `http_server_requests_seconds` | Timer (count/sum/max) | `method`, `status`, `uri` |
| `jvm_memory_used_bytes` | Gauge | `area` (heap/nonheap), `id` |
| `jvm_gc_pause_seconds` | Timer | `action`, `cause` |
| `jvm_threads_live_threads` | Gauge | — |
| `process_cpu_usage` | Gauge | — |
| `system_cpu_usage` | Gauge | — |

*Les noms et labels exacts seront vérifiés sur la sortie réelle de `/actuator/prometheus` avant d'écrire les requêtes PromQL.*

---

## 7. Règles d'alerte Prometheus

### A01 — BackendScrapeFailed
```yaml
alert: BackendScrapeFailed
expr: up{job="riskboard-backend"} == 0
for: 2m
labels:
  severity: critical
annotations:
  summary: "Scraping backend échoué"
  description: "Prometheus ne peut plus scraper {{ $labels.instance }} depuis 2 minutes."
  runbook: "docs/observability/runbook.md#backend-scrape-failed"
```

### A02 — HighHttpErrorRate
```yaml
alert: HighHttpErrorRate
expr: |
  sum(rate(http_server_requests_seconds_count{job="riskboard-backend", status=~"5.."}[5m]))
  /
  sum(rate(http_server_requests_seconds_count{job="riskboard-backend"}[5m])) > 0.05
for: 5m
labels:
  severity: warning
annotations:
  summary: "Taux d'erreurs HTTP 5xx élevé"
  description: "Plus de 5 % des requêtes retournent une erreur 5xx depuis 5 minutes."
  runbook: "docs/observability/runbook.md#high-http-error-rate"
```

*Note : expression à vérifier contre les noms réels après Lot 1.*

### A03 — DiskSpaceLow
```yaml
alert: DiskSpaceLow
expr: |
  (node_filesystem_avail_bytes{job="node-exporter", fstype!~"tmpfs|overlay|squashfs"}
  / node_filesystem_size_bytes) * 100 < 15
for: 5m
labels:
  severity: warning
annotations:
  summary: "Espace disque bas sur {{ $labels.mountpoint }}"
  description: "Moins de 15 % d'espace disponible sur {{ $labels.mountpoint }}."
  runbook: "docs/observability/runbook.md#disk-space-low"
```

---

## 8. Volumes, rétention et ressources

### Rétention Prometheus
```yaml
# dans docker-compose.prod.yml
command:
  - '--storage.tsdb.retention.time=15d'
  - '--storage.tsdb.retention.size=10GB'
```

### Limites ressources
```yaml
deploy:
  resources:
    limits:
      memory: 512m
      cpus: '0.5'
```

| Service | RAM limite | CPU limite |
|---|---|---|
| prometheus | 512 Mo | 0.5 |
| grafana | 256 Mo | 0.3 |
| alertmanager | 64 Mo | 0.1 |
| node-exporter | 32 Mo | 0.1 |
| nginx-proxy | 64 Mo | 0.1 |
| **Total** | **928 Mo** | **1.1** |

Marge disponible : ~11 Go RAM, ~5 CPU cores pour l'application.

### Sauvegarde
Les volumes `prometheus_data` et `grafana_data` doivent être inclus dans la stratégie de backup du VPS. Commande de snapshot : `docker run --rm -v prometheus_data:/data -v $(pwd):/backup alpine tar czf /backup/prometheus_data_$(date +%Y%m%d).tar.gz /data`.

---

## 9. Reverse proxy Nginx pour Grafana

### Stratégie
Nouveau service `nginx-proxy` dans Compose, dédié au reverse proxy observabilité. Distinct du Nginx du frontend (qui proxifie `/api/`).

### Configuration Nginx (simplifiée)
```nginx
server {
    listen 80;
    server_name grafana.mondomaine.tld;

    location / {
        proxy_pass http://grafana:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

HTTPS via Let's Encrypt (Certbot) : à ajouter après confirmation du domaine.

---

## 10. Structure des fichiers de configuration

```
observability/
├── prometheus/
│   ├── prometheus.yml
│   └── rules/
│       └── riskboard.rules.yml
│   └── tests/
│       └── riskboard.rules.test.yml
├── alertmanager/
│   └── alertmanager.yml
├── grafana/
│   └── provisioning/
│       ├── datasources/
│       │   └── prometheus.yml
│       └── dashboards/
│           ├── dashboards.yml
│           ├── application.json
│           └── infrastructure.json
└── nginx/
    └── grafana.conf
```

---

## 11. Rollback par lot

| Lot | Rollback |
|---|---|
| 1 (backend) | `git revert` des commits `pom.xml` + `application.yml` ; `mvn clean install` |
| 2 (Compose) | `docker compose -f docker-compose.prod.yml stop prometheus node-exporter alertmanager` ; supprimer les sections du Compose ; volumes conservés |
| 3 (Grafana) | `docker compose stop grafana` ; supprimer les fichiers de provisioning |
| 4 (Alertes) | Supprimer `riskboard.rules.yml` ; `docker compose restart prometheus` |
| 5 (Nginx) | Supprimer `nginx-proxy` du Compose ; arrêter le service |

**Jamais `docker compose down -v`** comme rollback ordinaire.

---

## 12. Questions ouvertes

| # | Question | Impact |
|---|---|---|
| Q1 | Nom de domaine disponible pour `grafana.*` ? | Config Nginx + Let's Encrypt |
| Q2 | HTTPS immédiat ou HTTP d'abord ? | Certbot en Lot 5 ou différé |
| Q3 | Canal de notification Alertmanager (email/webhook) ? | Config `alertmanager.yml` — désactivé jusqu'à décision |
