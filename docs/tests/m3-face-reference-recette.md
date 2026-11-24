# Recette — préparer les visages de référence

M3-02-A, PC 0.4.0 / ArcFace ResNet50, disponible sur http://127.0.0.1:8501.
Les preuves du premier lot SFace 0.3.0 sont conservées dans son rapport ; celles
du [passage ResNet50](../validation/m3-02-resnet50.md) distinguent le nouvel état.
Parcours essentiel accepté le 1er novembre 2026 : l'utilisateur a préparé ses
visages et confirme les choix / mentions « Visage confirmé » conservés après
rechargement, à la suite de la résolution de l'incident de profil. Les cas
optionnels ci-dessous ne sont pas réputés tous exécutés individuellement.
Les anciennes références SFace 0.3.0 nécessitent une nouvelle préparation avec
ResNet50 ; leur disparition de l'affichage ne signifie pas la perte des photos.

1. Dans Personnes, retrouver les mêmes personnes et photos et le nom du modèle
   ArcFace ResNet50 ; aucune préparation
   ne doit se lancer seule au démarrage ou au changement de personne.
2. Sur une photo individuelle, préparer les visages puis vérifier l'aperçu proposé.
   Confirmer le bon visage : il devient une référence conservée au rechargement.
3. Sur une photo de groupe, vérifier les aperçus numérotés, choisir explicitement
   la bonne personne et confirmer ; aucun choix automatique du plus grand visage.
4. Sur une image sans visage, le résultat doit être explicite et la galerie
   continuer à fonctionner. Essayer une autre photo sans redémarrer l'outil.
5. Renommer la personne doit conserver sa référence ; réaffecter une photo à
   une autre personne doit demander une nouvelle confirmation du visage.
6. Recharger la page puis, si souhaité, relancer le serveur : mêmes choix et
   références. Refaire un choix permet de corriger une mauvaise sélection.

Les fichiers réels restent dans l'outil local. Le retour attendu concerne les
visages trouvés/manqués, le choix du bon visage, les erreurs et le délai ressenti.
La comparaison d'identité et ses seuils ne sont pas encore livrés dans cet incrément.
Les données de référence/validation/test et inconnus seront cadrées après ce retour.
