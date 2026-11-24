# Assets du diagnostic Android 0.6

Préparation locale explicite, sans effet à l'import, dans un nouveau périmètre.
Les anciens modèles, exports, fixtures et gels sont lus et vérifiés, jamais modifiés.
Le protocole fixe avant les inférences 14 images : NASA, synthétique, 8 LVIS et 4 ADE.
Ce diagnostic privé ne constitue pas une nouvelle étude de qualité sur 144 images.

## Exécution

Depuis la racine, avec l'environnement CPU existant, sans installation :

```powershell
.tools/m1-model-spike/.venv/Scripts/python.exe -m unittest discover -s experiments/m1-yoloe-phone-assets -v
.tools/m1-model-spike/.venv/Scripts/python.exe experiments/m1-yoloe-phone-assets/prepare_assets.py prepare
.tools/m1-model-spike/.venv/Scripts/python.exe experiments/m1-yoloe-phone-assets/prepare_assets.py export
.tools/m1-model-spike/.venv/Scripts/python.exe experiments/m1-yoloe-phone-assets/prepare_assets.py references
```

Les deux dernières étapes attendent la revue indépendante de ce source. Le CLI
bloque la création des sockets pendant chaque étape ; SSL est chargé avant ce
blocage. Ultralytics reste hors ligne, sans installation automatique, synchronisation
ni intégration de télémétrie. Tous ses caches sont dans le nouveau dossier ignoré.
Les sorties existantes différentes sont conservées et provoquent un arrêt.
Une reprise des références saute les cas déjà écrits ; leur intégrité doit être
contrôlée par le validateur avant l'utilisation des preuves finales.

## Contrat

Les poids copiés S/L/X sont exportés CPU FP32 statique 640, batch1, opset17,
simplify=False, end2end=False, NMS natif agnostique, score .35, IoU .5, max20.
Le compact baseline 80 classes existant est copié sans réexport et conserve NMS
par classe. Les dates exporteur actuelles sont conservées. Les 4585 catégories PF
restent dans l'ordre du checkpoint et du parent raw, sans mapping.

Les JPG figés sont décodés via OpenCV en RGB8 puis stockés en PNG sans perte des
pixels décodés, sans ICC. NASA et synthétique conservent leurs octets PNG.
Les SHA distinguent source, PNG, pixels RGB8 row-major et tenseur float32 LE NCHW.
Le prétraitement importe uniquement le module pur historique `postprocess.py`.
Le catalogue `models/m1-yoloe-phone-assets/catalog.properties` contient chaque
feuille packagée avec chemin dépôt, nom d'asset, taille et SHA256. Il est écrit
après les 56 références PC ; ni checkpoints, tenseurs ni références ne figurent
dans `files`. `NOTICE.json` conserve les licences et liens Flickr individuels,
provenance ADE et sources modèles. APK/corpus réservés à cet essai personnel local.

Une référence conserve toutes les boîtes après NMS, même avec masque vide.
`box` désigne les pixels source ; `networkBox` désigne XYXY dans le réseau 640.
Le masque 160 est `logit>0`, recadré par boîte et letterbox à `x=4*col,y=4*row`.
`maskBase64` contient 3200 octets bruts MSB-first row-major ; `maskSha256` porte
sur ces octets. Aucun redimensionnement ni contour n'entre dans cette mesure.
Deux masques vides peuvent être numériquement identiques sans preuve de qualité.

Les parents raw sont décodés avec le pré-cap300 historique et les mêmes seuils.
Le NMS compact Ultralytics n'a pas ce pré-cap : les algorithmes ne sont donc pas
identiques pour tout input. La parité des 14 entrées vérifie nombres et catégories,
boîtes réseau <=.05px, scores <=.0001, prototypes <=.005, masques160 IoU>=.99.
Ces bornes sont fixées ici avant les nouvelles inférences et sont distinctes de
l'amendement historique L (PT/raw). Toute différence arrête la campagne avec
`STOP.json`, sans modification des seuils ni substitution de labels.

## Preuves TDD

Commande unittest ci-dessus : Red initial exécuté (9 échecs contractuels et
1 erreur de résultat absent, sortie1), Green4 tests sortie0. NMS aware/agnostic :
Red1 échec fonctionnel sortie1 puis Green5 tests sortie0. Régression input négatif :
Red diffusion NumPy vide `(0,)` vers `(0,38)` sortie1, puis correction conditionnelle.
Les étapes export/fixtures sont une préparation expérimentale bornée ; les preuves
réelles de parité remplacent une simulation des bibliothèques et du modèle.
Couverture/duplication/complexité des scripts : non mesurées à ce stade, jamais
présentées conformes. Ruff utilise le binaire local existant 0.17.2.
