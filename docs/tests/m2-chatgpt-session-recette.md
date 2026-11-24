# Recette — connexion ChatGPT mémorisée

Version 0.12.0/code24 installée le 31 octobre 2026 à 15:18:01 UTC ; parcours essentiel
accepté par retour humain « ça fonctionne ». Recette historique avant simplification M2-08.
L'utilisateur ouvre et manipule Blamera lui-même. L'agent ne ferme pas l'application,
ne force pas son arrêt et ne lit aucun fichier privé du téléphone.

1. Après installation, ouvrir une photo et la fiche d'un objet. Faire une première
   connexion ChatGPT dans ce parcours, puis revenir manuellement dans Blamera.
   Cette connexion initiale est attendue : 0.11.0 ne conservait aucun jeton.
2. Choisir un modèle. Aucun envoi d'image n'est nécessaire pour tester la reprise.
   Si souhaité, vérifier les crédits désactivés, confirmer et envoyer explicitement.
3. Fermer puis rouvrir Blamera comme dans le parcours qui imposait auparavant une
   reconnexion. Ouvrir une photo et sélectionner un objet. Vérifier que le compte
   et le modèle sont retrouvés sans navigateur ni nouvelle autorisation de l'app.
4. La confirmation des crédits doit être décochée après cette réouverture. Avant
   tout envoi de photo, vérifier le réglage serveur puis confirmer ; s'il n'est pas
   vérifiable, arrêter avant l'envoi. Cette garde est distincte de la connexion.
5. Changer d'objet et revenir par la caméra : la connexion et le modèle doivent
   rester disponibles sans nouvel envoi automatique. Rotation et Markdown restent
   conformes au parcours 0.11.0.
6. Pour tester la déconnexion, toucher « Se déconnecter », puis fermer et rouvrir
   Blamera. Le compte doit rester déconnecté. Une nouvelle connexion doit rester
   possible ; elle ne doit pas réactiver l'ancienne confirmation des crédits.

Indiquer si la reconnexion a disparu, le modèle retrouvé et tout message d'erreur.
Aucune adresse, code, identifiant ou photo à transmettre. Les expirations,
révocations, erreurs réseau, fichiers corrompus et courses de renouvellement sont
simulés sur PC ; aucune modification de réglage ni manipulation par ADB n'est
requise sur le téléphone.
