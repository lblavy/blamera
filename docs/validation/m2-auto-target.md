# Cible automatique — validation M2-09-UI-01

Version 0.14.1/code27 ; baseline
`75466b8448a7349623de4273a34797e627908919`.
Contrôles globaux et testeur conformes sur PC ; version installée. Parcours
essentiel accepté par l'utilisateur sur Xiaomi.

[Contrat](../specs/automatic-photo-target.md),
[recette](../tests/m2-auto-target-recette.md).

Retour humain sur 0.14.0 : « c'est mieux », avec demande de simplification du
choix Cadrage/Objet. La sélection dans l'image devient l'unique source de vérité.
Aucun appel authentifié, accès privé ou pilotage du téléphone pour ces tests.

## TDD et revue

Preuves locales dans `build/reports/validation/m2-09-auto-target/coder/`.
Red réel : six tests, cinq échecs fonctionnels, sortie 1 en 45 s ; le garde-fou
appui vide sans sélection était déjà satisfait. Red du lecteur : paire de version
refusée, puis Green huit tests après ajout 0.14.1/code27.

Green ciblé : 210 tests Android, zéro échec, erreur ou omission, sortie 0 en
2 min 11 s. Vraie tâche JaCoCo exécutée : nouveau Photo/UI 28 lignes exécutables,
100 % lignes/85,71 % branches ; composant complet 98,90 %/81,58 % sur cette
campagne ciblée. Les autres composants de production ne changent pas. Formatage
et Detekt sortent à zéro ; duplication nouvelle 0/30 lignes. Les chiffres globaux
de la campagne complète seront distingués de ces résultats ciblés.

Revue indépendante conforme sur le gel de 337 fichiers : cible et consigne,
pixels transmis, invalidation/annulation, boîte invalide et appui vide vérifiés.
Captures PC portrait, paysage et police agrandie relues. Aucun blocage ; aucune
modification de source depuis le gel.

## Campagne globale locale

`scripts/quality.ps1 -BaseRef 75466b8448a7349623de4273a34797e627908919`
sortie 0, synthèse `passed`, aucune étape en échec. 456 tests Android, 76 scripts
et quatre PC réussis. Les rejets de fixtures intentionnellement mauvaises
vérifient les outils ; ils ne sont pas des échecs de cette campagne.

| Périmètre | Lignes | Branches | Nouveau code |
| --- | --- | --- | --- |
| Android global | 97,01 % | 86,18 % | 100 % / 85,71 %, 28 lignes exécutables |
| Photo/UI, sous-paquets inclus | 98,86 % | 83,44 % | 100 % / 85,71 % |
| Partage image | 98,14 % | 83,75 % | aucun code de production nouveau |
| UI ChatGPT | 97,52 % | 82,42 % | aucun code de production nouveau |
| Scripts Python | 94,35 % | 88,52 % | 100 % lignes ; aucune branche nouvelle |
| Socle PC | 100 % | 100 % | aucun code nouveau |

Duplication Android nouvelle 0/30 lignes ; globale 29/10049 (0,289 %).
Aucune violation de complexité 10/15, exclusion ou baisse de seuil. Audits :
98 paquets Maven/132 entrées de licences et 35 paquets PC/35 licences ; aucun
avis actif retourné. Les suivis individuels M2-08-COV-01 et M2-09-COV-01 ne sont
pas clos par ce changement.

Les rapports complets sont préservés dans `quality-final/`, les 456 résultats
dans `junit-global/` et JaCoCo dans `jacoco-global.xml`, sous le même répertoire
local de validation. Les 337 empreintes correspondent toujours au gel examiné.
APK préparé : `dist/blamera-0.14.1-debug.apk`, 458324887 octets ; SHA-256
`5ac6721f0031529d0e6a2f4e5502f6162fd829898f6599ea895713a0a5658e70`.

## Testeur et installation

Testeur indépendant : 210 tests Android ciblés, sortie 0 en 1 min 20 s,
aucun échec, erreur ou omission ; huit tests du lecteur réussis. Les 337 sources
sont inchangées. Signature v2 habituelle, trois permissions, sauvegarde désactivée
et 18 exclusions, activité de diagnostic privée/fullUser : vérifiés dans l'APK.
Ses 38 assets/bibliothèques sont identiques à 0.14.0. Aucun défaut bloquant reproduit.
Rapport local `tester/report.md` ; ces tests PC ne valent pas recette réelle.

Installation du seul APK principal par `adb -d install -r`, sortie 0 « Success »,
du 31 octobre 2026 à 18:41:22,707 au 18:41:42,633 UTC, après vérification des
empreintes des sources et de l'APK. Conservation des données ; aucun lancement
automatique ni autre modification du téléphone. Reçu local `install-result.json`.
Retour humain après cette installation : « oui ça marche ». Le parcours essentiel
de sélection/désélection et de cadrage est accepté sur Xiaomi. Aucun nouveau
chiffre, test PC ou résultat individuel des autres cas n'est déduit de ce retour.

Amélioration secondaire M2-09-UI-02 retenue en revue : pendant le chargement d'une
nouvelle image depuis une ancienne sélection, afficher le statut transitoire même
si le nom de l'ancien objet reste visible. Les actions sont désactivées durant ce
chargement ; aucun envoi incorrect n'est établi. Ce retour visuel peut être amélioré
dans un prochain ajustement de la fiche.
