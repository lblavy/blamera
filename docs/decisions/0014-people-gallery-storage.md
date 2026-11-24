# ADR 0014 — galerie locale PC et images transactionnelles

1er novembre 2026, M3-01. Décision de conception du premier lot fonctionnel PC.

## Décision

Conserver le socle Python/Streamlit local. Utiliser SQLite de la bibliothèque
standard pour les personnes, leurs associations et les octets d'images normalisées.
La base versionnée réside par défaut dans `pc/data/gallery.sqlite3`, ignorée par
Git ; `BLAMERA_DATA_DIR` permet une racine dédiée, notamment aux tests temporaires.
Les identifiants internes sont indépendants des noms et des chemins choisis.

Pour cette petite galerie personnelle, stocker les images en BLOB simplifie les
mutations : import, réaffectation et suppression sont transactionnels, sans
coordination fragile entre une base et des fichiers distincts. Une base inconnue
ou corrompue est signalée sans réinitialisation automatique. Aucun service distant,
ORM, synchronisation ou framework d'entraînement n'est nécessaire à ce lot.

Pillow, déjà présent dans le lock du socle, prépare JPEG/PNG/WebP : orientation
EXIF appliquée, RGB, limites de volume/dimensions, rejet d'animations et suppression
des métadonnées dans la copie conservée. Le fichier source n'est pas modifié.
Le contenu normalisé permet le dédoublonnage au sein d'une personne ; les mêmes
pixels peuvent être associés à des personnes distinctes avant sélection des
visages en M3-02. Aucune image ni identité personnelle n'est versionnée ou loguée.

## Alternatives et conséquences

JSON et fichiers auraient suffi au premier écran, mais leurs mises à jour séparées
compliquent les erreurs partielles et les corrections d'affectation. SQLite apporte
ces transactions sans ajouter de dépendance. Une séparation fichiers/base pourra
être réévaluée sur un besoin de volume mesuré ; elle n'est pas anticipée ici.

L'import local n'est pas une reconnaissance de personne : une photo de groupe
reste une image affectée manuellement, sans annotation de visage encore livrée.
La galerie de références, les embeddings et le paquet mobile restent M3-02/M3-03.
Le stockage local n'est pas déclaré chiffré ni doté d'un effacement sécurisé.
Les contrôles du socle et le flux coder/reviewer/tester restent applicables.

[Contrat](../specs/people-gallery.md), [guide PC](../pc-development.md).
