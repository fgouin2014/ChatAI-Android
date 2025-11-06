# Guide : Obtenir une Voix Masculine pour KITT

## 🎙️ Problème
La voix TTS par défaut est féminine. Pour KITT, on veut une voix masculine/grave.

## ✅ Solution : Changer le Moteur TTS

### Option 1: Google Text-to-Speech (Recommandé)

1. **Installer Google TTS** (si pas déjà installé)
   - Play Store → "Google Text-to-Speech"
   - Ou: https://play.google.com/store/apps/details?id=com.google.android.tts

2. **Activer et Configurer**
   ```
   Paramètres Android
   → Accessibilité
   → Synthèse vocale
   → Moteur favori: Google Text-to-Speech
   ```

3. **Télécharger Voix Française Masculine**
   ```
   Synthèse vocale
   → Paramètres (roue dentée Google TTS)
   → Installer les données vocales
   → Français (France)
   → Télécharger voix masculine (fr-FR-Wavenet-B ou fr-FR-Standard-B)
   ```

4. **Sélectionner la Voix**
   ```
   Google TTS Paramètres
   → Langue
   → Français (France)
   → Voix: fr-FR-Wavenet-B (Masculin)
   ```

### Option 2: Samsung Text-to-Speech (Samsung uniquement)

1. **Vérifier Samsung TTS** (préinstallé sur Samsung)
   ```
   Paramètres
   → Accessibilité
   → Synthèse vocale
   → Moteur favori: Samsung text-to-speech engine
   ```

2. **Télécharger Voix**
   ```
   Samsung TTS Paramètres
   → Télécharger des voix
   → Français
   → Sélectionner voix masculine
   ```

### Option 3: Vocalizer TTS (Payant - Qualité Premium)

1. **Installer** depuis Play Store
2. **Acheter pack français** (~5-10€)
3. **Activer comme moteur par défaut**

---

## 🧪 Tester la Voix

### Dans ChatAI

1. Ouvrir ChatAI
2. Activer KITT (power switch)
3. Ouvrir drawer (bouton MENU)
4. Section "PERSONNALITÉ IA"
5. Cliquer "KITT PROFESSIONNEL"
6. Dire "Bonjour KITT"
   → Devrait utiliser la voix masculine

### Test Direct Android

```
Paramètres
→ Accessibilité  
→ Synthèse vocale
→ "Écouter un exemple"
```

---

## 📋 Voix Disponibles (Google TTS)

| Nom | Genre | Qualité | Taille |
|-----|-------|---------|--------|
| fr-FR-Wavenet-A | Féminin | Haute | ~40 MB |
| fr-FR-Wavenet-B | **Masculin** | Haute | ~40 MB |
| fr-FR-Wavenet-C | Féminin | Haute | ~40 MB |
| fr-FR-Wavenet-D | **Masculin** | Haute | ~40 MB |
| fr-FR-Standard-A | Féminin | Normale | ~10 MB |
| fr-FR-Standard-B | **Masculin** | Normale | ~10 MB |
| fr-FR-Standard-C | Féminin | Normale | ~10 MB |
| fr-FR-Standard-D | **Masculin** | Normale | ~10 MB |

**Recommandation:** `fr-FR-Wavenet-B` ou `fr-FR-Wavenet-D` pour KITT

---

## 🔧 Résolution de Problèmes

### La voix reste féminine après changement

**Solution:**
1. Fermer complètement ChatAI (killer l'app)
2. Rouvrir ChatAI
3. Réactiver KITT

### Pas de voix masculine disponible

**Solution:**
1. Vérifier la connexion internet
2. Play Store → Mes apps → Google Text-to-Speech → Mettre à jour
3. Redémarrer le téléphone
4. Réessayer le téléchargement des voix

### Voix robotique/saccadée

**Solution:**
1. Télécharger les voix "Wavenet" au lieu de "Standard" (meilleure qualité)
2. Ajuster la vitesse dans Configuration IA (1.0x-1.2x recommandé)
3. Ajuster la tonalité (0.7x-0.9x pour voix plus grave)

---

## 💡 Astuce : Pitch Ajusté pour KITT

Si aucune voix masculine n'est disponible, ChatAI ajuste automatiquement le pitch à **0.7x** pour rendre la voix plus grave/masculine quand KITT est sélectionné.

**Dans Configuration IA:**
```
🎙️ VOIX DE KITT (TTS)
Tonalité: Ajuster vers 0.7x (grave) pour KITT
         Ajuster vers 1.0x (neutre) pour GLaDOS
```

---

## 📱 Instructions Rapides

### Pour KITT (Voix Masculine)

1. Installer Google TTS
2. Télécharger fr-FR-Wavenet-B
3. Sélectionner comme voix par défaut
4. Dans ChatAI Drawer → KITT PROFESSIONNEL
5. Pitch automatiquement ajusté à 0.7x

### Pour GLaDOS (Voix Féminine)

1. Télécharger fr-FR-Wavenet-A ou C
2. Dans ChatAI Drawer → GLaDOS SARCASTIQUE
3. Pitch automatiquement ajusté à 1.0x

---

*Guide créé le 1er novembre 2025*  
*ChatAI v2.8 - "TTS Voice Selection"*

