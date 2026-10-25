# RiskBoard — Plan de tâches observabilité

**Version** : 1.1
**Date** : 2026-10-25
**Statut** : Mis à jour — lots 1 à 5 DONE (voir `validation.md`)

---

## Légende statuts
`TODO` · `READY` · `IN_PROGRESS` · `BLOCKED` · `DONE`

Une tâche ne passe à `DONE` que si une preuve est fournie dans `validation.md`.

---

## Lot 1 — Instrumentation backend (OBS-01)

**Condition de sortie** : `/actuator/prometheus` répond 200 ; `mvn test` passe sans régression.

### T10 — Dépendance Micrometer Prometheus
| Champ | Valeur |
|---|---|
| **Statut** | DONE |
| **Exigence** | OBS-01 |
| **Prérequis** | Audit validé (Lot 0) |
| **Fichier** | `backend/pom.xml` |
| **Action** | Ajouter `io.micrometer:micrometer-registry-prometheus` sans version (géré par BOM) dans `<dependencies>` |
| **Test** | `mvn dependency:tree \| grep micrometer-registry-prometheus` |
| **Résultat attendu** | Artefact présent dans l'arbre de dépendances |
| **Rollback** | Supprimer la dépendance ; `mvn clean` |
| **Preuve** | `validation.md` — Lot 1 / T10 |

### T11 — Exposition endpoint prometheus dans application.yml
| Champ | Valeur |
|---|---|
| **Statut** | DONE |
| **Exigence** | OBS-01 |
| **Prérequis** | T10 |
| **Fichier** | `backend/src/main/resources/application.yml` |
| **Action** | Ajouter `prometheus` dans `management.endpoints.web.exposure.include` ; activer `management.endpoint.prometheus.enabled: true` ; tag `application` et `environment` |
| **Test** | `curl http://localhost:8081/actuator/prometheus` → 200 ; `curl http://localhost:8081/actuator/health` → 200 (non-régression) |
| **Résultat attendu** | Réponse `text/plain` avec métriques JVM ; health inchangé |
| **Rollback** | Revenir à `include: health` uniquement |
| **Preuve** | `validation.md` — Lot 1 / T11 |

### T12 — Non-régression tests existants
| Champ | Valeur |
|---|---|
| **Statut** | DONE |
| **Exigence** | OBS-01 |
| **Prérequis** | T10, T11 |
| **Fichier** | — |
| **Action** | `mvn test` dans `backend/` |
| **Test** | Tous les tests passent |
| **Résultat attendu** | `BUILD SUCCESS` |
| **Rollback** | — |
| **Preuve** | `validation.md` — Lot 1 / T12 |

---

## Lot 2 — Stack Compose observabilité (OBS-02, OBS-06)

**Condition de sortie** : cibles `riskboard-backend` et `node-exporter` en état `UP` dans Prometheus ; données persistent après restart.

### T20 — Prometheus dans docker-compose.prod.yml
| Champ | Valeur |
|---|---|
| **Statut** | DONE |
| **Exigence** | OBS-02, OBS-06 |
| **Prérequis** | T11 |
| **Fichiers** | `docker-compose.prod.yml`, `observability/prometheus/prometheus.yml` |
| **Action** | Ajouter service `prometheus`, volume `prometheus_data`, fichier de configuration `prometheus.yml` avec scrape configs backend + node-exporter ; rétention 15j/10Go ; limites ressources |
| **Test** | Prometheus UI → Targets : `riskboard-backend` UP ; `node-exporter` UP |
| **Résultat attendu** | Deux cibles vertes |
| **Rollback** | `docker compose stop prometheus` ; commenter le service dans Compose |
| **Preuve** | `validation.md` — Lot 2 / T20 |

### T21 — Node Exporter dans docker-compose.prod.yml
| Champ | Valeur |
|---|---|
| **Statut** | DONE |
| **Exigence** | OBS-02 |
| **Prérequis** | T20 |
| **Fichiers** | `docker-compose.prod.yml` |
| **Action** | Ajouter service `node-exporter` avec montages `/proc:/host/proc:ro`, `/sys:/host/sys:ro`, `/:/rootfs:ro` ; pas de mode privilégié |
| **Test** | `node_cpu_seconds_total` apparaît dans Prometheus |
| **Résultat attendu** | Métrique CPU hôte présente |
| **Rollback** | `docker compose stop node-exporter` |
| **Preuve** | `validation.md` — Lot 2 / T21 |

### T22 — Alertmanager dans docker-compose.prod.yml
| Champ | Valeur |
|---|---|
| **Statut** | DONE |
| **Exigence** | OBS-05 |
| **Prérequis** | T20 |
| **Fichiers** | `docker-compose.prod.yml`, `observability/alertmanager/alertmanager.yml` |
| **Action** | Ajouter service `alertmanager`, volume `alertmanager_data`, config minimaliste (pas de receiver actif) |
| **Test** | UI Alertmanager accessible en interne depuis Prometheus |
| **Résultat attendu** | Page status Alertmanager sans erreur |
| **Rollback** | `docker compose stop alertmanager` |
| **Preuve** | `validation.md` — Lot 2 / T22 |

### T23 — Persistance volumes
| Champ | Valeur |
|---|---|
| **Statut** | DONE |
| **Exigence** | OBS-02 |
| **Prérequis** | T20 |
| **Fichiers** | `docker-compose.prod.yml` |
| **Action** | Vérifier volumes nommés `prometheus_data`, `alertmanager_data` déclarés dans `volumes:` |
| **Test** | `docker compose restart prometheus` → données historiques toujours présentes |
| **Résultat attendu** | Graphes Prometheus continus après restart |
| **Rollback** | — |
| **Preuve** | `validation.md` — Lot 2 / T23 |

---

## Lot 3 — Dashboards Grafana (OBS-03)

**Condition de sortie** : Grafana démarre, datasource OK, deux dashboards visibles sans import manuel.

### T30 — Grafana + datasource provisionnée
| Champ | Valeur |
|---|---|
| **Statut** | DONE |
| **Exigence** | OBS-03, OBS-06 |
| **Prérequis** | T20 |
| **Fichiers** | `docker-compose.prod.yml`, `observability/grafana/provisioning/datasources/prometheus.yml` |
| **Action** | Ajouter service `grafana`, volume `grafana_data`, provisioning datasource Prometheus, mot de passe admin via `GF_SECURITY_ADMIN_PASSWORD` |
| **Test** | Grafana UI → Datasources → Prometheus → "Data source is working" |
| **Résultat attendu** | Connexion verte sans intervention manuelle |
| **Rollback** | `docker compose stop grafana` |
| **Preuve** | `validation.md` — Lot 3 / T30 |

### T31 — Dashboard application
| Champ | Valeur |
|---|---|
| **Statut** | DONE |
| **Exigence** | OBS-03 |
| **Prérequis** | T30, T12 (métriques réelles vérifiées) |
| **Fichiers** | `observability/grafana/provisioning/dashboards/application.json` |
| **Action** | Créer dashboard : état scraping, débit HTTP, erreurs 5xx, latences (si histogrammes), heap, GC, CPU JVM — avec requêtes PromQL vérifiées sur métriques réelles |
| **Test** | Panels non vides après trafic test (`curl /api/...`) |
| **Résultat attendu** | Au moins un panel avec données |
| **Rollback** | Supprimer fichier JSON |
| **Preuve** | `validation.md` — Lot 3 / T31 |

### T32 — Dashboard infrastructure
| Champ | Valeur |
|---|---|
| **Statut** | DONE |
| **Exigence** | OBS-03 |
| **Prérequis** | T30, T21 |
| **Fichiers** | `observability/grafana/provisioning/dashboards/infrastructure.json` |
| **Action** | Créer dashboard : CPU hôte, RAM, disque, réseau — labels et montpoints vérifiés |
| **Test** | Panels CPU et RAM non vides |
| **Résultat attendu** | Métriques hôte visibles |
| **Rollback** | Supprimer fichier JSON |
| **Preuve** | `validation.md` — Lot 3 / T32 |

### T33 — Métriques métier (Counter sur opérations réelles)
| Champ | Valeur |
|---|---|
| **Statut** | DONE |
| **Exigence** | OBS-04 |
| **Prérequis** | T31 |
| **Fichiers** | `backend/src/main/java/com/riskboard/service/DerogationService.java`, `backend/src/main/java/com/riskboard/service/CsvImportService.java` |
| **Action** | `derogation.submitted` (Counter, tag `limit_type`) dans `DerogationService.create()` ; `derogation.decided` (Counter, tag `decision`) dans `DerogationService.updateStatus()` ; `csv.import.rows` (Counter, tag `outcome`) dans `CsvImportService.importCsv()` |
| **Test** | `mvn test` → BUILD SUCCESS (21 tests) ; métriques visibles dans Prometheus après opération réelle |
| **Résultat attendu** | 3 métriques métier, labels bornés (max 3 valeurs chacun) |
| **Rollback** | Retirer l'injection `MeterRegistry` et les appels `Counter.builder()` dans les deux services |
| **Preuve** | `validation.md` — Lot 7 / T33 |

---

## Lot 4 — Alertes et scénario MVP (OBS-05)

**Condition de sortie** : 3 alertes `promtool` validées ; scénario arrêt/reprise complet démontré.

### T40 — Règles d'alerte
| Champ | Valeur |
|---|---|
| **Statut** | DONE |
| **Exigence** | OBS-05 |
| **Prérequis** | T20, T31 (métriques réelles connues) |
| **Fichiers** | `observability/prometheus/rules/riskboard.rules.yml` |
| **Action** | Écrire les 3 règles (BackendScrapeFailed, HighHttpErrorRate, DiskSpaceLow) avec labels, sévérité, summary, description, runbook link |
| **Test** | `promtool check rules observability/prometheus/rules/riskboard.rules.yml` |
| **Résultat attendu** | `SUCCESS` sans warning |
| **Rollback** | Supprimer fichier ; `docker compose restart prometheus` |
| **Preuve** | `validation.md` — Lot 4 / T40 |

### T41 — Tests promtool
| Champ | Valeur |
|---|---|
| **Statut** | DONE |
| **Exigence** | OBS-05 |
| **Prérequis** | T40 |
| **Fichiers** | `observability/prometheus/tests/riskboard.rules.test.yml` |
| **Action** | Écrire tests : nominal (alerte inactive), seuil franchi (alerte active), résolution, absence de données |
| **Test** | `promtool test rules observability/prometheus/tests/riskboard.rules.test.yml` |
| **Résultat attendu** | Tous les tests `PASS` |
| **Rollback** | — |
| **Preuve** | `validation.md` — Lot 4 / T41 |

### T42 — Intégration Alertmanager
| Champ | Valeur |
|---|---|
| **Statut** | DONE |
| **Exigence** | OBS-05 |
| **Prérequis** | T40, T22 |
| **Fichiers** | `observability/prometheus/prometheus.yml` |
| **Action** | Ajouter section `alerting.alertmanagers` pointant vers `alertmanager:9093` |
| **Test** | Arrêter backend → attendre 2 min → alerte visible dans Alertmanager UI |
| **Résultat attendu** | Alerte `BackendScrapeFailed` en état `firing` |
| **Rollback** | Retirer section `alerting` de prometheus.yml ; restart prometheus |
| **Preuve** | `validation.md` — Lot 4 / T42 |

### T43 — Scénario incident complet + runbook
| Champ | Valeur |
|---|---|
| **Statut** | DONE |
| **Exigence** | OBS-05 |
| **Prérequis** | T42, T31 |
| **Fichiers** | `docs/observability/runbook.md` |
| **Action** | Exécuter scénario : arrêt backend → alerte firing → redémarrage → resolved ; documenter commandes et captures |
| **Test** | Alerte passe `inactive → pending → firing → resolved` |
| **Résultat attendu** | Cycle complet observé et documenté dans validation.md |
| **Rollback** | — |
| **Preuve** | `validation.md` — Lot 4 / T43 |

---

## Lot 5 — Reverse proxy Nginx Grafana (OBS-06)

**Condition de sortie** : Grafana accessible via Nginx ; auth vérifiée.

### T50 — Service nginx-proxy dans Compose
| Champ | Valeur |
|---|---|
| **Statut** | DONE |
| **Exigence** | OBS-06 |
| **Prérequis** | T30, domaine DNS configuré |
| **Fichiers** | `docker-compose.prod.yml`, `observability/nginx/grafana.conf` |
| **Action** | Ajouter service `nginx-proxy`, publier port 80 (et 443 si HTTPS), monter `grafana.conf`, proxifier vers `grafana:3000` |
| **Test** | `curl http://grafana.mondomaine.tld` → redirect login Grafana |
| **Résultat attendu** | Page login Grafana accessible |
| **Rollback** | `docker compose stop nginx-proxy` |
| **Preuve** | `validation.md` — Lot 5 / T50 |

### T51 — Authentification Grafana
| Champ | Valeur |
|---|---|
| **Statut** | DONE |
| **Exigence** | OBS-06 |
| **Prérequis** | T50 |
| **Fichiers** | `docker-compose.prod.yml` (variables env), `.env.example` |
| **Action** | Définir `GF_SECURITY_ADMIN_USER` et `GF_SECURITY_ADMIN_PASSWORD` via variables d'environnement (hors Git) ; documenter dans `.env.example` |
| **Test** | Connexion avec mot de passe custom réussit ; mot de passe `admin` par défaut refusé |
| **Résultat attendu** | Auth personnalisée fonctionnelle |
| **Rollback** | — |
| **Preuve** | `validation.md` — Lot 5 / T51 |

---

## Lot 6 — CI/CD observabilité (OBS-10)

**Condition de sortie** : promtool et Compose validés à chaque PR dans le pipeline CI.

### T60 — Checks observabilité dans ci.yml
| Champ | Valeur |
|---|---|
| **Statut** | DONE |
| **Exigence** | OBS-10 |
| **Prérequis** | T40, T41 |
| **Fichiers** | `.github/workflows/ci.yml` |
| **Action** | Ajouter job `observability` : install promtool v3.4.1, `promtool check rules`, `promtool test rules`, `docker compose config --quiet` pour prod et overlay local |
| **Test** | Pipeline CI passe sur PR / push develop |
| **Résultat attendu** | Job `observability` vert dans GitHub Actions |
| **Rollback** | Retirer le job `observability` de `ci.yml` |
| **Preuve** | `validation.md` — Lot 7 / T60 |

---

## Récapitulatif

| Lot | Tâches | Exigences | Statut global |
|---|---|---|---|
| 1 — Backend | T10, T11, T12 | OBS-01 | DONE |
| 2 — Compose | T20, T21, T22, T23 | OBS-02, OBS-06 | DONE |
| 3 — Grafana | T30, T31, T32, T33 | OBS-03, OBS-04 | DONE |
| 4 — Alertes | T40, T41, T42, T43 | OBS-05 | DONE |
| 5 — Nginx | T50, T51 | OBS-06 | DONE |
| 6 — CI/CD | T60 | OBS-10 | DONE |
