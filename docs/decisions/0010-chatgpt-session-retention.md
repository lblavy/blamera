# ADR 0010 — conserver la connexion ChatGPT après fermeture

31 octobre 2026 — décision pour la version 0.12.0/code24.
Baseline : `fc6b5578a3b16aadab61ff399ff97df0f35b644d`.
Implémentation et revue conformes sur PC ; validation matérielle ouverte.
Retour humain suivant : mémorisation acceptée sur le Xiaomi. La confirmation des
crédits décrite ici est remplacée pour 0.13.0 par l'[ADR 0011](0011-chatgpt-direct-streaming.md),
à la demande explicite de l'utilisateur ; la protection du stockage reste active.

## Problème et décision

L'utilisateur juge le parcours 0.11.0 fonctionnel mais doit se reconnecter après
fermeture puis réouverture de Blamera. Il confirme que cela ne se produit pas
entre les objets d'une même ouverture. La conservation en mémoire prévue pour
l'essai M2-06 ne suffit donc plus pour le parcours courant.

Conserver une seule session du parcours principal dans le stockage privé de
l'application, chiffrée et authentifiée par AES-256/GCM avec clé Android Keystore
non exportable. Reprendre cette session et le modèle choisi à l'ouverture,
renouveler les jetons quand nécessaire, sans navigateur tant que la session reste
renouvelable. Aucun nouvel SDK payant, clé API ou achat ; les requêtes continuent
d'utiliser uniquement le plan ChatGPT autorisé.

Cette décision remplace la restriction « jetons uniquement en mémoire » du
parcours principal décrite dans l'ADR 0009 et le contrat 0.11.0. Le diagnostic
synthétique peut garder sa session éphémère séparée ; il ne doit pas concurrencer
le renouvellement du compte enregistré.

## Contrats externes et protection

La [documentation officielle des sessions](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions)
prévoit la conservation protégée, le renouvellement avec le client émis et le
remplacement atomique des jetons rotatifs. Le
[format de connexion](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)
associe identité validée, client, scopes et expiration à la même registration.
Les [durées documentées](https://developers.openai.com/siwc/token-sharing-open-source/token-reference)
ne garantissent pas une connexion permanente : refus, révocation ou expiration
peuvent toujours imposer une reconnexion explicite.

Le [Keystore Android](https://developer.android.com/privacy-and-security/keystore)
protège la clé de chiffrement ; l'[algorithme GCM recommandé](https://developer.android.com/privacy-and-security/cryptography)
authentifie aussi le fichier. Clé réservée à l'application, sans demander une
nouvelle biométrie ni modifier les réglages de l'appareil. Aucun forçage StrongBox
ou promesse de protection matérielle non vérifiée sur ce téléphone.

Stockage dans `noBackupFilesDir`, écritures atomiques, taille de texte clair
limitée à 128 Kio UTF-8 et enveloppe bornée. Sauvegarde et transfert Android sont
déjà désactivés et exclus dans le manifeste/règles. IV frais par chiffrement,
version de format et données associées fixes. En cas d'échec du Keystore ou
d'altération, aucune reprise partielle ou solution de repli en clair.

L'agent ne lit aucun jeton ni fichier privé sur le Xiaomi. Les tests utilisent
des données fictives sur PC ; le vrai Keystore et la reprise après fermeture
seront validés par l'utilisateur dans l'application installée.

## Cycle de vie et facturation

Le stockage ne contient ni image, ni consigne, ni réponse. La reprise ne déclenche
aucune inférence, seulement les opérations de compte/catalogue nécessaires.
Les parcours locaux restent utilisables si le réseau est indisponible.

Conserver l'identité vérifiée et le client avec chaque grant, sérialiser les
renouvellements de la même session et enregistrer tous les champs remplacés
ensemble. Annulation ou déconnexion ne doivent pas être suivies d'une écriture
tardive qui ressuscite la session. Fermer l'activité libère sa mémoire sans
effacer la connexion enregistrée ; « Se déconnecter » tente la révocation et
bloque toute reprise locale avant de tenter l'effacement, même si la révocation
distante échoue. Un échec d'effacement reste explicite : le blocage durable
interdit la réutilisation du fichier résiduel, sans prétendre l'avoir supprimé.

Un marqueur durable non secret dans la registration privée bloque la reprise
avant révocation/suppression. Ainsi, un ancien fichier qui ne peut pas être
supprimé ne redevient pas une connexion au redémarrage. Seule une nouvelle
connexion validée puis sauvegardée peut lever ce blocage ; le diagnostic éphémère
ne le modifie pas. Les échecs d'écriture du marqueur ou d'effacement sont signalés,
sans prétendre à une suppression physique réussie.

Une écriture disque déjà entamée peut finir atomiquement malgré l'annulation de
la coroutine. La coordination de session doit traiter ce commit : déconnexion
gagnante via verrou/marqueur, compensation d'une connexion annulée et conservation
appropriée d'un nouveau grant rotatif si seule l'analyse est annulée. L'annulation
n'est pas assimilée à l'absence de toute écriture.

Le modèle préféré est repris uniquement s'il figure dans le catalogue courant du
même compte. La confirmation humaine de désactivation des crédits n'est pas
persistée : elle repart décochée après une réouverture, mais reste acquise entre
les objets d'une même session ouverte. Blamera ne peut pas vérifier ce réglage
serveur et n'ajoute aucun achat ou repli facturé.

## Limites et suites distinctes

L'authentification initiale après mise à jour reste nécessaire : 0.11.0 n'avait
aucun jeton enregistré à migrer. La première connexion de 0.12.0 créera son
enregistrement protégé. Les photos et réponses ne sont pas restaurées au démarrage.

L'utilisateur a aussi signalé une attente longue avec GPT-6.1 Sol. Le code 0.11.0
omet `reasoning.effort`, donc utilise son défaut `medium`
([référence du modèle](https://developers.openai.com/api/docs/models/gpt-6.1-sol)).
Cette version ne modifie pas cet effort ni l'affichage du flux : comparaison
`low`/`medium` et affichage progressif relèvent d'une investigation distincte,
sans supposer une qualité identique et sans activer un mode payant.
