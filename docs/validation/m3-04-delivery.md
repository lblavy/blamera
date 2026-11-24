# M3-04 — qualification intégrée et livraison personnelle

2 novembre 2026, baseline locale `83d3645`. Version cible Android 0.17.0/code31.
La demande utilisateur remplace la recette de copie/import JSON par une APK
personnelle qui fournit directement la galerie évaluée.

## État qualifié

Revue indépendante conforme, y compris le correctif qui mémorise une cardinalité
incompatible avant tout nouveau chargement natif. Les annulations et erreurs I/O
restent réessayables. Les preuves Red réelles et les étapes Green sont dans les
[rapports UI](m3-04-ui.md) et [runtime](m3-04-runtime.md).

Commande globale exécutée, sortie **0** :

```powershell
$env:ORG_GRADLE_PROJECT_faceNativeCoverage = '<EC frais emu-reviewed/coverage.ec>'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\quality.ps1 -BaseRef 83d3645
```

Les variables TEMP/TMP du processus pointaient vers un dossier temporaire dédié
sous `build/tmp`. Aucun réglage persistant, seuil ou exclusion n'a été modifié.
La donnée native provient exclusivement de l'émulateur PC `emulator-5580`.

| Périmètre | Lignes | Branches |
| --- | ---: | ---: |
| Android global | 97,22 % | 86,90 % |
| Android nouveau code, 1038 lignes exécutables | 96,44 % | 84,93 % |
| Galerie/décision/contrôleur facial | 98,05 % | 85,71 % |
| Runtime facial natif | 95,57 % | 83,97 % |
| Contrôleur de photo faciale | 100 % | 85 % |
| Overlay facial | 100 % | 95,24 % |
| PC global | 99,50 % | 97,59 % |
| PC nouveau code | 99,03 % | 96,50 % |

**584 tests Android, 154 tests PC et 76 tests des outils qualité passent**.
Duplication nouvelle nulle : 0/1570 lignes Android et 0/969 lignes PC. Complexités
conformes aux limites 10/15. Audits : 101 paquets Maven/135 entrées de licence et
36 paquets PC/36 licences ; aucun avis de vulnérabilité actif retourné.
Les scripts qualité inchangés ont une couverture globale de 94,47 %/88,52 % ;
leur nouvelle couverture est non mesurée car aucune ligne nouvelle.

Archives locales : `build/reports/validation/m3-04/global-quality/archive/`,
comprenant rapports, JUnit, données JVM/native, APK générique ARM64 et 385 empreintes
de sources/configurations. Aucun avertissement de classes JaCoCo incompatibles.
La revue a vérifié les 385 empreintes et les métriques archivées.

## Validation indépendante et déploiement

Le lot ciblé du testeur indépendant a rencontré une saturation du disque pendant
l'extraction native Robolectric. Cet échec d'infrastructure est conservé dans
`mobile-tester/failed-disk-full/` et ne constitue pas un Red fonctionnel.
Trois anciennes APK générées et le TEMP de la globale terminée ont été supprimés
après vérification de leurs chemins ; reçus, couverture, classes, rapports et
dernier APK natif conservés. Aucune photo ou galerie supprimée. La relance ciblée
identique passe : **80 tests, aucun échec, erreur ou test ignoré**, sortie 0.
Le testeur indépendant confirme le logiciel et les 385 empreintes inchangées.
Le saut d'horloge de l'hôte pendant ce passage n'est pas une mesure de latence de
l'application. Rapport : `build/reports/validation/m3-04/mobile-tester/report.md`.

APK personnelle construite avec les paramètres explicites :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\gradle.ps1 assembleDebug -PfaceGalleryPath=C:/dev/blamera/pc/data/exports/blamera-face-gallery.json -PfaceTargetAbi=arm64-v8a -PfaceInstrumentedCoverage=false --console=plain --no-daemon
```

Build sortie 0 en 50 s, version 0.17.0/code31, ARM64 seul. Artefact privé local :
`build/outputs/blamera-personal-0.17.0.apk`, 682790109 octets, SHA-256
`e5c6b484157ce630a3f4e2f2bb24a4a10f9c7f1c5993632c9c11c2decc448963`.
La galerie embarquée est identique octet pour octet à l'export privé : deux
prototypes, cinq références, politique 0,30/0,50/0,05. Les 65 entrées assets,
bibliothèques, code DEX et manifeste du build générique restent identiques ; seul
le payload `assets/faces/bootstrap.json` est ajouté. Sept poids/notices aux
empreintes approuvées. Aucune photo personnelle ni référence individuelle ajoutée.
La signature est vérifiée avec le JBR du projet : sortie 0, certificat debug
SHA-256 `eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555`.
Le premier appel au wrapper système apksigner, resté en attente, a été interrompu ;
le contrôle effectif utilise le JBR et le même JAR du SDK. Aucun réglage système changé.

Preuves sous `build/reports/validation/m3-04/personal-deployment/` : build,
reçu de packaging sans données personnelles, script d'inspection, signature.
Après reconnexion confirmée par l'utilisateur, **Blamera 0.17.0/code31 est installée**
sur le Xiaomi le 2 novembre 2026 à 12 h 26, heure de Paris. L'empreinte de l'APK
personnelle est vérifiée avant l'appel explicite
`adb -s <serial-du-Xiaomi> install -r` : sortie 0, « Success ».
La mise à jour conserve les données. Aucun lancement automatique, transfert séparé
de JSON, lecture de stockage privé ou installation de test APK. Reçu
`personal-deployment/installation.json` ; état antérieur sans appareil conservé
dans `installation-before-reconnection.json`.

La recette utile est désormais [le parcours sur photo](../tests/m3-face-recognition-recette.md),
sans manipulation JSON. L'inférence est locale, limitée à un seul visage ; les
photos de référence ne sont pas embarquées, seulement les deux prototypes issus
des cinq références. La galerie PC reste intacte.

## Limites matérielles

Les trois cas publics passent sur émulateur x86_64 : boîtes/repères/MAE RGB à zéro,
cosinus PC/Android 0,9999999999917017 et décisions identiques pour les prototypes
publics P/−P. Ce petit jeu ne démontre pas la précision générale ou la parité près
des seuils sur ARM64 ; les règles de décision ont leurs propres tests unitaires.
Les seuils 0,30/0,50/0,05 restent ceux du gel PC, sans réglage sur le test final.

Le moteur refait l'autocontrôle public à la première utilisation de chaque instance
sur le Xiaomi. Validation du JNI ARM64, latence réelle et retour utilisateur restent
ouverts avant son lancement manuel. Les instantanés PSS ne mesurent pas le pic
mémoire ; le budget de pic reste non mesuré. Aucun test APK ni test instrumenté
n'a été installé ou lancé sur le téléphone personnel.

Retour humain après installation : « ça a l'air ok pour des personnes connues ou
inconnues ». Le parcours essentiel est accepté sur le Xiaomi. Une tentative de
lecture des diagnostics propres à Blamera n'a pas trouvé de PID exploitable ;
aucun log ni métrique chiffrée collecté. Latence et pic mémoire restent donc non
mesurés sur le téléphone. La demande suivante étend le parcours à plusieurs
visages avec des labels seuls, sans cadres faciaux, dans M3-05.
