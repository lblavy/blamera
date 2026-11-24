# M3-04 — essai de reconnaissance sur le Xiaomi

Version installée : Blamera 0.17.0/code31, APK personnelle locale. La galerie de
deux prototypes, calculés à partir des cinq références évaluées, est intégrée
à la mise à jour ; les photos de référence ne sont pas embarquées.
Aucun fichier JSON à transférer ni import à effectuer. L'application conserve une
galerie déjà enregistrée ; à défaut, elle initialise son stockage chiffré avec
la galerie fournie.

L'utilisateur ouvre et manipule Blamera lui-même. L'agent peut uniquement mettre
à jour l'APK principal et lire les diagnostics de Blamera filtrés par son PID.

## Parcours essentiel

1. Ouvrir Blamera, capturer ou importer une photo avec **un seul visage visible**
   d'une personne enregistrée. Attendre l'analyse : un rectangle facial et un nom
   ou un statut d'abstention doivent apparaître, en plus des contours d'objets.
   Une pose ou qualité insuffisante peut empêcher de nommer une personne connue.
   La première analyse charge
   les modèles et effectue un autocontrôle ; les suivantes réutilisent le moteur.
2. Essayer une autre photo d'une personne enregistrée, puis une personne absente
   de la galerie. Vérifier le nom ou « Inconnu » / « Identité incertaine » : le
   système doit pouvoir s'abstenir plutôt que forcer un nom. Signaler une erreur
   et le délai ressenti, sans transmettre les photos.
3. Zoomer, déplacer et tourner l'interface : le rectangle reste sur le visage.
   Après « Analyser » sur une zone, puis « Image entière », le résultat doit
   correspondre à l'image affichée. Les actions ChatGPT et Lens restent disponibles.
4. Laisser Blamera ouverte et le téléphone connecté pour la lecture de ses seuls
   diagnostics agrégés : résultat de parité, temps et instantanés mémoire. Aucun
   nom, photo, embedding ni contenu de galerie ne doit figurer dans ces logs.

## Cas complémentaires

- Une photo sans visage ne produit aucun nom. Une personne de dos ne peut pas
  être identifiée par ce modèle facial.
- Avec plusieurs visages, Blamera demande de cadrer une personne. La reconnaissance
  simultanée appartient au ticket M3-05.
- Quitter/revenir ou remplacer rapidement la photo ne doit pas afficher un résultat
  de l'image précédente. Fermer/rouvrir conserve la galerie chiffrée.
- Un échec facial explicite ne doit pas empêcher l'analyse d'objets ou remplacer
  l'image par une erreur globale.

Les tests logiciels et les trois références publiques sur émulateur ne valent pas
validation du JNI ARM64 ou de la précision sur les photos personnelles du téléphone.
Objectifs Xiaomi : premier chargement/autocontrôle ≤20 s, analyse chaude ≤5 s,
mémoire incrémentale au pic ≤700 Mio. Les instantanés PSS des logs ne mesurent pas
ce pic ; cette métrique reste non mesurée tant qu'une preuve adaptée est absente.
