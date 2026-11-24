# Qualité YOLOE Nano PF — résultats annotés

27 octobre 2026. **YOLOE apporte réellement des catégories supplémentaires, mais le gain est
limité et hétérogène ; il ne remplace pas clairement YOLO11n sur les catégories communes.**
Les contours sont comparables sur les mêmes objets retrouvés. Le choix définitif reste ouvert :
ce bénéfice qualité doit être mis en regard des usages image ponctuelle/caméra, puis des coûts
CPU/PSS déjà mesurés. Aucun remplacement du modèle courant ni réglage de l’application.

## Périmètre et preuve

144 images naturelles figées avant prédictions : 96 LVIS ciblées +32 contrôle indépendant des
catégories +16 ADE. LVIS : 98 GT communes et 381 nouvelles, réparties en 18 familles dont
12 absentes des 80 classes de baseline. Les familles fines/races/lieux ne sont évaluées
qu’au niveau sémantique déclaré : reconnaître « bâtiment » ne certifie pas « Prague Castle ».
Ce jeu enrichi n’est ni une prévalence d’usage réel ni un benchmark AP universel.

Même entrée FP32 640², CPU ORT1.31 (2 threads intra/1 inter), modèles exacts baseline
`383257…321b` et PF `c94efd…f2bb`, vocabulaire PF entier de 4 585 catégories avant argmax/mapping.
NMS IoU 0,5, pré-cap 300 ; 18 profils préfixés ci-dessous. Comparaison principale : score 0,35,
cap 20, baseline NMS par classe et PF NMS agnostique recommandé par son éditeur.
Boîtes avant rejet de masque vide ; masque/frontière à 0,35 seulement, GT manqué à zéro.
LVIS : GT partiel, inconnus/non exhaustifs conservés ; ADE : union sémantique, GT 0 ignoré.
[Protocole et sources](m1-yoloe-quality-protocol.md), [préparation/TDD](../validation/m1-yoloe-quality-preparation.md).

**Validation indépendante conforme** : 37 tests, 288 forwards primaires, 2 592 enregistrements,
18 profils ; 377 625 comparaisons de recomputation et 1 024 paires. Dix replays séparés sur
5 images ont vérifié 28 425 560 pixels de masques. Gel197 références, modèles et APK inchangés.
Artefacts locaux ignorés : [résumé](../../build/reports/validation/m1-yoloe-quality/results-v1/summary.json),
[par image](../../build/reports/validation/m1-yoloe-quality/results-v1/per-image.jsonl),
[tester](../../build/reports/validation/m1-yoloe-quality/tester-results.json),
[recomputation](../../build/reports/validation/m1-yoloe-quality/tester-recompute-results.json),
[replays](../../build/reports/validation/m1-yoloe-quality/tester-replay-results.json).

## Reconnaissance typée et localisation au profil principal

| Familles | Baseline TP / FP vérifiés / FN | YOLOE TP / FP vérifiés / FN |
| --- | --- | --- |
| 6 communes, 98 GT | 45 / 3 / 53 | 31 / 3 / 67 |
| 12 nouvelles, 381 GT | 0 / 0 / 381 | 35 / 3 / 346 |

Sur les mêmes 98 GT communes, la localisation agnostique sans nom retrouve 50 objets
avec baseline et 54 avec PF principal. Les 45 contre 31 TP typés mesurent le nommage
dans le mapping figé, pas 14 silhouettes physiquement perdues.

Rappel micro commun : 45,92 % →31,63 % ; macro par famille : 49,16 % →31,23 %.
Nouvelles : 9,19 % micro, 9,80 % macro pour PF, avec TP dans **5 familles sur 12**.
Le zéro typé baseline hors80 reflète son vocabulaire : elle peut néanmoins localiser
et détourer certains de ces objets sous un autre nom (mesure géométrique séparée ci-dessous).

| Famille | Images avec GT | GT | TP baseline / PF | FP vérifiés PF | Ignorés PF |
| --- | ---: | ---: | ---: | ---: | ---: |
| Personne | 6 | 29 | 18 / 13 | 2 | 163 |
| Chat | 7 | 7 | 7 / 4 | 1 | 2 |
| Chien | 11 | 12 | 8 / 4 | 0 | 7 |
| Bouteille | 8 | 24 | 6 / 5 | 0 | 13 |
| Siège | 8 | 16 | 5 / 5 | 0 | 11 |
| Brosse à dents | 7 | 10 | 1 / 0 | 0 | 0 |
| Poubelle | 7 | 8 | 0 / 0 | 0 | 2 |
| Miroir | 10 | 14 | 0 / 6 | 0 | 7 |
| Lampe | 9 | 27 | 0 / 12 | 0 | 15 |
| Meuble de rangement | 8 | 24 | 0 / 0 | 0 | 4 |
| Serviette textile | 6 | 16 | 0 / 0 | 0 | 1 |
| Chaussures | 9 | 34 | 0 / 3 | 0 | 4 |
| Chapeau | 10 | 182 | 0 / 13 | 0 | 18 |
| Savon | 6 | 10 | 0 / 0 | 1 | 0 |
| Dentifrice | 6 | 12 | 0 / 0 | 1 | 0 |
| Baignoire | 7 | 7 | 0 / 1 | 1 | 0 |
| Poignée de porte | 7 | 15 | 0 / 0 | 0 | 0 |
| Ampoule | 7 | 32 | 0 / 0 | 0 | 0 |

**Les 3 FP vérifiés sur les nouvelles familles ne signifient pas une fiabilité globale de 92 %.**
PF a aussi 51 prédictions nouvelles ignorées (49 non vérifiées, 1 non exhaustive, 1 hors cible),
196 communes ignorées (193 non vérifiées, 3 non exhaustives) et **1130 sorties LVIS non mappées**
(1233 avec ADE). Elles ne sont ni automatiquement correctes ni automatiquement fausses.
Les sous-types utiles hors mapping restent non évalués. Ampoule n’a pas de correspondance
littérale préfixée dans le vocabulaire ; son zéro ne prouve pas une incapacité visuelle générale.
Un [audit qualitatif postérieur de l’orchestrateur](../../build/reports/validation/m1-yoloe-quality/unmapped-localization-audit.json)
trouve des localisations plausibles exclues du mapping : `jacuzzi` sur 3 GT baignoire,
`schnauzer` sur 2 GT chien, `cleat` sur 3 GT chaussures, `craftsman` sur 1 GT personne.
Il relève aussi des confusions probables : races de chiens sur des chats, shelf/fridge sur
meuble de rangement, biology laboratory sur baignoire. Aucun nouveau TP ou score corrigé :
les 35 TP nommés relèvent de l’ontologie figée, pas de toute reconnaissance acceptable possible.
LVIS 203931 concentre 170 GT ; les chapeaux représentent 182/381 nouvelles GT.
Les résultats par famille et strates sont indispensables, aucune moyenne ne représente l’usage.

## Contours : couverture puis comparaison sur mêmes objets

Sur les nouvelles GT, matching **sans nom** : 6 objets retrouvés baseline contre 73 PF,
sur 381. Avec manqués à zéro, IoU moyenne 0,01447 →0,14835 ; F1 frontière 0,01478 →0,16952.
Ce gain mesure surtout la couverture. Comparer les seules moyennes conditionnelles de 6 et 73
objets ne mesure pas une précision intrinsèque du contour. Sur les mêmes GT retrouvés :

| Pool commun aux deux modèles | N GT | IoU baseline / PF | F1 frontière baseline / PF |
| --- | ---: | ---: | ---: |
| Matches typés communs | 28 | 0,79968 / 0,80751 | 0,86439 / 0,85498 |
| Nouvelles GT, matching sans nom | 6 | 0,91892 / 0,91405 | 0,93862 / 0,90597 |

Contours comparables sur ces petits supports ; pas de gain net de précision démontré.
F1 binaire à tolérance 0,5 % diagonale native (minimum 1 px), pas une mesure de fluidité ni
du crénelage du trait UI. Aucun masque vide au profil principal pour les deux modèles.

## Strates d’images et ADE

Axes chevauchants : une image « petit GT » contient au moins un GT cible aire<32²,
mais **tous les GT évalués de ces images** entrent dans les mesures ci-dessous.
Aucune mesure exclusive des petits objets ou d’occlusion. IoU avec manqués à zéro.

| Images | N / GT évaluées | TP typés baseline / PF | IoU tous GT baseline / PF | Paires typées / sans nom nouvelles |
| --- | ---: | ---: | ---: | ---: |
| Ciblées | 96 / 452 | 40 / 61 | 0,06961 / 0,10479 | 24 / 4 |
| Contrôle | 32 / 27 | 5 / 5 | 0,16061 / 0,13535 | 4 / 2 |
| ≥20 GT annotées | 26 / 308 | 21 / 29 | 0,05002 / 0,07095 | 16 / 2 |
| Contiennent un petit GT | 45 / 439 | 31 / 55 | 0,05358 / 0,09636 | 21 / 4 |

Les valeurs de chaque paire/axe (IoU/F1 sur mêmes GT) sont dans `paired_strata` du résumé.
ADE 16 : union bâtiment={2,26}, arbre=5, plante=18 ; toutes les 16 images agrégées, y compris
erreurs sur classe absente, void exclu sans créer de frontière artificielle.

| Catégorie ADE | IoU baseline / PF principal | F1 baseline / PF principal |
| --- | ---: | ---: |
| Bâtiment/maison | 0,00000 / 0,25665 | 0,00000 / 0,13204 |
| Arbre | 0,00000 / 0,28243 | 0,00000 / 0,16619 |
| Plante | 0,41622 / 0,42046 | 0,28033 / 0,29150 |

Le profil PF par classe préfixé donne IoU bâtiment 0,52114 (arbre 0,28440 ; plante 0,42046),
contre 0,25665 en agnostique. Différence de profil à conserver, pas de réglage gagnant choisi
après coup. Les sous-types physiques sont regroupés ; lieux/espèces/attributs fins non certifiés.
ADE 583 contient aussi un bâtiment bien localisé sous `natural history museum`, non mappé,
sans identité fine vérifiée. L’IoU bâtiment de 25,7 % ne signifie donc pas absence de bons
contours de bâtiments. Les sorties non mappées comprennent apports utiles et erreurs réelles.

## Tous les profils préfixés

TP/FP vérifiés/FN, mêmes 98 GT communes et 381 nouvelles. Les scores 0,20/0,35/0,50 ne sont pas
des probabilités calibrées comparables entre modèles. Cap100 reste limité par pré-cap 300.

| Modèle / NMS | Score | Cap | Communes TP/FP/FN | Nouvelles TP/FP/FN |
| --- | ---: | ---: | --- | --- |
| Baseline/classe | 0.20 | 20 | 51/4/47 | 0/0/381 |
| Baseline/classe | 0.20 | 100 | 51/4/47 | 0/0/381 |
| Baseline/classe | 0.35 | 20 | 45/3/53 | 0/0/381 |
| Baseline/classe | 0.35 | 100 | 45/3/53 | 0/0/381 |
| Baseline/classe | 0.50 | 20 | 37/2/61 | 0/0/381 |
| Baseline/classe | 0.50 | 100 | 37/2/61 | 0/0/381 |
| PF/agnostique | 0.20 | 20 | 32/4/66 | 40/4/341 |
| PF/agnostique | 0.20 | 100 | 34/5/64 | 68/4/313 |
| PF/agnostique | 0.35 | 20 | 31/3/67 | 35/3/346 |
| PF/agnostique | 0.35 | 100 | 31/3/67 | 39/3/342 |
| PF/agnostique | 0.50 | 20 | 26/2/72 | 30/3/351 |
| PF/agnostique | 0.50 | 100 | 26/2/72 | 31/3/350 |
| PF/classe | 0.20 | 20 | 35/5/63 | 47/5/334 |
| PF/classe | 0.20 | 100 | 39/10/59 | 79/8/302 |
| PF/classe | 0.35 | 20 | 35/5/63 | 38/4/343 |
| PF/classe | 0.35 | 100 | 35/6/63 | 43/6/338 |
| PF/classe | 0.50 | 20 | 29/4/69 | 32/3/349 |
| PF/classe | 0.50 | 100 | 29/4/69 | 33/3/348 |

À0,35, passer cap 20→100 en PF principal ajoute 4 TP nouveaux (35→39), aucune TP commune.
Cela ne rend pas les scènes riches exhaustives. Sensibilités toutes publiées, aucun optimum
ni nouveau seuil proposé à l’application depuis ce jeu de test.

## Galerie fixée avant prédictions et décision

Douze panneaux image/GT/baseline/PF principal ; tous les noms non mappés restent visibles.
GT partiel LVIS, union sémantique ADE. Aucune image retenue après avoir vu les réussites.

- [lvis-62692](../../build/reports/validation/m1-yoloe-quality/results-v1/panels/lvis-62692-comparison.png)
- [lvis-319617](../../build/reports/validation/m1-yoloe-quality/results-v1/panels/lvis-319617-comparison.png)
- [lvis-22892](../../build/reports/validation/m1-yoloe-quality/results-v1/panels/lvis-22892-comparison.png)
- [lvis-371699](../../build/reports/validation/m1-yoloe-quality/results-v1/panels/lvis-371699-comparison.png)
- [lvis-372349](../../build/reports/validation/m1-yoloe-quality/results-v1/panels/lvis-372349-comparison.png)
- [lvis-492878](../../build/reports/validation/m1-yoloe-quality/results-v1/panels/lvis-492878-comparison.png)
- [lvis-394328](../../build/reports/validation/m1-yoloe-quality/results-v1/panels/lvis-394328-comparison.png)
- [lvis-570736](../../build/reports/validation/m1-yoloe-quality/results-v1/panels/lvis-570736-comparison.png)
- [ade-ADE_val_00000498](../../build/reports/validation/m1-yoloe-quality/results-v1/panels/ade-ADE_val_00000498-comparison.png)
- [ade-ADE_val_00001828](../../build/reports/validation/m1-yoloe-quality/results-v1/panels/ade-ADE_val_00001828-comparison.png)
- [ade-ADE_val_00000583](../../build/reports/validation/m1-yoloe-quality/results-v1/panels/ade-ADE_val_00000583-comparison.png)
- [ade-ADE_val_00001565](../../build/reports/validation/m1-yoloe-quality/results-v1/panels/ade-ADE_val_00001565-comparison.png)

**Décision ouverte** : apport démontré sur lampes/miroirs/chapeaux/chaussures/baignoires
et arbre/bâtiment ADE, mais faible couverture de plusieurs familles, pertes sur communes
et sorties non évaluées nombreuses. Aucun motif suffisant pour annoncer un remplaçant
globalement meilleur. La durée d’inférence + NMS sur CPU (environ 2,07× celle de baseline) et les relevés PSS
Xiaomi du [diagnostic séparé](../validation/m1-yoloe-android.md) sont des coûts à arbitrer
**après** le bénéfice utile, pas un veto à eux seuls. Une analyse
ponctuelle peut avoir un compromis différent du direct. M1 et D-M1-02 restent ouverts ;
pas de nouveau modèle, poids, seuil, APK, mesure téléphone ou campagne supplémentaire ici.

Données : Gupta, Dollár et Girshick, [LVIS](https://github.com/lvis-dataset/lvis-api),
annotations CC BY 4.0 ; photos COCO/Flickr avec licences individuelles 1/2/4/5 et attribution
URL/ID conservées dans le manifeste. Zhou et al., [ADE20K](https://groups.csail.mit.edu/vision/datasets/ADE20K/) :
[photos recherche/éducation non commerciales, annotations BSD 3](https://ade20k.csail.mit.edu/terms/).
Images/GT/panneaux locaux hors Git et APK, aucune redistribution des photographies ADE.
