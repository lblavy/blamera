# M3-04 — reconnaissance faciale sur photo Android

2 novembre 2026. Implémentation demandée après le test final PC. L'utilisateur
demande de prendre en charge le JSON et de le déployer avec l'application, sans
recette séparée de transfert/import. Le contrat de galerie reste défini dans
[le paquet v1](face-gallery-package.md).

## Livraison et données personnelles

Produire une APK locale personnelle avec les poids publics et la galerie déjà
évaluée. La galerie provient uniquement d'une propriété de build explicite qui
copie le fichier privé vers les assets générés ignorés ; aucune donnée personnelle
dans les sources, fixtures publiques ou builds génériques/CI. L'APK personnelle
contient les noms et prototypes et reste hors Git et de toute publication.

Au premier chargement, si le stockage facial est vide, valider la galerie fournie
puis la conserver dans le stockage chiffré exclu des sauvegardes. Conserver une
galerie existante ; ne pas remplacer silencieusement un import manuel ou un
stockage invalide. Aucun choix de fichier ou case technique n'est nécessaire
pour cette mise en route personnelle. Les imports explicites restent disponibles.

Le déploiement vers le Xiaomi utilise uniquement la mise à jour de l'APK principal.
L'utilisateur lance l'application lui-même. Aucun transfert ADB de fichier,
pilotage, lecture de stockage privé ou test APK sur ce téléphone.

## Chaîne à reproduire

Conserver les poids, paramètres, sous-ensemble et prototypes associés au gel PC :
YuNet avec letterbox noir 640, cinq points, alignement 112, ArcFace ResNet50
RGB `(pixels−127,5)/127,5`, normalisation L2, puis comparaison cosinus.
L'analyse faciale est indépendante de la détection des silhouettes YOLOE.

La voie retenue à qualifier est OpenCV Android de la même branche 4.13 que
le PC. L'équipe OpenCV distribue un AAR officiel via Maven Central et documente
son chargement dans le [guide Android 4.13](https://docs.opencv.org/4.13.0/d5/df8/tutorial_dev_with_OCV_on_Android.html).
L'API Java expose [FaceRecognizerSF.alignCrop](https://docs.opencv.org/4.13.0/javadoc/org/opencv/objdetect/FaceRecognizerSF.html),
utilisé sur PC. Réutiliser cette opération est une hypothèse de réduction des
écarts de prétraitement, pas une preuve de parité numérique entre plateformes.
La [distribution officielle Android](https://opencv.org/opencv4android-usage-models/)
décrit également le SDK et la compilation personnalisée comme alternatives.

Vérifier l'artefact exact, son empreinte, ses licences, sa compatibilité avec le
build Android actuel et son coût réel en taille/mémoire. Aucun changement de
poids, de profil ou de seuil implicite.
Le runtime ONNX déjà présent reste une alternative si la voie OpenCV présente
un problème démontré ; il faudrait alors qualifier aussi le décodage YuNet et
l'alignement reproduits.

## Incrément observable

- Sur capture ou import photo, avec une galerie importée compatible, analyser
  les visages localement et afficher un cadre facial et nom, inconnu ou identité
  incertaine sur l'image. La galerie fournie avec l'APK remplit ce prérequis.
- Commencer avec une personne cadrée, les silhouettes sans visage exploitable
  et une galerie d'une seule personne ; ne jamais forcer un nom. En présence de
  plusieurs visages, ne pas inventer une association. L'extension simultanée
  reste conditionnelle au ticket M3-05.
- Préserver zoom, sélection et résultats d'objets. Un changement de photo ou de
  galerie annule la publication des anciens résultats ; sérialiser les accès au
  moteur natif et libérer ses ressources avec le cycle de vie. Le retour Image
  entière doit retrouver des coordonnées et une identité correspondant à l'image.
- Conserver la galerie valide et les autres analyses en cas d'échec facial.
  Aucun transfert automatique d'image, nom ou empreinte vers un service externe.
- Prévoir explicitement le provisionnement manuel/local des poids et les
  erreurs de poids absents, altérés ou incompatibles. Les modèles de cette livraison
  sont copiés depuis les fichiers locaux vérifiés dans les assets générés.

## Qualification avant validation utilisateur

Séparer les tests logiciels déterministes et la comparaison réelle des moteurs.
Utiliser des fixtures publiques autorisées, conservées hors des données privées,
avec provenance et conditions d'utilisation. Références PC et embeddings attendus
générés hors Git, dans `build/fixtures/m3-04`, puis embarqués dans les assets
générés du diagnostic. Aucun visage personnel dans ces références.

Tolérances fixées avant mesure : coordonnées des boîtes et points ≤1 pixel
d'écart absolu ; erreur moyenne absolue de l'alignement RGB ≤1,0 sur l'échelle
0..255 ; cosinus entre embeddings PC/Android ≥0,9999 ; écarts des scores de
comparaison cosinus aux prototypes ≤0,002
et mêmes décisions sur les cas publics attendus. Norme L2 à tolérance 0,001.
Un écart doit être analysé, pas absorbé en assouplissant ces seuils.
Ne pas ajuster la politique 0,30 / 0,50 / 0,05 sur les quinze photos finales
déjà exposées.

Les tests PC et Robolectric couvrent contrats, décisions, amorçage, annulation,
erreurs et UI. L'émulateur Android déjà installé peut compléter la preuve native,
dans un AVD créé sous le workspace et avec un numéro de série explicitement
ciblé ; aucun test instrumenté n'est dirigé vers le téléphone personnel.

Ils ne prouvent pas le JNI ARM64 ni ses performances sur Xiaomi. Au premier usage
de chaque instance du moteur, un autocontrôle public doit comparer les moteurs
avant de publier un nom. La même instance conserve son résultat ; une nouvelle
instance se qualifie à nouveau, sans registre global retenant les anciens moteurs.
Un échec déterministe de parité désactive cette instance avec un état explicite,
tout en gardant les autres analyses. Une annulation ou erreur de lecture transitoire
ne doit pas empoisonner la qualification. Ne journaliser que les métriques agrégées
de ce contrôle, jamais images, noms, empreintes ou contenu de galerie.

Budgets cibles sur Xiaomi 14T Pro : premier chargement et contrôle ≤20 s,
analyse suivante d'un visage ≤5 s, mémoire incrémentale au pic ≤700 Mio. Mesures
réelles et délai ressenti restent à recueillir après ouverture manuelle par
l'utilisateur ; les résultats d'émulateur ne valent pas preuve de performance
sur téléphone. Lecture des seuls diagnostics agrégés de Blamera filtrés par PID.
