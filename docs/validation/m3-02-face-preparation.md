# Validation M3-02-A — préparation des visages

Rapport historique du premier profil PC 0.3.0/SFace. L'utilisateur a ensuite
privilégié ResNet50 ; [l'évolution PC 0.4.0](m3-02-resnet50.md) décrit le profil
actif et le nouveau redémarrage du serveur. Les preuves ci-dessous restent
celles du premier lot, sans validation utilisateur ajoutée par déduction.

Baseline `5e9d5eae053d622b845bb4e5ec079169ba681188`, PC cible 0.3.0.
Comparaison initiale et intégration PC validées indépendamment. PC 0.3.0
disponible ; recette utilisateur des visages en attente, M3-02 toujours en cours.

[Contrat](../specs/face-reference-preparation.md),
[décision provisoire](../decisions/0015-face-reference-model-profile.md),
[recette](../tests/m3-face-reference-recette.md).

Le spike de comparaison utilise uniquement des modèles et fixtures publiques
autorisées dans un environnement isolé. Les photos personnelles et embeddings
de l'utilisateur ne sont pas lus par l'agent. Les modèles InsightFace ne sont
pas ajoutés au runtime de la première intégration PC.

Les preuves ci-dessous distinguent logiciel, spike et essais réels. La préparation des références précède la
calibration connu/inconnu/incertain ; M3-02 ne sera pas clôturé par ce seul lot.

## Intégration de production et TDD

Le lot ajoute la préparation explicite, les aperçus zéro/un/plusieurs visages,
le choix humain et la confirmation avant stockage d'une empreinte normalisée.
Migration SQLite v1→v2 additive et transactionnelle, reprise des choix, profil
versionné, invalidation sur réaffectation et cascade à la suppression. Révision
de photo et génération de détection empêchent les publications obsolètes, y
compris après déplacement aller-retour ou nouvelle détection durant un calcul.

Preuves `build/reports/validation/m3-02-face-preparation/coder/` : Red/Green
successifs pour stockage, provisionnement, remappage non carré/alignement,
service et interface. Les erreurs de faux doubles/namespace et fixtures sont
distinguées des vrais Red ; deux régressions de génération et une validation
de crop aligné échouent fonctionnellement avant correction. Rapport détaillé
`coder/report.md`, avec commandes et sorties, sans reconstituer les exécutions.

Premier contrôle : 48 tests verts, mais couverture nouvelle des branches
73,85 %, gate en échec. Les cas d'erreurs de modèles, manifestes, références
corrompues et sorties natives sont complétés ; aucune règle n'est abaissée.

Dernier `scripts/quality_pc.py --base-ref 5e9d5eae053d622b845bb4e5ec079169ba681188` :
sortie 0, passed, 58 tests verts en 7,998 s. Ruff format/lint, mypy strict,
complexités, couverture, duplication, environnement verrouillé et audit conformes.

| Périmètre PC | Lignes | Branches |
| --- | --- | --- |
| Global canonique | 749/753, 99,47 % | 177/180, 98,33 % |
| Nouveau code | 99,19 % | 97,69 % |

Chaque nouveau composant mesurable dépasse 80 % lignes/branches. Six lignes
« ... » des méthodes de Protocol relèvent des filtres par défaut de coverage.py
7.10.7, sans filtre ajouté visant Protocol ; aucune exclusion
de configuration ou logique métier ajoutée. Duplication 0/670 nouvelles lignes,
13 sources/1160 lignes analysables ; globale nulle. Cognitive maximale PC 11≤15,
cyclomatique Ruff C901≤10. Archives `quality-final/`, 398 empreintes root et
27 empreintes coder, sans source modifiée depuis le gel.

Runtime PC : 36 paquets et 36 licences, aucun avis actif retourné dans ce scope
au scan. OpenCV headless 4.13.0.92 ajouté ; NumPy existant déclaré directement.
Sa normalisation de métadonnées Apache 2.0 est liée à la version et à son hash,
sans licence inconnue autorisée. Wrapper MIT, bibliothèque Apache et notices
natives, dont FFmpeg LGPL, sont distingués des licences MIT/Apache des deux poids.
L'audit runtime des métadonnées ne revendique pas un audit complet des binaires natifs.

Le CLI `scripts/pc.ps1 Models`, sortie 0, vérifie poids et notices du manifeste ;
profil `c9f4027256424bb15b761672d11daea61eae80f25847e4b22c5f863e9f14db97`.
Les contrôles Android et scripts Python inchangés ne sont pas relancés pour ce
lot PC ; leurs dernières preuves restent celles de 0.15.1.

## Smoke réel et navigateur isolé

Adaptateur de production OpenCV CPU, fixtures publiques NASA uniquement : un
visage dans 512², un dans 1892×1502, aucun dans l'uniforme. Les confirmations
produisent 128 valeurs, norme ≈1 et référence retrouvée à la réouverture.
Le chargement est mesuré à 93 ms, détection 41/93 ms et confirmation 64/81 ms
dans ces appels isolés ; aucune de ces mesures n'est un budget ou un test Android.
Preuve `coder/smoke-cpu.json`, sans vecteurs bruts journalisés.

Root teste le vrai navigateur sur `http://127.0.0.1:8502`, base QA séparée :
portrait public, collage des deux portraits et fond uniforme. Préparation 1/2/0,
aucune radio précochée, enregistrement désactivé avant choix, confirmation du
candidat 1 puis du candidat 2 dans le groupe, « Visage confirmé » et crops visibles.
Après rechargement, choix et références conservés, résultat négatif conservé.
Le nom de personne est factice : il s'agit d'un test d'interface, pas d'une
galerie d'identités cohérente ni d'une mesure de qualité de reconnaissance.
Capture `browser-group.png`, uniquement des images publiques, crédit NASA.

La galerie personnelle n'a pas été inspectée. Après validation indépendante,
seul le processus serveur créé par l'agent pour M3-01 est arrêté, identité de
processus/commande revérifiée, puis le serveur 0.3.0 reprend la même racine pc/data.
URL http://127.0.0.1:8501, nouveau PID 59560, endpoint de santé 200/ok uniquement.
Reçus `server-user.json`, `server-update.json`, `server-health.json`. Aucune lecture
des contenus de la galerie utilisateur ni autre application par les outils.
Aucun modèle de visage ou nouvelle APK installé sur le téléphone dans ce lot.

## Validation indépendante de production

Reviewer : conforme, sans blocage sur le gel. Testeur : 58 tests PC, sortie 0
en 5,023 s, puis sept contrôles complémentaires : déplacement en conflit sans
perte, pixels modifiés pendant encodage, ABA déplacement aller-retour, cascade de
photo, redétection en erreur conservant l'ancien résultat, erreur native sans
détails privés et géométrie d'image portrait impaire. Tous passent.

Le smoke de production est rejoué indépendamment sur les seules fixtures publiques,
avec 1/1/0 visages, confirmation explicite, 128 valeurs L2 relues après réouverture.
La commande Models est aussi exécutée sur les fichiers valides, sortie 0, avec
PYTHONPATH restauré. Les tableaux, couvertures par module, lock, licences et
398/27 empreintes sont vérifiés. Les résultats des tests indépendants ne remplacent
pas les archives de couverture du gel. Rapport `tester/report.md`.

La prochaine validation appartient à l'utilisateur : retrouver ses photos,
préparer et confirmer les bons visages, vérifier groupe/zéro visage et reprises.
Le choix final de reconnaissance, le calibrage des seuils et le jeu indépendant
d'inconnus restent M3-02 ; export/import et Android restent M3-03/M3-04.

## Évaluation exploratoire distincte de la production

[Comparaison détaillée](../research/m3-face-evaluation.md),
[protocole et manifeste](../../experiments/m3-02-face-evaluation/README.md).
Exception TDD justifiée pour ce spike exploratoire CPU borné et isolé : les scripts
mesurent trois poids préentraînés, sans devenir du code de production.

SFace 38,70 Mo/128 valeurs, MobileFaceNet InsightFace 13,62 Mo/512 valeurs,
ArcFace ResNet50 174,38 Mo/512 valeurs ; même détecteur YuNet et même alignement
pour isoler les reconnaisseurs. Ce n'est pas une comparaison des pipelines
complets SCRFD/InsightFace. Les prétraitements sont propres à chaque poids.

Sur i7-1365U CPU, ORT quatre threads, médianes d'empreinte seules : 9,54 ms,
4,81 ms et 303,38 ms respectivement. Deux échauffements et cinq répétitions,
charge/fréquence/température non contrôlées : mesures descriptives, pas budgets.
Le runtime de production SFace est OpenCV, dont une mesure native monothread
donne 20,95 ms ; aucune de ces valeurs n'est une latence Xiaomi.

Les deux portraits publics NASA et leurs dégradations fournissent dix images
avec un visage, un collage avec deux et un fond uniforme sans visage. Huit paires
positives et dix négatives dérivées ne donnent ni précision statistique ni seuil
validé. Le cosinus n'est pas une probabilité et ses échelles diffèrent par poids.

Revue indépendante du spike : conforme, onze assets vérifiés par SHA, sources et
licences revues, tableaux cohérents avec les JSON 1/4 threads. La variante SFace
à entrées constantes conserve les poids/nœuds ; parité sur dix crops, erreur
maximale 4,53×10⁻⁶, cosinus minimal 0,99999988. Cette variante reste expérimentale.

Root exécute séparément Ruff check, Ruff format --check et py_compile des trois
scripts : sorties 0. Journaux conservés dans
`build/reports/validation/m3-02-face-evaluation/`, avec résultats, manifeste et
vérifications. Ces contrôles de spike ne remplacent pas les gates du code PC.

Testeur indépendant du spike : verify.py réexécuté dans l'environnement isolé,
sortie 0, onze empreintes et parité reproduites. Cinq graphes ONNX, onze versions
épinglées, membres ZIP et médianes/extrema vérifiés ; deux fixtures invalides
rejetées. Les timings bruités ne sont pas relancés inutilement. Verdict conforme,
sans lecture du code PC alors instable ni des données personnelles.
