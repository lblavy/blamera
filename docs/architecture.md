# Frontières du socle mobile

M0-02 utilise un seul module `app`. La [décision de stack](decisions/0001-technical-stack.md)
reste la référence ; M1-03 ajoute le moteur d’inférence image décrit ci-dessous. Aucun client réseau.

- `dev.blamera.domain` : source choisie et transition immuable entre Caméra et Image,
  indépendante d'Android. Choisir une source remplace la précédente sans acquérir d'image.
- `dev.blamera.ui` : présentation Compose, choix accessible par groupe de boutons radio,
  état sauvegardé/restauré et information explicite sur les fonctions à venir.
- `MainActivity` : point d'entrée Android ; installe le contenu et gère l'affichage bord à bord.

L’import M1-02, l’analyse image M1-03 et les contours image M1-06 sont décrits ci-dessous.
M1-04 ajoute seulement l’aperçu caméra. L’analyse du flux M1-05 réutilisera les règles
pertinentes de dimensions/orientation sans imposer CAMERA au parcours d’import.
Les frontières livrées ont une implémentation réelle et sont injectables pour les tests.

Les tests JVM vérifient les transitions du domaine et le comportement visible Compose
sous Robolectric API 36, y compris la restauration d'état. Un test instrumenté est conservé
pour une éventuelle autorisation ultérieure : aucune installation de test APK ou exécution
sur le téléphone personnel n'est autorisée actuellement. Robolectric ne prouve ni comportement
de la caméra matérielle ni performance de l'inférence ; ces preuves appartiennent à M1.

## Import d'image M1-02

Le parcours image utilise Photo Picker `PickVisualMedia.ImageOnly`, sans permission caméra
ou accès global aux photos. `PhotoLoader` est sa frontière injectable ; `AndroidPhotoLoader`
lit un flux limité, contrôle le format et les dimensions, puis réduit avec ImageDecoder
avant allocation du bitmap final, sur IO. Un mutex partagé entre instances sérialise les
lectures/décodages même après recréation d'Activity : le travail natif annulé peut la dépasser.
Ce verrou coordonne une ressource mémoire ; il ne contient ni image ni état métier global.
`ImportedImagePane` conserve l'aperçu courant en mémoire ; seule l'URI confirmée entre
dans SavedState. Une demande remplacée est annulée et vérifiée avant publication du résultat.
Annulation ou échec conservent l'aperçu précédent ; une recréation relit la dernière URI
confirmée. L'import en cours non confirmé n'est pas sauvegardé et peut être abandonné
à la recréation. Une permission URI perdue devient une erreur avec nouvelle sélection possible.
Ni copie persistante de photo, ni historique, ni réseau applicatif.

## Socle PC M0-05

`pc/src/blamera_pc/app.py` présente les limites et le choix du parcours futur Personnes/Chats.
L'état du widget est géré par Streamlit ; `render` reste une seule responsabilité de présentation.
Pas de gestion de données ou de moteur ML anticipé. Les adaptations fichiers, calculs CPU
et exports seront ajoutées avec leurs tests en M3/M4. L'environnement et les mesures PC
sont indépendants du mobile et des scripts de qualité. Voir l'[ADR PC](decisions/0002-pc-foundation.md).

## Segmentation d’image M1-03 / M1-06 partiel

`dev.blamera.vision` contient géométrie, prétraitement RGB/letterbox, post-traitement
YOLO borné et extraction des frontières du masque, testables sans caméra.
`OrtModelRunner` est la frontière JNI : modèle contrôlé, CPU, ressources natives fermées.
`OnnxImageAnalyzer` adapte le bitmap hors UI avec coordination entre instances ;
`VisionAnalyzer` est injecté à l’interface pour tester les parcours sans simuler ORT.
`ImageAnalysisPane` ne conserve les résultats qu’en mémoire pour la photo confirmée.
`SegmentedPreview` utilise la même projection Fit pour image, traits et catégories.
Voir [ADR 0003](decisions/0003-image-segmentation.md) et [guide](image-segmentation.md).

## Aperçu caméra M1-04

`CameraPane` gère la demande CAMERA au clic et la présentation de l’aperçu uniquement.
`CameraAccess` sépare activation confirmée, refus/révocation et requête abandonnée ;
seule l’activation est sauvée, puis la permission réelle est relue.
`PreviewProviderLoader` est la frontière asynchrone injectable. Son adaptateur réel
`CameraXPreviewLoader` utilise ProcessCameraProvider et le sélecteur arrière.
`runPreviewSession` attend puis contrôle l’annulation avant le bind ; le handle
`CameraXPreviewProvider` observe flux/erreurs et libère uniquement son Preview.
Le cycle Compose/lifecycle n’a aucun client de capture ou d’analyse du flux.
Photo importée conservée au retour Image, réanalyse explicite possible.
[ADR caméra](decisions/0004-camera-preview.md), [preuves PC et limites](validation/m1-04.md).

## Capture et exploration — M1-07 / M1-08

L'[ADR 0006](decisions/0006-capture-analysis.md) fait évoluer l'entrée principale vers
caméra → capture ou galerie → image stable analysée automatiquement. Les descriptions
précédentes conservent le contexte des incréments historiques et du parcours secondaire.

`CaptureApp` orchestre le parcours ; `CaptureSession`, conservée en mémoire par un
ViewModel, associe image, résultat et génération de requête. Un remplacement ou un retour
invalide les réponses précédentes. La survie à une rotation ne constitue pas un historique
persistant et ne promet pas de restaurer une photo après destruction du processus.

`CameraXCaptureLoader` lie Preview et ImageCapture dans un même cadrage. La frontière
de capture fournit une photo décodée en mémoire, orientée et limitée en dimensions ; elle
ne publie pas de fichier dans la galerie. L'aperçu et ses ressources appartiennent au
binding, avec libération au départ et reprise au retour.

`ImageExploration` porte la transformation de l'image et son inverse pour le toucher.
La présentation `ExplorablePhoto` réutilise cette transformation pour les contours et
le masque sélectionné. La sélection s'appuie sur les pixels du masque dans son repère
letterbox, avec un panneau de nom et des couleurs déterministes par catégorie.

Le parcours photo utilise `createPhotoAnalyzer` et `createPhotoCategoryLabels` :
graphe compact Large, catalogue officiel de 4 585 noms, `CompactPhotoDecoder` et
isocontours partagés. `ModelFileCache` vérifie les fichiers en streaming avant de fournir
un chemin au runtime. Les actifs Large, libellés et licence sont partagés avec le
diagnostic debug sous leurs chemins historiques `benchmark/`, sans copie en double
dans l'APK. Le parcours direct garde le modèle minimal et ses libellés COCO distincts.

Le verrou `VisionExecution` reste commun aux deux modèles et au diagnostic jusqu'à
la fin effective des appels natifs. Aucun accès réseau ni recyclage d'une image encore
utilisée par l'inférence n'est ajouté. Les états de livraison et les preuves figurent
dans la [validation de cet incrément](validation/m1-capture-analysis.md).

## Adaptations 0.8.0 — direct intégré, zone et noms français

M1-10 conserve le même écran caméra pour capture et direct. Le processeur
`LiveCameraAnalysis`, les conversions de frames et les contours restent partagés.
Le groupe CameraX peut posséder Preview, ImageCapture et ImageAnalysis ; la capture
depuis le direct suspend le processeur sans détruire le binding JPEG encore utilisé.
Le départ de la caméra ferme uniquement ses use cases et invalide les réponses tardives.

L'interaction d'image appartient maintenant à la session conservée par le ViewModel.
La zone visible est projetée vers le bitmap décodé par l'inverse du viewport, avec
intersection et bornes ; une copie hors UI devient une nouvelle image de travail.
Un seul snapshot original (photo et résultats) est conservé pour « Image entière ».
Le retour restaure les résultats sans appel au modèle. L'import et le recadrage partagent
une protection mémoire ; annulation, publication et propriété du bitmap restent explicites.

Le catalogue français est une ressource statique vérifiée, indépendante du vocabulaire
anglais canonique et des poids. `FrenchPhotoLabels` vérifie sa source, son intégrité
et son schéma pendant le chargement en arrière-plan ; les noms sont ensuite consultés
en mémoire. Les diagnostics restent en anglais pour conserver leurs comparaisons.
Aucune infrastructure de traduction n'est ajoutée au téléphone.

Les anciennes pages de choix de source et d'analyse manuelle sont retirées ; les
contrats utiles sont testés sur le parcours actuel. Les preuves et la correspondance
des tests se trouvent dans le [rapport M1-10](validation/m1-photo-refinements.md).

## Historique web 0.9.0/0.9.1 — M2-01

Cette implémentation est remplacée en 0.9.2 par la remise native décrite ci-dessous.

La [décision 0007](decisions/0007-google-visual-search.md) ajoute une frontière externe
sur action explicite. `SearchImage` prépare un recadrage de l'objet sélectionné depuis
les pixels décodés de l'image courante ; un JPEG neuf et borné sert à la fois à
l'aperçu de la fiche et au transfert. La sélection seule déclenche cette préparation
locale, sans appel réseau. Le masque coloré et les métadonnées de l'import ne sont
pas intégrés au JPEG envoyé.

`GoogleLensRequest` construit le multipart ; `GoogleLensUploader` possède le client
HTTPS injectable, sans redirection automatique, et ses bornes. `GoogleResultBrowser`
valide l'adresse de résultats puis ouvre une activité de navigateur. Aucun client
réseau supplémentaire, clé ou hébergement de photos n'est ajouté. La permission
`INTERNET` sert au bouton de recherche ; l'inférence reste locale.

`ObjectSearchState` distingue préparation, envoi et résultat de recherche pour une
sélection donnée. Un changement de source ou sélection, l'annulation et le départ
invalident le travail en cours ; une réponse périmée ne doit pas ouvrir le navigateur.
L'action de recherche ne remplace ni l'image de travail ni l'original conservé pour
les recadrages. Leur conservation reste en mémoire et ne constitue pas un historique.

Ce protocole web Google n'est pas une API publique garantie. La vraie ouverture
des résultats de l'image sur le Xiaomi doit être validée manuellement, distinctement
de l'observation d'une redirection HTTP et des tests simulés du logiciel.
[Preuves et état de livraison](validation/m2-object-actions.md).

Le correctif 0.9.1 ne change que `GoogleLensRequest` : endpoint fichier direct
`/v3/upload`, seul champ `encoded_image`, paramètres publics du formulaire Chromium.
Le client annulable, l'allowlist, les pixels et l'interface restent identiques à 0.9.0.
Aucun serveur local ou JavaScript expérimental n'est livré.
[Diagnostic et preuve du correctif](validation/m2-search-fix.md).

## Remise native à Lens — 0.9.2

Après deux échecs réels du parcours web, l'[ADR 0008](decisions/0008-google-lens-share.md)
confie la recherche au destinataire Google Lens par `ACTION_SEND image/jpeg`.
`LensImageSender` résout un gestionnaire public compatible dans les seuls packages
Google admis ; il transmet un seul URI par `EXTRA_STREAM` et `ClipData`, en lecture.
Il n'ouvre pas une activité privée et ne propose pas de fallback HTTP.

`SharedSearchImageStore` publie le JPEG d'aperçu dans un cache privé borné.
`ShareFileMetadata` conserve l'échéance et la taille ; ces métadonnées ne sont pas
dans le répertoire exposé. `ShareImageFiles` contrôle le token, le chemin canonique,
la présence des fichiers et leur validité temporelle. `ExpiringSearchImageProvider`
exécute ces contrôles avant de remettre un descripteur en lecture seule et fournit
un nom générique au destinataire. Le provider est non exporté, avec grants URI
temporaires limités au fichier. Un accès expiré reste refusé après reprise du processus.

Le coordinateur protège chaque tentative par sa génération, détache les fichiers
déjà remis sans les fermer lors d'une nouvelle demande et annule les publications
périmées. `ON_STOP` après ouverture de Lens ne supprime pas prématurément son fichier.
Le quota peut évincer un ancien fichier avant le maximum de cinq minutes ; aucune
copie déjà reçue n'est rétractable. Les photos et résultats originaux restent en mémoire.

Le client HTTP, sa construction multipart et l'adaptateur navigateur sont retirés,
avec la permission `INTERNET`. Leurs anciens tests et leur remplacement sont archivés.
Les modèles, catalogues et le diagnostic demeurent inchangés.
[Preuves et limites de la validation native](validation/m2-lens-handoff.md).

## Diagnostic Gemini image et consigne — M2-05

Le [contrat Gemini](specs/gemini-image-share.md) ajoute une destination explicite
au partage existant. `ImageShareTarget` décrit Lens et l'essai Gemini ;
`ImageShareSender` remplace le sender Lens spécialisé et conserve la résolution
publique des gestionnaires et le grant de lecture d'un seul URI. La route Gemini
joint la consigne fixe à l'image ; Lens garde son envoi d'image seule.

Un seul `ObjectSearchState` possède la publication, la tentative courante et son
annulation, quelle que soit la destination. Provider, cache, autorité historique
`dev.blamera.lens-images` et expiration restent communs et inchangés. Aucun retour
de réponse Gemini, API réseau, clé, modèle ou permission supplémentaire ; seule la
visibilité du package Gemini est ajoutée au manifeste.

Dans le panneau défilant, le statut, les choix d'objets superposés et les commandes
de recadrage précèdent la fiche de partage. L'ajout d'une consigne ne doit pas
repousser ces commandes essentielles derrière la fiche ; ce point est vérifié
après préparation complète, en portrait et en paysage.

L'action initiale est « Tester Gemini ». Un intent ouvert ne prouve pas la réception
des deux éléments ; le passage au libellé final « Demander à Gemini » dépend de
l'essai utilisateur. [Preuves et statut](validation/m2-gemini-share.md).

Le retour réel 0.9.3 reçoit l'image sans consigne. La variante publique 0.9.4
ajoute la consigne à l'unique élément ClipData portant l'image, en plus d'EXTRA_TEXT,
comme le fait AndroidX ShareCompat. Lens garde cet élément sans texte. Aucun
presse-papiers système ni changement du panneau ou du stockage. Cette représentation
est vérifiée après sérialisation Parcel ; seule la recette humaine dira si Gemini
l'utilise. [Hypothèse et preuves](validation/m2-gemini-clipdata.md).
