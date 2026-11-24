# Finaliser ChatGPT au retour dans Blamera

30 octobre 2026. Baseline : `91206a4aa1c1f27df91d310bca793b3ae11cd692`.
Correctif 0.10.3/code21 ; revue, qualité globale et testeur conformes sur PC,
APK principal installé. Finalisation réelle sur Xiaomi en attente.

## Observation et décision

La preuve réelle 0.10.2 est dans
`build/reports/validation/m2-chatgpt-network/app-events-0.10.2.json`, composée
uniquement d'événements fixes du PID Blamera. L'application est RESUMED lorsque
DISCOVERY réussit avec HTTP200. Elle est PAUSED après ouverture du navigateur ;
le callback valide arrive, puis TOKEN échoue à OPEN_OUTPUT avec catégorie DNS,
sans statut HTTP, avant le retour RESUMED environ 3,8 secondes plus tard.

Cette différence de contexte est établie ; la politique système exacte qui
produit l'erreur ne l'est pas. La documentation du
[cycle de vie Android](https://developer.android.com/guide/components/activities/activity-lifecycle)
et des [processus mis en cache](https://source.android.com/docs/core/perf/cached-apps-freezer)
justifie de prendre en compte le retour au premier plan, mais ne prouve pas
l'activation d'un mécanisme précis sur ce Xiaomi. Aucun réglage, état de freezer,
DNS ou journal d'autre application n'est lu ou modifié.

Après réception et validation du callback et sauvegarde du client émis, attendre
RESUMED de l'activité Blamera avant de finaliser l'échange. Le code et le PKCE
restent uniquement en mémoire. Le navigateur indique déjà de revenir dans
Blamera ; ce retour devient une condition explicite avant le POST de finalisation.
Aucun changement du formulaire, des scopes, de l'URI, des certificats ou du service.

## Critères du correctif

- Le retour reçu pendant PAUSED ne déclenche aucun POST token, validation ou catalogue.
- RESUMED débloque une seule finalisation, avec le code et les paramètres OAuth inchangés.
- Une phase WAITING_APP_RETURN explique l'attente. La limite globale de cinq minutes
  inclut cette attente et l'annulation reste disponible.
- Annulation, fermeture et expiration interdisent un POST tardif après reprise.
- La présence est partagée avec le ViewModel retenu : une rotation ne garde pas
  une ancienne activité et ne bloque ni ne relance une nouvelle connexion.
- Pas de service permanent, permission, réglage téléphone ou DNS alternatif.
- Les gardes de crédits et l'absence d'envoi automatique d'image sont conservées.

## TDD et validation

Red exécuté : `build/chatgpt-foreground-red.log`, sortie **1**, trois tests/trois
échecs fonctionnels. Ils démontrent le POST déjà émis en pause, l'absence d'attente
annulable et le câblage de factory ne bloquant pas lorsque l'activité est PAUSED.
Compilation réussie ; aucun échec d'infrastructure compté comme Red.

Deux régressions ciblées supplémentaires ont chacune échoué fonctionnellement
avant correction : reprise après cinq minutes sans livraison du timer
(`build/chatgpt-foreground-elapsed-red.log`), et annulation au moment de la phase
EXCHANGING_CODE (`build/chatgpt-foreground-edge-red.log`). La première ajoute une
vérification du temps monotone ; la seconde vérifie à nouveau la génération
immédiatement avant l'échange.

Un Red séparé du watchdog pendant l'attente échoue fonctionnellement (un test/un
échec, `build/chatgpt-foreground-timeout-red.log`), puis est traité sans prolonger
le budget. Le lecteur PC reproduit d'abord
le refus de la paire 0.10.3/code21 (huit tests, une erreur de version attendue),
puis ses huit tests passent.

Le premier Green ciblé donne 20 succès sur 21 tests. Le dernier échec est une
hypothèse de fixture : ActivityScenario.recreate() depuis STARTED peut passer par
RESUMED sous Robolectric. La fixture vérifie désormais pause/reprise explicitement,
puis rotation et nouvelle pause sur la même gate ; elle conserve ses assertions
de blocage. Un passage ultérieur de 83 tests a révélé 16 échecs de fixtures :
15 utilisaient l'horloge Android dans des tests JVM et un lisait l'état avant la
fin du Job. Les fixtures injectent une horloge fixe et attendent cette fin ;
les assertions de comportement sont conservées.

Green final : `build/chatgpt-foreground-green-final.log`, sortie **0**, 54 secondes,
**83 tests sans échec ni erreur** ; XML dans
`app/build/test-results/testDebugUnitTest/`. Lecteur : huit tests verts ; formatage,
detekt ciblé et `git diff --check` : sorties 0.

JaCoCo ciblé : backend 904/933 lignes (96,89 %) et 489/546 branches (89,56 %) ;
UI ChatGPT 323/333 lignes (97,00 %) et 125/142 branches (88,03 %). Nouveau code
contre la baseline ci-dessus : 50 lignes exécutables, lignes et branches 100 %.
Le run ciblé n'exécute pas toute la baseline et ne prouve donc pas sa couverture
globale ; celle-ci sera mesurée par la commande qualité complète.

Un point propre à cette attente est aussi traité : après une pause longue, la
livraison du callback de watchdog peut être retardée. Le début de connexion est
donc mémorisé à l'appui avec une horloge monotone injectable et le temps écoulé
est revérifié après RESUMED, avant de rendre la main pour le POST. Le test avance
l'horloge sans livrer l'alarme et exige l'absence d'échange tardif. Cela reste dans
la gate retenue, sans modifier les timeouts HTTP/callback ni un réglage du téléphone.

Revue indépendante : conforme, sans blocage. Le reviewer a relu le diff gelé,
les Red et les 83 XML verts ; il confirme le câblage réel, le maintien du protocole
et des gardes, et la protection contre l'échange tardif. Revue en lecture seule,
sans appel au compte ni accès au téléphone.

## Qualité globale

Commande réellement exécutée :

```powershell
./scripts/quality.ps1 -BaseRef 91206a4aa1c1f27df91d310bca793b3ae11cd692
```

Sortie **0**, tous les contrôles configurés conformes. Archives dans
`build/reports/validation/m2-chatgpt-foreground/` : `quality.log`,
`quality-final/summary.json`, `jacoco-global.xml`, `junit-global/` et
`junit-counts.json`. **311 tests Android, 76 scripts et quatre PC**, sans échec.
Les 265 empreintes de sources/configuration du snapshot sont inchangées.

| Mesure | Résultat |
| --- | --- |
| Mobile global, lignes / branches | 96,70 % / 87,15 % |
| Nouveau mobile, lignes / branches | 100 % / 100 %, 50 lignes exécutables |
| Duplication nouvelle mobile | 0/81 lignes ; globale 0,230 % |
| Complexité cyclomatique / cognitive | Aucune violation des limites 10/15 |
| Scripts globaux, lignes / branches | 94,35 % / 88,52 % |
| Nouveau script | Lignes 100 % ; aucune branche nouvelle mesurable |
| PC global, lignes / branches | 100 % / 100 % ; aucun nouveau code mesurable |
| Audits et licences | 97 paquets Maven / 131 entrées, 35 PyPI / 35 entrées |

Aucune vulnérabilité active retournée dans ces inventaires et avis consultés ;
ce résultat n'est pas une garantie hors des périmètres documentés. Aucune règle,
exclusion, dépendance ou permission modifiée pour faire passer les contrôles.

## Validation indépendante et installation

Testeur conforme sur PC : 83 tests ciblés et huit du lecteur, sorties 0 ;
annulation, rotation, reprise unique et expiration sans livraison du timer validées.
Rapport : `build/reports/validation/m2-chatgpt-foreground/tester/report.md`.
Package `dev.blamera`, version 0.10.3/code21, activité privée `fullUser`, permissions
inchangées. Les 37 assets/bibliothèques sont identiques à 0.10.2 ; les 265 empreintes
de sources/configuration sont inchangées avant et après validation.

APK `dist/blamera-0.10.3-debug.apk`, **457 851 229 octets**, SHA-256 :

```text
d137229725e8851f0c295c8a10e1e6e3dfc5faa54c0dbce5af88ef8e9d0620b7
```

Certificat debug inchangé :
`eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555`.
Installation du seul APK principal sur le Xiaomi : `adb -d install -r`, sortie 0
« Success », le 30 octobre 2026 de 20:06:59 à 20:07:18 UTC. Reçu local
`build/reports/validation/m2-chatgpt-foreground/install-result.json`.
Aucun lancement automatique ni autre mutation du téléphone.

L'utilisateur doit ouvrir l'essai, terminer l'autorisation dans le navigateur puis
revenir manuellement dans Blamera à la page locale de retour. Les modèles ou
l'erreur exacte restent à observer. Aucun envoi d'image nécessaire pour cette
étape ; vérification humaine des crédits désactivés puis image synthétique restent
des étapes distinctes. Aucun appel authentifié sur PC ; M2-06 reste ouvert.

## Résultat réel 0.10.3

L'essai humain atteint désormais le chargement des modèles, puis signale une
réponse invalide/incomplète. La trace locale `app-events-0.10.3.json` confirme
WAITING_APP_RETURN puis RESUMED, TOKEN HTTP200, vérification JWKS HTTP200, puis
MODELS HTTP200 et refus à READ_BODY/PROTOCOL. La finalisation OAuth et la
vérification ont réussi dans cet essai ; le catalogue et l'inférence restent
incomplets. Le garde de 256 Kio coupe la lecture du catalogue avant son parseur.
La cause système initiale n'est toujours pas attribuée à une politique précise.
[Correctif borné du catalogue](m2-chatgpt-catalog.md).
