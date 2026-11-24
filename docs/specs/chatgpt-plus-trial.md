# Essai ciblé — image et consigne avec ChatGPT Plus

31 octobre 2026. M2-06 terminé pour l'essai ciblé : logiciel validé sur PC après
revue et testeur indépendants, puis catalogue et réponse française à l'image
synthétique validés sur le Xiaomi en 0.10.4 par capture fournie par l'utilisateur.
Le modèle choisi et la latence ne sont pas mesurés par cette capture. L'intégration
à la fiche de l'objet et le rendu Markdown restent une suite distincte.
Référence avant changement : `863ef77a382e9abc86c68960d715563a8f49ac62`.

## Objectif et limites

L'utilisateur autorise un essai dans Blamera : connexion à son compte ChatGPT Plus,
envoi d'une image avec une consigne et affichage de la réponse, sans activer de
crédits payants. L'essai doit valider le protocole avant son intégration à la fiche
des objets personnels. Il ne change ni les modèles de segmentation ni les parcours
Lens/Gemini existants. Aucun succès réel n'est déduit d'un test PC simulé.

Un écran séparé « Essai ChatGPT » présente une image synthétique générée localement
(tasse et inscription BLAMERA), sans photo personnelle, et la consigne :

> Décris cette image, identifie l’objet principal et indique tes incertitudes. Réponds en français.

Seul l'appui explicite d'envoi transmet l'image et cette consigne à OpenAI. Entrer
dans l'écran, se connecter ou choisir un modèle ne lance aucune analyse. La réponse
se lit dans Blamera. Le modèle est choisi parmi les entrées proposées au compte ;
une entrée peut refuser l'image et doit alors produire une erreur compréhensible.
L'essai évalue le fonctionnement du transfert, pas la qualité statistique d'un modèle.

## Fondement vérifié

- [Cookbook officiel : Paste Perfect](https://developers.openai.com/cookbook/articles/sign-in-with-chatgpt)
  : compte Plus/Pro éligible, usage du plan avec consentement, projets personnels
  locaux annoncés ; exemple complet pour macOS et texte, pas une preuve Android.
- [Enregistrement et connexion](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)
  : client public dynamique, PKCE, state, nonce, callback HTTP `127.0.0.1`, aucun secret client.
- [Modèles et inférence](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
  : découverte propre au compte, endpoint public Responses, `store:false`, `stream:true`,
  succès seulement à `response.completed`.
- [Restrictions de preview](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)
  : images acceptées par les modèles compatibles, Files upload non pris en charge,
  paramètres habituels tels que `max_output_tokens` non acceptés par cette route.
- [Entrées image](https://developers.openai.com/api/docs/guides/images-vision)
  : image incluse en data URL Base64 avec le texte, sans hébergement public.
- [Comptes et sessions](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions)
  et [erreurs](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery)
  : renouvellement, révocation, permissions et erreurs d'allocation.

Exemple Android concret inspecté en lecture seule :
[Rivet, révision c0c49c83](https://github.com/Kaiser0733/rivet/tree/c0c49c83a715e03378077938c3a43464a397ef4a).
`ChatGptAuth.kt`, `ChatGptSession.kt` et `ChatGptPlanClient.kt` implémentent
respectivement le protocole officiel, un listener ServerSocket sur loopback et
Responses public avec SSE. Le client examiné transmet du texte : aucune preuve
de bout en bout Android + image + abonnement n'est revendiquée pour ce dépôt.
Pas de code tiers copié ni d'APK tiers installé dans cet essai.

## Allocation et crédits

[ChatGPT documente](https://learn.chatgpt.com/docs/sign-in-with-chatgpt) que l'usage
consomme l'allocation Codex / ChatGPT Work existante, partagée avec les autres
applications. La connexion ne crée pas une nouvelle allocation. L'utilisateur
peut limiter l'usage d'une application et autoriser ou non l'usage de crédits
après atteinte de sa limite dans [les réglages d'utilisation](https://chatgpt.com/settings/usage).

Aucun paramètre documenté n'a été trouvé pour forcer « allocation incluse seulement »
dans une requête Responses. Blamera ne prétend ni lire ni modifier ce réglage serveur.
Avant tout envoi, une confirmation humaine, décochée au départ et réinitialisée
à la déconnexion/changement de compte, exige la vérification que l'usage des crédits
après la limite est désactivé. Un lien ouvre les réglages pour cette vérification.
Si elle ne peut pas être faite, l'envoi reste désactivé. La protection serveur dépend
du réglage réellement appliqué au compte, pas de la case déclarative dans Blamera.

Aucun achat, activation de facturation, clé API, repli vers une route facturée,
changement de réglage automatique ou nouvelle tentative automatique d'inférence.
Une limite, un refus ou une indisponibilité arrête la demande et conserve un message
utile ; aucune proposition d'achat n'est nécessaire à cet essai.

## Frontières et sécurité

L'app principale obtient la permission Android INTERNET pour ce parcours explicite.
Les parcours de vision conservent leur inférence locale. L'activité d'essai n'est
pas exportée. Le navigateur système gère la saisie des identifiants : Blamera ne
demande pas le mot de passe et ne réutilise aucun jeton d'une autre application.

Le listener est lié uniquement à `127.0.0.1`, sur port disponible, avant ouverture
du navigateur. Il utilise `/auth/callback` et la même URI pendant tout l'échange.
Chaque tentative possède state, nonce et PKCE frais, valide le retour et l'ID token
(signature, issuer, audience, expiration, nonce), et vérifie les scopes accordés.
Le client émis est sauvegardé avant l'échange du code. Le host ID opaque est stable.
Seuls ces identifiants non secrets sont persistés ; les jetons restent en mémoire
pour la durée de l'essai et sont effacés à sa fermeture. Une reprise après arrêt
du processus exige une nouvelle connexion. Renouvellement sérialisé et révocation
explicite respectent les contrats documentés, avec échec récupérable.

Pas de jeton, photo, consigne, réponse, URL contenant des secrets ou détail brut
d'erreur réseau dans les logs. Les communications utilisent les endpoints HTTPS
officiels fixés, sans suivre silencieusement une redirection vers un tiers. Les
lectures HTTP, callback, durées et sortie SSE sont bornées. Les requêtes sont
annulables ; rotation et retour du navigateur ne soumettent rien automatiquement.

## Critères et preuves attendues

1. Connexion utilisateur, refus, expiration, annulation et permission du plan
   correctement distingués ; aucune inférence déclenchée par la connexion.
2. Image et consigne visibles, modèle provenant du compte, envoi unique explicite
   impossible sans connexion, autorisation du plan et confirmation des crédits.
3. Contrat image + texte exact testé avec transport simulé ; réponse affichée
   seulement après succès terminal, erreurs/quota/interruption sans succès trompeur.
4. Sécurité OAuth, cycle de vie, bornes et régressions du parcours local vérifiés
   en TDD sur PC ; format, lint, complexités, duplication, dépendances et couverture
   inchangés dans leurs exigences. Revue indépendante puis validation du testeur.
5. APK principal installable ; l'utilisateur ouvre et manipule l'application.
   L'agent ne se connecte pas à sa place, ne lance pas l'application, ne modifie pas
   les réglages du téléphone et ne lit pas les données d'autres applications.
6. Preuve appareil en attente jusqu'au retour humain sur connexion, choix du modèle,
   image reçue avec consigne et réponse affichée. Ne pas clore M2-06 avant cette preuve.

Preuve reçue le 31 octobre : catalogue affiché et réponse pertinente en français à
la tasse synthétique dans Blamera ; confirmation des crédits cochée. Le parcours
essentiel est validé, avec modèle exact non visible et latence non mesurée.
[Résultat et limites](../validation/m2-chatgpt-catalog.md#résultat-réel--31-octobre-2026).

## Diagnostic du retour de connexion — 0.10.1

Le premier retour humain 0.10.0 montre un blocage après consentement : Blamera est
ajoutée au portail ChatGPT, mais le navigateur reste sur la page ChatGPT en
chargement, sans afficher la page locale de retour. Dans Blamera, les commandes
conditionnées à la fin de connexion restent grises. L'étape exacte n'est pas encore
établie ; un enregistrement au portail ne prouve pas la réception du code.

L'incrément de diagnostic est limité aux éléments suivants :

- Étapes fixes visibles pour distinguer préparation/attente du navigateur,
  réception du retour, échange, validation d'identité et catalogue. L'échec conserve
  son étape sans divulguer d'information sensible. L'annulation reste accessible.
- Échéance absolue pour la connexion complète, pas seulement pour chaque sous-requête,
  afin que plusieurs attentes HTTP consécutives n'apparaissent pas indéfinies.
- Correction d'une préconnexion ou requête locale abandonnée seulement si elle
  provoque un défaut reproductible sur PC ; ne pas consommer un callback légitime
  ni ignorer un vrai refus d'autorisation en renforçant la résilience HTTP.
- Si nécessaire, événements de diagnostic exclusivement composés d'étapes et
  catégories d'erreurs prédéfinies. Aucun URL, port, state, nonce, code, identifiant
  de client ou de compte, jeton, photo, consigne ou réponse dans les logs.

Pas de nouvelle permission, service permanent, modification de batterie ou de
Chrome, endpoint privé, changement arbitraire de scope ou achat. Les endpoints
publics actuels ont été confirmés par la configuration OpenID officielle et le
protocole comparé au DevKit. Une seule reprise manuelle guidée sera demandée après
revue, validation PC et mise à jour ; aucun succès réel n'est présumé.

## Échange de code et accès public — diagnostic 0.10.2

En 0.10.1, le retour réel est reçu, puis NETWORK apparaît à EXCHANGING_CODE avant
la vérification d'identité. Pour distinguer transport et HTTP5xx, le diagnostic
peut journaliser seulement un endpoint/stade/catégorie sous forme d'enums et un
statut HTTP validé entre 100 et 599. Aucun message, stack, en-tête, corps ou URL
de requête/réponse dans les logs. Les HTTP5xx doivent annoncer un service indisponible,
pas conseiller un changement de réseau. Métadonnée de cycle de vie autorisée :
RESUMED/PAUSED de l'activité Blamera exclusivement.

Après l'appui de connexion, CHECKING_SERVICE vérifie la configuration OpenID
publique avant ouverture du navigateur, avec validation stricte d'issuer/origine.
Cette découverte est réutilisée en mémoire après retour, sans ajout d'un appel au
flux froid. Elle n'envoie aucun identifiant ou jeton. L'échange de code, ses scopes,
ses limites et ses protections TLS restent inchangés. Un défaut d'accès public
est ainsi explicite sans redemander inutilement une connexion au compte.

## Finalisation au premier plan — 0.10.3

Le diagnostic réel 0.10.2 établit HTTP200 pour la découverte dans Blamera RESUMED,
puis un échec DNS sur TOKEN/OPEN_OUTPUT en PAUSED, avant retour dans l'application.
Cela motive un changement de contexte d'exécution ; aucune politique système
précise ni panne générale DNS n'est présumée.

Après callback valide et sauvegarde du client émis, WAITING_APP_RETURN attend le
retour RESUMED de l'activité avant d'émettre le POST d'échange. Une présence retenue
par le ViewModel et partagée avec le gateway survit à la rotation sans retenir
l'ancienne Activity. L'attente est annulable et reste dans le budget global de
cinq minutes. Expiration, annulation ou fermeture interdisent tout POST tardif.
Le code demeure en RAM, le formulaire et les gardes OAuth/TLS/crédits sont inchangés.
Aucun service, réglage DNS, permission, achat ou nouvelle tentative automatique
de connexion n'est ajouté.

## Lecture bornée du catalogue — 0.10.4

La trace réelle 0.10.3 confirme TOKEN et JWKS HTTP200 puis MODELS HTTP200,
avec refus du corps avant parsing par la borne de 256 Kio. La correction réserve
huit Mio au seul GET exact du catalogue public. Les réponses d'erreur restent à
64 Kio, l'inférence à deux Mio ; les contrôles de schéma, identifiants, ordre et
visibilité sont conservés. Un dépassement reste une réponse invalide pour l'UI,
avec diagnostic fixe BODY_LIMIT sans texte externe. Aucun catalogue personnel
n'est enregistré pour développer ou tester ce traitement.
[Preuves, budget et limites](../validation/m2-chatgpt-catalog.md).
