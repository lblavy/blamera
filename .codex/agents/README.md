# Sous-agents du projet

Les directives communes se trouvent dans `../../AGENTS.md`. Chaque fichier TOML
définit un rôle autonome avec `name`, `description` et `developer_instructions` :

- `coder.toml` : implémentation et corrections en TDD.
- `reviewer.toml` : critique indépendante, sans modification des fichiers.
- `tester.toml` : validation indépendante des critères d'acceptation.

L'orchestrateur enchaîne ces rôles selon `AGENTS.md`. Les rôles héritent du modèle,
du raisonnement et des permissions de la session parente. Les restrictions
d'écriture du reviewer et du tester sont des instructions, pas des sandbox distinctes.

Les versions actuelles de Codex découvrent les rôles du projet dans `.codex/agents/`.
Le fichier `../config.toml` active les agents et limite leur concurrence à trois,
en plus de l'orchestrateur. La configuration locale nécessite un projet de confiance.
La présence des fichiers ne prouve pas leur chargement dans une session déjà ouverte :
vérifier les rôles disponibles dans le client et relancer une session si nécessaire.

Si le runtime expose seulement des agents standards, l'orchestrateur transmet le
champ `developer_instructions` du rôle dans la délégation, avec le contrat du ticket.
L'absence d'outil de délégation doit être signalée, sans simuler une revue indépendante.

Références officielles consultées pour cette configuration :

- [Sous-agents et fichiers de rôle](https://learn.chatgpt.com/docs/agent-configuration/subagents)
- [Référence de configuration](https://learn.chatgpt.com/docs/config-file/config-reference)
