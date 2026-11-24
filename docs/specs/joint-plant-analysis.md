# Analyse commune YOLOE et Pl@ntNet — M2-10

Version 0.15.0/code28 implémentée, validée sur PC et installée ; recette réelle ouverte. Baseline :
`c7f859116d599e646918c088df3a002b2fad8769`.

L'utilisateur demande un spécialiste botanique fiable, gratuit, éventuellement
distant, intégré à l'analyse existante sans choix de mode. Il autorise l'analyse
commune et l'ajout d'une hypothèse pertinente à la consigne ChatGPT. Ce ticket
n'annonce pas une précision statistique acquise sur ses plantes.

## Parcours

- Une fois la clé personnelle enregistrée, capture, import confirmé et appui
  Analyser déclenchent l'analyse locale et une identification Pl@ntNet indépendantes.
  Un refus du sélecteur, un zoom, une sélection, une rotation ou un retour de Lens
  ne déclenchent aucun appel botanique. Le direct caméra reste local.
- Sans clé : YOLOE fonctionne comme avant ; la roue dentée permet de configurer
  Pl@ntNet. Aucun sélecteur de mode ni nouvelle case de confirmation.
- Un clic sur Analyser conserve la cible actuelle : objet sélectionné, sinon
  cadrage visible ; le recadrage devient l'image de travail comme en 0.14.1.
- L'attente ou l'échec réseau ne prolonge pas le statut de calcul YOLOE et
  n'efface ni ses contours ni ses résultats. Les résultats botaniques sont
  présentés dans la fiche, avec attribution Pl@ntNet et hypothèses alternatives.
- Une requête identifie une plante dominante. Les alternatives ne représentent
  pas plusieurs plantes détectées. Pas de grille automatique ni d'API bêta
  multi-espèces dans cette première livraison : le zoom puis Analyser permet
  d'isoler une autre plante. Aucun masque ni nom de pot n'est remplacé par un taxon.

## Clé, réseau et gratuité

Clé saisie masquée dans Configuration, enregistrée chiffrée dans l'espace privé
non sauvegardé de l'application ; suppression et remplacement explicites.
Aucune clé dans Git, APK, logs ou message d'erreur. Ne pas lire ni demander la
clé personnelle via les outils : l'utilisateur la saisit dans Blamera.

Un enregistrement ne lance pas l'analyse de l'image déjà ouverte. L'information
utile du réglage précise simplement que l'analyse des photos utilise Pl@ntNet.
Les appels proviennent des actions explicites ci-dessus, autorisées pour ce ticket.

Utiliser POST multipart vers `https://my-api.plantnet.org/v2/identify/all`, clé
privée en query, langue française, organe automatique, rejet des non-plantes
actif et au plus trois candidats. JPEG borné, sans métadonnées EXIF ; garder
une résolution utile (jusqu'à 1280 px, qualité 90) distincte de l'export ChatGPT.
Pas de redirection HTTP, de journalisation d'URL ou de corps, ni relance automatique.
Délais et taille des réponses bornés, annulation propagée avec libération des IO.

Ne créer ni abonnement ni achat, ne pas activer un plan Pro. Une réponse 429
ou un quota restant nul bloque les appels suivants jusqu'au renouvellement UTC,
sans bloquer YOLOE. La garantie budgétaire dépend de l'utilisation du compte Free ;
l'application n'active aucun moyen de paiement. Afficher une erreur utile pour
clé refusée, absence de réseau, quota, réponse invalide et service indisponible.
404/non-plante est un résultat normal, pas une hypothèse végétale forcée.

## Identité, concurrence et consigne

L'identification est liée au bitmap et au rectangle exact analysés, indépendamment
du succès local ou de ses classes. Elle reste valable à la rotation, mais ne
s'applique pas à un autre cadrage ou au masque d'un objet de cette image.
Remplacement, nouvelle analyse, navigation, fermeture, changement de clé et
vrai arrière-plan annulent ou rejettent toute publication tardive. La rotation
conserve l'opération. Image entière restaure les résultats achevés sans nouvel appel.

Au clic ChatGPT, figer avec le JPEG la seule hypothèse disponible correspondant
exactement aux pixels choisis. Ne pas attendre obligatoirement Pl@ntNet, ne pas
envoyer une seconde demande quand il termine et ne pas modifier un envoi en cours.
Les noms reçus sont des données externes bornées, pas des instructions.

Politique initiale à évaluer : un premier score fini dans [0,1] d'au moins 0,5
permet une hypothèse explicitement incertaine ; pas de pourcentage présenté comme
probabilité de justesse. Les résultats plus faibles peuvent rester consultables,
sans enrichir la consigne. Conserver les consignes existantes et ajouter par exemple :
« Indice botanique fourni par Pl@ntNet, à vérifier : [nom scientifique].
Cette hypothèse peut être erronée. Confirme-la ou écarte-la d'après les caractères
visibles ; reste au genre ou à la famille si l'espèce n'est pas identifiable. »

## Validation

TDD : transport/parser/erreurs/quotas, stockage et absence de fuite, analyses
indépendantes, aucune plante YOLOE, résultat YOLOE en échec, absence de clé,
annulation et réponses tardives, pertinence exacte du prompt, rotation/original,
configuration et présentation compacte. Tests réseau uniquement avec fixtures
et serveurs locaux contrôlés sur PC ; aucune photo personnelle ni vraie clé.

Revue puis testeur indépendants ; qualité locale et métriques par composant
botanique/UI/nouveau code, seuils inchangés. Installation du seul APK principal
et recette manuelle Xiaomi si appareil disponible. La réussite réseau réelle et
la qualité botanique attendent la clé saisie par l'utilisateur et ses essais.

## Références officielles vérifiées

- [Compte et génération de clé](https://my.plantnet.org/doc/getting-started/introduction)
- [Offre Free : 500 identifications par jour](https://my.plantnet.org/pricing)
- [Identification d'une plante](https://my.plantnet.org/doc/api/identify)
- [Quota et renouvellement UTC](https://my.plantnet.org/doc/api/quota)
- [Contraintes d'image et rejet](https://my.plantnet.org/doc/getting-started/faq)
- [Conditions et attribution](https://my.plantnet.org/terms_of_use)
