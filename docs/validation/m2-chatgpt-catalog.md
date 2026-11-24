# Catalogue ChatGPT au-delà de 256 Kio

30 octobre 2026. Baseline : `cad9bdbb0581a95d535ab259b6d14b26de6c9245`.
Correctif 0.10.4/code22, revue, contrôles globaux et testeur conformes sur PC ;
APK principal installé. Parcours réel synthétique validé sur Xiaomi par retour
humain le 31 octobre 2026.

## Preuve réelle et diagnostic

Après 0.10.3, l'utilisateur revient dans Blamera et attend quelques secondes.
Il voit « réponse du service invalide ou incomplète », étape chargement des modèles.
La trace locale `build/reports/validation/m2-chatgpt-foreground/app-events-0.10.3.json`
est composée uniquement d'événements fixes du PID Blamera, sans corps de réponse,
compte, jeton, code OAuth ou URL.

L'ordre observé est WAITING_APP_RETURN, RESUMED, EXCHANGING_CODE, TOKEN HTTP200,
VERIFYING_ACCOUNT, JWKS HTTP200, LOADING_MODELS, MODELS HTTP200, puis
HTTP_MODELS_READ_BODY_PROTOCOL_200 et FAILURE_LOADING_MODELS_INVALID_RESPONSE.
La reprise au premier plan permet donc cette fois l'échange et la vérification.
Elle ne prouve encore ni catalogue utilisable ni inférence.

À READ_BODY, le garde fonctionnel susceptible de produire cette catégorie est
celui de `readTrialBody` : les octets dépassent le maximum demandé. Le gateway
limite le catalogue à 256 Kio ; les délais ont leur propre catégorie TIMEOUT.
L'erreur intervient avant le parseur JSON, donc aucun défaut de schéma n'est
établi et aucun contenu du catalogue personnel n'est nécessaire au diagnostic.
La taille exacte au-delà de cette borne n'est pas mesurée.

La [documentation officielle des modèles et de l'inférence](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
confirme le catalogue `models`, le filtre `visibility: list`, les champs `slug`
et `display_name`, l'ordre du service et le endpoint public déjà utilisé. Elle
ne donne pas de plafond de réponse à 256 Kio. Les métadonnées additionnelles du
catalogue ne doivent pas empêcher la lecture de ses choix utiles.

## Contrat de correction

- Lecture du catalogue synthétique de plus de 256 Kio via le véritable adapter
  HTTP simulé sur PC, avec métadonnées inutiles ; mêmes choix visibles, noms,
  identifiants et ordre après parsing.
- Budget retenu : huit Mio uniquement pour GET sur le endpoint exact `/v1/models`.
  C'est une borne locale explicite, pas une taille promise par OpenAI. Les objets
  parsés restent limités à 500 ; les noms, identifiants et doublons gardent leurs
  contrôles existants. Le corps n'est ni persisté ni journalisé.
- La borne est acceptée ; un octet de plus est refusé sans lecture infinie.
  Les réponses d'erreur restent bornées à 64 Kio ; l'inférence à deux Mio.
- Le diagnostic fixe BODY_LIMIT identifie le dépassement sans contenu externe.
  Une annulation ou expiration garde sa priorité et une requête ne produit pas
  deux diagnostics d'échec.
- Délais absolus, annulation, nettoyage de connexion et validations HTTPS restent
  actifs. Aucun changement OAuth, scope, permission, paiement, garde des crédits
  ou envoi automatique de photo.
- Fixture synthétique et tests PC uniquement. Aucune lecture de données privées
  sur le téléphone, aucun appel authentifié PC et aucun réglage de l'appareil.

Les huit Mio donnent une marge bornée au catalogue riche en métadonnées. Le coût
transitoire comprend le buffer, la chaîne et les objets JSON ; l'impact réel sur
le Xiaomi n'est pas encore mesuré. Il ne s'agit ni d'un cache permanent, ni d'une
augmentation des budgets des autres réponses.

## TDD

Red exécuté : `build/chatgpt-catalog-red.log`, sortie **1**, 36 secondes,
trois tests/trois échecs fonctionnels. Le catalogue synthétique d'environ 360 Kio
échoue par le véritable gateway et l'adapter HTTPS simulé, avant son parseur.
Le flux généré de huit Mio est rejeté par l'ancien plafond global de deux Mio.
Le test huit Mio plus un octet expose ce même rejet préalable au lieu du garde
de lecture attendu. Aucune requête vers un compte réel ou donnée personnelle.

Green initial : `build/chatgpt-catalog-green.log`, sortie **0**, 21 secondes,
cinq tests verts. Green final : `build/chatgpt-catalog-green-final.log`, sortie
**0**, 53 secondes, **91 tests sans échec ni erreur**. Les sept tests de
`TrialCatalogBoundsTest` couvrent les bornes, la séparation des endpoints,
l'erreur HTTP, l'annulation, le délai et la priorité du délai sur le dépassement.
Le lecteur PC refuse d'abord la nouvelle paire de version (huit tests, une erreur
attendue), puis les huit tests passent ; journaux `build/chatgpt-catalog-reader-red.log`
et `build/chatgpt-catalog-reader-green.log`. Formatage et detekt finaux : sorties 0.

JaCoCo ciblé : backend 914/942 lignes (97,03 %) et 495/550 branches (90 %) ; UI
323/333 lignes (97 %) et 125/142 branches (88,03 %). Nouveau code contre la
baseline : 13 lignes exécutables, lignes 100 %, branches 90 %. La couverture
globale attend la campagne complète ; un run ciblé ne la prouve pas.

Revue indépendante conforme, sans problème bloquant : bornes par endpoint,
fermeture des ressources, priorité annulation/délai, diagnostic unique et parseur
conservé vérifiés sur le diff gelé. Le reviewer a relu les Red et les 91 XML verts,
sans relancer de tests ni accéder au compte ou au téléphone.

## Contrôles globaux

Commande exécutée, sortie **0** :

```powershell
./scripts/quality.ps1 -BaseRef cad9bdbb0581a95d535ab259b6d14b26de6c9245
```

Archives dans `build/reports/validation/m2-chatgpt-catalog/` : `quality.log`,
`quality-final/summary.json`, `jacoco-global.xml`, `junit-global/` et
`junit-counts.json`. **319 tests Android, 76 scripts et quatre PC** sans échec.
Les 266 empreintes du snapshot de sources/configuration sont inchangées.

| Mesure | Résultat |
| --- | --- |
| Mobile global, lignes / branches | 96,73 % / 87,25 % |
| Nouveau mobile, lignes / branches | 100 % / 90 %, 13 lignes exécutables |
| Duplication nouvelle mobile | 0/17 lignes ; globale 0,229 % |
| Complexité cyclomatique / cognitive | Aucune violation des limites 10/15 |
| Scripts globaux, lignes / branches | 94,35 % / 88,52 % |
| Nouveau script | Lignes 100 % ; aucune branche nouvelle mesurable |
| PC global, lignes / branches | 100 % / 100 % ; aucun nouveau code mesurable |
| Audits et licences | 97 paquets Maven / 131 entrées, 35 PyPI / 35 entrées |

Aucune vulnérabilité active retournée dans les inventaires et avis consultés.
La portée reste celle des contrôles documentés ; aucune règle, exclusion ou
dépendance n'est modifiée pour faire passer cette campagne.

## Validation indépendante et installation

Testeur conforme sur PC : **91 tests ciblés** sans échec/erreur/ignoré, sortie 0
en 53 secondes, et huit tests verts du lecteur. Les critères fonctionnels et
limites ci-dessus sont validés sur PC. Rapport :
`build/reports/validation/m2-chatgpt-catalog/tester/report.md`.
Package `dev.blamera`, version 0.10.4/code22, activité privée `fullUser`, permissions
inchangées. Les 37 assets/bibliothèques sont identiques à 0.10.3 ; les 266 empreintes
de sources/configuration sont inchangées avant et après validation.

APK `dist/blamera-0.10.4-debug.apk`, **457 867 613 octets**, SHA-256 :

```text
7e91ba8dc329003d92eb2a39e65939d7da7040473d9fcc12962489bad1bfbb0c
```

Certificat debug inchangé :
`eeb48cbe8207aaba89a6822099f10d5dc4dcc491f64cb288ce7e248141449555`.
Installation du seul APK principal sur le Xiaomi : `adb -d install -r`, sortie 0
« Success », le 30 octobre 2026 de 22:02:59 à 22:03:19 UTC. Reçu local
`build/reports/validation/m2-chatgpt-catalog/install-result.json`.
Aucun lancement automatique ni autre mutation du téléphone.

L'utilisateur doit ouvrir l'essai, refaire la connexion puis revenir manuellement
dans Blamera pour vérifier les modèles. Si le catalogue apparaît, l'image
synthétique et sa consigne restent soumises à la vérification humaine préalable
des crédits désactivés puis à l'appui explicite d'envoi. Si ce réglage n'est pas
vérifiable, aucun envoi ne doit être fait. M2-06 reste ouvert jusqu'au succès réel
du catalogue puis de l'image avec consigne, sans crédits payants activés.

## Résultat réel — 31 octobre 2026

L'utilisateur fournit une capture de l'essai 0.10.4 montrant la liste des modèles,
la case de confirmation des crédits cochée, « Analyse terminée » et la réponse
dans Blamera. Celle-ci décrit en français la tasse bleue, sa grande anse à droite,
l'ouverture sombre et le mot BLAMERA. Elle expose aussi les incertitudes sur le
matériau et le contenu de cette illustration. Le contenu pertinent et la langue
attendue valident l'image et la consigne pour ce parcours synthétique.

Le catalogue et l'inférence sont donc validés sur le Xiaomi 14T Pro personnel,
HyperOS 3.0.305.0 / Android 16. Aucun nouvel accès au téléphone ou appel API n'est
réalisé par l'agent pour consigner ce retour ; la capture personnelle reste hors
des fichiers versionnés. Les preuves PC précédentes sont inchangées.

Limites : le modèle sélectionné n'est pas visible dans la portion transmise et
la latence n'est pas mesurée. La case atteste la vérification humaine demandée ;
elle ne constitue pas une lecture du réglage serveur ou un audit de facturation.
Les marqueurs Markdown sont affichés comme texte brut : amélioration de
présentation suivie dans M2-03-UI-01, sans blocage fonctionnel de cet essai.

**M2-06 est terminé pour l'essai ciblé.** L'envoi du recadrage réel depuis la fiche
de l'objet, sa réponse et les validations associées relèvent de M2-03/M2-04.
