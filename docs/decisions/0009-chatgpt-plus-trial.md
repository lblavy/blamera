# ADR 0009 — essai direct avec l'abonnement ChatGPT

Évolution 0.13.0 : à la demande de l'utilisateur, l'[ADR 0011](0011-chatgpt-direct-streaming.md)
remplace les confirmations et l'ergonomie de diagnostic par l'envoi direct, le
choix de réflexion et le streaming ; le test Gemini est retiré. Les contrats
OAuth et l'absence d'activation de paiement restent applicables.

30 octobre 2026, actualisé le 31 — M2-06. Essai validé sur PC après revue et testeur,
puis catalogue et analyse synthétique validés sur le Xiaomi en 0.10.4 par retour
humain. L'intégration du recadrage dans la fiche reste un ticket distinct.

## Besoin

L'utilisateur souhaite une identification plus pertinente avec une image et une
consigne, en utilisant son abonnement Plus. Le partage public avec l'application
Gemini a transmis l'image sans préremplir la consigne dans les deux essais réels.
Une recherche documentaire préalable a trouvé un protocole officiel OpenAI et une
implémentation Android en Kotlin. [Sources et contrat](../specs/chatgpt-plus-trial.md).

## Décision

Ajouter à l'APK principal un écran d'essai explicite séparé des parcours de vision.
Utiliser le protocole public Sign in with ChatGPT et Responses avec autorisation
de l'abonnement, un modèle découvert pour le compte et une image synthétique visible.
Le navigateur système possède le parcours de connexion. Blamera reçoit le résultat
OAuth par HTTP loopback puis communique directement avec OpenAI en HTTPS.

La permission INTERNET devient nécessaire pour cet écran. La présence de cette
permission ne déclenche pas d'envoi automatique ; le modèle de vision et ses données
restent traités localement. Les parcours Lens et Gemini conservent leurs contrats.
Ne pas généraliser l'intégration aux images personnelles avant la preuve de ce trajet.

Ne pas ajouter un runtime Node/Codex complet à Android : le SDK d'exemple officiel
cible Node/Electron, alors que le protocole HTTP/OAuth peut être implémenté dans la
stack Kotlin existante. Ne pas appeler les endpoints privés ChatGPT, copier des
jetons d'une autre application, automatiser l'interface de ChatGPT ou demander une
clé API Platform. Aucun backend ni facturation n'est ajouté.

## Compromis de l'essai

Les jetons restent en mémoire, avec renouvellement contrôlé si nécessaire ; seule
la registration non secrète est conservée pour réautoriser l'installation. Fermer
l'essai efface la session locale. Cela évite d'introduire une conservation durable
de secrets pour un prototype ciblé, au prix d'une reconnexion après fermeture.
La rotation et l'aller-retour navigateur doivent conserver l'instance pendant l'essai.

Pour le parcours principal après l'essai, l'[ADR 0010](0010-chatgpt-session-retention.md)
remplace ce choix en 0.12.0 par une conservation chiffrée de la connexion et du
modèle après fermeture. Le diagnostic synthétique reste éphémère ; la confirmation
des crédits n'est pas persistée.

La gratuité supplémentaire n'est pas une propriété du protocole : il consomme
l'allocation existante, et l'usage de crédits après limite dépend d'un réglage
ChatGPT. L'application exige une vérification humaine préalable de sa désactivation,
sans prétendre contrôler ce réglage côté serveur. Aucun mécanisme d'achat ou
d'activation des crédits n'est livré. [Contrat détaillé](../specs/chatgpt-plus-trial.md#allocation-et-cr%C3%A9dits).

Un échec d'éligibilité, de callback Android ou de modèle image peut invalider cet
essai. Il doit être rendu visible, pas masqué par un repli vers une API facturée.
Le retour du 31 octobre valide le parcours synthétique de bout en bout : réponse
pertinente en français affichée dans Blamera et confirmation des crédits cochée.
[Preuve et limites](../validation/m2-chatgpt-catalog.md#résultat-réel--31-octobre-2026).
