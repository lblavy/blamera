# M1-06 image — contours interpolés, 0.3.1

26 octobre 2026. **Revue et tester indépendants conformes sur PC ; APK principal installé,
validation visuelle humaine conforme : « Contours améliorés et bien placés ».**
La partie image est validée ; M1-06 reste partiel, caméra ouverte.
Branche locale `codex/m1-image-segmentation`, base `a2ea69e` (`HEAD` lors des métriques).
Aucune action téléphone, clé privée, publication, compte, commit ou push par le coder.

## Demande et périmètre

Après0.3.0, l’utilisateur rapporte : « ça a l'air de fonctionner. contours un peu crénelés
et il a pris un chat pour un chien. mais sinon ça a l'air ok ».
Le parcours logiciel M1-03 est clos avec ces limites qualitatives, pas avec une promesse
de précision. Aucune photo privée ou score n’a été demandé ou examiné.
Les quatre observations ARM0.3.0 sont dans [M1-03](m1-03.md) ; elles ne mesurent pas0.3.1.

Ce correctif remplace uniquement le tracé en cellules par l’isocontour interpolé des
logits, niveau0. Modèle/poids/ORT1.31.0, confiance0,35, NMS0,5, catégories et prétraitement
restent inchangés. Le routage synthétique classe15→Chat/classe16→Chien passe par le vrai
post-traitement et ses labels. Sans l’image et les scores, la cause exacte de la confusion
ne peut être établie ; aucun entraînement ou réglage sur une anecdote.

[ADR et règle de topologie](../decisions/0003-image-segmentation.md),
[reconstruction](../image-segmentation.md), [installation protégée](../install-android.md).

## TDD et contrôles réellement exécutés

- Red : `scripts/gradle.ps1 :app:testDebugUnitTest --tests dev.blamera.vision.IsocontourTest`,
  sortie1, **2/2 échecs fonctionnels** sur les anciennes frontières : diagonale oblique
  absente et croisement -1→+3 placé sur une cellule plutôt qu’à la position240.
  Log `.gradle-user-home/m1-06-image-red.log`.
- Green ciblé après implémentation : **7 tests isocontour +8 post-traitement**, sortie0 ;
  format et analyse statique conformes. Une erreur de compilation de référence de fonction
  de test a été corrigée ; elle n’est pas revendiquée comme Red.
- Tests numériques : interpolation au niveau0 depuis les centres, disque sous-pixel,
  trous et composantes fermés (degré2), les deux ambiguïtés diagonales4-connexes,
  crop fractionnaire, bords image non carrée, champ vide, dimensions/données invalides,
  NaN et plafond de segments. Régression NASA native conservée, UI et Fit inchangés.
- Refactor limité : `LogitGrid` pour la valeur bilinéaire, traceur et axe du domaine.
  L’ancien tracé est conservé seulement en fixture de test pour comparer avant/après.
  Aucun nouveau runtime ou dépendance de rendu dans l’application.
- Après vérification de la clé personnelle existante : `scripts/pc.ps1 Quality`,
  **sortie0**, **60 tests Android +56 scripts +4 PC**, aucun échec.
  Log `.gradle-user-home/m1-06-image-quality.log`.

| Composant / mesure | Global lignes / branches | Nouveau lignes / branches |
| --- | --- | --- |
| Mobile | 95,20% / 89,57% | 100% / 94,44% (92 lignes exécutables nouvelles) |
| Scripts qualité | 94,44% / 86,36% | Non applicable, pas de nouveau code |
| PC | 100% / 100% | Non applicable, pas de nouveau code |

Duplication nouveau mobile **0/138=0%**, globale0,48% ; scripts/PC0%.
Complexités10/15 conformes, aucun seuil ou exclusion changé. Build, lint, format et analyse
statiques passent ; licences conformes, scan OSV réel Maven60 modules/92 entrées de licences,
PyPI35/35, aucune vulnérabilité active. Rapports dans `build/reports/quality/` et `app/build/reports/`.

## Comparaison réelle sur PC

Les tests écrivent leurs segments Kotlin effectifs avant/après dans
`build/reports/validation/m1-06-image/{disk,nasa}-contours.json`. Le renderer Matplotlib
déjà disponible dans l’environnement du spike produit les figures locales, inspectées.
Aucun scikit-image installé, nouvelle dépendance ou capture téléphone.

| Fixture | Avant0.3.0 | Après0.3.1 |
| --- | --- | --- |
| Disque analytique, grille32² | 74 segments, tous axiaux ; erreur radiale moyenne3,8009px | 74 segments obliques ; moyenne0,3816px, maximum0,5414px |
| NASA publique | 584 segments, aucun oblique | 596 segments, dont496 obliques |

Le disque compare le milieu de chaque segment à son cercle analytique (centre313/297,
rayon185). C’est une mesure géométrique synthétique, pas une précision de reconnaissance.
Sur NASA, personne score**0,88390815**, masque binaire et IoU PyTorch**0,9884497** inchangés.
Les contours suivent désormais les croisements interpolés, pas les cases du masque.
Les règles explicites préservent trous/composants et ferment aux bords du domaine ;
elles n’ajoutent aucun détail au champ160² appris par le modèle.

Figures locales comparables :
[disque](../../build/reports/validation/m1-06-image/disk-comparison.png),
[NASA](../../build/reports/validation/m1-06-image/nasa-comparison.png).
La NASA conserve sa provenance publique du spike ; aucune image utilisateur n’est présente.

## APK et limites restantes

`dist/blamera-0.3.1-debug.apk`, **69 908 907 octets**, package `dev.blamera`, version0.3.1/code5,
min/target/compile36, ABIarm64-v8a. Aapt confirme seulement la permission interne AndroidX,
aucune caméra/media/réseau. Signature v2 vérifiée, certificat inchangé :
`eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555`.

SHA-256 APK : `ea69d7d1d0f52d8c8379d2a77b1bc537d11463a6ef8eec0740fcb519e4fca666`.
Le modèle embarqué conserve SHA `38325721a41fcd732b0a30512ffa8d564b1a8a74ca8c1d6b7e7f37609b2d321b`.
L’APK0.3.0 est conservé, SHA `96701bb0d76abcce69f3190b25178acdbc7c7b4f00ce21abaa64c79566d9ebe7`.
Preuves PC locales : `build/reports/validation/m1-06-image/apk.json`, badging/signature et checksum `dist/`.

## Revue indépendante et installation — 26 octobre 2026

Revue conforme, puis tester indépendant conforme, sans défaut retenu. Le tester a
réellement réexécuté les 60 tests Android et la globale, sortie0 (56 scripts, 4 PC),
avec les mêmes métriques et artefacts que ci-dessus. L’orchestrateur a revérifié
identité, hash et certificat, puis installé exclusivement l’APK principal :

```text
adb -s [cible locale] install -r dist/blamera-0.3.1-debug.apk
Performing Streamed Install
Success
Sortie : 0
```

Aucun lancement, log, réglage, permission, test APK ou autre action téléphone pendant
cette mise à jour. L’utilisateur a été invité à reprendre la même photo si possible,
analyser puis vérifier que les contours sont moins crénelés et bien placés.
Aucune photo n’est demandée ; le modèle reste identique, donc une confusion de catégorie
reste possible. L’utilisateur a ensuite répondu exactement : **« Contours améliorés
et bien placés »**. Ce retour humain valide visuellement la partie image0.3.1 après
ouverture et analyse manuelles ; aucun lancement automatique ou test instrumenté.
La même photo était suggérée si possible, mais son réemploi effectif et le format
ne sont pas précisés et ne sont donc pas revendiqués. La confusion chat/chien connue
reste une limite qualitative : ce retour ne prouve aucune précision globale.
M1-06 demeure ouvert pour la caméra, M1-01 pour budgets/chauffe et mesures prolongées.

Aucune performance ARM0.3.1, chauffe, pic mémoire ou précision
générale ne découle de ces figures. Les quatre échantillons0.3.0 sont non contrôlés,
PSS échantillonné pas pic ; M1-01 budgets/prolongé/chauffe restent ouverts.
La caméra n’est pas implémentée ; CI distante toujours volontairement reportée.
Contrôles documentaires directs sortie0 : 32 titres/statuts/ordre cohérents, liens locaux,
UTF-8 et `git diff --check`. APKs0.3.0/0.3.1 stables après la globale ; aucun changement
applicatif effectué ensuite. Figures publiques inspectées, aucune donnée privée utilisée.
