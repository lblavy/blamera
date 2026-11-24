# Recette — exporter les prototypes faciaux qualifiés

PC 0.7.0, [contrat](../specs/face-gallery-package.md).

1. Ouvrir Personnes → Évaluation. Conserver les paramètres figés et les choix du
   test final ; le bilan courant est requis. L'export ne relance pas l'évaluation.
2. Sous le bilan final, toucher Exporter la galerie. Télécharger
   `blamera-face-gallery-v1.json` dans un dossier privé hors Git.
3. Le paquet doit reprendre deux identités, cinq références retenues agrégées,
   rejet 0,30 / acceptation 0,50 / marge 0,05 et les effectifs globaux du test :
   quinze images, treize exploitables, deux non exploitables. Aucune photo ou
   ligne individuelle du corpus n'y figure.
4. Les photos / confirmations de la galerie et le gel ne changent pas. Ne pas
   modifier les seuils pour tester le bouton dans le corpus réel : l'invalidation
   et le refus d'export sont vérifiés sur bases synthétiques.

L'orchestrateur est autorisé à produire l'export local privé à partir du rapport
courant. Le fichier contient noms et prototypes personnels ; aucun besoin de le
transmettre dans la conversation. La recette d'import Android est distincte :
elle valide le paquet / stockage, pas encore la reconnaissance faciale mobile.
