# Script pour activer le mode 100% Hugging Face
# Usage: .\activer_mode_huggingface.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "ACTIVATION MODE 100% HUGGING FACE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Vérifier clé API Hugging Face
Write-Host "[1/4] Verification cle API Hugging Face..." -ForegroundColor Yellow
$hfKey = adb shell "run-as com.chatai cat /data/data/com.chatai/shared_prefs/chatai_ai_config.xml 2>/dev/null | grep -i huggingface_api_key"
if ($hfKey -match 'huggingface_api_key.*value="([^"]+)"') {
    $keyValue = $matches[1]
    if ($keyValue -and $keyValue.Length -gt 10) {
        Write-Host "  ✅ Cle API Hugging Face configuree (${keyValue.Length} chars)" -ForegroundColor Green
    } else {
        Write-Host "  ❌ Cle API Hugging Face manquante ou invalide" -ForegroundColor Red
        Write-Host "     → Configurer dans l'app: Configuration → API Keys → Hugging Face" -ForegroundColor Yellow
        exit 1
    }
} else {
    Write-Host "  ❌ Cle API Hugging Face non trouvee" -ForegroundColor Red
    Write-Host "     → Configurer dans l'app: Configuration → API Keys → Hugging Face" -ForegroundColor Yellow
    exit 1
}
Write-Host ""

# 2. Activer mode Hugging Face uniquement
Write-Host "[2/4] Activation mode Hugging Face uniquement..." -ForegroundColor Yellow
Write-Host "  ⚠️  MODIFICATION CODE NECESSAIRE" -ForegroundColor Yellow
Write-Host "     Le code actuel ne supporte pas encore le mode 100% Hugging Face pour LLM" -ForegroundColor Gray
Write-Host "     Les embeddings Hugging Face fonctionnent deja" -ForegroundColor Gray
Write-Host ""

# 3. Configuration actuelle
Write-Host "[3/4] Configuration actuelle..." -ForegroundColor Yellow
$config = adb shell "run-as com.chatai cat /data/data/com.chatai/shared_prefs/chatai_ai_config.xml 2>/dev/null | grep -E 'use_ollama_cloud|rag_enabled|rag_use_huggingface'"
if ($config) {
    Write-Host "  Configuration trouvee:" -ForegroundColor Green
    $config -split "`n" | ForEach-Object {
        if ($_ -match 'use_ollama_cloud.*true') {
            Write-Host "    ✅ Mode Cloud active" -ForegroundColor Green
        } elseif ($_ -match 'use_ollama_cloud.*false') {
            Write-Host "    ⚠️  Mode Local active (Hugging Face embeddings non disponibles)" -ForegroundColor Yellow
        }
        if ($_ -match 'rag_enabled.*true') {
            Write-Host "    ✅ RAG active" -ForegroundColor Green
        } elseif ($_ -match 'rag_enabled.*false') {
            Write-Host "    ❌ RAG desactive" -ForegroundColor Red
        }
        if ($_ -match 'rag_use_huggingface.*true') {
            Write-Host "    ✅ Hugging Face pour embeddings active" -ForegroundColor Green
        } elseif ($_ -match 'rag_use_huggingface.*false') {
            Write-Host "    ⚠️  Hugging Face pour embeddings desactive" -ForegroundColor Yellow
        }
    }
} else {
    Write-Host "  ⚠️  Configuration non trouvee" -ForegroundColor Yellow
}
Write-Host ""

# 4. Instructions
Write-Host "[4/4] Instructions pour activer mode 100% Hugging Face..." -ForegroundColor Yellow
Write-Host ""
Write-Host "  POUR EMBEDDINGS (RAG) - DEJA FONCTIONNEL:" -ForegroundColor Cyan
Write-Host "    1. Configuration → Local → Mode: Cloud" -ForegroundColor Gray
Write-Host "    2. Configuration → RAG → Activer RAG" -ForegroundColor Gray
Write-Host "    3. Configuration → RAG → Utiliser Hugging Face: Oui" -ForegroundColor Gray
Write-Host ""
Write-Host "  POUR LLM (GENERATION TEXTE) - MODIFICATION CODE NECESSAIRE:" -ForegroundColor Yellow
Write-Host "    ⚠️  Le code actuel utilise Hugging Face LLM seulement en fallback" -ForegroundColor Yellow
Write-Host "    → Voir: ChatAI-Android/docs/MODE_HUGGINGFACE_COMPLET.md" -ForegroundColor Gray
Write-Host "    → Modifications necessaires dans KittAIService.kt" -ForegroundColor Gray
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "RESUME" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "✅ Embeddings Hugging Face: DISPONIBLE (apres config)" -ForegroundColor Green
Write-Host "⚠️  LLM Hugging Face: FALLBACK SEULEMENT (modification code necessaire)" -ForegroundColor Yellow
Write-Host ""
Write-Host "Pour plus d'infos:" -ForegroundColor Gray
Write-Host "  ChatAI-Android/docs/MODE_HUGGINGFACE_COMPLET.md" -ForegroundColor Gray

