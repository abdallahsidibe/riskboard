# TODO — Pistes d'amélioration

Ce fichier recense les fonctionnalités non implémentées et les améliorations identifiées lors du développement.

---

## Sécurité

- [ ] Authentification : intégrer Spring Security + JWT (ou OAuth2 / Keycloak)
- [ ] Autorisation par rôle : distinguer `SALES` (lecture + dérogation) et `RISK_MANAGER` (validation)
- [ ] Remplacer le champ texte libre `requestedBy` par l'identifiant de l'utilisateur connecté
- [ ] Sécuriser les endpoints REST avec `@PreAuthorize`
- [ ] HTTPS en production (termination TLS via reverse proxy ou Spring SSL)

## Tests

- [ ] Tests d'intégration sur les controllers (MockMvc ou `@SpringBootTest`)
- [ ] Tests d'import CSV : cas limites (fichier vide, colonnes manquantes, montants négatifs)
- [ ] Tests end-to-end frontend (Cypress ou Playwright)
- [ ] Tests unitaires des composants Angular (Jasmine / Jest)
- [ ] Couverture de code : configurer JaCoCo et seuil minimum

## Fonctionnalités

- [ ] Pagination côté serveur pour les risklimits (actuellement côté client)
- [ ] Historique des dérogations (toutes, pas seulement PENDING)
- [ ] Notifications par email lors de l'approbation / rejet d'une dérogation
- [ ] Export CSV / Excel des risklimits depuis le dashboard
- [ ] Recherche/filtre multi-critères côté backend (secteur, type, statut)
- [ ] Détail d'une contrepartie avec historique des limites

## Qualité & Opérationnel

- [ ] Logging structuré (JSON) avec correlation ID
- [ ] Métriques Prometheus + dashboard Grafana
- [ ] Health checks applicatifs (`/actuator/health`) exposés dans docker-compose
- [ ] Gestion des migrations de schéma avec Flyway ou Liquibase (remplace `ddl-auto: update`)
- [ ] Secrets Docker (`docker secret`) ou Vault — actuellement gérés via fichier `.env`
- [ ] Pipeline CI : ajouter étape de lint frontend (`ng lint`) et analyse SonarQube
- [ ] Profil Spring `staging` (actuellement : `dev` et `prod` uniquement)

## Dette technique

- [ ] Remplacer `SPRING_JPA_HIBERNATE_DDL_AUTO: update` par Flyway en production
- [ ] Externaliser la configuration CORS (actuellement liste d'origines codée en dur)
- [ ] Ajouter validation `@NotNull` / `@Size` sur tous les DTOs d'entrée
- [ ] Internationalisation (i18n) des messages d'erreur frontend
- [ ] Annoter `ExposureController` et `CounterpartyController` avec `@Tag` Swagger
- [ ] Fixer la compatibilité Maven locale : MapStruct 1.6.3 ne supporte pas Java 25 — épingler Java 21 (`JAVA_HOME`) ou upgrader MapStruct dès qu'une version stable Java 25 est disponible
