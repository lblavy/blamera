# YOLOE peut-il remplacer le modèle actuel ?

27 octobre 2026. **Le choix entre YOLO11n et YOLOE reste ouvert.** Le coût CPU/PSS est
maintenant mesuré sur Xiaomi. Le diagnostic historique de 16 images PC, sa projection figée
vers 80 excluant certains termes fins/races et ses deux scènes ADE ne suffisaient pas à
généraliser la qualité. Il est désormais complété par une [étude annotée de 144 images](m1-yoloe-quality-results.md),
préfixée, revue et validée indépendamment : apport réel mais limité/hétérogène de Nano PF,
pertes sur les catégories communes et contours comparables sur mêmes objets retrouvés.
YOLO11n est maintenu **provisoirement** pour éviter un changement non validé, sans
prétendre qu’il est meilleur. Une détection meilleure ou de nouvelles catégories utiles
correctement reconnues pourraient justifier un temps de réponse plus long.
Le spike CPU historique ne modifiait pas l’application.
Revue et tester indépendants conformes sur calculs/exports, après clarification de portée.
Le [diagnostic Android séparé 0.5.1](../validation/m1-yoloe-android.md) est désormais
développé, revu et testé indépendamment sur PC. Son APK a été installé le 27 octobre
par mise à jour du seul APK principal depuis 0.5.0, après une première procédure arrêtée
faute de connexion. L’utilisateur a ensuite confirmé deux fins de comparaison manuelles ;
un run complet est analysé, sans lancement automatique. Modèle par défaut conservé.

## Ce qui a réellement été évalué

Une famille nano officielle, ultralytics 8.4.190, ONNX/ORT CPU 1.31.0, FP32, entrée
statique 640², batch 1, opset 17, simplify=False, end2end=False. Baseline exacte de
l’app (SHA 383257…321b), conf 0,35 / NMS par classe IoU 0,5 / top 300 / max 20.
YOLOE texte (11,71 Mo) seulement chargé/inspecté : labels numériques, encodeur nécessaire,
non évalué. PF (16,27 Mo checkpoint) contient déjà les 4 585 noms ; aucun texte encodé,
aucun CLIP/poids Apple téléchargé. Le filtrage vers 80 n’allège pas le réseau 4 585.
[Protocole et sources exécutables](../../experiments/m1-yoloe/README.md).

16 images figées avant prédictions : 12 COCO128-seg/159 instances, 2 ADE20K, NASA,
négatif synthétique. COCO vient de **train2017**, potentiellement vu à l’entraînement :
ce n’est pas un test indépendant ni une mesure de généralisation/AP. Synonymes figés
avant résultats : 69 noms stricts communs, puis alias pour80 cibles, certains approximatifs
(luggage/phone/houseplant). PF commun réduit les scores synonymes par max AVANT argmax,
NMS et cap 20, après le réseau 4 585 ; PF complet garde les noms du modèle.

## Détections et masques sur le diagnostic COCO

Matching 1 à 1 par classe + IoU boîte≥0,5, score décroissant. Hors80 non comptés comme FP
sur un GT qui ne les annote pas. IoU masque calculée sur les seuls matches, pas sur
les objets manqués ; ce n’est pas une AP. GT : vrais polygones dérivés COCO, jamais
les masques d’un modèle. Tous les paramètres initiaux restent identiques entre modèles.

**Les rappels ci-dessous mesurent la projection figée vers 80 catégories, pas toutes
les reconnaissances sémantiquement correctes possibles.** Le mapping ne rattache pas
les races/termes fins American shorthair, feline, pomeranian ou chihuahua à cat/dog :
ces sorties réelles peuvent être exclues des comptes communs. Leur pertinence n’est
pas automatiquement validée ; elle exige un examen sémantique distinct. Les nombres
ne permettent pas d’affirmer que YOLOE voit moins d’objets en général.

| Mode | TP / FP / FN | Rappel de la projection vers 80 | Précision de la projection vers 80 | IoU masque moyenne (N matches) |
| --- | --- | --- | --- | --- |
| Baseline YOLO11n | 51 / 12 / 108 | 32,08 % | 80,95 % | 0,726 (51) |
| PF filtré vers 80 | 34 / 12 / 125 | 21,38 % | 73,91 % | 0,705 (34) |
| PF complet, évaluation des classes communes | 27 / 8 / 132 | 16,98 % | 77,14 % | 0,723 (27) |
| PF complet agnostique, complément après résultats | 26 / 3 / 133 | 16,35 % | 89,66 % | 0,747 (26) |

Sur les mêmes 27 GT matchés par baseline et PF filtré, IoU moyenne 0,749 vs 0,689 ;
sur les mêmes 21 GT baseline/PF complet,0,756 vs 0,710. Ces faibles effectifs et le biais
d’entraînement interdisent une conclusion universelle ; score 0,35 non calibré entre modèles.

Le tableau par catégorie porte sur la même projection figée : un FN chat/chien
peut correspondre à une sortie fine non rattachée. Ce n’est pas un décompte exhaustif
des reconnaissances sémantiquement pertinentes de l’animal.

| Catégorie (nombre GT) | Baseline TP/FP/FN | PF filtré vers 80 TP/FP/FN | PF complet TP/FP/FN |
| --- | --- | --- | --- |
| Personne (29) | 13/1/16 | 9/1/20 | 9/1/20 |
| Chat (2) | 2/2/0 | 1/1/1 | 1/1/1 |
| Chien (2) | 2/0/0 | 1/1/1 | 1/0/1 |
| Bouteille (11) | 1/1/10 | 2/1/9 | 1/1/10 |
| Brosse à dents (5) | 1/0/4 | 1/0/4 | 0/0/5 |

Diagnostic max 100, sans nouveau tuning : aucun compte baseline/PF filtré n’augmente ;
le plafond 20 n’explique donc pas tous les manques ici. PF complet passe de 20 à28/39/22
sur trois cas (142/196/NASA), sans preuve que ces résultats supplémentaires soient corrects.

## Catégories nouvelles et contours : distinguer label et localisation

ADE est sémantique : IoU d’UNION des masques portant le label EXACT tree/building/road,
pixels 0 ignorés. Ce n’est pas un test d’instances. Sur les deux images, aucun de ces
labels exacts n’est retenu : IoU 0 pour les catégories présentes ; road absent sur la
seconde → non applicable. **building=0 ne signifie pas contour de maison nul** : la
première grande maison est bien localisée sous manor house/stone house. La seconde
propose notamment monastery/Prague Castle/church tower, pas le label building.

Le vocabulaire est effectivement plus large (tree existe à l’ID 4253), mais un nom
présent ne garantit pas reconnaissance sur une image. Les premiers visuels montrent
plusieurs synonymes sur un objet et des concepts comme historic/season/stormy ; leur
présence n’est pas une nouvelle détection d’objet correcte. Les contours peuvent être
plausibles, tandis que le label cible et l’exhaustivité échouent.

La [documentation YOLOE](https://docs.ultralytics.com/models/yoloe#deployment-notes) et
le code 8.4.190 définissent agnostic_nms=True par défaut. La comparaison initiale utilise
False pour suivre l’app. UNE passe exploratoire True a été ajoutée après résultats,
protocole initial conservé : ADE passe de 2/14 propositions à1/5, moins de synonymes,
sans gain de rappel commun ni apparition des trois labels exacts. Pas de sélection
rétrospective d’un meilleur modèle/réglage. Les deux modes sont montrés.

## Coût PC et faisabilité téléphone

Mêmes tenseurs pertinents, ORT CPU 2 threads intra/1 inter, sessions chaudes réutilisées,
ordre alterné, 3 warmups +10 répétitions/image (N = 160). p95 percentile NumPy linéaire.

| Mesure PC, pas Xiaomi | Baseline | PF 4 585 (identique pour filtre80 et complet) |
| --- | --- | --- |
| ONNX brut | 11 720 021 octets | 15 615 371 octets |
| Forward chaud médiane / p95 | 130,07 / 195,38 ms | 292,78 / 404,81 ms |
| Cycle froid NASA médiane / p95 | 218,26 / 349,54 ms | 1161,35 / 1403,24 ms |
| Cycle froid scène164 médiane / p95 | 242,97 / 544,90 ms | 952,69 / 1070,36 ms |

Froid = lecture/hash, session neuve, inférence puis post/contours **Python**, prétraitement
et dessin exclus. Le code actuel recrée bien sa session ; cette recette ne mesure pas
la latence Kotlin ni caméra→contours. Phases détaillées brutes dans checks/results JSON.
RSS maximum ÉCHANTILLONNÉ1,284 GiB du processus partagé (Torch importé/deux ORT/outputs) :
ni pic, ni attribution au seul PF, ni mémoire Android. Aucune extrapolation FPS/thermique.

Export de déploiement séparé, nms=True/agnosticFalse/conf0,35/IoU 0,5/max 20, sans pré-top 300 :

| Sortie / graphe | PF brut sans NMS | PF compact avec NMS natif |
| --- | --- | --- |
| Détections + prototypes | [1,4621,8400] + [1,32,160,160] | [1,20,38] + mêmes prototypes |
| Octets de sortie | 158 542 400 | 3 279 840 |
| Taille ONNX | 15 615 371 | 15 644 237 |
| NASA médiane / p95 (N = 10) | 278,84 / 372,90 ms | 367,53 / 437,24 ms |
| Scène164 médiane / p95 (N = 10) | 276,41 / 320,78 ms | 358,81 / 528,00 ms |

Colonnes de travail différent : le compact inclut NMS, pas le brut. Pas de comparaison
équitable de pipelines complets ou preuve de vitesse gagnée. Le tenseur intermédiaire
volumineux reste potentiellement interne au graphe ; sa mémoire n’est pas mesurée ici.
Parité compact/brut sur 2 cas :20 et19 détections, aucun FP/FN entre versions, IoU masque 1.
PT/ORT sur baseline et PF mêmes 2 cas : erreurs max prédictions≤0,01096/protos≤0,000121,
IoU masque min0,99999 dans les tolérances déclarées .02/.005/.99.

Adaptation Kotlin au parcours normal à chiffrer dans un contrat séparé : validation de forme et parseur
xyxy/score/class/32 coefficients/padding0 au lieu de 116 canaux, dictionnaire4585 et
traduction/ontologie, éviter double NMS, garder géométrie/contours et ownership natif.
La sortie compacte réduit le transfert vers Java, sans prouver RAM/latence sur ARM.
Aucun essai GPU/NPU ni caméra physique, aucun remplacement silencieux des poids.

## Gratuit/local et décision du diagnostic initial de 16 images

L’[asset officiel AGPL3](https://github.com/ultralytics/assets/blob/b64813c026a0a8924b193afa2badba244696f0ee/LICENSE)
a été téléchargé gratuitement et exécuté intégralement hors réseau. Le texte §2/§13
permet cet usage privé non transmis sans achat ni publication, dans le périmètre actuel ;
notices retenues. Pas d’audit exhaustif de toute la chaîne d’entraînement revendiqué.
Les [poids Apple directement distribués](https://github.com/apple-aiml-research/ml-mobileclip/blob/main/LICENSE_MODELS)
ont une licence recherche excluant product development : écartés, aucune fusion de
prompts texte ni encodeur dans un APK. Ne pas assimiler automatiquement leur régime
à tout artefact PF publié par un autre fournisseur.

| Choix | Avis sur ces seules preuves |
| --- | --- |
| Remplacer immédiatement YOLO11n | Bénéfice qualité/vocabulaire insuffisamment évalué ; maintien provisoire du modèle courant, choix ouvert |
| Essai Android diagnostique du compact PF | Coûts mesurés : session.run médiane ~539 ms contre ~260 ms, PSS partagé plus élevé ; ces coûts ne tranchent pas seuls le choix |
| Affirmer meilleure reconnaissance d’arbre ou scènes riches | Non démontré ; aucun label exact arbre ici, beaucoup de synonymes/concepts |
| Réévaluer vocabulaire personnalisé / tailles supérieures | Hors de cette étape, aucune promesse ni encodeur Apple utilisé |

Sources corpus : [COCO128-seg](https://docs.ultralytics.com/datasets/segment/coco128-seg),
[ADE20K terms](https://ade20k.csail.mit.edu/terms/), [sampleData épinglé](https://github.com/CSAILVision/sceneparsing/tree/e20222964c58eba41d8986d45d652d93adc234a5/sampleData),
[NASA via skimage](https://scikit-image.org/docs/stable/api/skimage.data.html#skimage.data.astronaut).
[Rapport d’exécution](../validation/m1-yoloe.md).


## Complément Xiaomi — 27 octobre 2026

[Rapport Android détaillé](../validation/m1-yoloe-android.md#mesure-xiaomi-contrôlée-dans-les-logs--27-octobre-2026) :
un seul run complet, 40 mesures (N=10 par modèle/fixture), hashes et statistiques vérifiés.
Durée écoulée `session.run` CPU/NMS comprise : NASA 260,291 → 539,717 ms, motif
260,458 → 539,328 ms, environ 2,07× ; copie, masques/contours et UI exclus.
PSS du processus après blocs session ouverte : baseline 294,10/309,65 MiB,
PF 1 619,58/1 636,49 MiB. Plus grand relevé enregistré dans ce run complet 1 647,18 MiB avant bloc motif PF,
pas un pic ni l’allocation propre du modèle. Ordre/caches/thermique non contrôlés.
Le motif coloré Android diffère du négatif uniforme PC ; vérification réelle PC sur le
motif exact donne aussi baseline 0/PF 20, contre 0/0 sur l’ancien négatif. Aucun bug ARM
ni parité complète des sorties ne sont démontrés ; pas de « 20 FP » sans GT.
La mesure CPU/PSS est terminée, le choix du modèle reste ouvert. Aucun remplacement,
GPU/NPU, chauffe/prolongé ou validation B04–B06 supposé ; M1 reste ouvert.

À l’issue du diagnostic initial, le critère discriminant suivant était une évaluation
ciblée et annotée de la qualité et du vocabulaire élargi : rappel/précision par catégorie utile, nouveaux objets correctement
reconnus plutôt que synonymes/concepts abstraits, contours sur objets appariés et objets
manqués, inconnus et scènes riches. Ces bénéfices passent avant l’arbitrage des coûts
mesurés, avec distinction entre direct caméra et analyse ponctuelle d’image. Aucun poids,
budget ou seuil d’attente utilisateur n’est inventé ; pas de préférence définitive sur
la seule latence. Cette étape historique n’ajoutait aucun corpus, essai ou réglage.
La [campagne qualité suivante](m1-yoloe-quality-results.md) apporte désormais ces mesures sur
12 familles nouvelles, 6 communes et ADE, sans changement de l’application. Elle conserve
les inconnus et tous les profils préfixés ; elle ne désigne pas un vainqueur universel.
