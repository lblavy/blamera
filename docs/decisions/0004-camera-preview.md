# ADR 0004 — aperçu caméra, avant analyse du flux

26 octobre 2026. **Décision retenue ; aperçu 0.4.0 installé, correctif 0.4.1 validé sur PC et prêt à installer.**
L’utilisateur confirme aperçu et transitions 0.4.0, mais signale un paysage inversé
incorrect. Le complément 0.4.1 ci-dessous a passé revue et tester ; son installation
et la validation humaine des deux paysages sont en attente, faute de téléphone connecté.
M1-04, branche locale `codex/m1-camera-preview`, version 0.4.0/code 6.

## Décision

CameraX **1.6.2**, artefacts `camera-core`, `camera-camera2`, `camera-lifecycle` et
`camera-view`, versions exactes dans le catalogue. Cette version stable est publiée
dans la [référence officielle](https://developer.android.com/jetpack/androidx/releases/camera).
`checkDebugAarMetadata` et la compilation vérifient sa compatibilité avec le SDK36
conservé ; aucun alpha, hausse SDK ou changement de modèle/runtime d'inférence.

Utiliser **ProcessCameraProvider + Preview seulement**, caméra arrière, `PreviewView`
dans Compose `AndroidView`, mode `COMPATIBLE` et `FIT_CENTER` dans une zone de320dp.
Pas de `CameraController`, `ImageCapture`, `ImageAnalysis` ou vidéo dans cet incrément.
Ce choix garde le rendu CameraX standard et permet une première validation manuelle
avant la chaîne d'analyse M1-05. Aucun pixel de caméra n'est sauvegardé ou analysé.
[Guide Preview](https://developer.android.com/media/camera/camerax/preview),
[architecture et lifecycle](https://developer.android.com/media/camera/camerax/architecture).

## Autorisation et cycle de vie

Le choix Caméra ne demande aucune permission tout seul. Un bouton utilisateur
**Autoriser la caméra**, ou **Activer la caméra** si déjà accordée, déclenche l'action.
Seule CAMERA est déclarée ; absence de micro, réseau, stockage ou accès media global.
Le refus et l'absence de dialogue possible ont des messages et laissent Image utilisable.
L'app peut expliquer le passage manuel aux réglages ; elle n'ouvre ni ne modifie ces
réglages. Les agents ne changent jamais les permissions sur le téléphone.
[Permissions Android](https://developer.android.com/training/permissions/requesting).

L'activation confirmée seule est sauvegardée pour une recréation ; la permission est
revérifiée et les requêtes en attente ne sont pas restaurées. Une réponse tardive d'une
demande abandonnée n'active pas une nouvelle vue. Chaque `ON_RESUME` relit la permission.
Le binding existe seulement au premier plan `RESUMED` ; pause, sortie vers Image et
disposition ferment la session. À la reprise, l'activation antérieure reprend si le droit
reste accordé ; une révocation revient au bouton et au message explicite.

`runPreviewSession` attend le provider puis vérifie l'annulation avant tout bind.
Annuler le future global CameraX ne constitue pas une protection : il reste partagé.
Le callback de chargement ne lie jamais la caméra ; un résultat tardif est ignoré.
Bind/unbind et observations se font sur le thread principal. Le handle idempotent retire
ses observers, déconnecte la surface et **unbind uniquement son Preview**, jamais `unbindAll`.
Une erreur de binding libère aussi le Preview créé. Les erreurs récupérables permettent
un réessai ; le nouvel essai clôt l'ancien handle avant de lier le suivant.
[API du provider](https://developer.android.com/reference/androidx/camera/lifecycle/ProcessCameraProvider).

L'état « Aperçu caméra actif » provient de `PreviewView.StreamState.STREAMING`, avec
`COMPATIBLE`, et non de `CameraState.OPEN`. Les erreurs caméra priment sur un état IDLE
ou STREAMING ancien. Un OPEN sans flux reste « Ouverture ».
[API PreviewView](https://developer.android.com/reference/androidx/camera/view/PreviewView).

## Tests et limites

Les tests utilisent les vraies classes Preview/PreviewView, états LiveData et cycles
Robolectric, avec bind/unbind et interfaces Camera simulés à la frontière publique.
La production appelle le vrai ProcessCameraProvider et le sélecteur arrière.
`camera-testing:1.6.2` n'étant pas publié dans les dépôts Maven officiels essayés, il n'est
pas conservé comme dépendance ; aucun framework de mock supplémentaire n'est ajouté.
Ces tests prouvent les transitions et la propriété des ressources, pas une surface
physique, un objectif caméra donné, l'orientation matérielle ou les performances.

L'import et l'analyse des photos restent indépendants de CAMERA. Passer à Caméra masque
leur vue et annule une analyse en cours ; revenir à Image garde la photo et permet une
réanalyse explicite. Modèle, seuils, catégories et contours image0.3.1 restent inchangés.
Le [rapport M1-04](../validation/m1-04.md) distingue preuves PC et validation humaine à venir.

## Correctif 0.4.1 — quatre orientations utilisateur et Display courant

Le manifeste 0.4.0 ne fixait pas de politique explicite et le Preview ne suivait pas
explicitement les changements de rotation du Display. Ces constats sont compatibles
avec le signalement ; la cause exacte sur HyperOS n’est pas prouvée sans nouvel essai.

Retenir `screenOrientation="fullUser"` : les quatre orientations quand l’autorotation
utilisateur l’autorise, respect du verrouillage sinon. Aucun `fullSensor`, capteur brut,
lecture/modification des réglages système ou `configChanges` d’orientation ajouté.
[Politique Activity officielle](https://developer.android.com/guide/topics/manifest/activity-element#screen).

Un écouteur possédé par le binding lit le Display du PreviewView et passe directement
`Surface.ROTATION_0/90/180/270` à `Preview.targetRotation`. Il initialise une vue déjà
attachée ou lors de son premier attachement, puis suit les changements du seul Display
concerné, notamment 90↔270 sans recréation d’Activity. Même Preview et même binding :
pas de réorientation Compose/View additionnelle, rebind, analyse ou capture.
L’écouteur et le callback d’attachement sont retirés à la fermeture et en cas d’échec
avant livraison du handle ; les callbacks tardifs sont ignorés après close.
[Rotations CameraX](https://developer.android.com/media/camera/camerax/orientation-rotation),
[API Preview](https://developer.android.com/reference/androidx/camera/core/Preview#setTargetRotation(int)).

Les tests PC vérifient ActivityInfo, Display/Preview réels sous Robolectric et les
ressources possédées. Ils ne prouvent pas les deux paysages physiques sur le Xiaomi.
[Rapport et validation restante](../validation/m1-04-rotation.md).

Évolution 0.5.0 : [ADR 0005](0005-live-camera-analysis.md) ajoute ImageAnalysis dans
le même ViewPort et réutilise ce suivi de rotation pour les deux use cases. Le présent
périmètre 0.4.0 reste historique ; le correctif 0.4.1 est inclus, sans installation
intermédiaire. Validation matérielle des deux paysages en attente.
