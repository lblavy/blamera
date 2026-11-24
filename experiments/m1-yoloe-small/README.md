# YOLOE Small PF — préparation, avant le passage des 144 images

28 octobre 2026 (acquisition UTC du 27 octobre). Adaptateur expérimental séparé,
**revue du runner à effectuer avant toute prédiction sur le corpus qualité**.
Les fichiers scellés de `../m1-yoloe-quality`, les résultats baseline/Nano, le modèle
par défaut, l’application et ses gates ne sont pas modifiés.
[Protocole](../../docs/research/m1-yoloe-small-protocol.md).

## Acquisition, export et parité exécutés

- Checkpoint officiel [YOLOE-26s-seg-pf](https://github.com/ultralytics/assets/releases/download/v8.4.0/yoloe-26s-seg-pf.pt),
  asset 570405543/release 276461319 : 40 003 064 octets,
  SHA `01605da3f2e0e5325df45d5f5eef8e40b5b5c7d085399be3c33ac638258b150a` ; digest GitHub concordant.
- [Licence AGPL3 de l’asset](https://raw.githubusercontent.com/ultralytics/assets/v8.4.0/LICENSE),
  SHA `0d96a4ff68ad6d4b6f1f30f713b18d5184912ba8dd389f86aa7710db079abcb0`.
  Même périmètre privé/local que Nano ; aucune distribution, aucun achat ou encodeur Apple.
- Export séparé `models/m1-yoloe-small/yoloe-26s-seg-pf.onnx` : 48 903 716 octets,
  SHA `a545b3e6933a3d94870f5c91d210c5eb2842f56c5221bad93f15db9f72a5c81f`.
  CPU FP32, statique 640, batch 1, opset 17, simplify=False, nms=False, end2end=False.
  Entrée `[1,3,640,640]`, sorties `[1,4621,8400]` et `[1,32,160,160]`.
- Comparaison intégrale : **4 585 noms et leur ordre identiques** au Nano, confirmés
  aussi contre les métadonnées de son ONNX réel. Aucun nouveau synonyme ou filtrage vers 80.
- Parité PyTorch branche raw end2end=False/ONNX sur NASA historique et négatif uniforme,
  hors corpus de 144 images : erreurs max raw 0,012421/prototypes 0,000043 ; tolérances 0,02/0,005.
  NASA 14 détections appariées, IoU masque minimum 1 ; négatif 0 détection.
  Ces fixtures ne mesurent pas la qualité générale. L’export implique des forwards synthétiques.

Environnement historique **réutilisé en lecture seule** : Python 3.12.14, Ultralytics 8.4.190,
Torch 2.14.1+cpu, ONNX 1.23.2, ORT 1.31.0 ; lock `../m1-yoloe/uv.lock`, rasteriseur privé
pycocotools 2.0.11 hérité. Ni CUDA, installation globale, modification de DLL/JBR, nouveau
modèle/appareil ou dépendance. Réseau bloqué pendant export/parité et future inférence,
auto-install/telemetry/sync désactivés ; réglages et caches nouveaux sous `.tools/m1-yoloe-small`.
La date d’export est conservée dans `export-parity.json` : un réexport peut changer le SHA,
aucune promesse d’empreinte universelle ; conserver l’artefact contrôlé pour le rejeu strict.

## Tests et incident de préparation

```powershell
.\.tools\m1-model-spike\.venv\Scripts\python.exe -m unittest discover -s experiments/m1-yoloe-small
```

6 tests conformes (0,567 s), dont Red → Green sur les 12 profils, la permutation pure des
indices, les paires sur mêmes GT et les alias natifs ; tests d’intégration ensuite verts
avec tenseurs synthétiques et session simulée. Les 37 tests hérités ont été réexécutés
sans modification, conformes (0,953 s). Ruff format et contrôle E9/F63/F7/F82 sortis 0.
Preuves dans `build/reports/validation/m1-yoloe-small/` : `adapter-red.log`,
`adapter-green.log`, `native-mapping-{red,green}.log`, `adapter-final-tests.log`, `inherited-tests.log`.

Premier export arrêté à l’import SSL avant chargement du checkpoint : la classe socket
était remplacée trop tôt par le blocage réseau. Précharger `ssl` corrige cette plomberie,
sans toucher l’environnement ou l’ancien moteur. Ce n’est pas un Red fonctionnel ou un
résultat du modèle. Le journal initial a été remplacé lors de la relance ; diagnostic
observé conservé dans `export-import-incident.txt`, journal final `export-parity.log` sortie 0.

## Gel et prochaine étape

Nouveau gel `build/reports/validation/m1-yoloe-small/sealed-manifest.json`, 90 références,
SHA `38562deb71a3a1a9fc4c2749b1baff08def2cb6f84d86b25eb4a71a95ccdc445` :
ancien gel 197 références `6ff9…dcb2` vérifié, données/versions hérites, résultats baseline/Nano
et panneaux relus, scripts/protocole Small, checkpoint/ONNX/notices et preuve d’export/parité.
Les nouveaux `.py`/`protocol.json` sont écrits **LF avant gel**. Les anciens CRLF restent
intacts ; leur archive exacte documentée demeure nécessaire après un checkout LF.

Vérification seule exécutée, sortie 0, sans prédiction sur le corpus :

```powershell
.\.tools\m1-model-spike\.venv\Scripts\python.exe experiments/m1-yoloe-small/run_small.py --seal-sha256 38562deb71a3a1a9fc4c2749b1baff08def2cb6f84d86b25eb4a71a95ccdc445
```

**Ne pas ajouter `--execute` avant revue et décision de l’orchestrateur.** Après revue,
le runner exécutera **Small seulement** : 144 forwards/12 profils/1 728 enregistrements.
Baseline/Nano sont relus, jamais réexécutés ; sortie nouvelle sous le dossier ignoré Small,
ancien dossier de résultat refusé. Même moteur de scoring/cache/NMS/masques, strates,
GT partiel, ADE/void et panneaux aux 12 IDs. Paires Small vs Nano du même NMS, et vs baseline,
noms communs/nouveaux et géométrie sans nom séparés. Aucun résultat Small sur ces 144 images
ni conclusion de remplacement actuellement. Aucun build/global Quality/scan/ADB/téléphone/Git.
