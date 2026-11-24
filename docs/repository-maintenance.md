# Entretien du dépôt local

Blamera conserve dans Git les sources, tests, configurations, notices, protocoles
et bilans de validation. Les photos personnelles, galeries et exports privés,
clés, pièces jointes de conversation, poids téléchargés, environnements locaux
et APK restent hors Git.

## Nettoyage du 2 novembre 2026

Après clôture de M3, les anciennes livraisons APK, environnements exploratoires
de M1 et du comparatif facial, caches de calcul, intermédiaires de compilation
et snapshot mémoire de l'émulateur ont été supprimés. Les entrées flottantes
temporaires des comparaisons M1 et l'archive téléchargée ADE ont aussi été retirées ;
les résultats, protocoles, annotations utiles et données extraites sont conservés.

Les APK intermédiaires des validations M3-04/M3-05 ne sont plus conservées.
Leurs reçus et empreintes restent des preuves historiques de leur construction,
pas une promesse de disponibilité de tous les binaires. Restent disponibles
localement l'APK personnelle 0.18.0 et l'APK générique de la validation finale M3-05.

Les photos de `resources/`, les données de `pc/data/`, les modèles, l'environnement
PC courant et les outils nécessaires à la qualité sont conservés. L'émulateur
conserve sa configuration et son disque ; seul son snapshot est retiré.
Le JRE Temurin utilisé par les tests ONNX reste sous `.tools/m1-ort-preflight/`.

Le plan exact, l'inventaire et les vérifications de conservation sont enregistrés
localement sous `build/reports/validation/repo-cleanup/`, hors Git. Aucun nettoyage
n'est effectué sur le téléphone.

## Pour les prochains nettoyages

- Examiner les chemins et leurs dépendances avant de supprimer ; ne pas lancer
  une suppression globale des fichiers ignorés.
- Garder l'APK courante, les bilans et preuves utiles. Les caches et environnements
  exploratoires peuvent être retirés lorsqu'ils sont reproductibles et inutilisés.
- Ne pas supprimer une galerie, des références personnelles, un modèle utilisé ou
  un outil courant sous prétexte qu'ils sont ignorés par Git.
- Vérifier les chemins absolus avant une suppression récursive et contrôler le
  contenu du commit avant publication.
