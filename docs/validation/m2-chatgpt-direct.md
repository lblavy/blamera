# Fiche ChatGPT simplifiée — validation

31 octobre 2026, M2-08, version 0.13.0/code25.
Baseline : `fb4a92b5817f25830bfb2b8b5361f72301d475d1`.
Implémentation, revue, contrôles globaux et testeur conformes sur PC. APK principal
installé ; parcours essentiel accepté sur le Xiaomi par retour humain.

[Décision et critères](../decisions/0011-chatgpt-direct-streaming.md),
[recette manuelle](../tests/m2-chatgpt-direct-recette.md).

Le retour humain « ça fonctionne » valide le parcours essentiel de mémorisation
M2-07 sur 0.12.0. La présente évolution traite l'ergonomie, l'envoi direct, le choix
de réflexion, le streaming et le retrait de Gemini. Les preuves de cette évolution
seront renseignées après les exécutions réelles, avec distinction Red/Green,
revue, contrôles globaux, testeur et retour du Xiaomi.

Les tests utilisent uniquement des fixtures fictives sur PC, sans lecture de
jeton privé, appel authentifié réel, activation de crédits ou modification du
téléphone. Installation limitée au seul APK principal, lancement manuel.

## Premiers tests d'ergonomie

Red `Personal*` : trois tests/trois échecs fonctionnels sur la production 0.12.0,
23 s, sortie 1. Le compte connecté ne peut pas envoyer sans confirmation ;
l'objet préparé ne part pas sans dépliage ; la case est encore présente dans
les contrôles. Console et XML dans
`build/reports/validation/m2-08-personal-chatgpt/` (`red-personal.log`).

Premier essai conservé séparément sous `attempt-fixture.log` : l'assertion UI
échoue pour la bonne raison, mais deux tests JVM échouent avant leur scénario
à cause de l'horloge Android non simulée. Ce premier essai ne constitue pas un
Red fonctionnel de ces deux tests. L'horloge est ensuite injectée, sans modification
de production, et les trois assertions fonctionnelles sont exécutées.

Red du transport et des réglages : `build/chatgpt-stream-core-red.log`, sortie 1,
21 s, quatre tests/quatre échecs d'assertions. Profils d'effort absents, champ
`reasoning.effort` absent de la requête, préférence non conservée et aucun bloc
observable avant EOF sur l'adaptateur HTTP. Stubs compilables ajoutés sans le
comportement attendu. Archives console/XML dans
`build/reports/validation/m2-chatgpt-streaming/coder/red/`.

Red du ViewModel en streaming : huit cas/huit échecs fonctionnels, sortie 1,
6 s. L'ancien chemin n'appelle pas le callback progressif, l'effort n'a aucun
effet et un compte périmé reste connecté dans l'UI. XML `red-stream-state.xml`
et console `red-stream-ui.log` sous `m2-08-personal-chatgpt/`.
Deux tests supplémentaires de coalescence et de plafond ont échoué par assertions
dans le lot précédent. Un premier lot avait une erreur de signature JUnit
(méthode de test retournant un booléen) ; `attempt-junit-shape.log` la conserve
séparément et elle n'est pas comptée comme Red des huit scénarios.

## Périmètre des régressions

Le retrait demandé de Gemini supprime les tests propres à cette fonctionnalité
abandonnée ; ce n'est pas une exclusion de couverture ni une désactivation de
test pour masquer un échec. Les tests de partage Lens, de recadrage, de fichier
partagé et de retour au document sont conservés/adaptés, avec absence du bouton
Gemini vérifiée dans la fiche. Les anciens tests imposant une case sont mis à
jour selon le nouveau contrat utilisateur ; les gardes de compte et les tests
d'absence d'envoi implicite restent nécessaires.

## Intégration en cours

Le premier Green commun s'arrête à la compilation d'un nouveau test de protocole
(`ByteArray.mapNotNull` indisponible), sortie 1, 46 s ; aucun test exécuté et aucun
Green revendiqué. La correction utilise l'itération adaptée. Les écarts statiques
détectés sont corrigés dans les périmètres des coders avant la nouvelle campagne ;
aucun seuil ni exclusion n'est changé.

Première exécution commune compilée : **294 tests, sept échecs**, aucun Green
revendiqué. Une fixture HTTP utilise le JSON Android non simulé ; deux attentes
de statut d'annulation et quatre attentes d'intégration de fiche restent à
diagnostiquer/corriger. Le rapport de couverture final ne peut pas être déduit
de ce passage en échec.

## Première revue

Lecture indépendante de l'état gelé, sans écriture ni build : défaut retenu dans
la sauvegarde des préférences. Choisir un modèle puis immédiatement un effort
annule potentiellement l'écriture du modèle pendant son attente de verrou/disque ;
la deuxième écriture ne conserve que l'effort avec l'ancien modèle. Le scénario
doit obtenir un Red déterministe puis une sauvegarde du couple courant.
Snapshot initial de **325 empreintes** conservé dans
`build/reports/validation/m2-chatgpt-direct/review1/source-snapshot.json`.
La lecture ne relève pas d'autre blocage établi ; verdict final suspendu aux
corrections et aux preuves Green/qualité, sans supposer leur réussite.

La revue vérifie aussi la capture PC synthétique à 300 dp et police ×1,5 :
sélecteurs côte à côte, textes longs en ellipse et action d'envoi accessible,
sans défaut visuel bloquant sur cette preuve. Copie locale dans
`build/reports/validation/m2-chatgpt-direct/visual/personal-panel-300dp-large-font.png`.
Ce rendu PC ne remplace pas la recette visuelle sur le Xiaomi.

Red de la correction : un test, sortie 1, 31 s, `ComparisonFailure` : après
écriture du modèle suspendue puis choix de réflexion, la réouverture attend Terra
mais retrouve Sol. Preuve `red-preference-race.xml` sous
`build/reports/validation/m2-08-personal-chatgpt/`. Le correctif fait porter à la
dernière écriture les deux préférences capturées.

Les autres échecs du passage commun sont localisés : fixture JSON du test HTTP
corrigée avec le runner Android simulé, même assertion conservée ; attentes de
fiche qui ne drainaient plus le Looper corrigées en gardant le délai et les
assertions ; statut d'annulation rendu visible sans rétablir les avertissements.
Format et detekt finaux des deux périmètres : sorties 0. Nouveau Green commun
avec JaCoCo en cours, aucune couverture actuelle déduite des rapports résiduels
du run précédent.

## Green corrigé et couverture ciblée

`green-review1-corrections.log` sous `m2-08-personal-chatgpt/` : sortie **0**,
**1 min 57 s**, **295 tests**, sans échec, erreur ou test ignoré. La tâche
`jacocoDebugReport` est exécutée ; `corrected-jacoco.xml` et les XML
`corrected-TEST-*` appartiennent à ce run. Les 17 nouveaux tests backend passent,
y compris HTTP avant EOF/cancel/deadline et migration du record 0.12.0.
Le lecteur PC valide huit tests pour la paire 0.13.0/code25.

Mesure avec `quality_gates.changed_lines`/`coverage_metrics` contre la baseline
indiquée, sans seuil ou exclusion modifié. Artefact local
`build/reports/validation/m2-chatgpt-direct/metrics-targeted.json` :

| Nouveau code | Lignes exécutables | Lignes couvertes | Branches couvertes |
| --- | --- | --- | --- |
| Ticket | 567 | 98,59 % | 84,05 % |
| Core ChatGPT hors UI | 280 | 98,93 % | 87,60 % |
| Présentation et partage | 287 | 98,26 % | 81,73 % |

Le package UI ChatGPT mesuré par le coder atteint 638/660 lignes (96,67 %) et
281/335 branches (83,88 %) sur ce lot. La couverture de tout Android dans ce
rapport ciblé est **partielle** (70,98 %/64,94 %) : elle n'est pas présentée comme
la conformité globale, qui doit être mesurée par la campagne complète.

Nouveau gel de **326 empreintes**. La revue relit le correctif de préférence et
son Red, sans nouveau risque bloquant à ce stade. Contrôles globaux, validation
indépendante finale et installation restent à exécuter. L'utilisateur a confirmé
la reconnexion USB, sans autre manipulation du téléphone par l'agent.

Verdict final de revue du code : **conforme**, aucun blocage résiduel. Les 326
empreintes sont identiques au gel corrigé. Les seuils sont satisfaits sur les
composants et le nouveau code mesurés ; des mesures individuelles de branches
restent partielles : TrialHttpBody 11/14 (78,57 %), défauts de l'interface TrialHttp
3/6 (50 %). Elles ne sont pas annoncées conformes individuellement et ne sont pas
exclues. Suivi secondaire M2-08-COV-01 : compléter les cas rares du lecteur/fallback
HTTP sans modifier les seuils. Les chemins critiques avant EOF, erreur, annulation
et délai sont couverts ; la campagne globale reste nécessaire.

## Environnement de la campagne complète

Premier passage global : sortie 1, bloqué par l'environnement avant preuve Android
complète (DNS PyPI indisponible, AuthorizationManager du PowerShell enfant,
erreur interne mypy). Les rapports sont conservés dans
`quality-environment-attempt/` et `quality-environment.log` sous le dossier local
`m2-chatgpt-direct/`. Il ne constitue pas une validation logicielle ni une mesure
globale de couverture. Aucun contrôle ou seuil désactivé.

Relance avec les accès PC nécessaires au SDK et aux audits publics ; autorisation
de scripts limitée à ce processus PowerShell (`PSExecutionPolicyPreference=Bypass`),
sans réglage persistant, changement de source ou opération téléphone. Résultat
conforme de cette relance détaillé ci-dessous.

## Campagne globale conforme et APK

Relance réelle de `./scripts/quality.ps1 -BaseRef fb4a92b5817f25830bfb2b8b5361f72301d475d1` :
sortie **0**, résumé `passed`. **421 tests Android**, sans échec, erreur ou test
ignoré ; **76 tests de scripts** et **quatre PC**. Mobile global : **96,86 % lignes /
86,55 % branches** ; nouveau code **98,59 % / 84,05 %**, 567 lignes exécutables.
Scripts 94,35 %/88,52 %, PC 100 %/100 %. Branches nouvelles des scripts et nouveau
code PC non mesurables dans ce diff, pas déclarés artificiellement à 100 %.

Duplication nouvelle mobile **1/797 lignes (0,1255 %)** ; scripts 0/1 et PC sans
nouvelle ligne. Aucune violation des limites de complexité 10/15, aucun changement
de seuil ou d'exclusion. Audits : **98 paquets Maven/132 entrées de licences** et
**35 paquets PC/35 licences**, aucune vulnérabilité active dans les périmètres
configurés. Rapports archivés avant la validation du testeur dans `quality-final/`,
`jacoco-global.xml`, `junit-global/`, `junit-counts.json` ; journal `quality.log`.
Les **326 empreintes** restent identiques au gel revu.

APK principal `dist/blamera-0.13.0-debug.apk`, package `dev.blamera`, version
0.13.0/code25 vérifiée dans les métadonnées Gradle. **458 274 381 octets**,
SHA-256 `358af30c093214dad99181513e6b74ccc5d3101509de36eccdcd38fe7571fd54`.
Original Gradle et copie identiques ; métadonnées `apk.json`. Validation indépendante
et installation de cet APK décrites ci-dessous.

## Validation indépendante finale

**267 tests Android ciblés**, sortie **0**, **2 min 03 s**, sans échec, erreur ou
test ignoré ; **huit tests du lecteur**, verts. Les 421 XML globaux et le résumé
`passed` sont vérifiés séparément. Les **326 empreintes** restent identiques
avant/après validation. Rapport et preuves locaux dans
`build/reports/validation/m2-chatgpt-direct/tester/`, dont `report.md`.

Package/code25/SHA-256 et taille conformes à `apk.json`, APK build/dist identiques.
Signature v2 avec certificat inchangé :
`eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555`.
Trois permissions exactes, `allowBackup=false` et 18 exclusions cloud/transfert,
activité synthétique privée en `fullUser`. Les **38 assets/bibliothèques natifs**
sont tous identiques à 0.12.0. Destinataire/consigne/query Gemini et cases absents
du code de production ; Lens conservé. Captures PC et liens documentaires vérifiés.
Verdict **conforme sur PC**, aucun défaut bloquant reproduit. Les mesures
individuelles de branches et le suivi M2-08-COV-01 restent explicites.

## Installation réelle

Après reconnexion USB confirmée par l'utilisateur : un seul Xiaomi `2407FPN8EG`
prêt. Empreinte/taille APK et 326 sources revérifiées avant `adb -d install -r`
sur le seul APK principal **0.13.0/code25**. Sortie **0**, **Success**. Début
31 octobre 2026 à **16:20:15,077 UTC**, fin **16:20:33,369 UTC**. Reçu
`install-result.json` dans le dossier local de validation, empreinte identique
à celle de l'APK validé. Aucun lancement automatique, test APK, test instrumenté,
autre modification du téléphone ou lecture privée.

Recette manuelle demandée après installation : fiche compacte sans cases, modèle
et réflexion modifiables, envoi et vrai streaming Markdown, annulation puis reprise
des deux préférences après réouverture. La connexion 0.12.0 est déjà acceptée,
mais cette nouvelle version exige encore son retour humain. Aucune inférence
authentifiée réelle ni validation sur compte personnel n'est déduite des tests PC.

## Retour humain

31 octobre 2026 : l'utilisateur répond « ça semble ok » à la recette proposée
pour la fiche compacte, le choix modèle/réflexion, le flux Markdown, l'annulation
et la conservation des préférences. Le parcours essentiel M2-08 est accepté sur
son Xiaomi avec la version installée 0.13.0/code25 ; aucune anomalie signalée.

Ce retour global ne constitue pas une validation individuelle de tous les cas
complémentaires, une mesure de latence ou une comparaison de qualité entre
efforts/modèles. Les preuves PC et le suivi secondaire M2-08-COV-01 restent
inchangés. Mise à jour documentaire uniquement : aucun nouveau test, appel
authentifié ou accès au téléphone n'est nécessaire pour enregistrer ce retour.
