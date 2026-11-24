# Recette du diagnostic de partage Gemini

30 octobre 2026 — diagnostic principal 0.9.4/code 17 installé après reconnexion,
qualité globale, revue et testeur indépendant conformes sur PC. Mise à jour du seul
APK principal sortie 0 « Success », sans lancement automatique. Retour humain :
« Toujours pas de texte dans gemini. L'image y est ». Le transfert conjoint échoue
aussi en 0.9.4. Aucun bouton définitif ni troisième variante arbitraire.
Historique 0.9.3 du 29 octobre : « Image reçue, consigne absente », réception
conjointe en échec. Le premier préflight 0.9.4 sans appareil n'avait rien installé.
L'utilisateur lance et manipule Blamera lui-même ; aucune photo à envoyer à l'agent.

## Essai décisif

1. Capturer ou importer une image et sélectionner un objet après l'analyse locale.
2. Faire défiler le panneau au besoin, vérifier l'aperçu de la zone et la consigne
   annoncée, puis toucher « Tester Gemini ».
3. Dans Gemini, **avant de retaper, coller ou envoyer quoi que ce soit**, vérifier
   que l'image est lisible et correspond au recadrage, et que la consigne suivante
   est déjà présente dans le message à soumettre :

   > Identifie cet objet aussi précisément que possible, décris son usage et indique tes incertitudes. Réponds en français.

4. Si les deux sont présents, envoyer la demande soi-même si souhaité, puis revenir
   à Blamera et vérifier la conservation de l'image, du zoom et de la sélection.

Retour attendu : **image + consigne présentes**, **image seule**, **texte seul**,
ou **message d'erreur exact**. Une réponse descriptive dans Gemini permet aussi
un premier avis de pertinence, sans constituer une évaluation statistique du modèle.
Ne pas sélectionner une image de galerie dans Gemini pour compenser un transfert
raté : ce serait un autre parcours. Aucun réglage système ni logiciel supplémentaire
n'est demandé pour fabriquer un succès.

## Régressions et limites

La simple sélection, le zoom ou le retour ne doivent pas envoyer de données.
« Rechercher cette image » continue d'ouvrir Lens avec le recadrage seul. Les cas
de destinataire absent, refus, annulation et changement de sélection sont simulés
sur PC ; aucune désactivation ni modification de permissions sur le téléphone.

Si Gemini ne conserve pas les deux éléments, le diagnostic reste non validé et le
bouton définitif « Demander à Gemini » n'est pas livré comme fonctionnel. Si les
deux sont reçus, finaliser le bouton et consigner le retour avec sa portée exacte.
Le scénario positif ci-dessus reste un critère de référence, pas un test réussi.
La série bornée d'essais est terminée ; aucune nouvelle manipulation utilisateur
n'est demandée tant qu'une alternative n'a pas été choisie.
