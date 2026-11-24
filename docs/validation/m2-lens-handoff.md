# Remise native de l'image à Google Lens — 0.9.2

29 octobre 2026 — implémentation revue, contrôle global final, package et testeur
indépendant conformes sur PC. Mise à jour principale 0.9.2 réussie ; parcours Lens
essentiel accepté par l'utilisateur, avec la portée du retour précisée ci-dessous.
Baseline : `8365cb96483d52890bfe2002e34b144f6005d05b`.

## Besoin vérifié

L'image reste cassée dans Google après installation de 0.9.1, malgré la correction
du protocole fichier direct et les validations PC. Le retour utilisateur « non,
toujours pareil » maintient W02 en échec. L'utilisateur précise que, depuis Chrome,
appuyer sur l'icône d'image ouvre Lens, et qu'y choisir une image de galerie fonctionne.

Le contrat [ADR 0008](../decisions/0008-google-lens-share.md) remplace le client HTTP
web par un partage Android ciblé vers un gestionnaire Google Lens compatible.
Le JPEG de l'aperçu doit être remis via un URI en lecture seule, fichier temporaire
privé expirant et stockage borné ; pas de sélection automatique d'autres destinataires.
Aucune cause unique de l'échec du site web n'est affirmée.

## Organisation et preuves attendues

Un coder implémente en TDD ; un reviewer examine l'état gelé puis un tester indépendant
valide le résultat. Les anciennes frontières HTTP supprimées doivent être archivées
et leur remplacement documenté sans affaiblir les invariants encore pertinents.

Le contrôle portera sur :

- résolution publique réelle de `ACTION_SEND image/jpeg` dans les seuls packages admis ;
- JPEG identique à l'aperçu, sans EXIF, grant de lecture d'un seul URI et aucun accès général ;
- provider, chemins, refus d'écriture et expiration durable, y compris après reprise du processus ;
- nombre et volume des fichiers bornés, nettoyage limité au répertoire de partage ;
- annulation ou image périmée avant lancement, conservation après `ON_STOP` consécutif à Lens ;
- destinataire absent/refusé et retour photo, masque, zoom, original et recadrage ;
- suppression du client HTTP et de `INTERNET`, sans autre permission ni changement de modèle ;
- qualité globale et nouveau code, package, signature, actifs et lecteur de diagnostic versionné.

Les tests PC utiliseront des destinataires simulés et des images synthétiques.
Aucun autre upload exploratoire Google : les trois essais précédents et le blocage
anti-robot sont documentés dans le [diagnostic antérieur](m2-search-fix.md).
Seuls installation/mise à jour principale et logs Blamera sont autorisés sur téléphone ;
aucun lancement, pilotage ou accès aux photos par l'agent. La réussite de W02
ne sera déclarée qu'après le retour humain sur la bonne image et les résultats Lens.

## Première validation ciblée et revue

Le Red natif échoue pour le comportement attendu avant implémentation du partage.
Après branchement, 28 tests dans six classes réussissent, sans erreur ni ignoré :
quatre du sender, cinq du cache, trois du provider, six du coordinateur, six UI et
quatre du manifeste. Formatage et detekt passent ; le lecteur versionné passe ses
huit tests après son Red propre à 0.9.2/code 15.
[Green initial](../../build/reports/validation/m2-lens-native/coder/green-native-lens-targeted-5.log),
[XML et décompte](../../build/reports/validation/m2-lens-native/coder/targeted-results.json).

Une fixture lit le JPEG via le vrai ContentResolver et le provider applicatif :
Pillow le décode indépendamment en RGB, 64 × 32 pixels, 774 octets, couleur bleue
attendue et aucune entrée EXIF. Les PNG portrait et paysage sont examinés par le
coder et le reviewer ; image préservée et panneau lisible/défilant.
[Décodage indépendant](../../build/reports/validation/m2-lens-native/coder/independent-jpeg-decode.json).

La frontière AndroidX statique `FileProvider.getUriForFile(Context,String,File)`
est adaptée dans les seuls tests concernés : AndroidX compare les descendants avec
`rootPath + '/'`, tandis que ce JVM Windows donne des chemins avec antislashs.
Le shadow lit la vraie configuration XML et résout le vrai PackageManager ; seul
le calcul descendant et la construction d'URI sont rendus portables avec `Path`.
Notre cache, les métadonnées durables, les contrôles canoniques/TTL/mode, le provider,
le ContentResolver et le descripteur de fichier restent exécutés réellement.
Cela ne prouve pas l'implémentation des chemins AndroidX sur Android ni la réception
par Lens. [Mapping et périmètre du shadow](../../build/reports/validation/m2-lens-native/coder/supersession-map.json).

La première revue indépendante retient un défaut fonctionnel : l'état de remise
restait vrai après un premier succès. Lors d'une deuxième publication retardée,
un départ en arrière-plan pouvait laisser lancer Lens tardivement. Correction
demandée : état par tentative, ancien fichier déjà remis détaché sans fermeture,
nouvelle publication annulée et nettoyée. Le test de régression doit reproduire
premier succès, deuxième publication retenue, arrière-plan puis retour tardif :
un seul intent, deuxième fichier fermé et premier conservé. Aucun autre blocage
n'est retenu. Le quota peut évincer un ancien fichier avant cinq minutes ; cette
durée est documentée comme un maximum, sans promesse de disponibilité minimale.

Le Red du défaut reproduit deux intents au lieu d'un. Après les deux lignes de
correction et leur test, 13 tests du coordinateur/UI réussissent, avec format et
detekt conformes. La relecture indépendante lève le P2 et ne retient aucun blocage
supplémentaire. Le contrôle global est lancé sur cet état gelé.
[Red P2](../../build/reports/validation/m2-lens-native/coder/red-p2-repeat-share-background.log),
[Green P2](../../build/reports/validation/m2-lens-native/coder/green-p2-repeat-share-background.log).

## Premier contrôle global

La commande `scripts/quality.ps1 -BaseRef 8365cb96483d52890bfe2002e34b144f6005d05b`
sort avec le code 1. Les 213 tests Android, 76 tests de scripts et quatre tests PC
réussissent ; les étapes de build, formatage, analyse statique, lint, duplication et
contrôle PC sortent toutes avec 0. La gate finale échoue sur la couverture des
branches du nouveau code : **73,45 %, sous le minimum de 80 %**. Aucun seuil,
exclusion ni baseline ne change. Le coder reprend les cas utiles insuffisamment
exercés ; ces ajouts de couverture après implémentation seront distingués du TDD
initial. Installation en attente de correction, revue et nouveau contrôle global.
[Rapport figé de la première tentative](../../build/reports/validation/m2-lens-native/attempt1/quality/summary.json),
[log complet](../../build/reports/validation/m2-lens-native/attempt1/quality.log).

Le complément de tests couvre des métadonnées corrompues, échéances et tailles
invalides, les fichiers étrangers à préserver lors du nettoyage, un fragment URI,
une projection explicite du provider et les droits du destinataire. Aucun défaut
de production découvert, aucun changement de production : trois tests supplémentaires
et des scénarios étendus. Deux erreurs d'outillage/compilation de fixture sont
corrigées sans les présenter comme un Red fonctionnel. Les 35 tests ciblés passent,
avec formatage et detekt. Sur le nouveau code, 244/246 lignes (99,19 %) et 146/177
branches (82,49 %) sont couvertes. Ces valeurs ciblées ne remplacent pas la mesure
globale à refaire. La revue indépendante du complément ne relève aucun blocage ;
les sept sources du composant correspondent toujours à celles du premier contrôle
global. Aucun seuil ni configuration qualité changé.
[Preuves](../../build/reports/validation/m2-lens-native/coder/branch-scenarios/metrics.json).

## APK candidat

Snapshot local destiné au contrôle indépendant, avant acceptation du contrôle global :
`build/reports/validation/m2-lens-native/package/blamera-0.9.2-candidate.apk`,
0.9.2/code 15, **457 604 689 octets**, SHA-256
`2ddb8a5702a0f48fca5ccd01d908605b214e074d7b895074ca3a8e885d62fd35`.
Ce snapshot a servi au contrôle indépendant avant la globale finale ; l'identité
avec l'APK livré et installé est ensuite vérifiée, voir les sections suivantes.

Le contrôle indépendant du candidat confirme version/code, certificat identique à
0.9.1, SDK 36 et arm64-v8a. Le manifeste binaire contient exactement `CAMERA` et
la permission interne AndroidX, sans `INTERNET`, ainsi que les deux packages Google
attendus. Le provider est non exporté, autorise les grants URI et utilise l'autorité
`dev.blamera.lens-images` ; son unique chemin XML est `lens-share/images/` dans le
cache. Les 25 actifs, dont cinq ONNX et un seul modèle Large, sont identiques à
0.9.1. Les anciennes classes HTTP et le code expérimental serveur/JavaScript sont
absents. Le lecteur passe huit tests et son rejeu historique conserve 56 cas/437
détails ; ses paires version/code exactes acceptent notamment 0.9.2/15 et rejettent
les variantes invalides. Source gelée : seuls trois fichiers de tests diffèrent
depuis la première tentative globale.
[Rapport du package candidat](../../build/reports/validation/m2-lens-native/tester/package-candidate.json).

## Contrôle global final

Même commande et même baseline, sortie **0** après le complément revu. Les **216 tests
Android**, **76 tests de scripts** et **quatre tests PC** réussissent. Aucun test
Android ignoré, aucune erreur. Build, formatage, detekt, lint, contrôles de duplication,
licences et dépendances passent. Les messages négatifs des fixtures de preuve des
gates sont attendus ; aucune étape du rapport final n'échoue.

| Mesure | Résultat final |
| --- | --- |
| Mobile, lignes / branches globales | 96,59 % / 85,63 % |
| Mobile, nouvelles lignes / branches | 244/246 = 99,19 % / 147/177 = 83,05 % |
| Duplication Kotlin globale / nouveau code | 0,23 % / 0 ligne sur 359 |
| Scripts, lignes / branches globales | 94,35 % / 88,52 % |
| Scripts, nouvelle ligne / branches | 100 % / non mesurées, aucune branche nouvelle |
| PC, lignes / branches globales | 100 % / 100 % ; aucune ligne exécutable nouvelle |
| Complexité cyclomatique / cognitive | Aucun dépassement des limites 10 / 15 |
| Dépendances et licences | 97 composants Maven / 131 entrées ; 35 paquets Python / 35 entrées |
| Vulnérabilités actives du périmètre scanné | 0 |

Les rapports globaux sont archivés avant toute exécution ciblée indépendante.
[Synthèse finale](../../build/reports/validation/m2-lens-native/final-quality/quality/summary.json),
[log](../../build/reports/validation/m2-lens-native/quality-final.log),
[résultats Android](../../build/reports/validation/m2-lens-native/final-quality/android-tests).

L'APK final a la même empreinte que le candidat vérifié et est copié dans
`dist/blamera-0.9.2-debug.apk`, 457 604 689 octets, SHA-256
`2ddb8a5702a0f48fca5ccd01d908605b214e074d7b895074ca3a8e885d62fd35`.
La preuve de recherche réelle est distincte des validations PC ; retour humain ci-dessous.

## Validation indépendante et installation

Le testeur exécute **35 tests ciblés dans sept classes**, zéro échec, erreur ou ignoré,
sortie 0, et huit tests du lecteur réussis. Le JPEG synthétique relu par le vrai
ContentResolver est régénéré identique. Les 222 empreintes de sources gelées et
516 fichiers de rapports globaux restent inchangés ; les APK candidat, build et
`dist/` sont identiques. Les preuves de package, provider, signature et actifs sont
confirmées. Verdict **conforme sur PC** ; W02 réel reste indispensable.
[Verdict et preuves](../../build/reports/validation/m2-lens-native/tester/tester-results.json),
[log ciblé](../../build/reports/validation/m2-lens-native/tester/targeted.log).

Après vérification du verdict, de la globale, des empreintes de sources et de l'APK,
l'unique Xiaomi attendu est détecté. La mise à jour du seul APK principal réussit le
29 octobre 2026, `adb -d install -r`, sortie 0 « Performing Streamed Install / Success ».
L'agent n'a pas lancé l'application ni effectué d'autre mutation sur le téléphone.
[Reçu sans identifiant appareil](../../build/reports/validation/m2-lens-native/install-result.json).

L'utilisateur a été invité à ouvrir Blamera manuellement, rechercher un objet d'une
photo qui échouait, vérifier la réception directe du bon recadrage et les résultats
dans Lens, puis le retour à la photo, au zoom et à la sélection. Le message exact
est demandé en cas de partage indisponible ou refusé.

Après cet essai, l'utilisateur répond **« ça a l'air de marcher »** le 29 octobre.
Ce retour accepte le parcours Lens essentiel et permet de clôturer M2-01 ; la
réussite n'est donc pas déduite des seuls tests PC. Le message reste global et ne
décrit pas séparément chaque variante W01–W07, notamment le détail du zoom au retour.
Ces variantes demeurent une référence de régression ; les cas d'erreur détaillés
restent suivis par M2-04. Aucune photo personnelle reçue ni manipulation du téléphone
par l'agent. Les limites du shadow Windows et des autres versions du destinataire
Google restent explicites.
