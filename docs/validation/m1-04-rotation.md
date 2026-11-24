# M1-04 — paysage inversé, correctif 0.4.1

26 octobre 2026. **Revue et tester conformes sur PC ; APK prêt, installation et validation humaine des deux paysages en attente.**
Branche locale `codex/m1-camera-preview`, base `938b90c` (`HEAD` lors des métriques).
Aucune action téléphone, clé privée, réglage système, commit ou publication par le coder.

## Signalement et diagnostic

Retour réel 0.4.0 : « aperçu et transitions ok. quand je mets en paysage, l'interface ne
bascule que dans un sens (donc en paysage de l'autre côté, c'est à l'envers) ».
M1-04 reste ouvert malgré aperçu/transitions confirmés. L’[historique 0.4.0](m1-04.md)
conserve ses tests et sa signature ; il ne devient pas une validation de 0.4.1.

Constats PC : aucune politique screenOrientation explicite, aucun suivi de la rotation
du Display sur le Preview. Ils sont compatibles avec le défaut ; la cause exacte sur
ce Xiaomi/HyperOS n’est pas établie. Aucun réglage d’autorotation n’a été inspecté.

## Correction bornée

- `fullUser` permet les quatre orientations selon le choix d’autorotation/verrouillage
  existant. Aucun `fullSensor`, capteur brut, réglage système ou `configChanges` ajouté.
- Un écouteur du Display du PreviewView met à jour **le même Preview.targetRotation**,
  directement avec les constantes Surface 0/1/2/3, sans conversion en degrés.
- Initialisation si vue attachée, sinon lors de l’attachement ; 90→270 en place sans
  recréation ni rebind. Autres Displays ignorés. Pas de rotation Compose/View supplémentaire.
- Écouteur Display et attachement possédés par le handle, retirés sur fermeture idempotente
  et échec de création/binding ; garde contre callback tardif après close.
- Permissions, lifecycle, retry, import/analyse d’image et graph Preview seul conservés.
  Ni ML, modèle, seuil, catégorie, bibliothèque, SDK ou permission ajouté.

[Décision et sources Android officielles](../decisions/0004-camera-preview.md).

## TDD et tests PC réels

Red : `scripts/gradle.ps1 :app:testDebugUnitTest` avec filtres
`MainActivityLocalTest.activityAllowsFourUserOrientationsWithoutOverridingTheirRotationLock`
et `CameraXPreviewProviderTest.displayRotationChangesUpdateTheSamePreviewWithoutRebinding`,
**sortie 1, 2 échecs fonctionnels attendus** : ActivityInfo n’est pas FULL_USER et le Preview
ne suit pas le Display. Log `.gradle-user-home/m1-04-rotation-red.log`.

Green : mêmes tests, **sortie 0**, puis 10 tests ciblés (manifest/ActivityInfo, provider,
Display) avec format et detekt conformes, sortie 0. Logs `m1-04-rotation-green.log` et
`m1-04-rotation-checks.log` dans `.gradle-user-home/`.
Le test utilise les vrais DisplayManager/Display/Preview/View sous Robolectric, avec les
opérations matérielles bind/unbind simulées à la frontière déjà livrée.

Régressions : séquence 90→270→180→0→90 sur la même instance (un seul bind), vue initialement
non attachée puis attachée/réattachée, autre Display, callbacks tardifs, close répété,
rotation après erreur de binding, absence de configChanges orientation/screenSize.
Les rotations physiques et le comportement du verrouillage utilisateur sur HyperOS
ne sont pas simulés comme preuves matérielles ; nouvelle confirmation humaine nécessaire.

## Contrôles et APK

La commande commune `scripts/pc.ps1 Quality` est exécutée après vérification de la présence
de la clé personnelle existante : **sortie 0, 83 tests Android + 56 scripts + 4 PC verts**.
Log `.gradle-user-home/m1-04-rotation-quality.log`.

| Composant | Global lignes / branches | Nouveau lignes / branches |
| --- | --- | --- |
| Mobile | 94,29% / 87,56% | 100% / 91,67% (31 lignes exécutables) |
| Scripts | 94,44% / 86,36% | Non applicable, aucun changement |
| PC | 100% / 100% | Non applicable, aucun changement |

Duplication mobile nouveau **0/50=0%**, globale 0,35% ; scripts/PC 0%.
Complexités 10/15 conformes, aucun seuil ou exclusion changé. Format/types/build passent ;
lint : 0 erreur, 17 avertissements, dont `DiscouragedApi` sur `screenOrientation` dans
le manifeste. Cet avertissement générique Android 16 est conservé sans désactivation ;
il ne bloque pas les contrôles.
Les licences inchangées conformes et scans OSV réels Maven 99/PyPI 35 sans vulnérabilité active.
La politique de licences et les versions de dépendances ne changent pas.

APK `dist/blamera-0.4.1-debug.apk`, **79 962 807 octets**, SHA-256 :
`fd6dbe7ccfae17ba38e843c92def995170aeb80c75467a0bbc706037249a0da7`.
Aapt confirme version 0.4.1/code 7, package `dev.blamera`, min/target/compile 36, ARM64,
permissions exactement CAMERA + interne AndroidX, et fullUser (13/0xd) dans le manifeste
binaire. La classe de suivi est présente dans le DEX. Signature v2 vérifiée sortie 0,
certificat conservé `eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555`.
Modèle embarqué inchangé SHA `38325721a41fcd732b0a30512ffa8d564b1a8a74ca8c1d6b7e7f37609b2d321b`.
L’APK 0.4.0 conserve SHA `44ea438acf4e64bdf82bc202e3bea34e657830dc5a67dd3ac531ac8b8fcade1c`.
Preuves : `build/reports/validation/m1-04-rotation/apk.json`, badging/signature/orientation
et checksum `dist/`. Aucune modification applicative après cette globale.
Rapports locaux dans `build/reports/quality/` et `app/build/reports/`.

## Revue, validation indépendante et installation restante

Le 26 octobre 2026, revue indépendante **conforme**, sans correction supplémentaire.
Le tester a réellement réexécuté les **83 tests Android, sortie 0 (33 s)**, puis la
commande Quality : **sortie 0, 12 étapes**, dont **56 tests scripts et 4 tests PC**.
Les métriques ci-dessus sont confirmées, sans changement applicatif. Rapport local :
`build/reports/validation/m1-04-rotation/tester-results.json`.

L’orchestrateur a revérifié empreinte APK, métadonnées aapt et signature, sortie 0.
**La mise à jour 0.4.1 n’est pas installée** : la procédure s’est arrêtée avant toute
commande d’installation, faute d’appareil. L’inventaire ADB confirme 0 appareil autorisé,
0 offline et 0 unauthorized. Une demande de reconnexion a été envoyée à l’utilisateur ;
sa réponse est en attente. Aucun lancement, permission ou réglage n’a été effectué.

La mise à jour 0.4.1/code 7 conserve `dev.blamera`, SDK 36, ARM64, certificat et modèle.
Ancien APK 0.4.0 conservé. Après reconnexion, l’orchestrateur pourra installer seulement
le principal ; l’utilisateur ouvrira lui-même et vérifiera les deux paysages et leurs transitions.
Aucun pilotage, grant, capture, réglage d’autorotation ou lancement automatique.
M1-05, M1-06 caméra et M1-01 budgets/chauffe/prolongé restent ouverts.

Contrôles documentaires directs sortie 0 : UTF-8, liens locaux, 32 titres/statuts/ordre et
`git diff --check`. APK stable après vérification ; aucun code applicatif changé après
la globale. M1-04 reste ouvert : installation et validation inverse physique en attente.

Suite du 26 octobre : ce correctif est inclus sans changement dans l’APK 0.5.0/code 8
d’[analyse caméra](m1-05.md). La 0.4.1 n’a pas été installée ; une seule mise à jour
directe de la 0.4.0 vers 0.5.0 est prévue. Les deux paysages physiques restent à valider.
