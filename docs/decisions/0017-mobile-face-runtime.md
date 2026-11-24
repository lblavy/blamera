# ADR 0017 — chaîne faciale locale Android et galerie fournie avec l'APK

2 novembre 2026, M3-04. L'utilisateur demande la reconnaissance sur photo et
la prise en charge du fichier de galerie par l'agent, avec déploiement dans
l'application. Il ne souhaite pas une recette séparée de manipulation du JSON.

## Décision

Conserver la chaîne qui a produit les références PC : YuNet 640 avec letterbox
noir, cinq repères en coordonnées source, `FaceRecognizerSF.alignCrop` en
112×112, puis ArcFace ResNet50 RGB NCHW `(pixels−127,5)/127,5` et normalisation
L2. SFace sert uniquement à l'alignement ; sa méthode `feature` ne remplace pas
le réseau ResNet50. Les poids et paramètres restent ceux de
[`config/face-models.json`](../../config/face-models.json), sans réentraînement.

Utiliser l'AAR officiel `org.opencv:opencv:4.13.0`, sur CPU. Cette branche est la
même que celle du runtime Python 4.13.0.92 ; cela limite les divergences de code
mais ne prouve pas une égalité numérique entre processeurs ou plateformes.
Les API Java officielles exposent
[`FaceDetectorYN`](https://docs.opencv.org/4.13.0/javadoc/org/opencv/objdetect/FaceDetectorYN.html)
et [`FaceRecognizerSF.alignCrop`](https://docs.opencv.org/4.13.0/javadoc/org/opencv/objdetect/FaceRecognizerSF.html).
La [distribution Android officielle](https://docs.opencv.org/4.13.0/d5/df8/tutorial_dev_with_OCV_on_Android.html)
décrit l'intégration Maven Central.

Désactiver explicitement Winograd pour le réseau ResNet50 (`Net.enableWinograd(false)`).
Cette option CPU évite la rétention de grands poids transformés. L'essai borné
sur deux processus frais de l'émulateur x86_64, avec exactement les mêmes
références et tolérances, a réduit le delta PSS instantané de 887 038 à 457 733 Kio
et le chargement / autocontrôle de 14 696 à 3 952 ms. Le cosinus PC / Android
reste 0,9999999999917017, les boîtes, repères et pixels alignés sont identiques.
Cette observation motive le réglage sans changer de modèle. Elle ne constitue
ni une mesure de pic mémoire ni une validation des performances du Xiaomi.

L'[artefact 4.13.0 sur Maven Central](https://repo.maven.apache.org/maven2/org/opencv/opencv/4.13.0/)
est téléchargé et son SHA-256 comparé à celui publié : 121695690 octets,
`a8c9b0929d8d6367bb476320a1f36587db2b57ea18d8f7521ce509514903ca04`.
L'empreinte est épinglée dans
[`config/face-runtime.properties`](../../config/face-runtime.properties) et
vérifiée sur l'AAR résolu avant build. Le POM déclare Apache-2.0 ; les conditions
des poids restent documentées séparément dans le manifeste des modèles.

Inspection locale de l'AAR : minSdk21, quatre ABI disponibles ; les segments
PT_LOAD de `libopencv_java4.so` et `libc++_shared.so` ARM64 sont alignés à 16384
octets. L'APK personnelle reste ARM64 ; un paramètre de build explicite cible
x86_64 pour l'émulateur PC. Aucune stratégie `pickFirst` ne masque un conflit
de bibliothèque native.

## Frontières et cycle de vie

Le moteur reçoit un instantané RGB borné, indépendant d'Android Bitmap. Il
retourne aucun visage, plusieurs visages ou un visage avec boîte source et
embedding. Le premier périmètre reconnaît une seule personne cadrée. Plusieurs
visages ne conduisent pas à choisir arbitrairement un candidat ; l'extension
simultanée reste M3-05.

Le chargement est paresseux, hors du thread UI, puis les appels natifs sont
sérialisés. La fermeture du moteur ne bloque pas l'UI : une inférence en cours
finit de disposer ses ressources sur son worker, sans publier de résultat périmé.
Les objets Mat sont libérés explicitement. L'API Java OpenCV de Net,
FaceDetectorYN et FaceRecognizerSF n'offre pas de fermeture publique : leurs
références sont détachées à la fermeture, puis leurs finalizers amont libèrent
les pointeurs natifs. Aucune réflexion ou double destruction de pointeur ne
contourne cette limite du SDK.

## Provisionnement personnel

La tâche `prepareFaceAssets` appelle le module PC testable
`blamera_pc.face_mobile_assets`. Elle exige le Python autonome du projet, les
poids préalablement provisionnés et les références publiques de parité générées.
Elle vérifie tailles / SHA, profil / tolérances et contenu du seed avant de
publier le dossier généré `app/build/generated/faceAssets`. Les chemins de
staging / sauvegarde / sortie sont bornés à ce dossier généré et vérifiés avant
toute suppression ; les sources et la galerie PC ne sont jamais modifiées.

Le paramètre local explicite `-PfaceGalleryPath=...` ajoute la galerie personnelle
dans `faces/bootstrap.json`. Il n'existe aucun chemin personnel par défaut dans
les sources. Une préparation générique retire aussi un éventuel ancien seed du
dossier généré. Poids, références numériques et galerie restent hors Git.
L'APK personnalisée demeure un artefact local privé, non publié.

Le code de mise en route charge cette galerie fournie uniquement lorsque le
stockage de l'application est vide. Une galerie importée existante n'est pas
écrasée silencieusement. Le stockage chiffré et le format validé restent ceux du
[contrat de galerie v1](../specs/face-gallery-package.md).

## Preuves nécessaires

Les tests logiciels couvrent transformations, sorties invalides, normalisation,
annulation et cycle de vie aux frontières. Le JNI Android nécessite une preuve
native distincte : émulateur PC isolé explicitement ciblé, puis lancement manuel
de l'APK principale sur Xiaomi. Aucun test APK ni pilotage du téléphone personnel.

Les fixtures publiques figées couvrent un portrait, une image vide et plusieurs
visages. Tolérances fixées avant mesure : boîtes et repères ≤1px, MAE RGB aligné
≤1,0 sur 0..255, cosinus des embeddings ≥0,9999 et delta de score aux prototypes
≤0,002. Les embeddings / images ne sont jamais imprimés dans les diagnostics.
Un échec de cette vérification désactive la reconnaissance sans invalider les
analyses d'objets ni modifier la galerie ou les seuils.

L'autocontrôle porte sur chaque nouvelle instance de moteur natif, avant sa
première publication. Une instance conserve son noyau après réussite ; une
incompatibilité déterministe (nombre de visages ou comparaison numérique) reste
désactivée dans cette instance, avant tout nouveau chargement de noyau natif.
L'annulation ou une erreur de copie I/O ne constituent pas un verdict négatif
définitif. Ce périmètre évite qu'un registre global valide implicitement un
nouveau noyau, dont les wrappers JNI ont leur propre cycle de vie.

Les objectifs matériels initiaux sont chargement à froid ≤20s, traitement chaud
≤5s et mémoire supplémentaire ≤700Mio. Ils restent à mesurer sur le Xiaomi ;
ni les tests PC ni les temps d'émulateur ne constituent cette preuve.
