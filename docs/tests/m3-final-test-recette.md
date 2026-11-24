# Recette — test final après calibration figée

PC 0.6.1, [contrat](../specs/face-final-test.md).

1. Recharger http://127.0.0.1:8501/ puis Personnes → Évaluation. Vérifier le gel
   courant : rejet 0,30, acceptation 0,50, marge 0,05, cinq références retenues.
   L'orchestrateur peut avoir déjà évalué la calibration et figé ces paramètres.
2. Dans Afficher le lot, choisir Test final réservé. Les quinze photos sont
   conservées dans leur ordre ; les noms attendus restent ceux des annotations.
   Le guide privé `pc/data/import-plans/test-final-a-confirmer.md` indique
   aussi le sujet du dossier pour les inconnus, avec numérotation propre au lot.
3. Pour chaque photo où le sujet attendu est détecté, choisir son visage et
   Enregistrer ce visage. Si le bon sujet manque parmi les candidats, toucher
   Sujet attendu non détecté ; ne jamais choisir un autre sujet. Le constat est
   annulable et distinct de l'identité Personne inconnue. Dans ce lot, l'image 5
   correspond à cette situation signalée ; l'image 6 n'a aucun visage détecté.
   Les treize autres confirmations déjà enregistrées restent conservées.
4. Toucher Évaluer le test final après ces préparations. Le Bilan du Test final
   doit contenir quinze images au total, dont treize exploitables et deux non
   exploitables avec des motifs distincts. Il reste séparé du Bilan de Calibration.
   L'orchestrateur est autorisé à appliquer le constat signalé et lancer le rapport.
   Signaler une erreur ; ne pas changer les paramètres pour corriger
   un résultat statistique.
5. Recharger : choix et rapport doivent rester conservés. Confirmer ici que
   l'évaluation est terminée ; l'orchestrateur lira le rapport local pour analyser
   les bons noms, inconnus, abstentions et échecs de détection.

La galerie principale ne reçoit aucune photo ni modification de confirmation.
Après ouverture du test, les mêmes images ne servent plus à régler un nouveau
seuil présenté comme indépendant. Aucun besoin de transférer des photos ici.
