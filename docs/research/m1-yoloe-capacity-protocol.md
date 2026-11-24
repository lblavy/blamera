# YOLOE26 PF X, M et L — protocole de capacité

28 octobre 2026. **Préparation, en attente de revue du runner avant les passes qualité.**
Ordre convenu X → M → L, CPU local gratuit. Aucun modèle/application/APK/téléphone modifié.
[Code et commandes](../../experiments/m1-yoloe-capacity/README.md).

[Protocole qualité](m1-yoloe-quality-protocol.md) et [adaptation Small](m1-yoloe-small-protocol.md)
réutilisés sans édition : mêmes 144 images (128 LVIS +16 ADE), mapping/GT/12 IDs visuels,
score 0,20/0,35/0,50, NMS IoU 0,5 agnostique et par classe, caps 20/100, pré-cap 300.
Masques/frontières à 0,35 seulement, boîtes avant rejet des masques vides. Inconnus et noms
non mappés visibles ; typé commun/nouveau, géométrie nouvelle sans nom et mêmes GT appariés
restent séparés. FN à zéro, strates d’images riches/petit GT et ADE union {2,26}/arbre 5/plante 18/void
hérités. Pas de nouveau synonyme, jeu de données, seuil qualité ou rescoring après résultats.

Les trois candidats ont exactement les mêmes 4 585 noms/ordre que Nano/Small. Exports FP32 CPU,
statique 640²/batch 1/opset 17/simplifyFalse/nmsFalse/end2endFalse ; formes
`[1,3,640,640]` → `[1,4621,8400]` + `[1,32,160,160]` vérifiées.
[Sources officielles](https://github.com/ultralytics/assets/releases/tag/v8.4.0) et
[licence AGPL3](https://raw.githubusercontent.com/ultralytics/assets/v8.4.0/LICENSE) conservées,
usage privé local identique aux étapes précédentes, sans encodeur Apple ou achat.
Checkpoint/export/provenance/SHA et notices sont sous les dossiers ignorés dédiés.

Parité hors corpus : X/M passent les tolérances initiales. L a conservé son échec brut initial
et fait l’objet d’un amendement technique **avant toute prédiction sur les 144 images** :
seules les coordonnées tolèrent ≤0,05 pixel réseau ; scores/coefficients≤0,02/protos≤0,005.
Diagnostic sur les deux anciennes fixtures, sans réexport : décisions identiques pour les 12
profils ; masques comparés uniquement à 0,35 et IoU≥0,99. Aucun affaiblissement global des
anciennes preuves ou règles, aucun résultat sur 144 images utilisé pour cette décision. Détails dans README.

Adaptateur mince : mêmes engines qualité/Small, session canonique et métriques importées ;
uniquement taille, chemins, préfixe et trois références appariées adaptés. Chaque candidat
sera comparé à Nano et Small de même NMS, et à baseline par classe ; supports des mêmes GT,
couverture et scores avec manqués à zéro publiés. Panneaux aux 12 IDs : image/GT/actuel/Nano/
Small/candidat, références en lecture seule. Tous les 12profils conservés, pas de meilleur profil
choisi après coup. Les annotations partielles et le déséquilibre chapeaux/scène dense restent
des limites ; aucune moyenne de prévalence d’usage ni AP universelle.

7 tests ciblés conformes après Red/Green pertinents, intégration synthétique et format/syntaxe
conformes. Nouveaux sources/JSON LF avant gel, aucun ancien octet scellé normalisé.
Gel `1596a5630e3b7d0635721f990c5a919c40cf919ab28fbe05b85852fdb8464d34`, 66 références,
imbrique les gels hérités `6ff9…dcb2` et `38562…c445` avec données, dépendances et résultats.
Après revue/GO : trois passes sérielles de 144 forwards chacune, 1 728 enregistrements/12 profils ;
les 288+144 forwards primaires des références ne seront pas rejoués. Aucun résultat prétendu ici.
Qualité utile prioritaire, coûts ensuite ; performances Android X/M/L non mesurées et aucun
choix de modèle fondé sur les seules tailles de poids.
