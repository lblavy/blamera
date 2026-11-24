# Cible automatique selon la sélection — M2-09-UI-01

Version 0.14.1/code27, validée sur PC ; parcours essentiel accepté sur Xiaomi. Baseline :
`75466b8448a7349623de4273a34797e627908919`.

Après la 0.14.0, l'utilisateur juge l'interface meilleure et trouve les boutons
Cadrage/Objet redondants avec la sélection dans l'image. Supprimer ces deux
contrôles et déduire la cible de la sélection réelle.

- Un objet détecté est sélectionné : son recadrage avec 5 % de contexte et la
  consigne Objet sont utilisés.
- Aucun objet n'est sélectionné : les pixels du cadrage visible et la consigne
  Cadrage sont utilisés, même avec zéro détection ou après un échec d'analyse locale.
- Un second appui sur l'objet ou un appui dans une zone vide désélectionne.
  Cela permet notamment d'envoyer une plante entière autour d'un pot sélectionné.

Analyser, ChatGPT et Lens gardent la même cible. Le nom de l'objet ou « Cadrage
visible » indique passivement le périmètre ; aucune étape de confirmation n'est
ajoutée. Placer la configuration avec cette indication pour éviter une ligne vide
après suppression des boutons. Navigation d'en-tête et barre de traitements restent
inchangées. Les [consignes et le contrat d'export](photo-actions.md) sont conservés.

## Cycle de vie et erreurs

Un changement réel de sélection invalide l'ancienne image préparée, annule son
opération et rejette les fragments tardifs. Désélectionner utilise le cadrage
actuel, sans restaurer un ancien JPEG. Un appui vide alors que rien n'est sélectionné
ne change pas la cible et ne doit pas effacer gratuitement une réponse.

Le zoom/déplacement ne change pas implicitement la sélection. En cadrage, un geste
changeant les pixels invalide le résultat ; avec un objet sélectionné, le rectangle
d'objet reste la cible. Une rotation conserve l'opération et le snapshot ; le
prochain appui recalcule le cadrage si aucun objet n'est sélectionné.

Une sélection dont la boîte est invalide ne doit jamais provoquer un repli
silencieux vers l'image entière. Aucun envoi automatique, nouveau prompt, endpoint,
modèle, moyen de paiement ou changement des préférences persistées.

TDD sur sélection/désélection, appui vide inchangé, absence des contrôles redondants,
annulation et image réellement transmise. Conserver les régressions géométrie,
rotation, Lens et réglages ; revue et testeur indépendants, qualité locale avant
installation du seul APK principal. Manipulations du téléphone par l'utilisateur.
