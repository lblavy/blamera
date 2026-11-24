# Comparaison locale des modèles — 0.5.1 / code 9

État : APK 0.5.1 validé par revue/tester PC et installé le 27 octobre 2026 par mise à
jour principale depuis 0.5.0. L’utilisateur a lancé manuellement deux comparaisons et
confirmé leur fin ; une capture complète filtrée aux logs Blamera permet les mesures
CPU/PSS. Aucun lancement automatique. Le gabarit ci-dessous est conservé ; seuls les
résultats explicitement confirmés sont consignés séparément. Le modèle
habituel n’est pas remplacé. Aucune photo personnelle utilisée, aucun accès réseau,
aucun fichier de résultat enregistré sur le téléphone. Les deux fixtures embarquées
sont NASA publique et un motif synthétique ; ce test mesure le moteur, pas la qualité.

Après la mise à jour du seul APK principal par l’orchestrateur, l’utilisateur réalise :

| ID | Action manuelle | Attendu | Résultat |
| --- | --- | --- | --- |
| B01 | Ouvrir Blamera, toucher « Comparer les modèles » (faire défiler si besoin) | Écran diagnostic au repos ; aucun calcul automatique | |
| B02 | Toucher « Démarrer la comparaison », garder l’écran ouvert | Progression baseline puis YOLOE, deux fixtures par modèle ; retour toujours disponible | |
| B03 | Laisser terminer ; toucher une zone vide si nécessaire pour éviter la veille, sans quitter ni tourner l’appareil | Résultat complet avec lecture/SHA, création session, premier appel par fixture, N=10 et médiane/min/max, copie Java et PSS échantillonné | |
| B04 | Relancer puis Annuler pendant la progression | Annulation après l’appel natif en vol, aucun résultat partiel présenté complet ; un nouveau clic est nécessaire | |
| B05 | Relancer puis passer en arrière-plan ou tourner l’appareil | Calcul annulé ; aucun redémarrage automatique, écran recréé au repos | |
| B06 | Retour à Blamera ; vérifier Caméra/Image avec actions habituelles | Parcours et modèle YOLO11n habituels conservés, aucun résultat diagnostic superposé | |

## Résultats confirmés le 27 octobre 2026

Retours exacts : « Comparaison terminée », puis « Nouvelle comparaison terminée ».
Un premier journal partiel est exclu des comparaisons ; le second run complet compte
50 lignes et `end status=complete`. [Mesures et portée](../validation/m1-yoloe-android.md#mesure-xiaomi-contrôlée-dans-les-logs--27-octobre-2026).

| Cas | Ce qui est confirmé | Sous-critères non vérifiés séparément |
| --- | --- | --- |
| B01 | Lancement manuel du diagnostic | État idle observé et détail de navigation non rapportés |
| B02 | Démarrage manuel ; start et ordre des modèles/fixtures conformes dans les logs | Progression visuelle et disponibilité du Retour non détaillées |
| B03 | Fin confirmée par l’utilisateur ; statistiques complètes recalculées, N=10 par bloc | Détail de l’affichage et interactions pour éviter la veille non rapportés |
| B04 | Aucune exécution confirmée | Annulation/réessai sur téléphone |
| B05 | Aucune exécution confirmée | Arrière-plan/rotation du diagnostic sur téléphone |
| B06 | Aucune exécution confirmée | Retour aux parcours Caméra/Image après diagnostic |

Aucune validation de chauffe, stabilité prolongée, hors ligne ou qualité de détection
générale déduite de ces retours. Aucune autre manipulation demandée pour cette évaluation.

Les 56 appels = 2 modèles × 2 fixtures × (1 premier appel + 3 échauffements + 10 mesures).
La deuxième fixture utilise une session déjà chaude ; seul le tout premier appel du
modèle suit sa création de session. Ordre fixe baseline → YOLOE, sans contrôle du cache
ni de la température : ne pas déduire un gain caméra/FPS ou une mesure de qualité.
`session.run` inclut inférence et NMS, exclut copie Java, préparation, masques/contours et
publication UI. PSS : échantillon du processus entier, session ouverte après chaque
bloc ; ce n’est ni un pic ni la mémoire attribuée au seul modèle.

Retour utile : terminé/annulé/erreur, valeurs affichées par modèle/fixture, chauffe
ressentie et éventuelle gêne. Aucune photo ni capture demandée. Ne changer aucun réglage
OS pour cette recette. L’orchestrateur peut lire ensuite **uniquement** les logs Blamera
filtrés au PID de l’app et au tag `BlameraBenchmark` ; aucun log global ni autre donnée.

[Rapport et limites](../validation/m1-yoloe-android.md), [installation protégée](../install-android.md).
