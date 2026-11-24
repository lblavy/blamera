# M1-01 — étude préliminaire des modèles

26 octobre 2026. État : **spike CPU effectué**, aucun modèle ni runtime Android retenu.
Cette note conserve la recherche de l'orchestrateur/reviewer et ses sources primaires.
La comparaison initiale ci-dessous a été suivie d'un essai local YOLO11n-seg isolé,
décrit en fin de note ; aucun poids n'est intégré à l'APK.

| Candidat | Intérêt à vérifier | Point à résoudre avant intégration |
| --- | --- | --- |
| YOLO11n-seg | Variante nano de segmentation d'instances, catégories COCO et masques | Licence des artefacts, export CPU et contrat de sorties sur Android |
| YOLO26n-seg | Variante nano plus récente ; inférence de bout en bout annoncée par l'éditeur | Comparaison réelle avec YOLO11, export/post-traitement et licence |
| RTMDet-Ins-tiny | Segmentation d'instances COCO ; 5,6 M paramètres annoncés | Licence exacte du checkpoint, exports et coût de la chaîne Windows MMCV/MMDeploy |

Sources : [YOLO11](https://docs.ultralytics.com/models/yolo11),
[YOLO26](https://docs.ultralytics.com/models/yolo26),
[exports Ultralytics](https://docs.ultralytics.com/modes/export),
[RTMDet-Ins, documentation versionnée](https://github.com/open-mmlab/mmdetection/blob/v3.3.0/configs/rtmdet/README.md).
Les chiffres éditeur, notamment les latences GPU, ne sont pas des mesures sur ce PC CPU
ou le Xiaomi. Aucun budget FPS, mémoire, chauffe ou qualité n'est fixé ici.

Ultralytics présente ses modèles/code sous AGPL-3.0 ou licence commerciale et revendique
sur sa [page de licence](https://www.ultralytics.com/license) l'ouverture de l'ensemble du
projet dans son offre AGPL. Cette position de l'éditeur est à distinguer des conditions
du texte de licence et des artefacts exacts : elle n'entraîne ici ni achat, publication,
modification de licence du dépôt ou décision automatique d'intégration.
Le [code MMDetection](https://github.com/open-mmlab/mmdetection/blob/v3.3.0/LICENSE)
est Apache-2.0 ; cela ne confirme pas à lui seul la portée de la licence de chaque poids.

Restent à effectuer : cadrer la licence et les artefacts gratuits utilisables, figer leurs
versions/empreintes, vérifier un export CPU reproductible, comparer ONNX Runtime Mobile
et LiteRT selon ce modèle, puis mesurer sur le téléphone dans les opérations autorisées.
Les étapes PC ont désormais une première preuve ; le téléphone reste sans benchmark.
L'import d'image M1-02 est indépendant et ne charge aucun de ces candidats.

## Premier candidat réellement essayé

YOLO11n-seg est retenu comme **référence de spike**, pour tester une voie connue sans
multiplier les combinaisons. Poids officiel 6 182 636 octets, export ONNX statique FP32
11 720 021 octets, entrée 1×3×640×640, sorties 1×116×8400 et 1×32×160×160.
Sur l'image publique NASA astronaut : une personne, score 0,8841, masques PyTorch/ONNX
identiques pour ce cas. Les formes synthétiques produisent deux faux positifs ; aucune
qualité statistique générale n'est déduite de ces deux fixtures.
[Rapport réel](../validation/m1-01.md), [recette, source et lock](../../experiments/m1-01/README.md).

La lecture indépendante du [texte AGPL, §2 et §13](https://github.com/ultralytics/ultralytics/blob/main/LICENSE)
distingue l'œuvre non transmise et l'interaction réseau des affirmations marketing :
l'exploration personnelle locale actuelle n'exige ici ni achat ni publication.
Les notices sont conservées ; cela ne change pas la licence des sources propres du dépôt.
Distribution/backend ultérieurs demanderaient une nouvelle analyse des artefacts et obligations.

YOLO26n-seg reste une alternative nano, sans deuxième essai pour une préférence de récence.
RF-DETR Seg Nano est un repli à examiner si utile : code/poids annoncés Apache-2.0,
mais 33,6 M paramètres annoncés, donc une autre enveloppe CPU à mesurer, sans l'installer ici.
[Licences Roboflow](https://roboflow.com/licensing), [dépôt RF-DETR](https://github.com/roboflow/rf-detr).
