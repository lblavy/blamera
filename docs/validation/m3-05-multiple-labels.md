# M3-05 — plusieurs labels de personnes sur une photo

2 novembre 2026. Version installée 0.18.0/code32, après acceptation du parcours
connu/inconnu en 0.17.0. [Contrat](../specs/face-multiple-recognition.md) et
[recette utilisateur](../tests/m3-multiple-face-labels-recette.md).

## Comportement livré dans le code

Une détection par photo, puis encodage séquentiel de chaque visage et comparaison
indépendante avec la galerie existante. Le résultat associe toujours géométrie et
identité. Des noms répétés sont possibles, sans forcer une identité unique par
photo ; un inconnu ou une abstention ne supprime pas les autres résultats.

Les labels seuls sont ancrés près des visages, sans rectangle facial, y compris
pour un visage unique. Le placement cherche à éviter les collisions simples et
respecte le cadrage visible. Zoom, déplacement, rotation, analyse de zone et retour
à l'original gardent leurs transformations communes. La photo n'est pas annotée
dans son stockage ; les noms ne sont pas ajoutés aux demandes externes.

Les encodages restent sérialisés. Les changements de photo, galerie ou cycle de
vie invalident les anciens lots. Une erreur locale indique un visage non analysé ;
une défaillance globale ne publie aucun lot partiel. Au-delà de la borne existante
de vingt candidats, le lot est refusé explicitement plutôt que tronqué.

Modèles, prétraitement, galerie v1 et seuils 0,30/0,50/0,05 inchangés. Le manifeste
de parité publique passe seul en v2 pour porter les attentes de chaque visage du
cas multiple. Sept fichiers générés sous `build/fixtures/m3-05`, SHA du manifeste
`9845caade1c57d284ce8f39859c0d2ebd110832088428d18f4a066631befbaae`.
Le portrait public est répété : cette référence vérifie la chaîne numérique,
sans démontrer la précision sur plusieurs identités réelles.

## TDD, revue et corrections retenues

Preuves sous `build/reports/validation/m3-05/` :

- PC : Red fonctionnels puis 15 tests générateur et 16 préparateur, revue conforme.
  Le testeur a ensuite trouvé qu'un manifeste à vingt attentes valides dépassait
  la limite héritée de la galerie. Correction avec Red réel puis 18 tests verts :
  limite propre au manifeste de 1 Mio, galerie personnelle toujours à 120 Kio.
  Revue du correctif et scénario indépendant conformes, dont vingt visages
  acceptés, vingt et un refusés, publication générique et rollback préservés.
- Android : Red exécutés pour les lots, l'association boîte/résultat, les erreurs
  individuelles, l'annulation, les snapshots, le contrôleur, la parité multiple,
  les labels sans cadres et leurs collisions, le compte de visages des diagnostics
  et la limite de manifeste. Les anciennes assertions de cadre et de refus de
  plusieurs visages sont remplacées par le nouveau comportement demandé.
- La revue a établi un unique blocage Android : OpenCV remonte le manque de
  mémoire natif en `CvException`, une `RuntimeException`, qui aurait été traitée
  comme une erreur locale. Red réel : erreur au deuxième visage et lot partiel
  observé. Correction conservatrice : les exceptions natives OpenCV deviennent
  globales avant le repli local, sans analyser ou journaliser leur message.
  Régression : aucun troisième encodage ni publication partielle, fermeture unique.

Le comportement mémoire s'appuie sur les sources officielles OpenCV 4.13 :
[allocation native](https://raw.githubusercontent.com/opencv/opencv/4.13.0/modules/core/src/alloc.cpp),
[conversion JNI](https://raw.githubusercontent.com/opencv/opencv/4.13.0/modules/java/generator/src/cpp/Mat.cpp)
et [classe CvException](https://docs.opencv.org/4.13.0/javadoc/org/opencv/core/CvException.html).
Les autres erreurs locales et les annulations gardent leurs tests.

Après correction : **84 tests Android ciblés verts**, detekt et ktlint conformes.
La revue du code corrigé ne retient plus de blocage. Une nouvelle qualification
native postérieure au correctif passe : `OK (1 test)`, trois cas en 5,962 s.
Boîtes : écart maximal 0 px ; repères 2,44×10⁻⁵ px ; MAE RGB 4,78×10⁻⁴ sur 0..255 ;
cosinus minimal 0,999999681444 ; delta prototype maximal 3,19×10⁻⁷ ; décisions
identiques. Toutes les tolérances restent celles fixées avant mesure.

Archives natives définitives : `runtime-coder/emu-final/`, dont EC au SHA-256
`0468ddf1bbc0dbdddf63924227cfc742592bc0e21a18775e59759f4c1510a2b3`,
classes, données JVM et rapport fusionné sans avertissement de classes incompatibles.
Runtime 515/537 lignes (95,90 %) et 316/378 branches (83,60 %) ; contrôleur
104/104 lignes (100 %) et 53/62 branches (85,48 %) ; overlay 73/74 lignes (98,65 %)
et 28/32 branches (87,50 %). La revue a vérifié ces preuves et les 330 empreintes
Android stables avant le correctif IO.

Sur cet émulateur x86_64, l'initialisation/autocontrôle du moteur prend 4333 ms et
l'analyse chaude de deux visages 543 ms. Le delta PSS instantané est 458528 Kio.
Ces instantanés ne sont ni un pic mémoire ni une mesure sur Xiaomi ; ils n'incluent
pas tous les traitements du parcours photo. L'émulateur PC est arrêté après
vérification de ses processus, sans appel au téléphone.

## Contrôles intégrés et incident de test

La première globale a exécuté 602 tests Android : 601 passent, un échec de persistance
dans `TrialRefreshCommitCancellationTest` ; tous les autres outils exécutés passent.
Les rapports et données sont conservés sous `global-quality/failed-first/`.
Le rapport de couverture Android n'a pas été produit, la tâche dépendant des tests
en échec ; cette première globale est donc en échec, pas une qualification conforme.

Les 88 fichiers ChatGPT (production et tests) sont identiques à ceux de la globale
0.17.0 réussie. L'examen indépendant de la trace ne démontre pas un problème de
fermeture/rotation ou un délai : échec en 0,086 s et cause I/O/cryptographique
assainie avant le message final. L'hypothèse d'un remplacement atomique Windows
transitoirement refusé reste **non confirmée**.

La classe isolée inchangée passe ensuite : 4 tests, 0 échec, sortie 0 ; aucun code,
délai, assertion ou contrôle modifié. Sur arbitrage de revue, une seule nouvelle
globale est lancée. Elle échoue aussi : 600/602 tests réussis, cette fois avec
`AccessDeniedException` sur `Files.move(last.json.pending, last.json)` lors du
remplacement du rapport de benchmark. Deuxième essai conservé dans
`global-quality/failed-second/` ; les 389 empreintes de source sont identiques.
Le second échec est un test natif ONNX sur un chemin de cache de 262 caractères,
identifié séparément après examen des XML ; les comptages intermédiaires du log
ne constituaient pas le résultat final.
Les relances à code inchangé sont alors arrêtées.

L'examen établit l'opération en échec mais pas le propriétaire d'un éventuel verrou
Windows. Lectures et écritures sont fermées par `use`, les accès sont sérialisés,
le test est synchrone ; aucune fuite de handle propre n'est démontrée. Cette
seconde trace ne permet pas d'attribuer rétroactivement une cause au premier échec.

Le correctif défensif [M3-05-QA-01](../specs/atomic-file-replacement.md), retenu en
revue, partage les remplacements atomiques de session et benchmark : trois essais
maximum et deux pauses de 25 ms après refus d'accès seulement. Même fichier temporaire
fermé, mêmes options atomiques, aucun effacement du document valide ni repli non
atomique. Refus permanent, autres erreurs et interruption restent des échecs explicites.

Red réel : 12 tests, 8 échecs fonctionnels, puis Green 34 tests ciblés, format et analyse
statique conformes. Helper 30/30 lignes et 4/4 branches à 100 % ; benchmark 100 %/93,33 %.
Les anciens tests et délais sont inchangés. Revue des six fichiers conforme,
sources gelées ; preuve que les 44 classes compilées du runtime facial sont toujours
identiques octet par octet aux classes qualifiées par l'émulateur. La donnée native
reste donc applicable. Rapports sous `atomic-replace-coder/`.

Validation indépendante du correctif de fichier : 38 tests réussis, aucun défaut
supplémentaire établi. Un contrôle des seuls cas ciblés laisse le fichier
`SessionFile.kt` à 39/40 lignes et 11/14 branches (78,57 %), **exactement comme la globale
0.17.0 archivale**, sans nouvelle branche manquée. Le composant cohérent des trois
fichiers d'écriture atomique atteint 112/113 lignes (99,12 %) et 43/48 branches (89,58 %).
Le helper nouveau reste à 100 %. Les gardes individuelles de lecture/effacement
concurrent sont suivies en M3-05-COV-01, sans exclusion ni baisse de seuil.

La globale suivante après correctif exécute 614 tests : 613 passent, les tests de
stockage compris. Le seul échec restant est `PhotoAnalyzerNativeTest`, dont le
chemin de cache mesure 261 caractères ; archives `global-quality/failed-third/`.
Les cas similaires à 255/256 caractères réussissaient, ceux à 261/262 échouent avant
la première inférence. Un diagnostic temporaire sur les mêmes poids publics
compare les chemins courts et longs au niveau ONNX, sans modifier le modèle,
préremplir le cache ou affaiblir le test.

Ce diagnostic confirme une limite du runtime Windows ORT 1.31.0 avec le Temurin 21
configuré : mêmes poids publics et SHA, fichier présent selon Java, chargement
réussi à 70 et 259 caractères, `ORT_NO_SUCHFILE` à 260, 261, 262 et 263.
La première sonde avec le JBR a planté avant ce test et n'a pas été retenue comme
preuve de longueur. Le test canonique inchangé passe ensuite avec TEMP/TMP courts,
cache absent au départ, chemin de 242 caractères et assertions complètes sur les
deux fixtures (NASA : 17 détections, LVIS : 19 ; masques et labels exacts).
Rapports : `ort-path-probe/`. Aucun réglage Windows, modèle, assertion ou délai modifié.

## Qualification intégrée finale

La globale avec TEMP/TMP de processus courts `C:/dev/blamera/build/t/q` passe,
sortie **0**, contre `83d3645` (cumul des changements M3, pas seul delta M3-05).
**614 tests Android, 157 tests PC et 76 tests des outils qualité réussis**, sans
test Android ignoré. Les trois essais en échec restent archivés séparément.

| Périmètre | Lignes | Branches |
| --- | ---: | ---: |
| Android global | 97,26 % | 86,61 % |
| Android nouveau code, 1145 lignes exécutables | 96,77 % | 84,41 % |
| Runtime facial | 95,90 % | 83,60 % |
| Contrôleur facial | 100 % | 85,48 % |
| Rendu des labels | 98,65 % | 87,50 % |
| Écritures atomiques, trois fichiers | 99,12 % | 89,58 % |
| Nouveau helper atomique | 100 % | 100 % |
| PC global | 99,55 % | 97,81 % |
| PC nouveau code | 99,21 % | 97,12 % |

Duplication nouvelle : 0/1749 lignes Android et 0/986 lignes PC. Complexités 10/15
respectées. Audits : 101 paquets Maven/135 entrées de licence, 36 paquets PC/36
licences ; aucun avis actif retourné. Les scripts qualité n'ont pas de nouvelle
ligne ; couverture globale 94,47 %/88,52 %, nouvelle couverture non mesurée.

La revue vérifie les **393 empreintes stables**, les rapports, la donnée native
et l'absence d'avertissement de classes JaCoCo incompatibles. Archives complètes :
`global-quality/archive/`, avant toute exécution ciblée suivante.

La mesure individuelle d'`ExplorablePhoto.kt` varie de 31/40 à 30/40 branches
(77,50 % à 75 %), avec 85/91 lignes couvertes. Elle n'est pas déclarée identique
ou conforme seule. Le composant UI est à 818/980 branches (83,47 %) et le rendu
spécialisé à 87,50 % ; le suivi d'intégration est M3-05-COV-02. Aucun seuil abaissé
ni logique exclue pour ces suivis individuels.

## Validation indépendante, paquet et déploiement

Le testeur indépendant confirme **98 tests ML/UI réussis**, aucun échec, erreur ou
test ignoré, et les 393 sources inchangées. Le correctif IO a sa validation séparée
de 38 tests. Les rapports sont sous `mobile-tester/` et `atomic-replace-tester/`.
La qualification JNI est inspectée sans être rejouée inutilement ; aucun test
instrumenté sur le téléphone personnel.

APK personnelle locale : `build/outputs/blamera-personal-0.18.0.apk`,
683353164 octets, SHA-256
`ceac8086bb1a04b3dcbe84f2e507e7001b834b7a492f96e90b7106931f553add`.
Build sortie 0 en 45s, ARM64 seul, package `dev.blamera`, version 0.18.0/code32.
Signature vérifiée avec le JAR du SDK et le JBR du build : sortie 0, certificat
debug habituel `eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555`.

La galerie embarquée est identique à l'export personnel évalué : deux prototypes,
cinq références, politique figée, aucune photo personnelle. Les 68 payloads
communs au build générique sont identiques ; seul `assets/faces/bootstrap.json`
est ajouté. Sept poids/notices aux SHA approuvés, 393 sources et galerie PC
inchangées. Le testeur vérifie SHA, métadonnées, script/reçu et signature, sans
décoder le JSON personnel ni lire son export. Preuves : `personal-deployment/`
et `mobile-tester/personal-packaging-review.json`.

Après reconnexion confirmée par l'utilisateur, **installation réussie le
2 novembre 2026 à 15 h 16, heure de Paris** : appel explicite
`adb -s <serial-du-Xiaomi> install -r`, sortie 0 « Success ». Empreinte du paquet
revérifiée juste avant installation. Aucun lancement automatique, test APK,
transfert séparé de JSON ou manipulation du téléphone. Reçu
`personal-deployment/installation.json` ; galerie existante et données conservées.
Retour humain à la recette de groupe : « ça a l'air de fonctionner ». Le parcours
essentiel est accepté sur le Xiaomi. Cette réponse qualitative n'est pas une
mesure chronométrée et ne confirme pas individuellement tous les cas complémentaires.

Deux lectures des diagnostics propres à Blamera après l'essai, filtrées par PID
et tag `BlameraFaces`, n'ont trouvé aucun événement d'analyse disponible, y compris
en format raw explicite. Aucun log global ou contenu privé lu ; aucune latence,
valeur de parité numérique ou mémoire téléphone inférée de ces lectures vides.
Reçus sous `phone-validation/`. La raison de l'absence d'événements n'est pas établie.

## Limites de validation

Tests logiciel, parité numérique et précision statistique sont distincts. Aucune
calibration sur les quinze photos finales déjà exposées. Aucun transfert de données
personnelles, test APK ou automatisation du Xiaomi. La validation humaine de la
photo de groupe est acceptée pour le parcours essentiel. M3-06 a ensuite mesuré
la parité ARM64 et les temps moteur ; les compléments non nécessaires à l'usage
ont été clos comme limites acceptées, sur décision explicite pour le POC.
[Bilan terrain et clôture](m3-06-device-measurements.md).

Objectifs sur Xiaomi : trois visages chauds ≤15 s, premier lot avec chargement et
autocontrôle ≤30 s, pic mémoire supplémentaire ≤700 Mio. Ces budgets ne sont pas des
résultats mesurés ; les instantanés PSS des diagnostics ne mesurent pas le pic.
