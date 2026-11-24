# ADR 0008 — remettre le recadrage à Google Lens par partage Android

29 octobre 2026 — implémenté en 0.9.2, validé sur PC et installé ; parcours Lens
essentiel accepté par l'utilisateur. Ticket M2-01.

## Motif

Les versions 0.9.0 et 0.9.1 ouvrent Google avec une image cassée sur le Xiaomi.
Le protocole fichier direct a été aligné sur Chromium en 0.9.1, sans réparer le
parcours réel. L'utilisateur précise que le bouton photo de Chrome ouvre Lens,
où le choix d'une image de galerie fonctionne.

Reproduire ce parcours avec Lens comme destinataire de l'image. Ne plus faire
poster le JPEG par Blamera vers un endpoint web privé, puis transférer une adresse
de résultats à un autre client. La cause unique de cet échec n'est pas établie.

## Décision

Utiliser le contrat Android public `ACTION_SEND` de type `image/jpeg`, avec
`EXTRA_STREAM`, `ClipData` et un droit temporaire de lecture sur un seul URI de
recadrage. Résoudre un gestionnaire réellement compatible et exporté dans les seuls
packages `com.google.ar.lens` ou `com.google.android.googlequicksearchbox` ; leur
installation seule ne suffit pas. Ne pas forcer une activité privée ou un extra
interne de Google. Si aucun gestionnaire compatible n'existe, présenter une erreur
claire sans ouvrir un autre service ou retomber sur le transfert HTTP défaillant.

Le contrat de partage et les permissions URI sont décrits par
[Android](https://developer.android.com/training/sharing/send) et
[FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider).
Une implémentation primaire existante est examinée dans
[ReFra, commit 7a9bbbc9d379b36124bbcbccb60f7da86f395d7e](https://github.com/IacobIonut01/ReFra/commit/7a9bbbc9d379b36124bbcbccb60f7da86f395d7e).
Ce précédent ne garantit pas le comportement de toutes les versions Google ; la
résolution Android et la recette utilisateur restent indispensables.

## Données et confinement

Le JPEG remis est celui de l'aperçu, sans EXIF, nom de fichier personnel, catégorie
estimée ou URI de l'original. Il reste limité à 2 Mio, 1 000 pixels sur le côté long,
avec le même recadrage contextuel. Aucun fichier n'est enregistré dans la galerie.

Le partage nécessite un fichier temporaire dans un répertoire privé dédié du cache.
Le provider est non exporté et n'expose que ce répertoire ; le grant porte sur le
seul URI, en lecture, sans écriture, préfixe ou persistance. Les chemins canoniques
restent contrôlés. Toute ouverture doit vérifier l'expiration durable et refuser un
fichier ou des métadonnées manquants, même après redémarrage du processus. Un
FileProvider standard sans ce contrôle ne suffit pas à promettre une expiration.

Retenir une durée de cinq minutes, au plus deux JPEG de 2 Mio chacun et un brouillon
borné. Cinq minutes est une limite maximale d'accès : au quota, une nouvelle demande
peut évincer le fichier le plus ancien même avant son échéance. Le nettoyage ne touche
que ces fichiers : nouvelles demandes, démarrage et
expiration quand le processus reste actif. Le contrôle d'expiration reste nécessaire
si le processus s'arrête avant le nettoyage. La limite interdit les nouvelles
ouvertures, sans pouvoir révoquer un descripteur déjà remis ou une copie par Google.

Avant remise, annulation et changement de sélection/source empêchent tout intent
périmé et suppriment les préparations abandonnées. Après ouverture de Lens, le
passage en arrière-plan ne détruit pas prématurément le fichier dont Lens peut
encore avoir besoin. Image, sélection, zoom et original restent dans la session.

## Conséquences

Le réseau appartient à Google Lens. Blamera retire son client HTTP de recherche et
sa permission `INTERNET`, tout en gardant ses modèles locaux et son diagnostic.
Pas de nouvelle permission audio, médias, stockage ou liste générale des applications.
Les queries de résolution sont limitées aux deux packages Google. Aucun logiciel
supplémentaire n'est installé sur le téléphone par l'agent.

La recherche peut afficher l'interface native Lens plutôt qu'un onglet Chrome ;
cela correspond au parcours fonctionnel décrit par l'utilisateur. L'absence ou le
refus d'un destinataire est une erreur récupérable. Après installation et essai
demandé, l'utilisateur répond « ça a l'air de marcher » : parcours essentiel accepté.
Ce retour ne garantit pas tous les gestionnaires Google ni chaque variante de la recette.
