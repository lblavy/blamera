# Diagnostic Android de capacité YOLOE — 0.6.0

État au 28 octobre 2026 : développement, revue/contre-revue indépendantes et qualité
locale conformes. **Tester indépendant final conforme** : 39 tests JVM ciblés et
16 tests Python réexécutés, APK/22 assets/certificat/empreinte vérifiés.
**APK 0.6.0/code 10 installé le 28 octobre 2026** par l’orchestrateur.
**Comparaison manuelle, réémission et mesures du diagnostic validées indépendamment**.
Aucune manipulation du téléphone par le codeur. [Résultats et limites](../research/m1-yoloe-capacity-phone-results.md).

## Périmètre

Le seul APK principal personnel compare baseline YOLO11n et YOLOE26 PF Small,
Large et Xlarge. Le modèle habituel, ses poids et les parcours image/caméra sont
conservés. Aucun encodeur Apple/CLIP, réseau, photographie privée ou apprentissage.
Le diagnostic est accessible en debug par action explicite, sans démarrage automatique.

Les graphes compacts nouveaux intègrent NMS : baseline par classe, PF agnostique,
seuil .35, IoU .5, maximum 20, **sans pré-cap 300**. Ce dernier point diffère de
l’étude de qualité sur 144 images : ces mesures ne remplacent pas son évaluation.
Les anciens modèles, études, résultats et APK sont conservés.

Les quatre exports et les 56 références PC proviennent du
[préparateur dédié](../../experiments/m1-yoloe-phone-assets/README.md).
Quatorze entrées : NASA, motif synthétique historique et les douze cas visuels
déjà fixés (huit LVIS, quatre ADE). Les JPEG ont été décodés puis conservés en PNG
sans perte ; les notices individuelles et AGPL sont embarquées, les images restent hors Git.

## Protocole réellement implémenté

- Ordre fixe baseline → Small → Large → Xlarge ; une session à la fois.
- Pour chaque modèle : NASA puis LVIS 371699, chacune premier appel + trois
  échauffements + dix mesures, puis une passe de qualité sur chacune des 14 entrées.
  Soit **168 appels natifs**, dont 112 de performance et 56 de qualité.
- Seul le premier appel NASA utilise une session neuve. Le premier appel de la
  seconde fixture utilise une session déjà échauffée ; le processus et les caches
  ne sont jamais annoncés froids ni contrôlés thermiquement.
- CPU ORT 1.31.0, deux threads intra / un inter, FP32 640², optimisation complète.
- `runNs` : durée écoulée `session.run`, inférence et NMS incluses.
  `copyNs` : copies des deux sorties vers Java. `postNs` : validation, logits,
  masques et vrais isocontours. `totalNs` : intervalle associé, tenseur déjà prêt
  jusqu’aux masques/contours prêts ; préparation, fermeture des Result et stockage
  du rapport exclus. Aucun FPS du parcours caméra ni temps NMS isolé n’est déduit.
- PSS du processus avant/après blocs, session ouverte, puis après fermeture du
  modèle : échantillons hors chronomètres, ni pics ni allocations du seul modèle.
- Qualité : noms anglais exacts, scores non calibrés, boîtes source/réseau, masques
  binaires160² MSB-first compressés et vérifiables. Les boîtes à masque vide sont
  conservées avec indicateur explicite ; aucun contour inventé.

## Ressources, annulation et récupération

Les modèles sont copiés et vérifiés par flux dans un cache propre à l’application,
puis ouverts par chemin ORT ; Xlarge n’est jamais chargé dans une grosse ByteArray.
Le verrou natif partagé avec l’analyse normale reste détenu jusqu’au retour natif,
y compris après annulation. Tenseurs, Result, sessions et options sont fermés dans
l’ordre ; l’environnement global n’est pas fermé.

Le dernier rapport est borné à 8 MiB, remplacé atomiquement dans les fichiers propres
à l’application. Le nouveau `runId` remplace immédiatement l’ancien état complet.
Une fin tardive d’un ancien run ne peut pas écraser un run plus récent.
Annulation et erreurs produisent des états explicites, jamais un faux succès.

La réémission manuelle emploie des fragments numérotés, un SHA-256 global et un
début/une fin, avec cadence de 5 ms. Le collecteur PC filtre le PID Blamera et le
tag propre ; aucune lecture privée, lancement, capture d’écran ou modification OS.
La [recette](../tests/m1-capacity-phone-recette.md) explique capture et analyse locale.
L’analyseur vérifie aussi l’index des références Kotlin et le catalogue source
épinglés dans [la configuration PC](../../config/benchmark-references.json), puis
le SHA et l’identité de chaque JSON de référence. Un mauvais dossier, une
substitution ou un index altéré sont rejetés avant tout appariement.

## Preuves TDD et contrôles intermédiaires

Journaux locaux : `build/reports/validation/m1-yoloe-capacity-phone/`.

| Comportement | Red réellement exécuté | Green / contrôle |
| --- | --- | --- |
| Archivage atomique, fraîcheur et corruption | `archive-red.log` | `archive-green-final.log`, 2 tests |
| Fragments Unicode numérotés, taille et hash | `transfer-red.log` | `transfer-green.log`, avec archivage |
| Ordre 168 appels / qualité après performance | `schedule-red.log` | `cache-red-schedule-green.log`, ordonnanceur vert |
| Masques crop, MSB, contours et boîtes vides | `mask-red.log` | `cache-mask-schedule-green.log` |
| Modèle streamé, corruption / taille / fermeture | `cache-red-schedule-green.log` | `cache-mask-schedule-green.log`, 4 tests |
| Rapport complet / annulé / erreur et réémission sans calcul | `stored-red-functional.log` | `document-stored-assets-green.log` |
| Capture, transport, métriques et appariement PC | `parser-red.log`, `metrics-red.log`, `compare-red.log`, `collect-red.log` | `python-complete.log`, puis contrôles finaux à consigner |

Les échecs d’infrastructure Gradle sandbox et de compilation intermédiaires ne
constituent pas des Red fonctionnels. Une première solution AtomicFile échouait
au remplacement sur Windows : le test l’a détectée ; la version finale utilise
un déplacement atomique dans le même dossier, sans repli non atomique.

Première commune (`quality-first.log`) : les 12 étapes outils sortent à 0,
132 tests Android passent, mais le gate du nouveau code échoue à **77,50 % de
branches**. Les cas catalogue/archivage invalides et la réémission ont été complétés,
sans exclusion ni baisse de seuil. Un Red additionnel (`contour-error-red.log`)
a montré qu’une limite de contour restait une exception non présentée ; sa
conversion en échec lisible est vérifiée par `branch-error-green.log`, sortie 0.
Une passe suivante a encore détecté une ligne de test trop longue et la complexité
du dispatch ; ces constats ont été corrigés, sans masquer les journaux intermédiaires.

Revue indépendante des sources et contre-revue des corrections : **conformes**.
La correction de provenance PC a son Red `reference-index-red.log`, puis son Green
`reference-index-final-green.log`. Le libellé des chronomètres UI a été précisé.
Le dispatch calcul/réémission a été séparé de la classification d’erreurs, après
un constat detekt à 10 décisions ; `review-corrections-green.log` passe avec
format, analyse statique et tests Controller/UI. Les seuils sont inchangés.

## Prétraitement et portée de la comparaison

Le diagnostic JVM a confirmé 14/14 SHA de pixels RGB identiques aux PNG PC. Six
tenseurs différaient par arrondis : NASA 681 valeurs sur 1 228 800, maximum 1/255 ;
LVIS 22892 et les quatre ADE avaient 1 à 72 différences. Le calcul NumPy gelé
interpole en float64 après coordonnées float32 ; l’application utilise float32.
Le pipeline habituel n’a pas été modifié. Un nouveau jeu de références dédié aux
tenseurs Kotlin exacts permet une comparaison utile ; les anciennes références
et leur provenance sont préservées. Tout écart de SHA du tenseur sur ARM sera
signalé avant d’attribuer des écarts à l’inférence native.

Incident de preuve intermédiaire : le test JVM réécrivait le manifeste de diagnostic
avec les six nouveaux SHA de référence du catalogue Kotlin. Les 14 tenseurs,
pixels et résultats numériques n’avaient pas changé. Le manifeste original a été
restauré octet pour octet par l’orchestrateur/agent assets, SHA
`61252eaff94278a91d08149d25ccccf99509b171412848532d04879925f99702`,
avec archive et trace explicite de récupération. Les tests écrivent désormais
uniquement dans `jvm-checks/run-*`, et distinguent référence NumPy initiale et SHA
Kotlin attendu. Aucun pin n’a été actualisé pour masquer l’incident ; aucune
réinférence n’était nécessaire pour cette différence de métadonnées.

Les comparaisons de masques emploient les mêmes graphes et coordonnées ; une
égalité de hash des masques ARM/PC n’est pas exigée. IoU et écarts de noms/boîtes
sont présentés séparément. Le petit sous-ensemble n’est pas une nouvelle AP ni
une preuve de qualité générale. Le choix du modèle reste ouvert, fondé d’abord
sur les gains de reconnaissance et de détourage, puis sur les coûts mesurés.

## Livraison et validation du diagnostic

La commande finale `.\scripts\pc.ps1 Quality` passe : **sortie 0, 12 étapes**,
journal `quality-confirmed.log`, synthèse `build/reports/quality/summary.json`.
135 tests Android JVM, 72 tests scripts et 4 tests PC ont réellement été exécutés.

| Mesure finale | Global | Nouveau code (référence `f8459cc`, HEAD au début de la campagne) |
| --- | ---: | ---: |
| Mobile, lignes / branches | 94,61 % / 86,79 % | 98,18 % / 85,64 %, 548 lignes exécutables |
| Scripts Python, lignes / branches | 94,23 % / 88,52 % | 93,95 % / 91,38 % |
| Duplication Kotlin | 0,168 % | 0 / 779 lignes analysables |
| Duplication scripts | 0 % | 0 / 562 lignes analysables |

Socle PC inchangé : 100 % lignes/branches, nouveau code non applicable.
Complexités conformes aux limites 10/15 ; lint **0 erreur, 18 avertissements**,
notamment mises à jour proposées et orientation explicite, sans désactivation.
Scans frais : 97 paquets Maven et 35 PyPI, aucune vulnérabilité active ;
131 entrées de licences Maven et 35 PyPI acceptées par les politiques existantes.

Copie locale finale **`dist/blamera-0.6.0-debug.apk`**, **457 503 753 octets**, SHA-256
`19eeed37127a2b80d43afc68fc7449cf466429bf9bbda93a4a66f329549e5794`.
`aapt` confirme `dev.blamera`, 0.6.0/code10, SDK min/target/compile36, arm64-v8a.
`apksigner verify --verbose --print-certs` sort à 0, signature v2 valide,
certificat conservé `eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555`.
Permissions exactement CAMERA et permission interne AndroidX ; aucun réseau/micro/media.
Les 22 feuilles contrôlées, catalogue et notices sont vérifiés dans le ZIP ; aucun
`.pt` ou tenseur `.f32le` embarqué. Le modèle habituel garde son SHA `383257…321b`.
L’APK 0.5.1 est conservé, SHA `fb6605…e4a4` inchangé.
Preuves locales : `apk-badging.txt`, `apk-permissions.txt`, `apk-signature.txt`,
`apk-verification.json` dans le dossier de validation du diagnostic.

Preuve du tester : `build/reports/validation/m1-yoloe-capacity-phone/tester-results.json`.
Après le précontrôle sans appareil conservé dans `install-preflight.json`,
l’orchestrateur a revérifié taille/empreinte puis mis à jour le seul APK principal
sur l’unique téléphone autorisé : `adb install -r`, sortie 0,
`Performing Streamed Install / Success`, le 28 octobre à 14:30 UTC (15:30 CET).
Preuve locale : `install-result.json`. Aucun lancement, réglage ou lecture de logs
n’a accompagné cette installation.

L’utilisateur a ensuite ouvert et démarré manuellement la comparaison, confirmé sa
fin puis la réémission finie. Lecture limitée aux PID/tag Blamera, collecteur et
analyse CLI sortis à 0 : 261 fragments et SHA validés, run
`3e014101-48b1-4e58-aad4-330c70ad576f`, commencé à 16:19:17.436 UTC,
420,25 secondes rapportées. **Tester téléphone indépendant conforme** :
`device/tester-phone-results.json`. Les 56 résultats de qualité et huit blocs sont
vérifiés ; les 24 échauffements configurés ne sont pas persistés individuellement.
Les 14 tenseurs concordent pour les quatre modèles ; 437 détections appariées,
aucune classe/nom différent, masques binaires 160² identiques, IoU 1. Cela ne prouve
ni la vérité des noms ni l’identité des isocontours interpolés ou des sorties natives complètes.
[Tableaux CPU/PSS et interprétation](../research/m1-yoloe-capacity-phone-results.md).

D01/D02 confirmés ; D03/D04 non exécutés dans cette recette. L’utilisateur rapporte
« Pas de chauffe gênante » sur ce seul run : aucun relevé thermique/batterie ni
validation de cadence durable. M1 et ses budgets restent ouverts ; modèle habituel
inchangé, CI distante toujours volontairement reportée.
