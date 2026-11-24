# ChatGPT dans la fiche d'un objet

31 octobre 2026 — M2-03, M2-04 et M2-03-UI-01.
Le contrat ci-dessous décrit 0.11.0/0.12.0. L'[ADR 0011](../decisions/0011-chatgpt-direct-streaming.md)
le remplace pour 0.13.0 sur l'ergonomie, l'envoi sans case, la réflexion et le
streaming. La connexion mémorisée 0.12.0 est validée par l'utilisateur.
Évolution suivante M2-09 : [cadrage et actions de photo](photo-actions.md), avec
source Cadrage/Objet, barre commune et réglages derrière une roue dentée.
Baseline : `882deea0ff66019dec3b944dc13071342b58c556`.
Version 0.11.0/code23 : implémentation, revue et qualité conformes sur PC.
APK principal installé après reconnexion USB ; recette Xiaomi ouverte.

## Parcours

Après capture ou import et analyse locale, sélectionner un objet. Sa fiche garde
l'aperçu du recadrage et propose « Demander à ChatGPT », avec une icône usuelle.
Ce bouton déplie les contrôles dans la fiche. La réponse se lit sur place, sans
remplacer l'image de travail ni le zoom par l'écran d'essai synthétique.

Le recadrage est celui déjà préparé pour la recherche visuelle : rectangle de
l'objet avec 5 % de contexte, borné à la photo courante, JPEG qualité 80, bord
maximal 1000 pixels et deux Mio maximum. Les octets prévisualisés sont envoyés
directement ; aucun hébergement public ou partage de fichier externe n'est requis.
Après « Analyser cette zone », il provient de la nouvelle image de travail.

Consigne visible avant envoi :

> Identifie cet objet aussi précisément que possible à partir de l’image. Décris ses caractéristiques utiles et indique tes incertitudes. Ne présente pas une marque ou un modèle comme certain sans indice visible. Réponds en français.

La catégorie estimée par la segmentation n'est pas imposée comme une identité
certaine. La description générée reste une proposition de ChatGPT, avec ses
incertitudes ; aucune source ou référence produit n'est inventée par l'interface.

## Connexion et déclenchement

Réutiliser le [protocole validé par l'essai M2-06](chatgpt-plus-trial.md) : navigateur
système, PKCE/state/nonce, vérification de compte et autorisation du plan,
catalogue du compte, puis Responses public. Le retour RESUMED de l'activité
principale est requis avant finalisation. La
[documentation officielle](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
conserve `store:false`, `stream:true` et un succès seulement après `response.completed`.
Aucun changement des endpoints, des scopes, des limites HTTP ou de la facturation.

La sélection, la préparation du recadrage, l'ouverture de la fiche, la connexion,
le choix du modèle et la rotation n'envoient aucune image. Seul « Envoyer à
ChatGPT » déclenche l'inférence. La connexion seule ne vaut pas consentement
d'envoi de la photo.

La confirmation de vérification des crédits reste décochée au départ et remise à
zéro à la déconnexion. L'utilisateur vérifie que les crédits après limite sont
désactivés avant de la cocher. Si ce réglage n'est pas vérifiable, l'envoi reste
bloqué. L'application ne lit ni ne change ce réglage serveur, n'achète aucun crédit
et n'utilise aucune clé API ou route payante de repli.

La session, le modèle et cette confirmation restent en mémoire pendant l'usage
de Blamera et survivent à la rotation. Aucun secret ou réponse personnelle n'est
persisté. Un arrêt du processus exige une nouvelle connexion. Le parcours d'essai
synthétique reste disponible avec les mêmes protections.

Évolution autorisée après retour humain : l'[ADR 0010](../decisions/0010-chatgpt-session-retention.md)
prévoit en 0.12.0 la conservation chiffrée du compte et du modèle du parcours
principal. La confirmation des crédits, les images et les réponses restent non
persistées. Le diagnostic synthétique conserve sa session éphémère séparée.

## Identité de l'objet et cycle de vie

Chaque demande appartient à une photo, une version de résultats et un index
d'objet. Le même index sur une nouvelle photo désigne une autre entrée.
Le changement d'objet, la désélection, le remplacement de photo, une nouvelle
analyse de zone, le retour à l'image entière ou à la caméra invalident l'entrée
et la réponse et annulent la demande courante. Une réponse tardive ne doit jamais
apparaître pour un autre objet.
La reprise ou connexion du compte est indépendante du document : changer de
sélection annule l'inférence associée à l'ancienne image, mais ne doit pas
interrompre une restauration de compte encore en cours.

La rotation conserve l'entrée, la réponse et l'opération sans nouvel envoi. La
recréation du seul aperçu local ne doit pas être confondue avec une nouvelle
sélection. Le contrôleur ne conserve ni bitmap recyclé ni ancienne activité.
Annulation, erreur, quota, modèle incompatible et délai restent récupérables ;
aucune nouvelle tentative automatique d'inférence.

## Markdown

Utiliser un rendu natif commun à la fiche et à l'essai : gras, italique, titres,
paragraphes, listes, citations, code et liens. Le parseur est CommonMark Java
0.30.0, version épinglée ; [source](https://github.com/commonmark/commonmark-java/tree/commonmark-parent-0.30.0)
et [licence BSD-2-Clause](https://raw.githubusercontent.com/commonmark/commonmark-java/commonmark-parent-0.30.0/LICENSE.txt).
Le rendu transforme son arbre en texte Compose stylé, sans WebView, exécution de
HTML ou chargement d'images distantes. Un lien HTTP(S) n'ouvre le navigateur que
sur appui explicite. Les autres schémas ne sont pas activés.

Texte, profondeur et nombre de nœuds restent bornés. Une entrée malformée ou trop
complexe conserve un repli lisible sans blocage de l'application. Les limites
exactes et le sous-ensemble rendu seront consignés avec la validation.

## Validation

- TDD réel du recadrage et de la consigne envoyés, de l'absence d'envoi implicite,
  des gardes de crédits et de la réponse associée au bon objet.
- Tests de sélection A vers B, nouvel objet au même index, région/original,
  annulation/retour, résultat tardif, rotation et reprise de connexion navigateur.
- Tests de styles Markdown et d'ouverture explicite de liens autorisés, texte
  malformé, code, HTML inerte, images sans chargement et limites de complexité.
- Régressions Lens/Gemini, photo/caméra, zoom, contours et essai synthétique.
- Revue indépendante, contrôles de qualité complets puis testeur sur l'état gelé.
- Installation du seul APK principal, puis recette manuelle sur le Xiaomi. Aucun
  test instrumenté, lancement automatique ou réglage de l'appareil par l'agent.

L'essai précédent prouve le transfert synthétique ; l'intégration du recadrage
personnel et son ergonomie exigent encore la validation humaine de cette version.
