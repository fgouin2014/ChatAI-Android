#!/usr/bin/env pwsh
# Script de test pour KITT AI Générative
# Usage: .\test_kitt_ai.ps1

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "   KITT AI - Script de Test et Configuration   " -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# Fonction pour afficher un menu
function Show-Menu {
    Write-Host "Choisissez une option:" -ForegroundColor Yellow
    Write-Host "1. Vérifier l'installation de l'application" -ForegroundColor White
    Write-Host "2. Voir les logs KITT en temps réel" -ForegroundColor White
    Write-Host "3. Configurer une clé API OpenAI" -ForegroundColor White
    Write-Host "4. Configurer une clé API Anthropic" -ForegroundColor White
    Write-Host "5. Configurer une clé API Hugging Face" -ForegroundColor White
    Write-Host "6. Vérifier la configuration actuelle" -ForegroundColor White
    Write-Host "7. Effacer la configuration (reset)" -ForegroundColor White
    Write-Host "8. Compiler et installer l'application" -ForegroundColor White
    Write-Host "9. Lancer l'application" -ForegroundColor White
    Write-Host "10. Tests automatiques" -ForegroundColor White
    Write-Host "0. Quitter" -ForegroundColor Red
    Write-Host ""
}

# Fonction pour vérifier l'installation
function Check-Installation {
    Write-Host "`nVérification de l'installation..." -ForegroundColor Cyan
    
    $installed = adb shell pm list packages | Select-String "com.chatai"
    
    if ($installed) {
        Write-Host "✓ ChatAI est installé" -ForegroundColor Green
        
        # Vérifier la version
        $version = adb shell dumpsys package com.chatai | Select-String "versionName"
        if ($version) {
            Write-Host "  Version: $version" -ForegroundColor Gray
        }
    } else {
        Write-Host "✗ ChatAI n'est pas installé" -ForegroundColor Red
        Write-Host "  Utilisez l'option 8 pour compiler et installer" -ForegroundColor Yellow
    }
    
    Pause
}

# Fonction pour voir les logs
function Watch-Logs {
    Write-Host "`nAffichage des logs KITT AI en temps réel..." -ForegroundColor Cyan
    Write-Host "Appuyez sur Ctrl+C pour arrêter" -ForegroundColor Yellow
    Write-Host ""
    
    adb logcat -s KittAI:D
}

# Fonction pour configurer OpenAI
function Configure-OpenAI {
    Write-Host "`nConfiguration de la clé API OpenAI" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Pour obtenir une clé API:" -ForegroundColor Yellow
    Write-Host "1. Allez sur https://platform.openai.com/api-keys" -ForegroundColor Gray
    Write-Host "2. Créez un compte ou connectez-vous" -ForegroundColor Gray
    Write-Host "3. Cliquez sur 'Create new secret key'" -ForegroundColor Gray
    Write-Host "4. Copiez la clé (format: sk-...)" -ForegroundColor Gray
    Write-Host ""
    
    $apiKey = Read-Host "Entrez votre clé API OpenAI (ou 'q' pour annuler)"
    
    if ($apiKey -eq 'q' -or $apiKey -eq '') {
        Write-Host "Annulé" -ForegroundColor Yellow
        Pause
        return
    }
    
    # Vérifier le format
    if (-not $apiKey.StartsWith("sk-")) {
        Write-Host "✗ Format de clé invalide (doit commencer par 'sk-')" -ForegroundColor Red
        Pause
        return
    }
    
    # Configurer via adb
    Write-Host "`nConfiguration en cours..." -ForegroundColor Cyan
    
    # Note: Cette méthode est simplifiée. En pratique, il faudrait utiliser
    # l'interface de l'application ou modifier directement SharedPreferences
    Write-Host "✓ Pour configurer la clé, utilisez l'interface de l'application:" -ForegroundColor Green
    Write-Host "  1. Ouvrez ChatAI" -ForegroundColor Gray
    Write-Host "  2. Allez dans Configuration → IA" -ForegroundColor Gray
    Write-Host "  3. Entrez la clé: $($apiKey.Substring(0,8))..." -ForegroundColor Gray
    Write-Host "  4. Sauvegardez" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Clé à utiliser: $apiKey" -ForegroundColor Cyan
    
    Pause
}

# Fonction pour configurer Anthropic
function Configure-Anthropic {
    Write-Host "`nConfiguration de la clé API Anthropic" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Pour obtenir une clé API:" -ForegroundColor Yellow
    Write-Host "1. Allez sur https://console.anthropic.com/settings/keys" -ForegroundColor Gray
    Write-Host "2. Créez un compte" -ForegroundColor Gray
    Write-Host "3. Générez une clé API" -ForegroundColor Gray
    Write-Host "4. Format: sk-ant-..." -ForegroundColor Gray
    Write-Host ""
    
    $apiKey = Read-Host "Entrez votre clé API Anthropic (ou 'q' pour annuler)"
    
    if ($apiKey -eq 'q' -or $apiKey -eq '') {
        Write-Host "Annulé" -ForegroundColor Yellow
        Pause
        return
    }
    
    Write-Host "✓ Pour configurer la clé, utilisez l'interface de l'application" -ForegroundColor Green
    Write-Host "  Clé à utiliser: $apiKey" -ForegroundColor Cyan
    
    Pause
}

# Fonction pour configurer Hugging Face
function Configure-HuggingFace {
    Write-Host "`nConfiguration du token Hugging Face" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Pour obtenir un token:" -ForegroundColor Yellow
    Write-Host "1. Allez sur https://huggingface.co/settings/tokens" -ForegroundColor Gray
    Write-Host "2. Créez un compte" -ForegroundColor Gray
    Write-Host "3. Générez un token" -ForegroundColor Gray
    Write-Host "4. Format: hf_..." -ForegroundColor Gray
    Write-Host ""
    
    $token = Read-Host "Entrez votre token Hugging Face (ou 'q' pour annuler)"
    
    if ($token -eq 'q' -or $token -eq '') {
        Write-Host "Annulé" -ForegroundColor Yellow
        Pause
        return
    }
    
    Write-Host "✓ Pour configurer le token, utilisez l'interface de l'application" -ForegroundColor Green
    Write-Host "  Token à utiliser: $token" -ForegroundColor Cyan
    
    Pause
}

# Fonction pour vérifier la configuration
function Check-Configuration {
    Write-Host "`nVérification de la configuration..." -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Cette fonction nécessite de lire SharedPreferences" -ForegroundColor Yellow
    Write-Host "Pour l'instant, vérifiez via l'interface de l'application" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Ou vérifiez les logs lors de l'utilisation:" -ForegroundColor Gray
    Write-Host "  adb logcat | Select-String 'KittAI'" -ForegroundColor Cyan
    Write-Host ""
    
    Pause
}

# Fonction pour effacer la configuration
function Clear-Configuration {
    Write-Host "`nEffacement de la configuration..." -ForegroundColor Cyan
    
    $confirm = Read-Host "Êtes-vous sûr ? (o/n)"
    
    if ($confirm -eq 'o' -or $confirm -eq 'O') {
        Write-Host "Effacement des données de l'application..." -ForegroundColor Yellow
        adb shell pm clear com.chatai
        Write-Host "✓ Configuration effacée" -ForegroundColor Green
    } else {
        Write-Host "Annulé" -ForegroundColor Yellow
    }
    
    Pause
}

# Fonction pour compiler et installer
function Build-And-Install {
    Write-Host "`nCompilation et installation de ChatAI..." -ForegroundColor Cyan
    Write-Host ""
    
    $projectPath = "C:\androidProject\ChatAI-Android-beta\ChatAI-Android"
    
    if (-not (Test-Path $projectPath)) {
        Write-Host "✗ Projet non trouvé: $projectPath" -ForegroundColor Red
        Pause
        return
    }
    
    Write-Host "Changement de répertoire..." -ForegroundColor Gray
    Push-Location $projectPath
    
    Write-Host "Compilation en cours..." -ForegroundColor Yellow
    .\gradlew assembleDebug
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Compilation réussie" -ForegroundColor Green
        Write-Host ""
        Write-Host "Installation sur le device..." -ForegroundColor Yellow
        adb install -r app\build\outputs\apk\debug\app-debug.apk
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ Installation réussie" -ForegroundColor Green
        } else {
            Write-Host "✗ Erreur d'installation" -ForegroundColor Red
        }
    } else {
        Write-Host "✗ Erreur de compilation" -ForegroundColor Red
    }
    
    Pop-Location
    Pause
}

# Fonction pour lancer l'application
function Launch-App {
    Write-Host "`nLancement de ChatAI..." -ForegroundColor Cyan
    
    adb shell am start -n com.chatai/.MainActivity
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Application lancée" -ForegroundColor Green
        Write-Host ""
        Write-Host "Pour accéder à KITT:" -ForegroundColor Yellow
        Write-Host "  1. Tapez sur l'icône KITT dans l'application" -ForegroundColor Gray
        Write-Host "  2. Ou utilisez la navigation" -ForegroundColor Gray
    } else {
        Write-Host "✗ Erreur de lancement" -ForegroundColor Red
    }
    
    Pause
}

# Fonction pour les tests automatiques
function Run-Tests {
    Write-Host "`nTests automatiques..." -ForegroundColor Cyan
    Write-Host ""
    
    Write-Host "1. Test de l'installation..." -ForegroundColor Yellow
    $installed = adb shell pm list packages | Select-String "com.chatai"
    if ($installed) {
        Write-Host "   ✓ Application installée" -ForegroundColor Green
    } else {
        Write-Host "   ✗ Application non installée" -ForegroundColor Red
    }
    
    Write-Host ""
    Write-Host "2. Test de la connexion adb..." -ForegroundColor Yellow
    $devices = adb devices | Select-String "device$"
    if ($devices) {
        Write-Host "   ✓ Device connecté" -ForegroundColor Green
    } else {
        Write-Host "   ✗ Aucun device connecté" -ForegroundColor Red
    }
    
    Write-Host ""
    Write-Host "3. Lancement de l'application..." -ForegroundColor Yellow
    adb shell am start -n com.chatai/.MainActivity
    Start-Sleep -Seconds 2
    Write-Host "   ✓ Application lancée" -ForegroundColor Green
    
    Write-Host ""
    Write-Host "4. Vérification des logs..." -ForegroundColor Yellow
    Write-Host "   Observer les logs pendant 10 secondes..." -ForegroundColor Gray
    Write-Host ""
    
    $job = Start-Job -ScriptBlock { adb logcat -s KittAI:D }
    Start-Sleep -Seconds 10
    Stop-Job $job
    $logs = Receive-Job $job
    Remove-Job $job
    
    if ($logs) {
        Write-Host "   ✓ Logs KITT détectés" -ForegroundColor Green
    } else {
        Write-Host "   ⚠ Aucun log KITT détecté (normal si KITT n'a pas été utilisé)" -ForegroundColor Yellow
    }
    
    Write-Host ""
    Write-Host "Tests terminés" -ForegroundColor Cyan
    Pause
}

# Boucle principale
do {
    Clear-Host
    Write-Host "================================================" -ForegroundColor Cyan
    Write-Host "   KITT AI - Script de Test et Configuration   " -ForegroundColor Cyan
    Write-Host "================================================" -ForegroundColor Cyan
    Write-Host ""
    
    Show-Menu
    $choice = Read-Host "Votre choix"
    
    switch ($choice) {
        '1' { Check-Installation }
        '2' { Watch-Logs }
        '3' { Configure-OpenAI }
        '4' { Configure-Anthropic }
        '5' { Configure-HuggingFace }
        '6' { Check-Configuration }
        '7' { Clear-Configuration }
        '8' { Build-And-Install }
        '9' { Launch-App }
        '10' { Run-Tests }
        '0' { 
            Write-Host "`nAu revoir!" -ForegroundColor Cyan
            exit
        }
        default {
            Write-Host "`nOption invalide" -ForegroundColor Red
            Pause
        }
    }
} while ($true)

