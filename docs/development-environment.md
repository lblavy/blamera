# Environnement de développement Windows

Inventaire initial M0-01 du 26 octobre 2026, actualisé pour le bootstrap M0-02 ; voir
l'[ADR 0001](decisions/0001-technical-stack.md). Le projet existe désormais ; les résultats
réellement exécutés sont séparés dans le [rapport M0-02](validation/m0-02.md).

## Inventaire actuel et limites de l'audit

| Élément | Observation réelle de l'orchestrateur |
| --- | --- |
| Matériel de travail | i7, 32 Go RAM, sans GPU : déclaré par l'utilisateur ; accès CIM refusé, non vérifié par CIM |
| Windows | ADB rapporte Windows 10.0.26200 ; ne pas en déduire ici un nom d'édition |
| Git / Node | `git --version` : 2.52.0.windows.1 ; `node --version` : v24.12.0 |
| Android Studio | Installé dans `C:\Program Files\Android\Android Studio` ; `product-info.json` : AI-253.30387.90.2532.14935130, build 253.30387.90.2532.14935130 |
| JBR | Commande par chemin complet réussie : OpenJDK 21.0.9, build 21.0.9+-14787801-b1163.94 ; `javac.exe` présent |
| Java du PATH | Résout vers `C:\Program Files (x86)\Common Files\Oracle\Java\java8path\java.exe` ; `java -version` sans résultat après plus de 20 s, interrompu, sortie 1 ; version non confirmée |
| SDK réel | `%LOCALAPPDATA%\Android\Sdk` ; plateformes `android-36`, `android-36.1` |
| Build Tools présents | 36.0.0, 36.1.0, 37.0.0 ; cela n'implique pas une plateforme Android 37 installée |
| ADB par chemin complet | Version 1.0.41, package 37.0.0-14910828, sortie 0 ; connexion téléphone non vérifiée, daemon non lancé par l'audit |
| PATH / variables | `javac`, `gradle`, `adb` non trouvés dans le PATH ; aucune variable `ANDROID*` ou `JAVA_HOME` visible |
| Command-Line Tools SDK | Dossier `cmdline-tools` absent du SDK inspecté |
| Projet Android | Absent à l'audit M0-01 ; module `app` et Wrapper créés en M0-02 |
| Python | Alias WindowsApps inutilisable ; Python 3.12.14 fourni par Codex existe, mais ne constitue pas le futur environnement PC autonome |

Le sandbox redirige `$env:LOCALAPPDATA` : son chemin `Android\Sdk` ne désigne pas forcément
le SDK personnel existant. Employer le chemin explicite vérifié ci-dessus et un réglage local
non versionné, plutôt que conclure à une absence de SDK sur ce seul test.
Le téléphone et sa version HyperOS/Android sont déclarés par l'utilisateur, non audités par ADB.

## Versions retenues au bootstrap M0-02

| Élément | Version / configuration |
| --- | --- |
| Gradle Wrapper | 8.14.3, checksum de distribution SHA-256 épinglé |
| AGP | 8.13.2 |
| Kotlin et compilateur Compose | 2.3.21 pour les deux plugins |
| Compose BOM / Activity Compose | 2025.08.01 / 1.10.1 |
| SDK / Build Tools | min/target/compile 36 ; Build Tools 36.0.0 |
| JVM / bytecode | JBR 21.0.9 ; cibles Java et Kotlin 17 |
| Tests | JUnit 4.13.2, Robolectric 4.16 API 36 ; dépendances exactes dans `gradle/libs.versions.toml` |

Les versions explicites résident dans le catalogue et les fichiers Gradle. La table
[Kotlin/Gradle/AGP](https://kotlinlang.org/docs/gradle-configure-project.html) inclut
Kotlin 2.3.21 avec cette plage Gradle/AGP ; [AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
supporte SDK 36. Le choix [Gradle 8.14.3](https://docs.gradle.org/8.14.3/release-notes.html)
inclut le correctif de couverture Android de la branche 8.14. Le compilateur Compose
suit Kotlin ; les bibliothèques sont gérées par la [BOM](https://developer.android.com/develop/ui/compose/bom/bom-mapping).
[Robolectric 4.16](https://robolectric.org/compatibility_table/) couvre API 36, avec JDK 21.

## Configuration locale

- Réutiliser Android Studio, le SDK et le JBR existants ; compléter seulement les composants
  manquants après contrôle. Aucun Gradle global ni JDK 17 supplémentaire requis par principe.
- Les dépendances de M0-02 sont épinglées ; CameraX et le runtime d'inférence ne sont pas
  intégrés à ce socle. Les plugins qualité vérifiés en M0-03 sont décrits dans [quality.md](quality.md).
- Aligner le JDK Gradle du Studio et celui du terminal ; le JBR livré est un JDK réutilisable.
  [Documentation JDK Android](https://developer.android.com/build/jdks).
- Versionner Wrapper, scripts Kotlin DSL et catalogue ; vérifier l'intégrité de la distribution
  Gradle et conserver les versions exactes. [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).
- Résoudre le SDK par `local.properties` non versionné ou configuration locale équivalente.
  Vérifier les licences installées ; ne pas considérer leur acceptation comme établie par l'audit.
  Les téléchargements SDK/dépendances exigent du réseau au bootstrap, pas la vision à l'usage.
  [Configuration SDK 36](https://developer.android.com/about/versions/16/setup-sdk),
  [conditions du SDK](https://developer.android.com/studio/terms).
- Si des composants manquent, passer par le SDK Manager du Studio ; choisir des versions
  reproductibles pour la future CI. L'outil SDK en ligne de commande reste à préparer,
  sans installer NDK/émulateur par défaut. [Gestion SDK](https://developer.android.com/tools/sdkmanager).
- `.gitignore` exclut configurations locales, caches/builds, clés et données personnelles ;
  seule l'exception précise `gradle/wrapper/gradle-wrapper.jar` permet ce JAR versionné.
- `scripts/gradle.ps1` sélectionne le JBR installé, résout le SDK depuis le profil utilisateur
  et place `GRADLE_USER_HOME` dans `.gradle-user-home`. Il ne modifie pas la configuration machine.
- Le téléphone est la référence matérielle mais sa configuration est protégée : ne changer
  ni débogage, ni permissions, ni paramètres. L'utilisateur lance et manipule Blamera.

## Commandes PowerShell de contrôle reproductibles

Les commandes d'inventaire ci-dessous servent à répéter les observations ; seuls les résultats
explicitement indiqués dans le tableau ont été exécutés pendant l'audit de l'orchestrateur.
Les deux variables sont locales au processus et ne changent pas la configuration machine.

```powershell
$blameraJdk = 'C:\Program Files\Android\Android Studio\jbr'
$blameraSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
git --version
Get-Command java,javac,gradle,adb -ErrorAction SilentlyContinue
& "$blameraJdk\bin\java.exe" -version
Test-Path -LiteralPath "$blameraJdk\bin\javac.exe"
Get-ChildItem -LiteralPath "$blameraSdk\platforms" -Name
Get-ChildItem -LiteralPath "$blameraSdk\build-tools" -Name
Test-Path -LiteralPath "$blameraSdk\cmdline-tools"
& "$blameraSdk\platform-tools\adb.exe" version
```

## Commandes du projet sur PC

Depuis `C:\dev\blamera`, le script configure les chemins du terminal avant d'appeler le Wrapper.
Les résultats déjà obtenus sont dans le rapport de validation ; ces exemples servent à les reproduire.
[Build et APK en ligne de commande](https://developer.android.com/build/building-cmdline).

```powershell
.\scripts\gradle.ps1 --version
.\scripts\gradle.ps1 :app:testDebugUnitTest
.\scripts\gradle.ps1 :app:lintDebug
.\scripts\gradle.ps1 :app:assembleDebug
```

Les tests locaux incluent les interactions Compose et la restauration d'état sous Robolectric.
Ils n'installent rien sur le téléphone. La compilation d'un éventuel test APK sur PC n'autorise
pas son installation : `connectedDebugAndroidTest` est interdit sans nouvel accord humain explicite.

## Téléphone personnel : opérations strictement limitées

Seules l'installation/mise à jour de l'APK **principal** et la lecture des logs **Blamera** sont
autorisées. Pas de lancement automatique, désinstallation, effacement, force-stop, paramètres,
permissions, input/rotation, capture d'écran ou tests instrumentés. Voir [AGENTS.md](../AGENTS.md).
L'utilisateur lance et manipule l'application ; ses observations et les logs filtrés fournissent
la preuve sur appareil. Ne pas publier l'identifiant de l'appareil dans les rapports.

Exemples à exécuter seulement sur le téléphone cible identifié localement :

```powershell
$blameraSerial = '<identifiant du téléphone cible, conservé localement>'
# Apres controles package/version/certificat de la procedure install-android.md :
& "$blameraSdk\platform-tools\adb.exe" -s $blameraSerial install -r '.\dist\blamera-0.1.1-debug.apk'
if ($LASTEXITCODE -ne 0) { throw 'Installation refusee : arret et retour utilisateur.' }
# Après lancement manuel par l'utilisateur ; lecture du seul PID de Blamera :
$blameraAppPid = (& "$blameraSdk\platform-tools\adb.exe" -s $blameraSerial shell pidof -s dev.blamera).Trim()
if ($blameraAppPid -match '^\d+$') {
    & "$blameraSdk\platform-tools\adb.exe" -s $blameraSerial logcat --pid=$blameraAppPid -d -t 100
}
```

Ne pas remplacer un PID absent par une collecte globale de logcat. Une preuve impossible
reste en attente ; aucune modification supplémentaire du téléphone n'est une solution autorisée.

Depuis M0-05, `scripts/pc.ps1 Bootstrap` prépare uv/Python 3.12.14 dans `.tools` et le
venv verrouillé `pc/.venv`, sans dépendance à Codex ni Python global. `scripts/quality.ps1`
sélectionne ce Python par défaut ; `scripts/pc.ps1 Quality` fournit le même point d'entrée.
Node 24.12.0 reste nécessaire. Les anciens audits M0-01/03 décrivent leur environnement
au moment de leur exécution ; ils ne sont pas des prérequis cachés du socle PC actuel.
Voir [le guide PC](pc-development.md) et [la qualité](quality.md).
`lintDebug` ou un build seul ne prouvent pas la conformité de l'ensemble des gates.
La [procédure courante](install-android.md) documente la signature debug personnelle et
la mise à jour préparée ; les rapports M0/M1 conservent les validations historiques.
Secrets et clés privées restent hors Git.
L'APK personnel et son checksum restent dans `dist/`, ignoré. Le certificat doit rester
celui de la version installée ; ne pas régénérer la clé si elle manque. Aucun APK CI
signé par une autre clé ne constitue une mise à jour compatible.
Le socle PC utilise Streamlit local ; aucun framework ML ou modèle n'est installé en M0-05.
Les benchmarks du modèle, la fidélité des exports et les budgets restent à valider en M1-01.

Pour M1-03, les tests JNI Windows utilisent un JRE Temurin privé 21.0.12.1+1 ; Gradle
et la compilation gardent le JBR existant. Le modèle contrôlé et les fixtures publiques
sont des prérequis locaux hors Git : [préparation exacte](image-segmentation.md).
L’APK image 0.3.0/code 4 a passé revue et tester PC et a été installé par l’orchestrateur ;
le retour utilisateur est acceptable avec réserves, et quatre échantillons CPU/PSS sont
consignés dans M1-03. L’incrément0.3.1 des contours a passé revue/tester PC puis installation ;
L’utilisateur confirme0.3.1 : « Contours améliorés et bien placés ». La partie image
est validée ; budgets, chauffe et mesures prolongées restent ouverts.

M1-04 a livré l’APK0.4.0/code6, revu et validé indépendamment sur PC puis installé
par l’orchestrateur. Aperçu et transitions sont confirmés, avec un défaut de paysage
inversé suivi dans le [correctif 0.4.1](validation/m1-04-rotation.md). Celui-ci a passé
revue et tester PC ; APK prêt, installation et validation humaine des deux paysages
en attente faute de téléphone connecté. CameraX 1.6.2 est compatible SDK 36, CAMERA est sur demande
utilisateur. Seul l’aperçu est livré, sans analyse/capture ; les contrôles PC ne prouvent
pas la surface matérielle. [Rapport courant](validation/m1-04.md), [ADR](decisions/0004-camera-preview.md).
Sur téléphone : l’orchestrateur installe uniquement l’APK principal après validation PC ;
l’utilisateur ouvre, autorise s’il le souhaite et manipule lui-même. Aucun grantADB,
changement de réglage ou test APK par un agent.

L’incrément local 0.5.0/code 8 réutilise ces dépendances sans nouveau runtime ; analyse
caméra sur demande et correctif de rotation inclus. Développement, revue et tester PC
conformes. Téléphone reconnecté le 27 octobre 2026 : mise à jour du seul APK principal
0.5.0 réussie depuis 0.4.0, `adb install -r` sortie 0. Aucun lancement ni autre manipulation ;
P*/O*/I* déclarés OK le 27 octobre avec réserves de reconnaissance/fluidité. Essai
inférieur à 5 min sans chauffe ressentie ; prolongé et hors ligne non validés. [Rapport et contrôles PC](validation/m1-05.md),
[recette manuelle](tests/m1-recette.md). La 0.4.1 n’a pas été installée séparément.


Historique : diagnostic personnel 0.5.1/code 9 préparé le 27 octobre : mêmes JBR/JRE/ORT et
environnement PC ; contrainte transitive de sécurité Guava 33.7.2-android vérifiée.
Revue ciblée/sécurité et tester indépendants conformes sur PC. Les [assets debug contrôlés](../experiments/m1-yoloe/README.md)
sont requis en plus du modèle normal pour le build debug. [Validation locale](validation/m1-yoloe-android.md)
et [recette dédiée](tests/m1-yoloe-comparaison.md). Après une première procédure arrêtée
faute de connexion, l’APK 0.5.1 a été installé le 27 octobre par mise à jour du seul APK
principal depuis 0.5.0, sortie 0. L’utilisateur a ensuite confirmé deux fins de comparaison
manuelles ; un run complet CPU/PSS capturé uniquement aux logs Blamera est vérifié
indépendamment. [Mesures et limites](validation/m1-yoloe-android.md#mesure-xiaomi-contrôlée-dans-les-logs--27-octobre-2026).
Modèle des parcours habituels conservé ; aucun build/test global ou réglage appareil
supplémentaire pour cette analyse.

Le diagnostic suivant [0.6.0/code 10](validation/m1-yoloe-capacity-phone.md) conserve
ces versions et requiert les [nouveaux assets contrôlés](../experiments/m1-yoloe-phone-assets/README.md).
Les références PC Kotlin et leur index épinglé restent locaux, avec les modèles et
les images autorisées. Aucun runtime global supplémentaire, appareil automatisé
ou accès réseau n’est requis pour le diagnostic embarqué ; les scans qualité PC
requièrent le réseau comme auparavant.
