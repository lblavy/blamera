# Validation M3-02-A — passage à ArcFace ResNet50

1er novembre 2026. Baseline `11d76ac` (PC 0.3.0), cible PC 0.4.0.
L'utilisateur privilégie la qualité de reconnaissance et signale que le site
local est arrêté. PC 0.4.0 est conforme après revue et validation indépendantes,
serveur relancé. Le parcours essentiel est ensuite accepté : préparation des
visages effectuée, choix et confirmations conservés après rechargement.
[Incident transitoire et retour utilisateur](m3-02-profile-error.md).

## Périmètre

ArcFace ResNet50 `w600k_r50.onnx`, issu du pack officiel `buffalo_l`, remplace
SFace pour les nouvelles références PC. YuNet conserve la détection. La galerie
reste locale ; les anciennes références sont incompatibles avec le nouveau
profil et doivent être préparées explicitement. Aucun changement Android,
reconnaissance par nom ou seuil d'identité calibré dans ce lot.

Poids de reconnaissance : 174383860 octets, SHA-256
`4c06341c33c2ca1f86781dab0e829f88ad5b64be9fba56e56bc9ebdefc619e43`.
Les résultats publiés motivent le choix ; la qualité sur des proches et des
inconnus reste à évaluer sur des données indépendantes. La licence des poids
InsightFace réserve leur utilisation à la recherche non commerciale.

## Serveur

Le premier redémarrage demandé utilise la même racine privée `pc/data`, sans
inspection de son contenu. Le endpoint `/_stcore/health` répond HTTP200 `ok`.
Reçu et preuves du lot dans `build/reports/validation/m3-02-resnet50/`.
Après revue et validation indépendantes, le seul serveur créé par l'agent est
arrêté après vérification de son PID, heure de démarrage et commande. Nouveau
serveur PC 0.4.0 : PID 47704, démarré le 1er novembre 2026 à 16:02:46 UTC,
URL http://127.0.0.1:8501. Même racine de données, aucune lecture de photos ou
base privée par les outils. Santé HTTP200 `ok`, reçus `server-user.json` et
`server-health.json`. L'utilisateur recharge son navigateur lui-même.

## Implémentation et TDD

OpenCV DNN CPU 4.13.0.92 existant charge ResNet50 ; aucun runtime ajouté. SFace
reste une ressource de `FaceRecognizerSF.alignCrop` pour conserver l'alignement
natif cinq points sans réimplémentation. Sa méthode `feature` n'est plus appelée.
Le graphe reçoit RGB/NCHW float32 112×112 `(pixels−127,5)/127,5` ; la sortie
exacte 1×512 est contrôlée, finie, de norme non nulle et normalisée L2.

Profil `4a6472a476b37ec36626f41542d7fd41f13da63dfbeb09cd1ffbc480993aded1` :
manifestes, poids, runtime et prétraitement le distinguent du profil 128D.
Le provisionnement CLI vérifie sept fichiers. L'archive et son seul membre
de reconnaissance ont chacun taille et empreinte vérifiées, copie bornée,
activation atomique et conservation de l'ancien fichier si échec.

Rapport `coder/report.md`, logs Red/Green et archive `coder/quality-final/` :

- Red moteur avant adaptation : ancienne dimension/feature/prétraitement et
  chargement DNN ne satisfont pas le nouveau contrat. Green moteur : huit tests.
- Red manifeste : 128 au lieu de 512 et ancien profil accepté. Red UI : nom du
  modèle absent. Les erreurs de fixture 128D, d'encodage et de permissions Temp
  sont distinguées des échecs fonctionnels ; aucune n'est une preuve Red.
- Les cas détaillés d'archive sont ajoutés pendant Green après le Red du
  manifeste. Il manque un Red comportemental propre à l'extraction d'archive :
  écart de méthode signalé, pas un historique TDD reconstitué. La revue juge
  leur pertinence et la validation indépendante vérifie ce contrat.
- Un premier passage global révèle une régression de légende UI, corrigée en
  conservant l'ancien test. Aucun test, seuil ou contrôle n'est affaibli.

## Qualité sur le gel

`pc/.venv/Scripts/python.exe scripts/quality_pc.py --base-ref 11d76ac`, sortie 0 :
66 tests en 9,561 s, format/lint Ruff, mypy strict, complexités, environnement
verrouillé, couverture, duplication et audit conformes.

| Périmètre PC | Lignes | Branches |
| --- | --- | --- |
| Canonique global | 816/819, 99,63 % | 198/202, 98,02 % |
| Nouveau code | 98,88 % | 97,22 % |

Chaque composant mesurable satisfait 80 % / 80 %. Duplication nouvelle 0/118
lignes physiques, globale 0 % ; 13 sources et 1259 lignes analysables.
Cognitive maximale PC 12≤15, cyclomatique Ruff C901≤10. Aucun nouveau filtre
de couverture ; les ellipsis de Protocol suivent le défaut de coverage.py.
36 paquets runtime, 36 licences, aucun avis actif renvoyé par OSV dans ce
périmètre au scan. Les versions des dépendances d'exécution ne changent pas.
Les contrôles Android/scripts inchangés ne sont pas relancés pour ce lot PC.

`scripts/pc.ps1 Models`, sortie 0, vérifie les sept fichiers déjà présents.
Les poids InsightFace et leurs conditions sont figés au commit du tag `v0.7`,
`3e6486942a1be2da0e5b475fac375ea73264bd21`, avec la licence MIT du code
distincte de la restriction de recherche non commerciale des poids.
31 empreintes de sources/configuration/tests/scripts sont gelées dans
`coder/source-snapshot.json` avant revue et testeur.

## Vérification native

Moteur de production OpenCV comparé à ONNX Runtime CPU 1.31.1 dans l'environnement
expérimental séparé, même poids et même alignement, normalisation de référence
NumPy indépendante. Sur les deux portraits publics NASA, un visage chacun,
512 valeurs normalisées, cosinus ≈1 et écart maximal des vecteurs L2 6,94×10⁻⁷.
Preuves `coder/native-parity.py` et `coder/native-parity.json`.
Ce contrôle confirme la portabilité numérique, pas une précision d'identité ni
une performance Android. Aucune galerie personnelle n'est inspectée.

## Revue et validation indépendante

Reviewer : conforme, aucun défaut bloquant établi. Les 31 empreintes sont
identiques au gel ; diff, prétraitement, profils, archive, pertinence des tests
et métriques archivées vérifiés. Aucun test supplémentaire lancé par le reviewer.

Arbitrage de l'orchestrateur : accepter l'écart TDD d'archive comme limite de
méthode non bloquante, au vu des tests d'erreurs, de la revue et de la validation
indépendante ci-dessous. Ne pas inventer de Red ni modifier artificiellement du
code validé pour en produire un après coup. Suivi `M3-02-TDD-01` : au prochain
changement du provisionnement, exécuter d'abord un Red fonctionnel propre au
contrat modifié. Aucun changement des directives TDD générales.

Testeur : conforme sur les 31 fichiers gelés. 66 tests, sortie 0 en 5,841 s,
sans remplacer la couverture archivée. Smoke natif indépendant : deux portraits
NASA, un visage chacun ; uniforme, zéro. Références 512D normalisées relues
après réouverture. Migration v1 synthétique et transition 128D→512D conservent
photos/noms, rejettent l'ancien profil sans le supprimer au démarrage et
enregistrent le nouveau profil uniquement après préparation explicite.

Les sept fichiers sont vérifiés hors réseau ; une archive dont le CRC est
corrompu est rejetée, ancien poids conservé et aucun temporaire `.part` restant.
Parité indépendante ORT/OpenCV : cosinus ≈1 et écart maximal L2 6,42×10⁻⁷.
Un premier helper de test avait une connexion SQLite de fixture non fermée au
nettoyage ; il est corrigé dans l'artefact temporaire et relancé avec succès,
sans changement du code de production. Preuves `tester/`.

Le serveur est redémarré sur ce même gel. L'utilisateur signale ensuite une
erreur de profil apparue en travaillant sur sa dernière image ; après redémarrage
complet et rechargement, il confirme sa disparition et ses choix / confirmations
conservés. Parcours essentiel accepté, sans revendiquer tous les cas optionnels
de la recette. M3-02 reste en cours pour les données indépendantes, inconnus et
calibration ; ce retour ne mesure pas la précision des identités.
