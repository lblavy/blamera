# Validation exploratoire YOLOE — 27 octobre 2026

**Spike PC terminé ; revue et tester indépendants conformes sur calculs et exports.**
Clarification documentaire de portée intégrée. Le tester a réexécuté les inférences NASA
et cas 164 (sorties exactes), compact NASA IoU 1 et négatif padding 0 ; fichiers figés intacts.
Preuve locale : `build/reports/validation/m1-yoloe/tester-results.json`.
Le périmètre historique ci-dessous concerne le spike PC ; le [diagnostic Android](m1-yoloe-android.md)
est un incrément distinct : désormais développé, revu et testé indépendamment sur PC,
APK 0.5.1 installé le 27 octobre par mise à jour principale depuis 0.5.0, après une
première procédure arrêtée faute de connexion. Sans remplacement du modèle courant ;
lancements manuels et fins confirmés ensuite : un run complet CPU/PSS vérifié,
revu et contrôlé par le tester indépendant. [Résultats Xiaomi](m1-yoloe-android.md#mesure-xiaomi-contrôlée-dans-les-logs--27-octobre-2026).
Cette mesure distincte ne réécrit ni le corpus ni les résultats du spike PC.
Le spike CPU historique avait pour base d2e5ca3, branche locale codex/m1-yoloe-evaluation,
et ne touchait ni code app/APK ni téléphone, compte, publication, système, clé ou photo
privée. Baseline/lock M1-01 et corpus d’origine préservés. [Conclusion et tableaux](../research/m1-yoloe-evaluation.md).

## Périmètre et protocole

Exception TDD exploratoire bornée (~60 min premier candidat) : pas production, aucun
script branché aux gates/app. Nano PF officiel testé, pas de seconde famille ; nano
texte chargé/inspecté mais non évalué (encodeur Apple écarté avant usage). Aucun
nouveau paquet : environnement CPU M1-01 réutilisé, lock distinct reproductible.

[Corpus figé](../../experiments/m1-yoloe/corpus.json),16 images avant prédictions :
12 COCO/159 instances (train2017 non indépendant),2 ADE sémantiques, NASA sans GT et
négatif synthétique. Hashes/IDs/licences/annotations/exclusions/mapping80 fixés. Les
images, annotations, poids et résultats restent ignorés, notices originales conservées.
[Recette et sources](../../experiments/m1-yoloe/README.md), manifest versions/assets.

## Exécutions réelles

Environnement Python 3.12.14 / torch 2.14.1+cpu / ultralytics 8.4.190 / ORT 1.31.0,
CPU 2/1. Préparation réseau explicite autorisée ; aucun réseau pendant modèles/export.
Tous les chemins ci-dessous sont relatifs à `build/reports/validation/m1-yoloe/`.

| Exécution | Résultat et preuve |
| --- | --- |
| Inventaire assets + téléchargements publics vérifiés | Nano PF16 271 345 B SHA ae805a…ba7 ; nano texte11 710 443 B inspecté sans encodeur ; assets-metadata.json/checkpoint-probe.log |
| export_pf.py | Sortie0, ONNX statique FP32/opset 17,15 615 371 B, output[1,4621,8400]+protos ; export.json/export.log |
| run_evaluation.py | Sortie0,16 cas,3 warmups+10 répétitions/image/ordre alterné ; results.json, partial-results.json, evaluation.log |
| checks.py | Sortie0, parité PT/ORT2 cas, froid3+10 fois/2 cas, UN export compact séparé ; checks.json/checks.log |
| summarize.py | Sortie0, tableaux catégorie/GT matchés ; correction panneauGT sans modifier pixels de prédiction ; quality-breakdown.json/visuals |
| agnostic.py | Sortie0, amendement après résultats, UNE passe16 cas/aucun nouveau timing ; agnostic-amendment/results.json et agnostic-visuals |
| prepare_corpus.py --offline | Sortie0,16 images+annotations/checkpoint/notices vérifiés par hashes |
| uv lock --check --offline | Sortie0,47 paquets, aucun environnement historique modifié |
| 4 tests utilitaires | Sortie0, unit-tests.log ; matching doublons/FN/pixels ignorés et NMS agnostique/non carré |
| Ruff (F seulement) + syntaxe | Vérification des scripts exploratoires, pas de couverture80/80 ni validation production revendiquée |

TDD utile pour utilitaires : metrics-red.log (2 échecs fonctionnels), metrics-green.log
(3 tests verts) ; agnostic-red.log (2synonymes au lieu1 attendu), agnostic-green.log
(1test vert). Les essais modèle relèvent de l’exception exploratoire, pas d’un Red
inventé. Une première tentative checks a échoué sur un import Path manquant, conservée
dans checks-first-attempt.log, corrigée avant export compact réussi ; pas une preuve
d’incompatibilité modèle. Une erreur de génération README a aussi été corrigée sans
modifier les mesures. Aucune version/library changée pour masquer un échec.

## Synthèse factuelle et limites

Baseline TP 51/FP 12/FN 108 contre PF filtré vers 80 TP 34/FP 12/FN 125 ; rappel de
la projection figée vers 80 catégories : 32,08 vs 21,38 %. Ce n’est pas le rappel de
toutes les reconnaissances sémantiquement correctes possibles. Les sorties fines
American shorthair/feline/pomeranian/chihuahua ne sont pas rattachées à cat/dog par
ce mapping ; leur éventuelle pertinence n’est pas automatiquement validée. Ces chiffres
ne prouvent pas que YOLOE voit moins d’objets en général.
Filtrage commun avant argmax/NMS, réseau PF toujours4585. PF complet class-aware
TP 27/FP 8/FN 132 ; complément agnostique 26/3/133, moins de synonymes mais pas de gain de rappel dans cette projection figée.
Contours : IoU sur les mêmes 27 GT baseline 0,749/PF filtré 0,689, non généralisation.
ADE exact tree/building/road : IoU 0 sur présents, road absent image2 non applicable.
La maison est bien localisée sous manor house : l’échec building porte sur le label,
pas une démonstration de contour nul. Images/mosaïques publiques inspectées localement.

Forward chaud N = 160 : baseline130,07/p95 195,38 ms ; PF 292,78/404,81 ms. Froid inclut
session neuve et post/contours Python, pas latence Android. RSS max observé1,284 GiB
partagé Torch/deux ORT/outputs, ni pic ni attribution PF. Aucune extrapolation téléphone.
Compact [1,20,38]+protos :3 279 840 B de sorties vs158 542 400 B brutes, mêmes 20/19
résultats sur 2 cas et IoU 1 ; tenseur interne/mémoire ARM non mesurés. Compact avec NMS
et brut sans NMS ont des travaux différents, timings présentés séparément dans l’étude.

## Artefacts et immutabilité

- Baseline ONNX SHA 38325721a41fcd732b0a30512ffa8d564b1a8a74ca8c1d6b7e7f37609b2d321b.
- Baseline PT SHA 55ed65c56c91713d23e8402371c6c49a6fd84f257f7dce452e8d70e41dcbe152.
- PF brut SHA c94efd44d029bf6dcf7e713ae4b2d30d1874f761b5c9523fc31c5d1d4a37f2bb.
- PF compact SHA 289e096cef23c0c3bec3a46ead571059702414109aa5319d679891e879d3ee91,15 644 237 B.

Baseline vérifiée avant/après, aucun artefact production réexporté. Hashes exports
observés, metadata.date volatile ; recette et parité documentées, pas promesse de SHA
universel. AGPL publié/notices conservées, pas d’encodeur Apple téléchargé. ADE réservé
à recherche noncommerciale, photos hors Git/APK ; licences Flickr individuelles non
auditées une à une, aucune redistribution. Pas de dépense/service/cloud/inférence externe.

Contrôles directs conformes : UTF-8/liens/syntaxe, invariants de résultats et hashes ;
preuve locale validation-summary.json. Git diff --check vérifié séparément. Étape Android uniquement sur contrat séparé ; pas de remplacement
actuel du modèle ni promesse de performance ou de précision générale.
