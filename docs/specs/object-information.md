# Fiche d'objet et recherche Google par image

29 octobre 2026 — les parcours web 0.9.0/0.9.1 échouent sur appareil ;
le partage natif 0.9.2 vers Lens est implémenté, revu et installé ; qualité globale,
package et testeur indépendant conformes sur PC.
Après l'essai Lens demandé, l'utilisateur répond « ça a l'air de marcher » le
29 octobre : parcours essentiel accepté, sans preuve individuelle de chaque variante.

L'utilisateur demande de poursuivre après acceptation du parcours 0.8.0, puis précise :
« ouvrir navigateur dans google image à la photo, ce sera mieux. les descriptions/noms
sont assez mauvais ». Cette réponse remplace la piste textuelle préparée en attente
de préférence. Aucun bouton de recherche fondé sur le libellé estimé ne sera livré.

## Parcours attendu

Après capture ou import puis analyse locale, toucher un objet conserve la sélection
et son masque. La fiche indique que la catégorie est estimée et propose une recherche
visuelle avec texte et icône. Le second appui désélectionne comme auparavant ; les
choix entre masques superposés restent accessibles.

La recherche porte sur les pixels de l'objet sélectionné, avec son contexte proche,
plutôt que sur sa catégorie. Le périmètre effectivement transmis doit être visible
avant l'appui et l'envoi à Google doit être annoncé. L'original et le masque restent
dans la session locale ; une extraction pour la recherche ne remplace pas l'image
de travail. La catégorie estimée n'est pas utilisée comme vérité d'identification.

Une action explicite remet le recadrage à un gestionnaire Google Lens compatible,
qui réalise la recherche dans son propre contexte. L'utilisateur confirme que
l'icône image de Chrome ouvre Lens dans le parcours qui fonctionne. La sélection
seule ne transmet rien. L'échec de préparation ou
d'ouverture doit être récupérable, sans perdre photo, zoom, sélection ou original.
Le retour conserve ces éléments tant que le processus reste en mémoire ; aucune
restauration après destruction du processus ou historique persistant n'est promis.

## Faisabilité et sources

L'[aide officielle Google pour Android](https://support.google.com/websearch/answer/1325808?co=GENIE.Platform%3DAndroid&hl=fr)
décrit la recherche Lens depuis une image et le choix d'une zone. Elle ne fournit
pas de contrat d'intégration permettant à Blamera de précharger directement une
photo locale dans un onglet de navigateur.

Mozilla documente pour son navigateur une
[requête multipart vers Lens](https://github.com/mozilla-mobile/firefox-ios/issues/34457).
Cette source primaire décrit un usage par Mozilla ; elle ne constitue pas une
API publique garantie par Google. Le spike PC du 29 octobre effectue un unique envoi
d'une image géométrique synthétique 160 × 120 (1 924 octets JPEG), sans cookies,
authentification ni suivi de redirection. Google répond HTTP 302 vers une URL HTTPS
`www.google.com/search` avec des paramètres visuels. Cela démontre la réception et
la production d'une adresse candidate, pas l'ouverture ou la qualité de la page de
résultats sur le Xiaomi. Aucun contournement de protection, CAPTCHA ou refus réseau.
[Preuve locale sans identifiants de recherche](../../build/reports/validation/m2-object-actions/coder/lens-spike/probe-result.json).

L'[ADR 0007](../decisions/0007-google-visual-search.md) retient cette intégration
expérimentale et ses limites. Le transfert sera testé indépendamment sur des frontières
simulées avant la recette manuelle ; le spike n'en tient pas lieu.

Le retour réel 0.9.0 révèle une image cassée dans Google. Une recherche directe par
photo dans Chrome fonctionne sur le même téléphone. Le correctif 0.9.1 aligne
l'envoi sur le [formulaire fichier direct Chromium](https://chromium.googlesource.com/chromium/src/+/HEAD/chrome/browser/resources/new_tab_page/lens_form.ts) :
`https://lens.google.com/v3/upload`, seul champ `encoded_image`, paramètres publics
`ep=cntpubb`, `hl=fr`, `st`, `re=df`, `s=4`, `vpw`/`vph` égaux aux dimensions du JPEG.
Le champ Mozilla `processed_image_dimensions` n'est plus envoyé. Aucune variation
privée Chrome, cookie ou imitation de User-Agent n'est ajoutée. Le client reste natif
et le navigateur reçoit l'adresse Google validée ; aucun serveur local ni JavaScript
n'est livré. [Diagnostic, preuves et limites](../validation/m2-search-fix.md).

Ce correctif web ne suffit pas : l'utilisateur indique « non, toujours pareil ».
La décision courante est donc l'[ADR 0008](../decisions/0008-google-lens-share.md) :
partage Android public `ACTION_SEND image/jpeg` à Lens/l'application Google,
sans activité privée, endpoint web Blamera ou fallback vers l'ancien parcours.
Le JPEG est remis via un URI opaque de lecture seule avec `EXTRA_STREAM` et
`ClipData`. La résolution doit trouver un handler réellement compatible, exporté
et accessible dans les seuls packages `com.google.ar.lens` ou
`com.google.android.googlequicksearchbox`. Leur présence seule ne suffit pas.
[Validation du parcours natif](../validation/m2-lens-handoff.md).

## Contraintes et acceptation

- Gratuit, sans clé de service facturé ni hébergement public intermédiaire.
- Pas de transfert sur sélection, zoom, recadrage, rotation ou recomposition.
- Pixels préparés depuis l'image décodée courante ; aucun EXIF, chemin local,
  nom de fichier personnel, image d'une ancienne sélection ou masque superposé envoyé.
- Aucun appel HTTP par Blamera ; seul le partage explicite du recadrage vers le
  destinataire Google résolu. Aucune journalisation de photo, URI opaque ou donnée privée.
- Traitement hors UI, mémoire bornée, erreurs et délais bornés ; aucune relance
  réseau automatique. Une annulation ne promet pas d'effacer une image déjà reçue.
- Bornes retenues pour l'implémentation : rectangle de l'objet élargi de 5 % sur
  chaque côté puis borné à l'image ; côté long au plus 1 000 pixels sans agrandissement ;
  JPEG qualité 80, au plus 2 Mio ; aucune tentative automatique supplémentaire.
- Cache privé dédié, au plus deux JPEG et 4 Mio hors brouillon borné ; aucun
  enregistrement en galerie. URI `content` du provider Blamera, droit de lecture
  seul sans écriture, préfixe ou persistance. Métadonnées privées hors du chemin exposé.
- Expiration durable maximale de cinq minutes, contrôlée avant chaque nouvelle
  ouverture, y compris après reprise du processus. Fichier ou métadonnées invalides
  refusés ; un fichier ancien peut être évincé au quota avant cinq minutes. Aucun
  contrôle ne rétracte un descripteur déjà remis ou une copie par Google.
- L'absence ou le refus d'un destinataire est signalé dans Blamera. Le passage
  dans Lens conserve le fichier publié pendant sa durée disponible ; l'annulation
  avant remise interdit tout lancement tardif et nettoie les préparations abandonnées.
- Si aucune méthode exploitable n'est démontrée, exposer la limite et cadrer une
  alternative avec l'utilisateur plutôt que simuler une recherche visuelle.
- TDD pour le code de production, revue et testeur indépendants, contrôles qualité
  existants sans assouplissement. Le spike de faisabilité est une exception
  exploratoire bornée au TDD ; son code n'est pas repris sans tests adaptés.
- Recette manuelle sur Xiaomi après installation ; l'agent ne lance ni ne manipule
  l'application. Les résultats Google et leur pertinence restent externes à Blamera.

Les descriptions par IA dans Blamera, leur fournisseur et leur financement restent
à cadrer séparément dans M2-02/M2-03. Ce bouton ne constitue pas une API Google Lens
contractuelle ni une promesse de référence produit exacte.

L'essai ultérieur de l'application Gemini, avec recadrage et consigne et réponse
consultée dans Gemini, est cadré séparément dans [M2-05](gemini-image-share.md).
Sa disponibilité ne remplace pas la recette Lens acceptée ; le bouton définitif
Gemini dépend de la réception conjointe vérifiée par l'utilisateur.
