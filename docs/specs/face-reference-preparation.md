# M3-02-A — préparer les visages de référence

1er novembre 2026. Baseline `5e9d5eae053d622b845bb4e5ec079169ba681188`.
L'utilisateur demande l'évaluation des modèles PC/téléphone, puis l'implémentation
jusqu'à la prochaine validation. M3-01 et ses données existantes sont conservés.

## Incrément et décision provisoire

Comparer SFace, ArcFace ResNet50 et ArcFace MobileFaceNet sur CPU avec des
fixtures publiques autorisées, dans un spike isolé. Documenter qualité publiée,
licences des poids, tailles, temps réellement mesurés et limites de comparaison.
La qualité sur les proches et les temps Android ne se déduisent pas de ce test.

Le premier lot PC 0.3.0 intègre provisoirement YuNet 2023mar + SFace 2021dec
OpenCV Zoo. Après la préférence explicite de l'utilisateur pour la qualité,
le profil actif passe à **YuNet + ArcFace ResNet50**, toujours sur OpenCV
4.13.0.92 CPU. Un adaptateur injectable permet de tester le comportement sans
poids ; les profils ne mélangent jamais des empreintes incompatibles.
Les anciennes 128 valeurs SFace ne sont pas réutilisées avec ArcFace, qui
produit 512 valeurs. Les photos et les personnes restent conservées ; préparer
et confirmer à nouveau les visages est une action explicite.
La préparation vérifiable des références précède la calibration sur les proches.

## Parcours PC

- Dans les photos d'une personne, une action explicite prépare/détecte les visages.
  Aucun calcul au simple démarrage, rerun, import, changement de personne ou réseau.
  La galerie CRUD reste utilisable sans modèles ou en cas d'erreur de préparation.
- Afficher absence de visage, un visage ou plusieurs candidats, avec des aperçus
  numérotés permettant de choisir la bonne personne. Ne jamais choisir silencieusement
  le plus grand visage d'un groupe. Même avec un seul candidat, l'utilisateur
  confirme le visage avant de l'associer comme référence.
- « Enregistrer ce visage » calcule localement le crop aligné et son embedding,
  normalisé L2 et validé (dimension, valeurs finies, norme non nulle). Afficher un
  état « Visage confirmé » et un aperçu, puis permettre de refaire le choix.
- Conserver détections/sélection/référence à la réexécution et au redémarrage.
  Aucun nom reconnu automatiquement, taux de certitude ou seuil d'identité ici.
- Réaffecter une photo à une autre personne invalide sa confirmation/référence.
  Supprimer photo/personne retire ses références associées dans la transaction.
  Échec de déplacement ou de calcul : aucune ancienne référence valide perdue
  au profit d'un succès trompeur. Une préparation tardive d'une photo supprimée,
  déplacée ou modifiée n'est pas enregistrée sous une mauvaise identité.
- Invalider l'usage d'une référence d'un autre profil, poids ou source d'image.
  Renommer une personne conserve les références de cette même identité stable.
  Messages courts et utiles ; pas de détails techniques répétés dans les cartes.

## Pipeline et stockage

Détecter à résolution bornée avec conservation des proportions, puis rapporter
rectangles et cinq repères dans les coordonnées de l'image source normalisée.
Limiter à 20 candidats par photo. Un score de détection n'est pas un score
d'identité. Alignement cinq points 112×112 via l'API OpenCV de référence.
ArcFace ResNet50 reçoit RGB/NCHW float32, `(pixels−127,5)/127,5`, puis une
normalisation L2 en sortie. Ce prétraitement diffère de celui de SFace.
Conserver le contrat exact poids/prétraitement/dimension dans un identifiant
versionné. CPU explicite, appels natifs sérialisés si ressource mise en cache.

Étendre SQLite par migration transactionnelle v1→v2, sans réinitialiser ni lire
les données personnelles via les outils de l'agent. Tester les galeries v1
synthétiques avec photos ; panne DDL = rollback et données anciennes intactes.
Métadonnées de profil/source et références liées aux IDs des photos, foreign keys
et cascades ; pas de fichiers biométriques hors de la racine privée ignorée.
Ne calculer/stocker l'empreinte que pour le visage confirmé, pas pour tous les
passants d'une photo de groupe. Valider aussi les données relues, sans faire
confiance à une forme ou un ancien profil incompatible.

Poids ignorés dans `models/m3-02/`, jamais en Git. Provisionnement reproductible
explicite par commande locale, URL officielle figée au commit
`48b10fa505aeb51fbd127fc60a5c660040e823b7` pour YuNet, release `v0.7` de
InsightFace pour le pack `buffalo_l`, taille et SHA-256 vérifiés avant activation.
Le membre `w600k_r50.onnx` est vérifié après lecture de l'archive ; ne pas
extraire les autres fichiers du pack. Aucun téléchargement réseau automatique
de l'application. Le manifeste actif est `config/face-models.json`.

| Poids | Taille | SHA-256 |
| --- | --- | --- |
| YuNet 2023mar | 232589 | 8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4 |
| SFace 2021dec, alignement natif uniquement | 38696353 | 0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79 |
| ArcFace ResNet50 `w600k_r50.onnx` | 174383860 | 4c06341c33c2ca1f86781dab0e829f88ad5b64be9fba56e56bc9ebdefc619e43 |

YuNet MIT, poids ArcFace réservés à la recherche non commerciale : conserver
provenance, restrictions des poids et notices distinctes du code InsightFace MIT.
SFace reste chargé uniquement pour `FaceRecognizerSF.alignCrop`, sans appeler
sa fonction `feature`. Les 39 Mo supplémentaires conservent l'alignement natif
déjà vérifié et évitent une réimplémentation mathématique. La reconnaissance
utilise exclusivement les 512 valeurs ArcFace. SFace reste Apache-2.0. Ajouter seulement
opencv-python-headless 4.13.0.92 et ses contraintes nécessaires au lock PC ; audit
des nouvelles métadonnées/licences et compatibilité NumPy/Pillow/Streamlit.
Ne pas installer InsightFace/PyTorch/ORT dans le runtime PC pour cette première
intégration : le spike reste isolé. La voie mobile ONNX/OpenCV ou ORT sera testée
en M3-03/M3-04 ; aucune compatibilité exacte ni latence Xiaomi revendiquée ici.

## Preuves, limites et arrêt de l'incrément

Coder → reviewer → corrections → tester, TDD réel avant chaque comportement :
migration/rollback, zéro/un/plusieurs visages, choix explicite, profils/empreintes
invalides, erreurs de modèle, périmètre privé, persistance, déplacement/cascade,
réponse obsolète, reruns sans calcul et intégration UI. Doubles aux frontières
ML pour tests déterministes ; smoke réel CPU sur une fixture publique autorisée.

Qualité PC : Ruff/mypy strict, couverture lignes/branches ≥80 % par composant et
nouveau code, duplication ≤3 %, complexités 10/15 et audit runtime inchangés.
Commandes dans `docs/quality.md` et `scripts/quality_pc.py --base-ref <baseline>`.
Pas de téléphone, cloud, dépense ou inspection de la galerie personnelle par l'agent.

Prochaine validation : l'utilisateur vérifie sur ses images locales les visages
détectés, choisit les bonnes références et confirme les reprises. Ensuite cadrer
les données séparées référence/validation/test, groupées par séance, inconnus et
conditions difficiles pour calibrer les décisions connu/inconnu/incertain.
M3-02 reste en cours : aucun seuil documentaire tel que 0,363 n'est déclaré
calibré sur les proches. Aucun entraînement, export ou reconnaissance Android
n'est livré dans ce premier incrément.
