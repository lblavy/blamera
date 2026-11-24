# ADR 0015 — profil de préparation faciale et priorité qualité

1er novembre 2026 — M3-02-A, décision provisoire pour un incrément vérifiable.

## Besoin et comparaison

Préparer localement les visages de la galerie PC, en gardant une voie vers Android.
La chaîne distingue détection, alignement, extraction d'empreinte et décision
d'identité. Le nom appartient à la galerie de l'utilisateur ; le modèle produit
une représentation numérique permettant de comparer les visages.

Trois profils sont évalués dans un environnement CPU isolé : SFace, ArcFace sur
ResNet50 et ArcFace sur MobileFaceNet. ResNet50/MobileFaceNet sont des architectures ;
ArcFace/SFace sont des méthodes d'apprentissage. Le poids SFace d'OpenCV utilise
lui-même MobileFaceNet : ces noms ne désignent pas trois tailles d'un même modèle.

| Profil | Intérêt | Réserve pour Blamera |
| --- | --- | --- |
| YuNet + SFace OpenCV | Chaîne documentée, poids ONNX, licences explicites et API d'alignement/extraction | Qualité sur les proches et latence téléphone encore à mesurer |
| ArcFace ResNet50 InsightFace | Challenger qualité, bons résultats publiés sur plusieurs protocoles | Poids plus volumineux, coût CPU à mesurer ; recherche non commerciale uniquement |
| ArcFace MobileFaceNet InsightFace | Challenger compact pour le mobile | Qualité publiée inférieure au grand modèle sur certains tests ; même restriction des poids |
| AdaFace | Pertinent pour variations de qualité/flou/résolution | Prétraitement et licence dépendent des poids exacts ; second challenger si nécessaire |
| FaceNet/InceptionResnetV1 | Référence historique et implémentations disponibles | Conversion mobile et provenance des poids à contrôler ; moins prioritaire pour ce premier lot |

Les scores LFW, parfois saturés et issus de protocoles/poids différents, ne
permettent pas une promesse de précision sur les proches. Les mesures sur deux
portraits autorisés et leurs variantes sont des contrôles de fonctionnement,
pas une calibration ni une évaluation statistique des inconnus.

## Premier incrément PC 0.3.0

Retenir provisoirement YuNet 2023mar et SFace 2021dec OpenCV Zoo avec OpenCV
4.13.0.92 CPU, notices et SHA-256 figés. YuNet repère le visage et cinq points,
SFace extrait 128 valeurs après alignement 112×112. La confirmation humaine de
la bonne personne précède l'enregistrement d'une référence normalisée.
Ce choix permet de préparer et vérifier les données pendant la comparaison,
sans consacrer une supériorité de qualité de SFace sur les autres candidats.

SFace doit recevoir le prétraitement prévu par son graphe ; les poids ArcFace
évalués utilisent un autre centrage/échelle. Les empreintes de profils différents
ne sont jamais mélangées : modèle, poids, alignement, source et dimension sont
versionnés avec les références. Un modèle ultérieur peut imposer de les recalculer.

Les notices de YuNet déclarent tous les fichiers du dossier MIT, celles de SFace
Apache-2.0. Le code InsightFace est MIT, ses poids publics sont limités à la
recherche non commerciale : ils servent ici au comparatif privé, pas au runtime
du premier lot. Aucun achat, cloud ou crédit n'est nécessaire.

## Validation suivante

### Orientation utilisateur après le premier lot

Le 1er novembre 2026, l'utilisateur précise : « je préfère avoir un modèle
performant (restnet?) ». La priorité devient la qualité de reconnaissance plutôt
que la compacité. ArcFace ResNet50 (`w600k_r50.onnx`, pack `buffalo_l`) devient le
profil PC 0.4.0 ; SFace reste une référence de comparaison historique.
Les résultats publiés d'InsightFace motivent ce candidat, sans prouver sa
supériorité sur les proches de l'utilisateur. Le changement ne constitue pas
une calibration ni un déploiement Android.

Conserver les personnes et photos, isoler le nouveau profil à 512 dimensions et
demander une nouvelle préparation explicite. Ne jamais interpréter les anciennes
128 valeurs de SFace comme une référence ArcFace. Le poids, l'alignement et le
prétraitement exacts sont figés avant activation ; aucun calcul ou transfert
réseau automatique des photos n'est introduit. Les poids InsightFace restent
réservés à la recherche non commerciale dans cet essai privé gratuit.

Le profil PC 0.4.0 utilise OpenCV DNN CPU pour l'inférence ResNet50, sans ajouter
ONNX Runtime au runtime de l'application. SFace reste une ressource d'alignement
via `FaceRecognizerSF.alignCrop` ; sa méthode `feature` n'est plus appelée.
Conserver ce poids supplémentaire d'environ 39 Mo évite de réimplémenter la
transformation de similarité native. Le graphe ResNet50 reçoit RGB/NCHW 112×112,
centré et réduit par `(pixels−127,5)/127,5`, puis produit une empreinte normalisée
L2 de 512 valeurs. Le profil versionné change ; aucune conversion des anciennes
empreintes n'est possible.

Le manifeste conserve les modèles et notices OpenCV au commit déjà figé et les
conditions InsightFace au commit `3e6486942a1be2da0e5b475fac375ea73264bd21`
correspondant à `v0.7`. L'archive officielle `buffalo_l.zip` et son seul membre
de reconnaissance sont vérifiés indépendamment avant activation du poids.

### Évaluation représentative à poursuivre

La [comparaison locale exécutée](../research/m3-face-evaluation.md) conserve les
tailles, latences et contrôles numériques des trois poids. Elle utilise deux
portraits NASA et leurs dérivés ; les trois candidats passent ce diagnostic.
ResNet50 reste prioritaire comme challenger qualité, même si son coût CPU est
supérieur. La revue indépendante du spike ne constate pas de classement abusif.

Faire vérifier par l'utilisateur les visages détectés et confirmés dans sa galerie
locale. Organiser ensuite des références, validations et tests distincts par séance,
avec personnes inconnues, pour choisir les seuils connu/inconnu/incertain.
Le seuil de démonstration publié n'est pas une calibration des proches.

Le runtime Android dispose de voies OpenCV Java et ONNX Runtime ; cela donne une
possibilité d'intégration à vérifier en M3-03/M3-04. Ni les performances PC ni le
format ONNX ne prouvent le fonctionnement exact sur Xiaomi. Android ne change
pas dans M3-02-A ; la reconnaissance et l'export viendront ensuite.

## Sources primaires

- [YuNet et sa licence](https://github.com/opencv/opencv_zoo/tree/48b10fa505aeb51fbd127fc60a5c660040e823b7/models/face_detection_yunet).
- [SFace, architecture et licence](https://github.com/opencv/opencv_zoo/tree/48b10fa505aeb51fbd127fc60a5c660040e823b7/models/face_recognition_sface).
- [InsightFace : profils, résultats publiés et restriction des poids](https://github.com/deepinsight/insightface/blob/master/model_zoo/README.md).
- [Prétraitement SFace OpenCV](https://github.com/opencv/opencv/blob/4.x/modules/objdetect/src/face_recognize.cpp).
- [Prétraitement ArcFace](https://github.com/deepinsight/insightface/blob/master/python-package/insightface/model_zoo/arcface_onnx.py).
- [AdaFace](https://github.com/mk-minchul/AdaFace), [poids CVLFace officiels](https://huggingface.co/minchul/cvlface_adaface_ir101_webface12m).
- [FaceNet PyTorch](https://github.com/timesler/facenet-pytorch).
- [API OpenCV Android](https://docs.opencv.org/4.x/javadoc/org/opencv/objdetect/FaceRecognizerSF.html), [ONNX Runtime Mobile](https://onnxruntime.ai/docs/get-started/with-mobile.html).
