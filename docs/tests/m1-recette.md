# Recette manuelle M1 — APK 0.5.0 / code 8

Préparée le 26 octobre 2026 pour le Xiaomi 14T Pro, Android 16. **À exécuter par
l’utilisateur après mise à jour du seul APK principal. Aucun résultat matériel
n’est prérempli.** Une mise à jour directe de 0.4.0 vers 0.5.0 suffit ; le correctif des
deux paysages de 0.4.1 est inclus, sans installation intermédiaire.

Prévoir 20–30 minutes : priorité rapide 5 min, orientations/transitions 5 min,
images 5 min, prolongé 5 min, retour 2–5 min. On peut s’arrêter après la priorité.
Ne changer aucun réglage du téléphone pour cette recette. Ne partager aucune photo,
vidéo, capture d’écran ou donnée personnelle ; un retour textuel suffit.

## Préparation locale

- Ouvrir Blamera manuellement. Choisir Caméra puis « Activer la caméra » ou
  « Autoriser la caméra » ; accepter la demande Android seulement si vous le souhaitez.
- Préparer une tasse, un livre et une chaise, bien éclairés ; une personne consentante
  ou une image existante de personne peut compléter. Chat facultatif, aucun achat requis.
- Préparer deux images déjà sur le téléphone, portrait et paysage. Garder ces données
  locales ; rien n’est envoyé par Blamera. Une photo affichée sur un autre écran est
  possible, mais ne constitue pas un test représentatif d’une scène réelle.
- Fond uni/scène pauvre en objets pour le cas vide ; classe inconnue et faux positifs
  restent possibles. « Aucun objet reconnu à ce seuil » ne garantit pas l’absence d’objet.

L’analyse démarre seulement au clic « Analyser en direct ». Cadence provisoire : au
maximum une analyse démarrée par seconde, parfois moins selon le travail réel. Les
contours décrivent une image récente du flux : tenir la scène immobile 2–3 secondes
pour juger l’alignement. Un retard sur un objet qui bouge est attendu ; un décalage
persistant sur scène immobile est un défaut à signaler. Le modèle n’a pas changé :
un chat peut encore être classé chien. Aucun nom personnel ni identité n’est reconnu.

## Priorité rapide — environ 5 minutes

Remplir Résultat avec OK / problème / non testé et une phrase si besoin.

| ID | Action manuelle | Attendu | Résultat |
| --- | --- | --- | --- |
| P01 | Ouvrir Caméra et l’activer ; bouger doucement sans démarrer l’analyse | Aperçu continu, orienté correctement ; aucune analyse ni contour initial | |
| P02 | Viser tasse/livre/chaise ; cliquer Analyser en direct puis immobiliser | Catégories connues et traits jaunes issus des masques ; contours plausibles sur les objets, pas de masque plein | |
| P03 | Cliquer Arrêter l’analyse puis déplacer la caméra | Aperçu toujours mobile ; catégories et contours effacés ; bouton Analyser en direct disponible | |
| P04 | Redémarrer l’analyse, viser un fond pauvre en objets, attendre quelques analyses | Les anciennes détections sont remplacées, y compris par un résultat vide ; noter les faux positifs éventuels | |
| P05 | Choisir Image, sélectionner une photo, Analyser l’image | Import et contours image fonctionnent ; aucun contour caméra conservé | |

## Orientations et transitions — environ 5 minutes

Utiliser seulement les orientations déjà autorisées par le verrouillage actuel du
téléphone. Si un verrouillage empêche un cas, indiquer non testé, sans le modifier.
Le défaut précédemment signalé concernait le second paysage : O* a été déclaré OK
le 27 octobre 2026 ; le gabarit est conservé pour les prochains essais.

| ID | Action manuelle | Attendu | Résultat |
| --- | --- | --- | --- |
| O01 | Caméra + analyse, passer portrait → paysage gauche ; immobiliser | Interface et aperçu à l’endroit ; anciens contours effacés puis résultats alignés | |
| O02 | Passer au paysage opposé, puis revenir ; immobiliser à chaque fois | Les deux paysages fonctionnent ; pas de double rotation ni de contours de l’ancienne orientation | |
| O03 | Depuis une analyse active, mettre l’app en arrière-plan puis revenir normalement | Aperçu reprenable ; analyse désactivée après pause, résultats effacés ; redémarrage explicite possible | |
| O04 | Caméra → Image → Caméra, plusieurs fois ; activer si demandé | Photo importée conservée ; aucune accumulation ni overlay d’une autre source ; caméra et analyse réactivables | |
| O05 | Si la permission est déjà refusée/indisponible, essayer le parcours proposé | Message récupérable et Image utilisable ; ne pas provoquer une révocation ni changer les réglages pour ce test | |

Après recréation de l’Activity (certaines rotations), l’analyse repart désactivée :
cliquer à nouveau Analyser en direct. Une rotation de Display sans recréation invalide
les résultats, puis continue l’analyse avec la nouvelle géométrie.

## Images et limites du modèle — environ 5 minutes

| ID | Action manuelle | Attendu | Résultat |
| --- | --- | --- | --- |
| I01 | Importer une photo portrait puis une paysage, analyser | Bon sens, dimensions plausibles, contours dans la zone de la photo avec ses bandes éventuelles | |
| I02 | Remplacer une image, puis ouvrir le sélecteur et revenir sans choisir | Remplacement effectif ; annulation conserve l’image précédente ; pas de résultat d’une ancienne photo | |
| I03 | Tourner le téléphone avec une image importée, puis réanalyser si nécessaire | Aperçu correct ; pas de bitmap perdu ni de contours décalés persistants | |
| I04 | Observer plusieurs objets, une personne et éventuellement un chat, y compris au bord du cadre | Noter catégories erronées/objets manqués ; aucun nom personnel, aucune promesse d’exhaustivité | |

## Analyse prolongée — 5 minutes, sans cible de performance inventée

« Dernière analyse » mesure l’appel à `analyzer.analyze` après conversion/projection,
avec attente éventuelle du verrou moteur. Cela exclut conversion, arrivée de la frame,
publication et dessin : ce n’est pas la latence caméra→contours ni le temps d’inférence
seul. Noter ce chiffre descriptif sans en déduire FPS ou budget garanti.

| ID | Action manuelle | Attendu | Résultat |
| --- | --- | --- | --- |
| L01 | Caméra + analyse pendant 5 min, scène plutôt stable ; noter « Dernière analyse » au début, vers 2 min et à 5 min | App réactive ; durées descriptives en ms, pas une promesse de FPS ; noter lenteur ou interruption | |
| L02 | Noter la chauffe ressentie au début/à la fin ; arrêter l’analyse puis changer de source | Arrêt accessible, aperçu mobile puis Image utilisable ; noter stable/tiède/chaud/inconfortable et arrêter si inconfort | |
| L03 | Hors ligne uniquement si le téléphone est déjà dans cet état ; sinon marquer non testé | Les deux analyses restent locales ; ne pas activer mode avion ni modifier le réseau pour la recette | |

Mémoire et chauffe chiffrée ne sont pas mesurées par cette recette. Les logs numériques
Blamera peuvent fournir à l’orchestrateur des échantillons PSS de son propre processus
après lancement manuel, si le téléphone est reconnecté et dans le cadre autorisé ;
ce n’est pas un pic mémoire. Aucun log système global ni contenu d’image n’est requis.

## Fiche de retour compacte — 2 à 5 minutes

- Version testée / date : 0.5.0 / …
- Priorité P01–P05 : …
- Deux paysages O01–O02 : … / non testable avec verrouillage actuel
- Pause/reprise et changement de source : …
- Images portrait/paysage, remplacement/annulation : …
- Alignement sur scène immobile : …
- Catégories erronées ou objet manqué (texte seulement) : …
- Durées affichées début / 2 min / 5 min : … / … / … ms
- Chauffe début / fin, réactivité : …
- Hors ligne déjà actif : testé / non testé
- Problème reproductible : ID … ; actions … ; attendu … ; observé …

Le gabarit ci-dessus reste vierge pour un prochain essai. Les résultats réellement
déclarés le 27 octobre sont séparés ci-dessous ; ne pas extrapoler un protocole complet.
## Résultats déclarés — 27 octobre 2026, version 0.5.0

Retour textuel humain ; aucune photo reçue ni lue, aucun test téléphone automatisé.
Les OK ci-dessous reprennent une déclaration par groupes, sans reconstruire des
observations détaillées pour chaque action. Les tableaux de procédure restent vierges.

| Cas | Résultat réel déclaré et limites |
| --- | --- |
| P01–P05 | « P* ok » : parcours rapide OK déclaré, avec les limites de reconnaissance ci-dessous. |
| O01–O04 | « O* ok » : deux paysages, pause/reprise et changement de source OK déclarés. |
| O05 | O* OK déclaré ; condition refus/révocation non détaillée. Aucun retrait de permission OS déduit. |
| I01–I04 | « I* ok » : parcours image OK déclaré ; catégories parfois erronées, objets manqués ; contours jugés bons. |
| L01 | Incomplet : moins de 5 min, durée exacte et durées affichées non renseignées. Aucun essai prolongé validé. |
| L02 | Pas de chauffe ressentie sur cet essai court, « semble ok ». Pas de mesure thermique ni preuve de chauffe durable/stabilité prolongée. |
| L03 | Hors ligne non renseigné ; état réseau non précisé. |

Observations : image d’une bouteille classée brosse à dents, arbre non détecté,
objets manquants dans des scènes riches. « les contours semblent bon » et
« les contours sont ok mais pour du mouvement le rafraichissement est lent ».
Ces observations qualitatives ne donnent ni précision statistique ni mesure de latence.

M1-04 est clos sur les preuves PC et les deux paysages déclarés OK ; la réserve O05
reste explicite. M1-05/M1-06 restent ouverts pour fluidité/performance, M1-01 pour
budgets/prolongé et M1-09 pour le prolongé et le hors ligne. M1 n’est pas entièrement clos.
Voir les suivis D-M1-01 et D-M1-02 dans le [backlog](../../backlog.md) et les
[limites exactes de la version](../validation/m1-05.md).
