# Retour de connexion ChatGPT — diagnostic 0.10.1

30 octobre 2026. Baseline : `de7b90ab1e98565bfacd5f07c23b57171e6c7b51`.
État : logiciel conforme sur PC après revue et testeur indépendants ; APK
0.10.1/code19 installé ; retour OAuth reçu lors de l'essai réel, échange suivant
en échec avant vérification du compte et chargement des modèles.

## Défaut humain et périmètre

Après connexion et consentement, l'utilisateur voit la page ChatGPT rester en
chargement. Blamera apparaît dans son portail, mais les commandes liées à la fin
de connexion restent grises dans l'application. Il confirme ne pas voir la page
locale « Revenez dans Blamera… ». L'enregistrement côté compte ne suffit donc pas
à confirmer le retour OAuth, l'échange du code ou la validation de l'identité.
[Historique et première livraison](m2-chatgpt-plus-trial.md).

L'incrément conserve endpoints, scopes, permissions, protection des jetons et
confirmation préalable des crédits. Il renforce la tolérance aux connexions
locales abandonnées, expose uniquement des étapes fixes non secrètes et borne
l'ensemble de la connexion et du catalogue à cinq minutes. Il ne prétend pas
résoudre une cause Android ou serveur qui n'est pas encore démontrée.
[Contrat](../specs/chatgpt-plus-trial.md).

## Recherche et preuves

Le guide officiel et une lecture publique sans compte de la configuration OpenID
confirment les endpoints actuellement employés. Le code officiel DevKit
`0a36fefeb913055c8c7a1a29b63d82b96b2e841a`, `packages/local/src/oauth.ts`,
utilise un GET et Host loopback, sans contrat CORS/OPTIONS supplémentaire. Aucun
changement arbitraire de l'URL, des scopes ou du protocole de consentement.

Le codeur reproduit sur PC une fragilité : une préconnexion TCP abandonnée par
reset peut arrêter le listener avant le callback suivant. Le test
`TrialBrowserConnectionTest.resetSpeculative…` échoue pour cette raison, sortie 1,
un test/un échec, `build/chatgpt-browser-red.log`. Il s'agit d'un défaut établi du
listener ; sa responsabilité dans le chargement observé sur le Xiaomi n'est pas
prouvée. La correction garde les contrôles Host, état, URI et refus d'autorisation.

Le test de préconnexion passe après correction et synchronisation de la fixture
sur l'acceptation TCP, sans délai implicite court. Deux régressions complémentaires
échouent ensuite pour la raison attendue : un code valide ou un refus déjà validé
sont perdus si l'écriture de l'accusé HTML lève IOException. Le journal
`build/chatgpt-ack-red.log` exécute neuf tests, avec ces deux échecs et sept succès.
L'accusé doit être best-effort après validation, sans transformer un refus en succès.

Les six tests du nouveau suivi d'étapes/budget/UI ont également leur Red exécuté
avant implémentation, `build/chatgpt-progress-red.log`, puis passent dans ce groupe.
Le lecteur PC reproduit le refus du couple 0.10.1/code19 : huit tests, une erreur
fonctionnelle attendue, `build/chatgpt-callback-reader-red.log`. Aucune de ces preuves
n'est une tentative de connexion réelle au compte.

L'ancien statut unique couvrait l'attente du navigateur et plusieurs sous-requêtes
successives. Les délais de chaque sous-requête ne bornaient pas à eux seuls la
durée totale perçue. Le diagnostic doit distinguer préparation, attente navigateur,
retour reçu, échange, validation et catalogue, puis conserver l'étape en cas d'erreur.
Les événements autorisés dans les logs sont exclusivement des étapes et catégories
prédéfinies : pas d'URL, port, code, state/nonce, jeton, compte, photo ou texte libre.

## Validation et appareil

Le Green final ciblé passe sur **67 tests**, sortie 0 en 50 s, avec JaCoCo :
`build/chatgpt-callback-green-final.log`. Le lecteur PC passe ses huit tests,
`build/chatgpt-callback-reader-green.log`. Format et detekt ciblés terminent à 0,
sans changement de règles ou suppression : `build/chatgpt-callback-format-final.log`
et `build/chatgpt-callback-detekt-final.xml`/`.log`.

Couverture ciblée : backend 753/771 lignes (**97,67 %**) et 413/465 branches
(**88,82 %**) ; UI 312/322 lignes (**96,89 %**) et 125/142 branches (**88,03 %**).
Ces mesures ne remplacent pas la mesure globale et du nouveau code.

Le dernier Red de catalogue tardif est conservé dans `build/chatgpt-late-catalog-red.log` :
une annulation après la frontière de connexion doit empêcher le GET du catalogue,
même si un faux composant retourne tardivement. Le contrôle d'activité immédiat
après cette frontière complète les gardes de génération. L'échéance totale de
300 s couvre connexion et catalogue, annule les opérations et conserve l'étape
dans l'erreur ; les événements tardifs et timers terminés ne doivent plus agir.

Étapes : PREPARING_CALLBACK, WAITING_BROWSER, CALLBACK_RECEIVED, EXCHANGING_CODE,
VERIFYING_ACCOUNT, LOADING_MODELS. Le tag `BlameraChatGpt` n'accepte que
`PHASE_<ENUM>` et `FAILURE_<ENUM ou NO_PHASE>_<TrialFailure ou BOUNDARY>`.
L'interface place état et annulation en haut pendant l'attente.

Le contrôle global, le testeur et le package sont désormais conformes, et seul
l'APK principal a été mis à jour après ces vérifications. Aucun test instrumenté
sur téléphone ni appel authentifié OpenAI sur PC. L'utilisateur avait confirmé
la reconnexion USB pour cette mise à jour.

La revue indépendante du diff conclut **conforme, aucun problème bloquant** :
tolérance aux connexions abandonnées, préservation des codes/refus après échec
d'accusé, budget total et gardes de génération, whitelist de logs et absence
d'envoi d'image pendant la connexion vérifiés en lecture seule. Aucun test relancé
par le reviewer, aucune opération compte/téléphone. La commande globale est lancée
avec la baseline explicite ci-dessus ; 251 empreintes sources/configuration sont
conservées dans `build/reports/validation/m2-chatgpt-callback/source-state.json`.
Le précontrôle de couverture sur les lignes nouvelles donnait 95,83 % lignes /
87,10 % branches (144 lignes exécutables), confirmé par la globale ci-dessous.

### Contrôle global et APK

`scripts/quality.ps1 -BaseRef de7b90ab1e98565bfacd5f07c23b57171e6c7b51` termine
à **0**, sans échec : **295 tests Android**, **76 tests de scripts**, **quatre PC**.
Les rapports sont archivés avant la validation ciblée dans
`build/reports/validation/m2-chatgpt-callback/quality-final/`, `jacoco-global.xml`
et `junit-global/`. Les 251 empreintes restent identiques.

| Mesure | Résultat |
| --- | --- |
| Mobile global, lignes / branches | 96,82 % / 86,87 % |
| Nouveau mobile, lignes / branches | 95,83 % / 87,10 %, 144 lignes exécutables |
| Duplication mobile nouvelle | 0/211 lignes ; globale 0,238 % |
| Complexités | aucune violation, limites 10/15 inchangées |
| Scripts, lignes / branches | 94,35 % / 88,52 % ; nouvelle ligne 100 %, branches nouvelles non applicables |
| PC, lignes / branches | 100 % / 100 %, nouveau code non applicable |
| Duplication Python | nulle dans les périmètres mesurés |
| Dépendances runtime | 97 Maven / 131 licences ; 35 PyPI / 35 licences ; aucun avis actif retourné par OSV dans ces scopes |

Les règles, exclusions et scopes d'audit de `docs/quality.md` ne changent pas.
Les contrôles ne prouvent pas l'absence de vulnérabilités inconnues ni le succès
réel du retour Android.

Copie de l'APK final : `dist/blamera-0.10.1-debug.apk`, **457 834 845 octets**,
SHA-256 `25ec5fac38654344b7b46a0298b0fc8d316b18bb3eb20cdcece1a67c63bd8462`.
Copie et APK source sont identiques. `apksigner verify --print-certs` termine à 0,
certificat SHA-256 inchangé :
`eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555`.
La validation indépendante est **conforme sur PC** : 67 tests ciblés, aucun échec,
erreur ou test ignoré, sortie 0 en 50 s ; huit tests du lecteur PC verts. Les 251
empreintes sont identiques avant/après. Version19/API36/arm64, permissions et
activité privée/fullUser, certificat, taille et SHA vérifiés. Les 37 assets/libs
comparés sont identiques à 0.10.0. Les archives globales sont conservées avant
l'exécution ciblée. Aucun défaut reproductible trouvé.
[Rapport du testeur local](../../build/reports/validation/m2-chatgpt-callback/tester/report.md).

### Installation et reprise humaine

Après revérification de la copie APK, des 251 sources et de l'unique Xiaomi prêt,
la mise à jour du seul APK principal réussit le 30 octobre 2026 de 18:23:14 à
18:23:33 UTC : **`adb -d install -r`, sortie 0 « Success »**.
Reçu sans identifiant appareil :
`build/reports/validation/m2-chatgpt-callback/install-result.json`.
Aucun lancement automatique, effacement de données ou autre mutation du téléphone.

L'utilisateur est invité à refaire une seule connexion manuelle. Si la page reste
en chargement environ quinze secondes, il revient dans Blamera et relève la
dernière étape ou l'erreur affichée en haut. Aucun envoi d'image requis pour ce
diagnostic. Application ouverte et téléphone connecté permettent la lecture des
seuls événements fixes du PID Blamera. Le succès réel reste en attente ; la
responsabilité du défaut PC corrigé dans le blocage initial n'est pas présumée.

## Retour humain suivant et événements Blamera

L'utilisateur voit maintenant la page locale « Revenez dans Blamera pour poursuivre
la connexion », puis « Essai interrompu, connexion au service impossible… » dans
l'application. La lecture autorisée du seul PID Blamera et du tag BlameraChatGpt
retourne PREPARING_CALLBACK, WAITING_BROWSER, CALLBACK_RECEIVED, EXCHANGING_CODE,
puis FAILURE_EXCHANGING_CODE_NETWORK. Entre EXCHANGING_CODE et l'échec : environ
41 ms. Événements sans secrets conservés dans
`build/reports/validation/m2-chatgpt-network/app-events.json`.

Le callback valide est donc reçu dans cet essai, sans prouver la cause du blocage
0.10.0. L'erreur survient à l'échange du code, avant vérification d'identité,
catalogue ou image. NETWORK regroupe actuellement IOException et HTTP5xx ; la
durée ne permet pas de distinguer DNS, TLS, connexion, lecture/écriture ou statut
serveur. Une seconde lecture limitée au PID et à des marqueurs connus d'erreurs
ne trouve aucun détail supplémentaire ; aucun contenu brut ni log d'autre app
n'est affiché ou conservé. Un diagnostic borné de cette frontière est engagé.

Après cette reconnexion, une lecture limitée au PID de Blamera déjà ouvert par
l'utilisateur et aux filtres AndroidRuntime:E, System.err:W et BlameraChatGpt:I
ne retourne aucun message dans le tampon disponible. Aucun crash n'est donc
documenté par cette lecture, mais l'absence de phases dans 0.10.0 empêche de
localiser la connexion. Aucun log Chrome/ChatGPT ou contenu brut n'est collecté.

L'utilisateur devra refaire uniquement la connexion et relever la dernière étape
affichée. Si elle échoue, l'agent pourra lire les seuls événements de diagnostic
du processus Blamera lancé manuellement, sans lire Chrome ni le portail ChatGPT.
Aucun nouvel envoi d'image n'est requis pour localiser ce défaut.
