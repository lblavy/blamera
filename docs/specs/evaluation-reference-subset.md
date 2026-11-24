# M3-02-B-01 — cinq références datées, galerie conservée

1er novembre 2026, baseline `beb1e75`, PC 0.5.0.
L'utilisateur ne connaît pas les dates de deux références et demande :
« Avance avec les cinq références datées, sans modifier ma galerie. »

## Périmètre logiciel

- Ajouter une sélection de références propre à l'évaluation, conservée dans sa
  base / ses settings, sans changement des personnes, photos ou confirmations
  de la galerie principale. Aucun modèle, dépendance ou téléphone modifié.
- Par défaut, conserver le comportement précédent : toutes les références
  compatibles et confirmées. Une sélection explicite de photos limite les
  prototypes et les séances exigées aux seules références retenues.
- Une identité sans référence retenue n'a pas de prototype. Une sélection vide,
  une référence sélectionnée supprimée / non confirmée / incompatible ne doit
  pas se transformer silencieusement en une sélection de remplacement.
- Valider le stockage de la sélection (identifiants, doublons, données corrompues),
  la persistance et les erreurs ; ne pas confondre absence de réglage et liste vide.
- Afficher les références disponibles et celles utilisées dans Évaluation, avec
  sélection explicite, compte retenu / disponible et possibilité de revenir à
  toutes. La page Références conserve son comportement accepté.
- Le rapport et son instantané incluent cette politique de sélection ; tout
  changement invalide le statut courant du rapport. La comparaison utilise
  uniquement les vecteurs retenus. Les doublons sont toujours contrôlés contre
  toutes les images de la galerie, y compris les références exclues du calcul.
- Ni réencodage de la galerie ni confirmation automatique d'un visage.
  Réutiliser la protection par transactions et les invariants de M3-02-B.

TDD Red → Green réel : différence prototypes avec référence exclue, conservation
de la galerie, défaut rétrocompatible, sélection vide / disparue / invalide,
persistance, invalidation et parcours UI. Gates PC inchangées, baseline `beb1e75`.
Version cible PC 0.5.1. Un seul propriétaire de couverture à la fois.

## Préparation du corpus personnel autorisée

La sélection concrète des cinq IDs vient de l'audit privé du corpus `resources/`
et de la liaison par digest aux références enregistrées. Garder ces données,
noms, fichiers et dates hors Git et des rapports publics.

Préparer d'abord un plan local réduit pour Calibration et Test final réservé,
avec les cinq dates EXIF comme séances de référence, séances disjointes entre
lots, personnes inconnues distinctes et présence de connus / inconnus dans chaque
lot. Exclure tous les dossiers `*models` et les doublons avec toute la galerie.
Un découpage par séances peut être non chronologique si cela préserve la séparation
et la représentation des identités ; ne jamais optimiser le choix par score de
reconnaissance ou sélectionner le plus grand visage d'une photo de groupe.

Limiter le premier lot à quelques photos par identité ; utiliser les dates
vérifiées plutôt que les dates de messagerie. Les photos avec plusieurs visages
restent admissibles, mais nécessitent une sélection humaine de la cible.
Revalider chemins résolus, sources et digests avant tout import ; les anciens
comptes de détection par chemin ne sont pas une garantie d'immuabilité.

Après revue du code et du plan, importer des copies normalisées dans la seule
base d'évaluation et préparer les détections sans choisir ou encoder les cibles.
Ne calculer aucun score d'identité sur le Test final réservé. Les confirmations
de cibles d'évaluation restent à l'utilisateur avant le rapport exploratoire.

Mesurer avant / après l'intégrité de la galerie principale, dont les métadonnées,
les photos et confirmations. Les données originales `resources/` restent en
lecture seule. Aucun import ou effacement de référence principale n'est autorisé.
