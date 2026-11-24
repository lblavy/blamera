# M2-01 — fiche d'objet et recherche web explicite

29 octobre 2026 — code, revue, qualité globale, testeur indépendant et package
conformes sur PC ; mise à jour installée, recherche en échec sur appareil. Référence du nouveau code :
`09ee6bef5cff35ce95c52f6a78d196d34ce1aaf6`.

Retour humain après installation le 29 octobre : toutes les opérations fonctionnent
globalement sauf la recherche, qui ouvre Google avec une image vide (icône cassée
et barrée dans la zone de recherche, aucun résultat en dessous). W02 échoue ;
la réussite PC ne valide donc pas l'intégration navigateur/service sur le Xiaomi.
Le parcours local est accepté globalement sans déclaration individuelle de toutes
les variantes. Diagnostic et correction en cours, cause non encore établie.
Comparaison humaine : Chrome est le navigateur utilisé ; une recherche par photo
directement depuis Google Lens dans Chrome, avec choix en galerie, affiche
correctement l'image et les résultats. Ce constat isole le défaut au parcours
Blamera vers Chrome, sans démontrer à lui seul un défaut précis de cookies ou d'URI.
[Diagnostic et correctif 0.9.1](m2-search-fix.md).

## Contrat et organisation

Le parcours essentiel 0.8.0 est accepté par l'utilisateur, qui demande de poursuivre
les fonctionnalités. [Contrat M2-01](../specs/object-information.md) : enrichir la
fiche d'un objet sélectionné et proposer une recherche Google par image après
appui explicite. L'utilisateur a écarté la piste textuelle en raison des catégories
peu fiables. La faisabilité du transfert et de l'ouverture des résultats dans le
navigateur est étudiée avant toute livraison. Un spike synthétique unique obtient
HTTP 302 vers une adresse Google candidate, sans valider la page de résultats ;
voir l'[ADR 0007](../decisions/0007-google-visual-search.md). Le choix d'une description par IA
et de son financement reste ouvert.

Un agent coder implémente en TDD la frontière Android, la fiche et les régressions.
La revue indépendante examine l'état stable, puis un agent tester valide les critères.
L'orchestrateur maintient les documents. Les preuves PC sont distinguées du
retour physique, attendu dans la [recette manuelle](../tests/m2-object-actions-recette.md).

## Plan de validation et traçabilité

- Red, Green et vérifications ciblées réelles.
- Revue indépendante et corrections utiles.
- Contrôles globaux : tests, couverture globale et nouveau code, duplication,
  complexité, licences et audit des dépendances runtime.
- Testeur indépendant : action explicite, pixels effectivement transmis, erreurs,
  sélection/image/ROI conservés et rendu portrait/paysage.
- APK : identité, taille, empreinte, signature, permissions et actifs préservés.
- Installation du seul APK principal si le Xiaomi est disponible, puis recette
  humaine ; aucun lancement ou pilotage automatique sur le téléphone.

Cette liste décrit le plan initial ; les résultats réellement obtenus et les
points encore ouverts sont détaillés dans les sections suivantes.

## Revue préalable du contrat

La revue indépendante en lecture seule ne trouve pas de blocage de conception.
Elle demande de vérifier la vraie page de résultats sur téléphone, car le POST du
spike reçoit des cookies qui ne sont pas transférés au navigateur. L'ouverture
d'une URL seule ne valide donc pas le parcours. Les autres précisions retenues
sont une allowlist d'hôtes et de chemins de résultats exacts, des bornes chiffrées
sur le JPEG et le délai total, et des tests de double-appui et réponses tardives
après changement de sélection, annulation ou départ. Cette revue du contrat ne
remplace pas la revue du diff final ni la validation du testeur.

## Développement et première revue du diff

Le Red de la fiche visuelle est observé séparément de la piste textuelle abandonnée.
Deux tests supplémentaires reproduisent ensuite une sélection changée avant
recomposition et l'absence du sélecteur de navigateur, avant correction.
Les échecs de connexion au daemon ou de compilation ne sont pas comptés comme Red.

Premier Green : formatage et detekt conformes, 35 tests ciblés dans sept classes
réussis, puis huit tests du lecteur de diagnostic réussis avec l'identité exacte
`0.9.0/13`. Les deux fixtures de rendu après recadrage sont corrigées pour adapter
rectangle, masque et contours aux dimensions réellement analysées ; leurs deux
tests sont réexécutés avec succès. Ces corrections de fixture ne démontrent pas
un défaut de projection du code de production. Les PNG portrait et paysage sont
examinés par le coder, l'orchestrateur et le reviewer : fiche lisible et commandes
accessibles avec défilement du panneau en paysage.

La première revue indépendante du diff retient un défaut P1 dans l'envoi : après
une annulation pendant la construction d'une connexion encore inactive, le code
pouvait poursuivre jusqu'à écrire le JPEG malgré un premier `disconnect()` sans
effet. Les tests existants d'annulation après début d'envoi ne couvraient pas ce
scénario. Correction demandée : gardes d'activité après construction de la connexion
et acquisition du flux, avant toute écriture, avec fermeture garantie. Deux tests
de régression doivent reproduire l'annulation pendant la factory et pendant
l'acquisition du flux. Aucun autre défaut établi n'est retenu sur ce premier batch.
Les deux tests échouent effectivement avant correction : une acquisition de flux
indue dans le premier cas, 278 octets écrits dans le second. Après ajout des gardes
d'activité et fermeture garantie, formatage, detekt et 12 tests ciblés passent.
La relecture indépendante lève le P1 : les gardes couvrent le retour de la factory,
l'acquisition du flux et l'écriture ; le `finally` et le `use` ferment connexion et flux
même si une garde interrompt le travail. Aucun autre défaut introduit n'est retenu.
Le contrôle global est lancé après cette relecture, sur les sources gelées.

Preuves locales : [Red annulation](../../build/reports/validation/m2-object-actions/coder/red-cancel-before-send.log),
[Green annulation](../../build/reports/validation/m2-object-actions/coder/green-cancel-before-send.log),
[Green visuel initial](../../build/reports/validation/m2-object-actions/coder/green-visual-targeted-final.log),
[deux fixtures finales](../../build/reports/validation/m2-object-actions/coder/green-visual-fixture-final.log).

## Premier contrôle global

Le premier passage complet exécute 207 tests Android : 206 réussis, un échec, aucune
erreur ni test ignoré. L'unique échec est l'ancien contrat de permissions dans
`MainActivityLocalTest`, qui interdisait `INTERNET`. Cette permission est désormais
intentionnelle pour la recherche visuelle de l'ADR 0007. Le build s'arrête et le
rapport JaCoCo n'est pas généré ; aucune métrique de couverture nouvelle n'est
revendiquée à partir de cet essai. [Essai archivé](../../build/reports/validation/m2-object-actions/attempt1/quality.log).

Le test est actualisé pour exiger exactement `CAMERA`, `INTERNET` et la permission
interne AndroidX propre au package. Les interdictions d'état réseau, audio et accès
général aux médias/stockage sont conservées. Aucun test n'est supprimé ni seuil
affaibli : le changement exprime le nouveau contrat et ferme la liste des droits
autorisés. Les quatre tests de cette classe et le formatage passent sur ce delta.
[Preuve ciblée](../../build/reports/validation/m2-object-actions/coder/green-manifest-contract.log).

## Contrôle global final

Après relecture indépendante du contrat de permissions, la commande
`scripts/quality.ps1 -BaseRef 09ee6bef5cff35ce95c52f6a78d196d34ce1aaf6`
termine avec le code 0 : douze étapes conformes, 207 tests Android sans échec,
erreur ou ignoré, 76 tests de scripts et quatre tests PC. Les rapports sont copiés
avant toute validation ciblée ultérieure pour préserver la preuve globale.
[Journal final](../../build/reports/validation/m2-object-actions/quality-confirmed.log),
[synthèse archivée](../../build/reports/validation/m2-object-actions/final-quality/quality/summary.json),
[XML des tests](../../build/reports/validation/m2-object-actions/final-quality/android-tests),
[couverture JaCoCo](../../build/reports/validation/m2-object-actions/final-quality/jacoco).

| Périmètre | Lignes globales | Branches globales | Lignes nouvelles | Branches nouvelles |
| --- | --- | --- | --- | --- |
| Android | 96,44 % | 85,66 % | 98,56 % | 80,97 % |
| Scripts Python | 94,35 % | 88,52 % | 100 % | non applicable |
| PC inchangé | 100 % | 100 % | non applicable | non applicable |

Android : 347 lignes exécutables nouvelles, duplication nouvelle 0/551 lignes
analysables, duplication Kotlin globale 0,2380 %. Scripts : une ligne nouvelle
analysable, aucune duplication ; pas de branche nouvelle mesurable. Complexités
cyclomatique/cognitive conformes aux limites 10/15, aucune violation. Aucun seuil,
exclusion ou configuration de qualité n'est affaibli.

Audit runtime à ce passage : 97 packages Maven, 131 entrées de licence ; 35 packages
PyPI du socle PC et 35 entrées de licence ; aucun avis actif retourné dans ces scopes.
Ce constat ne couvre pas les dépendances privées de préparation ML ni l'intégralité
des outils de développement. Aucune dépendance runtime n'est ajoutée par la recherche.

## APK, installation et limites appareil

`dist/blamera-0.9.0-debug.apk`, **457 587 683 octets**, SHA-256
`4a5a7fb78eaeb3bb6aeaf79fa37aada91b366dd283c74a0afb3147f7dcb6b0ba`.
Le testeur indépendant corrobore package `dev.blamera`, version 0.9.0/code 13,
SDK min/target/compile 36, arm64-v8a et certificat personnel inchangé
`eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555`.
Les trois permissions exactes sont CAMERA, INTERNET et la permission interne
AndroidX du package. Les 22 actifs de diagnostic et trois fichiers français ont
les mêmes octets/empreintes qu'en 0.8.0 ; cinq ONNX, Large présent une seule fois.
[Preuve du package](../../build/reports/validation/m2-object-actions/tester/package.json).

Les préflights sans appareil se sont arrêtés sans tentative d'installation.
Après reconnexion confirmée par l'utilisateur, l'orchestrateur vérifie le Xiaomi
cible unique, la taille et le SHA de l'APK ainsi que la preuve PC. Le 29 octobre
2026, **mise à jour principale réussie** avec conservation des données :
`adb -d install -r`, sortie 0 « Performing Streamed Install / Success ».
[Preuve d'installation](../../build/reports/validation/m2-object-actions/install-result.json).
Aucun lancement automatique ni autre manipulation sur le téléphone. Les APK
précédents restent conservés localement ; la recette réelle est demandée à l'utilisateur.

Le vrai résultat Google de la bonne image et sa compatibilité avec les cookies
du navigateur restent à valider manuellement dans W02. Aucun succès physique ni
latence ou pertinence Google n'est déduit du spike ou des tests simulés. Aucun
lancement automatique, pilotage, capture d'écran, lecture de photos personnelles
ou modification de réglage n'est effectué sur le Xiaomi.

## Validation indépendante finale

Verdict **CONFORME_PC_ONLY** : 43 tests ciblés dans neuf classes, aucune erreur,
échec ou test ignoré ; huit tests du lecteur exécutés indépendamment. Le rejeu du
rapport historique conserve 56 lignes et 437 paires de détections pour les quatre
identités supportées, dont 0.9.0/13 ; neuf variantes invalides restent rejetées.
Ce rejeu de métadonnées n'est pas un nouveau comparatif sur téléphone.

Les 216 empreintes de sources, tests et configuration sont inchangées pendant la
validation, ainsi que les 492 fichiers de preuve globale archivés et l'APK final.
Le testeur examine aussi les deux PNG et confirme la lisibilité de la fiche et du
panneau paysage défilant. Aucun nouveau transfert réel à Google ou accès téléphone
n'est réalisé par le testeur.
[Verdict et commandes](../../build/reports/validation/m2-object-actions/tester/tester-results.json).

Lint : zéro erreur, 36 avertissements conservés. Dix-huit ressources inutilisées
restent dans le nettoyage historique D-M1-03 (dix-neuf à son introduction) ; deux
suggestions `UseKtx` proposent des écritures équivalentes pour URI et bitmap.
L'orchestrateur accepte ces suggestions de style comme non bloquantes sans modifier
le code validé ni désactiver les règles. Les autres avertissements concernent les
versions d'outillage/dépendances et les points déjà documentés.
[Rapport Lint archivé](../../build/reports/validation/m2-object-actions/final-quality/lint-results-debug.xml).
