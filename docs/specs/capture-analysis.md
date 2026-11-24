# Capture, analyse et exploration d'une image

Spécification de travail du 28 octobre 2026, issue des choix confirmés dans la discussion.
Elle cadre les évolutions M1-07/M1-08 du [backlog](../../backlog.md) ; ces évolutions
sont implémentées et validées sur PC en 0.7.0. Le parcours essentiel est accepté
sur téléphone après le retour global sur 0.7.0 et les adaptations 0.8.0 du 29 octobre.
Les variantes complémentaires, le prolongé et le hors ligne restent suivis en M1-09.

Le retour utilisateur sur 0.7.0 est globalement positif (« le reste a l'air de
fonctionner »), avec les adaptations M1-10 ci-dessous. Il ne vaut pas validation
individuelle de tous les cas ni des budgets d'usage.

## Parcours confirmé

L'entrée commune affiche la caméra par défaut pour cadrer. Deux boutons avec texte et
icône usuelle proposent « Capturer » (appareil photo) et « Galerie » (image/photo).
Une capture ou une image choisie en galerie rejoint le même écran sur image stable.
L'analyse locale démarre automatiquement, sans appui supplémentaire sur « Analyser ».
La capture pour analyse n'implique aucun enregistrement dans la galerie du téléphone.

Pendant l'analyse, une bande horizontale balaie l'image verticalement, de haut en bas
puis de bas en haut. Le panneau inférieur affiche « Analyse en cours… ». Ce balayage
indique une activité ; il ne représente ni un pourcentage ni l'analyse effective de
chaque ligne. Il disparaît à la fin du traitement, y compris en erreur ou annulation.

Les résultats affichent les contours des objets détectés. L'utilisateur peut zoomer
et déplacer l'image ; contours, masques et zones tactiles suivent la même transformation.
Ces gestes explorent les résultats disponibles et ne relancent pas l'analyse.

Un appui sur un objet sélectionne son masque : un remplissage semi-transparent laisse
voir la photo et le panneau inférieur affiche, pour le moment, son nom de catégorie.
Un nouvel appui sur le même objet le désélectionne et retire ce remplissage. Il n'y a
qu'un objet sélectionné à la fois ; sélectionner un autre objet remplace la sélection.

Contour et remplissage utilisent une couleur stable par catégorie connue du modèle.
L'identifiant de catégorie et son libellé sont déjà disponibles dans les détections ;
cette présentation ne nécessite pas un autre modèle. Les objets d'une même catégorie
partagent la même couleur. Une palette bornée pourra réutiliser une couleur pour des
catégories différentes : aucune promesse de milliers de teintes distinctes. Le nom
reste lisible dans le panneau ; la couleur ne constitue pas une identification certaine.

## Critères à vérifier lors du développement

- Caméra par défaut, capture et galerie avec libellés visibles et icônes usuelles ;
  galerie accessible même si l'autorisation caméra est refusée.
- Même écran et même lancement automatique de l'analyse pour les deux sources ;
  orientation correcte de l'image capturée ou importée.
- Annuler le sélecteur conserve l'état précédent, sans déclencher d'analyse inutile.
- Attente visible et animée pendant le traitement ; arrêt en succès, erreur ou annulation.
- Erreurs de capture/import/analyse et résultat sans objet signalés clairement.
- Zoom/déplacement sans nouvelle inférence ; contour, remplissage et toucher alignés,
  y compris après rotation ou redimensionnement de l'affichage.
- Sélection avec remplissage semi-transparent et nom correspondant ; second appui sur
  le même objet pour désélectionner ; changement d'objet sans accumuler les sélections.
- Couleurs déterministes par catégorie pour un modèle et une palette donnés ; même
  teinte pour contour/remplissage et pour plusieurs instances d'une catégorie.
- Image et résultats toujours cohérents : une réponse tardive ne remplace pas les
  résultats d'une nouvelle image ; une nouvelle image réinitialise l'ancienne sélection.
- Retour au cadrage et nouveau choix d'image possibles, sans transmission externe ni
  sauvegarde implicite en galerie. Comportement du cycle de vie à préciser au contrat technique.

## Choix de la première implémentation et points ouverts

- En cas de masques superposés, la première implémentation permet de choisir parmi les
  objets touchés dans le panneau inférieur. Le second appui sur l'objet sélectionné
  reste réservé à sa désélection. Cette solution technique sera éprouvée à la recette.
- Le double appui pour zoomer/réinitialiser est une suggestion, non une demande confirmée.
- Opacité, palette, limites du zoom et détails visuels sont des choix d'implémentation à
  essayer ; un appui hors objet pour désélectionner reste une proposition à préciser.
- YOLOE Large est intégré au parcours photo pour cette itération d'essai, à partir des
  [mesures sur téléphone](../research/m1-yoloe-capacity-phone-results.md). Ce choix reste
  réévaluable et ne promet pas une latence complète déduite des seuls timings du diagnostic.

L'analyse en direct déjà livrée garde ses résultats et ses limites documentés. Ce parcours
ne décide ni son retrait ni un usage exclusivement photo. Les recherches et les informations
externes restent en M2, après action explicite ; le nom affiché ici est une sortie du modèle,
qui peut se tromper, et ne constitue pas encore une fiche d'information générale.

## Adaptations 0.8.0 implémentées et parcours essentiel accepté

« Analyse en direct » doit activer les contours dans l'aperçu caméra actuel, avec les
boutons de capture et de galerie toujours accessibles. L'arrêt efface les contours ;
pause, changement de source et capture arrêtent ou invalident le traitement live.
L'ancienne interface de choix de source ne fait plus partie de ce parcours.

« Analyser cette zone » relance explicitement le modèle sur les pixels visibles après
zoom/déplacement, hors bandes vides. Choix confirmé par l'utilisateur : cette
portion devient l'image de travail, centrée, avec un bouton « Image entière » qui
restaure l'original et ses résultats sans nouvelle inférence. L'original reste conservé
en mémoire pendant les recadrages successifs. L'utilisateur confirme « Tout fonctionne »
pour le direct, les noms français et l'analyse de zone avec retour à l'original sur
le Xiaomi le 29 octobre ; les recadrages successifs ne sont pas rapportés individuellement.

Les 4 585 noms de catégories sont affichés en français via un catalogue statique
aligné sur les identifiants existants. Les poids, le vocabulaire anglais de référence
et les rapports de comparaison restent inchangés. La préparation des traductions se
fait localement sur PC ; aucune traduction en ligne ou modèle de traduction sur téléphone.
Une traduction affichée peut encore nécessiter une correction lexicale.
