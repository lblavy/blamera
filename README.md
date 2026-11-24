# Blamera

Application Android personnelle de vision par ordinateur, avec détection et
segmentation locales, identification par ChatGPT et recherche visuelle avec Lens.
Elle est dédiée au Xiaomi 14T Pro sous Android 16, sans publication sur un store.

La **0.18.0/code32** affiche les noms reconnus de plusieurs personnes sur une
photo à partir d'une galerie importée, en complément de la segmentation locale
et des recherches sur l'image. Le parcours essentiel est validé sur Xiaomi.
Voir le [backlog](backlog.md) et le [bilan M3](docs/validation/m3-06-device-measurements.md).

## Parcours photo

1. Capturer depuis la caméra ou importer via l'icône Galerie dans l'en-tête.
2. L'analyse locale automatique affiche catégories et contours. Avec une clé
   Pl@ntNet configurée, une identification botanique distante s'exécute en parallèle,
   sans choix de mode. Zoomer/déplacer l'image et sélectionner éventuellement un masque.
3. La cible est automatique : le recadrage de l'objet si un masque est sélectionné,
   sinon les pixels du cadrage visible. Un second appui ou un appui vide désélectionne.
4. Utiliser la barre **Analyser · ChatGPT · Lens** sur cette même cible. Analyser
   traite la zone avec les deux analyses disponibles et permet de retrouver
   l'original depuis l'en-tête.
   ChatGPT reçoit le JPEG et une consigne française, avec réponse Markdown en
   streaming ; un indice botanique assez étayé est ajouté seulement pour le même
   cadrage. Lens reçoit l'image par partage Android.
5. La roue dentée ouvre les réglages ChatGPT (modèle, réflexion, compte) et la clé
   Pl@ntNet. Connexion et clé sont conservées dans le stockage protégé.

Les boutons ChatGPT et Lens déclenchent le transfert du cadrage choisi. Avec
Pl@ntNet configuré, une capture, un import confirmé ou une demande d'analyse
envoie aussi le cadrage à Pl@ntNet. La galerie faciale et la reconnaissance des
personnes restent locales.

[Obtenir la clé Pl@ntNet gratuite et configurer l'analyse commune](docs/plantnet.md).
Une requête identifie une plante dominante ; les alternatives ne constituent pas
un inventaire des plantes de la photo. YOLOE reste utilisable sans clé ni réseau.

[Étude UI/UX et consignes exactes](docs/specs/photo-actions.md),
[cible automatique](docs/specs/automatic-photo-target.md),
[recette téléphone](docs/tests/m2-auto-target-recette.md),
[recette analyse commune](docs/tests/m2-joint-plants-recette.md),
[connexion protégée](docs/decisions/0010-chatgpt-session-retention.md).

Les prédictions locales peuvent être erronées ou incomplètes. La vision conserve
le parcours photo YOLOE Large et le direct léger dans la caméra ; les budgets de
fluidité et les essais prolongés restent ouverts. L'envoi d'un cadrage à ChatGPT
ne dépend pas des catégories reconnues par le modèle local.

Les évaluations des modèles et l'historique des versions restent dans le
[backlog](backlog.md), les [résultats qualité YOLOE](docs/research/m1-yoloe-quality-results.md)
et les [mesures de capacité sur Xiaomi](docs/research/m1-yoloe-capacity-phone-results.md).

L'outil PC local **0.7.0** permet de préparer les références des personnes,
d'évaluer la reconnaissance et d'exporter une galerie pour Android. Son
environnement Python est autonome et verrouillé. L'identification individuelle
des chats reste en préparation.

Stack : Kotlin, Jetpack Compose, Android 16 uniquement (`minSdk` / `targetSdk` / `compileSdk` 36).
Un module `app`, identifiant `dev.blamera`, APK installé manuellement.
La validation du POC s'exécute localement. La CI distante reste volontairement
reportée et nécessite des modèles et des fixtures préparés hors Git.

## Développer sous Windows

Commencer par le [runbook M0](docs/runbook.md) pour les prérequis, la reconstruction,
les démonstrations et le dépannage. Le dépôt public fournit les sources et les
tests. Les modèles, les données personnelles, les clés et les APK restent hors Git.

Le script utilise le JBR d'Android Studio, le SDK personnel sous `AppData/Local/Android/Sdk`
et un cache Gradle `.gradle-user-home` dans le dépôt. Voir les prérequis et limites dans
[l'environnement de développement](docs/development-environment.md).

Préparer aussi le [modèle contrôlé, la fixture et la JVM de test isolée](docs/image-segmentation.md).

```powershell
$blameraKey = Join-Path $env:USERPROFILE '.android/debug.keystore'
if (-not (Test-Path -LiteralPath $blameraKey)) { throw 'Cle personnelle absente : arret.' }
.\scripts\gradle.ps1 --version
.\scripts\gradle.ps1 :app:testDebugUnitTest
.\scripts\gradle.ps1 :app:lintDebug
.\scripts\gradle.ps1 :app:assembleDebug
```

La commande globale ajoute formatage, analyse statique, couvertures mobile/scripts/PC
séparées, duplication, licences et scans OSV. Préparer le Python autonome et garder Node 24.12.0 :

```powershell
.\scripts\pc.ps1 Bootstrap
.\scripts\quality.ps1
```

Voir les [gates, périmètres et rapports](docs/quality.md). Les alias Python WindowsApps
ne suffisent pas ; aucun chemin Codex n'est codé en dur dans le projet.
`scripts/pc.ps1 Run` démarre l'UI sur `127.0.0.1:8501`, à ouvrir manuellement.
Voir le [guide PC](docs/pc-development.md) pour les commandes, limites et arrêt du serveur.

Les tests JVM comprennent la sélection de source et l'interface Compose sous Robolectric
Android 16. Sur le téléphone, seules l'installation/mise à jour de l'APK principal et
la lecture des logs Blamera sont autorisées. L'utilisateur lance et manipule l'application.
Ne pas installer de test APK ni exécuter `connectedDebugAndroidTest` sans nouvelle
autorisation humaine explicite ; voir [la protection du téléphone](AGENTS.md).

L'APK debug est produit dans `app/build/outputs/apk/debug/app-debug.apk`.
La commande qualité globale vérifie les seuils de couverture, de complexité et de
duplication. Voir [l'état de la CI](docs/quality.md) pour ses prérequis distants.
Pour fabriquer et mettre à jour l'APK personnel avec la même clé, suivre la
[procédure Android](docs/install-android.md). La copie livrable reste locale dans `dist/`, hors Git.

## Repères

- [Analyse d’image : reconstruction et limites](docs/image-segmentation.md)
- [Aperçu caméra : décision et limites](docs/decisions/0004-camera-preview.md)
- [Validation M1-04](docs/validation/m1-04.md)
- [Backlog et statuts](backlog.md)
- [Directives TDD et organisation des agents](AGENTS.md)
- [Décision de stack](docs/decisions/0001-technical-stack.md)
- [Frontières du socle](docs/architecture.md)
- [Validation M0-02](docs/validation/m0-02.md)
- [Validation qualité M0-03](docs/validation/m0-03.md)
- [Validation et limites M0-04](docs/validation/m0-04.md)
- [Décision PC](docs/decisions/0002-pc-foundation.md)
- [Validation du socle PC M0-05](docs/validation/m0-05.md)
