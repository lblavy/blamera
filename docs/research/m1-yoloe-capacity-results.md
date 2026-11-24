# YOLOE26 PF — comparaison des capacités N/S/M/L/X

28 octobre 2026. **Résultats revus et validation scientifique indépendante conformes.**
Les capacités supérieures apportent de la qualité utile, pas une simple hausse de catégories :
les cinq PF ont exactement les mêmes 4 585 noms/ordre. X retrouve le plus d’objets des catégories
communes sur ce corpus ; L couvre le plus de familles nouvelles et davantage de nouvelles
silhouettes. Aucun vainqueur universel ni changement du modèle de l’application.
Lecture pratique : **L est le candidat équilibré à poursuivre, X la borne haute sur les objets
communs ; M reste pertinent à investiguer par famille et sur les bâtiments.** Ces pistes de
qualité ne tranchent pas les coûts Android encore non mesurés ni un usage « photo seulement ».

## Périmètre et lecture pratique

Même corpus de 144 images, GT/mapping fixés avant prédictions, 18 familles évaluées
(98 GT communes +381 nouvelles), 12 IDs visuels, CPU FP32 640² et ORT 1.31 2/1 threads.
Principal : score 0,35/cap 20, NMS agnostique PF ; YOLO11n actuel NMS par classe.
Les poids plus grands ne créent pas de nouveaux libellés et ne prédisent pas la latence.
[Protocole](m1-yoloe-capacity-protocol.md), [Nano](m1-yoloe-quality-results.md),
[Small](m1-yoloe-small-results.md), [preuves, profils et paires complètes](../validation/m1-yoloe-capacity.md).
X→M→L ont exécuté 432 forwards/5 184 enregistrements, sans rerun des références.
Recomputation conforme : 1 003 154 comparaisons/13 824 paires ; 15 cas natifs/60 profils et
144 287 544 pixels vérifiés sans incident. [Verdict tester](../../build/reports/validation/m1-yoloe-capacity/tester-results.json).
Les performances Android M/L/X ne sont pas mesurées ; hypothèse photo ponctuelle à étudier,
aucune décision « photo seulement », pas de sélection sur la seule latence.

## Reconnaissance typée et localisation

TP/FP vérifiés/FN, puis rappel macro non pondéré des familles. La localisation sans nom
n’implique pas une reconnaissance correcte du libellé. Les nombres ne couvrent pas les 4 585 catégories.

| Modèle | Communes TP/FP/FN | Nouvelles TP/FP/FN | Rappel macro commun / nouveau | Localisation sans nom C/98 ; N/381 | Familles nouvelles avec TP |
| --- | ---: | ---: | ---: | ---: | ---: |
| Actuel | 45/3/53 | 0/0/381 | 49,16 % / 0,00 % | 50 ; 6 | 0/12 |
| N | 31/3/67 | 35/3/346 | 31,23 % / 9,80 % | 54 ; 73 | 5/12 |
| S | 41/6/57 | 52/2/329 | 44,86 % / 18,02 % | 59 ; 89 | 10/12 |
| M | 46/4/52 | 57/1/324 | 51,59 % / 22,56 % | 63 ; 89 | 10/12 |
| L | 49/7/49 | 67/1/314 | 56,15 % / 28,32 % | 65 ; 92 | 11/12 |
| X | 55/5/43 | 67/3/314 | 60,02 % / 28,91 % | 70 ; 89 | 10/12 |

X a le plus de TP communs (55 contre 49 L/46 M/45 actuel), mais moins de chats que M/L/actuel
et moins de personnes que l’actuel. L rejoint X sur 67 nouveaux TP, avec 11 familles contre 10
et davantage de chaussures/miroirs ; X conserve des avantages en bouteilles/sièges/meubles.
M est intermédiaire en nommage (46/57), notamment plus de personnes que S/L/X. Les types
d’erreurs comptent : ces totaux n’impliquent ni fiabilité globale ni dominance par famille.

| Famille | GT (images) | Actuel TP | N TP | S TP | M TP | L TP | X TP |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Personne | 29 (6) | 18 | 13 | 13 | 14 | 13 | 13 |
| Chat | 7 (7) | 7 | 4 | 5 | 7 | 7 | 6 |
| Chien | 12 (11) | 8 | 4 | 8 | 7 | 9 | 10 |
| Bouteille | 24 (8) | 6 | 5 | 9 | 10 | 11 | 15 |
| Siège | 16 (8) | 5 | 5 | 3 | 5 | 5 | 7 |
| Brosse à dents | 10 (7) | 1 | 0 | 3 | 3 | 4 | 4 |
| Poubelle | 8 (7) | 0 | 0 | 1 | 1 | 1 | 2 |
| Miroir | 14 (10) | 0 | 6 | 5 | 7 | 9 | 8 |
| Lampe | 27 (9) | 0 | 12 | 14 | 16 | 16 | 16 |
| Meuble | 24 (8) | 0 | 0 | 4 | 2 | 2 | 3 |
| Serviette | 16 (6) | 0 | 0 | 1 | 2 | 1 | 3 |
| Chaussures | 34 (9) | 0 | 3 | 4 | 2 | 6 | 3 |
| Chapeau | 182 (10) | 0 | 13 | 17 | 18 | 18 | 19 |
| Savon | 10 (6) | 0 | 0 | 0 | 1 | 3 | 3 |
| Dentifrice | 12 (6) | 0 | 0 | 1 | 2 | 3 | 3 |
| Baignoire | 7 (7) | 0 | 1 | 4 | 6 | 7 | 7 |
| Poignée de porte | 15 (7) | 0 | 0 | 1 | 0 | 1 | 0 |
| Ampoule | 32 (7) | 0 | 0 | 0 | 0 | 0 | 0 |

Le mapping reste incomplet pour certains sous-types utiles ; aucun nouveau synonyme ni TP
remappé après résultats. GT LVIS partiels : ignorer un nom ne le rend ni vrai ni faux.
Chapeaux 182/381 et LVIS 203931 à 170 GT : le micro est fortement pondéré par une foule.

| PF principal | Ignorés communs / nouveaux LVIS | Non mappés sur 144 LVIS+ADE |
| --- | ---: | ---: |
| N | 196 / 51 | 1233 |
| S | 250 / 78 | 1305 |
| M | 277 / 77 | 1352 |
| L | 263 / 79 | 1340 |
| X | 274 / 87 | 1331 |

Ces inconnus/non mappés sont exclus du dénominateur de précision évaluée : pas de
« 98 % de fiabilité globale » depuis 57 TP/1 FP M ou 67/1 L. Scores non calibrés entre modèles.

## Contours : couverture puis mêmes objets

Moyennes géométriques sur 381 GT nouvelles avec les manqués à zéro ; masque binaire/F1
de frontières, pas épaisseur du trait UI ni fluidité. À 0,35 uniquement.
Au principal sur 144 images : **3 masques vides X, 1 M et 3 L**. Une boîte peut être un TP
sans masque affichable ; sa qualité masque/frontière est alors zéro, sans retirer le TP de boîte.

| Modèle | GT localisées sans nom /381 | IoU tous GT | F1 tous GT |
| --- | ---: | ---: | ---: |
| Actuel | 6 | 0,01447 | 0,01478 |
| N | 73 | 0,14835 | 0,16952 |
| S | 89 | 0,18351 | 0,20777 |
| M | 89 | 0,18452 | 0,20868 |
| L | 92 | 0,19025 | 0,21856 |
| X | 89 | 0,18294 | 0,20881 |

L ajoute quelques silhouettes nouvelles (92 contre 89 S/M/X), pas une révolution du contour.
Sur les **mêmes GT appariées à Small**, voici les gains/pertes ; supports différents entre
capacités, donc ne pas classer leur précision intrinsèque par les seules moyennes.

| Capacité / pool apparié à Small | N GT | IoU S → candidat | F1 S → candidat |
| --- | ---: | ---: | ---: |
| M, nom commun | 35 | 0,79687 → 0,81893 | 0,89616 → 0,91023 |
| M, nom nouveau | 41 | 0,77156 → 0,77741 | 0,89853 → 0,89225 |
| M, nouvelle silhouette sans nom | 70 | 0,80208 → 0,80756 | 0,89668 → 0,89826 |
| L, nom commun | 37 | 0,80026 → 0,81751 | 0,90329 → 0,92055 |
| L, nom nouveau | 44 | 0,76150 → 0,76964 | 0,90033 → 0,90941 |
| L, nouvelle silhouette sans nom | 74 | 0,77889 → 0,80287 | 0,88715 → 0,90778 |
| X, nom commun | 36 | 0,80852 → 0,82899 | 0,89690 → 0,92838 |
| X, nom nouveau | 45 | 0,76139 → 0,77087 | 0,89456 → 0,89607 |
| X, nouvelle silhouette sans nom | 68 | 0,78700 → 0,80229 | 0,88706 → 0,89458 |

Gains appariés généralement modestes, et F1 de certains pools nommés baisse (M nouveaux).
Les paires vers Nano et l’actuel, avec tous les N, sont dans la validation ; aucune paire
nommées nouvelles vers l’actuel (vocabulaire absent, N=0), géométrie mesurée séparément.

## ADE et strates : pas de dominance uniforme

Union ADE sur 16 images, bâtiment={2,26}, arbre=5, plante=18 ; classes absentes incluses,
void exclu sans créer de frontières artificielles. Cellules IoU/F1 au profil principal.

| Classe ADE | Actuel | N | S | M | L | X |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Bâtiment | 0,00000/0,00000 | 0,25665/0,13204 | 0,17070/0,12764 | 0,51244/0,27685 | 0,52781/0,27867 | 0,33479/0,26867 |
| Arbre | 0,00000/0,00000 | 0,28243/0,16619 | 0,11749/0,13186 | 0,02460/0,07055 | 0,02451/0,07466 | 0,02424/0,07595 |
| Plante | 0,41622/0,28033 | 0,42046/0,29150 | 0,41912/0,31148 | 0,42075/0,30251 | 0,42080/0,30144 | 0,42116/0,30799 |

Le score arbre est une mesure **appellation/mapping coarse + masque**, pas une incapacité
totale de localiser/détourer les arbres. Les noms fins non mappés et les bâtiments absorbant
du feuillage peuvent déplacer cette mesure ; les exemples visuels ne sont pas un audit des 16 cas.
Bâtiment dépend aussi de NMS/noms : IoU par classe préfixé N 0,52114/S 0,55188/M 0,74665/
L 0,49618/X à lire ci-dessous ; aucun mode gagnant choisi après coup.

| IoU ADE en NMS par classe, score 0,35/cap 20 | N | S | M | L | X |
| --- | ---: | ---: | ---: | ---: | ---: |
| Bâtiment | 0,52114 | 0,55188 | 0,74665 | 0,49618 | 0,33492 |
| Arbre | 0,28440 | 0,11769 | 0,02460 | 0,02451 | 0,02424 |
| Plante | 0,42046 | 0,38177 | 0,42075 | 0,42080 | 0,42116 |

Strates d’**images** chevauchantes : tous GT évalués de l’image, pas seulement les petits
objets ni une mesure d’occlusion. Cellule TP typés ; IoU tous GT avec manqués à zéro.

| Images / GT | Actuel | N | S | M | L | X |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Ciblage 96/452 | 40 ; 0,06961 | 61 ; 0,10479 | 84 ; 0,14594 | 95 ; 0,16721 | 109 ; 0,18807 | 113 ; 0,19594 |
| Contrôle 32/27 | 5 ; 0,16061 | 5 ; 0,13535 | 9 ; 0,27386 | 8 ; 0,24502 | 7 ; 0,21318 | 9 ; 0,27900 |
| Riches 26/308 | 21 ; 0,05002 | 29 ; 0,07095 | 34 ; 0,08560 | 40 ; 0,10048 | 39 ; 0,09593 | 40 ; 0,10111 |
| Contiennent petit GT 45/439 | 31 ; 0,05358 | 55 ; 0,09636 | 76 ; 0,13551 | 80 ; 0,14301 | 96 ; 0,16819 | 100 ; 0,17565 |

Les 36 profils X/M/L sont tous publiés dans la validation, sans optimisation sur ce test.
À 0,35 cap 20→100 : X nouveaux 67→133, **+66 chapeaux uniquement** ; M 57→111
(+53 chapeaux/+1 lampe) ; L 67→133 (+65 chapeaux/+1 lampe). Ces gains ne décrivent pas une
amélioration générale sur les scènes riches ; pré-cap 300 demeure même avec cap 100.

## Panneaux fixés et observations

Douze IDs préfixés, chaque panneau image/GT/actuel/Nano/Small/candidat ; noms non mappés visibles.

| Cas | X | M | L |
| --- | --- | --- | --- |
| lvis-62692 | [X](../../build/reports/validation/m1-yoloe-capacity/x/results-v1/panels/lvis-62692-comparison.png) | [M](../../build/reports/validation/m1-yoloe-capacity/m/results-v1/panels/lvis-62692-comparison.png) | [L](../../build/reports/validation/m1-yoloe-capacity/l/results-v1/panels/lvis-62692-comparison.png) |
| lvis-319617 | [X](../../build/reports/validation/m1-yoloe-capacity/x/results-v1/panels/lvis-319617-comparison.png) | [M](../../build/reports/validation/m1-yoloe-capacity/m/results-v1/panels/lvis-319617-comparison.png) | [L](../../build/reports/validation/m1-yoloe-capacity/l/results-v1/panels/lvis-319617-comparison.png) |
| lvis-22892 | [X](../../build/reports/validation/m1-yoloe-capacity/x/results-v1/panels/lvis-22892-comparison.png) | [M](../../build/reports/validation/m1-yoloe-capacity/m/results-v1/panels/lvis-22892-comparison.png) | [L](../../build/reports/validation/m1-yoloe-capacity/l/results-v1/panels/lvis-22892-comparison.png) |
| lvis-371699 | [X](../../build/reports/validation/m1-yoloe-capacity/x/results-v1/panels/lvis-371699-comparison.png) | [M](../../build/reports/validation/m1-yoloe-capacity/m/results-v1/panels/lvis-371699-comparison.png) | [L](../../build/reports/validation/m1-yoloe-capacity/l/results-v1/panels/lvis-371699-comparison.png) |
| lvis-372349 | [X](../../build/reports/validation/m1-yoloe-capacity/x/results-v1/panels/lvis-372349-comparison.png) | [M](../../build/reports/validation/m1-yoloe-capacity/m/results-v1/panels/lvis-372349-comparison.png) | [L](../../build/reports/validation/m1-yoloe-capacity/l/results-v1/panels/lvis-372349-comparison.png) |
| lvis-492878 | [X](../../build/reports/validation/m1-yoloe-capacity/x/results-v1/panels/lvis-492878-comparison.png) | [M](../../build/reports/validation/m1-yoloe-capacity/m/results-v1/panels/lvis-492878-comparison.png) | [L](../../build/reports/validation/m1-yoloe-capacity/l/results-v1/panels/lvis-492878-comparison.png) |
| lvis-394328 | [X](../../build/reports/validation/m1-yoloe-capacity/x/results-v1/panels/lvis-394328-comparison.png) | [M](../../build/reports/validation/m1-yoloe-capacity/m/results-v1/panels/lvis-394328-comparison.png) | [L](../../build/reports/validation/m1-yoloe-capacity/l/results-v1/panels/lvis-394328-comparison.png) |
| lvis-570736 | [X](../../build/reports/validation/m1-yoloe-capacity/x/results-v1/panels/lvis-570736-comparison.png) | [M](../../build/reports/validation/m1-yoloe-capacity/m/results-v1/panels/lvis-570736-comparison.png) | [L](../../build/reports/validation/m1-yoloe-capacity/l/results-v1/panels/lvis-570736-comparison.png) |
| ade-ADE_val_00000498 | [X](../../build/reports/validation/m1-yoloe-capacity/x/results-v1/panels/ade-ADE_val_00000498-comparison.png) | [M](../../build/reports/validation/m1-yoloe-capacity/m/results-v1/panels/ade-ADE_val_00000498-comparison.png) | [L](../../build/reports/validation/m1-yoloe-capacity/l/results-v1/panels/ade-ADE_val_00000498-comparison.png) |
| ade-ADE_val_00001828 | [X](../../build/reports/validation/m1-yoloe-capacity/x/results-v1/panels/ade-ADE_val_00001828-comparison.png) | [M](../../build/reports/validation/m1-yoloe-capacity/m/results-v1/panels/ade-ADE_val_00001828-comparison.png) | [L](../../build/reports/validation/m1-yoloe-capacity/l/results-v1/panels/ade-ADE_val_00001828-comparison.png) |
| ade-ADE_val_00000583 | [X](../../build/reports/validation/m1-yoloe-capacity/x/results-v1/panels/ade-ADE_val_00000583-comparison.png) | [M](../../build/reports/validation/m1-yoloe-capacity/m/results-v1/panels/ade-ADE_val_00000583-comparison.png) | [L](../../build/reports/validation/m1-yoloe-capacity/l/results-v1/panels/ade-ADE_val_00000583-comparison.png) |
| ade-ADE_val_00001565 | [X](../../build/reports/validation/m1-yoloe-capacity/x/results-v1/panels/ade-ADE_val_00001565-comparison.png) | [M](../../build/reports/validation/m1-yoloe-capacity/m/results-v1/panels/ade-ADE_val_00001565-comparison.png) | [L](../../build/reports/validation/m1-yoloe-capacity/l/results-v1/panels/ade-ADE_val_00001565-comparison.png) |

Inspection qualitative bornée de l’orchestrateur, pas un audit exhaustif : LVIS 22892,
X dit possum sur le chat (score 0,935), M American shorthair→cat coarse, L cat ; races non
certifiées. Le chien est doberman non mappé avec X/M et polecat erroné avec L. LVIS 570736,
bath/cabinet/miroirs mieux retrouvés, mais baignoire X fragmentée et parasites persistants.
ADE 583 : stone building X/M, courthouse L→building coarse sans fonction du lieu certifiée,
pas de tree nommé ; certains masques englobent aussi du feuillage. ADE 498/1565, noms
de scène/concepts et erreurs subsistent. Aucun nouveau TP ou score qualitatif remappé.

## Ce que ces données motivent

**L est un candidat équilibré à investiguer** : autant de nouveaux TP que X, 11 familles contre 10,
couverture géométrique légèrement supérieure et bâtiment principal meilleur. **X reste la
borne haute des TP communs sur ce profil**, avec des gains par famille et contours appariés,
mais pas sur tout objet ni tout dataset. **M demeure un compromis à examiner**, surtout sur
bâtiments en mode par classe et certaines catégories, sans remplacer la preuve d’usage réel.
Ce sont des pistes de décision, pas un vainqueur universel ni un choix photo-only.

La suite doit considérer la qualité des noms/vocabulaire et les objets utiles, pas seulement
grossir le réseau. Les coûts Android X/M/L ne sont pas mesurés et aucune latence attendue
n’est déduite des poids (M 108,52/L 126,15/X 272,48 Mo). Un usage photo ponctuelle reste une
hypothèse d’investigation. Modèle, paramètres et APK actuels inchangés.

Données : Gupta/Dollár/Girshick, [LVIS](https://github.com/lvis-dataset/lvis-api), annotations
CC BY 4.0/photos COCO-Flickr aux licences individuelles ; Zhou et al.,
[ADE20K/conditions](https://ade20k.csail.mit.edu/terms/), recherche locale non commerciale,
annotations BSD 3. Sources et attributions détaillées dans le manifeste hérité, aucun dataset
ou panneau publié, aucune photo privée.
