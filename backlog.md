# Blamera — backlog de travail

## Récapitulatif

| ID | Nom | Statut |
| --- | --- | --- |
| M0-01 | choisir la stack et établir les contraintes techniques | terminé |
| M0-02 | initialiser l'application et ses frontières | terminé |
| M0-03 | automatiser tests et qualité mobiles | terminé localement ; CI distante en attente |
| M0-04 | produire et installer l'APK manuellement | terminé |
| M0-05 | préparer le socle qualité de l'outil PC | terminé |
| M0-06 | documenter l'exécution et les décisions | terminé |
| M1-01 | sélectionner et mesurer le modèle de segmentation | clos par décision utilisateur ; profils actuels retenus, investigations reportées |
| M1-02 | importer une image et gérer ses erreurs | terminé |
| M1-03 | analyser l'image importée et afficher les catégories | terminé ; limites qualitatives consignées |
| M1-04 | intégrer le flux caméra | terminé ; deux paysages validés |
| M1-05 | analyser la caméra localement | clos ; parcours accepté, fluidité en suivi non bloquant |
| M1-06 | afficher les contours des objets | clos ; contours acceptés, fluidité en suivi non bloquant |
| M1-07 | explorer une image stable et sélectionner un objet | terminé ; parcours essentiel accepté |
| M1-08 | capturer ou importer une image et lancer son analyse | terminé ; parcours essentiel accepté |
| M1-09 | valider et démontrer le premier parcours hors ligne | clos par décision utilisateur ; recettes complémentaires reportées |
| M1-10 | affiner le cadrage, intégrer le direct et traduire les catégories | terminé ; parcours essentiel validé sur Xiaomi |
| M2-01 | sélectionner un objet et ouvrir sa fiche d'actions | terminé ; parcours essentiel Lens accepté sur 0.9.2 |
| M2-02 | étudier les services, financements et données transmises | clos ; ChatGPT Plus et Pl@ntNet Free retenus, aucun nouveau paiement |
| M2-03 | intégrer la demande d'information et les résultats | terminé — parcours essentiel accepté, évolué jusqu'à 0.15.1 |
| M2-04 | valider erreurs réseau et retour au parcours local | clos par décision utilisateur ; recette réelle des erreurs reportée |
| M2-05 | partager un objet et sa consigne avec Gemini | abandonné ; test retiré en 0.13.0 |
| M2-06 | essayer ChatGPT Plus avec une image et une consigne dans Blamera | terminé ; parcours synthétique validé sur Xiaomi en 0.10.4 |
| M2-07 | retrouver la connexion ChatGPT après réouverture | terminé ; parcours essentiel validé sur Xiaomi en 0.12.0 |
| M2-08 | simplifier la fiche, choisir la réflexion et afficher le flux ChatGPT | terminé ; parcours essentiel accepté sur Xiaomi en 0.13.0 |
| M2-09 | traiter le cadrage et regrouper les actions de photo | terminé — parcours essentiel accepté, cible automatique depuis 0.14.1 |
| M2-09-UI-01 | déduire la cible de la sélection et supprimer le choix de mode | terminé — validé sur PC et Xiaomi en 0.14.1 |
| M2-10 | analyser ensemble objets et plante, enrichir la consigne ChatGPT | terminé — parcours essentiel accepté sur Xiaomi en 0.15.0 |
| M2-10-UI-01 | afficher la saisie de clé, uniformiser les actions et compléter les diagnostics | terminé — parcours essentiel accepté sur Xiaomi en 0.15.1 |
| M3-01 | créer l'interface locale de gestion des personnes | terminé — galerie PC 0.2.0, parcours essentiel accepté |
| M3-02 | préparer et calibrer la galerie faciale | terminé — seuil figé, test PC mesuré et parité ARM64 confirmée en M3-06 |
| M3-02-A | détecter et confirmer les visages de référence sur PC | terminé — PC 0.4.0, préparation et reprise des références acceptées |
| M3-02-B | préparer le corpus et évaluer la reconnaissance sur PC | terminé — 15 cas évalués, 13 exploitables, aucun faux nom |
| M3-02-B-01 | évaluer avec un sous-ensemble de références et préparer les lots | terminé — calibration exécutée et lue, galerie conservée |
| M3-02-B-02 | figer les paramètres et évaluer le lot final indépendant | terminé — test exécuté sans nouveau réglage |
| M3-02-B-03 | déclarer le sujet attendu absent des visages détectés | terminé — 13 confirmations conservées, deux motifs non exploitables comptés |
| M3-02-B-CFG-01 | permettre de réinitialiser une sélection corrompue depuis l'interface | suivi non bloquant — réparation API disponible, données conservées |
| M3-02-A-BUG-01 | diagnostiquer l'erreur de profil après sélection des visages | clos — disparition après redémarrage et rechargement confirmée ; cause précise non établie |
| M3-02-TDD-01 | vérifier le Red avant le prochain changement de provisionnement | terminé — Red réels de provisionnement M3-04/M3-05 ; écart historique conservé |
| M3-03 | exporter et importer une galerie versionnée | parcours essentiel accepté — galerie intégrée à l'APK personnelle 0.17.0 |
| M3-03-A | exporter les prototypes du test final courant sur PC | terminé — PC 0.7.0 disponible, export personnel de deux identités produit |
| M3-03-B | importer la galerie sur Android | parcours intégré accepté sur 0.17.0 — import manuel facultatif non recetté séparément |
| M3-03-B-TDD-01 | vérifier le Red avant le prochain changement de lecture ou stockage de galerie | terminé — Red des incréments suivants vérifiés ; écart historique conservé |
| M3-04 | afficher le nom reconnu dans la scène | terminé — parcours connu/inconnu accepté et bilan POC M3-06 clos |
| M3-05 | afficher les labels de plusieurs personnes sur une photo | terminé — parcours essentiel accepté sur Xiaomi 0.18.0, labels seuls sans cadres |
| M3-05-QA-01 | fiabiliser les écritures atomiques face aux refus d'accès | terminé — reprise bornée revue, 38 tests indépendants et globale conformes |
| M3-05-COV-01 | compléter les gardes de lecture et d'effacement du fichier de session | clos — limite individuelle acceptée pour le POC ; composants et nouveau code conformes |
| M3-05-COV-02 | compléter l'intégration des labels dans la vue d'image | clos — limite individuelle acceptée pour le POC ; composant UI et rendu conformes |
| M3-06 | valider la reconnaissance et la préparation de bout en bout | terminé pour le POC — parcours accepté et mesures terrain consignées |
| M3-06-A | mesurer le moteur facial sur Xiaomi sans nouvelle APK | terminé — 0,42 s médiane un visage, 1,12 s trois ; premier lot trois 2,22 s |
| M3-06-LIMIT-01 | mesurer le pic mémoire supplémentaire et le délai complet d'affichage | clos par décision utilisateur — mesures non requises pour le POC accepté, limites conservées |
| M4-01 | étendre l'UI aux chats et préparer les jeux de données | à affiner ; vues et scènes en attente Q-02 |
| M4-02 | établir la référence par embeddings | à affiner |
| M4-03 | entraîner un classificateur sur extracteur gelé | à affiner |
| M4-04 | expérimenter un fine-tuning partiel, option | à affiner, conditionné à la faisabilité CPU |
| M4-05 | exporter l'approche retenue et permettre l'ajout d'un chat | à affiner |
| M4-06 | reconnaître les chats sur téléphone | à affiner |
| M4-07 | valider l'ensemble et documenter les comparaisons | à affiner |

## Situation actuelle — 2 novembre 2026

**Version installée sur Xiaomi : 0.18.0/code32 ; parcours multi-visages accepté.**
La galerie est intégrée à l'APK personnelle. Les labels de plusieurs visages, sans
cadres faciaux, sont livrés et le parcours essentiel accepté. M3 est clos dans le
périmètre du POC personnel ; les mesures approfondies non réalisées sont des limites
acceptées, sans chantier de validation restant ouvert pour ce seul motif.
Le parcours principal
est caméra → capture ou galerie → analyse de l'image stable → exploration et actions.
Sans sélection, les actions utilisent le cadrage visible ; une sélection utilise
le recadrage de l'objet. Modèle et réflexion ChatGPT se règlent dans Configuration.

| Milestone | Livré / état | Suite ou limites acceptées |
| --- | --- | --- |
| M0 — socle | Application installable, TDD, agents coder/reviewer/tester, contrôles locaux mobile/PC | CI distante volontairement reportée |
| M1 — vision | Clos par décision utilisateur ; capture/import, contours, catégories, zoom, sélection, analyse de zone et direct secondaire livrés | Limites de fluidité du direct et recettes approfondies acceptées dans le POC |
| M2 — informations | Clos par décision utilisateur ; Lens, ChatGPT persistant/streaming/Markdown/réflexion, Pl@ntNet et hypothèse contextualisée, finitions UI livrés | Limites de précision et recettes complémentaires acceptées dans le périmètre personnel |
| M3 — personnes | Clos pour le POC ; galerie conservée, test PC mesuré, APK 0.18.0 acceptée et moteur mesuré sur Xiaomi | Pic mémoire et délai complet non mesurés : limites acceptées, sans validation supplémentaire requise |
| M4 — chats | Intentions et expériences prévues au backlog ; aucune identification individuelle livrée | Préparer les données, comparer les approches CPU, exporter et intégrer la reconnaissance |

Dernier lot mobile 0.15.1 : 519 tests Android, 76 tests des scripts et quatre tests PC
réussis ; couverture Android 97,35 % lignes / 86,63 % branches, nouveau code
100 % / 97,06 %, aucune nouvelle duplication ou violation des limites de complexité.
Revue et testeur indépendants conformes. Le retour utilisateur sur 0.15.1 et les
seuls logs Blamera confirment identification Pl@ntNet publiée, prompt enrichi et
réponse ChatGPT HTTP200. Cela ne mesure pas la précision par espèce.
[Preuves courantes](docs/validation/m2-key-visibility.md).

Lot intégré M3-04 : revue et testeur indépendants conformes, **584 tests Android,
154 tests PC, 76 tests des scripts**, puis 80 tests Android ciblés indépendants
réussis. Android 97,22 % lignes / 86,90 % branches ; nouveau code 96,44 % / 84,93 %.
Runtime facial 95,57 % / 83,97 %, aucune duplication nouvelle ni contrôle assoupli.
Trois cas publics de parité passent sur émulateur ; ces mesures ne qualifient pas
encore ARM64 sur le téléphone. La galerie PC est inchangée ; seuls deux prototypes
issus des cinq références sont inclus dans l'APK personnelle locale, hors Git.
[Preuves intégrées et limites](docs/validation/m3-04-delivery.md).

Premier lot PC M3-01 : 26 tests PC réussis et cinq contrôles indépendants ;
couverture canonique 264 instructions / 52 branches à 100 %, duplication nouvelle
nulle, complexités et dépendances conformes. Création d'identité, vrai choix de
fichier, import et affichage vérifiés dans le navigateur sur données synthétiques.
[Preuves PC](docs/validation/m3-01-people-gallery.md).

Lot PC 0.3.0 M3-02-A : 58 tests et sept contrôles indépendants conformes,
couverture PC 99,47 % lignes / 98,33 % branches, nouveau code 99,19 % / 97,69 %,
duplication nouvelle nulle et audit conforme. Vrai navigateur testé sur portraits
publics : zéro/un/deux visages, choix explicite et références conservées. La
recette utilisateur restait à faire à cette étape ; aucune précision d'identité n'était annoncée.
[Preuves faciales](docs/validation/m3-02-face-preparation.md).

Évolution PC 0.4.0 : ArcFace ResNet50 retenu pour privilégier la qualité, sans
promesse de précision sur les proches avant calibration. 66 tests rejoués
indépendamment ; couverture PC 99,63 % / 98,02 %, nouveau code 98,88 % / 97,22 %,
duplication nouvelle nulle, revue et testeur conformes. Inférence publique réelle,
parité numérique et transition 128D→512D vérifiées. Serveur relancé et santé
HTTP200 ; galerie personnelle non inspectée. L'utilisateur confirme ensuite
la préparation de ses visages et la conservation des choix / confirmations
après rechargement ; parcours essentiel accepté.
[Preuves ResNet50](docs/validation/m3-02-resnet50.md).

PC 0.5.0 M3-02-B : 95 tests et six vérifications complémentaires indépendantes
conformes ; couverture PC 99,70 % / 98,13 %, nouveau code 99,80 % / 98,31 %,
duplication nouvelle nulle et audit conforme. Corpus distinct, séances, prototypes
et décisions exploratoires, rapport persistant et test final réservé non prédit.
Vrai navigateur et inférence native vérifiés sur données publiques isolées,
sans mesure de précision des proches dans ce premier lot. Depuis, la calibration
personnelle a été exécutée et le gel candidat appliqué en PC 0.6.0. Le test final
a ensuite été mesuré : 15 cas, dont 13 exploitables, sans nouveau réglage des seuils.
[Preuves d'évaluation](docs/validation/m3-02-evaluation.md).

PC 0.6.0 : revue indépendante conforme, 117 tests indépendants réussis, scénario
supplémentaire de quinze cas conforme. Couverture globale 99,55 % / 98,22 %,
nouveau code 98,31 % / 98,08 %, duplication nouvelle nulle. Paramètres appliqués
et figés dans l'évaluation séparée ; galerie principale vérifiée identique.
[Gel et test final](docs/validation/m3-02-final-test.md).

Décision explicite suivante : « tu peux clôturer M1/M2. commençons M3 ».
**M1 et M2 sont clos sur le périmètre accepté ; M3-01 est livré sur PC.** Les vérifications
et dettes ci-dessous sont reportées hors clôture, sans être déclarées exécutées
ou résolues. [Arbitrage et limites conservées](docs/decisions/0013-m1-m2-closure.md),
[contrat de la galerie PC](docs/specs/people-gallery.md).

### Suivis ouverts à planifier

Ces suivis conservent les limites déjà identifiées. Les pourcentages cités dans
les fiches détaillées sont historiques : une amélioration incidente de la
couverture globale ne vaut pas clôture de ces cas sans vérification dédiée.
Ils sont reportés hors M1/M2 clos et ne bloquent pas le développement M3.

| ID | Nom | Statut |
| --- | --- | --- |
| D-M1-01 | fluidité et cadence du direct | ouvert ; secondaire au parcours photo |
| D-M1-02 | qualité des détections en scènes riches et arbitrage des modèles | ouvert ; comparaisons disponibles, pas d'exhaustivité promise |
| D-M1-03 | ressources inutilisées de l'ancienne interface | dette secondaire ouverte |
| M1-09-VAL-01 | essais prolongés, hors ligne et budgets sur appareil | reporté hors clôture M1 ; non exécuté, à reprioriser |
| M2-03-LAT-01 | comparer qualité et latence selon le niveau de réflexion | ouvert ; choix et streaming livrés, comparaison non réalisée |
| M2-04-COV-01 | branches conditionnelles restantes de la fiche | dette de tests ouverte ; mesure historique à réévaluer |
| M2-04-VAL-01 | recette réseau/quota/délai/annulation sur Xiaomi | reportée hors clôture M2 ; non exécutée, à reprioriser |
| M2-08-COV-01 | branches rares HTTP et interface | dette de tests ouverte ; mesure historique à réévaluer |
| M2-09-COV-01 | branches restantes des actions sur cadrage | dette de tests ouverte ; mesure historique à réévaluer |
| M2-09-UI-02 | rendre visible le chargement au remplacement d'image | amélioration secondaire ouverte |
| M2-10-QUAL-01 | mesurer la précision botanique et l'apport au prompt | ouvert ; corpus avec vérité terrain à préparer |

## Historique du socle et de M1

Les paragraphes suivants retracent les versions antérieures ; la situation
actuelle et les fiches actualisées priment sur leurs décisions provisoires.
M0-01 et M0-02 sont validés ; M0-03 est
validé localement, la CI distante étant reportée par décision utilisateur. M0-04 est
validé : mise à jour personnelle installée et vérifiée manuellement par l'utilisateur.
M0-05 et M0-06 sont validés : **M0 est terminé localement**, avec CI distante volontairement reportée.
M1-02 et le parcours logiciel M1-03 sont validés ; l’utilisateur juge le fonctionnement
acceptable avec réserves sur les contours et une confusion chat/chien. Quatre mesures
applicatives initiales sont disponibles. Les contours image0.3.1 ont passé revue/tester
PC puis installation ; l’utilisateur confirme « Contours améliorés et bien placés ».
Les parcours et contours image/caméra sont déclarés OK le 27 octobre, avec
réserves sur la classification, les objets manqués et le rafraîchissement en mouvement.
Budgets, fluidité, essai prolongé et hors ligne restent ouverts : **M1 n’est pas entièrement clos**. M1-04 est validé sur PC et
installé en 0.4.0 ; aperçu et transitions confirmés, avec défaut de paysage inversé.
Le correctif 0.4.1 a passé revue et tester PC, sans installation séparée. Il est inclus
dans l’APK 0.5.0/code 8 installé le 27 octobre par mise à jour directe de 0.4.0 ;
M1-05 et les contours caméra ont passé les contrôles PC (102 tests Android, globale sortie 0).
Revue et tester indépendants sont conformes sur PC. Installation principale réussie,
`adb install -r` sortie 0 ; aucun lancement automatique ni autre manipulation par agent.
Le retour humain du 27 octobre accepte P*, O*, I* ; deux paysages validés, M1-04 clos.
Essai inférieur à 5 min, pas de chauffe ressentie, hors ligne non renseigné.
[Résultats déclarés et gabarit](docs/tests/m1-recette.md).
Le diagnostic YOLOE 0.5.1 a passé revue ciblée/sécurité et tester PC ; mise à jour du
seul APK principal réussie le 27 octobre depuis 0.5.0, après une première procédure
arrêtée faute de connexion. Après lancements manuels confirmés, un run complet CPU/PSS
est vérifié indépendamment : YOLOE ~2,07× en durée écoulée session.run/NMS sur ces deux
fixtures, PSS processus session ouverte supérieur. Ces coûts ne tranchent pas le choix :
YOLO11n maintenu provisoirement. L’évaluation annotée de 144 images est désormais validée :
apport YOLOE réel mais hétérogène, choix ouvert ; M1/D-M1-02 et budgets/prolongé restent ouverts.
Le 28 octobre, le parcours caméra par défaut → capture ou galerie → analyse automatique
sur image stable est confirmé, avec attente animée, zoom/déplacement et sélection colorée.
L'implémentation de M1-07/M1-08 est validée sur PC : revue, testeur indépendant et qualité
locale conformes en 0.7.0/code 11. Mise à jour du seul APK principal réussie le 28 octobre,
après reconnexion USB. Le retour global sur 0.7.0 puis « Tout fonctionne » sur les
adaptations 0.8.0 du 29 octobre acceptent le parcours essentiel M1-07/M1-08/M1-10.
Les variantes détaillées, le prolongé, le hors ligne et les budgets restent en M1-09.
YOLOE Large est intégré pour essayer le nouveau parcours photo ;
ce choix reste réévaluable et ne supprime pas le direct.
[Preuves et limites](docs/validation/m1-capture-analysis.md).
Retour humain sur 0.7.0 : « le reste a l'air de fonctionner », avec trois adaptations
suivies en M1-10 : réanalyser le cadrage après zoom/déplacement, activer le direct dans
la nouvelle interface et afficher les catégories en français. Ce retour global ne
constitue pas une validation individuelle de tous les cas de recette ni des budgets.
Sources : [idée initiale](docs/initial_idea.md), réponses récentes de l'utilisateur et
[directives de développement](AGENTS.md). Les décisions récentes priment sur l'idée initiale.

## Contraintes confirmées

- Téléphone cible unique : Xiaomi 14T Pro, HyperOS 3.0.305.0, Android 16.
- Développement et préparation sur cet ordinateur Windows, i7, 32 Go de RAM, sans GPU.
  Aucun entraînement cloud payant ni GPU sur ordinateur à supposer.
- Usage personnel ; APK installé manuellement. Pas de publication en store,
  de comptes propres à Blamera, de gestion de droits ou de multiutilisateur à ajouter.
  La connexion ChatGPT sert uniquement à ce service externe.
- Téléphone personnel protégé : seules installation/mise à jour de l'APK principal et lecture
  des logs Blamera sont autorisées. L'utilisateur lance/manipule l'application ; pas de test APK,
  instrumentation, pilotage, capture, effacement, désinstallation ou changement de paramètres.
- Aucun coût supplémentaire autorisé : ChatGPT utilise le parcours Plus retenu et
  l'allocation incluse, sans achat/activation de crédits ni repli sur une clé API payante.
  Pl@ntNet utilise l'offre Free avec clé personnelle et quota. Tout nouveau service
  payant ou changement de financement demanderait une décision distincte.
- Vision objets/contours locale. Après configuration volontaire d'une clé Pl@ntNet,
  capture/import/Analyser envoient l'image de travail pour identification botanique
  en parallèle ; sans clé, cette requête n'a pas lieu. ChatGPT et Lens sont déclenchés
  explicitement sur le cadrage visible ou l'objet sélectionné. Secrets et clés hors de Git.
- Point d'entrée commun, caméra par défaut ; capture ou galerie rejoignent la même
  image stable et son analyse automatique. Les accès Caméra/Galerie sont désormais
  des icônes dans l'en-tête ; Analyser/ChatGPT/Lens sont regroupés sous l'image.
- Les contours restent requis. Sur l'image stable, un appui sélectionne le masque avec
  remplissage semi-transparent ; un nouvel appui sur cet objet le désélectionne.
  Contour/remplissage utilisent une couleur stable par catégorie ; le nom apparaît en bas.
- L'attente associe une bande horizontale parcourant l'image verticalement en aller-retour
  et « Analyse en cours… » dans le panneau inférieur. Zoom et déplacement sont implémentés,
  avec validation PC et acceptation humaine du parcours essentiel.
  Voir le [parcours confirmé et les points à cadrer](docs/specs/capture-analysis.md).
- Le précédent report du gel en M2 est remplacé par ce parcours M1. L'analyse en direct
  déjà livrée reste documentée ; sa suppression et un usage exclusivement photo ne sont pas décidés.
- M2 fournit la description du cadrage ou de l'objet par ChatGPT dans la fiche,
  avec réponse progressive en Markdown. La recherche par image transfère le
  recadrage à Lens ; le transfert est accepté sur Xiaomi depuis 0.9.2. Les liens
  sources ne sont pas garantis par une description IA ; leur provenance doit rester
  honnête. Le test Gemini est retiré depuis 0.13.0.
- M3 concerne quelques proches, avec galerie préparée sur ordinateur et noms affichés
  automatiquement dans la même scène que les catégories d'objets et de chats.
- L'outil PC aura une **interface utilisateur locale**, pour personnes et chats,
  préparation des photos, lancement des expériences et export. Un outil CLI seul ne suffit pas.
- Les photos sont importées depuis des fichiers locaux ou des exports manuels.
  Aucun connecteur automatique Google Photos n'est présumé.

## Périmètre à confirmer et états

- **Prêt à cadrer** : intention assez claire pour détailler le ticket, sans autorisation
  implicite de coder avant choix techniques et contrat d'acceptation.
- **En cours** : travail démarré ; revue et validation encore nécessaires.
- **Terminé** : critères satisfaits, revue et validation indépendantes effectuées.
- **Clos par décision utilisateur** : périmètre restant explicitement accepté ou
  reporté par l'utilisateur ; les critères non vérifiés restent identifiés et ne
  deviennent pas une preuve de conformité technique.
- **Terminé localement ; CI distante en attente** : critères locaux validés ; essai
  distant explicitement reporté par l'utilisateur, sans résultat GitHub revendiqué.
- **À affiner** : epic ou ticket dont les critères techniques restent à préciser.
- **En attente de décision** : aucune implémentation de la partie dépendante avant réponse.
- Les dépendances ci-dessous donnent l'ordre nécessaire ; les numéros ne sont pas des dates.
  Les incréments optionnels ne bloquent pas un milestone sauf décision explicite de les y inclure.
- Dans chaque fiche, l'état en tête et les retours finaux font foi. Les paragraphes
  attachés à une ancienne version et leurs rapports liés sont des instantanés
  historiques ; ils ne réouvrent pas une installation ou une recette acceptée ensuite.
  La consolidation courante est la [validation 0.15.1](docs/validation/m2-key-visibility.md).

| Question / décision | État et réponse attendue ou confirmée | Impact |
| --- | --- | --- |
| Q-01 — actualisée | Décision initiale « Contours dès M1 ; gel de l’image plus tard », remplacée le 28 octobre par un parcours d'analyse sur image stable. | Contours conservés ; M1-07 revient en M1 pour exploration et sélection locale |
| Q-02 | Quelles vues de chats viser : tête, corps, profil, dos ; quelles scènes avec plusieurs chats ? À confronter aux données et benchmarks. | M4-01 et critères de reconnaissance |
| Q-03 — résolue | Capture et galerie réunies dès M1, avec analyse automatique sur image stable ; aucun enregistrement en galerie demandé. | M1-08 terminé ; variantes complémentaires suivies en M1-09 |
| Q-04 — résolue pour le périmètre actuel | ChatGPT via Plus et allocation incluse ; Pl@ntNet Free avec clé personnelle ; aucun nouveau paiement ni crédit activé. | M2-02/M2-10 ; rouvrir seulement si un changement de service ou de financement est proposé |

Les stacks mobile et socle PC sont retenues dans les [ADR 0001](docs/decisions/0001-technical-stack.md)
et [0002](docs/decisions/0002-pc-foundation.md). Le parcours photo utilise YOLOE26
Large PF (4 585 libellés) et le direct secondaire YOLO11n-seg (80 catégories).
Ces vocabulaires ne garantissent pas la détection exhaustive. Le choix définitif des modèles,
la cadence et les budgets de latence/mémoire/chauffe restent ouverts ; les frameworks
ML de préparation/entraînement seront choisis dans les tickets M3/M4 concernés.
Le remplissage semi-transparent du masque sélectionné et les couleurs par catégorie
sont implémentés pour l'image stable ; Large est intégré pour l'essai photo,
avec choix définitif et budgets d'usage réévaluables après recette.
La reconnaissance simultanée de plusieurs personnes et de plusieurs chats est une ambition
à évaluer, pas une garantie de la première itération.

Hors périmètre actuel : historique, shopping, recherche de produits similaires ou de référence
exacte, infrastructure multiutilisateur, publication publique et entraînement payant.

## M0 — socle technique et qualité

Livrable proposé : application minimale installable manuellement, décisions reproductibles
et contrôles qualité mobiles exécutables localement et en CI. Le socle PC sera complété
avant sa première fonctionnalité, sans retarder artificiellement M1.

### M0-01 — choisir la stack et établir les contraintes techniques

**État : terminé. Dépendances : aucune.**
Objectif : choisir une stack compatible avec le téléphone et le travail sur ordinateur CPU.
Acceptation : comparaison motivée Kotlin natif/Flutter/React Native ; choix mobile, contraintes
Android 16 et licences/coûts sourcés ; inventaire Windows et prérequis distingués des commandes
futures ; stratégie CPU et exports ONNX/LiteRT comparée sans modèle présumé ; faisabilité des
contrôles qualité explicitée sans prétendre les avoir installés ou exécutés.
Livrables validés : [ADR 0001](docs/decisions/0001-technical-stack.md) et
[environnement Windows](docs/development-environment.md). Versions exactes et compatibilité
compilée relèvent de M0-02 ; gates de M0-03 ; mesures des modèles de M1-01.

### M0-02 — initialiser l'application et ses frontières

**État : terminé. Dépendances : M0-01.**
Objectif : disposer d'une application minimale avec une structure simple et testable.
Acceptation : build reproductible ; écran minimal lancé manuellement par l'utilisateur sur
le téléphone, vérifié par son retour ou les logs Blamera ; frontières caméra, images, inférence
et présentation identifiables ; premier comportement logiciel en TDD et tests UI sur PC.
Aucun framework ou abstraction sans besoin concret.
Livrables pour revue : [frontières du socle](docs/architecture.md),
[environnement et commandes](docs/development-environment.md), [preuves TDD/PC et limites](docs/validation/m0-02.md).
L'utilisateur a confirmé le lancement manuel et les deux choix sur téléphone ; revue conforme,
validation du tester conforme.

### M0-03 — automatiser tests et qualité mobiles

**État : terminé localement ; CI distante en attente. Dépendances : M0-02.**
Objectif : rendre les règles d'AGENTS.md mesurables dès le socle mobile.
Acceptation : mêmes commandes reproductibles localement et en CI pour build, tests, formatage,
lint, analyse statique, dépendances et licences ; rapports conservés ; échec effectif des gates.
Configurer couverture lignes et branches ≥ 80 %, duplication du nouveau code ≤ 3 %,
complexités cyclomatique ≤ 10 et cognitive ≤ 15 avec revue des dépassements comme défini
dans AGENTS.md ; périmètres/exclusions explicites. Signaler toute métrique non supportée.
Livrables validés localement : [contrôles et périmètres](docs/quality.md),
[preuves M0-03](docs/validation/m0-03.md) et workflow GitHub fourni, sans exécution distante revendiquée.
Mobile et scripts de qualité ont des mesures de couverture séparées ; nouveau code non suivi inclus.
Revue et tester conformes. Périmètre amendé le 26 octobre à la demande de l'utilisateur :
la CI distante est mise en attente ; le workflow reste prêt, non publié et non exécuté.
La validation locale et les preuves de rejet satisfont le ticket dans ce périmètre.

### M0-04 — produire et installer l'APK manuellement

**État : terminé. Dépendances : M0-02, M0-03.**
Objectif : livrer une procédure de démonstration reproductible sur le téléphone cible.
Acceptation : APK principal construit et installé ; lancement manuel par l'utilisateur vérifié
par son retour ou les seuls logs Blamera ; mise à jour documentée sans désinstallation ni
effacement ; aucune instrumentation/test APK sur téléphone ; signature/secrets hors de Git.
Aucune publication en store requise ni modification des paramètres du téléphone autorisée.
Incrément : APK personnel de développement 0.1.1/code 2, même package et certificat
que la version installée. [Procédure](docs/install-android.md), [preuves et limites](docs/validation/m0-04.md).
Revue et tester PC conformes ; mise à jour `install -r` réussie, puis ouverture et
deux choix confirmés par l'utilisateur après manipulation manuelle.

### M0-05 — préparer le socle qualité de l'outil PC

**État : terminé. Dépendances : M0-01.**
Objectif : préparer l'outillage de l'interface locale et des pipelines CPU.
Acceptation : stack PC choisie et justifiée ; environnement reproductible ; tests et contrôles
locaux/CI couvrant ses composants selon AGENTS.md ; données personnelles exclues de Git.
La couverture PC ne sera pas masquée par une moyenne avec le mobile.
Périmètre cadré : Python autonome géré par uv, Streamlit local et écran d'orientation
Personnes/Chats sans import ni entraînement ; tests AppTest/HTTP local, format/types,
complexités, couverture et duplication mesurées ; inventaire runtime PyPI, OSV et licences.
La CI reste préparée mais non publiée/non exécutée selon la décision utilisateur.
Livrables validés : [ADR PC](docs/decisions/0002-pc-foundation.md), [guide autonome](docs/pc-development.md),
[qualité](docs/quality.md) et [preuves M0-05](docs/validation/m0-05.md).
Revue et tester conformes localement ; réserve mineure d'encodage des diagnostics corrigée.

### M0-06 — documenter l'exécution et les décisions

**État : terminé. Dépendances : M0-01, M0-04.**
Objectif : permettre de reconstruire et démontrer le socle sur cet environnement.
Acceptation : prérequis, versions, commandes et limites connus consignés ; décisions
structurantes motivées ; emplacement des rapports et procédure de validation explicités.
Compléter cette documentation à chaque milestone et lors du bootstrap PC.
Périmètre cadré : runbook principal sur ce Windows à partir des sources locales,
bootstrap autonome, reconstruction et commandes ciblées sans profil, démonstrations honnêtes,
rapports/dépannage, protection du téléphone et CI volontairement reportée.
Livrables validés : [runbook](docs/runbook.md) et [preuves M0-06](docs/validation/m0-06.md).
Revue et tester documentaires conformes ; aucune nouvelle passe globale ni preuve matérielle
revendiquée pour la clôture documentaire. La CI distante reste volontairement reportée.

## M1 — objets et catégories locales, caméra et image importée

Livrable proposé actualisé : caméra par défaut, capture ou galerie puis analyse automatique
hors ligne sur image stable, catégories et contours, zoom/déplacement et sélection locale.
L'analyse du flux déjà livrée conserve ses preuves et ses limites ; ce cadrage n'en décide pas le retrait.
Aucune identité personnelle ou de chat n'est attendue à ce milestone.

### M1-01 — sélectionner et mesurer le modèle de segmentation

**État : clos par décision utilisateur ; profils actuels retenus, investigations reportées. Dépendances : M0-01, M0-04.**
Clôture du 1er novembre : photo Large et direct minimal acceptés pour le périmètre
livré. Les budgets et évaluations ci-dessous restent des suivis non bloquants.
Objectif : choisir un modèle préentraîné d'instances adapté à l'appareil et aux exports CPU.
Acceptation : catégories, masques/rectangles/scores disponibles et licences vérifiés ; procédure
reproductible sur l'ordinateur sans GPU ; export testé ; inférence mesurée sur le téléphone.
Rapporter latence, mémoire, taille du modèle, comportement prolongé et chauffe observée.
Proposer cadence, qualité et budgets d'après les mesures, sans inventer une cible FPS.
Un spike d'évaluation reste distinct du code de production testé.
L'[étude](docs/research/m1-01-models.md) est complétée par un [spike YOLO11n-seg CPU](docs/validation/m1-01.md)
avec export ONNX, comparaison et mesures PC réelles ; revue/tester du spike conformes.
Quatre échantillons applicatifs téléphone existent désormais, sans protocole prolongé ni preuve de chauffe. La voie image retient YOLO11n-seg et ORT CPU dans M1-03 ;
son APK et ses contours ont passé revue et tester PC, puis ont été installés.
L’utilisateur rapporte un fonctionnement acceptable avec réserves qualitatives ;
budgets, comportement prolongé et chauffe restent à valider.
Le retour du 27 octobre sur 0.5.0 est qualitatif et inférieur à 5 min ; D-M1-02 suit
les erreurs/objets manqués en scène riche, sans changer modèle ou seuils.
L’[évaluation YOLOE CPU](docs/research/m1-yoloe-evaluation.md) a passé revue et tester ;
elle ne justifie pas un remplacement direct. Le [diagnostic 0.5.1](docs/validation/m1-yoloe-android.md)
prépare deux graphes comparables sur les fixtures publiques, modèle normal inchangé.
Qualité, revue ciblée/sécurité et tester indépendants conformes sur PC (119 tests Android).
APK 0.5.1 installé le 27 octobre par mise à jour du seul APK principal depuis 0.5.0,
sortie 0, après une première procédure arrêtée faute de connexion. L’utilisateur a
confirmé deux fins de comparaison ; un run complet CPU/PSS a été recalculé et vérifié
indépendamment. [Résultats et limites](docs/validation/m1-yoloe-android.md#mesure-xiaomi-contrôlée-dans-les-logs--27-octobre-2026) :
coûts de ce profil CPU/PSS mesurés, sans trancher le choix ni clore budgets/prolongé/M1.
La [comparaison qualité annotée](docs/research/m1-yoloe-quality-results.md) sur 128 LVIS +16 ADE
a été exécutée et validée indépendamment : bénéfice réel sur quelques nouvelles familles,
pertes sur communes, contours comparables sur mêmes GT. Tous les profils et inconnus sont
conservés ; pas de remplacement du modèle ni de modification de l’application.
La [comparaison des cinq tailles YOLOE26 PF](docs/research/m1-yoloe-capacity-results.md)
est validée ; elle éclaire le choix qualité sans clore les tickets appareil ni changer le modèle courant.

État actuel : après ces études, YOLOE26 Large PF a été intégré au parcours photo
de M1-07/M1-08 ; YOLO11n-seg reste utilisé pour le direct secondaire. Les décisions
précédentes de maintien de la baseline concernent les étapes historiques. Les
modèles sont utilisables ; budgets et comparaisons restent des investigations
reportées hors clôture M1 par la décision du 1er novembre.

### M1-02 — importer une image et gérer ses erreurs

**État : terminé. Dépendances : M0-04.**
Objectif : ouvrir une image locale indépendamment de la permission caméra.
Acceptation : sélection, annulation et remplacement de l'image ; orientation et dimensions
gérées ; formats/taille pris en charge documentés ; image invalide ou trop grande traitée
sans crash. Un refus de permission caméra ne bloque pas ce parcours.
Incrément cadré : Photo Picker ImageOnly, aperçu orienté ≤ 2048 px, source ≤ 30 Mio/100 MP,
erreurs explicites, annulation/échec conservant l'image précédente, décodage IO sérialisé
et référence seule restaurée. APK personnel 0.2.0/code 3 ; [preuves M1-02](docs/validation/m1-02.md).
Revue et tester indépendants conformes ; APK principal installé par l'orchestrateur,
`install -r` sortie 0. L'utilisateur a confirmé « Tout fonctionne » pour le choix de photo,
orientation/dimensions, remplacement, annulation conservant l'image et rotation manuels.

### M1-03 — analyser l'image importée et afficher les catégories

**État : terminé ; limites qualitatives consignées. Dépendances : M1-01, M1-02.**
Objectif : relier image, segmentation locale et résultats lisibles.
Acceptation : catégories connues, dont personne/chat si présentes dans le modèle retenu ;
scène sans objet et inférence en erreur gérées ; coordonnées cohérentes après orientation
et redimensionnement ; aucune identité inventée. L’incrément actuel livre aussi les contours
image de M1-06 ; la partie caméra reste dépendante de M1-05.
[Décision](docs/decisions/0003-image-segmentation.md), [preuves PC et installation](docs/validation/m1-03.md).
Revue et tester conformes ; installation principale par l’orchestrateur, `install -r` sortie0.
L’utilisateur rapporte « ça a l’air de fonctionner », mais des contours crénelés et un chat
classé chien ; le parcours logiciel est clos avec ces limites, sans précision générale promise.
Les nouveaux contours de M1-06 et leurs validations restent distincts.

### M1-04 — intégrer le flux caméra

**État : terminé ; deux paysages validés. Dépendances : M0-04.**
Objectif : afficher la caméra en respectant le cycle de vie Android.
Acceptation : permission accordée/refusée/révoquée ; arrêt/reprise et rotations vérifiés ;
ressources libérées ; erreurs accessibles ; bascule vers image importée toujours possible.
Incrément 0.4.0/code 6 : aperçu arrière CameraX 1.6.2, activation/permission explicites,
aucune capture ni analyse caméra. Revue et tester indépendants conformes : 78 tests
Android réexécutés puis globale sortie 0, avec 56 scripts et 4 PC. L’orchestrateur a installé
uniquement l’APK principal, `install -r` sortie 0. L’utilisateur confirme l’aperçu et
les transitions, mais le paysage
inversé présente un défaut. Correctif 0.4.1/code 7 : fullUser et suivi du Display sans
rebind ; revue et tester conformes (83 Android, Quality sortie 0). La 0.4.1 n’a pas été
installée séparément. Correctif inclus dans 0.5.0, mise à jour principale directe depuis
0.4.0 réussie le 27 octobre, sortie 0. O* déclaré OK le même jour : deux paysages,
pause/reprise et changement de source acceptés ; M1-04 terminé. O05 reste conditionnel,
refus/révocation non détaillés ; ces états sont testés sur PC, sans preuve de retrait
réel d’une permission Android.
[Preuves rotation](docs/validation/m1-04-rotation.md).
[Décision caméra](docs/decisions/0004-camera-preview.md), [preuves](docs/validation/m1-04.md).

### M1-05 — analyser la caméra localement

**État : clos ; parcours accepté, fluidité en suivi non bloquant. Dépendances : M1-03, M1-04.**
Objectif : réutiliser la chaîne de vision sur le flux en respectant les budgets mesurés.
Acceptation : catégories correctement positionnées ; traitement des images maîtrisé sans
accumulation ; rotations, scène vide et reprise validées ; mesures comparées à M1-01.
La caméra et l'image importée partagent les règles de prétraitement pertinentes.
Incrément 0.5.0/code 8 : analyse explicite start/stop, cadence provisoire maximale 1/s,
frame unique sans accumulation, projection CameraX ; 102 tests Android et qualité commune
sortie 0 ; revue ciblée et tester final conformes. Le tester a réexécuté les 102 tests
Android (38 s), puis Quality sortie 0. Mise à jour principale 0.5.0 réussie le 27 octobre,
`adb install -r` sortie 0. Aucun lancement automatique. P*, O*, I* déclarés OK le
27 octobre, mais rafraîchissement lent en mouvement ; essai court inférieur à 5 min,
sans chauffe ressentie. Pas de budget de performance ou de stabilité prolongée validé.
Sous-action **D-M1-01 — fluidité** : mesurer conversion, chargement/session, attente,
inférence/post-traitement et publication, puis évaluer cadence et éventuelle réutilisation
de session avec ownership sûr. Impact contours à suivre aussi dans M1-06. Aucune
implémentation, baisse d’intervalle, tuning ou promesse de vitesse décidée ici.
[Preuves PC](docs/validation/m1-05.md).
[ADR](docs/decisions/0005-live-camera-analysis.md), [recette appareil](docs/tests/m1-recette.md).

### M1-06 — afficher les contours des objets

**État : clos ; contours acceptés, fluidité en suivi non bloquant. Dépendances : M1-03, M1-05.**
Objectif : afficher les contours dès M1 sur la caméra et l'image importée.
Acceptation : alignement image/masques et lisibilité vérifiés sur les deux sources,
en rotation et après redimensionnement ; impact de performance mesuré.
Les contours sont conservés ; le remplissage semi-transparent de l'objet sélectionné
et les couleurs par catégorie sont implémentés en M1-07, validés sur PC en 0.7.0 ;
le parcours essentiel est accepté sur téléphone ; variantes détaillées suivies en M1-09.
La partie image0.3.0 a passé revue et tester PC puis installation ; l’utilisateur a signalé
son crénelage. L’incrément0.3.1 interpole l’isocontour des logits à0, en préservant trous,
composants et bords. Revue et tester indépendants conformes, 60 tests Android réexécutés
et globale sortie0 ; APK principal installé par l’orchestrateur, `install -r` sortie0.
Validation visuelle humaine0.3.1 : « Contours améliorés et bien placés ». La partie
image est validée ; le ticket global reste ouvert pour la caméra. Cette réponse ne
prouve ni réemploi effectif de la même photo, ni format précis, ni performance0.3.1.
[Preuves de cet incrément](docs/validation/m1-06-image.md). Les contours caméra 0.5.0
réutilisent ces isocontours ; développement, revue et tester PC conformes. Installation
0.5.0 réussie le 27 octobre ; contours jugés bons dans P*/O*/I*, mais fluidité en
mouvement à améliorer via D-M1-01. Impact/performance non clos. [Rapport caméra](docs/validation/m1-05.md).

### M1-07 — explorer une image stable et sélectionner un objet

**État : terminé ; parcours essentiel accepté. Dépendances : M1-03, M1-06.**
Objectif : proposer le même écran de résultats après capture et import.
L'identifiant historique est conservé ; son report en M2 est remplacé par ce périmètre M1.
Acceptation : zoom et déplacement sans relancer l'analyse ; image, contours, masques et
zones tactiles alignés ; appui sur un objet pour sélectionner son masque avec remplissage
semi-transparent et nom dans le panneau inférieur, nouvel appui sur le même objet pour
désélectionner. Contour et remplissage partagent une couleur stable par catégorie connue ;
les objets d'une même catégorie ont la même couleur. Une palette bornée ne garantit pas
une teinte unique pour toutes les catégories. Préciser le choix en cas de chevauchement
avant développement, sans sacrifier la désélection au second appui.
Image et résultats restent associés lors des changements de source et de cycle de vie ;
les résultats tardifs d'une autre image sont ignorés. Revenir au cadrage est possible.
[Spécification et critères détaillés](docs/specs/capture-analysis.md).

Implémenté en 0.7.0 ; revue finale sans blocage, qualité globale et testeur PC conformes.
Chevauchements, alpha/couleurs, toucher et zoom ont des tests réels sur fixture simulée.
Le retour global positif sur 0.7.0 puis la validation 0.8.0 du 29 octobre acceptent
le parcours essentiel. Les cas détaillés non rapportés individuellement restent
suivis en M1-09. [Rapport](docs/validation/m1-capture-analysis.md).

### M1-08 — capturer ou importer une image et lancer son analyse

**État : terminé ; parcours essentiel accepté. Dépendances : M1-02, M1-03, M1-04.**
Objectif : entrer par la caméra et rejoindre automatiquement l'analyse commune après
capture ou choix en galerie. Q-03 est résolue ; ce parcours est inclus en M1.
Acceptation : boutons avec texte et icônes usuelles ; orientation correcte ; annulation
du sélecteur sans perte de l'état précédent ; erreurs de capture/import/analyse et absence
d'objet explicites. Pendant le traitement, bande horizontale balayant l'image de haut
en bas puis de bas en haut et « Analyse en cours… » dans le panneau inférieur ; aucune
progression chiffrée fictive. L'animation s'arrête en succès, erreur ou annulation.
L'analyse démarre sur la nouvelle image sans second bouton ; Large est intégré pour
l'essai photo, les budgets d'usage restent à valider. Aucune sauvegarde implicite en galerie
ni transmission externe.
[Spécification et critères détaillés](docs/specs/capture-analysis.md).

APK 0.7.0 prêt après 175 tests Android, 76 scripts, 4 PC et validation indépendante.
Le délai complet n'est pas mesuré sur ce parcours ; les variantes d'orientation
restent à éprouver dans la recette complémentaire.
Après un premier préflight sans appareil, la reconnexion permet la mise à jour principale,
sortie 0 « Success ». Aucun lancement automatique. Le retour global positif sur
0.7.0 puis la validation 0.8.0 du 29 octobre acceptent le parcours essentiel,
sans valider individuellement tous les cas de recette ; suivi complémentaire en M1-09.
[Recette](docs/tests/m1-capture-analysis-recette.md).

### M1-09 — valider et démontrer le premier parcours hors ligne

**État : clos par décision utilisateur ; recettes complémentaires reportées. Dépendances : M1-03, M1-05, M1-06, M1-07, M1-08, M1-10, M0-06.**
Les essais ci-dessous ne sont pas déclarés exécutés : M1-09-VAL-01 conserve
prolongé/hors ligne/budgets hors clôture. Ils ne conditionnent plus M3.
Objectif : vérifier la cohérence des deux sources sur le téléphone réel.
Acceptation : démonstration sans réseau ; cas d'erreur et non-régression ; rapport des budgets
retenus, latence/mémoire/chauffe et limites du modèle ; gates et revue/validation indépendantes.
Vérifier les contours sur les deux sources et, après développement, le nouveau parcours
M1-07/M1-08 : capture/import, analyse automatique, attente, exploration et sélection.
Inclure aussi M1-10 : direct intégré, réanalyse de zone, retour à l'original et noms
français, dont le parcours essentiel de 0.8.0 est accepté par l'utilisateur le
29 octobre 2026 ; variantes complémentaires, prolongé et hors ligne restent ouverts.
Les preuves historiques ci-dessous ne valident pas ces nouveaux comportements.
La [recette manuelle française](docs/tests/m1-recette.md) propose priorité 5 min,
compléments et prolongé 5 min, 17 cas et fiche de retour vierges ; aucune mesure
matérielle inventée. Revue et tester PC conformes. Résultats humains du 27 octobre
consignés séparément : P*/O*/I* OK déclarés avec réserves ; L01 incomplet, L02 sans
chauffe ressentie sur essai court, L03 hors ligne non renseigné.
Sous-action **D-M1-02 — détection des scènes riches** (avec M1-01/M1-03) : évaluer sur
fixtures publiques/synthétiques autorisées les catégories/objets manqués et les effets
du plafond 20, des 300 candidats, score 0,35, NMS IoU 0,5 et entrée 640². Le modèle historique YOLO11n-seg
connaît 80 catégories, pas de libellé Arbre ; aucun score calibré ou exhaustivité
promis. Évaluation YOLOE CPU revue/testée ; 16 cas diagnostiques, rappel de la projection
figée vers 80 catégories distinct de toute reconnaissance sémantiquement correcte.
Diagnostic personnel 0.5.1 installé puis lancé manuellement le 27 octobre ; comparaison
CPU/PSS complète vérifiée. Choix ouvert, YOLO11n maintenu provisoirement : le bénéfice
qualité/vocabulaire élargi prime sur la latence seule. La comparaison annotée de 144 images
est désormais validée (288 forwards, 18 profils) : [résultats par catégorie, strates et contours](docs/research/m1-yoloe-quality-results.md).
Apport réel mais hétérogène, forte part de sorties non évaluées ; **D-M1-02 reste ouvert**,
sans réglage ou choix définitif déduit de cette étude.
Le motif coloré donne aussi baseline 0/PF 20 sur PC : différent du négatif uniforme,
pas de divergence ARM démontrée ni de score de qualité générale. Aucun réglage du parcours courant modifié.
Le [diagnostic personnel 0.6.0](docs/validation/m1-yoloe-capacity-phone.md) mesure
la comparaison baseline/Small/Large/Xlarge et un rapport durable réémissible ;
revue, qualité locale et tester final conformes ; APK principal installé le
28 octobre, comparaison manuelle et réémission confirmées puis validées indépendamment.
[Résultats CPU/PSS, parité PC et arbitrage qualité](docs/research/m1-yoloe-capacity-phone-results.md).
À ce stade historique, aucun choix n'était arrêté. Le parcours photo utilise
désormais YOLOE26 Large PF. M1 est clos sur décision utilisateur ; D-M1-02 et
les budgets restent des investigations non bloquantes reportées hors clôture.

### M1-10 — affiner le cadrage, intégrer le direct et traduire les catégories

**État : terminé ; parcours essentiel validé sur Xiaomi. Dépendances : M1-07, M1-08.**
Objectif : répondre au retour humain 0.7.0 sans changer le modèle de reconnaissance.
Acceptation : le bouton d'analyse en direct active/arrête les contours sur l'aperçu
actuel, avec capture et galerie disponibles et cycle de vie sûr ; aucune redirection
vers l'ancienne interface. Les catégories du parcours photo sont affichées en français,
à partir d'un catalogue local complet aligné sur les 4 585 identifiants ; données
anglaises de référence et diagnostics préservés. Aucun service payant ou traduction
réseau sur téléphone. Un bouton explicite relance l'analyse du cadrage visible après
zoom/déplacement ; l'utilisateur confirme une zone recadrée comme image de travail,
avec retour à l'original et ses résultats. Aucun relancement à chaque geste.
Tests TDD, revue et testeur indépendants,
qualité et recette ciblée requis avant clôture. Les prédictions peuvent rester erronées.

Livré sur PC en 0.8.0/code 12 : revue et testeur indépendants conformes, qualité
globale réussie (185 tests Android, 76 scripts, 4 PC). Catalogue français aligné,
direct intégré et recadrage/restauration vérifiés. Après reconnexion USB, mise à jour du
seul APK principal réussie le 29 octobre 2026, sortie 0 « Success ». Aucun lancement
automatique. Le même jour, l'utilisateur répond « Tout fonctionne » à la vérification
du direct dans la caméra actuelle, des noms français et de l'analyse de zone après
capture ou import avec retour à l'original et ses résultats. M1-10 est clos ; ce
retour ne valide pas les variantes complémentaires de la recette, un délai chiffré,
l'usage prolongé ou hors ligne. [Preuves et limites](docs/validation/m1-photo-refinements.md).

Dette secondaire **D-M1-03 — ressources de l'ancienne interface** : retirer les
ressources encore inutilisées après la suppression des pages Legacy (dix-neuf
initialement, dix-huit signalées par Lint en 0.9.0).
Impact limité à des données inutilisées, sans effet sur le parcours ; garder les
avertissements Lint visibles. Nettoyage local à effectuer dans un incrément ultérieur,
sans élargir ni retarder cette livraison validée.

## M2 — sélectionner un objet et obtenir des informations

Livrable proposé : depuis un objet sélectionné, déclencher explicitement une description
et des informations générales accompagnées de sources. Aucun historique ou achat prévu.
L'image stable et la sélection locale avec le nom sont désormais prévues en M1-07/M1-08 ;
M2 ajoute les actions et les informations externes après une demande explicite.

### M2-01 — sélectionner un objet et ouvrir sa fiche d'actions

**État : terminé ; parcours essentiel Lens accepté sur 0.9.2. Dépendances : M1-07, M1-10.**
Objectif : enrichir le panneau de l'objet sélectionné avec sa fiche d'actions.
Acceptation : réutiliser la sélection locale de M1-07 sans changer le second appui de
désélection ; conserver original, recadrage et masque ; fiche liée à la bonne image.
Les actions d'information restent distinctes de la simple sélection et exigent un
déclenchement explicite avant tout appel externe.

Choix utilisateur du 29 octobre : « ouvrir navigateur dans google image à la photo,
ce sera mieux. les descriptions/noms sont assez mauvais ». La piste de recherche
textuelle préparée en attente de réponse est abandonnée, sans livraison.
La fiche doit proposer une recherche visuelle de l'objet après action explicite,
en annonçant l'image transmise à Google. Après précision du parcours fonctionnel,
vérifier la remise directe du recadrage à Lens et ses résultats, sans API payante
ni hébergement public intermédiaire.
Échec récupérable et retour à la même image tant que le processus reste en mémoire.
Aucun choix de service IA ni dépense n'en est déduit.
Les validations prolongées/hors ligne de M1-09 restent ouvertes et ne bloquent pas
ce travail sur la sélection déjà acceptée.
[Contrat détaillé](docs/specs/object-information.md).

APK 0.9.0/code 13 préparé : aperçu du JPEG exact de l'objet avec contexte proche,
envoi Lens explicite sans libellé du modèle, puis résultats dans le navigateur.
Revue sans blocage après correction d'une annulation précoce ; contrôle global
conforme (207 tests Android, 76 scripts, 4 PC). Testeur indépendant conforme sur
43 tests ciblés et huit tests du lecteur ; package, signature et actifs inchangés
vérifiés. Après les préflights sans appareil, reconnexion confirmée par l'utilisateur
et mise à jour du seul APK principal réussie le 29 octobre 2026 : `adb -d install -r`,
sortie 0 « Success », taille et empreinte revérifiées avant envoi. Aucun lancement
automatique ; vraie page de résultats sur Xiaomi et retour à l'image restent à vérifier.
Retour humain après installation : « toutes les opérations marchent sauf la
recherche proprement dite. google image s'ouvre, mais l'image passée est vide. »
Le parcours local est accepté globalement ; W02 échoue et bloque la clôture de M2-01.
Diagnostic et correction en cours ; aucune cause unique n'est encore établie.
Chrome est confirmé par l'utilisateur : choisir une image directement dans Google
Lens depuis Chrome fonctionne, ce qui isole l'échec au transfert depuis Blamera.
Les sources Chromium révèlent ensuite un écart concret : Chrome utilise `/v3/upload`
pour l'envoi direct du fichier, tandis que Blamera utilisait `/upload`. Le correctif
minimal 0.9.1 vise cet endpoint et le protocole associé ; la piste d'un serveur local
est archivée et non livrée. Il n'est pas encore validé sur appareil ;
[contrat et preuves](docs/validation/m2-search-fix.md).
La revue est conforme et les contrôles globaux 0.9.1 passent : 208 tests Android,
76 scripts, quatre tests PC. Le parseur MIME indépendant confirme le JPEG exact,
sans EXIF, comme seul champ du formulaire. Testeur indépendant conforme sur 24 tests
ciblés et huit tests du lecteur, package et actifs inchangés vérifiés. Mise à jour
du seul APK principal réussie le 29 octobre, `adb -d install -r`, sortie 0 « Success »,
sans lancement automatique. Nouvel essai humain : « non, toujours pareil ». W02
échoue donc aussi en 0.9.1 ; l'alignement du protocole n'est pas suffisant.
La correction du transfert reste ouverte, aucun succès Google réel déduit des tests PC.
L'utilisateur précise ensuite que l'icône image de Chrome ouvre Lens, où l'import
fonctionne. La version 0.9.2 implémentée et revue vise ce parcours natif : partage Android
du seul JPEG d'aperçu à un destinataire Google compatible, accès URI temporaire et
cache privé borné, avec retrait du client HTTP et de `INTERNET`.
Revue sans blocage ; qualité globale réussie (216 tests Android, 76 scripts, quatre
tests PC), nouvelles lignes/branches couvertes à 99,19 %/83,05 %, sans duplication
nouvelle ni dépassement de complexité. Testeur indépendant conforme sur 35 tests
ciblés et huit tests du lecteur ; APK, signature et actifs vérifiés. Mise à jour
du seul APK principal 0.9.2/code 15 réussie le 29 octobre, `adb -d install -r`, sortie 0
« Success », sans lancement automatique. Après l'essai demandé sur la recherche
Lens et le retour à Blamera, l'utilisateur répond « ça a l'air de marcher » le
29 octobre : parcours essentiel accepté, M2-01 terminé. Ce retour global ne prouve
pas séparément toutes les variantes de la recette, notamment le détail du zoom au
retour ; elles restent une référence de régression, et les cas d'erreur détaillés
relèvent de M2-04.
[Décision](docs/decisions/0008-google-lens-share.md),
[preuves et limites](docs/validation/m2-lens-handoff.md).
[Preuves et limites](docs/validation/m2-object-actions.md),
[recette visuelle](docs/tests/m2-object-actions-recette.md).

### M2-02 — étudier les services, financements et données transmises

**État : clos ; ChatGPT Plus et Pl@ntNet Free retenus, aucun nouveau paiement autorisé. Dépendances : M1-09.**
Objectif : comparer un parcours gratuit/hors IA et les services IA réellement accessibles.
Acceptation : documentation officielle des API, financement et limites vérifiée à ce moment ;
contenu transmis explicite ; comparaison coût/pertinence ; décision utilisateur avant dépense.
La possibilité initialement évoquée de crédits/API séparés n'a pas été retenue ;
le parcours utilise la connexion ChatGPT Plus étudiée en M2-06.
Les pistes initiales de recherche visuelle sans clé ni API facturée et leurs
limites figurent dans l'[ADR 0007](docs/decisions/0007-google-visual-search.md).
Le transfert actuel utilise Lens, accepté en M2-01 après ces essais web.
L'historique de Gemini ci-dessous précède les décisions Plus/Pl@ntNet.
L'utilisateur précise ensuite privilégier la pertinence à l'exécution locale et
demande un essai de transfert vers l'application Gemini gratuite, suivi dans M2-05.
Cet essai ne choisit ni API payante ni abonnement et n'intègre pas la réponse dans Blamera.

Décisions suivantes : parcours ChatGPT Plus intégré et accepté jusqu'à M2-09-UI-01 ;
Pl@ntNet Free autorisé pour l'analyse commune M2-10, avec clé personnelle, quota
quotidien, envoi du cadrage et restitution dans Blamera. Aucun achat ni contrat
payant supplémentaire. L'historique ci-dessus décrit les pistes antérieures.

### M2-03 — intégrer la demande d'information et les résultats

**État : terminé — parcours essentiel accepté, évolué jusqu'à 0.15.1. Dépendances : M2-01, M2-02, M2-06.**
Objectif actuel : envoyer le cadrage visible ou le recadrage de l'objet sélectionné
après action explicite et afficher la réponse dans la fiche. Markdown livré dès
0.11.0 ; session persistante, streaming/réflexion et cible automatique livrés
par M2-07/M2-08/M2-09-UI-01. Hypothèse botanique contextualisée confirmée en 0.15.1.
Les recettes d'erreurs M2-04 restent distinctes du parcours heureux accepté.

Historique de l'intégration initiale 0.11.0 : les attentes d'installation/recette
ci-dessous décrivent les étapes précédant le retour humain final de cette fiche.
Acceptation : description, informations et liens consultables ; provenance honnête et absence
de source signalée, liens jamais inventés ; clés hors de Git ; aucun envoi automatique.
L'essai synthétique M2-06 est validé sur Xiaomi en 0.10.4. La suite à cadrer ici
est l'envoi du recadrage sélectionné avec sa consigne et l'affichage de la réponse
dans la fiche. Suivi UI M2-03-UI-01 : rendre lisible le Markdown renvoyé ; les
marqueurs `**` restent visibles dans l'essai. C'est un défaut de présentation
secondaire, sans blocage du transfert ni de la compréhension du résultat.
L'utilisateur demande maintenant l'intégration et son déploiement : recadrage et
consigne visibles, connexion et envoi explicites, réponse dans la fiche et rendu
Markdown partagé avec l'essai. Les gardes de crédits restent celles de M2-06.
[Contrat et critères](docs/specs/object-chatgpt.md). Version 0.11.0/code23 prête :
recadrage JPEG exact et consigne, envoi explicite, session retenue à la rotation,
réponses associées à la sélection et Markdown natif. Revue conforme, 164 ciblés
verts puis globale 349 Android/76 scripts/quatre PC. Nouveau code 98,19 % lignes /
88,69 % branches, intégration seule hors Markdown 98,06 % / 83,33 %, duplication
nouvelle 0,140 %, complexités conformes. M2-03-UI-01 est corrigé sur PC ; la recette
réelle reste à faire après installation. [Preuves](docs/validation/m2-object-chatgpt.md).
Le testeur indépendant confirme 195 ciblés Android et huit lecteur verts, le
package/signature/permissions, les 37 actifs précédents inchangés et la licence
additionnelle CommonMark. Après le préflight sans téléphone, reconnexion USB et
mise à jour du seul APK principal réussie le 31 octobre, `adb -d install -r`,
sortie 0 « Success », sans lancement automatique. Aucune source modifiée depuis
les validations PC ; le parcours réel reste à vérifier par l'utilisateur.
Retour humain : le parcours semble correct. La répétition de la connexion est
confirmée uniquement après fermeture/réouverture, conséquence de la conservation
en mémoire du prototype. Le parcours essentiel M2-03 est accepté ; la nouvelle
mémorisation protégée est traitée dans M2-07, avec validation matérielle dédiée.

### M2-04 — valider erreurs réseau et retour au parcours local

**État : clos par décision utilisateur ; recette réelle des erreurs reportée. Dépendances : M2-03, M1-07.**
La recette ciblée non exécutée reste M2-04-VAL-01, hors clôture M2.
Objectif : préserver l'usage local si le service échoue.
Acceptation : absence de réseau, erreur/quota, annulation et délai dépassé testés ; retour
caméra/image cohérent ; description distinguée d'une identification certaine ; aucune
recherche shopping ou référence produit exacte ajoutée. Démonstration et limites documentées.
Vérifier le gel, la sélection et la reprise caméra, ainsi que le parcours sur image importée.
Les retours favorables des versions récentes et les quatre événements 0.15.1
confirment le parcours heureux. Ils ne valident pas individuellement absence de
réseau, quota, délai dépassé, annulation et reprise. Une recette ciblée sur la
version courante pourra être repriorisée dans M2-04-VAL-01, hors clôture M2,
sans réinstaller une ancienne version.
Suivi secondaire M2-04-COV-01 : compléter les chemins conditionnels restants du
fichier ObjectInformationCard (77/100 branches sur la campagne 0.11.0). Le nouveau
code d'intégration et les contrôles globaux satisfont leurs seuils ; aucune
exclusion ni baisse de règle n'est appliquée à ce suivi.

### M2-05 — partager un objet et sa consigne avec Gemini

**État : abandonné ; test et code inutile retirés en 0.13.0 dans M2-08. Dépendances : M2-01.**
Retour du 31 octobre : supprimer le test Gemini de l'application. Le diagnostic
et ses limites restent documentés ci-dessous ; aucun nouvel essai n'est prévu.
Demande du 29 octobre : tester le transfert du recadrage et de la consigne vers
l'application Gemini, puis ajouter « Demander à Gemini » si cela fonctionne.
Priorité à la pertinence, sans exigence de traitement local ; application Gemini
gratuite dans ses limites, aucun achat ni API facturée.

Première étape : diagnostic provisoire « Tester Gemini » dans l'APK principal
0.9.3/code 16. L'utilisateur doit retrouver le bon JPEG et la consigne préremplie
ensemble, sans copie ni saisie manuelle ; ouvrir Gemini seul ne suffit pas. Partage
Android public ciblé, seul URI en lecture et cache existant ; Lens reste disponible.
La réponse se consulte dans Gemini. Aucun envoi automatique, changement de modèle,
réglage téléphone ou installation tierce. Qualité, revue et testeur sur PC avant
installation ; réception réelle vérifiée par l'utilisateur. Le bouton définitif
reste conditionné à ce résultat.
Le diagnostic est implémenté et revu. Après correction d'une régression de visibilité
du panneau détectée par les anciens tests, la qualité globale passe : 232 tests
Android, 76 scripts, quatre PC ; nouveau code à 100 % des lignes et 83,33 % des
branches, sans duplication nouvelle ni dépassement de complexité. Testeur
indépendant conforme sur 66 tests ciblés et huit tests du lecteur ; package,
signature et actifs inchangés vérifiés. Mise à jour du seul APK principal réussie
le 29 octobre 2026, `adb -d install -r`, sortie 0 « Success », sans lancement
automatique. Retour humain : « Image reçue, consigne absente ». L'image est donc
reçue sur ce téléphone, mais le transfert conjoint échoue ; aucun bouton définitif.
Une variante publique 0.9.4/code 17 est prête : joindre la même consigne
à l'unique élément ClipData portant le JPEG, en conservant EXTRA_TEXT, comme le
fait AndroidX. Cette hypothèse ne garantit pas le comportement du récepteur Gemini.
Revue et testeur indépendants conformes ; qualité globale réussie (233 tests
Android, 76 scripts, quatre PC), 67 tests ciblés et huit du lecteur réussis.
Cinq nouvelles lignes couvertes à 100 %, aucune branche nouvelle, sans duplication
nouvelle ni dépassement de complexité ; package et actifs vérifiés. Au préflight
du 29 octobre, zéro téléphone détecté : aucune installation 0.9.4 tentée à ce stade.
Après reconnexion confirmée, sources et APK restent identiques aux preuves et la
mise à jour du seul APK principal réussit le 30 octobre 2026, `adb -d install -r`,
sortie 0 « Success », sans lancement automatique ni autre mutation. Essai réel
image ET consigne demandé ; M2-05 reste incomplet, sans bouton définitif.
Le 30 octobre, l'utilisateur répond : « Toujours pas de texte dans gemini. L'image
y est ». Le recadrage est reçu dans les deux essais, mais la consigne n'est pas
préremplie dans ce parcours. Les tests du format émis ne valident donc pas le
critère produit. Fin de la série bornée de variantes publiques : aucune nouvelle
tentative arbitraire ni bouton définitif. Choix demandé entre une étude de l'API
Gemini (éligibilité gratuite, quotas et clé à vérifier, sans facturation activée)
et un parcours manuel explicitement accepté avec copie puis collage de la consigne.
Aucune de ces alternatives n'est encore implémentée ou choisie.
[Preuves 0.9.4](docs/validation/m2-gemini-clipdata.md).
[Contrat et recette](docs/specs/gemini-image-share.md),
[preuves](docs/validation/m2-gemini-share.md).

### M2-06 — essayer ChatGPT Plus avec une image et une consigne

**État : terminé ; connexion, catalogue et réponse à l'image synthétique validés sur Xiaomi en 0.10.4 le 31 octobre. Dépendances : M0-02.**
Demande du 30 octobre : après recherche de faisabilité et exemples concrets,
implémenter un essai limité dans Blamera avec le compte Plus, sans activer de
crédits payants. L'écran présente une image synthétique non personnelle et sa
consigne, ouvre la connexion officielle dans le navigateur, propose les modèles
du compte et affiche la réponse à une demande explicite dans Blamera.

Les paragraphes suivants décrivent le prototype 0.10.x et ses diagnostics
successifs. Ses confirmations et son stockage uniquement en mémoire ont depuis
été remplacés dans le parcours principal par M2-07/M2-08 ; ils ne décrivent pas
l'interface actuelle. Le recadrage réel et le Markdown sont livrés dans M2-03.

L'allocation incluse est partagée avec Codex / ChatGPT Work. L'application n'achète
ni n'active de crédits et ne se replie pas sur une clé API. Le réglage serveur
d'usage des crédits après limite n'est pas contrôlable par un paramètre documenté :
une vérification humaine de sa désactivation est exigée avant l'envoi. Si elle
ne peut pas être faite, l'envoi reste bloqué. Les jetons sont limités à la mémoire
de la session, les erreurs et annulations sont récupérables, sans nouvelle tentative
automatique d'inférence. INTERNET est nécessaire à ce seul parcours explicite ;
les analyses de vision restent locales. Pas de modification du téléphone en dehors
de la mise à jour principale autorisée, lancement et essai réalisés par l'utilisateur.

L'image personnelle recadrée et l'intégration à la fiche d'objet restent une suite
conditionnée à ce premier succès ; les choix et limites M2-05 restent consignés.
Deux corrections contractuelles de revue (annulation pendant acquisition du
listener, délais absolus) et la calibration d'une fixture de récupération du
direct sont validées. Qualité globale : 286 tests Android, 76 scripts, quatre PC ;
nouveau mobile 96,99 % lignes / 88,55 % branches, duplication nouvelle 0,389 %,
aucune violation des complexités 10/15 ni changement de règles. Testeur indépendant :
58 tests ciblés et huit du lecteur conformes, package/signature/37 assets-bibliothèques
contrôlés. Mise à jour du seul APK principal réussie le 30 octobre 2026,
`adb -d install -r`, sortie 0 « Success », sans lancement automatique.
L'utilisateur réalise la connexion et le premier envoi ; aucun succès réel n'est
déduit de ces preuves PC. [Preuves](docs/validation/m2-chatgpt-plus-trial.md).
Retour humain : la connexion ChatGPT et l'ajout de Blamera au portail ont lieu,
mais le navigateur reste en chargement et les contrôles Blamera restent grisés.
La réception du callback et la fin de connexion ne sont donc pas établies.
Aucun envoi d'image ni résultat réel confirmé ; diagnostic ciblé avant nouvel essai.
Le correctif/diagnostic 0.10.1/code19 tolère les préconnexions abandonnées et les
accusés HTML impossibles, sans perdre code/refus validé. Il expose les étapes
non secrètes et borne toute la connexion/catalogue à cinq minutes avec annulation
visible. Revue et testeur conformes sur PC ; globale 295 tests Android, 76 scripts,
quatre PC, nouveau code 95,83 % lignes / 87,10 % branches, duplication nouvelle nulle.
Testeur : 67 ciblés et huit lecteur verts, package/signature/actifs contrôlés.
Mise à jour du seul APK principal réussie le 30 octobre (`adb -d install -r`, sortie 0),
sans lancement automatique. La cause du blocage humain et le succès du retour réel
ne sont pas déduits des simulations PC. [Preuves du diagnostic](docs/validation/m2-chatgpt-callback.md).
Nouveau retour humain : page locale « Revenez dans Blamera… » affichée, puis
erreur de connexion au service. Les seuls événements du PID Blamera confirment
CALLBACK_RECEIVED puis EXCHANGING_CODE, suivi d'une erreur NETWORK environ 41 ms
plus tard. Le retour OAuth arrive donc dans cet essai, mais la finalisation
échoue avant vérification du compte/catalogue. Le type réseau ou le statut HTTP
ne sont pas encore discriminés ; aucune cause ni réussite d'inférence n'est présumée.
0.10.2/code20 vérifie l'accès public avant navigateur, conserve la découverte en
mémoire pour la validation, et journalise uniquement enums d'endpoint/stade/catégorie,
statut HTTP borné et cycle de vie propre. HTTP5xx devient service indisponible,
sans changement OAuth/TLS/permission ou achat. Les tests au fil PC confirment le
formulaire et l'ancienne confusion503, sans prouver TLS Android. Revue et testeur
conformes ; globale 304 tests Android, 76 scripts, quatre PC ; nouveau code
92,25 % lignes / 91,36 % branches, duplication nouvelle nulle. Testeur : 76 ciblés
et huit lecteur verts, package et 37 assets/libs inchangés vérifiés. Mise à jour
du seul APK principal réussie le 30 octobre (`adb -d install -r`, sortie 0), sans
lancement automatique. [Preuves réseau](docs/validation/m2-chatgpt-network.md).
L'essai 0.10.2 confirme HTTP200 sur l'accès public au premier plan, puis une erreur
DNS à TOKEN/OPEN_OUTPUT pendant PAUSED, avant le retour RESUMED de Blamera. La
restriction système exacte n'est pas supposée. Le correctif 0.10.3 attend le
retour au premier plan avant finalisation, sous le même budget et les mêmes gardes,
sans changement de réglage ou de permission. [Contrat](docs/validation/m2-chatgpt-foreground.md).
Revue et testeur conformes sur PC ; globale : 311 tests Android, 76 scripts,
quatre PC ; nouveau code 100 % lignes/branches, duplication nouvelle 0/81,
aucune violation de complexité. Testeur : 83 ciblés et huit lecteur verts,
package/certificat/37 actifs inchangés vérifiés. Mise à jour du seul APK principal
réussie le 30 octobre (`adb -d install -r`, sortie 0), sans lancement automatique.
Le succès de connexion/catalogue puis de l'image réelle demeure une preuve humaine.
Retour réel 0.10.3 : TOKEN et JWKS HTTP200 après retour RESUMED, puis catalogue
HTTP200 refusé par le garde de lecture à 256 Kio, avant parsing. L'échange et la
vérification du compte ont progressé ; ni les choix de modèles ni une réponse
d'image ne sont encore validés. Correction bornée 0.10.4 installée, sans lecture
du contenu personnel du catalogue. Revue et testeur conformes ; globale 319 tests
Android, 76 scripts, quatre PC, nouveau mobile 100 % lignes/90 % branches,
duplication nouvelle 0/17, aucune violation de complexité. Testeur : 91 ciblés
et huit lecteur verts ; package, certificat et 37 actifs inchangés vérifiés.
Mise à jour du seul APK principal réussie le 30 octobre (`adb -d install -r`,
sortie 0), sans lancement automatique. [Preuve et contrat](docs/validation/m2-chatgpt-catalog.md).
Retour humain du 31 octobre : la capture fournie montre le catalogue, la
confirmation de vérification des crédits cochée, « Analyse terminée » et une
réponse française pertinente décrivant la tasse bleue, son anse et le mot BLAMERA.
Le parcours synthétique de bout en bout est validé. Le modèle effectivement choisi
et la latence ne sont pas visibles ; aucune mesure de performance ou vérification
serveur de facturation n'est déduite. L'intégration du recadrage réel reste M2-03,
avec amélioration du rendu Markdown ; M2 dans son ensemble reste ouvert.
[Contrat](docs/specs/chatgpt-plus-trial.md),
[décision](docs/decisions/0009-chatgpt-plus-trial.md),
[recette](docs/tests/m2-chatgpt-plus-recette.md).

### M2-07 — retrouver la connexion ChatGPT après réouverture

**État : terminé ; parcours essentiel validé sur Xiaomi en 0.12.0. Dépendances : M2-03, M2-06.**
Après le retour 0.11.0, mémoriser la session du parcours principal et son modèle
pour ne plus refaire l'autorisation à chaque ouverture. Stockage privé AES-GCM
avec clé Android Keystore, hors sauvegardes, sans photos/consignes/réponses.
Le lot ci-dessous décrit 0.12.0 ; les anciennes cases ont été supprimées dans
M2-08. Le fonctionnement actuel conserve la session et propose un envoi direct.
Reprise et renouvellement des jetons sans navigateur tant que la session reste
renouvelable, refus/révocation récupérables et déconnexion bloquant toute reprise,
avec erreur explicite si l'effacement local échoue. Aucune inférence implicite ; la confirmation des crédits reste
décochée après réouverture. Version prévue 0.12.0/code24.
[Décision et critères](docs/decisions/0010-chatgpt-session-retention.md),
[preuves](docs/validation/m2-chatgpt-session.md),
[recette](docs/tests/m2-chatgpt-session-recette.md).

Retour humain du 31 octobre : « ça fonctionne ». La mémorisation après
réouverture est acceptée ; aucune mesure de latence ni validation de tous les
cas d'erreur n'est déduite de ce retour. La suppression des cases est traitée
dans le nouveau contrat M2-08.

Suivi distinct M2-03-LAT-01 : l'utilisateur juge GPT-6.1 Sol lent. L'app omet
`reasoning.effort`, donc applique le défaut documenté `medium`, et n'affiche la
réponse qu'après réception complète. Prévoir une comparaison `low`/`medium` sur
les mêmes images et étudier l'affichage progressif, sans présumer une qualité
identique. Aucun changement d'effort ou de mode payant dans M2-07. Le choix
d'effort et le streaming sont désormais demandés et implémentés dans M2-08 ;
la comparaison de qualité à images identiques reste une investigation distincte.

### M2-08 — simplifier la fiche et afficher la réponse progressivement

**État : terminé ; parcours essentiel accepté sur Xiaomi en 0.13.0. Dépendances : M2-03, M2-07.**
Usage personnel : aucune case ni disclaimer répété, sélecteurs de modèle et
réflexion côte à côte avant l'action d'envoi. Réponse Markdown reçue et affichée
progressivement, arrêt possible, aucun fragment tardif sur un autre objet.
Connexion persistante et préférences compatibles conservées ; aucun envoi
automatique ni changement de facturation. Retirer le test Gemini et son code
inutile, garder Lens et une navigation compacte en portrait et paysage.
Cette description correspond au lot 0.13.0 ; les réglages modèle/réflexion sont
désormais dans Configuration depuis M2-09, avec le même choix avant l'envoi.
Version 0.13.0/code25 installée. Revue sans blocage, globale 421 Android/76 scripts/quatre PC,
testeur 267 ciblés et huit lecteur verts ; nouvelle couverture 98,59 %/84,05 %,
duplication 1/797. Retour humain du 31 octobre : « ça semble ok » après la recette
proposée ; parcours essentiel accepté sur Xiaomi. Ce retour ne mesure pas la
latence ni la qualité comparée des niveaux de réflexion, et ne certifie pas
individuellement tous les cas complémentaires. [Décision et critères](docs/decisions/0011-chatgpt-direct-streaming.md),
[preuves](docs/validation/m2-chatgpt-direct.md),
[recette](docs/tests/m2-chatgpt-direct-recette.md).

Suivi secondaire M2-08-COV-01 : compléter les branches rares du lecteur HTTP et
du fallback d'interface, mesurés à 78,57 % et 50 % dans le ciblé. Composants et
nouveau code dépassent 80 % lignes/branches ; aucune exclusion ou baisse de seuil.

### M2-09 — traiter le cadrage et regrouper les actions de photo

**État : terminé — parcours essentiel accepté, cible automatique depuis 0.14.1. Dépendances : M2-08, M1-10.**
La conception initiale 0.14.0 ci-dessous a évolué : le choix Cadrage/Objet a été
retiré par M2-09-UI-01, accepté sur Xiaomi. La présence d'une sélection détermine
désormais la cible. Les trois actions sont pleines et homogènes depuis 0.15.1.

Conception initiale : traiter sans sélection les pixels visibles après zoom, ou choisir explicitement
l'objet détecté. Regrouper Analyser/ChatGPT/Lens sur la même cible, conserver les
réponses et réglages du parcours existant. Navigation Caméra/Galerie en icônes
dans l'en-tête, modèle/réflexion derrière une roue dentée. Géométrie exacte sans
bandes vides, annulation sur geste réel et conservation à la rotation ; le clic
suivant utilise le cadrage actuel. Version 0.14.0/code26 installée.
[Étude UI/UX et critères](docs/specs/photo-actions.md),
[preuves](docs/validation/m2-photo-actions.md),
[recette](docs/tests/m2-photo-actions-recette.md).

Suivi secondaire M2-09-COV-01 : compléter les branches restantes de PhotoTargetActions
(41/52, 78,85 % individuellement). Le composant Photo/UI et son nouveau code passent
80 % lignes/branches ; aucune exclusion ni baisse de seuil pour cette limite.

### M2-09-UI-01 — déduire la cible de la sélection

**État : terminé — validé sur PC et Xiaomi en 0.14.1. Dépendance : M2-09.**
Supprimer les boutons Cadrage/Objet. Une sélection détectée détermine le recadrage
d'objet ; sans sélection, les trois traitements utilisent le cadrage visible.
Désélection par second appui ou zone vide. Indication passive de cible et
configuration compacte, sans nouvel état de mode divergent. Version
0.14.1/code27. [Contrat](docs/specs/automatic-photo-target.md),
[preuves](docs/validation/m2-auto-target.md),
[recette](docs/tests/m2-auto-target-recette.md).

Retour humain après installation : « oui ça marche ». Le parcours essentiel de
cible automatique est accepté ; aucun résultat détaillé supplémentaire n'est déduit.

Suivi secondaire M2-09-UI-02 : rendre visible le statut de chargement lorsqu'une
nouvelle image remplace une ancienne sélection. Les actions restent désactivées
pendant le chargement ; amélioration du retour visuel sans défaut d'envoi établi.

### M2-10 — analyser les objets et identifier une plante sans choix de mode

**État : terminé — parcours essentiel accepté sur Xiaomi en 0.15.0. Dépendance : M2-09-UI-01.**
Conserver YOLOE pour les objets et les contours ; une clé Pl@ntNet personnelle
permet une identification botanique en parallèle à chaque analyse de photo,
sans dépendre de la présence d'une détection végétale locale. Capture, import et
Analyser partagent ce parcours. Sans clé ou réseau, l'analyse locale reste utilisable.

Configuration compacte de la clé privée, stockage chiffré, offre Free uniquement,
quota et erreurs explicites. Une hypothèse suffisamment étayée enrichit ChatGPT
seulement pour le même cadrage ; les réponses tardives ne changent pas un envoi.
Les alternatives concernent une même plante dominante, sans promesse d'inventaire
multi-espèces ou de masque botanique. Version cible 0.15.0/code28.
[Contrat](docs/specs/joint-plant-analysis.md),
[clé et utilisation](docs/plantnet.md),
[preuves](docs/validation/m2-joint-plants.md),
[recette](docs/tests/m2-joint-plants-recette.md).

Suivi M2-10-QUAL-01 : évaluer l'identification sur un jeu botanique représentatif
avec vérité terrain, incluant plantes d'intérieur, cactus/succulentes, arbres,
fleurs, inconnus et scènes mixtes ; comparer ChatGPT seul et enrichi. Les tests
logiciels et l'accès réseau réel ne démontrent pas cette précision statistique.

Retour utilisateur : « ça a l'air de fonctionner, même après redémarrage ».
Parcours essentiel et conservation observée de la clé acceptés, sans extrapoler
la précision par espèce ou la protection matérielle. Lecture des diagnostics du
processus Blamera : catalogue/réponse ChatGPT HTTP200, trois analyses CPU, pas
d'événement FAILURE dans la lecture retenue ; aucun diagnostic botanique en 0.15.0.

### M2-10-UI-01 — saisir la clé et présenter les actions clairement

**État : terminé — parcours essentiel accepté sur Xiaomi en 0.15.1. Dépendance : M2-10.**
Ajouter l'œil Afficher/Masquer à la saisie courante, sans relire la clé enregistrée.
Les trois traitements utilisent le même style plein que ChatGPT, avec activation
réelle inchangée. Compléter les diagnostics locaux par des catégories botaniques
et un indicateur de prompt enrichi, sans contenu personnel. Version cible
0.15.1/code29. [Contrat](docs/specs/plant-key-visibility.md),
[preuves](docs/validation/m2-key-visibility.md),
[recette](docs/tests/m2-key-visibility-recette.md).

Retour utilisateur : « ça a l'air ok ». Lecture des seuls diagnostics Blamera
le 1er novembre à 11:29, heure de Paris : identification Pl@ntNet publiée,
prompt préparé avec hypothèse botanique, réponse ChatGPT HTTP200 et analyse
locale terminée. Aucun événement d'échec dans ces quatre événements conservés.
Le suivi statistique M2-10-QUAL-01 reste ouvert : transport et enrichissement
réussis ne démontrent pas la justesse de l'espèce.

## M3 — reconnaître quelques proches et préparer la galerie sur PC

Livrable proposé : UI locale de préparation, galerie exportée et reconnaissance avec abstention.
La première itération peut viser une personne à la fois ; l'extension simultanée est conditionnelle.
M3-01 est livré sur PC après clôture M1/M2. Après le premier profil YuNet/SFace
de M3-02-A, l'utilisateur privilégie la qualité : ArcFace ResNet50 devient le
profil PC retenu. La comparaison sur données indépendantes et la calibration
doivent encore établir les erreurs et les seuils de reconnaissance.
Les tickets suivants restent à cadrer.

### M3-01 — créer l'interface locale de gestion des personnes

**État : terminé — galerie PC 0.2.0, parcours essentiel accepté. Dépendances : M0-05.**
Objectif : gérer quelques proches et importer leurs photos depuis le PC.
Acceptation : ajout/correction/suppression des identités et photos ; prévisualisation et erreurs
d'import ; données locales hors Git ; structure extensible aux chats ; aucune authentification.
[Contrat détaillé](docs/specs/people-gallery.md) : stockage SQLite local, import
multiple normalisé, dédoublonnage, correction d'affectation et persistance.
Pas de reconnaissance ni export dans ce premier incrément.
Revue indépendante sans blocage, 26 tests PC et cinq contrôles complémentaires
conformes, vrai import navigateur d'une fixture synthétique confirmé. Serveur
local disponible pour la recette utilisateur ; Android reste en 0.15.1.
[Preuves](docs/validation/m3-01-people-gallery.md),
[recette](docs/tests/m3-people-gallery-recette.md).

Retour utilisateur : « ok ça a l'air de marcher ». Le parcours essentiel proposé
est accepté qualitativement. Aucune inspection des photos ou de la base personnelle
n'est nécessaire ; les cas optionnels et volumes importants ne sont pas réputés
testés individuellement. M3-02 est démarré sur demande utilisateur.

### M3-02 — préparer et calibrer la galerie faciale

**État : terminé sur PC — seuil figé et test indépendant mesuré ; parité mobile à valider en M3-04/M3-06. Dépendances : M3-01.**
Objectif : sélectionner un modèle facial compatible CPU/téléphone et calculer les embeddings.
Acceptation : détection, alignement et prétraitement reproductibles ; licences vérifiées ;
cas sans visage/plusieurs visages traités ; calibration sur validation, test séparé ;
inconnus/incertains et faux positifs évalués, pas de reconnaissance forcée.

Premier incrément M3-02-A : comparer les candidats sur sources primaires et CPU,
préparer les visages de référence puis solliciter leur validation dans la galerie
locale. Le premier profil YuNet/SFace est remplacé par YuNet/ArcFace ResNet50
selon la priorité qualité exprimée par l'utilisateur ; les poids, profils et
empreintes sont versionnés. La calibration sur références/validation/test
séparés reste à faire, sans utiliser un seuil de démonstration comme preuve.
[Comparaison et décision provisoire](docs/decisions/0015-face-reference-model-profile.md).
[Mesures et compromis des candidats](docs/research/m3-face-evaluation.md).

### M3-02-A — détecter et confirmer les visages de référence sur PC

**État : terminé — PC 0.4.0, préparation et reprise des références acceptées. Dépendance : M3-01.**
Action explicite de préparation, aperçu des candidats, choix de la bonne personne
même dans une photo de groupe et confirmation avant calcul de l'empreinte.
Persistance et migration de galerie non destructive ; réaffectation invalide la
confirmation, profils incompatibles non réutilisés. Aucun calcul automatique ni
reconnaissance certaine, export ou changement Android dans ce lot.
[Contrat](docs/specs/face-reference-preparation.md).
Revue sans blocage, 58 tests et sept contrôles indépendants conformes, smoke CPU
sur fixtures NASA et interface réelle zéro/un/deux visages vérifiés. Galerie v1
migrée par transaction, références protégées contre résultats obsolètes, aucun
calcul implicite. Serveur local mis à jour ; la galerie personnelle n'est pas
inspectée par l'agent. Après passage à 0.4.0 et résolution du symptôme de profil,
l'utilisateur confirme ses sélections et les mentions « Visage confirmé »
conservées après rechargement. Parcours essentiel accepté ; les cas optionnels
de la recette ne sont pas tous réputés vérifiés individuellement.
[Preuves](docs/validation/m3-02-face-preparation.md),
[recette](docs/tests/m3-face-reference-recette.md).

Évolution demandée : privilégier un modèle performant, ArcFace ResNet50, plutôt
que sélectionner principalement selon la taille ou la vitesse. Le nouveau
profil produit 512 valeurs et ne réutilise pas les anciennes références SFace
128D. Photos et personnes conservées ; nouvelle préparation explicite nécessaire.
Le serveur local arrêté a été relancé ; les preuves du changement et du
redémarrage final figurent dans la [validation ResNet50](docs/validation/m3-02-resnet50.md).

Suivi de méthode **M3-02-TDD-01** : les sous-cas détaillés d'extraction d'archive
ont été testés après leur implémentation, malgré un Red antérieur du manifeste.
Pas de TDD intégral revendiqué pour ce lot. Revue sans défaut bloquant ; au
prochain changement de provisionnement, exécuter d'abord le Red fonctionnel
propre au contrat touché. Les directives et contrôles restent inchangés.

Incident **M3-02-A-BUG-01** : après sélection de ses visages, l'utilisateur signale
une erreur de profil sur toutes les photos, apparue pendant le travail sur la
dernière image. Sa capture confirme le message exact du manifeste. Redémarrage
complet du worker et du lanceur identifiés, aucune lecture des photos ou de la
base personnelle. L'utilisateur confirme que le rechargement supprime l'erreur :
incident clos pour ce symptôme. Les essais isolés et indépendants passent ; la
cause précise reste non établie. L'utilisateur confirme séparément les choix et
confirmations conservés. Un changement incompatible de profil doit se faire serveur utilisateur
arrêté, après validation sur une galerie QA isolée.
[Diagnostic et état](docs/validation/m3-02-profile-error.md).

### M3-02-B — préparer le corpus et évaluer la reconnaissance sur PC

**État : terminé — calibration puis test final exécutés, galerie conservée. Dépendance : M3-02-A.**
Importer des photos distinctes des références, indiquer l'identité attendue ou
inconnu et les séances. Séparer Calibration et Test final réservé, contrôler les
doublons exacts et les chevauchements de séances, puis préparer explicitement
les visages. Comparer aux références ResNet50 par prototypes et scores cosinus,
avec seuils exploratoires, noms / inconnu / incertain et rapport à effectifs
visibles. Les paramètres initiaux ne sont pas des seuils calibrés.

Les probes ne deviennent jamais des références dans la galerie principale.
Le test final reste réservé, sans prédiction dans ce lot ; calibration finale,
gel de la politique et ouverture du test relèvent de l'incrément suivant.
Rapport local versionné et invalidation si données, références ou réglages changent.
Le serveur utilisateur a été arrêté avant modification du checkout actif, puis
relancé après revue et validation ; aucune inspection des données personnelles.
[Contrat](docs/specs/face-evaluation.md),
[protocole et décisions](docs/decisions/0016-face-evaluation-protocol.md).
95 tests, six cas complémentaires indépendants, smoke natif et navigateur public
conformes sur le même gel de 42 fichiers. La recette sur les images personnelles
reste ouverte et ne vaut pas validation statistique du modèle.
[Recette](docs/tests/m3-face-evaluation-recette.md),
[preuves](docs/validation/m3-02-evaluation.md).

Corpus personnel fourni dans `resources/` sur instruction explicite de l'utilisateur :
dossier ignoré par Git, audit local en lecture seule et aucune image importée ou
modifiée automatiquement. Sept fichiers `models` admissibles correspondent aux
sept références actuelles ; deux séances ne sont pas prouvées, leur seul indice
de date provenant d'une messagerie. L'utilisateur choisit ensuite de continuer
avec les cinq références datées, sans modifier sa galerie. L'ancienne demande
privée de dates est conservée comme historique sous `pc/data/import-plans/`.
Les cas de groupe demandent un choix humain
du sujet, pas une sélection fondée sur la similarité attendue.
[Audit et limites](docs/validation/m3-02-resource-corpus.md).

### M3-02-B-01 — évaluer avec un sous-ensemble de références et préparer les lots

**État : terminé — calibration exécutée et lue, galerie conservée. Dépendance : M3-02-B.**
Décision utilisateur : les dates des deux références restent inconnues ; avancer
avec les cinq références datées, sans modifier la galerie. Ajouter une sélection
persistante propre à l'évaluation et l'inclure dans l'empreinte du rapport. Toutes
les photos principales restent interdites comme doublons d'évaluation, même les
références exclues du calcul. Aucun prototype ou séance exigé pour les exclues.

Préparer un petit corpus avec les ressources autorisées, lots et jours distincts,
personnes inconnues distinctes, normalisation et détections sans sélection ni
encodage automatique de cible. La préparation ne modifie que la base d'évaluation.
L'intégrité binaire de la galerie principale est vérifiée avant / après : ses
sept photos et confirmations sont conservées. Les trente cas sont importés et
détectés, aucun visage n'est sélectionné ni encodé automatiquement à la publication.
Les quinze cibles de Calibration sont maintenant confirmées par l'utilisateur et
le rapport a été lu. Au seuil initial 0,60 : six bons noms sur dix proches,
quatre incertains, cinq inconnus correctement rejetés, aucun faux nom. L'examen
local des cas difficiles est autorisé ; le réglage candidat est traité ci-dessous.
107 tests PC, revue et testeur conformes, couverture 99,71 % / 98,25 %, nouveau
code 100 % / 100 %, duplication nouvelle nulle. Le cas de première publication
sans dossier d'évaluation est corrigé après reproduction synthétique et nouvelle
validation du seul applicateur, sans modification supplémentaire du logiciel.
[Contrat](docs/specs/evaluation-reference-subset.md),
[preuves](docs/validation/m3-02-reference-subset.md).

Dette acceptée **M3-02-B-CFG-01** : un JSON de réglage corrompu empêche d'atteindre
la réparation UI ; le setter valide les écritures normales et une restauration
par API reste disponible. Prévoir un accès à la réinitialisation dans une prochaine
évolution de configuration. Aucun contournement des contrôles de données.

### M3-02-B-02 — figer les paramètres et ouvrir le test final indépendant

**État : terminé — gel conservé et test final exécuté après B-03. Dépendance : M3-02-B-01.**
Après examen autorisé des quatre cas incertains, retenir sur la calibration seule
rejet 0,30, acceptation 0,50 et marge 0,05. Le recalcul confirme neuf proches sur dix reconnus,
un reste incertain et les cinq inconnus sont rejetés. Le petit effectif et une
seule identité inconnue dans ce lot empêchent une conclusion de robustesse générale.
Les prédictions des quinze photos réservées ne servent pas à ce choix.

Ajouter un gel explicite à partir du rapport courant, puis permettre la préparation
humaine des cibles et l'évaluation distincte du Test final. Conserver la galerie,
les cinq références retenues, les sources / séances et les quinze cas, y compris
le cas sans visage. Les changements de contexte invalident gel et rapport courant ;
une réutilisation d'images déjà exposées sous un autre contexte est refusée.
Les paramètres sont appliqués et le gel est enregistré. Treize cibles sont
confirmées ; un sujet attendu manque parmi les candidats, traité dans B-03.
Le quinzième cas sans visage est conservé dans le dénominateur. Les sept photos et
confirmations de la galerie principale restent identiques à l'import initial.
[Contrat](docs/specs/face-final-test.md),
[recette](docs/tests/m3-final-test-recette.md),
[preuves logicielles](docs/validation/m3-02-final-test.md).

### M3-02-B-03 — déclarer le sujet attendu absent des visages détectés

**État : terminé — correctif PC 0.6.1 revu et validé, test réel exécuté. Dépendance : M3-02-B-02.**
La recette révèle un sujet attendu de dos, absent des candidats alors que d'autres
personnes sont détectées. Le parcours bloquait le rapport sans choix de visage.
Ajouter le constat explicite Sujet attendu non détecté, réversible et lié à la
source / annotation / préparation courantes. Compter le cas comme non exploitable
avec motif, sans changer son identité attendue ou forcer un autre visage.
Le gel 0,30 / 0,50 / 0,05 et le rapport de calibration doivent rester courants.
Le test conserve quinze cas : treize cibles confirmées, un sujet attendu absent,
un cas sans aucune détection. Aucun résultat final n'est utilisé pour régler
les seuils ; l'utilisateur autorise le lancement par l'orchestrateur après validation.
Résultat réel : huit bons noms sur dix proches prévus, soit huit sur huit visages
exploitables ; cinq inconnus rejetés sur cinq photos d'un seul individu inconnu,
deux cas non exploitables sur quinze et aucun faux nom / rejet de proche / incertain.
Les treize confirmations et la galerie principale restent identiques. Le petit
effectif ne démontre pas une fiabilité générale.
[Contrat](docs/specs/face-final-test.md),
[preuves](docs/validation/m3-02-missing-target.md).

### M3-03 — exporter et importer une galerie versionnée

**État : validation Xiaomi attendue ; export PC produit, import Android validé sur PC. Dépendances : M3-02.**
Objectif : transférer manuellement les références nécessaires vers le téléphone.
Acceptation : noms, embeddings, modèle/prétraitements et seuils versionnés ; export déclenché
dans l'UI ; paquet invalide/incompatible rejeté sans perdre la galerie utilisable ; photos
originales non requises sur mobile ; compatibilité ordinateur/téléphone vérifiée.

#### M3-03-A — export PC des prototypes qualifiés

**État : terminé — PC 0.7.0 disponible, export personnel produit après revue et validation indépendantes.**
Le test final courant autorise l'export en mémoire des seuls prototypes moyens
des cinq références retenues, deux identités, politique 0,30 / 0,50 / 0,05,
profil et métadonnées exactes de la chaîne / modèles. Qualification exploratoire
agrégée ; pas de photos, crops, vecteurs individuels, cas, chemins ou séances.
Un téléchargement explicite écrit le fichier privé, borné à 120 Kio UTF-8.
Tout changement pertinent invalide l'export ; aucun recalcul du test ou gel
implicite. Le contrat v1 et sa fixture synthétique sont communs à l'import Android.
La reconnaissance mobile et sa parité numérique restent à M3-04 / M3-06.
[Contrat](docs/specs/face-gallery-package.md),
[recette](docs/tests/m3-face-gallery-export-recette.md),
[preuves](docs/validation/m3-03-face-gallery-export.md).

#### M3-03-B — importer la galerie sur Android

**État : PC conforme — Blamera 0.16.0/code30 installée ; recette isolée regroupée avec M3-04 à la demande utilisateur.**
Choix explicite du fichier depuis Configuration, validation stricte du contrat
commun avant remplacement atomique et chiffré dans le stockage exclu des
sauvegardes. Afficher les personnes importées, conserver la galerie précédente
sur erreur ou annulation et préserver les parcours photo existants. Le transfert
du fichier était manuel dans ce premier lot. Le 2 novembre, l'utilisateur demande
de prendre en charge le JSON et de le déployer avec l'application : le parcours
personnel M3-04 fournit donc la galerie dans les assets générés de l'APK et
l'enregistre automatiquement si le stockage facial est vide. L'import manuel
reste facultatif ; aucune validation isolée de choix du JSON n'est désormais
demandée. La reconnaissance appartient à M3-04.

Qualité globale conforme : 535 tests Android, 139 tests PC et 76 tests des outils,
aucun échec. Couverture Android 97,39 % lignes / 86,92 % branches, nouveau code
98,26 % / 86,00 % ; composant facial 97,93 % / 85,58 %, interface faciale
100 % / 86,21 %. Duplication nouvelle nulle, complexités conformes, aucun avis
actif retourné par les scans des 98 paquets Maven et 36 paquets PyPI. Revue sans
blocage ; testeur indépendant : 73 tests, dont trois scénarios supplémentaires,
tous conformes. Aucun ajout de dépendance ni de poids facial.

Le 2 novembre 2026, après reconnexion du Xiaomi, la mise à jour principale
0.16.0/code30 est installée avec conservation des données (`adb install -r`,
résultat Success). Aucun lancement ni transfert de galerie par l'agent. Le paquet
personnel de deux identités est disponible dans
`pc/data/exports/blamera-face-gallery.json` ; il sera pris en charge dans l'APK
personnelle M3-04, sans copie ADB de fichier. La validation matérielle sera celle
du parcours photo avec reconnaissance, et de sa conservation après réouverture.
[Preuves](docs/validation/m3-03-import-android.md),
[recette](docs/tests/m3-face-gallery-import-recette.md).

Suivi **M3-03-B-TDD-01** accepté sans blocage fonctionnel : deux tests de frontière
(raccord au stockage partagé et lecteur borné) ont été ajoutés avec leur code,
sans Red distinct préalable. Les Red / Green du décodeur, contrôleur, interface,
cycle de vie et calendrier sont archivés. Le prochain changement comportemental
de lecture / stockage devra commencer par le test exécuté en Red, puis une revue
de cette preuve ; aucun historique fictif n'est reconstitué.

### M3-04 — afficher le nom reconnu dans la scène

**État : terminé — parcours connu/inconnu accepté et bilan POC M3-06 clos. Dépendances : M3-03, M1-09.**
Objectif : ajouter l'identité aux résultats des deux sources sans confusion avec les catégories.
Acceptation : nom automatique pour un proche reconnu ; inconnu/incertain sinon ; association
visage/personne vérifiée ; catégories objets/chat conservées ; mesures sur appareil et tests
d'identification erronée documentés. Commencer par un périmètre à une personne cadré explicitement.
La version construite 0.17.0/code31 embarque localement les poids publics vérifiés et,
sur propriété de build explicite, la galerie personnelle évaluée. Aucun contenu
personnel dans les sources ou builds génériques. Amorçage seulement si le stockage
chiffré est vide ; conserver les imports existants. L'utilisateur n'a pas à copier
le JSON, il ouvre une photo et vérifie le nom. Les cadres utilisent les coordonnées
du bitmap de travail avec zoom/cadrage ; plusieurs visages ne forcent pas de choix.
Revue et testeur indépendants conformes. Globale : 584 tests Android, 154 PC et
76 scripts verts, puis 80 tests ciblés indépendants verts ; les seuils qualité
passent sans exclusion ajoutée. Parité JNI fraîche sur émulateur : trois cas
publics conformes, sans modification du modèle ou des tolérances. Runtime facial
95,57 % lignes / 83,97 % branches ; contrôleur 100 % / 85 %.
L'APK personnelle ARM64 signée est vérifiée : galerie exacte, cinq références
agrégées en deux prototypes, seuils figés, aucune photo de référence embarquée,
65 entrées de code/assets/bibliothèques identiques au build générique qualifié.
Galerie PC et 385 empreintes de sources/configuration conservées.
Les erreurs d'infrastructure initiales (timeout PC, saturation disque du testeur,
suspension de l'hôte) sont conservées sans modifier délais ni tests. Après
reconnexion confirmée, le seul APK principal 0.17.0 est mis à jour avec conservation
des données (`adb -s <serial-du-Xiaomi> install -r`, sortie 0 « Success »), le
2 novembre 2026 à 12 h 26, heure de Paris. Aucun lancement automatique ni transfert
séparé de JSON. Validation JNI ARM64, latence,
pic mémoire et recette humaine restent ouverts ; aucun JSON à manipuler.
Retour utilisateur suivant : « ça a l'air ok pour des personnes connues ou
inconnues ». Ce retour accepte le parcours essentiel sur le moteur ARM64 ; il
ne fournit ni mesure chronométrée ni pic mémoire. Une lecture des diagnostics
Blamera filtrés a été préparée, mais aucun PID exploitable n'était disponible :
aucun log n'a été collecté et aucune métrique native chiffrée n'en est déduite.
[Qualification et livraison](docs/validation/m3-04-delivery.md).
[Recette sur photo](docs/tests/m3-face-recognition-recette.md).
[Cadrage du moteur mobile et des preuves attendues](docs/specs/face-mobile-recognition.md).

### M3-05 — afficher les labels de plusieurs personnes sur une photo

**État : terminé — parcours essentiel accepté sur Xiaomi 0.18.0/code32 ; mesures complémentaires en M3-06. Dépendances : M3-04.**
Objectif : analyser chaque visage d'une photo et afficher automatiquement son nom,
« Inconnu » ou « Identité incertaine » près de celui-ci, sans sélection préalable.
À la demande utilisateur, afficher **les labels seuls, sans cadres faciaux**, pour
préserver la lisibilité des contours d'objets. Conserver modèle, galerie et seuils.
Acceptation : zéro/un/trois visages, deux proches et un inconnu, associations sans
permutation, abstention individuelle, zoom/crop/rotation corrects, annulation des
anciens lots, mémoire native libérée. Au-delà de la borne existante de vingt
candidats, refus explicite plutôt que résultat tronqué silencieusement.
Version 0.18.0/code32 installée le 2 novembre 2026 à 15 h 16, heure de Paris,
avec conservation des données (`install -r`, sortie 0 « Success »), sans lancement
automatique ni transfert séparé de JSON. Galerie et modèle conservés. Le paquet
personnel est vérifié : ARM64 seul, deux identités/cinq références, sept poids et
notices approuvés, 68 payloads communs identiques au build générique qualifié.
Revue et testeur indépendants conformes : **614 tests Android, 157 PC, 76 scripts**
dans la globale finale, puis 98 tests ML/UI et 38 tests IO indépendants. Android
97,26 % lignes / 86,61 % branches ; nouveau code 96,77 % / 84,41 % (cumul M3 contre
`83d3645`). Duplication nouvelle nulle. Parité publique de chaque visage vérifiée
sur émulateur, sans changer de poids, de seuils ou de tolérances.
Les trois globales en échec restent archivées. La dernière passe après le correctif
de fichier et le raccourcissement du TEMP Windows, justifié par une sonde ONNX et
le test canonique inchangé. Les mesures sur Xiaomi et la recette de groupe sont
distinctes du logiciel ; le bilan M3-06 est depuis clos pour le POC. Retour humain après la recette de groupe : « ça a
l'air de fonctionner ». Le parcours essentiel est accepté ; aucun délai chiffré
ou détail de tous les cas limites n'est inféré. Deux lectures des seuls diagnostics
Blamera, limitées au PID de l'application et au tag facial, n'ont retrouvé aucun
événement disponible ; aucune mesure supplémentaire n'en est déduite.
[Contrat multi-visages](docs/specs/face-multiple-recognition.md).
[Preuves et limites](docs/validation/m3-05-multiple-labels.md).
[Recette de groupe](docs/tests/m3-multiple-face-labels-recette.md).

#### M3-05-QA-01 — refus d'accès au remplacement atomique

Les deux premières globales échouent sur des tests de persistance, à code identique :
erreur de session assainie, puis `AccessDeniedException` confirmé sur `Files.move`
du rapport de benchmark. L'examen n'établit ni fuite de handle propre ni cause
Windows exacte. Les essais restent archivés et aucun délai/test n'est affaibli.
Correctif borné partagé entre les deux remplacements : trois tentatives maximum,
deux pauses de 25ms uniquement pour le refus d'accès, même opération atomique,
ancien document conservé et échec explicite si le refus persiste. Tests déterministes,
revue et validation des deux intégrations conformes : 34 tests coder, 38 tests
indépendants, puis globale 614/157/76 réussie. La cause Windows exacte et celle
du premier incident de session restent inconnues ; aucune attribution à un antivirus.
[Contrat du correctif](docs/specs/atomic-file-replacement.md).

### M3-06 — valider la reconnaissance et la préparation de bout en bout

**État : terminé pour le POC — parcours accepté, mesures terrain disponibles et limites acceptées. Dépendances : M3-04, M3-05.**
Objectif : démontrer UI → galerie → téléphone, hors ligne pour la reconnaissance.
Acceptation : proches/inconnus et conditions variées évalués avec effectifs ; faux positifs
et abstention rapportés ; performances/non-régressions mesurées ; limites explicites.
Inclure le parcours de groupe M3-05 livré en 0.18.0, avec les abstentions et
les budgets de latence/mémoire ; aucune précision générale garantie.

Mesures réelles sur Xiaomi en 0.18.0, sans nouvelle APK : quatre appels d'un visage
(médiane 418 ms), quatre de trois visages (1118,5 ms, maximum 1202 ms). L'import
automatique est compté ; l'événement initial antérieur à la demande est conservé
mais exclu de la série. Nouvelle instance confirmée par `FACE_PARITY_OK` :
initialisation/autocontrôle 1576 ms, premier lot trois visages 2218 ms **chargement
compris**, appel suivant 1175 ms. Trois cas publics de parité conformes sur ARM64.
Retours : « ok. pas de lenteur ni d'erreurs » et « fait. pas long ».

Ces temps concernent le moteur, pas le décodage, la comparaison galerie ou le
rendu complet. Les PSS et leur delta sont des instantanés du processus entier,
pas le pic supplémentaire du visage. La cible de pic ≤700 Mio reste non vérifiée.
L'utilisateur précise explicitement qu'il s'agit d'un POC et demande de clôturer
ces chantiers lorsque l'usage lui convient. M3-06 et les compléments de mesure sont
donc clos dans ce périmètre, sans instrumentation supplémentaire. Aucun seuil
mémoire ni délai complet n'est déclaré mesuré ou conforme par absence de symptôme.
Les limites individuelles de couverture M3-05-COV-01/02 sont aussi closes comme
limites acceptées, les composants et le nouveau code respectant les seuils convenus.
[Protocole de mesure](docs/specs/face-device-validation.md).
[Résultats et limites](docs/validation/m3-06-device-measurements.md).

## M4 — identifier les chats et comparer les approches sur CPU

Livrable proposé : même UI étendue aux chats, expériences reproductibles et modèle exportable.
Point de départ : trois chats, environ 74, 200 et 300 photos par identité, à vérifier après export.

### M4-01 — étendre l'UI aux chats et préparer les jeux de données

**État : à affiner ; vues et scènes en attente Q-02. Dépendances : M3-01.**
Objectif : importer les dossiers/exportations et corriger les identités et recadrages.
Acceptation : gestion des chats, photos invalides et images multi-chats ; doublons/rafales
repérés ; séparation train/validation/test par séance ou période sans fuite ; inconnus ajoutés ;
références des données et conditions versionnées sans publier les photos. Préciser Q-02 avant
de fixer les critères tête/corps/profil/dos et plusieurs chats.

### M4-02 — établir la référence par embeddings

**État : à affiner. Dépendances : M4-01.**
Objectif : mesurer une première identification avec extracteur préentraîné sur CPU.
Acceptation : expérience lancée depuis l'UI ; paramètres/versions/graines et résultats
reproductibles ; calibration sur validation ; résultats par chat/condition et rejet des inconnus,
y compris cas d'une seule identité ; test final séparé et limites liées aux effectifs rapportées.

### M4-03 — entraîner un classificateur sur extracteur gelé

**État : à affiner. Dépendances : M4-02.**
Objectif : comparer l'apprentissage personnalisé à la référence avec les mêmes jeux.
Acceptation : entraînement CPU déclenché dans l'UI ; temps/mémoire mesurés ; comparaison
par identité/condition et inconnus ; prétraitements, versions et paramètres conservés.

### M4-04 — expérimenter un fine-tuning partiel, option

**État : à affiner, conditionné à la faisabilité CPU. Dépendances : M4-03.**
Objectif : évaluer l'intérêt d'un ajustement limité de l'extracteur.
Acceptation : budget temps/mémoire décidé après essai CPU ; expérience et comparaison équitables
reproductibles si poursuivie ; décision motivée d'arrêt ou de poursuite ; aucun cloud payant supposé.
Cette expérience n'est pas un prérequis au déploiement d'une approche déjà satisfaisante.

### M4-05 — exporter l'approche retenue et permettre l'ajout d'un chat

**État : à affiner. Dépendances : M4-02, M4-03 ; M4-04 seulement si retenu.**
Objectif : choisir une approche par les résultats et produire le paquet depuis l'UI.
Acceptation : modèles, noms, prétraitements, seuils et versions exportés ; ajout d'un chat
sans changement de code, avec réentraînement si nécessaire ; erreurs/annulation explicites ;
compatibilité du paquet contrôlée et procédure de transfert manuel documentée.

### M4-06 — reconnaître les chats sur téléphone

**État : à affiner. Dépendances : M4-05, M3-04.**
Objectif : afficher le nom du chat reconnu dans la scène existante.
Acceptation : import valide et rejet des paquets incompatibles ; catégories/personnes conservées ;
inconnu/incertain disponibles ; prétraitements cohérents ; ambitions multi-chats et postures
limitées au périmètre décidé après Q-02 et essais, sans garantie anticipée.

### M4-07 — valider l'ensemble et documenter les comparaisons

**État : à affiner. Dépendances : M4-06, M3-06, M2-04.**
Objectif : démontrer caméra/image, objets, proches, chats et action externe explicite.
Acceptation : performances finales sur Xiaomi et limites CPU consignées ; comparaison des
approches et erreurs par condition ; inconnus et non-régressions évalués ; installation,
préparation UI, export et reproduction documentés ; qualité et validation indépendantes.

## Règles transversales de préparation des tickets

- Avant développement : fixer critères, dépendances réalisées, preuves et commandes attendues.
- Appliquer TDD, revue indépendante puis validation du tester selon AGENTS.md ; exception
  documentaire/spike justifiée. Aucun ticket déclaré terminé sans preuve sur son état final.
- Suivre progressivement latence, mémoire, chauffe et qualité ; fixer les budgets après mesure.
- Versionner références de données, expériences, modèles et paquets, avec données personnelles
  et secrets hors Git. Préserver l'abstention et les limites connues dans les démonstrations.
- Conserver l'historique de Q-01 actualisée et Q-03/Q-04 résolues pour le périmètre actuel,
  suivre Q-02 et les dettes acceptées ; ne pas transformer
  une proposition en engagement ou une vérification non exécutée en réussite.
