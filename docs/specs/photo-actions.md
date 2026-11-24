# Actions sur une photo — étude UI/UX et contrat M2-09

31 octobre 2026. Version 0.14.0/code26.
Baseline : `d14c599b04e902edc36eaad37445ba98b0b7721f`.
Implémentation, revue, contrôles globaux et testeur conformes sur PC ; APK principal
installé sur le Xiaomi, recette réelle encore ouverte.

Retour suivant : l'interface est jugée meilleure, mais les boutons Cadrage/Objet
sont jugés redondants. Le [contrat 0.14.1](automatic-photo-target.md) remplace le
choix explicite décrit ci-dessous par la sélection réelle dans l'image ; le reste
de l'export, des consignes et du cycle de vie reste applicable.

## Problèmes observés et choix

| Situation actuelle | Choix retenu | Effet attendu |
| --- | --- | --- |
| Une sélection détectée est obligatoire pour ChatGPT/Lens | Source explicite Cadrage / Objet | Une plante non détectée peut être analysée avec son image visible |
| Les actions sont dispersées verticalement | Barre commune Analyser · ChatGPT · Lens | Les trois traitements sont accessibles au même endroit |
| Modèle et réflexion occupent deux champs permanents | Roue dentée ouvrant les réglages | La place revient à la photo et à la réponse |
| Reprendre et Galerie occupent le bas | Icônes dans l'en-tête, en face de Blamera | Navigation stable et zone inférieure libérée |
| Le recadrage objet peut différer du zoom visible | Même cible pour les trois actions, aperçu du contenu envoyé | Le périmètre traité est prévisible |

La préférence va à trois actions directes plutôt qu'à un menu de traitements :
elles sont fréquentes et leur nom court suffit à les distinguer. Le menu convient
aux réglages moins fréquents. Les icônes seules sont réservées à la navigation et
à la configuration ; les traitements gardent icône et texte. Cibles tactiles
d'au moins 48 dp, libellés accessibles et aide sur appui long pour les icônes.

Schéma d'intention, adapté en portrait et paysage :

```text
Blamera                         [Caméra] [Galerie]
┌──────────────────────────────────────────────┐
│             Photo zoomable                   │
└──────────────────────────────────────────────┘
[ Cadrage | Objet ]                          [⚙]
[ Analyser ]     [ ChatGPT ]     [ Lens ]
Progression, erreur utile ou réponse Markdown
```

Le retour à l'image entière reste une action de navigation conditionnelle quand
un recadrage a créé une nouvelle image de travail. Pas de bouton équivalent
dupliqué en bas. Les diagnostics restent secondaires. À largeur normale de panneau
300 dp, viser les trois actions sur une ligne ; avec police agrandie ou espace
insuffisant, permettre un retour à la ligne propre, sans découper les cibles.

## Cible commune

**Cadrage**, choisi par défaut, désigne les pixels actuellement visibles après
zoom/déplacement. Il fonctionne sans objet sélectionné, sans détection, et après
échec de l'analyse locale si la photo reste disponible. Une image entière visible
est une cible valide, mais un viewport non mesuré/invalide ne l'est pas : ne pas
confondre les deux cas sous une valeur `null`.

**Objet** est disponible avec une sélection valide ; il conserve le rectangle
d'objet avec le contexte de 5 % du partage existant. Sélectionner un masque ne
change pas silencieusement la source Cadrage. Si la source Objet perd sa sélection,
revenir à Cadrage. Changer de source invalide l'ancienne réponse/opération.

Les trois actions portent sur la même cible. Analyser relance la segmentation
locale ; un recadrage devient une nouvelle image de travail avec retour à l'original,
comme le parcours existant. Un cadrage entier permet de réanalyser l'image courante.
ChatGPT et Lens utilisent un JPEG issu du rectangle choisi : aucun contour, masque,
contrôle d'interface ou bande vide ne fait partie des pixels envoyés.

Préserver les bornes existantes : bord maximal 1 000 pixels, qualité JPEG 80,
maximum deux Mio pour les services externes. Le cadrage est exact, sans la marge
de 5 % propre aux objets. Ne jamais recycler le bitmap source de la session ou
de l'original lors du nettoyage des copies/aperçus.

## Moment du choix et cycle de vie

Figer source et rectangle au clic. Une préparation locale peut précéder l'envoi,
avec progression et annulation ; aucun envoi automatique à la sélection, au zoom,
au choix de modèle/réflexion, à l'ouverture de la configuration ou à la rotation.
Si le compte exige une reconnexion, le bouton ChatGPT ouvre le parcours explicite,
puis un nouvel appui déclenche l'envoi.

Un déplacement/zoom volontaire qui change les pixels de la cible Cadrage invalide
son ancien résultat et annule le flux associé. Comparer des rectangles de pixels
plutôt que des flottants ; ne pas invalider pour un mouvement sans changement utile.
Une rotation ne représente pas un geste : conserver la requête et le résultat
envoyés, avec leur aperçu. Le prochain appui doit recalculer le cadrage actuel,
même si le nouveau ratio d'affichage change le rectangle sans autre geste.

Nouvelle photo, retour caméra, nouvelle analyse, retour à l'original et changement
de cible rejettent les préparations/fragments tardifs. Les clés ne doivent plus
exiger une détection réussie pour la cible Cadrage. Les fichiers déjà remis à Lens
restent accessibles selon le cycle de vie du partage existant.

## Réglages et consignes

La roue dentée ouvre modèle, réflexion et actions de compte sans ajouter d'étape
obligatoire ni de case. Conserver le stockage protégé et les profils compatibles
de 0.13.0, y compris le couple modèle/réflexion lors de changements rapides.
Ne modifier aucun endpoint, scope, moyen de paiement ou mode de service.

Consigne Objet conservée :

> Identifie cet objet aussi précisément que possible à partir de l’image. Décris ses caractéristiques utiles et indique tes incertitudes. Ne présente pas une marque ou un modèle comme certain sans indice visible. Réponds en français.

Consigne Cadrage :

> Identifie aussi précisément que possible le sujet principal et les éléments visibles dans cette image. Décris leurs caractéristiques utiles et indique tes incertitudes. Ne présente pas une marque, une espèce ou un modèle comme certain sans indice visible. Réponds en français.

Le nom prédit par le détecteur n'est pas injecté dans la consigne. Les requêtes
ChatGPT contiennent le JPEG et la consigne de la cible ; réponse française en streaming,
Markdown et erreurs récupérables restent ceux du parcours validé.
Lens reçoit l'image par le partage Android existant ; l'analyse locale utilise
les pixels, sans consigne textuelle.

## Vérifications attendues

TDD sur le cadrage entier/zoomé sans détection, l'absence de marge/letterbox et la
bonne consigne, le choix de source pour les trois actions, les invalidations,
la rotation et l'export frais au prochain clic. Régressions Objet/Lens, original,
connexion, streaming, réglages persistants et préparation annulée. Captures PC
portrait/paysage/police agrandie, configuration fermée et ouverte. Revue puis
testeur indépendants et contrôles globaux avant installation du seul APK principal.
L'utilisateur ouvre et valide lui-même sur son Xiaomi ; aucun test instrumenté
ou pilotage de son téléphone n'est autorisé.
