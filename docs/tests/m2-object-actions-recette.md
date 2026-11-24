# Recette — fiche d'objet et recherche Google par image

Correctif actuel : **0.9.2/code 15**, partage Android natif vers Google Lens.
Revue indépendante sans blocage ; qualité globale et package conformes sur PC.
Testeur indépendant conforme sur 35 tests ciblés et huit tests du lecteur. Mise à
jour du seul APK principal réussie le 29 octobre 2026, sans lancement automatique.
En réponse à la recette proposée, l'utilisateur répond « ça a l'air
de marcher » le 29 octobre. W02 est accepté au niveau du parcours essentiel ;
ce retour global ne décrit pas chaque variante W01–W07 séparément, notamment
le détail de la conservation du zoom au retour. La recette ci-dessous reste la
référence de régression : Lens doit recevoir directement le recadrage visible dans
l'aperçu Blamera et afficher des résultats, sans choisir à nouveau une photo.

Historique 0.9.1 : qualité PC, revue, testeur indépendant et package conformes ;
mise à jour principale réussie le 29 octobre 2026, sans lancement automatique.
Retour humain « non, toujours pareil » : W02 reproduit l'image cassée et reste
en échec. Les autres variantes ne sont pas redéclarées individuellement.

Historique 0.9.0 :
Version : 0.9.0/code 13, qualité PC, revue, testeur indépendant et package conformes.
Mise à jour du seul APK principal réussie le 29 octobre 2026, sortie 0 « Success »,
après vérification de l'empreinte et de l'appareil cible unique. L'agent ne lance
pas l'application ; le retour humain est consigné ci-dessous.
Retour du 29 octobre : l'utilisateur juge toutes les opérations fonctionnelles
sauf la recherche, où Google s'ouvre avec une image vide. **W02 échoue** ; les autres
opérations sont acceptées globalement, sans prétendre que chaque variante W01–W07
a été exécutée individuellement. L'image est signalée comme cassée et barrée dans
la zone de recherche Google, sans résultat en dessous. Le correctif reste à valider.
L'utilisateur précise utiliser Chrome : choisir directement une image de galerie
dans Google Lens depuis Chrome fonctionne, avec vignette et résultats. Le défaut
est donc isolé au parcours de transfert depuis Blamera, sans cause technique unique établie.
L'utilisateur ouvre et manipule Blamera lui-même. Aucun besoin de partager ses photos.
La recherche actuelle ouvre Google Lens et nécessite une connexion pour obtenir
des résultats. Blamera ne publie plus elle-même l'image sur un endpoint web.

| ID | Manipulation | Résultat attendu |
| --- | --- | --- |
| W01 | Capturer ou importer une image, attendre l'analyse puis toucher un objet. | Masque sélectionné, catégorie estimée et action de recherche visuelle avec icône. Aucun envoi ni application externe à la sélection. |
| W02 | Vérifier la zone proposée et l'indication d'envoi à Google, puis toucher le bouton de recherche. | Google Lens reçoit directement la bonne image et affiche des résultats liés à cette zone, sans image cassée ni nouvel import à faire. L'ouverture de Lens seule ne suffit pas à valider ce cas. Si le partage est indisponible ou refusé, relever le message exact. |
| W03 | Revenir à Blamera avec le retour Android. | Même image, zoom, résultats et sélection ; pas de nouvelle analyse locale déclenchée par ce seul retour. |
| W04 | Retoucher l'objet puis sélectionner un autre objet. | La désélection retire la fiche ; la fiche du nouvel objet propose ses propres pixels. Aucune recherche automatique. |
| W05 | Zoomer/déplacer, analyser la zone puis sélectionner un objet et rechercher ; revenir et toucher « Image entière ». | Recherche liée au nouvel objet ; recadrage et original utilisables au retour, avec leurs résultats. |
| W06 | Avec une fiche sélectionnée, essayer portrait et les deux paysages. | Image exploitable et actions accessibles ; texte lisible, pas de contrôle masqué. |
| W07 | Remplacer l'image, ou revenir à la caméra. | Aucune ancienne fiche ni recherche automatique liée à l'image précédente. |

L'absence d'un gestionnaire Google compatible et le refus d'ouverture sont simulés
sur PC ; aucun changement d'application par défaut ou de réglage du téléphone n'est
demandé pour fabriquer ces cas. Le provider privé remet un URI en lecture seule,
expirant au plus tard après cinq minutes ; le quota peut évincer plus tôt un ancien
fichier. Cela ne retire pas les pixels déjà ouverts ou copiés par Lens. Permissions,
pixels remis et absence d'EXIF se vérifient sur PC : on ne les déduit pas de la seule
apparence de Lens. W02 reste la preuve indispensable de réception réelle.

Retour attendu : cas essayés, anomalie et étapes, lisibilité en paysage, état de
l'image au retour. Le test de ce bouton ne clôt pas les essais prolongés/hors ligne
de M1-09 et n'évalue pas la justesse des informations retournées par le moteur.
