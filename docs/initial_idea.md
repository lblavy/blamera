Projet : application mobile de vision par ordinateur pour un portfolio IA

Créer une application Android qui analyse le flux de la caméra, détoure les objets, affiche leurs catégories, reconnaît des personnes préenregistrées et identifie les chats d’un utilisateur. L’utilisateur peut sélectionner un objet pour lancer une recherche visuelle et/ou une analyse par IA.

L’objectif est de démontrer à la fois l’usage de l’IA pour développer l’application et la compréhension des modèles : intégration, préparation des données, entraînement personnalisé, évaluation et déploiement. L’originalité commerciale n’est pas une exigence.

Fonctionnement attendu
- Afficher la caméra avec les contours ou les masques colorés des objets détectés.
- Afficher les catégories connues du modèle : « personne », « chat », « chaise », etc.
- Afficher le nom des personnes et des chats reconnus, avec une possibilité de répondre « inconnu » ou « identité incertaine ».
- Permettre de toucher un objet pour rechercher des informations, des produits correspondants ou une fiche produit.
- Faire fonctionner la vision et la reconnaissance localement sur le téléphone, dans la mesure des performances disponibles. Les recherches externes nécessitent une connexion.
Découpage en milestones
Milestone	Fonctionnalités mobiles	Préparation sur ordinateur / backend
1. Détection et détourage	Caméra, segmentation des objets, contours ou masques, labels génériques	Aucun backend nécessaire
2. Recherche sur sélection	Sélection d’un objet, extraction de son image, recherche visuelle et/ou IA, résultats avec liens sources	Éventuel relais API pour les services externes
3. Reconnaissance des personnes	Détection des visages et comparaison avec une galerie importée	Gestion des personnes et photos, calcul des embeddings, export
4. Reconnaissance personnalisée des chats	Identification individuelle à partir du modèle importé	Gestion des photos, entraînement, évaluation et export du modèle


Chaque milestone doit produire une version utilisable et démontrable.

Détection et détourage des objets

Privilégier initialement un modèle préentraîné de segmentation d’instances pouvant fournir simultanément rectangles, masques, catégories et scores.

Le premier périmètre couvre les catégories connues du modèle choisi. Il ne vise pas à reconnaître exhaustivement tous les éléments possibles d’une scène.

La catégorie « chat » peut donc être détectée dès le milestone 1 ; le milestone 4 ajoute l’identité du chat.

Reconnaissance des personnes

Utiliser un modèle facial préentraîné avec une galerie d’embeddings, sans entraînement personnalisé prévu au départ.

Chaîne envisagée : détection du visage, recadrage et alignement, calcul de l’embedding, comparaison avec les références enregistrées.

Le détourage de la silhouette n’est pas un préalable à la reconnaissance faciale. La détection générale des personnes et la détection spécialisée des visages peuvent utiliser des modèles distincts.

Exporter vers le téléphone les embeddings, les noms et les paramètres nécessaires. Les photos originales ne sont pas indispensables à la reconnaissance mobile. Le modèle facial et la préparation des images doivent être cohérents entre ordinateur et téléphone.

Reconnaissance personnalisée des chats

L’approche privilégiée est un apprentissage personnalisé à partir des photos fournies par l’utilisateur :
1. Partir d’un extracteur visuel préentraîné.
2. Entraîner un classificateur pour distinguer les chats enregistrés.
3. Expérimenter ensuite un fine-tuning partiel de l’extracteur.
4. Comparer ces approches à une référence utilisant simplement des embeddings existants.
L’application doit permettre d’enregistrer d’autres chats sans modifier son code. Ajouter un chat peut nécessiter un nouvel entraînement.

Prévoir la reconnaissance de chats inconnus, y compris lorsqu’un utilisateur n’a enregistré qu’un seul chat : choisir systématiquement une classe ne suffit pas.

Données disponibles pour commencer

Trois chats aux pelages différents, avec environ 74, 200 et 300 photos, regroupées par identité dans Google Photos. Les photos ont été prises au fil des années.

Travail de préparation à prévoir :
- Vérifier les identités et les recadrages, notamment sur les photos contenant plusieurs chats.
- Repérer les doublons et les rafales.
- Séparer entraînement, validation et test par séances ou périodes pour éviter les images presque identiques entre ensembles.
- Mesurer les résultats par chat, orientation et conditions de prise de vue.
- Ajouter des images d’autres chats pour évaluer le rejet des inconnus.
Les variations de posture, d’âge et d’éclairage constituent des axes d’expérimentation.

Outil de préparation et d’entraînement

Pour la première version, construire un outil local sur ordinateur, éventuellement avec une interface web.

Il gère les personnes, les chats, leurs photos, les vérifications, les entraînements et les exports. Des dossiers et des fichiers de configuration peuvent suffire initialement.

Pas d’authentification, de gestion de droits ou d’infrastructure multiutilisateur à prévoir à ce stade. Le transfert vers le téléphone peut être manuel.

Le paquet exporté contient les modèles nécessaires, les noms associés aux sorties, la galerie faciale, les paramètres de préparation et de décision, ainsi que les versions.

Recherche sur un objet sélectionné

Conserver l’image originale, le recadrage et le masque : une image détourée n’est pas systématiquement meilleure pour la recherche.

Distinguer recherche par image, description par IA et recherche textuelle enrichie par les marques ou références visibles. Présenter les sources et différencier produit ressemblant et référence exacte. Vérifier les API réellement disponibles sans supposer que Google Lens est directement intégrable.

Choix techniques encore ouverts

Aucune stack ni aucun modèle précis n’a été arrêté. Les choix devront tenir compte de la compatibilité Android, de la fluidité, de la consommation mémoire et batterie, des possibilités d’export et des licences.