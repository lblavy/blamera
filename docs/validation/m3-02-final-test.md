# M3-02-B-02 — gel de calibration et ouverture du test final

1er novembre 2026, baseline `83d3645`, PC 0.6.0.
[Contrat](../specs/face-final-test.md), [recette](../tests/m3-final-test-recette.md).
Sources et tests conformes après revue indépendante et validation du testeur.
PC 0.6.0 disponible sur http://127.0.0.1:8501/ ; paramètres appliqués et gel
enregistré. La confirmation des cibles privées et les mesures réelles du Test
restent en attente et sont distinctes de ces preuves logicielles.

## Comportement et TDD

Gel explicite dans les settings de la base d'évaluation, à partir d'un rapport
de calibration courant. Le manifeste conserve sources / annotations de tous les
cas, mais ne dépend pas des préparations finales : ces choix humains peuvent
suivre le gel. Le rapport de test, lui, dépend de leur état complet. Les archives
`final_runs` ne remplacent ni la calibration ni un ancien résultat final.
Les pixels déjà évalués interdisent une nouvelle ouverture sous un autre contexte ;
changer les choix après exposition rend l'historique non courant sans le réécrire.

Preuves réelles sous `build/reports/validation/m3-02-final-test/coder/` :

- `red-service-functional.log` : cinq tests, quatre échecs fonctionnels et une
  erreur de valeur absente contre les façades vides, sortie 1.
- `green-service.log` : 21 tests final / service / sélection, sortie 0.
- `red-ui-functional.log` : deux parcours échouent car actions de gel / test
  absentes ; `green-ui.log` : treize AppTest final / évaluation / sélection, sortie 0.
- `red-corruption.log` : absence de politique dans une archive produit KeyError ;
  `green-corruption.log` : quinze tests après validation explicite du contrat, sortie 0.

Les essais initiaux bloqués par les droits TEMP / cache mypy / initialisation
Streamlit dans le bac à sable ne constituent pas les preuves Red. Réexécution
sur bases synthétiques temporaires sous `build/`, avec droits adaptés. Aucun test
sur la galerie privée, aucun téléphone et aucun service d'inférence externe.

## Contrôles

Passage final PC 0.6.0 : 117 tests en 26,868 s, sortie 0 ; format, Ruff C901≤10,
mypy strict, couverture et dépendances conformes. Couverture canonique globale
1549/1556 lignes, 99,55 %, et 387/394 branches, 98,22 % ; nouveau code 98,31 %
lignes / 98,08 % branches. Duplication nouvelle 0/261 lignes, cognitive maximale
13≤15. Trente-six paquets runtime / licences, aucun avis actif retourné par le scan
dans son périmètre. Aucune règle ou exclusion ajoutée / assouplie. Rapports finaux
et empreintes archivés dans `coder/quality-final/` et `coder/source-snapshot.json`.
Les résultats réels du lot final restent à mesurer après confirmation des cibles.

## Validation indépendante et mise à disposition

Revue : aucun blocage, vérification supplémentaire du refus de réutilisation après
suppression / réimport sous un nouvel identifiant. Testeur : 117 tests en 22,279 s,
sortie 0, AppTest compris ; scénario synthétique supplémentaire de quinze cas
conforme, dont quatorze exploitables et un sans visage. Préparation partielle
refusée, calibration conservée, relecture après reprise, changement de seuil après
exposition refusé et galerie principale identique. Les 50 empreintes de l'état
qualifié étaient stables à la validation ; les seules modifications suivantes
concernent les statuts documentaires et la présente restitution.

Rapports locaux : `build/reports/validation/m3-02-final-test/tester/report.md`,
`tester/state-check.json` et `application-summary.json`. Le recalcul de Calibration
avec rejet 0,30 / acceptation 0,50 / marge 0,05 confirme neuf bons noms sur dix
proches, une incertitude et cinq inconnus rejetés, aucun faux nom. Le gel est
persisté avant toute sélection ou prédiction finale. Les quinze cas restent
présents : zéro cible sélectionnée / encodée à la publication, un cas sans visage.
L'intégrité binaire de la galerie principale reste identique à l'import initial :
sept photos et sept confirmations, aucune modification des références.

Serveur redémarré avec le profil existant ; worker et lanceur identifiés,
endpoint de santé HTTP 200. Cette disponibilité ne remplace pas la recette
utilisateur ni une preuve de qualité statistique. Aucun changement Android.

### Correction du lancement après mise à disposition

Le premier lancement manuel du serveur a omis `PYTHONPATH=pc/src`, contrairement
au lanceur documenté `scripts/pc.ps1 Run`. Le endpoint de santé répondait 200,
mais l'ouverture de la page produisait `ModuleNotFoundError: blamera_pc`, signalé
par l'utilisateur. Redémarrage avec le chemin absolu du dossier source dans
l'environnement du processus enfant. Aucun code applicatif ni profil changé ;
galerie principale et gel vérifiés identiques, deux lots de quinze cas conservés.
Validation indépendante complémentaire conforme : import, rendu AppTest réel du
point d'entrée et navigation Évaluation sans exception dans un processus neuf,
avec cet environnement, sur une galerie temporaire et sans correction de
`sys.path` dans les tests. Sortie 0 ; sources applicatives inchangées.
Preuve locale : `tester/launch-fix-report.md` dans le dossier de validation.
