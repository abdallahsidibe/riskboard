# Référence des workflows GitHub Actions — RiskBoard

Ce document détaille tous les paramètres, déclencheurs, variables et comportements
des trois workflows CI/CD du projet.

---

## Vue d'ensemble

```
┌─────────────────────────────────────────────────────────────────┐
│  ci.yml          — Qualité : compile, tests, Docker lint         │
│  build.yml       — Production d'images Docker taguées par SHA    │
│  deploy.yml      — Déploiement sur VPS (INT et/ou PROD)          │
└─────────────────────────────────────────────────────────────────┘

Flux automatique (Git Flow)
───────────────────────────
  push develop  →  ci.yml  →  build.yml  →  deploy INT (auto)
  push main     →  ci.yml  →  build.yml  →  deploy PROD (approbation)

Flux manuel (hors Git Flow)
───────────────────────────
  build.yml (dispatch, feature branch, deploy_to_int=true)  →  deploy INT
  deploy.yml (dispatch, image_tag existant, target au choix) →  INT / PROD / les deux
```

---

## 1. `ci.yml` — Intégration continue

### Déclencheurs

| Événement | Condition | Rôle |
|-----------|-----------|------|
| `pull_request` | vers `main`, `develop`, `release/**` | Vérifie la PR avant merge |
| `push` | sur `develop` | Double-vérification après merge |

> **Pourquoi aussi sur push develop ?** Une PR peut être mergée sans attendre la CI
> (squash merge rapide). Le push sur develop garantit que develop est toujours vert.

### Jobs

#### `backend` — Compile & test

| Étape | Outil | Détail |
|-------|-------|--------|
| Checkout | `actions/checkout@v7.0.1` | Récupère le code source |
| Java 21 | `actions/setup-java@v6` | Distribution Temurin, cache Maven automatique |
| Compile | `mvn compile -q` | Échoue si erreur de compilation |
| Tests | `mvn test -q` | Lance tous les tests JUnit |
| Résultats | `dorny/test-reporter@v3.0.0` | Publie les résultats JUnit dans l'onglet GitHub Checks |
| Docker lint | `docker/build-push-action@v7.3.0` (push: false) | Valide le Dockerfile sans pousser d'image |

#### `frontend` — Build

| Étape | Outil | Détail |
|-------|-------|--------|
| Checkout | `actions/checkout@v7.0.1` | — |
| Node 24 | `actions/setup-node@v7.0.0` | Cache npm via `package-lock.json` |
| Install | `npm ci --prefix frontend` | Installation stricte (lockfile) |
| Build | `npm run build --prefix frontend` | Échoue si erreur TypeScript / build Angular |
| Docker lint | `docker/build-push-action@v7.3.0` (push: false) | Valide le Dockerfile sans pousser |

### Variables utilisées

Aucune variable d'environnement ou secret externe requis — tout est public (code source).

---

## 2. `build.yml` — Build & push des images

### Déclencheurs

#### Automatique — `push`

| Branche | Comportement post-build |
|---------|------------------------|
| `develop` | Déclenche automatiquement `deploy-int` → déploiement INT sans approbation |
| `main` | Déclenche automatiquement `deploy-prod` → déploiement PROD avec approbation |

#### Manuel — `workflow_dispatch`

Accessible depuis : `Actions → Build & Push Images → Run workflow`

| Paramètre | Type | Défaut | Description |
|-----------|------|--------|-------------|
| `deploy_to_int` | boolean | `false` | Si coché, déploie sur INT après le build. Utile pour tester une feature branch sans merger dans `develop`. |

> **Cas d'usage typique :** tu travailles sur `feature/nouveau-calcul-risque` et tu veux
> la voir tourner sur INT avant de créer une PR. Tu lances le workflow manuellement sur
> ta branche, coches `deploy_to_int`, et l'image est construite + déployée sur INT.

### Variables d'environnement (`env`)

| Variable | Valeur | Rôle |
|----------|--------|------|
| `REGISTRY` | `ghcr.io` | Registre Docker cible |
| `BACKEND_IMAGE` | `ghcr.io/<owner>/riskboard-backend` | Nom complet de l'image backend |
| `FRONTEND_IMAGE` | `ghcr.io/<owner>/riskboard-frontend` | Nom complet de l'image frontend |

`<owner>` est résolu automatiquement via `github.repository_owner`.

### Job `build`

#### Permissions requises

| Permission | Niveau | Raison |
|------------|--------|--------|
| `contents` | `read` | Checkout du code |
| `packages` | `write` | Push vers GHCR |

#### Output produit

| Output | Valeur | Consommé par |
|--------|--------|--------------|
| `image_tag` | 7 premiers caractères du SHA Git (`${GITHUB_SHA::7}`) | `deploy-int`, `deploy-prod` |

#### Étapes

| Étape | Détail |
|-------|--------|
| Checkout | Récupère le commit exact qui a déclenché le workflow |
| Compute short SHA | `${GITHUB_SHA::7}` — ex : `a84f093`. Immuable, traçable, unique |
| Log in GHCR | Authentification via `GITHUB_TOKEN` (token temporaire automatique, jamais à stocker) |
| Setup Buildx | Active le builder multi-plateforme avec cache |
| Build & push backend | Contexte `./backend`, produit deux tags : `<sha>` et `latest` |
| Build & push frontend | Contexte `./frontend`, produit deux tags : `<sha>` et `latest` |

#### Stratégie de tags

```
ghcr.io/<owner>/riskboard-backend:a84f093   ← tag immuable (SHA)
ghcr.io/<owner>/riskboard-backend:latest    ← alias flottant (pratique en dev)
```

> En déploiement, on utilise **toujours le SHA**. Le tag `latest` est un alias de
> commodité (ex : docker pull rapide en local) mais jamais référencé en CI/CD.

#### Cache Docker

| Paramètre | Valeur | Effet |
|-----------|--------|-------|
| `cache-from` | `type=gha` | Réutilise le cache des builds précédents |
| `cache-to` | `type=gha,mode=max` | Sauvegarde toutes les couches (même intermédiaires) |

Réduit le temps de build de ~3 min à ~30 s sur les commits sans changement de dépendances.

### Job `deploy-int`

| Paramètre | Valeur |
|-----------|--------|
| Condition | `github.ref == 'refs/heads/develop'` OU `inputs.deploy_to_int == true` |
| Dépendance | `build` |
| Workflow appelé | `.github/workflows/deploy.yml` |
| `image_tag` transmis | SHA calculé par le job `build` |
| `target` transmis | `int-only` |

### Job `deploy-prod`

| Paramètre | Valeur |
|-----------|--------|
| Condition | `github.ref == 'refs/heads/main'` uniquement |
| Dépendance | `build` |
| Workflow appelé | `.github/workflows/deploy.yml` |
| `image_tag` transmis | SHA calculé par le job `build` |
| `target` transmis | `prod-only` |

---

## 3. `deploy.yml` — Déploiement sur VPS

### Déclencheurs

#### Via workflow (`workflow_call`)

Appelé par `build.yml`. Reçoit les inputs suivants :

| Input | Requis | Type | Description |
|-------|--------|------|-------------|
| `image_tag` | Oui | string | SHA de l'image à déployer (ex : `a84f093`) |
| `target` | Non | string | Environnement cible. Défaut : `int-then-prod` |

#### Manuel (`workflow_dispatch`)

Accessible depuis : `Actions → Deploy → Run workflow`

| Input | Requis | Type | Options / Défaut | Description |
|-------|--------|------|------------------|-------------|
| `image_tag` | Non | string | vide = HEAD du commit courant | SHA à déployer. Si vide, utilise le dernier commit de la branche sélectionnée. |
| `target` | Oui | choice | `int-only` / `prod-only` / `int-then-prod` — défaut `int-only` | Environnement(s) cible(s) |

> **Quand utiliser `workflow_dispatch` ?**
> - Rollback : redéployer un SHA précédent (`a84f093` → revenir à `c3b2a1f`)
> - Hotfix urgent : image déjà sur GHCR, besoin de pousser PROD sans attendre un push main
> - Test isolation : déployer un SHA précis sur INT pour reproduire un bug

> **Restriction `prod-only` :** même en manuel, le déploiement PROD est bloqué par
> l'Environment `production` → approbation obligatoire d'un Release Manager.

### Variables GitHub requises par environnement

Ces variables sont définies dans `Settings → Environments` sur GitHub.

#### Environment `integration`

| Variable | Type | Exemple | Description |
|----------|------|---------|-------------|
| `VPS_HOST` | var | `194.147.58.6` | IP ou hostname du VPS |
| `VPS_USER` | var | `deploy` | Utilisateur SSH sur le VPS |
| `DEPLOY_PATH` | var | `/opt/riskboard/int` | Répertoire de déploiement sur le VPS |
| `COMPOSE_PROJECT_NAME` | var | `riskboard-int` | Nom du projet Docker Compose (isole les conteneurs INT des PROD) |
| `BACKEND_PORT` | var | `8083` | Port exposé par le backend (pour le health check) |
| `CORS_ALLOWED_ORIGINS` | var | `http://194.147.58.6:4202` | Origines autorisées — injectées dans `.env` au moment du déploiement |
| `SSH_PRIVATE_KEY` | **secret** | — | Clé privée SSH (format PEM) pour se connecter au VPS |

#### Environment `production`

| Variable | Type | Exemple | Description |
|----------|------|---------|-------------|
| `VPS_HOST` | var | `194.147.58.6` | Même VPS, isolation par répertoire et projet Compose |
| `VPS_USER` | var | `deploy` | — |
| `DEPLOY_PATH` | var | `/opt/riskboard/prod` | Répertoire distinct de INT |
| `COMPOSE_PROJECT_NAME` | var | `riskboard-prod` | Isole les conteneurs PROD des INT |
| `BACKEND_PORT` | var | `8081` | Port distinct de INT |
| `CORS_ALLOWED_ORIGINS` | var | `https://riskboard.example.com` | URL publique de PROD |
| `SSH_PRIVATE_KEY` | **secret** | — | Peut être la même clé ou une clé dédiée PROD |

> **Pourquoi `var` et non `secret` pour VPS_HOST, VPS_USER, etc. ?**
> Ces valeurs ne sont pas sensibles : connaître l'IP d'un serveur ne suffit pas à y accéder.
> Les mettre en `var` les rend visibles dans les logs GitHub, ce qui facilite le débogage.
> Seule la clé SSH est un secret réel.

### Job `resolve-tag`

| Étape | Comportement |
|-------|-------------|
| Checkout | Nécessaire pour `git rev-parse` si `image_tag` est vide |
| Compute tag | Si `inputs.image_tag` fourni → l'utilise. Sinon → `git rev-parse --short HEAD` |
| Verify image | `docker manifest inspect ghcr.io/<owner>/riskboard-backend:<tag>` — échoue explicitement si l'image n'existe pas sur GHCR avec un message clair |

> La vérification d'existence évite de déployer un SHA qui n'a jamais été buildé
> (ex : lancement manuel avec un SHA de feature branch non passée par `build.yml`).

### Job `deploy-int`

| Paramètre | Valeur |
|-----------|--------|
| Condition | `inputs.target == 'int-only'` OU `inputs.target == 'int-then-prod'` |
| Environment GitHub | `integration` (pas d'approbation requise) |
| Dépendance | `resolve-tag` |

#### Script de déploiement (étapes sur le VPS)

| Action | Commande | Raison |
|--------|----------|--------|
| Sauvegarde du tag courant | `cp .current_tag .previous_tag` | Permet le rollback |
| Écriture du nouveau tag | `echo "<sha>" > .current_tag` | Trace quelle version tourne |
| Injection CORS | `sed -i '/^CORS_ALLOWED_ORIGINS=/d' .env` + `echo "CORS_ALLOWED_ORIGINS=..." >> .env` | Met à jour l'origine sans recréer tout le `.env` |
| Pull des images | `docker compose pull backend frontend` | Télécharge les nouvelles images depuis GHCR |
| Redémarrage | `docker compose up -d --no-deps backend frontend` | Redémarre uniquement backend et frontend (pas la DB) |
| Nettoyage | `docker image prune -f` | Supprime les anciennes images non utilisées |

#### Health check

Attend jusqu'à **60 secondes** (12 tentatives × 5 s) que le backend réponde :
```
GET http://localhost:<BACKEND_PORT>/actuator/health
→ {"status":"UP"}
```

#### Rollback automatique

Si le health check échoue, le job `Rollback INT on failure` (conditionné par `if: failure()`) :
1. Lit `.previous_tag`
2. Relance `docker compose up -d` avec l'ancien SHA

### Job `deploy-prod`

Même comportement que `deploy-int` avec deux différences majeures :

| Différence | `deploy-int` | `deploy-prod` |
|------------|-------------|--------------|
| Environment GitHub | `integration` | `production` |
| Approbation | Aucune | **Required Reviewers obligatoire** |
| Condition sur target | `int-only` ou `int-then-prod` | `prod-only` ou `int-then-prod` |
| Condition sur deploy-int | — | `deploy-int` doit être `success` ou `skipped` |

> La condition `needs.deploy-int.result == 'skipped'` permet `prod-only` sans passer par INT.
> `needs.deploy-int.result == 'success'` garantit que INT a validé avant PROD dans le flux `int-then-prod`.

---

## Secrets et variables — Récapitulatif complet

### Secrets (valeurs sensibles, masquées dans les logs)

| Nom | Où le définir | Utilisé par |
|-----|---------------|-------------|
| `SSH_PRIVATE_KEY` | Environment `integration` ET `production` | `deploy.yml` — connexion SSH au VPS |
| `GITHUB_TOKEN` | Automatique (GitHub) | `build.yml` — push GHCR |

### Variables (valeurs non sensibles, visibles dans les logs)

| Nom | Scope | Utilisé par |
|-----|-------|-------------|
| `VPS_HOST` | Environment | `deploy.yml` |
| `VPS_USER` | Environment | `deploy.yml` |
| `DEPLOY_PATH` | Environment | `deploy.yml` |
| `COMPOSE_PROJECT_NAME` | Environment | `deploy.yml` |
| `BACKEND_PORT` | Environment | `deploy.yml` — health check |
| `CORS_ALLOWED_ORIGINS` | Environment | `deploy.yml` → injecté dans `.env` → Spring Boot |

---

## Résumé des flux possibles

| Déclencheur | Branche | Résultat |
|-------------|---------|----------|
| Push | `develop` | Build + deploy INT automatique |
| Push | `main` | Build + deploy PROD (approbation requise) |
| `build.yml` dispatch | n'importe laquelle, `deploy_to_int=false` | Build uniquement (image disponible sur GHCR) |
| `build.yml` dispatch | n'importe laquelle, `deploy_to_int=true` | Build + deploy INT |
| `deploy.yml` dispatch | — | Deploy avec SHA existant sur INT / PROD / les deux |
| PR | vers `main`/`develop`/`release/**` | CI uniquement (pas de build, pas de deploy) |
