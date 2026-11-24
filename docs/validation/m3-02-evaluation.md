# Validation M3-02-B — évaluation exploratoire sur PC

1er novembre 2026, baseline `ea5fed3`, cible PC 0.5.0.
Contrôles PC, revue et validation fonctionnelle indépendants conformes ; serveur
utilisateur relancé, recette personnelle en attente. [Contrat](../specs/face-evaluation.md),
[décision](../decisions/0016-face-evaluation-protocol.md),
[recette](../tests/m3-face-evaluation-recette.md).

Corpus séparé des références, séances, calibration exploratoire et réserve de
test final. Pas d'optimisation automatique ni de reconnaissance Android dans ce
lot. Les scores et résultats sur fixtures ne démontrent pas la qualité sur les
proches. Le profil ResNet50 et les poids restent inchangés.

Le serveur utilisateur est arrêté avant toute écriture du checkout actif :
worker 62928, lanceur 52432, identité / démarrage / commande vérifiés. Reçu
`build/reports/validation/m3-02-evaluation/stopped-server.json`.
Aucune lecture de la galerie personnelle, de ses photos ou du navigateur privé.

## Implémentation et TDD

Personnes → Références / Évaluation. Le corpus réutilise les composants de galerie
et de préparation dans une base distincte `<data>/evaluation/gallery.sqlite3`,
sans modifier le schéma ou les données de la galerie principale. Photos et
annotations de cas sont importées atomiquement. Séances et doublons normalisés
exacts sont contrôlés, corrections et suppression volontaire disponibles.

Les références compatibles et confirmées alimentent les prototypes moyens
normalisés. Cosinus et seuils exploratoires rejet 0,30 / acceptation 0,60 / marge
0,05 ; une seule identité conserve une possibilité d'inconnu. Le rapport inclut
effectifs et dénominateurs, faux noms, rejets, incertains et non exploitables.
Les données de Test final réservé ne produisent pas de prédiction.

Rapport lié à un instantané du profil, politique, identités, sources, séances,
générations, sélections et empreintes de vecteurs. Le court calcul pur et la
publication prennent les verrous dans l'ordre base principale → évaluation ;
aucun appel natif ou interaction utilisateur pendant ces transactions. Le rapport
est masqué comme périmé si une entrée pertinente change. Les empreintes permettent
d'identifier les données du calcul ; rejouer une expérience exige de conserver
les données correspondantes, ce lot n'archive pas toutes leurs versions historiques.

Preuves dans `build/reports/validation/m3-02-evaluation/coder/` :

- Red puis Green des décisions / prototypes, stockage, persistance des seuils,
  service de rapport et parcours AppTest. Les assertions fonctionnelles sont
  distinguées des erreurs d'infrastructure, d'indentation ou de fixtures.
- Régressions réellement observées : erreur d'évaluation masquant le corpus,
  identité retirée affichée comme inconnue et rapport relu incomplet accepté.
  Chacune a son Red fonctionnel archivé avant correction, puis Green.
- Un premier Green UI rencontre le timeout AppTest de 3 s sur le rapport.
  Rejoué seul avec un diagnostic faulthandler, le même cas passe en 0,986 s sans
  changer délai, code ni assertion. Les suites ultérieures passent ; la cause
  initiale n'est pas attribuée avec certitude.
- Une fonction cognitive 23 est refusée, puis extraite en validations des
  références, du corpus et des préparations. Aucun seuil qualité abaissé.

## Qualité sur le gel

`pc/.venv/Scripts/python.exe scripts/quality_pc.py --base-ref ea5fed3`, sortie 0.
95 tests en 19,520 s, format / lint Ruff, mypy strict, complexités, couverture,
duplication, lock et audit conformes. Archives `coder/quality-final/`, rapport
`coder/report.md` et 42 empreintes `coder/source-snapshot.json`.

| Périmètre PC | Lignes | Branches |
| --- | --- | --- |
| Canonique global | 1323/1327, 99,70 % | 314/320, 98,13 % |
| Nouveau code | 99,80 % | 98,31 % |

Chaque nouveau composant mesurable dépasse 80 % / 80 %. Duplication nouvelle
0/890 lignes physiques, globale 0 % ; 20 sources / 2244 lignes analysables.
Cognitive maximale 13≤15, cyclomatique Ruff C901≤10. Aucune exclusion de
production ajoutée ; versions runtime inchangées. 36 paquets, 36 licences,
aucun avis actif renvoyé dans ce périmètre au scan. Contrôles Android et scripts
inchangés non relancés, aucune nouvelle preuve mobile revendiquée.

## Vérification native et navigateur sur données publiques

Galerie QA distincte `browser-data/` sous le dossier de preuve, portraits NASA
autorisés du spike initial et uniforme synthétique. Un portrait de référence,
un dérivé du même portrait pour exercer le cas connu, l'autre portrait comme
inconnu, un uniforme et un dérivé réservé. Les séances sont fictives de test
logiciel ; le positif dérivé n'est pas une photo indépendante. Ce jeu ne mesure
pas la qualité de reconnaissance sur une population.

Le moteur de production prépare une référence 512D, trois cas de Calibration
(un / un / zéro visage) et un cas réservé (un visage). Le navigateur exécute
Évaluer la calibration : deux images exploitables sur trois, un bon nom, un
inconnu rejeté, un non exploitable, zéro faux nom dans ce jeu ; un cas réservé
non évalué. Les effectifs et résultats par image / identité sont visibles.

Seuil d'acceptation 0,60→0,65 : rapport à recalculer. Recalcul puis rechargement
et réouverture d'Évaluation conservent cas / rapport. Le lot réservé affiche son
seul cas sans ligne de prédiction supplémentaire. Retour à 0,60 et recalcul.
42 empreintes du gel recontrôlées après cette séquence.

Preuves `seed-browser.py/json`, `browser-check.json`,
`check-browser-report.py` et capture `browser-report.png` (données publiques
seulement). Les imports QA sont préparés par API ; le clic d'évaluation et les
autres actions ci-dessus sont réalisés dans le vrai navigateur. Les contrôles
des imports UI sont des AppTest et une recette utilisateur distincte, pas une
preuve de choix de fichiers réel déduite de ce seed.

## Revue et validation indépendante

Reviewer : conforme, aucun défaut bloquant. 42 empreintes identiques, contrats,
transactions, décisions aux limites, séparations, invalidation, UI, pertinence
des tests et archives vérifiés. Aucun contrôle affaibli ni métrique absente
présentée comme conforme. Le reviewer ne relance pas les tests.

Testeur : 95 tests, sortie 0 en 16,635 s, puis six cas complémentaires conformes :
faux noms entre deux proches et sur un inconnu, égalité de marge / valeur flottante
supérieure, sélection de probe ou de référence en attente de confirmation,
rollback photo et annotation, séance normalisée et doublon. Aucune source ou
archive de couverture du coder modifiée pendant ces contrôles.

Smoke natif indépendant dans une base temporaire avec les seuls portraits publics :
une référence 512D ; cas de Calibration avec un / un / zéro visage, un cas réservé.
Résultats Reconnu / Inconnu / Non exploitable ; rapport relu dans un second
processus, galerie principale de test conservée. Preuves `tester/` ; aucune
mesure de qualité statistique des proches n'en découle.

## Déploiement et prochaine validation

L'onglet QA est fermé et les seuls worker / lanceur QA identifiés sont arrêtés,
reçu `server-qa-stopped.json`. Après les validations, démarrage du serveur PC 0.5.0
sur la racine existante `pc/data` : lanceur 42912, worker écoutant 39756, démarrage
le 1er novembre 2026 à 18:22:13 UTC, http://127.0.0.1:8501. Reçu `server-user.json`
avec identité des deux processus, commande et santé HTTP200 `ok`. Seul le endpoint
de santé est lu pour ce serveur ; aucune photo ou base personnelle n'est inspectée.

L'utilisateur recharge, annote les séances, importe quelques cas connus et inconnus,
prépare leurs visages puis consulte le rapport exploratoire. Cette recette reste
ouverte. M3-02 reste ouvert pour la calibration réelle, le gel et l'ouverture du
test final. Aucun modèle ou APK nouveau n'est installé sur le téléphone.
