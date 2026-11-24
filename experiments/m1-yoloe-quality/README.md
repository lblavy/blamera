# Phase 1 — protocole qualité YOLOE, sans prédiction

**État courant : campagne exécutée après revue, validation scientifique indépendante conforme.**
[Résultats annotés](../../docs/research/m1-yoloe-quality-results.md). Les sections de phase 1/2
conservent l’historique de préparation avant le premier forward ; les fichiers scellés ne changent pas.

Proposition à revoir avant acquisition d’images et exécution. Aucun runner d’inférence
n’est livré ici. `protocol.json` porte paramètres/modèles figés ; `mapping-proposal.json`
les correspondances explicites de sens ; `selection-proposal.json` les 144 IDs et effectifs.
Les données binaires restent dans build/models/.tools ignorés, jamais dans Git/APK.

## Reproduire les seules opérations autorisées en phase 1

Depuis la racine, environnement CPU historique réutilisé **sans mutation** : Python 3.12.14,
NumPy 2.5.4/OpenCV 5.0.0.93, lock `../m1-yoloe/uv.lock` référencé et hashé dans protocol.json.
Les annotations locales ont été acquises par l’orchestrateur et sont vérifiées par SHA.
Aucun JPEG COCO individuel téléchargé ni JPEG ADE extrait/ouvert ; l’archive ADE a été
acquise par l’orchestrateur, seuls ses masques GT sont utilisés ici.

```powershell
$qualityPython = '.\.tools\m1-model-spike\.venv\Scripts\python.exe'
& $qualityPython -c "import sys,unittest;sys.path.insert(0,'.tools/m1-yoloe-quality/pydeps');r=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.discover('experiments/m1-yoloe-quality'));sys.exit(not r.wasSuccessful())"
if ($LASTEXITCODE -ne 0) { throw 'Noyau protocole en echec' }
& $qualityPython experiments/m1-yoloe-quality/prepare_selection.py
if ($LASTEXITCODE -ne 0) { throw 'Selection metadata en echec' }
```

pycocotools 2.0.11 est provisionné seulement dans `.tools/m1-yoloe-quality/pydeps`,
wheel Windows CP312abi3 SHA
`ffe806ce535f5996445188f9a35643791dc54beabc61bd81e2b03367356d604f`.
Licence FreeBSD ; aucune installation dans l’environnement ML/PATH. `sys.path` ci-dessus
ne vaut que pour ce processus. Recette/version/provenance/scan ciblé :
`build/reports/validation/m1-yoloe-quality/source/pycocotools-acquisition.json`.
Rasterisation officielle `mask.frPyObjects/merge/decode`, jamais fillPoly approximatif.

## État et preuves

Noyau expérimental non production, hors commande qualité commune ; règles/gates de l’app
inchangés. Tests comportementaux : Red réel puis Green, pas d’historique reconstruit.
6 cas initiaux : 5 assertions et un alias absent en Red, puis Green. Rasterisation et
localisation hors vocabulaire : Red fonctionnels, puis **8 tests Green** après refactoring.
Journaux dans `build/reports/validation/m1-yoloe-quality/` : `protocol-red.log`,
`protocol-green.log`, `raster-red.log`, `geometry-red.log`, `protocol-refactor-green.log`,
`selection-license-filter.log`. Ruff format et contrôles syntaxiques ciblés conformes.
Aucune couverture de code de production ou validation des modèles revendiquée pour ces tests.

Le premier tirage était une proposition avec licences ND ; **avant toute prédiction**,
filtre IDs 1/2/4/5 autorisé, même seed/quotas et tirage régénéré. Population LVIS 19809,
COCO val2017 4809, population admissible 3221. Contrôle 32 indépendant des catégories,
ciblage 96 + ADE 16 ; au moins 6 images ciblées pour chaque famille, 22 scènes avec au
moins 20 instances LVIS annotées et 41 avec petits objets. L’annotation partielle ne
permet pas d’assimiler ces comptes au nombre réel d’objets de la scène.

Au gel historique de phase 1, les images et leurs SHA/tailles/dimensions/doublons
restaient à acquérir après revue, avant premier forward. Aucun résultat qualité.
[Protocole de revue](../../docs/research/m1-yoloe-quality-protocol.md).


## Correction de revue phase 1, avant exécution

Passage Red de régression : alias coarse hors GT (cougar non vérifié, positif hors
cible lion/papier) et exclusion ADE majuscule ; garde de validation d’alias hors GT.
`review-red.log` : 13 tests, 4 régressions fonctionnelles ; puis `review-green.log` :
13 tests conformes. L’équivalence de dilation bords/rayons 0/1/6 est déjà verte sur
l’ancien calcul : optimisation OpenCV conservant la formule, aucun faux Red revendiqué.
Les nonmatches des alias `feline` et `towel` sont `always_unknown`, comptés séparément
`outside_target_scope`, même lorsque l’objet hors cible est positif vérifié. Scopes
complétés avec cougar 316 et paper_towel 757 ; les GT évaluées restent domestiques/textiles.
Le validateur bloque un alias dépassant sa famille GT sans cette politique conservatrice.

Exclusion ADE insensible à la casse et non vacue : ADE_val_00000001/00000002 avec leurs
SHA d’origine sont enregistrés. Les 128 IDs LVIS, 16 ADE et 12 visuels restent identiques.
ADE bâtiment = union codes 2 building +26 house ; 66 alias ADE dont bâtiments physiques
(châteaux/temples nommés inclus sans valider le lieu exact), pine/willow pour tree.
Mapping/empreintes/sélection actualisés dans `review-selection.log` et
`phase1-validation.json`. Toujours aucun modèle/JPEG lancé ou acquis par ces commandes.

## Phase 2 : préparation figée, aucune prédiction

Les 128 JPEG LVIS +16 ADE sont acquis : 20 287 600 octets, dimensions vérifiées,
zéro doublon exact et zéro candidat dHash64 à distance ≤4. Les IDs n’ont pas changé.
`build/reports/validation/m1-yoloe-quality/inputs-manifest.json` est immuable,
SHA `faa145fbe98b97bf55cbbd3ebfdc46bb2ae49b352ff6774f7d2d97a1110a08b8`.
La racine a inspecté les 12 cas/GT avant prédictions (`preflight-gt-inspection.json` et
`preflight-gt-contact.png`) ; ce contrôle des entrées ne mesure pas la qualité des modèles.

Le runner est `study_runner.py` ; `study_runtime.py` ne charge ORT qu’à l’exécution
explicite. Les tests utilisent des tenseurs synthétiques et des frontières simulées.
`freeze_study.py` a produit `sealed-manifest.json` (197 références : entrées, GT,
code, anciens helpers géométriques en lecture, modèles, lock, rasteriseur privé).
SHA du gel : `6ff9b6b463dde7b28e7f065438aa7a6162437b4ccf16d9d226ee38fa4543dcb2`.
Les JSON de phase 1 gardent leur statut historique ; ce gel décrit l’état courant.

Le gel correspond aux octets du working tree Windows : un checkout normalisé LF modifie
les empreintes des sources CRLF. Pour un rejeu strict, utiliser une copie des 27 sources
exactes de `source/frozen-source-bytes.zip` sous le dossier de validation ignoré, avec les
données/modèles/addon épinglés ; ne pas changer les hashes initiaux. Archive de 74 587 octets,
SHA `16b754e268c7ce51c7fc97dc198cfa1352d0a14d0a81df0ace09949272f32a60`, créée **après les
résultats** : chaque membre a été vérifié identique au hash pré-inférence du gel `6ff9…dcb2`
(`source/frozen-source-snapshot.json`). Aucun fichier scellé, résultat ou modèle modifié.

Depuis la racine, vérification seule exécutée, sortie 0, **sans import ORT ni inférence** :

```powershell
.\.tools\m1-model-spike\.venv\Scripts\python.exe experiments/m1-yoloe-quality/study_runner.py --seal-sha256 6ff9b6b463dde7b28e7f065438aa7a6162437b4ccf16d9d226ee38fa4543dcb2
```

La revue indépendante et l’autorisation ont ensuite permis l’exécution primaire avec `--execute`.
`results-v1` existe désormais, validé ; ne pas relancer l’étude dans ce suivi documentaire.
Le runner refuse tout dossier de sortie existant pour conserver l’historique.
Un seul forward/image/modèle (288 au total), une session CPU à la fois, 2 threads intra/1 inter.
Réseau bloqué pendant la boucle ; ni Torch, Ultralytics, export, CLIP, ni téléchargement caché.
Les sorties PF brutes ne sont jamais sérialisées ; candidats ≤300 et masques source en
cache ≤200 par image, libérés avant l’image suivante. Les 18 profils sont tous conservés.

Les boîtes sont évaluées avant rejet des masques vides, à tous les seuils. À 0,35,
masques vides comptés séparément et qualité zéro : boîte retrouvée ≠ objet détouré affichable.
IoU/frontières LVIS sur matches, tous GT avec manqués à zéro, mêmes GT appariés entre modèles,
et par famille. Contrôle : localisation aussi sur tous les objets annotés hors familles cibles.
ADE : union building={2,26}, tree=5, plant=18 sur les 16 images, erreurs de classe absente
incluses. Frontières seulement où le pixel et ses quatre voisins sont vérifiés ; pas de contour
créé contre void/extérieur. Aucun support vérifiable : F1 non mesuré (`null`).
Les 12 panneaux fixés montrent image/GT/baseline/PF principal et les noms non mappés,
sans les masquer ; aucun panneau de prédiction n’a encore été produit.

37 tests ciblés conformes après les Red fonctionnels consignés ; commandes et limites dans
[le rapport de préparation](../../docs/validation/m1-yoloe-quality-preparation.md).
Le code reste un spike non importé par l’application : les gates de production restent inchangés.

### Compléments de revue du runner, avant prédiction

Les contours des catégories nouvelles sont évalués aussi **sans reconnaissance du nom** :
matching agnostique unique sur le pool complet des GT nouvelles, IoU/F1 sur matches,
tous GT avec manqués à zéro et mêmes GT appariés entre baseline/PF. Ces résultats sont
`geometry_masks_new`, séparés des scores typés ; aucune réutilisation de prédiction par famille.
Un masque parfait portant un mauvais nom donne donc qualité géométrique 1 et TP typé 0.

Axes d’images supplémentaires, provenant uniquement des métadonnées de la sélection figée :
`rich_images` (≥20 instances LVIS annotées : 26 images, 308 GT évaluées) et
`images_with_small_target` (≥1 GT cible aire<32² : 45 images, 439 GT évaluées).
Ces axes se chevauchent et incluent contrôle/ciblage ; les mesures portent sur **tous les GT
évalués de ces images**, pas seulement sur de petits objets. Effectifs, comptes typés,
localisation, masques avec manqués à zéro et paires des mêmes GT sont restitués par axe.
L’ancien gel `215a…86dd` est conservé dans `sealed-manifest-phase2-proposal-215a.json`.
Deux Red fonctionnels puis Green, suite finale 37 tests sortie 0 ; aucune inférence réelle.

## Exécution postérieure validée — 27 octobre 2026

Le tester a exécuté 288 forwards primaires, 2 592 enregistrements sur 18 profils puis
10 replays séparés sur 5 images. Recomputation indépendante : 377 625 comparaisons,
1 024 paires ; vérification de 28 425 560 pixels de masques. Les 37 tests sont conformes,
le gel 197 références et les artefacts protégés sont intacts. Preuves `tester-results.json`,
`tester-recompute-results.json`, `tester-replay-results.json` et `results-v1/` sous le dossier
de validation ignoré. Les 12 panneaux fixés sont produits, noms non mappés conservés.
Aucune nouvelle inférence n’a été lancée pour rédiger les résultats ; application inchangée.
