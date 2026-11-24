# Recette — galerie locale des proches

M3-01, version PC 0.2.0 livrée, revue et validation indépendantes conformes.
Après la recette proposée, l'utilisateur confirme « ok ça a l'air de marcher ».
Le parcours essentiel est accepté ; ce retour ne déclare pas individuellement
exécutés tous les cas ci-dessous. Le navigateur avait aussi été testé avec une
fixture synthétique, indépendamment de ce retour.
Android reste en 0.15.1, indépendant de cet incrément.

Lancer depuis la racine : `./scripts/pc.ps1 Run`, puis ouvrir
`http://127.0.0.1:8501`. Données par défaut dans `pc/data/gallery.sqlite3`, hors Git.
Les tests utilisent une racine temporaire et des images synthétiques ; l'utilisateur
choisit ensuite ses propres fichiers dans l'interface, sans les envoyer dans le chat.

1. Choisir Personnes et créer deux identités. Vérifier qu'un nom vide ou déjà
   présent est refusé, et que renommer conserve les photos et la sélection.
2. Importer un petit lot JPEG/PNG/WebP, incluant si possible une photo orientée
   par EXIF. Vérifier aperçus, orientation, dimensions et nombre de photos.
3. Importer une image déjà présente puis provoquer un simple rerun/changement de
   sélection : aucun nouveau doublon ne doit apparaître. Un fichier invalide du
   lot est signalé sans empêcher les autres fichiers valides d'être importés.
4. Réaffecter une photo à l'autre personne ; vérifier les deux compteurs. Un
   conflit de doublon doit laisser la photo originale intacte.
5. Annuler une suppression de photo, puis la confirmer. Répéter pour une personne
   créée pour le test : aucune suppression sur simple sélection ou annulation.
6. Passer dans Chats puis revenir dans Personnes. Chats affiche toujours la feuille
   de route, tandis que les personnes et photos sont conservées.
7. Recharger le navigateur, puis arrêter et relancer le serveur local : mêmes
   personnes, noms et images. Les fichiers originaux choisis restent inchangés.

Les limites de taille, corruption de base et erreurs d'écriture sont validées
avec fixtures isolées sur PC ; ne pas endommager une vraie galerie pour la recette.
La reconnaissance des visages et le calcul des embeddings relèvent de M3-02.
