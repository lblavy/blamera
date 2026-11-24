# YOLOE Small PF — reconnaissance et contours sur le même corpus

28 octobre 2026. **Résultats et galerie revus conformes ; validation scientifique indépendante conforme.**
Small retrouve plus d’objets nommés que Nano sur ce jeu, notamment
10 des 12 nouvelles familles contre 5. Il ne domine pas toutes les catégories, les contours
ou les scènes ADE. Aucun changement du modèle de l’application ni conclusion de latence Android.

## Conditions identiques et portée

Mêmes 144 images : 96 LVIS ciblées +32 contrôle et 16 ADE, mêmes GT/mapping fixés avant
prédictions, mêmes 12 visuels. [Protocole Small](m1-yoloe-small-protocol.md),
[références Nano/baseline](m1-yoloe-quality-results.md), [validation](../validation/m1-yoloe-small.md).
CPU ORT 1.31, 2 threads intra/1 inter, FP32 640² ; score 0,35/cap 20 principal,
NMS agnostique Small/Nano et par classe pour YOLO11n actuel. Tous les profils préfixés ci-dessous.
Small et Nano ont **exactement les mêmes 4 585 catégories dans le même ordre** :
Small ne possède pas davantage de catégories, il en reconnaît davantage ici. Export ONNX
Small 48,90 Mo contre Nano 15,62 Mo ; ce rapport de tailles ne prédit ni mémoire ni latence.

Passe Small seulement : sortie 0, 144 forwards, 1 728 enregistrements/12 profils.
Baseline/Nano relus sans réexécution. Recomputation indépendante conforme : 304 926 comparaisons,
3 072 paires. Cinq cas natifs validés, 20 profils et 38 372 336 pixels vérifiés ; 6 forwards
diagnostiques avec une reprise après incident du harnais ADE, documenté dans la validation.
Les 6 tests Small +37 hérités sont conformes, les deux gels et 10 artefacts protégés intacts.
Preuves : [résumé](../../build/reports/validation/m1-yoloe-small/results-v1/summary.json),
[tester](../../build/reports/validation/m1-yoloe-small/tester-results.json),
[recomputation](../../build/reports/validation/m1-yoloe-small/tester-recompute-results.json),
[replays](../../build/reports/validation/m1-yoloe-small/tester-replay-results.json).

## Reconnaissance typée et présence géométrique

| Mesure principale | YOLO11n actuel | Nano PF | Small PF |
| --- | ---: | ---: | ---: |
| Communes, 98 GT : TP/FP vérifiés/FN | 45/3/53 | 31/3/67 | 41/6/57 |
| Nouvelles, 381 GT : TP/FP vérifiés/FN | 0/0/381 | 35/3/346 | 52/2/329 |
| Rappel commun micro / macro familles | 45,92 % / 49,16 % | 31,63 % / 31,23 % | 41,84 % / 44,86 % |
| Rappel nouveau micro / macro familles | 0 % / 0 % | 9,19 % / 9,80 % | 13,65 % / 18,02 % |
| Localisation sans nom, communes/98 | 50 | 54 | 59 |
| Localisation sans nom, nouvelles/381 | 6 | 73 | 89 |

Les TP typés suivent l’ontologie fixée ; un nom non retenu peut pourtant localiser un objet.
Les différences de TP ne sont pas autant de silhouettes disparues ou apparues. Small progresse
sur Nano en bouteilles, chiens, brosses à dents et plusieurs nouvelles familles ; miroirs et
sièges régressent. Il reste sous le modèle actuel sur chats/personnes/sièges, malgré des gains
sur bouteilles/brosses à dents. FP vérifiés plus nombreux sur les communes : 6 contre 3.

| Famille | Images / GT | TP actuel / Nano / Small | FP Small vérifiés | Ignorés Small |
| --- | ---: | ---: | ---: | ---: |
| Personne | 6 / 29 | 18 / 13 / 13 | 2 | 212 |
| Chat | 7 / 7 | 7 / 4 / 5 | 1 | 4 |
| Chien | 11 / 12 | 8 / 4 / 8 | 0 | 2 |
| Bouteille | 8 / 24 | 6 / 5 / 9 | 0 | 15 |
| Siège | 8 / 16 | 5 / 5 / 3 | 0 | 17 |
| Brosse à dents | 7 / 10 | 1 / 0 / 3 | 3 | 0 |
| Poubelle | 7 / 8 | 0 / 0 / 1 | 0 | 7 |
| Miroir | 10 / 14 | 0 / 6 / 5 | 0 | 5 |
| Lampe | 9 / 27 | 0 / 12 / 14 | 0 | 15 |
| Meuble de rangement | 8 / 24 | 0 / 0 / 4 | 0 | 11 |
| Serviette textile | 6 / 16 | 0 / 0 / 1 | 0 | 7 |
| Chaussures | 9 / 34 | 0 / 3 / 4 | 0 | 6 |
| Chapeau | 10 / 182 | 0 / 13 / 17 | 0 | 24 |
| Savon | 6 / 10 | 0 / 0 / 0 | 1 | 1 |
| Dentifrice | 6 / 12 | 0 / 0 / 1 | 0 | 0 |
| Baignoire | 7 / 7 | 0 / 1 / 4 | 1 | 0 |
| Poignée de porte | 7 / 15 | 0 / 0 / 1 | 0 | 2 |
| Ampoule | 7 / 32 | 0 / 0 / 0 | 0 | 0 |

Small : 250 sorties communes ignorées (246 non vérifiées, 4 non exhaustives), 78 nouvelles
(71 non vérifiées, 6 hors cible, 1 non exhaustive), **1 179 sorties LVIS non mappées**
(1 305 avec ADE). Elles ne sont ni automatiquement justes ni fausses. Les 52/(52+2)
sur les nouvelles ne constituent donc pas une fiabilité globale de 96 %. Mapping incomplet,
sous-types utiles potentiellement exclus, noms fins/races/lieux non certifiés par les GT coarse.
Aucun rescoring postérieur. Chapeaux 182/381 et LVIS 203931 à 170 GT concentrent les comptes :
garder les résultats par famille/macro/strates, pas de moyenne représentant l’usage réel.

## Contours : couverture et mêmes objets

Sur les 381 nouvelles GT, IoU géométrique moyenne avec manqués à zéro :
actuel 0,01447 →Nano 0,14835 →Small 0,18351 ; F1 frontière 0,01478 →0,16952 →0,20777.
C’est notamment un gain de couverture (6/73/89 objets), pas une comparaison intrinsèque de
masques sur des supports différents. Comparaisons appariées au profil principal :

| Référence → Small, mêmes GT | N GT | IoU référence / Small | F1 frontière référence / Small |
| --- | ---: | ---: | ---: |
| Nano, nom commun | 25 | 0,79485 / 0,82927 | 0,85253 / 0,89190 |
| Nano, nom nouveau | 31 | 0,73522 / 0,74960 | 0,91780 / 0,91176 |
| Nano, nouvelle GT sans nom | 63 | 0,77766 / 0,79663 | 0,88502 / 0,89734 |
| Actuel, nom commun | 32 | 0,78478 / 0,82547 | 0,86568 / 0,88961 |
| Actuel, nouvelle GT sans nom | 6 | 0,91892 / 0,92037 | 0,93862 / 0,91950 |

Small améliore modestement IoU/F1 sur les mêmes 25 GT communes appariées à Nano. Sur les 31
nouvelles GT nommées retrouvées par les deux, IoU augmente légèrement mais F1 baisse
légèrement ; pas de supériorité uniforme des frontières. Les paires avec l’actuel ont un autre
support et ne se comparent pas directement aux moyennes des paires avec Nano. Aucun masque
vide au profil principal Small. Tolérance frontière 0,5 % diagonale, minimum 1 px, pas la fluidité UI.

## Strates d’images et ADE

Axes chevauchants, métriques sur tous les GT évalués des images : «petit GT» signifie
contient au moins un objet cible aire<32², pas une mesure sur les seuls petits objets.

| Strate | Images / GT | TP actuel / Nano / Small | IoU typée tous GT actuel / Nano / Small |
| --- | ---: | ---: | ---: |
| Ciblées | 96 / 452 | 40 / 61 / 84 | 0,06961 / 0,10479 / 0,14594 |
| Contrôle | 32 / 27 | 5 / 5 / 9 | 0,16061 / 0,13535 / 0,27386 |
| ≥20 GT annotées | 26 / 308 | 21 / 29 / 34 | 0,05002 / 0,07095 / 0,08560 |
| Contiennent un petit GT | 45 / 439 | 31 / 55 / 76 | 0,05358 / 0,09636 / 0,13551 |

Paires par axe et compte d’ignorés détaillés dans le résumé. Ces 26/45 images ne mesurent
pas l’occlusion et ne représentent pas une population naturelle d’usage.

| ADE, union sur 16 images | IoU actuel / Nano / Small | F1 actuel / Nano / Small |
| --- | ---: | ---: |
| Bâtiment/maison={2,26} | 0,00000 / 0,25665 / 0,17070 | 0,00000 / 0,13204 / 0,12764 |
| Arbre=5 | 0,00000 / 0,28243 / 0,11749 | 0,00000 / 0,16619 / 0,13186 |
| Plante=18 | 0,41622 / 0,42046 / 0,41912 | 0,28033 / 0,29150 / 0,31148 |

Au profil principal, Small baisse sur arbre/bâtiment, plante très proche.
Le profil par classe préfixé donne bâtiment IoU 0,55188 (Nano 0,52114), arbre 0,11769
(Nano 0,28440), plante 0,38177 (Nano 0,42046) : pas de profil universellement gagnant choisi
après coup. Le bâtiment dépend donc de l’interaction NMS/noms : la baisse en agnostique
ne prouve pas une infériorité intrinsèque de Small. L’arbre baisse dans les deux modes.
Les noms non mappés peuvent aussi décrire des bâtiments bien localisés.

## Les 12 profils Small préfixés

Même IoU NMS 0,5/pré-cap 300 ; scores non calibrés entre modèles. TP/FP vérifiés/FN.

| NMS Small | Score | Cap | Communes, 98 GT | Nouvelles, 381 GT |
| --- | ---: | ---: | --- | --- |
| Agnostique | 0.20 | 20 | 43/6/55 | 57/3/324 |
| Agnostique | 0.20 | 100 | 47/8/51 | 116/6/265 |
| Agnostique | 0.35 | 20 | 41/6/57 | 52/2/329 |
| Agnostique | 0.35 | 100 | 43/6/55 | 83/2/298 |
| Agnostique | 0.50 | 20 | 38/4/60 | 47/1/334 |
| Agnostique | 0.50 | 100 | 38/4/60 | 54/1/327 |
| Par classe | 0.20 | 20 | 48/7/50 | 56/7/325 |
| Par classe | 0.20 | 100 | 56/14/42 | 124/13/257 |
| Par classe | 0.35 | 20 | 47/7/51 | 52/6/329 |
| Par classe | 0.35 | 100 | 51/9/47 | 86/6/295 |
| Par classe | 0.50 | 20 | 42/6/56 | 48/3/333 |
| Par classe | 0.50 | 100 | 43/7/55 | 56/3/325 |

À 0,35, Small cap 20→100 : nouveaux 52→83, dont chapeaux 17→48 ; communes 41→43.
Ce gain est fortement lié à la scène dense et ne prouve pas exhaustivité. Tous les profils
restent publiés ; aucun nouveau seuil, pondération ou mapping choisi depuis ce test.

## Galerie fixée et observations qualitatives

Image/GT/actuel/Nano/Small, douze IDs identiques au protocole ; noms non mappés visibles.
L’orchestrateur a inspecté 4 panneaux : LVIS 22892, Small nomme cat le chat nommé german shepherd
par Nano ; dobermann sur un chien est non mappé et ne certifie pas la race. LVIS 570736,
Small ajoute cabinet/bath/doorhandle mais la baignoire est plus fragmentée que le jacuzzi Nano,
et des labels parasites persistent. ADE 583, natural history museum→orangery restent non mappés
malgré la localisation du bâtiment ; végétation Small fragmentée. ADE 1565, noms de scènes
citysquare/infrastructure/modern/remove ne constituent pas un nommage fiable des objets.
Illustrations des gains/limites, aucun nouveau TP ou score corrigé.

- [lvis-62692](../../build/reports/validation/m1-yoloe-small/results-v1/panels/lvis-62692-comparison.png)
- [lvis-319617](../../build/reports/validation/m1-yoloe-small/results-v1/panels/lvis-319617-comparison.png)
- [lvis-22892](../../build/reports/validation/m1-yoloe-small/results-v1/panels/lvis-22892-comparison.png)
- [lvis-371699](../../build/reports/validation/m1-yoloe-small/results-v1/panels/lvis-371699-comparison.png)
- [lvis-372349](../../build/reports/validation/m1-yoloe-small/results-v1/panels/lvis-372349-comparison.png)
- [lvis-492878](../../build/reports/validation/m1-yoloe-small/results-v1/panels/lvis-492878-comparison.png)
- [lvis-394328](../../build/reports/validation/m1-yoloe-small/results-v1/panels/lvis-394328-comparison.png)
- [lvis-570736](../../build/reports/validation/m1-yoloe-small/results-v1/panels/lvis-570736-comparison.png)
- [ade-ADE_val_00000498](../../build/reports/validation/m1-yoloe-small/results-v1/panels/ade-ADE_val_00000498-comparison.png)
- [ade-ADE_val_00001828](../../build/reports/validation/m1-yoloe-small/results-v1/panels/ade-ADE_val_00001828-comparison.png)
- [ade-ADE_val_00000583](../../build/reports/validation/m1-yoloe-small/results-v1/panels/ade-ADE_val_00000583-comparison.png)
- [ade-ADE_val_00001565](../../build/reports/validation/m1-yoloe-small/results-v1/panels/ade-ADE_val_00001565-comparison.png)

**Conclusion sur ces données validées** : Small est un candidat
plus convaincant que Nano pour plusieurs familles LVIS et améliore certains contours appariés,
mais ne domine ni les arbres/bâtiments ADE ni toutes les catégories communes. Même vocabulaire,
couverture faible et inconnus encore nombreux ; aucun remplaçant universel démontré.
Les performances Android Small ne sont pas mesurées ; les ~49 Mo de poids ne se convertissent
pas en délai attendu. Coût et usages image/direct seront à arbitrer après le bénéfice qualité,
sans changement de l’application dans cette étape.

Données et attributions : Gupta/Dollár/Girshick, [LVIS](https://github.com/lvis-dataset/lvis-api),
annotations CC BY 4.0 et photos COCO/Flickr licences individuelles ; Zhou et al.,
[ADE20K et conditions](https://ade20k.csail.mit.edu/terms/) (photos recherche non commerciale,
annotations BSD 3). Sources/licences individuelles dans le manifeste hérité ; images/GT/panneaux
restent hors Git/APK, aucune nouvelle redistribution ou photo privée.
