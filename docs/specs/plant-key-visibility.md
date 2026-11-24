# Saisie de clé et boutons cohérents — M2-10-UI-01

Version cible 0.15.1/code29. Baseline :
`b45bf335db2bc9b50c8374bceacedbd765f9ab1d`.

Retour utilisateur sur 0.15.0 : « ça a l'air de fonctionner, même après
redémarrage ». Le parcours essentiel est accepté qualitativement ; cela ne
constitue pas une mesure de précision botanique ni de protection matérielle.
L'utilisateur demande de pouvoir afficher sa clé pendant la saisie, puis le
même style plein pour Analyser, ChatGPT et Lens, jugés à tort désactivés.

## Saisie

- Ajouter une icône œil accessible « Afficher la clé » / « Masquer la clé »
  au champ existant. Seule la saisie en cours peut être affichée.
- Masqué par défaut, affichage volontaire réversible, saisie et position de
  curseur conservées au basculement. L'œil est utilisable avant de saisir.
- La clé enregistrée n'est jamais recopiée dans le champ. Après enregistrement,
  suppression ou réouverture de Configuration, le champ reste vide et masqué.
  Une recréation/rotation ne révèle pas une saisie précédente.
- Conserver désactivation durant stockage, clavier adapté, absence de correction
  automatique, stockage chiffré et absence de requête au simple changement de visibilité.
- Pas de nouvelle dépendance ; icônes vectorielles cohérentes avec les ressources
  existantes. Conserver le vrai dialogue à largeur/hauteur bornées validé en 0.15.0.

## Actions

Les trois traitements Analyser / ChatGPT / Lens utilisent le même bouton plein
et la même couleur. La différence de 0.15.0 venait du choix de ChatGPT comme
action principale ; elle ne représentait pas un état sélectionné. Conserver les
icônes, textes, tailles tactiles, retours à la ligne et règles d'activation réelles.
Ne pas uniformiser les actions de suppression/fermeture du dialogue à cette occasion.

## Diagnostics demandés

Lecture du 1er novembre vers 10:46, heure de Paris : seuls PID Blamera et tags
BlameraChatGpt/BlameraVision, huit événements autorisés conservés. Reprise de
session, catalogue HTTP200, réponse HTTP200, trois analyses CPU. Aucun événement
FAILURE dans ces événements ; ils ne prouvent pas l'absence d'erreur ailleurs.
La 0.15.0 n'émet pas d'événement botanique ni d'indicateur d'enrichissement de prompt.

Ajouter des événements locaux minimaux, de catégorie fermée : identification
botanique publiée, aucune plante, échec typé ; préparation ChatGPT avec/sans
hypothèse botanique. Pas de photo, clé, URI, URL, nom/taxon, score, compte,
consigne ou contenu de réponse. Pas de télémétrie distante, nouvelle requête,
identifiant de personne/image ou framework d'observabilité. Ne pas journaliser
un succès pour une réponse obsolète/annulée. Ces événements ne valident pas la
justesse de l'espèce proposée.

Tests TDD ciblés saisie/visibilité/cycle de vie, styles réellement identiques,
activation inchangée et innocuité des diagnostics sur fixtures. Revue puis
testeur indépendants, contrôles locaux et métriques inchangées, installation du
seul APK principal puis recette manuelle. Les logs de la version précédente
restent une preuve partielle, pas un succès botanique reconstitué.
