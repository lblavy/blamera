# M3-02-B-02 — figer la calibration et ouvrir le test final

1er novembre 2026, baseline `83d3645`, cible PC 0.6.0.

Après lecture du rapport et examen autorisé des quatre images incertaines, une
politique candidate est retenue sur la seule calibration : rejet 0,30, acceptation
0,50, marge 0,05. Elle donne neuf bonnes identifications sur dix proches et cinq
rejets corrects sur cinq images d'une même personne inconnue. Un proche reste
incertain. Ce petit lot ne démontre pas la fiabilité en population ; le lot final
n'a fourni aucun score pour ce choix. Photos, noms, empreintes et rapports privés
restent hors Git. La galerie de références est conservée.

## Gel et évaluation

- Exiger un rapport de calibration courant et un lot final non vide avant le gel.
- Persister politique, profil, agrégation, sélection et état des références,
  sources et annotations du corpus, ainsi que préparation de la calibration.
- La préparation des cibles du Test final peut suivre le gel. Aucun visage n'est
  choisi automatiquement, même lorsqu'un seul candidat existe. Un cas préparé
  sans visage reste non exploitable et compte dans le dénominateur.
- Depuis PC 0.6.1, un sujet attendu absent des candidats peut être déclaré
  explicitement non détecté. Ce constat appartient à la préparation du cas,
  pas à son identité attendue ; aucun autre visage n'est forcé. Il est conservé
  dans le dénominateur et son motif est distinct de l'absence de toute détection.
  Le marqueur est lié aux pixels / révision, annotation, profil et génération
  courants. Changer la préparation, la cible ou confirmer un visage invalide
  l'ancien constat ; son annulation exige de choisir à nouveau un visage.
- Tout autre changement pertinent invalide le gel courant. Aucun gel automatique.
  Les choix/détections du test ne rendent pas le rapport de calibration obsolète.
- Ouvrir les prédictions uniquement sur action explicite après gel et confirmation
  de tous les cas ayant des candidats. Une préparation partielle bloque le rapport.
- Conserver séparément rapports de calibration et de test, par image et identité,
  avec nombres de bons noms, faux noms, inconnus rejetés, incertains et inexploités.
- Le rapport final conserve l'empreinte complète, y compris préparation des cibles,
  et l'instantané du gel. La relecture / nouvelle action à état identique restitue
  le même résultat, sans nouvel essai ni écrasement d'historique.
- Après exposition, une modification de préparation ne réécrit pas le test ; le
  rapport historique devient non courant. Réutiliser un pixel déjà évalué dans
  un autre contexte de gel est refusé, même après retrait / réimport. Il faudra
  un nouveau lot indépendant, pas régler les seuils sur le résultat final.

## Preuves et limites

TDD sur corpus synthétiques : prérequis, persistance / reprise, préparation
partielle, zéro visage, invalidation par politique / profil / références /
annotations, séparation des rapports, refus de réutilisation et erreurs d'archives.
AppTest valide le gel avant préparation, le parcours final et son invalidation.
Les deux bases sont lues sous verrous cohérents, dans l'ordre galerie puis
évaluation ; seule la base d'évaluation reçoit des écritures.

Gates PC inchangées : `scripts/quality_pc.py --base-ref 83d3645`. Aucun modèle,
poids, dépendance ou composant Android modifié. L'orchestrateur applique les
paramètres retenus au corpus privé après revue / validation. L'utilisateur
confirme les cibles détectées parmi les quinze photos réservées. Après signalement
humain, deux cas sont non exploitables : sujet attendu absent des candidats et
aucun visage détecté. Aucun n'est remplacé ou exclu ; treize confirmations sont
conservées. Résultats réels en attente.
