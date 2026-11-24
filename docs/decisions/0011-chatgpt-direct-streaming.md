# ADR 0011 — envoi direct, réflexion et réponse progressive

31 octobre 2026 — M2-08, version 0.13.0/code25.
Baseline : `fb4a92b5817f25830bfb2b8b5361f72301d475d1`.
Implémentation, revue, globale et testeur conformes sur PC ; parcours essentiel
accepté sur Xiaomi en 0.13.0 par retour humain « ça semble ok ».

## Décision produit

L'utilisateur confirme que la connexion mémorisée de 0.12.0 fonctionne. Il demande
maintenant un usage personnel sans cases à cocher ni avertissements répétés :
choisir éventuellement le modèle et la réflexion, puis envoyer. Il demande aussi
une réponse progressive, le retrait du test Gemini et une fiche plus compacte.

Cette demande remplace explicitement la garde de confirmation des crédits des
ADR 0009/0010 pour les nouveaux parcours. Aucun achat, activation de crédits,
changement de facturation serveur ou repli sur une clé API n'est ajouté. Blamera
ne peut toujours pas lire ce réglage serveur. Les contrôles utiles de compte,
d'autorisation du plan, de modèle et d'erreur restent applicables sans confirmation
supplémentaire dans l'interface.

La fiche présente directement les deux sélecteurs « Modèle » et « Réflexion »,
puis l'action d'envoi avec icône et texte. Compte, déconnexion et accès aux limites
sont des actions secondaires. Supprimer les paragraphes de diagnostic, la consigne
développée et l'étape de dépliage avant l'envoi. Préserver les messages d'erreur
actionnables et l'état de traitement, l'aperçu du recadrage, Lens et la navigation.
Retirer le destinataire Gemini et son code devenu inutile ; conserver son historique
d'évaluation dans la documentation. Le diagnostic ChatGPT synthétique peut rester
accessible à part, sans occuper l'action principale de la caméra.

La restauration du compte n'envoie aucune photo. Sélection, connexion, changement
de modèle ou de réflexion et rotation n'envoient rien non plus. Un appui d'envoi
reste nécessaire ; aucune nouvelle tentative d'inférence automatique.

## Contrat OpenAI

La [route officielle d'usage du plan](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
reste le catalogue du compte puis `POST https://api.openai.com/v1/responses`,
`store:false`, `stream:true`, avec l'accès OAuth validé. Aucun endpoint privé,
changement de scopes ou nouveau SDK n'est nécessaire.

Le [streaming Responses](https://developers.openai.com/api/docs/guides/streaming-responses)
fournit des événements de texte ; leur réception doit être visible avant la fin
du flux HTTP. Une réponse n'est terminée qu'à `response.completed`. Une coupure,
un événement d'échec ou un EOF prématuré restent des erreurs, même après un texte
partiel. Ne pas exposer les événements internes de raisonnement comme une réponse.

La [réflexion](https://developers.openai.com/api/docs/guides/reasoning) dépend du
modèle. Option « Par défaut » : omettre `reasoning.effort`. Les profils explicites
vérifiés sur les pages officielles le 31 octobre sont :

| Modèles | Efforts explicites |
| --- | --- |
| [GPT-6.1 Sol](https://developers.openai.com/api/docs/models/gpt-6.1-sol), [GPT-6 Astra](https://developers.openai.com/api/docs/models/gpt-6-astra) | low, medium, high, xhigh, max |
| [GPT-6.1 Terra](https://developers.openai.com/api/docs/models/gpt-6.1-terra), [GPT-6 Sol](https://developers.openai.com/api/docs/models/gpt-6-sol), [GPT-6 Luna](https://developers.openai.com/api/docs/models/gpt-6-luna) | none, low, medium, high, xhigh, max |
| [GPT-5.6 Sol](https://developers.openai.com/api/docs/models/gpt-5.6-sol), [GPT-5.6 Terra](https://developers.openai.com/api/docs/models/gpt-5.6-terra), [GPT-5.6 Luna](https://developers.openai.com/api/docs/models/gpt-5.6-luna) | none, low, medium, high, xhigh, max |

Pour un modèle inconnu, proposer uniquement le défaut plutôt qu'inventer ses
capacités. Ne pas extrapoler un préfixe à une variante future. Le catalogue reste
l'autorité pour la disponibilité du modèle au compte ; ces profils décrivent
uniquement les réglages documentés. Ne pas sélectionner de mode Pro ou Fast.

## État et cycle de vie

Conserver le modèle et l'effort préféré avec le compte dans le stockage protégé
existant, avec migration compatible du record 0.12.0. Ne pas prolonger la validité
d'un jeton lors d'une écriture de préférence, ni écraser un grant renouvelé.
Un effort incompatible avec un nouveau modèle revient au défaut.

Le texte reçu reste attaché au document et à l'opération d'origine. Changer
d'objet ou de photo, annuler ou fermer ne doit jamais publier de fragments tardifs.
La rotation conserve l'opération et son texte ; elle ne relance pas la requête.
Le rendu Markdown doit rester lisible pendant la réception et après sa fin.
Les photos, consignes et réponses ne sont pas persistées. Les bornes mémoire,
délais, annulations, restrictions HTTPS et diagnostics sans secrets restent actifs.

## Validation

TDD sur l'envoi sans case, les options autorisées et leur persistance, la réception
avant EOF, le découpage UTF-8/SSE, les limites, erreurs et fragments obsolètes.
Régressions de connexion/renouvellement, rotation, recadrage et Lens. Vérification
de la fiche en portrait, paysage et largeur réduite. Revue indépendante, campagne
qualité locale et testeur avant installation du seul APK principal. La validation
visuelle et le vrai streaming depuis le compte restent une recette humaine.
