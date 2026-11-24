# Reconstruire et démontrer le socle M0 sur ce Windows

Ce guide part du dossier local **`C:\dev\blamera`**, sur l'ordinateur i7/32 Go sans GPU.
Il rassemble les commandes déjà livrées ; il n'ajoute aucune fonction de vision ou de ML.
Les [preuves M0-06](validation/m0-06.md) indiquent ce qui a été rejoué sans profil.
Ce runbook conserve la démonstration M0 datée ; l'import 0.2.0 est décrit dans
les [preuves M1-02](validation/m1-02.md) et la [procédure Android courante](install-android.md).
Pour M1-03, préparer d’abord le [modèle, la fixture et la JVM de test](image-segmentation.md) ;
le reste de ce guide conserve les preuves historiques M0.
L’incrément courant 0.5.0 inclut l’analyse caméra et le correctif des deux paysages :
[recette manuelle M1](tests/m1-recette.md), [rapport PC et limites appareil](validation/m1-05.md).
La mise à jour principale directe de 0.4.0 vers 0.5.0 a réussi le 27 octobre 2026 ;
P*/O*/I* déclarés OK le 27 octobre avec réserves de détection/fluidité ; prolongé
et hors ligne encore à valider.
Aucune opération téléphone automatisée pour démontrer ce guide.

## Sources et prérequis

Les repères M0 ci-dessous ont été établis sur la branche locale `codex/m0-foundation`.
L'instantané public de Blamera 0.18.0 est destiné à `main`. Les données, modèles
et caches nécessaires à la démonstration restent sur le PC. Contrôler `git status
--short` et conserver les modifications locales en plus des commits. L'archive
source citée dans le rapport M0 est une preuve historique de l'état exporté.

Ouvrir un PowerShell ordinaire, éventuellement sans profil avec `powershell.exe -NoProfile`,
puis exécuter :

```powershell
Set-Location -LiteralPath 'C:\dev\blamera'
git branch --show-current
git rev-parse HEAD
git status --short
git --version
node --version
```

| Élément | Référence vérifiée / emplacement |
| --- | --- |
| Git / Node | 2.52.0.windows.1 / 24.12.0, disponibles dans le PATH |
| JDK Gradle | JBR 21.0.9 : `C:\Program Files\Android\Android Studio\jbr` |
| SDK / Build Tools | `%USERPROFILE%\AppData\Local\Android\Sdk`, API 36 / 36.0.0 |
| Wrapper / AGP / Kotlin | 8.14.3 / 8.13.2 / 2.3.21 ; cibles Java et Kotlin 17 |
| Compose BOM / Activity | 2025.08.01 / 1.10.1 ; min/target/compile Android 36 |
| Python / uv | 3.12.14 / 0.13.5 ; installés par le bootstrap dans `.tools` |
| UI PC | Streamlit 1.66.1, dépendances et outils verrouillés dans `pc/uv.lock` |

Le [catalogue mobile](../gradle/libs.versions.toml), le [pyproject PC](../pc/pyproject.toml)
et les [ADR mobile](decisions/0001-technical-stack.md)/[PC](decisions/0002-pc-foundation.md)
fixent les versions et leurs sources officielles. L'[inventaire Windows](development-environment.md)
distingue les observations initiales de la configuration actuelle. Un accès réseau est nécessaire
pour préparer les dépendances et exécuter OSV ; aucun abonnement, GPU ou service cloud requis.

## Préparer et contrôler

Depuis la racine :

```powershell
.\scripts\pc.ps1 Bootstrap
if ($LASTEXITCODE -ne 0) { throw 'Bootstrap en echec : arret' }
.\.tools\uv\0.13.5\uv.exe --version
.\pc\.venv\Scripts\python.exe -c 'import sys; print(sys.version); print(sys.executable); print(sys.base_prefix)'
.\scripts\gradle.ps1 --version
```

Le Python doit provenir de `pc/.venv` et sa base de `.tools/python`, sans runtime Codex.
Le bootstrap vérifie uv, respecte le lock et n'installe rien globalement : pas de PATH
permanent ou de registre Python. Il est répétable. Le [guide PC](pc-development.md)
donne les empreintes et les détails ; ne pas supprimer le lock pour contourner un échec.

Pour la validation complète lors d'un changement logiciel :

```powershell
# Quality inclut assembleDebug : ne jamais laisser Gradle generer une autre cle par defaut.
$blameraKey = Join-Path $env:USERPROFILE '.android\debug.keystore'
if (-not (Test-Path -LiteralPath $blameraKey)) { throw 'Cle personnelle absente : arret' }
.\scripts\pc.ps1 Quality
if ($LASTEXITCODE -ne 0) { throw 'Qualite en echec : consulter les rapports' }
```

Cette commande inclut build, tests, format, types, complexités, duplications, couvertures
et scans runtime. Les trois composants mobile/PC/scripts restent mesurés séparément.
Si la clé existante utilise un chemin personnalisé, la retrouver avec l'utilisateur avant
toute commande signante ; ne pas générer une clé de remplacement. Avant une mise à jour,
comparer le certificat obtenu à la référence selon la [procédure Android](install-android.md).
Les seuils et exclusions sont dans [quality.md](quality.md), pas déduits d'un build seul.
La dernière validation complète de M0-05 est [datée et consignée](validation/m0-05.md) ;
la rédaction M0-06 ne prétend pas avoir relancé cette commande globale.

Contrôles ciblés utiles, à choisir selon le changement :

```powershell
# Tests PC : interactions AppTest + HTTP loopback ; son propre serveur est ensuite ferme.
Push-Location pc
try {
    .\.venv\Scripts\python.exe -m unittest discover -s tests -v
    if ($LASTEXITCODE -ne 0) { throw 'Tests PC en echec' }
} finally { Pop-Location }
# Android sur PC, sans installation ni commande ADB :
.\scripts\gradle.ps1 :app:testDebugUnitTest
.\scripts\gradle.ps1 :app:lintDebug
# Avant toute reconstruction signee : verifier la cle existante, jamais en generer une autre.
$blameraKey = Join-Path $env:USERPROFILE '.android\debug.keystore'
if (-not (Test-Path -LiteralPath $blameraKey)) { throw 'Cle personnelle absente : arret' }
.\scripts\gradle.ps1 :app:assembleDebug
```

`scripts/gradle.ps1` sélectionne le JBR/SDK et le cache local, sans modifier la machine.
Si la configuration locale diffère, lire le guide d'environnement avant d'adapter les chemins.
La reproductibilité concerne versions et procédure ; aucun APK bit à bit universel n'est garanti.

## Démontrer ce qui existe

PC : lancer `scripts/pc.ps1 Run`, puis ouvrir **manuellement** `http://127.0.0.1:8501`.
Choisir Personnes puis Chats : seules les prochaines étapes M3/M4 sont présentées, avec
un message indiquant qu'aucune photo n'est importée et aucun modèle entraîné/exporté.
Arrêter le processus de ce terminal avec **Ctrl+C**. Aucun navigateur automatique,
authentification ou accès réseau local exposé : adresse `127.0.0.1`, télémétrie désactivée.
AppTest et le smoke-test HTTP ont été validés ; aucun rendu de navigateur réel n'est revendiqué.

Android : l'utilisateur ouvre et manipule lui-même l'application déjà installée,
puis vérifie Blamera, Caméra/Image et l'annonce des fonctions à venir. Il n'y a encore
ni acquisition, ni import, ni segmentation. La [validation M0-04](validation/m0-04.md)
consigne l'installation et son retour manuel, pas une preuve de rotation réelle.

APK personnel existant : `dist/blamera-0.1.1-debug.apk`, `dev.blamera`, version **0.1.1 / code 2**,
Android 16/API 36, signature debug personnelle. Références publiques :

```text
SHA-256 APK : 64a7d63e8792037dd9c0335f9d112adeb048ed8a296052a601f019373dd19ffe
SHA-256 certificat : eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555
```

Une mise à jour éventuelle suit exclusivement la [procédure Android](install-android.md) :
contrôler package/version/signature avant installation et conserver la clé existante hors Git.
Sur le téléphone, seules installation/update de l'APK principal et lecture des logs Blamera
sont autorisées ; l'utilisateur lance et touche l'application. Pas de test APK/instrumentation,
automatisation tactile, capture, réglages, permissions, arrêt forcé, effacement ou désinstallation.
M0-06 n'exécute **aucune action téléphone**. Aucun log global n'est autorisé.

## Lire les résultats et dépanner

| Question | Emplacement ou action limitée |
| --- | --- |
| Verdict complet | `build/reports/quality/summary.json` et `summary.md` ; contrôler aussi les sorties des étapes |
| Mobile | `app/build/reports/tests/`, `app/build/reports/jacoco/jacocoDebugReport/`, `app/build/reports/lint-results-debug.html`, `app/build/reports/dependency-license/` |
| Scripts | `build/reports/quality/python/` : couverture JSON/XML/HTML et duplication |
| PC | `build/reports/quality/pc/` : verdict, couverture, complexités, duplication, inventaire/licences, OSV et logs |
| Historiques | `docs/validation/m0-02.md` à `m0-06.md` ; mesures datées, pas garanties après un nouveau changement |
| Python/py alias WindowsApps | Utiliser le bootstrap puis `pc/.venv/Scripts/python.exe`, pas l'alias système |
| Java ou SDK introuvable | Utiliser `scripts/gradle.ps1` ; vérifier JBR et SDK réels, pas le Java Oracle du PATH ni le LOCALAPPDATA redirigé du sandbox |
| Réseau OSV/dépendances indisponible | Conserver l'erreur et réessayer une fois le réseau rétabli ; jamais déclarer le scan conforme sans données |
| Lock incompatible | Lire l'erreur et vérifier les versions du projet ; toute mise à jour du lock est intentionnelle et revue |
| Port 8501 occupé | Arrêter seulement sa propre session Blamera si elle est connue ; sinon demander à l'utilisateur de résoudre le conflit, sans tuer un autre processus |
| Clé absente ou certificat différent | Arrêter et retrouver la clé existante avec l'utilisateur ; aucune génération, rotation ou désinstallation de compensation |
| Installation refusée | Arrêter et informer l'utilisateur ; aucun changement de sécurité/debug ou contournement |

Les rapports locaux, venv, caches, APK, clés, photos, embeddings et exports restent hors Git
selon [les exclusions](../.gitignore). Ne pas joindre des données privées aux diagnostics.
L'exécution de la CI distante reste volontairement reportée. La démonstration
Windows s'effectue localement avec les actifs préparés sur le PC.

## Maintenir le guide

À chaque milestone : mettre à jour commandes/version, ADR utile, limites, rapports et statut
du backlog ; conserver les preuves anciennes datées sans les réécrire comme nouvelles.
Avant une évolution logicielle, l'orchestrateur cadre le ticket puis fait intervenir
coder en Red/Green/Refactor, reviewer indépendant et tester sur l'état final. Toute correction
invalide les preuves impactées. Les détails, gates et arbitrages sont dans [AGENTS.md](../AGENTS.md).
Une modification de documentation seule reçoit des contrôles directs adaptés.

## Diagnostic personnel de capacité — 0.6.0

Ce guide M0 conserve ses preuves datées. Le diagnostic debug courant en préparation
mesure baseline/Small/Large/Xlarge sur quatorze images publiques, sans changer les
parcours habituels. Préparer ses [assets contrôlés](../experiments/m1-yoloe-phone-assets/README.md)
avant le build ; garder la garde de présence de la clé avant toute commande signante.
Suivre la [recette dédiée](tests/m1-capacity-phone-recette.md) pour le lancement
manuel et la récupération du rapport durable. Les commandes PC ne lancent pas
l’application et ne lisent aucun fichier privé du téléphone.
[Contrôles, protocole et état de validation](validation/m1-yoloe-capacity-phone.md).
