# Essai de transfert image et consigne à Gemini

29 octobre 2026 — diagnostic 0.9.3/code 16 revu et validé sur PC, mise à jour
principale installée. L'essai humain reçoit l'image mais pas la consigne : réception
conjointe en échec. Ticket M2-05, bouton définitif non livré.
Baseline : `14778f81228c44e667e5f41186a749bb937dca32`.

Le [contrat](../specs/gemini-image-share.md) exige la réception conjointe du JPEG
de l'objet sélectionné et de la consigne française fixe dans l'application Gemini.
Le bouton définitif dépend du résultat réel. Les tests PC vérifieront le contrat
émis et les erreurs ; seule la manipulation humaine montrera ce que Gemini affiche.

Organisation : coder en TDD, revue indépendante de l'état gelé, corrections utiles,
contrôles globaux et validation indépendante. Les artefacts seront conservés dans
`build/reports/validation/m2-gemini-share/`. Les anciens rapports Lens restent intacts.
Sur téléphone, seuls la mise à jour de l'APK principal et les logs Blamera sont
autorisés ; ouverture, vérification et envoi dans Gemini restent à l'utilisateur.

## Développement et preuves ciblées

Le Red fonctionnel reproduit deux échecs : la route Gemini est indisponible au lieu
d'ouvrir le destinataire simulé, et le bouton de diagnostic n'existe pas. Les deux
tests passent ensuite, avant extraction du sender commun. Le lecteur versionné
0.9.3/code 16 a son Red puis huit tests réussis.

Une campagne de 48 tests ciblés, formatage, detekt et JaCoCo réussit après deux
corrections de formatage. Ces erreurs de format ne sont pas des Red fonctionnels.
Les PNG portrait/paysage sont examinés par le coder. Les lignes du nouveau code
sont couvertes à 87/87, mais les branches à 37/48 = 77,08 %, sous le seuil requis.
Le coder complète les scénarios utiles de remise synchrone, arrière-plan,
annulation et nouvelle tentative après refus. Il ne change ni seuil ni baseline.
Ces ajouts de couverture après implémentation ne sont pas présentés comme un
nouveau cycle TDD. L'état n'est pas encore gelé pour revue et aucune validation
globale ni installation n'est déclarée à ce stade.

Après ces ajouts, la campagne finale ciblée réussit : **49 tests**, zéro échec,
erreur ou ignoré, avec formatage, ktlint, detekt et JaCoCo. Le nouveau code atteint
**87/87 lignes = 100 %** et **40/48 branches = 83,33 %**. Les métriques globales de
ce run ciblé sont partielles et ne valent pas contrôle global. Le code est gelé
pour revue indépendante ; aucun changement de provider, cache, modèle ou permission.
[Métriques ciblées](../../build/reports/validation/m2-gemini-share/coder/targeted-metrics.json),
[log](../../build/reports/validation/m2-gemini-share/coder/green-gemini-regressions-4.log).

Le JPEG synthétique recadré est lu via le vrai ContentResolver, puis décodé par
Pillow : **66 × 44 pixels, 1 317 octets, aucune entrée EXIF**, objet central rouge
et contexte vert attendus. Le shadow Windows existant reste limité à la construction
statique AndroidX de l'URI ; il ne remplace ni le provider applicatif ni les octets
lus. Les limites AndroidX/Android décrites dans la [validation Lens](m2-lens-handoff.md)
restent applicables. Les captures synthétiques portrait/paysage sont examinées par
le coder et l'orchestrateur : les actions sont accessibles dans le panneau défilant.
[Décodage indépendant](../../build/reports/validation/m2-gemini-share/coder/independent-crop-decode.json).

## Revue indépendante

Verdict conforme pour le diagnostic, aucun blocage ni correction utile retenue.
La revue confirme l'intent image/consigne ciblé, la route Lens inchangée, les
annulations et demandes croisées, ainsi que l'absence de modification du provider,
du cache, du TTL, des modèles, des dépendances et des permissions. Seule la query
Gemini est ajoutée. Les preuves Red/Green, XML, métriques, JPEG et rendus sont lus.
Le contrôle global est lancé sur cet état gelé, avec la baseline ci-dessus ; le
testeur démarre ses vérifications sans lancer de Gradle en parallèle.
La réception effective image et consigne dans Gemini reste non prouvée.

## Premier contrôle global : régression du panneau

La commande `scripts/quality.ps1 -BaseRef 14778f81228c44e667e5f41186a749bb937dca32`
termine avec **1** : 230 tests Android exécutés, trois échecs. Le choix d'un objet
superposé n'est plus affiché dans `CaptureWorkflowTest` ; deux scénarios de
`PhotoRegionUiTest` expirent au second recadrage. Les nouveaux contenus de la
fiche précèdent ces contrôles dans un panneau de hauteur bornée et les repoussent
hors de la zone visible. Il s'agit d'une régression utilement détectée, sans
attribution à une instabilité de tests.

Les résultats, le lint et le log sont figés dans
[attempt1](../../build/reports/validation/m2-gemini-share/attempt1/quality/summary.json).
Le rapport JaCoCo global n'est pas produit après les échecs ; aucune conformité de
couverture globale n'est déduite du run. Le coder reprend la production pour
placer statut, choix d'objet et commandes de recadrage avant la fiche de partage
défilante. Les trois tests existants sont conservés et une régression après
préparation complète est ajoutée en Red avant correction. Installation suspendue
jusqu'à relecture et contrôles sur le nouvel état.

Le Red ajouté reproduit bien deux échecs « Analyser cette zone n'est pas affiché »
en portrait et paysage, après préparation complète. Après le seul déplacement de
la fiche dans `PhotoResults`, **66 tests ciblés réussissent**, dont les trois
régressions initiales ; `CaptureWorkflowTest` et `PhotoRegionUiTest` restent
intégralement inchangés. Formatage, ktlint, detekt et JaCoCo passent. Le nouveau
code atteint 88/88 lignes et 40/48 branches (83,33 %) sur la campagne ciblée.
Les PNG actualisés montrent les commandes essentielles accessibles et la fiche
de partage toujours défilante. Code regelé pour relecture ; la globale complète
reste à refaire.
[Red](../../build/reports/validation/m2-gemini-share/coder/red-panel-controls.log),
[Green](../../build/reports/validation/m2-gemini-share/coder/green-panel-controls.log),
[métriques](../../build/reports/validation/m2-gemini-share/coder/panel-controls-metrics.json).

La relecture indépendante de la correction est conforme, sans nouveau blocage :
le déplacement garde le panneau défilant et les contrôles essentiels visibles.
Le testeur renouvelle les 227 empreintes ; seuls `PhotoResults` et son test UI
changent depuis la première phase. Les anciens tests en échec restent identiques.
Une nouvelle globale complète est lancée sur cet état gelé, avec la même baseline.

## Contrôle global final et APK

La même commande et la même baseline terminent avec **0** sur l'état corrigé.
**232 tests Android**, **76 tests de scripts** et **quatre tests PC** réussissent.
Aucun échec, erreur ou ignoré Android. Build, formatage, ktlint, detekt, lint,
couvertures, duplication, licences et scan des dépendances passent.

| Mesure | Résultat final |
| --- | --- |
| Mobile, lignes / branches globales | 96,61 % / 85,71 % |
| Mobile, nouvelles lignes / branches | 88/88 = 100 % / 40/48 = 83,33 % |
| Duplication Kotlin globale / nouveau code | 0,23 % / 0 ligne sur 124 |
| Scripts, lignes / branches globales | 94,35 % / 88,52 % |
| Scripts, nouvelle ligne / branches | 100 % / non mesurées, aucune nouvelle branche |
| PC, lignes / branches globales | 100 % / 100 %, aucun code exécutable nouveau |
| Complexités cyclomatique / cognitive | Aucun dépassement des limites 10 / 15 |
| Dépendances et licences | 97 composants Maven / 131 entrées ; 35 paquets Python / 35 entrées |
| Vulnérabilités actives du périmètre scanné | 0 |

Les rapports sont figés avant la nouvelle campagne ciblée du testeur.
[Synthèse finale](../../build/reports/validation/m2-gemini-share/final-quality/quality/summary.json),
[log](../../build/reports/validation/m2-gemini-share/quality-final.log),
[résultats Android](../../build/reports/validation/m2-gemini-share/final-quality/android-tests).

Le candidat copié après `assembleDebug` et l'APK final sont identiques :
`dist/blamera-0.9.3-debug.apk`, **458 002 233 octets**, SHA-256
`03810d5a8c76fe231e82b4a5fca8c6c6ffabbda1866a08a2fa15dd4cac202a40`.
Le package et les tests ciblés sont vérifiés indépendamment avant installation.
Ces preuves ne valident toujours pas la réception effective des deux éléments
par Gemini, qui nécessite l'essai humain de la recette.

L'écart de 397 544 octets avec l'APK 0.9.2 est expliqué par le contrôle indépendant
des entrées ZIP : 5 351 octets de données compressées, 11 527 d'alignement,
380 536 de six enregistrements vides remplis de zéros et 130 d'en-têtes/répertoire.
Seule nouvelle entrée nommée : `ic_message.xml`. Aucun rapport, fixture photo ou
autre donnée inattendue ajouté au package.
[Détail binaire](../../build/reports/validation/m2-gemini-share/tester/zip-delta-explanation.json).

## Validation indépendante finale et installation

Verdict **conforme sur PC** : 66 tests indépendants dans douze classes, zéro
échec, erreur ou ignoré, sortie 0, et huit tests du lecteur réussis. Les trois
anciennes régressions passent sans modifier leurs classes. Le JPEG synthétique
est régénéré identique ; 227 empreintes de sources et 525 fichiers de rapports
globaux sont inchangés. Les APK build/candidat/`dist/` sont identiques.

Le package est bien `dev.blamera` 0.9.3/code 16, SDK 36, arm64-v8a ; certificat
SHA-256 `eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555` identique.
Deux permissions seulement : `CAMERA` et la permission interne AndroidX, sans
`INTERNET`. Trois queries Google prévues, provider et chemin privé inchangés ;
les 25 actifs contrôlés dont cinq ONNX sont identiques à 0.9.2. Le libellé reste
« Tester Gemini », sans prétention de réception ou d'identification réussie.
[Verdict et preuves](../../build/reports/validation/m2-gemini-share/tester/tester-results.json),
[package](../../build/reports/validation/m2-gemini-share/tester/package.json).

Après contrôle des verdicts, des empreintes de sources, de l'APK et de l'unique
Xiaomi attendu, la mise à jour du seul APK principal réussit le 29 octobre 2026 :
`adb -d install -r`, sortie 0 « Performing Streamed Install / Success ». Aucun
lancement automatique ni autre mutation sur le téléphone.
[Reçu sans identifiant appareil](../../build/reports/validation/m2-gemini-share/install-result.json).

La question de recette demande à l'utilisateur d'ouvrir lui-même Blamera, choisir
un objet puis « Tester Gemini », et de constater avant saisie/copie manuelle le
bon recadrage et la consigne préremplie. La réponse dans Gemini et le retour à
Blamera peuvent ensuite être essayés manuellement. L'utilisateur répond ensuite
**« Image reçue, consigne absente »**. Le transfert du recadrage fonctionne dans
cet essai, mais la consigne n'est pas préremplie : M2-05 reste à corriger. Les
validations PC conformes ne prouvent pas le traitement des extras par Gemini.
La variante publique suivante est suivie dans la [validation ClipData](m2-gemini-clipdata.md).
