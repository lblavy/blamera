# Analyse commune — validation M2-10

Version cible 0.15.0/code28. Baseline
`c7f859116d599e646918c088df3a002b2fad8769`.
Revue indépendante, tests ciblés, contrôles globaux et testeur conformes sur PC.
Version installée ; validation réelle Pl@ntNet encore ouverte.

[Contrat](../specs/joint-plant-analysis.md), [clé](../plantnet.md),
[recette](../tests/m2-joint-plants-recette.md).

Les tests logiciels utiliseront des réponses et clés synthétiques sur PC. Aucun
appel authentifié réel ni photo personnelle via les outils. La réussite réseau
sur Xiaomi et la précision botanique restent à valider séparément par l'utilisateur.

## État après revue et compléments

Gel de 368 fichiers exact, baseline ci-dessus. Revue indépendante conforme, aucun
blocage résiduel identifié. Finale ciblée : **386 tests**, zéro échec, erreur ou
omission, sortie 0 en **1 min 37 s** ; vraie tâche JaCoCo exécutée.

| Nouveau code du composant | Lignes | Branches | Lignes exécutables |
| --- | --- | --- | --- |
| Botanique | 99,45 % | 89,52 % | 365 |
| Photo/UI, sous-paquets inclus | 100 % | 90,79 % | 148 |
| Partage/export image | 100 % | 94,44 % | 20 |
| UI ChatGPT | 100 % | 100 % | 10 |
| Stockage partagé | 100 % | aucune branche nouvelle mesurable | 23 |
| Ticket | 99,65 % | 90,45 % | 566 |

Ces mesures proviennent de la campagne ciblée, pas de la future globale Android.
Le stockage complet atteint 96,67 % lignes/88,24 % branches, le partage complet
98,97 %/85,51 %. Duplication nouvelle mesurée 11/832 lignes (1,32 %), sous le
seuil 3 %. Seuils et exclusions inchangés. Les résultats complets finaux sont
dans `coder/final/` et `coder/component-metrics-final.json`, sous le répertoire
local de validation ; rapports UI et TDD dans `ui-coder/rapport-ui.md` et
`coder/tdd-evidence.json`. Les limites matérielles ne sont pas déclarées conformes.

Les deux écarts de couverture ont été corrigés par quatre tests de frontières
et leur helper JCA, sans changer la production : alias effectivement demandés,
enveloppe historique indépendante, refus du backend sans fuite, JPEG écrit octet
par octet, limites et décodage invalide sans recyclage de la source. Le reviewer
les juge utiles et non triviaux. Le provider synthétique est retiré en `finally`.
Une compilation du helper utilisait un constructeur Provider absent du stub
Android : seul le helper a été adapté, sans revendiquer de Red comportemental.

## Campagne globale et APK

`scripts/quality.ps1 -BaseRef c7f859116d599e646918c088df3a002b2fad8769` :
sortie 0, synthèse `passed`, aucune étape en échec. **512 tests Android**, 76 tests
des scripts et quatre tests PC réussis. Les fixtures volontairement mauvaises
rejetées dans le journal prouvent les outils ; elles ne sont pas des défauts du ticket.

Android global : **97,34 % lignes/86,52 % branches** ; nouveau code
**99,65 %/90,45 %** sur 566 lignes exécutables. Mesures des cinq composants
confirmées séparément dans `component-metrics.json`. Scripts Python 94,35 %/88,52 %,
nouvelle ligne 100 %, aucune branche nouvelle ; socle PC 100 %/100 %, sans nouveau code.
Duplication nouvelle **11/832 lignes (1,32 %)**, globale 33/10885 (0,303 %).
Aucune violation des limites de complexité 10/15 ni modification de seuil ou
d'exclusion. Audits : 98 paquets Maven/132 entrées de licences, 35 paquets PC/35
licences, aucun avis actif retourné. Les suivis M2-08-COV-01/M2-09-COV-01 restent distincts.

Les 368 sources correspondent au gel. Rapports globaux préservés dans
`quality-final/`, 512 résultats dans `junit-global/`, JaCoCo dans
`jacoco-global.xml`, sous le répertoire local de validation.
APK : `dist/blamera-0.15.0-debug.apk`, package `dev.blamera`, code28,
458445762 octets ; SHA-256
`ea8dc4b5d040c6e6b8270665e0a6be2dac307b220a1970025ab0354a6a568703`.

## Testeur indépendant et installation

Testeur conforme sur PC : **141 tests ciblés**, zéro échec, erreur ou omission,
sortie 0 en 81,95 s ; lecteur huit tests verts. Il recompte les 512 résultats de
la globale et recalcule les métriques par composant sans défaut, vérifie les
368 empreintes après exécution et confirme l'égalité des hashes APK build/dist.

Audit du paquet : certificat v2 habituel, trois permissions, sauvegarde désactivée
et 18 exclusions, activité de diagnostic privée/fullUser (valeur AAPT 13), aucune
instrumentation, 38 assets/bibliothèques identiques à 0.14.1, nouveau logo présent
dans les ressources. Aucun blocage reproduit, aucune source modifiée ni accès à
un appareil/compte/service réel. Rapport local `tester/report.md`.

Précontrôle du 1er novembre à 00:35, heure de Paris : aucun téléphone prêt,
aucune installation tentée à ce stade. Relevé local `install-pending.json`, avec
l'empreinte de l'APK validé, conservé comme historique.

Après reconnexion, mise à jour du seul APK principal par `adb -d install -r` :
sortie 0 « Success », terminée le **1er novembre 2026 à 10:36:49, heure de Paris**
(09:36:49 UTC). Reçu local `install-result.json`, même version 0.15.0/code28 et
même SHA-256 `ea8dc4b5d040c6e6b8270665e0a6be2dac307b220a1970025ab0354a6a568703`.
Les 368 empreintes de sources sont inchangées ; données conservées, aucun
lancement automatique ni autre mutation du téléphone. L'utilisateur ouvre
l'application et saisit sa clé lui-même.

Réseau réel, conservation de clé dans le Keystore matériel et précision botanique
restent à confirmer par l'utilisateur. Le ticket reste ouvert pour la recette,
et M2-10-QUAL-01 suit l'évaluation statistique future.

## Premières preuves TDD

Artefacts locaux sous `build/reports/validation/m2-10-joint-plants/coder/`.
Premier Red du parser : deux tests exécutés, deux échecs fonctionnels, sortie 1
en 1 min 06 s. Puis lot commun parser/transport/configuration/quota : six tests,
quatre échecs fonctionnels et les deux tests parser désormais verts, sortie 1
en 1 min 01 s. Ces résultats ne constituent pas la validation finale.

Deux tentatives de fixture HTTP n'ont pas compilé (module HttpServer non exposé
puis adaptation d'une signature de test) : conservées séparément, elles ne sont
pas présentées comme une preuve Red. La fixture réseau utilise ensuite un serveur
loopback PC, sans requête publique ni vraie clé.

Red de l'état botanique/export et de l'isolation du chiffrement : 11 tests,
cinq échecs fonctionnels attendus, six tests précédents verts, sortie 1 en 49 s.
Une incompatibilité de constructeur Kotlin a été corrigée séparément avant ce
Red ; elle ne constitue pas une preuve comportementale.

Premier Green cœur : 31 tests réussis, sortie 0 en 1 min 13 s, JaCoCo réellement
exécuté. Il inclut les tests botaniques, du stockage historique et de l'export.
Mesure intermédiaire botanique : 233/259 lignes (89,96 %), 148/212 branches
(69,81 %). Ce résultat n'est pas encore conforme : cas d'erreur à compléter et
complexité de la fonction de démarrage à réduire avant revue finale. Les rapports
de cette étape restent dans `coder/green-first/`, distincts des futurs rapports finaux.

Complément Red frontières et consigne : 21 tests, quatre échecs fonctionnels,
sortie 1 en 55 s. Les protections de transport supplémentaires passent déjà ;
les échecs portent sur quota en mémoire, chargement initial de clé, journal
corrompu et enrichissement de la consigne. Régression de capture pendant la
relecture de clé : un test, échec fonctionnel attendu, sortie 1 en 45 s.

Red de l'intégration : sept tests, sept échecs fonctionnels, sortie 1 en 72 s
(preuves `ui-coder/`). Un second cas de composition vérifie que sans indice la
consigne existante reste exactement conservée. Les deux agents passent ensuite
au Green sur leurs périmètres séparés, avec campagne ciblée commune prévue.

## Première revue indépendante

Le gel initial comporte 363 fichiers. Le premier essai commun n'a pas atteint les
assertions : deux imports de helpers locaux invalides dans des tests ont été
retirés, sans changement de production ni d'assertion. Cette compilation échouée
est archivée séparément ; aucun Green final ni couverture finale n'en sont déduits.

Trois corrections fondées retenues : empêcher le démarrage botanique après un
arrière-plan durant le chargement de photo ; ne pas rejeter une espèce valide
à cause de ponctuation d'un nom français ; déplacer le commit du quota à une
frontière IO. Régressions puis corrections en cours dans un même cycle de revue.
Le gel initial est conservé sous `review1/`, avant les nouvelles preuves finales.

Le second essai commun a exécuté 376 tests : trois échecs, sortie 1 en 4 min 18 s ;
JaCoCo non exécuté. Un scénario d'orchestration réutilisait une réponse annonçant
un quota restant nul : sa fixture utilise maintenant un quota positif, tandis que
les tests dédiés au quota nul restent inchangés. Deux cas d'interface ont rencontré
une attente Espresso après une capture PNG de la fenêtre : diagnostic en cours,
aucune assertion ni limite de temps affaiblie. Les autres cas de rotation,
réenvoi sur même cible et snapshot pendant export passent dans cette campagne.

Red groupé de revue : neuf tests, six échecs, sortie 1 en 2 min 10 s. Après examen
indépendant des preuves, quatre sont des échecs fonctionnels utiles : capture en
chargement après STOP, deux noms français et une frontière IO. Trois garde-fous
restent verts. Un autre échec vient d'une précondition incorrecte de fixture : le
test de clé en chargement exigeait un snapshot absent alors que le contrôleur
publie légitimement sa progression avant d'attendre la clé. Cet échec précédait
STOP et ne prouve pas une fuite réseau ; le test corrigé sera un garde-fou, sans
reconstituer de Red. Le dernier échec est le diagnostic d'interface, encore en
attente de mesure/layout ; déplacer la capture PNG n'a pas suffi et cette première
hypothèse est écartée. Ces deux limites sont distinctes du Red comportemental.

Le passage commun après corrections a exécuté 382 tests, trois échecs, sortie 1
en 1 min 58 s. Les corrections de production sont relues favorablement ; l'échec
de précondition de fixture est corrigé séparément. Lecture détaillée des XML :
après contrôle de l'horloge, les deux dialogues échouent sur un champ non placé
dans l'arbre de test, et non sur le précédent délai Espresso. Cette tentative
ne prouve donc pas une boucle de production. Le diagnostic est isolé avec retour
à l'horloge normale et bornage responsive de la zone scrollable, sans retirer de
champ ni affaiblir l'accessibilité. Aucune conformité d'interface n'est anticipée.

L'isolé avec hauteur bornée a exécuté cinq tests : trois cycles de vie verts,
deux dialogues en échec, sortie 1 en 3 min 02 s. La largeur de fenêtre est ensuite
stabilisée, à la suite du [signalement Robolectric #8460](https://github.com/robolectric/robolectric/issues/8460).
Les contrôles et l'horloge normale sont conservés ; ce signalement ne démontre
pas un défaut sur le Xiaomi. Nouveau ciblé isolé en cours avant le prochain lot commun.

## Préparation de livraison

Retour utilisateur après installation le 1er novembre : « ça a l'air de
fonctionner, même après redémarrage ». Parcours essentiel et conservation de
clé acceptés qualitativement. Les logs Blamera retenus ensuite confirment
reprise de session, HTTP200 ChatGPT et trois analyses CPU ; la 0.15.0 ne produit
pas d'événement botanique. Cette lecture ne prouve ni identification botanique
réussie ni précision par espèce. Le [suivi UI](m2-key-visibility.md) documente
la lecture bornée, l'œil demandé et les boutons homogènes.

Précontrôle ADB : aucun Xiaomi détecté à 22:16 UTC le 31 octobre 2026 ; demande
de reconnexion envoyée. L'absence de téléphone ne bloque pas les tests PC.
Le logo officiel est téléchargé et inspecté ; provenance, empreinte et crédit
dans le [guide Pl@ntNet](../plantnet.md). Aucun modèle ML n'a été remplacé.
