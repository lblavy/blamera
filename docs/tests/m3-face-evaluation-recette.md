# Recette — évaluation exploratoire des visages

M3-02-B, PC 0.5.0 disponible sur http://127.0.0.1:8501/. Parcours utilisateur à valider ; les
mesures de reconnaissance sur les photos personnelles restent à produire.

## Préparer quelques photos

Garder les références déjà acceptées. Pour le premier essai, utiliser quelques
autres photos de proches, prises à des moments différents, et des photos de
personnes absentes de la galerie. Les photos d'une même séance ou rafale doivent
porter le même nom de séance. Réserver d'autres séances pour le test final.

Le nombre proposé sert à découvrir l'outil ; il ne démontre pas un faible taux
d'erreur. Pas besoin de transmettre les photos à l'agent.

## Parcours essentiel

1. Retrouver les personnes et les références confirmées dans la galerie. Ouvrir
   Évaluation et attribuer une séance / période aux références utilisées. Plusieurs
   photos du même moment peuvent être affectées ensemble.
2. Importer un petit lot Calibration, choisir l'identité attendue et une séance
   différente des références. Importer également un cas « Personne inconnue ».
   Vérifier que les nouveaux cas ne sont pas ajoutés à la galerie de références.
3. Préparer les visages de chaque cas, choisir le bon visage et l'enregistrer.
   Avec plusieurs candidats, vérifier que le choix reste volontaire. Une photo
   sans visage doit afficher un résultat non exploitable, sans bloquer la galerie.
4. Évaluer la calibration. Le tableau montre l'identité attendue, le résultat,
   le meilleur candidat et sa similarité ; les effectifs permettent de retrouver
   chaque cas. Un mauvais nom ou un inconnu mal reconnu doit apparaître comme
   erreur, pas disparaître du rapport. Noter les cas pertinents sans envoyer d'image.
5. Recharger la page : imports, annotations, choix et rapport sont conservés.
   Changer un seuil doit rendre le rapport à recalculer. Le modifier revient à
   explorer une politique ; les valeurs initiales ne sont pas déjà calibrées.
6. Importer une image dans Test final réservé avec une autre séance. Vérifier
   qu'elle reste réservée et n'obtient pas de prédiction ni métrique de calibration.

## Cas complémentaires

- Réimporter une référence ou le même cas : le doublon normalisé est refusé.
- Utiliser une séance déjà présente dans un autre lot : la séparation est exigée.
- Corriger une identité / séance ou retirer un cas avec confirmation ; le rapport
  précédent ne doit plus être présenté comme courant.
- Modifier une référence puis revenir à Évaluation : le rapport demande un recalcul.
- Revenir à la galerie des proches : photos et confirmations déjà acceptées restent
  disponibles. Le corpus d'évaluation n'est jamais une référence implicite.

Retour utile : déroulement, erreurs / lenteur, conservation des données et exemples
de bonnes reconnaissances / faux noms / inconnus / incertains. La validation de cet
écran n'est pas une clôture de la calibration, du test final ou de M3.
