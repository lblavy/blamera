# ADR 0003 — segmentation locale des images importées

26 octobre 2026. **Décision retenue ; revue et validation PC indépendantes conformes, APK installé.**
Le fonctionnement0.3.0 est accepté par l’utilisateur avec réserves qualitatives ; quatre
mesures applicatives initiales sont disponibles. Le complément0.3.1 a passé revue et
tester PC puis installation principale ; l’utilisateur confirme « Contours améliorés
et bien placés ». La partie image est validée, la caméra reste ouverte.
Chauffe et budgets restent ouverts.
Périmètre : M1-03 et partie image de M1-06, sur la branche locale `codex/m1-image-segmentation`.

## Décision et raisons

Utiliser le YOLO11n-seg du [spike CPU](../validation/m1-01.md), export ONNX statique FP32
opset 17, avec **ONNX Runtime Android 1.31.0**, provider CPU explicite. Les tests Windows
utilisent l'artefact JVM de même version. Réutiliser l'export déjà mesuré évite de changer
simultanément modèle, conversion et runtime. LiteRT reste une alternative non comparée
expérimentalement ; YOLO26n-seg et RF-DETR restent des candidats de repli, sans deuxième
installation. Aucune supériorité générale ni accélération matérielle n'est établie.

Le modèle contrôlé fait 11 720 021 octets ; son SHA-256 est dans
`config/vision-model.properties`. `prepareVisionModel` vérifie présence, taille et empreinte
avant copie vers les assets générés. Les poids restent hors Git. La date d'export étant
volatile, le réexport temporaire restaure uniquement cette date à sa valeur historique,
puis exige ce hash exact. Cette [recette vérifiée sous Windows](../../experiments/m1-01/README.md)
reconstitue un modèle manquant sans remplacer l'original existant ; toute autre divergence
bloque la reconstruction. Pas de téléchargement à l'exécution.

## Contrat de calcul et affichage

- Aperçu orienté de M1-02, RGB float32 planaire, interpolation bilinéaire et letterbox
  640² avec remplissage 114. Sorties obligatoires `[1,116,8400]` et `[1,32,160,160]`, finies.
- Confiance initiale **0,35**, NMS par classe **0,5** ; au plus **300** candidats puis
  **20** détections. Ces seuils sont des réglages initiaux, sans calibration statistique.
- Masque calculé depuis les 32 coefficients/prototypes, seuil logit > 0, recadré à la
  boîte et à l’image utile. Le masque binaire160² reste inchangé pour la comparaison
  NASA avec PyTorch640² (IoU>=0,95). Depuis0.3.1, le trait est l’isocontour interpolé
  du champ de logits décrit ci-dessous, au plus8 000 segments par objet. Un dépassement
  est une erreur récupérable ; aucun rectangle ou aplat ne remplace le masque.
- Projection inverse vers le bitmap puis même centrage/échelle que `ContentScale.Fit`
  dans une zone de 320 dp. Traits et catégories françaises suivent l'image, avec clipping.
  Les catégories COCO génériques ne sont jamais des identités personnelles.

## Ressources, confidentialité et limites

L'analyse est explicite, hors thread UI ; un mutex partagé entre instances sérialise
le calcul natif même si une Activity annulée n'a pas fini. Une nouvelle photo confirmée
réinitialise le résultat ; une annulation/erreur d'import conserve la précédente.
Les permissions réseau ajoutées par le manifeste ORT sont explicitement retirées avec
[`tools:node="remove"`](https://developer.android.com/build/manage-manifests#node_markers),
puis vérifiées sur le manifeste fusionné et l’APK final.
Les résultats ne sont pas sauvegardés : après recréation, réimport de l'URI puis analyse
manuelle. Aucun historique, copie persistante, permission caméra/media globale ou Internet.

Une session CPU est créée par analyse (2 threads intra-op, 1 inter-op), puis fermée.
Résultat et tenseur sont fermés avant session, puis options ; l'environnement ORT unique
appartient au processus, sa fermeture étant un no-op depuis ORT 1.11. La télémétrie ORT
est désactivée. [API environnement](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtEnvironment.html),
[durée de vie des options](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtSession.SessionOptions.html).
Ce cycle simple privilégie la libération des ressources au cache de session ; son coût
sur ARM doit encore être mesuré. Les latences Windows ne prédisent pas celles du Xiaomi.

Le tag `BlameraVision` ne contient que provider, taille modèle et durées après analyse,
éventuellement PSS du processus courant positif (échantillon, pas pic). Aucune URI,
photo, sortie du tenseur ou identité n'est journalisée. Téléphone : manipulations manuelles
et opérations limitées par AGENTS ; aucun test instrumenté ni lancement automatique.

## Licences

Le runtime est [MIT, version 1.31.0](https://github.com/microsoft/onnxruntime/blob/v1.31.0/LICENSE).
Le checkpoint officiel YOLO11n-seg et sa provenance sont conservés dans le manifeste du
spike ; notice et texte AGPL-3.0 sont embarqués avec l'APK. La lecture indépendante des
[§2 et §13 AGPL](https://github.com/ultralytics/ultralytics/blob/main/LICENSE) distingue
l'usage personnel non transmis et l'interaction réseau : aucun achat ni publication
n'est demandé dans le périmètre privé local actuel. La licence MIT des sources propres
reste intacte. Réexaminer les obligations avant distribution ou backend.

## Validation et suite

Voir le [guide de reconstruction](../image-segmentation.md) et les
[preuves M1-03](../validation/m1-03.md). M1-01 attend encore les mesures téléphone ;
M1-06 reste partiel tant que la caméra n'a pas ses contours. Aucun budget de latence,
mémoire, chauffe ou qualité générale n'est inventé à partir de la seule fixture NASA.

## Complément M1-06 image — 0.3.1

Le retour utilisateur signale des contours crénelés. Remplacer les arêtes des cellules
par marching squares au niveau logit0 : valeurs aux centres `(x+0,5)*640/160`,
interpolation linéaire des intersections, sans filtre décoratif ni modification du
masque binaire, des poids, des scores ou de la NMS. Les logits restent de résolution160² ;
cette géométrie plus régulière ne crée pas de nouveaux détails reconnus par le modèle.

Dans les deux cas diagonaux ambigus, le positif strict est4-connexe et le fond8-connexe :
les deux îlots positifs restent séparés, règle déterministe. Les trous et composants
restent des courbes distinctes. Le domaine est l’intersection boîte/image utile ;
interpolation bilinéaire des valeurs aux limites puis coordonnées de bord dupliquées
avec un anneau extérieur négatif. Cela ferme le contour au bord exact du domaine,
sans fermer arbitrairement un trou intérieur. Projection inverse/Fit et clipping inchangés.
Grille bornée160², axes au plus164 échantillons avec anneau,20 objets et8 000 segments/objet.

Implémentation locale indépendante ;
[source primaire sur interpolation et ambiguïté](https://scikit-image.org/docs/stable/api/skimage.measure.html#skimage.measure.find_contours).
Aucun ajout de scikit-image ou autre bibliothèque.
[Tests et figures publiques avant/après](../validation/m1-06-image.md). Le routage synthétique
15→Chat/16→Chien est vérifié ; la confusion observée n’entraîne aucun réglage du modèle,
faute d’image/scores examinés ou d’évaluation statistique.
