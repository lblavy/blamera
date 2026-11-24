# ADR 0012 — identification botanique complémentaire à YOLOE

31 octobre 2026 — M2-10, version cible 0.15.0/code28.
Décision demandée par l'utilisateur ; implémentation, revue et contrôles globaux
conformes sur PC, validation indépendante du testeur conforme. Version installée,
recette réelle ouverte ; la clé de l'utilisateur n'a pas été utilisée par les agents.

## Objectif et choix

Améliorer l'identification des plantes sans imposer de mode Général/Plantes et
sans remplacer les objets ou contours locaux. L'utilisateur privilégie la
pertinence, accepte un service distant et exige un usage gratuit.

Retenir l'API publique Pl@ntNet avec compte Free personnel, à clé saisie dans
Blamera. L'offre annonce 500 identifications par jour ; aucun contrat Pro ni
paiement ne doit être activé. Le service est indépendant de l'abonnement ChatGPT.
Les modèles ouverts Pl@ntNet-300K et BioCLIP restent des alternatives locales
non évaluées ici ; leur intégration Android n'est pas nécessaire pour ce parcours.

## Composition

Conserver l'inférence YOLOE actuelle et lancer une tâche botanique indépendante
à chaque capture, import ou analyse de photo, après configuration de la clé.
La demande explicite d'analyse commune autorise ces appels ; les simples gestes,
sélections, rotations et le direct caméra ne transfèrent rien à Pl@ntNet.
Une tâche distante lente ou en échec ne doit ni effacer les résultats locaux ni
prolonger leur verrou de calcul. Sans clé, le parcours local reste opérationnel.

L'API classique identifie une plante dominante par requête. Les candidats renvoyés
sont des alternatives d'identité, pas un inventaire des instances. Le service
multi-espèces est en bêta à accès limité et ne fonde pas cette livraison gratuite.
L'analyse d'une autre plante se fait par le recadrage déjà disponible.

## Identité des pixels et ChatGPT

Un résultat botanique est attaché à une image et à son rectangle exact. Il ne
renomme pas le masque d'un pot et n'enrichit pas la demande portant sur un autre
objet ou un autre cadrage. La consigne et l'hypothèse pertinente sont figées au
clic ChatGPT avec la cible, sans attendre nécessairement la botanique et sans
second envoi lors d'une réponse tardive.

Le seuil initial de score 0,5 est une politique prudente de présentation comme
hypothèse, à évaluer ; il ne démontre pas une probabilité calibrée de justesse.
Les données externes sont bornées et traitées comme données, jamais comme
instructions. Un résultat rejeté, faible, invalide ou obsolète n'enrichit pas le prompt.

## Données et limites

JPEG redimensionné sans EXIF, API HTTPS fixe, redirections et relances automatiques
interdites ; clé jamais compilée ni journalisée, stockage chiffré non sauvegardé.
Quota, délais, tailles et annulations sont bornés. La réponse 429 ou le quota nul
suspend les appels jusqu'au renouvellement UTC. Les requêtes d'identification,
y compris celles sans résultat, peuvent consommer le quota gratuit.

Les tests logiciels utilisent seulement des clés/réponses synthétiques sur PC.
L'utilisateur configurera sa clé et vérifiera le service sur Xiaomi. Le test de
transport ne prouve pas la qualité botanique : précision par espèce, confusions,
rejet des images sans plante et effet du contexte sur ChatGPT restent à mesurer.

## Sources et suivi

- [Compte et clé](https://my.plantnet.org/doc/getting-started/introduction)
- [Offre Free](https://my.plantnet.org/pricing)
- [Identification classique](https://my.plantnet.org/doc/api/identify)
- [Rejet et qualité des images](https://my.plantnet.org/doc/getting-started/faq)
- [Quota UTC](https://my.plantnet.org/doc/api/quota)
- [Bêta multi-espèces](https://my.plantnet.org/doc/api/survey)
- [Contrat détaillé](../specs/joint-plant-analysis.md)
- [Preuves de validation](../validation/m2-joint-plants.md)
