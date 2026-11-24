# Diagnostic Android YOLOE — préparation du 27 octobre 2026

**Revue ciblée et sécurité conformes ; tester indépendant conforme sur PC.**
APK personnel 0.5.1 / code 9 **installé le 27 octobre 2026**, par mise à jour du seul
APK principal depuis 0.5.0/code 8. Lancement manuel et une comparaison complète confirmés ; mesures CPU/PSS analysées ci-dessous. Aucune donnée privée, publication/compte, modification
système ou clé privée manipulée dans cet incrément. Le [spike PC](m1-yoloe.md)
a passé revue et tester indépendants ; aucune performance ARM n’en est déduite.

## Périmètre et décisions

Une Activity interne (`exported=false`, `fullUser`), ouverte par l’action debug
« Comparer les modèles », démarre au repos. Démarrer/Annuler/Retour sont explicites ;
pause ou recréation annulent sans relance. Le modèle YOLO11n et les paramètres du
parcours caméra/image restent inchangés. Le même verrou process protège toutes les
inférences : une ancienne analyse annulée garde ses ressources jusqu’au retour natif.
Les résultats tardifs ne deviennent jamais un rapport complet. Le diagnostic n’utilise
ni caméra ni photos sélectionnées. Aucun nouveau framework/runtime ; seule une contrainte de sécurité Guava a été ajoutée après le scan frais décrit plus bas.

Les assets supplémentaires appartiennent au sourceSet debug uniquement, liste fermée
et contrôlée en taille/SHA à chaque préparation : deux ONNX compacts, NASA, motif local,
licence AGPL et notices/catalogue. Pas de `.pt`, encodeur Apple ou images COCO/ADE.
Modèles/fixtures hors Git, copies générées sous build, [recette](../../experiments/m1-yoloe/README.md).
L’échec d’un contrôle de présence/hash bloque la fabrication et le chargement natif.

| Artefact | Octets | SHA-256 |
| --- | ---: | --- |
| Baseline compacte de mesure | 11 746 701 | 5e11ab61781495f96661a0409a3a868fd0bfe213f04f3e83e1b61fe4d0bfcc92 |
| YOLOE PF compact | 15 644 237 | 289e096cef23c0c3bec3a46ead571059702414109aa5319d679891e879d3ee91 |
| NASA astronaut | 791 555 | 88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5 |
| Motif synthétique local | 31 587 | c98056be2497324d795c3b0397b14d68ef8c33dd1ee4ef35d3f1ca33d8c17ee0 |

Les deux exports de mesure ont le même contrat FP32 CPU, batch 1, 640², opset 17,
simplify=False, nms=True, end2end=False, conf 0,35 / IoU 0,5 / max 20 / agnostic=False.
Ce choix class-aware sert la comparaison contrôlée ; il ne remplace pas l’essai PF
agnostique de l’étude PC. Pas de pré-top300 dans les graphes compacts. Aucun score
calibré ni supériorité générale de détection prétendue.

## Protocole et portée des nombres

Deux entrées RGB/letterbox préparées une fois avant les chronos modèles. Ordre fixe
baseline puis PF, une seule session à la fois : CPU 2 threads intra / 1 inter, séquentiel,
ALL_OPT. Par fixture : premier appel séparé, 3 échauffements, 10 répétitions mesurées,
soit **56 runs** au total. Le premier appel de la deuxième fixture est celui d’une
session déjà chaude ; seul fixture1 suit sa création. Tenseur créé hors chrono.

- Lecture + SHA : une fois par modèle, avant session ; création session séparée.
- `runNanos` : `session.run`, inférence + NMS ; pas préparation, copie Java, reconstruction
  des masques/contours ni UI. `copyNanos` couvre les copies des deux FloatBuffer en Java.
- Chaque résultat est ensuite validé hors chrono : formes `[1,20,38]` et `[1,32,160,160]`,
  nombres finis, padding zéro, scores/boîtes/classes 80 ou 4 585 valides. Aucun rendu de contours.
- Result/tensor/session/options fermés sur succès/erreur/annulation ; options vivent jusqu’à
  fermeture session. L’environnement ORT global reste ouvert. Aucune interruption native forcée.
- PSS propre processus, hors chrono : avant/après chaque bloc avec session ouverte
  (après fermeture du tensor), puis avant modèle/après fermeture session, avec log `phase=modelEnd` exposant les mêmes échantillons
  `pssBeforeModelKiB` / `pssAfterSessionCloseKiB` (aucune seconde lecture). Valeur non positive
  = non mesurée. Échantillons du processus partagé, ni pic ni attribution au seul modèle.
- Statistiques N/médiane/min/max, aucun p95 fragile imposé à N=10. Ordre/cache/thermique
  non contrôlés ; aucune extrapolation FPS du parcours normal. Logs numériques tag
  `BlameraBenchmark`, identifiant de run, paramètres/hashes et fin explicite ; pas d’URI/pixels.

[SessionOptions et durée de vie](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtSession.SessionOptions.html),
[FloatBuffer de copie](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OnnxTensor.html),
[PSS propre processus](https://developer.android.com/reference/android/os/Debug#getPss()),
[cycle Activity](https://developer.android.com/guide/components/activities/activity-lifecycle)
et [Activity non exportée](https://developer.android.com/guide/topics/manifest/activity-element#exported)
consultés le 27 octobre. Ces API existent dans ORT 1.31 / SDK 36 effectivement utilisés.

## Preuves de développement

Préparation exploratoire distincte du code livré : export compact baseline en dossier
séparé, sockets bloquées, parité NASA une détection/IoU masque 1, négatif zéro/padding nul.
Preuve `build/reports/validation/m1-yoloe/android-preparation.json`, commande sortie 0.
Baseline production ONNX SHA 383257…321b et checkpoint SHA 55ed65…152 préservés.

| Phase réelle | Commande / preuve locale | Résultat |
| --- | --- | --- |
| Red protocole/statistiques | filtre BenchmarkProtocolTest ; yoloe-android-protocol-red.log | sortie 1, 2 assertions fonctionnelles |
| Green protocole | même filtre ; yoloe-android-protocol-green.log | sortie 0 |
| Red frontières | Concurrency + CompactValidation ; yoloe-android-boundaries-red.log | sortie 1, 3 assertions fonctionnelles |
| Red backend natif | OrtBenchmarkBackendTest ; yoloe-android-native-red.log | sortie 1, backend absent ; premier essai compile ImageIO non compté Red |
| Green natif | benchmark.* ; yoloe-android-native-green.log | sortie 0, 7 tests, vrais modèles NASA/négatif |
| Red UI/contrôleur | Controller + action Main ; yoloe-android-ui-red.log | sortie 1, 3 assertions fonctionnelles |
| Refactor + suite Android | yoloe-android-coverage.log | 116 tests conformes, rapport couverture ; tâche format encore rouge sur imports/nom de test corrigés ensuite |
| Format final | yoloe-android-format.log | sortie 0 |
| Première qualité avant revue | `scripts/pc.ps1 Quality` ; yoloe-android-quality.log | sortie 0, 12 étapes ; 118 Android / 56 scripts / 4 PC |
| Correction PSS | model-end-red / model-end-green.log | Red sortie 1 puis Green sortie 0, 4 tests protocole |
| Globale après PSS | model-end-quality.log | 12 étapes conformes, gate final bloqué par avis Guava actif |
| Finale après Guava | yoloe-android-guava-quality.log | sortie 0, 12 étapes ; 119 Android / 56 scripts / 4 PC |

Journaux Gradle dans `.gradle-user-home/`. Un essai intermédiaire UI a aussi identifié
un test double-start qui bloquait lui-même le thread (remplacé par attente suspendue)
et un sélecteur Compose ambigu (deux lignes de statistiques identiques), corrigés
sans modifier le protocole. Tests couvrent annulation/attente native, fermeture ressources,
absence d’autodémarrage, pause/recréation, retry, formes/classes/padding et erreurs.
Les durées des tests backend utilisent une horloge injectée : preuve des frontières
chronométrées, **aucune mesure de vitesse téléphone**.

## Correction retenue en revue — 27 octobre

La revue source/APK n’a retenu qu’un point : les échantillons PSS avant modèle et
après fermeture session étaient collectés mais absents des logs et de l’UI. Correction
bornée : une ligne de fin par modèle, `phase=modelEnd`, après `session.close`, réutilise
les deux valeurs de `BenchmarkModelTiming`. Aucun nouveau relevé, appel natif ou changement
du protocole. Le PSS de bloc reste mesuré session ouverte ; le log modèle identifie
explicitement la phase après fermeture. `null` reste non mesuré, jamais zéro artificiel.

Red réellement exécuté : filtre
`BenchmarkProtocolTest.modelEndReportsExistingSamplesAfterSessionClose`, sortie 1,
une assertion fonctionnelle (aucun log au lieu de deux), preuve
`.gradle-user-home/yoloe-android-model-end-red.log`. Le test vérifie également l’ordre
après chaque fermeture/avant le modèle suivant, les phases, les valeurs et l’absence
de lecture PSS supplémentaire. Green exécuté : `BenchmarkProtocolTest`, les 4 tests passent ; format et detekt conformes,
sortie 0, `.gradle-user-home/yoloe-android-model-end-green.log`.
La recette B03 autorise un toucher sur une zone vide pour éviter la veille, sans
quitter/tourner l’appareil ou changer un réglage OS. Aucun wakelock ajouté.

## Alerte transitive détectée lors du contrôle frais

La passe `yoloe-android-model-end-quality.log` a exécuté avec succès les 12 étapes,
mais le gate final a bloqué sur l’avis actif GHSA-xxph-c9ww-hj94 / CVE-2026-102554,
pour Guava 33.3.1-android transitif. Ce constat de scan ne démontre aucun chemin
`ObjectInputStream` exposé dans Blamera et n’est pas une faille observée sur le téléphone.
La [publication officielle 33.7.2](https://github.com/google/guava/releases/tag/v33.7.2)
et l’[avis éditeur](https://github.com/google/guava/security/advisories/GHSA-xxph-c9ww-hj94)
identifient le correctif d’allocation lors de désérialisation. Les métadonnées d’avis
ont évolué pendant la séance ; les preuves du scan sont conservées sans fusionner
leurs dates/scores avec ceux du texte historique.

L’orchestrateur a autorisé une contrainte Gradle ciblée **33.7.2-android**, version
cataloguée avec motif CVE ; aucun changement CameraX/ORT/modèle, aucun ignore ni
abaissement de gate. Exception TDD configuration : validation par résolution effective,
compilation et régressions existantes, sans test miroir de version ni PoC d’OOM.
`dependencyInsight` runtime debug et tests : sortie 0, variante `androidRuntimeElements`,
`org.gradle.jvm.environment=android` ; pas de forçage universel JRE. Preuves
`yoloe-android-guava-runtime.log` et `yoloe-android-guava-tests.log`.
Contrôle des artefacts réels via tâche temporaire de validation (aucun build release) :
`verifyGuavaVariants --init-script=build/reports/validation/m1-yoloe/guava-variant.init.gradle`,
sortie 0, preuve `guava-variants.json` et `yoloe-android-guava-variants.log`.
Debug, release et tests sélectionnent `androidRuntimeElements`, JAR `guava-33.7.2-android.jar`,
SHA `b949fe975a32421c1a2633c07a4f1578b462f2f467668aa0a4023c5ff7c4a808`,
et failureaccess 1.0.3. Le graphe runtime perd jsr305/checker-qual et met à jour les
annotations transitives error_prone/j2objc/jspecify ; inventaire et licences frais contrôlés.
Preuves avant correction : `osv-guava-before-update.json`, `quality-before-guava-update.json`
dans `build/reports/validation/m1-yoloe/`. La finale après mise à jour est conforme,
scan OSV frais complet sans vulnérabilité active. Aucun avis ignoré ou gate modifié.

## Validation indépendante et état de l’installation — 27 octobre 2026

Revue ciblée de la correction PSS et de la mise à jour de sécurité : **conforme**.
Tester final indépendant : **conforme sur PC**, 119 tests Android réellement réexécutés,
sortie 0 (94 s), contrôle Guava des trois classpaths sortie 0 (20 s), puis qualité commune
sortie 0 / 12 étapes, 56 tests scripts et 4 PC. Métriques, artefacts, licences et scan frais
confirmés. Preuve locale : `build/reports/validation/m1-yoloe/android-tester-results.json`.
L’orchestrateur a revérifié hash APK, modèles du ZIP, identité et signature avant
la procédure de mise à jour.

La première procédure s’était arrêtée **avant `adb install`** : inventaire de connexion
à zéro appareil autorisé, zéro offline, zéro unauthorized. Aucune installation ni
autre action téléphone n’avait alors été exécutée ; une reconnexion avait été demandée.

Après reconnexion le même 27 octobre, l’orchestrateur a identifié exactement un appareil
autorisé et revérifié APK SHA
`fb660502b8ed799de399852704af2e19d3e370f8464687788d7f22bb56fee4a4`,
103 753 509 octets, identité `dev.blamera` / 0.5.1 / code 9 et certificat attendu
`eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555`.
Il a ensuite mis à jour **uniquement l’APK principal** : `adb install -r`, sortie 0,
`Performing Streamed Install` puis `Success`. Preuve locale sans identifiant téléphone :
`build/reports/validation/m1-yoloe/android-installation.json`.
Aucun lancement automatique, réglage, lecture de logs ou autre accès téléphone.

L’installation 0.5.1 est confirmée. L’utilisateur a ensuite répondu « Comparaison terminée »,
puis « Nouvelle comparaison terminée » après une relance manuelle. La mesure diagnostique
CPU/PSS est disponible ; aucun résultat d’annulation/rotation/retour ni essai prolongé n’en est déduit.

## Mesure Xiaomi contrôlée dans les logs — 27 octobre 2026

L’orchestrateur a lu uniquement le PID Blamera et le tag `BlameraBenchmark`. La première
lecture ne conservait que 8 lignes du run `98774978-e078-48b2-b83a-81f5dfd14796` :
fin complète présente, baseline absente ; ce fragment est exclu de la comparaison.
Après relance manuelle, capture live stable de **50 lignes** pour le seul run
`cb76977d-3b58-4638-9325-1876127f3da8`, terminé `end status=complete`.
L’arrêt Ctrl-C du lecteur **sur PC**, sortie 1 après cette fin, est volontaire ; ce n’est
pas un échec du banc. Aucun tampon effacé, autre log/donnée personnelle, capture ou
pilotage UI. Revue et contrôle indépendants des calculs conformes ; preuve du tester
`tester-device-results.json`. Preuves sous `build/reports/validation/m1-yoloe/device/` :
`benchmark-live.log`, `capture-metadata.json`, `benchmark-analysis.json` et `fixture-reference.json`.
SHA du journal complet : `d683d39e210d9d7ae3b7a747bdfb6e0d12af75a88c8c7ad01507b31a8fb0a734`.

Contrôles directs conformes : un start et un end, 2 modèles/hashes/classes exacts,
4 blocs/hashes de fixtures exacts, **40 mesures numérotées 1–10**, 2 fins de modèle dans
l’ordre attendu. Médianes/min/max des durées et médianes des copies recalculées depuis
les entiers nanosecondes, sans écart. Les 12 échauffements ne sont pas logués séparément :
56 appels au total découle du protocole testé, pas de 56 lignes observées.

| Modèle / fixture | N | Durée écoulée `session.run` médiane [min–max], ms | Copie Java médiane, ms | PSS après bloc, MiB |
| --- | ---: | --- | ---: | ---: |
| Baseline / NASA | 10 | 260,291 [258,896–262,001] | 3,048 | 294,10 |
| Baseline / motif | 10 | 260,458 [259,098–261,193] | 3,087 | 309,65 |
| YOLOE / NASA | 10 | 539,717 [534,727–542,379] | 3,653 | 1 619,58 |
| YOLOE / motif | 10 | 539,328 [533,887–542,689] | 3,617 | 1 636,49 |

| Modèle | Lecture + SHA, ms | Création session, ms | Premier appel NASA, ms | Premier appel motif, ms |
| --- | ---: | ---: | ---: | ---: |
| Baseline | 104,512 | 118,042 | 274,144 | 260,073 |
| YOLOE | 143,407 | 135,973 | 821,054 | 534,883 |

Durée écoulée sur backend CPU, **NMS compris**, pas consommation CPU ni FPS.
Les médianes YOLOE/baseline valent 2,074 sur NASA et 2,071 sur motif : environ **2,07×**
pour ces graphes compacts et ce run. Ce n’est pas une mesure du pipeline normal,
qui conserve un autre export sans NMS intégré ; copie, masques/contours et UI sont exclus.
Un seul run complet, ordre baseline puis PF fixe, cache/thermique non contrôlés ;
aucun GPU/NPU testé ni conclusion de chauffe/stabilité prolongée.

Le plus grand échantillon PSS **enregistré dans ce run complet** est 1 686 711 KiB = 1 647,18 MiB, avant le
bloc motif PF, session ouverte. Après fermeture des sessions : 217 277 KiB pour baseline
et 172 679 KiB pour PF. Ce sont des relevés du **processus entier**, jamais un pic ou
l’allocation attribuée au seul modèle ; les phases sont distinctes des relevés après bloc.

### Motif coloré distinct du négatif PC

L’ancien négatif est RGB114 uniforme, SHA `1cb06d…692`, une couleur. La fixture Android
`synthetic` SHA `c98056…7ee0` comporte carrés/cercle colorés, 47 couleurs : ce n’est pas le
même négatif. `prepare_android.py` vérifiait d’abord l’ancien négatif puis générait le motif ;
le test JVM négatif utilise lui aussi un tenseur uniforme 114/255.
Une vérification CPU ORT 1.31 bornée, sans export ni mesure de vitesse supplémentaire,
a appliqué aux deux PNG 640×480 le prétraitement équivalent Kotlin : RGB normalisé,
padding 80 haut/bas, aucun redimensionnement. Avec les mêmes compacts/hashes :

| Fixture | Baseline PC | YOLOE PC | Compteurs du run Xiaomi |
| --- | ---: | ---: | --- |
| Négatif uniforme historique | 0 | 0 | Non embarqué dans ce banc |
| Motif coloré Android | 0 | 20 | 0 / 20 à chacune des 10 mesures |

L’écart apparent n’établit donc aucune divergence ARM. PF propose notamment hot air
balloon / ember / band aid sur ce motif ; ce contrôle n’établit ni justesse sémantique
ni « 20 faux positifs » avec une vérité terrain absente. Il ne prouve pas non plus une
parité numérique complète des sorties ARM. NASA donne 1 / 20 dans ce banc, sans qu’un
compteur de sorties mesure le rappel ou une amélioration de qualité.

**La mesure des coûts CPU/PSS est terminée ; le choix du modèle reste ouvert.**
Le temps de réponse supérieur ne suffit pas à écarter YOLOE : une meilleure détection,
de nouvelles catégories utiles ou de meilleurs contours pourraient le justifier.
YOLO11n reste provisoirement actif pour éviter un remplacement non validé, sans
prétendre à sa supériorité. Le bénéfice qualité/vocabulaire est insuffisamment évalué
(corpus de 16 images limité, projection vers 80 excluant des termes fins/races, deux ADE non représentatifs).
Prochain critère discriminant : qualité annotée ciblée, rappel/précision par catégorie,
nouveaux objets corrects vs synonymes/abstractions, contours appariés/objets manqués,
inconnus et scènes riches ; puis coûts selon direct caméra ou image ponctuelle.
Aucun poids ni budget utilisateur inventé, aucune nouvelle évaluation exécutée ici. Annulation, pause/rotation et retour Caméra/Image
de cette recette ne sont pas déclarés validés sur téléphone. M1/D-M1-02 restent ouverts.

## Livraison et limites restantes

Qualité finale après correction : mobile 93,20 % lignes / 86,50 % branches ; nouveau code
94,20 % / 85,23 % sur 414 lignes exécutables (base HEAD d2e5ca3, fichiers non suivis inclus).
Duplication nouvelle 0/670 lignes ; globale 0,21 %. Complexités 10/15 respectées.
Scripts Python 94,44/86,36 ; composant PC 100/100, aucun nouveau code dans ces composants.
Lint : 0 erreur, 18 avertissements, dont avertissement générique `DiscouragedApi`
pour le `fullUser` de l’Activity interne ; aucune désactivation ajoutée. Scans frais
Maven 97 / PyPI 35, aucune vulnérabilité active ; licences runtime 131 / 35 conformes.
Rapports : `build/reports/quality/summary.json`, XML/HTML JaCoCo/lint et inventaires.

APK : `dist/blamera-0.5.1-debug.apk`, **103 753 509 octets**, SHA-256
`fb660502b8ed799de399852704af2e19d3e370f8464687788d7f22bb56fee4a4`.
AAPT : package `dev.blamera`, 0.5.1/code 9, SDK min/target/compile 36, ARM64 seulement.
Signature v2 valide, certificat SHA-256
`eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555`.
Permissions exactes : CAMERA et permission interne AndroidX, aucun réseau/media/micro.
ZIP contrôlé : 7 fichiers diagnostic (2 ONNX, 2 PNG, licence, notice, catalogue),
aucun `.pt`/COCO/ADE/encodeur. Hash du modèle normal 383257…321b et checkpoint 55ed65…152
inchangés. Preuves `android-package.json`, `android-apk-badging.txt`,
`android-apk-signature.txt` dans `build/reports/validation/m1-yoloe/` ; toutes sorties 0.
Un contrôle ponctuel avait attendu 8 fichiers au lieu des 7 prévus : assertion corrigée,
aucun asset applicatif modifié. Les lignes de logs sont séparées par modèle/fixture/run,
sans JSON global volumineux ; formats bornés bien sous 4 KiB par ligne.

Ancien APK 0.5.0 conservé ; certificat personnel inchangé. Le candidat initial avant
correction est aussi conservé sous `dist/blamera-0.5.1-before-model-end-debug.apk`,
SHA `d3d05e562ddb068f3e18a5b9383731eef3ec535daabd2b1c01cadee582213290`.
Les contrôles de documentation UTF-8/liens/32 statuts et `git diff --check` sont exécutés
sur l’état final ; aucun build supplémentaire après certification. Les preuves Windows
Java amd64 CPU ne prouvent ni consommation/mémoire ARM ni avantage YOLOE sur Xiaomi.
Revue et tester PC obtenus, installation principale réussie ;
[recette manuelle](../tests/m1-yoloe-comparaison.md) : lancement/fin et run diagnostique
CPU/PSS confirmés, autres sous-critères non présumés. Aucun nouveau build, test global,
scan ou export pour cette analyse des preuves. M1/budgets/prolongé et
D-M1-02 restent ouverts ; aucune substitution du modèle par défaut autorisée ici.
