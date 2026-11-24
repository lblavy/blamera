# ADR 0005 — analyse caméra locale sur demande

Décision du 26 octobre 2026, état actualisé le 27 octobre : développement, revue ciblée
et tester PC conformes ; APK principal installé, parcours manuels déclarés OK avec réserves. Branche locale
`codex/m1-live-segmentation`, référence de nouveau code `57a1192`.

## Décision

Conserver CameraX 1.6.2, YOLO11n-seg ONNX et ORT CPU 1.31.0. Réutiliser le vrai
`VisionAnalyzer`/`OnnxImageAnalyzer`, les seuils 0,35/0,5 et les contours interpolés
existants. Aucun nouveau modèle, accélérateur, identité, capture, gel ou service.

L’utilisateur active la caméra, puis démarre/arrête l’analyse séparément. Le Preview
reste lié lorsque l’analyse s’arrête ; les résultats sont alors effacés. L’analyse
n’est pas sauvegardée : pause ou recréation → désactivée, redémarrage explicite.

Un même `UseCaseGroup`, avec le `ViewPort` de `PreviewView` FIT_CENTER, possède Preview
et ImageAnalysis. Demander 640×480 via ResolutionSelector, fallback proche inférieur
puis supérieur ; refuser un buffer dépassant 4 millions de pixels. Format RGBA : un
plan R,G,B,A, lecture tenant compte du rowStride et du cropRect. Bitmap appartenant
au traitement, crop puis rotation horaire imageInfo.rotationDegrees, recyclé après
le retour effectif du moteur natif. Ne pas recycler pendant une inférence annulée.

`ImageProxyTransformFactory` avec crop et rotation activés produit le repère du bitmap
croppé/rotaté. `CoordinateTransform` le projette vers `PreviewView.outputTransform` ;
pas de repli qui étire l’image si cette transformation est absente. Les traits jaunes
partagent le dessin des contours image. Aucun deuxième décalage crop ni rotation UI.
Ces API portent TransformExperimental dans camera-view 1.6.2 ; opt-in explicite aux
seules frontières concernées, sans désactivation de lint. Leur contrat est testé et
le risque d’évolution de l’API reste limité par la version verrouillée.
Les tests PC utilisent des pixels et coins asymétriques, crop décentré, les quatre
rotations et des zones FIT avec bandes horizontales/verticales ; le matériel reste à vérifier.

Deux protections complémentaires : KEEP_ONLY_LATEST côté CameraX ; slot partagé entre
instances avant copie/lancement d’une coroutine et départs espacés d’au moins 1 seconde
sur horloge monotone. Une inférence lente n’accumule pas de tâches. C’est un réglage
provisoire, pas un budget validé sur Xiaomi. Le verrou global natif existant reste en place.

Arrêt, pause, source, fermeture, rotation et resize invalident la génération et effacent
l’overlay. Tous les chemins ferment ImageProxy, y compris rejet et annulation avant
travail ; le slot reste occupé jusqu’au retour natif et au nettoyage. Rotation courante
sur Preview ET ImageAnalysis, sans rebind lors de 90↔270 ; frame de rotation obsolète rejetée.
Une notification Display avec mêmes identité et rotation ne déclenche pas d’invalidation.
Une scène vide remplace les résultats précédents. Erreur visible, arrêt/réessai et Image disponibles.

Les use cases/executor/listeners appartiennent au binding ; aucun `unbindAll`. Caméra
et permissions gardent le contrat précédent. Aucun INTERNET, micro, stockage global,
copie persistante, photo/URI/tensor dans les logs. Durée de dernière analyse affichée :
`LiveResult.millis` chronomètre seulement l’appel suspendu `analyzer.analyze`, après
projection et conversion du bitmap. Il inclut une éventuelle attente du verrou du
moteur ; il exclut conversion, publication/dessin et latence frame→overlay. Ce n’est
ni un temps d’inférence seul ni une mesure de latence bout en bout. Les logs numériques
restent limités à BlameraVision (PSS éventuel = échantillon, pas pic).

## Sources et conséquences

- [Analyse CameraX](https://developer.android.com/media/camera/camerax/analyze) : backpressure et fermeture ImageProxy.
- [Référence ImageAnalysis](https://developer.android.com/reference/androidx/camera/core/ImageAnalysis#OUTPUT_IMAGE_FORMAT_RGBA_8888()) : ordre RGBA explicite (le guide général contient un exemple d’ordre différent).
- [Transformations de sortie](https://developer.android.com/media/camera/camerax/transform-output), [ImageProxyTransformFactory](https://developer.android.com/reference/androidx/camera/view/transform/ImageProxyTransformFactory), [CoordinateTransform](https://developer.android.com/reference/androidx/camera/view/transform/CoordinateTransform).
- Contrats vérifiés sur les classes des AAR 1.6.2 déjà verrouillés ; sources officielles 1.6.2 relues indépendamment par le reviewer.

Les catégories et contours sont approximatifs et retardés par le traitement. Tenir
une scène immobile pour juger leur alignement. L’essai [M1 préparé](../tests/m1-recette.md)
a reçu P*/O*/I* OK déclarés le 27 octobre : deux paysages et contours acceptés.
Rafraîchissement lent en mouvement et détection partielle/erronée restent connus ;
essai inférieur à 5 min sans chauffe ressentie. Prolongé, budgets et hors ligne non
validés. Aucune preuve physique déduite de Robolectric. D-M1-01/D-M1-02 suivent les
améliorations dans le backlog, sans décision de tuning ici. CI distante toujours reportée.
