# Diagnostic personnel baseline / Small / Large / Xlarge

Version installée le 28 octobre 2026 : Blamera **0.6.0, code 10**.
Qualité, revue et tester final PC conformes. Comparaison manuelle et réémission
confirmées le 28 octobre ; capture et résultats du diagnostic validés indépendamment.
Ce diagnostic conserve le modèle habituel de l’import et de la caméra.
Il utilise uniquement quatorze images publiques embarquées ; aucune photo personnelle.

1. Ouvrir Blamera manuellement puis **Comparer les modèles**.
2. Toucher **Démarrer la comparaison** et laisser le traitement finir, sans quitter
   ni tourner l’application. L’écran reste actif uniquement pendant ce travail.
   Les quatre modèles passent dans l’ordre baseline → Small → Large → Xlarge.
3. Attendre **Comparaison terminée**. Si une erreur ou une annulation apparaît,
   la signaler : aucun résultat partiel ne constitue une comparaison complète.
4. L’orchestrateur prépare ensuite le lecteur PC, limité au PID de Blamera et au
   tag `BlameraBenchmark`. Quand il annonce `READY`, toucher
   **Réémettre le dernier résultat** et attendre la fin de réémission.
   Cette action relit le rapport propre à l’application, sans refaire d’inférence.
5. Si la capture est incomplète, réémettre le même résultat avec le lecteur prêt.
   Aucun changement de réglage Android, suppression de données ou relancement
   automatique n’est nécessaire.

Compléments facultatifs : annuler un calcul puis réessayer ; revenir à Blamera et
vérifier que l’import et la caméra restent accessibles. Quitter ou tourner le
diagnostic annule le travail ; un nouveau calcul exige un nouveau clic.
Ne pas modifier les réglages OS pour cette recette.

| Cas | Résultat humain / preuve |
| --- | --- |
| D01 — démarrage explicite, quatre modèles, fin complète | Confirmé le 28 octobre : lancement manuel, utilisateur « Comparaison terminée », rapport complete pour quatre modèles. |
| D02 — réémission et capture PC complète | Confirmé le 28 octobre : utilisateur réémission finie, collecteur/analyse sortis à 0, 261 fragments/SHA et résultats validés par tester. |
| D03 — annulation / erreur lisible puis retour normal | Non exécuté dans cette recette. |
| D04 — import et caméra habituels conservés | Non exécuté dans cette recette. |

[Résultats et preuves](../research/m1-yoloe-capacity-phone-results.md). L’utilisateur
rapporte « Pas de chauffe gênante » : constat subjectif sur un run d’environ sept
minutes, sans mesure thermique ou de batterie ni validation d’une cadence durable.

## Lecture sur PC, après lancement manuel

Depuis la racine du dépôt, terminal PowerShell ; renseigner seulement la cible locale :

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$target = '<cible locale autorisée>'
.\pc\.venv\Scripts\python.exe scripts/benchmark_collect.py --adb $adb --serial $target --output build/reports/validation/m1-yoloe-capacity-phone/device/capture.log
```

Le collecteur ne lance pas l’application, n’efface aucun log et ne lit aucun fichier
privé du téléphone. Il arrête uniquement son propre lecteur PC après le transfert.
Choisir un nouveau nom de capture à chaque essai ; conserver les captures incomplètes.

```powershell
.\pc\.venv\Scripts\python.exe scripts/benchmark_analyze.py build/reports/validation/m1-yoloe-capacity-phone/device/capture.log --references models/m1-yoloe-phone-assets/kotlin-references --output build/reports/validation/m1-yoloe-capacity-phone/device/analyzed
```

Le rapport JSON et les tableaux Markdown sont locaux et ignorés par Git. Le parseur
rejette toute capture incomplète/corrompue et recalcule les statistiques depuis les
échantillons associés. L’index des références et son catalogue sont épinglés dans
`config/benchmark-references.json` : conserver ces artefacts locaux, tout comme les
modèles. Les paires avec le PC décrivent des écarts de sorties,
pas une nouvelle vérité terrain ni un classement général sur quatorze images.
