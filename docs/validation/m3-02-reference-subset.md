# M3-02-B-01 — sous-ensemble de références et préparation des lots

1er novembre 2026, baseline `beb1e75`, cible PC 0.5.1.
PC 0.5.1 disponible après contrôles, revue et validation indépendants conformes.
Trente cas sont préparés ; les cibles restent à confirmer par l'utilisateur.
L'utilisateur demande cinq références datées, sans modifier sa galerie.
[Contrat](../specs/evaluation-reference-subset.md).

Le serveur utilisateur est arrêté avant modification des sources (worker 39756,
lanceur 42912, identité et démarrage vérifiés). Les données de la galerie restent
conservées ; la sélection et les annotations de séance appartiennent à l'évaluation.

## Comportement et TDD

Sélection persistée dans les settings de la seule base d'évaluation. Absence de
réglage / `None` conserve toutes les références compatibles confirmées ; une liste
explicite, y compris vide, ne revient jamais implicitement à toutes. Prototypes
et séances exigées sont limités aux retenues. Les références devenues indisponibles
bloquent le rapport ; l'interface permet de corriger le choix ou de revenir à toutes.
Toutes les photos de la galerie, même exclues, restent interdites comme doublons.
La politique de sélection fait partie de l'empreinte et invalide l'ancien rapport.

Preuves Red / Green réellement exécutées dans `coder/` : stockage et filtrage,
défaut rétrocompatible, liste vide / invalide / référence disparue, persistance,
invalidation, doublon avec une référence exclue et parcours UI. Les tests mesurent
aussi l'identité binaire de la galerie avant / après. Les erreurs d'infrastructure
de cache / répertoire courant mypy sont distinguées des Red fonctionnels.

## Qualité sur le gel

`pc/.venv/Scripts/python.exe scripts/quality_pc.py --base-ref beb1e75`, sortie 0 :
107 tests en 20,686 s, format / Ruff / mypy strict, complexités, couverture,
duplication, environnement verrouillé et audit conformes. Rapport `coder/report.md`,
archives `coder/quality-final/` et 46 empreintes `coder/source-snapshot.json`.

| Périmètre PC | Lignes | Branches |
| --- | --- | --- |
| Canonique global | 1393/1397, 99,71 % | 336/342, 98,25 % |
| Nouveau code | 71/71, 100 % | 22/22, 100 % |

Duplication nouvelle 0/113 lignes physiques, cognitive maximale 13≤15,
cyclomatique Ruff C901≤10. Pas d'exclusion ou seuil ajouté / assoupli.
36 paquets runtime et licences, aucun avis actif renvoyé par le scan dans ce
périmètre ; versions d'exécution et modèle inchangés. Android / scripts inchangés
ne sont pas relancés dans ce lot PC.

## Revue et validation indépendante

Reviewer : conforme sur le code, le plan et son applicateur ; aucun blocage établi,
46 empreintes identiques et preuves TDD / métriques cohérentes. Le plan ne produit
aucune sélection de cible ni score de reconnaissance pour choisir les photos.

Amélioration acceptée `M3-02-B-CFG-01` : un JSON de sélection corrompu bloque la
construction du snapshot avant d'atteindre sa réinitialisation UI. Récupération
par API possible, données conservées ; ce cas extérieur au parcours normal ne
bloque pas ce lot. Ajouter un accès à la réparation dans une prochaine évolution
de configuration ; les setters actuels refusent les entrées invalides.

Testeur : 107 tests indépendants, sortie 0 en 14,110 s, puis cinq vérifications
complémentaires en 0,885 s. Elles couvrent notamment publication réelle `os.replace`
et sauvegarde sur bases SQLite synthétiques, conservation binaire des sources /
galerie, refus de sources ou base changées après staging et échec du remplacement
conservant original et sauvegarde. Aucune application au corpus personnel par le
testeur, aucune archive de couverture du coder écrasée. Rapport `tester/report.md`.

## Interface sur données publiques

QA isolée : deux références confirmées à partir du portrait public NASA, dont une
sans séance. En retenir une permet le rapport, sans supprimer l'autre ; la galerie
est binaire identique après configuration et calcul de seed. Dans le vrai navigateur,
compteur 1/2, sélection vide 0/2 et calcul refusé, restauration explicite 2/2,
puis retour à 1/2 et recalcul. La référence exclue sans séance ne bloque pas ce
dernier. Capture `browser-subset.png`, images / labels publics ou fictifs seulement.
Ce scénario vérifie le logiciel, pas la reconnaissance de proches.

## Corpus personnel et publication

Plan privé normalisé de 30 photos : 15 Calibration et 15 Test final réservé.
Chaque lot contient cinq images de chacun des deux connus et cinq inconnues,
avec des personnes inconnues distinctes. Les jours EXIF sont séparés entre les
cinq références, Calibration et Test final. Tous les fichiers `models` sont exclus
du corpus, et les doublons sont contrôlés contre les sept références principales.
Les fichiers personnels, dates et identités restent sous `pc/data/import-plans/`,
ignorés par Git ; seules les synthèses anonymisées figurent dans les preuves publiques.

L'applicateur temporaire a été relu et testé sur données synthétiques avant la
commande personnelle. Il vérifie le digest du plan, les sources, les frontières
de chemins et l'absence de modification concurrente ; clone la base d'évaluation,
prépare les seules détections puis sauvegarde la base précédente avant remplacement
du seul fichier d'évaluation. La galerie principale est ouverte en lecture seule,
sans constructeur `GalleryStore` sur celle-ci.

La première application s'arrête avant publication : la base d'évaluation n'avait
jamais été créée, son dossier parent manquait à `os.replace`. Galerie binaire
inchangée, aucun fichier d'évaluation publié. Deux régressions synthétiques sont
exécutées avant correction : première publication sans dossier (FileNotFoundError),
puis reçu annonçant une sauvegarde qui n'existe pas. Le correctif crée seulement
le dossier borné, revérifie la destination et utilise `evaluation_backup=null`
pour une base initialement absente. Revue du delta conforme, six tests de l'outil
et six vérifications indépendantes passent, sans relancer les 107 tests produit
dont les 46 sources restent inchangées. Preuves `data/publication-fix-report.md`
et addendum du testeur. Ces outils ponctuels restent hors code de production.

La seconde application du même plan validé réussit. Le reçu privé et la synthèse
`data/application-summary.json` attestent : galerie exacte au SHA du plan, sept
photos et sept confirmations conservées, cinq références retenues, trente cas
publiés dans la seule base d'évaluation. Les fichiers originaux concernés sont
inchangés. À la publication, zéro cible choisie et zéro embedding d'évaluation.
Calibration : trois images à un visage, douze à plusieurs. Test réservé : sept
à un visage, sept à plusieurs et une sans visage. Les jours des trois ensembles
sont disjoints ; aucun résultat d'identité n'est produit sur le test.

Le guide privé `pc/data/import-plans/evaluation-targets-a-confirmer.md` indique
l'ordre dans chaque lot et le sujet du dossier à viser. L'ancienne demande de
dates est marquée historique : l'utilisateur a choisi de continuer avec cinq
références, ces deux dates ne sont plus requises pour cette variante.

## Déploiement et validation utilisateur

Le serveur QA public est arrêté après vérification des identités, reçu
`server-qa-stopped.json`. Le serveur utilisateur PC 0.5.1 reprend la racine
`pc/data` : lanceur 22808, worker écoutant 46916, démarrage le 1er novembre 2026
à 19:36:45 UTC, http://127.0.0.1:8501. Reçu `server-user.json` avec identité et
santé HTTP200 `ok`. L'agent ne parcourt pas le navigateur de la galerie personnelle.

Prochaine validation : retrouver le compteur 5/7, choisir et enregistrer les cibles
des quinze photos de Calibration, puis lancer l'évaluation. Les photos de groupe
exigent de viser le sujet attendu, précisé dans le guide privé pour les inconnus.
Si le sujet manque parmi les candidats, demander une correction du parcours plutôt
que choisir un autre visage pour pouvoir continuer. La recette reste ouverte.

Aucun résultat de reconnaissance personnel annoncé : les cibles sont à confirmer
humainement, y compris avec une seule détection ; le Test final réservé ne produit
pas de score d'identité. M3-02 reste ouvert pour la calibration et le test final.
