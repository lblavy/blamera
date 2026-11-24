# M3-04 / M3-05 — références publiques de parité faciale

2 novembre 2026. Générateur séparé de la galerie personnelle. Il réutilise le
moteur CPU PC qualifié et produit les attentes pour comparer le moteur Android,
sans régler les seuils d'identité ni mesurer la qualité statistique des proches.

## Données et reproduction

Le seul fichier source est `models/m3-02/astronaut.png`, déjà provisionné pour
M3-02, copie de scikit-image v0.25.2. SHA-256 attendu :
`88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5`.
La [documentation de la fixture](https://scikit-image.org/docs/stable/api/skimage.data.html#skimage.data.astronaut)
attribue la photographie à NASA et indique sa mise dans le domaine public.
Sa provenance épinglée est aussi conservée dans
[`model-assets.json`](../m3-02-face-evaluation/model-assets.json).

Depuis la racine du dépôt, avec l'environnement PC et ses modèles déjà présents :

```powershell
./pc/.venv/Scripts/python.exe -c "import cv2,runpy; cv2.setNumThreads(1); runpy.run_path('experiments/m3-04/generate_references.py',run_name='__main__')"
./pc/.venv/Scripts/python.exe -m unittest discover -s experiments/m3-04 -p 'test_*.py' -v
```

Le générateur ne télécharge rien, ne modifie pas la source, ne lit ni `resources/`
ni `pc/data/`, et publie seulement sous `build/`. Une publication se fait par
renommage d'un répertoire temporaire dans le même dossier. Une sortie identique
est réutilisée ; une sortie différente ou contenant des fichiers étrangers est
refusée. Les contenus étrangers ou de mauvaise taille ne sont pas chargés pour
valider cette réutilisation.

## Contrat v2 — comparaison de chaque visage

M3-05 fait évoluer uniquement le protocole des références publiques en **schema2**.
Le paquet de galerie et son profil restent v1. La sortie v1 de M3-04, qui vérifiait
seulement le nombre de visages du cas multiple, est conservée sous
`build/fixtures/m3-04/` ; le générateur refuse toujours de remplacer une sortie
différente.

Sortie active par défaut : `build/fixtures/m3-05/`, copiée ensuite dans les assets
APK `faces/parity/`. Les sept fichiers sont : `manifest.json`, `images/portrait.png`,
`images/blank.png`, `images/multiple.png`, `expected/portrait-aligned.png`,
`expected/multiple-0-aligned.png`, `expected/multiple-1-aligned.png`.

Le manifeste contient exactement `schema`, `profile`, `tolerances`, `cases`.
Le profil est le couple `{key, dimension}` de `config/face-models.json`.
Chaque cas porte `id`, `image` (chemin relatif PNG), `image_sha256`, `width`,
`height`, `expected_kind` parmi `single`, `none`, `multiple`. Le cas `single`
ajoute `bounds` (left/top/right/bottom), `landmarks` (dix coordonnées), `aligned`
(PNG relatif), `embedding` (512 nombres unitaires). Le cas `none` n'ajoute rien.
Le cas `multiple` ajoute exactement `faces`, une liste de deux à vingt objets
contenant chacun ces quatre champs. Les crops sont encodés individuellement par
le moteur PC : aucun embedding du portrait simple n'est recopié à leur place.
L'ordre reste celui du moteur PC, score décroissant stable et ordre du détecteur
conservé en cas d'égalité ; les boîtes restent associées à leur propre encodage.
Le moteur Android peut apparier géométriquement les candidats plutôt que supposer
que l'ordre natif est identique entre processeurs.

| Cas | Dimensions | Résultat PC attendu |
| --- | --- | --- |
| portrait | 512 × 512 | un visage |
| blank | 320 × 240 | aucun visage |
| multiple | 1024 × 512 | deux visages, chacun avec sa géométrie, son crop et son embedding PC |

Les tolérances sont fixées avant la comparaison Android : positions/repères
≤1 pixel source, erreur moyenne absolue des couleurs alignées ≤1 sur 0..255,
cosinus des embeddings ≥0,9999, écart des scores ≤0,002. Clés JSON respectives :
`bounds_abs_px`, `landmarks_abs_px`, `aligned_rgb_mae`, `embedding_cosine_min`,
`score_abs`. Aucune tolérance n'est ajustée pour faire réussir une mesure.

Les images dérivées et embeddings restent dans `build/`, ignoré par Git ; seuls
le générateur, ses tests synthétiques et ce document sont suivis. Les trois cas
ne constituent ni un corpus d'identification indépendant ni une mesure sur
Xiaomi. Ils servent à détecter un écart de prétraitement ou d'exécution.

Le préparateur `blamera_pc.face_mobile_assets` exige schema2 et valide chaque visage
avant publication : clés exactes, bornes/repères, vecteur unitaire et crop PNG RGB
112×112 au chemin local borné. Il conserve la validation stricte des types entiers,
du profil, des SHA des images et des tolérances. Le seed privé reste facultatif et
explicite ; une préparation générique supprime un ancien bootstrap de la sortie.

## Émulateur PC isolé

Le lot utilise l'image Android 36 Google APIs x86_64 déjà présente sur le PC.
L'AVD propre au lot se trouve dans `.tools/avd/blamera-m3-04.avd`, avec les
variables de processus Android redirigées sous `.tools/`. Quatre cœurs, 4 Gio,
écran 720×1280 et rendu logiciel ; aucune caméra hôte. Aucun AVD existant ou
fonctionnalité Windows n'est modifié.

Le lancement est caché, sans fenêtre, snapshot ou audio, sur **emulator-5580**.
Seules des commandes explicitement ciblées avec `adb -s emulator-5580` sont
autorisées pour ces essais. Ne pas utiliser un runner qui ciblerait également
le téléphone physique. L'installation de l'APK principal/test public sur cet
émulateur sera déclenchée séparément par l'orchestrateur après stabilisation.
Le téléphone n'a reçu aucune opération de ce sous-lot.

Les reçus de lancement, PID du worker, commande, démarrage et confirmation de
boot sont dans `build/reports/validation/m3-04/parity-coder/emulator-ready.json`.
L'orchestrateur conserve la responsabilité de l'arrêt vérifié de cet émulateur.
