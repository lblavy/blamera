# M3-05-QA-01 — refus d'accès au remplacement atomique

2 novembre 2026. La qualification globale a rencontré deux échecs sur le PC
Windows, à sources identiques : erreur de persistance assainie dans un test de
session ChatGPT, puis `AccessDeniedException` sur le remplacement atomique du
rapport de benchmark. Les essais sont archivés, pas considérés conformes.

Le second cas est établi : `Files.move(last.json.pending, last.json)` lors d'une
seconde écriture après relecture. Flux fermés et accès sérialisés par le code ;
aucune fuite de handle propre ni cause Windows exacte démontrée. Le propriétaire
d'un éventuel verrou externe reste inconnu. La cause du premier échec reste
également inconnue ; le même mécanisme de remplacement est utilisé pour la session.

## Correctif retenu par l'orchestrateur et la revue

- Helper partagé, limité aux deux appelants : archive de benchmark et fichier
  atomique de session. Aucun changement des modèles ou du parcours de reconnaissance.
- Réessayer seulement `Files.move` après `AccessDeniedException`, sur le fichier
  temporaire déjà écrit, synchronisé et fermé. Trois tentatives maximum, deux
  pauses de 25ms. Le helper n'ouvre pas de flux et reste synchrone sous les verrous
  déjà détenus par les appelants.
- Garder `ATOMIC_MOVE` et `REPLACE_EXISTING`. Aucun effacement préalable du document
  valide, copie non atomique ou retour de succès après échec.
- Refus permanent : échec explicite après le budget, ancien document intact.
  Autres erreurs : propagation immédiate. Interruption : arrêt des nouvelles
  tentatives, statut d'interruption restauré, aucune réussite trompeuse.
- Conserver limites de taille, chiffrement, nettoyage des temporaires, coordination
  de session et exclusion des sauvegardes. Aucun message de chemin, clé, jeton ou
  contenu ajouté aux logs. Préserver l'API des appelants autant que possible.

## Preuves attendues

TDD sur un refus injecté une ou deux fois puis succès, refus permanent, autres
erreurs et interruption, avec déplacement/pauses injectables et tests déterministes.
Vérifier aussi les deux intégrations : contenu complet publié, dernier document
préservé en échec et temporaire nettoyé. Les tests existants de session/fermeture/
rotation et d'archive doivent continuer à passer sans changer leurs assertions
ou délais. Revue puis validation indépendantes avant une nouvelle globale.

Les deux échecs antérieurs restent archivés. Ce correctif traite défensivement
l'opération observée ; il ne prouve pas l'identité du processus ou la cause du
refus Windows et n'attribue pas rétroactivement le premier échec à cette cause.
Ne pas changer de réglage Windows ni désactiver un outil pour rendre la suite verte.
