# Partager un objet et une consigne avec l'application Gemini

30 octobre 2026 — M2-05, variante 0.9.4 installée et validée sur PC ; réception
conjointe réelle en échec : image reçue mais texte absent. Retour historique 0.9.3 :
« Image reçue, consigne absente », réception conjointe en échec.
Le bouton définitif reste conditionné à un succès réel des deux éléments.
Baseline initiale 0.9.3 : `14778f81228c44e667e5f41186a749bb937dca32`.
Baseline de la variante 0.9.4 : `dd79d18b44b44bc4b4e63a4fa9befbb004dd68c7`.

## Besoin et périmètre

L'utilisateur privilégie la pertinence des descriptions, même avec traitement en
ligne. Il demande de tester le transfert du recadrage et de la consigne vers
l'application Gemini, puis d'ajouter « Demander à Gemini » si les deux sont reçus.
Il ne demande pas Gemini Nano ni une API facturée. La réponse se consulte dans
Gemini ; aucun retour automatique de son contenu dans Blamera n'est promis.

La première étape livre dans l'APK principal personnel 0.9.3/code 16 une action
provisoire « Tester Gemini » sur la fiche de l'objet sélectionné. Après preuve
humaine de réception des deux éléments, elle pourra devenir « Demander à Gemini ».
Le parcours Lens accepté en 0.9.2 est conservé. Aucun succès de description n'est
déduit de l'ouverture d'une activité Android ou des seuls tests PC.

## Contrat de transfert

Partager le JPEG exact de l'aperçu existant, avec son contexte proche, sans masque,
libellé estimé, EXIF, nom de fichier personnel ni URI de l'original. Réutiliser le
cache privé, le provider non exporté, les bornes et l'expiration de l'[ADR 0008](../decisions/0008-google-lens-share.md).
L'image est prête localement sur sélection ; seul l'appui de partage la remet à
une application externe.

Consigne fixe :

> Identifie cet objet aussi précisément que possible, décris son usage et indique tes incertitudes. Réponds en français.

Utiliser le contrat public Android `ACTION_SEND image/jpeg`, `EXTRA_STREAM` et
`ClipData` désignant un seul URI de lecture ; `EXTRA_TEXT` contient la consigne.
La variante 0.9.4 conserve EXTRA_TEXT et ajoute la même consigne au champ texte
de l'unique `ClipData.Item` portant l'URI ; HTML et intent imbriqué restent nuls.
L'élément Lens conserve son texte nul. Cela suit la construction publique de
[ShareCompat dans AndroidX](https://android.googlesource.com/platform/frameworks/support/+/f24416ddf0d3fcd79fa2542aa90c391ce731f755/core/core/src/main/java/androidx/core/app/ShareCompat.java).
`ClipData` est ici le contenu de l'intent : aucun accès ni écriture au presse-papiers
système. Ce format est un nouvel essai fondé, sans preuve que Gemini lit ce champ.
La route Lens n'ajoute pas cette consigne. Résoudre un gestionnaire compatible,
activé, exporté et accessible dans `com.google.android.apps.bard`. La présence du
package seul ne suffit pas. Aucun destinataire Google générique ambigu, activité
privée, extra interne, presse-papiers ou automatisation de saisie/envoi.

L'appui ouvre Gemini pour que l'utilisateur vérifie puis soumette la demande
lui-même. Le texte et l'image transmis sont annoncés dans Blamera. Sans gestionnaire
compatible, ou si l'ouverture échoue, présenter une erreur récupérable sans ouvrir
Lens en remplacement. Préserver photo, sélection, zoom et retour à l'original dans
la session, selon les limites existantes de conservation en mémoire.

Une seule publication est active, quel que soit le destinataire. Annulation,
arrière-plan avant remise et changement de source/sélection empêchent un lancement
périmé. Une image déjà remise doit rester disponible pour la lecture différée du
destinataire dans les bornes du cache et du TTL. Aucun changement de modèle,
dépendance, permission Internet, réglage du téléphone ou installation d'une autre
application n'est nécessaire à l'essai.

## Sources et limites de faisabilité

[Android décrit le partage de données](https://developer.android.com/training/sharing/send).
Ce contrat définit ce que Blamera peut transmettre ; il ne garantit pas que Gemini
utilisera simultanément tous les extras reçus.
[L'aide Gemini](https://support.google.com/gemini/answer/14554984?hl=fr) décrit les
questions avec image et [sa fiche officielle](https://play.google.com/store/apps/details?id=com.google.android.apps.bard&hl=fr)
identifie le package. Ces sources ne prouvent ni l'acceptation de la consigne par
partage Android ni la version effectivement présente sur le Xiaomi.

L'utilisation gratuite de Gemini est soumise aux
[limites de son application](https://support.google.com/gemini/answer/16275805).
Pas de clé API, achat, abonnement ni activation automatique d'un forfait.
La disponibilité du service, ses réponses et leur justesse restent externes.

## Acceptation et preuve

- Tests PC réels en TDD : image et consigne exactes dans l'intent ciblé, grant seul,
  destinataire absent/refusé, erreurs, annulation, concurrence et cycle de vie.
- Régression Lens et sélection/capture/import/zone ; aucun envoi sur sélection.
- Qualité globale, revue indépendante puis testeur ; métriques sans assouplissement.
- L'utilisateur ouvre Blamera et actionne le diagnostic sur un objet sélectionné.
  Il vérifie, avant toute saisie manuelle, l'image lisible et la consigne préremplie
  dans Gemini. Il peut ensuite envoyer lui-même la demande et juger la réponse.
- Vérifier le retour à Blamera et relever le message exact si le transfert échoue.
  Aucune photo personnelle à transmettre à l'agent, aucun pilotage du téléphone.
- Si seule l'image est reçue, le critère de transfert conjoint échoue : ne pas
  présenter le bouton définitif comme fonctionnel ni substituer silencieusement
  une étape de copier/coller à l'automatisation demandée.

Après réception conjointe confirmée, finaliser le libellé « Demander à Gemini » et
mettre à jour les preuves ; jusque-là, M2-05 reste en cours.
Après l'échec réel 0.9.3, un seul essai supplémentaire de ce format public est
retenu. Sans succès ni nouvelle preuve de faisabilité, ne pas accumuler des extras
ou MIME arbitraires : documenter la limite et cadrer une alternative avec l'utilisateur.

Le second retour confirme l'échec du transfert conjoint : « Toujours pas de texte
dans gemini. L'image y est ». Les deux formats publics testés ne préremplissent pas
la consigne sur cette installation ; aucune cause interne ni impossibilité universelle
n'est démontrée. Le test borné s'arrête. Le bouton définitif reste non livré et aucune
copie au presse-papiers ou nouvelle API n'est ajoutée sans choix explicite du parcours.
Une préférence est demandée entre étude d'une API image/texte et parcours manuel
avec copie sur appui et collage dans Gemini. Les deux restent à cadrer.
