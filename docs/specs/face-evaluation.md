# M3-02-B — évaluation exploratoire de la reconnaissance faciale sur PC

1er novembre 2026, baseline `ea5fed3`, PC actuel 0.4.0.
L'utilisateur demande l'implémentation après avoir accepté la préparation de ses
références. Le modèle reste YuNet / ArcFace ResNet50 OpenCV CPU, profil inchangé.

## Livrable de ce lot

Un parcours « Évaluation » de l'outil PC : créer un corpus distinct de la galerie
de références, annoter l'identité attendue et les séances, préparer les visages,
exécuter une comparaison exploratoire et lire les résultats par image / identité.
Réserver des images de test final sans utiliser leurs résultats pour régler les
seuils. Ce lot prépare la calibration ; il ne déclare aucun seuil fiable ni
une précision obtenue sur les proches sans retour et mesures représentatives.
Android, export, entraînement et optimisation automatique des seuils sont exclus.

## Données et parcours

- La galerie de référence acceptée reste intacte. Les nouveaux cas d'évaluation
  sont persistés dans une zone privée distincte sous la même racine de données
  ignorée par Git. Aucun ajout de probe parmi les références de reconnaissance.
- Navigation simple entre « Références » et « Évaluation », en conservant le
  parcours Personnes / Chats existant. L'écran explique les actions utiles,
  sans avertissements répétés ou détails d'implémentation dans chaque carte.
- Import explicite JPEG/PNG/WebP, limites et normalisation déjà utilisées pour
  la galerie (20 fichiers, 20 Mio chacun, 24 millions de pixels). Choisir une
  identité connue de la galerie ou « Personne inconnue », le lot « Calibration »
  ou « Test final réservé », et un nom de séance (même moment / rafale).
- Les images normalisées identiques à une référence ou à un autre cas du corpus
  sont refusées, même entre identités ou lots. L'étiquette, le lot et la séance
  restent corrigeables ; suppression volontaire confirmée. Pas d'import au rerun.
- Annoter également les séances des photos de référence compatibles et confirmées,
  par sélection / affectation en lot si possible. Conserver la galerie existante,
  sans réinitialisation. Un changement des pixels ou d'affectation invalide une
  ancienne annotation liée à cette source ; un renommage ne change pas l'identité.
- Interdire une même séance entre références et évaluation ou entre Calibration
  et Test final. Les noms de séance sont normalisés (espaces / casse) pour cette
  vérification. Cette annotation manuelle ne détecte pas magiquement les rafales
  ni les dérivés d'une même photo ; la recette demande des séances distinctes.
- Préparation explicite zéro / un / plusieurs visages, choix humain du bon visage
  et enregistrement de son empreinte, en réutilisant la chaîne vérifiée. Même
  avec un seul visage, confirmer le choix. Ne pas encoder tous les passants.
  Les détections / choix / empreintes de probes restent séparés des références.

## Comparaison et décisions exploratoires

- Utiliser seulement les références compatibles et confirmées de chaque personne.
  Construire un prototype par identité : moyenne des vecteurs unitaires puis
  normalisation L2. Chaque identité a un seul prototype, quel que soit son nombre
  de références. Valider dimension, valeurs finies et norme ; pas d'identité forcée.
- Comparer par cosinus avec les prototypes. Afficher meilleur candidat et score
  comme une similarité, jamais comme un pourcentage de certitude. Versionner cette
  règle et les seuils dans le rapport avec le profil modèle et les références.
- Paramètres exploratoires éditables : seuil de rejet 0,30, seuil d'acceptation
  0,60, marge entre deux identités 0,05. Ce sont des valeurs de départ de l'outil,
  sans prétention de calibration. Seuils finis, -1 ≤ rejet < acceptation ≤ 1,
  0 ≤ marge ≤ 2, booléens refusés. Documenter les égalités aux frontières.
- Score sous le seuil de rejet : « Inconnu ». Dans la bande entre les seuils :
  « Incertain ». Au-dessus du seuil d'acceptation : identité candidate si la marge
  avec le second est suffisante ; sinon « Incertain ». Avec une seule identité,
  appliquer les seuils absolus, sans inventer de second score ou forcer le nom.
- Calcul uniquement sur action explicite « Évaluer la calibration ». Les cas de
  Test final réservé ne produisent pas de prédiction ou métrique dans ce lot.
  La calibration / le gel de la politique et l'ouverture du test final feront
  l'objet de l'incrément suivant ; aucune sélection du meilleur seuil sur le test.
- Vérifier séparation des séances et sources, présence de références pour toutes
  les identités attendues connues, et préparation de tous les cas de Calibration.
  Un cas sans visage a un état non exploitable explicite. Plusieurs visages sans
  choix ou cas non préparé empêchent un rapport présenté comme complet.
- Rapporter effectifs, résultats par image et personne / inconnus, bonnes identités,
  mauvais noms (y compris un nom attribué à un inconnu), rejets, incertains et cas
  non exploitables. Toujours rendre les dénominateurs visibles ; ne pas gonfler
  le résultat en masquant les échecs de préparation ou les cas manquants.
- Conserver un rapport local après rechargement, avec instantané reproductible de
  la politique, sources / séances et références. Ne pas l'afficher comme courant
  si références, personne, annotations, sélection, modèle ou paramètres changent.
  Un changement pendant un calcul ne doit pas publier un résultat sous une autre
  identité ou un rapport trompeur. Un échec conserve les données déjà valides.

## Logiciel, données privées et qualité

L'agent ne lit ni la galerie personnelle ni le corpus de l'utilisateur et ne
pilote pas son navigateur contenant ses photos. Fixtures synthétiques ou NASA
autorisées et base QA isolée uniquement. Aucun téléphone, cloud, crédit, nouvelle
dépendance ML ou changement des poids. Le site utilisateur est arrêté avant
écriture du checkout actif, puis redémarré après revue et validation indépendantes.

TDD réel par incrément avant implémentation : import / correction / séparation,
persistance et erreurs, calcul des prototypes / décisions aux limites, une ou
plusieurs identités et inconnus, invalidation et intégration UI sans calcul au
rerun. Doubles aux frontières ML ; smoke réel CPU autorisé sur fixtures publiques.
Pas de Red de compilation / infrastructure présenté comme preuve fonctionnelle.

Gates PC inchangées : format, Ruff / mypy strict, complexités 10 / 15, couverture
globale et nouvelle ≥80 % lignes / branches par composant, duplication ≤3 %,
lock / licences / vulnérabilités. `scripts/quality_pc.py --base-ref ea5fed3` avec
le Python `pc/.venv/Scripts/python.exe`. Archiver la qualité avant le testeur.
Un seul propriétaire du rapport de couverture à la fois ; tests dans leurs bases
temporaires, pas de modification des sources entre revue et validation.

La prochaine validation utilisateur consiste à retrouver ses références, annoter
les séances, importer quelques photos nouvelles dont un inconnu, vérifier les
préparations et les résultats exploratoires. M3-02 reste ouvert après ce lot.
