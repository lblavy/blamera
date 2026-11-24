# Cadrage et actions de photo — validation M2-09

31 octobre 2026, version 0.14.0/code26.
Baseline : `d14c599b04e902edc36eaad37445ba98b0b7721f`.
Implémentation, revue, contrôles globaux et testeur conformes sur PC.
APK principal installé ; recette Xiaomi encore ouverte.

[Étude UI/UX et critères](../specs/photo-actions.md),
[recette](../tests/m2-photo-actions-recette.md).

Évolution demandée après acceptation de 0.13.0 : envoyer le cadrage visible sans
dépendre d'un objet reconnu, regrouper Analyser/ChatGPT/Lens, déplacer la navigation
en icônes dans l'en-tête et placer modèle/réflexion derrière une roue dentée.
Les preuves TDD, revue, métriques et testeur seront ajoutées après exécution réelle.
Tests sur PC avec images et comptes fictifs ; aucun appel authentifié réel,
lecture privée ou manipulation du téléphone. Installation limitée à l'APK principal.

Première tentative de Red bloquée avant compilation : connexion au daemon Gradle
impossible dans le contexte d'exécution initial. Ce défaut d'infrastructure n'est
pas compté comme un Red fonctionnel. Log conservé sous
`build/reports/validation/m2-09-framing-actions/coder/` ; relance dans le contexte
PC adapté, sans accès au téléphone ni changement de source de production.

## Red fonctionnels

Deux lots exécutés avant le comportement de production correspondant :

- Contrat d'interface : **cinq tests/cinq échecs d'assertions**, 76 s ;
  `red-contract.log` et XML dans le dossier local du coder.
- Cibles et snapshots : **quatre tests/quatre échecs fonctionnels**, 63 s ;
  `red-target.log` et XML. Le snapshot de cible n'est pas encore implémenté.

Ces échecs fonctionnels sont distincts de la tentative initiale de connexion au
daemon. L'implémentation réutilise les frontières locales existantes ; aucune
évolution du protocole réseau/OpenAI n'est nécessaire.

## Premier Green

Contrat et cibles : **neuf tests/neuf succès**, sortie **0**, **2 min 14 s**,
`green-contract.log`. Le lecteur des diagnostics reproduit d'abord le refus
« Unsupported pair » de la nouvelle version, puis ses **huit tests** passent
pour 0.14.0/code26, sans élargir les couples précédemment admis. Les cas directs
JPEG, annulation et rotation ainsi que les régressions existantes restent à
exécuter avant le gel final ; aucune couverture ou validation globale n'est
déduite de ce premier lot.

## Première revue sur le gel initial

État de **335 empreintes** conservé dans `review1/source-snapshot.json` sous le
dossier local de validation. Lecture indépendante sans écriture ni build.
Défaut retenu : `ON_STOP` était traité comme une sortie arrière-plan même lors
d'une rotation, ce qui annulait un export JPEG ou une publication Lens suspendus.
Conserver seulement le flux déjà envoyé ne suffit pas au contrat ; distinguer
rotation/recréation et véritable arrière-plan sans retenir l'Activity, et tester
la recréation pendant une préparation suspendue, y compris nettoyage/recomposition.

Le premier ciblé large compile ; des anciens tests cherchent encore un texte
Galerie remplacé par l'icône accessible. Résultats et couverture restent en attente.
Les constats statiques préliminaires sont groupés avec la correction de cycle de
vie, sans modifier seuils ni exclusions. Aucun verdict final n'est déduit de cet état.

Premier ciblé large : **192 tests/dix échecs**, sortie **1**, **4 min 06 s** ;
aucune tâche JaCoCo exécutée ni rapport actuel revendiqué. Cinq attentes de
navigation portent encore sur les anciens textes, deux fixtures Lens conservent
des boîtes devenues invalides après recadrage. Trois échecs révèlent un vrai
défaut : l'original n'est pas un état observable et l'en-tête ne se met pas à jour
au premier recadrage. Les assertions existantes sont conservées pour vérifier
la correction. Un test de boîte invalide au clic doit aussi vérifier que la
nouvelle construction synchrone du snapshot traite l'erreur sans crash, comme
le faisait la préparation asynchrone précédente.

## Corrections groupées

Red de revue : **deux tests/deux échecs fonctionnels**, **71 s**, recréation
pendant un JPEG suspendu et boîte hors image. Red de présentation : **un test/un
échec**, **69 s**, Lens est sur une deuxième ligne dans le panneau de 300 dp en
police normale. Ces preuves sont distinctes des simples migrations d'assertions.

Corrections posées avant nouvelle vérification : distinguer la recréation de
configuration du vrai arrière-plan à `ON_STOP`, rendre l'original observable,
extraire la gestion de cible/clé/rectangle dans un composant dédié, et traiter
un snapshot invalide sans repli vers une autre image ni envoi. La barre est
compactée, la configuration devient défilable et sa vraie fenêtre de dialogue
est capturée sur PC. La carte ChatGPT devenue sans appel de production est
supprimée ; les contrôles partagés restent testés dans le nouveau parcours.
Formatage, nouveau Green large et mesures sont encore attendus.

Formatage et detekt du lot corrigé : sorties **0**, sans modification des règles.
Duplication ciblée : jscpd sortie **0**, **six lignes nouvelles dupliquées sur
608 analysables (0,987 %)** contre la baseline ; seuil de 3 % satisfait.
Le rapport porte sur 129 fichiers/10 078 lignes, 29 lignes dupliquées globalement
(0,288 %). Ces mesures ne remplacent pas la campagne globale finale.

Ciblé corrigé : **195 tests/un échec**, **2 min 50 s**. Les trois régressions de
revue et neuf anciens échecs passent. L'échec restant est une assertion de test
obsolète : l'absence de « Réessayer l'analyse » avait été remplacée mécaniquement
par l'absence d'Analyser, alors que cette action est maintenant toujours disponible.
La correction conserve l'absence d'état d'erreur et vérifie Analyser activé.
Pas de nouveau défaut de production identifié ni de couverture actuelle déduite.

Le test de configuration ouverte en paysage/police ×1,5 est ajouté avant la
globale prévue. Celle-ci fournira le dernier Green et le rapport JaCoCo, sans
répéter un ciblé large uniquement pour ces changements de tests. La revue du
code corrigé ne trouve plus de blocage à la lecture ; son verdict complet attend
les preuves globales et la vraie capture de dialogue.

## Première globale et mesure par composant

Commande `./scripts/quality.ps1 -BaseRef d14c599b04e902edc36eaad37445ba98b0b7721f`
dans le contexte PC adapté, sortie **0**, résumé `passed` : **442 tests Android**,
sans échec, erreur ou test ignoré. Nouveau code : 398 lignes exécutables,
98,24 % lignes/80,82 % branches ; duplication nouvelle six/608 lignes (0,987 %),
aucune violation de complexité 10/15. Les 335 empreintes restent identiques au
gel final et la vraie capture Configuration paysage ×1,5 est lisible et testée.

La moyenne ne suffit toutefois pas à déclarer le ticket conforme. Mesure avec
les mêmes `changed_lines` et `coverage_metrics`, par packages effectifs :

| Composant | Global lignes/branches | Nouveau lignes/branches |
| --- | --- | --- |
| Photo/UI | 98,88 % / 83,87 % | 98,62 % / 82,52 % |
| Information/partage | 97,88 % / 80,21 % | 94,74 % / 66,67 % |
| UI ChatGPT | 97,52 % / 81,56 % | 93,75 % / 50 % |

Complément de tests requis pour les deux derniers nouveaux périmètres, sans
changer la production ou les seuils : propriété des aperçus lors d'un refus
d'adoption, régions invalides sans encodage/repli, export exact du préparateur
par défaut et rejet d'un résultat tardif quand le document est fermé mais le
ViewModel encore vivant. Le reviewer confirme que ces cas couvrent de vrais
invariants, sans tests artificiels des méthodes générées d'une data class.

Ce premier état global et ses mesures sont conservés dans
`review1/first-global/` du dossier local. Il n'est pas présenté comme une
conformité complète des nouveaux composants ; nouveau Green et mesure attendus
après ces tests. Aucun testeur final ni déploiement n'est revendiqué à ce stade.

## Complément de couverture et revue finale

Tests uniquement, production inchangée : huit cas ajoutés sur l'adoption d'aperçus,
les régions invalides, le préparateur par défaut et le contexte de document fermé.
Ciblé **204 tests sans échec, erreur ou test ignoré**, sortie **0**, **2 min 25 s** ;
JaCoCo réellement exécuté. Archives `coder/coverage-jacoco.xml`,
`coder/coverage-components.json`, `coder/coverage-test-counts.json` et rapport
`coder/coverage-rapport.md`. Formatage des tests : sortie 0.

| Nouveau composant | Lignes exécutables | Lignes | Branches |
| --- | --- | --- | --- |
| Photo/UI | 363 | 98,62 % | 82,52 % |
| Information/partage | 19 | 100 % | 100 % |
| UI ChatGPT | 16 | 93,75 % | 100 % |

Ticket nouveau : 398 lignes, **98,49 %/84,28 %**. Aucun seuil ou exclusion modifié,
aucun test artificiel d'égalité/hashCode de data class. La branche ChatGPT vérifie
le prédicat de document courant indépendamment de la génération : contexte objets
fermé, ViewModel encore vivant, réponse non annulable tardive rejetée.

Gel final de **336 empreintes**, seules trois sources de tests modifiées depuis
la première globale. Revue indépendante finale **conforme**, aucun blocage
résiduel ; preuve réelle de configuration paysage/police ×1,5 et gardes relues.
Dernière campagne globale en cours sur cet état, puis validation du testeur et
installation ; aucun succès de ces étapes n'est encore anticipé.

## Dernière globale conforme

Même commande et baseline, sortie **0**, résumé `passed` : **450 tests Android**,
sans échec, erreur ou test ignoré, **76 tests de scripts** et **quatre PC**.
Android global **97,02 % lignes/86,29 % branches**, nouveau code **98,49 %/84,28 %**
sur 398 lignes exécutables. Scripts 94,35 %/88,52 %, PC 100 %/100 % ; les
branches nouvelles des scripts et le nouveau code PC sont non mesurables dans
ce diff, pas déclarés artificiellement à 100 %.

| Composant final | Global lignes/branches | Nouveau lignes/branches |
| --- | --- | --- |
| Photo/UI | 98,88 % / 83,87 % | 98,62 % / 82,52 % |
| Information/partage | 98,14 % / 83,75 % | 100 % / 100 % |
| UI ChatGPT | 97,52 % / 82,42 % | 93,75 % / 100 % |

Périmètres explicites : préfixes de packages avec sous-packages inclus ; Photo/UI
inclut donc le rendu Markdown. Le recalcul du testeur sur le package exact
`dev/blamera/ui`, sans Markdown, donne aussi 98,92 % lignes/82,09 % branches.
Les plages nouvelles de Photo/UI n'incluent aucun nouveau code Markdown.

Duplication nouvelle six/608 lignes (0,987 %), globale 0,288 %. Aucune violation
de complexité 10/15, aucun seuil ou exclusion modifié. Audits : 98 paquets
Maven/132 entrées de licences et 35 paquets PC/35 licences, aucune vulnérabilité
active dans les périmètres configurés. Mesure individuelle PhotoTargetActions :
41/52 branches (78,85 %), explicitement distincte de la conformité des composants.
Suivi secondaire M2-09-COV-01 pour ses branches restantes ; aucune exclusion.

Rapports archivés avant les exécutions du testeur : `quality-final/`,
`jacoco-global.xml`, `junit-global/`, `junit-counts.json`, `component-metrics.json`,
journal `quality.log`. Les noms des fichiers JUnit archivés sont comparés aux
originaux pour éviter un mélange de suites anciennes. **336 empreintes** vérifiées,
production identique au gel relu ; les compléments précédents portent sur trois
fichiers de tests seulement.

APK principal `dist/blamera-0.14.0-debug.apk`, package `dev.blamera`, version
0.14.0/code26 vérifiée dans les métadonnées Gradle, **458 324 887 octets**,
SHA-256 `4126579d600e6a1d1c0d6596e304c3a044027f1615bbbdc1f3a041a23b822c3a`.
Copie et original identiques ; métadonnées `apk.json`. Signature, ressources et
parcours en validation finale indépendante ; installation prévue après ce verdict.

## Testeur et correction finale de format

Validation indépendante : **324 tests Android ciblés**, sortie **0**, **1 min 59 s**,
sans échec, erreur ou test ignoré ; **huit tests du lecteur**, verts. Les 450 XML
globaux, les métriques de chaque nouveau composant et les captures sont vérifiés.
APK build/dist, certificat habituel, code26, trois permissions, sauvegardes
désactivées avec 18 exclusions et documentation/liens conformes.

Le testeur signale deux lignes vides ajoutées en fin des scripts Python du lecteur.
Le coder les supprime sans autre changement : AST Python avant/après identiques,
newline finale conservée, `git diff --check` à 0. Preuve `coder/format-eof.json`.
Les SHA de 336 fichiers sont contrôlés ; seuls ces deux fichiers Python changent
par rapport à l'état testé, Android et APK restant strictement identiques. Snapshot
avant format dans `review1/validated-before-format-source-snapshot.json`, snapshot
final avec `formatOnlyDelta`. Aucun test comportemental n'est relancé pour ces
seuls espaces ; la vérification de format est indépendante des preuves de comportement.

Le testeur confirme le correctif de format, les 336 empreintes actuelles et le
verdict final **conforme sur PC**. Les 38 assets/bibliothèques natifs sont identiques
à 0.13.0. Aucun défaut bloquant reproduit, aucun accès téléphone ou compte réel
effectué par les agents de validation.

## Préparation de l'installation

APK, taille, empreinte et 336 sources vérifiés avant le déploiement. Le préflight
ADB ne retrouve pas le Xiaomi au moment d'installer ; la commande s'arrête avant
`install`. **Aucune installation tentée**, aucun changement du téléphone. Reçu
local `install-preflight.json` ; demande de reconnexion USB adressée à l'utilisateur.
L'installation attendait ce prérequis matériel ; la reprise est documentée ci-dessous.

## Installation réelle après reconnexion

Après confirmation de rebranchement par l'utilisateur : un seul Xiaomi
`2407FPN8EG` prêt. Version 0.14.0/code26, taille/SHA-256 de l'APK et les **336
empreintes** revérifiés avant l'opération. `adb -d install -r` sur le seul APK
principal : sortie **0**, **Success**. Début le 31 octobre 2026 à
**18:20:33,321 UTC**, fin **18:20:53,701 UTC**. Reçu local `install-result.json`,
empreinte `4126579d600e6a1d1c0d6596e304c3a044027f1615bbbdc1f3a041a23b822c3a`.

Aucun lancement automatique, autre modification du téléphone, test APK, test
instrumenté ou lecture privée. Aucun code modifié ni test relancé pour ce
déploiement. La [recette manuelle](../tests/m2-photo-actions-recette.md) est demandée :
cadrage sans sélection, source Objet, actions regroupées, navigation et réglages,
rotation et retour à l'original. Le fonctionnement réel sur le Xiaomi reste
à confirmer par l'utilisateur ; l'installation seule ne vaut pas validation.
