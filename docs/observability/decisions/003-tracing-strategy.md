---
id: 003
titre: Stratégie logs centralisés et traces
date: 2026-10-25
statut: En attente de décision
décideurs: équipe RiskBoard
---

## Contexte

Le MVP observabilité (métriques + dashboards + alertes) est validé. Les extensions OBS-07 (logs) et OBS-08 (traces) sont soumises à décision après mesure des ressources.

## Options logs centralisés (OBS-07)

| Option | RAM estimée | Intégration Grafana | Complexité |
|---|---|---|---|
| **Loki + Promtail** (recommandé) | ~200 MB | Native (Grafana Labs) | Faible |
| ELK (Elasticsearch + Logstash + Kibana) | ~3-4 GB | Séparée (Kibana) | Élevée |
| EFK (Elasticsearch + Fluent Bit + Kibana) | ~2-3 GB | Séparée | Moyenne |

**Recommandation** : Loki + Promtail — cohérent avec le modèle de labels Prometheus, s'intègre dans Grafana existant, budget RAM compatible avec le VPS (12 GB, ~1.5 GB utilisés au repos).

## Options traces distribuées (OBS-08)

| Option | Approche |
|---|---|
| Java Agent OpenTelemetry | Zéro code, injection JVM |
| Micrometer Tracing / OpenTelemetry | Code minimal, cohérent avec Micrometer existant |

**Recommandation** : Micrometer Tracing (déjà présent via Micrometer) + export OTLP vers Tempo (backend Grafana Labs).

## Décision

**Non prise** — à valider après :
1. Mesure des ressources sous charge (OBS-11)
2. Confirmation du besoin de corrélation logs/traces par l'équipe

## Prochaine étape proposée

Implémenter Loki + Promtail en lot 6 après validation OBS-04 (métriques métier) et OBS-10 (CI checks).
