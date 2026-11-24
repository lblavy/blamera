# Installation personnelle Android

Cette procédure concerne uniquement l'APK **principal de développement personnel**
`dev.blamera`, Android 16/API 36. Aucune diffusion ni publication en store n'est prévue.
La version **0.10.2/code20 est installée**, après revue, qualité globale et testeur
conformes sur PC : vérification publique avant navigateur, métadonnées réseau
sans secrets et distinction HTTP5xx/service indisponible. Aucune permission,
protection TLS/OAuth ou activation de crédits modifiée. Globale : 304 tests Android,
76 scripts, quatre PC ; testeur : 76 ciblés et huit lecteur verts, package/certificat
et 37 assets/libs inchangés vérifiés. Mise à jour du seul APK principal réussie
le 30 octobre, `adb -d install -r`, sortie 0 « Success », sans lancement automatique.
L'échange de code réel et sa cause d'échec restent à établir sur Xiaomi.
APK : `dist/blamera-0.10.2-debug.apk`, 457 851 229 octets, SHA-256
`3468a8fd283410ecbb21e960f1f82996ede2ede068306ccb2ede769d27974afc`.
[Preuves réseau](validation/m2-chatgpt-network.md),
[recette](tests/m2-chatgpt-plus-recette.md).

Historique : la version **0.10.1/code19 a été installée**, après qualité globale, revue et testeur
indépendants conformes : diagnostic du retour OAuth bloqué en 0.10.0, préconnexions
locales abandonnées tolérées, étapes non secrètes visibles et délai global de cinq
minutes. Aucune nouvelle permission, clé ou activation de crédits. Globale : 295
tests Android, 76 scripts, quatre PC ; testeur : 67 tests ciblés et huit du lecteur,
package/signature/actifs vérifiés. Mise à jour du seul APK principal réussie le
30 octobre 2026, `adb -d install -r`, sortie 0, sans lancement automatique. La cause
sur Xiaomi et le succès du retour réel restent à vérifier manuellement.
APK : `dist/blamera-0.10.1-debug.apk`, 457 834 845 octets, SHA-256
`25ec5fac38654344b7b46a0298b0fc8d316b18bb3eb20cdcece1a67c63bd8462`.
[Preuves 0.10.1](validation/m2-chatgpt-callback.md),
[recette de reprise](tests/m2-chatgpt-plus-recette.md).

Historique : la version **0.10.0/code 18 a été installée**, après qualité globale, revue et testeur
indépendants conformes sur PC : essai ChatGPT Plus avec illustration synthétique,
consigne et réponse dans Blamera. Nouvelle permission INTERNET pour ce parcours
explicite ; aucun achat, clé API ou activation de crédits. La vérification humaine
du réglage serveur des crédits après limite est exigée avant l'envoi.
286 tests Android, 76 scripts et quatre PC passent ; le testeur confirme 58 tests
ciblés, huit du lecteur PC, package et signature. Mise à jour du seul APK principal
le 30 octobre 2026, `adb -d install -r`, sortie 0 « Success », sans lancement automatique.
L'utilisateur ouvre et teste l'application ; connexion et analyse réelles restent
en attente. APK : `dist/blamera-0.10.0-debug.apk`, 458 097 128 octets, SHA-256
`5024db6ac5250da744cb1cac5b8cea1f46ed71cfe96a5c0d02760220cafefb77`.
[Preuves 0.10.0](validation/m2-chatgpt-plus-trial.md),
[recette](tests/m2-chatgpt-plus-recette.md).

Historique : le diagnostic **0.9.4/code 17 a été installé et validé sur PC** : même consigne dans
EXTRA_TEXT et l'élément ClipData portant l'image, selon la représentation publique
AndroidX. 233 tests Android, 76 scripts et quatre tests PC réussis ; revue, package
et testeur indépendant conformes (67 ciblés, huit tests du lecteur). Après le
préflight sans appareil du 29 octobre, reconnexion et mise à jour du seul APK
principal réussie le 30 octobre 2026 : sortie 0 « Success », taille et empreinte
revérifiées, sans lancement automatique ni autre mutation. Retour humain 0.9.4 :
image reçue, texte toujours absent. Réception conjointe non validée ; le bouton
demeure « Tester Gemini » et aucune nouvelle installation n'est demandée à ce stade.
[Preuves 0.9.4](validation/m2-gemini-clipdata.md),
[recette](tests/m2-gemini-share-recette.md).

Historique 0.9.3/code 16 : installé et validé sur PC, essai du transfert du
recadrage et d'une consigne fixe à l'application Gemini. « Tester Gemini » reste
provisoire jusqu'à réception conjointe confirmée. 232 tests Android, 76 scripts et
quatre tests PC réussis ; testeur indépendant conforme sur 66 tests ciblés et huit
tests du lecteur, package et signature vérifiés. Mise à jour du seul APK principal
le 29 octobre 2026 : sortie 0 « Success », taille et empreinte revérifiées, sans
lancement automatique ni autre mutation. Retour humain : « Image reçue, consigne
absente » ; transfert conjoint non validé. La variante publique 0.9.4 est prête.
Pas de nouvelle permission, modèle, dépendance ou API facturée. La réponse se lit
dans Gemini. [Preuves](validation/m2-gemini-share.md),
[recette de l'essai](tests/m2-gemini-share-recette.md).

Historique 0.9.2 : installé et validé sur PC, remise native du recadrage à
Google Lens par `ACTION_SEND`, fichier temporaire privé et lecture seule. Le client
HTTP et `INTERNET` sont retirés ; aucune nouvelle dépendance ni permission.
Revue indépendante sans blocage ; 216 tests Android, 76 scripts et quatre PC réussis.
Testeur indépendant conforme sur 35 tests ciblés et huit tests du lecteur ; package vérifié.
Mise à jour du seul APK principal le 29 octobre 2026 : `adb -d install -r`, sortie 0
« Success », taille et empreinte revérifiées. Aucun lancement automatique ni autre
mutation du téléphone. APK local `dist/blamera-0.9.2-debug.apk`. Après l'essai demandé,
l'utilisateur répond « ça a l'air de marcher » : parcours Lens essentiel accepté,
sans validation détaillée de chaque variante de la recette.
[Preuves du correctif](validation/m2-lens-handoff.md),
[recette recherche visuelle](tests/m2-object-actions-recette.md).

Historique 0.9.1 : qualité PC, revue et testeur conformes ; mise à jour du seul APK
principal le 29 octobre 2026, sortie 0 « Success », taille et empreinte revérifiées,
sans lancement automatique. Le transfert fichier direct `/v3/upload` reproduit
encore l'image cassée d'après le retour humain. [Diagnostic](validation/m2-search-fix.md).

Historique 0.9.0 :
L'APK **0.9.0/code 13 est installé et validé sur PC**, avec recherche Google par image après
appui explicite et aperçu de la zone transmise. Qualité globale réussie et revue
sans blocage ; testeur indépendant et package conformes sur PC. Après reconnexion,
mise à jour du seul APK principal le 29 octobre 2026, sortie 0 « Success », taille
et SHA revérifiés. Aucun lancement automatique ; retour humain ci-dessous.
Le retour humain révèle ensuite une image cassée dans Google, alors qu'un import
direct dans Chrome fonctionne ; le correctif 0.9.1 répond à cet échec W02.
La permission `INTERNET` était nécessaire à cet ancien envoi ; elle est retirée
en 0.9.2. Vision et traduction restent locales.
[Preuves 0.9.0](validation/m2-object-actions.md),
[recette recherche visuelle](tests/m2-object-actions-recette.md).

Historique :
La version **0.8.0/code 12 est installée et validée sur PC** : direct dans l'aperçu courant,
analyse du cadrage zoomé avec retour à l'original, noms français hors ligne.
Revue, qualité globale et testeur indépendants conformes. Après reconnexion USB,
mise à jour du seul APK principal le 29 octobre 2026 : `adb -d install -r`, sortie 0
« Success », avec taille et SHA revérifiés. Aucun lancement automatique. Le même jour,
l'utilisateur confirme « Tout fonctionne » pour le direct intégré, les noms français
et l'analyse de zone avec retour à l'original. Les variantes complémentaires et les
essais prolongés ou hors ligne restent à vérifier.
[Preuves et APK](validation/m1-photo-refinements.md),
[recette ciblée](tests/m1-photo-refinements-recette.md).

La précédente version **0.7.0/code 11 a été installée et validée sur PC** : capture ou galerie,
analyse automatique Large, exploration et sélection de masque. Revue indépendante,
qualité globale et testeur conformes. Après reconnexion USB, mise à jour du seul APK
principal le 28 octobre 2026 : `adb -d install -r`, sortie 0 « Success », taille et SHA
revérifiés avant installation. Aucun lancement automatique ; recette manuelle en attente.
[Preuves et APK](validation/m1-capture-analysis.md),
[recette du nouveau parcours](tests/m1-capture-analysis-recette.md).

La précédente version **0.6.0/code 10 avait été installée le 28 octobre 2026** : mise à jour
du seul APK principal, `adb install -r`, sortie 0 `Success`, après vérification
de taille et d’empreinte. Aucun lancement ou autre accès téléphone pendant cette
installation. L’utilisateur a ensuite lancé la comparaison et réémis le résultat
manuellement ; capture et mesures du diagnostic validées indépendamment.
[Protocole et preuves](validation/m1-yoloe-capacity-phone.md),
[résultats CPU/PSS et limites](research/m1-yoloe-capacity-phone-results.md).

Historique : **0.5.1 / code 9**, installée le 27 octobre 2026 : un diagnostic CPU
explicite « Comparer les modèles », sans remplacement du modèle des parcours habituels.
[Preuves de préparation](validation/m1-yoloe-android.md), [recette](tests/m1-yoloe-comparaison.md).
Revue ciblée/sécurité et tester indépendants conformes sur PC. Une première procédure
s’était arrêtée avant `adb install`, faute d’appareil connecté. Après reconnexion,
l’orchestrateur a revérifié identité/hash/signature puis mis à jour le seul APK principal
sur l’unique appareil autorisé : `adb install -r`, sortie 0, `Performing Streamed Install / Success`.
Aucun autre accès téléphone, lancement, réglage ou logs pendant cette installation.
L’utilisateur a ensuite lancé manuellement le diagnostic et confirmé sa fin ; un run
complet CPU/PSS a été capturé aux seuls PID/tag Blamera et vérifié indépendamment.
[Mesures et limites](validation/m1-yoloe-android.md#mesure-xiaomi-contrôlée-dans-les-logs--27-octobre-2026).
Annulation, rotation/retour et validation générale de M1 n’en sont pas déduites.
La version précédente **0.5.0 / code 8** avait livré analyse caméra et contours sur demande,
avec correctif des deux paysages inclus. Installation historique le 27 octobre 2026
par mise à jour directe du seul APK principal depuis 0.4.0/code 6, sortie 0 `Success`.
L’aperçu et les transitions 0.4.0 étaient confirmés ; le retour O* OK du 27 octobre
sur 0.5.0 valide désormais les deux paysages. La 0.4.1 n’a jamais été installée séparément. Revue et tester PC conformes ;
package, empreinte et certificat revérifiés juste avant installation. Aucun lancement,
permission/réglage, manipulation, capture ou log par l’orchestrateur après la mise à jour.
Retour humain du 27 octobre : P*/O*/I* OK déclarés, avec limites de détection et
fluidité ; essai inférieur à 5 min sans chauffe ressentie. Prolongé et hors ligne
restent à valider. Ouverture et manipulations sont manuelles par l’utilisateur.
[Recette manuelle](tests/m1-recette.md). Aucun réglage système modifié ;
fullUser respecte le verrouillage existant. Les contours image 0.3.1 gardent leur
validation humaine « Contours améliorés et bien placés ».
Copie historique contrôlée : `dist/blamera-0.5.1-debug.apk`, 103 753 509 octets,
SHA-256 `fb660502b8ed799de399852704af2e19d3e370f8464687788d7f22bb56fee4a4`.
L’ancien APK 0.5.0 (SHA `8e9d1e0737dd83c512a29bfcc30377695d6475919bcc5cf900cb7233496b4731`)
est conservé ; cet historique précède la mise à jour réussie vers 0.5.1.
Empreinte à revérifier avant installation ; anciens APK conservés, même certificat.
[Preuves caméra 0.5.0](validation/m1-05.md), [correctif rotation](validation/m1-04-rotation.md),
[historique caméra 0.4.0](validation/m1-04.md), [historique image 0.3.1](validation/m1-06-image.md).
Voir les [preuves M1-03](validation/m1-03.md), les [prérequis modèle/JVM](image-segmentation.md),
les [preuves M1-02](validation/m1-02.md), l'[historique M0-04](validation/m0-04.md)
et les [prérequis](development-environment.md).

## Clé et vérifications avant fabrication

Le diagnostic local **0.9.4/code 17** ajoute la consigne à l'élément ClipData
portant l'image, en conservant EXTRA_TEXT. Les commandes ci-dessous ciblent cet
APK. La qualité globale passe sur 233 tests Android, 76 scripts et quatre tests PC ;
package, signature et testeur indépendant conformes sur 67 tests ciblés et huit
tests du lecteur. La mise à jour principale est installée le 30 octobre ; la
réception réelle a été essayée : seule l'image arrive dans Gemini, sans consigne.
M2-05 reste non validé ; les commandes ci-dessous servent à reconstruire le diagnostic,
sans présenter ses tests PC comme une réussite de l'intégration réelle.
[Preuves et limites](validation/m2-gemini-clipdata.md),
[recette manuelle](tests/m2-gemini-share-recette.md).
Copie finale : `dist/blamera-0.9.4-debug.apk`, 457 621 726 octets, SHA-256
`4aa3ae5431ced75706738d9ad5f91f55cf798bfce5856cd5ab495f0425609048`.
L'APK 0.9.3 est conservé : 458 002 233 octets, SHA-256
`03810d5a8c76fe231e82b4a5fca8c6c6ffabbda1866a08a2fa15dd4cac202a40`.
L'APK 0.9.2 est conservé : 457 604 689 octets, SHA-256
`2ddb8a5702a0f48fca5ccd01d908605b214e074d7b895074ca3a8e885d62fd35`.
L'APK 0.9.1 est conservé : 457 587 683 octets, SHA-256
`f191996d3ae152c5390e130cef32eafb5b7b15d8368a27456731d052c991da1d`.
L’APK 0.9.0 est conservé : 457 587 683 octets, SHA-256
`4a5a7fb78eaeb3bb6aeaf79fa37aada91b366dd283c74a0afb3147f7dcb6b0ba`.
L’APK 0.8.0 est conservé : 457 521 459 octets, SHA-256
`f76353d56f1716b4e1393b70e246619b545012921d0c99bff7617e6defb491e8`.
L’APK 0.7.0 est conservé : 457 888 580 octets, SHA-256
`73d011647e4e67633a45f3d19bd2978a904188661f9bab45f2b8062e1f35ed49`.
L’APK 0.6.0 est conservé : 457 503 753 octets, SHA-256
`19eeed37127a2b80d43afc68fc7449cf466429bf9bbda93a4a66f329549e5794`.

La clé debug personnelle existante doit rester disponible localement, hors Git et CI.
Ne pas la remplacer, la régénérer, l'exporter ou la copier dans le dépôt. Sa perte empêcherait
une mise à jour avec ce certificat ; une désinstallation ne constitue pas une solution autorisée.
L'APK est débogable : il convient à cet usage personnel, pas à une distribution commerciale.
Une construction CI avec une autre clé debug ne peut pas servir de mise à jour personnelle.

Empreinte SHA-256 du certificat de la version déjà installée, référence publique à conserver :

```text
eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555
```

Depuis la racine du dépôt, dans PowerShell, vérifier la présence de la clé **avant** le build.
Si le chemin standard a été personnalisé, retrouver la clé existante avec l'utilisateur ;
ne pas laisser Gradle en créer une nouvelle par défaut.

```powershell
$blameraKey = Join-Path $env:USERPROFILE '.android\debug.keystore'
if (-not (Test-Path -LiteralPath $blameraKey)) { throw 'Cle personnelle absente : arret.' }
.\scripts\pc.ps1 Quality
if ($LASTEXITCODE -ne 0) { throw 'Qualite ou build en echec : arret.' }
```

Cette commande inclut `assembleDebug`. Pour reconstruire uniquement l'APK après une
validation qualité toujours applicable : `.\scripts\gradle.ps1 :app:assembleDebug`.
Le Wrapper, le catalogue et `.gitattributes` fixent versions et fins de ligne LF ;
la signature utilise la clé locale existante. Le hash du fichier est à recalculer après
chaque fabrication : reproductibilité de la procédure ne signifie pas APK bit à bit garanti.

## Contrôler puis préparer la copie locale

Les commandes suivantes lisent des fichiers sur PC. Adapter seulement les chemins du SDK/JDK.
Contrôler la sortie de `aapt` : package `dev.blamera`, version `0.9.4`/`17`,
SDK min/target/compile `36`, label `Blamera`. Permissions attendues : CAMERA
et permission interne AndroidX seulement ; aucun INTERNET, état réseau, micro ou accès
global media/stockage. Arrêter au moindre écart de package,
de version attendue ou de certificat. `apksigner verify` vérifie la signature et
`--print-certs` fournit son empreinte. [Référence officielle](https://developer.android.com/tools/apksigner).

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$blameraSdk = Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk'
$blameraTools = Join-Path $blameraSdk 'build-tools\36.0.0'
$blameraApk = '.\app\build\outputs\apk\debug\app-debug.apk'
& "$blameraTools\aapt.exe" dump badging $blameraApk |
    Select-String '^package:|^sdkVersion:|^targetSdkVersion:|^application-label:'
if ($LASTEXITCODE -ne 0) { throw 'Metadonnees illisibles : arret.' }
& "$blameraTools\aapt.exe" dump permissions $blameraApk
if ($LASTEXITCODE -ne 0) { throw 'Permissions illisibles : arret.' }
$blameraSignature = & "$blameraTools\apksigner.bat" verify --verbose --print-certs $blameraApk
if ($LASTEXITCODE -ne 0) { throw 'Signature invalide : arret.' }
$blameraSignature
$blameraExpectedCert = 'eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555'
$blameraDigest = @($blameraSignature | Select-String '^Signer #\d+ certificate SHA-256 digest: (\w+)$')
if ($blameraDigest.Count -ne 1 -or
    $blameraDigest[0].Matches[0].Groups[1].Value -ne $blameraExpectedCert) {
    throw 'Certificat different de la version installee : arret.'
}
# Apres controle humain des metadonnees ci-dessus :
New-Item -ItemType Directory -Force '.\dist' | Out-Null
Copy-Item -LiteralPath $blameraApk -Destination '.\dist\blamera-0.9.4-debug.apk'
Get-FileHash '.\dist\blamera-0.9.4-debug.apk' -Algorithm SHA256
```

`dist/` et les builds restent ignorés par Git. Seuls le checksum, les métadonnées publiques
et les constats nécessaires sont recopiés dans les rapports ; aucun secret ou identifiant
du téléphone n'y figure. Ne plus modifier l'APK signé après vérification.

## Mise à jour strictement limitée

L'orchestrateur réalise cette étape après revue et validation PC ; elle n'a pas été
exécutée par l'agent de code. Utiliser le téléphone cible déjà identifié localement,
sans changer son débogage, ses permissions ou ses paramètres.
`install -r` réinstalle en conservant les données selon la [documentation ADB](https://developer.android.com/tools/adb#move).

```powershell
$blameraSerial = '<identifiant cible conserve localement>'
& "$blameraSdk\platform-tools\adb.exe" -s $blameraSerial install -r '.\dist\blamera-0.9.4-debug.apk'
if ($LASTEXITCODE -ne 0) { throw 'Installation refusee : arret et retour utilisateur.' }
```

En cas de `INSTALL_FAILED_USER_RESTRICTED`, signature incompatible ou refus de downgrade,
arrêter et décrire l'erreur à l'utilisateur. Ne pas contourner par réglages de sécurité,
option de downgrade, désinstallation, effacement de données ou remplacement de clé.
Pas de test APK, instrumentation, lancement automatique, force-stop ou pilotage ADB.
L'utilisateur ouvre Blamera manuellement. La caméra est l'entrée par défaut ; il décide
lui-même de l'autorisation Android si elle est demandée. « Capturer » et « Galerie »
ouvrent une image stable analysée automatiquement. Vérifier attente animée et panneau,
orientation, remplacement et annulation, zoom/déplacement, sélection/désélection du
masque et nom de catégorie en français. « Analyse en direct » active les contours dans
le même aperçu. Après zoom, « Analyser cette zone » puis « Image entière » permettent
d'éprouver le recadrage et le retour à l'original sans nouvelle analyse.
Suivre la [recette 0.8.0](tests/m1-photo-refinements-recette.md).
Sur 0.9.2, reprendre si possible une photo dont la recherche échouait en 0.9.0/0.9.1,
sélectionner un objet puis « Rechercher cette image » doit ouvrir Lens directement
avec le bon recadrage et ses résultats ; aucun nouvel import de galerie ne doit être
nécessaire. Le retour doit retrouver la même image, le zoom et la sélection. Relever
le message exact en cas de partage indisponible ou refusé.
Voir la [recette Lens](tests/m2-object-actions-recette.md).
Sur le diagnostic 0.9.4, faire défiler la fiche puis toucher « Tester Gemini » :
vérifier avant toute saisie ou copie manuelle le bon recadrage et la consigne
préremplie ensemble dans Gemini. La soumission reste manuelle. Aucun bouton définitif
« Demander à Gemini » n'est annoncé avant ce succès réel.
Voir la [recette Gemini](tests/m2-gemini-share-recette.md).
Consigner le retour sans inventer de preuve physique ni lire les photos personnelles.

Si nécessaire, après ce lancement manuel, lire uniquement les logs du PID Blamera :

```powershell
$blameraAppPid = (& "$blameraSdk\platform-tools\adb.exe" -s $blameraSerial shell pidof -s dev.blamera).Trim()
if ($blameraAppPid -match '^\d+$') {
    & "$blameraSdk\platform-tools\adb.exe" -s $blameraSerial logcat --pid=$blameraAppPid -d -t 100
}
```

Sans PID, laisser la preuve en attente ; aucune collecte globale de logcat.
Les restrictions complètes d'[AGENTS.md](../AGENTS.md) restent applicables.
