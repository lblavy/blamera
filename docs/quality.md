# Qualité mobile, PC et scripts de contrôle

Le socle M0-03 fournit une commande commune locale/CI. Les règles restent celles
d'[AGENTS.md](../AGENTS.md) ; aucune exception de couverture métier ou UI n'est ajoutée.

## Exécuter

Prérequis : JDK 21, SDK Android 36/Build Tools 36.0.0, Node 24.12.0 avec npm,
Git et le socle Python autonome préparé par `scripts/pc.ps1 Bootstrap` : Python 3.12.14
géré localement, venv verrouillé `pc/.venv`. Aucun alias WindowsApps ou runtime Codex requis.

```powershell
.\scripts\pc.ps1 Bootstrap
# Le venv PC est selectionne par defaut, sauf BLAMERA_PYTHON/-PythonExe explicite :
.\scripts\quality.ps1
# Autre référence du nouveau code :
.\scripts\quality.ps1 -BaseRef main
```

Sur Linux, avec `JAVA_HOME` et `ANDROID_HOME` configurés :

```bash
pc/.venv/bin/python scripts/quality.py --base-ref HEAD
```

La commande installe coverage.py dans `.tools/python-quality` et jscpd dans `node_modules`,
avec versions épinglées et sans installation globale. Le script Windows du build utilise
le JBR d'Android Studio et le SDK personnel ; voir l'[environnement](development-environment.md).
Un accès réseau est nécessaire aux dépôts officiels et au scan OSV. Sans preuve complète,
la commande termine en échec ; elle ne fait rien sur le téléphone.

Pour reformater volontairement le Kotlin : `scripts/gradle.ps1 formatKotlin`.
La commande qualité vérifie le format sans le réécrire.

## Contrôles et versions

| Contrôle | Outil épinglé | Périmètre et preuve |
| --- | --- | --- |
| Build, types et Android Lint | AGP 8.13.2 / Kotlin 2.3.21 | APK debug, compilation et rapports lint HTML/XML |
| Format Kotlin | ktlint CLI 1.7.1 | Sources/tests Kotlin et scripts Gradle ; rapport Checkstyle |
| Analyse statique / complexités | detekt CLI 1.23.8 isolé | Sources Kotlin de production ; HTML/Checkstyle ; cyclomatique ≤ 10 et cognitive ≤ 15 |
| Couverture Android | JaCoCo 0.8.15 | Tests JVM/Compose/Robolectric 4.16, dont MainActivity ; XML/HTML |
| Duplication | jscpd **4.0.5 Node**, lock npm | Sources Kotlin de production ; JSON/HTML, clones ≥ 5 lignes et ≥ 50 tokens |
| Inventaire et licences | Gradle License Report 3.1.1 | `debugRuntimeClasspath`, normalisation SPDX, JSON/HTML et notices extraites |
| Vulnérabilités | API publique OSV v1 | Les coordonnées Maven exactes des artefacts runtime directs/transitifs ; réponse et date conservées |
| Couverture des scripts | coverage.py 7.10.7 | Cinq scripts Python de qualité ; lignes et branches séparées, JSON/XML/HTML |
| Format/lint PC | Ruff 0.17.2 | Sources/tests PC et `scripts/quality_pc.py` |
| Types PC | mypy 2.4.0 strict | `pc/src` ; types des bibliothèques selon leurs annotations |
| Complexités Python | Ruff C901 / complexipy 8.2.0 | Cyclomatique ≤ 10 et cognitive ≤ 15 ; sources PC et cinq scripts de qualité |
| Couverture PC | coverage.py 7.10.7 | AppTest + smoke-test HTTP ; mesure de `pc/src` distincte des scripts |
| Duplication Python | jscpd 4.0.5 | PC et scripts de production séparés, tests exclus, mêmes seuils de clones et nouveau code ≤ 3 % |
| Runtime PC | uv 0.13.5, OSV PyPI | Export `--no-dev` du lock et métadonnées installées, versions exactes, politique licences PC |

Les configurations explicites sont dans le catalogue Gradle, `requirements-quality.txt`,
`package-lock.json`, `config/detekt.yml`, `.editorconfig`, `.jscpd.json` et `.coveragerc`.
Les seuils de couverture s'appliquent séparément au mobile, au PC et aux scripts : **80 % des lignes
et 80 % des branches**, sur l'ensemble et sur le nouveau code mesurable. Aucune moyenne
entre ces composants ne peut masquer un défaut.

detekt s'exécute dans un processus CLI distinct, sans résolution de types Kotlin : aucun
plugin compilé avec un ancien Kotlin n'est injecté dans le compilateur de l'application.
La vérification des types reste faite par la compilation Kotlin. Une syntaxe future non
supportée par ce parseur demandera une mise à jour, pas une exclusion silencieuse.
Depuis M0-05, les complexités Python sont mesurées automatiquement. Deux extractions
ciblées séparent les compteurs d'une source et les intersections de clones ; elles conservent
les formules et ont ramené les fonctions préexistantes aux seuils, sans exception nécessaire.
Les scripts antérieurs conservent leur format ; le nouveau script PC suit Ruff.

## Définition du nouveau code

Par défaut, comparaison avec **HEAD**, sur l'état de travail courant, puis ajout explicite
des fichiers non suivis de Git. Le bootstrap ne présente donc pas les nouveaux fichiers
comme « aucun nouveau code ». Une référence absente/invalide provoque un échec.

PC : `pc/src/**/*.py`, instructions canoniques de coverage.py et arcs dont l'origine
appartient aux lignes modifiées ; le préfixe `pc/` est normalisé pour le rapport produit depuis `pc/`.
La duplication Python des scripts mesure les cinq `scripts/*.py` de production ; celle
du PC mesure `pc/src/**/*.py`. Les plages dupliquées nouvelles sont unies pour éviter
leur double comptage ; aucun pourcentage global ne remplace le taux du nouveau code.

- Mobile : fichiers `app/src/main/java/**/*.kt` ; couverture à partir des lignes exécutables
  et branches JaCoCo correspondant aux lignes ajoutées/modifiées dans le diff.
- Scripts : fichiers `scripts/**/*.py`, hors tests ; même référence Git et même prise en
  compte des non-suivis, rapport coverage.py distinct et branches partant des lignes nouvelles.
- Les lignes Python sont intersectées avec l'ensemble canonique des instructions fourni
  par [Coverage.analysis2](https://coverage.readthedocs.io/en/7.10.7/api_coverage.html#coverage.Coverage.analysis2),
  exporté dans le JSON avec vérification du nombre d'instructions. Les événements bruts
  `executed_lines` incluent notamment des docstrings : ils ne définissent pas ce dénominateur.
  Une preuve canonique absente, mal formée ou incohérente bloque la validation.
- En PR, la CI utilise le SHA de base ; sur push, le SHA précédent. Le premier push sans
  parent utilise la référence explicite `EMPTY` et traite toutes les sources comme nouvelles.
- Si aucun changement ne contient de ligne/branche exécutable, la mesure correspondante
  est **non applicable**, sans lui attribuer 100 %. Le rapport global reste obligatoire.
- Les seules exclusions de couverture Android configurées sont `R`, ses classes internes
  et `BuildConfig`, générées par Android. Les filtres Kotlin/Compose de JaCoCo traitent le
  bytecode synthétique ; l'UI écrite à la main et MainActivity restent incluses.
- Côté Python, seuls les tests sont omis de la mesure du code de production des outils ;
  aucun `pragma: no cover` ou retrait de logique n'est ajouté.

La duplication du nouveau code est calculée par intersection des plages de clones jscpd
avec les lignes nouvelles, en dédupliquant les plages qui se chevauchent. Le dénominateur
est le nombre de **lignes physiques non vides de Kotlin ajoutées/modifiées**, commentaires
et imports compris. Cette définition est explicite et distincte du taux global de jscpd,
ainsi que du calcul exact de Sonar. Le gate exige **≤ 3 %** ; le taux global est aussi rapporté.
Le nombre de fichiers réellement analysés doit correspondre à toutes les sources Kotlin
du périmètre : une absence de rapport ou un fichier ignoré ne donne pas un résultat propre.

## Dépendances : portée et décisions

Le scope audité est le **runtime de l'APK debug actuel**, directement et transitivement,
avec versions résolues exportées à chaque exécution. Le rapport de licences inclut aussi
des métadonnées/BOM, d'où un nombre d'entrées supérieur au nombre d'artefacts exécutables.
La présence d'une licence autorisée est exigée pour chacun des artefacts de l'inventaire.
La liste SPDX acceptée est explicite dans `config/allowed-licenses.json` ; une licence
inconnue ou non admise bloque, sans attribution fictive ni liste d'exceptions automatique.

OSV reçoit uniquement noms, écosystème Maven et versions des bibliothèques publiques.
Les IDs du batch sont complétés par les détails de chaque avis ; les avis retirés restent
conservés séparément. Toute vulnérabilité active bloque jusqu'à correction ou triage explicite,
y compris si sa gravité est inconnue. Une pagination ou réponse incohérente/non accessible
bloque l'audit plutôt que d'ignorer des résultats. Un scan réussi signifie « aucun avis actif
retourné dans ce scope à cette date », sans prétendre prouver l'absence de vulnérabilités inconnues.

Dev/test, transitifs des outils Node/Python/Gradle, JDK/SDK et futurs poids de modèles ne sont
**pas** couverts par ce scan runtime. Leurs versions/outils principaux sont épinglés ; leur audit
de vulnérabilités complet n'est pas revendiqué. Réexaminer ce périmètre à l'ajout de composants.
Les notices extraites et leurs sources restent consultables dans le rapport de licences.

## Rapports, échecs et CI

Le point d'entrée retire les rapports de décision précédents avant l'exécution, conserve
les codes de sortie et écrit une synthèse même si un outil échoue. Une mesure réussie ne
peut compenser une compilation ou un formateur en échec. Principaux chemins :

- `build/reports/quality/summary.json` et `summary.md` : verdict et métriques séparées.
- `build/reports/quality/detekt.html`, `detekt.xml`, `ktlint.xml`.
- `build/reports/quality/duplication/html/` et `jscpd-report.json`.
- `build/reports/quality/python/` : couverture des scripts.
- `build/reports/quality/osv.json` : scan daté, inventaire interrogé et avis éventuels.
- `app/build/reports/jacoco/jacocoDebugReport/` et `app/build/reports/tests/`.
- `app/build/reports/dependency-license/` et `app/build/reports/lint-results-debug.html`.

Les tests des gates vérifient les seuils, mesures manquantes, mauvais scope, références Git,
licences non acceptées, scan incomplet et vulnérabilités. Une fixture passée au **vrai detekt**
doit produire les deux violations de complexité ; une fixture passée au **vrai jscpd** doit
échouer au gate de duplication. Les fixtures sont générées dans `build/`, hors production.
Les preuves Python ajoutent de vrais Ruff C901/complexipy sur une fonction excessive,
mypy sur un retour de mauvais type, Ruff format sur un fichier mal formaté et jscpd
sur deux fonctions Python copiées. Leurs erreurs d'invocation ne comptent pas comme des rejets valides.

## Périmètre PC et dépendances

`scripts/quality_pc.py` produit `build/reports/quality/pc/summary.json` ; la commande globale
exige un verdict PC réussi et recalcule ensuite la couverture des scripts, incluant son exécution.
La couverture PC utilise uniquement `pc/src`, sans exclusion de logique. Les tests et
les bibliothèques tierces restent hors code de production mesuré.
Le serveur de smoke-test écoute sur `127.0.0.1`, ne lance aucun navigateur et son processus
est fermé dans un `finally`. AppTest ne prouve pas le rendu graphique dans un navigateur.

Le lock exporté exclut les outils dev/test ; les marqueurs sont évalués pour la plateforme
réelle, et chaque version est comparée aux métadonnées du venv PC. OSV interroge ces
coordonnées PyPI exactes, avec les mêmes refus de pagination, avis actifs et données absentes.
Le rapport conserve date, scope et résultat ; le scan Maven reste distinct et inchangé.
`pc/config/licenses.json` autorise explicitement les expressions recensées. Six normalisations
de métadonnées anciennes sont liées à leur version, empreinte et licence installée relue.
Une licence absente ou inconnue échoue ; aucune clause « unknown accepté ».
Les notices sont conservées dans les roues installées (`dist-info`), sans redistribution ici.
Les dépendances natives embarquées, les outils dev/test, uv/CPython et leurs composants ne
sont pas certifiés par ce scan runtime : aucune absence globale de vulnérabilité n'est prétendue.

Rapports supplémentaires : `pc/coverage.{json,xml}`, `pc/html/`, `pc/complexity.json`,
`pc/duplication/`, `pc/inventory.json`, `pc/osv.json` et les logs de chaque commande,
ainsi que `python/duplication/` pour les scripts. Le [guide PC](pc-development.md)
donne les commandes autonomes ; l'[ADR 0002](decisions/0002-pc-foundation.md) motive les choix.

Le workflow GitHub utilise Ubuntu standard sur le dépôt public, des actions épinglées par SHA,
JDK 21, Node/Python épinglés et le SDK 36. Les pushs sur `main`/`codex/**`, les PR et le
déclenchement manuel sont configurés. Il exécute la même commande, sans appareil,
cache distant, upload d'artefact ou clé personnelle. Logs et `GITHUB_STEP_SUMMARY` donnent
le résultat même en échec ; les rapports complets restent produits localement sur le runner.
Les runners standard sont gratuits pour les dépôts publics ; les quotas de stockage étant
distincts, aucun stockage distant supplémentaire n'est activé. Le workflow accompagne
l'instantané public. La commande locale fournit les preuves de validation du POC.
Le 26 octobre 2026, l'utilisateur a explicitement reporté la CI distante et accepté
la validation locale. Le commit d'import public porte `[skip ci]` pour conserver
ce report. Une exécution distante exige toujours la préparation des modèles et fixtures.
L'APK éventuellement construit en CI possède sa clé debug éphémère ; ce n'est pas une mise à jour
signée avec la clé personnelle du téléphone et il n'est pas diffusé par ce workflow.

## Sources primaires consultées

L'incrément image M1-03 ajoute ORT Android 1.31.0 (licence MIT) à l'inventaire runtime
Maven et au scan OSV. Le modèle AGPL possède ses notices embarquées et son contrôle
d'empreinte distincts ; un scanner de dépendances Maven ne mesure pas sa qualité statistique.
Les tests natifs Windows utilisent un JRE privé et des fixtures publiques préparées :
voir [reconstruction image](image-segmentation.md). Aucune exclusion métier ou réduction
des seuils n'accompagne ce changement. Le workflow reste différé et ne peut pas passer
sans modèle contrôlé et fixtures ; leur publication/hébergement n'est pas prévue ici.


- [Plugin JaCoCo](https://docs.gradle.org/current/userguide/jacoco_plugin.html), [filtres et versions](https://www.jacoco.org/jacoco/trunk/doc/changes.html).
- [detekt CLI](https://detekt.dev/docs/1.23.8/gettingstarted/cli/), [règles de complexité](https://detekt.dev/docs/1.23.8/rules/complexity/).
- [ktlint 1.7.1](https://github.com/ktlint/ktlint/releases/tag/1.7.1), [jscpd v4](https://github.com/kucherenko/jscpd/blob/master/README-v4.md).
- [Gradle License Report](https://github.com/jk1/Gradle-License-Report), [releases](https://github.com/jk1/Gradle-License-Report/releases).
- [OSV batch et pagination](https://google.github.io/osv.dev/post-v1-querybatch/).
- [coverage.py branches](https://coverage.readthedocs.io/en/7.10.7/branch.html), [configuration](https://coverage.readthedocs.io/en/7.10.7/config.html).
- [Facturation GitHub Actions](https://docs.github.com/en/billing/concepts/product-billing/github-actions).
- [Règles Android d'extraction des données](https://developer.android.com/identity/data/autobackup) : exclusions de sauvegarde/transfert ajoutées à l'application, sans modifier les paramètres du téléphone.

## Métadonnées jakarta.inject-api2.0.1

CameraX ajoute un transitif dont le POM/JAR déclarent Apache-2.0 ; l’union des POM parents
du plugin faisait apparaître GPL+Classpath. La correction du normaliseur est strictement
limitée au module et à2.0.1, sans règle de licence globale, élargissement de politique,
exclusion ou changement des parsers/gates. [Textes, sources et empreintes vérifiées](licenses/jakarta.inject-api-2.0.1/README.md).

L’incrément caméra 0.5.0 a passé les contrôles locaux sur 102 tests Android JVM,
56 tests scripts et 4 tests PC ; [rapport, périmètres et métriques](validation/m1-05.md).
Revue ciblée et tester indépendants conformes sur PC ; CI distante reportée et
APK principal installé le 27 octobre 2026, P*/O*/I* OK déclarés avec réserves.
Budgets, essai prolongé et hors ligne restent ouverts.

## Diagnostic personnel de capacité 0.6.0

Les nouveaux scripts `benchmark_collect.py`, `benchmark_report.py`,
`benchmark_metrics.py` et `benchmark_analyze.py` appartiennent au périmètre Python
mesuré ; le collecteur est testé avec une frontière subprocess simulée, sans ADB
réel par les tests. Aucun seuil ou exclusion n’a changé. Le diagnostic Android
réutilise ORT et les contours existants ; les tests JVM incluent de vraies sessions
sur les quatre graphes compacts, des références de tenseurs identiques et les
erreurs/cycles de vie simulés. Cela ne constitue pas une validation matérielle.
[Protocole, TDD, qualité et livraison](validation/m1-yoloe-capacity-phone.md).

## Capture et exploration 0.7.0

Le parcours photo Large conserve les mêmes seuils et exclusions. La campagne locale
sur la baseline `eee1a3c` réussit sur 175 tests Android, 76 scripts et 4 PC ; revue
et testeur indépendants conformes sur PC. Mobile global : 94,51 % lignes / 85,98 %
branches ; nouveau : 93,76 % / 84,00 %. Duplication nouvelle : 29/1 440 = 2,01 %.
Le gate a d'abord refusé 78,35 % de branches nouvelles ; les tests d'acceptation ont
été complétés, sans modifier le code de production ni les règles pour ce complément.
Les rapports globaux sont archivés avant les exécutions ciblées du testeur.
[Résultats détaillés, premières campagnes et limites](validation/m1-capture-analysis.md).

## Adaptations 0.8.0

La campagne locale sur la baseline `3b031de` réussit sur 185 tests Android, 76 scripts
et 4 PC. Mobile global : 96,22 % lignes / 86,49 % branches ; nouveau : 96,39 % / 83,78 %.
Duplication nouvelle : 1/436 = 0,229 %, sans changement de seuil ou d'exclusion.
Le testeur indépendant valide 41 tests ciblés et le package. Le catalogue français
statique est contrôlé à la fabrication et au chargement ; les diagnostics anglais
gardent leur vocabulaire et leurs références. L'outillage de traduction PC possède
ses tests et mesures séparées ; ses avis privés triés ne sont pas masqués dans les
résultats runtime. [Détails et limites](validation/m1-photo-refinements.md).

## Essai ChatGPT Plus 0.10.0

Les règles, scopes et exclusions restent inchangés. Après correction de deux cas
de nettoyage/délai et d'une fixture caméra hors viewport, la commande globale passe
sur 286 tests Android, 76 scripts et quatre PC. Nouveau mobile : 96,99 % lignes,
88,55 % branches ; duplication nouvelle 6/1 541 = 0,389 %, complexités sans violation.
Pas de nouvelle bibliothèque runtime. Revue et testeur indépendants conformes sur PC ;
l'authentification Plus et la réponse réelle restent une preuve humaine.
[Commandes, métriques et limites](validation/m2-chatgpt-plus-trial.md).

## Diagnostic du retour OAuth 0.10.1

Revue et testeur indépendants conformes sur PC. Globale : 295 tests Android,
76 scripts, quatre PC ; nouveau code 95,83 % lignes / 87,10 % branches, duplication
nouvelle 0/211, aucune violation des seuils de complexité. Pas de dépendance,
permission, scope ou seuil modifié pour faire passer le correctif. Les défauts de
préconnexion/accusé et les étapes/délais sont testés ; la cause réelle du blocage
sur Xiaomi n'est pas établie. [Preuves](validation/m2-chatgpt-callback.md).

## Diagnostic réseau ChatGPT 0.10.2

Globale conforme : 304 tests Android, 76 scripts et quatre PC. Nouveau mobile :
92,25 % lignes / 91,36 % branches ; duplication nouvelle 0/187 ; aucune violation
des seuils de complexité, ni changement de règle/exclusion. Revue et testeur
indépendants conformes (76 ciblés et huit lecteur). Le test HTTP au fil utilise
le JDK sur PC ; la cause réelle Android reste ouverte. [Preuves](validation/m2-chatgpt-network.md).

## Finalisation ChatGPT au premier plan 0.10.3

Revue indépendante et globale conformes : 311 tests Android, 76 scripts et quatre
PC. Mobile global 96,70 % lignes / 87,15 % branches ; nouveau 100 % / 100 % sur
50 lignes exécutables. Duplication nouvelle 0/81, aucune violation de complexité
10/15, aucun changement de seuil ou d'exclusion. Le testeur valide 83 tests ciblés,
huit du lecteur et le package installé. L'échange attend le retour RESUMED,
avec annulation et budget monotone de cinq minutes. La finalisation réelle reste
à valider sur le Xiaomi. [Preuves](validation/m2-chatgpt-foreground.md).

## Catalogue ChatGPT 0.10.4

Revue et globale conformes : 319 tests Android, 76 scripts et quatre PC. Mobile
global 96,73 % lignes / 87,25 % branches ; nouveau 100 % / 90 % sur 13 lignes
exécutables. Duplication nouvelle 0/17, aucune violation des limites 10/15,
aucun changement de seuil ou d'exclusion. Le catalogue garde une lecture bornée
de huit Mio uniquement pour son GET exact ; erreur et inférence gardent leurs
plafonds distincts. Testeur : 91 ciblés et huit lecteur verts, package installé,
actifs et permissions inchangés. Le catalogue réel et l'inférence restent à valider sur le
Xiaomi. [Preuves](validation/m2-chatgpt-catalog.md).

Retour humain du 31 octobre : catalogue et réponse française pertinente à la
tasse synthétique visibles dans Blamera 0.10.4. Le parcours essentiel M2-06 est
validé sur Xiaomi ; aucune nouvelle exécution de tests n'est déduite de cette
preuve. Le modèle exact et la latence ne sont pas mesurés ; l'intégration du
recadrage et le rendu Markdown restent des suites dans M2-03/M2-04.

## ChatGPT dans la fiche et Markdown 0.11.0

Revue indépendante et globale conformes sur PC : 349 tests Android, 76 scripts,
quatre PC. Mobile global 96,77 % lignes / 87,33 % branches ; nouveau code
98,19 % / 88,69 % sur 441 lignes exécutables. L'intégration seule, hors Markdown,
atteint 98,06 % / 83,33 %. Duplication nouvelle 1/712 lignes (0,140 %), aucune
violation des limites de complexité 10/15 ni changement de seuil ou d'exclusion.
La bibliothèque CommonMark 0.30.0 est épinglée, auditée et son avis BSD-2-Clause
est embarqué comme asset distinct. La mesure par fichier ObjectInformationCard
reste à 77 % branches, documentée avec suivi M2-04-COV-01 ; elle n'est pas présentée
comme une conformité individuelle. [Preuves et limites](validation/m2-object-chatgpt.md).
Le testeur indépendant confirme 195 tests ciblés Android et huit du lecteur,
le package/signature/permissions et la licence embarquée. Le seul APK principal
est installé après reconnexion USB, `adb -d install -r`, sortie 0 « Success » ;
la recette matérielle reste ouverte. Aucune source modifiée ni test relancé pour
l'installation ; aucun test instrumenté ou appel authentifié réel sur PC.

## Connexion ChatGPT mémorisée 0.12.0

Revue indépendante sans blocage après correction des annulations pendant un
renouvellement et du délai d'attente du premier plan. Globale locale conforme
contre `fc6b5578a3b16aadab61ff399ff97df0f35b644d` : 401 tests Android sans
échec, erreur ou test ignoré, 76 tests de scripts et quatre PC. Mobile global
96,78 % lignes / 87,13 % branches ; nouveau code 96,64 % / 85,10 % sur 625 lignes
exécutables. Duplication nouvelle 0/889 lignes, aucune violation des limites de
complexité 10/15, aucun nouveau seuil ou exclusion. Audits : 98 paquets Maven,
132 entrées de licences et 35 paquets/35 licences PC, aucune vulnérabilité active.
Les métriques partielles du binding Android Keystore et du fichier atomique restent
explicites dans les [preuves et limites](validation/m2-chatgpt-session.md).
La reprise réelle après fermeture doit être confirmée par l'utilisateur ; les tests
PC ne constituent pas une preuve du Keystore de son Xiaomi.
Le testeur indépendant confirme 247 tests Android ciblés et huit tests du lecteur
sans défaut, les 313 empreintes de sources, le package signé, ses protections de
sauvegarde et ses 38 assets/bibliothèques identiques à 0.11.0. Après reconnexion USB,
installation du seul APK principal avec `install -r` : sortie 0 « Success » le
31 octobre 2026 à 15:18:01 UTC ; aucun lancement automatique. Recette humaine de
fermeture/réouverture encore ouverte.

Retour humain suivant : « ça fonctionne ». Le parcours essentiel de reprise de
connexion 0.12.0 est accepté sur Xiaomi ; aucune nouvelle mesure PC n'est déduite
de cette acceptation. La simplification, la réflexion et le streaming relèvent de
M2-08, avec leur [contrat distinct](decisions/0011-chatgpt-direct-streaming.md).

## Fiche simplifiée et streaming ChatGPT 0.13.0

Revue indépendante conforme après correction de la course entre les préférences
de modèle et de réflexion. Globale locale conforme contre
`fb4a92b5817f25830bfb2b8b5361f72301d475d1` : **421 tests Android**, 76 scripts,
quatre PC. Mobile global 96,86 % lignes / 86,55 % branches ; nouveau code
98,59 % / 84,05 % sur 567 lignes exécutables. Core nouveau 98,93 % / 87,60 %,
présentation et partage nouveaux 98,26 % / 81,73 %. Duplication nouvelle
1/797 lignes (0,1255 %), aucune violation des limites 10/15, aucun seuil ou
exclusion modifié. Audits : 98 paquets Maven/132 licences, 35 paquets PC/35 licences,
aucune vulnérabilité active retournée.

Les cas HTTP avant EOF, annulation de lecture bloquée, délai, UTF-8/SSE, fragments
obsolètes, préférences et migration 0.12 sont vérifiés sur PC. Les mesures
individuelles de branches TrialHttpBody (78,57 %) et du fallback TrialHttp (50 %)
restent explicites avec suivi M2-08-COV-01, sans exclusion. Le premier essai global
bloqué par l'environnement est conservé séparément ; la relance conforme n'a
changé aucun réglage persistant. [Preuves et limites](validation/m2-chatgpt-direct.md).
Le testeur confirme 267 tests Android ciblés et huit du lecteur, le package signé,
les 38 assets/bibliothèques identiques à 0.12.0, l'absence de Gemini et des cases,
et les 326 sources inchangées. Mise à jour du seul APK principal installée avec
`adb -d install -r`, sortie 0 « Success », le 31 octobre 2026 à 16:20:33 UTC,
sans lancement automatique. Retour humain suivant : « ça semble ok » ; parcours
essentiel de la fiche et du streaming accepté sur Xiaomi en 0.13.0. Aucun nouveau
test ou chiffre de latence n'est déduit de ce retour, et M2-08-COV-01 reste suivi.

## Cadrage et actions regroupées 0.14.0

Revue indépendante conforme après correction des rotations pendant un export,
de l'état d'original pour l'en-tête et des rectangles invalides. Dernière globale
locale conforme contre `d14c599b04e902edc36eaad37445ba98b0b7721f` : **450 tests
Android**, 76 scripts, quatre PC. Android global 97,02 % lignes/86,29 % branches ;
nouveau code 98,49 %/84,28 % sur 398 lignes exécutables. Chaque composant satisfait
ses seuils : nouveau Photo/UI 98,62 %/82,52 %, partage 100 %/100 %, UI ChatGPT
93,75 %/100 %. Duplication nouvelle six/608 lignes (0,987 %), aucune violation
de complexité 10/15 ni changement de seuil ou d'exclusion. Audits : 98 paquets
Maven/132 licences et 35 paquets PC/35 licences, aucun avis actif retourné.

La mesure par composant a révélé des branches nouvelles insuffisamment couvertes
dans deux périmètres ; des tests de contrats/ownership les complètent, sans toucher
la production ni tester artificiellement le code généré. La mesure individuelle
PhotoTargetActions 78,85 % branches reste explicite avec suivi M2-09-COV-01.
Captures PC portrait/paysage et vrai dialogue paysage/police ×1,5 vérifiés.
[Preuves](validation/m2-photo-actions.md). Le testeur confirme 324 tests Android
ciblés et huit du lecteur, le package signé, ses 38 assets/bibliothèques identiques
à 0.13.0, et les 336 sources finales. Deux lignes vides EOF Python sont supprimées
avec AST identiques et diff vérifié, sans changement Android/APK ni relance inutile
de tests. Après reconnexion USB, installation du seul APK principal par
`adb -d install -r`, sortie 0 « Success », le 31 octobre 2026 à 18:20:53 UTC,
sans lancement automatique. Recette réelle encore ouverte.

## Cible automatique 0.14.1

M2-09-UI-01 supprime le choix Cadrage/Objet au profit de la sélection réelle.
Globale locale conforme contre `75466b8448a7349623de4273a34797e627908919` :
456 tests Android, 76 scripts et quatre PC. Android global 97,01 % lignes/86,18 %
branches ; nouveau code 100 %/85,71 % sur 28 lignes exécutables. Photo/UI complet
98,86 %/83,44 % ; partage et UI ChatGPT inchangés et toujours au-dessus des seuils.
Duplication nouvelle 0/30 lignes, aucune violation des limites de complexité 10/15,
aucune exclusion ni modification de seuil. Audits Maven 98 paquets/132 licences,
PC 35 paquets/35 licences, aucun avis actif retourné. Revue conforme sur les
337 sources ; testeur indépendant conforme avec 210 tests Android ciblés et huit
du lecteur, APK signé et 38 assets/bibliothèques identiques à 0.14.0 vérifiés.
Installation du seul APK principal par `adb -d install -r`, sortie 0 « Success »,
le 31 octobre 2026 à 18:41:42 UTC, sans lancement automatique. Retour humain suivant :
« oui ça marche » ; parcours essentiel de cible automatique accepté sur Xiaomi.
Ce retour n'ajoute aucune mesure ni preuve individuelle des autres cas.
Les dettes individuelles de couverture restent suivies ; amélioration visuelle
du statut de chargement suivie M2-09-UI-02. [Preuves](validation/m2-auto-target.md).

## Analyse commune YOLOE et Pl@ntNet 0.15.0

M2-10, baseline `c7f859116d599e646918c088df3a002b2fad8769` : revue indépendante
conforme sur les 368 sources finales. Finale ciblée 386 tests verts, puis globale
locale conforme : **512 tests Android**, 76 scripts, quatre PC. Android global
97,34 % lignes/86,52 % branches ; nouveau code 99,65 %/90,45 % sur 566 lignes exécutables.

Mesures distinctes du nouveau code : botanique 99,45 %/89,52 %, Photo/UI
100 %/90,79 %, partage/export 100 %/94,44 %, UI ChatGPT 100 %/100 %, stockage partagé
100 % lignes et aucune nouvelle branche mesurable. Deux périmètres initialement
insuffisants ont été complétés par des tests réels de binding JCA et de limites
d'encodeur, sans changer la production. Aucune moyenne ne masque ces composants.
Duplication nouvelle 11/832 lignes (1,32 %), aucune violation de complexité 10/15,
aucun changement de seuil ni d'exclusion. Audits Maven 98 paquets/132 licences,
PC 35 paquets/35 licences, aucun avis actif retourné.

Le vrai dialogue portrait/paysage conserve ses champs et assertions ; son
dimensionnement est stabilisé face au problème Robolectric #8460. Les preuves
séparent erreurs de fixture/compilation et vrais Red ; aucune mesure statistique
de précision botanique n'est déduite de ces tests. Le testeur indépendant confirme
141 tests ciblés et huit du lecteur, 368 sources inchangées, signature habituelle,
trois permissions, sauvegarde désactivée/18 exclusions, 38 assets/bibliothèques
identiques à 0.14.1 et logo présent. Aucun blocage reproduit. Après reconnexion,
le seul APK principal est mis à jour par `adb -d install -r`, sortie 0 « Success »,
le 1er novembre 2026 à 10:36, heure de Paris. Les 368 sources et l'empreinte de
l'APK sont vérifiées ; aucun lancement automatique ni autre opération téléphone.
Réseau Pl@ntNet, Keystore matériel et recette réelle restent ouverts.
[Détails et limites](validation/m2-joint-plants.md).

## Reconnaissance faciale sur photo 0.17.0

Baseline `83d3645`, 2 novembre 2026 : globale locale conforme, **584 tests Android,
154 PC et 76 scripts**. Android 97,22 % lignes / 86,90 % branches ; nouveau code
96,44 % / 84,93 % ; runtime facial 95,57 % / 83,97 % et contrôleur 100 % / 85 %.
Duplication nouvelle nulle, complexités 10/15 respectées, aucun contrôle assoupli.
Audits Maven 101 paquets / 135 licences et PC 36/36 : aucun avis actif retourné.
Revue conforme ; testeur indépendant : 80 tests ciblés verts après résolution d'un
manque d'espace disque, avec échec initial conservé.

La couverture combine les tests JVM et une donnée native fraîche de l'émulateur
PC explicitement ciblé, sans test sur le téléphone. La tâche de rapport choisit
le fichier JVM correspondant au mode de couverture AGP et accepte la donnée
native via `faceNativeCoverage` ; après changement de classes, régénérer celle-ci.
Trois cas publics passent sans assouplir les tolérances. APK personnelle ARM64
vérifiée avec galerie intégrée puis installée sur Xiaomi le 2 novembre 2026 à
12 h 26, heure de Paris (`install -r`, sortie 0 « Success »), sans lancement
automatique. La validation matérielle reste ouverte. Les instantanés PSS ne sont pas des
mesures de pic mémoire. [Preuves, commandes et limites](validation/m3-04-delivery.md).

## Chemins temporaires des tests natifs sous Windows

Utiliser un chemin TEMP/TMP court pour les tests JVM qui chargent ONNX Runtime,
par exemple `C:/dev/blamera/build/t/q` dans ce dépôt. Le chemin de cache construit
par Robolectric ajoute le nom complet du test et l'empreinte du modèle : un dossier
temporaire de validation au nom trop long peut donc dépasser la limite du runtime.

La sonde M3-05 sur ORT 1.31.0 et le Temurin 21 déjà configuré pour les tests confirme
que les mêmes poids publics se chargent à 259 caractères, mais donnent
`ORT_NO_SUCHFILE` dès 260 malgré un fichier présent et une empreinte identique.
Le test original passe avec un cache absent au départ et un chemin de 242 caractères,
sans changement des modèles, assertions ou délais. Les premières tentatives de
qualification avec chemins longs restent archivées comme échecs.

Créer le dossier court sous `build/`, définir TEMP et TMP pour le seul processus
de validation, puis restaurer les valeurs précédentes. Ne pas modifier Windows,
préremplir le cache ou ignorer le test de première utilisation. Le JBR reste le
JDK du build ; le Temurin 21 qualifié reste l'exécutable des tests ORT, selon
`app/build.gradle.kts`. Cette limite constatée du runtime Windows ne constitue
pas une limite mesurée du moteur Android sur le Xiaomi.

## Labels multi-visages 0.18.0

Globale finale conforme contre `83d3645` (cumul M3) : **614 tests Android,
157 PC et 76 scripts**. Android 97,26 % lignes / 86,61 % branches ; nouveau code
96,77 % / 84,41 % sur 1145 lignes exécutables. PC 99,55 % / 97,81 %, nouveau code
99,21 % / 97,12 %. Duplication nouvelle nulle, complexités 10/15 respectées.
Audits Maven 101 paquets / 135 licences, PC 36/36 : aucun avis actif retourné.

Composants modifiés qualifiés : runtime facial 95,90 %/83,60 %, contrôleur
100 %/85,48 %, rendu des labels 98,65 %/87,50 %, écritures atomiques
99,12 %/89,58 %. Le nouveau helper est à 100 %. Les mesures individuelles de
SessionFile (11/14 branches, baseline identique) et ExplorablePhoto (31/40→30/40,
77,50 %→75 %) restent explicites en M3-05-COV-01/02 ; composant UI 83,47 %.
Aucune exclusion ni règle assouplie.

Revue conforme, puis 98 tests ML/UI et 38 tests IO indépendants réussis ;
393 empreintes stables. Les trois globales en échec restent archivées. Correctifs
vérifiés : borne propre du manifeste public, erreurs mémoire natives globales,
remplacement atomique borné et TEMP Windows court. Le dernier point est un
réglage de processus justifié expérimentalement, sans changement de modèle ou
d'assertion de première utilisation. APK personnelle signée et vérifiée,
installée le 2 novembre 2026 à 15 h 16, heure de Paris (`install -r`, sortie 0),
sans lancement automatique. Retour humain : « ça a l'air de fonctionner » ; parcours
essentiel de groupe accepté. Aucun événement d'analyse disponible lors des lectures
filtrées lors de cet essai ; M3-06 a ensuite mesuré la parité et les temps moteur
en direct, puis a été clos dans le périmètre du POC avec limites acceptées.
[Preuves, métriques et limites](validation/m3-05-multiple-labels.md).

## Validation terrain et clôture POC de M3

M3-06 obtient les mesures sans modification de l'application : médiane moteur
418 ms pour un visage, 1118,5 ms pour trois (quatre appels par série) ; nouvelle
instance, initialisation/autocontrôle1576 ms et premier lot trois visages2218 ms,
chargement compris. Trois cas publics respectent toutes les tolérances PC/ARM64.
Les retours humains ne signalent ni erreur ni lenteur gênante.

Le pic mémoire supplémentaire et le délai complet d'affichage restent non mesurés.
L'utilisateur demande explicitement de clôturer ces chantiers pour le POC personnel
qui lui convient : limites acceptées, sans nouvelle instrumentation ou tâche
exploratoire conservée en suspens. Aucun résultat absent n'est déclaré conforme ;
les règles de qualité du code et les protections restent en vigueur.
[Bilan mesuré et portée de la clôture](validation/m3-06-device-measurements.md).
