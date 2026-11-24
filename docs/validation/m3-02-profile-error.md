# M3-02-A — erreur de profil signalée après sélection des visages

1er novembre 2026, baseline `aea8842`, PC 0.4.0.
Après avoir sélectionné ses visages, l'utilisateur signale une erreur pour
toutes les photos : « profil de visages incompatible ». Une capture fournie
confirme le texte exact. L'utilisateur précise que l'erreur est apparue pendant
le travail sur la dernière photo, après plusieurs photos sans erreur ; il ne
sait plus si c'était à la préparation ou à l'enregistrement. Après redémarrage
et rechargement, il confirme : « Non, le rechargement a supprimé l'erreur ».
Il confirme séparément que ses choix et confirmations de visages sont conservés.
L'incident est clos ; la cause précise reste non établie.

## Diagnostic sans données personnelles

Le message exact `Profil de visages incompatible.` vient du contrôle schema /
dimension du manifeste, avant toute lecture des références. Le manifeste public
actuel a schema 1, dimension 512 et profil
`4a6472a476b37ec36626f41542d7fd41f13da63dfbeb09cd1ffbc480993aded1`.
Un processus Python neuf le charge correctement. Les messages d'incompatibilité
de référence ou de moteur sont distincts. La capture désigne bien le message du
manifeste. Ce contrôle dépend du code et de la configuration chargés, pas des
pixels de la dernière photo ; un rerun peut être le moment où il est réévalué.

Le serveur observé écoutait sur 8501 avec le worker 26724, enfant du lanceur venv
47704, démarré à 16:02:47 UTC. Cela confirme l'identité du dernier serveur lancé,
sans prouver la version des modules Python déjà chargés. L'hypothèse d'un module
périmé reste une hypothèse ; le succès du endpoint de santé ne prouve pas le
fonctionnement de la préparation des visages.

## Action locale

Après vérification PID, parent, heure de démarrage et commande, arrêter le seul
worker Blamera identifié puis son lanceur s'il reste présent. Redémarrer le
serveur avec les mêmes chemins de code et de données ; aucun fichier de la
galerie n'est lu ou modifié par les outils de l'agent.

Nouveau lanceur 52432, worker écoutant 62928, démarrage 16:08:48 UTC,
http://127.0.0.1:8501, santé HTTP200 `ok`. Reçus dans
`build/reports/validation/m3-02-profile-error/`. L'utilisateur recharge sa page
pour vérifier la reprise de ses choix et références ; aucune nouvelle sélection
massive ni suppression n'est demandée.

## Vérifications isolées

Le coder exécute le diagnostic dans un dossier temporaire synthétique : deux
candidats, sélection explicite du second, confirmation d'une référence 512D
normalisée et relecture depuis un second processus Python neuf. Choix, référence,
personne et pixels sont conservés ; une lecture avec l'ancien profil 128D est
ignorée. Le loader 0.3.0 extrait de Git, isolé du loader courant, reproduit le
message exact face au manifeste 512D. Ce scénario démontre une incompatibilité
attendue, pas une régression reproduite dans le code actuel.

Preuves `coder/diagnostic.py`, `coder/diagnostic.json`, `coder/report.md` :
exécutions sortie 0. Revue indépendante conforme, 31 empreintes de sources
inchangées et périmètre des scripts vérifié sans accès à la galerie personnelle.
Les 66 tests et métriques du lot ResNet50 ne sont pas réexécutés ou présentés
comme de nouvelles mesures ; aucun code suivi, manifeste ou dépendance ne change.

Le testeur rejoue indépendamment une copie du script dans `tester/`, sortie 0,
sans écraser les preuves du coder. Même chargement actuel valide, reproduction
historique isolée et conservation du choix / référence 512D dans un second
processus. 31 empreintes inchangées, reçus des deux processus cohérents, même
racine de données et santé HTTP200. Aucun accès à la galerie personnelle.
Verdict du diagnostic : conforme. Le retour utilisateur ci-dessus confirme
ensuite la disparition du symptôme après redémarrage et rechargement.

## État et suite

Aucun défaut du code courant reproduit, aucune validation stricte de profil
assouplie et aucun correctif de production inventé. L'instrumentation de
production envisagée pendant l'investigation devient inutile après ce retour.
La conservation effective des choix et confirmations personnels est confirmée
séparément par l'utilisateur, sans inspection de sa base ni déduction à partir
des seuls tests synthétiques. Le parcours essentiel M3-02-A est accepté.

Risque de déploiement identifié : le serveur 0.3.0 était accessible pendant le
changement vers le manifeste 512D, avant le lancement du serveur 0.4.0. Cela
permet techniquement le scénario reproduit, sans prouver que c'était l'état de
la page au moment de l'erreur. Pour les prochains changements incompatibles de
profil, arrêter le serveur utilisateur avant de remplacer les sources ou le
manifeste de son checkout, puis le relancer sur l'état validé. L'évaluation peut
continuer dans une copie de travail et une galerie QA isolées.
