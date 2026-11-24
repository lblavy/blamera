# Finitions de saisie et diagnostics — validation M2-10-UI-01

Version cible 0.15.1/code29. Baseline
`b45bf335db2bc9b50c8374bceacedbd765f9ab1d`.
Implémentation, revue, contrôles globaux et tests indépendants conformes sur PC.
Version 0.15.1/code29 installée sur Xiaomi ; parcours essentiel accepté par
l'utilisateur et diagnostics du parcours botanique/ChatGPT confirmés.

[Contrat](../specs/plant-key-visibility.md),
[recette](../tests/m2-key-visibility-recette.md).

## Retour et lecture de la 0.15.0

L'utilisateur indique « ça a l'air de fonctionner, même après redémarrage ».
Le parcours essentiel est accepté qualitativement. La précision statistique,
la qualité de chaque espèce et le niveau de protection matérielle ne sont pas mesurés.

Le 1er novembre 2026 vers 10:46, heure de Paris, root lit uniquement le PID
Blamera actif et les tags BlameraChatGpt/BlameraVision. Huit événements de
diagnostic autorisés conservés dans
`build/reports/validation/m2-10-key-visibility/safe-app-events.log` ; logs bruts
non sauvegardés, aucune lecture Chrome/ChatGPT/autre application, aucun contrôle
du téléphone. Reprise de session, catalogue HTTP200 et réponse HTTP200, trois
analyses CPU (total 2560/3697/3635 ms). Aucun événement FAILURE dans cette lecture
bornée du PID courant ; elle ne prouve pas l'absence globale de crash ni l'historique.

La 0.15.0 ne journalise aucun résultat Pl@ntNet ou indicateur d'hypothèse ajoutée
au prompt. Aucune réussite botanique n'est donc reconstruite à partir de ces logs.
La présente mise à jour ajoute seulement des catégories/booleans utiles à une
prochaine lecture, sans données privées et sans requête réseau supplémentaire.

## Implémentation et preuves TDD

Le champ conserve son clavier de mot de passe, sans autocorrection. L'œil change
seulement la transformation visuelle de la saisie locale. Sauvegarder, supprimer,
fermer/rouvrir ou recréer l'Activity laisse le champ vide et masqué ; la clé
stockée n'est pas recopiée. Les trois traitements utilisent le même Button plein,
sans changer leurs règles d'activation. Les diagnostics BlameraPlantNet utilisent
uniquement booléens/catégories fermées, après publication courante ou préparation
effective du document. Aucun nom botanique, score ou texte de consigne n'est logué.

Preuves dans `build/reports/validation/m2-10-key-visibility/coder/` :

- `red-functional.log`, sortie 1 : dix tests, sept échecs fonctionnels attendus,
  deux anciens garde-fous verts et un échec PixelCopy d'infrastructure. Ce dernier
  ne constitue pas une preuve Red de comportement.
- `red-style.log`, sortie 1 : après remplacement de PixelCopy par dessin Canvas
  du vrai View, un test échoue sur le fond d'Analyser différent du primary Material.
  Aucun changement de production n'a précédé cette preuve.
- Lecteur : huit tests, une erreur attendue pour la paire 0.15.1/29 absente ;
  après ajout de cette seule paire, huit tests verts, sortie 0.
- Premier Green : 265 tests, 264 verts ; une assertion de fixture supposait que
  l'indicateur sémantique Password disparaissait en mode visible. Le clavier
  Password le conserve. Le test corrigé observe le texte effectivement rendu,
  les puces, le retour visible et une insertion dans la sélection conservée.
  Aucun correctif de production n'a été nécessaire pour cette assertion.
- Green final : `scripts/gradle.ps1 :app:testDebugUnitTest --tests dev.blamera.ui.*
  --tests dev.blamera.botany.* --tests dev.blamera.chatgpt.ui.*
  --tests dev.blamera.information.* :app:jacocoDebugReport --no-daemon --no-watch-fs` ;
  sortie 0, 265 tests sans échec, erreur ou omission, 1 min 50 s, JaCoCo exécuté.

Format scoped et Detekt finaux : sorties 0. L'extraction du petit composant œil
résout une première complexité cognitive de 23 sans changer les règles 10/15.
Nouveau code du ciblé : UI 31 lignes exécutables, 100 % lignes/branches ;
botanique 15 lignes, 100 %/87,5 %. Duplication nouvelle 0/68 lignes analysables.
Ces mesures ciblées ne remplacent pas la campagne globale.

Revue indépendante : conforme sur les 373 empreintes gelées, aucun blocage.
Le reviewer a examiné code, tests corrigés, preuves et captures PC portrait/paysage,
sans modification, lancement de tests ou accès au téléphone.

## Contrôles globaux gelés

`scripts/quality.ps1 -BaseRef b45bf335db2bc9b50c8374bceacedbd765f9ab1d` :
sortie 0, synthèse `passed`, 519 tests Android sans échec/erreur/omission,
76 tests des scripts et quatre tests PC. Build, types, format, lint, analyse
statique, couvertures, duplication et audits de dépendances conformes.
Les fixtures volontairement invalides sont rejetées par les vrais outils ; leurs
messages d'échec dans le log ne sont pas des échecs de production.

| Périmètre | Lignes globales | Branches globales | Lignes nouvelles | Branches nouvelles |
| --- | --- | --- | --- | --- |
| Android | 97,35 % | 86,63 % | 100 % (46 exécutables) | 97,06 % |
| UI photo, sous-packages inclus | 99,03 % | 83,85 % | 100 % (31) | 100 % |
| Botanique | 99,47 % | 89,76 % | 100 % (15) | 87,5 % |
| Scripts qualité | 94,35 % | 88,52 % | 100 % | non mesurables |
| PC | 100 % | 100 % | non applicable | non applicable |

Duplication Kotlin : 0/68 nouvelles lignes analysables ; globale 33/10925
(0,30 %), 139 fichiers. Complexités 10/15 : aucune violation. Inventaire runtime
Android : 98 artefacts/132 entrées de licences ; PC : 35/35 ; aucun avis actif
retourné dans ces scopes lors du scan. Aucune règle, exclusion ou limite abaissée.

Les 373 empreintes du gel correspondent aux sources utilisées. Archives dans
`build/reports/validation/m2-10-key-visibility/` : `quality-final/`,
`junit-global/`, `jacoco-global.xml`, `component-metrics.json` et
`source-snapshot.json`. Ces preuves sont conservées avant les tests indépendants.

APK principal copié byte pour byte dans `dist/blamera-0.15.1-debug.apk` :
dev.blamera, code29, 458447198 octets, SHA-256
`6238cf48e0f35d60108a5c958bbb67873231e723820088e5a0506627c1dad204`.
À l'archivage de ces preuves, installation et recette réelle restaient à faire.

## Validation indépendante et livraison

Le testeur exécute 20 tests Android ciblés et huit tests du lecteur : sorties 0,
aucun échec, erreur ou omission. Un premier appel Gradle a échoué avant les tests
sur la connexion au daemon local ; cette preuve d'infrastructure est distincte
du passage réussi avec l'accès loopback nécessaire. Aucun fichier suivi modifié.

Audit local AAPT2/apksigner conforme : signature et certificat habituels, trois
permissions inchangées, sauvegarde désactivée et 18 exclusions conservées,
aucun test instrumenté dans l'APK. Les 38 assets/bibliothèques ont exactement
les mêmes noms et empreintes qu'en 0.15.0. Les captures PC sont lisibles en portrait
et paysage. Les 373 empreintes de sources, 519 résultats globaux et métriques sont
recalculés et cohérents. Rapport et preuves : sous-dossier `tester/`.

Précontrôle ADB : aucun téléphone connecté après la validation globale ; demande
de reconnexion envoyée, sans tentative d'installation à ce moment-là.

Après confirmation de reconnexion par l'utilisateur, installation réussie le
1er novembre 2026 à 11:25:32, heure de Paris. `adb -d install -r`, sortie 0,
`Success`, APK principal ci-dessus et 373 empreintes revérifiés. Conservation
des données, aucun lancement par l'agent ni autre modification du téléphone.
Reçu : `build/reports/validation/m2-10-key-visibility/install-result.json`.

## Retour Xiaomi et diagnostics 0.15.1

L'utilisateur a lancé et manipulé Blamera lui-même puis confirmé « ça a l'air ok »
sur la recette proposée. Parcours essentiel accepté qualitativement ; cela ne
déclare pas individuellement exécutés tous les cas optionnels du protocole.

Le 1er novembre 2026 à 11:29:03, heure de Paris, lecture bornée du PID courant
Blamera 18716 et des seuls tags BlameraPlantNet/BlameraChatGpt/BlameraVision.
Quatre événements autorisés conservés dans
`build/reports/validation/m2-10-key-visibility/safe-app-events-0151.log` :

- `IDENTIFICATION_PUBLISHED` : résultat botanique publié pour l'analyse courante.
- Analyse CPU terminée : inférence 2920 ms, total local 3825 ms ; ce total n'est
  pas la latence Pl@ntNet ou ChatGPT ni une mesure statistique de performance.
- `PROMPT_PREPARED_WITH_HINT` : document ChatGPT préparé avec l'hypothèse botanique.
- `HTTP_RESPONSES_READ_STATUS_HTTP_STATUS_200` : lecture de réponse ChatGPT HTTP200.

Reçu technique `log-read-0151.json`, quatre événements, logs bruts non sauvegardés.
Ni clé, photo, taxon, score, consigne, réponse, URL ni donnée d'autre application
collectés. Aucune manipulation ni modification du téléphone par l'agent.
Aucun événement d'échec dans cette lecture ; elle ne démontre pas l'absence
globale de crash ni l'historique complet. Le prompt enrichi et le transport sont
confirmés, sans prétendre vérifier l'espèce ni la qualité du contenu de réponse.
Le suivi de précision botanique M2-10-QUAL-01 reste ouvert.
