# Paquet de galerie faciale — contrat v1 figé

M3-03-A, 1er novembre 2026. Référence publique complète et normative :
[`config/fixtures/face-gallery-v1.json`](../../config/fixtures/face-gallery-v1.json).
Cette fixture contient uniquement deux identités et vecteurs synthétiques ; les
métadonnées de la chaîne reprennent `config/face-models.json`.

## Encodage et bornes

Un document JSON UTF-8 strict, sans BOM, maximum **122880 octets (120 Kio)**,
profondeur maximum **12** (conteneurs racine compris). Clés dupliquées, valeurs
NaN / Infinity, types inattendus et clés non prévues sont refusés. Les entiers
refusent les booléens et les nombres fractionnaires. Les objets ont exactement
les clés décrites ; l'ordre des clés n'a pas de sens. Les listes modèles et notices
suivent l'ordre du manifeste public. Les SHA sont 64 caractères hexadécimaux
minuscules ; ce sont des références de contenu, pas une signature d'authenticité.

## Racine : huit clés exactes

| Clé | Valeur / structure |
| --- | --- |
| `format` | chaîne exacte `blamera-face-gallery` |
| `schema` | entier exact `1` |
| `exported_utc` | date UTC valide `YYYY-MM-DDTHH:MM:SSZ`, sans fraction |
| `profile` | `{key: SHA-256, dimension: 512}` |
| `pipeline` | objet défini ci-dessous, égal à la chaîne approuvée publique |
| `policy` | `{reject: nombre, accept: nombre, margin: nombre}` |
| `identities` | liste de 1 à 10 objets définis ci-dessous |
| `qualification` | objet défini ci-dessous |

`policy` : nombres finis, booléens refusés ; -1 ≤ reject < accept ≤ 1 et
0 ≤ margin ≤ 2. Comparaison cosinus, mêmes règles d'égalité et d'abstention que
`mean-unit-cosine-v1`. L'import ne modifie pas les seuils.

`identities` : chaque objet contient exactement `id`, `name`, `reference_count`,
`prototype`. ID unique, 32 caractères hexadécimaux minuscules. Nom de 1 à 80 points
de code Unicode, sans espaces de bord ni caractères de catégorie Unicode C
(contrôle, format, surrogate, privé, non assigné) ; noms exacts uniques.
`reference_count` entier de 1 à 10000. Prototype de **512 nombres finis**, sans
booléen, de norme L2 unitaire à tolérance absolue **0,001**. Ne pas renormaliser un
vecteur invalide à l'import pour le faire accepter.

## Pipeline : sept clés exactes

`profile_name`, `preprocessing`, `runtime`, `detector`, `aggregation`, `models`,
`notices`. Les valeurs exactes sont dans la fixture publique.

- `profile_name` : `opencv-yunet2023mar-arcface-r50-webface600k-v1`.
- `preprocessing` : `opencv-alignCrop-5points-RGB112-minus127.5-div127.5-L2-v1`.
- `runtime` : `opencv-dnn-4.13.0.92-cpu`, référence PC de production des prototypes ;
  sa présence ne déclare pas une parité numérique Android déjà mesurée.
- `detector` : `{size: 640, score: 0.8, nms: 0.3}`.
- `aggregation` : `mean-unit-cosine-v1`.
- `models` : trois objets `{role, name, size, sha256}`, dans l'ordre detector,
  aligner, encoder, avec noms, tailles et empreintes exacts du manifeste public.
- `notices` : quatre objets `{role, name, size, sha256, url}`, dans l'ordre du
  manifeste, références publiques figées des licences / conditions des modèles.

Le `profile.key` est le SHA-256 du JSON canonique du manifeste public complet
(`sort_keys=True`, séparateurs `,` et `:`), dimension 512. Un profil ou pipeline
différent est incompatible. Le paquet ne contient aucun poids ni notice embarquée,
et son import ne déclenche aucun téléchargement ou accès réseau.

## Qualification : cinq clés exactes

`status` vaut exactement `exploratory`. `frozen_context` est l'empreinte SHA-256
du contexte de gel, `test_report` celle du rapport final privé canonique, sans
exporter ce rapport. `counts` contient les dix entiers non négatifs ci-dessous,
chacun ≤100000. `scope` vaut exactement `held-out-local-corpus`.

Les clés de `counts` sont : `total`, `usable`, `unusable`, `correct_names`,
`false_names`, `false_names_known`, `false_names_unknown`, `correct_unknown`,
`false_rejections`, `uncertain`. La liste est normative, sans clé
supplémentaire. `total` est ≥1 ; toutes les autres valeurs sont ≤total.
`usable + unusable = total`, `false_names_known + false_names_unknown = false_names`
et `correct_names + false_names + correct_unknown + false_rejections + uncertain
+ unusable = total`. Ces effectifs décrivent ce corpus, sans prétention de
fiabilité générale ou d'effectif d'identités inconnues.

## Production et import

L'export PC exige le rapport Test final **courant**, son gel courant, la même
sélection de références, le même profil, prétraitement, paramètres et agrégation.
Les prototypes sont les moyennes des références unitaires retenues, puis L2.
Un export en mémoire à partir d'un état incohérent est refusé. Aucun fichier
personnel n'est écrit automatiquement : téléchargement sur action explicite.

Le paquet contient noms, IDs et prototypes agrégés, donc demeure une donnée
personnelle hors Git. Il exclut photos, crops, vecteurs individuels, chemins,
séances, cas et lignes du rapport personnel. L'import Android valide format et
profil avant remplacement atomique de la galerie conservée. Il ne qualifie pas
encore l'exécution faciale Android, qui relève du ticket suivant.
