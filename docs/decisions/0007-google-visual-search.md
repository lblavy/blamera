# ADR 0007 — recherche Google par image après action explicite

Décision historique du transfert web 0.9.0/0.9.1, remplacée pour l'implémentation
courante par l'[ADR 0008 — partage natif vers Lens](0008-google-lens-share.md).

29 octobre 2026 — décision implémentée, revue et validation PC conformes ;
recette 0.9.0 en échec sur appareil ; correctif 0.9.1 installé et validé sur PC,
recette réelle encore en échec après le nouvel essai humain. Ticket M2-01.

## Besoin et choix

L'utilisateur préfère la recherche Google dans le navigateur à partir de la photo,
car les noms estimés par le modèle ne sont pas assez fiables. La recherche textuelle
envisagée avant cette réponse est abandonnée sans livraison.

Préparer localement un recadrage rectangulaire de l'objet sélectionné, avec son
contexte proche et sans contours dessinés, puis montrer l'image proposée dans la
fiche. Le bouton de recherche annonce son envoi à Google. Lui seul déclenche un
téléversement JPEG HTTPS vers Lens ; une adresse de résultats strictement contrôlée
est ensuite ouverte avec une activité web Android. Aucun hébergement intermédiaire,
SDK tiers, clé ou facturation API n'est utilisé.

Le [spike borné](../../build/reports/validation/m2-object-actions/coder/lens-spike/probe-result.json)
sur une image synthétique a observé HTTP 302 vers `https://www.google.com/search`,
sans authentification ni cookies envoyés. Aucune page de résultats ni pertinence
sur téléphone n'est validée par ce test.

## Sources et alternatives

L'[aide Google](https://support.google.com/websearch/answer/1325808?co=GENIE.Platform%3DAndroid&hl=fr)
décrit la recherche Lens sur photo et la sélection d'une zone.
[Mozilla utilise une requête multipart Lens dans son navigateur](https://github.com/mozilla-mobile/firefox-ios/issues/34457).
Les sources Mozilla examinées dans le spike décrivent les champs `encoded_image`
et `processed_image_dimensions`. Ce constat d'implémentation n'est pas un contrat
de support Google pour les applications tierces.

### Révision du protocole — 0.9.1

Le test humain 0.9.0 ouvre Google avec une image cassée, tandis qu'un import direct
dans Google Lens depuis Chrome fonctionne. Les sources Chromium distinguent
`/upload` (Scotty) de `/v3/upload` (fichier direct), et leur formulaire fichier
ne contient que `encoded_image`. Retenir le protocole fichier direct dans le client
existant : `/v3/upload`, fichier JPEG seul, paramètres publics `ep=cntpubb`, `hl=fr`,
`st`, `re=df`, `s=4` et dimensions JPEG dans `vpw`/`vph`. Ne pas copier `cd`, cookie,
User-Agent ou requête de préchargement Chrome.
[Code Chromium](https://chromium.googlesource.com/chromium/src/+/HEAD/chrome/browser/resources/new_tab_page/lens_form.ts),
[formulaire](https://chromium.googlesource.com/chromium/src/+/HEAD/chrome/browser/resources/new_tab_page/lens_form.html).

L'écart est établi ; son rôle causal unique n'est pas prouvé. La revue approuve le
correctif minimal avant une refonte du transfert. Le travail exploratoire de serveur
local est archivé et retiré, aucun serveur ou JavaScript n'est livré. Les gardes du
client, délais et allowlist restent inchangés ; aucune réponse incompatible n'est
interprétée comme un succès. [Preuves et état du correctif](../validation/m2-search-fix.md).

Le partage Android standard d'une image ne garantit pas une recherche Google dans
le navigateur. Ouvrir seulement Google Images ne lui transmet pas les pixels locaux.
Héberger publiquement la photo pour rechercher son URL introduirait un service et
une exposition supplémentaires inutiles. Une API IA payante est hors de cet incrément.

## Conséquences et limites

Blamera a désormais besoin de `INTERNET` pour l'action de recherche. La détection,
la segmentation et les traductions restent locales. Aucun contrôle réseau ou envoi
n'est déclenché par la sélection ou l'analyse. Le JPEG neuf ne reprend ni EXIF,
nom personnel, URI de galerie, chemin de fichier ou masque dessiné.

La préparation est faite hors UI : rectangle de l'objet élargi de 5 % par côté puis
borné à l'image, côté long limité à 1 000 pixels sans agrandissement, JPEG qualité 80
d'au plus 2 Mio. Les délais de connexion et lecture sont de 10 secondes ; la durée
totale de l'envoi est bornée à 25 secondes. L'envoi a des délais et une taille bornés,
sans nouvelle tentative automatique, cookie ou authentification. Le client ne suit
pas les redirections et ne conserve pas de corps de réponse. Seule une adresse HTTPS
de résultats Google validée peut être ouverte : hôte exactement `www.google.com`
ou `lens.google.com`, chemin brut `/search`, requête non vide, au plus 16 384 caractères,
sans port explicite, identifiant utilisateur ou fragment. Échec, refus, réponse inattendue ou
annulation restent récupérables. Les adresses de résultats et les images ne sont
pas journalisées.

La recherche dépend d'un protocole web susceptible de changer ou d'être refusé par
Google ; une réponse inattendue est une erreur, sans contournement. Une annulation
ou un échec d'ouverture ne peut pas rétracter des pixels déjà reçus par Google.
Les pratiques et résultats du service Google restent externes à Blamera.
La validation réelle reste une recette effectuée manuellement par l'utilisateur.
