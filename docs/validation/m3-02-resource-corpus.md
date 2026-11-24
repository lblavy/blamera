# M3-02-B — audit initial du corpus personnel fourni

1er novembre 2026, après livraison PC 0.5.0 (`4c91255`).
L'utilisateur fournit `resources/`, précise que les dossiers `*models` contiennent
ses images de référence et autorise leur usage local pour préparer l'évaluation.
Le dossier est exclu de Git par `/resources/` ; aucun de ses fichiers n'était suivi.
La revue et le testeur vérifient cette configuration sans lire les photos.

## Périmètre

Audit exploratoire borné, sans changement de production, import ou modification
des originaux. Lecture SQLite en URI `mode=ro` et `query_only`, uniquement sur
les métadonnées nécessaires à la liaison aux références. Aucun BLOB d'image ou
vecteur de la galerie n'est lu. Détection YuNet locale seulement, sans prédiction
d'identité ni choix automatique d'un visage.

Outils temporaires et rapport agrégé sous
`build/reports/validation/m3-02-resource-corpus/`. Détails et plan privés sous
`pc/data/import-plans/`, ignorés par Git ; aucun nom de personne, chemin de photo,
date personnelle ou empreinte biométrique n'est reproduit dans ce document.
Les outils ne sont pas promus en code de production ni présentés comme un
nouveau cycle TDD. Les 42 empreintes du logiciel validé restent inchangées.

## Résultats et limites

- 398 images potentielles après inclusion de huit fichiers JFIF identifiés JPEG.
  Onze JPEG dépassent la limite actuelle de 24 millions de pixels, dont un fichier
  `models`. Les sept fichiers `models` admissibles correspondent aux sept références
  enregistrées, par digest normalisé, sans inférence d'identité depuis les visages.
- Cinq références ont une date EXIF Original ; deux n'en ont pas. Leur seul indice
  temporel vient d'un nom de fichier de messagerie, potentiellement date d'envoi.
  Aucune annotation de séance n'existait dans l'évaluation au début de l'audit.
- Parmi les 387 en-têtes respectant les limites vérifiées, 248 ont une date EXIF
  Original. La détection et le décodage concernent 205 images, pas tout le corpus ;
  les autres ne sont déclarées ni conformes ni inutilisables.
- Deux autres dossiers sont des candidats inconnus d'après les labels fournis.
  Dans l'un, les cinq images datées détectées contiennent plusieurs visages.
  Elles peuvent être utilisées avec une sélection humaine du sujet ; le zéro de
  candidats à un seul visage ne prouve pas un manque de photos.
- Trois groupes de doublons entre sujets sont identifiés dans le sous-ensemble
  pixel audité. La déduplication globale complète n'est pas revendiquée.

Première passe : budget 180 s, 161 images traitées. Complément priorisant les
en-têtes et les références : 17,452 s dans un budget autorisé de 60 s. Les comptes
et limites complets sont dans le rapport agrégé ; les artefacts privés ne sont
ni publiés ni recopiés dans les preuves publiques du logiciel.

## Prochaine action

Mise à jour après cet audit : l'utilisateur ne connaît pas les deux dates et
choisit les cinq références datées, sans modifier sa galerie. La demande ci-dessous
est historique ; [PC 0.5.1 et la préparation des lots](m3-02-reference-subset.md)
décrivent la variante désormais appliquée. Les sept références principales restent
conservées, les deux sans date sont seulement exclues des prototypes d'évaluation.

Un document privé `reference-sessions-a-completer.md` identifie les deux références
concernées, leur position dans l'application et un lien local vers chaque fichier.
L'utilisateur doit préciser leur date / période réelle et leur contexte, ou
indiquer qu'il ne sait pas. Les dates de messagerie ne sont pas prises pour des
dates de capture sans cette information.

Aucun plan automatique n'est encore appliqué. Avant un futur import, revalider
les chemins et digests des fichiers : certains comptes de détection antérieurs
sont réutilisés par chemin dans cet audit, sans garantie d'immuabilité du corpus.
Après clarification des séances,
préparer un premier lot réduit, avec séances disjointes et personnes inconnues
dans chaque lot, puis faire confirmer les visages cibles. Le lot final reste
réservé, sans mesure utilisée pour régler les seuils. Les photos originales restent
intactes. Aucune photo supplémentaire n'est requise pour la prochaine clarification.
