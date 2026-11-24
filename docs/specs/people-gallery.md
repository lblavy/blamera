# M3-01 — galerie locale des proches sur PC

1er novembre 2026. Baseline : `65bbab0d0bbb7e373239c164139ff50eede16eb7`.
L'utilisateur clôture M1/M2 et demande de commencer M3. Premier incrément :
une interface de gestion de personnes et photos réellement persistante sur PC.

## Périmètre observable

- Conserver Streamlit local, loopback, sans télémétrie, authentification ni réseau
  externe. Personnes devient utilisable ; Chats conserve sa feuille de route.
- Créer, sélectionner et renommer une personne. Nom nettoyé, 1 à 80 caractères,
  sans caractères de contrôle ; doublon normalisé refusé avec erreur utile.
  Identifiant interne stable, indépendant du nom ou d'un chemin fourni.
- Importer plusieurs fichiers JPEG/PNG/WebP depuis le sélecteur local. Aucun
  parcours automatique de dossiers personnels, connecteur ou transfert distant.
  Import déclenché par bouton, compte rendu par fichier et garde contre réimport
  au simple rerun de Streamlit. Pas plus de 20 fichiers par lot, 20 Mio par fichier
  et 24 millions de pixels décodés. Refuser fichiers invalides, images animées et
  dimensions excessives avant stockage ; traiter les autres fichiers du lot.
- Normaliser l'orientation EXIF, convertir en RGB et conserver les pixels dans
  une image normalisée sans EXIF/GPS. Conserver format/dimensions cohérents ;
  dédupliquer le contenu normalisé au sein de la personne. Un même cliché peut
  être associé à deux personnes : l'affectation précise des visages relève de M3-02.
- Afficher les photos, leur nombre et les dimensions ; pouvoir déplacer une
  photo vers une autre personne pour corriger l'affectation. Si elle y existe
  déjà, refuser sans perdre l'original. Le fichier original choisi reste intact.
- Retirer une photo ou une personne avec confirmation explicite dans l'UI.
  Supprimer une personne retire ses images gérées. Pas de suppression sur simple
  changement de sélection ; aucune suppression arbitraire de fichier utilisateur.
- Retrouver personnes, noms et images après rerun, changement de parcours ou
  redémarrage du serveur. Les erreurs d'écriture/lecture sont visibles sans
  succès trompeur ni perte d'une autre entrée valide.

## Stockage et frontières

SQLite local, transactions pour les mutations et images normalisées en BLOB :
ce premier volume ne nécessite ni service, ni synchronisation fichier/DB fragile.
Chemin par défaut `pc/data/gallery.sqlite3`, déjà ignoré par Git ; une variable
`BLAMERA_DATA_DIR` permet une racine distincte et l'isolation des fixtures de test.
Schéma versionné, schéma inconnu/corrompu refusé sans écrasement automatique.
Répertoires créés seulement dans la racine de données choisie. Ne pas utiliser le
nom ou le nom de fichier de l'utilisateur dans un chemin ou une requête SQL.

Séparer domaine/stockage, préparation d'image et présentation. Réutilisation future
par M4 possible, sans framework générique prématuré. Aucun visage, embedding,
reconnaissance, entraînement ni export livré ici. Aucune modification Android/APK
ou opération sur téléphone. Aucun secret ou fichier personnel utilisé par l'agent.

Pillow 13.0.0 est déjà installé transitivement dans le lock Streamlit ; le déclarer
directement à cette même version si son API est utilisée. Préserver uv.lock et
l'audit runtime/licences ; aucune nouvelle dépendance coûteuse ni framework ML.
Pas de journalisation de photos, noms, contenu de base, chemins personnels ou EXIF.

## Validation et livraison

Coder → revue indépendante → corrections utiles → testeur sur état stable.
TDD réel avant comportement : CRUD/persistance, noms, transactions/erreurs,
imports mixtes/limites/orientation/métadonnées/doublons, suppression confirmée,
réaffectation et rerun sans réimport. Fixtures synthétiques temporaires uniquement.
Ne pas affaiblir les anciens tests : faire évoluer leurs attentes de feuille de
route vers le nouveau comportement autorisé. AppTest et HTTP localhost, puis
recette navigateur réelle ; distinguer ce qu'AppTest ne sait pas automatiser.

Commandes connues : `pc/.venv/Scripts/python.exe -m unittest discover -s pc/tests -v`
(adapter PYTHONPATH/cwd au lanceur), `scripts/quality.ps1 -BaseRef <baseline>` ;
Ruff, mypy strict, coverage.py lignes/branches par composant et nouveau code ≥80 %,
cyclomatique ≤10, cognitive ≤15, duplication nouvelle ≤3 %, audits inchangés.
Ne pas ajouter d'exclusion, réduire les seuils ni mettre de données réelles en Git.

Fournir preuves Red/Green et métriques réelles, commande de lancement, guide bref
et recette. L'orchestrateur tient backlog/clôture M1-M2 ; le coder possède les
sources/tests PC et adaptations minimales de configuration/lock. Un serveur local
peut être laissé disponible pour la recette utilisateur après les tests ; son
arrêt doit viser seulement le processus créé pour ce ticket.
