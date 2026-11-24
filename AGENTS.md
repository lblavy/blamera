# Directives de développement — Blamera

Ces directives s'appliquent à tout le dépôt et à tous les agents qui y travaillent.
Les instructions explicites de l'utilisateur et les règles de l'environnement priment.

## Contexte et décisions

- Lire `docs/initial_idea.md` avant de proposer une évolution du produit.
- Blamera est une application Android de vision par ordinateur, complétée à terme
  par un outil local de préparation des données, d'entraînement et d'export.
- Privilégier l'inférence locale et livrer des milestones utilisables et démontrables.
- Consulter `docs/decisions/0001-technical-stack.md` pour la stack mobile retenue.
  Les versions du socle mobile sont dans `gradle/libs.versions.toml` et ses contrôles
  sont documentés dans `docs/quality.md`. Le socle PC et son environnement autonome
  sont décrits dans `docs/decisions/0002-pc-foundation.md` et `docs/pc-development.md`.
  Le modèle/runtime d'analyse image est décrit dans `docs/decisions/0003-image-segmentation.md`.
  L’analyse caméra locale est décrite dans `docs/decisions/0005-live-camera-analysis.md` ;
  sa validation appareil reste ouverte. Les frameworks ML PC restent à leurs tickets dédiés.
- Documenter les choix structurants, leurs raisons et leurs conséquences dans `docs/`.
- Garder les changements limités au ticket ; préserver les modifications existantes.

## Proportionner le travail au POC

- Blamera est un POC personnel, sans mise en production prévue dans le périmètre
  actuel. Ne pas lui ajouter implicitement les exigences d'un produit de production.
- Quand l'utilisateur accepte un parcours qui répond au besoin du POC, clôturer
  les tickets correspondants. Les mesures approfondies, optimisations et recettes
  exploratoires sans problème concret peuvent être closes comme limites acceptées,
  plutôt que maintenues indéfiniment en suspens.
- Conserver les résultats et les limites dans le bilan : une mesure non réalisée
  reste non mesurée. Une limite acceptée ne devient pas un résultat conforme.
- Rouvrir une limite seulement en présence d'un besoin nouveau, d'un problème
  observé ou d'une demande de l'utilisateur. Éviter les campagnes de validation
  supplémentaires qui n'aident pas son usage personnel.
- Cette proportionnalité conserve le Clean Code, le TDD, les revues, les tests et
  les contrôles qualité convenus, ainsi que la protection du téléphone et des
  données. Un défaut concret ou un critère essentiel non satisfait doit rester explicite.

## Organisation des agents

- L'orchestrateur est l'interlocuteur de l'utilisateur. Il définit les tickets,
  maintient la cohérence de l'architecture, coordonne les agents et arbitre la revue.
- Confier l'implémentation du code de production et des tests au rôle `coder`.
- Confier la critique indépendante au rôle `reviewer`, puis la validation au rôle `tester`.
- Les rôles sont définis dans `.codex/agents/*.toml` ; lire leurs instructions avant usage.
- Pour chaque ticket de développement : coder → reviewer → corrections éventuelles
  → vérification des corrections → tester → synthèse de l'orchestrateur.
- Attendre la fin des écritures avant la revue et la validation. Un seul agent écrit
  dans un même périmètre à la fois ; les autres examinent un état stable.
- Le reviewer ne modifie pas les fichiers. Le tester ne modifie pas les sources suivies ;
  il peut exécuter les outils et produire des artefacts temporaires de validation.
- Les sous-agents ne délèguent pas à leur tour. L'orchestrateur conserve la coordination.
- Ne pas imposer de modèle ou de niveau de raisonnement : hériter de la session parente.
- Si le runtime ne sait pas sélectionner un rôle personnalisé, transmettre explicitement
  ses `developer_instructions` à un agent standard, avec le contexte du ticket.
- Si aucun outil de sous-agent n'est disponible, le signaler : une auto-revue ne constitue
  pas une revue indépendante. Ne jamais prétendre qu'un autre agent a validé.
- L'orchestrateur peut préparer les directives et la documentation d'organisation ;
  leur vérification reste indépendante lorsque les outils le permettent.

## Contrat d'un ticket

Avant délégation, fournir au minimum :

- Objectif observable, périmètre inclus et exclusions utiles.
- Critères d'acceptation vérifiables, cas d'erreur et contraintes pertinentes.
- Contexte, références, décisions existantes et état du diff concerné.
- Commandes de build, de test et de qualité connues ; indiquer ce qui manque.
- Fichiers ou composants concernés, dépendances et limites de l'environnement.

Résoudre les choix courants dans le périmètre autorisé. Demander une clarification
uniquement lorsqu'une ambiguïté modifie réellement l'objectif ou une décision engageante.

## Protection du téléphone personnel

- Sur le téléphone, seules l'installation et la mise à jour de l'APK principal Blamera
  sont autorisées, ainsi que la lecture des logs de cette application exclusivement.
- L'utilisateur lance et manipule l'application lui-même. Ne pas la lancer automatiquement.
- Ne pas désinstaller l'application, effacer ses données ou caches, forcer son arrêt,
  modifier les paramètres ou permissions, redémarrer l'appareil ou modifier le débogage.
- Ne pas piloter les entrées tactiles/rotations, sauvegarder/restaurer, supprimer des fichiers,
  capturer l'écran ou lire les données privées et celles d'autres applications.
- Ne pas installer de test APK ni exécuter de tests instrumentés sur le téléphone,
  y compris `connectedDebugAndroidTest`, sans nouvelle autorisation humaine explicite.
- Limiter la lecture des logs à Blamera par PID/package ; ne pas collecter de logcat global.
- Réaliser l'automatisation sur PC : tests JVM et Compose/Robolectric. Pour une preuve
  réelle sur appareil, utiliser le retour de l'utilisateur ou les logs après son lancement manuel.
- Si une opération exige une action interdite, la signaler et laisser cette preuve en attente.
  Ces restrictions ne limitent pas les tests et opérations autorisés sur l'ordinateur.

## Clean Code pragmatique

- Utiliser des noms explicites et le vocabulaire du domaine ; éviter les abréviations opaques.
- Donner aux fonctions, classes et modules une responsabilité cohérente.
- Garder les fonctions compréhensibles, les niveaux d'abstraction cohérents et l'imbrication limitée.
- Préférer les retours anticipés et des conditions nommées quand ils simplifient la lecture.
- Séparer la logique métier des interfaces, de la caméra, du stockage, du réseau et des modèles.
- Injecter les dépendances aux frontières pour permettre des tests simples et déterministes.
- Encapsuler les invariants ; éviter l'état global mutable et les effets de bord cachés.
- Favoriser la composition et des interfaces adaptées au besoin réel.
- Appliquer simplicité et YAGNI : créer une abstraction lorsqu'un besoin concret la justifie.
- Réduire la duplication de connaissances sans fusionner artificiellement des comportements distincts.
- Traiter explicitement les erreurs attendues ; fournir un contexte utile aux erreurs inattendues.
- Ne pas avaler les exceptions, retourner un succès trompeur ou utiliser une valeur ambiguë.
- Libérer les ressources et gérer annulation, concurrence et cycle de vie aux frontières concernées.
- Écrire des commentaires pour les raisons et contraintes ; supprimer le code mort et les TODO vagues.
- Ne pas journaliser de secrets, de photos, d'embeddings ou d'autres données personnelles.
- Limiter le refactoring au changement utile ; isoler les transformations importantes dans un ticket dédié.
- Utiliser le formateur du projet et éviter les débats de style déjà réglés par l'outillage.

## TDD obligatoire pour le comportement logiciel

Pour chaque incrément de comportement :

1. **Red** : écrire un test décrivant le comportement attendu et l'exécuter avant l'implémentation.
   Vérifier qu'il échoue pour la raison fonctionnelle attendue. Un échec d'infrastructure
   ou de compilation sans rapport avec le comportement ne prouve pas le Red.
2. **Green** : implémenter le minimum nécessaire, puis exécuter le test et les tests pertinents.
3. **Refactor** : améliorer la conception en conservant les tests au vert, puis revérifier.

- Pour un bug, commencer par un test de régression qui reproduit le défaut.
- Tester les comportements et invariants, avec cas normaux, limites et erreurs utiles.
- Les tests doivent être lisibles, isolés et déterministes ; contrôler horloge, hasard et entrées externes.
- Simuler les frontières coûteuses ou non déterministes, sans reproduire toute l'implémentation en mocks.
- Ajouter des tests d'intégration pour les contrats entre composants et des tests fonctionnels ciblés
  pour les parcours importants. Les tests unitaires ne suffisent pas pour la caméra et l'appareil.
- Les changements purement documentaires ou de configuration se vérifient avec les validateurs adaptés ;
  ne pas créer de tests artificiels qui recopient leur contenu.
- Un spike exploratoire peut déroger au TDD avec justification, périmètre et durée limités.
  Son code ne devient pas du code de production sans tests et revue adaptés.
- Rapporter les commandes et résultats réels du Red, du Green et des vérifications finales,
  avec codes de sortie ou preuves équivalentes. Ne pas reconstituer un historique TDD fictif.
- Ne jamais supprimer, ignorer ou affaiblir un test pour masquer une régression.

## Mesures et contrôles qualité

Lors du bootstrap de chaque stack, avant de livrer sa première fonctionnalité,
mettre en place les contrôles locaux et leur exécution en CI. Choisir Sonar ou des
outils équivalents selon les langages ; documenter versions, commandes, périmètres
et rapports dans `docs/`. Le socle mobile et ses scripts disposent des contrôles décrits
dans `docs/quality.md`, ainsi que le socle PC ; conserver ces contrôles lors de leurs évolutions.

Les contrôles doivent couvrir :

- Build reproductible, formatage, lint, analyse statique et vérification des types si applicable.
- Tests et couverture des lignes **et** des branches du code de production.
- Complexité cyclomatique et cognitive par fonction, et duplication du code.
- Dépendances : vulnérabilités connues, versions reproductibles et compatibilité des licences.

Seuils initiaux du projet, à traduire dans les configurations des outils :

| Mesure | Règle |
| --- | --- |
| Couverture des lignes | Au moins 80 % du code de production mesurable |
| Couverture des branches | Au moins 80 % des branches mesurables |
| Duplication du nouveau code | Au plus 3 % des lignes analysables |
| Complexité cyclomatique | Au plus 10 par fonction, sinon revue et justification |
| Complexité cognitive | Au plus 15 par fonction, sinon revue et justification |
| Défauts critiques ou de gravité haute | Aucun nouveau défaut confirmé non traité |

- Mesurer la couverture par composant, sur le nouveau code et sur la baseline globale.
  Une moyenne entre langages ne doit pas masquer un composant insuffisamment testé.
- Définir explicitement la référence du nouveau code, les dénominateurs et les exclusions.
- Build, tests, formatage, lint, analyse statique et seuils de couverture/duplication
  doivent faire échouer la CI lorsqu'ils ne sont pas respectés, une fois configurés.
- Les dépassements de complexité déclenchent une revue : extraire une responsabilité utile
  ou documenter pourquoi une extraction rendrait le code moins clair.
- Les exclusions doivent être étroites et justifiées, notamment pour le code généré et tiers.
  Ne pas exclure de logique métier pour améliorer artificiellement les métriques.
- Une métrique non supportée ou sans données est « non mesurée », jamais « conforme ».
  Documenter la limitation, une alternative et le suivi ; bloquer si la preuve est indispensable.
- Ne pas désactiver un contrôle ni abaisser un seuil pour faire passer un ticket.
  Une évolution durable des règles doit être motivée et traitée comme une décision explicite.
- La couverture ne prouve pas la qualité : vérifier que les tests détectent des comportements incorrects.
  Éviter les assertions triviales, les tests miroirs et le découpage artificiel destiné aux métriques.

## Revue, corrections et arbitrage

- Le reviewer examine le diff, les contrats, la pertinence des tests et les preuves d'exécution.
- Chaque problème doit citer le fichier, les lignes utiles, le scénario et l'impact concret.
- Distinguer **bloquant** (bug, sécurité, critère essentiel manquant ou contrôle qualité injustement
  contourné), **amélioration fondée** et **suggestion de style** non bloquante.
- Prévoir deux cycles de correction/revue par défaut, puis un arbitrage de l'orchestrateur.
  Le quota évite les retouches insignifiantes ; il n'autorise pas à livrer un défaut bloquant.
- L'orchestrateur retient les corrections utiles et documente les désaccords.
  Pour une dette secondaire acceptée, conserver motif, impact et action de suivi identifiable.
- Après la revue, le tester valide les critères d'acceptation sur l'état final et recherche
  les cas limites, problèmes d'intégration et régressions pertinents.
- Si le tester trouve un défaut, retourner au coder ; refaire la revue des corrections
  et les validations impactées. Toute modification invalide les preuves devenues obsolètes.
- Le verdict indique clairement : conforme, à corriger ou validation bloquée, avec les preuves.

## Qualité des modèles et des données

- Distinguer tests du logiciel et évaluation statistique des modèles.
- Séparer entraînement, validation et test par séance ou période ; traiter doublons et rafales
  pour éviter les fuites. Le test final ne sert pas au réglage des modèles ou des seuils.
- Évaluer les inconnus et identités incertaines, y compris avec un seul individu enregistré.
- Rapporter résultats par personne/chat et conditions pertinentes, avec effectifs et limites.
- Versionner les références des jeux de données, modèles, prétraitements, paramètres de décision,
  graines aléatoires et environnements pour rendre les expériences reproductibles.
- Vérifier la cohérence des prétraitements et exports entre ordinateur et téléphone.
- Mesurer latence, mémoire et, lorsque pertinent, batterie sur un appareil identifié.
  Définir les budgets et critères IA dans les tickets concernés avant leur validation.
- Garder photos personnelles, embeddings, secrets et jeux de données sensibles hors de Git.
  Utiliser des fixtures synthétiques ou autorisées pour les tests et vérifier les licences.
- Ne pas transférer automatiquement les données personnelles vers un service externe.

## Définition de terminé et restitution

Un ticket est terminé lorsque :

- Les critères d'acceptation sont satisfaits et le périmètre demandé est livré.
- Les tests pertinents et contrôles qualité applicables passent sur l'état final.
- La revue indépendante n'a plus de problème bloquant et le tester a validé les critères.
- Les métriques disponibles, preuves TDD applicables et limites sont rapportées honnêtement.
- La documentation et les décisions utiles sont actualisées ; les dettes acceptées ont un suivi.

La synthèse de l'orchestrateur indique changements, validations, métriques et limites matérielles.
Une validation indispensable impossible donne un statut bloqué ou incomplet, jamais une réussite.
