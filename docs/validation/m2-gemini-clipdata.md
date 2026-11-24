# Variante publique du transfert Gemini — 0.9.4

30 octobre 2026 — diagnostic 0.9.4/code 17 revu et validé sur PC, installé après
reconnexion. Réception conjointe réelle en échec : image reçue, consigne absente.
Ticket M2-05 non validé, variante diagnostique uniquement.
Baseline : `dd79d18b44b44bc4b4e63a4fa9befbb004dd68c7`.

Le [premier essai](m2-gemini-share.md) est conforme sur PC, mais l'utilisateur
reçoit seulement l'image dans Gemini : « Image reçue, consigne absente ».
La cause interne à Gemini n'est pas établie. Ne pas prétendre que son traitement
d'EXTRA_TEXT est connu ni qu'une nouvelle représentation réparera le parcours.

## Hypothèse fondée et contrat

Notre intent 0.9.3 contient la consigne dans EXTRA_TEXT et seulement l'URI dans
ClipData. Le [code primaire AndroidX ShareCompat](https://android.googlesource.com/platform/frameworks/support/+/f24416ddf0d3fcd79fa2542aa90c391ce731f755/core/core/src/main/java/androidx/core/app/ShareCompat.java)
place aussi le texte dans le premier élément ClipData portant l'URI. La
[référence ClipData.Item](https://developer.android.com/reference/android/content/ClipData.Item)
décrit cette représentation publique texte/URI.

La variante conserve EXTRA_TEXT et ajoute la même consigne dans l'unique item,
avec le même URI, HTML et intent imbriqué nuls. Lens reste sans texte. MIME,
destinataires, grants de lecture, provider/cache/TTL, panneau, modèles et permissions
restent inchangés. Aucun presse-papiers système, extra privé, automatisme de saisie,
envoi ou API facturée. Le bouton reste « Tester Gemini ».

## Preuves requises

Le coder écrit et exécute le Red du texte absent de ClipData avant correction,
puis vérifie les deux représentations et leur conservation après sérialisation
Parcel. Il ne s'agit pas d'un test du vrai récepteur Gemini. Régression Lens et
parcours locaux conservés ; revue indépendante, contrôles globaux et testeur avant
installation principale seulement. Artefacts : `build/reports/validation/m2-gemini-clipdata/`.

Le même essai humain doit constater le bon recadrage ET la consigne préremplie,
avant toute saisie/copie manuelle. Sans les deux, aucun bouton définitif ni succès
conjoint déclaré. Cet essai supplémentaire est borné ; une nouvelle série de
variantes sans preuve ou un copier/coller de substitution exige de recadrer le
besoin avec l'utilisateur.

## TDD de la variante

Deux tests Red exécutés avant la correction échouent pour la raison attendue :
la consigne de l'élément ClipData est nulle, directement et après sérialisation
Parcel. La modification de production se limite au constructeur de cet élément,
avec la consigne existante, HTML/intent nuls et le même URI. Version et lecteur
passent à 0.9.4/code 17 ; les huit tests du lecteur sont verts après son Red.
La campagne élargie de partage, panneau, sélection et recadrage reste en cours.
[Red du contenu](../../build/reports/validation/m2-gemini-clipdata/coder/red-clipdata.log).

La campagne ciblée finale passe : **67 tests**, sans échec, erreur ou ignoré,
formatage, ktlint, detekt et JaCoCo conformes. Les cinq nouvelles lignes exécutables
sont couvertes ; aucune branche nouvelle, mesure correspondante non applicable.
Ces valeurs ciblées ne remplacent pas les couvertures globales. Les huit tests
du lecteur réussissent. Nouveau test de transport : `SharedImageParcelTest` ;
les assertions Gemini/Lens contrôlent les deux représentations et l'absence de
texte côté Lens. Aucun seuil ni exclusion changé. Code gelé pour revue.
[Métriques](../../build/reports/validation/m2-gemini-clipdata/coder/targeted-metrics.json),
[Green](../../build/reports/validation/m2-gemini-clipdata/coder/green-clipdata-regressions.log).

La revue indépendante est conforme, sans blocage : elle confirme le delta limité,
les assertions de Lens conservées/renforcées, le test de frontière Parcel et la
distinction entre format émis et comportement de Gemini. Aucun fichier d'interface,
manifeste, cache, provider ou configuration qualité n'est changé. La globale est
lancée sur cet état gelé ; le testeur commence ses vérifications de lecture sans
lancer de Gradle en parallèle.

## Contrôle global et package

`scripts/quality.ps1 -BaseRef dd79d18b44b44bc4b4e63a4fa9befbb004dd68c7` termine
avec **0** : **233 tests Android**, **76 tests de scripts** et **quatre tests PC**
réussissent. Aucun test Android ignoré, erreur ou échec. Build, formatage, ktlint,
detekt, lint, couverture, duplication, licences et scans passent.

| Mesure | Résultat final |
| --- | --- |
| Mobile, lignes / branches globales | 96,62 % / 85,71 % |
| Mobile, nouvelles lignes / branches | 5/5 = 100 % / non applicable, aucune branche nouvelle |
| Duplication Kotlin globale / nouveau code | 0,23 % / 0 ligne sur 6 |
| Scripts, lignes / branches globales | 94,35 % / 88,52 % |
| Scripts, nouvelle ligne / branches | 100 % / non applicable, aucune branche nouvelle |
| PC, lignes / branches globales | 100 % / 100 %, aucun code exécutable nouveau |
| Complexités cyclomatique / cognitive | Aucun dépassement des limites 10 / 15 |
| Dépendances et licences | 97 composants Maven / 131 entrées ; 35 paquets Python / 35 entrées |
| Vulnérabilités actives du périmètre scanné | 0 |

Les rapports sont archivés avant la validation ciblée indépendante.
[Synthèse finale](../../build/reports/validation/m2-gemini-clipdata/final-quality/quality/summary.json),
[log](../../build/reports/validation/m2-gemini-clipdata/quality.log),
[résultats Android](../../build/reports/validation/m2-gemini-clipdata/final-quality/android-tests).

Le candidat copié après `assembleDebug` et l'APK final sont identiques :
`dist/blamera-0.9.4-debug.apk`, **457 621 726 octets**, SHA-256
`4aa3ae5431ced75706738d9ad5f91f55cf798bfce5856cd5ab495f0425609048`.
Le contrôle indépendant confirme version/code, SDK36/arm64, certificat stable,
deux permissions `CAMERA` et interne AndroidX sans `INTERNET`, trois queries et
provider privé inchangés. Les 25 actifs documentés, dont cinq ONNX et un seul Large,
sont identiques à 0.9.3 ; tous les 32 payloads d'assets sont identiques. Aucune
entrée ZIP ajoutée/supprimée, seuls manifeste/version et quatre DEX changent.
Le bouton reste provisoire, les 228 empreintes sources sont inchangées.
[Package](../../build/reports/validation/m2-gemini-clipdata/tester/package.json),
[comparaison ZIP](../../build/reports/validation/m2-gemini-clipdata/tester/zip-delta.json).

## Validation indépendante finale et préflight appareil

Verdict **conforme sur PC** : 67 tests indépendants dans treize classes, zéro échec,
erreur ou ignoré, sortie 0, et huit tests du lecteur réussis. Rejeu historique
inchangé. Après exécution, 228 empreintes de sources et 526 fichiers de rapports
globaux restent identiques ; les APK build/candidat/`dist/` sont inchangés.
[Verdict et preuves](../../build/reports/validation/m2-gemini-clipdata/tester/tester-results.json).

Le 29 octobre, les contrôles avant installation relisent verdict, globale, sources
et empreinte APK. Le préflight ne trouve plus de téléphone prêt : zéro appareil ADB et zéro
Xiaomi attendu. La procédure s'arrête **avant** tout `adb install`, sans lancer
d'application ni modifier le téléphone. Aucun reçu de succès n'est produit.
Une demande de reconnexion est adressée à l'utilisateur ; 0.9.3 reste alors la dernière
version installée. [Préflight historique sans identifiant](../../build/reports/validation/m2-gemini-clipdata/install-preflight.json).

## Installation après reconnexion — 30 octobre

L'utilisateur confirme le téléphone connecté. Les verdicts logiciel et package,
la globale, les 228 empreintes de sources et la taille/SHA de l'APK sont relus et
inchangés ; l'unique Xiaomi attendu est présent. Sans reconstruire ni relancer les
tests déjà applicables, la mise à jour du seul APK principal réussit le 30 octobre
2026 : `adb -d install -r`, sortie 0 « Performing Streamed Install / Success ».
Aucun lancement automatique, lecture de données privées ou autre mutation téléphone.
[Reçu sans identifiant appareil](../../build/reports/validation/m2-gemini-clipdata/install-result.json).

L'utilisateur est invité à ouvrir Blamera manuellement, sélectionner un objet et
toucher « Tester Gemini ». Il doit vérifier avant toute saisie/copie manuelle le
bon recadrage et la consigne dans le message à envoyer.

## Retour humain et arbitrage — 30 octobre

Réponse exacte : **« Toujours pas de texte dans gemini. L'image y est »**.
L'image arrive, mais la consigne n'est pas préremplie dans ce parcours, même avec
les deux représentations du texte. Le critère essentiel de M2-05 échoue ; les
conformités PC et de package restent des preuves du logiciel émis, pas de la
réception par Gemini. Ni la cause interne ni une impossibilité générale de Gemini
ne sont établies. Aucun accès aux données de Gemini ; aucune photo personnelle reçue.

La série bornée d'essais publics s'arrête. Pas d'extras privés, modification MIME
arbitraire, presse-papiers ou saisie automatique pour forcer un succès. Le bouton
définitif « Demander à Gemini » n'est pas livré ; l'APK 0.9.4 reste le diagnostic installé.

Deux suites sont proposées, sans implémentation ni décision acquise :

- Étudier l'API Gemini pour transmettre image et consigne ensemble et afficher la
  réponse dans Blamera. La [documentation d'entrée image/texte](https://ai.google.dev/gemini-api/docs/image-understanding)
  décrit ce contrat ; l'[offre gratuite](https://ai.google.dev/gemini-api/docs/pricing)
  et les [quotas](https://ai.google.dev/gemini-api/docs/rate-limits) restent à vérifier
  pour le compte et le modèle retenus. Une clé est nécessaire ; aucune facturation
  ni requête utilisateur n'est activée par cette étude documentaire.
- Si l'utilisateur accepte un parcours manuel, garder le partage d'image vers
  l'application Gemini et proposer une copie explicite de la consigne à coller.
  Cela modifie le critère initial et exige ce choix explicite, sans copie automatique.

La préférence est demandée pendant la mise à jour des preuves. Aucune modification
de production, test supplémentaire ou opération téléphone pour consigner ce retour.
