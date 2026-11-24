# Validation M3-01 — galerie des proches sur PC

1er novembre 2026. Baseline `65bbab0d0bbb7e373239c164139ff50eede16eb7`.
Livré sur PC en 0.2.0 : revue, contrôles PC et testeur conformes ; import réel
dans le navigateur vérifié avec une fixture synthétique.

[Contrat](../specs/people-gallery.md), [recette](../tests/m3-people-gallery-recette.md),
[clôture explicite M1/M2](../decisions/0013-m1-m2-closure.md).

Premier lot fonctionnel PC 0.2.0 : personnes et images locales,
SQLite transactionnel, imports normalisés et interface Streamlit. Les images
utilisées dans les tests sont synthétiques et temporaires. Aucun accès aux
photos personnelles, compte externe ou téléphone dans ce lot.

## Preuves TDD et contrôles PC

Artifacts dans `build/reports/validation/m3-01-people-gallery/coder/`.
Les premiers lots Red de stockage et d'interface précèdent leurs implémentations.
Les journaux distinguent indisponibilités fonctionnelles attendues, erreurs de
fixture (référence sqlite3 absente, connexion de test non fermée), restrictions
Temp du sandbox et attente incorrecte du type d'élément AppTest. Ces erreurs de
fixture/infrastructure ne sont pas comptées comme Red fonctionnels.

Les deux assertions du lot d'import, et le cas d'initialisation atomique de
schéma, échouent pour les raisons attendues puis passent après correction.
L'initialisation SQLite utilise un BEGIN explicite pour que son DDL soit annulé
en cas de panne ; une base partiellement créée n'est pas laissée inutilisable.
Rapport détaillé : `coder/report.md` et journaux Red/Green conservés.

`pc/.venv/Scripts/python.exe scripts/quality_pc.py --base-ref
65bbab0d0bbb7e373239c164139ff50eede16eb7` : sortie 0, synthèse passed, 26 tests
PC réussis. Ruff format/lint, mypy strict, couvertures globales/nouvelles,
complexités, duplication, lock, licences et audit runtime conformes.

- Couverture canonique : 264/264 instructions et 52/52 branches, 100 % global et
  nouveau code. Aucun code métier exclu ; le chiffre 265 annoncé initialement
  par le coder est rectifié d'après le JSON final vérifié indépendamment.
- Six sources Python, 398 lignes analysables ; duplication nouvelle 0/333 lignes,
  globale nulle. Cyclomatique ≤10, cognitive maximale PC 6, plafond 15 conservé.
- 35 paquets runtime et 35 entrées de licences ; aucun avis actif retourné dans
  ce scope au scan. Pillow 13.0.0 était déjà présent transitivement et devient
  direct ; les dépendances hors projet du lock restent inchangées.
- Package namespace Python, racine mypy explicite et strict conservé. Un
  __init__.py vide n'a pas été conservé : aucune logique exclue ni remplissage
  ajouté pour satisfaire l'analyseur de duplication.

Archives gelées : `quality-final/`, snapshot de 385 fichiers source/configuration,
plus les 13 empreintes du périmètre coder. Les contrôles Android et scripts
Python de qualité ne sont pas relancés pour ce changement PC : leurs sources
restent inchangées et leurs dernières preuves sont celles de 0.15.1. Le lanceur
PowerShell restaure son PYTHONPATH temporaire ; les serveurs/tests PC chargent la
même racine de modules. Aucun seuil ou exclusion de qualité n'est assoupli.

## Validation indépendante

Reviewer : conforme, sans blocage. Testeur : 26 tests PC, sortie 0 en 3,281 s,
puis cinq contrôles supplémentaires synthétiques (20 Mio, header >24 MP sans
décodage, métadonnées/dédoublonnage, panne SQL sans perte, racine de données).
Le premier AppTest sandbox a été interrompu avant assertion ; passage réussi
avec les droits nécessaires à Streamlit/Temp. Une connexion SQLite oubliée dans
la fixture du testeur a été fermée, sans modification de production.

Couverture, lock, 385 empreintes et liens recalculés. Aucun téléphone, API externe
ou photo réelle utilisé par le testeur. Rapport `tester/report.md`.

## Navigateur réel et disponibilité

Root a lancé un serveur QA loopback sur une base isolée `browser-data/` dans les
artefacts ignorés, sans utiliser la galerie personnelle. Création d'une identité
par le navigateur, puis premier aperçu préparé par l'API locale avec le PNG
synthétique `browser-fixtures/cadre-demo.png` (240×160).

Ensuite le vrai sélecteur navigateur a reçu ce fichier. Le premier aperçu ayant
été réaffecté par l'API à une autre identité de test, le bouton Importer a bien
effectué un nouvel ajout : « cadre-demo.png — Ajoutée », compteur 1, aperçu et
dimensions visibles, sélecteur réinitialisé. Aller-retour Chats/Personnes et
changement de personne : données conservées, pas de réimport. Cette séquence
réelle est distincte du mock du seul widget file_uploader dans AppTest.

Preuves : `browser-check.json`, `browser-import-context.png` et captures voisines.
Le serveur QA a ensuite été arrêté en vérifiant l'identité de son seul processus.
Le serveur utilisateur est disponible sur `http://127.0.0.1:8501`, avec la racine
normale `pc/data`, sans les données synthétiques. L'onglet est ouvert pour l'essai.
Reçu de lancement `server-user.json` ; relance habituelle via `scripts/pc.ps1 Run`.

Retour utilisateur après la recette proposée : « ok ça a l'air de marcher ».
Le parcours essentiel est accepté qualitativement. Aucun accès aux photos ou à
la base personnelle n'a été effectué pour consigner ce retour ; les variantes
optionnelles et volumes importants ne sont pas déclarés individuellement testés.
Ce lot gère des références ; aucun visage, embedding, modèle ML, reconnaissance
ou export n'est livré ici. Les galeries volumineuses pourront nécessiter pagination
et prévisualisations dédiées : optimisation à décider sur volume réel.
