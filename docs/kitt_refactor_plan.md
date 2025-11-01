## Plan de refactorisation KITT

### 1. Cadrage & mesures
- dresser l’inventaire des responsabilités actuelles de `KittFragment` (UI, animations, audio, IA)
- identifier les zones critiques (taille du fichier, dépendances globales, duplications)
- définir la check-list de tests manuels (activation, écoute, TTS, VU-meter, mode persistant)

### 2. Refonte des modules
- `KittUiState` : créer un état centralisé (data class + événements) et déplacer la logique d’affichage
- `KittStatusManager` : encapsuler la mise à jour toolbar, chips, voyants et messages
- `ScannerAnimator` / `VuMeterAnimator` : isoler les animations (handlers/runnables)
- `KittAudioController` : regrouper SpeechRecognizer, TTS et MediaPlayer avec une API claire (start/stop)

### 3. Réorganisation du fragment
- limiter `KittFragment` aux interactions cycle de vie + délégation vers les nouveaux modules
- réduire les propriétés globales, injecter les dépendances via le ViewModel ou via des factories internes
- maintenir la compatibilité avec les ViewModel existants (`KittViewModel`)

### 4. Nettoyage des ressources
- harmoniser les libellés (`strings.xml`, `dimens.xml`, `colors.xml`)
- supprimer les assets inutilisés et renommer ceux conservés
- documenter les nouvelles ressources (commentaires dans XML)

### 5. Validation
- ajouter tests unitaires ciblés (`KittStatusManagerTest`, `KittUiStateTest`)
- prévoir un test instrumenté simple (par exemple un `FragmentScenario`) pour valider les animations
- aligner la journalisation (tag unique `KittUI`, niveaux cohérents)

### 6. Documentation & transition
- rédiger un README du module KITT (diagrammes, interactions, dépendances)
- lister les améliorations futures (migration Compose, automatisation tests, accessibilité)
- planifier une revue après refactor pour valider UX et dette résiduelle
