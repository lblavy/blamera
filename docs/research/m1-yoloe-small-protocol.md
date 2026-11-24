# Comparaison YOLOE Small — protocole hérité, préparation

28 octobre 2026. **Préparation terminée, revue indépendante avant passage des 144 images.**
Objectif : mesurer si Small améliore réellement reconnaissance et contours par rapport
au Nano sur **exactement les mêmes images**, sans nouveau tirage ni réglage après résultats.
La qualité utile prime ; aucune exclusion fondée sur le coût CPU seul ni changement de l’application.

## Ce qui est conservé

[Protocole et mapping figés](m1-yoloe-quality-protocol.md),
[résultats baseline/Nano](m1-yoloe-quality-results.md) inchangés. 128 LVIS (96 ciblées +32 contrôle)
et 16 ADE ; mêmes 12 visuels, données/licences/GT, vocabulaire et règles d’inconnus/non exhaustifs.
Ancien gel `6ff9b6b463dde7b28e7f065438aa7a6162437b4ccf16d9d226ee38fa4543dcb2`,
entrées `faa145…08b8`, mapping `7701…f82e`, sélection `15d3…1feb`.
Résultats de référence relus, pas de nouveau forward baseline/Nano ni réévaluation du mapping.

Le [nouvel adaptateur](../../experiments/m1-yoloe-small/README.md) importe les helpers
scellés en lecture seule : preprocessing, argmax vocabulaire entier, pré-cap 300, NMS,
cache des masques, scoring/agrégats, GT officiel pycocotools et contrôles de hash.
Un seul forward Small par image, CPU ORT 1.31, 2 threads intra/1 inter, sessions sérialisées.

## Modèle et export réellement préparés

[Checkpoint officiel YOLOE-26s-seg-pf](https://github.com/ultralytics/assets/releases/download/v8.4.0/yoloe-26s-seg-pf.pt),
40 003 064 octets, SHA `01605da3f2e0e5325df45d5f5eef8e40b5b5c7d085399be3c33ac638258b150a`.
Même génération 26/mode PF que Nano, aucune branche texte/CLIP/encodeur Apple.
[Licence AGPL3 de l’asset](https://raw.githubusercontent.com/ultralytics/assets/v8.4.0/LICENSE)
et notices conservées, même usage privé local sans achat ou redistribution que l’étude Nano.

Export ONNX séparé, 48 903 716 octets, SHA
`a545b3e6933a3d94870f5c91d210c5eb2842f56c5221bad93f15db9f72a5c81f`.
FP32 CPU, statique 640², batch 1, opset 17, simplify=False/nms=False/end2end=False.
Entrée `[1,3,640,640]`, sorties `[1,4621,8400]` + `[1,32,160,160]`.
Les **4 585 noms et indices** sont identiques au Nano, vérifiés intégralement contre son ONNX.
L’adaptateur sait vérifier une permutation pure d’indices, sans ajouter de sens ; elle est
identité pour ces artefacts. Pas de projection/filtrage vers 80 avant argmax.

Parité PT raw/ONNX sur les seules NASA et négatif uniforme historiques : sorties finies,
max raw 0,012421 et prototypes 0,000043 sous les mêmes tolérances 0,02/0,005 ;
NASA 14 détections appariées et IoU masque minimum 1 ≥0,99 ; négatif 0. Ce n’est pas un score qualité.
Export et parité ont donc exécuté des forwards synthétiques/hors corpus, **aucune prédiction
sur les 144 images**. Versions/caches/provenance/journaux dans le README et `export-parity.json`.

## Comparaison prévue, avant résultats

12 profils Small : NMS agnostique principal et par classe de contrôle × scores 0,20/0,35/0,50
× caps 20/100 ; IoU NMS 0,5, pré-cap 300. Tous publiés, aucun meilleur réglage choisi après coup.
Masques/frontières à 0,35 seulement ; boîtes avant rejet des masques vides, compte séparé.
Métriques héritées : par famille, micro/macro, contrôle/ciblage et axes d’images riches/petit GT,
FN à zéro pour tous GT, inconnus et noms non mappés conservés. ADE union bâtiment={2,26},
arbre 5/plante 18, GT 0 ignoré, classes absentes et frontières vérifiables traitées à l’identique.

Paires sur mêmes GT : Small agnostique vs Nano agnostique, Small par classe vs Nano par classe,
les deux vs baseline par classe ; reconnaissance typée commune/nouvelle séparée de la géométrie
sans nom des nouvelles catégories. Aucun rematching par famille ou confusion de couverture et
précision conditionnelle des contours. Les masques sur tous GT avec manqués à zéro restent visibles.
12 panneaux préfixés : image/GT/baseline/Nano/Small principal, noms non mappés affichés.

## État des preuves et exécution future

TDD adapté aux nouveaux comportements : Red réels puis Green, 6 tests adaptateur conformes ;
37 tests hérités réexécutés conformes, code ancien inchangé. Intégration testée sur tenseurs
synthétiques/session simulée. Format et contrôle statique ciblé conformes ; pas de gate production
désactivé ni de prétention à une nouvelle couverture globale de l’application.
Gel Small `38562deb71a3a1a9fc4c2749b1baff08def2cb6f84d86b25eb4a71a95ccdc445`,
90 références et ancien gel 197 références vérifié. Nouveaux fichiers sources/JSON en LF,
anciens octets CRLF intacts ; aucun changement de `.gitattributes` ou d’environnement ML.

**Revue du runner nécessaire avant la passe Small 144 forwards/1 728 enregistrements.**
Aucun APK, téléphone, cloud, compte, paiement, build, scan global ou commit dans cette étape.
Le choix définitif reste ouvert ; performances Android Small, chauffe et fluidité non mesurées.
