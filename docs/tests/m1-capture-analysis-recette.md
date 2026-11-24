# Recette — capture, analyse et exploration

Version : Blamera 0.7.0/code 11, mise à jour principale installée le 28 octobre 2026,
sortie 0 « Success » après contrôle de l'APK. L'application n'a pas été lancée par l'agent.
Le retour global positif sur 0.7.0 puis la validation des adaptations 0.8.0 du
29 octobre acceptent le parcours essentiel de capture/import et d'exploration.
Ils ne déclarent pas chaque cas ci-dessous exécuté individuellement. Les variantes,
le prolongé et le hors ligne restent suivis en M1-09 ; voir la
[recette 0.8.0 et la portée du retour](m1-photo-refinements-recette.md).

L'utilisateur ouvre et manipule l'application. Aucun besoin de transmettre des photos.
Les seules opérations autorisées à l'agent sur le téléphone restent la mise à jour du
seul APK principal et, si nécessaire, la lecture des logs Blamera exclusivement.

Prévoir une scène simple, une photo connue, puis une scène avec plusieurs objets.
La reconnaissance peut se tromper : noter séparément erreur de catégorie, objet manqué,
contour décalé et problème de navigation. Le plafond du modèle photo reste 20 sorties.

| ID | Manipulation manuelle | Résultat à vérifier |
| --- | --- | --- |
| C01 | Ouvrir Blamera ; observer l'entrée et les commandes. | Caméra proposée par défaut ; boutons capture et galerie avec texte et icône. |
| C02 | Accorder soi-même l'autorisation caméra si souhaité, puis capturer une scène. | Aperçu fluide avant capture ; photo stable orientée ; analyse démarrée sans second appui. |
| C03 | Observer l'attente puis la fin. | Bande horizontale en aller-retour vertical ; « Analyse en cours… » en bas ; animation arrêtée une fois le résultat affiché. |
| C04 | Depuis la galerie, choisir une photo connue. | Même écran et même analyse automatique que pour la capture ; résultats correspondant à cette photo. |
| C05 | Ouvrir le sélecteur de galerie puis revenir sans choisir. | État précédent conservé ; pas de nouvelle analyse inutile. |
| C06 | Pincer pour zoomer, déplacer l'image, puis sélectionner un objet après déplacement. | Image, contours et zone touchée restent alignés ; aucun nouveau temps d'analyse provoqué par le zoom. |
| C07 | Toucher un objet, le retoucher, puis toucher un autre objet. | Remplissage semi-transparent et nom en bas ; second appui retire la sélection ; un seul objet sélectionné à la fois. |
| C08 | Observer plusieurs objets de même catégorie et de catégories différentes. | Couleur stable par catégorie, commune au contour et au remplissage ; nom lisible même lorsque des catégories partagent une couleur. |
| C09 | Tester des objets qui se chevauchent, si la photo en contient, puis les noms proposés dans le panneau. | Choix compréhensible entre objets touchés, désélection toujours possible ; noter tout objet impossible à sélectionner. |
| C10 | Revenir au cadrage, reprendre une photo ou remplacer l'image pendant/après une analyse. | Retour utilisable ; aucun ancien contour ou nom ne s'attache à la nouvelle image. |
| C11 | Tourner dans les deux paysages et revenir au portrait ; essayer aussi pendant l'analyse. | Image et contrôles utilisables, panneau accessible, contours et toucher toujours alignés ; pas de résultat obsolète. |
| C12 | Passer en arrière-plan puis revenir, depuis le cadrage et depuis une image. | Caméra et image reprennent dans un état cohérent ; pas de blocage ou de doublon visible. |
| C13 | Essayer une photo sans objet reconnu et, si l'occasion se présente, un fichier illisible. | Message compréhensible ; possibilité de choisir une autre image ou de réessayer. |
| C14 | Sans autoriser la caméra, utiliser Galerie si ce cas est disponible naturellement. | Import et analyse utilisables. Aucun changement système n'est demandé pour fabriquer ce cas. |
| C15 | Sans réseau, répéter capture et import, puis quelques analyses successives. | Parcours local fonctionnel ; noter délai ressenti, chauffe, arrêt ou ralentissement. |
| C16 | Ouvrir le parcours secondaire d'analyse en direct et revenir. | Fonction précédente accessible ; retour au nouveau parcours sans erreur ni résultat mélangé. |

Le premier chargement peut être plus long. Pour apprécier le délai, distinguer une
première analyse des suivantes ; ne pas comparer ce temps complet directement aux
2,8 s du diagnostic qui excluait chargement, préparation et affichage.

## Retour à compléter

- Version et date :
- Cas réussis :
- Cas non faits :
- Anomalies et étapes pour les reproduire :
- Délai ressenti (première analyse / suivantes) :
- Reconnaissance et contours, par rapport aux images déjà essayées :
- Lisibilité des couleurs et facilité de sélection/zoom :
- Durée d'utilisation et chauffe ressentie :

Les constats humains et les mesures instrumentées sont consignés séparément dans
le rapport de validation. Aucun statut terminé ne sera déduit des seuls tests PC.

## Retour humain sur 0.7.0

L'utilisateur indique « le reste a l'air de fonctionner », avec trois adaptations :
réanalyser la section visible après zoom, activer le direct dans la nouvelle interface
et traduire les noms anglais. Ce retour global est conservé sans déclarer chacun
des cas C01–C16 réalisé. Le comportement de C16 redirige vers l'ancienne interface ;
sa correction et les adaptations sont suivies en M1-10 et dans la
[recette suivante](m1-photo-refinements-recette.md).
