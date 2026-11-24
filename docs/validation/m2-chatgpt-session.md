# Connexion ChatGPT persistante — validation

31 octobre 2026. Version 0.12.0/code24 : revue, contrôles globaux et validation
indépendante conformes sur PC ; APK principal installé sur le Xiaomi.
Parcours essentiel de fermeture/réouverture validé par l'utilisateur.
Baseline : `fc6b5578a3b16aadab61ff399ff97df0f35b644d`.
[Décision et critères](../decisions/0010-chatgpt-session-retention.md),
[recette](../tests/m2-chatgpt-session-recette.md).

Retour humain 0.11.0 : fonctionnement principal jugé correct, mais reconnexion
nécessaire après fermeture/réouverture uniquement. Le code conserve les données
entre objets et rotations ; les jetons disparaissent à la fermeture. Le diagnostic
synthétique a sa propre activité/session. Aucun défaut de session entre objets
n'est déduit de ce retour.

Deux périmètres d'implémentation : stockage chiffré opaque et tests crypto d'une
part ; reprise OAuth/préférence de modèle/host et tests de parcours d'autre part.
Créneaux Gradle sérialisés, aucun Kotlin incomplet pendant le build de l'autre
périmètre. Revue indépendante sur sources gelées puis testeur ; la qualité globale
et le déploiement seront exécutés seulement sur cet état validé.

Les commandes Red/Green et métriques exécutées sont détaillées ci-dessous.
Les opérations de test sont locales avec fixtures fictives ; aucun secret du
compte, appel authentifié réel sur PC ou lecture privée du téléphone.

Premières preuves stockage : `build/chatgpt-session-store-red.log`, un test/un
échec fonctionnel, 55 s ; `build/chatgpt-session-cipher-bounds-red.log`, cinq
tests/quatre échecs, 22 s. Le run mixte suivant valide les cinq tests crypto et
expose l'écriture interrompue qui détruit l'ancien record (un échec sur huit,
1 min 15 s, `build/chatgpt-session-atomic-red.log`). Ces essais utilisent uniquement
des valeurs fictives. L'implémentation atomique et les cas d'erreur IO/annulation
sont en cours avant le Green complet.

Le lecteur PC reproduit le refus de la nouvelle paire 0.12.0/code24 (huit tests,
une erreur attendue), puis ses huit tests passent ; journaux
`build/chatgpt-session-reader-red.log` et `build/chatgpt-session-reader-green.log`.

Red intégration : `build/chatgpt-session-integration-red.log`, sortie 1, 26 s,
six tests/six échecs d'assertions fonctionnelles (trois gateway : session ou reprise
absente ; trois ViewModel : restauration, modèle et reprise indépendante absents).
Archives dans `build/reports/validation/m2-chatgpt-session/coder/red/`.
Un premier essai a été interrompu car un stub ne produisait pas l'événement
attendu par la fixture ; il n'est pas compté comme Red. La fixture attend désormais
un état précédé d'une assertion et possède une attente bornée.

## Module stockage gelé

Green final `build/chatgpt-session-store-green-final.log`, sortie **0**, 35 s,
**17 tests sans échec ni erreur**. Formatage et detekt ciblés : sorties 0.
Archives originales dans `build/reports/validation/m2-session-store/` :
`jacoco-targeted.xml`, `junit/`, `green.log` et `metrics.json` ; copie des preuves
du run dans `build/reports/validation/m2-chatgpt-session/store/`.

| Périmètre ciblé | Lignes | Branches |
| --- | --- | --- |
| Package stockage | 133/144, 92,36 % | 30/34, 88,24 % |
| Chiffrement | 34/34, 100 % | 13/14, 92,86 % |
| Store chiffré | 26/26, 100 % | 2/2, 100 % |
| Fichier atomique | 39/40, 97,50 % | 11/14, 78,57 % |
| Factory Android | 4/5, 80 % | Aucune branche mesurable |
| Fournisseur de clé Android | 16/25, 64 % | 4/4, 100 % |

Les neuf lignes non exécutées du fournisseur concernent les bindings de défaut
du vrai Android Keystore/keygen. Les tests utilisent le vrai JCE avec clés de test
et paramètres Android injectés ; ils ne prouvent pas le fonctionnement matériel
du Xiaomi. Aucun seuil ni exclusion changé ; les mesures partielles par fichier
ne sont pas déclarées conformes individuellement.

Un premier Green de 17 tests avait trois échecs : deux lors du remplacement avec
AtomicFile/Robolectric et un test trop strict exigeant une cause nulle. Kotlin
peut ajouter une copie de l'exception générique comme cause lors du stacktrace
recovery ; le test vérifie maintenant que toute la chaîne reste générique et
exclut le Throwable d'origine. Aucun secret réel n'a été exposé.

Le probe JDK local confirme que renameTo peut remplacer la cible sur cet hôte :
Windows n'est donc pas désigné comme cause certaine des deux échecs AtomicFile.
Le backend final utilise un équivalent atomique contrôlable : fichier `.new`
chiffré, synchronisation du flux puis déplacement NIO atomique avec remplacement,
sans repli non atomique. Une erreur de commit est remontée, l'ancien record est
préservé et le temporaire nettoyé. L'ordre initial incorrect des arguments Gradle
d'un Green a produit un échec CLI de deux secondes, jamais compté comme Red.

La revue indépendante de ce module gelé est en cours pendant le développement
de l'intégration ; seul le périmètre core/gateway/ViewModel continue d'écrire.

## Premier Green d'intégration

`build/chatgpt-session-integration-green.log`, sortie **0**, 52 s, **32 tests sans
échec** : 20 gateway existants, trois reprise, six récupération/concurrence et
trois redémarrage ViewModel. Les cas comprennent préférence sans modification
de savedAt, deux clients partageant le renouvellement, annulation pendant sauvegarde,
préflight hors réseau et changement de document pendant reprise. La validation
du schéma, la frontière chiffrée réelle sur PC, l'UI et les mesures finales restent
en cours ; ce run intermédiaire n'est pas présenté comme une campagne complète.

Revue indépendante du module stockage gelé : **conforme, aucun blocage**. Le
reviewer a relu les 17 XML et Red, validé chiffrement/bornes, erreurs génériques,
fichier privé, fsync/remplacement atomique et absence de repli. Aucun test relancé,
écriture, compte ou appareil utilisé.

Point d'orchestration retenu pour la revue globale : un save déjà lancé peut
committer avant un retour annulé. Le core doit sérialiser/compenser ce cas, sans
perdre un grant rotatif valide ni ressusciter une connexion déconnectée. Un
marqueur privé non secret « session bloquée » est aussi prévu avant revoke/clear :
si le fichier ne peut pas être supprimé, la reprise reste interdite au redémarrage.
Seule une nouvelle connexion validée et sauvegardée peut débloquer la session.
Ces cas ont été transmis au coder ; tests et intégration restent en cours.

Red de déconnexion durable : `build/chatgpt-session-logout-red.log`, sortie **1**,
23 s, un test/un échec fonctionnel sur l'ancien payload encore repris malgré
l'échec de suppression. Le marqueur est maintenant implémenté pour le seul
parcours persistant, avant révocation/suppression ; son déblocage exige une
nouvelle connexion validée puis un commit réussi. Le Green de ce cas et de la
vraie écriture chiffrée déjà engagée pendant annulation/déconnexion est en cours.

Red de présence avant renouvellement :
`build/chatgpt-session-refresh-foreground-red.log`, sortie **1**, 35 s, deux
tests/deux échecs d'assertions. Ils exposent un POST pendant PAUSED après préflight
et l'absence de barrière monotone. La nouvelle barrière utilise un budget local
à l'opération, distinct du départ de connexion ; le test avance l'horloge d'une
heure. Formatage et detekt ciblés passent après cette implémentation. Le run
ciblé complet compte/stockage/photo/caméra avec JaCoCo est en cours avant gel.

Premier ciblé complet : `build/chatgpt-session-green-all.log`, sortie **0**,
1 min 05 s, **191 tests sans échec** et rapport JaCoCo produit ; format/detekt à 0.
La couverture nouvelle mesurée n'est pas encore suffisante : ticket 95,39 % lignes
/ 79,87 % branches, core seul 95,90 % / 78,79 %. TrialGatewaySession atteint
67,35 % des branches. Les cas métier 401/remplacement de jeton, absence de session,
mode éphémère, annulation et fermeture sont complétés avant gel ; aucune exclusion
ou baisse de seuil et aucun arrondi de 79,87 % en conformité.

Green suivant : `build/chatgpt-session-green-final.log`, sortie **0**, 1 min 10 s,
**195 tests sans échec**. Nouveau core : 97,20 % lignes / 85,94 % branches ; ticket
entier 96,39 % / 86,21 %. Duplication nouvelle mesurée à 0/862 lignes. Le composant
TrialGatewaySession atteint 100 % lignes / 86,67 % branches, codec 100 % / 88,75 %,
persistance 98 % / 100 %, host 100 % / 100 %, ViewModel 98,65 % / 83,10 %.

Le test du vrai adaptateur SharedPreferences du marqueur est complété avant gel
(73,33 % lignes dans cette mesure intermédiaire). Le module stockage déjà revu
reste inchangé : bindings matériels partiellement exécutés et SessionFile à
78,57 % branches ne sont pas déclarés conformes individuellement. Aucun test
fictif ne prétend prouver Android Keystore sur le PC ; sa preuve fonctionnelle
reste la recette humaine. Le cas de lecture d'un chemin répertoire reste une
amélioration secondaire possible, sans blocage identifié par la revue stockage.

## État gelé avant revue complète

Sources gelées, 310 empreintes dans
`build/reports/validation/m2-chatgpt-session/source-snapshot.json`. Dernier Green :
`build/chatgpt-session-green-final.log`, sortie **0**, 57 s, **196 tests sans échec,
erreur ou test ignoré**, JaCoCo produit. Le vrai adaptateur SharedPreferences du
marqueur atteint maintenant 100 % lignes et branches. Formatage, detekt et
`git diff --check` : sorties 0 ; lecteur PC : huit tests verts.

| Nouveau périmètre, référence de baseline ci-dessus | Lignes | Branches |
| --- | --- | --- |
| Core seul, 465 lignes exécutables | 98,06 % | 85,94 % |
| Ticket entier avec stockage, 609 lignes exécutables | 97,04 % | 86,21 % |
| TrialGatewaySession | 100 % | 86,67 % |
| Codec de record | 100 % | 88,75 % |
| Persistance | 98 % | 100 % |
| Host | 100 % | 100 % |
| ViewModel | 98,65 % | 83,10 % |
| Environnement du gateway | 100 % | Aucune branche mesurable |

Duplication nouvelle ciblée : **0/862 lignes (0 %)** ; globale 0,2165 %. Ces
mesures ne remplacent pas la future campagne complète. Archives immuables
`coder/metrics.json`, `coder/jacoco-targeted.xml`, `coder/junit-final/`, `coder/red/`
et journaux du dossier de preuve `build/reports/validation/m2-chatgpt-session/`.

Un Red supplémentaire de révocation avec fichier indélébile (un échec sur quatre
tests) complète la preuve du marqueur. Les essais de compilation/fixtures
interrompus ne sont pas comptés comme des Red. Le stockage n'a pas changé après
sa revue ; ses limites par fichier et sa preuve matérielle restent explicites.
La revue de l'intégration complète est en cours avant la globale puis le testeur.

## Première revue complète — deux corrections retenues

Verdict **à corriger**, malgré les 196 tests verts : deux cas absents des tests
ont été identifiés. Preuves du premier état gelé conservées dans `review1/` du
dossier local de validation.

1. TrialGatewaySession sauvegarde aussi les refresh, mais son traitement de toute
   annulation bloque et efface le record. Annuler/changer une image pendant un
   refresh validé en cours de commit peut donc imposer une reconnexion. Préserver
   le grant rotatif validé si seule l'analyse est annulée, garder la compensation
   stricte de nouvelle connexion et la déconnexion concurrente gagnante.
2. Le withTimeout d'attente de présence lève TimeoutCancellationException, traité
   comme annulation silencieuse pendant une analyse sans watchdog de connexion.
   `busy` retombe mais le statut peut rester « Analyse en cours… ». Convertir le
   seul timeout propre en TrialFailure.TIMEOUT, préserver l'annulation humaine ou
   du parent et vérifier l'absence de POST tardif et l'état final de l'UI.

Le reste des protections est cohérent selon la revue, sans autre blocage trouvé.
Aucun test, fichier, appareil ou compte utilisé par le reviewer. Retour au coder
avec Red de régression puis Green/mesures et nouvelle vérification de ces points.
La globale et le testeur restent en attente d'un nouvel état gelé conforme.

Red des corrections : `build/chatgpt-session-review1-red.log`, sortie **1**,
17 s, six tests/trois échecs fonctionnels attendus : refresh perdu après analyse
annulée, absence de révocation du nouveau refresh lors d'une déconnexion concurrente,
et absence de TIMEOUT dans l'UI. Les trois cas de contrôle (connexion initiale
annulée, annulation parent et annulation humaine) restent verts. Archives
`coder/correction1/red/` dans le dossier local de validation.

Correction minimale posée : commit d'un renouvellement validé non annulable,
conservé pour révocation avec blocage gagnant si déconnexion, et
withTimeoutOrNull pour reconnaître le délai propre. Green des cas impactés puis
suite large/mesures et nouvelle revue restent en cours.

Green impacté : `build/chatgpt-session-review1-green.log`, sortie **0**, 24 s,
**14 tests sans échec** (six nouveaux et huit protections existantes). Une analyse
annulée conserve le nouveau refresh utilisable en RAM et après redémarrage ;
la déconnexion concurrente révoque ce nouveau refresh avant effacement. Le délai
propre devient TIMEOUT et les annulations parent/humaine restent des annulations.
Formatage et detekt des corrections sortent à 0. Un cas de fermeture pendant
commit validé complète la suite finale avant nouveau gel et revue des corrections.

La cible suivante de 203 tests donne 202 succès et révèle une régression dans
un test existant : annulation avant retour du POST refresh, sans nouvel ID token.
Le checkpoint déplacé permettait de retenir cette réponse arrivée après annulation.
L'assertion existante est conservée ; le contrôle est rétabli immédiatement après
réception du grant, avant validation. Seul un grant déjà validé au début du commit
est protégé. La fermeture pendant commit et les six régressions nouvelles passent.
Un nouveau passage sur cet état est requis avant le gel.

Nouveau gel après corrections : `build/chatgpt-session-review1-green-final.log`,
sortie **0**, 57 s, **203 tests sans échec, erreur ou test ignoré**, JaCoCo produit.
Le checkpoint avant validation est conservé, les assertions anciennes ne sont pas
affaiblies. Formatage, detekt et contrôle du diff : sorties 0. Archives
`coder/correction1/` : `metrics.json`, `jacoco-targeted.xml`, `junit-final/`, `red/`
et journaux ; le stockage n'a pas changé.

Mesures ciblées finales contre la même baseline : core 481 lignes exécutables,
97,51 % lignes / 84,70 % branches ; ticket 625 lignes exécutables, 96,64 % /
85,10 %. TrialGatewaySession : 97,32 % / 83,33 %, TrialAppForeground : 100 % /
100 %. Duplication nouvelle 0/889 lignes. L'ancien snapshot et ses preuves restent
dans `review1/` ; le snapshot courant est régénéré avant la revue des corrections.

Revue des corrections terminée : **conforme, aucun blocage résiduel**. Le reviewer
confirme le checkpoint avant validation, le commit rotatif protégé, la RAM vide
après fermeture, le logout concurrent gagnant révoquant le nouveau refresh et la
conversion du seul délai propre en TIMEOUT. Les 203 XML et les régressions aux
frontières fichier chiffré/UI sont relus sans exécution ni écriture. Le snapshot
courant contient **313 empreintes** ; les mesures ciblées ne sont pas présentées
comme une preuve globale. Campagne complète lancée avant validation du testeur.

## Campagne globale et APK final

Commande réelle : `./scripts/quality.ps1 -BaseRef fc6b5578a3b16aadab61ff399ff97df0f35b644d`,
sortie **0**, résumé `passed` daté du 31 octobre 2026 à 15:05:55 UTC.
**401 tests Android**, sans échec, erreur ou test ignoré, 76 tests de scripts
et quatre PC. Mobile global 96,78 % lignes / 87,13 % branches ; nouveau code
96,64 % / 85,10 % sur 625 lignes exécutables. Scripts 94,35 % / 88,52 % ;
PC 100 % / 100 %. Les branches nouvelles des scripts et le nouveau code PC
sont non mesurables dans ce diff, pas déclarés à 100 %.

Duplication nouvelle mobile **0/889** lignes, scripts **0/1**, PC sans nouvelle
ligne. Aucune violation des limites de complexité 10/15 ; aucun seuil ou exclusion
changé. Audits : 98 paquets Maven/132 entrées de licences et 35 paquets/35 licences
PC, aucune vulnérabilité active. Rapports archivés avant le testeur dans
`quality-final/`, `jacoco-global.xml`, `junit-global/`, `junit-counts.json` ;
journal complet `quality.log`. Les **313 empreintes** correspondent au gel revu.

APK principal copié sans modification vers `dist/blamera-0.12.0-debug.apk`,
**458 207 188 octets**, SHA-256
`88e38f89d190eacd908c16ad3fd3ff9cd0bc25dafde25508e224c96a15ebd814`.
La copie et l'original Gradle ont la même empreinte. Métadonnées dans `apk.json`.
Validation indépendante de cet APK conforme ; installation décrite ci-dessous.
Aucune preuve
Keystore matériel ou restauration réelle n'est déduite de la campagne PC.

## Validation indépendante finale

Le testeur exécute **247 tests Android ciblés**, sortie **0**, 1 min 54 s, sans
échec, erreur ou test ignoré, et **huit tests du lecteur**, verts. Les 313 empreintes
restent identiques après exécution. Archives `tester/`, dont 56 suites XML dans
`tester/junit/`. Les 401 XML de la globale et leurs compteurs sont aussi vérifiés.

Package `dev.blamera`, version 0.12.0/code24, certificat debug identique à 0.11.0 :
`eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555`.
Trois permissions exactes, `allowBackup=false`, 18 exclusions cloud/transfert,
activité d'essai privée en `fullUser`. Les **38 assets/bibliothèques natifs** sont
identiques à 0.11.0, y compris la licence CommonMark embarquée. Empreintes des
APK build/dist conformes. Aucun défaut bloquant signalé : **conforme sur PC**.

Limites : vrais bindings Keystore partiellement couverts sur PC (68 % lignes dans
la campagne élargie), SessionFile 78,57 % branches ; pas déclarés conformes
individuellement. Aucun appareil, compte réel, jeton privé ni appel authentifié
n'est utilisé par le testeur. Le vrai Keystore du Xiaomi et la reprise après
fermeture restent à valider manuellement. Le préflight ADB n'a trouvé aucun
appareil prêt ; une demande de reconnexion USB a été adressée à l'utilisateur.

## Installation réelle

Après confirmation humaine de reconnexion USB, préflight : un seul appareil prêt,
Xiaomi `2407FPN8EG`. Vérification des 313 empreintes, de la taille et du SHA-256
de l'APK avant installation. Commande `adb -d install -r` sur le seul APK principal
0.12.0/code24 : sortie **0**, **Success**. Début 31 octobre 2026 à
**15:17:41,733 UTC**, fin **15:18:01,094 UTC**. Reçu local `install-result.json`,
avec l'empreinte indiquée plus haut. Données conservées ; aucun lancement automatique,
autre modification du téléphone, test APK ou test instrumenté.

L'utilisateur doit ouvrir Blamera et effectuer la [recette de reprise](../tests/m2-chatgpt-session-recette.md).
Une première connexion après mise à jour est nécessaire : aucun jeton n'était
persisté dans 0.11.0. Aucune inférence n'est requise pour vérifier la mémorisation.
La confirmation de crédits décochée après réouverture est attendue. En attente de
ce retour, le ticket reste matériellement non validé ; aucun Keystore matériel
fonctionnel ou restauration réussie n'est déduit de l'installation seule.

Retour humain suivant : « ça fonctionne ». Le parcours essentiel M2-07 est
accepté sur le Xiaomi. Les scénarios d'erreur simulés sur PC et les propriétés
matérielles fines du Keystore ne sont pas tous prouvés par ce retour. La demande
suivante de retrait des confirmations est suivie dans M2-08/ADR 0011.
