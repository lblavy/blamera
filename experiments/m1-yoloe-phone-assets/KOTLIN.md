# Variante d'entrée Kotlin float32

Le diagnostic JVM a vérifié les mêmes pixels RGB sur les 14 PNG. Six tenseurs
diffèrent du calcul NumPy, avec une variation de 1/255 sur 1 à 681 valeurs :
NASA, LVIS22892 et les quatre ADE. L'interpolation NumPy historique promeut les
poids en float64 ; le préprocesseur mobile utilise float32. Le préprocesseur de
l'application et toutes les sorties v1 restent inchangés.

`kotlin-protocol.json` fixe avant les nouveaux forwards : SHA du manifeste JVM,
sources du prétraitement et géométrie, versions JVM/PC, catalogue v1, index des
56 références v1. Les tensors effectifs sont relus et vérifiés avant les calculs.
Le contrôle vérifie 14 identités uniques, dimensions et pixels identiques,
hashs parent/effectifs, taille exacte 4915200 octets par tenseur, valeurs finies
dans [0,1], nombre de valeurs différentes et erreur maximale du diagnostic.

```powershell
.tools/m1-model-spike/.venv/Scripts/python.exe experiments/m1-yoloe-phone-assets/prepare_kotlin.py validate
.tools/m1-model-spike/.venv/Scripts/python.exe -m unittest discover -s experiments/m1-yoloe-phone-assets -v
# Après revue indépendante et GO :
.tools/m1-model-spike/.venv/Scripts/python.exe experiments/m1-yoloe-phone-assets/prepare_kotlin.py references
```

L'étape `references` réalise exactement 24 forwards compacts CPU (2/1 threads ORT)
et copie octet pour octet 32 cas v1 dont le tenseur est identique. Elle ne calcule
pas de nouveau parent raw et ne prétend donc pas produire une nouvelle preuve
de parité raw/compact sur les entrées modifiées. Les modèles, PNG, labels et
licences packagés restent les mêmes 22 feuilles. Le nouveau catalogue
`catalog-kotlin.properties` change seulement six pins de tenseur et ajoute le
mode `kotlin-float32` ainsi que la provenance. Les tenseurs ne sont pas packagés.
Les nouvelles références sont dans `models/m1-yoloe-phone-assets/kotlin-references/` ;
l'index et les hashes sont dans le nouveau rapport `kotlin-complete.json`.
Une sortie déjà présente bloque la campagne, sans reprise implicite ni réécriture.

Tests de comportement de l'addon : Red réel sortie1, huit échecs fonctionnels
(identité, provenance, sélection et mise à jour du catalogue) ; Green3 tests
sortie0 puis ensemble9 tests sortie0. Ruff0.17.2 format/check exécutés localement.
L'étape `validate`, sans inférence, est passée sortie0 avant la revue.
Les métriques de couverture/duplication/complexité des scripts restent non mesurées.
