# ADR 0001 — stack et frontières techniques

Date : 26 octobre 2026. Ticket : M0-01. Statut : décision retenue ; revue et validation documentaire effectuées.
Cette ADR choisit une direction ; elle ne prouve aucun build ou benchmark.

## Contraintes et décision mobile

Un seul appareil : Xiaomi 14T Pro, HyperOS 3.0.305.0, Android 16. Préparation sur Windows,
i7, 32 Go, sans GPU PC. APK personnel installé manuellement, aucun store ni backend initial.
Vision locale : caméra et image importée, contours dès M1 ; gel proposé en M2.
L'externe exige une action explicite ; service et budget IA M2 restent ouverts.

**Retenir Android natif, Kotlin, Jetpack Compose et CameraX**, avec Gradle Wrapper et
scripts Kotlin DSL. Choisir `minSdk = 36`, `targetSdk = 36` et `compileSdk = 36` :
le support d'appareils plus anciens n'est pas demandé. Ce choix réduit la matrice de tests
et exclut explicitement Android < 16 ; un nouveau téléphone entraînerait une réévaluation.
SDK/target 36 correspondent à Android 16. [Configuration officielle Android 16](https://developer.android.com/about/versions/16/setup-sdk).

CameraX fournit l'analyse d'images et une stratégie de conservation de la dernière image,
utile pour éviter une accumulation si l'inférence est lente. Ce mécanisme ne garantit
aucune cadence sur ce téléphone. [Analyse CameraX](https://developer.android.com/media/camera/camerax/analyze).
Compose propose des tests d'interface basés sur les comportements et la sémantique ;
les règles métier garderont des tests JVM séparés. [Tests Compose](https://developer.android.com/develop/ui/compose/testing).

## Alternatives examinées

Les appréciations de maintenance et de risque ci-dessous sont des choix de conception
pour Blamera ; aucune comparaison chiffrée de vitesse n'a été exécutée.

| Option | Caméra, segmentation et intégration | Tests et maintenance dans ce projet | Décision |
| --- | --- | --- | --- |
| Kotlin + Compose + CameraX | Accès direct aux contrats Android et aux runtimes JVM/Android ; maîtrise du cycle de vie et des coordonnées | Une stack mobile ; tests JVM + UI/appareil ; apprentissage Android à assumer | Retenue |
| Flutter | Intégration native possible via plugins, canaux de plateforme et code Kotlin ; intérêt multiplateforme | Dart et frontière native supplémentaires pour ce besoin ; tests de cette frontière à maintenir | Écartée pour le téléphone unique |
| React Native | Modules natifs possibles pour les capacités absentes des bibliothèques | TypeScript/JavaScript, codegen et code Android à coordonner ; aucun besoin web/iOS confirmé | Écartée pour ce périmètre |

Les mécanismes d'intégration sont documentés par [Flutter](https://docs.flutter.dev/platform-integration/platform-channels)
et [React Native](https://reactnative.dev/docs/turbo-native-modules-introduction). Ils rendent
ces alternatives possibles ; ils ne prouvent pas un déficit de performance intrinsèque.
Flutter est sous [BSD 3-Clause](https://github.com/flutter/flutter/blob/master/LICENSE),
React Native sous [MIT](https://github.com/react/react-native/blob/main/LICENSE) : le coût de
licence n'est pas le motif de leur exclusion, les plugins restant à auditer séparément.

## Compatibilité : repères M0-01 et bootstrap M0-02

Ne pas recopier les versions « latest » des exemples : des bibliothèques récentes peuvent
exiger un compile SDK ou un AGP plus élevé. Le bootstrap choisira des versions stables
compatibles avec SDK 36 et les outils qualité, puis les figera dans le catalogue de versions,
le Wrapper et la documentation. Ces repères datent de M0-01 ; le couple depuis compilé
en M0-02 est détaillé dans l'[environnement](../development-environment.md) et son
[rapport de validation](../validation/m0-02.md). Les plugins qualité restent à éprouver en M0-03.

| Élément | Repère vérifié, sans figer la future version |
| --- | --- |
| AGP / SDK | AGP 8.13 prend en charge jusqu'à API 36.1 ; sa table prescrit Gradle 8.13 et JDK minimum 17 |
| Kotlin / AGP | La même note indique la prise en charge de Kotlin 2.3 par AGP 8.13.2 ; cela ne valide pas tous les plugins tiers |
| JDK de build | Réutiliser le JBR 21.0.9 installé si le couple final le permet ; Gradle sait s'exécuter sur Java 21 depuis 8.5 |
| Compose | Plugin compilateur de même version que Kotlin ; BOM/bibliothèques et leurs exigences AAR à vérifier séparément |
| CameraX | Choisir une version stable compatible avec le SDK et le reste du catalogue, puis vérifier sur l'appareil |

Sources : [table AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes),
[compatibilité Java/Gradle](https://docs.gradle.org/current/userguide/compatibility.html),
[compilateur Compose/Kotlin](https://kotlinlang.org/docs/compose-compiler-migration-guide.html),
[configuration Compose](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
et [versions CameraX](https://developer.android.com/jetpack/androidx/releases/camera).
Fixer explicitement le JDK d'exécution de Gradle et aligner entre elles les cibles Java
et Kotlin, sans imposer leur égalité avec la version de ce JDK, en M0-02.
[JDK et build Android](https://developer.android.com/build/jdks).

## Frontières de l'application

- Un module applicatif initial suffit ; séparer les responsabilités par packages avant
  de multiplier les modules. Dépendances injectées explicitement, sans framework DI lourd.
- Caméra et import produisent une entrée commune : image, taille, orientation, identifiant
  et transformation de coordonnées. L'import ne dépend pas de la permission caméra.
- Un adaptateur d'inférence transforme les tenseurs du modèle en instances : catégorie,
  score, rectangle, masque et coordonnées rapportées à l'image source. Versionner classes,
  prétraitements et paramètres ; ne pas exposer les tenseurs directement à l'UI.
- Présentation : état explicite pour source, traitement, erreurs et résultats ; contours
  alignés après rotation/redimensionnement ; calcul hors du thread UI.
- Personnes et chats ajouteront des identités avec abstention, distinctes des catégories.
  L'action externe M2 sera une frontière séparée, déclenchée explicitement, sans clé dans Git.

## Inférence : comparaison et expérience reportées à M1-01

| Candidat | Intérêt envisagé | Risque à vérifier avant choix |
| --- | --- | --- |
| ONNX Runtime Mobile | Export ONNX depuis PyTorch, API Java Android, CPU et XNNPACK ; licence MIT | Opérateurs/export du modèle, taille APK, fidélité numérique et accélérateurs réellement utilisables |
| LiteRT | Runtime Android et API CompiledModel CPU/GPU/NPU ; licence Apache 2.0 | Conversion du modèle, support des opérateurs, chaîne de conversion sur Windows CPU et matériel supporté |

Sources : [déploiement ORT](https://onnxruntime.ai/docs/tutorials/mobile/),
[licence ORT](https://github.com/microsoft/onnxruntime/blob/main/LICENSE),
[API LiteRT Android](https://developers.google.com/edge/litert/android),
[conversion PyTorch/LiteRT](https://developers.google.com/edge/litert/conversion/pytorch/overview)
et [licence LiteRT](https://github.com/google-ai-edge/LiteRT/blob/main/LICENSE).
Ce sont des candidats, pas deux dépendances à intégrer simultanément. Aucun modèle,
runtime ou format de paquet binaire n'est encore retenu. Tester d'abord une référence CPU
sur ordinateur et téléphone ; GPU/NPU du téléphone restent des pistes mesurées en M1-01,
sans présumer leur disponibilité pour le modèle choisi. Aucun GPU PC n'est requis ou promis.
L'expérience devra comparer qualité, export/rechargement, latence, mémoire, taille et chauffe,
puis définir les budgets réalistes. La licence d'un runtime ne couvre pas les poids d'un modèle.

## Outil PC : orientation, choix final en M0-05

Envisager Python avec PyTorch CPU pour les extracteurs/expériences et scikit-learn pour
les classificateurs de référence. [PyTorch local Windows/CPU](https://pytorch.org/get-started/locally/).
Exporter en ONNX ou LiteRT selon le modèle : les convertisseurs et leurs opérateurs doivent
être éprouvés sur cet ordinateur, sans supposer un export universel.
[Export PyTorch ONNX](https://docs.pytorch.org/docs/2.14/onnx.html),
[limites des exports scikit-learn](https://scikit-learn.org/stable/model_persistence.html).
PyTorch et scikit-learn ont des licences de type BSD avec notices à conserver
([PyTorch](https://github.com/pytorch/pytorch/blob/main/LICENSE),
[scikit-learn](https://github.com/scikit-learn/scikit-learn/blob/main/COPYING)).

Comparer en M0-05 Streamlit, UI dans un navigateur local, et PySide6, UI de bureau.
L'UI couvrira personnes, chats, photos/corrections, expériences et exports ; pas de CLI seul
livré, pas d'authentification ou de serveur multiutilisateur. Streamlit se développe localement
et est sous Apache 2.0 ([installation](https://docs.streamlit.io/get-started/installation),
[licence](https://github.com/streamlit/streamlit/blob/develop/LICENSE)). PySide6 reste une option
dont les obligations de licence et modules seront examinés avant adoption ; aucune licence
commerciale ne sera achetée. [Conditions Qt for Python](https://doc.qt.io/qtforpython-6/commercial/index.html).
Le fine-tuning partiel sera un essai CPU borné, après les références embeddings et extracteur gelé.

## Qualité : candidats gratuits, implémentation en M0-03/M0-05

| Mesure / gate AGENTS.md | Candidat et périmètre | Rapport / limite à lever au bootstrap |
| --- | --- | --- |
| Formatage et lint Kotlin | ktlint, MIT ; sources Kotlin | Console/Checkstyle ; conventions Compose et version à vérifier |
| Analyse Android | Android Lint ; code et ressources Android | HTML/XML ; ne mesure pas à lui seul couverture et duplication |
| Complexités ≤ 10 cyclomatique / ≤ 15 cognitive | detekt, Apache 2.0 ; fonctions Kotlin | HTML/SARIF ; activer explicitement la règle cognitive, vérifier compatibilité Kotlin/Gradle |
| Lignes et branches ≥ 80 % | JaCoCo, EPL 2.0 ; code JVM mesurable | XML/HTML ; interpréter code synthétique Kotlin/Compose et définir intégration tests appareil |
| Duplication nouveau code ≤ 3 % | jscpd, MIT ; clones Kotlin | JSON/HTML ; fixer référence Git/dénominateur et calcul du nouveau code, pas assimiler taux global et diff |
| Dépendances et licences | OWASP Dependency-Check candidat + inventaire des notices | Rapports à choisir ; données CVE réseau, faux positifs et licences restent des contrôles distincts |

Sources : [ktlint](https://github.com/ktlint/ktlint), [Android Lint](https://developer.android.com/studio/write/lint),
[detekt et compatibilités](https://github.com/detekt/detekt), [règles de complexité](https://detekt.dev/docs/1.23.8/rules/complexity/),
[compteurs JaCoCo](https://www.jacoco.org/jacoco/trunk/doc/counters.html), [licence JaCoCo](https://www.jacoco.org/jacoco/trunk/doc/license.html),
[jscpd](https://github.com/kucherenko/jscpd), [OWASP Dependency-Check](https://owasp.org/projects/dependency-check).
JaCoCo ne compte pas les exceptions comme branches. Une métrique absente reste non mesurée.
M0-03 doit choisir les versions compatibles, prouver les échecs des gates, publier les rapports
localement/en CI et traiter les dépassements de complexité par revue selon AGENTS.md.
Ne pas activer d'analyse sémantique de duplication exigeant une API ou un modèle téléchargé.
Aucun service Sonar payant ou serveur de qualité n'est nécessaire à cette orientation.
M0-05 fera l'équivalent sur le PC avant sa première fonctionnalité.

## Coûts, licences et suites

Les sources [Kotlin](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt),
[AndroidX](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) et
[Gradle](https://github.com/gradle/gradle/blob/master/LICENSE) sont sous Apache 2.0 ;
le SDK a ses [conditions distinctes](https://developer.android.com/studio/terms).
Conserver les notices et auditer les artefacts réellement épinglés, poids et données inclus.
Aucun achat n'est prévu ; une CI distante dépendra d'une offre gratuite vérifiée en M0-03,
avec exécution locale toujours possible. Pas de service IA choisi dans cette ADR.
Prochaines preuves : bootstrap/compatibilité M0-02, gates M0-03, APK M0-04, benchmarks M1-01.
