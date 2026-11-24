# Échange de connexion ChatGPT — diagnostic réseau

30 octobre 2026. Baseline : `07b84a04b6023a28816a271cc9dac3505501e370`.
État : diagnostic 0.10.2/code20 exécuté sur téléphone, DNS échoue en pause après
découverte publique réussie au premier plan. Correctif 0.10.3 installé ; cause
système exacte et succès d'échange réel encore à établir.

## Preuve réelle et frontière concernée

L'utilisateur de 0.10.1 voit la page locale lui demandant de revenir dans Blamera,
puis un message de connexion au service impossible. Les événements lus uniquement
sur le PID Blamera, tag BlameraChatGpt, donnent :

1. PREPARING_CALLBACK ;
2. WAITING_BROWSER ;
3. CALLBACK_RECEIVED ;
4. EXCHANGING_CODE ;
5. FAILURE_EXCHANGING_CODE_NETWORK, environ 41 ms après l'étape précédente.

Preuve sans donnée d'authentification :
`build/reports/validation/m2-chatgpt-network/app-events.json`.
Le retour du navigateur est reçu dans cet essai ; aucune causalité n'est déduite
pour le précédent blocage. L'échange échoue avant la vérification d'identité,
le catalogue et tout envoi d'image. Aucun log Chrome/ChatGPT n'est lu. Une recherche
de marqueurs réseau connus dans les seuls logs du PID ne donne aucun autre détail ;
aucun contenu brut n'est conservé ou affiché.

## Investigation autorisée

Le contrat OAuth est confronté au guide officiel : formulaire POST, code,
client émis, vérificateur PKCE, URI exacte et resource. L'ordre de configuration
de HttpsURLConnection paraît cohérent. Le code actuel confond une IOException
et un HTTP5xx dans la catégorie NETWORK ; 41 ms ne permet pas de trancher.

Le codeur teste le formulaire au fil sur un serveur local PC avec une véritable
HttpURLConnection déléguée, sans compte externe ni TLS assoupli en production.
Si ce test ne démontre pas de défaut, le seul incrément autorisé est un diagnostic
borné permettant de distinguer étape de transport, catégorie fixe DNS/TLS/socket/IO,
endpoint par enum et statut HTTP entre 100 et 599. Jamais de message/stack de
Throwable, URL, en-tête, corps, code OAuth, jeton, compte ou donnée d'image dans
les logs. Ne pas changer au hasard les scopes, endpoints, certificats ou redirections.

Aucune demande de changement de réseau ou de réglage sur le téléphone ne découle
d'un message générique. Pas de nouvelle permission, achat, clé API, activation de
crédits ou tentative automatique d'inférence. L'utilisateur garde le lancement et
les manipulations, l'agent ne fait que mettre à jour l'APK principal validé et lire
ses événements autorisés. [Contrat principal](../specs/chatgpt-plus-trial.md).

## Preuves à produire

Test réel de contrat HTTP PC, TDD pour un défaut établi ou pour le diagnostic
nouveau, contrôles ciblés et globaux sur état stable, revue puis testeur indépendant.
La version 0.10.2/code20 est permise uniquement si cet incrément se justifie après
l'analyse. Une reprise manuelle du compte restera nécessaire : une fixture PC
ne démontre ni le succès du serveur réel ni la cause de l'échec sur le Xiaomi.

## Contrat HTTP réel sur PC

Deux tests au fil réussissent, sortie 0, `build/chatgpt-network-wire.log` : le vrai
client JDK envoie vers le serveur local un POST avec formulaire, Content-Type,
Content-Length et champs attendus. Une réponse HTTP503 réelle reproduit NETWORK
sans échec DNS/TLS. Aucun défaut d'ordre d'écriture n'est établi sur ce client PC,
et aucune conclusion Android/TLS n'en découle. Aucun compte externe ou code réel
n'est utilisé, aucune protection TLS de production n'est assouplie.

Le diagnostic 0.10.2/code20 est donc retenu pour rendre cette frontière discriminante.
Les HTTP5xx seront distingués comme service indisponible, sans conseiller de
modifier le réseau du téléphone. Les catégories de transport et le statut HTTP
resteront des enums/nombres validés, sans message ou corps arbitraire.

La [documentation Android sur les apps mises en cache](https://source.android.com/docs/core/perf/cached-apps-freezer)
décrit aussi une suspension possible des processus et la fermeture de sockets.
Cela fournit une hypothèse de cycle de vie, pas une observation sur ce Xiaomi.
Une éventuelle métadonnée RESUMED/PAUSED proviendra exclusivement de l'activité
Blamera ; aucun état Chrome/ChatGPT, dumpsys, réglage ou commande de freezer ne
sera lu ou modifié. Aucun service permanent ou changement de permission n'est ajouté.

## Vérification préalable avant ouverture du navigateur

Le DevKit officiel charge la découverte OpenID avant d'ouvrir le navigateur.
Le même ordre est retenu dans 0.10.2 : après l'appui explicite « Continuer »,
phase CHECKING_SERVICE puis GET public OpenID et validation stricte de l'issuer
et de l'origine JWKS avant création du listener et ouverture du navigateur.
Un échec à cette étape n'exige donc pas de repasser par le compte ChatGPT.

La découverte est conservée uniquement en mémoire et réutilisée pour la validation
de l'identité après retour. Elle est déplacée, pas ajoutée au flux froid : aucun
appel de diagnostic en boucle, aucun secret envoyé pour cette requête publique.
Le POST d'échange reste inchangé ; aucun report de requête ou changement TLS n'est
utilisé pour forcer un succès. Le lien éventuel avec le cycle de vie reste à prouver.

## Preuves TDD du diagnostic

`build/chatgpt-network-red.log` : sept tests, six échecs fonctionnels attendus
(métadonnées absentes, HTTP5xx mal classé, visibilité lifecycle absente).
`build/chatgpt-network-preflight-red.log` : trois tests, trois échecs fonctionnels attendus
(découverte absente avant navigateur et diagnostic correspondant). Ces échecs
précèdent l'implémentation. Le lecteur PC a également son Red : huit tests, une
erreur attendue pour le couple 0.10.2/code20 encore non admis, puis huit tests verts.

Green final : **76 tests**, zéro échec/erreur, sortie 0 en 57 s, avec JaCoCo,
`build/chatgpt-network-green-final.log`. Lecteur : huit tests verts, sortie 0,
`build/chatgpt-network-reader-green.log`. ktlint et detekt ciblés terminent à 0,
`build/chatgpt-network-format-final.log` et `build/chatgpt-network-detekt-final.log`.
`git diff --check` termine à 0. Aucune exclusion ni règle abaissée.

Couverture ciblée backend : 868/897 lignes (**96,77 %**) et 485/542 branches
(**89,48 %**). UI : 318/328 lignes (**96,95 %**) et 125/142 branches (**88,03 %**).
Le précontrôle lecture seule des lignes nouvelles sur la baseline donne 92,25 %
lignes / 91,36 % branches pour 142 lignes exécutables ; il ne remplace pas la
commande globale, confirmée ci-dessous. Revue indépendante, globale et testeur
ont précédé la mise à jour du seul APK principal.

La revue indépendante conclut **conforme, aucun problème bloquant** sur le diff :
métadonnées bornées, absence de données sensibles, priorité permission/DNS,
annulation/délai sans double échec, découverte avant navigateur et cache validé,
gardes OAuth/TLS/crédits et absence d'envoi automatique conservés. Aucun test,
fichier, compte ou téléphone manipulé par le reviewer. Il confirme 75 tests
ChatGPT/MainActivity et un test caméra, cohérents avec les 76 annoncés.

La commande globale est lancée avec la baseline explicite de ce rapport ;
254 empreintes de sources/configuration sont enregistrées dans
`build/reports/validation/m2-chatgpt-network/source-state.json`.
Le testeur a attendu la fin de la globale avant son exécution indépendante afin
de préserver les rapports archivés.

## Contrôle global et APK préparé

`scripts/quality.ps1 -BaseRef 07b84a04b6023a28816a271cc9dac3505501e370`
termine à **0**, sans échec : **304 tests Android**, **76 tests de scripts**,
**quatre PC**. Rapports archivés avant le passage ciblé du testeur dans
`build/reports/validation/m2-chatgpt-network/quality-final/`, `jacoco-global.xml`
et `junit-global/`. Les 254 empreintes restent identiques.

| Mesure | Résultat |
| --- | --- |
| Mobile global, lignes / branches | 96,67 % / 87,08 % |
| Nouveau mobile, lignes / branches | 92,25 % / 91,36 %, 142 lignes exécutables |
| Duplication mobile nouvelle | 0/187 lignes ; globale 0,232 % |
| Complexités | limites 10/15 inchangées, aucune violation |
| Scripts, lignes / branches | 94,35 % / 88,52 % ; nouvelle ligne 100 %, branches nouvelles non applicables |
| PC, lignes / branches | 100 % / 100 %, nouveau code non applicable |
| Duplication Python | nulle dans les périmètres mesurés |
| Runtime | 97 Maven / 131 licences ; 35 PyPI / 35 licences ; aucun avis actif retourné par OSV dans ces scopes |

Aucune bibliothèque runtime, permission ou règle de qualité nouvelle. Le scope
des audits reste celui de `docs/quality.md` ; aucun résultat ne démontre l'absence
de vulnérabilités inconnues, ne valide la pile TLS réelle de l'appareil ou le succès
d'un compte réel.

APK : `dist/blamera-0.10.2-debug.apk`, **457 851 229 octets**, SHA-256
`3468a8fd283410ecbb21e960f1f82996ede2ede068306ccb2ede769d27974afc`.
La copie et l'APK source sont identiques. `apksigner verify --print-certs` termine
à 0, certificat SHA-256 inchangé :
`eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555`.
Le testeur indépendant conclut **conforme sur PC** : 76 tests ciblés réussis,
zéro échec/erreur/test ignoré, sortie 0 en 48 s ; huit tests du lecteur verts.
Version20/API36/arm64, permissions, activité privée/fullUser, certificat, taille
et SHA vérifiés. Les 37 assets/libs sont identiques à 0.10.1 ; les 254 empreintes
restent identiques avant/après. L'archive globale de 304 tests est confirmée.
[Rapport détaillé local](../../build/reports/validation/m2-chatgpt-network/tester/report.md).

## Installation et essai manuel

Après revérification du SHA, de la taille, des 254 sources et de l'unique Xiaomi
prêt, mise à jour du seul APK principal réussie le 30 octobre 2026 de 19:32:07
à 19:32:26 UTC : **`adb -d install -r`, sortie 0 « Success »**. Reçu sans identifiant
appareil : `build/reports/validation/m2-chatgpt-network/install-result.json`.
Aucun lancement automatique, effacement de données ou autre mutation du téléphone.

L'utilisateur ouvre l'essai puis touche « Continuer ». Si CHECKING_SERVICE échoue,
il donne l'erreur sans repasser par le compte. Si le navigateur s'ouvre, il termine
la connexion puis revient dans Blamera et indique le résultat. Aucun envoi d'image
requis. L'application reste ouverte et connectée pour la lecture des seuls événements
autorisés du PID ; ne pas écraser `app-events.json`, qui conserve la preuve 0.10.1.
La nouvelle trace devra être versionnée 0.10.2. La cause réelle demeure ouverte.

## Résultat réel 0.10.2

L'utilisateur retrouve la page locale après autorisation, puis le même message
d'échec dans Blamera. La trace 0.10.2, collectée exclusivement sur le PID de
l'application avec les messages whitelistes, est conservée dans
`build/reports/validation/m2-chatgpt-network/app-events-0.10.2.json`.

Elle confirme LIFECYCLE_RESUMED puis CHECKING_SERVICE et HTTP200 pour DISCOVERY.
Après ouverture du navigateur : LIFECYCLE_PAUSED, CALLBACK_RECEIVED,
EXCHANGING_CODE puis **HTTP_TOKEN_OPEN_OUTPUT_DNS_NONE**, sans statut HTTP.
L'échec survient avant le retour LIFECYCLE_RESUMED, environ 3,8 secondes plus tard.
L'accès public au même hôte fonctionne donc au premier plan, et une erreur DNS
survient à l'ouverture du POST pendant que Blamera est en pause.

Aucun serveur503 ni panne TLS n'est établi. La restriction système précise reste
inconnue ; aucune inspection de réglages ou d'autre application n'est faite.
La correction suivante attendra le retour RESUMED de Blamera avant l'échange du
code, sans DNS alternatif, IP imposée, permission, service ou réglage nouveau.
[Preuve et contrat de reprise](m2-chatgpt-foreground.md).

Messages autorisés du tag BlameraChatGpt :
`HTTP_<endpoint>_<stage>_<category>_<NONE ou statut100..599>`, au plus un statut et
un échec par requête, plus LIFECYCLE_RESUMED/PAUSED et PHASE_CHECKING_SERVICE.
Endpoints : TOKEN, DISCOVERY, JWKS, REVOCATION, MODELS, RESPONSES, OTHER.
Stades : OPEN_CONNECTION, CONFIGURE, OPEN_OUTPUT, WRITE_BODY, READ_STATUS, READ_BODY.
Catégories : HTTP_STATUS, DNS, TLS, CONNECT_REFUSED, CONNECT_UNREACHABLE,
SOCKET_PERMISSION, TIMEOUT, SOCKET, IO, CANCELLED, PROTOCOL, INPUT.
Ces champs sont limités à des enums et un statut validé ; aucun texte externe
n'est journalisé. Les événements phase/échec déjà autorisés sont conservés.

La correction de catalogue 0.10.4 ajoute la catégorie fixe BODY_LIMIT : elle
désigne un dépassement de borne du lecteur, sans corps, taille mesurée, adresse
ou message d'exception. Elle conserve INVALID_RESPONSE côté utilisateur.
