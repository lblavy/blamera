# Intégration ChatGPT et rendu Markdown

31 octobre 2026 — M2-03/M2-04/M2-03-UI-01.
Baseline : `882deea0ff66019dec3b944dc13071342b58c556`.
Version 0.11.0/code23 : revue, contrôles globaux et testeur conformes sur PC.
APK principal **installé** le 31 octobre après reconnexion USB. Recette réelle en attente.

[Contrat](../specs/object-chatgpt.md) et
[recette manuelle](../tests/m2-object-chatgpt-recette.md).

Deux périmètres d'implémentation distincts : rendu Markdown natif et dépendance
épinglée d'une part ; intégration du document et de la session ChatGPT dans la
fiche d'autre part. Les agents n'écrivent pas les mêmes fichiers et sérialisent
leurs exécutions Gradle pour conserver des preuves lisibles. La revue indépendante
commence après gel de l'ensemble des sources ; le testeur intervient ensuite.

L'essai 0.10.4 a validé le transfert synthétique sur le Xiaomi. Cette preuve ne
vaut pas validation de l'intégration d'une sélection réelle. Aucun appel authentifié
sur PC, lecture de credentials existants ou transfert automatique de photo.

Les preuves TDD et métriques seront consignées après leur exécution réelle.
Premier Red Markdown : `build/chatgpt-markdown-red.log`, sortie 1, 43 secondes,
un test/un échec fonctionnel ComparisonFailure sur les marqueurs `**` visibles,
compilation réussie. Le premier lancement sandbox bloqué par la communication
avec Gradle est un incident d'infrastructure, jamais compté comme Red.

Rendu en cours : CommonMark 0.30.0, plafond 64 K caractères, profondeur 32 et
2048 nœuds ; repli en texte brut borné pour les entrées trop complexes. Ces
limites sont distinctes des bornes HTTP existantes.

Un premier Green Markdown a rencontré une erreur d'API Compose à la compilation,
puis un autre a croisé la création des tests d'intégration alors que leur
squelette n'était pas encore compilable. Ces échecs ne constituent pas des Red
fonctionnels. Les écritures Kotlin sont désormais gelées pendant chaque run
Gradle ; les deux périmètres gardent leurs créneaux sérialisés.

Green final Markdown : `build/chatgpt-markdown-green-final.log`, sortie 0,
1 min 03 s, 13 tests sans échec. JaCoCo du package : 129/131 lignes (98,47 %),
105/109 branches (96,33 %). Formatage et detekt ciblés : sorties 0. Le lecteur
PC reproduit le refus de la nouvelle paire 0.11.0/code23 (huit tests, une erreur
attendue), puis ses huit tests passent. Archives du run ciblé dans
`build/reports/validation/m2-object-chatgpt/markdown/`, avant toute nouvelle
exécution Gradle d'intégration. Ces mesures ne constituent pas la baseline globale.
Le rendu active le cœur CommonMark ; les extensions GFM, notamment les tableaux,
ne sont pas activées et restent du texte lisible. L'API ne promet pas leur mise
en page sous forme de grille.

Revue indépendante du module Markdown gelé : conforme, sans bloqueur. Le reviewer
a vérifié styles effectifs, structure, code, liens explicitement ouverts, images
et HTML inertes, bornes et tests archivés. Aucun test relancé ni accès au téléphone
ou au compte ; l'intégration complète sera revue après son propre gel. L'audit
global de la nouvelle dépendance reste à exécuter.

Vérification de licence : le JAR CommonMark porte `META-INF/LICENSE.txt`, exclu
par défaut par AGP 8.13.2. Une copie exacte est donc ajoutée comme asset
`licenses/commonmark-0.30.0-BSD-2-Clause.txt`, contenant le copyright 2015
Atlassian Pty Ltd et les conditions d'origine. Source du binaire : entrée du JAR
CommonMark 0.30.0 résolu par Gradle, 1300 octets, SHA-256
`11cfcf6da0142fb1971c1f40f5924ddded14454e2793d7f1dd87c9f4abe42dfd`.
Le testeur vérifiera cette entrée dans l'APK final ; les 37 assets/bibliothèques
précédents doivent rester identiques. Ce changement documentaire/de ressource
se valide par empreinte et packaging, sans test TDD artificiel.

Red état documentaire : `build/object-chatgpt-state-red.log`, sortie 1, 13 s,
cinq tests/quatre échecs. Un test lisait un événement absent via `single`/`last`
et produisait NoSuchElement ; des assertions de compte ont ensuite été ajoutées
pour rendre ce défaut de notification explicite. Red intégration :
`build/object-chatgpt-integration-red.log`, sortie 1, 57 s, dix tests/neuf échecs
(quatre état, deux identité, trois Compose avec bouton absent).

Après implémentation de ce périmètre : `build/object-chatgpt-main-red.log`, sortie
1, une minute, dix tests verts et le nouveau test MainActivity rouge. Celui-ci
expose l'absence de factory pour son ViewModel (NoSuchMethod au constructeur).
Ce résultat intermédiaire n'est pas un Green global : le câblage de l'activité,
les cas de rotation et les métriques de l'intégration restent en cours.

Premier Green intégration/régressions : `build/object-chatgpt-green-first.log`,
sortie 0, 52 secondes. Le host/factory et l'observation de présence sont partagés
entre les deux activités ; le protocole est inchangé. Trois écarts detekt ont
été corrigés par extraction du binding de fiche et regroupement cohérent de ses
outils ; formatage et detekt finaux sortent à 0. Un run ciblé plus complet avec
JaCoCo ajoute les rotations MainActivity, requête en cours puis réponse,
annulation et régressions photo/capture avant le gel final.

Ce run large donne 161 succès sur 162 tests : le seul échec porte sur la fixture
du repli d'aperçu invalide. Sans GraphicsMode.NATIVE, le shadow BitmapFactory
accepte des octets invalides ; la fixture adopte donc le mode natif déjà utilisé
pour les tests image. Aucun changement de production ni assertion affaiblie pour
cette correction. Les cas de rotation de l'activité principale sont verts.

Green suivant : `build/object-chatgpt-green-final.log`, sortie 0, 53 secondes,
162 tests sans échec. Nouveau code contre la baseline : 441 lignes exécutables,
98,19 % lignes / 88,69 % branches. État documentaire 100 % / 90 % ; fiche ChatGPT
82,35 % / 100 % ; contrôles de compte 100 % / 88 % ; aperçu JPEG 100 % / 100 %.
Le host n'a pas de branche mesurable. Le composant existant ObjectInformationCard
atteint 100 % lignes mais 76 % branches sur ce run ciblé : le cas préparation
ratée puis réessai sans envoi est ajouté avant le gel, sans exclusion ou baisse
de seuil. Ces résultats intermédiaires seront remplacés par les métriques finales.

Les deux scénarios supplémentaires de préparation passent, mais le run de 164
tests révèle une fixture pixel existante instable dans CaptureWorkflowTest : le
centre d'écran était réutilisé après le redimensionnement asynchrone du panneau,
et lisait sa couleur au lieu du masque. La fixture attend la préparation puis
recalcule les coordonnées pour chaque sélection/désélection. Les assertions de
couleurs et de désélection sont conservées ; aucun changement de production pour
ce cas. Le dernier run sur cet état reste nécessaire avant le gel.

## État gelé et mesures ciblées finales

Sources gelées : 286 empreintes de sources/configuration dans
`build/reports/validation/m2-object-chatgpt/source-snapshot.json`. Dernier run :
`build/object-chatgpt-green-final.log`, sortie 0, 49 s, **164 tests sans échec ni
erreur**. XML archivés dans `coder/final-test-results/`, JaCoCo dans
`jacoco-targeted.xml`, mesures dans `coder/metrics.json` du même dossier de preuve.
Formatage et detekt finaux : sorties 0.

| Périmètre ciblé | Lignes | Branches |
| --- | --- | --- |
| Nouveau code, intégration seule hors Markdown, 310 lignes exécutables | 98,06 % | 83,33 % |
| Nouveau code, ensemble, 441 lignes exécutables | 98,19 % | 88,69 % |
| État documentaire | 100 % | 90 % |
| Fiche ChatGPT | 82,35 % | 100 % |
| Contrôles de compte | 100 % | 88 % |
| Aperçu JPEG | 100 % | 100 % |
| Host partagé | 100 % | Non mesurée, aucune branche mesurable |

Le fichier existant ObjectInformationCard modifié reste à 100 % lignes / 77 %
branches dans ce ciblé ; il n'est pas présenté conforme individuellement.
Les nouveaux composants et le nouveau code d'intégration dépassent 80/80 ; la
baseline globale sera mesurée avec tous les tests. Aucun seuil ou exclusion modifié.

Captures PC synthétiques relues en portrait, paysage et après rotation dans
`coder/screenshots/`. Elles ne constituent pas une preuve du Xiaomi. L'échec
pixel et sa correction de fixture sont archivés dans `coder/workflow-geometry/`.
À ce stade du développement, la revue complète et les contrôles globaux restaient
à faire et le téléphone devait être reconnecté pour la mise à jour.

Revue complète terminée : **conforme, aucun blocage**. Le reviewer confirme les
clés et notifications synchrones, les gardes de génération/validité, la propriété
du JPEG, l'absence d'envoi implicite, les gardes de crédits et le host retenu.
Il a relu les Red, les 164 XML verts et les corrections de fixtures sans relancer
Gradle ni utiliser le téléphone ou le compte. L'asset de licence source est
conforme par empreinte ; sa présence dans l'APK reste à vérifier. Le run global
est lancé contre la baseline ci-dessus avant validation finale du testeur.

## Contrôles globaux

Commande exécutée, sortie **0**, tous les contrôles configurés conformes :

```powershell
./scripts/quality.ps1 -BaseRef 882deea0ff66019dec3b944dc13071342b58c556
```

Archives dans `build/reports/validation/m2-object-chatgpt/` : `quality.log`,
`quality-final/summary.json`, `jacoco-global.xml`, `junit-global/` et
`junit-counts.json`. **349 tests Android, 76 scripts et quatre PC**, sans échec,
erreur ou test ignoré. Les 286 empreintes de sources/configuration sont inchangées.

| Mesure | Résultat |
| --- | --- |
| Mobile global, lignes / branches | 96,77 % / 87,33 % |
| Nouveau mobile, lignes / branches | 98,19 % / 88,69 %, 441 lignes exécutables |
| Duplication nouvelle mobile | 1/712 lignes, 0,140 % ; globale 0,238 % |
| Complexité cyclomatique / cognitive | Aucune violation des limites 10/15 |
| Scripts globaux, lignes / branches | 94,35 % / 88,52 % |
| Nouveau script | Lignes 100 % ; aucune branche nouvelle mesurable |
| PC global, lignes / branches | 100 % / 100 % ; aucun nouveau code mesurable |
| Audits et licences | 98 paquets Maven / 132 entrées, 35 PyPI / 35 entrées |

Aucune vulnérabilité active retournée dans les inventaires et avis consultés.
La portée reste celle des contrôles documentés ; aucune règle, exclusion ou
dépendance existante n'est assouplie pour faire passer cette campagne.

Limite explicite : ObjectInformationCard reste à 79/79 lignes et 77/100 branches
sur la campagne globale. Les seuils du nouveau code et des composants mesurés
sont respectés ; cette mesure par fichier n'est pas déclarée conforme à 80 %.
La revue ne trouve pas de scénario essentiel manquant ni de défaut bloquant.
Suivi secondaire M2-04-COV-01 : compléter les chemins conditionnels restants de
cette fiche lors de son évolution ; impact actuel limité à une preuve partielle
de ces branches, sans seuil abaissé ou exclusion ajoutée.

APK contrôlé : `dist/blamera-0.11.0-debug.apk`, **458 108 884 octets**,
SHA-256 `32febcfe3bac11c5087ab00d5365067352380a3c22ff2b97a52f050f4219cc19`.
L'installation est confirmée ci-dessous ; la validation du parcours sur Xiaomi reste ouverte.

## Validation indépendante finale et installation

Testeur **conforme sur PC**, sans défaut bloquant reproduit : **195 tests ciblés
élargis**, sortie 0 en 1 min 19 s, incluant Lens/Gemini (`information.*`), et huit
tests verts du lecteur. Rapport et preuves dans
`build/reports/validation/m2-object-chatgpt/tester/` (`report.md`, JUnit et JSON).
Les 286 empreintes restent inchangées après ces exécutions.

Le testeur confirme le même APK construit et copié dans `dist`, la version23,
les permissions inchangées et l'activité d'essai privée `fullUser`. Les 37
assets/bibliothèques précédents sont identiques à 0.10.4 ; le seul ajout de
ressource est la licence CommonMark, avec ses 1300 octets et son empreinte exacte.
Certificat debug SHA-256 inchangé :
`eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555`.

Préflight d'installation le 31 octobre 2026 à 00:59:03 UTC : aucun appareil prêt,
aucune installation tentée. Reçu local :
`build/reports/validation/m2-object-chatgpt/install-preflight.json`.
Après réponse de l'utilisateur, le Xiaomi est reconnecté. Les 286 empreintes de
sources et l'empreinte de l'APK validé ont été revérifiées sans reconstruire ni
réexécuter les tests, puisqu'aucun code n'a changé.

Installation du seul APK principal `dist/blamera-0.11.0-debug.apk` avec
conservation des données : `adb -d install -r`, sortie **0 « Success »**, le
31 octobre 2026 de **10:29:15 à 10:29:35 UTC**. Reçu local :
`build/reports/validation/m2-object-chatgpt/install-result.json`.
Aucun lancement automatique ni autre mutation du téléphone. Aucun test APK,
test instrumenté, changement de paramètres ou lecture d'autres applications.
Les critères essentiels sur appareil restent ouverts : vrai recadrage et consigne,
connexion au retour navigateur, réponse pertinente dans la fiche, Markdown,
rotation et changement de sélection. La [recette](../tests/m2-object-chatgpt-recette.md)
est prête. M2-03/M2-04 ne sont pas déclarés terminés avant ce retour humain.

Pas de TDD artificiel pour cette actualisation documentaire : reçu, version,
empreinte, liens et statuts sont les preuves appropriées. La recette matérielle
reste à l'utilisateur ; l'installation ne prouve pas le parcours réel.
