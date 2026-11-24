# M3-06 — mesures réelles de reconnaissance sur Xiaomi

2 novembre 2026, Blamera 0.18.0/code32. Xiaomi 14T Pro ; Android 16 et
HyperOS 3.0.305.0 sont les versions déclarées par l'utilisateur, non relues par
ADB. OpenCV 4.13.0 est confirmé par les événements du moteur. Aucun modèle,
seuil, galerie, source de production ou test logiciel modifié.

Le [protocole](../specs/face-device-validation.md) utilise uniquement les diagnostics
de Blamera filtrés par son PID. L'utilisateur a ouvert l'application et effectué
chaque analyse. Aucune photo envoyée au PC ou à un service par ce travail, aucune
manipulation du téléphone par l'agent. Les lecteurs PC sont arrêtés après capture.

## Série à moteur déjà chargé

Import d'une photo à un visage puis trois analyses ; même parcours sur une photo
à trois visages. L'import déclenche aussi une analyse : **quatre observations par
groupe**, pas quatre photos indépendantes. Le seul événement `-T 1` reçu avant
la demande d'essai est exclu de cette série, mais conservé dans le brut.

| Nombre de visages | n | Minimum moteur | Médiane moteur | Maximum moteur |
| --- | ---: | ---: | ---: | ---: |
| 1 | 4 | 220 ms | 418 ms | 567 ms |
| 3 | 4 | 610 ms | 1118,5 ms | 1202 ms |

Valeurs : un visage 220/420/567/416 ms ; trois visages 610/1078/1159/1202 ms.
Retour utilisateur : « ok. pas de lenteur ni d'erreurs ». Aucun taux de réussite
statistique ou de faux noms n'est déduit de ce retour ou de ces logs de succès.

Preuves locales : `build/reports/validation/m3-06/live/20261102-152838/`.
SHA-256 du brut à neuf événements :
`187a09fb76bafcb22c079ada9739a28be4c2e52542016789e7d7a1aba07789e5`.
Le récapitulatif conserve l'événement exclu et sa raison, les effectifs et valeurs.

## Nouvelle instance du moteur

Après fermeture/réouverture manuelle avec Retour, le lecteur est prêt avant la
première photo de groupe, puis un nouvel appui Analyser. Le PID est identique :
on ne conclut pas à un nouveau processus. Un **nouvel autocontrôle observé**
confirme en revanche une nouvelle ouverture du moteur, avec cache local existant.

| Mesure | Observation |
| --- | ---: |
| Initialisation et autocontrôle (`cold_ms`) | 1576 ms |
| Première analyse de trois visages, initialisation comprise | 2218 ms |
| Analyse suivante de trois visages | 1175 ms |

Ne pas additionner 1576 et 2218 ms : le premier fait partie du second. Le dernier
appel est une observation séparée de la série précédente, pas un ajout silencieux
à sa médiane. Retour humain : « fait. pas long ».

Preuves : `build/reports/validation/m3-06/cold/20261102-153419/`, exactement trois
événements : autocontrôle, premier appel, appel suivant. SHA-256 :
`a41202c4162b59b61f580c2b9cdd9bb468da2fd978fc1828e48869160ec83e40`.

### Parité numérique sur ARM64

Trois cas publics : zéro visage, un visage et deux copies d'un portrait public,
avec alignements et encodages comparés individuellement au PC. Décisions identiques.

| Mesure | Maximum/minimum observé | Tolérance préexistante |
| --- | ---: | ---: |
| Écart des boîtes | 0,0000244141 px | ≤1 px |
| Écart des repères | 0,0000244141 px | ≤1 px |
| MAE des RGB alignés | 0,000345451 sur 0..255 | ≤1 |
| Cosinus minimal des embeddings | 0,99999972485 | ≥0,9999 |
| Écart maximal des scores de prototypes publics | 0,00000027515 | ≤0,002 |

Toutes les tolérances sont respectées, sans réajustement. Cela qualifie cette
chaîne numérique publique sur ce téléphone, pas la précision générale des noms.

## Mémoire : valeurs disponibles et limites

- Série à un visage : PSS total instantané entre 439,23 et 777,99 Mio.
- Série à trois visages : entre 786,38 et 838,18 Mio.
- Nouvelle instance : delta de deux instantanés pendant l'initialisation 408,77 Mio ;
  PSS total après le premier appel 944,71 Mio, puis 854,03 Mio après le suivant.

Ces valeurs concernent le **processus entier** (dont YOLOE, photos et interface).
Le delta est influençable par les autres tâches et le ramasse-miettes. Le plus
grand instantané ne donne pas le maximum atteint entre les relevés ; ce n'est
donc ni un pic réel ni la mémoire supplémentaire attribuable au seul visage.
La cible de pic supplémentaire ≤700 Mio n'est **pas vérifiée**, et n'est pas
infirmée par un total processus supérieur à 700 Mio.

## Bilan et suites

La parité publique et les latences moteur sont mesurées sur le Xiaomi. Les cibles
≤15 s chaud et ≤30 s premier lot sont respectées sur les appels moteur observés.
Le temps jusqu'au rendu à l'écran, le processus/cache complètement froid, le pic
mémoire supplémentaire, la batterie et une précision statistique mobile restent
non mesurés. Les temps moteur ne valident pas un budget complet d'interface.

Le test final PC reste celui déjà gelé : 15 cas, 13 exploitables, 8/8 proches exploitables
corrects et 5/5 photos d'un seul inconnu rejetées, deux cas non exploitables et aucun
faux nom sur ce petit corpus. Voir [le bilan final PC](m3-02-missing-target.md).
Ces effectifs ne sont ni étendus par les répétitions sur téléphone ni réutilisés
pour régler les seuils.

Après ces mesures, l'utilisateur précise que Blamera est un POC personnel et demande
de clôturer ce type de chantier lorsque l'usage lui convient. **M3-06 est clos
dans ce périmètre accepté**, sans instrumentation supplémentaire du pic mémoire
ou du délai complet. Les mesures absentes restent des limites documentées et ne
sont pas déclarées conformes. Aucune validation complémentaire n'est requise pour
ce seul motif ; rouvrir en cas de besoin concret ou de nouvelle demande.

Revue indépendante des captures chaudes puis de la nouvelle instance : calculs,
SHA, portée des chronomètres et tolérances confirmés. La revue documentaire finale
confirme la cohérence du bilan et de la clôture POC, les liens et les 393 empreintes
de sources inchangées. Aucun nouvel incrément logiciel ou test artificiel ajouté.
