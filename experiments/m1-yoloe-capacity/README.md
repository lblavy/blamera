# YOLOE26 PF X → M → L — préparation

28 octobre 2026. Sources et gel prêts pour revue ; **aucune prédiction sur les 144 images**.
[Protocole](../../docs/research/m1-yoloe-capacity-protocol.md).
Moteur qualité et adaptateur Small importés en lecture seule, anciens résultats/gels intacts.
Seuls taille, chemins, préfixes et comparaisons aux trois références sont adaptés.

## Exports et parité hors corpus

Checkpoints officiels `ultralytics/assets`, release v8.4.0, acquis par l’orchestrateur.
Notices AGPL3 conservées sous `models/m1-yoloe-capacity/LICENSE-assets-AGPL.txt` ; même
périmètre privé/local que Nano/Small, aucun achat/encodeur Apple/CLIP ou distribution.

| Taille | ONNX octets | SHA256 ONNX | NASA / uniforme, détections à 0,35 |
| --- | ---: | --- | ---: |
| X | 272 481 865 | `7d98a8398f43fb291bd576f1f78a607a4ed16d7329ca99248f78decfd1cd3414` | 17 / 1 |
| M | 108 522 038 | `e0488791e524d19c2fe3a2e6b94554b7b2004d869d968bb138b27956669ff5d1` | 12 / 0 |
| L | 126 147 362 | `b3db19c7f11550fb76b5607d81234f6275126568cee71f3b8c3291e054b4e2de` | 17 / 0 |

CPU FP32/statique 640/batch 1/opset 17, simplify=False/nms=False/end2end=False.
Entrée `[1,3,640,640]`, sorties `[1,4621,8400]` + `[1,32,160,160]` ; 4 585 noms et ordre
identiques au Nano, vérifiés intégralement contre ses métadonnées ONNX réelles.
Exporteur Small réutilisé sans édition, constantes de checkpoint/sortie configurées dans
chaque processus. Ses caches workspace `.tools/m1-yoloe-small/{yolo-config,mpl,torch-cache}`
sont réutilisés honnêtement ; environnement Python CPU/versions inchangé, réseau bloqué,
auto-install/telemetry/sync désactivés et SSL préchargé avant blocage.
L’export exécute des forwards synthétiques ; la parité utilise seulement NASA/négatif
uniforme historiques, hors corpus de 144 images. Une sortie X sur l’uniforme reste consignée.

X/M passent les bornes historiques raw≤0,02/protos≤0,005 et masque IoU≥0,99 au profil
agnostique 0,35/cap 20 (minimum M 0,9999197). L a d’abord échoué raw 0,03760>0,02,
journal `export-l.log` conservé, sans réexport. Diagnostic borné : écart dans les coordonnées,
max 0,04187 pixel réseau ; scores≤0,00004134, coefficients≤0,00031531, protos≤0,00010014.
**Amendement L seul accepté avant corpus par l’orchestrateur et le reviewer** : coordonnées
≤0,05 pixel réseau (1/20 pixel), scores/coefficients≤0,02 et protos≤0,005 inchangés.
Les 12 profils ont mêmes anchors/classes/ordre/comptes après NMS ; argmax sans différence
au-dessus de 0,20/0,35/0,50. IoU≥0,99 vérifiée **uniquement à 0,35**, caps 20/100, deux NMS
(minimum NASA 0,9999783) ; pas de vérification de masque revendiquée aux autres scores.
`parity_l.py` finalise les preuves existantes et teste ces garde-fous, sans nouveau forward.

## Tests, gel et commandes

7 tests ciblés conformes, sortie 0 (0,176 s) : Red → Green sur préfixes/ordre, chemins protégés,
trois références et bornes L ; intégration synthétique ensuite verte, une seule session et
un forward par image. Les helpers hérités sont ceux déjà validés (37 qualité +6 Small),
sans réexécution inutile de leurs anciennes campagnes. Ruff format/syntaxe ciblée sortis 0.
Logs/preuves dans `build/reports/validation/m1-yoloe-capacity/`, dont les trois exports,
`l/diagnosis.json`, `l/export-parity.json`, `final-tests.log`, `adapter-{red,green}.log`.

Gel `sealed-manifest.json` : 66 références, SHA
`1596a5630e3b7d0635721f990c5a919c40cf919ab28fbe05b85852fdb8464d34` ; ancien gel 197
références et Small 90 vérifiés, modèles/provenance, références relues et nouveaux scripts/JSON.
Nouveaux fichiers LF avant gel ; anciens CRLF inchangés. Date volatile ONNX conservée,
pas de promesse d’un hash universel après réexport ; conserver les exports contrôlés.

Vérification seule exécutée sortie 0, sans corpus :

```powershell
.\.tools\m1-model-spike\.venv\Scripts\python.exe experiments/m1-yoloe-capacity/run_capacity.py --size x --seal-sha256 1596a5630e3b7d0635721f990c5a919c40cf919ab28fbe05b85852fdb8464d34
```

Après revue/GO seulement, le tester lancera **sériellement X puis M puis L** :

```powershell
.\.tools\m1-model-spike\.venv\Scripts\python.exe experiments/m1-yoloe-capacity/run_capacity.py --size x --seal-sha256 1596a5630e3b7d0635721f990c5a919c40cf919ab28fbe05b85852fdb8464d34 --execute
.\.tools\m1-model-spike\.venv\Scripts\python.exe experiments/m1-yoloe-capacity/run_capacity.py --size m --seal-sha256 1596a5630e3b7d0635721f990c5a919c40cf919ab28fbe05b85852fdb8464d34 --execute
.\.tools\m1-model-spike\.venv\Scripts\python.exe experiments/m1-yoloe-capacity/run_capacity.py --size l --seal-sha256 1596a5630e3b7d0635721f990c5a919c40cf919ab28fbe05b85852fdb8464d34 --execute
```

Sorties nouvelles `<taille>/results-v1`, dossier existant refusé. Chaque passe = 144 forwards,
12 profils/1 728 enregistrements, sans rerun baseline/Nano/Small. Pas de résultat qualité
ou verdict final avant exécution/validation ; aucune app/APK/ADB/téléphone/CI/Git/scan global.
