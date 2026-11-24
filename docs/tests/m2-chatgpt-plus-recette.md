# Recette — essai ChatGPT Plus image + consigne

Version 0.10.4/code 22 installée. Parcours essentiel validé sur le Xiaomi le
31 octobre 2026 : catalogue et réponse française pertinente à la tasse synthétique
visibles sur la capture fournie. Modèle exact et latence non mesurés ; les étapes
ci-dessous restent la recette de référence, pas de nouveaux essais demandés.

L'utilisateur ouvre et manipule Blamera. L'agent ne saisit aucun identifiant, ne lit
pas les données de ChatGPT et ne change aucun réglage sur le téléphone. L'essai
utilise une illustration synthétique intégrée, sans photo de la galerie.

1. Ouvrir Blamera, puis « Essai ChatGPT ». Vérifier l'image de test et la consigne
   en français. Rien ne doit être envoyé à cette étape.
2. Toucher « Continuer avec ChatGPT ». Dans le navigateur, se connecter à son compte
   Plus personnel et autoriser l'usage du plan pour Blamera. Ne rien acheter,
   ne pas activer de crédits supplémentaires ou de paiement. Revenir dans Blamera
   si le navigateur reste sur la page de fin de connexion.
3. Vérifier que Blamera indique la connexion et présente les modèles du compte.
   Un refus de permission ou une connexion interrompue doit rester récupérable.
4. Toucher « Vérifier les limites ChatGPT ». Vérifier que l'option autorisant les autres
   applications à utiliser des crédits après atteinte de la limite est **désactivée**.
   La laisser désactivée. Si le réglage n'est pas vérifiable, arrêter avant l'envoi
   et signaler ce point ; aucune supposition de gratuité n'est nécessaire.
5. Revenir dans Blamera et cocher la confirmation uniquement après cette vérification.
   Choisir un modèle. L'envoi était désactivé avant ces prérequis.
6. Toucher « Envoyer l’image de test » une fois. Attendre la réponse dans Blamera.
   Elle devrait parler de l'illustration et répondre en français. Une interprétation
   imparfaite n'est pas à elle seule un défaut du transfert ; indiquer ce qu'elle décrit.
7. Vérifier que la réponse reste lisible après rotation, sans nouvel envoi automatique.
   Revenir au parcours caméra/photo et vérifier que Blamera reste utilisable.
8. Si souhaité, revenir dans l'essai et toucher « Se déconnecter » : aucun envoi ne doit rester
   possible avec l'ancien compte et la confirmation des crédits doit être remise à zéro.

Cas d'erreur utiles, sans épuiser volontairement l'allocation : annuler une connexion,
refuser l'autorisation, annuler une analyse en cours. Une erreur réseau, un modèle
incompatible ou une allocation atteinte doivent afficher une explication et ne jamais
déclencher de nouvelle tentative automatique ou d'achat. Ne pas modifier les réglages
réseau du téléphone pour ces tests ; les cas réseau sont simulés sur PC.

Retour attendu : connexion réussie ou étape exacte du blocage, modèle choisi,
possibilité de vérifier la désactivation des crédits, réponse cohérente avec l'image,
délai ressenti et éventuel message d'erreur. Ne pas transmettre de mot de passe,
code de connexion, jeton, URL de callback ni capture contenant des données de compte.

## Reprise ciblée avec le diagnostic 0.10.1

La version 0.10.0 a bloqué après consentement sur la page ChatGPT, bien que Blamera
apparaisse au portail. Le diagnostic 0.10.1 est installé après ses contrôles PC.
Aucun nouvel envoi d'image n'est nécessaire pour
localiser la connexion.

Ouvrir manuellement « Essai ChatGPT », puis « Continuer avec ChatGPT ». Après
consentement, si la page reste en chargement une quinzaine de secondes, revenir
manuellement dans Blamera et lire l'étape affichée en haut. Signaler uniquement
cette étape et le message d'erreur éventuel, sans adresse ni identifiant.
« Annuler » reste accessible en haut ; l'ensemble connexion/catalogue est limité
à cinq minutes. Aucune relance automatique ni manipulation de Chrome à effectuer.

Si la connexion aboutit et que les modèles apparaissent, reprendre la vérification
des crédits désactivés puis l'envoi de la tasse selon les étapes précédentes.
Garder Blamera ouverte et le téléphone connecté pour permettre, si nécessaire,
la lecture des seuls événements de diagnostic du processus Blamera. L'agent ne
lit ni Chrome ni ChatGPT et ne lance pas l'application.

## Diagnostic réseau 0.10.2

0.10.2 est validée sur PC et installée. Ouvrir l'essai puis toucher « Continuer avec
ChatGPT ». La configuration publique du service est vérifiée avant le navigateur.
Si une erreur apparaît directement, garder Blamera ouverte et le téléphone connecté,
et signaler ce message : aucune nouvelle connexion au compte n'est nécessaire.

Si le navigateur s'ouvre, terminer l'autorisation et revenir dans Blamera, puis
signaler le résultat (erreur ou modèles disponibles). Aucun envoi d'image n'est
requis pour cette étape. Les seuls événements autorisés pour le diagnostic
contiennent étapes/endpoint/catégorie fixes, statut HTTP validé et cycle de vie
de l'activité Blamera, sans secret, URL, en-tête ou corps de réponse.

## Finalisation au retour — 0.10.3

0.10.3 est validée sur PC et installée. Ouvrir l'essai et toucher « Continuer ».
Terminer la connexion dans le navigateur, puis revenir manuellement dans Blamera
dès que la page locale le demande. La finalisation attend désormais ce retour au
premier plan. Signaler si les modèles apparaissent ou le message d'erreur exact.

L'attente reste dans la limite globale de cinq minutes. Une annulation ou une
expiration ne doit pas déclencher un échange tardif au retour. Garder l'application
ouverte et le téléphone connecté pour la lecture des seuls événements autorisés.
Si la connexion réussit, la vérification des crédits désactivés puis la tasse
restent les étapes distinctes de la recette principale.

## Lecture du catalogue — 0.10.4

0.10.4 est validée sur PC et installée. Refaire la connexion puis revenir
manuellement dans Blamera. Le catalogue dépassant 256 Kio doit maintenant pouvoir
être lu dans son nouveau budget borné. Indiquer si les modèles apparaissent ou
donner le message exact, sans adresse, code, identifiant ou jeton.

Si les modèles apparaissent, poursuivre les étapes 4 à 6 de la recette principale :
vérifier soi-même que les crédits après limite sont désactivés, puis seulement
confirmer dans Blamera et envoyer l'image synthétique. Si ce réglage n'est pas
vérifiable, arrêter avant l'envoi. Indiquer le modèle choisi et si la réponse
en français décrit la tasse, ou le message d'erreur et son étape.
