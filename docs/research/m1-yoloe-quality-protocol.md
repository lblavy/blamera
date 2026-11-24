# Protocole qualité YOLOE — phase 1, avant prédictions

**Protocole historique figé avant prédictions, puis revu et exécuté le 27 octobre 2026.**
La campagne et sa validation indépendante sont terminées : [résultats](m1-yoloe-quality-results.md).
Les sections ci-dessous conservent les règles et étapes antérieures au premier forward.
Le choix reste ouvert :
une meilleure reconnaissance ou des catégories utiles supplémentaires peuvent justifier
un coût supérieur. Cette étape fixe d’abord les critères de qualité, pas une pondération
arbitraire coût/qualité ni un budget supposé de l’utilisateur. Aucun code app/APK/téléphone,
poids/export, environnement global ou gate existant modifié.

## Artefacts exécutables et blocage avant inférence

[Paramètres](../../experiments/m1-yoloe-quality/protocol.json),
[mapping à revue indépendante](../../experiments/m1-yoloe-quality/mapping-proposal.json),
[IDs/effectifs de sélection](../../experiments/m1-yoloe-quality/selection-proposal.json),
[noyau testé et commandes](../../experiments/m1-yoloe-quality/README.md).
Le seul programme de préparation lit les GT déjà locaux ; il ne télécharge aucune image
et ne charge aucun modèle. Pas de runner d’inférence avant revue. Après celle-ci :
acquérir seulement les IDs fixés, noter URL finale/licence/SHA/taille/dimensions, vérifier
doublons exacts et proches, puis sceller hashes mapping/sélection/inputs avant tout résultat.
Erreur de téléchargement ou dimension : arrêt, aucune substitution en fonction des prédictions.

## Sources, licences et champ d’application

[LVIS v1 officiel](https://github.com/lvis-dataset/lvis-api) : 1203 catégories et masques,
val 19809 images, différent d’un minival 5000. Archive officielle
`https://dl.fbaipublicfiles.com/LVIS/lvis_v1_val.json.zip` ; JSON 201235232 octets,
SHA `2bf946b92c3037f53c172d80017f5b74ea035f00a21b20e0766b3b638b2363f9`.
Zip SHA `5cae9a3c79aadb667550c2b5dcf7f4d86e059a41ec91ef690225b667e28e9ba5`.
SHA d’ingress local, pas checksum éditeur prétendu. Annotations CC BY 4 ; images selon
licence COCO/Flickr propre à chaque ID. Filtre **avant tirage** : licences 1/2/4/5 autorisant
les adaptations, usage local non commercial ; IDs 3/6 NoDerivs exclus. Rien redistribué,
pas de photos privées. URL `coco_url`, `flickr_url` et licence conservées ; téléchargement
futur HTTPS du même stockage officiel via `https://s3.amazonaws.com/images.cocodataset.org/`.
Pas de désactivation TLS ni archive d’images complète.

Complément [ADE20K](https://ade20k.csail.mit.edu/terms/) : images recherche/enseignement
non commerciaux, annotations BSD3. Archive officielle ADEChallengeData2016.zip 967382037
octets, SHA `7ff1be44964418441f542a7cc1e1a650e7dc0fc275f5d23252bc9bbdbc977b29`.
Seuls les 2000 masques validation sont disponibles ici, aucun JPEG extrait. Codes vérifiés
par objectInfo150 : building {2 building, 26 house}/tree {5}/plant {18} ; GT0 ignoré. Seules 16 images choisies seront
extraites après revue. Exclure les anciens samples et doublons avant première prédiction.
LVIS n’a pas de tree générique : ne pas faire passer Christmas_tree pour son équivalent.

Images/annotations/outputs complets sous build ignoré ; uniquement métadonnées de
protocole versionnées. Toutes ces images peuvent avoir été vues par les poids préentraînés :
la restriction COCO val2017 évite train2017 évident, **ne garantit pas l’inédit pour PF**.
Sources et acquisitions root archivées dans `build/reports/validation/m1-yoloe-quality/source/`.

## Sélection déterministe pré-résultats : 144 images naturelles

LVIS : 4809 images du val2017 COCO, 3221 admissibles après licences, 128 retenues.
**32 contrôles** : premiers IDs selon SHA256(`blamera-quality-v1:20261027:control:ID`),
indépendamment des catégories. **96 ciblées**, disjointes : couverture gloutonne déterministe
jusqu’à 6 images par famille, gain en familles déficitaires puis même hash comme départage ;
compléments pour au moins 16 images riches et 16 avec petits objets, puis remplissage hashé.
Règle et ordre exacts dans prepare_selection.py, pas de choix visuel basé sur des succès.
Le tirage final compte 22 images ciblées avec ≥20 instances annotées, 41 avec aire masque
native<32² pixels. Encombrement/taille objectifs, **pas une vérité d’occlusion** : LVIS ne
l’annote pas. Un éventuel audit visuel d’occlusion doit précéder les prédictions, garder
tous les IDs et indiquer incertain ; sinon occlusion non mesurée.

Effectifs connus GT, images / instances ; communes et nouvelles séparées. Le regroupement
ne transforme pas les sous-types d’une classe COCO en nouvelles catégories.

| Famille | Périmètre | Ciblage images / GT | Contrôle images / GT |
| --- | --- | ---: | ---: |
| person | commune | 6 / 29 | 0 / 0 |
| cat | commune | 6 / 6 | 1 / 1 |
| dog | commune | 8 / 9 | 3 / 3 |
| bottle | commune | 7 / 22 | 1 / 2 |
| chair | commune | 8 / 16 | 0 / 0 |
| toothbrush | commune | 7 / 10 | 0 / 0 |
| trash_can | hors 80 | 7 / 8 | 0 / 0 |
| mirror | hors 80 | 10 / 14 | 0 / 0 |
| lamp | hors 80 | 8 / 25 | 1 / 2 |
| cabinet | hors 80 | 7 / 11 | 1 / 13 |
| towel | hors 80 | 6 / 16 | 0 / 0 |
| footwear | hors 80 | 7 / 31 | 2 / 3 |
| hat | hors 80 | 9 / 181 | 1 / 1 |
| soap | hors 80 | 6 / 10 | 0 / 0 |
| toothpaste | hors 80 | 6 / 12 | 0 / 0 |
| bathtub | hors 80 | 7 / 7 | 0 / 0 |
| doorknob | hors 80 | 7 / 15 | 0 / 0 |
| lightbulb | hors 80 | 6 / 30 | 1 / 2 |

12 familles hors 80, 6 communes. Ampoule conservée même sans libellé PF explicite identifié :
ne pas retirer une cible défavorable avant/après résultats. Fauteuil/races sont dans les
familles communes, pas comptés comme nouveaux objets. Les groupes n’incluent ni accessoires
(shoebox/shoelace), ni composants/contenants faux amis (lamp shade/soap dispenser).
Les 32 contrôles ne sont pas un échantillon de prévalence de l’usage personnel : résultats
séparés du ciblage, dénominateurs GT en-scope / tous GT et ignorés explicités.

ADE 16 : bâtiment présent dans 12, arbre 11, plante 7 ; union sémantique **séparée des instances**,
aucune moyenne mélangée à LVIS. Images choisies par GT seulement, au moins 4 par catégorie,
seed identique. Les 12 visuels sont déjà fixés par IDs : 2 contrôles, 6 cas ciblés, 4 ADE
(dans selection-proposal.json). Panneaux image/GT/baseline/PF, contours réels issus des
masques et labels lisibles, **y compris sorties PF non rattachées** (liste/compte/score).
Pas de sélection des seuls succès ni galerie/framework supplémentaire.

## Mapping sémantique fixé puis relu, jamais fuzzy

101 alias PF LVIS et 66 ADE proposés, IDs+noms vérifiés dans le vocabulaire 4585 figé.
Correspondances exactes/synonymes/sous-types explicites, dont American/english shorthair,
persian cat/siamese, chihuahua/pomeranian : pas de sous-chaîne ni projection 80 avant argmax.
Un label coarse feline peut couvrir plusieurs classes possibles : son périmètre de
vérification inclut cougar 316 et les autres félins sauvages ; `feline` non apparié reste
`always_unknown` car sa portée excède les GT domestiques. Le label générique `towel`
inclut paper_towel 757 dans sa portée et applique la même politique hors GT textiles.
Ces ignorés sont publiés à part (`outside_target_scope`), pas effacés du compte rendu.
Shepherd ambigu non accepté automatiquement. Le reviewer
révise les sens **avant résultats**, puis SHA scellé. Reconnaître une famille ne certifie
pas une race, un rôle ou un lieu fin annoncé par PF. Aucun remapping opportuniste après test.
Arbre inclut espèces pertinentes, dont pine/willow (pas pinecone/nut/pineapple).
Bâtiment réunit GT building 2+house 26 et sous-types physiques (manor/stone house,
monastery/shed/barn/hut/castle/cathedral/church/garage/mosque/pagoda/palace/skyscraper,
temples nommés), plante
houseplant ; exclut tree house/shoe tree/building block. Ambigus/non mappés conservés,
listés et comptés, sans les transformer en FP ou reconnaître leur sens par supposition.

## Profils identiques de forward, variantes préfixées de post-traitement

Deux exports existants strictement inchangés : baseline ONNX SHA 383257…321b ; PF brut
SHA c94efd…f2bb. CPU ORT 1.31, 2 intra/1 inter, FP32/batch1/640², même RGB/letterbox 114
et géométrie native. Aucun réexport, poids modifié, texte/CLIP ni download caché.
PF argmax sur **4585** avant mapping, jamais filtré vers 80.
Profils : baseline class-aware actuelle ; PF agnostic=True principal selon
[recommandation éditeur](https://docs.ultralytics.com/models/yoloe#deployment-notes) ;
PF class-aware comme contrôle (agnostic_nms=False, donc NMS par classe).
Confiance principale 0,35, sensibilité **0,20/0,35/0,50 tous rapportés**, NMS IoU 0,5.
Même pré-cap 300 candidats ; caps 20 et 100 diagnostics, **100 n’est pas illimité**.
Pas de meilleur seuil/profil choisi a posteriori ; scores non calibrés entre modèles.
Forward une seule fois par image/modèle, traitement séquentiel et libération des sorties,
pas de conservation disque des énormes tenseurs PF. Les sensibilités ne nécessitent
pas d’autres forwards. Ce runner n’est pas encore implémenté ni lancé.

## Comptes et qualité, avec annotations partielles

L’[évaluateur LVIS officiel](https://github.com/lvis-dataset/lvis-api/blob/7d7f07def11da91f8b2710ce352c62a78fd5a7ad/lvis/eval.py)
sert de référence d’interprétation, pas d’AP officielle revendiquée. Pour chaque prédiction :
appairer une seule GT compatible par score décroissant et IoU boîte ≥0,5 ; doublons non
appariés restent soumis aux règles de vérification. Un alias dépassant le périmètre GT
évalué a une politique conservatrice `always_unknown` : un lion ou papier positif hors
pool cible ne devient pas FP sous prétexte que toute sa portée serait vérifiée.
Ces nonmatches ont un motif distinct `outside_target_scope`. Une catégorie positive
**ne ferme pas sa famille**. Nonmatch dans not_exhaustive→ignoré ; groupe coarse non apparié FP seulement
si **toutes** ses catégories possibles sont positives exhaustives ou négatives vérifiées.
Sinon ignoré/inconnu ; catégorie ni positive ni négative vérifiée n’est pas un FP.
Rapporter TP/FP/FN, précision conditionnelle, rappel des GT annotées, comptes ignorés/
non mappés et dénominateurs, par catégorie/strate et micro ; macro non pondérée en complément.
Un dénominateur nul est non mesuré, jamais une réussite.

Hors80 : baseline a un rappel typé structurellement limité ; mesurer **séparément**
localisation class-agnostic sur mêmes GT nouvelles avec toutes ses propositions, sans
prétendre qu’elle a reconnu leur nom. Sur contrôle 32, localisation sur tous les GT
annotés et métriques typées limitées aux familles couvertes, avec couverture déclarée.

Masques GT LVIS rasterisés par pycocotools 2.0.11 officiel en dossier privé (voir README),
aucun fillPoly approximatif. Masques prédits logit>0, crop/rotation/letterbox identiques.
Rapporter IoU et F1 des frontières binaires (tolérance préfixée 0,5% diagonale native,
minimum 1 pixel, voisinage carré), pas épaisseur/crénelage du trait UI. Par modèle :
conditionnel sur matches **et sur tous GT avec les manqués à zéro** ; puis comparer les
**mêmes IDs GT appariés par les deux** avec effectif. Ne pas déduire qualité globale
sur les seuls survivants. IoU/métriques de frontières primaires 0,35 caps 20/100 ; sensibilités
0,20/0,50 : détection et comptes, sans sélection avantageuse des masques.
ADE : union des masques par catégorie, IoU/F1 par classe sur GT≠0, supports et surface ;
pas d’AP d’instances ni de vérité terrain produite par baseline/PF. Sorties de noms fins
non assimilées à une preuve d’identité spécifique. Aucun AP universel, aucune conclusion
statistique de population depuis ce sous-ensemble enrichi.

## Validation de cette phase et critère de suite

8 tests synthétiques Green initiaux après Red fonctionnels, puis 13 tests après corrections de revue, dont coarse/partial GT, sémantique
explicite, doublons 1 à 1, masques avec trous/manqués, rectangle raster officiel 4 pixels,
localisation sans nom et tirage déterministe. Preuves/commandes dans README. La dilation des frontières utilise `cv2.dilate` rectangulaire, même tolérance/bords
et résultat que la définition de voisinage testée aux rayons 0/1/6. Exclusion ADE insensible
à la casse et vérifiée non vacue (anciens 1/2), 144 IDs et 12 visuels inchangés après correction.
La revue indépendante du protocole/mapping/sélection précède images et prédictions. Les gates 80/80,
duplication 3%, complexités 10/15 du code livré ne changent pas ; ce noyau exploratoire
n’est pas promu en production ni couvert par une fausse exécution de la qualité app.

Décision future fondée d’abord sur bénéfice qualité/vocabulaire utile, erreurs/objets
manqués/contours, puis coûts selon caméra ou image ponctuelle. Pas de poids arbitraires,
choix définitif ou changement du modèle par défaut dans cette phase.

## Préparation de phase 2 — 27 octobre 2026

Phase 1 revue conforme ; les 144 entrées sont acquises et vérifiées indépendamment,
sans changement des IDs/visuels. Le runner et ses 37 tests synthétiques sont préparés,
sans forward réel et en attente de revue. Le gel source/entrées et les commandes sont dans
[le rapport de préparation](../validation/m1-yoloe-quality-preparation.md).
La sélection, le mapping et le protocole JSON de phase 1 restent des snapshots immuables.

Les métriques de boîtes sont calculées avant rejet des masques vides à chaque seuil ;
à 0,35, `empty_masks` est explicite et le masque/frontière vaut zéro pour ces matches.
Une boîte localisée n’est pas nécessairement un objet détouré affichable par l’application.
ADE agrège les 16 images, y compris celles sans GT de la classe ; frontières uniquement
sur voisinages vérifiés, pas de frontière contre void, absence de support signalée `null`.

Compléments de revue du runner : les matches agnostiques uniques sur l’ensemble des
GT nouvelles mesurent aussi les masques/frontières, indépendamment du nom reconnu,
avec manqués à zéro et comparaisons sur mêmes GT. Axes d’images riches et contenant
au moins un petit GT cible issus de la sélection figée (26 et 45 images), avec supports,
comptes et qualité de **tous** les GT évalués dans ces images ; aucune mesure exclusive
des petits objets ni d’occlusion. Aucun changement de sélection, seuil ou modèle.

## État après exécution

Revue du runner conforme après les compléments, puis tester conforme : 37 tests,
288 forwards primaires, 2 592 enregistrements/18 profils, recomputation indépendante et
10 replays séparés sur 5 images. Gel `6ff9…dcb2` intact ; aucun fichier source/JSON/TOML
scellé modifié pendant l’exécution. [Résultats et limites](m1-yoloe-quality-results.md).
