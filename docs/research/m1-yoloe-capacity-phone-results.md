# YOLOE Small / Large / Xlarge — mesures sur Xiaomi

28 octobre 2026, Blamera **0.6.0/code 10**, Xiaomi 14T Pro Android 16.
**Comparaison manuelle complète, capture et validation indépendante conformes.**
L apporte un gain de reconnaissance utile sur l’étude annotée ; X retrouve davantage
d’objets des catégories communes. Le téléphone permet désormais de chiffrer leurs
coûts : ces durées ne suffisent pas à choisir ou éliminer un modèle.
Le modèle des parcours habituels reste inchangé.

| Modèle | NASA, total médian (ms) | Scène dense LVIS 371699, total médian (ms) | Plus grand PSS processus échantillonné pendant ce modèle (MiB) |
| --- | ---: | ---: | ---: |
| YOLO11n actuel | 194,906 | 260,831 | 278,34 |
| YOLOE Small | 1 001,020 | 1 010,032 | 1 093,89 |
| YOLOE Large | 2 796,850 | 2 786,496 | 1 633,35 |
| YOLOE Xlarge | 5 524,001 | 5 520,142 | 1 914,74 |

**Total : tenseur prêt → masques et isocontours prêts.** Prétraitement, création du
tenseur, lecture/chargement du modèle et UI exclus. Chaque médiane vient des dix
totaux appariés, pas d’une somme de médianes. Ce n’est ni un FPS caméra ni la latence
complète d’une photo. PSS : échantillons du processus, **pas pics ni mémoire propre
du modèle**. Les valeurs après fermeture des sessions vont de 143,75 à 176,47 MiB.

Pour L/X, `run + NMS` représente environ 2,68 s / 5,41 s, contre environ
0,10–0,12 s pour masques/isocontours et 3,5 ms pour la copie. Dans ce traitement
mesuré à partir du tenseur prêt, le coût principal est donc le réseau avec NMS,
plutôt que les contours. Le temps de NMS seul n’est pas isolé.

## Ce qui est établi

L’utilisateur a ouvert et démarré l’application manuellement, confirmé la comparaison
terminée puis la réémission finie. L’orchestrateur n’a lu que les logs du PID Blamera
et du tag `BlameraBenchmark`, avec le collecteur prévu : sortie 0, **261 fragments**,
rapport complet reconstitué et vérifié par SHA. Analyse CLI épinglée : sortie 0.
Run `3e014101-48b1-4e58-aad4-330c70ad576f`, commencé à **16:19:17.436 UTC**
(17:19 CET), durée du run rapportée **420,25 s**, soit environ sept minutes.

Le rapport contient 56 résultats de qualité, huit blocs de performance,
80 mesures et huit premiers appels. Les 24 échauffements sont déclarés par le
protocole et ne sont pas persistés individuellement ; le total de 168 appels est
cohérent avec le code testé et les données récupérées.

CPU ORT 1.31.0, 2 threads intra / 1 inter, FP32 640². Les quatre graphes compacts
incluent NMS : par classe pour baseline, agnostique pour PF, score 0,35 / IoU 0,5 /
cap 20, sans pré-cap 300. Le [protocole et ses différences avec l’étude](../validation/m1-yoloe-capacity-phone.md)
restent applicables. Les détails lecture/SHA, copie, session, première exécution,
run/NMS et post-traitement sont conservés dans le rapport complet.

Ordre fixe baseline → S → L → X, une session à la fois. Les caches de modèle
étaient présents pour baseline/S/L, absents pour X. Ni caches globaux ni état
thermique contrôlés ; les chargements ne sont pas une comparaison à froid équitable.
Seul le premier appel NASA de chaque modèle utilise une session neuve.

## Comparabilité avec le PC

Les 14 tenseurs sont identiques aux références Kotlin épinglées, pour les quatre
modèles. Les 437 détections sont appariées, sans nom/classe différent ni objet
non apparié. Les 437 masques binaires **160² MSB-first** sont identiques au PC,
IoU 1 ; IoU minimale des boîtes 0,9999898024 et écart maximal de score
2,38445 × 10⁻⁶.

Cette parité valide la reproduction de ce diagnostic, **y compris les erreurs de
reconnaissance du PC**. Elle ne transforme pas les prédictions PC en vérité terrain.
Des masques binaires 160² identiques ne prouvent ni l’identité de la géométrie des
isocontours interpolés, ni celle de toutes les sorties natives internes.

## Ce qui motive la suite

La [comparaison annotée sur 144 images](m1-yoloe-capacity-results.md) reste la base
qualité, avec son mapping figé, ses catégories partiellement annotées et ses
limites. Au profil principal, L et X retrouvent chacun **67 nouveaux objets typés** ;
L couvre **11 familles nouvelles contre 10 pour X**, tandis que X retrouve
**55 objets communs contre 49 pour L**. Les gains de contours sur les mêmes GT
appariées sont modestes et non uniformes. Les noms fins, confusions et objets
manqués restent importants ; le nombre de libellés ne garantit pas leur fiabilité.

**L reste un candidat à approfondir pour l’analyse d’une photo, X un comparateur
de qualité**, sans décision d’usage « photo seulement ». Baseline et S restent
des candidats à évaluer pour l’analyse directe : les bénéfices par catégorie et
la qualité du détourage doivent être mis en regard des coûts et de la fluidité
réellement recherchée. Aucun remplacement ou réglage n’est décidé ici, et la
latence seule n’écarte aucun modèle. M n’a pas été mesuré sur téléphone dans ce run.

L’utilisateur indique **« Pas de chauffe gênante »** : constat subjectif sur un
seul run d’environ sept minutes, sans mesure de température/batterie ni validation
d’une cadence durable. Annulation/erreur et retour aux parcours habituels ne sont
pas validés par ce run. **M1 et D-M1-02 restent ouverts.**

## Preuves locales

- [Capture filtrée](../../build/reports/validation/m1-yoloe-capacity-phone/device/capture-20261028-162712.log).
- [Rapport complet](../../build/reports/validation/m1-yoloe-capacity-phone/device/analyzed-20261028-162712/report.json),
  [synthèse JSON](../../build/reports/validation/m1-yoloe-capacity-phone/device/analyzed-20261028-162712/summary.json)
  et [tableaux](../../build/reports/validation/m1-yoloe-capacity-phone/device/analyzed-20261028-162712/summary.md).
- [Validation indépendante téléphone](../../build/reports/validation/m1-yoloe-capacity-phone/device/tester-phone-results.json).
- [Recette D01/D02](../tests/m1-capacity-phone-recette.md) ; données et artefacts restent locaux.
