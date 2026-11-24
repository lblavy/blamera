# M3-02 — modèles faciaux : comparaison initiale PC/Android

1er novembre 2026. Spike CPU borné, distinct du code de production et de la
calibration sur les proches. Aucun accès à la galerie personnelle ou au téléphone.

## Ce que font les modèles

Il faut deux opérations. **YuNet** détecte le visage et cinq points (yeux, nez,
bouche), utilisés pour redresser/recadrer le visage. **SFace, MobileFaceNet ou
ArcFace/ResNet50** produisent ensuite une signature numérique du visage. Comparer
ces signatures avec celles de la galerie permettra de proposer un nom, ou de
répondre inconnu/incertain. Le modèle ne connaît pas les noms des proches.

Ces noms ne sont pas tous au même niveau : MobileFaceNet désigne une architecture
légère, ArcFace et SFace des objectifs d'apprentissage. Le modèle SFace distribué
par OpenCV utilise lui-même une architecture MobileFaceNet ; la comparaison ci-dessous
porte sur trois jeux de poids précis, pas sur trois familles totalement disjointes.
[SFace OpenCV](https://github.com/opencv/opencv_zoo/tree/48b10fa505aeb51fbd127fc60a5c660040e823b7/models/face_recognition_sface),
[ArcFace](https://arxiv.org/abs/1801.07698),
[MobileFaceNets](https://arxiv.org/abs/1804.07573).

## Candidats réellement chargés

| Candidat | Poids de reconnaissance mesurés | Atouts | Compromis |
| --- | --- | --- | --- |
| SFace OpenCV | 38,70 Mo ; signature 128 valeurs | Intégration OpenCV directe, alignement officiel, taille raisonnable, licence Apache 2.0 du dossier | Ne démontre pas la meilleure reconnaissance sur nos proches ; poids plus gros que le MBF testé |
| MobileFaceNet du pack buffalo_sc | 13,62 Mo ; signature 512 valeurs | Le plus compact des trois, rapide dans ce spike, pensé pour le mobile | Les poids fournis restent limités à la recherche non commerciale ; compromis de capacité à vérifier sur cas difficiles |
| ArcFace ResNet50 du pack buffalo_l | 174,38 Mo ; signature 512 valeurs | Candidat sérieux pour privilégier la qualité ; les mesures officielles du fournisseur sont supérieures au petit pack sur plusieurs benchmarks | Empreinte et coût CPU supérieurs ; mêmes restrictions des poids ; aucune supériorité générale démontrée ici |

YuNet ajoute **232 589 octets**, sous MIT. Les tailles sont celles des graphes
ONNX de reconnaissance extraits, pas des ZIP contenant détecteur/attributs/landmarks.
Les fichiers sont dans `models/m3-02`, ignoré par Git ; sources et SHA dans le
[manifeste versionné](../../experiments/m3-02-face-evaluation/model-assets.json).

Le fournisseur indique par exemple AgeDB-30 98,23 % pour buffalo_l et 96,58 % pour
buffalo_s/sc ; ce sont ses benchmarks, pas une précision attendue dans Blamera.
Il faut distinguer la licence MIT du code InsightFace de celle des poids,
réservés à la recherche non commerciale. Leur présence dans ce spike ne constitue
pas un choix de redistribution ou de déploiement de ces poids.
[Conditions et benchmarks officiels](https://github.com/deepinsight/insightface/blob/master/python-package/docs/model_zoo.md).

## Mesures exécutées sur cet ordinateur

Windows, Intel Core **i7-1365U**, 10 cœurs/12 processeurs logiques ; Python 3.12.14,
OpenCV headless 4.13.0.92, ONNX Runtime CPU 1.31.1. Environnement isolé
`.tools/m3-02-eval`, versions exactes dans le
[fichier requirements](../../experiments/m3-02-face-evaluation/requirements.txt).
OpenCV utilise un thread ; ORT est mesuré séparément avec un puis quatre threads.
Deux échauffements, cinq répétitions, processus neuf pour chaque candidat.

| Poids / profil | Signature seule, médiane 1 thread | Détection + alignement + signature, 1 thread | Signature seule, 4 threads ORT | Chaîne complète, 4 threads ORT |
| --- | --- | --- | --- | --- |
| SFace original, ORT | 17,71 ms | 40,85 ms | 9,54 ms | 41,64 ms |
| SFace constantes explicites, ORT | 14,82 ms | 37,05 ms | 7,36 ms | 32,69 ms |
| MobileFaceNet buffalo_sc, ORT | 9,38 ms | 32,34 ms | 4,81 ms | 31,76 ms |
| ArcFace ResNet50, ORT | 308,97 ms | 257,93 ms | 303,38 ms | 362,95 ms |

La signature SFace par l'API OpenCV native, avec le même visage aligné, mesure
20,95 ms dans le profil monothread final. Elle est numériquement cohérente avec
ORT (cosinus des signatures normalisées : 1,0 sur le portrait de référence).

**Ces mesures sont exploratoires et bruitées.** Chaque colonne vient d'une boucle
distincte ; la médiane complète peut donc être inférieure à la signature isolée
lorsque la charge change, comme pour ResNet50. Le premier diagnostic ResNet50
dépassait 1,1 seconde pour la signature ; la répétition finale est nettement plus
basse. Ni charge utilisateur, fréquence CPU ni régime thermique ne sont contrôlés.
Les quatre threads ne garantissent pas une accélération. Aucun budget d'usage ou
latence du Xiaomi n'est déduit de ces petits échantillons. Chaque répétition et
son étendue sont conservées dans `results-threads1.json` et `results-threads4.json`.

La mémoire résidente après inférence est d'environ 241/167/333 Mio pour
SFace/MBF/ResNet50 dans les processus monothread. Ces valeurs incluent Python,
OpenCV, ORT, YuNet et le modèle SFace requis pour l'alignement commun ; ce ne sont
pas des mesures de RAM du seul modèle ou de mémoire sur Android.

### Contrôle du graphe SFace

Le SFace ONNX original déclare 175 entrées, dont 174 tenseurs déjà initialisés :
ORT avertit que cela limite certaines optimisations. Une copie expérimentale
retire seulement ces entrées redondantes. Tous les nœuds et tenseurs de poids
restent byte-identiques, aucune quantification ni réentraînement. Sur dix visages
alignés : erreur absolue maximale 4,53×10⁻⁶ et cosinus minimal 0,99999988.
Ce graphe dérivé est un diagnostic de portabilité/runtime ; l'intégration
provisoire OpenCV peut conserver le poids original. Les graphes MBF/ResNet50
avaient déjà une seule entrée : cet avertissement ne les explique pas.

## Contrôles de détection et de signatures

Deux portraits publics autorisés : Eileen Collins via scikit-image/NASA et John
Glenn via sa page officielle NASA. Sources, crédits et empreintes sont conservés.
Chaque portrait fournit une référence et quatre dérivés : assombrissement, flou,
basse résolution et rotation de 15°. YuNet trouve un visage dans les dix images,
deux dans leur collage et aucun dans le fond uniforme. Les pixels sont letterboxés
à 640² ; les cinq points alignent un visage de 112².

Pour contrôler les différences de poids, **les trois modèles utilisent le même
YuNet et l'alignement OpenCV**. Ce n'est pas le pipeline officiel complet
SCRFD+InsightFace et ne mesure pas les avantages éventuels de SCRFD.
SFace reçoit RGB/NCHW flottant 0–255, sa normalisation étant dans le graphe.
MBF/ResNet50 reçoivent (RGB−127,5)/127,5, d'après
l'[adaptateur officiel](https://github.com/deepinsight/insightface/blob/master/python-package/insightface/model_zoo/arcface_onnx.py).

| Poids | Plus petit cosinus positif dérivé | Plus grand cosinus négatif sur ces deux identités |
| --- | --- | --- |
| SFace | 0,465 | 0,060 |
| MobileFaceNet | 0,541 | 0,023 |
| ArcFace ResNet50 | 0,443 | 0,063 |

Huit positifs dérivés et dix négatifs sont produits, mais les clichés sources ne
sont que deux. **Cela ne classe pas la fiabilité des modèles.** Les transformations
d'une même photo ne remplacent pas des photos de séances différentes, inconnus,
ressemblances familiales ou conditions difficiles. Aucun seuil, taux FAR/TAR ou
précision de reconnaissance de proches n'est calibré. Les cosinus de modèles
différents ne se comparent pas directement comme des probabilités.

## Recommandation initiale et orientation retenue

Le premier lot PC 0.3.0 utilise **YuNet + SFace**, comme référence locale
reproductible : intégration/alignement cohérents, droits permissifs des fichiers
retenus et coût raisonnable. Il ne s'agit pas d'affirmer qu'il est le plus fiable.
Conserver le profil de modèle, la dimension et le prétraitement avec les signatures
permettra de changer de candidat sans mélanger des espaces incompatibles.

Après ce premier lot, l'utilisateur privilégie explicitement la qualité et
demande ResNet. **ArcFace ResNet50 devient le profil PC 0.4.0**, en conservant
YuNet pour repérer les visages et le même alignement de référence. Ses résultats
publiés en font un choix cohérent pour cette priorité, au prix d'un poids et d'un
temps de calcul supérieurs. Ce changement ne transforme pas le diagnostic sur
deux portraits en preuve de meilleure reconnaissance sur la galerie personnelle.
L'[ADR 0015](../decisions/0015-face-reference-model-profile.md) consigne cette orientation.

La décision finale de qualité devra privilégier les erreurs sur des images
indépendantes, surtout les faux noms attribués aux inconnus. Les gains de ResNet50
sur les cas difficiles restent à vérifier dans le périmètre d'utilisation retenu ;
sa lenteur seule ne suffit pas à l'écarter.
Le petit MBF reste intéressant si la mémoire ou le délai deviennent déterminants.

La prochaine étape utile est la validation humaine des visages proposés dans la
galerie, puis une séparation références/validation/test par séances avec des
personnes inconnues. M3-02-A peut préparer les visages et signatures confirmés ;
il ne doit pas encore présenter de noms reconnus avec un seuil générique de démo.
Le déploiement et les performances Android restent à vérifier dans M3-03/M3-04.

Code, protocole et commandes : [spike](../../experiments/m3-02-face-evaluation/README.md).
Preuves locales : `build/reports/validation/m3-02-face-evaluation/`, notamment
`downloads.json`, `verification.json`, `cpu.json`, les deux résultats finaux et
les journaux bruts. Aucun résultat issu d'une photo personnelle n'y figure.
