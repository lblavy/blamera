# M1-10 — cadrage, direct intégré et catégories françaises

Développement, revue et validation PC conformes le 28 octobre 2026.
Mise à jour installée et parcours essentiel accepté par l'utilisateur le 29 octobre 2026.
Version visée : 0.8.0/code 12. Baseline du nouveau code :
`3b031de2454034d1073b8f0b3c0b144f8d659aed`.

## Retour utilisateur et contrat

L'utilisateur confirme globalement que le parcours 0.7.0 fonctionne, avec trois demandes :

- Pouvoir relancer l'analyse sur la partie visible après zoom ou déplacement.
- Activer les contours en direct dans la nouvelle interface, sans retour à l'ancienne.
- Traduire les noms de catégories en français.

Ce constat ne valide pas individuellement les seize cas de recette, les temps de
réponse ni l'usage prolongé. L'utilisateur confirme explicitement « Oui, zone recadrée
avec retour à l'original » : la portion visible devient l'image de travail et un bouton
restaure l'original et ses résultats sans nouvelle inférence.

Après installation de 0.8.0, l'utilisateur répond « Tout fonctionne » à la vérification
du direct dans la caméra actuelle, des noms français et de l'analyse du cadrage après
capture ou import avec retour à l'original et ses résultats. Ce retour clôt M1-10,
avec les validations PC et indépendantes ci-dessous. Il ne constitue pas une mesure
de délai, ni une validation des essais prolongés ou hors ligne de M1-09. La
[recette ciblée](../tests/m1-photo-refinements-recette.md) distingue les vérifications
essentielles confirmées et les variantes complémentaires non rapportées.

## Périmètres

L'agent interface traite caméra, gestes, cadrage et tests UI. L'agent modèle traite
le catalogue d'affichage français et la compatibilité de version du lecteur diagnostic.
Un agent distinct prépare les 4 585 traductions statiques sur le PC, avec modèle
de traduction libre, provenance et révision des termes usuels et ambigus.

Le modèle de vision Large, les identifiants de catégories, les données anglaises et
les références scientifiques ne sont pas modifiés. Le téléphone ne charge pas de
modèle de traduction et n'appelle aucun service de traduction. Les noms proposés
restent des prédictions du modèle et peuvent se tromper.

Preuves à produire sous `build/reports/validation/m1-photo-refinements/` : Red et
Green réellement exécutés, catalogue/provenance, qualité, revue, validation indépendante,
APK et éventuelle mise à jour. Aucun contrôle n'est déclaré conforme par anticipation.

## Direct et recadrage

Le direct utilise le moteur léger existant dans le même aperçu, avec Preview,
ImageAnalysis et ImageCapture réunis dans un ViewPort commun. L'arrêt efface les
contours ; capture, galerie, pause et rotation invalident les réponses précédentes.
Lors d'une capture depuis le direct, le processeur est arrêté mais le binding reste
valide jusqu'au retour du JPEG. Le verrou natif commun sérialise la fin d'un appel
live non interruptible puis l'analyse photo.

Le recadrage inverse le viewport vers les pixels de l'image décodée, exclut les
bandes vides et borne les coordonnées par floor/ceil. Le travail de copie est hors
UI, avec possession explicite des bitmaps ; une copie annulée avant publication est
libérée, sans recycler une image encore utilisée par le moteur. Original et zone
courante sont les seules sources conservées au repos. Les résultats de l'original
sont restaurés sans inférence, avec zoom Fit et sélection réinitialisés. L'état de
travail et l'original survivent à une recréation d'Activity via le ViewModel.

Reds exécutés avant implémentation : un échec de routage live, deux assertions de
géométrie/pixels sur quatre tests, trois assertions de session ROI. Les Greens
successifs sont conservés sous `app/`, avec un dernier ciblage initial de 92 tests
réussis, formatage et detekt conformes. La revue indépendante du code est conforme.

Quatre anciennes pages sans route produit et trois classes de tests devenues
obsolètes sont supprimées. Les contrats utiles (picker, URI, erreur, remplacement,
chargement tardif et dessin) sont migrés vers le parcours actuel ; ce n'est pas une
suppression destinée aux métriques. L'archive byte à byte et la correspondance des
tests sont dans [legacy-supersession.json](../../build/reports/validation/m1-photo-refinements/app/legacy-supersession.json).

### Compléments de preuve

La première campagne globale passe 181 tests Android mais refuse le seuil de
branches nouvelles à 78,38 %. Les résultats sont archivés dans `attempt1/`.
Des tests supplémentaires couvrent le direct en échec puis sans objet, les appels
ROI sans source/viewport ou déjà occupés, et le cadrage où seule la hauteur change.
Aucun code de production ni seuil n'est modifié pour ce complément.

Le contrôle visuel découvre aussi une incohérence de fixture : son masque réseau
restait fixe alors que ses contours dépendaient du bitmap recadré. Le testeur
confirme que la projection réelle n'est pas en cause. La fixture produit désormais
masque et contour depuis les mêmes logits/projection ; les assertions portent sur
le remplissage intérieur et l'absence de débordement, en portrait et paysage.
La tolérance 2/255 concerne le filtrage des pixels, pas une tolérance du modèle.

Le [ciblage corrigé](../../build/reports/validation/m1-photo-refinements/app/coverage-complements-corrected.log)
passe treize tests. Les nouveaux rendus
[portrait](../../build/reports/validation/m1-photo-refinements/app/screenshots/roi-portrait.png)
et [paysage](../../build/reports/validation/m1-photo-refinements/app/screenshots/roi-landscape.png)
sont inspectés par le codeur, l'orchestrateur et le testeur ; ils montrent une fixture
synthétique sur PC, aucune photo ou capture du téléphone. La revue de ces tests est conforme.

## Catalogue français

Le catalogue statique contient 4 585 noms alignés sur l'anglais canonique inchangé
(SHA `d8973978ecc1d94436964d44124e9893276d94a317de2868c440cb5ea2e2af35`).
Fichier `app/src/main/assets/photo/labels-fr.json`, **86 998 octets**, SHA-256
`4bc2c70a7ebbbba02c144df8d9d214e4535fb639f06a06a427183bfa710d4704`.
Sa provenance et les notices sont embarquées ; les poids et SDK de traduction ne le sont pas.

Préparation sur CPU après téléchargement public du modèle
[Helsinki-NLP/opus-mt-tc-big-en-fr](https://huggingface.co/Helsinki-NLP/opus-mt-tc-big-en-fr),
révision `6e062862ced6f5622a589ab2aadc2a1d4978db78`, licence CC BY 4.0 avec attribution.
Le premier modèle plus petit et l'essai de préfixe de phrase ont été écartés après
inspection de termes difficiles. Les sorties brutes sont conservées localement.
Ce travail de traduction est distinct de l'évaluation des modèles de vision.

Les paires ont été lues, dont 1 076 par le reviewer indépendant, avec 877 corrections
explicites et 558 noms inchangés justifiés (cognats français, emprunts, noms propres
ou scientifiques). La revue finale vérifie les positions, le SHA, les propositions
fusionnées et les noms conservés. Trois libellés sources opaques restent volontairement
inchangés : `power see`, `jackcrab`, `bonasa umbellu`. Aucune précision sémantique n'est
inventée pour les résoudre. Ce travail ne constitue pas une certification linguistique.

Le chargeur français vérifie schéma, langue, taille, SHA, source anglaise, nombre et
types avant publication en mémoire. Le résolveur utilisé par l'affichage n'effectue
aucune entrée/sortie ; une ressource française invalide fait échouer l'analyse au lieu
de masquer son absence par un retour silencieux à l'anglais.

### Outillage de préparation

L'environnement privé `.tools/m1-labels-fr/` reste séparé du socle PC et du précédent
environnement ML, lu sans modification. Les tests du noyau et les opérations de
génération/export sont documentés dans `experiments/m1-labels-fr/README.md`.
La couverture unitaire annoncée concerne le noyau déterministe, pas l'inférence MT.

Le scan privé a identifié des avis sur pip et transformers. Pip a été corrigé dans
ce seul environnement. Onze avis transformers restants, représentant six problèmes
avec alias, sont triés selon les API réellement utilisées : Marian explicite,
safetensors et manifeste vérifiés, exécution CPU hors ligne, pas de code distant,
Trainer ou sauvegarde de modèle. Le rapport ne prétend pas que cet environnement
est exempt d'avis et ne présente pas ces paquets comme des dépendances Android.

Le noyau déterministe est mesuré à 91,18 % de lignes et 86,36 % de branches, sans
exclusion ; dix tests d'outillage passent après le dernier correctif de recette.
La CLI de génération refuse désormais les réglages autres que batch 32 / quatre
threads, avant tout chargement ML : Red de cinq sous-cas puis Green. Cela conserve
la concordance avec le checkpoint et la provenance du run réellement effectué ;
aucun catalogue n'est régénéré pour ce correctif. La réserve de revue est levée.

## Intégration modèle et diagnostic

Le Green final ciblé modèle exécute douze tests : vraie factory française sur deux
fixtures, 36 détections et masques binaires identiques aux références, noms anglais
scientifiques conservés et huit traductions connues contrôlées. Le modèle minimal
du direct passe aussi son test natif. Le test de falsification de la ressource
française est exécuté et réussi.
[Journal](../../build/reports/validation/m1-photo-refinements/model/green-factory-french.log).

Le lecteur PC accepte les seules paires de versions `0.6.0/10`, `0.7.0/11` et
`0.8.0/12`, avec schéma, protocole et autres contrôles inchangés. Vingt tests passent.
Le CLI conserve exactement l'analyse de la capture historique 0.6 et d'une copie
PC dont seuls les champs de version deviennent 0.8/12. Cette copie ne constitue
pas une nouvelle exécution du diagnostic sur téléphone.

## Qualité finale et validation indépendante

Commande globale réellement exécutée :

```powershell
.\scripts\quality.ps1 -PythonExe C:\dev\blamera\pc\.venv\Scripts\python.exe -BaseRef 3b031de2454034d1073b8f0b3c0b144f8d659aed
```

Sortie **0**, douze étapes réussies ; **185 tests Android JVM, 76 tests scripts et
4 tests PC**, sans échec ni test Android ignoré. Le premier refus du gate et les
compléments de tests sont conservés ; aucun seuil ou exclusion n'a été abaissé.

| Périmètre | Lignes globales | Branches globales | Lignes nouvelles | Branches nouvelles |
| --- | ---: | ---: | ---: | ---: |
| Mobile | 96,22 % | 86,49 % | 96,39 % | 83,78 % |
| Scripts Python | 94,35 % | 88,52 % | 100 % | N/A : aucune branche nouvelle mesurable |
| Socle PC inchangé | 100 % | 100 % | N/A | N/A |

305 lignes exécutables mobiles nouvelles ; duplication globale 0,285 %, nouvelle
1/436 = 0,229 %. Duplication scripts/PC : 0 %. Complexités cyclomatique ≤ 10 et
cognitive ≤ 15, aucune violation. Licences : 131 entrées Maven et 35 Python acceptées ;
scans frais de 97 packages Maven et 35 PyPI, aucun avis actif retenu sur ces périmètres.
L'environnement privé de préparation MT et son triage restent distincts, comme décrit plus haut.

Lint : zéro erreur et trente-cinq avertissements (versions, API, ABI ChromeOS,
ressources inutilisées). La dette non bloquante **D-M1-03** suit les dix-neuf ressources
de l'ancienne interface à nettoyer ; elles ne sont pas utilisées dans le parcours actuel.
Les règles restent actives et la livraison validée n'est pas retardée pour ces textes sans effet.

[Journal final](../../build/reports/validation/m1-photo-refinements/quality-confirmed.log),
[synthèse archivée](../../build/reports/validation/m1-photo-refinements/final-quality/quality/summary.json)
et [JaCoCo archivé](../../build/reports/validation/m1-photo-refinements/final-quality/jacoco/html/index.html).
Les rapports sont archivés avant les exécutions ciblées du testeur. CI distante toujours reportée.

Le testeur indépendant conclut **CONFORME_PC_ONLY** : 41 tests ciblés dans treize
classes, vingt tests du lecteur et dix de l'outillage catalogue réussis. Les 204 empreintes
de sources, tests et configuration et le hash de l'APK restent inchangés.
[Verdict et preuves](../../build/reports/validation/m1-photo-refinements/tester/tester-results.json).

## APK et installation

Copie principale préparée : `dist/blamera-0.8.0-debug.apk`, **457 521 459 octets**,
SHA-256 `f76353d56f1716b4e1393b70e246619b545012921d0c99bff7617e6defb491e8`.
Package `dev.blamera`, version 0.8.0/code 12, SDK 36, arm64-v8a, signature v2 et
certificat personnel habituel vérifiés indépendamment. Permissions : CAMERA et
permission interne AndroidX seulement. Les vingt-deux assets diagnostic sont intacts,
Large présent une fois, trois nouveaux fichiers français présents une seule fois,
aucun poids ni SDK de traduction embarqué et aucune permission réseau.
[Contrôle du package](../../build/reports/validation/m1-photo-refinements/tester/package.json).

Les premiers préflights se sont arrêtés sans installation, faute d'appareil connecté.
Après reconnexion confirmée par l'utilisateur, le Xiaomi attendu est détecté seul et
la taille et le SHA de l'APK sont revérifiés. Le 29 octobre 2026, **mise à jour réussie** :
`adb -d install -r`, sortie 0, « Performing Streamed Install / Success », avec conservation
des données. [Preuve d'installation](../../build/reports/validation/m1-photo-refinements/install-result.json).
Le parcours essentiel de la [recette 0.8.0](../tests/m1-photo-refinements-recette.md)
est accepté par le retour humain ci-dessus ; les variantes complémentaires restent ouvertes.
Aucun résultat physique n'est déduit des contrôles PC. Aucun lancement, lecture de logs,
pilotage, capture d'écran ou changement de réglage n'est effectué par l'agent sur le téléphone.

## Téléphone personnel

Seuls installation/mise à jour de l'APK principal et logs Blamera sont autorisés.
L'utilisateur lance et manipule l'application. Aucune opération sur le téléphone
n'est requise pour préparer les traductions ou les tests PC.
