# M3-02 — spike facial CPU

1er novembre 2026. Exploration bornée à environ 20 minutes, séparée du code de
production. L'exception TDD concerne la collecte de mesures, pas le futur logiciel.
Aucun accès à `pc/data`, aux photos de l'utilisateur ou au téléphone.

## Reproduire

Depuis la racine, créer un environnement isolé avec Python 3.12.14 puis installer
les versions de `requirements.txt` avec le uv du dépôt :

```powershell
$env:UV_CACHE_DIR = "$PWD/.tools/uv-cache"
./.tools/uv/0.13.5/uv.exe venv .tools/m3-02-eval --python ./pc/.venv/Scripts/python.exe
./.tools/uv/0.13.5/uv.exe pip install --python .tools/m3-02-eval/Scripts/python.exe -r experiments/m3-02-face-evaluation/requirements.txt
./.tools/m3-02-eval/Scripts/python.exe experiments/m3-02-face-evaluation/prepare.py
```

Les poids/fixtures vont dans `models/m3-02`, ignoré par Git. `model-assets.json`
fixe sources, révisions, tailles et SHA-256 constatés. Le setup ne lit aucune
galerie et n'importe pas InsightFace : les trois graphes sont utilisés directement.
Les archives officielles buffalo_l/sc restent des poids de recherche non commerciale.
Ils ne sont pas intégrés à l'application ni redistribués dans Git.

Le profil expérimental `sface_constant` est une copie de SFace retirant uniquement
des graph inputs les tenseurs qui sont déjà des initializers. Les 174 tenseurs
de poids et les nœuds restent identiques. Pour le régénérer :

```powershell
./.tools/m3-02-eval/Scripts/python.exe -c 'import onnx; p="models/m3-02/"; m=onnx.load(p+"sface.onnx"); names={x.name for x in m.graph.initializer}; inputs=[x for x in m.graph.input if x.name not in names]; del m.graph.input[:]; m.graph.input.extend(inputs); onnx.checker.check_model(m); onnx.save(m,p+"sface-constant.onnx")'
./.tools/m3-02-eval/Scripts/python.exe experiments/m3-02-face-evaluation/verify.py
./.tools/m3-02-eval/Scripts/python.exe experiments/m3-02-face-evaluation/benchmark.py --threads 1
./.tools/m3-02-eval/Scripts/python.exe experiments/m3-02-face-evaluation/benchmark.py --threads 4
```

Résultats dans `build/reports/validation/m3-02-face-evaluation/` : téléchargements,
graphes, parité, versions et chaque répétition. Les anciens `results.json` et
`benchmark.log` sont le premier diagnostic, conservé avec sa forte variance ;
les deux profils finaux portent `results-threads1.json` et `results-threads4.json`.

## Fixtures et limites

- `astronaut.png`, NASA/Eileen Collins : copie scikit-image v0.25.2, domaine public
  explicitement indiqué dans sa [documentation](https://scikit-image.org/docs/stable/api/skimage.data#skimage.data.astronaut).
- `john-glenn.jpg` : portrait officiel crédit NASA, asset lié dans la
  [page NASA](https://www.nasa.gov/people/john-glenn/) ;
  [conditions de réutilisation](https://www.nasa.gov/nasa-brand-center/images-and-media/).
- Le fond uniforme et le collage sont produits en mémoire ; les variantes de
  luminosité, flou, résolution et rotation proviennent des mêmes deux portraits.

Ce n'est ni LFW ni un test d'identification de proches. Deux identités et des
transformations d'une même photo ne calibrent pas FAR/TAR, inconnus ou seuils.
Le détecteur YuNet et l'alignement SFace sont communs : ce n'est pas le pipeline
officiel complet SCRFD+InsightFace. Mémoire = processus comprenant aussi les
modèles OpenCV communs, pas poids seuls. Aucun résultat Android n'est déduit.

[Comparaison et recommandation](../../docs/research/m3-face-evaluation.md).
