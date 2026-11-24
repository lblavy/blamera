# Spike M1-01 — YOLO11n-seg CPU

Exploration locale bornée à un candidat et une combinaison de dépendances, environ
30 minutes de recherche/exécution. **Pas de code de production**, pas de lien avec
l'application ou les gates de livraison. Exception TDD exploratoire d'AGENTS.md :
les assertions vérifient les résultats réels, sans prétendre à un cycle Red/Green.
Toute promotion d'un comportement dans l'application exigera son TDD et ses gates.

`run_spike.py` est la source exacte exécutée depuis `.tools/m1-model-spike/` ;
le déplacement sous `experiments/m1-01` conserve sa résolution de la racine du dépôt.
Le manifeste versionné précise versions, provenance, hashes et limites ; poids, images,
environnement et résultats restent ignorés dans `models/`, `.tools/` et `build/`.

## Reproduire sur le Windows du projet

Prérequis : uv 0.13.5 et Python géré 3.12.14 déjà préparés par le socle M0.
Depuis la racine, après lecture du manifeste et de la licence conservée localement :

```powershell
$env:UV_PYTHON_INSTALL_DIR = Join-Path (Get-Location) '.tools/python'
$env:UV_CACHE_DIR = Join-Path (Get-Location) '.tools/m1-model-spike/cache'
$env:UV_PROJECT_ENVIRONMENT = Join-Path (Get-Location) '.tools/m1-model-spike/.venv'
$env:UV_PYTHON_INSTALL_BIN = '0'
$env:UV_PYTHON_NO_REGISTRY = '1'
$env:UV_NO_BUILD = '1'
.\.tools\uv\0.13.5\uv.exe sync --project experiments/m1-01 --locked --python 3.12.14 --managed-python
if ($LASTEXITCODE -ne 0) { throw 'Environnement spike en echec' }
New-Item -ItemType Directory -Force 'models/m1-01/yolo11n-seg','build/reports/validation/m1-01' | Out-Null
Invoke-WebRequest 'https://github.com/ultralytics/assets/releases/download/v8.3.0/yolo11n-seg.pt' -OutFile 'models/m1-01/yolo11n-seg/yolo11n-seg.pt'
Invoke-WebRequest 'https://raw.githubusercontent.com/ultralytics/ultralytics/e17decf32df88eb6a5c502b7e054f32257d08699/LICENSE' -OutFile 'models/m1-01/yolo11n-seg/LICENSE-AGPL-3.0.txt'
Invoke-WebRequest 'https://raw.githubusercontent.com/scikit-image/scikit-image/v0.19.3/skimage/data/astronaut.png' -OutFile 'build/reports/validation/m1-01/astronaut.png'
# Comparer les SHA-256 des trois fichiers au manifeste avant execution.
.\.tools\m1-model-spike\.venv\Scripts\python.exe experiments/m1-01/run_spike.py
```

Les roues `torch` et `torchvision` proviennent exclusivement de l'index CPU officiel ;
pas de CUDA. La petite dépendance `nvidia-ml-py` est un binding de monitoring transitif,
pas une installation de GPU/driver/CUDA. Aucun compte ou achat.

Le script configure le répertoire YOLO dans `.tools` **avant import**, désactive sync,
intégrations et télémétrie ORT, puis bloque les connexions socket Python avant chargement,
export et prédiction. Cette version ajoute le sous-dossier `Ultralytics` à YOLO_CONFIG_DIR.
Il exporte FP32, 640², batch 1, opset 17 statique, sans NMS intégré ni simplification.
Il utilise ensuite le même tenseur pour comparer les sorties natives/ORT, puis le même
post-traitement Ultralytics (conf 0,25 / IoU NMS 0,7) pour comparer boîtes/classes/masques.

Attention : `threads=4` dans le rapport représente la requête initiale du script.
La sélection CPU upstream peut réinitialiser les threads PyTorch et le wrapper ORT utilise
ses valeurs par défaut ; seul ORT direct fixe explicitement 4/1 threads. Les latences
sont des observations de cette recette, sans comparaison de vitesse à threads strictement égaux.
La date ajoutée aux métadonnées ONNX est volatile : son SHA n'est pas une promesse bit à bit universelle.
Voir [le rapport](../../docs/validation/m1-01.md) ; aucune intégration Android ni mesure téléphone.

## Restituer le modèle contrôlé de l'application

Le benchmark `run_spike.py` ci-dessus est la recette historique et réexporte à côté du
checkpoint. Pour reconstruire l'artefact contrôlé sans écraser le modèle retenu, utiliser
désormais cette recette distincte, avec le même environnement Windows verrouillé :

```powershell
& '.tools/m1-model-spike/.venv/Scripts/python.exe' experiments/m1-01/reconstruct_onnx.py --install-if-missing
if ($LASTEXITCODE -ne 0) { throw 'Reconstruction en echec : arret.' }
```

Elle copie le checkpoint vérifié en dossier temporaire sous `build/`, exporte avec les
mêmes paramètres, puis restaure **uniquement** `metadata.date` à la valeur du manifeste.
Le SHA intégral et la taille doivent retrouver l'artefact d'origine ; toute divergence
arrête la recette. `--install-if-missing` copie seulement après ces preuves et seulement
si le modèle de destination manque (création exclusive, aucun remplacement).
Le modèle original existant doit garder son SHA avant/après. Le résultat et les exports
temporaires restent dans `build/reports/validation/m1-03/model-reconstruction/`.
Preuve du 26 octobre : 11 720 021 octets, SHA `38325721a41fcd732b0a30512ffa8d564b1a8a74ca8c1d6b7e7f37609b2d321b`.
Cette preuve est limitée à ce Windows et aux versions figées ; aucune garantie binaire
universelle n'est déduite. La recette reste exploratoire, sans import dans le code livré.

## Référence de masque pour les tests applicatifs

`reference_mask.py` réutilise le même environnement et les réglages hors ligne du spike,
sans export ni benchmark : il exécute une seule prédiction CPU NASA avec confiance0,35
et NMS0,5 puis écrit les octets 0/1 du masque640². Cette courte recette reste exploratoire,
hors code de production et hors commande qualité. Sa sortie sert de fixture autorisée
aux tests Kotlin ; voir [préparation M1-03](../../docs/image-segmentation.md).
