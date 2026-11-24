# M3-06 — validation sur le Xiaomi

2 novembre 2026. Version installée 0.18.0/code32. Le parcours connu/inconnu et les
labels de groupe ont été acceptés ; ce lot documente ce que l'appareil permet
réellement de mesurer, sans nouvelle fonctionnalité ni modification de modèle.

## Protocole réalisé

L'utilisateur ouvre et manipule Blamera. L'agent lit seulement le PID du package,
puis ses événements `BlameraFaces` via `logcat --pid=… -v raw -T 1` avec les autres
tags désactivés. Aucune lecture des photos, du stockage privé ou d'autres apps,
aucune modification des réglages, aucun lancement automatique ou test APK.

1. Préparer la lecture avant les analyses. Sur une photo à un visage puis une
   photo à trois visages : import automatique puis trois appuis Analyser, en
   attendant chaque fin. Conserver toutes les observations de la série, sans
   les présenter comme autant de photos indépendantes.
2. Exclure explicitement le seul événement déjà reçu avant la demande de série
   (`-T 1`), en conservant la capture brute. Rapporter n, médiane, minimum et maximum.
3. Demander une fermeture/réouverture manuelle par Retour, puis préparer le lecteur
   avant l'import de groupe et un appui Analyser. Qualifier une nouvelle instance
   seulement si un nouveau `FACE_PARITY_OK` est observé. Ne pas prétendre démontrer
   un nouveau processus ou un cache vide.
4. Conserver résultats et SHA des captures sous `build/reports/validation/m3-06/`,
   ignoré par Git ; publier uniquement agrégats et limites dans `docs/`.

## Portée des métriques

`elapsed_ms` mesure le moteur après dispatch et avant attente du mutex : ouverture
éventuelle, autocontrôle, détection, alignement, encodages et normalisations. Il
exclut conversion Bitmap→RGB, comparaison galerie, publication et affichage.
Le premier temps d'analyse contient le temps `cold_ms` ; ne pas les additionner.

`count` compte les observations détectées, y compris un éventuel visage non
encodé. Les logs de succès ne comptent pas tous les échecs ou annulations, et
ne prouvent pas à eux seuls une publication à l'écran. Les retours humains sont
qualitatifs, pas une précision statistique ou un taux de faux noms mesuré.

PSS et delta PSS sont des instantanés du processus entier, qui comprend aussi
YOLOE et l'interface. Ils ne mesurent ni pic, ni mémoire imputable au seul visage.
La cible de pic supplémentaire ≤700 Mio reste non vérifiée ; un instantané total
supérieur à 700 Mio n'est pas comparable à cette cible supplémentaire.

## Clôture dans le périmètre du POC

Conserver les cibles du moteur chaud à trois visages ≤15 s et du premier lot ≤30 s,
avec effectifs et conditions. Les observations satisfont ces cibles pour la
portion moteur et ce petit essai, sans garantir d'autres images ou conditions.

Après le retour utilisateur ne signalant aucun problème à l'usage, celui-ci précise
que le projet est un POC personnel et demande de clôturer ce type de chantier une
fois le parcours accepté. Pic mémoire et délai complet sont donc des limites
acceptées, sans instrumentation supplémentaire ni tâche de mesure en suspens.
Ne pas marquer ces deux mesures conformes. Rouvrir seulement en cas de demande
ou de besoin concret, en conservant les limites et résultats historiques.

Ce lot est un bilan de validation et de mesures, pas un incrément logiciel :
pas de cycle TDD artificiel ni de nouvelle globale sur les 393 sources inchangées.
Faire vérifier indépendamment les captures, calculs, provenance et conclusions.
