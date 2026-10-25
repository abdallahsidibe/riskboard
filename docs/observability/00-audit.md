# RiskBoard — Audit observabilité (Lot 0)

**Date** : 2026-10-25
**Statut** : Validé par l'utilisateur
**Périmètre** : Lecture seule — aucun fichier modifié

---

## 1. Architecture actuelle — confirmée dans le dépôt

| Composant | Valeur | Fichier source |
|---|---|---|
| Java | 21 | `backend/pom.xml` |
| Spring Boot | 4.1.1 | `backend/pom.xml` |
| Angular | 22.0.0 | `frontend/package.json` |
| Stack MVC | Spring MVC (pas WebFlux) | `pom.xml` : `spring-boot-starter-web`, absence de `spring-webflux` |
| Sécurité | Aucune Spring Security | Aucune classe `@EnableWebSecurity` dans `backend/src/main/java/` |
| CORS | Profil-dépendant, `cors.allowed-origins` | `CorsConfig.java`, `application-dev.yml`, `application-prod.yml` |
| Actuator | `/actuator/health` uniquement, `show-details: never` | `pom.xml`, `application.yml` |
| Prometheus | **Absent** | `pom.xml` : aucune dépendance `micrometer-registry-prometheus` |
| Base de données | PostgreSQL 16-alpine | `docker-compose.yml`, `docker-compose.prod.yml` |
| Base de données tests | H2 | `backend/pom.xml` |
| Runtime backend | JAR, `eclipse-temurin:21-jre-alpine` | `backend/Dockerfile` |
| Runtime frontend | Nginx Alpine | `frontend/Dockerfile`, `frontend/nginx.conf` |
| Registre images | GHCR `ghcr.io/${owner}/...` | `.github/workflows/build.yml` |
| Profils Spring | `dev` (défaut), `prod` | `application.yml`, `application-dev.yml`, `application-prod.yml` |
| Port backend (interne) | 8080 | `docker-compose.yml` |
| Port backend (mappé dev) | 8081 | `docker-compose.yml` |
| Port frontend (mappé dev) | 4200 | `docker-compose.yml` |
| Réseau prod | bridge `internal` | `docker-compose.prod.yml` |
| Restart prod | `unless-stopped` | `docker-compose.prod.yml` |
| Healthcheck CI/CD | `wget .../actuator/health \| grep UP` | `docker-compose.prod.yml` |
| CI GitLab | build + test | `.gitlab-ci.yml` |
| CI GitHub Actions | ci, build-push, deploy (INT/PROD) | `.github/workflows/` |

**Structure Java confirmée** : architecture en couches — controller / service / repository / entity / dto / mapper / exception / enums. Cinq controllers, cinq services, trois repositories, deux classes `@Configuration` (CORS, OpenAPI).

---

## 2. Tableau confirmé / hypothèse / à vérifier

| Statut | Élément |
|---|---|
| Confirmé | Spring MVC (pas WebFlux) |
| Confirmé | Actuator actif sur `/actuator/health`, `show-details: never` |
| Confirmé | Aucune dépendance Prometheus / Micrometer dans `pom.xml` |
| Confirmé | Aucun `@EnableWebSecurity` — pas de Spring Security actif |
| Confirmé | Réseau Compose prod : bridge `internal` |
| Confirmé | Deux fichiers Compose : `docker-compose.yml` (dev) + `docker-compose.prod.yml` (INT/PROD) |
| Confirmé | Workflows GitHub préservés (`ci.yml`, `build.yml`, `deploy.yml`) |
| Confirmé | `open-in-view: false` dans `application.yml` |
| Hypothèse | Profil actif sur VPS : `prod` (via `SPRING_PROFILES_ACTIVE=prod` dans compose.prod) |
| À vérifier | Images GHCR actuellement déployées — tag exact en prod |
| À vérifier | `CORS_ALLOWED_ORIGINS` correctement défini sur VPS |

---

## 3. Instrumentation disponible et manquante

**Disponible aujourd'hui :**
- `/actuator/health` → `{"status":"UP"}` sans détails

**Manquant (nécessaire pour le MVP) :**
- `micrometer-registry-prometheus` → endpoint `/actuator/prometheus`
- Exposition de `prometheus` dans `management.endpoints.web.exposure.include`
- Conteneur Prometheus (scraping)
- Conteneur Grafana (dashboards provisionnés)
- Conteneur Alertmanager (alertes)
- Conteneur Node Exporter (métriques hôte)
- `prometheus.yml` (scrape configs)
- Règles d'alerte (fichiers `.rules.yml`)
- Provisioning Grafana (datasources + dashboards JSON)

---

## 4. Risques identifiés

| Risque | Niveau | Mitigation |
|---|---|---|
| Exposition Prometheus sans auth | Élevé | Pas de port public ; réseau `internal` uniquement |
| Node Exporter exposé publiquement | Élevé | Pas de port public ; réseau `internal` uniquement |
| Grafana sans auth | Moyen | Mot de passe non-défaut obligatoire ; accès via reverse proxy Nginx |
| Régression healthcheck CI/CD | Faible | `/actuator/health` non modifié |
| Régression CORS | Faible | Aucun nouveau endpoint API frontend |
| Rollback avec `down -v` | Moyen | Rollback documenté : jamais `-v` |

---

## 5. Informations VPS — collectées lors de la validation

| Paramètre | Valeur |
|---|---|
| Disque | 200 GB |
| CPU | 6 cores |
| RAM | 12 GB |
| Architecture | À vérifier (amd64 présumé) |
| Accès Grafana | Reverse proxy Nginx |

---

## 6. Décisions de l'utilisateur (validées le 2026-10-25)

| Question | Décision |
|---|---|
| Accès Grafana | Reverse proxy Nginx |
| Stratégie Compose | Ajout dans `docker-compose.prod.yml` (pas de fichier overlay séparé) |
| Profil Spring | Modifier `application.yml` directement (pas de profil `observability` dédié) |

---

## 7. Questions ouvertes résiduelles

- Architecture VPS : `amd64` ou `arm64` ? (impact sur les images Docker)
- Nom de domaine ou IP publique pour le reverse proxy Nginx/Grafana ?
- Grafana : accès sur sous-domaine dédié ou chemin (`/grafana`) ?
- Alertmanager : canal de notification (email/webhook) à configurer ultérieurement ?
