# RiskBoard — Runbook observabilité

Ce runbook décrit les procédures de diagnostic et de résolution pour chaque alerte MVP.

---

## BackendScrapeFailed {#backend-scrape-failed}

**Alerte** : `BackendScrapeFailed`
**Sévérité** : critical
**Déclenchement** : `up{job="riskboard-backend"} == 0` pendant 2 minutes

### Signification

Prometheus ne peut plus collecter les métriques du backend. Ce n'est pas nécessairement une indisponibilité métier : le service peut répondre aux requêtes HTTP tout en ayant l'endpoint Actuator inaccessible. Distinguer les deux avant d'escalader.

### Diagnostic

**Étape 1 — Vérifier l'état du conteneur**
```bash
docker compose -f /opt/riskboard/prod/compose.yml ps backend
# Vérifier : State = running, Health = healthy
```

**Étape 2 — Vérifier la réponse Actuator directement**
```bash
docker exec riskboard-backend wget -qO- http://localhost:8080/actuator/health
# Attendu : {"status":"UP"}

docker exec riskboard-backend wget -qO- http://localhost:8080/actuator/prometheus | head -5
# Attendu : lignes de métriques au format text/plain
```

**Étape 3 — Vérifier les logs du backend**
```bash
docker compose -f /opt/riskboard/prod/compose.yml logs --tail=50 backend
# Chercher : erreurs de démarrage, OOM, exceptions non récupérées
```

**Étape 4 — Vérifier la cible dans Prometheus**
```bash
# Depuis le VPS, tunnel SSH ou accès interne :
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | select(.job=="riskboard-backend") | {health, lastError}'
```

**Étape 5 — Vérifier la connectivité réseau inter-conteneurs**
```bash
docker exec riskboard-prometheus wget -qO- http://backend:8080/actuator/health
# Si échec : problème réseau entre prometheus et backend
```

### Résolution

| Cause | Action |
|---|---|
| Conteneur arrêté | `docker compose -f compose.yml up -d backend` |
| OOM (killed) | Augmenter les limites mémoire du conteneur backend dans compose.yml |
| Endpoint prometheus désactivé | Vérifier `application.yml` : `management.endpoints.web.exposure.include` doit contenir `prometheus` |
| Réseau Docker corrompu | `docker compose -f compose.yml down --remove-orphans && docker compose up -d` (sans `-v`) |

### Validation de résolution

```bash
# Vérifier que la cible repasse UP dans Prometheus (délai max 30s après démarrage)
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | select(.job=="riskboard-backend") | .health'
# → "up"
```

---

## HighHttpErrorRate {#high-http-error-rate}

**Alerte** : `HighHttpErrorRate`
**Sévérité** : warning
**Déclenchement** : taux 5xx > 5 % sur 5 minutes avec volume de trafic non nul

### Signification

Un nombre significatif de requêtes HTTP retournent des erreurs serveur. Peut indiquer : exception non gérée, problème de base de données, mémoire insuffisante, déploiement défaillant.

### Diagnostic

**Étape 1 — Identifier les URIs en erreur**
```bash
# Dans Prometheus / Grafana, requête :
# rate(http_server_requests_seconds_count{job="riskboard-backend", status=~"5.."}[5m])
# groupé par uri, method
```

**Étape 2 — Consulter les logs applicatifs**
```bash
docker compose -f /opt/riskboard/prod/compose.yml logs --tail=100 backend | grep -i "ERROR\|Exception\|5[0-9][0-9]"
```

**Étape 3 — Vérifier l'état de la base de données**
```bash
docker compose -f /opt/riskboard/prod/compose.yml exec postgres pg_isready -U $DB_USERNAME
docker compose -f /opt/riskboard/prod/compose.yml logs --tail=20 postgres
```

**Étape 4 — Vérifier les ressources JVM**
```bash
# Dans Grafana, dashboard Application :
# - Heap JVM utilisé % : si > 90 %, GC sous pression
# - Threads actifs : pic anormal ?
# - CPU processus : saturation ?
```

### Résolution

| Cause | Action |
|---|---|
| Exception non gérée | Identifier l'URI, corriger le code, redéployer |
| Base de données surchargée | Vérifier les connexions actives, optimiser les requêtes lentes |
| OOM / GC pressure | Redémarrer le backend (`docker compose restart backend`) en attendant un fix |
| Déploiement partiel | Rollback via GitHub Actions (déployer le tag précédent) |

### Validation de résolution

```bash
# Taux 5xx doit revenir sous 5 % dans Grafana → panel "Taux d'erreurs 5xx %"
# Alerte passe à "resolved" dans Alertmanager UI
```

---

## DiskSpaceLow {#disk-space-low}

**Alerte** : `DiskSpaceLow`
**Sévérité** : warning
**Déclenchement** : espace disponible < 15 % sur un système de fichiers réel pendant 5 minutes

### Signification

L'espace disque s'épuise. Le VPS dispose de 200 Go — les causes probables : logs Docker non rotatés, données Prometheus qui grossissent, dumps de base de données accumulés.

### Diagnostic

**Étape 1 — Identifier les plus gros consommateurs**
```bash
df -h
du -sh /var/lib/docker /opt/riskboard /var/log 2>/dev/null | sort -rh | head -20
```

**Étape 2 — Vérifier les volumes Docker**
```bash
docker system df -v
# Colonnes : volumes, images, conteneurs, build cache
```

**Étape 3 — Vérifier les logs Docker**
```bash
du -sh /var/lib/docker/containers/*/
# Identifier les conteneurs avec logs volumineux
```

**Étape 4 — Vérifier la rétention Prometheus**
```bash
docker compose -f /opt/riskboard/prod/compose.yml exec prometheus \
  du -sh /prometheus
# Si > 10 Go : la rétention par taille ne s'applique pas encore ou le volume déborde
```

### Résolution

| Cause | Action |
|---|---|
| Logs Docker volumineux | `docker compose logs --no-log-prefix backend > /dev/null` puis configurer log rotation dans compose.yml (`logging.options.max-size`) |
| Images Docker obsolètes | `docker image prune -f` (images non utilisées) |
| Build cache Docker | `docker builder prune -f` |
| Volume Prometheus > 10 Go | Réduire `--storage.tsdb.retention.time` ou `--storage.tsdb.retention.size` |
| Données applicatives | Identifier avec `du` et supprimer les fichiers temporaires inutiles |

**Ne jamais** supprimer `postgres_data`, `prometheus_data` ou `grafana_data` sans backup préalable.

### Validation de résolution

```bash
df -h /
# Disponible doit dépasser 15 %
# Alerte passe à "resolved" dans Alertmanager UI (délai : 5 min après retour > 15 %)
```

---

## Scénario incident complet — arrêt / reprise backend {#incident-scenario}

Ce scénario valide le cycle complet de l'alerte `BackendScrapeFailed`.

**Prérequis** : stack complète démarrée sur l'environnement de test (INT).

```bash
# 1. Vérifier l'état initial : backend UP dans Prometheus
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | select(.job=="riskboard-backend") | .health'
# → "up"

# 2. Arrêter uniquement le backend
docker compose -f compose.yml stop backend

# 3. Attendre ~30s : la cible passe à DOWN dans Prometheus (Targets UI)
# 4. Attendre 2 min : alerte passe pending → firing (Alertmanager UI)
# 5. Vérifier dans Alertmanager : alerte BackendScrapeFailed visible

# 6. Redémarrer le backend
docker compose -f compose.yml start backend

# 7. Attendre ~30s : cible repasse UP
# 8. Alerte passe firing → resolved dans Alertmanager (~5 min max)
```

**Délais totaux observables** :
- Cible DOWN → pending : immédiat (prochain scrape, 15s max)
- Pending → firing : 2 minutes (`for: 2m`)
- Firing → resolved : prochain scrape après redémarrage + délai d'évaluation (~15-30s)

---

## Contacts et escalade

Canal de notification : non configuré (receiver `blackhole` actif).
Pour activer un canal email ou webhook : modifier `observability/alertmanager/alertmanager.yml` et définir les secrets dans `.env`. Valider avec l'équipe avant activation.
