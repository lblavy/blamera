# Développer le socle PC

Le choix et ses limites sont dans l'[ADR 0002](decisions/0002-pc-foundation.md).
Préparation locale Windows x64, réseau public pour téléchargements/scans, Node 24.12.0
et Git pour la qualité. Aucun Python global, GPU, compte cloud ou runtime Codex nécessaire.
JDK/SDK Android restent nécessaires seulement pour la commande globale mobile + PC.

## Préparer depuis un terminal Windows ordinaire

Depuis la racine du dépôt :

```powershell
.\scripts\pc.ps1 Bootstrap
if ($LASTEXITCODE -ne 0) { throw 'Bootstrap en echec' }
.\pc\.venv\Scripts\python.exe -c 'import sys; print(sys.version); print(sys.executable); print(sys.base_prefix)'
.\scripts\pc.ps1 Quality
```

Le bootstrap vérifie l'archive officielle **uv 0.13.5** Windows avant extraction :
`a38b8d1902b7cfbc76b423ba1bb456f623f42064cdc72628b6075b89afd1019a`.
Python **3.12.14** est géré sous `.tools/python`, sans installation système,
registre ou modification permanente du PATH. Le binaire uv reste sous `.tools/uv/0.13.5`.
Les variables UV ne concernent que ce processus et ses enfants ; caches/outils sont ignorés.
Les métadonnées Python embarquées dans uv épinglé déterminent son archive officielle.
`pc/.python-version`, `pyproject.toml` et `uv.lock` fixent runtime et dépendances.
Une nouvelle version doit être réévaluée puis verrouillée intentionnellement ; ne pas
utiliser `--upgrade` pour faire disparaître un problème de qualité.

Le bootstrap est répétable et la synchronisation `--locked` refuse un lock obsolète.
Une erreur réseau/checksum laisse le bootstrap en échec ; ne pas exécuter une archive différente.
Le groupe `dev` inclut pip 26.2.2 pour l'installateur existant des outils qualité du socle.
Les dépendances d'exécution restent distinctes et sont exportées avec `--no-dev` pour leur audit.

## Ouvrir l'interface locale

```powershell
.\scripts\pc.ps1 Run
```

Ouvrir manuellement `http://127.0.0.1:8501`, puis arrêter le processus avec Ctrl+C.
Aucun navigateur ne s'ouvre automatiquement. Le lanceur conserve `pc/` comme répertoire
courant afin de charger sa configuration : adresse loopback, mode headless, télémétrie
désactivée, protections CORS/XSRF actives. Ne pas exposer ce serveur au réseau local.
La galerie locale M3-01 (PC 0.2.0) est enrichie depuis PC 0.3.0 par la préparation
des visages de référence M3-02-A. Le profil PC 0.4.0 utilise ArcFace ResNet50,
selon la préférence utilisateur pour la qualité. Chats reste prévu pour M4.
La décision d'identité calibrée et l'export vers Android restent à venir.

Pour modifier les sources ou un profil de modèle incompatible dans le checkout
qui sert la galerie utilisateur, arrêter d'abord son serveur. Développer et
valider dans une copie / galerie QA isolée, puis relancer sur l'état validé ;
ne pas laisser un ancien module actif lire un nouveau manifeste incompatible.
Sous Windows, le lanceur Python du venv et le worker qui écoute le port peuvent
avoir des PID différents : vérifier identité, commande et démarrage du worker
et de son lanceur avant de les arrêter. Le endpoint de santé seul ne prouve
pas que le parcours utilise le bon profil.
[Incident de profil et limites du diagnostic](validation/m3-02-profile-error.md).

## Galerie des proches

Choisir Personnes, créer une identité puis importer ses fichiers JPEG/PNG/WebP.
L'interface permet de renommer une personne, prévisualiser ses photos, corriger
leur affectation et supprimer une photo ou une personne avec confirmation.
Un import se déclenche par action explicite ; les réexécutions de Streamlit ne
doivent pas réimporter le lot. Les doublons sont refusés pour une même personne.

Le stockage par défaut est `pc/data/gallery.sqlite3` : métadonnées et copies
d'images normalisées dans une base SQLite transactionnelle, ignorée par Git.
Les fichiers originaux choisis restent intacts. L'orientation EXIF est appliquée,
les images sont converties en RGB et les métadonnées ne sont pas conservées.
Limites : 20 fichiers par lot, 20 Mio par fichier et 24 millions de pixels.

La variable de processus `BLAMERA_DATA_DIR` peut sélectionner un autre dossier
de données ; les tests utilisent un dossier temporaire isolé. Sans cette variable,
le chemin par défaut ne dépend pas du répertoire courant du terminal. Pour une
copie de la galerie, arrêter d'abord le serveur puis copier sa base ; cette copie
de données PC n'est pas encore le paquet d'export pour le téléphone de M3-03.

[Contrat](specs/people-gallery.md), [recette](tests/m3-people-gallery-recette.md)
et [preuves du lot](validation/m3-01-people-gallery.md).

## Préparer les visages de référence

Provisionner explicitement les modèles publics et leurs notices :

```powershell
.\scripts\pc.ps1 Models
.\scripts\pc.ps1 Run
```

La commande Models vérifie les fichiers existants ou télécharge les sources
officielles figées ; taille et SHA-256 sont contrôlés avant remplacement atomique.
Le manifeste est `config/face-models.json`, les fichiers sont dans
`models/m3-02/`, hors Git. Le fonctionnement normal de la galerie ne télécharge
pas les modèles et n'envoie aucune photo à un service.

Dans une carte photo, toucher Préparer les visages. Vérifier les aperçus numérotés,
choisir le visage de cette personne puis Enregistrer ce visage. Même avec un seul
candidat, l'association est volontaire. « Visage confirmé » indique la référence
choisie par l'utilisateur ; ce n'est pas une reconnaissance automatique d'identité.
Les visages absents et erreurs de modèle sont affichés sans bloquer la galerie.

La galerie SQLite migre transactionnellement vers v2 en conservant les données
v1. Références, crops alignés et empreintes restent dans sa racine privée ignorée.
Renommer conserve la référence ; réaffecter une photo demande une nouvelle
confirmation ; supprimer retire les références associées. Un changement de
profil de modèle ne réutilise pas une ancienne empreinte incompatible.

Le profil actif PC 0.4.0 est YuNet/ArcFace ResNet50 CPU. SFace sert seulement à
l'alignement natif OpenCV ; ses empreintes ne sont plus calculées. Les nouvelles
empreintes ArcFace ont 512 dimensions, contre 128 pour l'ancien profil. Les photos
et noms sont conservés, mais préparer puis confirmer à nouveau les visages est
nécessaire ; les anciens résultats ne sont jamais convertis ou mélangés.
Les poids InsightFace sont réservés à la recherche non commerciale dans cet
essai privé, sans compte ni achat. Le modèle et les notices restent hors Git.
[Comparatif des modèles](research/m3-face-evaluation.md),
[contrat](specs/face-reference-preparation.md), [recette](tests/m3-face-reference-recette.md)
et [preuves](validation/m3-02-face-preparation.md). Le calibrage sur des photos
indépendantes et des inconnus précède les futures décisions connu/inconnu/incertain.

## Évaluer les visages sur PC

PC 0.5.0 ajoute Personnes → Évaluation, avec le même profil ResNet50 que les
références. Dans Séances des références, choisir la personne et affecter une
séance / période aux photos confirmées, éventuellement en lot. Ajouter ensuite
des photos nouvelles avec une identité attendue ou Personne inconnue, un lot
Calibration / Test final réservé et une séance distincte des références.

Le corpus est dans `pc/data/evaluation/gallery.sqlite3` par défaut, distinct
de `pc/data/gallery.sqlite3` : ses images ne deviennent jamais des références
principales. Avec `BLAMERA_DATA_DIR`, ces deux bases restent sous la racine
choisie. Les références et leur schéma existant sont conservés.

Préparer et confirmer les visages du corpus, puis Évaluer la calibration.
PC 0.5.1 permet de choisir les références utilisées uniquement pour l'évaluation,
avec un compteur retenues / disponibles. Cette sélection persistée limite les
prototypes et les séances demandées, sans retirer de photo ou confirmation de la
galerie principale. Changer ce choix invalide le rapport ; les doublons restent
contrôlés contre toute la galerie. Une sélection vide ne réactive pas toutes les
références implicitement.
Le rapport local distingue bons noms, faux noms, rejets, incertains et cas non
exploitables, avec les effectifs. Les paramètres de comparaison sont exploratoires,
pas des seuils calibrés. Un changement pertinent des données ou réglages exige
un recalcul. En PC 0.6.0, le bouton Figer les paramètres pour le test final exige
un rapport de calibration courant et fixe politique, références et annotations.
Les cibles du lot réservé peuvent ensuite être confirmées manuellement, puis
Évaluer le test final ouvre ses résultats séparés. Un cas sans visage reste dans
le dénominateur. Après exposition, changer les préparations ne réécrit pas le
rapport, et les mêmes pixels ne servent pas à un nouveau gel indépendant.
[Protocole du gel](specs/face-final-test.md),
[recette du test final](tests/m3-final-test-recette.md).

PC 0.6.1 ajoute Sujet attendu non détecté dans les cartes d'évaluation ayant des
candidats. Utiliser cette action si aucun candidat ne correspond au sujet annoté,
plutôt que confirmer une autre personne. Ce constat humain, réversible, produit
un cas non exploitable avec motif ; il ne transforme pas l'identité en inconnu.
Il est lié à la source, annotation, profil et préparation courants. Il appartient
à la préparation du Test et conserve donc le gel déjà enregistré. La galerie de
références n'expose pas cette action.

PC 0.7.0 propose Exporter la galerie sous le bilan final courant. Le téléchargement
JSON reprend uniquement les prototypes moyens des références retenues, les noms,
la politique figée, le profil / prétraitement / modèles et une qualification
agrégée. Pas d'inférence ou export personnel automatique ; aucun poids téléchargé.
Les sources, photos, recadrages et vecteurs individuels ne sont pas transférés.
Conserver ce fichier privé hors Git. [Contrat commun](specs/face-gallery-package.md),
[recette PC](tests/m3-face-gallery-export-recette.md),
[preuves](validation/m3-03-face-gallery-export.md).

[Contrat d'évaluation](specs/face-evaluation.md),
[protocole](decisions/0016-face-evaluation-protocol.md),
[recette](tests/m3-face-evaluation-recette.md),
[preuves](validation/m3-02-evaluation.md).

## Tests et rapports

Tests PC indépendants de l'Android et sans fenêtre visible :

```powershell
Push-Location pc
try {
    .\.venv\Scripts\python.exe -m coverage run -m unittest discover -s tests -v
} finally { Pop-Location }
```

AppTest vérifie les parcours de gestion et les confirmations ; les tests de
stockage/import utilisent une base temporaire et des images synthétiques.
Le vrai sélecteur de fichiers et le rendu navigateur font aussi l'objet d'une
recette distincte : leur réussite n'est pas déduite d'AppTest seul.
Le smoke-test démarre son propre serveur sur un port loopback disponible, vérifie
`/_stcore/health`, puis ferme **ce seul processus** dans un `finally`. Aucun serveur n'est laissé actif.
Ses logs sont dans `build/reports/quality/pc/server.log`.

Pour toutes les gates et rapports : `scripts/pc.ps1 Quality`, ou `scripts/quality.ps1`
qui sélectionne `pc/.venv/Scripts/python.exe` par défaut. L'option `-PythonExe` reste
explicite ; employer le Python de ce venv pour l'environnement complet.
`python scripts/quality.py` doit également être lancé avec ce Python, pas un alias WindowsApps.

Rapports PC : `build/reports/quality/pc/` contient `summary.json`, couverture XML/JSON/HTML,
complexités JSON, duplication JSON/HTML, inventaire/licences et `osv.json` daté.
Les fichiers `.log` décrivent chaque outil. La synthèse globale distingue mobile, scripts
et PC. Aucune donnée absente n'est assimilée à une conformité. Voir [quality.md](quality.md).

Les notices des dépendances sont conservées dans `pc/.venv/Lib/site-packages/*dist-info/`
(Linux : `pc/.venv/lib/python3.12/site-packages/`). Les exceptions de normalisation
de métadonnées dans `pc/config/licenses.json` fixent version, empreinte, licence et source.
Une dépendance inconnue ou modifiée demande une nouvelle lecture, pas une autorisation automatique.
Conserver ces notices si un environnement est un jour redistribué ; ce ticket ne le distribue pas.

## Linux / CI préparée

Le workflow non publié décrit le bootstrap Linux exact : uv 0.13.5 officiel vérifié
(`f68d291c75783122dad58ab77def0e13f3233f358731db5d6d7e8715603b928f`), Python
géré 3.12.14 dans `.tools`, synchronisation verrouillée puis `pc/.venv/bin/python scripts/quality.py`.
Il ne téléverse ni données, ni APK personnel, ni clé. Son résultat Linux demeure **non validé**
tant qu'il n'a pas été exécuté ; la CI distante est volontairement reportée par l'utilisateur.

Photos, jeux de données, embeddings, modèles, exports et secrets restent hors Git dans
les dossiers ignorés `data/`, `datasets/`, `models/`, `exports/` à la racine ou sous `pc/`.
Ne pas déposer de données privées dans les fixtures. M3/M4 apporteront l'import, les
corrections, modèles et expériences CPU ; ils ne font pas partie du socle M0-05.
