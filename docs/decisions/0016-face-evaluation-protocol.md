# ADR 0016 — corpus d'évaluation et décisions exploratoires

1er novembre 2026, M3-02-B. [Contrat](../specs/face-evaluation.md).

## Besoin

Les références ResNet50 et leur reprise sont acceptées. Il faut mesurer sur
d'autres photos si un proche reçoit le bon nom et si une personne absente de la
galerie reste inconnue / incertaine. Une similarité sur la photo ayant servi de
référence ne constitue pas une évaluation indépendante.

## Corpus et séparation

Conserver la galerie de références. Un stockage d'évaluation distinct sous la
racine privée locale réutilise ses composants de normalisation et de préparation
des visages, avec les annotations attendues et les résultats propres aux probes.
Le bucket technique de ce stockage ne devient jamais une identité de la galerie
principale. Imports et annotations doivent être atomiques.

L'utilisateur fournit le nom de séance, l'identité connue ou l'étiquette inconnu,
et le lot Calibration / Test final réservé. Il attribue aussi une séance aux
références actives confirmées. Rejeter doublons normalisés exacts et chevauchements
de séances entre références / calibration / test. Les variantes, rafales et dates
ne peuvent pas être déduites automatiquement des seules images normalisées :
l'indépendance réelle repose aussi sur les annotations humaines.

Le lot réservé ne produit aucune prédiction dans ce premier écran. Le gel d'une
politique calibrée et l'ouverture du test final viendront ensuite ; tester chaque
modification des seuils sur le même lot final en ferait un lot de validation.
M3-02 reste ouvert et aucune précision statistique des proches n'est promise.

## Prototype et politique de départ

Par personne, moyenner ses références unitaires compatibles puis normaliser le
prototype. Cela fournit un seul vecteur par identité et évite de donner plus
d'occasions de gagner à une personne possédant davantage de références. Une
moyenne dégénérée ou une référence invalide produit une erreur explicite.
L'effet de poses très différentes et des mauvaises références devra être mesuré
avant de figer cette agrégation pour le téléphone.

Règle exploratoire versionnée, scores cosinus et trois paramètres éditables :
rejet 0,30, acceptation 0,60 et marge 0,05. Ces nombres ne proviennent pas d'une
calibration de l'utilisateur et ne sont pas recommandés comme seuils validés.

| Condition | Décision |
| --- | --- |
| Meilleur score < rejet | Inconnu |
| Rejet ≤ meilleur score < acceptation | Incertain |
| Meilleur score ≥ acceptation et marge avec le second ≥ marge requise | Nom du meilleur candidat |
| Acceptation atteinte mais marge insuffisante | Incertain |
| Une seule identité dans la galerie | Mêmes seuils absolus, sans inventer une marge avec un second candidat |

Le cosinus est une similarité, pas une probabilité d'identité. Une galerie à une
personne doit toujours pouvoir rejeter un inconnu. Sans référence compatible ou
sans visage exploitable, ne pas forcer un nom ou produire un score fictif.

## Mesures et reproductibilité

Présenter les résultats par image et par identité attendue, avec les inconnus
distincts. Toujours afficher effectifs importés, exploitables, non exploitables,
et les dénominateurs des proportions. Distinguer au minimum :

- bon nom pour un connu ;
- mauvais nom attribué à un connu ou nom attribué à un inconnu ;
- rejet correct d'un inconnu ;
- rejet d'un connu et décisions incertaines ;
- absence de visage / cas non exploitable.

L'abstention n'est pas une bonne identification d'un proche. Les cas non
exploitables ne disparaissent pas du rapport ; une mesure conditionnelle sur les
visages utilisables doit annoncer ce dénominateur. Un petit corpus n'est pas une
preuve de taux d'erreur faible en population.

Le rapport local conserve politique, agrégation, profil du modèle, sources /
séances et instantané des références. Toute modification pertinente invalide
son statut courant. Comparer les instantanés avant publication pour refuser un
résultat calculé avec d'anciennes affectations, sources ou sélections. Aucun
contenu privé, nom, vecteur ou photo n'est journalisé dans les diagnostics.

## Conséquences et validation

### Sélection propre à l'évaluation — PC 0.5.1

Après l'audit, l'utilisateur ne connaît pas les séances de deux références et
demande de continuer avec les cinq références datées, sans modifier sa galerie.
La sélection de références est donc une politique de l'évaluation, persistée
dans ses settings. Les sept photos et confirmations principales sont conservées.
Le comportement par défaut reste toutes les références compatibles confirmées ;
une liste explicite fixe le sous-ensemble, sans inclure automatiquement les nouvelles.

Seules les références retenues alimentent prototypes et séances exigées. Une liste
vide ou une référence retenue devenue indisponible ne revient jamais silencieusement
à toutes les références. Les doublons restent contrôlés contre l'ensemble des
images principales. La politique de sélection fait partie de l'empreinte du rapport.
Ce choix évite de supprimer des références utiles pour contourner un problème de
protocole d'évaluation.
Un futur export associé à une politique validée devra reprendre ce même sous-ensemble
et son agrégation ; conserver la galerie complète ne valide pas implicitement ses
autres variantes de prototypes.

Pas de nouvelle dépendance ou modèle. Calcul natif uniquement sur action, choix
humain des visages conservé, limites d'import inchangées et tests déterministes
aux frontières. Les fixtures synthétiques / NASA servent à vérifier le logiciel,
pas à annoncer la qualité de reconnaissance sur les proches.
Les données utilisateur ne sont ni lues par l'agent ni transférées.

Les prochains arbitrages s'appuieront sur des données indépendantes : stratégie
d'agrégation, seuils / marge et décision d'ouvrir le test final. Export et runtime
Android restent séparés jusqu'à la cohérence numérique des chaînes PC / téléphone.

## Ouverture du test final — PC 0.6.0

Le [lot de gel](../specs/face-final-test.md) ajoute le gel explicite d'une politique
à partir d'un rapport de calibration courant, puis l'évaluation séparée du lot
réservé. L'utilisateur autorise l'examen local des images pour le diagnostic ;
cela remplace pour ce travail la restriction d'accès initiale, sans autoriser
aucun transfert externe ou publication de données personnelles.

La politique candidate 0,30 / 0,50 / 0,05 est choisie avant les prédictions finales.
Les sources / annotations du test appartiennent au gel ; seule sa préparation
faciale peut ensuite évoluer sans invalider ce contexte. L'empreinte du rapport
final inclut aussi cette préparation. Les résultats sont conservés séparément
du rapport de calibration et ne sont jamais réécrits sous le même contexte.
Les pixels exposés ne peuvent pas être réutilisés dans un autre gel présenté
comme indépendant ; un nouveau corpus est requis. Cette protection porte sur
les doublons normalisés exacts ; l'indépendance des séances / variantes demeure
une propriété des données et de leur annotation, pas une garantie universelle
déduite d'un hash.

### Sujet attendu absent des candidats — PC 0.6.1

Plusieurs personnes peuvent être détectées sans le sujet attendu. Exiger le
choix de l'une d'elles fausserait l'évaluation ; interdire tout rapport masquerait
ce défaut de détection. Ajouter un constat humain explicite et réversible, propre
à l'évaluation, compté comme non exploitable et distinct d'un rejet d'identité.
Son motif figure dans la ligne du rapport, sans prédiction ni score.

Le marqueur est lié à la source, révision, annotation attendue, profil et génération
de détection. L'action retire une éventuelle ancienne confirmation de ce cas ;
une nouvelle détection, sélection ou confirmation rend l'ancien marqueur inactif.
Les annotations modifiées effacent le constat. Sans marqueur, le manifeste garde
exactement sa structure 0.6.0 ; avec marqueur, seule la préparation du cas reçoit
ce supplément. Le gel de calibration reste donc courant pour un constat sur Test,
tandis que l'empreinte finale change et protège l'historique déjà exposé.
