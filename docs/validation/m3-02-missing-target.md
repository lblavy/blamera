# M3-02-B-03 — sujet attendu absent des candidats

1er novembre 2026, PC 0.6.1, diff cumulatif depuis `83d3645` comprenant le parcours
0.6.0 déjà revu / validé. [Contrat](../specs/face-final-test.md),
[recette](../tests/m3-final-test-recette.md). Revue et testeur conformes ;
constat appliqué et évaluation finale exécutée par l'orchestrateur sur demande.

## Défaut et comportement

Sur un cas final, d'autres personnes sont détectées mais le sujet attendu de dos
est absent des candidats. Les treize autres confirmations et le cas sans détection
sont conservés. Aucun choix d'un autre visage ne doit être exigé ou automatisé.

Ajouter une table étroite `target_absences` dans la seule base d'évaluation.
Le constat volontaire est lié à la source / révision, annotation, profil et génération.
Il retire un ancien choix de ce cas et reste annulable ; une nouvelle préparation
ou confirmation ne conserve pas un constat actif trompeur. Les modifications
d'annotation effacent le constat, même si l'annotation est ensuite rétablie.

Une préparation partielle sans constat bloque toujours le rapport. Avec constat,
le cas est non exploitable, sans candidat / score / identité prédite. Le motif
Sujet attendu non détecté est distinct de Aucun visage détecté. Le dénominateur
reste quinze. Sans annotations nouvelles, le manifeste est identique à 0.6.0 ;
sur Test, le marqueur est dans la préparation que le contexte de calibration exclut.
Le gel courant et la calibration sont ainsi conservés sans nouveau réglage.
Après exposition, changer le constat ne réécrit pas l'historique final.

## TDD et contrôles

Preuves sous `build/reports/validation/m3-02-missing-target/coder/` :

- `red-regression.log` : deux candidats, cible absente, action de constat sans effet,
  puis rapport bloqué ; sortie 1 pour le défaut fonctionnel reproduit.
- `green-service.log` : 21 tests après implémentation, sortie 0.
- `red-ui.log` : bouton absent dans AppTest, sortie 1 ; `green-ui.log` : onze
  AppTest final / absence / évaluation, sortie 0.
- `green-extra.log` : dix tests dédiés, sortie 0, dont quinze cas / treize
  confirmations / deux motifs distincts, retour en arrière, génération ou source
  périmée, profil incompatible, conservation des empreintes et migration 0.6.0.

Les fixtures sont synthétiques. Aucun accès du coder aux données privées, aucun
modèle, service externe, téléphone ou dépendance ajouté. Le serveur utilisateur
est arrêté avant modification ; redémarrage et données réelles restent à
l'orchestrateur après revue et validation. Les contrôles qualité cumulatifs sont
exécutés sans abaisser les règles ; métriques finales dans le rapport du coder.

Passage final : `pc/.venv/Scripts/python.exe scripts/quality_pc.py --base-ref 83d3645`,
sortie 0, 128 tests en 30,092 s. Couverture canonique 1621/1628 lignes, 99,57 %, et
403/410 branches, 98,29 % ; nouveau code 98,81 % lignes / 98,57 % branches.
Duplication nouvelle 0/384 lignes, cognitive maximale 13≤15, cyclomatique C901≤10,
format / Ruff / mypy strict conformes. Trente-six paquets runtime et licences,
aucun avis actif retourné par le scan dans son périmètre. Aucune règle ou exclusion
ajoutée / assouplie. Archives propres à ce lot : `coder/quality-final/` et
`coder/source-snapshot.json` ; les preuves précédentes 0.6.0 restent conservées.

## Validation indépendante et bilan réel

Revue : aucun blocage, dix tests dédiés supplémentaires réussis. Testeur :
62 tests de service / interface réussis en 14,692 s et scénario synthétique
supplémentaire de quinze cas conforme. Les 55 empreintes de l'état qualifié
étaient identiques ; rapport sous
`build/reports/validation/m3-02-missing-target/tester/report.md`.

Après ce GO, l'orchestrateur a enregistré le constat pour l'image signalée par
l'utilisateur, puis lancé le test une seule fois. Les treize confirmations
précédentes, les annotations des cas, le gel et la galerie principale sont
restés identiques. Aucun nouveau réglage ni recalcul de calibration.

| Mesure | Résultat réel |
| --- | --- |
| Photos prévues | 15 |
| Photos exploitables | 13 |
| Non exploitables | 2 : sujet attendu non détecté et aucun visage détecté |
| Bons noms sur les proches | 8/10 cas prévus ; 8/8 visages exploitables |
| Inconnus correctement rejetés | 5/5 photos d'un seul individu inconnu |
| Faux noms / proches rejetés / incertains | 0 / 0 / 0 |

Le seuil candidat 0,50, le rejet 0,30 et la marge 0,05 sont conservés. Deux
personnes connues et un individu inconnu dans ce test ne permettent pas de
conclure à une fiabilité générale. Les photos non exploitables ne sont pas
remplacées ; le test exposé ne sert plus à optimiser ces paramètres. L'export
et l'import préparés ensuite ne constitueront pas une preuve de parité numérique
ni de reconnaissance mobile : ces validations restent aux tickets suivants.
Le rapport détaillé demeure uniquement dans le stockage privé ignoré par Git.
