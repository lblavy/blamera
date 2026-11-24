# M3-05 — plusieurs labels de personnes sur une photo

2 novembre 2026. Après validation du parcours connu/inconnu en 0.17.0,
l'utilisateur demande de reconnaître plusieurs visages et d'afficher leurs labels
automatiquement. Il précise ensuite : **labels seuls, sans cadres faciaux**, pour
éviter la confusion avec les contours d'objets.

## Comportement attendu

- Après capture, import ou analyse d'une zone : détecter les visages une fois,
  encoder chacun et comparer indépendamment avec la galerie existante. Afficher
  près de chaque visage son nom, « Inconnu » ou « Identité incertaine », sans
  sélection préalable ni choix de mode. Aucun rectangle facial supplémentaire,
  y compris dans le cas à un visage ; les contours YOLOE sont préservés.
- Conserver ensemble coordonnées et résultat de chaque visage. Ne pas attribuer
  arbitrairement un nom à une silhouette, ni forcer une identité unique par photo.
  Deux vues d'une même personne peuvent recevoir le même nom. Une personne de dos
  ou un visage non détecté ne reçoit pas d'identité inventée.
- Ancrer les labels aux coordonnées du bitmap de travail, avec les transformations
  communes de zoom/déplacement/rotation. Garder lisibles les labels visibles, éviter
  les collisions simples entre voisins et ne pas afficher ceux des visages sortis
  du cadrage. Le retour à l'image entière relance un résultat cohérent avec l'original.
- Préserver les analyses et actions d'objets, les informations botaniques et les
  réponses ChatGPT. Les annotations restent une couche de présentation : aucune
  mutation de la photo ni injection automatique des noms dans les requêtes externes.
- Préserver l'abstention individuelle : un encodage invalide ou une erreur locale
  de visage ne doit pas attribuer un faux nom ni supprimer les autres résultats.
  Une défaillance globale du moteur conserve un état explicite. Ne pas transformer
  annulation ou manque de mémoire en résultat silencieusement partiel.

## Modèle, exécution et données

Conserver YuNet, l'alignement, ArcFace ResNet50, les poids, la galerie évaluée et
la politique figée 0,30/0,50/0,05. Aucune nouvelle calibration sur le test final.
L'inférence reste locale, sur photo stable ; la caméra live est hors périmètre.

Sérialiser les encodages sur le moteur, sans paralléliser les réseaux ni conserver
tous les crops natifs en mémoire. Vérifier l'annulation entre les visages et après
les appels natifs. Un changement de photo/galerie, la navigation ou le passage en
arrière-plan ne doit pas publier un ancien lot. Conserver la fermeture différée
et l'autocontrôle public avec mémorisation des incompatibilités déterministes.
Aucune limite silencieuse ne doit tronquer les visages détectés.
La borne de sécurité existante de vingt candidats est conservée : au-delà,
refuser explicitement le lot avec « Trop de visages, recadrez l’image », au lieu
de nommer seulement les vingt premiers sans l'indiquer. Ne pas changer le profil.

Version cible : 0.18.0/code32. APK personnelle ARM64 avec le même bootstrap privé,
stockage chiffré existant conservé. Aucune manipulation JSON demandée à l'utilisateur,
photo personnelle dans les fixtures ou transfert vers un service externe.

## Vérification et budgets

TDD sur les cas zéro/un/trois visages : deux identités connues et un inconnu,
abstention, galerie à une personne, noms répétés, ordre des détections, visage
invalide, annulation entre encodages, fermeture pendant travail et lots périmés.
Tests UI : plusieurs labels ancrés, aucun cadre facial, zoom/crop/rotation et
labels proches des bords. Les tests doivent vérifier des comportements observables.

La parité native doit être reproduite après les changements de classes, sur le
seul émulateur PC explicitement ciblé, avec une donnée de couverture fraîche.
Compléter la référence publique multiple afin de comparer chaque visage au PC,
sans modifier les tolérances (boîtes/repères ≤1px, MAE RGB ≤1, cosinus ≥0,9999,
delta des scores ≤0,002). Versionner/documenter l'évolution du manifeste de test
si nécessaire ; le paquet de galerie reste v1. Le manifeste de parité passe en
v2 : le cas multiple contient la liste des attentes par visage (boîte, repères,
PNG aligné, embedding). Sortie distincte `build/fixtures/m3-05`, archives M3-04
conservées. La taille du manifeste de parité est bornée séparément à 1Mio pour
permettre vingt attentes complètes ; la limite de 120Kio du paquet personnel de
galerie v1 ne change pas. Aucun test APK sur le Xiaomi.

Objectifs Xiaomi à recueillir après ouverture manuelle : trois visages chauds
≤15s, premier lot de trois avec chargement/autocontrôle ≤30s. Ce sont des budgets,
pas des résultats mesurés. Le budget de pic mémoire supplémentaire reste ≤700Mio ;
les instantanés PSS ne mesurent pas ce pic. Consigner les temps et le nombre de
visages uniquement, jamais noms, photos, embeddings ou contenu de galerie.

Build/tests/SAST/couverture/duplication/licences : conserver les contrôles et seuils
d'AGENTS.md. Revue indépendante puis testeur sur l'état stable, contrôles globaux
archivés avant les tests ciblés suivants, puis mise à jour du seul APK principal.
La validation sur le téléphone se fait par l'utilisateur et ses logs Blamera
filtrés par PID ; aucune manipulation automatique ou lecture de données privées.
