---
id: 001
titre: Stratégie d'intégration Docker Compose
date: 2026-10-25
statut: Accepté
décideurs: équipe RiskBoard
---

## Contexte

Le projet possède déjà `docker-compose.yml` (dev) et `docker-compose.prod.yml` (prod). L'observabilité doit s'y intégrer sans perturber les workflows existants.

## Options évaluées

| Option | Description | Avantages | Inconvénients |
|---|---|---|---|
| A — Fusionner dans `docker-compose.prod.yml` | Ajouter les services directement | Un seul fichier | Observabilité obligatoire partout, mélange des responsabilités |
| B — Fichier overlay séparé (retenu) | `docker-compose.observability.yml` | Opt-in, séparation des responsabilités, cohérent avec pratique entreprise | Commande plus longue |
| C — Profil Docker Compose | `--profile observability` | Natif Docker Compose | Moins lisible, profils peu connus des juniors |

## Décision

**Option B** — fichier overlay `docker-compose.observability.yml` pour le développement local. En production (VPS), les services observabilité sont intégrés dans `docker-compose.prod.yml` car ils démarrent avec l'application et tournent en continu.

## Conséquences

- Local : `docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d` (ou `make up-obs`)
- VPS : `docker compose -p riskboard-int --env-file .env -f compose.yml up -d`
- Le workflow GitHub Actions (`deploy.yml`) ne touche que `backend` et `frontend` — l'observabilité tourne indépendamment.

## Retour arrière

Retirer les services observabilité de `docker-compose.prod.yml` et stopper les containers concernés sans `down -v`.
