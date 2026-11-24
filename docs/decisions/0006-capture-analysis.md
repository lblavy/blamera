# ADR 0006 — cadrer, capturer puis analyser une image stable

28 octobre 2026 — parcours implémenté, revue et validation PC conformes ; recette appareil en attente.

## Contexte

L'analyse en direct fonctionne, mais son rafraîchissement et les limites du modèle
minimal gênent l'exploration des scènes. Les essais YOLOE montrent un apport de
vocabulaire et de qualité, avec un coût qui se prête davantage à une analyse ponctuelle.
Les [mesures Xiaomi](../research/m1-yoloe-capacity-phone-results.md) ne constituent
pas une mesure du délai complet capture → affichage des résultats.

## Décision

La caméra est l'entrée par défaut. Les boutons avec texte et icône « Capturer » et
« Galerie » conduisent à un même écran d'image stable, avec analyse locale automatique.
La capture est destinée à l'analyse en mémoire ; elle ne crée pas une photo en galerie.
La galerie reste accessible lorsque l'autorisation caméra n'est pas accordée.

Pendant le traitement, une bande horizontale balaie l'image verticalement en aller-retour
et le panneau inférieur affiche « Analyse en cours… ». Cette animation indique une
activité, sans progression chiffrée fictive. L'utilisateur peut ensuite zoomer et déplacer
l'image, sélectionner un masque et consulter son nom dans le panneau.

Le masque sélectionné reçoit un remplissage semi-transparent ; retoucher cet objet
le désélectionne. Contour et remplissage utilisent une couleur stable par catégorie.
La palette peut réutiliser une teinte et le nom reste nécessaire pour identifier la
catégorie prédite. Les [spécifications](../specs/capture-analysis.md) font référence.

YOLOE26 Large PF est intégré comme modèle du nouveau parcours photo pour cette
itération d'essai. Le choix s'appuie sur les gains mesurés sur le corpus annoté et sur
la faisabilité CPU vérifiée sur le Xiaomi. Il reste réévaluable après la recette réelle ;
il ne certifie pas les catégories prédites et n'écarte pas Xlarge sur la seule latence.
Le graphe compact déjà évalué conserve ses paramètres : entrée 640² FP32, score 0,35,
NMS agnostique IoU 0,5 et au plus 20 sorties. Aucun réglage statistique nouveau n'est
déduit de ce changement de parcours.

L'analyse en direct avec le modèle minimal reste un parcours secondaire. Le nouveau
parcours ne constitue pas une décision d'abandon définitif du direct. La recherche
d'informations externes reste en M2 et exige une action explicite ultérieure.

## Conséquences à vérifier

- Caméra, import et inférence restent séparés, avec des frontières injectables pour les tests PC.
- Image, contours, masque et coordonnées tactiles partagent la même transformation.
- Remplacement, retour et rotation ne peuvent rattacher une ancienne réponse à une nouvelle image.
- L'annulation d'une attente UI n'interrompt pas artificiellement un appel natif ; les
  ressources restent possédées et les inférences sérialisées jusqu'à son retour réel.
- Le modèle et les libellés sont contrôlés par taille et SHA ; les notices sont embarquées.
  Un grand modèle est chargé par fichier contrôlé, sans copie intégrale en tableau Java.
- La qualité logicielle se valide localement ; cadrage, ergonomie tactile, délai complet,
  pression mémoire et chauffe demandent une recette manuelle sur le téléphone.

Cette décision décrit le comportement à livrer. Elle ne constitue pas une preuve de
build, d'installation ou d'acceptation utilisateur.

## Adaptation confirmée pour 0.8.0

L'utilisateur demande que le direct s'active sur l'aperçu courant, sans ancienne
interface, et que les noms soient en français. Il confirme aussi une zone recadrée
comme image de travail, avec retour à l'original. Le bouton de réanalyse traite
uniquement le cadrage visible après un geste explicite ; le retour restitue les
résultats originaux sans appeler de nouveau le modèle. Les noms français sont une
ressource statique, sans modifier les identifiants ou poids de vision.
[Validation et limites M1-10](../validation/m1-photo-refinements.md).
