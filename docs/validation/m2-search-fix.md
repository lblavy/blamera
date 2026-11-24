# Recherche Google avec image cassée — correctif 0.9.1

29 octobre 2026 — écart de protocole établi, code, revue, qualité globale et testeur
indépendant conformes sur PC. Mise à jour 0.9.1 installée ; nouvel échec de la
recherche Google confirmé par l'utilisateur. Référence du nouveau code :
`94dd2fa28125108ab6320409aabae5eb382b2777` (installation 0.9.0, état propre avant ce diagnostic).

## Défaut observé sur le Xiaomi

L'utilisateur juge les opérations locales fonctionnelles, mais Google affiche
une image cassée et barrée dans la zone de recherche, sans résultat. W02 échoue.
Il confirme utiliser Chrome ; choisir une image de galerie directement depuis
Google Lens dans Chrome fonctionne, avec image visible et résultats.

Ces observations désignent le transfert depuis Blamera comme parcours défaillant.
Elles ne démontrent pas que l'image est vide dans Blamera, que les cookies sont
la cause unique ou que le sélecteur Android perd l'URI. Aucun de ces diagnostics
n'est présenté comme établi.

## Investigation bornée et limites

L'agent coder examine les sources et prépare deux requêtes sur une image géométrique
synthétique de 160 × 120, JPEG valide de 2 613 octets. Les champs multipart correspondent
au constructeur Mozilla : `encoded_image`, fichier `image.jpg`, `image/jpeg` et
`processed_image_dimensions` sous la forme largeur,hauteur. Les réponses sont des
redirections Google ; les GET indépendants avec et sans cookies ne fournissent
qu'une enveloppe JavaScript, sans preuve d'une image réellement affichée.
[Synthèse sans données de recherche](../../build/reports/validation/m2-search-fix/coder/investigation-summary.json).

Un troisième envoi de la même image synthétique utilise le formulaire officiel
Google Images dans le navigateur PC. La navigation atteint une page anti-robot
Google ; aucun CAPTCHA n'est résolu ou contourné, aucun autre envoi Google n'est
effectué. Cet essai ne valide ni image ni résultats. L'onglet temporaire est fermé.
Aucune photo personnelle n'est utilisée ni aucun téléphone piloté pendant le diagnostic.
[Constat du navigateur PC](../../build/reports/validation/m2-search-fix/browser-probe.json).

Les sources Chromium réservent les extras de POST aux appels internes de Chrome et
ne fournissent pas de réception publique `ACTION_SEND image/jpeg` pour ce parcours.
L'utilisation de ces interfaces privées n'est pas retenue.
[IntentHandler Chromium](https://chromium.googlesource.com/chromium/src/+/HEAD/chrome/android/java/src/org/chromium/chrome/browser/IntentHandler.java).

## Écart de protocole établi et correctif retenu

Une vérification supplémentaire des sources Chromium apporte un écart concret :
`lens_form.ts` distingue `/upload` (Scotty) de `/v3/upload` (envoi direct), puis
`submitFile_` choisit explicitement l'envoi direct. Le formulaire contient uniquement
le fichier `encoded_image`. Les paramètres publics incluent `ep=cntpubb`, `hl`, `st`,
`re=df`, `s=4`, et les dimensions du JPEG traité dans `vpw`/`vph`.
[Code Chromium](https://chromium.googlesource.com/chromium/src/+/HEAD/chrome/browser/resources/new_tab_page/lens_form.ts),
[formulaire](https://chromium.googlesource.com/chromium/src/+/HEAD/chrome/browser/resources/new_tab_page/lens_form.html).

La version 0.9.0 utilisait `/upload` et le champ `processed_image_dimensions` d'un
constructeur Mozilla. Cette différence est vérifiée ; elle ne démontre pas encore
qu'elle explique seule l'image cassée sur le Xiaomi.

Retenir d'abord un correctif limité de `GoogleLensRequest` : `/v3/upload`, fichier
JPEG seul, paramètres publics ci-dessus et `hl=fr`. Ne pas reprendre les variations
propres à Chrome (`cd`), les cookies, un faux User-Agent ni le préchargement `gen204`.
Conserver le client natif annulable déjà revu, ses délais, l'absence de nouvelle
tentative automatique et la validation stricte de l'URL Google avant ouverture.
Toute réponse incompatible reste une erreur explicite ; la source ne garantit ni
son statut HTTP ni l'adresse reçue par un client tiers.

La revue indépendante approuve cet arbitrage proportionné. La version prévue est
0.9.1/code 14, avec lecteur de diagnostic acceptant cette identité exacte ; aucune
permission, dépendance, catégorie, modèle ou autre fonction n'est modifiée.

## Alternative étudiée et non livrée

Avant cet écart établi dans Chromium, une page locale éphémère avait été envisagée
pour confier l'envoi à Chrome. Le travail exploratoire est arrêté, archivé puis
retiré du périmètre livré : quinze fichiers du coder sont restaurés octet pour octet
depuis `94dd2fa`, huit fichiers expérimentaux supprimés après contrôle de l'archive,
et les documents de l'orchestrateur restent inchangés. Aucun serveur local n'est actif.
Archive `loopback-not-shipped.zip`, SHA-256
`7c2da8750417a5a0b2e6371e1a56096b75b94a03eb226ce15347771a2d42851d`,
dans les preuves temporaires `build/reports/validation/m2-search-fix/coder/`.

Le contrat exploratoire ci-dessous est conservé comme contexte de cette alternative,
pas comme description du correctif minimal ou d'une fonction disponible.

Conserver la sélection et l'aperçu du JPEG, puis confier à Chrome à la fois le
téléversement et la navigation, après le bouton explicite. Une page locale éphémère
contient uniquement ce JPEG et prépare un formulaire standard via `File` et
`DataTransfer`. Il n'y a plus de POST Google natif suivi d'un transfert de son URL
de résultats vers un autre client. Aucun cookie n'est lu ou copié entre applications.

- Écoute uniquement sur `127.0.0.1`, port éphémère ; jamais sur toutes les interfaces.
- Capacité imprévisible de 256 bits dans le chemin, contrôle du Host et du chemin
  exact, GET seul ; aucune lecture du système de fichiers ou fonction de proxy.
- Une seule capacité active par propriétaire ; révocation de l'ancienne avant
  une nouvelle action et lors du changement de sélection/source avant remise.
- Expiration globale de 30 secondes, lectures de trois secondes, en-têtes bornés
  à 8 Kio et connexions bornées ; fermeture après réponse complète, erreur ou expiration.
- Pixels uniquement en mémoire, JPEG borné à 2 Mio, `no-store` et absence de referrer.
  CSP étroite : script avec nonce, images de données, formulaire vers Google Lens
  et origine Google nécessaire à sa redirection ; aucune origine arbitraire.
- L'ouverture de Chrome ne doit pas fermer la capacité lors de `ON_STOP` avant son
  GET. Un échec d'ouverture ferme la capacité. Les annulations précoces empêchent
  toute remise ou navigation tardive ; après remise à Chrome, l'envoi ne peut pas
  être rétracté par Blamera.
- Aucun serveur public, fichier de photo, nouveau SDK, service payant, permission
  supplémentaire ou changement aux modèles, catalogues ou paramètres de reconnaissance.
- Pas de cache d'une ancienne URL de résultats : chaque bouton explicite crée une
  capacité à usage unique pour la bonne image. Masque, zoom, recadrage et original
  restent dans la session locale.

La revue indépendante préalable juge ce design acceptable pour un essai correctif,
avec preuve navigateur locale obligatoire du multipart exact et d'une redirection
vers une seconde origine autorisée. Elle souligne que l'origine reste locale :
Google peut refuser ce formulaire même si l'envoi est exécuté par Chrome. Cette
méthode ne peut donc pas être déclarée fonctionnelle sur Google avant le retour réel.

L'alternative prévoyait un propriétaire dans le ViewModel et au plus huit
connexions pour absorber les préconnexions ou demandes invalides sans consommer
la capacité secrète. Le délai global fermera aussi un client accepté ou une sortie
bloquée, pas seulement le socket d'écoute. La validation doit le vérifier.

Elle aurait embarqué du JavaScript non mesuré par JaCoCo, nécessitant une preuve
navigateur distincte. Le correctif retenu n'embarque aucun JavaScript ni serveur.

## Validation attendue

Red fonctionnel puis Green sur le protocole direct : endpoint, paramètres publics,
champ fichier unique et octets JPEG exacts, sans donnée privée Chrome ni nom estimé.
Conserver les tests du client et du navigateur, des annulations et de la session photo.
Aucun nouvel appel exploratoire à Google ; les tests PC utilisent des frontières simulées.

Ensuite : revue indépendante du diff, contrôles globaux existants sans assouplissement,
testeur indépendant, APK 0.9.1/code 14 contrôlé et installation du seul APK principal
si le téléphone est disponible. W02 et la vraie recherche Google seront retentés
manuellement par l'utilisateur ; les preuves PC ne remplacent pas ce contrôle.

## Preuves ciblées et revue du correctif minimal

Le Red spécifique natif constate `/upload` à la place de `/v3/upload` avant correction.
Il est distinct des tests de la piste HTML abandonnée. Après correction, formatage,
detekt et 20 tests Android dans quatre classes passent, ainsi que huit tests du lecteur.
Le diff du coder est limité à cinq fichiers : requête et son test, version applicative,
identités exactes du lecteur et test associé. Les douze frontières de sécurité,
annulation, interface, session et permissions contrôlées restent identiques à la
baseline, à la normalisation de fins de ligne du manifeste près.
[Red natif](../../build/reports/validation/m2-search-fix/coder/red-native-direct-upload.log),
[Green natif](../../build/reports/validation/m2-search-fix/coder/green-native-direct-upload.log),
[contrôle du périmètre](../../build/reports/validation/m2-search-fix/coder/final-scope-results.json).

Une fixture générée par le code de production prépare un JPEG synthétique natif de
220 × 110 pixels et 908 octets. Un parseur MIME indépendant Python `email` et Pillow
confirme un seul champ `encoded_image`, fichier `image.jpg`, MIME `image/jpeg`,
pixels décodables, octets strictement identiques au JPEG de l'aperçu et absence EXIF.
Le corps multipart fait 1 074 octets ; les paramètres correspondent au contrat direct.
Ce contrôle ne fait aucun appel réseau.
[Résultat MIME indépendant](../../build/reports/validation/m2-search-fix/coder/native-mime-fixture/independent-mime-results.json).

La revue finale ne retient aucun blocage. Elle confirme le protocole source,
l'absence de code loopback livré et le maintien du rejet des réponses HTTP ou
adresses non admises. Elle distingue explicitement ces preuves PC de la recherche
Google réelle, qui reste à retenter par l'utilisateur après installation.

## Contrôle global et APK préparé

La commande `scripts/quality.ps1 -BaseRef 94dd2fa28125108ab6320409aabae5eb382b2777`
termine avec le code 0, douze étapes conformes : 208 tests Android sans échec,
erreur ou ignoré, 76 tests de scripts et quatre tests PC. Les rapports sont copiés
avant toute nouvelle validation ciblée pour conserver la preuve globale.
[Journal](../../build/reports/validation/m2-search-fix/quality.log),
[synthèse archivée](../../build/reports/validation/m2-search-fix/final-quality/quality/summary.json),
[tests XML](../../build/reports/validation/m2-search-fix/final-quality/android-tests),
[JaCoCo](../../build/reports/validation/m2-search-fix/final-quality/jacoco).

| Périmètre | Lignes globales | Branches globales | Lignes nouvelles | Branches nouvelles |
| --- | --- | --- | --- | --- |
| Android | 96,44 % | 85,66 % | 100 % | non applicable |
| Scripts | 94,35 % | 88,52 % | 100 % | non applicable |
| PC inchangé | 100 % | 100 % | non applicable | non applicable |

Android : trois lignes exécutables nouvelles, pas de branche nouvelle mesurable ;
duplication nouvelle 0/4 lignes analysables, Kotlin global 0,2381 %. Scripts : une
ligne analysable nouvelle, aucune duplication. Complexités aux limites 10/15, zéro
violation ; aucun seuil, exclusion ou configuration qualité n'est modifié.
Audit runtime : 97 packages Maven, 131 entrées de licence ; 35 packages PyPI PC,
35 entrées de licence ; aucun avis actif retourné dans ces scopes à ce passage.
Cette mesure ne couvre pas l'ensemble des outils privés de préparation ML.

APK `dist/blamera-0.9.1-debug.apk`, **457 587 683 octets**, SHA-256
`f191996d3ae152c5390e130cef32eafb5b7b15d8368a27456731d052c991da1d`.
Le testeur indépendant vérifie package `dev.blamera`, version 0.9.1/code 14,
SDK min/target/compile 36, arm64-v8a et certificat personnel inchangé
`eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555`.
Permissions exactes : CAMERA, INTERNET, permission interne AndroidX du package.
Les 25 actifs contrôlés et cinq ONNX sont identiques à 0.9.0, Large présent une
seule fois. Aucun asset HTML/JS ou classe expérimentale loopback n'est trouvé.
[Preuve du package](../../build/reports/validation/m2-search-fix/tester/package.json).

## Testeur indépendant et installation

Verdict **CONFORME_PC_ONLY** : 24 tests ciblés dans cinq classes, aucun échec,
erreur ou ignoré ; huit tests du lecteur réussis. Les 216 empreintes de sources,
tests et configuration ainsi que les 493 fichiers de preuve globale restent
inchangés pendant ce contrôle. Le multipart généré de nouveau a les trois mêmes
empreintes de fixture que le contrôle MIME initial. Les cinq identités de version
supportées conservent les 56 lignes et 437 paires du rapport historique ; dix
variantes invalides sont refusées. Ce rejeu n'est pas un nouveau diagnostic téléphone.
[Verdict et commandes](../../build/reports/validation/m2-search-fix/tester/tester-results.json).
Lint : zéro erreur, 36 avertissements maintenus, comme en 0.9.0 ; aucun seuil
affaibli pour ce correctif.

Après confirmation du Xiaomi cible unique, de la preuve indépendante, de la taille
et du SHA, **mise à jour principale réussie le 29 octobre 2026** : `adb -d install -r`,
sortie 0 « Performing Streamed Install / Success », avec conservation des données.
[Preuve d'installation](../../build/reports/validation/m2-search-fix/install-result.json).
Aucun lancement automatique, pilotage, capture d'écran, lecture de photos ou de
données d'autres applications, effacement ou changement de réglage. Le retour
humain est demandé après installation. L'utilisateur répond « non, toujours pareil » :
W02 échoue encore en 0.9.1, avec l'image cassée déjà décrite. Le changement du protocole
direct est conforme à la source, mais ne suffit pas à réparer l'intégration réelle.
Les hypothèses de contexte de navigateur restent ouvertes ; aucun nouveau POST
exploratoire Google n'est fait sur PC. La correction doit être poursuivie.
