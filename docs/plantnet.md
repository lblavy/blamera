# Identifier une plante avec Pl@ntNet

L'intégration M2-10 est implémentée dans Blamera 0.15.0/code28. Le parcours
combine les contours locaux YOLOE et une identification botanique distante, sans
sélection de mode. La qualité sur les plantes de l'utilisateur n'est pas encore mesurée.
Revue, contrôles globaux et testeur indépendants sont conformes sur PC. L'APK est
installé sur Xiaomi ; l'utilisateur a accepté le parcours essentiel avec sa clé
personnelle, y compris après redémarrage. Cela ne mesure pas la précision par espèce.

## Obtenir une clé gratuite

1. Créer un [compte développeur](https://my.plantnet.org/signup), distinct du
   simple parcours public d'identification de plantes. Suivre la confirmation de
   compte demandée par le site.
2. Se connecter puis ouvrir [Réglages → clé API](https://my.plantnet.org/settings/api-key).
3. Générer la clé privée si aucune n'existe, puis la copier.
4. Dans Blamera, ouvrir une photo puis la roue dentée Configuration ; saisir la
   clé dans « Clé API Pl@ntNet » puis toucher « Enregistrer la clé ». La clé est saisie directement sur le
   téléphone, sans partage dans le chat ni fichier du dépôt.
5. Toucher Analyser sur l'image déjà ouverte, ou capturer/importer une nouvelle
   image. L'enregistrement de la clé seul n'envoie aucune photo.

La configuration affiche « Clé configurée » après enregistrement. La clé peut
être remplacée par « Remplacer la clé » ou retirée par « Supprimer ». Le champ
reste vide à la prochaine ouverture : la clé enregistrée n'est pas recopiée dedans.
La finition 0.15.1 ajoute un œil pour afficher uniquement la saisie en cours ;
fermer la configuration efface cette saisie et rétablit le masquage.

[Guide officiel compte et clé](https://my.plantnet.org/doc/getting-started/introduction).
Il s'agit d'une application Android native : l'option du portail destinée à
« exposer » une clé à des domaines de navigateur n'est pas nécessaire pour ce parcours.

## Offre gratuite et fonctionnement

L'[offre Free](https://my.plantnet.org/pricing) annonce 500 identifications par jour
à 0 €. Ne pas souscrire Pro ni activer un contrat payant. La clé n'est pas un
abonnement ChatGPT et aucun crédit ChatGPT n'est utilisé par l'analyse botanique.
La gratuité dépend des conditions et du quota de cette offre ; Blamera n'achète
aucun crédit et n'active aucun paiement. Le quota se renouvelle à 00:00 UTC selon
la [documentation du service](https://my.plantnet.org/doc/api/quota).

Une fois la clé configurée, une capture, un import confirmé ou Analyser envoie
l'image de travail à Pl@ntNet. Zoom, sélection, rotation et direct caméra ne
déclenchent pas de requête. YOLOE continue localement et indépendamment du réseau.

Une requête porte sur une plante dominante : les noms alternatifs ne sont pas une
liste de plantes distinctes. Pour isoler une plante dans une scène riche, zoomer
et toucher Analyser. L'espèce proposée ne remplace jamais le masque d'un pot.
L'identification est une hypothèse ; son score n'est pas une probabilité calibrée
de justesse. La consigne ChatGPT n'en est enrichie que si elle est assez étayée et
concerne exactement l'image envoyée.

## Évaluation à venir

La recette réseau réelle se fait sur Xiaomi avec la clé de l'utilisateur ; les
tests PC utilisent des clés et images synthétiques. Un jeu botanique de référence
devra inclure plantes d'intérieur, cactus/succulentes, arbres, fleurs, confusions,
scènes mixtes et images sans plante. Ne pas confondre validation du transport et
mesure de précision de reconnaissance.

## Attribution et logo

Le logo `app/src/main/res/drawable-nodpi/powered_by_plantnet.png` est le PNG
officiel [Powered by Pl@ntNet](https://my.plantnet.org/images/powered-by-plantnet-light.png),
fourni par les [conditions d'utilisation de l'API](https://my.plantnet.org/terms_of_use).
Téléchargé sans modification : 859×236 pixels, 21645 octets, SHA-256
`2e5b33be097bb04e8fbe80a9b6a84a62ab1f655a3e9221b94c446c9f4260f0f3`.
Ce logo tiers ne relève pas de la licence des sources propres de Blamera ; son
usage sert à créditer le service. Ne pas le réutiliser comme identité de l'application.

Crédit français : « Service d'identification d'espèces végétales fondé sur l'API
de reconnaissance Pl@ntNet, régulièrement mise à jour : https://my.plantnet.org/ ».
