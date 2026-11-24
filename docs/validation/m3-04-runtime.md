# M3-04 — runtime facial Android : preuves natives intermédiaires

Date : 2 novembre 2026. Périmètre : moteur YuNet / alignCrop SFace / ArcFace
ResNet50 local, sans modification de la galerie PC ni des paramètres du modèle.
La revue indépendante et la validation finale sont coordonnées séparément.

## TDD et exécutions

- `FaceEngineTest` : quatre Red fonctionnels avant calculs / cycle de vie ; Green
  dans le batch partagé `ui-coder/red-recognition-green-bootstrap.log`.
- `FaceRuntimeInstrumentedTest.publicFixturesMatchTheFrozenPcPipeline` : Red
  réel sur l'émulateur PC `emulator-5580` avec la factory volontairement indisponible,
  puis Green après implémentation JNI (`runtime-coder/red-native-instrumentation.log`
  et `green-native-instrumentation.log`). Le code retour du transport Android
  n'est pas le verdict : le Green contient `OK (1 test)` et STATUS_CODE 0.
- Le build précédent le Green natif a exécuté 26 tests JVM ciblés, sans erreur
  ni échec ; APK x86_64 et APK de test archivés sous
  `build/reports/validation/m3-04/runtime-coder/emu-green-build`.
- Une tentative Gradle en sandbox a échoué sur la connexion au daemon ; ce
  résultat d'infrastructure ne compte pas comme Red fonctionnel.
- La qualification a été ajustée selon l'arbitrage : autocontrôle par instance
  de moteur, sans registre global ; une incompatibilité numérique déterministe
  reste mémorisée dans cette instance. Annulation et I/O peuvent être réessayées.
  Red ajusté : trois tests, un échec fonctionnel attendu dans
  `runtime-coder/red-instance-qualification.log`.

## Parité réelle sur émulateur

OpenCV Android 4.13.0, x86_64, trois références publiques figées : portrait NASA,
aucun visage et plusieurs visages. Aucun portrait personnel utilisé par le test.
Les tolérances ont été définies avant mesure et n'ont pas été changées.

| Mesure | Limite figée | Résultat du premier Green |
| --- | --- | --- |
| Erreur des boîtes | ≤1 px | 0 px |
| Erreur des cinq repères | ≤1 px | 0 px |
| MAE RGB de l'alignement | ≤1 sur 0..255 | 0 |
| Cosinus embedding PC / Android | ≥0,9999 | 1 |
| Delta de score des prototypes publics P et −P | ≤0,002 | 0 |
| Décisions connues / inconnues publiques | Identiques | Oui |

Le test dure 15,385 s ; le diagnostic indique 14 696 ms pour initialisation et
qualification à froid. Les analyses suivantes vide / multiple prennent 96 / 56 ms.
Ce ne sont pas des mesures de performance du Xiaomi ni un benchmark représentatif
sur photo personnelle.

Le delta PSS instantané à la fin de la qualification est 887 038 Kio, supérieur
au budget cible de 700 Mio. Ce n'est pas une mesure de pic mémoire et ce résultat
émulateur ne prouve aucune conformité au budget téléphone. Le Xiaomi doit encore
être mesuré après ouverture manuelle de l'APK principale par l'utilisateur.

## Couverture et limites

Un unique essai supplémentaire a désactivé `Net.enableWinograd(false)` ; les
deux variantes ont été exécutées dans des processus frais du même émulateur.
Le code conserve cette option CPU, sans changement des poids ou des tolérances.

| Mesure émulateur | Winograd par défaut | Winograd désactivé retenu |
| --- | --- | --- |
| Initialisation et autocontrôle | 14 696 ms | 3 952 ms |
| Delta PSS instantané | 887 038 Kio | 457 733 Kio |
| Cosinus minimal PC / Android | 1 | 0,9999999999917017 |
| Delta prototype maximal | 0 | 8,2983 × 10⁻¹² |
| Boîtes / repères / MAE RGB | 0 / 0 / 0 | 0 / 0 / 0 |

Les trois cas publics passent à nouveau : `OK (1 test)` en 4,503 s, et les
décisions restent identiques. Le delta mémoire diminue de 48,4 % ; il reste un
instantané sur émulateur, pas un pic ou une preuve téléphone. Les nouveaux APK
et la donnée native sont archivés dans `runtime-coder/emu-winograd-off`.

Les 24 tests JVM du runtime passent (calculs, invalides, assets corrompus,
parité incorrecte, annulation, fermeture différée et erreurs natives). La
fusion fraîche donne au composant `dev/blamera/faces/runtime` : 381/402 lignes
(94,78 %) et 284/349 branches (81,38 %), sans exclusion métier. Le rapport ne
contient aucun avertissement d'identifiants de classes incompatibles pour cette
étape. Ces métriques devront être reproduites après le formatage final.

Le rapport JaCoCo accepte la donnée native via
`-PfaceNativeCoverage=<chemin absolu vers coverage.ec>` ou
`ORG_GRADLE_PROJECT_faceNativeCoverage`. La donnée native doit correspondre aux
classes compilées : après modification du code ou formatage, reconstruire et
réexécuter le test sur l'émulateur avant le rapport final. Aucune classe métier
n'est exclue pour relever les métriques.

Avec `-PfaceInstrumentedCoverage=true`, AGP écrit les données JVM dans
`app/build/outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec`.
Sans ce flag, le chemin habituel reste `app/build/jacoco/testDebugUnitTest.exec`.
La tâche de rapport choisit le chemin correspondant, sans lire un ancien fichier
de l'autre mode. Pour un rapport seul après ces tests :
`jacocoDebugReport -x testDebugUnitTest` avec les deux paramètres de couverture.

La première donnée `.ec` est archivée (19 202 octets, SHA-256
`8e2ef250630f83c2e756676e077699389d9f01d2f55d539285500c9df35b52fa`).
Elle conserve la preuve de cette étape et ne suffit pas à qualifier les modifications
postérieures. Les métriques finales et la qualification ARM64 restent à compléter.

## État après formatage et SAST

Le formatage, `detektCheck` et `ktlintCheck` passent sur l'ensemble Kotlin
(`final-style-green.log`, sortie0, 28s), sans suppression de contrôle. Les
corrections concernent constantes, clés JSON, conditions nommées et organisation
des déclarations. Le forwarder `CaptureSession.onForeground` est une extension
interne, comme les helpers existants, après arbitrage de l'orchestrateur.

Le build final exécute 51 tests ciblés, aucun échec ou erreur. Il reconstruit
les deux APK publics x86_64 après formatage ; le test natif est ensuite
réexécuté dans un nouveau processus de l'émulateur. Résultat : `OK (1 test)`,
trois cas, 4,717s. Les erreurs de boîte / repères / RGB restent nulles ; cosinus
0,9999999999917017, delta prototype8,2983×10⁻¹², décisions identiques. Initialisation
et autocontrôle4 290ms ; delta PSS instantané461 141Kio.

Les artefacts finaux sont dans `runtime-coder/emu-final` : APK, classes, données
JVM et native, XML fusionné, empreintes et vérification du packaging (x86_64,
aucun bootstrap personnel). Le rapport fusionné final ne signale aucun
identifiant de classe incompatible. Runtime : **467/492 lignes94,92% et
289/355 branches81,41%**, sans exclusion métier.

Le même rapport ciblé indique pour `FacePhotoAnalysis.kt` 96/96 lignes mais
46/60 branches76,67% : le complément de validation de ce contrôleur est remonté
à l'orchestrateur. Les contrôles globaux, la revue indépendante et la validation
ARM64 ne sont pas déclarés terminés par ce checkpoint.

## Correction retenue en revue : cardinalité et garde de chargement

Le nombre de visages incompatible était déjà refusé, mais l'exception générique
n'était pas mémorisée comme échec de parité. La régression exécutée avant
correction (`red-cardinality.log` et XML conservé) montre deux chargements pour
deux tentatives, au lieu d'un. Le test utilise le moteur et le garde de production
avec une ouverture native simulée ; il vérifie aussi contrôle1 et fermeture1.

Le refus de cardinalité est désormais un `FaceParityFailure`. Le garde englobe
l'ouverture du noyau et ferme une fois tout noyau rejeté. Une seconde tentative
ne recharge ni ne contrôle à nouveau. Les trois incompatibilités aucun / unique /
multiple sont couvertes, sans changement des tolérances, modèles ou décisions.
Un échec avant encodage ne publie aucune métrique numérique fictive.

Après cette correction, `green-cardinality-final-build.log` passe : **54 tests
JVM**, dont les neuf tests du contrôleur UI, `detektCheck`, `ktlintCheck` et les
deux APK publics x86_64. La nouvelle instrumentation passe (`OK (1 test)`, trois
cas, 5,752s). Parité : boîtes / repères / MAE0, cosinus0,9999999999917017, delta
prototype8,2983×10⁻¹² et décisions identiques. Autocontrôle à froid5 160ms ; delta
PSS instantané461 977Kio sur émulateur uniquement.

Archives finales de cette correction : `runtime-coder/emu-reviewed`, incluant
APK, classes, couvertures JVM/native, empreintes des sources et packaging
vérifié sans galerie personnelle. Fusion fraîche sans avertissement mismatch :

| Composant | Lignes | Branches |
| --- | --- | --- |
| Runtime facial | 475/497 (95,57%) | 288/343 (83,97%) |
| Contrôleur FacePhotoAnalysis | 96/96 (100%) | 51/60 (85%) |
| Reconnaissance FaceRecognition | 46/46 (100%) | 52/64 (81,25%) |
| Overlay facial | 54/54 (100%) | 20/21 (95,24%) |

La limite de couverture du contrôleur précédemment signalée est donc levée.
Les contrôles globaux et le verdict indépendant restent à la coordination de
l'orchestrateur ; les budgets du téléphone ne sont pas déclarés mesurés.

Aucune installation de test APK, manipulation ni lancement automatique n'a été
réalisé sur le téléphone personnel. L'automatisation native concerne uniquement
l'émulateur PC explicitement ciblé.
