.PHONY: up up-obs down down-obs logs logs-obs ps build help

COMPOSE_APP  = docker compose -f docker-compose.yml
COMPOSE_OBS  = docker compose -f docker-compose.yml -f docker-compose.observability.yml

# ─── Application seule ────────────────────────────────────────────────────────

up: ## Démarrer l'application (postgres + backend + frontend)
	$(COMPOSE_APP) up -d --build

down: ## Arrêter l'application (sans supprimer les volumes)
	$(COMPOSE_APP) down

logs: ## Suivre les logs de l'application
	$(COMPOSE_APP) logs -f

ps: ## État des conteneurs
	$(COMPOSE_APP) ps

build: ## Reconstruire les images sans démarrer
	$(COMPOSE_APP) build

# ─── Application + Observabilité ─────────────────────────────────────────────

up-obs: ## Démarrer l'application + stack observabilité complète
	$(COMPOSE_OBS) up -d --build

down-obs: ## Arrêter tout (application + observabilité, sans supprimer les volumes)
	$(COMPOSE_OBS) down

logs-obs: ## Suivre les logs de la stack observabilité
	$(COMPOSE_OBS) logs -f prometheus grafana alertmanager node-exporter

# ─── Aide ─────────────────────────────────────────────────────────────────────

help: ## Afficher cette aide
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

.DEFAULT_GOAL := help
