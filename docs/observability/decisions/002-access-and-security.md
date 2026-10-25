---
id: 002
titre: Accès Grafana et exposition réseau
date: 2026-10-25
statut: Accepté (HTTP provisoire — HTTPS planifié)
décideurs: équipe RiskBoard
---

## Contexte

Grafana doit être accessible depuis un navigateur externe. Prometheus, Node Exporter et Alertmanager ne doivent pas être exposés publiquement.

## Options évaluées

| Option | Description |
|---|---|
| A — Accès direct Grafana sur port 3000 | Simple mais pas de point d'entrée unifié |
| B — Nginx reverse proxy (retenu) | Découple le port public du port interne, prépare HTTPS |
| C — Traefik | Plus puissant, surcoût de complexité pour ce projet |

## Décision

**Option B** — Nginx reverse proxy (`nginx:1.27-alpine`) sur le port `${GRAFANA_PORT:-3000}`, proxifiant vers `grafana:3000` en interne. WebSocket `/api/live/` inclus pour le live streaming Grafana.

Prometheus, Node Exporter et Alertmanager n'exposent **aucun port sur l'hôte** en production — accessibles uniquement via `docker exec` pour les vérifications opérationnelles.

## État actuel

HTTP uniquement — IP publique `194.147.58.6:3000`. Acceptable pour INT.

## Migration HTTPS (planifiée)

Voir section 8 de `installation.md` : Certbot + Let's Encrypt sur un nom de domaine dédié. Nécessite un domaine DNS et une validation explicite avant mise en œuvre.

## Conséquences

- Accès Grafana authentifié (`GF_SECURITY_ADMIN_PASSWORD` hors Git, `GF_USERS_ALLOW_SIGN_UP: false`)
- Aucun secret dans `.env.example`
- `actuator/prometheus` non exposé publiquement (port interne uniquement)

## Retour arrière

`docker compose stop nginx-proxy` — Grafana reste accessible en interne.
