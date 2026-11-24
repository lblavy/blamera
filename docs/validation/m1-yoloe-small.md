# Validation de la comparaison YOLOE Small

28 octobre 2026. **Conforme : revue de préparation, revue des résultats/galerie et validation
scientifique indépendante sur PC.** Aucun calcul restant ni changement de l’application.

## Préparation acquise

[Protocole Small](../research/m1-yoloe-small-protocol.md),
[adaptateur et commandes](../../experiments/m1-yoloe-small/README.md).
Export/parité hors corpus préparés ; noms et ordre des 4 585 catégories identiques au Nano.
Gel Small `38562deb71a3a1a9fc4c2749b1baff08def2cb6f84d86b25eb4a71a95ccdc445`,
ancien gel `6ff9…dcb2` et références baseline/Nano conservés.
Les 6 tests Small ont été réexécutés par le tester, sortie 0 (0,491 s), ainsi que les
37 tests hérités, sortie 0 (0,836 s). Format et contrôle syntaxique ciblé conformes.

## Exécution primaire et contrôles indépendants

Primaire confirmé terminé, sortie 0 : Small seulement, 144 forwards, 12 profils,
1 728 enregistrements ; `reference_results_reused_not_rerun=true`. Baseline/Nano relus
sans réexécution. Recomputation indépendante conforme : 304 926 comparaisons, 3 072 paires.
Replays natifs et verdict final du tester conformes, détails ci-dessous.
Résumé SHA `3ffabdaa748b1947954086eceadc1398cc419aee0be25af86fcc65a416f015b3`,
JSONL SHA `c56c1726818956a9966f6da9c7ed87ea51b778c334bdd93f5015fe53d08d609f`.

Cinq cas sélectionnés indépendamment ont validé 20 profils et 38 372 336 pixels de masques,
par reconstruction bilinéaire indépendante et comparaison pixel à pixel, GT officiels,
IoU/frontières/void recomputés. Six forwards diagnostiques au total : première passe
avec 4 cas complets et un cinquième partiel, puis reprise de ce seul cas ADE368 conforme.
Le harnais du tester exigeait à tort `possible_categories` LVIS sur un alias sémantique ADE
(`KeyError`, journal initial sortie 1). Correction du helper ignoré, reprise sortie 0 ;
aucune source scellée/modèle/donnée ni primaire modifié ou réexécuté. Ressources natives
libérées sur les deux chemins. Journaux `tester-replay.log` et `tester-replay-resume.log` conservés.

Deux gels vérifiés avant/après : 197 références héritées +90 Small ; 10 artefacts protégés
(anciens résultats, modèles, APK) intacts. Douze panneaux fixés produits et contrôlés ; plusieurs exemples examinés qualitativement ; aucune
image choisie après prédiction ni rescoring qualitatif des noms manquants.
Preuves locales ignorées :

- [Verdict tester](../../build/reports/validation/m1-yoloe-small/tester-results.json).
- [Recomputation](../../build/reports/validation/m1-yoloe-small/tester-recompute-results.json).
- [Replays et incident/reprise](../../build/reports/validation/m1-yoloe-small/tester-replay-results.json).
- [Résumé complet](../../build/reports/validation/m1-yoloe-small/results-v1/summary.json) et
  [enregistrements par image](../../build/reports/validation/m1-yoloe-small/results-v1/per-image.jsonl).

Points d’entrée utilisés pour les tests, la vérification et le primaire ; **ne pas relancer**
pour ce suivi documentaire :

```powershell
.\.tools\m1-model-spike\.venv\Scripts\python.exe -m unittest discover -s experiments/m1-yoloe-small
.\.tools\m1-model-spike\.venv\Scripts\python.exe -c "import sys,unittest;sys.path.insert(0,'.tools/m1-yoloe-quality/pydeps');r=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.discover('experiments/m1-yoloe-quality'));sys.exit(not r.wasSuccessful())"
.\.tools\m1-model-spike\.venv\Scripts\python.exe experiments/m1-yoloe-small/run_small.py --seal-sha256 38562deb71a3a1a9fc4c2749b1baff08def2cb6f84d86b25eb4a71a95ccdc445
.\.tools\m1-model-spike\.venv\Scripts\python.exe experiments/m1-yoloe-small/run_small.py --seal-sha256 38562deb71a3a1a9fc4c2749b1baff08def2cb6f84d86b25eb4a71a95ccdc445 --execute
```

## Résultats et portée

[Rapport comparatif](../research/m1-yoloe-small-results.md) renseigné depuis le résumé complet
et les preuves indépendantes conformes.
Reconnaissance typée, géométrie sans nom, couverture et précision des contours restent séparées.
Aucune nouvelle mesure Android, application, photo personnelle, modification d’environnement
global, build, scan global, APK ou accès téléphone dans ce suivi documentaire.
L’étude compare la qualité sur ce corpus restreint, pas la latence Android Small ou une
population d’usage générale. Aucun choix définitif du modèle, nouveau seuil ou mapping déduit.
