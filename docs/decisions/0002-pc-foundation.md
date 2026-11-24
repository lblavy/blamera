# ADR 0002 — socle PC local

26 octobre 2026, M0-05. Décision retenue, implémentée et vérifiée localement ;
revue et validation indépendantes effectuées. CI distante volontairement reportée.

## Décision et alternatives

Retenir **Python 3.12.14, uv 0.13.5 et Streamlit 1.66.1**, sur cet ordinateur Windows CPU.
L'interface navigateur écoute uniquement `127.0.0.1`, sans ouverture automatique du navigateur,
télémétrie, authentification ou service distant. Le socle permet seulement de choisir
Personnes/Chats et de lire les prochaines étapes M3/M4. Aucun import, stockage personnel,
embedding, modèle, entraînement ou export n'est livré dans M0-05.

| Option | Intérêt pour Blamera | Compromis et choix |
| --- | --- | --- |
| Streamlit | Widgets Python, affichage d'images et outils de données ; interactions vérifiables par AppTest | Serveur local et navigateur, réexécution du script à chaque interaction ; retenu pour le socle simple |
| PySide6 | Fenêtres bureau, contrôle fin des interactions et des traitements longs | Cycle de vie Qt et distribution bureau supplémentaires ; pas nécessaires à ce premier socle |

Cette comparaison est un choix de maintenance, sans benchmark comparatif des frameworks.
[AppTest](https://docs.streamlit.io/develop/api-reference/app-testing/st.testing.v1.apptest)
simule l'application ; il ne prouve pas son rendu dans un vrai navigateur. Un smoke-test
HTTP local complète cette preuve sans ouvrir de fenêtre. [Configuration Streamlit](https://docs.streamlit.io/develop/api-reference/configuration/config.toml) :
le répertoire courant détermine le fichier projet `.streamlit/config.toml`.
Le lanceur et les tests démarrent donc depuis `pc/`, avec CORS/XSRF conservés.

[Streamlit](https://github.com/streamlit/streamlit/blob/1.66.1/LICENSE) est sous Apache-2.0.
[Qt for Python](https://doc.qt.io/qtforpython-6/licenses.html) offre des licences libres
LGPL/GPL et une licence commerciale selon les composants ; aucun achat n'est nécessaire
pour évaluer PySide6, mais ses obligations de redistribution exigeraient leur propre cadrage.
Le choix Streamlit évite ici cet outillage supplémentaire ; il pourra être réévalué si
les futures corrections graphiques ou traitements en arrière-plan le justifient.

## Environnement autonome et reproductibilité

`scripts/pc.ps1 Bootstrap` vérifie le SHA-256 de l'archive officielle uv Windows et
installe Python dans `.tools/python`, les caches dans `.tools/uv-cache` et l'environnement
dans `pc/.venv`. Aucun runtime Codex, PATH permanent, enregistrement registre ou lien Python
dans le profil utilisateur n'est requis. Les options `--no-registry` / `--no-bin` sont
supportées par le binaire vérifié. [Gestion Python](https://docs.astral.sh/uv/guides/install-python/),
[stockage et variables](https://docs.astral.sh/uv/reference/environment/).

Les versions directes résident dans `pc/pyproject.toml` ; `pc/uv.lock` verrouille les
transitifs et empreintes d'archives. `uv sync --locked` refuse un lock devenu incohérent.
[Synchronisation](https://docs.astral.sh/uv/concepts/projects/sync/).
Le téléchargement Python géré provient de python-build-standalone sélectionné par uv
épinglé ; CPython conserve ses [licences et notices](https://docs.python.org/3/license.html).
uv est utilisable sous [MIT](https://github.com/astral-sh/uv/blob/0.13.5/LICENSE-MIT).
Réseau public nécessaire à la préparation et aux scans ; fonctionnement de l'écran local sans service externe.

## Qualité et frontières

| Contrôle | Outil retenu et périmètre |
| --- | --- |
| Format/lint | Ruff 0.17.2, sources/tests PC et nouvel orchestrateur PC |
| Types | mypy 2.4.0 strict, `pc/src` |
| Couverture | coverage.py 7.10.7, PC et scripts séparés ; instructions canoniques `analysis2` |
| Cyclomatique / cognitive | Ruff C901 ≤ 10 / complexipy 8.2.0 ≤ 15 ; PC et cinq scripts de qualité |
| Duplication | jscpd 4.0.5 Python, PC et scripts séparés ; seuil du nouveau code ≤ 3 % |
| Dépendances | Export runtime installé depuis le lock, OSV PyPI réel, politique licences PC explicite |

[Ruff C901](https://docs.astral.sh/ruff/rules/complex-structure/),
[mypy](https://mypy.readthedocs.io/en/stable/getting_started.html) et
[complexipy](https://complexipy.com/usage-guide/) mesurent des propriétés distinctes.
Ruff/mypy/complexipy sont MIT ; leurs versions ont été vérifiées sur PyPI avant installation.
La commande globale conserve les seuils d'AGENTS.md et ne mélange pas les couvertures.
La CI Ubuntu est préparée mais reste volontairement non publiée/non exécutée.

La politique runtime accepte les expressions permissives recensées, PSF-2.0 et MPL-2.0
de certifi non modifié. Les notices des roues installées restent dans leurs `dist-info`.
Six anciennes métadonnées nécessitent une normalisation explicite après lecture de leur
licence installée : nom/version et empreinte de métadonnées sont vérifiés, sans accepter
une licence inconnue. Les bibliothèques natives embarquées peuvent avoir des notices
supplémentaires ; aucune distribution de l'environnement à des tiers n'est effectuée.
Dev/test, uv, CPython et leurs dépendances natives ne sont pas couverts par le scan OSV runtime.

PyTorch CPU, scikit-learn et les exports ONNX/LiteRT restent des orientations M3/M4,
à choisir selon les modèles et benchmarks. Aucun de ces frameworks n'est installé ici.
Les futures opérations longues devront être séparées de la présentation et testées aux
frontières ; aucun gestionnaire d'expériences fictif ou abstraction de données anticipée.
