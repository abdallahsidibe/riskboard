# RiskBoard — Exigences observabilité

**Version** : 1.0
**Date** : 2026-10-25
**Statut** : Validé (audit Lot 0 approuvé)

---

## Périmètre MVP

OBS-01 à OBS-06 constituent le MVP. OBS-07 à OBS-11 sont des extensions décidées après mesure du MVP.

---

## OBS-01 — Instrumentation backend

**Objectif** : exposer les métriques applicatives au format Prometheus.

**Critères d'acceptation :**
- `GET /actuator/health` retourne `{"status":"UP"}` sans détails sensibles
- `GET /actuator/prometheus` retourne 200 avec `Content-Type: text/plain; version=0.0.4`
- Prometheus scrape le backend depuis le réseau `internal` et la cible passe à `UP`
- Métriques présentes : JVM (heap, GC, threads), CPU, requêtes HTTP (`http_server_requests_seconds`)
- Les endpoints métier existants continuent à fonctionner sans régression
- Les tests Maven existants passent sans modification

**Questions ouvertes :** aucune.

---

## OBS-02 — Collecte et persistance

**Objectif** : Prometheus scrape backend et hôte ; données persistent.

**Critères d'acceptation :**
- Prometheus scrape toutes les 15 secondes
- Cibles utilisent les noms de services Docker (`riskboard-backend:8080`, `node-exporter:9100`)
- Aucun `localhost` pour désigner un autre conteneur
- Volume Prometheus persiste après `docker compose restart prometheus` (sans `-v`)
- Rétention : 15 jours ou 10 Go (whichever comes first)
- Node Exporter expose les métriques de l'hôte Linux (CPU, RAM, disque, réseau)
- Node Exporter sans mode privilégié ; montages `/proc`, `/sys`, `/` en lecture seule
- Aucun port observabilité publié sur l'interface publique (réseau `internal` uniquement)

**Questions ouvertes :** architecture VPS confirmée `amd64`.

---

## OBS-03 — Dashboards reproductibles

**Objectif** : Grafana démarre avec datasource et dashboards sans manipulation manuelle.

**Critères d'acceptation :**
- Datasource Prometheus provisionnée depuis fichier versionné
- Dashboard application : état scraping, débit HTTP, erreurs 5xx, latences (si histogrammes disponibles), mémoire heap, GC, CPU JVM
- Dashboard infrastructure : CPU hôte, RAM, disque, I/O, réseau
- Reconstruction complète possible en recréant les conteneurs sans supprimer les volumes
- Métriques absentes ou à zéro distinctement identifiables dans les panels
- p95/p99 affichés uniquement si histogrammes réellement collectés

**Questions ouvertes :** noms exacts des métriques et labels à vérifier après Lot 1.

---

## OBS-04 — Métriques métier

**Objectif** : instrumenter 3 à 5 opérations métier réelles.

**Indicateurs candidats (à confirmer après inspection des services) :**

| Indicateur | Type | Justification métier |
|---|---|---|
| Import CSV — succès/erreur | Counter | Volume et taux d'erreur des imports de limites |
| Dérogations soumises | Counter | Activité commerciale Sales |
| Calcul du niveau d'alerte par niveau (GREEN/ORANGE/RED) | Counter | Distribution des expositions au risque |

**Contraintes :**
- Aucun UUID, utilisateur, URL brute ou valeur non bornée en label
- Un scénario de test déclenche l'opération et vérifie la variation

**Questions ouvertes :** confirmation des services instrumentés après lecture du code métier.

---

## OBS-05 — Alertes

**Objectif** : 3 alertes MVP testées avec scénario arrêt/reprise.

| ID alerte | Condition | Délai `for` | Sévérité |
|---|---|---|---|
| `BackendScrapeFailed` | Echec scraping cible `riskboard-backend` | 2 min | critical |
| `HighHttpErrorRate` | Taux 5xx > 5 % sur 5 min (volume minimal requis) | 5 min | warning |
| `DiskSpaceLow` | Disque disponible < 15 % sur systèmes pertinents | 5 min | warning |

**Critères d'acceptation :**
- Règles validées par `promtool check rules`
- Tests `promtool` couvrent : nominal, seuil, délai, résolution, absence de données
- Alertmanager reçoit les alertes (visible dans l'UI)
- Email/webhook désactivés tant qu'aucun canal autorisé
- Scénario complet : arrêt backend → alerte `firing` → redémarrage → `resolved`

---

## OBS-06 — Sécurité et exploitation

**Critères d'acceptation :**
- Toutes les images avec tags explicites (pas de `latest`)
- Architecture `amd64` confirmée pour toutes les images
- Aucun port Prometheus (9090), Node Exporter (9100), Alertmanager (9093) publié
- Grafana accessible uniquement via reverse proxy Nginx (sous-domaine `grafana.*`)
- Mot de passe Grafana admin via variable d'environnement (`GF_SECURITY_ADMIN_PASSWORD`)
- Aucun secret dans Git (fichier `.env` non commité)
- Volumes nommés pour Prometheus, Grafana, Alertmanager
- Rollback de chaque lot documenté sans `docker compose down -v`
- Budget ressources mesuré au repos et noté dans `validation.md`

---

## Hors périmètre MVP (extensions)

- OBS-07 — Logs centralisés (Loki) : décision après mesure MVP
- OBS-08 — Traces (OpenTelemetry) : décision après mesure MVP
- OBS-09 — Frontend observability : décision après mesure MVP
- OBS-10 — Intégration CI/CD : lot séparé, accord spécifique requis
- OBS-11 — Tests de charge / SLO : lot séparé
