# Catalogue français de YOLOE

Ce dossier prépare les noms affichés de **toutes les 4585 catégories**, sans modifier
leurs IDs, leur ordre anglais ni les poids de segmentation. Seuls `labels-fr.json`,
sa provenance et sa notice sont embarqués. Le téléphone ne reçoit aucun moteur de
traduction, SDK, accès réseau ou abonnement supplémentaire.

## Données et choix

Source canonique : `models/m1-yoloe-phone-assets/labels4585.json`, 64833 octets,
SHA-256 `d8973978ecc1d94436964d44124e9893276d94a317de2868c440cb5ea2e2af35`.
Le contrat Android est un objet `schema: 1`, `locale: fr`, `englishSha256` et
`labels: [4585 chaînes]`. Une catégorie anglaise conserve toujours sa position,
même lorsque plusieurs synonymes partagent un nom français.

Les traductions initiales viennent de
[Helsinki-NLP/opus-mt-tc-big-en-fr](https://huggingface.co/Helsinki-NLP/opus-mt-tc-big-en-fr/tree/6e062862ced6f5622a589ab2aadc2a1d4978db78),
révision `6e062862ced6f5622a589ab2aadc2a1d4978db78`, licence **CC BY 4.0**.
La notice séparée `app/src/main/assets/photo/NOTICE-labels-fr.txt` attribue OPUS-MT
et signale les modifications ; la notice AGPL existante reste intacte.
Le manifeste `model-files.lock.json` épingle tailles et SHA de tous les fichiers
nécessaires, dont les poids safetensors (461429130 octets,
`5c88b4f7a63934b8be72372b86661e616227c20be4b75612eeee7dca96494217`).
Les noms scientifiques, marques et emprunts français peuvent conserver leur forme.

L'essai avec `opus-mt-en-fr` plus petit a été rejeté : erreurs nominales et longues
phrases hallucinées sur certains mots. Un essai Big sur 50 termes a comparé mots
isolés et contexte « A photo of … » ; les mots isolés étaient plus fiables.
Les preuves et sorties rejetées restent locales sous les répertoires ignorés.
Nous n'avons pas réalisé de nouvelle étude générale des traducteurs.

Les 4585 paires ont été lues : revue ciblée du coder et de l'orchestrateur, dont
1076 paires relues indépendamment (450–899, 2600–3199 et 1934–1959).
`overrides.json` contient 877 corrections explicites, y compris des noms COCO déjà
francisés dans le projet. Les noms polysémiques utilisent parfois `/` pour éviter
d'affirmer un sens fin que le texte anglais isolé ne donne pas. Ce travail ne
constitue pas une certification linguistique exhaustive ni une mesure de qualité
du modèle de vision.

`retained-names.json` justifie chaque nom inchangé (comparaison insensible à la
casse) et son ID exact. Trois sources opaques sont conservées sur décision de
l'orchestrateur : `power see`, `jackcrab`, `bonasa umbellu` (taxon tronqué).
Les noms botaniques régionaux `bayberry`, `buttonwood` et le nom zoologique
`grampus` sont conservés explicitement pour éviter d'inventer une espèce précise.
Suivi : clarifier ces noms à partir du vocabulaire source avant toute correction
taxonomique ; ne jamais les remplacer silencieusement par une classe COCO.

## Reproduction locale

Python 3.12.14 ; environnement **distinct** `.tools/m1-labels-fr/.venv`, installé
depuis `requirements.lock.txt`. Les versions et licences de ses 25 paquets (torch
compris) sont relevées dans `dependency-licenses.json`. Le torch CPU 2.14.1 déjà
installé est lu via un chemin ajouté **à la fin** de `sys.path`, sans modifier
`.tools/m1-model-spike/.venv`. Le script interdit la création de bytecode pendant
la préparation. pip a été corrigé de 25.0.1 vers 26.2.2 après la génération ; cela
ne change pas les dépendances d'inférence ni les sorties de traduction.

Depuis la racine, après création du nouveau venv et installation de son lock :

```powershell
.tools/m1-labels-fr/.venv/Scripts/python.exe -m pip install --only-binary=:all: -r experiments/m1-labels-fr/requirements.lock.txt
# Télécharger les fichiers publics du manifeste, à la révision épinglée,
# dans .tools/m1-labels-fr/model-big/ ; aucun token n'est nécessaire.
.tools/m1-labels-fr/.venv/Scripts/python.exe experiments/m1-labels-fr/translate_catalogue.py --variant big --threads 4 --batch-size 32 --beams 4
.tools/m1-labels-fr/.venv/Scripts/python.exe experiments/m1-labels-fr/export_catalogue.py
```

URL de chaque fichier :
`https://huggingface.co/Helsinki-NLP/opus-mt-tc-big-en-fr/resolve/6e062862ced6f5622a589ab2aadc2a1d4978db78/<file>`.
La génération vérifie le manifeste **avant** de charger modèle/tokenizer. Le
chargement est Marian explicite, `local_files_only=True`, `trust_remote_code=False`,
attention eager, safetensors uniquement pour Big. Les indicateurs offline sont
actifs ; aucune requête d'inférence ne part du PC. Aucune photo n'intervient.

Réglages : CPU4 threads intra/1 inter, seed0, `eval`, `inference_mode`, algorithmes
déterministes, beams4, sans échantillonnage, au plus 48 nouveaux tokens, batch32.
La recette refuse explicitement tout `--batch-size` différent de 32 ou `--threads`
différent de 4, avant le chargement du modèle. Ces réglages fixes garantissent la
cohérence avec le checkpoint historique et la provenance exportée ; ils ne sont
pas des paramètres de performance ajustables dans cet outil.
Un checkpoint atomique est écrit après chaque lot et vérifié avant reprise
(même hash anglais, révision et paramètres). Big a produit 4585/4585 noms en
676,9 secondes sur ce PC. Les sorties brutes, logs et checkpoints sont ignorés ;
les chaînes finales et décisions éditoriales sont suivies dans le dépôt.

L'export exige toutes les traductions, les overrides connus et une justification
exacte des inchangés. Il rejette blancs, contrôles, U+FFFD, placeholders et longueurs
anormales. Il ne déduit jamais les IDs depuis les traductions. Les SHA de données,
modèle, correction et environnement sont dans `labels-fr-provenance.json`.

## Vérifications et limites

Les validations de catalogue, reprise, caractères, conservation des positions,
justifications des inchangés et contrôle du modèle suivent un Red exécuté puis
Green, avec logs dans `build/reports/validation/m1-photo-refinements/labels-fr/`.
Le chargement ML et l'expérience de traduction sont une préparation statistique
bornée ; leur qualité lexicale se vérifie par revue, pas par assertions qui
recopieraient 4585 résultats attendus. Les CLI de préparation/export sont validées
par exécution réelle ; leur orchestration n'est pas annoncée couverte par les
tests unitaires du noyau.

```powershell
.tools/m1-labels-fr/.venv/Scripts/python.exe -m unittest discover -s experiments/m1-labels-fr -v
pc/.venv/Scripts/python.exe -m ruff check --config pc/pyproject.toml experiments/m1-labels-fr
pc/.venv/Scripts/python.exe -m ruff format --config pc/pyproject.toml --check experiments/m1-labels-fr
pc/.venv/Scripts/python.exe -m coverage run --branch --source=catalog_core --data-file=build/reports/validation/m1-photo-refinements/labels-fr/.coverage -m unittest discover -s experiments/m1-labels-fr -v
pc/.venv/Scripts/python.exe -m coverage json --data-file=build/reports/validation/m1-photo-refinements/labels-fr/.coverage -o build/reports/validation/m1-photo-refinements/labels-fr/coverage.json
pc/.venv/Scripts/complexipy.exe experiments/m1-labels-fr --max-complexity-allowed 15 --output build/reports/validation/m1-photo-refinements/labels-fr/complexity.json --output-format json --cache-dir .tools/m1-labels-fr/complexipy-cache --no-ignore --plain
node node_modules/jscpd/bin/jscpd experiments/m1-labels-fr --config experiments/m1-labels-fr/jscpd.json
```

Contrôle OSV ciblé des 25 paquets de préparation exécuté le 28/10/2026 : 23 avis
initiaux (alias inclus), puis 11 après correction de pip. Les 11 avis restants de
transformers sont détaillés avec leurs alias et les chemins non exercés dans
`dependency-triage.json`. Aucun de ces paquets n'est embarqué sur Android. Ce
triage est spécifique au chemin offline/Marian/fichiers vérifiés ; il ne déclare
pas cette ancienne version sûre pour charger des modèles arbitraires, entraîner
ou servir une API. Réévaluer avant toute réutilisation de ce venv hors de ce cas.

Les mesures des outils de préparation ne remplacent pas les tests du chargeur
Android, le build et les contrôles mobiles pilotés par l'orchestrateur.
