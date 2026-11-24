# M3-03-A — export PC de la galerie faciale

1er novembre 2026, PC 0.7.0, baseline qualité cumulatif `83d3645`.
[Contrat commun v1](../specs/face-gallery-package.md).
Revue indépendante et validation du testeur conformes. Export personnel produit
et PC 0.7.0 disponible sur http://127.0.0.1:8501/.

## Livraison

Exporter la galerie est un téléchargement JSON explicite, proposé après un rapport
final courant. Le calcul en mémoire ne lance aucune inférence, ne recalcule aucun
résultat statistique et n'écrit pas de fichier personnel automatiquement. Lire
galerie et corpus sous le même instantané cohérent, puis reprendre uniquement
les références retenues, leurs prototypes L2 moyens, les noms, paramètres et
métadonnées de la chaîne approuvée. Refuser gel ou rapport périmé, profil inconnu,
vecteur / nom / effectifs invalides ou sortie dépassant 120 Kio UTF-8.

Le paquet contient la qualification agrégée exploratoire et les empreintes du
contexte figé / rapport personnel, sans photos, crops, vecteurs individuels, cas,
chemins ou séances. Les modèles ne sont pas empaquetés ou téléchargés ; leurs
tailles / SHA et les références publiques des notices sont conservés. L'import
de format ne prouve pas la parité numérique future du moteur Android.

La fixture publique `config/fixtures/face-gallery-v1.json` contient deux identités
et vecteurs synthétiques de 512 dimensions. Son SHA-256 figé est
`a7e6b8bc61fd106a2e2b505df038b7d04c6a0e1e15309c8a6bf4edc7d568bf7d`.
Elle sert au contrat commun PC / Android, sans données personnelles.

## TDD et preuves

Sous `build/reports/validation/m3-03-export/coder/` :

- `red-export.log` : cinq tests, quatre échecs et une erreur sur retour vide ;
  sortie 1. Ils décrivent qualification exigée, export non vide, moyenne renormalisée,
  sélection retenue et données exclues, profil et horloge valides.
- `green-export.log` : cinq tests, sortie 0, avec galerie et corpus binaires
  identiques avant / après, paramètres conservés et aucune action réseau.
- `red-ui.log` : téléchargement absent malgré le rapport courant, sortie 1 ;
  `green-ui.log` : trois AppTest export / test final, sortie 0. Le bouton disparaît
  lorsque la politique ne correspond plus au gel.
- `green-package.log` : dix tests du writer / export, sortie 0 ; compatibilité
  sémantique exacte avec la fixture, types entiers stricts, ID / noms / doublons,
  dimensions / norme / valeurs finies, effectifs cohérents, limites UTF-8 et
  paquet valide de plus de 120 Kio correctement refusé.

Les tests utilisent uniquement des bases synthétiques temporaires. Aucun accès
du coder au corpus personnel ni export réel. L'orchestrateur crée le fichier privé
après qualification logicielle, en conservant le gel et les confirmations. Les
contrôles qualité PC restent inchangés et sont exécutés sur le diff cumulatif.

Qualité finale : `pc/.venv/Scripts/python.exe scripts/quality_pc.py --base-ref 83d3645`,
sortie 0, 139 tests en 38,163 s. Couverture canonique globale 1734/1740 lignes,
99,66 %, et 434/440 branches, 98,64 % ; nouveau code 99,45 % lignes / 100 % branches.
Duplication nouvelle 0/620 lignes, cognitive maximale 13≤15, cyclomatique C901≤10,
format / Ruff / mypy strict conformes. Trente-six paquets et licences, aucun avis
actif renvoyé par le scan dans son périmètre. Aucune règle / exclusion ajoutée ou
assouplie. Un cast redondant refusé par mypy au premier passage est supprimé avant
ce résultat ; ce problème de types ne constitue pas une preuve Red fonctionnelle.
Archives `coder/quality-final/` et `coder/source-snapshot.json` dans le dossier de
validation du lot, distinct des preuves précédentes 0.6.x.

## Validation indépendante et export réel

Revue : aucun blocage, dix tests writer / export réexécutés avec succès et
61 empreintes conformes. Testeur : onze tests service / writer / UI réussis en
5,500 s ; scénario synthétique utilisant cinq références sur sept, prototypes
vérifiés analytiquement, deux bases / gel / rapports identiques. Le lancement
dans un processus neuf avec `PYTHONPATH=pc/src`, sans correction de `sys.path`
dans le test, charge la page et Évaluation sans exception. Rapport local :
`build/reports/validation/m3-03-export/tester/report.md`.

L'orchestrateur a ensuite produit `pc/data/exports/blamera-face-gallery.json`
dans le stockage privé ignoré : 24123 octets, deux identités, cinq références
agrégées et les mêmes paramètres. Qualification conservée : quinze cas, treize
exploitables, deux non exploitables, huit bons noms, cinq inconnus rejetés et
aucun faux nom. Aucun nouvel essai, réglage ou calcul de modèle ; les bases de
galerie et d'évaluation sont binaires identiques avant / après export, gel et
rapport final inchangés. Reçu agrégé sous `application-summary.json` dans le
dossier de preuves locales. L'import Android et sa recette restent distincts.
