# M1-07 / M1-08 — capture et exploration d'une image

28 octobre 2026 — **code, revue et qualité locale conformes**.
29 octobre : parcours essentiel accepté par les retours humains sur 0.7.0 puis 0.8.0 ;
variantes complémentaires, prolongé, hors ligne et budgets suivis en M1-09.
Version visée : **0.7.0/code 11**. Baseline du nouveau code :
`eee1a3c48cfef308e181301680e19abf6e538b6e`.

## Contrat

Entrée caméra par défaut ; capture et galerie avec texte et icône ; analyse locale
automatique sur une image stable commune ; balayage et message dans le panneau bas ;
zoom/déplacement ; sélection du masque semi-transparent, désélection au second appui,
couleurs stables par catégorie et nom dans le panneau. Le direct reste secondaire.

Références : [spécification](../specs/capture-analysis.md),
[décision](../decisions/0006-capture-analysis.md),
[recette manuelle](../tests/m1-capture-analysis-recette.md).

YOLOE26 Large PF est intégré pour essayer le parcours photo : graphe compact
126 179 359 octets, SHA-256
`2e94f433084b40fb7d558355af7e50b4ff8a483c875a964e0d308a6984d779b8`.
Son vocabulaire comporte 4 585 libellés ; cela ne garantit pas leur reconnaissance.
Entrée FP32 640², score 0,35, NMS agnostique IoU 0,5, plafond 20 sorties inchangés.
Les [preuves précédentes sur Xiaomi](../research/m1-yoloe-capacity-phone-results.md)
ne valident pas encore le nouveau parcours complet.

## Organisation et preuves à compléter

Deux agents de code interviennent sur des périmètres distincts : capture/interface et
adaptateur de modèle. La revue indépendante intervient sur leur état stable, puis
le testeur valide l'ensemble. Les changements de documentation précédant ce ticket
sont conservés ; aucun push ni CI distante n'est prévu.

Les journaux réellement exécutés sont conservés sous
`build/reports/validation/m1-capture-analysis/`, avec sous-dossiers `app/` et `model/`.
Le premier Red UI compile et échoue sur l'absence de « Capturer » :
[journal](../../build/reports/validation/m1-capture-analysis/app/red-entry-functional.log).
Les preuves de qualité globale et de validation finale seront ajoutées à leur réalisation.

### Parcours et revue

Le [Green UI](../../build/reports/validation/m1-capture-analysis/app/green-ui-validated.log)
réussit sur 56 tests, avec formatage et detekt. Les régressions couvrent notamment
l'analyse automatique, le refus de caméra avec galerie disponible, le toucher du masque
et le pincement sans nouvelle inférence, la capture JPEG et sa fermeture après annulation,
ainsi qu'une vraie recréation d'Activity conservant photo, résultats et sélection.

Deux remarques de revue sont retenues : les chemins des contours étaient reconstruits
pendant chaque geste, et un import raté pendant la première analyse ne proposait pas
de réessai. Les [deux Reds de régression](../../build/reports/validation/m1-capture-analysis/app/red-review-regressions.log)
échouent fonctionnellement avant correction. Le
[Green final ciblé](../../build/reports/validation/m1-capture-analysis/app/green-review-regressions.log)
réussit sur douze tests, avec formatage et detekt : chemins source mémorisés par résultat,
dessin par transformation du canvas, réessai et rejet de l'ancien résultat natif tardif.
La revue indépendante des corrections est conforme, sans blocage restant.

Les rendus PC [portrait](../../build/reports/validation/m1-capture-analysis/app/screenshots/portrait.png)
et [paysage](../../build/reports/validation/m1-capture-analysis/app/screenshots/landscape.png)
montrent une fixture synthétique avec masque semi-transparent, contour, nom et boutons.
Ils ont été inspectés ; ce sont des preuves de présentation simulée, pas des captures
du téléphone ni une mesure de fluidité tactile réelle.

### Intégration du modèle sur PC

Le [Green froid](../../build/reports/validation/m1-capture-analysis/model/green-photo-cold.log)
réussit : dix tests, cache initialement absent. La véritable usine du parcours photo et
son résolveur de noms reproduisent 36 détections sur NASA et la scène dense LVIS 371699
(17 et 19). Catégories et noms sont exacts, les 36 masques binaires 160² sont identiques
bit à bit aux références Kotlin ; scores et boîtes respectent les tolérances du test
(0,001 et 0,01 pixel). Les contours sont non vides et bornés dans l'image source ;
leur identité géométrique exacte n'est pas revendiquée.
[Synthèse, périmètre et limites](../../build/reports/validation/m1-capture-analysis/model/native-result.json).

Le premier échec natif a été isolé : ORT Windows refuse un chemin de 268 caractères
avec `ORT_NO_SUCHFILE`, alors que Java voit le même fichier et vérifie son empreinte ;
un chemin de 60 caractères fonctionne. La
[contrepreuve autonome](../../build/reports/validation/m1-capture-analysis/model/path-probe/result.log)
ne modifie ni Windows ni le téléphone. Le test froid final utilise un chemin de test
plus court, sans préchauffage cachant un défaut de premier chargement.

La revue indépendante du périmètre modèle est conforme, sans blocage établi. La
validation complète UI, les gates et la recette téléphone restent à réaliser.
Le cache commun calcule désormais le SHA pendant la copie sur un cache absent :
cela économise une relecture de fichier par rapport à l'ancien diagnostic. Les
graphes et paramètres du diagnostic restent identiques ; les anciennes mesures
de lecture/chargement ne sont pas réécrites.

### Compatibilité du diagnostic conservé

La première campagne globale exécute 169 tests Android et relève quatre échecs :
trois lectures de tests supposaient que tous les fichiers générés résidaient dans
le répertoire debug, et un test attendait encore le code de version 10. Les tests
utilisent désormais l'AssetManager réellement fusionné et vérifient la version
courante. Les cinq tests ciblés passent, avec les quatorze SHA de pixels et de
tenseurs vérifiés et le pipeline Large à froid. Les preuves initiales sont conservées
dans `attempt1/` ; [Green de compatibilité](../../build/reports/validation/m1-capture-analysis/model/bench-compat-green.log).

Le testeur indépendant a aussi reproduit un défaut du lecteur PC, qui rejetait le
code 11. Le lecteur accepte maintenant seulement les paires `0.6.0/10` et `0.7.0/11`,
avec protocole, schéma, modèles et références inchangés. Après le Red exécuté,
vingt tests ciblés et Ruff passent. La capture historique est inchangée ; une copie
PC ne modifiant que les deux champs de version produit la même analyse :
56 références et 437 détections appariées.
[Preuve de compatibilité](../../build/reports/validation/m1-capture-analysis/python-compat/historical-and-derived-proof.json).
Cette copie de rapport ne constitue pas une nouvelle exécution sur le téléphone.

Le scan de duplication a signalé des blocs d'imports devenus inutilisés après
extraction. Leur suppression ramène le nouveau code à 29/1 440 lignes dupliquées,
soit 2,01 %, sans changer le seuil, la configuration ou les exclusions. La campagne
globale finale réussit sur l'ensemble corrigé.

## Qualité finale

Commande réellement exécutée :

```powershell
.\scripts\quality.ps1 -PythonExe C:\dev\blamera\pc\.venv\Scripts\python.exe -BaseRef eee1a3c48cfef308e181301680e19abf6e538b6e
```

Sortie **0**, douze étapes réussies ; **175 tests Android JVM, 76 tests scripts et
4 tests PC**, sans échec ni test Android ignoré. Après un arrêt effectif du gate à
78,35 % de branches nouvelles, six cas d'acceptation supplémentaires couvrent les
chevauchements, pixels de remplissage, limites tactiles, remplacement en chargement
et cycle de vie caméra. Aucun code de production, seuil ou exclusion n'a changé
pour ce complément de tests ; aucun Red artificiel n'est revendiqué.

| Périmètre | Lignes globales | Branches globales | Lignes nouvelles | Branches nouvelles |
| --- | ---: | ---: | ---: | ---: |
| Mobile | 94,51 % | 85,98 % | 93,76 % | 84,00 % |
| Scripts Python | 94,35 % | 88,52 % | 100 % | N/A : aucune branche nouvelle mesurable |
| Socle PC inchangé | 100 % | 100 % | N/A | N/A |

849 lignes exécutables mobiles nouvelles ; duplication mobile globale 0,49 %, nouvelle
29/1 440 = 2,01 %. Scripts : duplication globale et nouvelle 0 % (huit lignes nouvelles
analysables). Complexités cyclomatique ≤ 10 et cognitive ≤ 15 : aucune violation.
Licences : 131 entrées Maven et 35 Python acceptées ; scans frais de 97 packages Maven
et 35 PyPI, aucun avis actif retenu. Lint : zéro erreur et dix-neuf avertissements
(versions, API, ABI ChromeOS, ressources inutilisées et suggestion KTX), sans règle désactivée.

[Journal final](../../build/reports/validation/m1-capture-analysis/quality-confirmed.log),
[synthèse archivée](../../build/reports/validation/m1-capture-analysis/final-quality/quality/summary.json)
et [JaCoCo archivé](../../build/reports/validation/m1-capture-analysis/final-quality/jacoco/html/index.html).
Les deux campagnes précédentes et leurs échecs restent conservés dans `attempt1/`
et `attempt2/`. La CI distante reste volontairement reportée.

## Livraison et appareil

Clé personnelle existante vérifiée avant fabrication, sans export ni régénération.
L'APK principal préparé est `dist/blamera-0.7.0-debug.apk`, **457 888 580 octets**,
SHA-256 `73d011647e4e67633a45f3d19bd2978a904188661f9bab45f2b8062e1f35ed49`.
Le package `dev.blamera`, SDK 36, arm64-v8a, signature v2 et certificat habituel sont
vérifiés indépendamment. Permissions : CAMERA et permission interne AndroidX seulement.
Les vingt-deux assets du diagnostic, les libellés et notices sont vérifiés ; Large
figure une seule fois dans le ZIP. Le hash reste identique après la campagne finale.
[Preuve du package](../../build/reports/validation/m1-capture-analysis/tester/package.json).

Le seul APK principal a été mis à jour avec conservation des données, après contrôle
de sa taille et de son SHA. Aucun lancement, pilotage, test APK ou instrumentation
sur le téléphone. L'utilisateur y réalise la recette lui-même.

Le testeur indépendant conclut **CONFORME_PC_ONLY** : 29 tests ciblés dans onze classes,
zéro échec/erreur/ignoré ; les 152 empreintes de sources, tests et configuration sont
inchangées. L'APK conserve l'empreinte vérifiée. Les rapports globaux archivés sont
contrôlés indépendamment, ainsi que la compatibilité de l'ancienne capture du diagnostic.
[Verdict et critères](../../build/reports/validation/m1-capture-analysis/tester/tester-results.json).

Un premier préflight s'est arrêté faute d'appareil, sans tentative d'installation.
Après reconnexion confirmée par l'utilisateur, le Xiaomi attendu est à nouveau détecté
seul. Le 28 octobre 2026, **mise à jour réussie** : `adb -d install -r`, sortie 0,
« Performing Streamed Install / Success ».
[Preuve d'installation](../../build/reports/validation/m1-capture-analysis/install-result.json).
L'utilisateur indique sur 0.7.0 « le reste a l'air de fonctionner », puis « Tout fonctionne »
sur les adaptations 0.8.0. Ces retours acceptent le parcours essentiel et permettent
de clore M1-07/M1-08 avec les preuves PC ; ils ne valident pas chaque cas physique
individuellement ni les budgets. [Portée précise du retour 0.8.0](../tests/m1-photo-refinements-recette.md).
Aucun lancement, log, pilotage, capture d'écran
ou changement de réglage n'est effectué par l'agent sur le téléphone.
