# Évaluation qualité YOLOE — préparation avant prédictions

27 octobre 2026. **Rapport historique de préparation, rédigé avant prédictions.**
Phase 1 puis runner revus conformes ; la campagne ultérieure a été exécutée et validée
indépendamment : [résultats](../research/m1-yoloe-quality-results.md).
Les constats d’absence de forward ci-dessous décrivent la livraison de préparation.
L’application, ses exports, son APK, ses gates et le téléphone ne sont pas modifiés.

## Entrées et gel

Le [protocole](../research/m1-yoloe-quality-protocol.md) et le
[mapping/sélection](../../experiments/m1-yoloe-quality/README.md) restent ceux de la revue.
128 LVIS (96 ciblées +32 contrôle) et 16 ADE acquis, 20 287 600 octets ; IDs/licences
et 12 visuels inchangés. Zéro doublon exact et zéro rapprochement dHash≤4.
Les 144 SHA/dimensions/orientations/licences/URL et les 16 JPEG ADE identiques au ZIP
ont aussi été contrôlés indépendamment par le reviewer. Pas d’inférence lors de ces contrôles.
Inspection GT préalable par l’orchestrateur : aucun décalage spatial évident sur les 12 cas,
contextes variés ; ce n’est pas une preuve de qualité modèle. LVIS62692 contient une personne
visuellement présente mais non vérifiée/annotée dans la cible : ne pas la compter comme FP.

Artefacts ignorés sous `build/reports/validation/m1-yoloe-quality/` :

- `inputs-manifest.json`, SHA `faa145fbe98b97bf55cbbd3ebfdc46bb2ae49b352ff6774f7d2d97a1110a08b8` ; immuable.
- `sealed-manifest.json`, SHA `6ff9b6b463dde7b28e7f065438aa7a6162437b4ccf16d9d226ee38fa4543dcb2` ; 197 références, horodaté avant modèle.
- `phase2-validation.json`, synthèse des vérifications réelles et artefacts protégés.
- `preflight-gt-contact.png` / `preflight-gt-inspection.json`, examen des seules entrées/GT.

Le gel contient sources/mapping/sélection/protocole, lock et versions, deux modèles,
144 JPEG, annotations et 16 masques ADE, fichiers du rasteriseur officiel privé.

Reproduction byte exacte : le gel décrit les octets du working tree Windows ; Git normalise
les sources CRLF en LF au checkout, ce qui change leurs hashes. Utiliser une copie des
27 sources exactes archivées dans `source/frozen-source-bytes.zip` du dossier de validation
ignoré, avec données/modèles/addon épinglés, sans modifier les empreintes initiales.
L’archive a été créée **après les résultats**, puis chaque membre a été vérifié contre le
hash pré-inférence du gel `6ff9…dcb2` (`source/frozen-source-snapshot.json`) : 74 587 octets,
SHA `16b754e268c7ce51c7fc97dc198cfa1352d0a14d0a81df0ace09949272f32a60` ; aucune nouvelle inférence.

pycocotools 2.0.11 reste sous `.tools/m1-yoloe-quality/pydeps`, injecté au seul processus,
sans mutation de l’environnement ML. Wheel/licence/OSV ciblé : acquisitions de la racine
dans `source/pycocotools-acquisition.json` et `source/pycocotools-osv.json`.

## Vérifications exécutées

Tests ciblés avec le Python autonome déjà verrouillé et chemin privé pycocotools :

```powershell
.\.tools\m1-model-spike\.venv\Scripts\python.exe -c "import sys,unittest;sys.path.insert(0,'.tools/m1-yoloe-quality/pydeps');r=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.discover('experiments/m1-yoloe-quality'));sys.exit(not r.wasSuccessful())"
.\pc\.venv\Scripts\python.exe -m ruff format experiments/m1-yoloe-quality
.\pc\.venv\Scripts\python.exe -m ruff check --select E9,F63,F7,F82 experiments/m1-yoloe-quality
```

État initial avant revue : **35 tests, 0 échec/erreur, sortie 0** (`phase2-final-tests.log`, 0,773 s),
formatage et contrôle statique ciblé sortis 0. Aucune couverture de production ou analyse
statique complète n’est revendiquée pour ce spike hors production.
Le contrôle statique a fait clarifier la possession explicite de la session native ; tests revérifiés.

Red → Green réels, fichiers `phase2-*-red.log` / `phase2-*-green.log` :

- Entrées : JPEG valide, mauvais MIME/tailles/dimensions, doublons exacts/proches.
- Postprocess : argmax vocabulaire entier avant mapping, NMS par classe/agnostique,
  pré-cap300/cap100, profils, sorties invalides/non finies, projection et masques bornés.
- Métriques : classe absente, invariance void, dénominateurs nuls, micro/macro,
  comparaisons sur mêmes GT. Les tests des agrégats de bout en bout ont ensuite été ajoutés.
- Scoring : boîte TP conservée malgré masque vide, qualité zéro/manqués, ignorés/non mappés,
  house inclus dans building. Régression supplémentaire Red → Green sur contrôle hors familles.
- Orchestration : 1 forward/image/modèle, 1 session à la fois, fermeture sur erreur.
- Gel : code altéré ou SHA du gel erroné rejeté avant chargement natif.
- Frontière native simulée : Red puis Green sur télémétrie désactivée avant création
  de session, provider CPU explicite et référence native libérée après fermeture du scope.
  Le dernier Green est inclus dans les 35 tests ; aucun chargement réel d’ORT.

Le premier essai des tests d’orchestration a aussi rencontré le répertoire temporaire
Windows de la sandbox non supprimable ; les temporaires sont désormais bornés au dossier
`build/reports/validation/m1-yoloe-quality`, puis les 3 tests sont sortis 0.
Ce problème de plomberie n’est pas compté comme Red fonctionnel.

`freeze_study.py` puis la vérification seule documentée dans le README sont sortis 0.
Cette dernière vérifie les empreintes sans décoder à nouveau les images et sans importer ORT.
Deux exports ONNX +APK0.5.1 : empreintes/tailles comparées à `source/before-artifacts.json`, inchangées.

## Portée et suite

Le runner prépare 288 forwards et 2 592 enregistrements de profil, sans les exécuter.
Boîtes avant rejet des masques vides ; masques/frontières seulement à 0,35, caps20/100.
Les inconnus/non exhaustifs/hors cible restent visibles et comptés séparément.
Les erreurs ADE sur classe absente participent aux intersections/unions globales ; void
ne fabrique pas de frontières, aucun support donne `null` plutôt qu’une réussite.
Panneaux aux 12 IDs fixés, noms PF non mappés conservés dans légendes et JSON.

La revue du runner précède toute prédiction. Les tests natifs sur les vrais modèles,
les résultats statistiques et les panneaux de prédiction sont donc **non exécutés** ici.
Pas de tests Android/global Quality/scan/export/accès téléphone ; aucune donnée privée.
Le code est expérimental et ne devient pas une dépendance de l’application.
Le bénéfice qualité/vocabulaire utile reste à mesurer ; le choix du modèle reste ouvert.

## Correction ciblée de revue du runner, toujours avant prédictions

Deux compléments retenus ; aucun autre défaut de parsing, void, ownership ou protocole
signalé dans cette revue. Les entrées, modèles, mapping et sélection restent intacts.

1. Les matches agnostiques uniques sur **tout le pool des GT nouvelles** conservent aussi
   leurs IoU/F1 de masque : matches seuls, tous GT avec manqués à zéro et mêmes GT appariés
   entre modèles. Restitution `geometry_masks_new`, indépendante des scores typés ; pas de
   rematching par famille. Red réel : mauvais nom + masque parfait produisait encore un score
   géométrique non mesuré ; Green : typed TP=0, géométrie IoU/F1=1, qualité zéro pour le GT supplémentaire manqué.
2. Restitution des axes `rich_images` et `images_with_small_target` depuis la sélection
   figée, en plus de contrôle/ciblage. Red réel : l’axe riche était absent. Green : supports,
   comptes, qualité tous GT et paires des mêmes GT restitués, image sous les seuils exclue.
   Riches : ≥20 GT LVIS annotées : 26 images (22 ciblées +4 contrôle), 308 GT évaluées.
   Contenant un petit GT cible aire<32² : 45 images (41 +4), 439 GT évaluées.
   Ce sont des **strates d’images se chevauchant** ; tous les GT évalués de l’image sont mesurés,
   aucune prétention de métrique sur les seuls petits objets ou sur l’occlusion.

Preuves `phase2-review-masks-{red,green}.log`, `phase2-review-axes-{red,green}.log` :
5 tests ciblés par module, un Red fonctionnel chacun puis Green. Suite finale
`phase2-review-final-tests.log` : **37 tests, sortie 0, 0,827 s**. Ruff format et contrôle
syntaxique ciblé sortis 0. Pas de Quality globale, scan, tests Android ou inférence.

Ancien gel `215a3b95f19b2e75cef4df416b8eb3bb5ad3a6c4eba9789d885fe3ae303786dd`
conservé dans `sealed-manifest-phase2-proposal-215a.json`. Nouveau gel courant ci-dessus,
197 références ; `phase2-validation.json` actualisé. Revue ciblée encore nécessaire avant
le premier forward. Aucun résultat de modèle n’a été créé.

## Événement postérieur : exécution et validation indépendantes

Le tester a exécuté la campagne primaire après revue ciblée conforme : 37 tests sortis 0,
288 forwards, 2 592 enregistrements/18 profils. Recomputation indépendante conforme :
377 625 comparaisons et 1 024 paires. Dix replays séparés sur 5 images, 28 425 560 pixels
vérifiés ; résultats, gel 197 références, modèles et APK inchangés à l’issue des contrôles.
Preuves ignorées : `tester-results.json`, `tester-recompute-results.json`,
`tester-replay-results.json`, `results-v1/summary.json` et `per-image.jsonl` dans le dossier
de validation de l’étude. Aucun nouveau test/build/scan/export/téléphone lors de cette mise à jour
documentaire. La [synthèse qualité](../research/m1-yoloe-quality-results.md) conserve tous les
profils et limites ; l’étude ne change ni modèle par défaut, ni paramètres, ni statut global M1.
