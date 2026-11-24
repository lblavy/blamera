# M3-05 — labels de plusieurs visages sur une photo

Version installée : Blamera 0.18.0/code32, APK personnelle. La galerie déjà configurée
est conservée. Aucun import de JSON ou choix de mode nécessaire. L'utilisateur
ouvre et manipule l'application ; l'agent ne lance pas Blamera automatiquement.

## Parcours essentiel

1. Capturer ou importer une photo avec deux ou trois visages bien visibles,
   idéalement deux personnes enregistrées et un inconnu. Après l'analyse,
   vérifier le label de chaque visage : nom, « Inconnu » ou « Identité incertaine ».
   Il doit être près du bon visage, sans rectangle facial ajouté ; les contours
   d'objets restent ceux de l'analyse habituelle. Aucun appui sur les personnes
   n'est nécessaire pour afficher leurs labels.
2. Vérifier que le résultat d'un inconnu n'empêche pas les noms des autres.
   Noter le délai ressenti du premier groupe, puis d'une seconde analyse.
3. Zoomer, déplacer et tourner l'interface : les labels doivent suivre leur visage.
   Analyser un cadrage plus serré, puis revenir à « Image entière » : chaque label
   doit correspondre à l'image visible, sans résultat de l'ancien cadrage.
4. Remplacer la photo, puis revenir à une photo avec un seul visage : label seul
   également, sans cadre. Les actions Analyser, ChatGPT et Lens restent disponibles.

Signaler une inversion de noms, un label masqué ou mal placé, une erreur explicite
ou une lenteur gênante. Aucun besoin de transmettre les photos. Garder Blamera
ouverte et le Xiaomi connecté pour la lecture de ses seuls diagnostics agrégés,
filtrés par PID, si cette lecture est disponible.

## Limites attendues

- La reconnaissance utilise les visages visibles ; une personne de dos ou un
  visage non détecté ne reçoit pas de nom inventé.
- Un visage encodé mais peu ressemblant peut donner « Inconnu » ou « Identité
  incertaine », même pour un proche. Une erreur locale d'encodage reste explicite
  sur ce visage et ne doit pas supprimer les résultats des autres.
- Au-delà de vingt candidats, le lot doit être refusé explicitement plutôt que
  tronqué silencieusement. Ce cas n'exige pas de constituer une photo de foule
  pour cette première validation utilisateur ; il a ses tests logiciels.
- Les tests de logiciel et les fixtures publiques ne constituent pas une nouvelle
  évaluation statistique personnelle. Le pic mémoire réel reste distinct des
  instantanés PSS éventuellement disponibles dans les diagnostics.
