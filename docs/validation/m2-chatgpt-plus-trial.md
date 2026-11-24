# Validation — essai ChatGPT Plus

30 octobre 2026. Baseline `863ef77a382e9abc86c68960d715563a8f49ac62`.
État : logiciel conforme sur PC après revue et testeur indépendants ; APK principal
0.10.0/code 18 installé. Le retour humain indique un blocage pendant la connexion,
après ajout de Blamera au portail ChatGPT ; l'analyse réelle reste non validée.

Actualisation du 31 octobre : après les correctifs 0.10.1 à 0.10.4, le catalogue
et la réponse pertinente en français à la tasse synthétique sont validés sur
Xiaomi par capture fournie par l'utilisateur. M2-06 est terminé pour ce parcours ;
le présent rapport conserve l'historique de 0.10.0.
[Résultat final et limites](m2-chatgpt-catalog.md#résultat-réel--31-octobre-2026).

## Périmètre et décision

[Contrat](../specs/chatgpt-plus-trial.md),
[ADR 0009](../decisions/0009-chatgpt-plus-trial.md),
[recette manuelle](../tests/m2-chatgpt-plus-recette.md).
L'utilisateur autorise l'implémentation du parcours connexion Plus, image et consigne,
réponse dans Blamera, sans activer de crédits payants. Essai sur image synthétique,
jetons en mémoire, aucune clé API Platform ou donnée d'une autre application.

Deux codeurs possèdent des périmètres disjoints : protocole/authentification et
UI/intégration. L'orchestrateur prépare les contrats et la documentation.
La revue indépendante puis le testeur portent sur l'état stabilisé final.

## Preuves TDD recueillies pendant le développement

Les premiers lancements Gradle ont rencontré un échec de daemon puis un problème
de compilation incrémentale ; ils ne comptent pas comme Red fonctionnel.
La relance coordonnée avec compilation complète a exécuté 12 tests (3 protocole,
9 contrôleur UI), tous en échec attendu sur les stubs, sortie 1.
Journal : `build/chatgpt-core-red.log`. Les commandes exactes et compléments de
preuve Green/Refactor seront reportés à la livraison des codeurs.

Le groupe protocole passe ensuite : trois tests, sortie 0, 21 s,
`build/chatgpt-core-green-protocol.log`. Une deuxième étape Red coordonnée exécute
15 tests : les neuf tests du contrôleur passent, les trois tests UI et les trois
tests d'autorisation échouent sur leurs stubs (six échecs attendus), sortie 1,
`build/chatgpt-ui-red.log`. Ce résultat ne valide pas encore le logiciel complet.

Le protocole d'autorisation puis la session ont leurs tests Red exécutés avant
implémentation, avec six échecs fonctionnels attendus de session dans
`build/chatgpt-core-red-gateway.log`. La régression d'un retour navigateur tardif
après annulation est reproduite dans `build/chatgpt-ui-callback-red.log`, puis
corrigée par invalidation de la tentative. Le lecteur PC reproduit le refus du
nouveau couple version/code dans `build/chatgpt-benchmark-reader-red.log`, puis
les huit tests du lecteur passent dans `build/chatgpt-benchmark-reader-green.log`.
Les combinaisons inconnues ou croisées restent rejetées.

Après refactorings de responsabilités, le Green final ciblé exécute :

```powershell
.\scripts\gradle.ps1 --no-watch-fs :app:testDebugUnitTest --tests 'dev.blamera.chatgpt.*' --tests 'dev.blamera.MainActivityLocalTest' :app:jacocoDebugReport
```

Sortie **0**, 42 s, **53 tests** : 29 backend, 19 UI/contrôleur/activité et cinq
MainActivity. `build/chatgpt-core-green-all.log` et XML JUnit font foi. Les tests
réseau HTTPS simulé et véritable loopback PC complètent les scénarios TDD ; aucun
Red distinct n'est revendiqué pour ces compléments. Aucun appel authentifié OpenAI.

Couverture du composant backend : 653/673 lignes (**97,03 %**), 389/435 branches
(**89,43 %**). UI : 249/261 lignes (**95,40 %**), 94/110 branches (**85,45 %**).
Ces mesures ciblées ne remplacent pas le gate global et du nouveau code.
ktlint et detekt ciblés : sortie 0, règles et exclusions inchangées. Les deux
captures d'interface **PC** sont générées et relues dans
`build/reports/validation/m2-chatgpt-plus/ui/` (portrait et paysage avec réponse
simulée). Elles ne proviennent pas du téléphone et ne prouvent pas une analyse réelle.

## Contrôles finaux

Les contrôles finaux, la seconde revue, le testeur et le package sont conformes
sur PC. L'historique ci-dessous conserve aussi la première passe globale échouée.
Seule la preuve réelle de connexion et d'analyse sur le compte reste en attente.

La revue indépendante en lecture seule demande deux corrections contractuelles :
nettoyage d'un listener acquis pendant une annulation avant remise du résultat
`withContext`, et délai absolu pour les flux HTTP/callback alimentés régulièrement.
Pas d'achat, envoi automatique, fuite de jeton, endpoint privé ou contournement des
permissions relevé. La suite est un cycle de régression Red/correction/Green, puis
relecture et contrôles finaux renouvelés. La reproduction n'a pas été exécutée par
le reviewer ; le constat initial repose sur le contrôle de flux du code.

Première commande globale : `scripts/quality.ps1 -BaseRef 863ef77a382e9abc86c68960d715563a8f49ac62`,
**sortie 1**. Sur 282 tests Android, un ancien test de récupération du direct échoue
(`InlineLiveRecoveryTest`, ligne 79). L'échec empêche la production du rapport JaCoCo
global ; aucune conformité globale n'est déduite des métriques ciblées. Les autres
étapes exécutées terminent à 0. Journal conservé dans
`build/reports/validation/m2-chatgpt-plus/quality-initial.log`.
Le codeur UI examine ce défaut séparément des deux corrections backend ; le test
ne sera ni ignoré ni affaibli pour faire passer la suite.

La reproduction caméra confirme une fixture mal calibrée : écran PC 320×470,
aperçu 296×118, transformation identité d'une frame 640×480 dont le premier contour
commence à y=120, donc entièrement hors de la vue. La production emploie la
transformation de `PreviewView.outputTransform`. Le Red ciblé (sortie 1) est archivé
dans `build/reports/validation/m2-chatgpt-plus/inline-live-recovery/red.log` et
`red.xml`. La correction ajuste uniquement la transformation de fixture au viewport
mesuré en conservant les proportions ; les assertions de rendu, effacement et
récupération restent exigées. Le XML original de la passe globale avait été lu,
mais a été nettoyé par Gradle avant archivage ; il n'est pas reconstitué.

Pour les deux points backend retenus en revue, quatre régressions Red s'exécutent
et échouent pour les raisons attendues, sortie 1 en 38 s :
`build/chatgpt-core-red-correction1.log`. Les quatre tests passent après correction
(`build/chatgpt-core-green-correction1.log`, sortie 0). Le listener est possédé dès
son acquisition dans le bloc IO ; une annulation avant reprise ne l'abandonne plus.
Un watchdog absolu ferme les ressources et impose TIMEOUT après 120 s pour HTTP,
300 s pour le callback, y compris lorsqu'un flux progresse continuellement.

Le Green commun après correction passe sur **58 tests**, sortie 0 en 53 s :
ChatGPT, MainActivity et InlineLiveRecoveryTest. Journal
`build/chatgpt-core-green-correction1-all.log`, JaCoCo généré. Le Green caméra est
archivé avec son Red. Backend : 714/732 lignes (**97,54 %**), 399/447 branches
(**89,26 %**). UI : 249/261 lignes (**95,40 %**), 94/110 branches (**85,45 %**).
Format et detekt ciblés passent sans suppression ni changement des seuils.
Une seconde relecture indépendante et la seconde commande globale ont ensuite
porté sur cet état gelé. Deux retours à la ligne du test ont été formatés après le Green,
sans changement sémantique ; la commande globale a recompilé cet état final.

La seconde revue indépendante confirme les deux corrections et la calibration de
fixture, sans problème bloquant restant. Elle n'a modifié aucun fichier ni relancé
de tests ; la seconde qualité globale et le testeur ont ensuite confirmé cet état. Un état
de référence de **246 fichiers** de sources/configuration est enregistré dans
`build/reports/validation/m2-chatgpt-plus/source-state.json` pour vérifier la
stabilité entre contrôle, validation et fabrication. `main` reste à
`9bf58a1c6614f080dce8bc2c0ec6fcaf30829877`, sans publication distante.

### Seconde commande globale — conforme

La même commande globale sur la même baseline termine à **0**, sans échec :
**286 tests Android**, **76 tests de scripts**, **quatre tests PC**. Les rapports
sont copiés avant le passage ciblé indépendant dans
`build/reports/validation/m2-chatgpt-plus/quality-final/`, `jacoco-global.xml`
et `junit-global/`. Le journal final est `quality.log` dans ce même dossier.

| Mesure | Résultat final |
| --- | --- |
| Mobile global, lignes / branches | 96,70 % / 86,84 % |
| Mobile nouveau, lignes / branches | 96,99 % / 88,55 % ; 998 lignes exécutables nouvelles |
| Duplication mobile nouvelle | 6/1 541 lignes = 0,389 % ; globale 0,243 % |
| Complexité cyclomatique / cognitive | limites 10 / 15, aucune violation |
| Scripts, lignes / branches | 94,35 % / 88,52 % ; nouvelle ligne 100 %, nouvelle branche non applicable |
| PC, lignes / branches | 100 % / 100 % ; nouveau code non applicable |
| Duplication Python | nulle dans les périmètres mesurés |
| Runtime Maven | 97 composants, 131 entrées de licences, aucun avis actif retourné par OSV |
| Runtime PyPI | 35 composants / 35 licences, aucun avis actif retourné par OSV |

Le scope et les limites des audits restent ceux de `docs/quality.md` ; aucune
absence de mesure n'est présentée comme conforme. Pas de nouveau paquet runtime,
exclusion, suppression de règle ni abaissement de seuil. Les 246 empreintes du
snapshot correspondent encore à l'état final après cette commande.

### APK préparé

`dist/blamera-0.10.0-debug.apk`, **458 097 128 octets**, SHA-256
`5024db6ac5250da744cb1cac5b8cea1f46ed71cfe96a5c0d02760220cafefb77`.
La copie est identique à l'APK produit par la commande globale.
`apksigner verify --print-certs` termine à 0 ; certificat SHA-256 identique au
précédent : `eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555`.
Métadonnées de copie : `build/reports/validation/m2-chatgpt-plus/apk.json`.
La validation indépendante du testeur est **conforme sur PC** : 58 tests ciblés et
huit tests du lecteur PC réussissent, sorties 0, aucun échec, erreur ou test ignoré.
Les 246 empreintes sont identiques avant/après. Le package a bien version 0.10.0/code18,
API 36 et ABI arm64 ; activité d'essai non exportée, INTERNET/CAMERA et permission
interne AndroidX attendues, sans autre permission privée ni cleartext global.
Les **37 assets/bibliothèques natives** comparés sont identiques à 0.9.4, dont les
modèles ; le ZIP comporte 556 entrées. Signature, taille et SHA sont confirmés.
Le testeur a préservé les copies des rapports globaux avant ses exécutions ciblées.
[Rapport détaillé local](../../build/reports/validation/m2-chatgpt-plus/tester/report.md).

## Appareil et compte

Une première détection ADB en sandbox a échoué au démarrage du daemon ; elle ne
prouve pas l'absence de téléphone. Une lecture indépendante hors sandbox a ensuite
confirmé l'absence d'appareil. L'utilisateur a depuis confirmé sa reconnexion.
Après contrôle de l'APK et des sources et verdict conforme du testeur, une première
commande d'installation a échoué à l'analyse syntaxique PowerShell, avant toute
opération sur le téléphone. La commande corrigée met ensuite à jour uniquement
l'APK principal : **`adb -d install -r`, sortie 0 « Success »**, le 30 octobre 2026,
de 13:37:41 à 13:38:05 UTC. Le reçu sans identifiant appareil est conservé dans
`build/reports/validation/m2-chatgpt-plus/install-result.json`. Aucun lancement
automatique, effacement ou autre mutation du téléphone.

L'utilisateur est invité à ouvrir « Essai ChatGPT », se connecter, vérifier la
désactivation des crédits après limite, puis envoyer l'illustration de la tasse
et sa consigne. M2-06 reste en attente de cette preuve réelle ; l'intégration du
recadrage d'un objet personnel n'est pas encore livrée par cet essai borné.

L'authentification, le choix du modèle, la vérification du réglage interdisant les
crédits après limite et l'envoi seront réalisés manuellement par l'utilisateur.
La case dans Blamera est une confirmation déclarative, pas une lecture du réglage
serveur. Si ce réglage ne peut pas être vérifié, l'envoi doit rester bloqué.

Ne pas lire les données de ChatGPT, copier les identifiants, interroger des API
authentifiées à la place de l'utilisateur ou collecter une URL contenant un code
OAuth. Les tests PC simulent les réponses ; ils ne prouvent ni l'éligibilité réelle
du compte ni la qualité d'identification d'un modèle distant.

## Retour humain — blocage après autorisation

L'utilisateur rapporte se connecter à ChatGPT et confirmer la connexion de Blamera,
puis voir un chargement qui ne se termine pas. Au retour dans Blamera, les contrôles
du bas restent grisés. Il voit toutefois Blamera ajoutée dans son portail ChatGPT.

Ce retour confirme l'enregistrement côté compte, pas la réception du callback dans
Blamera, l'échange du code ou la validation de l'identité. La cause et l'étape exacte
restent à établir ; aucun envoi d'image ni consommation d'inférence n'est confirmé.
Une précision est demandée sur la page affichée (page ChatGPT ou message de retour
Blamera), sans adresse/code/jeton. Une lecture des seuls logs du PID Blamera est
préparée, mais n'a pas eu lieu : le Xiaomi n'est plus détecté à cette tentative.
Aucune donnée Chrome/ChatGPT lue, aucun réglage ou paiement modifié.

Un agent de code recherche une reproduction PC et une correction fondée. Si un
diagnostic d'étapes est nécessaire, il doit exposer uniquement des étapes fixes
et erreurs sûres, jamais les URL, codes, jetons ou réponses personnelles.

Précision humaine : **la page ChatGPT reste en chargement**, et non la page locale
« Revenez dans Blamera… ». Le retour n'est donc pas établi. Une lecture publique
sans authentification de la configuration OpenID confirme les endpoints employés
(authorize, token, JWKS, revoke et issuer), identiques à ceux du guide officiel.
L'exemple officiel DevKit `0a36fefeb913055c8c7a1a29b63d82b96b2e841a`,
`packages/local/src/oauth.ts`, accepte un GET avec Host loopback et ne requiert
pas de CORS/OPTIONS spéciaux. Aucun endpoint ou scope n'est changé par supposition.

L'analyse du code distingue un manque de visibilité (un seul statut couvre le
callback puis plusieurs requêtes HTTP) d'une hypothèse de panne. Une préconnexion
navigateur abandonnée peut également exposer une fragilité du listener ; seule une
reproduction PC justifiera sa correction. L'incrément 0.10.1 prévu doit rendre
visibles des étapes non secrètes, préserver l'annulation et borner la connexion
complète. Le réglage serveur des crédits, les scopes et la validation de l'identité
ne seront pas contournés. Aucune cause Android/HyperOS n'est affirmée sans preuve.
