# Recette Android — importer la galerie faciale

Le 2 novembre 2026, l'utilisateur demande de déployer le JSON avec l'application.
Cette recette reste disponible pour l'import manuel facultatif ; elle n'est plus
un prérequis ou une validation isolée à demander. Le parcours personnel M3-04
charge la galerie fournie avec l'APK et se valide directement sur une photo.

M3-03-B, Blamera 0.16.0/code30. Logiciel validé sur PC, mise à jour installée sur
le Xiaomi le 2 novembre 2026 ; recette matérielle en attente.
Le [contrat du paquet](../specs/face-gallery-package.md) est partagé avec l'export PC.

1. Après mise à jour de Blamera, copier soi-même le fichier JSON exporté
   `pc/data/exports/blamera-face-gallery.json` depuis le PC vers le téléphone. Le
   transfert reste manuel ; aucune commande ADB de copie ne fait partie du parcours.
2. Ouvrir Blamera manuellement, capturer ou importer une image, puis ouvrir la roue
   Configuration. La section Galerie faciale propose Importer une galerie.
3. Choisir le fichier exporté. Vérifier le nombre de personnes et les noms affichés.
   Une réussite remplace la galerie importée précédente, sans ajouter de doublons.
4. Fermer puis rouvrir Blamera, revenir dans Configuration et vérifier la conservation.
5. Rouvrir le sélecteur avec Remplacer la galerie, puis revenir sans choisir : les
   personnes précédentes restent affichées. Si une erreur de lecture ou de format
   est signalée, la dernière galerie valide doit rester conservée.

Ce lot installe la galerie sur le téléphone. L'affichage des noms dans la scène et
la qualification du modèle facial Android appartiennent au ticket suivant.
Signaler un message d'erreur ou des noms manquants ; aucun fichier personnel ni
empreinte faciale à envoyer dans le chat.
