# ADR 0013 — clôture de M1/M2 et démarrage de M3

1er novembre 2026 — décision explicite de l'utilisateur.

Après le point projet sur Blamera 0.15.1, l'utilisateur demande : « tu peux
clôturer M1/M2. commençons M3 ».

## Décision

Clôturer M1 et M2 sur leurs parcours livrés et acceptés, et commencer M3-01,
la galerie locale des proches sur PC. Cette décision accepte le périmètre actuel :
YOLOE26 Large PF en photo, YOLO11n-seg en direct secondaire, Lens, ChatGPT Plus
et Pl@ntNet Free. Aucun changement de modèle, financement ou téléphone n'est requis.

Les recettes complémentaires et investigations encore ouvertes sont reportées
hors clôture, sans conditionner le début de M3. Les tickets M1-01/M1-05/M1-06,
M1-09 et M2-04 sont clos par cet arbitrage pour les points restants ; il ne
s'agit pas d'une nouvelle validation technique de ces points.

## Limites conservées

- Les mesures isolées ne deviennent pas des budgets validés ni une preuve
  d'endurance, d'absence de chauffe ou de fonctionnement hors ligne sur appareil.
  Suivi M1-09-VAL-01, avec les investigations D-M1-01/D-M1-02 déjà identifiées.
- Les cas réseau/quota/délai/annulation restent testés sur PC ; leur recette
  ciblée réelle n'est pas déclarée exécutée. Suivi M2-04-VAL-01.
- Les quatre événements Blamera confirment publication botanique, préparation
  de prompt enrichi et réponse ChatGPT HTTP200, sans prouver la précision
  statistique de l'espèce. M2-10-QUAL-01 reste une investigation reportée.
- Les dettes UI/tests, comparaison des niveaux de réflexion et nettoyage de
  ressources restent traçables, sans être bloquantes pour ces milestones clos.
- La CI distante reste volontairement reportée. Les restrictions de protection
  du téléphone, la qualité et le TDD des nouveaux développements restent applicables.

Le statut « clos par décision utilisateur » distingue cet ajustement de périmètre
d'un critère effectivement mesuré. Aucune preuve historique n'est réécrite.

## Suite autorisée

[M3-01](../specs/people-gallery.md) démarre sur le socle Streamlit existant :
gestion de personnes, import et prévisualisation de photos, corrections et
stockage local. Les modèles faciaux, embeddings, export/import mobile et
reconnaissance restent M3-02 à M3-06. Les données réelles restent choisies par
l'utilisateur dans l'interface locale ; aucun transfert de photos dans le chat.
