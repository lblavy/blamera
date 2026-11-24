# Validation YOLOE26 PF X/M/L

28 octobre 2026. **Conforme : revue et validation scientifique indépendante terminées.**
Sources/gels/app/références inchangés ; aucun calcul restant.

[Sources et commandes](../../experiments/m1-yoloe-capacity/README.md), [protocole](../research/m1-yoloe-capacity-protocol.md),
[résultats comparatifs](../research/m1-yoloe-capacity-results.md).

## Préparation et exécution acquises

Gel `1596a5630e3b7d0635721f990c5a919c40cf919ab28fbe05b85852fdb8464d34` (66 références),
imbrique les gels 197 qualité et 90 Small. Nouveaux sources/JSON LF, anciens CRLF intacts.
Sept tests ciblés, format/syntaxe et verify-only de préparation conformes, aucune ancienne source éditée.
Parité hors corpus X/M aux anciennes bornes ; L conserve son échec global raw 0,03760>0,02,
puis diagnostic sans réexport et amendement coordonnées ≤0,05 px accepté avant corpus. Scores/coefficients
≤0,02/protos≤0,005 inchangés ; décisions 12 profils identiques, masques vérifiés uniquement à 0,35,
caps 20/100 deux NMS, IoU≥0,99. Aucun résultat sur les 144 images utilisé pour cet amendement.

Tester : trois primaires sériels X→M→L sortis 0, chacun 144 forwards/1 728 records/12 profils,
soit 432 forwards/5 184 records. Références baseline/Nano/Small relues, pas réexécutées.
Recomputations indépendantes sorties 0 : X 334 240/M 334 508/L 334 406 =1 003 154 comparaisons,
4 608 paires et 12 panneaux chacun (13 824 paires/36 panneaux au total).
Replays natifs conformes sans incident : 15 forwards diagnostiques distincts des primaires,
15 cas/60 profils, 144 287 544 pixels vérifiés. Trois gels et 21 artefacts protégés intacts.
Sept tests nouveaux réexécutés sortie 0 (0,264 s), format/syntaxe conformes ; moteurs hérités
immuables, pas de nouvelle exécution des anciens tests/campagnes dans cette validation.
Au profil principal sur 144 images, masques vides X=3/M=1/L=3 : boîtes évaluées séparément,
qualité masque/frontière zéro, pas de promesse de contour affichable pour ces détections.
[Verdict global](../../build/reports/validation/m1-yoloe-capacity/tester-results.json),
recomputation [X](../../build/reports/validation/m1-yoloe-capacity/x/tester-recompute-results.json)/[M](../../build/reports/validation/m1-yoloe-capacity/m/tester-recompute-results.json)/[L](../../build/reports/validation/m1-yoloe-capacity/l/tester-recompute-results.json),
replays [X](../../build/reports/validation/m1-yoloe-capacity/x/tester-replay-results.json)/[M](../../build/reports/validation/m1-yoloe-capacity/m/tester-replay-results.json)/[L](../../build/reports/validation/m1-yoloe-capacity/l/tester-replay-results.json).

## Tous les 36 profils préfixés

TP/FP vérifiés/FN. 98 GT communes, 381 nouvelles. Scores non calibrés, IoU NMS 0,5/pré-cap 300.
Les tableaux N/S/courant complets restent dans leurs études liées ; aucun réglage choisi après coup.

| Taille / NMS | Score | Cap | Communes | Nouvelles |
| --- | ---: | ---: | --- | --- |
| X / agnostique | 0.20 | 20 | 55/5/43 | 71/3/310 |
| X / agnostique | 0.20 | 100 | 60/7/38 | 141/3/240 |
| X / agnostique | 0.35 | 20 | 55/5/43 | 67/3/314 |
| X / agnostique | 0.35 | 100 | 57/5/41 | 133/3/248 |
| X / agnostique | 0.50 | 20 | 49/5/49 | 58/3/323 |
| X / agnostique | 0.50 | 100 | 50/5/48 | 115/3/266 |
| X / classe | 0.20 | 20 | 56/6/42 | 68/7/313 |
| X / classe | 0.20 | 100 | 68/11/30 | 151/9/230 |
| X / classe | 0.35 | 20 | 56/5/42 | 65/7/316 |
| X / classe | 0.35 | 100 | 63/7/35 | 137/8/244 |
| X / classe | 0.50 | 20 | 50/5/48 | 58/6/323 |
| X / classe | 0.50 | 100 | 52/6/46 | 117/6/264 |
| M / agnostique | 0.20 | 20 | 47/4/51 | 63/3/318 |
| M / agnostique | 0.20 | 100 | 51/5/47 | 122/5/259 |
| M / agnostique | 0.35 | 20 | 46/4/52 | 57/1/324 |
| M / agnostique | 0.35 | 100 | 48/4/50 | 111/2/270 |
| M / agnostique | 0.50 | 20 | 45/3/53 | 53/1/328 |
| M / agnostique | 0.50 | 100 | 45/3/53 | 73/1/308 |
| M / classe | 0.20 | 20 | 51/9/47 | 68/3/313 |
| M / classe | 0.20 | 100 | 57/18/41 | 131/10/250 |
| M / classe | 0.35 | 20 | 50/8/48 | 61/1/320 |
| M / classe | 0.35 | 100 | 54/16/44 | 116/3/265 |
| M / classe | 0.50 | 20 | 48/8/50 | 57/1/324 |
| M / classe | 0.50 | 100 | 49/11/49 | 77/1/304 |
| L / agnostique | 0.20 | 20 | 49/8/49 | 72/1/309 |
| L / agnostique | 0.20 | 100 | 55/12/43 | 141/3/240 |
| L / agnostique | 0.35 | 20 | 49/7/49 | 67/1/314 |
| L / agnostique | 0.35 | 100 | 51/7/47 | 133/1/248 |
| L / agnostique | 0.50 | 20 | 45/6/53 | 53/0/328 |
| L / agnostique | 0.50 | 100 | 45/6/53 | 108/0/273 |
| L / classe | 0.20 | 20 | 52/7/46 | 70/3/311 |
| L / classe | 0.20 | 100 | 61/17/37 | 148/7/233 |
| L / classe | 0.35 | 20 | 52/6/46 | 67/2/314 |
| L / classe | 0.35 | 100 | 57/9/41 | 137/4/244 |
| L / classe | 0.50 | 20 | 47/5/51 | 54/1/327 |
| L / classe | 0.50 | 100 | 50/6/48 | 109/1/272 |

## Paires au principal : mêmes GT, pas mêmes survivants entre lignes

Score 0,35/cap 20/NMS agnostique candidat et N/S, classe pour l’actuel. Référence→candidat.
Trois pools distincts : nom commun, nom nouveau, nouvelle géométrie sans nom. Paires nommées
nouvelles vers actuel inexistantes (N=0, non mesuré) ; géométrie non nulle évaluée séparément.

| Candidat / référence / pool | N GT | IoU référence→candidat | F1 référence→candidat |
| --- | ---: | ---: | ---: |
| M / S / nom commun | 35 | 0,79687→0,81893 | 0,89616→0,91023 |
| M / S / nom nouveau | 41 | 0,77156→0,77741 | 0,89853→0,89225 |
| M / S / nouvelle géométrie | 70 | 0,80208→0,80756 | 0,89668→0,89826 |
| M / N / nom commun | 24 | 0,79222→0,83326 | 0,84117→0,89120 |
| M / N / nom nouveau | 29 | 0,74753→0,76271 | 0,90089→0,89244 |
| M / N / nouvelle géométrie | 58 | 0,77628→0,79381 | 0,88251→0,89246 |
| M / actuel / nom commun | 34 | 0,78877→0,83133 | 0,86687→0,89421 |
| M / actuel / nom nouveau | 0 | non mesuré→non mesuré | non mesuré→non mesuré |
| M / actuel / nouvelle géométrie | 5 | 0,91397→0,93143 | 0,93181→0,95254 |
| L / S / nom commun | 37 | 0,80026→0,81751 | 0,90329→0,92055 |
| L / S / nom nouveau | 44 | 0,76150→0,76964 | 0,90033→0,90941 |
| L / S / nouvelle géométrie | 74 | 0,77889→0,80287 | 0,88715→0,90778 |
| L / N / nom commun | 24 | 0,78872→0,83124 | 0,84678→0,90747 |
| L / N / nom nouveau | 31 | 0,74390→0,76194 | 0,89911→0,90543 |
| L / N / nouvelle géométrie | 57 | 0,77067→0,80811 | 0,87418→0,90043 |
| L / actuel / nom commun | 34 | 0,78188→0,83070 | 0,86732→0,90818 |
| L / actuel / nom nouveau | 0 | non mesuré→non mesuré | non mesuré→non mesuré |
| L / actuel / nouvelle géométrie | 6 | 0,91892→0,92545 | 0,93862→0,94019 |
| X / S / nom commun | 36 | 0,80852→0,82899 | 0,89690→0,92838 |
| X / S / nom nouveau | 45 | 0,76139→0,77087 | 0,89456→0,89607 |
| X / S / nouvelle géométrie | 68 | 0,78700→0,80229 | 0,88706→0,89458 |
| X / N / nom commun | 26 | 0,80225→0,84306 | 0,85220→0,91587 |
| X / N / nom nouveau | 30 | 0,71992→0,73347 | 0,90873→0,90191 |
| X / N / nouvelle géométrie | 55 | 0,76088→0,79530 | 0,87573→0,89470 |
| X / actuel / nom commun | 36 | 0,80248→0,85476 | 0,87355→0,92216 |
| X / actuel / nom nouveau | 0 | non mesuré→non mesuré | non mesuré→non mesuré |
| X / actuel / nouvelle géométrie | 5 | 0,93033→0,93176 | 0,95364→0,93870 |

Les scores tous GT avec manqués à zéro, variantes/strates par image et inconnus restent
dans chaque résumé complet. Seul le rendu documentaire est produit dans cette étape :
pas de nouveau test/forward/scan/build, aucune image personnelle ou accès téléphone.
