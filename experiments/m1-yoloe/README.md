# Spike YOLOE nano — 27 octobre 2026

Exploration CPU bornée (~60 min premier candidat), exception TDD d’AGENTS.md, **code
non production**. Une seule famille YOLOE26n PF, trois comparaisons initiales, puis
UN complément NMS agnostique autorisé après résultats et UN export de déploiement.
Aucune modification app/APK, poids baseline, système, compte, cloud ou téléphone.

## Sources figées

`corpus.json` fige AVANT prédiction16 images, IDs/annotations/hashes, catégories/synonymes,
paramètres et exclusions. `manifest.json` conserve versions, assets, licences/notices
et hashes. Résultats : `build/reports/validation/m1-yoloe/` ; poids et exports :
`models/m1-yoloe/`, tous ignorés. Aucune photo/annotation/mosaïque à redistribuer.

12 COCO128-seg (159 instances), issus de train2017 potentiellement vu par les modèles :
diagnostic, pas test indépendant ni AP/mAP officiel. Photos Flickr sous leurs droits
individuels (non audités un à un), annotations CC BY4, licence du convertisseur distincte.
Deux ADE20K (GT sémantique, union de catégorie, pixel 0 ignoré) : recherche noncommerciale,
photos droits tiers, annotations BSD3. NASA domaine public sans GT + négatif synthétique.

La baseline contrôlée M1-01 reste un prérequis (`../m1-01/README.md`) et n’est jamais
réexportée ici. SHA ONNX `38325721a41fcd732b0a30512ffa8d564b1a8a74ca8c1d6b7e7f37609b2d321b`,
PT `55ed65c56c91713d23e8402371c6c49a6fd84f257f7dce452e8d70e41dcbe152`.
Le champ date des nouveaux ONNX est volatile : hashes observés, pas promesse bit à bit universelle.

## Reproduire sur ce Windows depuis la racine

Run réel : environnement M1-01 réutilisé sans installation/mise à jour. Reconstruction
isolée : Python 3.12.14, torch 2.14.1+cpu/torchvision 0.29.1+cpu, ultralytics 8.4.190,
onnx 1.23.2/ORT 1.31.0 et transitifs épinglés par le lock copié (47 paquets).

```powershell
$env:UV_CACHE_DIR = Join-Path (Get-Location) '.tools/m1-yoloe/uv-cache'
$env:UV_PYTHON_INSTALL_DIR = Join-Path (Get-Location) '.tools/python'
$env:UV_PROJECT_ENVIRONMENT = Join-Path (Get-Location) '.tools/m1-yoloe/.venv'
$env:UV_PYTHON_NO_REGISTRY = '1'
$env:UV_PYTHON_INSTALL_BIN = '0'
.\.tools\uv\0.13.5\uv.exe sync --project experiments/m1-yoloe --locked --python 3.12.14 --managed-python
$yoloePython = '.tools/m1-yoloe/.venv/Scripts/python.exe'
& $yoloePython experiments/m1-yoloe/prepare_corpus.py
& $yoloePython experiments/m1-yoloe/export_pf.py
& $yoloePython experiments/m1-yoloe/run_evaluation.py
& $yoloePython experiments/m1-yoloe/checks.py
& $yoloePython experiments/m1-yoloe/summarize.py
& $yoloePython experiments/m1-yoloe/agnostic.py
& $yoloePython -m unittest discover -s experiments/m1-yoloe -p 'test_*.py'
```

Vérifier chaque code de sortie et arrêter sur erreur. `prepare_corpus.py` est la seule
phase réseau explicite ; `--offline` vérifie les fichiers présents. Si une notice distante
change, le hash échoue et ses termes doivent être réexaminés. Sync, auto-install et
ORT telemetry désactivées ; sockets Python bloquées en export/inférence ; caches `.tools`.
Aucun CLIP/encodeur Apple téléchargé ; nano texte inspecté, non évalué.

## Protocole

Baseline : conf0,35/NMS par classe IoU 0,5/top 300/max 20/640². PF commun : réseau 4 585,
max des synonymes vers 80 AVANT argmax/NMS/cap, pas un réseau 80 reparamétré. 69 noms
stricts et alias parfois approximatifs (luggage/phone/houseplant), tous figés avant résultats.
PF complet :4585 labels, dont synonymes et concepts non physiques, pas 4585 objets corrects.

Matching 1 à 1 classe+IoU boîte≥0,5 score décroissant, TP/FP/FN et IoU masque sur matches ;
IoU aussi sur les mêmes GT matchés entre modèles. Hors80 ignorés dans les métriques
communes car GT incomplet pour eux. ADE : union des labels EXACTS tree/building/road,
pas localisation de tous leurs sous-types. Aucun masque modèle utilisé comme GT.

ORT CPU 2/1, 3 warmups puis10 répétitions/image,16 images, ordre modèles alterné, p95 NumPy
linéaire. Phases Python pre/post/contours séparées (une observation/image), pas Kotlin.
Froid : lecture+hash, session neuve, inférence/post Python,3+10 fois sur 2 cas. RSS partagé
(Torch importé+deux ORT+outputs), échantillon, ni pic ni attributionPF ni mémoireAndroid.

`checks.py` : PT/ORT parité sur 2 cas, tolérances préfixées .02/.005, IoU masque≥.99.
Export compact distinct nms=True/agnosticFalse/conf.35/IoU.5/max 20, sans pré-top 300 :
20×38+protos. Brut sans NMS et compact avec NMS en colonnes séparées, pas pipelines équivalents.
Pas de preuve de baisse de mémoire interne ni de performances Xiaomi.

Amendement après premiers résultats : `agnostic.py`, défaut éditeur confirmé dans
YOLOE.predict8.4.190. UNE passe sur les16 images, sans nouveaux timings160 répétitions.
Protocole/résultats initiaux conservés, aucun meilleur réglage sélectionné a posteriori.

## Preuves

`results.json`, `quality-breakdown.json`, `checks.json`, `export.json`, logs,
`agnostic-amendment.json`, `agnostic-results.json`, `visuals/`, `agnostic-visuals/`.
`summarize.py` répare seulement les panneaux GT ADE/NASA des visuels, sans changer les
pixels des prédictions. Tests utilitaires matching/FN/pixels ignorés et NMS/non carré :
Red fonctionnel puis Green. Autres essais : exception exploratoire + assertions réelles,
pas couverture80/80 ou validation Android. Toute promotion exigera TDD/gates/revue/tester.

[Étude](../../docs/research/m1-yoloe-evaluation.md), [rapport](../../docs/validation/m1-yoloe.md).


## Préparation du diagnostic Android distinct (0.5.1)

`prepare_android.py` prépare un export compact **de mesure** YOLO11n, sans modifier
le ONNX du parcours normal. Il vérifie aussi le PF compact, les checkpoints, la NASA
publique et génère un motif synthétique. Aucun COCO/ADE n’entre dans l’APK.
L’environnement CPU et les téléchargements vérifiés des sections précédentes restent
requis ; aucune auto-installation/connexion cachée durant export ou prédiction.

```powershell
& $yoloePython experiments/m1-yoloe/prepare_android.py
if ($LASTEXITCODE -ne 0) { throw 'Preparation diagnostic en echec : arret.' }
```

L’exécution initiale a exporté la baseline compacte et vérifié sa parité NASA (1 détection,
IoU masque 1) et négatif (0, padding nul) avec le ONNX d’origine. Les actifs sont listés
par taille/SHA dans `config/benchmark.properties`. Si un compact manque, la recette
réexporte dans son dossier séparé, restaure **uniquement metadata.date** à la valeur
historique et exige le SHA figé. Tout autre écart bloque, sans relâcher le contrôle.
Cette restauration de date n’est pas une promesse de reproductibilité universelle :
seul l’environnement verrouillé Windows est visé. Ne pas lancer `checks.py` pour
recréer les assets Android : ce script historique produirait une nouvelle date.
Conserver une sauvegarde locale des artefacts contrôlés ; ils restent hors Git.

[Protocole Android et validation](../../docs/validation/m1-yoloe-android.md),
[recette manuelle dédiée](../../docs/tests/m1-yoloe-comparaison.md).
