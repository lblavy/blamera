# Reconstruire l'analyse locale d'image — M1-03

## Parcours photo et adaptations 0.8.0

Le parcours [capture et exploration](decisions/0006-capture-analysis.md) utilise
YOLOE26 Large PF pour la photo capturée ou choisie, avec analyse automatique.
Le direct secondaire conserve le modèle minimal décrit dans l'historique ci-dessous.
L'état de livraison fait foi dans le [rapport de validation](validation/m1-capture-analysis.md).

`config/photo-model.properties` fixe les sources locales, tailles et SHA du graphe
compact, des 4 585 noms officiels et de la licence. Conserver les fichiers de
`models/m1-yoloe-phone-assets/` : modèle Large de 126 179 359 octets, SHA
`2e94f433084b40fb7d558355af7e50b4ff8a483c875a964e0d308a6984d779b8`,
`labels4585.json` et licence AGPL. Le
[préparateur expérimental](../experiments/m1-yoloe-phone-assets/README.md) documente
leur provenance ; ne pas remplacer les empreintes pour accepter un export différent.
Les modèles, images et références scientifiques restent des fichiers locaux ignorés.

`preparePhotoAssets` vérifie ces données et génère les assets main. Le diagnostic debug
partage les mêmes feuilles Large/libellés/licence sous leurs chemins historiques
`benchmark/` ; ses autres modèles et fixtures restent requis par le build debug.
Le ZIP ne contient qu'une copie du modèle Large. Les notices photo sont dans
`app/src/main/assets/photo/NOTICE.txt`.

Le runtime ouvre un fichier de cache vérifié en streaming, avec CPU ORT, deux threads
intra et un inter. Graphe compact FP32 640², score 0,35, NMS agnostique IoU 0,5 et
plafond 20 : ce nombre limite les sorties d'une image, tandis que le vocabulaire
contient 4 585 catégories possibles. L'anglais canonique du modèle reste inchangé pour
les références et le diagnostic. En 0.8.0, un catalogue statique français vérifié est
utilisé pour l'affichage ; il n'ajoute aucun modèle de traduction ou service réseau.
Les masques binaires 160² servent au toucher et au remplissage ; les contours interpolés
sont calculés dans le repère de l'image source. Le zoom ne relance pas l'inférence.
Le bouton explicite « Analyser cette zone » recadre les pixels visibles en une nouvelle
image de travail ; « Image entière » retrouve l'original et ses résultats sans inférence.
Le direct léger s'active dans le même aperçu caméra.

## Historique du modèle minimal

L’analyse image et ses contours validés 0.3.1 restent disponibles. L’incrément
**0.5.0 / code 8**, branche locale `codex/m1-live-segmentation`, réutilise exactement
le modèle et le moteur pour la caméra, sur demande. Développement, revue et tester PC
conformes ; APK principal installé le 27 octobre 2026 par mise à jour directe depuis
0.4.0, sortie 0. Aucun lancement automatique ; P*/O*/I* déclarés OK le 27 octobre,
avec contours bons, détection parfois erronée/incomplète et fluidité en mouvement
à améliorer. Il inclut le correctif 0.4.1, jamais installé séparément ; deux paysages
validés, prolongé/budgets/hors ligne encore ouverts. [ADR caméra](decisions/0005-live-camera-analysis.md),
[rapport PC](validation/m1-05.md), [recette manuelle](tests/m1-recette.md).
Les [preuves image 0.3.1](validation/m1-06-image.md) conservent la validation humaine
« Contours améliorés et bien placés ». [Décision image](decisions/0003-image-segmentation.md),
[validation historique](validation/m1-03.md), [installation protégée](install-android.md).

## Artefact modèle et fixture publique

Conserver le modèle contrôlé localement dans
`models/m1-01/yolo11n-seg/yolo11n-seg.onnx` : 11 720 021 octets,
SHA-256 `38325721a41fcd732b0a30512ffa8d564b1a8a74ca8c1d6b7e7f37609b2d321b`.
Le [spike et son lock](../experiments/m1-01/README.md) expliquent téléchargement officiel,
versions CPU et export. La recette ci-dessous restaure uniquement la date historique
du manifeste après un export temporaire avec le même environnement verrouillé.
Elle exige ensuite exactement la taille et le SHA attendus : ne pas changer l'empreinte
pour accepter un autre export. Cette restitution bit à bit a été vérifiée sur ce Windows,
pas sur toute plateforme ou avec d'autres versions.
`prepareVisionModel` échoue si l'artefact manque ou diffère. L'APK inclut sa copie générée
et les notices ; ni poids, ni image NASA, ni résultats ne sont ajoutés à Git.

Après préparation du checkpoint officiel et de l'environnement du spike, reconstruire
le modèle manquant sans relancer le benchmark ni réexporter sur le fichier original :

```powershell
& '.tools/m1-model-spike/.venv/Scripts/python.exe' experiments/m1-01/reconstruct_onnx.py --install-if-missing
if ($LASTEXITCODE -ne 0) { throw 'Reconstruction du modele en echec : arret.' }
```

La recette vérifie les versions et le SHA du checkpoint, copie ce checkpoint dans un
dossier temporaire sous `build/reports/validation/m1-03/model-reconstruction/`, exporte
FP32/opset17 et restaure seulement `metadata.date` du manifeste. Le modèle généré doit
retrouver exactement SHA `383257…321b`. `--install-if-missing` crée exclusivement un
ONNX absent après ces contrôles ; un fichier existant est vérifié et jamais écrasé.
Sans cette option, seule la preuve temporaire est produite. Les fichiers temporaires
et `result.json` restent locaux, ignorés. Toute autre divergence fait échouer la recette.

Les tests natifs requièrent `build/reports/validation/m1-01/astronaut.png`, image publique
NASA, et son masque de référence PyTorch. La recette du spike documente provenance et
empreinte de l'image. Avec cet environnement déjà préparé :

```powershell
& '.tools/m1-model-spike/.venv/Scripts/python.exe' experiments/m1-01/reference_mask.py
```

Cette recette exploratoire ne réexporte pas le modèle et ne lance aucun benchmark.
Elle écrit `astronaut-pytorch-mask.bin` (640² octets, SHA-256
`6820a73ee0e1db4ddbb90f39abaa50e030fb6fc6c4c97e31cda37ec62410ed5a`).
L'absence d'une fixture requise fait échouer le test, sans saut silencieux.

## JVM de test Windows isolée

Le JBR Android Studio 21.0.9 reste utilisé par Gradle et la compilation. Son MSVCP 14.29
provoque ici une violation d'accès dans le chargement JNI ORT 1.31.0. Le préflight indépendant
a validé **Temurin JRE 21.0.12.1+1**, dont le MSVCP chargé est 14.40.33810.0, sans changer
le JBR, Windows ou ORT. Préparer ce runtime local avant les tests :

```powershell
$blameraJreDir = '.tools/m1-ort-preflight/temurin'
New-Item -ItemType Directory -Force $blameraJreDir | Out-Null
$blameraZip = Join-Path $blameraJreDir 'temurin-jre.zip'
$blameraUrl = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jre_x64_windows_hotspot_21.0.12.1_1.zip'
if (-not (Test-Path -LiteralPath $blameraZip)) { Invoke-WebRequest $blameraUrl -OutFile $blameraZip }
if ((Get-FileHash $blameraZip -Algorithm SHA256).Hash -ne 'd35f31e712f0fcf6ac5a093edc90204fbff22f720ba3950bd09d331d5e621636') {
    throw 'Archive JRE differente : arret.'
}
Expand-Archive -LiteralPath $blameraZip -DestinationPath $blameraJreDir -Force
& "$blameraJreDir/jdk-21.0.12.1+1-jre/bin/java.exe" -version
```

[Release officielle](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1),
[checksum officiel](https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jre_x64_windows_hotspot_21.0.12.1_1.zip.sha256.txt).
Gradle choisit ce dossier par défaut sous Windows pour ses processus de test uniquement.
Un chemin alternatif se donne avec `BLAMERA_TEST_JAVA_HOME` ou `-PtestJavaHome=...`.
Sous Linux, les tests gardent le Java du build ; ce changement n'est pas une validation Linux.

## Contrôles et fabrication

Depuis la racine, vérifier la clé personnelle existante avant toute commande qui peut signer :

```powershell
$blameraKey = Join-Path $env:USERPROFILE '.android/debug.keystore'
if (-not (Test-Path -LiteralPath $blameraKey)) { throw 'Cle personnelle absente : arret.' }
.\scripts\pc.ps1 Quality
if ($LASTEXITCODE -ne 0) { throw 'Controles en echec : arret.' }
```

La commande conserve les gates et mesures séparées mobile/scripts/PC, licences et OSV.
Les rapports restent dans `build/reports/quality/` et `app/build/reports/`.
Le workflow distant reste volontairement reporté ; sa préparation requiert aussi l'artefact
ONNX contrôlé et la fixture. Aucun dépôt/hébergement du modèle ni transfert de clé n'est ajouté.

Après revue et validation PC, l'orchestrateur peut mettre à jour uniquement l'APK principal
avec le même certificat. L'utilisateur ouvre l'app, choisit Image, importe puis touche
**Analyser l’image**. Il vérifie catégories et traits sur l'image, remplace/annule, puis
tourne l'appareil et relance explicitement l'analyse si nécessaire. Les logs éventuels
doivent être filtrés au seul processus Blamera après lancement manuel. Aucune performance
ou détection réelle Android n'est déduite des tests Windows.


## Diagnostic YOLOE distinct — 0.5.1

Cette section conserve l’historique 0.5.1. Le diagnostic suivant
[0.6.0 baseline/Small/Large/Xlarge](validation/m1-yoloe-capacity-phone.md) prépare
quatorze images publiques, des mesures masques/contours et un rapport durable
réémissible ; le pipeline normal reste celui décrit ci-dessus.

Le parcours normal conserve son ONNX, ses classes/seuils et ses contours. Une Activity
interne debug permet une comparaison explicite CPU des deux graphes compacts sur NASA
et un motif local ; aucun accès aux photos choisies ou à la caméra dans ce diagnostic.
Avant un build debug, préparer aussi les assets contrôlés de
[la recette YOLOE](../experiments/m1-yoloe/README.md#préparation-du-diagnostic-android-distinct-051).
Le build échoue si un modèle/fixture manque ou diverge. Les actifs supplémentaires ne
sont pas dans le sourceSet release. [Protocole, chronos et limites](validation/m1-yoloe-android.md),
[recette manuelle](tests/m1-yoloe-comparaison.md). Revue ciblée/sécurité et tester PC conformes.
APK 0.5.1 installé le 27 octobre par mise à jour du seul APK principal depuis 0.5.0,
après une première procédure arrêtée faute de connexion. Lancements manuels et fins
confirmés, un run CPU/PSS Xiaomi vérifié indépendamment : [résultats et limites](validation/m1-yoloe-android.md#mesure-xiaomi-contrôlée-dans-les-logs--27-octobre-2026).
YOLO11n reste provisoirement par défaut ; choix qualité/vocabulaire ouvert, aucune substitution ni extrapolation FPS ou qualité générale.
