# M3-03-B — import Android d'une galerie faciale

1er novembre 2026, baseline `83d3645`, cible Android 0.16.0/code30.
[Contrat partagé](../specs/face-gallery-package.md),
[recette matérielle](../tests/m3-face-gallery-import-recette.md).
Logiciel conforme après contrôle global, revue et validation indépendantes.
Blamera 0.16.0/code30 installée le 2 novembre 2026 après reconnexion USB ; recette
Xiaomi en attente. La galerie n'est pas copiée sur le téléphone par l'agent.

## Comportement

La section Galerie faciale du dialogue Configuration ouvre explicitement le
sélecteur Android de documents. Le contrôleur appartient au CaptureHolder conservé
aux rotations. Le launcher est enregistré dans la fiche photo, hors du dialogue,
pour recevoir un résultat même après fermeture de Configuration. Une annulation
du sélecteur n'importe rien. Les noms sont affichés après validation et écriture.

Le décodeur applique le contrat UTF-8/JSON strict : 120Kio, profondeur12, clés
uniques et exactes, pas de données après la racine, profil et pipeline publics
figés, entiers lexicaux, valeurs finies, dimension512 et norme unitaire, identités
uniques et compteurs cohérents. Il ne renormalise pas les données invalides et
n'affiche pas de contenu privé dans les erreurs. Les références publiques des
poids et notices sont des métadonnées ; aucun poids n'est importé ou téléchargé.

Le document validé est chiffré AES-GCM avec un domaine et un alias Keystore facial
distincts, puis conservé dans noBackupFilesDir via le fichier atomique existant.
Le plafond de 120Kio reste sous la limite existante de 128Kio de texte ;
l'enveloppe est binaire, sans expansion Base64. Les limites globales restent
inchangées. Un échec laisse le dernier fichier valide et l'état courant intacts.
La phase de remplacement atomique et de publication est indivisible vis-à-vis
de l'annulation ; annuler avant cette phase conserve l'ancien document.

Aucune nouvelle dépendance, permission, sauvegarde, collecte de logs biométriques,
reconnaissance ou opération téléphone automatisée. Le profil est compatible au
format ; ce lot ne démontre pas la parité numérique d'une exécution faciale Android.

## TDD et preuves

Artefacts locaux dans `build/reports/validation/m3-03-import/coder/` :

- `red-document-functional.log` : la fixture PC valide est rejetée par la façade,
  sortie1 ; `green-document-final.log` : décodeur et matrices de valeurs invalides verts.
- `red-settings.log` : import et état occupé absents, deux échecs fonctionnels ;
  `green-settings-final.log` : import, reprise, annulation et remplacement verts.
- `red-ui.log` : les deux présentations manquent ; Green de la section après implémentation.
- `red-picker-lifecycle.log` : résultat perdu après fermeture du dialogue ;
  `green-lifecycle.log` : 22 tests ciblés passent après déplacement du launcher.
- `red-date.log` : Java accepte l'année0000 contrairement au producteur Python ;
  correction explicite de la borne, sans modifier le contrat.

Les premiers essais de Gradle bloqués par le sandbox et les erreurs de fixtures
ne sont pas des Red fonctionnels. Les deux tests d'intégration du binding de
stockage partagé et du helper de lecture bornée ont été ajoutés avec leur
implémentation, sans Red préalable distinct : cet écart de méthode est rapporté,
pas reconstitué rétrospectivement. Le contrôleur a bien son Red préalable ; les
frontières natives restent également soumises à revue et validation indépendantes.

Les tests utilisent uniquement la fixture publique synthétique du contrat et
des fichiers temporaires PC. Aucun téléphone, compte, clé matérielle personnelle,
galerie utilisateur ou service externe n'est consulté. La vraie reprise Keystore
sur Xiaomi et le choix manuel du fichier restent à valider sur appareil.

## Contrôles finaux et revue

Commande finale : `powershell.exe -NoProfile -ExecutionPolicy Bypass -File
scripts/quality.ps1 -BaseRef 83d3645`, sortie 0. Le premier essai dans le sandbox,
interrompu après refus réseau / lancement d'outils, ne constitue pas cette preuve.
Le passage adapté a terminé tous les contrôles sans modifier les seuils.

- 535 tests Android sans échec, erreur ni test ignoré ; 139 tests PC et 76 tests
  des outils de qualité conformes. Build, format, lint, detekt et licences conformes.
- Android global : 97,39 % lignes / 86,92 % branches ; nouveau code :
  98,26 % / 86,00 %. Core facial : 237/242 lignes (97,93 %) et 184/215 branches
  (85,58 %). Interface faciale : 35/35 lignes et 25/29 branches (86,21 %).
  La première mesure core à 79,53 % précédait les compléments de tests ; elle
  n'est pas utilisée pour valider le composant.
- Duplication nouvelle 0/437 lignes Kotlin ; complexités ≤10 / ≤15, aucune
  violation. Aucun avis actif retourné sur 98 paquets Maven et 36 paquets PyPI ;
  132 entrées de licences Maven et 36 PyPI dans les périmètres documentés.
- Revue indépendante : aucun blocage, 333 empreintes de fichiers inchangées,
  contrôle du profil, alias / domaine distincts, atomicité et cycle de vie.
- Testeur indépendant : 73 tests verts, aucun échec / erreur / ignoré, dont trois
  scénarios ajoutés dans les seuls artefacts temporaires : isolation des fichiers
  ChatGPT / Pl@ntNet, annulation pendant le commit, Unicode / calendrier.

Rapports complets et couverture globale archivés avant les essais ciblés sous
`build/reports/validation/m3-03-import/final-quality/`. Rapport indépendant :
`tester/report.md` dans le même lot. Le passage global a émis des avertissements
de nettoyage de répertoires temporaires Robolectric ; les résultats JUnit et le
build sont conformes. Aucun avertissement n'est présenté comme un test en échec
ni comme une preuve de bon fonctionnement sur téléphone.

APK conservée : `build/outputs/blamera-0.16.0-debug.apk`, package `dev.blamera`,
versionCode 30, versionName 0.16.0 ; SHA-256
`607ba4968af7870538b9fdf50ae0f5f418dca137d9bb27ee0efcbe6cccd60b65`.
La recette réelle reste nécessaire avant clôture de M3-03-B. La dette de méthode
sur les deux tests de frontière est suivie explicitement dans M3-03-B-TDD-01.

## Installation du 2 novembre 2026

Empreinte de l'APK vérifiée identique à celle qualifiée ci-dessus. Le Xiaomi est
détecté puis la seule mise à jour principale est installée avec `adb install -r`,
sortie 0 et résultat Success. Aucun lancement, effacement, changement de permission
ou transfert de fichier sur téléphone par l'agent. Reçu local :
`build/reports/validation/m3-03-import/installation.json`. L'utilisateur ouvre
l'application et réalise le transfert / import puis vérifie la reprise.
